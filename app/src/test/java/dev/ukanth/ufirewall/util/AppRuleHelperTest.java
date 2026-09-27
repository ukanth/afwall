package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertFalse;
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
}
