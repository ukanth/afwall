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

import static dev.ukanth.ufirewall.service.RootShellService.ShellState.INIT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.ApplicationErrorLog;
import dev.ukanth.ufirewall.util.G;
import eu.chainfire.libsuperuser.Debug;
import eu.chainfire.libsuperuser.Shell;


public class RootShellService extends Service implements Cloneable {

    public static final String TAG = "AFWall";
    public static final int NOTIFICATION_ID = 1;
    public static final int EXIT_NO_ROOT_ACCESS = -1;
    public static final int NO_TOAST = -1;
    /* write command completion times to logcat */
    private static final boolean enableProfiling = false;
    //number of retries - increase the count
    private final static int MAX_RETRIES = 10;
    private static Shell.Interactive rootSession;
    private Context mContext;
    private NotificationManager notificationManager;
    private static ShellState rootState = INIT;
    private final LinkedList<RootCommand> waitQueue = new LinkedList<>();

    private void complete(final RootCommand state, int exitCode) {
        if (enableProfiling) {
            Log.d(TAG, "RootShell: " + state.getCommmands().size() + " commands completed in " +
                    (new Date().getTime() - state.startTime.getTime()) + " ms");
        }
        state.exitCode = exitCode;
        state.done = true;
        if (state.cb != null) {
            state.cb.cbFunc(state);
        }

        if (exitCode == 0 && state.successToast != NO_TOAST) {
            Api.sendToastBroadcast(mContext, mContext.getString(state.successToast));
        } else if (exitCode != 0 && state.failureToast != NO_TOAST) {
            Api.sendToastBroadcast(mContext, mContext.getString(state.failureToast));
        }

        // Refresh FirewallService notification after rules complete
        if (FirewallService.isInstanceRunning()) {
            Log.d(TAG, "Rules completed, refreshing FirewallService notification");
            FirewallService.refreshNotification();
        }
    }

    private void failRootCommand(final RootCommand state, final int exitCode, final Exception exception) {
        String command = state.lastCommand != null ? state.lastCommand : "root shell command";
        String message = "Root access failed while running " + command + " (exit " + exitCode + ")";
        if (exception != null) {
            Log.e(TAG, message, exception);
            message += ": " + exception.getMessage();
        } else {
            Log.e(TAG, message);
        }
        ApplicationErrorLog.add(mContext, message);
        if (exitCode == EXIT_NO_ROOT_ACCESS) {
            G.hasRoot(false);
        }
        rootState = ShellState.FAIL;
        complete(state, exitCode);
        runNextSubmission();
    }

    private boolean retryWithReopenedShell(final RootCommand state) {
        if (!state.reopenShell || state.retriedRootShell) {
            return false;
        }

        state.retriedRootShell = true;
        rootSession = null;
        rootState = ShellState.BUSY;
        waitQueue.addFirst(state);
        Log.w(TAG, "Cached root shell is unavailable; reopening before command retry");
        ApplicationErrorLog.add(mContext, "Cached root shell was unavailable; requesting root again before retry");
        startShellInBackground();
        return true;
    }

    private void runNextSubmission() {

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
            if (state != null) {
                //same as last one. ignore it
                if (enableProfiling) {
                    state.startTime = new Date();
                }
                if (rootState == ShellState.FAIL) {
                    // if we don't have root, abort all queued commands
                    complete(state, EXIT_NO_ROOT_ACCESS);
                    runNextSubmission();
                } else if (rootState == ShellState.READY) {
                    rootState = ShellState.BUSY;
                    // Don't create notification - let FirewallService handle it
                    // if (G.isRun()) {
                    //     createNotification(mContext);
                    // }
                    processCommands(state);
                }
            }
        } while (false);
    }

    private void processCommands(final RootCommand state) {
        if (state.commandIndex < state.getCommmands().size() && state.getCommmands().get(state.commandIndex) != null) {
            String command = state.getCommmands().get(state.commandIndex);
            //Log.i("AFWall", command);

            //not to send conflicting status
            if (!state.isv6) {
                sendUpdate(state);
            }
            if (command != null) {
                state.ignoreExitCode = false;

                if (command.startsWith("#NOCHK# ")) {
                    command = command.replaceFirst("#NOCHK# ", "");
                    state.ignoreExitCode = true;
                }
                state.lastCommand = command;
                state.lastCommandResult = new StringBuilder();
                try {
                    // Check if shell is still valid before executing command
                    if (rootSession == null || !rootSession.isRunning() ) {
                        if (retryWithReopenedShell(state)) {
                            return;
                        }
                        failRootCommand(state, EXIT_NO_ROOT_ACCESS, null);
                        return;
                    }
                    
                    rootSession.addCommand(command, 0, (Shell.OnCommandResultListener2) (commandCode, exitCode, output, STDERR)-> {
                        ListIterator<String> iter = output.listIterator();
                        while (iter.hasNext()) {
                            String line = iter.next();
                            if (line != null && !line.equals("")) {
                                if (state.res != null) {
                                    state.res.append(line).append("\n");
                                }
                                state.lastCommandResult.append(line).append("\n");
                            }
                        }
                        // Special handling for exit code 126 (command not executable) - fallback to system iptables
                        if (exitCode == 126 && shouldFallbackToSystem(state)) {
                            Log.w(TAG, "Built-in iptables failed with exit 126, attempting fallback to system iptables");
                            // Remember that built-in iptables failed for future preference
                            G.setBuiltinIptablesFailed(true);
                            fallbackToSystemBinary(state);
                            processCommands(state);
                            return;
                        }
                        
                        if (exitCode >= 0 && exitCode == state.retryExitCode && state.retryCount < MAX_RETRIES) {
                            //lets wait for few ms before trying ?
                            state.retryCount++;
                            
                            // Add exponential backoff delay for retries
                            try {
                                Thread.sleep(100 * state.retryCount);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            processCommands(state);
                            return;
                        }

                        state.commandIndex++;
                        state.retryCount = 0;

                        boolean errorExit = exitCode != 0 && !state.ignoreExitCode;
                        if (state.commandIndex >= state.getCommmands().size() || errorExit) {
                            complete(state, errorExit ? exitCode : 0);
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
                    });
                } catch (RuntimeException e) {
                    failRootCommand(state, EXIT_NO_ROOT_ACCESS, e);
                }
            }
        } else {
            complete(state, 0);
            rootState = ShellState.READY;
            runNextSubmission();
        }
    }

    private  void sendUpdate(final RootCommand state2) {
        new Thread(() -> {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("UPDATEUI4");
            broadcastIntent.putExtra("SIZE", state2.getCommmands().size());
            broadcastIntent.putExtra("INDEX", state2.commandIndex);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcastIntent);
        }).start();
    }

    private void createNotification(Context context) {

        String CHANNEL_ID = "firewall.service";
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);

        Intent appIntent = new Intent(context, MainActivity.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /* Create or update. */
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.firewall_service),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(resultPendingIntent);


        int notifyType = G.getNotificationPriority();

        Notification notification = builder.setSmallIcon(R.drawable.notification)
                .setAutoCancel(false)
                .setContentTitle(context.getString(R.string.applying_rules))
                .setTicker(context.getString(R.string.app_name))
                .setChannelId(CHANNEL_ID)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setPriority(NotificationManager.IMPORTANCE_LOW)
                .setContentText("").build();
        /*switch (notifyType) {
            case 0:
                notification.priority = NotificationCompat.PRIORITY_LOW;
                break;
            case 1:
                notification.priority = NotificationCompat.PRIORITY_MIN;
                break;
        }*/
        builder.setProgress(0, 0, true);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { // if crash restart...
            Log.i(TAG, "Restarting RootShell...");
            List<String> cmds = new ArrayList<>();
            cmds.add("true");
            new RootCommand().setFailureToast(R.string.error_su)
                    .setReopenShell(true).run(getApplicationContext(), cmds);
        }
        return Service.START_STICKY;
    }

    private void setupLogging() {
        Debug.setDebug(false);
        Debug.setLogTypeEnabled(Debug.LOG_ALL, false);
        Debug.setLogTypeEnabled(Debug.LOG_GENERAL, false);
        Debug.setSanityChecksEnabled(false);
        Debug.setOnLogListener((type, typeIndicator, message) -> Log.i(TAG, "[libsuperuser] " + message));
    }


    private synchronized void startShellInBackground() {
        Log.d(TAG, "Starting root shell(4)...");
        setupLogging();
        //start only rootSession is null or closed
        if (rootSession == null || !rootSession.isRunning()) {
            if (rootSession != null && !rootSession.isRunning()) {
                rootSession = null;
            }
            
            try {
                    rootSession = new Shell.Builder().
                        useSU().
                        setWatchdogTimeout(5).
                        open((success, reason) -> {
                            if (!success || reason < 0) {
                                String message = "Can't open root shell: exitCode " + reason;
                                Log.e(TAG, message);
                                ApplicationErrorLog.add(mContext, message);
                                G.hasRoot(false);
                                rootState = ShellState.FAIL;
                            } else {
                                Log.d(TAG, "Root shell(4) is open");
                                Log.i(TAG, "Root shell opened for IPv4 commands");
                                G.hasRoot(true);
                                rootState = ShellState.READY;
                            }
                            runNextSubmission();
                        });
            } catch (RuntimeException e) {
                String message = "Can't open root shell: " + e.getMessage();
                Log.e(TAG, message, e);
                ApplicationErrorLog.add(mContext, message);
                G.hasRoot(false);
                rootState = ShellState.FAIL;
                runNextSubmission();
            }
        } else {
            rootState = ShellState.READY;
            runNextSubmission();
        }
    }

    private void reOpenShell(Context context) {
        if (rootState == null || rootState != ShellState.READY || rootState == ShellState.FAIL) {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
            rootState = ShellState.BUSY;
            startShellInBackground();
            try {
                Intent intent = new Intent(context, RootShellService.class);
                context.startService(intent);
            } catch (Exception e){
                Log.e(TAG, e.getMessage(),e);
            }
        }
    }


    public void runScriptAsRoot(Context ctx, List<String> cmds, RootCommand state) {
        state.setCommmands(cmds);
        state.commandIndex = 0;
        state.retryCount = 0;
        if (mContext == null) {
            mContext = ctx.getApplicationContext();
        }
        //already in memory and applied
        //add it to queue

        waitQueue.add(state);

        if (rootState == INIT || (rootState == ShellState.FAIL && state.reopenShell)) {
            reOpenShell(ctx);
        } else if (rootState != ShellState.BUSY) {
            runNextSubmission();
        } else {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.i(TAG, "State of rootShell(4): " + rootState);
                    if (rootState == ShellState.BUSY) {
                        //try resetting state to READY forcefully after extended wait
                        Log.w(TAG, "Forcefully changing the state after 5s timeout: " + rootState);
                        rootState = ShellState.READY;
                    }
                    runNextSubmission();
                }
            }, 5000);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Check if fallback to system binary should be attempted for exit code 126
     * Only fallback once per command to avoid infinite loops
     */
    private boolean shouldFallbackToSystem(RootCommand state) {
        if (state.lastCommand == null || mContext == null) {
            return false;
        }
        
        // Check if command contains built-in iptables path and hasn't been fallback attempted
        String builtinDir = mContext.getDir("bin", 0).getAbsolutePath();
        return state.lastCommand.contains(builtinDir) && 
               !state.lastCommand.contains("__FALLBACK_ATTEMPTED__");
    }
    
    /**
     * Replace built-in iptables/ip6tables paths with system paths in the current command
     */
    private void fallbackToSystemBinary(RootCommand state) {
        if (state.lastCommand == null || mContext == null) {
            return;
        }
        
        String builtinDir = mContext.getDir("bin", 0).getAbsolutePath();
        String originalCommand = state.lastCommand;
        
        // Try to find system iptables
        String systemIptables = Api.findSystemBinary("iptables");
        String systemIp6tables = Api.findSystemBinary("ip6tables");
        
        if (systemIptables != null || systemIp6tables != null) {
            String updatedCommand = originalCommand;
            
            // Replace built-in paths with system paths
            if (systemIptables != null) {
                updatedCommand = updatedCommand.replace(builtinDir + "/iptables", systemIptables);
            }
            if (systemIp6tables != null) {
                updatedCommand = updatedCommand.replace(builtinDir + "/ip6tables", systemIp6tables);
            }
            
            // Mark as fallback attempted to prevent infinite loops
            updatedCommand += " # __FALLBACK_ATTEMPTED__";
            
            // Update the command in the current state
            List<String> commands = state.getCommmands();
            if (state.commandIndex < commands.size()) {
                commands.set(state.commandIndex, updatedCommand);
                Log.i(TAG, "Fallback applied: " + originalCommand + " -> " + updatedCommand);
            }
        } else {
            Log.w(TAG, "No system iptables found for fallback");
        }
    }

    public enum ShellState {
        INIT,
        READY,
        BUSY,
        FAIL
    }
}
