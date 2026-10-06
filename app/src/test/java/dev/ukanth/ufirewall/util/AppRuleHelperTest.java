package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppRuleHelperTest {

    @Test
    public void matchesProfileRuleForUid() {
        assertTrue(AppRuleHelper.isRuleForUidInAnyProfile("direct-rule:AFWallPrefs:10123: allow dst=1.2.3.4", 10123));
        assertTrue(AppRuleHelper.isRuleForUidInAnyProfile("direct-rule:AFWallProfile2:10123: allow proto=tcp dport=80:443", 10123));
    }

    @Test
    public void matchesLegacyRuleForUid() {
        assertTrue(AppRuleHelper.isRuleForUidInAnyProfile("direct-rule:10123: allow dst=1.2.3.4", 10123));
    }

    @Test
    public void doesNotMatchOtherUids() {
        assertFalse(AppRuleHelper.isRuleForUidInAnyProfile("direct-rule:AFWallPrefs:10124: allow", 10123));
        assertFalse(AppRuleHelper.isRuleForUidInAnyProfile("direct-rule:10124: allow", 10123));
        // a port range must not be mistaken for the uid
        assertFalse(AppRuleHelper.isRuleForUidInAnyProfile("direct-rule:AFWallPrefs:10124: allow dport=10123:10200", 10123));
    }

    @Test
    public void ignoresNonDirectRules() {
        assertFalse(AppRuleHelper.isRuleForUidInAnyProfile(null, 10123));
        assertFalse(AppRuleHelper.isRuleForUidInAnyProfile("my custom rule 10123", 10123));
    }

    @Test
    public void parsesRuleName() {
        AppRuleHelper.ParsedRule r = AppRuleHelper.parseRuleName(
                "direct-rule:AFWallProfile2:10123: allow dst=1.2.3.0/24 proto=tcp dport=80:443");
        assertEquals("AFWallProfile2", r.profile);
        assertEquals(10123, r.uid);
        assertEquals("1.2.3.0/24", r.destination);
        assertEquals("tcp", r.protocol);
        assertEquals("80:443", r.port);
    }

    @Test
    public void parsesLegacyRuleNameAsDefaultProfile() {
        AppRuleHelper.ParsedRule r = AppRuleHelper.parseRuleName("direct-rule:-10: allow");
        assertEquals("AFWallPrefs", r.profile);
        assertEquals(-10, r.uid);
        assertEquals("", r.destination);
        assertEquals("any", r.protocol);
        assertEquals("", r.port);
    }

    @Test
    public void parseRejectsOtherNames() {
        assertNull(AppRuleHelper.parseRuleName(null));
        assertNull(AppRuleHelper.parseRuleName("my custom rule"));
        assertNull(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:abc: allow"));
    }

    @Test
    public void buildsRuleForChainAndFamily() {
        AppRuleHelper.ParsedRule any = AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: allow proto=tcp dport=443");
        assertEquals("-A afwall -m owner --uid-owner 10123 -p tcp --dport 443 -j RETURN",
                AppRuleHelper.buildRule(any, "afwall", false));
        // no destination: the same rule for IPv6, in the chain of this user
        assertEquals("-A afwall10 -m owner --uid-owner 10123 -p tcp --dport 443 -j RETURN",
                AppRuleHelper.buildRule(any, "afwall10", true));
    }

    @Test
    public void destinationRuleOnlyInItsFamily() {
        AppRuleHelper.ParsedRule v4 = AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: allow dst=9.9.9.9");
        assertEquals("-A afwall -m owner --uid-owner 10123 -d 9.9.9.9 -j RETURN", AppRuleHelper.buildRule(v4, "afwall", false));
        assertNull(AppRuleHelper.buildRule(v4, "afwall", true));

        AppRuleHelper.ParsedRule v6 = AppRuleHelper.parseRuleName(
                "direct-rule:AFWallPrefs:10123: allow dst=2001:db8::/32 proto=udp dport=53");
        assertEquals("2001:db8::/32", v6.destination);
        assertEquals("-A afwall -m owner --uid-owner 10123 -d 2001:db8::/32 -p udp --dport 53 -j RETURN",
                AppRuleHelper.buildRule(v6, "afwall", true));
        assertNull(AppRuleHelper.buildRule(v6, "afwall", false));
    }

    @Test
    public void noRuleForSpecialEntriesWithoutUid() {
        // kernel (-11) has no UID iptables can match: "--uid-owner -11" breaks the whole load
        assertNull(AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:-11: allow dst=1.2.3.4"), "afwall", false));
        assertFalse(AppRuleHelper.supportsUid(-11));
        assertFalse(AppRuleHelper.supportsUid(-12));
        // "any app" has no owner match
        assertTrue(AppRuleHelper.supportsUid(-10));
        assertEquals("-A afwall -d 1.2.3.4 -j RETURN",
                AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:-10: allow dst=1.2.3.4"), "afwall", false));
        assertTrue(AppRuleHelper.supportsUid(0));
        assertTrue(AppRuleHelper.supportsUid(1010145));
    }

    @Test
    public void validatesDestinations() {
        assertTrue(AppRuleHelper.isValidDestination("10.0.0.0/8"));
        assertTrue(AppRuleHelper.isValidDestination("2001:db8::1"));
        assertTrue(AppRuleHelper.isValidDestination("2001:db8::/32"));
        assertTrue(AppRuleHelper.isValidDestination("::ffff:1.2.3.4"));
        assertFalse(AppRuleHelper.isValidDestination("2001:db8::/129"));
        assertFalse(AppRuleHelper.isValidDestination("2001:::1"));
        assertFalse(AppRuleHelper.isValidDestination("example.com"));
        assertFalse(AppRuleHelper.isValidDestination("1.2.3.4/33"));
        assertFalse(AppRuleHelper.isValidDestination("fe80::1%wlan0"));
        assertTrue(AppRuleHelper.isIpv6Destination("2001:db8::1"));
        assertFalse(AppRuleHelper.isIpv6Destination("1.2.3.4"));
    }

    @Test
    public void plainAllowRuleKeepsItsOldName() {
        // existing rules (and versions without block / network rules) use this exact name
        assertEquals("direct-rule:AFWallPrefs:10123: allow dst=1.2.3.4 proto=tcp dport=443",
                AppRuleHelper.buildRuleName("AFWallPrefs", 10123, AppRuleHelper.ACTION_ALLOW,
                        AppRuleHelper.NETWORK_ALL, "1.2.3.4", "tcp", "443"));
        AppRuleHelper.ParsedRule r = AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: allow dst=1.2.3.4");
        assertEquals(AppRuleHelper.ACTION_ALLOW, r.action);
        assertEquals(AppRuleHelper.NETWORK_ALL, r.network);
        assertTrue(r.isPlainAllow());
    }

    @Test
    public void blockAndNetworkRoundTrip() {
        String name = AppRuleHelper.buildRuleName("AFWallPrefs", -10, AppRuleHelper.ACTION_BLOCK,
                AppRuleHelper.NETWORK_WIFI, "203.0.113.50", "any", "");
        assertEquals("direct-rule:AFWallPrefs:-10: block net=wifi dst=203.0.113.50", name);
        AppRuleHelper.ParsedRule r = AppRuleHelper.parseRuleName(name);
        assertTrue(r.isBlock());
        assertEquals(AppRuleHelper.NETWORK_WIFI, r.network);
        assertEquals("203.0.113.50", r.destination);
        assertFalse(r.isPlainAllow());
        assertFalse(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:1: allow net=mobile dport=1").isPlainAllow());
    }

    @Test
    public void blockRuleJumpsToRejectChain() {
        assertEquals("-A afwall10 -d 203.0.113.50 -j afwall10-reject",
                AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:-10: block dst=203.0.113.50"),
                        "afwall10", false));
        assertEquals("-A afwall -m owner --uid-owner 10123 -p udp --dport 443 -j afwall-reject",
                AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: block proto=udp dport=443"),
                        "afwall", true));
    }

    @Test
    public void networkRuleGoesInItsChain() {
        assertEquals("-A afwall-wifi -m owner --uid-owner 10123 -d 10.0.0.0/8 -j RETURN",
                AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: allow net=wifi dst=10.0.0.0/8"),
                        "afwall", false));
        assertEquals("-A afwall-3g -p tcp --dport 25 -j afwall-reject",
                AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:-10: block net=mobile proto=tcp dport=25"),
                        "afwall", false));
        assertEquals("-A afwall-vpn -d 1.1.1.1 -j RETURN",
                AppRuleHelper.buildRule(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:-10: allow net=vpn dst=1.1.1.1"),
                        "afwall", false));
    }

    @Test
    public void unknownNetworkIsNotApplied() {
        // a rule from a newer version for a network this one doesn't know
        assertNull(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: allow net=satellite dst=1.2.3.4"));
        assertNull(AppRuleHelper.normalizeNetwork("satellite"));
        assertEquals(AppRuleHelper.NETWORK_ALL, AppRuleHelper.normalizeNetwork(""));
        assertNull(AppRuleHelper.normalizeAction("drop"));
        assertEquals(AppRuleHelper.ACTION_ALLOW, AppRuleHelper.normalizeAction(null));
        assertEquals(AppRuleHelper.ACTION_BLOCK, AppRuleHelper.normalizeAction("BLOCK"));
    }

    @Test
    public void appRulesBeforeGlobalRulesAndBlockBeforeAllow() {
        int appBlock = AppRuleHelper.applyOrder(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: block dst=1.2.3.4"));
        int appAllow = AppRuleHelper.applyOrder(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:10123: allow dst=1.2.3.4"));
        int anyBlock = AppRuleHelper.applyOrder(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:-10: block dst=1.2.3.4"));
        int anyAllow = AppRuleHelper.applyOrder(AppRuleHelper.parseRuleName("direct-rule:AFWallPrefs:-10: allow dst=1.2.3.4"));
        assertTrue(appBlock < appAllow);
        assertTrue(appAllow < anyBlock);
        assertTrue(anyBlock < anyAllow);
    }
}
