/**
 * UID Resolution Helper - Provides fallback mechanisms for identifying UIDs
 * 
 * Copyright (C) 2024 AFWall+ Contributors
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.util.LruCache;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.ukanth.ufirewall.R;

public class UidResolver {
    
    private static final String TAG = "AFWall";
    
    // System UID database - well-known Android system UIDs
    private static final SparseArray<String> SYSTEM_UIDS = new SparseArray<>();
    
    static {
        // Core system UIDs
        SYSTEM_UIDS.put(0, "root");
        SYSTEM_UIDS.put(1000, "system");
        SYSTEM_UIDS.put(1001, "radio");
        SYSTEM_UIDS.put(1002, "bluetooth");
        SYSTEM_UIDS.put(1003, "graphics");
        SYSTEM_UIDS.put(1004, "input");
        SYSTEM_UIDS.put(1005, "audio");
        SYSTEM_UIDS.put(1006, "camera");
        SYSTEM_UIDS.put(1007, "log");
        SYSTEM_UIDS.put(1008, "compass");
        SYSTEM_UIDS.put(1009, "mount");
        SYSTEM_UIDS.put(1010, "wifi");
        SYSTEM_UIDS.put(1011, "adb");
        SYSTEM_UIDS.put(1012, "install");
        SYSTEM_UIDS.put(1013, "media");
        SYSTEM_UIDS.put(1014, "dhcp");
        SYSTEM_UIDS.put(1015, "sdcard_rw");
        SYSTEM_UIDS.put(1016, "vpn");
        SYSTEM_UIDS.put(1017, "keystore");
        SYSTEM_UIDS.put(1018, "usb");
        SYSTEM_UIDS.put(1019, "drm");
        SYSTEM_UIDS.put(1020, "mdnsr");
        SYSTEM_UIDS.put(1021, "gps");
        SYSTEM_UIDS.put(1023, "media_rw");
        SYSTEM_UIDS.put(1024, "mtp");
        SYSTEM_UIDS.put(1025, "unused1");
        SYSTEM_UIDS.put(1026, "unused2");
        SYSTEM_UIDS.put(1027, "unused3");
        SYSTEM_UIDS.put(1028, "unused4");
        SYSTEM_UIDS.put(1029, "clat");
        SYSTEM_UIDS.put(1030, "hsm");
        SYSTEM_UIDS.put(1031, "reserved");
        
        // Shell and nobody
        SYSTEM_UIDS.put(2000, "shell");
        SYSTEM_UIDS.put(9999, "nobody");
        
        // Android framework UIDs (1000-1999 range commonly used)
        for (int uid = 1032; uid <= 1099; uid++) {
            SYSTEM_UIDS.put(uid, "system_" + uid);
        }
    }
    
    // Cache for resolved UIDs with timestamp
    private static final ConcurrentHashMap<Integer, CacheEntry> UID_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes TTL
    private static final int MAX_CACHE_SIZE = 500; // Maximum cache entries
    
    // LRU cache for frequently accessed UIDs
    private static final LruCache<Integer, String> FREQUENT_UID_CACHE = new LruCache<Integer, String>(100) {
        @Override
        protected void entryRemoved(boolean evicted, Integer key, String oldValue, String newValue) {
            if (evicted) {
                Log.d(TAG, "LRU cache evicted UID " + key + " -> " + oldValue);
            }
        }
    };
    
    // Cache entry with TTL
    private static class CacheEntry {
        final String name;
        final long timestamp;
        final ResolutionMethod method;
        
        CacheEntry(String name, ResolutionMethod method) {
            this.name = name;
            this.timestamp = System.currentTimeMillis();
            this.method = method;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
    
    // Track which resolution method was used
    private enum ResolutionMethod {
        SYSTEM_DB, PACKAGE_MANAGER, PROC_FS, PACKAGES_LIST, UNKNOWN
    }
    
    // Process information from /proc/[pid]/status
    private static class ProcessInfo {
        String name;
        int uid;
        int pid;
        String state;
        
        ProcessInfo(String name, int uid, int pid, String state) {
            this.name = name;
            this.uid = uid;
            this.pid = pid;
            this.state = state;
        }
    }
    
    /**
     * Resolve UID to app name using fallback chain with caching
     * 
     * @param ctx Context for accessing resources
     * @param uid UID to resolve
     * @return Resolved name or "Unknown" if all methods fail
     */
    public static String resolveUid(Context ctx, int uid) {
        // Check LRU cache first (most frequently accessed)
        String frequent = FREQUENT_UID_CACHE.get(uid);
        if (frequent != null) {
            Log.d(TAG, "UID " + uid + " resolved from LRU cache: " + frequent);
            return frequent;
        }
        
        // Check main cache
        CacheEntry cached = UID_CACHE.get(uid);
        if (cached != null && !cached.isExpired()) {
            // Move to frequent cache if accessed multiple times
            FREQUENT_UID_CACHE.put(uid, cached.name);
            Log.d(TAG, "UID " + uid + " resolved from cache (" + cached.method + "): " + cached.name);
            return cached.name;
        }
        
        // Clean expired entries periodically
        if (UID_CACHE.size() > MAX_CACHE_SIZE) {
            cleanExpiredEntries();
        }
        
        String result;
        ResolutionMethod method = ResolutionMethod.UNKNOWN;
        
        // Method 1: Check system UID database
        result = resolveSystemUid(uid);
        if (result != null) {
            method = ResolutionMethod.SYSTEM_DB;
            cacheResult(uid, result, method);
            Log.d(TAG, "UID " + uid + " resolved via system database: " + result);
            return result;
        }
        
        // Method 2: Use PackageManager
        result = resolveViaPackageManager(ctx, uid);
        if (result != null) {
            method = ResolutionMethod.PACKAGE_MANAGER;
            cacheResult(uid, result, method);
            Log.d(TAG, "UID " + uid + " resolved via PackageManager: " + result);
            return result;
        }
        
        // Method 3: Check running processes
        result = resolveViaProc(uid);
        if (result != null) {
            method = ResolutionMethod.PROC_FS;
            cacheResult(uid, result, method);
            Log.d(TAG, "UID " + uid + " resolved via /proc: " + result);
            return result;
        }
        
        // Method 4: Parse packages.list (requires root)
        result = resolveViaPackagesList(uid);
        if (result != null) {
            method = ResolutionMethod.PACKAGES_LIST;
            cacheResult(uid, result, method);
            Log.d(TAG, "UID " + uid + " resolved via packages.list: " + result);
            return result;
        }
        
        // Cache unknown result with shorter TTL
        String unknown = ctx.getString(R.string.unknown_item);
        UID_CACHE.put(uid, new CacheEntry(unknown, ResolutionMethod.UNKNOWN));
        Log.w(TAG, "UID " + uid + " could not be resolved by any method");
        return unknown;
    }
    
    /**
     * Resolve UID using system UID database
     */
    private static String resolveSystemUid(int uid) {
        return SYSTEM_UIDS.get(uid);
    }
    
    /**
     * Resolve UID using PackageManager with multi-user support
     */
    private static String resolveViaPackageManager(Context ctx, int uid) {
        try {
            PackageManager pm = ctx.getPackageManager();
            
            // Try direct lookup first
            String[] packages = pm.getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                String packageName = packages[0];
                
                // For multi-user UIDs, add user context
                if (isMultiUserUid(uid)) {
                    int userId = getUserId(uid);
                    int appId = getAppId(uid);
                    return packageName + " (User " + userId + ")";
                }
                return packageName;
            }
            
            // Try getNameForUid as fallback
            String name = pm.getNameForUid(uid);
            if (name != null && !name.isEmpty()) {
                if (isMultiUserUid(uid)) {
                    int userId = getUserId(uid);
                    return name + " (User " + userId + ")";
                }
                return name;
            }
            
            // For multi-user UIDs, try looking up the base app UID
            if (isMultiUserUid(uid)) {
                int appId = getAppId(uid);
                int userId = getUserId(uid);
                
                Log.d(TAG, "Trying base UID lookup for multi-user UID " + uid + " (user:" + userId + ", app:" + appId + ")");
                
                // Try to resolve the base app UID
                String[] basePackages = pm.getPackagesForUid(appId);
                if (basePackages != null && basePackages.length > 0) {
                    return basePackages[0] + " (User " + userId + ")";
                }
                
                String baseName = pm.getNameForUid(appId);
                if (baseName != null && !baseName.isEmpty()) {
                    return baseName + " (User " + userId + ")";
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "PackageManager resolution failed for UID " + uid, e);
        }
        return null;
    }
    
    /**
     * Resolve UID by checking running processes in /proc (enhanced version)
     */
    private static String resolveViaProc(int uid) {
        try {
            File procDir = new File("/proc");
            if (!procDir.exists() || !procDir.canRead()) {
                Log.d(TAG, "/proc directory not accessible");
                return null;
            }
            
            File[] pidDirs = procDir.listFiles(file -> {
                try {
                    Integer.parseInt(file.getName());
                    return file.isDirectory();
                } catch (NumberFormatException e) {
                    return false;
                }
            });
            
            if (pidDirs == null) return null;
            
            // Track best match found
            String bestMatch = null;
            int bestScore = 0;
            
            for (File pidDir : pidDirs) {
                try {
                    File statusFile = new File(pidDir, "status");
                    if (!statusFile.exists() || !statusFile.canRead()) continue;
                    
                    // Check if this process belongs to our UID
                    ProcessInfo procInfo = parseProcessStatus(statusFile);
                    if (procInfo != null && procInfo.uid == uid) {
                        
                        // Try multiple sources for process name
                        String processName = null;
                        int score = 0;
                        
                        // Method 1: Get from cmdline (highest priority)
                        File cmdlineFile = new File(pidDir, "cmdline");
                        if (cmdlineFile.exists() && cmdlineFile.canRead()) {
                            String cmdline = readFirstLine(cmdlineFile);
                            if (cmdline != null && !cmdline.isEmpty()) {
                                processName = cleanProcessName(cmdline);
                                score = 3; // Highest score for cmdline
                            }
                        }
                        
                        // Method 2: Get from comm file (medium priority)
                        if (processName == null || processName.isEmpty()) {
                            File commFile = new File(pidDir, "comm");
                            if (commFile.exists() && commFile.canRead()) {
                                String comm = readFirstLine(commFile);
                                if (comm != null && !comm.isEmpty()) {
                                    processName = comm.trim();
                                    score = 2;
                                }
                            }
                        }
                        
                        // Method 3: Get from status Name field (lowest priority)
                        if (processName == null || processName.isEmpty()) {
                            if (procInfo.name != null && !procInfo.name.isEmpty()) {
                                processName = procInfo.name;
                                score = 1;
                            }
                        }
                        
                        // Track the best match found
                        if (processName != null && score > bestScore) {
                            bestMatch = processName;
                            bestScore = score;
                        }
                    }
                } catch (Exception e) {
                    // Skip this process and continue
                    continue;
                }
            }
            
            if (bestMatch != null) {
                Log.d(TAG, "Found process for UID " + uid + ": " + bestMatch + " (score: " + bestScore + ")");
            }
            return bestMatch;
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve UID via /proc", e);
        }
        return null;
    }
    
    /**
     * Parse comprehensive process information from /proc/[pid]/status file
     */
    private static ProcessInfo parseProcessStatus(File statusFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(statusFile))) {
            String line;
            String name = null;
            int uid = -1;
            int pid = -1;
            String state = null;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Name:")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length >= 2) {
                        name = parts[1].trim();
                    }
                } else if (line.startsWith("Pid:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            pid = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                } else if (line.startsWith("State:")) {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length >= 2) {
                        state = parts[1].trim();
                    }
                } else if (line.startsWith("Uid:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            uid = Integer.parseInt(parts[1]); // Real UID
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                }
            }
            
            if (uid != -1) {
                return new ProcessInfo(name, uid, pid, state);
            }
            
        } catch (IOException e) {
            // Ignore, process might have died
        }
        return null;
    }
    
    /**
     * Read first line from file
     */
    private static String readFirstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line != null ? line.trim() : null;
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Clean process name from cmdline
     */
    private static String cleanProcessName(String cmdline) {
        if (cmdline == null || cmdline.isEmpty()) return null;
        
        // Replace null bytes with spaces
        cmdline = cmdline.replace('\0', ' ').trim();
        
        // Extract just the command name
        String[] parts = cmdline.split("\\s+");
        if (parts.length > 0) {
            String cmd = parts[0];
            // Remove path if present
            int lastSlash = cmd.lastIndexOf('/');
            if (lastSlash >= 0) {
                cmd = cmd.substring(lastSlash + 1);
            }
            return cmd;
        }
        return cmdline;
    }
    
    /**
     * Resolve UID by parsing /data/system/packages.list (requires root) - enhanced version
     */
    private static String resolveViaPackagesList(int uid) {
        // Try multiple possible locations for packages list
        String[] possiblePaths = {
            "/data/system/packages.list",
            "/system/etc/packages.list",  // Some ROMs
            "/data/data/packages.list"    // Alternative location
        };
        
        for (String path : possiblePaths) {
            String result = tryReadPackagesList(path, uid);
            if (result != null) {
                Log.d(TAG, "Found UID " + uid + " in " + path + ": " + result);
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Try to read packages list from specific path
     */
    private static String tryReadPackagesList(String path, int uid) {
        try {
            File packagesFile = new File(path);
            if (!packagesFile.exists() || !packagesFile.canRead()) {
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(new FileReader(packagesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip comments and empty lines
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    String[] parts = line.split("\\s+");
                    // Format: package_name uid debuggable data_dir selinux_label
                    if (parts.length >= 2) {
                        try {
                            int packageUid = Integer.parseInt(parts[1]);
                            
                            // Handle multi-user UIDs (user ID is in higher bits)
                            int baseUid = packageUid % 100000; // Remove user ID part
                            
                            if (packageUid == uid || baseUid == (uid % 100000)) {
                                String packageName = parts[0];
                                
                                // Validate package name format
                                if (isValidPackageName(packageName)) {
                                    return packageName;
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Skip malformed line
                            Log.d(TAG, "Malformed line in " + path + ": " + line);
                            continue;
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Failed to read " + path + ": " + e.getMessage());
        } catch (SecurityException e) {
            Log.d(TAG, "No permission to read " + path + " (expected on non-root)");
        }
        return null;
    }
    
    /**
     * Validate package name format
     */
    private static boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        
        // Basic package name validation (at least one dot, reasonable length)
        return packageName.contains(".") && 
               packageName.length() > 3 && 
               packageName.length() < 256 &&
               packageName.matches("^[a-zA-Z0-9._]+$");
    }
    
    /**
     * Check if UID is in system range
     */
    public static boolean isSystemUid(int uid) {
        return uid >= 0 && uid < 10000;
    }
    
    /**
     * Check if UID is in app range  
     */
    public static boolean isAppUid(int uid) {
        return uid >= 10000;
    }
    
    /**
     * Check if UID is in multi-user range
     */
    public static boolean isMultiUserUid(int uid) {
        return uid >= 100000; // User 1 and above
    }
    
    /**
     * Extract user ID from multi-user UID
     * @param uid the multi-user UID
     * @return user ID (0 for primary user, 1+ for secondary users)
     */
    public static int getUserId(int uid) {
        return uid / 100000;
    }
    
    /**
     * Extract app ID from multi-user UID
     * @param uid the multi-user UID
     * @return app ID (the base UID without user component)
     */
    public static int getAppId(int uid) {
        return uid % 100000;
    }
    
    /**
     * Create multi-user UID from user ID and app ID
     * @param userId user ID (0, 1, 2, etc.)
     * @param appId app ID (10000+)
     * @return multi-user UID
     */
    public static int createMultiUserUid(int userId, int appId) {
        return userId * 100000 + appId;
    }
    
    /**
     * Get user-friendly description of UID type
     */
    public static String getUidTypeDescription(int uid) {
        if (uid < 0) {
            return "invalid";
        } else if (uid == 0) {
            return "root";
        } else if (uid < 1000) {
            return "system_low";
        } else if (uid < 10000) {
            return "system";
        } else if (uid < 100000) {
            return "app_primary";
        } else {
            int userId = getUserId(uid);
            int appId = getAppId(uid);
            return String.format("app_user%d(app:%d)", userId, appId);
        }
    }
    
    /**
     * Cache a resolved UID result
     */
    private static void cacheResult(int uid, String name, ResolutionMethod method) {
        UID_CACHE.put(uid, new CacheEntry(name, method));
        
        // Add system UIDs to frequent cache immediately (they're stable)
        if (method == ResolutionMethod.SYSTEM_DB) {
            FREQUENT_UID_CACHE.put(uid, name);
        }
    }
    
    /**
     * Clean expired entries from cache
     */
    private static void cleanExpiredEntries() {
        long now = System.currentTimeMillis();
        UID_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
        Log.d(TAG, "Cache cleanup completed, remaining entries: " + UID_CACHE.size());
    }
    
    /**
     * Clear all caches (useful for testing or when packages change)
     */
    public static void clearCache() {
        UID_CACHE.clear();
        FREQUENT_UID_CACHE.evictAll();
        Log.i(TAG, "UID resolution caches cleared");
    }
    
    /**
     * Invalidate cache entry for specific UID
     */
    public static void invalidateUid(int uid) {
        UID_CACHE.remove(uid);
        FREQUENT_UID_CACHE.remove(uid);
        Log.d(TAG, "Cache invalidated for UID: " + uid);
    }
    
    /**
     * Get cache statistics for debugging
     */
    public static String getCacheStats() {
        return String.format("Cache: %d entries, LRU: %d entries", 
                UID_CACHE.size(), FREQUENT_UID_CACHE.size());
    }
}