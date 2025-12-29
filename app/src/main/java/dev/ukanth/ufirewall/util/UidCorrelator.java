/**
 * Enhanced UID correlation system for AFWall+
 * Attempts to resolve Unknown UID (-100) entries by correlating
 * netfilter logs with active network connections
 * 
 * Copyright (C) 2024 AFWall+ Contributors
 */
package dev.ukanth.ufirewall.util;

import android.util.Log;
import com.topjohnwu.superuser.Shell;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UidCorrelator {
    private static final String TAG = "UidCorrelator";
    
    // Cache active connections for correlation
    private static final Map<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();
    private static final Map<String, Integer> recentConnections = new ConcurrentHashMap<>();
    private static long lastRefresh = 0;
    private static final long REFRESH_INTERVAL = 5000; // 5 seconds
    private static final long CORRELATION_WINDOW = 10000; // 10 seconds
    
    public static class ConnectionInfo {
        public final int uid;
        public final String localAddress;
        public final String remoteAddress;
        public final int localPort;
        public final int remotePort;
        public final String protocol;
        public final long timestamp;
        
        public ConnectionInfo(int uid, String localAddr, String remoteAddr, 
                            int localPort, int remotePort, String protocol) {
            this.uid = uid;
            this.localAddress = localAddr;
            this.remoteAddress = remoteAddr;
            this.localPort = localPort;
            this.remotePort = remotePort;
            this.protocol = protocol;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getConnectionKey() {
            return protocol + ":" + remoteAddress + ":" + remotePort;
        }
    }
    
    /**
     * Attempt to correlate unknown UID with active/recent connections
     * 
     * @param srcIp Source IP from netfilter log
     * @param dstIp Destination IP from netfilter log  
     * @param dstPort Destination port from netfilter log
     * @param srcPort Source port from netfilter log
     * @param protocol Protocol (TCP/UDP)
     * @param logTimestamp Timestamp of the log entry
     * @return UID if found, -100 if still unknown
     */
    public static int correlateUid(String srcIp, String dstIp, int dstPort, 
                                  int srcPort, String protocol, long logTimestamp) {
        
        refreshConnectionCache();
        
        if (activeConnections.isEmpty()) {
            Log.w(TAG, "No active connections in cache - /proc/net parsing may have failed");
        }
        
        // Try exact match first (outbound connection)
        String connectionKey = protocol.toUpperCase() + ":" + dstIp + ":" + dstPort;
        ConnectionInfo conn = activeConnections.get(connectionKey);
        
        // Also try reverse lookup (for return traffic where src/dst are swapped)
        if (conn == null) {
            String reverseKey = protocol.toUpperCase() + ":" + srcIp + ":" + srcPort;
            conn = activeConnections.get(reverseKey);
        }
        
        if (conn != null && isWithinTimeWindow(conn.timestamp, logTimestamp)) {
            Log.d(TAG, "Found exact match for " + connectionKey + " -> UID " + conn.uid);
            return conn.uid;
        }
        
        // Try recent connections cache
        Integer recentUid = recentConnections.get(connectionKey);
        if (recentUid != null) {
            Log.d(TAG, "Found recent connection for " + connectionKey + " -> UID " + recentUid);
            return recentUid;
        }
        
        // Fallback: scan all connections for partial matches
        for (ConnectionInfo connection : activeConnections.values()) {
            if (isPartialMatch(connection, srcIp, dstIp, dstPort, srcPort, protocol, logTimestamp)) {
                Log.d(TAG, "Found partial match -> UID " + connection.uid);
                // Cache for future lookups
                recentConnections.put(connectionKey, connection.uid);
                return connection.uid;
            }
        }
        
        Log.d(TAG, "No correlation found for " + connectionKey);
        return -100; // Still unknown
    }
    
    /**
     * Refresh the connection cache by parsing /proc/net files
     */
    private static void refreshConnectionCache() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_INTERVAL) {
            return; // Cache still fresh
        }
        
        try {
            // Clear old data
            activeConnections.clear();
            cleanupOldRecentConnections(now);
            
            // Parse TCP connections
            parseNetworkConnections("/proc/net/tcp", "TCP");
            parseNetworkConnections("/proc/net/tcp6", "TCP");
            
            // Parse UDP connections  
            parseNetworkConnections("/proc/net/udp", "UDP");
            parseNetworkConnections("/proc/net/udp6", "UDP");
            
            lastRefresh = now;
            if (activeConnections.isEmpty()) {
                Log.w(TAG, "Connection cache refresh completed but no connections found - check root access");
            } else {
                Log.d(TAG, "Refreshed connection cache: " + activeConnections.size() + " active connections");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing connection cache", e);
        }
    }
    
    /**
     * Parse network connection files from /proc/net
     * Uses root shell to ensure access on modern Android versions
     */
    private static void parseNetworkConnections(String filePath, String protocol) {
        try {
            // Use getSU() to ensure root shell is used for /proc/net access
            Shell.Result result = Shell.cmd("cat " + filePath).exec();
            if (!result.isSuccess()) {
                Log.w(TAG, "Failed to read " + filePath + " - exit code: " + result.getCode());
                return;
            }
            
            String output = String.join("\n", result.getOut());
            BufferedReader reader = new BufferedReader(new StringReader(output));
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                
                ConnectionInfo conn = parseConnectionLine(line, protocol);
                if (conn != null && conn.uid > 0) {
                    activeConnections.put(conn.getConnectionKey(), conn);
                    // Also cache by local port for better matching
                    String localKey = protocol + ":" + conn.localAddress + ":" + conn.localPort;
                    activeConnections.put(localKey, conn);
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse " + filePath, e);
        }
    }
    
    /**
     * Parse a single line from /proc/net/tcp or /proc/net/udp
     * Format: sl local_address rem_address st tx_queue rx_queue tr tm->when retrnsmt uid timeout inode
     */
    private static ConnectionInfo parseConnectionLine(String line, String protocol) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                return null;
            }
            
            // Parse local address (IP:PORT in hex)
            String[] localAddr = parts[1].split(":");
            String localIp = hexToIp(localAddr[0]);
            int localPort = Integer.parseInt(localAddr[1], 16);
            
            // Parse remote address  
            String[] remoteAddr = parts[2].split(":");
            String remoteIp = hexToIp(remoteAddr[0]);
            int remotePort = Integer.parseInt(remoteAddr[1], 16);
            
            // Get UID (column 7) - handle potential parsing errors
            int uid;
            try {
                uid = Integer.parseInt(parts[7]);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse UID from: " + parts[7]);
                return null;
            }
            
            // Only interested in established connections or UDP sockets
            // TCP state 01 = ESTABLISHED, for UDP we take all
            if (protocol.equals("TCP")) {
                String state = parts[3];
                if (!"01".equals(state)) {
                    return null; // Not established
                }
            }
            
            return new ConnectionInfo(uid, localIp, remoteIp, localPort, remotePort, protocol);
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse connection line: " + line, e);
            return null;
        }
    }
    
    /**
     * Convert hex IP address to dotted decimal
     * /proc/net format uses little-endian hex representation
     */
    private static String hexToIp(String hexIp) {
        if (hexIp.length() == 8) {
            // IPv4 - /proc/net uses little-endian format
            long ip = Long.parseLong(hexIp, 16);
            // Convert from little-endian: reverse byte order
            return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + 
                   ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        } else if (hexIp.length() == 32) {
            // IPv6 - check if it's an IPv4-mapped IPv6 address
            // Format: 0000000000000000FFFF0000XXXXXXXX where XXXXXXXX is the IPv4 in hex
            if (hexIp.startsWith("0000000000000000FFFF0000")) {
                // Extract the IPv4 part (last 8 characters)
                String ipv4Hex = hexIp.substring(24);
                long ip = Long.parseLong(ipv4Hex, 16);
                // Convert from big-endian for IPv6 mapped addresses
                return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + 
                       ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
            }
        }
        // IPv6 or unknown format - return as is for now
        return hexIp;
    }
    
    /**
     * Check if connection matches the netfilter log entry
     */
    private static boolean isPartialMatch(ConnectionInfo conn, String srcIp, String dstIp, 
                                        int dstPort, int srcPort, String protocol, long logTime) {
        
        // Protocol must match
        if (!conn.protocol.equalsIgnoreCase(protocol)) {
            return false;
        }
        
        // Time window check
        if (!isWithinTimeWindow(conn.timestamp, logTime)) {
            return false;
        }
        
        // Check if this is an outbound connection matching the log
        boolean outboundMatch = conn.remoteAddress.equals(dstIp) && 
                               conn.remotePort == dstPort;
        
        // Check if local port matches (if available)
        boolean portMatch = srcPort == 0 || conn.localPort == srcPort;
        
        return outboundMatch && portMatch;
    }
    
    private static boolean isWithinTimeWindow(long connTime, long logTime) {
        return Math.abs(connTime - logTime) <= CORRELATION_WINDOW;
    }
    
    private static void cleanupOldRecentConnections(long now) {
        // Remove entries older than correlation window
        Iterator<Map.Entry<String, Integer>> iterator = recentConnections.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            if (now - lastRefresh > CORRELATION_WINDOW) {
                iterator.remove();
            }
        }
    }
}