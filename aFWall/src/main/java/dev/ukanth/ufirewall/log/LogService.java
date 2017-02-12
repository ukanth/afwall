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

package dev.ukanth.ufirewall.log;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;
import java.util.Set;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.events.LogEvent;
import dev.ukanth.ufirewall.util.G;
import eu.chainfire.libsuperuser.Shell;
import eu.chainfire.libsuperuser.StreamGobbler;

public class LogService extends Service {

    public static final String TAG = "AFWall";

    public static String logPath;
    private final IBinder mBinder = new Binder();

    private Shell.Interactive rootSession;

    static Handler handler;

    public static final int QUEUE_NUM = 40;

    public static Toast toast;
    public static TextView toastTextView;
    public static CharSequence toastText;
    public static int toastDuration;
    public static int toastDefaultYOffset;
    public static int toastYOffset;
    LogData data;

    private static Runnable showOnlyToastRunnable;
    private static CancelableRunnable showToastRunnable;
    private static View toastLayout;

    private static abstract class CancelableRunnable implements Runnable {
        public boolean cancel;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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

                    switch (toastDuration) {
                        case 3500:
                            toast.setDuration(Toast.LENGTH_LONG);
                            break;
                        case 7000:
                            toast.setDuration(Toast.LENGTH_LONG);

                            if (showOnlyToastRunnable == null) {
                                showOnlyToastRunnable = new Runnable() {
                                    public void run() {
                                        toast.show();
                                    }
                                };
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

    /*@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.i(TAG, "Restarting LogService");
            startLogService();
        }
        return Service.START_STICKY;
    }*/


    @Override
    public void onCreate() {
        startLogService();
    }

    private void startLogService() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
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
                            case "TB":
                                logPath = "echo PID=$$ & while true; do toybox dmesg -c ; sleep 1 ; done";
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

                closeSession();
                rootSession = new Shell.Builder()
                        .useSU()
                        .setMinimalLogging(true)
                        .setOnSTDOUTLineListener(new StreamGobbler.OnLineListener() {
                            @Override
                            public void onLine(String line) {
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

                            }
                        }).addCommand(logPath).open();
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(Api.TAG, "Cleanup session");
                if (rootSession != null) {
                    rootSession.close();
                }
            }
        }).start();
        Api.cleanupUid();
    }


    private void storeLogInfo(String line, Context context) {
        if (G.enableLogService()) {
            if (line != null && line.trim().length() > 0) {
                if (line.contains("AFL")) {
                    EventBus.getDefault().post(new LogEvent(LogInfo.parseLogs(line, context), context));
                }
            }
        }
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void showMessageToast(LogEvent event) {
        if (event.logInfo.uidString != null && event.logInfo.uidString.length() > 0) {
            if (G.showLogToasts()) {
                showToast(event.ctx, handler, event.logInfo.uidString, false);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void storeDataToDB(LogEvent event) {
        store(event.logInfo);
    }

    private void store(final LogInfo logInfo) {
        try {
            data = new LogData();
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
            FlowManager.getDatabase(LogDatabase.class).beginTransactionAsync(new ITransaction() {
                @Override
                public void execute(DatabaseWrapper databaseWrapper) {
                    data.save(databaseWrapper);
                }
            }).build().execute();
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("connection pool has been closed")) {
                //reconnect logic
                try {
                    FlowManager.init(new FlowConfig.Builder(this).build());
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
        EventBus.getDefault().unregister(this);
        closeSession();
        super.onDestroy();
    }
}
