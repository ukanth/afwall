package dev.ukanth.ufirewall.util;

import com.topjohnwu.superuser.Shell;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.ukanth.ufirewall.log.Log;

/**
 * File operations as root, on libsu's main shell (replaces the RootTools helpers). Blocking: call
 * them off the main thread.
 */
public final class RootFiles {

    private static final String TAG = "AFWall";

    // mount points remounted read-write by remount(..., true); only these are made read-only again
    private static final Set<String> remountedRw = new HashSet<>();

    private RootFiles() {
    }

    public static boolean exists(String path) {
        return path != null && Shell.cmd("[ -e " + quote(path) + " ]").exec().isSuccess();
    }

    /**
     * Copy a file; {@code dest} may be a directory.
     */
    public static boolean copy(String src, String dest) {
        return Shell.cmd("cp -f " + quote(src) + " " + quote(dest)).exec().isSuccess();
    }

    /**
     * Make the file system holding {@code path} writable (e.g. /system for an init.d script), or
     * restore it afterwards. A mount point that is already writable (like /data, where current
     * root solutions keep their boot scripts) is left alone, and only a mount point this class
     * made writable is made read-only again.
     *
     * @param busybox busybox binary to fall back to when the system mount can't remount; may be empty
     * @return true if {@code path} is (still) usable in the requested mode
     */
    public static synchronized boolean remount(String path, boolean readWrite, String busybox) {
        String[] mount = findMount(path);
        if (mount == null) {
            Log.w(TAG, "No mount point found for " + path);
            return false;
        }
        String point = mount[0];
        boolean isRw = mount[1].equals("rw") || mount[1].startsWith("rw,");
        if (readWrite) {
            if (isRw) {
                return true;
            }
            if (remountCmd(point, "rw", busybox)) {
                remountedRw.add(point);
                return true;
            }
            return false;
        }
        if (!remountedRw.remove(point)) {
            return true; // we didn't change it
        }
        return remountCmd(point, "ro", busybox);
    }

    private static boolean remountCmd(String point, String mode, String busybox) {
        if (Shell.cmd("mount -o remount," + mode + " " + quote(point)).exec().isSuccess()) {
            return true;
        }
        return busybox != null && !busybox.trim().isEmpty()
                && Shell.cmd(quote(busybox) + " mount -o remount," + mode + " " + quote(point)).exec().isSuccess();
    }

    /**
     * @return {mount point, options} of the longest mount point containing {@code path}, or null
     */
    private static String[] findMount(String path) {
        List<String> mounts = Shell.cmd("cat /proc/mounts").exec().getOut();
        String[] best = null;
        for (String line : mounts) {
            // device mountpoint type options dump pass
            String[] f = line.split("\\s+");
            if (f.length < 4) {
                continue;
            }
            String point = f[1];
            boolean contains = path.equals(point) || point.equals("/")
                    || path.startsWith(point.endsWith("/") ? point : point + "/");
            if (contains && (best == null || point.length() >= best[0].length())) {
                best = new String[]{point, f[3]};
            }
        }
        return best;
    }

    /**
     * @return {@code s} as a single-quoted shell word
     */
    static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
