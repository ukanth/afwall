package dev.ukanth.ufirewall.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LogServiceLocalAddressTest {

    @Test
    public void privateRangesAreLocal() {
        assertTrue(LogService.isLocalAddress("192.168.128.18"));
        assertTrue(LogService.isLocalAddress("10.1.2.3"));
        assertTrue(LogService.isLocalAddress("172.16.0.5"));
        assertTrue(LogService.isLocalAddress("169.254.10.1"));
    }

    @Test
    public void ipv6LocalAddressesAreLocal() {
        // dmesg prints IPv6 fully expanded
        assertTrue(LogService.isLocalAddress("fe80:0000:0000:0000:94a9:4aff:fe06:eddd"));
        assertTrue(LogService.isLocalAddress("fd12:3456:789a:0001:0000:0000:0000:0001"));
    }

    @Test
    public void publicAndInvalidAreNotLocal() {
        assertFalse(LogService.isLocalAddress("172.217.113.4"));
        assertFalse(LogService.isLocalAddress("172.32.0.1")); // just outside 172.16/12
        assertFalse(LogService.isLocalAddress("2607:f8b0:4005:0080:0000:0000:0000:2004"));
        assertFalse(LogService.isLocalAddress(null));
        assertFalse(LogService.isLocalAddress("example.com")); // never resolved
    }
}
