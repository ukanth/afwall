package dev.ukanth.ufirewall.log;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The keys of the log history ({@link LogHistory}): time buckets, protocol and network codes.
 * No Android dependencies, so it can be unit tested.
 */
public final class LogHistoryBuckets {

    public static final int NET_OTHER = 0;
    public static final int NET_WIFI = 1;
    public static final int NET_MOBILE = 2;
    public static final int NET_VPN = 3;
    public static final int NET_TETHER = 4;

    public static final int PROTO_OTHER = 0;
    public static final int PROTO_ICMP = 1;
    public static final int PROTO_TCP = 6;
    public static final int PROTO_UDP = 17;
    public static final int PROTO_ICMPV6 = 58;

    /** Not known yet: the log has one prefix for every block (see the backlog, block reasons). */
    public static final int REASON_UNKNOWN = 0;

    private LogHistoryBuckets() {
    }

    /**
     * @return the start of the local hour {@code timeMillis} is in, in epoch minutes. Local, so
     * hours line up with the clock also in zones with a half-hour offset (India: +5:30).
     */
    public static long hourStart(long timeMillis, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz, Locale.US);
        c.setTimeInMillis(timeMillis);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 60000L;
    }

    /** @return the start of the local day {@code timeMillis} is in, in epoch minutes */
    public static long dayStart(long timeMillis, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz, Locale.US);
        c.setTimeInMillis(timeMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() / 60000L;
    }

    /** @return the IP protocol number of a logged protocol name ("TCP", "udp", "ICMPv6", ...) */
    public static int protoCode(String proto) {
        if (proto == null) {
            return PROTO_OTHER;
        }
        switch (proto.trim().toUpperCase(Locale.US)) {
            case "TCP":
                return PROTO_TCP;
            case "UDP":
                return PROTO_UDP;
            case "ICMP":
                return PROTO_ICMP;
            case "ICMPV6":
            case "IPV6-ICMP":
                return PROTO_ICMPV6;
            default:
                return PROTO_OTHER;
        }
    }

    /**
     * @param iface interface of the logged packet ("wlan0", "rmnet_data1", "tun0")
     * @return the network type, classified with the interface lists the rules use
     * ({@code InterfaceTracker.ITFS_*}: "wlan+" is a prefix, "bt-pan" an exact name)
     */
    public static int networkCode(String iface, String[] wifi, String[] mobile, String[] vpn, String[] tether) {
        if (iface == null || iface.isEmpty()) {
            return NET_OTHER;
        }
        if (matches(iface, vpn)) {
            return NET_VPN;
        }
        if (matches(iface, tether)) {
            return NET_TETHER;
        }
        if (matches(iface, wifi)) {
            return NET_WIFI;
        }
        if (matches(iface, mobile)) {
            return NET_MOBILE;
        }
        return NET_OTHER;
    }

    private static boolean matches(String iface, String[] patterns) {
        for (String p : patterns) {
            if (p.endsWith("+") ? iface.startsWith(p.substring(0, p.length() - 1)) : iface.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
