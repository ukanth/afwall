/**
 * Keep a persistent root shell running in the background
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
 *
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import eu.chainfire.libsuperuser.Debug;
import eu.chainfire.libsuperuser.Shell;

import static dev.ukanth.ufirewall.service.RootShellService.ShellState.INIT;
import static dev.ukanth.ufirewall.util.G.ctx;

public class RootShellService extends Service {

    public static final String TAG = "AFWall";

    /* write command completion times to logcat */
    private static final boolean enableProfiling = true;

    private static Shell.Interactive rootSession;
    private static Context mContext;
    private static NotificationManager notificationManager;
    private static final int NOTIFICATION_ID = 33347;

    public enum ShellState {
        INIT,
        READY,
        BUSY,
        FAIL
    }

    private static ShellState rootState = INIT;

    private final static int MAX_RETRIES = 10;

    private static LinkedList<RootCommand> waitQueue = new LinkedList<RootCommand>();

    public final static int EXIT_NO_ROOT_ACCESS = -1;

    public final static int NO_TOAST = -1;

    public static class RootCommand {
        private List<String> commmands;

        private Callback cb = null;
        private int successToast = NO_TOAST;
        private int failureToast = NO_TOAST;
        private boolean reopenShell = false;
        private int retryExitCode = -1;

        private int commandIndex;
        private boolean ignoreExitCode;
        private Date startTime;
        private int retryCount;

        public StringBuilder res;
        public String lastCommand;
        public StringBuilder lastCommandResult;
        public int exitCode;
        public boolean done = false;

        public static abstract class Callback {

            /**
             * Optional user-specified callback
             */
            public abstract void cbFunc(RootCommand state);
        }

        /**
         * Set callback to run after command completion
         *
         * @param cb Callback object, with cbFunc() populated
         * @return RootCommand builder object
         */
        public RootCommand setCallback(Callback cb) {
            this.cb = cb;
            return this;
        }

        /**
         * Tell RootShell to display a toast message on success
         *
         * @param resId Resource ID of the toast string
         * @return RootCommand builder object
         */
        public RootCommand setSuccessToast(int resId) {
            this.successToast = resId;
            return this;
        }

        /**
         * Tell RootShell to display a toast message on failure
         *
         * @param resId Resource ID of the toast string
         * @return RootCommand builder object
         */
        public RootCommand setFailureToast(int resId) {
            this.failureToast = resId;
            return this;
        }

        /**
         * Tell RootShell whether or not it should try to open a new root shell if the last attempt
         * died.  To avoid "thrashing" it might be best to only try this in response to a user
         * request
         *
         * @param reopenShell true to attempt reopening a failed shell
         * @return RootCommand builder object
         */
        public RootCommand setReopenShell(boolean reopenShell) {
            this.reopenShell = reopenShell;
            return this;
        }

        /**
         * Capture the command output in this.res
         *
         * @param enableLog true to enable logging
         * @return RootCommand builder object
         */
        public RootCommand setLogging(boolean enableLog) {
            if (enableLog) {
                this.res = new StringBuilder();
            } else {
                this.res = null;
            }
            return this;
        }

        /**
         * Retry a failed command on a specific exit code
         *
         * @param retryExitCode code that indicates a transient failure
         * @return RootCommand builder object
         */
        public RootCommand setRetryExitCode(int retryExitCode) {
            this.retryExitCode = retryExitCode;
            return this;
        }

        /**
         * Run a series of commands as root; call cb.cbFunc() when complete
         *
         * @param ctx    Context object used to create toasts
         * @param script List of commands to run as root
         */
        public final void run(Context ctx, List<String> script) {
            RootShellService.runScriptAsRoot(ctx, script, this, false);
        }

        /**
         * Run a series of commands as root in thread mode; call cb.cbFunc() when complete
         *
         * @param ctx    Context object used to create toasts
         * @param script List of commands to run as root
         */
        public final void runThread(Context ctx, List<String> script) {
            RootShellService.runScriptAsRoot(ctx, script, this, true);
        }

        /**
         * Run a single command as root; call cb.cbFunc() when complete
         *
         * @param ctx Context object used to create toasts
         * @param cmd Command to run as root
         */
        public final void run(Context ctx, String cmd) {
            List<String> script = new ArrayList<String>();
            script.add(cmd);
            RootShellService.runScriptAsRoot(ctx, script, this, false);
        }
    }

    private static void complete(final RootCommand state, int exitCode) {
        if (enableProfiling) {
            Log.d(TAG, "RootShell: " + state.commmands.size() + " commands completed in " +
                    (new Date().getTime() - state.startTime.getTime()) + " ms");
        }
        state.exitCode = exitCode;
        state.done = true;
        if (state.cb != null) {
            state.cb.cbFunc(state);
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        if (exitCode == 0 && state.successToast != NO_TOAST) {
            sendToastBroadcast(mContext.getString(state.successToast));
        } else if (exitCode != 0 && state.failureToast != NO_TOAST) {
            sendToastBroadcast(mContext.getString(state.failureToast));
        }
    }

    private static void sendToastBroadcast(String message) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("TOAST");
        broadcastIntent.putExtra("MSG", message);
        ctx.sendBroadcast(broadcastIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { // if crash restart...
            Log.i(TAG, "Restarting RootShell...");
            List<String> cmds = new ArrayList<String>();
            cmds.add("true");
            new RootCommand().setFailureToast(R.string.error_su)
                    .setReopenShell(true).run(getApplicationContext(), cmds);
        }
        return Service.START_STICKY;
    }

    private static void runNextSubmission() {
        do {
            RootCommand state;
            try {
                state = waitQueue.remove();
            } catch (NoSuchElementException e) {
                // nothing left to do
                if (rootState == ShellState.BUSY) {
                    rootState = ShellState.READY;
                }
                break;
            }

            if (enableProfiling) {
                state.startTime = new Date();
            }

            if (rootState == ShellState.FAIL) {
                // if we don't have root, abort all queued commands
                complete(state, EXIT_NO_ROOT_ACCESS);
                continue;
            } else if (rootState == ShellState.READY) {
                rootState = ShellState.BUSY;
                notificationManager = createNotification(mContext);
                processCommands(state);
            }
        } while (false);
    }

    private static void processCommands(final RootCommand state) {
        if (state.commandIndex < state.commmands.size()) {
            String command = state.commmands.get(state.commandIndex);
            sendUpdate(state);
            if (command != null) {
                state.ignoreExitCode = false;

                if (command.startsWith("#NOCHK# ")) {
                    command = command.replaceFirst("#NOCHK# ", "");
                    state.ignoreExitCode = true;
                }
                state.lastCommand = command;
                state.lastCommandResult = new StringBuilder();
                try {
                    rootSession.addCommand(command, 0, new Shell.OnCommandResultListener() {
                        @Override
                        public void onCommandResult(int commandCode, int exitCode,
                                                    List<String> output) {
                            if (output != null) {
                                ListIterator<String> iter = output.listIterator();
                                while (iter.hasNext()) {
                                    String line = iter.next();
                                    if (line != null && !line.equals("")) {
                                        if (state.res != null) {
                                            state.res.append(line + "\n");
                                        }
                                        state.lastCommandResult.append(line + "\n");
                                    }
                                }
                            }
                            if (exitCode >= 0 && exitCode == state.retryExitCode && state.retryCount < MAX_RETRIES) {
                                state.retryCount++;
                                Log.d(TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
                                        ", retrying (attempt " + state.retryCount + "/" + MAX_RETRIES + ")");
                                processCommands(state);
                                return;
                            }

                            state.commandIndex++;
                            state.retryCount = 0;

                            boolean errorExit = exitCode != 0 && !state.ignoreExitCode;
                            if (state.commandIndex >= state.commmands.size() || errorExit) {
                                complete(state, exitCode);
                                if (exitCode < 0) {
                                    rootState = ShellState.FAIL;
                                    Log.e(TAG, "libsuperuser error " + exitCode + " on command '" + state.lastCommand + "'");
                                } else {
                                    if (errorExit) {
                                        Log.i(TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
                                                "\nOutput:\n" + state.lastCommandResult);
                                    }
                                    rootState = ShellState.READY;
                                }
                                runNextSubmission();
                            } else {
                                processCommands(state);
                            }
                        }
                    });
                } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    private static void sendUpdate(final RootCommand state) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction("UPDATEUI");
                broadcastIntent.putExtra("SIZE", state.commmands.size());
                broadcastIntent.putExtra("INDEX", state.commandIndex);
                ctx.sendBroadcast(broadcastIntent);
            }
        }).start();
    }

    private static void setupLogging() {
        Debug.setDebug(false);
        Debug.setLogTypeEnabled(Debug.LOG_ALL, false);
        Debug.setLogTypeEnabled(Debug.LOG_GENERAL, false);
        Debug.setSanityChecksEnabled(false);
        Debug.setOnLogListener(new Debug.OnLogListener() {
            @Override
            public void onLog(int type, String typeIndicator, String message) {
                Log.i(TAG, "[libsuperuser] " + message);
            }
        });
    }


    private static void startShellInBackground() {
        Log.d(TAG, "Starting root shell...");
        setupLogging();
        //start only rootSession is null
        if (rootSession == null) {
            rootSession = new Shell.Builder().
                    useSU().
                    setWantSTDERR(true).
                    setWatchdogTimeout(5).
                    open(new Shell.OnCommandResultListener() {
                        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                            if (exitCode < 0) {
                                Log.e(TAG, "Can't open root shell: exitCode " + exitCode);
                                rootState = ShellState.FAIL;
                            } else {
                                Log.d(TAG, "Root shell is open");
                                rootState = ShellState.READY;
                            }
                            runNextSubmission();
                        }
                    });
        }

    }

    private static void reOpenShell(Context ctx) {
        rootState = ShellState.BUSY;
        startShellInBackground();
        Intent intent = new Intent(ctx, RootShellService.class);
        ctx.startService(intent);
    }

    /*static class ExecuteCommand implements Callable<IpCmd> {
        private String command;

        ExecuteCommand(String command) {
            this.command = command;
        }

        @Override
        public IpCmd call() throws Exception {
            final IpCmd ip = new IpCmd(command);
            final StringBuilder builder = new StringBuilder();
            if (command != null) {
                if (command.startsWith("#NOCHK# ")) {
                    command = command.replaceFirst("#NOCHK# ", "");
                } else {
                }
                Shell.OnCommandResultListener listener = new Shell.OnCommandResultListener() {
                    @Override
                    public void onCommandResult(int commandCode, int exitCode,
                                                List<String> output) {
                        if (output != null) {
                            ListIterator<String> iter = output.listIterator();
                            while (iter.hasNext()) {
                                String line = iter.next();
                                if (line != null && !line.equals("")) {
                                    if (builder != null) {
                                        builder.append(line + "\n");
                                    }
                                }
                            }
                            ip.setOutput(builder.toString());
                            ip.setExitCode(exitCode);
                        }
                    }
                };
                if (listener != null) {
                    try {
                        rootSession.addCommand(command, 0, listener);
                    } catch (NullPointerException e) {
                        Log.d(TAG, "Unable to add commands to session");
                    }
                }
            }
            return ip;
        }
    }

    static class IpCmd {

        IpCmd(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public int getExitCode() {
            return exitCode;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        String command;
        String output;
        int exitCode;
    }

    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(20, TimeUnit.SECONDS))
                    Log.e(TAG, "thread pool did not terminate");
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }*/

    private static void runScriptAsRoot(Context ctx, List<String> cmds, RootCommand state, boolean useThreads) {

        if (mContext == null) {
            mContext = ctx.getApplicationContext();
        }
        if (rootState == ShellState.INIT || (rootState == ShellState.FAIL && state.reopenShell)) {
            reOpenShell(ctx);
        }
        state.commmands = cmds;
        state.commandIndex = 0;
        state.retryCount = 0;
        if (mContext == null) {
            mContext = ctx.getApplicationContext();
        }
        waitQueue.add(state);
        if (rootState != ShellState.BUSY) {
            runNextSubmission();
        }

       /* if (useThreads) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            List<Callable<IpCmd>> callables = new ArrayList<>();

            for (final String str : script) {
                callables.add(new ExecuteCommand(str));
            }

            Log.i(TAG, "Total rules waiting to be applied: " + script.size() + " , " + callables.size());
            try {
                if (script.size() > 0) {
                    notificationManager = createNotification(mContext);
                    List<Future<IpCmd>> results =
                            executor.invokeAll(callables);

                    for (Future<IpCmd> future : results) {
                        if (future.get().getExitCode() != 0) {
                            //failed to execute this command
                            // TODO: implement retry logic for those rules.
                            state.exitCode = -1;
                            Log.i(TAG, future.get().getCommand() + " : " + future.get().getExitCode());
                        } else {
                        }
                    }
                    if (results.size() != script.size()) {
                        state.exitCode = -1;
                    } else {
                        state.exitCode = 0;
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.e(e.getClass().getName(), e.getMessage(), e);
            } catch (Exception e) {
                Log.e(e.getClass().getName(), e.getMessage(), e);
            }
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
            state.done = true;
            if (state.cb != null) {
                state.cb.cbFunc(state);
            }
            //shut down the executor service now
            shutdownAndAwaitTermination(executor);

        } else { } */
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static void showToastUIThread(final String msg, final Context mContext) {
        try {
            Thread thread = new Thread() {
                public void run() {
                    Looper.prepare();
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (mContext != null && msg != null) {
                                    Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                                }
                                handler.removeCallbacks(this);
                                Looper.myLooper().quit();
                            } catch (Exception e) {
                                Log.i(TAG, "Exception in showToastUIThread: " + e.getLocalizedMessage());
                            }
                        }
                    }, 2000);
                    Looper.loop();
                }
            };
            thread.start();
        } catch (Exception e) {
            Log.e(TAG, "Exception in showing toast");
        }

    }

    private static NotificationManager createNotification(Context context) {

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

        Intent appIntent = new Intent(context, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        builder.setSmallIcon(R.drawable.notification)
                .setAutoCancel(false)
                .setContentTitle(context.getString(R.string.applying_rules))
                //keep the priority as low ,so it's not visible on lockscreen
                .setTicker(context.getString(R.string.app_name))
                .setPriority(-2)
                .setContentText("");
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
        return mNotificationManager;
    }
}