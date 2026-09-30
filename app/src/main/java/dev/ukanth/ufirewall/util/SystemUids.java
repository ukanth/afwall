package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.os.SystemClock;

import com.topjohnwu.superuser.Shell;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.ukanth.ufirewall.log.Log;

/**
 * System UIDs (below 10000) that use the network but have no package and no fixed special entry
 * in the app list: native daemons such as dns (1051) or wifi (1010), and manufacturer UIDs
 * (2900-2999, 5000-5999) that differ from device to device. Without an entry they could not be
 * allowed (allow-list mode) or blocked (block-list mode).
 * <p>
 * They are found when they own a socket (read as root from /proc/net) or show up in the block
 * log, and are remembered, so their entry stays when the daemon isn't running.
 */
public final class SystemUids {

    private static final String TAG = "AFWall";
    private static final String PREF = "discoveredSystemUids"; // "1051:dns|5012:vendor_x"
    private static final long DISCOVERY_INTERVAL_MS = 60_000;

    private static Map<Integer, String> known;
    private static long lastDiscovery;
    // UIDs already checked from the log in this process, to keep the per-line cost low
    private static final Set<Integer> seen = Collections.synchronizedSet(new HashSet<>());

    private SystemUids() {
    }

    /**
     * @return remembered system UIDs and their Android names, sorted by UID
     */
    public static synchronized Map<Integer, String> known() {
        if (known == null) {
            known = new TreeMap<>();
            String saved = G.gPrefs != null ? G.gPrefs.getString(PREF, "") : "";
            for (String entry : saved.split("\\|")) {
                int sep = entry.indexOf(':');
                if (sep <= 0) {
                    continue;
                }
                try {
                    known.put(Integer.parseInt(entry.substring(0, sep)), entry.substring(sep + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new TreeMap<>(known);
    }

    /**
     * A system UID showed up in the block log.
     */
    public static void seenInLog(Context ctx, int uid, String name) {
        if (!isCandidate(uid) || !seen.add(uid)) {
            return;
        }
        if (!isCovered(ctx, uid)) {
            // prefer the Android user name of the running daemon ("statsd") over a generic one
            String processName = null;
            try {
                processName = processUserNames().get(uid);
            } catch (Exception ignored) {
            }
            remember(uid, processName != null ? processName : name);
        }
    }

    /**
     * Look for system UIDs that own sockets (root). Rate limited; call it off the main thread.
     */
    public static void discover(Context ctx) {
        synchronized (SystemUids.class) {
            long now = SystemClock.elapsedRealtime();
            if (lastDiscovery != 0 && now - lastDiscovery < DISCOVERY_INTERVAL_MS) {
                return;
            }
            lastDiscovery = now;
        }
        try {
            if (!Boolean.TRUE.equals(Shell.isAppGrantedRoot())) {
                return;
            }
            Set<Integer> uids = new HashSet<>();
            List<String> lines = Shell.cmd("cat /proc/net/tcp /proc/net/tcp6 /proc/net/udp /proc/net/udp6 2>/dev/null")
                    .exec().getOut();
            for (String line : lines) {
                String[] f = line.trim().split("\\s+");
                // sl local_address rem_address st tx:rx tr:tm retrnsmt uid ...
                if (f.length > 7) {
                    try {
                        int uid = Integer.parseInt(f[7]);
                        if (isCandidate(uid)) {
                            uids.add(uid);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (uids.isEmpty()) {
                return;
            }
            Map<Integer, String> names = processUserNames();
            for (int uid : uids) {
                if (!isCovered(ctx, uid)) {
                    remember(uid, names.containsKey(uid) ? names.get(uid) : UidResolver.resolveUid(ctx, uid));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "System UID discovery failed: " + e.getMessage());
        }
    }

    /**
     * @return uid -> Android user name ("audioserver", "vendor_rfs", ...) of running processes;
     * manufacturer UIDs get the names their system defines
     */
    private static Map<Integer, String> processUserNames() {
        Map<Integer, String> names = new HashMap<>();
        for (String line : Shell.cmd("ps -A -o UID,USER").exec().getOut()) {
            String[] f = line.trim().split("\\s+");
            if (f.length >= 2) {
                try {
                    names.put(Integer.parseInt(f[0]), f[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return names;
    }

    /**
     * System UIDs of the main user; root (0) and app UIDs have their own entries.
     */
    static boolean isCandidate(int uid) {
        return uid > 0 && uid < android.os.Process.FIRST_APPLICATION_UID;
    }

    /**
     * @return true if the app list already has an entry for {@code uid}: a package or a fixed
     * special entry
     */
    private static boolean isCovered(Context ctx, int uid) {
        String[] pkgs = ctx.getPackageManager().getPackagesForUid(uid);
        if (pkgs != null && pkgs.length > 0) {
            return true;
        }
        for (dev.ukanth.ufirewall.Api.PackageInfoData data : dev.ukanth.ufirewall.Api.getFixedSpecialData()) {
            if (data.uid == uid) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void remember(int uid, String name) {
        Map<Integer, String> current = known();
        if (current.containsKey(uid)) {
            return;
        }
        String clean = name == null || name.trim().isEmpty() ? String.valueOf(uid)
                : name.trim().replace("|", "").replace(":", "");
        known.put(uid, clean);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : known.entrySet()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        G.gPrefs.edit().putString(PREF, sb.toString()).apply();
        dev.ukanth.ufirewall.Api.applications = null; // the app list gets the new entry
        Log.i(TAG, "New system UID with network use: " + uid + " (" + clean + ")");
    }
}
