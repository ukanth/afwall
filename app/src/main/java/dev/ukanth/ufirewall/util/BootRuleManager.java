/**
 * Robust boot rule application manager to prevent race conditions
 * and ensure proper rule application during system startup.
 * 
 * Copyright (C) 2024 AFWall+ Team
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootCommand;

public class BootRuleManager {
    
    private static final String TAG = "AFWall.BootRuleManager";
    
    // Boot state management
    private static final AtomicBoolean isBootInProgress = new AtomicBoolean(false);
    private static final AtomicBoolean initialBootRulesApplied = new AtomicBoolean(false);
    private static final AtomicBoolean delayedBootRulesScheduled = new AtomicBoolean(false);
    
    // Handler for delayed rule application
    private static final AtomicReference<Runnable> delayedBootRunnable = new AtomicReference<>();
    private static final Handler bootHandler = new Handler(Looper.getMainLooper());
    
    // Synchronization for rule application
    private static final Object ruleApplicationLock = new Object();
    
    /**
     * Initialize boot rule application process
     * Should be called from OnBootReceiver
     */
    public static void initializeBootRuleApplication(Context context) {
        synchronized (ruleApplicationLock) {
            Log.i(TAG, "Initializing boot rule application");

            if (!shouldApplyBootRules(context)) {
                Log.i(TAG, "Firewall disabled or inactive at boot; skipping boot rule application");
                markBootComplete();
                return;
            }
            
            // Mark boot as in progress
            isBootInProgress.set(true);
            initialBootRulesApplied.set(false);
            delayedBootRulesScheduled.set(false);
            
            // Cancel any existing delayed rule application
            cancelDelayedBootRules();
            
            // Check and copy fix leak script if needed
            checkFixLeakScript(context);
            
            // Apply initial boot rules immediately
            applyInitialBootRules(context);
            
            // Schedule delayed boot rules if enabled
            if (G.startupDelay()) {
                scheduleDelayedBootRules(context);
            } else {
                // Mark boot as complete if no delay is configured
                markBootComplete();
            }
        }
    }
    
    /**
     * Check and copy fix leak script if needed (optimized - only when required)
     */
    private static void checkFixLeakScript(Context context) {
        try {
            if (G.initPath() != null && G.fixLeak()) {
                Log.d(TAG, "Checking fix leak script requirement");
                Api.checkAndCopyFixLeak(context, "afwallstart");
            } else {
                Log.d(TAG, "Fix leak script not required - skipping");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking fix leak script: " + e.getMessage());
        }
    }
    
    /**
     * Apply initial boot rules immediately
     */
    private static void applyInitialBootRules(Context context) {
        Log.i(TAG, "Applying initial boot rules");

        InterfaceTracker.getCurrentCfg(context, true);

        InterfaceTracker.applyBootRules(context, InterfaceTracker.BOOT_COMPLETED + "_INITIAL");
        initialBootRulesApplied.set(true);
        
        Log.i(TAG, "Initial boot rules applied");
    }
    
    /**
     * Schedule delayed boot rule application
     */
    private static void scheduleDelayedBootRules(Context context) {
        if (delayedBootRulesScheduled.compareAndSet(false, true)) {
            int delay = G.getCustomDelay();
            Log.i(TAG, "Scheduling delayed boot rules in " + delay + "ms");
            
            Runnable delayedRules = () -> {
                synchronized (ruleApplicationLock) {
                    if (isBootInProgress.get()) {
                        if (!shouldApplyBootRules(context)) {
                            Log.i(TAG, "Firewall disabled before delayed boot apply; skipping delayed rules");
                            markBootComplete();
                            return;
                        }
                        Log.i(TAG, "Applying delayed boot rules");
                        try {
                            // Force interface configuration refresh for delayed rules
                            InterfaceTracker.getCurrentCfg(context, true);
                            InterfaceTracker.applyBootRules(context, InterfaceTracker.BOOT_COMPLETED + "_DELAYED");
                            Log.i(TAG, "Delayed boot rules applied successfully");
                        } catch (Exception e) {
                            Log.e(TAG, "Error applying delayed boot rules: " + e.getMessage());
                        } finally {
                            markBootComplete();
                        }
                    } else {
                        Log.d(TAG, "Boot process already completed, skipping delayed rules");
                    }
                }
            };
            
            delayedBootRunnable.set(delayedRules);
            bootHandler.postDelayed(delayedRules, delay);
        }
    }
    
    /**
     * Cancel any scheduled delayed boot rule application
     */
    private static void cancelDelayedBootRules() {
        Runnable existingRunnable = delayedBootRunnable.getAndSet(null);
        if (existingRunnable != null) {
            bootHandler.removeCallbacks(existingRunnable);
            Log.d(TAG, "Cancelled existing delayed boot rules");
        }
        delayedBootRulesScheduled.set(false);
    }

    private static boolean shouldApplyBootRules(Context context) {
        // BootRuleManager calls applyBootRules directly, so keep the same enabled checks
        // that protect normal connectivity-change rule application.
        return Api.isEnabled(context) && G.activeRules();
    }
    
    /**
     * Mark boot process as complete
     */
    private static void markBootComplete() {
        Log.i(TAG, "Boot rule application process completed");
        isBootInProgress.set(false);
        delayedBootRulesScheduled.set(false);
        delayedBootRunnable.set(null);
    }
    
    /**
     * Handle network connectivity changes during boot
     * Returns true if the network change should be processed, false if it should be ignored
     */
    public static boolean shouldProcessNetworkChange(Context context, String reason) {
        if (!isBootInProgress.get()) {
            // Boot process is complete, allow normal network change processing
            return true;
        }
        
        // During boot process, be more selective about network changes
        if (!initialBootRulesApplied.get()) {
            // Initial boot rules haven't been applied yet, ignore network changes
            Log.d(TAG, "Ignoring network change (" + reason + ") - initial boot rules not yet applied");
            return false;
        }
        
        // If we have a delayed boot rule scheduled, we should be careful about network changes
        if (delayedBootRulesScheduled.get()) {
            Log.d(TAG, "Network change during boot delay period (" + reason + ") - allowing limited processing");
            // Allow processing but don't trigger a full rule reapplication
            // The delayed boot rules will handle the final state
            return true;
        }
        
        // Boot rules applied but no delay configured, allow network change processing
        return true;
    }
    
    /**
     * Force completion of boot rule application (for manual testing or emergency)
     */
    public static void forceCompleteBootRuleApplication() {
        synchronized (ruleApplicationLock) {
            Log.i(TAG, "Forcing completion of boot rule application");
            cancelDelayedBootRules();
            markBootComplete();
        }
    }
    
    /**
     * Check if boot rule application is currently in progress
     */
    public static boolean isBootInProgress() {
        return isBootInProgress.get();
    }
    
    /**
     * Get current boot state for debugging
     */
    public static String getBootState() {
        return String.format("Boot in progress: %s, Initial rules applied: %s, Delayed rules scheduled: %s",
                isBootInProgress.get(),
                initialBootRulesApplied.get(),
                delayedBootRulesScheduled.get());
    }
}
