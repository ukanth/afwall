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
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

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
import dev.ukanth.ufirewall.util.G;
import eu.chainfire.libsuperuser.Debug;
import eu.chainfire.libsuperuser.Shell;

import static dev.ukanth.ufirewall.service.RootShellService.ShellState.INIT;


public class RootShellService extends Service {

    public static final String TAG = "AFWall";

    /* write command completion times to logcat */
    private static final boolean enableProfiling = false;

    private static Shell.Interactive rootSession;
    private static Context mContext;
    private static NotificationManager notificationManager;
    public static final int NOTIFICATION_ID = 33347;

    public enum ShellState {
        INIT,
        READY,
        BUSY,
        FAIL
    }

    private static ShellState rootState = INIT;

    //number of retries
    private final static int MAX_RETRIES = 5;

    private static LinkedList<RootCommand> waitQueue = new LinkedList<RootCommand>();

    public final static int EXIT_NO_ROOT_ACCESS = -1;

    public final static int NO_TOAST = -1;

    private static NotificationCompat.Builder builder;

    private static void complete(final RootCommand state, int exitCode) {
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

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
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

            Log.i(TAG, "Start processing next state");
            if (enableProfiling) {
                state.startTime = new Date();
            }

            if (rootState == ShellState.FAIL) {
                // if we don't have root, abort all queued commands
                complete(state, EXIT_NO_ROOT_ACCESS);
                continue;
            } else if (rootState == ShellState.READY) {
                Log.i(TAG, "Total commamds: #" + state.getCommmands().size());
                rootState = ShellState.BUSY;
                if(G.isRun()) {
                    createNotification(mContext);
                }
                processCommands(state);
            }
        } while (false);
    }

    private static void processCommands(final RootCommand state) {
        if (state.commandIndex < state.getCommmands().size() && state.getCommmands().get(state.commandIndex) != null) {
            String command = state.getCommmands().get(state.commandIndex);
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
                            if (state.commandIndex >= state.getCommmands().size() || errorExit) {
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
        } else {
            complete(state, 0);
        }
    }

    private static void sendUpdate(final RootCommand state) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction("UPDATEUI");
                broadcastIntent.putExtra("SIZE", state.getCommmands().size());
                broadcastIntent.putExtra("INDEX", state.commandIndex);
                mContext.sendBroadcast(broadcastIntent);

               /* if (builder != null) {
                    builder.setProgress(state.getCommmands().size(), state.commandIndex, false);
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                }*/
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

    private static void reOpenShell(Context context) {
        if (rootState == null || rootState != ShellState.READY || rootState == ShellState.FAIL) {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
            rootState = ShellState.BUSY;
            startShellInBackground();
            Intent intent = new Intent(context, RootShellService.class);
            context.startService(intent);
        }
    }


    public static void runScriptAsRoot(Context ctx, List<String> cmds, RootCommand state, boolean useThreads) {
        Log.i(TAG, "Received cmds: #" + cmds.size());
        state.setCommmands(cmds);
        state.commandIndex = 0;
        state.retryCount = 0;
        if (mContext == null) {
            mContext = ctx.getApplicationContext();
        }
        waitQueue.add(state);
        if (rootState == ShellState.INIT || (rootState == ShellState.FAIL && state.reopenShell)) {
            reOpenShell(ctx);
        } else if (rootState != ShellState.BUSY) {
            runNextSubmission();
        } else {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.i(TAG, "State of rootShell" + rootState);
                    if (rootState == ShellState.BUSY) {
                        //try resetting state to READY forcefully
                        Log.i(TAG, "Forcefully changing the state " + rootState);
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

    private static void createNotification(Context context) {

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        builder = new NotificationCompat.Builder(context);

        Intent appIntent = new Intent(context, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        builder.setSmallIcon(R.drawable.notification)
                .setAutoCancel(false)
                .setContentTitle(context.getString(R.string.applying_rules))
                .setTicker(context.getString(R.string.app_name))
                .setPriority(-2)
                .setContentText("");
        builder.setProgress(0, 0, true);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}