package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class CustomScriptTest {

    private static CustomScript.Command command(String line) {
        CustomScript.Result r = CustomScript.parse(line, "afwall10");
        assertEquals("rejected: " + r.rejected, 1, r.commands.size());
        return r.commands.get(0);
    }

    private static CustomScript.Rejected rejected(String line) {
        CustomScript.Result r = CustomScript.parse(line, "afwall");
        assertEquals("accepted: " + line, 1, r.rejected.size());
        return r.rejected.get(0);
    }

    @Test
    public void firstWordPicksFamily() {
        assertEquals(CustomScript.Family.IPV4, command("iptables -A INPUT -j ACCEPT").family);
        assertEquals(CustomScript.Family.IPV6, command("ip6tables -A INPUT -j ACCEPT").family);
        assertEquals(CustomScript.Family.IPV6, command("$IP6TABLES -A INPUT -j ACCEPT").family);
        assertEquals(CustomScript.Family.BOTH, command("$IPTABLES -A INPUT -j ACCEPT").family);
        assertEquals(CustomScript.Family.BOTH, command("${IPTABLES} -A INPUT -j ACCEPT").family);
        // a bare option line used to be accepted but then failed as a shell command
        CustomScript.Command bare = command("-A afwall -d 10.0.0.0/8 -j RETURN");
        assertEquals(CustomScript.Family.BOTH, bare.family);
        assertEquals(Arrays.asList("-A", "afwall", "-d", "10.0.0.0/8", "-j", "RETURN"), bare.args);
    }

    @Test
    public void familyDecidesPassAndBinary() {
        CustomScript.Command v4 = command("iptables -A INPUT -j ACCEPT");
        assertTrue(v4.appliesTo(false, true));
        assertFalse("IPv4 lines must not run again in the IPv6 pass", v4.appliesTo(true, true));

        CustomScript.Command v6 = command("ip6tables -A INPUT -j ACCEPT");
        assertFalse(v6.appliesTo(false, true));
        assertTrue(v6.appliesTo(true, true));
        // no IPv6 pass: run with ip6tables in the IPv4 pass
        assertTrue(v6.appliesTo(false, false));
        assertTrue(v6.usesIp6tables(false));

        CustomScript.Command both = command("$IPTABLES -A INPUT -j ACCEPT");
        assertTrue(both.appliesTo(false, true));
        assertTrue(both.appliesTo(true, true));
        assertFalse(both.usesIp6tables(false));
        assertTrue(both.usesIp6tables(true));
    }

    @Test
    public void chainVariable() {
        CustomScript.Command c = command("$IPTABLES -A \"$AFWALL_CHAIN\" -o ${AFWALL_CHAIN}x -j $AFWALL_CHAIN-wifi");
        assertEquals("afwall10", c.chain);
        assertEquals(Arrays.asList("-A", "afwall10", "-o", "afwall10x", "-j", "afwall10-wifi"), c.args);
        // single quotes are literal
        assertEquals("$AFWALL_CHAIN", command("iptables -A x -m comment --comment '$AFWALL_CHAIN' -j ACCEPT").args.get(5));
    }

    @Test
    public void tableChainAndOp() {
        CustomScript.Command c = command("iptables -t nat --insert OUTPUT 2 -p tcp --dport 53 -j REDIRECT --to-ports 5300");
        assertEquals("nat", c.table);
        assertEquals("-I", c.op);
        assertEquals("OUTPUT", c.chain);
        assertEquals("-I", c.args.get(2));
        assertEquals("filter", command("iptables -A INPUT -j DROP").table);
        assertEquals("mangle", command("iptables --table=mangle -A OUTPUT -j MARK --set-mark 1").table);
        assertNull(command("iptables -F").chain);
        assertEquals("mychain", command("iptables -N mychain").chain);
    }

    @Test
    public void deleteFormOfAddedRule() {
        assertEquals(Arrays.asList("-t", "nat", "-D", "OUTPUT", "-p", "tcp", "-j", "ACCEPT"),
                command("iptables -t nat -I OUTPUT 1 -p tcp -j ACCEPT").deleteArgs());
        assertEquals(Arrays.asList("-D", "INPUT", "-j", "ACCEPT"), command("iptables -A INPUT -j ACCEPT").deleteArgs());
        assertEquals(Arrays.asList("-D", "INPUT", "-j", "ACCEPT"), command("iptables -I INPUT -j ACCEPT").deleteArgs());
        assertNull(command("iptables -N foo").deleteArgs());
        assertTrue(command("iptables -N foo").failureIsHarmless());
        assertTrue(command("iptables -D INPUT -j ACCEPT").failureIsHarmless());
        assertFalse(command("iptables -A INPUT -j ACCEPT").failureIsHarmless());
    }

    @Test
    public void quotingRoundTrip() {
        CustomScript.Command c = command("iptables -A INPUT -j LOG --log-prefix \"drop; it's {AFL} \" ! -s 10.0.0.1");
        assertEquals("drop; it's {AFL} ", c.args.get(5));
        assertEquals("/sbin/iptables -A INPUT -j LOG --log-prefix 'drop; it'\\''s {AFL} ' '!' -s 10.0.0.1",
                CustomScript.toShell("/sbin/iptables", c.args));
    }

    @Test
    public void wordsThatUsedToBeRejected() {
        // the old keyword filter rejected these for containing "rm " / "dd "
        command("iptables -A INPUT -m comment --comment \"confirm add \" -j ACCEPT");
    }

    @Test
    public void commentsAndBlankLines() {
        CustomScript.Result r = CustomScript.parse("# allow LAN\n\n  \niptables -A INPUT -j ACCEPT  # trailing\r\n", "afwall");
        assertEquals(1, r.commands.size());
        assertTrue(r.rejected.isEmpty());
        assertEquals(4, r.commands.get(0).lineNumber);
        assertEquals(Arrays.asList("-A", "INPUT", "-j", "ACCEPT"), r.commands.get(0).args);
    }

    @Test
    public void shellSyntaxIsRejected() {
        assertEquals(CustomScript.Problem.SHELL_SYNTAX, rejected("iptables -L & reboot").problem);
        assertEquals(CustomScript.Problem.SHELL_SYNTAX, rejected("iptables -A INPUT -j ACCEPT; reboot").problem);
        assertEquals(CustomScript.Problem.SHELL_SYNTAX, rejected("iptables -A INPUT -j ACCEPT > /data/x").problem);
        // the old "only in one family" idiom: use iptables / ip6tables instead
        assertEquals(CustomScript.Problem.VARIABLE, rejected("[ $IPV6 = 0 ] && iptables -A INPUT -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.SHELL_SYNTAX, rejected("iptables -A INPUT -s $(cat /x) -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.SHELL_SYNTAX, rejected("iptables -A INPUT -s `id` -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.SHELL_SYNTAX, rejected("iptables -A INPUT \"`id`\" -j ACCEPT").problem);
        assertEquals("&&", rejected("iptables -F && reboot").detail);
    }

    @Test
    public void otherProblems() {
        assertEquals(CustomScript.Problem.NOT_IPTABLES, rejected(". /data/local/afwall.sh").problem);
        assertEquals(CustomScript.Problem.NOT_IPTABLES, rejected("reboot").problem);
        assertEquals(CustomScript.Problem.VARIABLE, rejected("iptables -A INPUT -s $HOME -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.VARIABLE, rejected("iptables -A INPUT $IPTABLES").problem);
        assertEquals(CustomScript.Problem.UNTERMINATED_QUOTE, rejected("iptables -A INPUT --comment 'x").problem);
        assertEquals(CustomScript.Problem.COMMAND_COUNT, rejected("iptables -p tcp -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.COMMAND_COUNT, rejected("iptables -A INPUT -D INPUT").problem);
        assertEquals(CustomScript.Problem.LISTING, rejected("iptables -L -n").problem);
        assertEquals(CustomScript.Problem.MISSING_CHAIN, rejected("iptables -A -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.UNKNOWN_TABLE, rejected("iptables -t natt -A OUTPUT -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.FORBIDDEN_OPTION, rejected("iptables --modprobe=/data/x -A INPUT -j ACCEPT").problem);
        assertEquals(CustomScript.Problem.FORBIDDEN_OPTION, rejected("iptables -M /data/x -A INPUT -j ACCEPT").problem);
    }

    @Test
    public void lineNumbersOfRejectedLines() {
        CustomScript.Result r = CustomScript.parse("iptables -A INPUT -j ACCEPT\nfoo\n", "afwall");
        assertEquals(1, r.commands.size());
        assertEquals(2, r.rejected.get(0).lineNumber);
        assertEquals("foo", r.rejected.get(0).line);
    }
}
