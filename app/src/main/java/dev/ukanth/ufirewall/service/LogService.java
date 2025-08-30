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

import static dev.ukanth.ufirewall.util.G.ctx;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.TimeUnit;
import org.ocpsoft.prettytime.units.JustNow;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.events.LogEvent;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.util.G;

public class LogService extends Service {

    public static final String TAG = "AFWall";
    public static String logPath;
    public static final int QUEUE_NUM = 40;

    private String NOTIFICATION_CHANNEL_ID = "firewall.logservice";


    private  NotificationManager manager;
    private  NotificationCompat.Builder notificationBuilder;

    private List<String> callbackList;
    private ExecutorService executorService;

    private Shell logWatcherShell; // Additional shell for long running log-watcher process

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLogService();
        return START_STICKY;
    }


    @Override
    public void onCreate() {
        startLogService();
    }


    /**
     * Get the best available command for reading kernel logs with iptables messages
     * Tries multiple methods in order of preference for efficiency and compatibility
     * @return command string or null if no suitable method is available
     */
    private String getBestLogCommand() {
        // Method 1: Try dmesg with follow and grep (most efficient for iptables logs)
        if (isCommandAvailable("dmesg --follow")) {
            Log.i(TAG, "Using dmesg --follow with grep filtering");
            return "dmesg --follow | grep '{AFL}'";
        }
        
        // Method 2: Try dmesg with tail simulation (good fallback)
        if (isCommandAvailable("dmesg") && isCommandAvailable("tail")) {
            Log.i(TAG, "Using dmesg with tail simulation");
            return "while true; do dmesg | grep '{AFL}' | tail -n +$(( $(wc -l < /tmp/afwall_lastline 2>/dev/null || echo 0) + 1 )); dmesg | wc -l > /tmp/afwall_lastline; sleep 1; done";
        }
        
        // Method 3: Try logcat kernel logs (Android 7+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isCommandAvailable("logcat")) {
            Log.i(TAG, "Using logcat for kernel logs");
            return "logcat -s kernel:* | grep '{AFL}'";
        }
        
        // Method 4: Try journalctl if available (some Android variants)
        if (isCommandAvailable("journalctl")) {
            Log.i(TAG, "Using journalctl for kernel logs");  
            return "journalctl -k -f | grep '{AFL}'";
        }
        
        // Method 5: Fall back to /proc/kmsg with improvements
        Log.w(TAG, "Falling back to /proc/kmsg - less efficient method");
        return "cat /proc/kmsg | grep --line-buffered '{AFL}'";
    }
    
    /**
     * Check if a command is available on the system
     * @param command the command to test
     * @return true if command is available
     */
    private boolean isCommandAvailable(String command) {
        try {
            String testCommand = command.split(" ")[0]; // Get the base command
            Shell.Result result = Shell.cmd("which " + testCommand + " || command -v " + testCommand).exec();
            return result.isSuccess() && !result.getOut().isEmpty();
        } catch (Exception e) {
            Log.d(TAG, "Failed to test command availability: " + command);
            return false;
        }
    }

    private void startLogService() {
        if (G.enableLogService()) {
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
                        logPath = getBestLogCommand();
                        if (logPath == null) {
                            Log.e(TAG, "No suitable log reading method available");
                            return;
                        }
                        break;
                    case "NFLOG":
                        logPath = Api.getEnhancedNflogCommand(getApplicationContext(), QUEUE_NUM);
                        if (logPath == null) {
                            Log.e(TAG, "NFLOG binary not available, cannot start logging service");
                            return;
                        }
                        break;
                }

                Log.i(TAG, "Starting Log Service: " + logPath + " for LogTarget: " + G.logTarget());
                callbackList = new CallbackList<String>() {
                    @Override
                    public void onAddElement(String line) {
                        // Handle device suspend/resume scenarios
                        if(line.contains("suspend exit") || line.contains("PM: suspend exit")) {
                            Log.d(TAG, "Device resumed from suspend, restarting log watcher");
                            restartWatcher(logPath);
                        }
                        
                        // Handle log rotation or kernel ring buffer wrap
                        if(line.contains("log_buf_len") || line.contains("Buffer wrap")) {
                            Log.d(TAG, "Kernel log buffer wrapped, restarting watcher");
                            restartWatcher(logPath);
                        }

                        // Process iptables/netfilter log entries
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
        }
    }

    private void restartWatcher(String logPath) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (G.enableLogService()) {
                Log.i(G.TAG, "Restarting log watcher after 5s");
                cleanupTempFiles();
                initiateLogWatcher(logPath);
            }
        }, 5000);
    }
    
    /**
     * Clean up temporary files used by log watchers
     */
    private void cleanupTempFiles() {
        try {
            Shell.cmd("rm -f /tmp/afwall_lastline").exec();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void createNotification() {
        manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(109);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, ctx.getString(R.string.firewall_log_notify), NotificationManager.IMPORTANCE_DEFAULT);
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

        PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);
    }

    private void initiateLogWatcher(String logCommand) {
        // Clear/remove existing tasks
        if(executorService != null) {
            executorService.shutdownNow();
        }

        //make sure it's enabled first
        if(G.enableLogService()) {
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
            }
            if (logWatcherShell == null) {
                logWatcherShell = Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(0) // No timeout for long-running log watcher
                    .build();
            }
            
            Log.i(TAG, "Starting log watcher with command: " + logCommand);
            try {
                logWatcherShell.newJob()
                    .add(logCommand)
                    .to(callbackList)
                    .submit(executorService, out -> {
                        Log.i(TAG, "Log watcher finished with code: " + out.getCode());
                        
                        // Handle different exit scenarios
                        if (out.getCode() == 0) {
                            // Normal termination, try restart after delay
                            Log.w(TAG, "Log watcher terminated normally, restarting...");
                            restartWatcher(logPath);
                        } else if (out.getCode() == 130) {
                            // SIGINT - likely manual termination
                            Log.i(TAG, "Log watcher interrupted (SIGINT)");
                        } else if (out.getCode() == 137) {
                            // SIGKILL - system killed the process
                            Log.w(TAG, "Log watcher killed by system, restarting...");
                            restartWatcher(logPath);
                        } else {
                            // Other error codes, try fallback method
                            Log.w(TAG, "Log watcher failed with code " + out.getCode() + ", trying fallback");
                            tryFallbackLogMethod();
                        }
                    });
            } catch(Exception e) {
                Log.e(TAG, "Unable to start log service: " + e.getMessage(), e);
                tryFallbackLogMethod();
            }
        }
    }
    
    /**
     * Try a fallback log reading method if the primary method fails
     */
    private void tryFallbackLogMethod() {
        Log.i(TAG, "Attempting fallback to basic /proc/kmsg reading");
        String fallbackCommand = "cat /proc/kmsg | grep --line-buffered '{AFL}'";
        
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (G.enableLogService()) {
                Log.i(TAG, "Starting fallback log watcher");
                initiateLogWatcherWithCommand(fallbackCommand);
            }
        }, 3000);
    }
    
    /**
     * Initiate log watcher with a specific command (used for fallback)
     */
    private void initiateLogWatcherWithCommand(String logCommand) {
        if(G.enableLogService() && logWatcherShell != null) {
            try {
                logWatcherShell.newJob()
                    .add(logCommand)
                    .to(callbackList)
                    .submit(executorService, out -> {
                        Log.i(TAG, "Fallback log watcher finished with code: " + out.getCode());
                        if (out.getCode() == 0) {
                            restartWatcher(logCommand);
                        }
                    });
            } catch(Exception e) {
                Log.e(TAG, "Fallback log service also failed: " + e.getMessage(), e);
            }
        }
    }


    private void storeLogInfo(String line, Context context) {
        try {

            LogEvent event = new LogEvent(LogInfo.parseLogs(line, context, "{AFL}", 0), context);
            if(event.logInfo != null) {
                store(event.logInfo, event.ctx);
                showNotification(event.logInfo);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }


    private void checkBatteryOptimize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Intent doze = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (Api.batteryOptimized(this) && getPackageManager().resolveActivity(doze, 0) != null) {
            }
        }
    }

    private static PrettyTime prettyTime;

    public static String pretty(Date date) {
        if (prettyTime == null) {
            prettyTime = new PrettyTime(new Locale(G.locale()));
            for (TimeUnit t : prettyTime.getUnits()) {
                if (t instanceof JustNow) {
                    prettyTime.removeUnit(t);
                    break;
                }
            }
        }
        prettyTime.setReference(date);
        return prettyTime.format(new Date(0));
    }

    @SuppressLint("RestrictedApi")
    private void showNotification(LogInfo logInfo) {
        if(G.enableLogService()) {
            manager.notify(109, notificationBuilder.setOngoing(false)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setContentText(logInfo.uidString)
                    .setSmallIcon(R.drawable.ic_block_black_24dp)
                    .setAutoCancel(true)
                    .build());
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
        
        // Shutdown executor service
        if(executorService != null) {
            executorService.shutdownNow();
        }
        executorService = null;
        
        // Close log watcher shell
        if(logWatcherShell != null) {
            try {
                logWatcherShell.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing log watcher shell: " + e.getMessage());
            }
            logWatcherShell = null;
        }
        
        // Clean up temporary files
        cleanupTempFiles();
        
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Log service task removed");

        // Restart service if log service is still enabled
        if (G.enableLogService()) {
            Intent intent = new Intent(getApplicationContext(), LogService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_MUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime() + 5000, pendingIntent);
        }
        
        // Clean up resources
        if(executorService != null) {
            executorService.shutdownNow();
        }
        executorService = null;
        
        if(logWatcherShell != null) {
            try {
                logWatcherShell.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing shell in onTaskRemoved: " + e.getMessage());
            }
            logWatcherShell = null;
        }
        
        cleanupTempFiles();
    }
}
