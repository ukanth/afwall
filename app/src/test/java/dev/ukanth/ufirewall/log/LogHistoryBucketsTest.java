package dev.ukanth.ufirewall.log;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class LogHistoryBucketsTest {

    // the interface lists of InterfaceTracker (copied: that class needs Android)
    private static final String[] WIFI = {"eth+", "wlan+", "tiwlan+", "ra+", "bnep+"};
    private static final String[] MOBILE = {"rmnet+", "pdp+", "clat4+", "v4-rmnet+", "rmnet_data+", "ccmni+"};
    private static final String[] VPN = {"tun+", "ppp+", "tap+", "wg+"};
    private static final String[] TETHER = {"bt-pan", "usb+", "rndis+", "rmnet_usb+"};

    private static long at(String local, TimeZone tz) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        f.setTimeZone(tz);
        return f.parse(local).getTime();
    }

    private static String local(long epochMinutes, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        f.setTimeZone(tz);
        return f.format(epochMinutes * 60000L);
    }

    @Test
    public void hoursFollowTheLocalClockWithHalfHourOffsets() throws Exception {
        TimeZone india = TimeZone.getTimeZone("Asia/Kolkata");
        long t = at("2026-10-06 08:47:12", india);
        assertEquals("2026-10-06 08:00", local(LogHistoryBuckets.hourStart(t, india), india));
        assertEquals("2026-10-06 00:00", local(LogHistoryBuckets.dayStart(t, india), india));
        // a UTC hour would start at :30 here
        assertEquals(30, LogHistoryBuckets.hourStart(t, india) % 60);
    }

    @Test
    public void daylightSavingDay() throws Exception {
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        // clocks jump from 02:00 to 03:00 on 2026-03-08: the day has 23 hours
        long t = at("2026-03-08 03:30:00", ny);
        assertEquals("2026-03-08 03:00", local(LogHistoryBuckets.hourStart(t, ny), ny));
        long day = LogHistoryBuckets.dayStart(t, ny);
        assertEquals("2026-03-08 00:00", local(day, ny));
        long nextDay = LogHistoryBuckets.dayStart(at("2026-03-09 12:00:00", ny), ny);
        assertEquals(23 * 60, nextDay - day);
    }

    @Test
    public void protocols() {
        assertEquals(LogHistoryBuckets.PROTO_TCP, LogHistoryBuckets.protoCode("TCP"));
        assertEquals(LogHistoryBuckets.PROTO_UDP, LogHistoryBuckets.protoCode("udp"));
        assertEquals(LogHistoryBuckets.PROTO_ICMP, LogHistoryBuckets.protoCode("ICMP"));
        assertEquals(LogHistoryBuckets.PROTO_ICMPV6, LogHistoryBuckets.protoCode("ICMPv6"));
        assertEquals(LogHistoryBuckets.PROTO_OTHER, LogHistoryBuckets.protoCode("SCTP"));
        assertEquals(LogHistoryBuckets.PROTO_OTHER, LogHistoryBuckets.protoCode(null));
    }

    @Test
    public void networksFromInterfaceNames() {
        assertEquals(LogHistoryBuckets.NET_WIFI, net("wlan0"));
        assertEquals(LogHistoryBuckets.NET_WIFI, net("eth0"));
        assertEquals(LogHistoryBuckets.NET_MOBILE, net("rmnet_data1"));
        assertEquals(LogHistoryBuckets.NET_MOBILE, net("v4-rmnet_data0"));
        assertEquals(LogHistoryBuckets.NET_VPN, net("tun0"));
        assertEquals(LogHistoryBuckets.NET_TETHER, net("bt-pan"));
        // starts like a mobile interface, but is USB tethering
        assertEquals(LogHistoryBuckets.NET_TETHER, net("rmnet_usb0"));
        assertEquals(LogHistoryBuckets.NET_OTHER, net("lo"));
        assertEquals(LogHistoryBuckets.NET_OTHER, net(""));
        assertEquals(LogHistoryBuckets.NET_OTHER, net(null));
        // exact names only match exactly
        assertEquals(LogHistoryBuckets.NET_OTHER, net("bt-pan1"));
    }

    private static int net(String iface) {
        return LogHistoryBuckets.networkCode(iface, WIFI, MOBILE, VPN, TETHER);
    }
}
