package dev.ukanth.ufirewall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
import android.os.Process;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.RootShellService.ShellState.INIT;

public class RootShellService extends RootService {

    public final int NOTIFICATION_ID = 33347;
    private final LinkedList<RootCommand> waitQueue = new LinkedList<>();
    private ShellState rootState = INIT;
    private NotificationManager notificationManager;
    private static final boolean enableProfiling = true;
    public static final int EXIT_NO_ROOT_ACCESS = -1;
    //number of retries - increase the count
    private final static int MAX_RETRIES = 10;
    private Context mContext;

    static {
        // Only load the library when this class is loaded in a root process.
        // The classloader will load this class (and call this static block) in the non-root
        // process because we accessed it when constructing the Intent to send.
        // Add this check so we don't unnecessarily load native code that'll never be used.
        if (Process.myUid() == 0) {
            Log.i(G.TAG, "UID matches");
            System.loadLibrary("native-lib");
        } else {
            Log.i(G.TAG, "UID does not match");
        }
    }

    // Demonstrate we can also run native code via JNI with RootServices
    native int nativeGetUid();

    native String nativeReadFile(String file);

    class RootIPC extends IRootShellService.Stub {

        @Override
        public int getPid() {
            return Process.myPid();
        }

        @Override
        public int getUid() {
            return nativeGetUid();
        }

        @Override
        public String readCmdline() {
            // Normally we cannot read /proc/cmdline without root
            return nativeReadFile("/proc/cmdline");
        }
    }

    @Override
    public void onRebind(@NonNull Intent intent) {
        // This callback will be called when we are reusing a previously started root process
        Log.d(G.TAG, "RootShellService: onRebind, daemon process reused");
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(G.TAG, "RootShellService: onBind");
        return new RootIPC();
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        Log.d(G.TAG, "RootShellService: onUnbind, client process unbound");
        // We return true here to tell libsu that we want this service to run as a daemon
        return true;
    }

    public void runCommandsAsSU(Context ctx, List<String> cmds, RootCommand state) {
        Log.i(G.TAG, "Received cmds: #" + cmds.size());
        state.setCommmands(cmds);
        state.commandIndex = 0;
        state.retryCount = 0;
        //already in memory and applied
        //add it to queue

        if (mContext == null) {
            mContext = ctx.getApplicationContext();
        }
        Log.d(G.TAG, "Hashing...." + state.isv6);
        Log.d(G.TAG, state.hash + "");

        waitQueue.add(state);

        if (rootState == INIT || (rootState == ShellState.FAIL && state.reopenShell)) {
            reOpenShell(ctx);
        } else if (rootState != ShellState.BUSY) {
            runNextSubmission();
        } else {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.i(G.TAG, "State of rootShell: " + rootState);
                    if (rootState == ShellState.BUSY) {
                        //try resetting state to READY forcefully
                        Log.i(G.TAG, "Forcefully changing the state " + rootState);
                        rootState = ShellState.READY;
                    }
                    runNextSubmission();
                }
            }, 10000);
        }
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
                Log.i(G.TAG, "Start processing next state");
                if (enableProfiling) {
                    state.startTime = new Date();
                }
                if (rootState == ShellState.FAIL) {
                    // if we don't have root, abort all queued commands
                    complete(state, EXIT_NO_ROOT_ACCESS);
                    continue;
                } else if (rootState == ShellState.READY) {
                    rootState = ShellState.BUSY;
                    if (G.isRun()) {
                        createNotification();
                    }
                    processCommands(state);
                }
            }
        } while (false);
    }

    private void sendUpdate(final RootCommand state2) {
        new Thread(() -> {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("UPDATEUI4");
            broadcastIntent.putExtra("SIZE", state2.getCommmands().size());
            broadcastIntent.putExtra("INDEX", state2.commandIndex);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(broadcastIntent);
        }).start();
    }

    private void processCommands(final RootCommand state) {
        if (state.commandIndex < state.getCommmands().size() && state.getCommmands().get(state.commandIndex) != null) {
            String command = state.getCommmands().get(state.commandIndex);
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
                    Shell.Result result = Shell.su(command).exec();
                    int exitCode = result.getCode();
                    if (result.isSuccess()) {
                        for (String line : result.getOut()) {
                            if (line != null && !line.equals("")) {
                                if (state.res != null) {
                                    state.res.append(line + "\n");
                                }
                                state.lastCommandResult.append(line + "\n");
                            }
                        }
                    }

                    if (exitCode >= 0 && exitCode == state.retryExitCode && state.retryCount < MAX_RETRIES) {
                        //lets wait for few ms before trying ?
                        state.retryCount++;
                        Log.d(G.TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
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
                            Log.e(G.TAG, "libsuperuser error " + exitCode + " on command '" + state.lastCommand + "'");
                        } else {
                            if (errorExit) {
                                Log.i(G.TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
                                        "\nOutput:\n" + state.lastCommandResult);
                            }
                            rootState = ShellState.READY;
                        }
                        runNextSubmission();
                    } else {
                        processCommands(state);
                    }

                } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
                    Log.e(G.TAG, e.getMessage(), e);
                }
            }
        } else {
            complete(state, 0);
        }
    }


    private void complete(final RootCommand state, int exitCode) {
        if (enableProfiling) {
            Log.d(G.TAG, "RootShell: " + state.getCommmands().size() + " commands completed in " +
                    (new Date().getTime() - state.startTime.getTime()) + " ms");
        }
        state.exitCode = exitCode;
        state.done = true;
        if (state.cb != null) {
            state.cb.cbFunc(state);
        }

        if (exitCode == 0 && state.successToast != -1) {
            Api.sendToastBroadcast(mContext, mContext.getString(state.successToast));
        } else if (exitCode != 0 && state.failureToast != -1) {
            Api.sendToastBroadcast(mContext, mContext.getString(state.failureToast));
        }

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void reOpenShell(Context context) {
        if (rootState == null || rootState != ShellState.READY || rootState == ShellState.FAIL) {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
            rootState = ShellState.BUSY;
            try {
                Intent intent = new Intent(context, RootShellService.class);
                context.startService(intent);
            } catch (Exception e) {
                Log.e(G.TAG, e.getMessage(), e);
            }
        }
    }


    private void createNotification() {

        String CHANNEL_ID = "firewall.apply";
        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID);

        Intent appIntent = new Intent(mContext, MainActivity.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, mContext.getString(R.string.runNotification),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);


        int notifyType = G.getNotificationPriority();

        Notification notification = builder.setSmallIcon(R.drawable.ic_apply)
                .setAutoCancel(false)
                .setContentTitle(mContext.getString(R.string.applying_rules))
                .setTicker(mContext.getString(R.string.app_name))
                .setChannelId(CHANNEL_ID)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationManager.IMPORTANCE_LOW)
                .setContentText("").build();
        switch (notifyType) {
            case 0:
                notification.priority = NotificationCompat.PRIORITY_LOW;
                break;
            case 1:
                notification.priority = NotificationCompat.PRIORITY_MIN;
                break;
        }
        builder.setProgress(0, 0, true);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    public enum ShellState {
        INIT,
        READY,
        BUSY,
        FAIL
    }


}
