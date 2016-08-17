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

import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import com.stericson.roottools.RootTools;

import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
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
    LogInfo logInfo;


    /*private static final int MAX_ENTRIES = 10;

    private static LinkedList<LogData> circular = new LinkedList<LogData>();
*/
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
                        if (android.os.Build.VERSION.SDK_INT > 10 || toast == null) {
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


    @Override
    public void onCreate() {

        if (G.logTarget() != null && G.logTarget().length() > 0 && !G.logTarget().isEmpty() && G.enableLogService()) {
            switch (G.logTarget()) {
                case "LOG":
                    if (RootTools.isBusyboxAvailable()) {
                        logPath = "while true; do busybox dmesg -c ; sleep 1 ; done";
                    } else if (RootTools.isToyboxAvailable()) {
                        logPath = "while true; do toybox dmesg -c ; sleep 1 ; done";
                    } else {
                        logPath = "while true; do dmesg -c ; sleep 1 ; done";
                    }
                    break;
                case "NFLOG":
                    logPath = Api.getNflogPath(getApplicationContext());
                    logPath = logPath + " " + QUEUE_NUM;
                    break;
            }
        } else {
            Log.i(TAG, "Unable to start log service. LogTarget is empty or LogService is not enabled");
            stopSelf();
        }

        Log.i(TAG, "Starting Log Service: " + logPath + " for LogTarget: " + G.logTarget());
        handler = new Handler();
        Log.i(TAG, "rootSession " + rootSession != null ? "rootSession is not Null" : "Null rootSession");

        if (rootSession != null) {
            try {
                rootSession.kill();
                rootSession.close();
            } catch (Exception e) {
            }
        }
        rootSession = new Shell.Builder()
                .useSU()
                .setMinimalLogging(true)
                .setOnSTDOUTLineListener(new StreamGobbler.OnLineListener() {
                    @Override
                    public void onLine(String line) {
                        storeLogInfo(line, getApplicationContext());
                    }
                })

                .open(new Shell.OnCommandResultListener() {
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                        if (exitCode != 0) {
                            Log.e(TAG, "Can't start logservice shell: exitCode " + exitCode);
                            stopSelf();
                        } else {
                            Log.d(TAG, "logservice shell started");
                            rootSession.addCommand(logPath);
                        }
                    }
                });
    }

   /* public static void clearCirc() {
        synchronized (circular) {
            while (circular.size() > 0) {
                circular.remove();
            }
        }
    }*/

    private void storeLogInfo(String line, Context context) {
        if (G.enableLogService()) {
            //Log.d(TAG,line);
            if (line != null && line.trim().length() > 0) {
                if (line.contains("AFL")) {
                    LogInfo logInfo = LogInfo.parseLogs(line, context);
                    store(logInfo);
                    if (logInfo.uidString != null && logInfo.uidString.length() > 0) {
                        if (G.showLogToasts()) {
                            showToast(context, handler, logInfo.uidString, false);
                        }
                    }
                }
            }
        }
    }

    /*private static void storeData() {
        try {
            Log.i(TAG,"Updating logs to database");
            FlowManager.getDatabase(LogDatabase.class)
                    .executeTransaction(FastStoreModelTransaction
                            .insertBuilder(FlowManager.getModelAdapter(LogData.class))
                            .addAll(circular).build());
            clearCirc();
        } catch (Exception e) {
            Log.i(TAG, "Exception in storeData: " + e.getLocalizedMessage());
        }
    }*/

    private void store(final LogInfo logInfo) {
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
    }

    @Override
    public void onDestroy() {
        if (rootSession != null) {
            try {
                rootSession.kill();
                rootSession.close();
            } catch (Exception e) {
            }
        }
        Log.d(TAG, "Received request to kill logservice");
        super.onDestroy();
    }
}
