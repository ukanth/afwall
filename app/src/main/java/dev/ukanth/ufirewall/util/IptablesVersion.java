package dev.ukanth.ufirewall.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the lock-wait option supported by an iptables version.
 * <p>
 * "-w" appeared in iptables 1.4.20, "-w &lt;seconds&gt;" in 1.6.0 and iptables-restore learned
 * "-w" in 1.6.2. Passing "-w 5" to 1.4.20 (Android 6 systems) fails every command with
 * "Bad argument '5'". Kept free of Android dependencies so it can be unit tested.
 */
public final class IptablesVersion {

    private static final Pattern VERSION = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)");

    private IptablesVersion() {
    }

    /**
     * @param versionOutput output of "iptables --version", e.g. "iptables v1.8.10 (legacy)"
     * @return {major, minor, patch}, or null if it can't be parsed
     */
    public static int[] parse(String versionOutput) {
        if (versionOutput == null) {
            return null;
        }
        Matcher m = VERSION.matcher(versionOutput);
        if (!m.find()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    /**
     * @return option to append to iptables commands (with a leading space), "" for none.
     * An unknown version gets the modern option, as before.
     */
    public static String waitOption(String versionOutput) {
        int[] v = parse(versionOutput);
        if (v == null || atLeast(v, 1, 6, 0)) {
            return " -w 5";
        }
        return atLeast(v, 1, 4, 20) ? " -w" : "";
    }

    /**
     * @return option to pass to iptables-restore (with a leading space), "" for none
     */
    public static String restoreWaitOption(String versionOutput) {
        int[] v = parse(versionOutput);
        return v == null || atLeast(v, 1, 6, 2) ? " -w 5" : "";
    }

    private static boolean atLeast(int[] v, int major, int minor, int patch) {
        if (v[0] != major) {
            return v[0] > major;
        }
        if (v[1] != minor) {
            return v[1] > minor;
        }
        return v[2] >= patch;
    }
}
