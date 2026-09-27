package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IptablesVersionTest {

    @Test
    public void parsesVersionOutput() {
        assertArrayEquals(new int[]{1, 8, 10}, IptablesVersion.parse("iptables v1.8.10 (legacy)"));
        assertArrayEquals(new int[]{1, 4, 20}, IptablesVersion.parse("iptables v1.4.20"));
        assertNull(IptablesVersion.parse("garbage"));
        assertNull(IptablesVersion.parse(null));
    }

    @Test
    public void waitOptionFollowsVersion() {
        assertEquals(" -w 5", IptablesVersion.waitOption("iptables v1.8.10 (legacy)"));
        assertEquals(" -w 5", IptablesVersion.waitOption("iptables v1.6.0"));
        // Android 6: "-w" exists but takes no seconds (#1500)
        assertEquals(" -w", IptablesVersion.waitOption("iptables v1.4.20"));
        assertEquals(" -w", IptablesVersion.waitOption("iptables v1.4.21"));
        assertEquals("", IptablesVersion.waitOption("iptables v1.4.11.1"));
        // unknown: keep the previous behaviour
        assertEquals(" -w 5", IptablesVersion.waitOption(null));
    }

    @Test
    public void restoreWaitOptionFollowsVersion() {
        assertEquals(" -w 5", IptablesVersion.restoreWaitOption("iptables v1.8.4 (legacy)"));
        assertEquals(" -w 5", IptablesVersion.restoreWaitOption("iptables v1.6.2"));
        assertEquals("", IptablesVersion.restoreWaitOption("iptables v1.6.1"));
        assertEquals("", IptablesVersion.restoreWaitOption("iptables v1.4.20"));
    }
}
