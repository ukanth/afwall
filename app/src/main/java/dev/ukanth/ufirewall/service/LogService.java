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
import android.net.ConnectivityManager;
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
import com.topjohnwu.superuser.NoShellException;
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
import dev.ukanth.ufirewall.InterfaceDetails;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.events.LogEvent;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.util.G;

public class LogService extends Service {

    public static final String TAG = "AFWall";
    public static String logPath;
    public static final int QUEUE_NUM = 40;
    
    public static final String ACTION_GRACEFUL_SHUTDOWN = "dev.ukanth.ufirewall.GRACEFUL_SHUTDOWN";
    public static final String ACTION_CHANGE_LOG_TARGET = "dev.ukanth.ufirewall.CHANGE_LOG_TARGET";
    public static final String EXTRA_NEW_LOG_TARGET = "new_log_target";

    private String NOTIFICATION_CHANNEL_ID = "firewall.logservice";


    private  NotificationManager manager;
    private  NotificationCompat.Builder notificationBuilder;

    private List<String> callbackList;
    private ExecutorService executorService;
    private volatile boolean isShuttingDown = false;

    private Shell logWatcherShell; // Additional shell for long running log-watcher process

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_GRACEFUL_SHUTDOWN.equals(intent.getAction())) {
                Log.i(TAG, "Received graceful shutdown request");
                initiateGracefulShutdown();
                return START_NOT_STICKY;
            } else if (ACTION_CHANGE_LOG_TARGET.equals(intent.getAction())) {
                String newLogTarget = intent.getStringExtra(EXTRA_NEW_LOG_TARGET);
                Log.i(TAG, "Received log target change request to: " + newLogTarget);
                changeLogTarget(newLogTarget);
                return START_STICKY;
            }
        }
        
        // Reset shutdown flag when service starts normally
        isShuttingDown = false;
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
            return "dmesg --follow | grep '{AFL}'";
        }
        
        // Method 2: Try dmesg with tail simulation (good fallback)
        if (isCommandAvailable("dmesg") && isCommandAvailable("tail")) {
            return "while true; do dmesg | grep '{AFL}' | tail -n +$(( $(wc -l < /tmp/afwall_lastline 2>/dev/null || echo 0) + 1 )); dmesg | wc -l > /tmp/afwall_lastline; sleep 1; done";
        }
        
        // Method 3: Try logcat kernel logs (Android 7+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isCommandAvailable("logcat")) {
            return "logcat -s kernel:* | grep '{AFL}'";
        }
        
        // Method 4: Try journalctl if available (some Android variants)
        if (isCommandAvailable("journalctl")) {
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
                            restartWatcher(logPath);
                        }
                        
                        // Handle log rotation or kernel ring buffer wrap
                        if(line.contains("log_buf_len") || line.contains("Buffer wrap")) {
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
        if (isShuttingDown) {
            return;
        }
        
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (G.enableLogService() && !isShuttingDown) {
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
    
    /**
     * Initiate graceful shutdown of the log service
     */
    private void initiateGracefulShutdown() {
        Log.i(TAG, "Starting graceful shutdown process");
        
        // Set shutdown flag to prevent new tasks
        isShuttingDown = true;
        
        // Stop in background thread to avoid blocking the main thread
        new Thread(() -> {
            try {
                // Close shell first to stop generating new tasks
                if (logWatcherShell != null) {
                    try {
                        logWatcherShell.close();
                        Log.i(TAG, "Log watcher shell closed");
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing log watcher shell during graceful shutdown: " + e.getMessage());
                    }
                    logWatcherShell = null;
                }
                
                // Give executor service time to finish current tasks
                if (executorService != null) {
                    try {
                        Log.i(TAG, "Shutting down executor service...");
                        executorService.shutdown(); // Don't accept new tasks
                        if (!executorService.awaitTermination(5000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "Executor service didn't terminate within 5s, forcing shutdown");
                            executorService.shutdownNow();
                            // Wait a bit more for tasks to respond to being cancelled
                            if (!executorService.awaitTermination(2000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                                Log.w(TAG, "Executor service still didn't terminate after force shutdown");
                            }
                        }
                        Log.i(TAG, "Executor service shutdown complete");
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while shutting down executor service");
                        executorService.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Clean up and stop service
                cleanupTempFiles();
                Log.i(TAG, "Graceful shutdown complete, stopping service");
                
                // Stop the service on the main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> stopSelf());
                
            } catch (Exception e) {
                Log.e(TAG, "Error during graceful shutdown: " + e.getMessage(), e);
                // Fallback to immediate stop
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> stopSelf());
            }
        }, "LogService-GracefulShutdown").start();
    }
    
    /**
     * Change the log target without restarting the service
     */
    private void changeLogTarget(String newLogTarget) {
        if (newLogTarget == null || newLogTarget.trim().isEmpty()) {
            Log.w(TAG, "Invalid log target provided, ignoring change request");
            return;
        }
        
        String currentLogTarget = G.logTarget();
        if (newLogTarget.equals(currentLogTarget)) {
            Log.i(TAG, "New log target is same as current, no change needed");
            return;
        }
        
        Log.i(TAG, "Changing log target from " + currentLogTarget + " to " + newLogTarget);
        
        // Stop current log watcher gracefully in background thread
        new Thread(() -> {
            try {
                // Set shutdown flag temporarily to prevent restarts
                isShuttingDown = true;
                
                // Close current shell and executor
                if (logWatcherShell != null) {
                    try {
                        logWatcherShell.close();
                        Log.i(TAG, "Closed existing log watcher shell");
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing existing shell: " + e.getMessage());
                    }
                    logWatcherShell = null;
                }
                
                if (executorService != null) {
                    try {
                        executorService.shutdown();
                        if (!executorService.awaitTermination(3000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            Log.w(TAG, "Executor didn't terminate gracefully, forcing shutdown");
                            executorService.shutdownNow();
                        }
                        Log.i(TAG, "Executor service shut down successfully");
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while shutting down executor");
                        executorService.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                    executorService = null;
                }
                
                // Wait a moment for cleanup
                Thread.sleep(1000);
                
                // Update log target in preferences
                G.logTarget(newLogTarget);
                
                // Reset shutdown flag
                isShuttingDown = false;
                
                // Restart log service with new target on main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    Log.i(TAG, "Restarting log service with new target: " + newLogTarget);
                    startLogService();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during log target change: " + e.getMessage(), e);
                isShuttingDown = false; // Reset flag on error
            }
        }, "LogService-ChangeTarget").start();
    }

    private void createNotification() {
        manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(109);

        // On Android 8+, LogService must piggyback on FirewallService's notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Set the flag so FirewallService knows to include log monitoring status
            FirewallService.setLogServiceActive(true);
            
            // LogService MUST call startForeground() within 5 seconds on Android 8+
            // Create a notification using FirewallService's channel and style
            String SHARED_CHANNEL_ID = "firewall.service";
            
            // Ensure the channel exists
            NotificationChannel notificationChannel = new NotificationChannel(SHARED_CHANNEL_ID, ctx.getString(R.string.firewall_service), NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (G.getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            } else {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_LOW);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(true);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
            
            // Build notification in FirewallService style (opens MainActivity, not LogActivity)
            Intent appIntent = new Intent(ctx, MainActivity.class);
            appIntent.setAction(Intent.ACTION_MAIN);
            appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
            
            String notificationText = Api.isEnabled(ctx) ? ctx.getString(R.string.active) : ctx.getString(R.string.inactive);
            notificationText += " • " + ctx.getString(R.string.log_monitoring);
            
            int icon = Api.isEnabled(ctx) ? R.drawable.notification : R.drawable.notification_error;
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, SHARED_CHANNEL_ID);
            builder.setContentIntent(notifyPendingIntent)
                    .setSmallIcon(icon)
                    .setContentTitle(ctx.getString(R.string.app_name))
                    .setContentText(notificationText)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE);
            
            Notification notification = builder.build();
            startForeground(1, notification);
            Log.i(TAG, "LogService started as foreground with shared notification ID 1");
            
            if (FirewallService.isInstanceRunning()) {
                // FirewallService is already running, it will refresh and take over the notification
                FirewallService.refreshNotification();
                Log.i(TAG, "FirewallService notification refreshed to include log status");
            } else {
                // Start FirewallService so it can take over notification management
                Log.i(TAG, "Starting FirewallService to manage shared notification");
                Intent intent = new Intent(ctx, FirewallService.class);
                ctx.startForegroundService(intent);
            }
        } else {
            // Pre-Android 8: Create notification channel for individual log events
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
            
            // Pre-Android 8: respect user preference for showing notifications
            if (G.activeNotification()) {
                Intent appIntent = new Intent(ctx, LogActivity.class);
                appIntent.setAction(Intent.ACTION_MAIN);
                appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
                notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
                notificationBuilder.setContentIntent(notifyPendingIntent)
                        .setSmallIcon(R.drawable.notification)
                        .setContentTitle(ctx.getString(R.string.firewall_log_notify))
                        .setContentText(ctx.getString(R.string.log_service_running))
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE);
                
                Notification notification = notificationBuilder.build();
                assert manager != null;
                manager.notify(1, notification);
                Log.i(TAG, "LogService notification shown (user preference enabled)");
            } else {
                Log.i(TAG, "LogService notification hidden (user preference disabled)");
            }
        }
    }


    private void initiateLogWatcher(String logCommand) {
        // Clear/remove existing tasks
        if(executorService != null) {
            executorService.shutdownNow();
        }

        //make sure it's enabled first
        if(G.enableLogService() && !isShuttingDown) {
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
            }
            if (logWatcherShell == null) {
                try {
                    logWatcherShell = Shell.Builder.create()
                        .setFlags(Shell.FLAG_REDIRECT_STDERR)
                        .setTimeout(10) // 10 second timeout for shell creation
                        .build();
                } catch (NoShellException e) {
                    Log.e(TAG, "Failed to create root shell for log watcher", e);
                    return;
                }
            }
            
            Log.i(TAG, "Starting log watcher with command: " + logCommand);
            try {
                if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
                    Log.w(TAG, "ExecutorService is not available, recreating...");
                    if (executorService != null) {
                        executorService.shutdownNow();
                    }
                    executorService = Executors.newCachedThreadPool();
                }
                
                logWatcherShell.newJob()
                    .add(logCommand)
                    .to(callbackList)
                    .submit(executorService, out -> {
                        try {
                            Log.i(TAG, "Log watcher finished with code: " + out.getCode());
                            
                            // Don't restart if service is shutting down
                            if (isShuttingDown) {
                                Log.i(TAG, "Service is shutting down, not restarting log watcher");
                                return;
                            }
                            
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
                        } catch (Exception e) {
                            if (e.getMessage() != null && e.getMessage().contains("RejectedExecutionException")) {
                                Log.w(TAG, "Caught SuperUser library RejectedExecutionException during app shutdown, ignoring to prevent crash");
                            } else {
                                Log.e(TAG, "Error in log watcher completion callback: " + e.getMessage(), e);
                            }
                        }
                    });
            } catch(Exception e) {
                Log.e(TAG, "Unable to start log service: " + e.getMessage(), e);
                if (e.getMessage() != null && (e.getMessage().contains("rejected") || e.getMessage().contains("terminated"))) {
                    Log.w(TAG, "ExecutorService rejected task, recreating executor and retrying...");
                    try {
                        if (executorService != null) {
                            executorService.shutdownNow();
                        }
                        executorService = Executors.newCachedThreadPool();
                        initiateLogWatcher(logPath);
                        return;
                    } catch (Exception retryException) {
                        Log.e(TAG, "Retry also failed: " + retryException.getMessage(), retryException);
                    }
                }
                tryFallbackLogMethod();
            }
        }
    }
    
    /**
     * Try a fallback log reading method if the primary method fails
     */
    private void tryFallbackLogMethod() {
        if (isShuttingDown) {
            return;
        }
        
        Log.i(TAG, "Attempting fallback to basic /proc/kmsg reading");
        String fallbackCommand = "cat /proc/kmsg | grep --line-buffered '{AFL}'";
        
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (G.enableLogService() && !isShuttingDown) {
                Log.i(TAG, "Starting fallback log watcher");
                initiateLogWatcherWithCommand(fallbackCommand);
            }
        }, 3000);
    }
    
    /**
     * Initiate log watcher with a specific command (used for fallback)
     */
    private void initiateLogWatcherWithCommand(String logCommand) {
        if(G.enableLogService() && logWatcherShell != null && !isShuttingDown) {
            try {
                if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
                    Log.w(TAG, "ExecutorService is not available for fallback, recreating...");
                    if (executorService != null) {
                        executorService.shutdownNow();
                    }
                    executorService = Executors.newCachedThreadPool();
                }
                
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
                if (e.getMessage() != null && (e.getMessage().contains("rejected") || e.getMessage().contains("terminated"))) {
                    Log.w(TAG, "ExecutorService rejected fallback task, service may be shutting down");
                }
            }
        }
    }



    /**
     * Determine if a log entry should be suppressed because the app is allowed
     * on the currently active network interface.
     * 
     * When an app is allowed on WiFi (the active connection), it can still generate
     * spurious block logs from:
     *   - Cross-interface probes (trying 3G while on WiFi)
     *   - VPN interface blocks (tun+, ppp+)
     *   - Tethering chains
     *   - INPUT chain (empty OUT= field)
     * 
     * All of these are noise because the app IS working on the active interface.
     * If the app is in the allowed list for the currently active network type,
     * suppress the log entry entirely.
     */
    private boolean shouldSuppressLog(LogInfo info, Context ctx) {
        // Only suppress regular app traffic, not kernel or special IDs
        if (info.uid < 0) return false;

        // Get current active network state
        InterfaceDetails details = InterfaceTracker.getCurrentCfg(ctx, false);
        if (details == null || !details.netEnabled) {
            return false;
        }

        int netType = details.netType;
        if (netType != ConnectivityManager.TYPE_WIFI && netType != ConnectivityManager.TYPE_MOBILE) {
            return false; // Unknown network state, don't suppress
        }
        
        // Check if the app is allowed on the currently active interface
        // Rules are stored in G.pPrefs as pipe-separated UIDs: "|1000|1005|..."
        String uidStr = "|" + info.uid + "|";
        String allowedUids;
        
        if (netType == ConnectivityManager.TYPE_WIFI) {
            allowedUids = G.pPrefs.getString(Api.PREF_WIFI_PKG_UIDS, "");
        } else {
            allowedUids = G.pPrefs.getString(Api.PREF_3G_PKG_UIDS, "");
        }
        
        // If the app IS allowed on the active interface, suppress this block log.
        // The block must be from an inactive/secondary interface (3G probe, VPN, tether, etc.)
        if ((allowedUids + "|").contains(uidStr)) {
            return true;
        }
        
        return false;
    }

    private void storeLogInfo(String line, Context context) {
        try {

            LogEvent event = new LogEvent(LogInfo.parseLogs(line, context, "{AFL}", 0), context);
            if(event.logInfo != null) {
                // Filter multicast/broadcast traffic to reduce log noise
                // IPv4 Multicast: 224.0.0.0/4 (224.0.0.0 - 239.255.255.255)
                // Broadcast: 255.255.255.255
                // IPv6 Multicast: ff00::/8
                String dst = event.logInfo.dst;
                if (dst != null) {
                    if (dst.equals("255.255.255.255") || 
                        dst.startsWith("224.") || dst.startsWith("239.") || 
                        dst.toLowerCase().startsWith("ff")) {
                        // Skip notification and storage for multicast/broadcast garbage
                        return;
                    }
                }
                
                // Smart Filter: Suppress logs for apps allowed on active interface but blocked on inactive one
                if (shouldSuppressLog(event.logInfo, context)) {
                    return;
                }

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
        if(G.enableLogService() && G.canShow(logInfo.uid) && logInfo.uid != -100) {
            // Create a separate notification for log events (disposable notifications)
            // These use a different channel and notification ID than the foreground service
            NotificationCompat.Builder logEventBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
            
            Intent appIntent = new Intent(ctx, LogActivity.class);
            appIntent.setAction(Intent.ACTION_MAIN);
            appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
            
            logEventBuilder.setContentIntent(notifyPendingIntent)
                    .setContentTitle(ctx.getString(R.string.firewall_log_notify))
                    .setContentText(logInfo.uidString)
                    .setSmallIcon(R.drawable.ic_block_black_24dp)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            
            manager.notify(109, logEventBuilder.build());
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
        
        // Set shutdown flag to prevent new tasks from starting
        isShuttingDown = true;
        
        // Close log watcher shell first to stop generating new tasks
        if(logWatcherShell != null) {
            try {
                logWatcherShell.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing log watcher shell: " + e.getMessage());
            }
            logWatcherShell = null;
        }
        
        // Shutdown executor service gracefully
        if(executorService != null) {
            try {
                executorService.shutdown(); // Try graceful shutdown first
                if (!executorService.awaitTermination(2000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "ExecutorService did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();
                    // Wait a bit more for tasks to respond to being cancelled
                    if (!executorService.awaitTermination(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        Log.w(TAG, "ExecutorService did not terminate after force shutdown");
                    }
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while shutting down ExecutorService");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        executorService = null;
        
        // Update FirewallService notification if it's running
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && FirewallService.isInstanceRunning()) {
            FirewallService.setLogServiceActive(false);
            Log.i(TAG, "Notified FirewallService that log monitoring stopped");
        } else {
            // Stop our own foreground service
            try {
                stopForeground(true);
                Log.i(TAG, "Stopped foreground service");
            } catch (Exception e) {
                Log.w(TAG, "Error stopping foreground service: " + e.getMessage());
            }
        }
        
        // Clean up temporary files
        cleanupTempFiles();
        
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Restart service if log service is still enabled
        if (G.enableLogService()) {
            Intent intent = new Intent(getApplicationContext(), LogService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_MUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime() + 5000, pendingIntent);
        }
        
        // Clean up resources gracefully
        if(logWatcherShell != null && !logWatcherShell.isAlive()) {
            try {
                logWatcherShell.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing shell in onTaskRemoved: " + e.getMessage());
            }
            logWatcherShell = null;
        }
        
        if(executorService != null) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        executorService = null;
        
        cleanupTempFiles();
    }
}
