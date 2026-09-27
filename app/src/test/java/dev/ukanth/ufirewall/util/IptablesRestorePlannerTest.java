package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class IptablesRestorePlannerTest {

    private static IptablesRestorePlanner.Plan plan(String... cmds) {
        return IptablesRestorePlanner.plan(Arrays.asList(cmds));
    }

    @Test
    public void fullApplyShape() {
        IptablesRestorePlanner.Plan p = plan(
                "-P INPUT ACCEPT",
                "-P OUTPUT DROP",
                "#NOCHK# -N afwall",
                "#NOCHK# -F afwall",
                "#NOCHK# -N afwall-wifi",
                "#NOCHK# -F afwall-wifi",
                "#NOCHK# -D OUTPUT -j afwall",
                "-I OUTPUT 1 -j afwall",
                "-A afwall -o lo -j RETURN",
                "-A afwall-wifi -m owner --uid-owner 10123 -j RETURN",
                "#NOCHK# -A afwall -o wlan+ -j afwall-wifi",
                "-A afwall-wifi -j REJECT",
                "-P OUTPUT ACCEPT");
        assertNotNull(p);
        assertEquals("*filter\n"
                + ":afwall - [0:0]\n"
                + ":afwall-wifi - [0:0]\n"
                + "-A afwall -o lo -j RETURN\n"
                + "-A afwall -o wlan+ -j afwall-wifi\n"
                + "-A afwall-wifi -m owner --uid-owner 10123 -j RETURN\n"
                + "-A afwall-wifi -j REJECT\n"
                + "COMMIT\n", p.restoreInput);
        // built-in chain commands keep their order (DROP still covers the jump re-insert)
        assertEquals(Arrays.asList(
                "-P INPUT ACCEPT",
                "-P OUTPUT DROP",
                "#NOCHK# -D OUTPUT -j afwall",
                "-I OUTPUT 1 -j afwall",
                "-P OUTPUT ACCEPT"), p.postCommands);
    }

    @Test
    public void laterFlushDropsEarlierRules() {
        IptablesRestorePlanner.Plan p = plan(
                "#NOCHK# -N afwall-wifi-fork",
                "-A afwall-wifi-fork -j stale",
                "-F afwall-wifi-fork",
                "-A afwall-wifi-fork -j afwall-wifi-wan");
        assertNotNull(p);
        assertEquals("*filter\n:afwall-wifi-fork - [0:0]\n-A afwall-wifi-fork -j afwall-wifi-wan\nCOMMIT\n",
                p.restoreInput);
    }

    @Test
    public void insertAndDeleteAreReplayed() {
        IptablesRestorePlanner.Plan p = plan(
                "#NOCHK# -N c",
                "-A c -j A",
                "-A c -j B",
                "-I c 2 -j X",
                "-I c -j FIRST",
                "#NOCHK# -D c -j missing",
                "-D c -j A");
        assertNotNull(p);
        assertEquals("*filter\n:c - [0:0]\n-A c -j FIRST\n-A c -j X\n-A c -j B\nCOMMIT\n", p.restoreInput);
    }

    @Test
    public void natTableGetsItsOwnSection() {
        IptablesRestorePlanner.Plan p = plan(
                "#NOCHK# -N afwall",
                "#NOCHK# -t nat -N afwall-tor-check",
                "-t nat -F afwall-tor-check",
                "#NOCHK# -t nat -D OUTPUT -j afwall",
                "-t nat -I OUTPUT 1 -j afwall",
                "-t nat -A afwall-tor-check -j RETURN");
        assertNotNull(p);
        assertEquals("*filter\n:afwall - [0:0]\nCOMMIT\n"
                + "*nat\n:afwall-tor-check - [0:0]\n-A afwall-tor-check -j RETURN\nCOMMIT\n", p.restoreInput);
        assertEquals(Arrays.asList("#NOCHK# -t nat -D OUTPUT -j afwall", "-t nat -I OUTPUT 1 -j afwall"),
                p.postCommands);
    }

    @Test
    public void quotedArgumentsArePassedThrough() {
        IptablesRestorePlanner.Plan p = plan(
                "#NOCHK# -N afwall-reject",
                "-A afwall-reject -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40");
        assertNotNull(p);
        assertEquals("*filter\n:afwall-reject - [0:0]\n"
                + "-A afwall-reject -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40\nCOMMIT\n", p.restoreInput);
    }

    @Test
    public void fallsBackForWhatItCannotReproduce() {
        // custom script lines are arbitrary shell
        assertNull(plan("#NOCHK# -N afwall", "#LITERAL# iptables -A afwall -j ACCEPT"));
        // appending to a chain this list didn't create: its current rules are unknown
        assertNull(plan("-A afwall -j RETURN"));
        // plain -F of a chain that may not exist would fail
        assertNull(plan("-F afwall"));
        // deleting a rule that isn't there would fail
        assertNull(plan("#NOCHK# -N c", "-D c -j nope"));
        // unsupported operations / tables
        assertNull(plan("#NOCHK# -N c", "-X c"));
        assertNull(plan("-t mangle -N c"));
        assertNull(plan("-Z OUTPUT"));
        // insert position out of range
        assertNull(plan("#NOCHK# -N c", "-I c 3 -j X"));
        // nothing for the restore at all
        assertNull(plan("-P OUTPUT ACCEPT"));
    }

    @Test
    public void builtinChainRulesGoToPostCommands() {
        List<String> post = plan("#NOCHK# -N afwall-input", "#NOCHK# -D INPUT -j afwall-input",
                "-I INPUT 1 -j afwall-input").postCommands;
        assertEquals(Arrays.asList("#NOCHK# -D INPUT -j afwall-input", "-I INPUT 1 -j afwall-input"), post);
    }
}
