/**
 * Serialized execution of RootCommand scripts on a persistent root shell.
 * <p>
 * Copyright (C) 2013  Kevin Cernekee
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.ukanth.ufirewall.service;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.topjohnwu.superuser.Shell;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

/**
 * All engine state is confined to a single worker thread: public entry points and libsu
 * callbacks only post work to it. This gives a real FIFO queue (one RootCommand runs at a time,
 * never interleaved with another), and RootCommand callbacks are always invoked on that thread.
 * <p>
 * Every submitted RootCommand is guaranteed to complete exactly once: on success, on a command
 * error, when the shell dies, when root is unavailable, or when the stall guard fires.
 */
final class RootShellEngine {

    static final int EXIT_NO_ROOT_ACCESS = -1;
    static final int EXIT_STALLED = -3;
    // same codes as libsuperuser, which this engine used before: a command ran longer than
    // WATCHDOG_TIMEOUT_SEC / the shell died
    static final int EXIT_WATCHDOG = -1;
    static final int EXIT_SHELL_DIED = -2;

    // Per-command watchdog: a command that doesn't finish in time kills the shell. Must stay well
    // above the "iptables -w 5" lock wait, otherwise waiting for the xtables lock during network
    // changes kills the shell.
    private static final int WATCHDOG_TIMEOUT_SEC = 30;
    // How long su may take to start (includes the superuser grant prompt).
    private static final int OPEN_TIMEOUT_SEC = 30;
    // If a script (or opening the shell) makes no progress for this long, give up on it.
    private static final long STALL_TIMEOUT_MS = 90_000;
    private static final long STALL_CHECK_INTERVAL_MS = 10_000;
    // Minimum interval between automatic reopen attempts after a shell that once worked died.
    private static final long AUTO_REOPEN_INTERVAL_MS = 10_000;
    private static final int MAX_RETRIES = 10;
    private static final String FALLBACK_MARKER = " # __FALLBACK_ATTEMPTED__";
    /** command prefix: record a failure as a warning on the RootCommand instead of failing it */
    static final String WARN = "#WARN# ";

    private enum State {INIT, OPENING, READY, BUSY, FAIL}

    private final String tag;
    private final String label;

    private final ScheduledExecutorService worker;

    // ---- confined to the worker thread ----
    private final ArrayDeque<RootCommand> queue = new ArrayDeque<>();
    private State state = State.INIT;
    // a dedicated shell, not libsu's shared main shell: the rule scripts must not queue behind (or
    // hold up) the app's other root commands, and stderr is kept apart from stdout
    private Shell session;
    private RootCommand current;
    private java.util.concurrent.ScheduledFuture<?> commandWatchdog;
    private long dispatchToken;
    private long openToken;
    private long lastProgress;
    private long lastOpenAttempt;
    private boolean everOpened;
    private Context appCtx;
    private boolean stallGuardStarted;

    RootShellEngine(String tag, String label) {
        this.tag = tag;
        this.label = label;
        this.worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AFWall-RootShell" + label);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Queue a script for execution. Safe to call from any thread, including from inside a
     * RootCommand callback.
     */
    void submit(Context ctx, List<String> cmds, RootCommand cmd) {
        enqueue(ctx, java.util.Collections.singletonList(cmd), cmds);
    }

    /**
     * Queue several RootCommands (each with its commands already set) back to back, so that no
     * other submission can run in between them.
     */
    void submitAll(Context ctx, List<RootCommand> batch) {
        enqueue(ctx, batch, null);
    }

    /**
     * @param cmds commands for a single-item batch, or null when each RootCommand already has its
     *             commands set
     */
    private void enqueue(Context ctx, List<RootCommand> batch, List<String> cmds) {
        final Context app = ctx != null ? ctx.getApplicationContext() : null;
        final List<RootCommand> items = new java.util.ArrayList<>(batch);
        worker.execute(() -> {
            if (appCtx == null && app != null) {
                appCtx = app;
            }
            for (RootCommand cmd : items) {
                if (cmd == current || queue.contains(cmd)) {
                    // Re-submitting a pending RootCommand would replace the commands it is about
                    // to run and fire its callback twice; keep the first submission.
                    List<String> pending = cmd.getCommmands();
                    Log.e(tag, "RootCommand submitted again while still pending; ignoring. Pending script starts with: "
                            + (pending != null && !pending.isEmpty() ? pending.get(0) : "<empty>"));
                    continue;
                }
                if (cmds != null) {
                    cmd.setCommmands(cmds);
                }
                cmd.commandIndex = 0;
                cmd.retryCount = 0;
                cmd.exitCode = 0;
                cmd.done = false;
                cmd.warnings.clear();
                queue.add(cmd);
            }
            startStallGuard();
            pump();
        });
    }

    private void pump() {
        if (state == State.BUSY || state == State.OPENING || queue.isEmpty()) {
            return;
        }
        if (state == State.INIT) {
            openShell();
            return;
        }
        if (state == State.READY && (session == null || !session.isAlive())) {
            Log.w(tag, "Root shell(" + label + ") is no longer running");
            state = State.FAIL;
        }
        if (state == State.FAIL) {
            if (canReopen(queue.peek())) {
                openShell();
            } else {
                // No usable shell and not allowed to reopen yet: fail what is queued now
                while (!queue.isEmpty()) {
                    complete(queue.poll(), EXIT_NO_ROOT_ACCESS);
                }
            }
            return;
        }
        current = queue.poll();
        state = State.BUSY;
        lastProgress = SystemClock.elapsedRealtime();
        dispatch();
    }

    private boolean canReopen(RootCommand next) {
        if (next != null && next.reopenShell) {
            return true;
        }
        // A shell that worked before most likely died (watchdog, su daemon restart); retry it
        // automatically, rate limited so a revoked grant doesn't turn into a prompt loop.
        return everOpened && SystemClock.elapsedRealtime() - lastOpenAttempt >= AUTO_REOPEN_INTERVAL_MS;
    }

    private void openShell() {
        closeSession();
        state = State.OPENING;
        lastOpenAttempt = lastProgress = SystemClock.elapsedRealtime();
        final long token = ++openToken;
        Log.d(tag, "Starting root shell(" + label + ")...");
        Shell shell = null;
        String failure = null;
        try {
            // Blocks until su is up (or refused); we are on the worker thread. build("su") runs
            // exactly su: libsu's plain build() would fall back to a non-root sh.
            shell = Shell.Builder.create()
                    .setFlags(0) // keep stderr separate: iptables reports its errors there
                    .setTimeout(OPEN_TIMEOUT_SEC)
                    .build("su");
            if (!shell.isRoot()) {
                failure = "not a root shell";
            }
        } catch (Exception e) {
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        onShellOpened(token, shell, failure);
    }

    private void onShellOpened(long token, Shell shell, String failure) {
        if (token != openToken || state != State.OPENING) {
            closeQuietly(shell); // a newer shell superseded this one
            return;
        }
        if (failure != null) {
            Log.e(tag, "Can't open root shell(" + label + "): " + failure);
            closeQuietly(shell);
            state = State.FAIL;
            failQueued();
            return;
        }
        session = shell;
        Log.d(tag, "Root shell(" + label + ") is open");
        everOpened = true;
        state = State.READY;
        pump();
    }

    private void failQueued() {
        while (!queue.isEmpty()) {
            complete(queue.poll(), EXIT_NO_ROOT_ACCESS);
        }
    }

    private void dispatch() {
        final RootCommand st = current;
        final List<String> cmds = st.getCommmands();
        // skip null entries instead of silently ending the script early
        while (cmds != null && st.commandIndex < cmds.size() && cmds.get(st.commandIndex) == null) {
            st.commandIndex++;
        }
        if (cmds == null || st.commandIndex >= cmds.size()) {
            finishCurrent(0);
            return;
        }
        String command = cmds.get(st.commandIndex);
        sendUpdate(st);
        st.ignoreExitCode = false;
        st.warnOnError = false;
        if (command.startsWith("#NOCHK# ")) {
            command = command.replaceFirst("#NOCHK# ", "");
            st.ignoreExitCode = true;
        } else if (command.startsWith(WARN)) {
            // e.g. a custom script line: a failure is reported, but doesn't stop the script
            command = command.substring(WARN.length());
            st.ignoreExitCode = true;
            st.warnOnError = true;
        }
        st.lastCommand = command;
        st.lastCommandResult = new StringBuilder();
        final long token = ++dispatchToken;
        try {
            final List<String> out = new java.util.ArrayList<>();
            final List<String> err = new java.util.ArrayList<>();
            session.newJob().add(command).to(out, err).submit(worker,
                    result -> onCommandResult(token, result.getCode(), out, err));
            // libsu has no per-command timeout
            cancelCommandWatchdog();
            commandWatchdog = worker.schedule(() -> onCommandTimeout(token), WATCHDOG_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(tag, "Unable to queue command on root shell(" + label + ")", e);
            closeSession();
            state = State.FAIL;
            finishCurrent(EXIT_NO_ROOT_ACCESS);
        }
    }

    private void onCommandResult(long token, int exitCode, List<String> output, List<String> stderr) {
        if (token != dispatchToken || current == null) {
            return; // stale result for a command the stall guard already gave up on
        }
        final RootCommand st = current;
        cancelCommandWatchdog();
        lastProgress = SystemClock.elapsedRealtime();
        if (output != null) {
            for (String line : output) {
                if (line != null && !line.isEmpty()) {
                    if (st.res != null) {
                        st.res.append(line).append("\n");
                    }
                    st.lastCommandResult.append(line).append("\n");
                }
            }
        }
        // iptables reports its errors on stderr; keep them for the failure log
        if (stderr != null) {
            for (String line : stderr) {
                if (line != null && !line.isEmpty()) {
                    st.lastCommandResult.append(line).append("\n");
                }
            }
        }

        if (exitCode == Shell.Result.JOB_NOT_EXECUTED) {
            // the shell died; nothing more can run on this session
            Log.e(tag, "Root shell(" + label + ") could not run '" + st.lastCommand + "'");
            closeSession();
            state = State.FAIL;
            finishCurrent(EXIT_SHELL_DIED);
            return;
        }

        // command not executable (126): retry once with the system iptables binary
        if (exitCode == 126 && fallbackToSystemBinary(st)) {
            Log.w(tag, "Built-in iptables failed with exit 126, retrying with system iptables");
            G.setBuiltinIptablesFailed(true);
            dispatch();
            return;
        }

        if (exitCode == st.retryExitCode && st.retryCount < MAX_RETRIES) {
            st.retryCount++;
            Log.d(tag, "command '" + st.lastCommand + "' exited with status " + exitCode +
                    ", retrying (attempt " + st.retryCount + "/" + MAX_RETRIES + ")");
            final long retryToken = dispatchToken;
            worker.schedule(() -> {
                if (current == st && dispatchToken == retryToken && state == State.BUSY) {
                    lastProgress = SystemClock.elapsedRealtime();
                    dispatch();
                }
            }, 100L * st.retryCount, TimeUnit.MILLISECONDS);
            return;
        }

        if (exitCode != 0 && st.warnOnError) {
            String result = st.lastCommandResult.toString().trim();
            Log.w(tag, "command '" + st.lastCommand + "' exited with status " + exitCode + ": " + result);
            st.warnings.add(st.lastCommand + (result.isEmpty() ? " (exit " + exitCode + ")" : ": " + result));
        }

        st.commandIndex++;
        st.retryCount = 0;

        if (exitCode != 0 && !st.ignoreExitCode) {
            Log.i(tag, "command '" + st.lastCommand + "' exited with status " + exitCode +
                    "\nOutput:\n" + st.lastCommandResult);
            finishCurrent(exitCode);
        } else if (st.commandIndex >= st.getCommmands().size()) {
            finishCurrent(0);
        } else {
            dispatch();
        }
    }

    /**
     * The command didn't finish within the watchdog time: kill the shell, as libsuperuser's
     * watchdog did. A hanging command would otherwise block every later script.
     */
    private void onCommandTimeout(long token) {
        if (token != dispatchToken || current == null || state != State.BUSY) {
            return; // finished in time
        }
        Log.e(tag, "Command '" + current.lastCommand + "' on root shell(" + label + ") didn't finish within "
                + WATCHDOG_TIMEOUT_SEC + "s; closing the shell");
        closeSession();
        state = State.FAIL;
        finishCurrent(EXIT_WATCHDOG);
    }

    private void cancelCommandWatchdog() {
        if (commandWatchdog != null) {
            commandWatchdog.cancel(false);
            commandWatchdog = null;
        }
    }

    private void finishCurrent(int exitCode) {
        RootCommand st = current;
        current = null;
        dispatchToken++;
        if (state == State.BUSY) {
            state = State.READY;
        }
        if (st != null) {
            complete(st, exitCode);
        }
        pump();
    }

    private void complete(RootCommand st, int exitCode) {
        st.exitCode = exitCode;
        st.done = true;
        try {
            if (st.cb != null) {
                st.cb.cbFunc(st);
            }
        } catch (Throwable t) {
            // a failing callback must never wedge the queue
            Log.e(tag, "RootCommand callback failed: " + android.util.Log.getStackTraceString(t));
        }
        try {
            if (appCtx != null) {
                if (exitCode == 0 && st.successToast != RootShellService.NO_TOAST) {
                    Api.sendToastBroadcast(appCtx, appCtx.getString(st.successToast));
                } else if (exitCode != 0 && st.failureToast != RootShellService.NO_TOAST) {
                    Api.sendToastBroadcast(appCtx, appCtx.getString(st.failureToast));
                }
            }
            if (FirewallService.isInstanceRunning()) {
                FirewallService.refreshNotification();
            }
        } catch (Throwable t) {
            Log.e(tag, "Error while reporting RootCommand completion: " + android.util.Log.getStackTraceString(t));
        }
    }

    private void startStallGuard() {
        if (stallGuardStarted) {
            return;
        }
        stallGuardStarted = true;
        worker.scheduleWithFixedDelay(this::checkStall, STALL_CHECK_INTERVAL_MS, STALL_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    private void checkStall() {
        if (state != State.BUSY && state != State.OPENING) {
            return;
        }
        long idle = SystemClock.elapsedRealtime() - lastProgress;
        if (idle < STALL_TIMEOUT_MS) {
            return;
        }
        Log.e(tag, "Root shell(" + label + ") made no progress for " + idle + "ms in state " + state
                + (current != null ? " on '" + current.lastCommand + "'" : "") + "; resetting");
        boolean wasOpening = state == State.OPENING;
        openToken++;
        closeSession();
        state = State.FAIL;
        if (wasOpening) {
            failQueued();
        } else {
            finishCurrent(EXIT_STALLED);
        }
    }

    private void closeSession() {
        closeQuietly(session);
        session = null;
    }

    private void closeQuietly(Shell shell) {
        if (shell != null) {
            try {
                shell.close();
            } catch (Exception e) {
                Log.w(tag, "Error closing root shell(" + label + "): " + e.getMessage());
            }
        }
    }

    private void sendUpdate(RootCommand st) {
        if (appCtx == null) {
            return;
        }
        Intent intent = new Intent(st.isv6 ? "UPDATEUI6" : "UPDATEUI4");
        intent.putExtra("SIZE", st.getCommmands().size());
        intent.putExtra("INDEX", st.commandIndex);
        LocalBroadcastManager.getInstance(appCtx).sendBroadcast(intent);
    }

    /**
     * Replace built-in iptables/ip6tables paths with system paths in the current command.
     *
     * @return true if the command was rewritten and should be retried
     */
    private boolean fallbackToSystemBinary(RootCommand st) {
        if (appCtx == null || st.lastCommand == null || st.lastCommand.contains(FALLBACK_MARKER)) {
            return false;
        }
        String builtinDir = appCtx.getDir("bin", 0).getAbsolutePath();
        if (!st.lastCommand.contains(builtinDir)) {
            return false;
        }
        String systemIptables = Api.findSystemBinary("iptables");
        String systemIp6tables = Api.findSystemBinary("ip6tables");
        String updated = st.lastCommand;
        if (systemIptables != null) {
            updated = updated.replace(builtinDir + "/iptables", systemIptables);
        }
        if (systemIp6tables != null) {
            updated = updated.replace(builtinDir + "/ip6tables", systemIp6tables);
        }
        if (updated.equals(st.lastCommand)) {
            Log.w(tag, "No system iptables found for fallback");
            return false;
        }
        List<String> commands = st.getCommmands();
        if (st.commandIndex >= commands.size()) {
            return false;
        }
        String original = commands.get(st.commandIndex);
        String prefix = original.startsWith("#NOCHK# ") ? "#NOCHK# " : "";
        commands.set(st.commandIndex, prefix + updated + FALLBACK_MARKER);
        Log.i(tag, "Fallback applied: " + st.lastCommand + " -> " + updated);
        return true;
    }
}
