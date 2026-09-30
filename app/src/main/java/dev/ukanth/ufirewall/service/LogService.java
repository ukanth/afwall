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

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
import static dev.ukanth.ufirewall.util.G.ctx;

import android.app.AlarmManager;
import android.app.Notification;
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

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.TimeUnit;
import org.ocpsoft.prettytime.units.JustNow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceDetails;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.events.LogEvent;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Notifications;
import dev.ukanth.ufirewall.util.SystemUids;

public class LogService extends Service {

    public static final String TAG = "AFWall";
    public static String logPath;
    public static final int QUEUE_NUM = 40;

    public static final String ACTION_GRACEFUL_SHUTDOWN = "dev.ukanth.ufirewall.GRACEFUL_SHUTDOWN";
    public static final String ACTION_CHANGE_LOG_TARGET = "dev.ukanth.ufirewall.CHANGE_LOG_TARGET";
    public static final String EXTRA_NEW_LOG_TARGET = "new_log_target";

    private static final int LOG_FLUSH_BATCH_SIZE = 50;
    private static final long LOG_FLUSH_INTERVAL_MS = 5000L;
    private static final long HEALTH_CHECK_INTERVAL_MS = 60_000L;

    private static LogService instance;

    private  NotificationManager manager;

    private List<String> callbackList;
    private ExecutorService executorService;
    // Dedicated single-thread scheduled executor so log-line parsing/storage runs OFF the
    // main (UI) thread. Scheduled so it can also handle timed batch flushes.
    private ScheduledExecutorService logProcessExecutor;
    private volatile boolean isShuttingDown = false;

    private Shell logWatcherShell;

    /**
     * libsu delivers job results and output lines to the given executor from its own threads. If
     * that executor rejects a task (it was shut down while the job was still running, e.g. when
     * the log shell's su session died or the watcher was restarted), libsu throws on its thread
     * and the whole app crashes. Bound to the executor in use when the job was started, so
     * callbacks of a replaced/stopped watcher are dropped rather than run on its successor.
     */
    private static Executor nonRejecting(final Executor target) {
        return command -> runOrDrop(target, command);
    }

    private static void runOrDrop(Executor target, Runnable command) {
        if (target == null) {
            Log.d(TAG, "Log watcher executor is gone; dropping callback");
            return;
        }
        try {
            target.execute(command);
        } catch (RejectedExecutionException e) {
            Log.d(TAG, "Log watcher executor was shut down; dropping callback");
        }
    }

    // Log watcher restart backoff
    private static final long RESTART_BASE_DELAY_MS = 5_000;
    private static final long RESTART_MAX_DELAY_MS = 5 * 60_000;
    private static final long WATCHER_STABLE_MS = 60_000;
    private final java.util.concurrent.atomic.AtomicBoolean restartPending = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile long watcherStartedAt;
    private volatile int quickRestarts;

    // A watcher that exits with an error this soon after starting never worked (e.g. nflog
    // can't be executed on this Android version); tell the user instead of logging nothing.
    private static final long START_FAILURE_WINDOW_MS = 5_000;
    private static volatile String lastStartFailure;
    private volatile String lastWatcherOutput;
    private boolean startFailureNotified;

    /** reason the log watcher could not start, or null if it is working / unknown */
    public static String getLastStartFailure() {
        return lastStartFailure;
    }

    private void reportStartFailure(String command, int exitCode) {
        String output = lastWatcherOutput;
        String reason = output != null ? output : command + " exited with code " + exitCode;
        lastStartFailure = reason;
        Log.e(TAG, "Log watcher could not start: " + reason);
        if (startFailureNotified) {
            return;
        }
        startFailureNotified = true;
        Context appCtx = getApplicationContext();
        Intent prefs = new Intent(appCtx, dev.ukanth.ufirewall.preferences.PreferencesActivity.class)
                .putExtra(android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                        dev.ukanth.ufirewall.preferences.LogPreferenceFragment.class.getName());
        Api.showNotification(appCtx, Api.LOG_WATCHER_FAILED_NOTIFICATION_ID,
                appCtx.getString(R.string.log_watcher_failed_title),
                appCtx.getString(R.string.log_watcher_failed_text, reason), prefs);
    }

    // Batching: accumulated entries flushed periodically or when the batch is full.
    // Only touched on logProcessExecutor — no lock needed.
    private final List<LogData> pendingLogs = new ArrayList<>();
    private ScheduledFuture<?> scheduledFlush;

    // Periodic health check handler (runs on main looper; does no DB work).
    private Handler healthHandler;
    private Runnable healthCheck;

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
        super.onCreate();
        instance = this;
    }

    public static boolean isInstanceRunning() {
        return instance != null;
    }

    public static void ensureRunning(Context ctx) {
        if (!G.enableLogService()) return;
        if (!isInstanceRunning()) {
            // LogService calls startForeground() in onCreate on 8+, so start it as a foreground
            // service; a plain startService() is refused while the app is in the background (boot).
            try {
                androidx.core.content.ContextCompat.startForegroundService(ctx, new Intent(ctx, LogService.class));
            } catch (Exception e) {
                Log.e(TAG, "Unable to start log service", e);
            }
        }
    }

    private boolean isWatcherHealthy() {
        return logWatcherShell != null && logWatcherShell.isAlive()
                && executorService != null && !executorService.isShutdown()
                && logProcessExecutor != null && !logProcessExecutor.isShutdown();
    }

    private void closeLogWatcher() {
        if (logWatcherShell != null) {
            try {
                logWatcherShell.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing log watcher shell: " + e.getMessage());
            }
            logWatcherShell = null;
        }
    }

    private void scheduleHealthCheck() {
        if (healthHandler == null) {
            healthHandler = new Handler(Looper.getMainLooper());
        }
        if (healthCheck == null) {
            healthCheck = new Runnable() {
                @Override
                public void run() {
                    if (!isShuttingDown && !isWatcherHealthy()) {
                        Log.w(TAG, "Health check: watcher unhealthy, restarting");
                        initiateLogWatcher(logPath);
                    }
                    if (healthHandler != null) {
                        healthHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
                    }
                }
            };
        }
        healthHandler.removeCallbacks(healthCheck);
        healthHandler.postDelayed(healthCheck, HEALTH_CHECK_INTERVAL_MS);
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
            String log = G.logTarget();
            if (log != null) {
                log = log.trim();
                if(log.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Please select log target first", Toast.LENGTH_LONG).show();
                    return;
                }
                String nextLogPath;
                switch (log) {
                    case "LOG":
                        nextLogPath = getBestLogCommand();
                        if (nextLogPath == null) {
                            Log.e(TAG, "No suitable log reading method available");
                            return;
                        }
                        break;
                    case "NFLOG":
                        nextLogPath = Api.getEnhancedNflogCommand(getApplicationContext(), QUEUE_NUM);
                        if (nextLogPath == null) {
                            Log.e(TAG, "NFLOG binary not available, cannot start logging service");
                            return;
                        }
                        break;
                    default:
                        nextLogPath = null;
                }
                if (nextLogPath == null) return;

                // Skip re-init if already watching the same path and shell is healthy.
                if (isWatcherHealthy() && nextLogPath.equals(logPath)) {
                    Log.i(TAG, "Log watcher already running for: " + logPath);
                    return;
                }

                logPath = nextLogPath;
                Log.i(TAG, "Starting Log Service: " + logPath + " for LogTarget: " + G.logTarget());
                if (logProcessExecutor == null || logProcessExecutor.isShutdown() || logProcessExecutor.isTerminated()) {
                    logProcessExecutor = Executors.newSingleThreadScheduledExecutor();
                }
                // Pass an Executor so libsu delivers onAddElement off the main thread.
                callbackList = new CallbackList<String>(nonRejecting(logProcessExecutor)) {
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
                            lastStartFailure = null; // logging works
                            storeLogInfo(line, getApplicationContext());
                        } else if (!line.trim().isEmpty()) {
                            // stderr is merged in: keep the last message in case the watcher dies
                            String trimmed = line.trim();
                            lastWatcherOutput = trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
                        }
                    }
                };
                initiateLogWatcher(logPath);
                scheduleHealthCheck();
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
        // Several triggers (suspend/resume lines, watcher exit) can fire together; keep one restart
        if (!restartPending.compareAndSet(false, true)) {
            return;
        }

        // A watcher that keeps exiting right away (unsupported command, no root) would otherwise be
        // restarted every 5s forever; back off exponentially until it stays up for a while.
        long ranFor = SystemClock.elapsedRealtime() - watcherStartedAt;
        if (ranFor >= WATCHER_STABLE_MS) {
            quickRestarts = 0;
        } else {
            quickRestarts = Math.min(quickRestarts + 1, 6);
        }
        final long delay = Math.min(RESTART_BASE_DELAY_MS << quickRestarts, RESTART_MAX_DELAY_MS);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            restartPending.set(false);
            if (G.enableLogService() && !isShuttingDown) {
                Log.i(G.TAG, "Restarting log watcher after " + delay + "ms");
                cleanupTempFiles();
                initiateLogWatcher(logPath);
            }
        }, delay);
    }

    /**
     * Clean up temporary files used by log watchers
     */
    private void cleanupTempFiles() {
        try {
            // async: this runs on the main thread
            Shell.cmd("rm -f /tmp/afwall_lastline").submit();
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
        // the status notification shows "log monitoring"; FirewallService owns it
        FirewallService.setLogServiceActive(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // LogService MUST call startForeground() within 5 seconds on Android 8+: share the
            // status notification (same id and content as FirewallService's)
            Notification notification = Notifications.buildStatus(ctx, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(Notifications.ID_STATUS, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notifications.ID_STATUS, notification);
            }
            Log.i(TAG, "LogService started as foreground with the shared status notification");
        }
        // FirewallService keeps the status notification up to date
        Notifications.refreshStatus(ctx);
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
            if (logWatcherShell != null && !logWatcherShell.isAlive()) {
                // a dead shell can't run new jobs; recreate it
                closeLogWatcher();
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
            watcherStartedAt = SystemClock.elapsedRealtime();
            final long startedAt = watcherStartedAt;
            lastWatcherOutput = null;
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
                    .submit(nonRejecting(executorService), out -> {
                        try {
                            Log.i(TAG, "Log watcher finished with code: " + out.getCode());
                            if (!isShuttingDown && out.getCode() != 0 && out.getCode() != 130
                                    && SystemClock.elapsedRealtime() - startedAt < START_FAILURE_WINDOW_MS) {
                                reportStartFailure(logCommand, out.getCode());
                            }
                            
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
        
        // Prefer polling dmesg: reading /proc/kmsg consumes the kernel messages, so logd and other
        // readers would miss them. /proc/kmsg is only used when dmesg is not there at all.
        String fallbackCommand;
        if (isCommandAvailable("dmesg")) {
            Log.i(TAG, "Attempting fallback to polling dmesg");
            fallbackCommand = "while true; do dmesg | grep '{AFL}' | tail -n +$(( $(wc -l < /tmp/afwall_lastline 2>/dev/null || echo 0) + 1 )); dmesg | wc -l > /tmp/afwall_lastline; sleep 1; done";
        } else {
            Log.i(TAG, "Attempting fallback to basic /proc/kmsg reading");
            fallbackCommand = "cat /proc/kmsg | grep --line-buffered '{AFL}'";
        }
        
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
        if(G.enableLogService() && logWatcherShell != null && logWatcherShell.isAlive() && !isShuttingDown) {
            watcherStartedAt = SystemClock.elapsedRealtime();
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
                    .submit(nonRejecting(executorService), out -> {
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
        
        // Rules are stored in G.pPrefs as pipe-separated UIDs: "1000|1005|..."
        String selected = netType == ConnectivityManager.TYPE_WIFI
                ? G.pPrefs.getString(Api.PREF_WIFI_PKG_UIDS, "")
                : G.pPrefs.getString(Api.PREF_3G_PKG_UIDS, "");
        String list = "|" + selected + "|";
        boolean listed = list.contains("|" + info.uid + "|") || list.contains("|" + Api.SPECIAL_UID_ANY + "|");

        // The list holds the allowed apps in whitelist mode but the blocked apps in blacklist
        // mode; treating it as "allowed" in both modes hid every block log in blacklist mode.
        boolean whitelist = Api.MODE_WHITELIST.equals(G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST));
        boolean allowedOnActiveInterface = whitelist == listed;

        // If the app IS allowed on the active interface, suppress this block log.
        // The block must be from an inactive/secondary interface (3G probe, VPN, tether, etc.)
        return allowedOnActiveInterface;
    }

    private void storeLogInfo(String line, Context context) {
        try {

            LogEvent event = new LogEvent(LogInfo.parseLogs(line, context, "{AFL}", 0), context);
            if(event.logInfo != null) {
                // a blocked system UID without an entry in the app list gets one
                SystemUids.seenInLog(context, event.logInfo.uid, event.logInfo.appName);
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

    private void showNotification(LogInfo logInfo) {
        // no notifications for packets without an app (kernel entry, formerly "unknown"), as before
        if (G.enableLogService() && G.notifyBlocked() && logInfo.uid != -100
                && logInfo.uid != Api.SPECIAL_UID_KERNEL && G.canShow(logInfo.uid)) {
            // collected and rate limited (see Notifications.blocked)
            Notifications.blocked(ctx, logInfo);
        }
    }



    // --- Batching ---

    // Called on logProcessExecutor — no lock needed (single-threaded executor).
    private void enqueueLog(LogData data) {
        pendingLogs.add(data);
        if (pendingLogs.size() >= LOG_FLUSH_BATCH_SIZE) {
            flushPendingLogs();
        } else {
            scheduleLogFlush();
        }
    }

    private void scheduleLogFlush() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
        }
        if (logProcessExecutor != null && !logProcessExecutor.isShutdown()) {
            scheduledFlush = logProcessExecutor.schedule(this::flushPendingLogs,
                    LOG_FLUSH_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private void flushPendingLogs() {
        if (pendingLogs.isEmpty()) return;
        final List<LogData> batch = new ArrayList<>(pendingLogs);
        pendingLogs.clear();
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
            scheduledFlush = null;
        }
        FlowManager.getDatabase(LogDatabase.class)
                .beginTransactionAsync(dw -> { for (LogData d : batch) d.save(dw); })
                .build().execute();
    }

    // --- Store ---

    private void store(final LogInfo logInfo, Context context) {
        store(logInfo, context, false);
    }

    private void store(final LogInfo logInfo, Context context, boolean isRetry) {
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
                data.setType(0);

                // Resolve hostname asynchronously. If DNS returns before the batch flushes,
                // hostname is included for free. If it returns after, the async save below
                // updates the already-saved record via its primary key.
                if (G.showHost() && logInfo.dst != null && !logInfo.dst.isEmpty()) {
                    final String dstIp = logInfo.dst;
                    final LogData dataRef = data;
                    new Thread(() -> {
                        try {
                            String hostname = java.net.InetAddress.getByName(dstIp).getHostName();
                            if (hostname != null && !hostname.equals(dstIp)) {
                                dataRef.setHostname(hostname);
                                FlowManager.getDatabase(LogDatabase.class)
                                    .beginTransactionAsync(dw -> dataRef.save(dw))
                                    .build().execute();
                            }
                        } catch (Exception e) {
                            // DNS resolution failed; hostname stays empty
                        }
                    }, "LogService-DNS-" + dstIp.hashCode()).start();
                }

                enqueueLog(data);
            }
        } catch (IllegalStateException e) {
            if (!isRetry && e.getMessage() != null && e.getMessage().contains("connection pool has been closed")) {
                try {
                    FlowManager.init(new FlowConfig.Builder(context).build());
                    store(logInfo, context, true);
                } catch (Exception de) {
                    Log.e(TAG, "Exception while saving log data (retry):" + de.getLocalizedMessage(), de);
                }
            }
            Log.e(TAG, "Exception while saving log data:" + e.getLocalizedMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Exception while saving log data:" + e.getLocalizedMessage(),e);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;

        // Stop health checks first so they don't restart the watcher mid-shutdown.
        if (healthHandler != null) {
            healthHandler.removeCallbacks(healthCheck);
            healthHandler = null;
        }

        // Set shutdown flag to prevent new tasks from starting.
        isShuttingDown = true;

        // Close log watcher shell first to stop generating new tasks.
        closeLogWatcher();

        // Flush any pending log entries, then drain the executor.
        if (logProcessExecutor != null && !logProcessExecutor.isShutdown()) {
            logProcessExecutor.execute(this::flushPendingLogs);
            logProcessExecutor.shutdown();
        }
        logProcessExecutor = null;

        // Shutdown the shell-submission executor.
        if(executorService != null) {
            try {
                executorService.shutdown();
                if (!executorService.awaitTermination(2000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "ExecutorService did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();
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

        // Update FirewallService notification if it's running.
        if (FirewallService.isInstanceRunning()) {
            FirewallService.setLogServiceActive(false);
            Log.i(TAG, "Notified FirewallService that log monitoring stopped");
        } else {
            try {
                stopForeground(true);
                Log.i(TAG, "Stopped foreground service");
            } catch (Exception e) {
                Log.w(TAG, "Error stopping foreground service: " + e.getMessage());
            }
        }

        cleanupTempFiles();

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Restart service if log service is still enabled
        if (G.enableLogService()) {
            Intent intent = new Intent(getApplicationContext(), LogService.class);
            // Must be a foreground-service start on 8+ (a background start from an alarm is refused),
            // and the trigger time must use the same clock as the alarm type.
            PendingIntent pendingIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? PendingIntent.getForegroundService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                    : PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 5000, pendingIntent);
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
