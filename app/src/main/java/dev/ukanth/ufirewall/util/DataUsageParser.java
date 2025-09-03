package dev.ukanth.ufirewall.util;

import com.topjohnwu.superuser.Shell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.ukanth.ufirewall.log.Log;

/**
 * Utility class to parse network data usage from /proc/net/xt_qtaguid
 * Provides WiFi/Mobile separation and caching for performance
 */
public class DataUsageParser {
    private static final String TAG = "DataUsageParser";
    
    // Cache for data usage stats to avoid frequent root shell calls
    private static final Map<Integer, DataUsageStats> dataUsageCache = new ConcurrentHashMap<>();
    private static long lastCacheUpdate = 0;
    private static final long CACHE_VALIDITY_MS = 30000; // 30 seconds cache

    public static class DataUsageStats {
        public long wifiRxBytes = 0;
        public long wifiTxBytes = 0;
        public long mobileRxBytes = 0;
        public long mobileTxBytes = 0;
        public long totalRxBytes = 0;
        public long totalTxBytes = 0;
        
        public long getTotalWifiBytes() {
            return wifiRxBytes + wifiTxBytes;
        }
        
        public long getTotalMobileBytes() {
            return mobileRxBytes + mobileTxBytes;
        }
        
        public long getTotalBytes() {
            return totalRxBytes + totalTxBytes;
        }
    }

    /**
     * Get data usage statistics for a specific UID
     * @param uid The application UID
     * @return DataUsageStats object with WiFi/Mobile breakdown
     */
    public static DataUsageStats getDataUsageForUID(int uid) {
        // Check cache first
        if (isCacheValid() && dataUsageCache.containsKey(uid)) {
            return dataUsageCache.get(uid);
        }

        // Refresh cache if needed
        if (!isCacheValid()) {
            refreshDataUsageCache();
        }

        DataUsageStats stats = dataUsageCache.get(uid);
        return stats != null ? stats : new DataUsageStats();
    }

    /**
     * Check if the cache is still valid
     */
    private static boolean isCacheValid() {
        return (System.currentTimeMillis() - lastCacheUpdate) < CACHE_VALIDITY_MS;
    }

    /**
     * Refresh the entire data usage cache by parsing /proc/net/xt_qtaguid
     */
    private static void refreshDataUsageCache() {
        try {
            dataUsageCache.clear();
            
            // Try different possible locations for xt_qtaguid
            String[] possiblePaths = {
                "/proc/net/xt_qtaguid/stats",
                "/proc/net/xt_qtaguid/ctrl",
                "/proc/net/xt_qtaguid",
                "/sys/kernel/debug/xt_qtaguid/stats"
            };

            String qtaguidData = null;
            for (String path : possiblePaths) {
                Shell.Result result = Shell.cmd("test -f " + path + " && cat " + path).exec();
                if (result.isSuccess() && !result.getOut().isEmpty()) {
                    qtaguidData = String.join("\n", result.getOut());
                    Log.d(TAG, "Found xt_qtaguid data at: " + path);
                    break;
                }
            }

            if (qtaguidData != null) {
                parseQtaguidData(qtaguidData);
            } else {
                // Fallback to TrafficStats if xt_qtaguid is not available
                Log.w(TAG, "xt_qtaguid not available, using fallback method");
                useFallbackMethod();
            }

            lastCacheUpdate = System.currentTimeMillis();

        } catch (Exception e) {
            Log.e(TAG, "Error refreshing data usage cache", e);
            useFallbackMethod();
        }
    }

    /**
     * Parse the xt_qtaguid data format
     * Format: idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets rx_tcp_bytes rx_tcp_packets tx_tcp_packets rx_udp_bytes rx_udp_packets tx_udp_bytes tx_udp_packets rx_other_bytes rx_other_packets tx_other_bytes tx_other_packets
     */
    private static void parseQtaguidData(String qtaguidData) {
        String[] lines = qtaguidData.split("\n");
        
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("idx")) {
                continue; // Skip empty lines and header
            }
            
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                continue; // Skip malformed lines
            }
            
            try {
                // Parse fields based on xt_qtaguid format
                String iface = parts[1];
                String uidTagStr = parts[3];
                long rxBytes = Long.parseLong(parts[5]);
                long txBytes = Long.parseLong(parts[7]);
                
                // Extract UID from uid_tag_int (format: uid << 32 | tag)
                long uidTag = Long.parseLong(uidTagStr);
                int uid = (int) (uidTag >> 32);
                
                if (uid <= 0) {
                    continue; // Skip invalid UIDs
                }
                
                // Get or create stats for this UID
                DataUsageStats stats = dataUsageCache.get(uid);
                if (stats == null) {
                    stats = new DataUsageStats();
                    dataUsageCache.put(uid, stats);
                }
                
                // Classify interface type and accumulate data
                if (isWiFiInterface(iface)) {
                    stats.wifiRxBytes += rxBytes;
                    stats.wifiTxBytes += txBytes;
                } else if (isMobileInterface(iface)) {
                    stats.mobileRxBytes += rxBytes;
                    stats.mobileTxBytes += txBytes;
                }
                
                // Update totals
                stats.totalRxBytes += rxBytes;
                stats.totalTxBytes += txBytes;
                
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse line: " + line);
            }
        }
        
        Log.d(TAG, "Parsed data usage for " + dataUsageCache.size() + " UIDs");
    }

    /**
     * Check if interface is WiFi-related
     */
    private static boolean isWiFiInterface(String iface) {
        return iface != null && (
            iface.startsWith("wlan") ||
            iface.startsWith("wifi") ||
            iface.equals("wl0") ||
            iface.equals("eth0") // Sometimes WiFi appears as eth0
        );
    }

    /**
     * Check if interface is mobile data-related
     */
    private static boolean isMobileInterface(String iface) {
        return iface != null && (
            iface.startsWith("rmnet") ||
            iface.startsWith("ccmni") ||
            iface.startsWith("pdp") ||
            iface.startsWith("ppp") ||
            iface.startsWith("mobile") ||
            iface.startsWith("radio") ||
            iface.matches("rmnet\\d+") ||
            iface.matches("rmnet_data\\d+")
        );
    }

    /**
     * Fallback method when xt_qtaguid is not available
     * Uses /proc/uid_stat as AFWall+ already does
     */
    private static void useFallbackMethod() {
        // This provides total data only, no WiFi/Mobile separation
        Shell.Result result = Shell.cmd("find /proc/uid_stat -name '[0-9]*' -type d 2>/dev/null").exec();
        
        if (result.isSuccess()) {
            for (String uidDir : result.getOut()) {
                try {
                    String uidStr = uidDir.substring(uidDir.lastIndexOf('/') + 1);
                    int uid = Integer.parseInt(uidStr);
                    
                    // Read rx and tx bytes
                    Shell.Result rxResult = Shell.cmd("cat " + uidDir + "/tcp_rcv 2>/dev/null || echo 0").exec();
                    Shell.Result txResult = Shell.cmd("cat " + uidDir + "/tcp_snd 2>/dev/null || echo 0").exec();
                    
                    if (rxResult.isSuccess() && txResult.isSuccess()) {
                        long rxBytes = Long.parseLong(rxResult.getOut().get(0).trim());
                        long txBytes = Long.parseLong(txResult.getOut().get(0).trim());
                        
                        DataUsageStats stats = new DataUsageStats();
                        stats.totalRxBytes = rxBytes;
                        stats.totalTxBytes = txBytes;
                        // Cannot separate WiFi/Mobile in fallback mode
                        
                        dataUsageCache.put(uid, stats);
                    }
                    
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse UID directory: " + uidDir);
                }
            }
        }
        
        Log.d(TAG, "Fallback method parsed " + dataUsageCache.size() + " UIDs");
    }

    /**
     * Clear the cache to force refresh on next request
     */
    public static void clearCache() {
        dataUsageCache.clear();
        lastCacheUpdate = 0;
    }

    /**
     * Get human readable data usage string
     */
    public static String formatDataUsage(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /**
     * Format WiFi/Mobile breakdown for display
     */
    public static String formatWifiMobileUsage(DataUsageStats stats) {
        if (stats.getTotalWifiBytes() == 0 && stats.getTotalMobileBytes() == 0) {
            return "No data usage";
        }
        
        StringBuilder sb = new StringBuilder();
        if (stats.getTotalWifiBytes() > 0) {
            sb.append("📶 ").append(formatDataUsage(stats.getTotalWifiBytes()));
        }
        if (stats.getTotalMobileBytes() > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("📱 ").append(formatDataUsage(stats.getTotalMobileBytes()));
        }
        
        return sb.toString();
    }
}