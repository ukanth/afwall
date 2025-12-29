/**
 * Debounces rapid network connectivity changes to avoid excessive iptables rule applications.
 * <p>
 * When network changes occur rapidly (e.g., switching between WiFi and mobile data),
 * this class delays rule application until the network has been stable for a configurable
 * period. If new changes arrive before the delay expires, the pending job is cancelled
 * and rescheduled.
 * <p>
 * Copyright (C) 2025 Umakanthan Chandran
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */
package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.log.Log;

public class NetworkChangeDebouncer {
    private static final String TAG = "AFWall";
    
    // Default debounce delay in milliseconds
    private static final long DEFAULT_DEBOUNCE_DELAY_MS = 2000; // 2 seconds
    //Retry delay when rules are currently being applied
    private static final long RETRY_DELAY_MS = 500; // 500ms
    
    // Maximum retry attempts
    private static final int MAX_RETRY_ATTEMPTS = 10;
    
    // 
    // Handler for posting delayed tasks
    private static final Handler handler = new Handler(Looper.getMainLooper());
    
    // Currently scheduled runnable (if any)
    private static final AtomicReference<Runnable> pendingRunnable = new AtomicReference<>(null);
    
    // Latest network change reason
    private static final AtomicReference<String> latestReason = new AtomicReference<>(null);
    
    // Latest context
    private static final AtomicReference<Context> latestContext = new AtomicReference<>(null);
    
    // Flag to track if a job is currently scheduled
    private static final AtomicBoolean isScheduled = new AtomicBoolean(false);
    
    // Retry counter
    private static volatile int retryCount = 0;
    
    // Timestamp of last change request
    private static volatile long lastChangeTimestamp = 0;
    
    // Counter for tracking how many changes were coalesced
    private static volatile int coalescedCount = 0;

    /**
     * Schedule a network change to be processed after the debounce delay.
     * If a job is already scheduled, it will be cancelled and replaced with this one.
     *
     * @param context Application context
     * @param reason  Reason for the network change (e.g., CONNECTIVITY_CHANGE)
     */
    public static void scheduleNetworkChange(Context context, String reason) {
        // Cancel any existing pending job
        cancelPendingJob();
        
        // Store the latest change information
        latestContext.set(context.getApplicationContext());
        latestReason.set(reason);
        lastChangeTimestamp = System.currentTimeMillis();
        
        // Get debounce delay from preferences
        long debounceDelay = getDebounceDelay();
        
        // Increment coalesced counter
        if (isScheduled.get()) {
            coalescedCount++;
            Log.d(TAG, "Network change coalesced (total: " + coalescedCount + "): " + reason);
        } else {
            coalescedCount = 0;
            retryCount = 0;
            Log.d(TAG, "Network change scheduled with " + debounceDelay + "ms delay: " + reason);
        }
        
        // Create new runnable for applying rules
        Runnable applyRulesRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Context ctx = latestContext.get();
                    String finalReason = latestReason.get();
                    
                    if (ctx != null && finalReason != null) {
                        // Check if rules are currently being applied
                        if (Api.isRulesBeingApplied()) {
                            if (retryCount < MAX_RETRY_ATTEMPTS) {
                                retryCount++;
                                Log.d(TAG, "Rules currently being applied, retrying in " + RETRY_DELAY_MS + "ms (attempt " + retryCount + ")");
                                // Reschedule with shorter delay
                                handler.postDelayed(this, RETRY_DELAY_MS);
                                return;
                            } else {
                                Log.w(TAG, "Max retry attempts reached, forcing rule application");
                            }
                        }
                        
                        long elapsedTime = System.currentTimeMillis() - lastChangeTimestamp;
                        if (coalescedCount > 0) {
                            Log.i(TAG, "Applying rules after debounce (" + elapsedTime + "ms, " + 
                                  coalescedCount + " changes coalesced): " + finalReason);
                        } else {
                            Log.i(TAG, "Applying rules after debounce (" + elapsedTime + "ms): " + finalReason);
                        }
                        
                        // Apply the rules
                        InterfaceTracker.applyRulesOnChange(ctx, finalReason);
                        
                        // Reset state
                        coalescedCount = 0;
                        retryCount = 0;
                    } else {
                        Log.w(TAG, "Cannot apply rules: context or reason is null");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error applying rules after debounce: " + e.getMessage(), e);
                } finally {
                    // Clear scheduled state only if not retrying
                    if (retryCount == 0 || retryCount >= MAX_RETRY_ATTEMPTS) {
                        isScheduled.set(false);
                        pendingRunnable.set(null);
                    }
                }
            }
        };
        
        // Store the runnable and schedule it
        pendingRunnable.set(applyRulesRunnable);
        isScheduled.set(true);
        handler.postDelayed(applyRulesRunnable, debounceDelay);
    }
    
    /**
     * Cancel any pending network change job.
     */
    private static void cancelPendingJob() {
        Runnable existingRunnable = pendingRunnable.getAndSet(null);
        if (existingRunnable != null) {
            handler.removeCallbacks(existingRunnable);
            Log.d(TAG, "Cancelled pending network change job");
        }
        isScheduled.set(false);
    }
    
    /**
     * Check if a job is currently scheduled.
     *
     * @return true if a network change job is pending
     */
    public static boolean isPending() {
        return isScheduled.get();
    }
    
    /**
     * Get the debounce delay from preferences.
     * Falls back to default if preference is not set or invalid.
     *
     * @return Debounce delay in milliseconds
     */
    private static long getDebounceDelay() {
        try {
            // Try to get user-configured delay from preferences
            int delaySeconds = G.getNetworkDebounceDelay();
            if (delaySeconds > 0 && delaySeconds <= 30) {
                return delaySeconds * 1000L;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading debounce delay preference: " + e.getMessage());
        }
        return DEFAULT_DEBOUNCE_DELAY_MS;
    }
    
    /**
     * Force immediate execution of any pending job (for testing or emergency situations).
     */
    public static void flushPending() {
        Runnable existingRunnable = pendingRunnable.getAndSet(null);
        if (existingRunnable != null) {
            handler.removeCallbacks(existingRunnable);
            Log.i(TAG, "Flushing pending network change job immediately");
            // Execute immediately on current thread
            existingRunnable.run();
        }
    }
    
    /**
     * Clear all pending jobs without executing them (for cleanup).
     */
    public static void clear() {
        cancelPendingJob();
        latestContext.set(null);
        latestReason.set(null);
        coalescedCount = 0;
        retryCount = 0;
        Log.d(TAG, "Cleared all pending network change jobs");
    }
}
