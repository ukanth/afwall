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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.events.LogEvent;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.log.LogRxEvent;
import dev.ukanth.ufirewall.util.G;
import eu.chainfire.libsuperuser.Shell;
import io.reactivex.disposables.Disposable;

public class LogService extends Service {

    public static final String TAG = "AFWall";

    public static String logPath;

    private Shell.Interactive rootSession;
    static Handler handler;

    public static final int QUEUE_NUM = 40;

    public static Toast toast;
    public static TextView toastTextView;
    public static CharSequence toastText;
    public static int toastDuration;
    public static int toastDefaultYOffset;
    public static int toastYOffset;

    private static Runnable showOnlyToastRunnable;
    private static CancelableRunnable showToastRunnable;
    private static View toastLayout;

    private Disposable disposable;

    private static abstract class CancelableRunnable implements Runnable {
        public boolean cancel;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void showToast(final Context context, final Handler handler, final CharSequence text, boolean cancel) {
        if (showToastRunnable == null) {
            showToastRunnable = new CancelableRunnable() {
                public void run() {


                    if (cancel && toast != null) {
                        toast.cancel();
                    }

                    if (cancel || toast == null) {
                        toastLayout = ((LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_toast, null);
                        toastTextView = (TextView) toastLayout.findViewById(R.id.toasttext);
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.i(TAG, "Restarting LogService");
            startLogService();
        }
        return START_STICKY;
    }


    @Override
    public void onCreate() {
        startLogService();
    }

    private static class LogTask extends AsyncTask<Void, Void, Void> {
        private LogEvent event;

        private LogTask(LogEvent event) {
            this.event = event;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            store(event.logInfo, event.ctx);
            return null;
        }

        @Override
        protected void onPostExecute(Void a) {
            if (event != null && event.logInfo.uidString != null && event.logInfo.uidString.length() > 0) {
                if (G.showLogToasts() && G.canShow(event.logInfo.uid)) {
                    showToast(event.ctx, handler, event.logInfo.uidString, false);
                }
            }
        }
    }

    private void startLogService() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = LogRxEvent.subscribe((event -> {
                    if (event != null) {
                        try {
                            new Thread(() -> {
                                store(event.logInfo, event.ctx);
                                if (event != null && event.logInfo != null && event.logInfo.uidString != null && event.logInfo.uidString.length() > 0) {
                                    if (G.showLogToasts() && G.canShow(event.logInfo.uid)) {
                                        showToast(event.ctx, handler, event.logInfo.uidString, false);
                                    }
                                }
                            }).start();
                        } catch (Exception e) {
                        }
                    }
                })
        );
        if (G.enableLogService()) {
            // this method is executed in a background thread
            // no problem calling su here
            if (G.logTarget() != null && G.logTarget().length() > 1) {
                if (G.logDmsg().isEmpty()) {
                    G.logDmsg("OS");
                }
                switch (G.logTarget()) {
                    case "LOG":
                        switch (G.logDmsg()) {
                            case "OS":
                                logPath = "echo PID=$$ & while true; do dmesg -c ; sleep 1 ; done";
                                break;
                            case "BX":
                                logPath = "echo PID=$$ & while true; do busybox dmesg -c ; sleep 1 ; done";
                                break;
                            default:
                                logPath = "echo PID=$$ & while true; do dmesg -c ; sleep 1 ; done";
                        }
                        break;
                    case "NFLOG":
                        logPath = Api.getNflogPath(getApplicationContext());
                        logPath = "echo $$ & " + logPath + " " + QUEUE_NUM;
                        break;
                }

                Log.i(TAG, "Starting Log Service: " + logPath + " for LogTarget: " + G.logTarget());
                Log.i(TAG, "rootSession " + rootSession != null ? "rootSession is not Null" : "Null rootSession");
                handler = new Handler();

                if(logPath != null) {
                    rootSession = new Shell.Builder()
                            .useSU()
                            .setMinimalLogging(true)
                            .setOnSTDOUTLineListener(line -> {
                                if (line != null && !line.isEmpty() && line.startsWith("PID=")) {
                                    try {
                                        String uid = line.split("=")[1];
                                        if (uid != null) {
                                            Set data = G.storedPid();
                                            if (data == null || data.isEmpty()) {
                                                data = new HashSet();
                                                data.add(uid);
                                                G.storedPid(data);
                                            } else if (!data.contains(uid)) {
                                                Set data2 = new HashSet();
                                                data2.addAll(data);
                                                data2.add(uid);
                                                G.storedPid(data2);
                                            }
                                        }
                                    } catch (Exception e) {
                                    }
                                } else {
                                    storeLogInfo(line, getApplicationContext());
                                }

                            }).addCommand(logPath).open();
                } else {
                    Log.i(TAG, "Unable to start log service. Log Path is empty");
                    Api.toast(getApplicationContext(), getApplicationContext().getString(R.string.error_log));
                    G.enableLogService(false);
                    stopSelf();
                }
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

    private void closeSession() {
        new Thread(() -> {
            Log.i(Api.TAG, "Cleanup session");
            if (rootSession != null) {
                rootSession.close();
            }
        }).start();
        Api.cleanupUid();
    }


    /* private static class Task extends AsyncTask<Void, Void, LogInfo> {
         private Context context;
         private String line;
         private Task(Context context, String line) {
             this.context = context;
             this.line = line;
         }
         @Override
         protected LogInfo doInBackground(Void... voids) {
             return LogInfo.parseLogs(line, context);
         }
         @Override
         protected void onPostExecute(LogInfo a) {
             if (a != null) {
                 LogRxEvent.publish(new LogEvent(a, context));
             }
         }
     }
 */
    private void storeLogInfo(String line, Context context) {
        if (G.enableLogService()) {
            if (line != null && line.trim().length() > 0) {
                if (line.contains("{AFL}")) {
                    try {
                        new Thread(() -> {
                            LogRxEvent.publish(new LogEvent(LogInfo.parseLogs(line, context, "{AFL}", 0), context));
                        }).start();
                    } catch (Exception e) {
                        //Handle when has exception thrown
                    }
                } /*else if (line.contains("{AFL-ALLOW}")) {
                    try {
                        new Thread(() -> {
                            LogRxEvent.publish(new LogEvent(LogInfo.parseLogs(line, context, "{AFL-ALLOW}", 1), context));
                        }).start();
                    } catch (RejectedExecutionException e) {
                        //Handle when has exception thrown
                    }
                }*/
            }
        }
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
                } catch (Exception de) {
                    Log.i(TAG, "Exception while saving log data:" + e.getLocalizedMessage());
                }
            }
            Log.i(TAG, "Exception while saving log data:" + e.getLocalizedMessage());
        } catch (Exception e) {
            Log.i(TAG, "Exception while saving log data:" + e.getLocalizedMessage());
        }

    }

    @Override
    public void onDestroy() {
        closeSession();
        if (disposable != null) {
            disposable.dispose();
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Log service removed");

        PendingIntent service = PendingIntent.getService(
                getApplicationContext(),
                1001,
                new Intent(getApplicationContext(), LogService.class),
                PendingIntent.FLAG_ONE_SHOT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, service);
    }


}
