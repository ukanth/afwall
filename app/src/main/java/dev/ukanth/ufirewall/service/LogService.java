/**
 * Background service to spool /proc/kmesg command output using klogripper
 * <p/>
 * Copyright (C) 2014 Umakanthan Chandran
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.graphics.drawable.IconCompat;

import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;


import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.ukanth.ufirewall.Api;

import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.events.LogEvent;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.util.G.ctx;

public class LogService extends Service {

    public static final String TAG = "AFWall";
    public static String logPath;
    public static final int QUEUE_NUM = 40;

    /*private Toast toast;
    private TextView toastTextView;
    private CharSequence toastText;
    private int toastDuration;
    private int toastDefaultYOffset;
    private int toastYOffset;

    private Runnable showOnlyToastRunnable;
    private CancelableRunnable showToastRunnable;
    private View toastLayout;

    private Disposable disposable;
    private final int BUFF_LEN = 2000;
    private static abstract class CancelableRunnable implements Runnable {
        public boolean cancel;
    }

    */

    private  NotificationManager manager;
    private  NotificationCompat.Builder notificationBuilder;

    private List<String> callbackList;
    private ExecutorService executorService;

    static {
        com.topjohnwu.superuser.Shell.enableVerboseLogging = BuildConfig.DEBUG;
        com.topjohnwu.superuser.Shell.setDefaultBuilder(com.topjohnwu.superuser.Shell.Builder.create()
                .setFlags(com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR)
        );
    }

    //public native String stringFromLog();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*public void showToast(final Context context, final Handler handler, final CharSequence text, boolean cancel) {
        if (showToastRunnable == null) {
            showToastRunnable = new CancelableRunnable() {
                public void run() {
                    if (cancel && toast != null) {
                        toast.cancel();
                    }
                    if (cancel || toast == null) {
                        toastLayout = ((LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_toast, null);
                        toastTextView = toastLayout.findViewById(R.id.toasttext);
                        if (toast == null) {
                            toast = new Toast(context);
                        }
                        toastDefaultYOffset = toast.getYOffset();
                        toast.setView(toastLayout);
                    }

                    //Fix for many crashes in android 28
                    if (Build.VERSION_CODES.P >= 28 && toast.getView().isShown()) {
                        toast.cancel();
                    }

                    switch (toastDuration) {
                        case 3500:
                            toast.setDuration(Toast.LENGTH_LONG);
                            break;
                        case 7000:
                            toast.setDuration(Toast.LENGTH_LONG);
                            if (showOnlyToastRunnable == null) {
                                showOnlyToastRunnable = () -> toast.show();
                            }
                            handler.postDelayed(showOnlyToastRunnable, 3250);
                            break;
                        default:
                            toast.setDuration(Toast.LENGTH_SHORT);
                    }

                    switch (G.toast_pos()) {
                        case "top":
                            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, toastYOffset);
                            break;
                        case "bottom":
                            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, toastYOffset);
                            break;
                        case "center":
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            break;
                        default:
                            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, toastDefaultYOffset);
                            break;
                    }

                    toastTextView.setText(android.text.Html.fromHtml(toastText.toString()));
                    toast.show();
                }
            };
        }

        showToastRunnable.cancel = cancel;
        toastText = text;
        handler.post(showToastRunnable);
    }*/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLogService();
        return START_STICKY;
    }


    @Override
    public void onCreate() {
        startLogService();
    }


    private void startLogService() {
        if (G.enableLogService() && executorService == null) {
            // this method is executed in a background thread
            // no problem calling su here
            String log = G.logTarget();
            if (log != null) {
                log = log.trim();
                if(log.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Please select log target first", Toast.LENGTH_LONG).show();
                    return;
                }
                switch (log) {
                    case "LOG":
                        logPath = "cat /proc/kmsg";
                        break;
                    case "NFLOG":
                        logPath = Api.getNflogPath(getApplicationContext());
                        logPath =  logPath + " " + QUEUE_NUM;
                        break;
                }

                Log.i(TAG, "Starting Log Service: " + logPath + " for LogTarget: " + G.logTarget());
                callbackList = new CallbackList<String>() {
                    @Override
                    public void onAddElement(String line) {
                        //kmsg entering into idle state, wait for few seconds and start again ?
                        Log.i(TAG, line);
                        if(line.contains("suspend exit")) {
                            restartWatcher(logPath);
                        }

                        if(line.contains("{AFL}")) {
                            storeLogInfo(line, getApplicationContext());
                        }
                    }
                };
                initiateLogWatcher(logPath);
                createNotification();

            } else {
                Log.i(TAG, "Unable to start log service. LogTarget is empty");
                Api.toast(getApplicationContext(), getApplicationContext().getString(R.string.error_log));
                G.enableLogService(false);
                stopSelf();
            }
        } else {
            Log.i(Api.TAG, "Logservice is running.. skipping");

        }
    }

    private void restartWatcher(String logPath) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.i(G.TAG, "Restarting log watcher after 5s");
            initiateLogWatcher(logPath);
        }, 5000);
    }

    private void createNotification() {
        String NOTIFICATION_CHANNEL_ID = "firewall.logservice";
        String channelName = ctx.getString(R.string.firewall_log_notify);

        manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(109);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert manager != null;
            if (G.getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }


        Intent appIntent = new Intent(ctx, LogActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Artificial stack so that navigating backward leads back to the Home screen
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
                .addParentStack(MainActivity.class)
                .addNextIntent(new Intent(ctx, LogActivity.class));

        PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);
    }

    private void initiateLogWatcher(String logCommand) {
        //kills the existing one
        if(executorService != null) {
            executorService.shutdownNow();
        }
        //make sure it's enabled first
        if(G.enableLogService()) {
            executorService = Executors.newSingleThreadExecutor();
            com.topjohnwu.superuser.Shell.su(logCommand).to(callbackList).submit(executorService, out -> {
                //failed to start, try restarting
                if(out.getCode() == 0) {
                    restartWatcher(logPath);
                }
            });
        } else{
            if(executorService != null) {
                executorService.shutdownNow();
            }
        }
    }


    private void storeLogInfo(String line, Context context) {
        try {

            LogEvent event = new LogEvent(LogInfo.parseLogs(line, context, "{AFL}", 0), context);
            store(event.logInfo, event.ctx);
            showNotification(event.logInfo);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            //e.printStackTrace();
        }
    }



    @SuppressLint("RestrictedApi")
    private void showNotification(LogInfo logInfo) {
        manager.notify(109, notificationBuilder.setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentText(logInfo.uidString)
                .setSmallIcon(R.drawable.ic_block_black_24dp)
                .setAutoCancel(true)
                .build());
    }

    private static void store(final LogInfo logInfo, Context context) {
        try {
            if (logInfo != null) {
                LogData data = new LogData();
                data.setDst(logInfo.dst);
                data.setOut(logInfo.out);
                data.setSrc(logInfo.src);
                data.setDpt(logInfo.dpt);
                data.setIn(logInfo.in);
                data.setLen(logInfo.len);
                data.setProto(logInfo.proto);
                data.setTimestamp(System.currentTimeMillis());
                data.setSpt(logInfo.spt);
                data.setUid(logInfo.uid);
                data.setAppName(logInfo.appName);
                data.setType(logInfo.type);
                if (G.isDoKey(context) || G.isDonate()) {
                    try {
                        data.setHostname(logInfo.host != null ? logInfo.host : "");
                    } catch (Exception e) {
                    }
                }
                data.setType(0);
                FlowManager.getDatabase(LogDatabase.class).beginTransactionAsync(databaseWrapper ->
                        data.save(databaseWrapper)).build().execute();
            }
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("connection pool has been closed")) {
                //reconnect logic
                try {
                    FlowManager.init(new FlowConfig.Builder(context).build());
                    store(logInfo,context);
                } catch (Exception de) {
                    Log.e(TAG, "Exception while saving log data:" + e.getLocalizedMessage(), de);
                }
            }
            Log.e(TAG, "Exception while saving log data:" + e.getLocalizedMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Exception while saving log data:" + e.getLocalizedMessage(),e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Log service onDestroy");
        if(executorService != null) {
            executorService.shutdownNow();
        }
        executorService = null;
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Log service removed");

        Intent intent = new Intent(getApplicationContext(), LogService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime() + 000, pendingIntent);
        if(executorService != null) {
            executorService.shutdownNow();
        }
        executorService = null;
        super.onTaskRemoved(rootIntent);
    }
}
