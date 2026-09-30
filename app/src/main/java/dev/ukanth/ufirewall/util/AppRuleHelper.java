package dev.ukanth.ufirewall.util;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.customrules.CustomRule;
import dev.ukanth.ufirewall.customrules.CustomRule_Table;
import dev.ukanth.ufirewall.log.Log;

public final class AppRuleHelper {

    private static final String RULE_PREFIX = "direct-rule:";
    private static final String DEFAULT_PROFILE = "AFWallPrefs";
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:/(?:[0-9]|[1-2][0-9]|3[0-2]))?$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*$");
    private static final Pattern PORT_PATTERN = Pattern.compile(
            "^(?:[1-9][0-9]{0,4})(?::(?:[1-9][0-9]{0,4}))?$");

    private AppRuleHelper() {
    }

    public static String rulePrefixForUid(int uid) {
        return rulePrefixForUid(currentProfileName(), uid);
    }

    private static String rulePrefixForUid(String profile, int uid) {
        return RULE_PREFIX + profile + ":" + uid + ":";
    }

    /**
     * A direct rule, as encoded in its name.
     */
    public static final class ParsedRule {
        public final String profile;
        public final int uid;
        public final String destination;
        public final String protocol;
        public final String port;

        ParsedRule(String profile, int uid, String destination, String protocol, String port) {
            this.profile = profile;
            this.uid = uid;
            this.destination = destination;
            this.protocol = protocol;
            this.port = port;
        }
    }

    /**
     * Parse "direct-rule:&lt;profile&gt;:&lt;uid&gt;: allow dst=.. proto=.. dport=.." (or the legacy
     * form without the profile, which belongs to the default profile).
     *
     * @return null if {@code name} is not a direct rule
     */
    public static ParsedRule parseRuleName(String name) {
        if (name == null || !name.startsWith(RULE_PREFIX)) {
            return null;
        }
        String profile;
        int uidStart;
        if (isLegacyRuleName(name)) {
            profile = DEFAULT_PROFILE;
            uidStart = RULE_PREFIX.length();
        } else {
            int sep = name.indexOf(':', RULE_PREFIX.length());
            if (sep <= RULE_PREFIX.length()) {
                return null;
            }
            profile = name.substring(RULE_PREFIX.length(), sep);
            uidStart = sep + 1;
        }
        int uid = parseUidAfterPrefix(name, uidStart);
        if (uid == Integer.MIN_VALUE) {
            return null;
        }
        String destination = "";
        String protocol = "any";
        String port = "";
        for (String token : name.substring(name.indexOf(':', uidStart) + 1).trim().split("\\s+")) {
            if (token.startsWith("dst=")) {
                destination = token.substring(4);
            } else if (token.startsWith("proto=")) {
                protocol = token.substring(6);
            } else if (token.startsWith("dport=")) {
                port = token.substring(6);
            }
        }
        return new ParsedRule(profile, uid, destination, protocol, port);
    }

    /**
     * @return true if direct rules can be made for {@code uid}: an app/system UID or "any app".
     * The other special entries (kernel, tethering, NTP, ...) have no UID iptables can match, a
     * rule for them would break the whole rule load.
     */
    public static boolean supportsUid(int uid) {
        return uid >= 0 || uid == Api.SPECIAL_UID_ANY;
    }

    /**
     * The rule as stored with the direct rule (shown in the rule list, and applied by versions
     * before 4.2.0); the rules that are applied are built by {@link #buildRule}.
     */
    public static String buildAllowRule(int uid, String destinationValue, String protocolValue, String portValue) {
        return buildRule("afwall", uid, destinationValue, protocolValue, portValue);
    }

    /**
     * @param chain main chain of the user ("afwall", or "afwall&lt;userId&gt;" in multi-user mode)
     * @return the rule for the IPv4 or IPv6 table, or null if the rule doesn't apply to it (its
     * destination is of the other family) or can't be made
     */
    public static String buildRule(ParsedRule parsed, String chain, boolean ipv6) {
        if (parsed == null || !supportsUid(parsed.uid)) {
            return null;
        }
        String destination = normalize(parsed.destination);
        if (!destination.isEmpty() && isIpv6Destination(destination) != ipv6) {
            return null;
        }
        return buildRule(chain, parsed.uid, destination, parsed.protocol, parsed.port);
    }

    private static String buildRule(String chain, int uid, String destinationValue, String protocolValue,
                                    String portValue) {
        String destination = normalize(destinationValue);
        String protocol = normalize(protocolValue).toLowerCase(Locale.US);
        String port = normalize(portValue);

        StringBuilder rule = new StringBuilder("-A ").append(chain);
        if (uid != Api.SPECIAL_UID_ANY) {
            rule.append(" -m owner --uid-owner ").append(uid);
        }
        if (!destination.isEmpty()) {
            rule.append(" -d ").append(destination);
        }
        if (!"any".equals(protocol) && !protocol.isEmpty()) {
            rule.append(" -p ").append(protocol);
        }
        if (!port.isEmpty()) {
            rule.append(" --dport ").append(port);
        }
        rule.append(" -j RETURN");
        return rule.toString();
    }

    public static String buildAllowRuleName(int uid, String destinationValue, String protocolValue, String portValue) {
        return buildAllowRuleName(currentProfileName(), uid, destinationValue, protocolValue, portValue);
    }

    public static String buildAllowRuleName(String profile, int uid, String destinationValue, String protocolValue,
                                            String portValue) {
        String destination = normalize(destinationValue);
        String protocol = normalize(protocolValue).toLowerCase(Locale.US);
        String port = normalize(portValue);

        StringBuilder name = new StringBuilder(rulePrefixForUid(profile, uid));
        name.append(" allow");
        if (!destination.isEmpty()) {
            name.append(" dst=").append(destination);
        }
        if (!"any".equals(protocol) && !protocol.isEmpty()) {
            name.append(" proto=").append(protocol);
        }
        if (!port.isEmpty()) {
            name.append(" dport=").append(port);
        }
        return name.toString();
    }

    /**
     * @return true for an IPv4 or IPv6 address or CIDR range
     */
    public static boolean isValidDestination(String value) {
        String v = normalize(value);
        return IPV4_PATTERN.matcher(v).matches() || isValidIpv6(v);
    }

    public static boolean isIpv6Destination(String value) {
        return normalize(value).contains(":");
    }

    private static boolean isValidIpv6(String value) {
        String address = value;
        int slash = value.indexOf('/');
        if (slash >= 0) {
            address = value.substring(0, slash);
            try {
                int prefix = Integer.parseInt(value.substring(slash + 1));
                if (prefix < 0 || prefix > 128) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (!IPV6_CHARS.matcher(address).matches()) {
            return false;
        }
        try {
            // a literal with ':' is parsed as IPv6, never looked up (IPv4-mapped addresses come
            // back as IPv4 objects, ip6tables takes them as IPv6)
            return java.net.InetAddress.getByName(address) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidPortRange(String value) {
        String port = normalize(value);
        if (!PORT_PATTERN.matcher(port).matches()) {
            return false;
        }
        String[] parts = port.split(":");
        int start = parsePort(parts[0]);
        int end = parts.length == 2 ? parsePort(parts[1]) : start;
        return start >= 1 && start <= 65535 && end >= 1 && end <= 65535 && start <= end;
    }

    public static String normalizeProtocol(String protocolValue) {
        String protocol = normalize(protocolValue).toLowerCase(Locale.US);
        if ("tcp".equals(protocol) || "udp".equals(protocol)) {
            return protocol;
        }
        return "any";
    }

    public static List<CustomRule> getRulesForUid(int uid) {
        try {
            List<CustomRule> rules = SQLite.select()
                    .from(CustomRule.class)
                    .queryList();
            List<CustomRule> matchingRules = new java.util.ArrayList<>();
            String currentPrefix = rulePrefixForUid(uid);
            String legacyPrefix = legacyRulePrefixForUid(uid);
            for (CustomRule rule : rules) {
                String name = rule.getName();
                if (name == null) {
                    continue;
                }
                if (name.startsWith(currentPrefix) || (isDefaultProfile() && name.startsWith(legacyPrefix))) {
                    matchingRules.add(rule);
                }
            }
            return matchingRules;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static boolean belongsToCurrentProfile(CustomRule customRule) {
        if (customRule == null) {
            return false;
        }
        String name = customRule.getName();
        if (name == null || !name.startsWith(RULE_PREFIX)) {
            return false;
        }
        if (name.startsWith(RULE_PREFIX + currentProfileName() + ":")) {
            return true;
        }
        return isDefaultProfile() && isLegacyRuleName(name);
    }

    public static String displayNameForRule(int uid, String ruleName) {
        if (ruleName == null) {
            return "";
        }
        String currentPrefix = rulePrefixForUid(uid);
        if (ruleName.startsWith(currentPrefix)) {
            return ruleName.substring(currentPrefix.length());
        }
        String legacyPrefix = legacyRulePrefixForUid(uid);
        if (ruleName.startsWith(legacyPrefix)) {
            return ruleName.substring(legacyPrefix.length());
        }
        return ruleName;
    }

    public static boolean hasRulesForUid(int uid) {
        return !getRulesForUid(uid).isEmpty();
    }

    public static boolean hasActiveRulesForUid(int uid) {
        for (CustomRule rule : getRulesForUid(uid)) {
            if (rule.isActive()) {
                return true;
            }
        }
        return false;
    }

    public static Set<Integer> getRuleUidsForCurrentProfile(boolean activeOnly) {
        Set<Integer> uids = new HashSet<>();
        try {
            List<CustomRule> rules = activeOnly
                    ? SQLite.select().from(CustomRule.class).where(CustomRule_Table.active.eq(true)).queryList()
                    : SQLite.select().from(CustomRule.class).queryList();
            for (CustomRule rule : rules) {
                if (activeOnly && !rule.isActive()) {
                    continue;
                }
                if (!belongsToCurrentProfile(rule)) {
                    continue;
                }
                int uid = uidFromRuleName(rule.getName());
                if (uid != Integer.MIN_VALUE) {
                    uids.add(uid);
                }
            }
        } catch (Exception e) {
            return Collections.emptySet();
        }
        return uids;
    }

    /**
     * Delete the direct rules of {@code uid} in every profile. Used when an app is uninstalled,
     * so a later app that is given the same UID does not inherit them.
     *
     * @return number of rules deleted
     */
    public static int deleteRulesForUidInAllProfiles(int uid) {
        int deleted = 0;
        try {
            for (CustomRule rule : SQLite.select().from(CustomRule.class).queryList()) {
                if (isRuleForUidInAnyProfile(rule.getName(), uid)) {
                    rule.delete();
                    deleted++;
                }
            }
        } catch (Exception e) {
            Log.e(Api.TAG, "Unable to delete direct rules for uid " + uid, e);
        }
        return deleted;
    }

    /**
     * @return true if {@code name} is a direct rule of {@code uid}, in any profile:
     * "direct-rule:&lt;profile&gt;:&lt;uid&gt;:..." or legacy "direct-rule:&lt;uid&gt;:..."
     */
    static boolean isRuleForUidInAnyProfile(String name, int uid) {
        if (name == null || !name.startsWith(RULE_PREFIX)) {
            return false;
        }
        String uidStr = String.valueOf(uid);
        String[] parts = name.substring(RULE_PREFIX.length()).split(":");
        return isLegacyRuleName(name)
                ? parts.length > 0 && uidStr.equals(parts[0])
                : parts.length > 1 && uidStr.equals(parts[1]);
    }

    /**
     * Delete the direct rules of a deleted profile, so a new profile with the same name doesn't
     * get them.
     */
    public static int deleteRulesForProfile(String profile) {
        String id = normalize(profile);
        if (id.isEmpty() || DEFAULT_PROFILE.equals(id)) {
            return 0; // the default profile can't be deleted
        }
        int deleted = 0;
        try {
            for (CustomRule rule : SQLite.select().from(CustomRule.class).queryList()) {
                ParsedRule parsed = parseRuleName(rule.getName());
                if (parsed != null && parsed.profile.equals(id)) {
                    rule.delete();
                    deleted++;
                }
            }
        } catch (Exception e) {
            Log.e(Api.TAG, "Unable to delete direct rules of profile " + id, e);
        }
        return deleted;
    }

    /**
     * Copy the direct rules of a profile to a cloned one.
     */
    public static int copyRulesToProfile(String fromProfile, String toProfile) {
        String from = profileId(fromProfile);
        String to = profileId(toProfile);
        int copied = 0;
        if (from.equals(to)) {
            return 0;
        }
        try {
            for (CustomRule rule : SQLite.select().from(CustomRule.class).queryList()) {
                ParsedRule parsed = parseRuleName(rule.getName());
                if (parsed == null || !parsed.profile.equals(from)) {
                    continue;
                }
                String name = buildAllowRuleName(to, parsed.uid, parsed.destination, parsed.protocol, parsed.port);
                CustomRule copy = SQLite.select().from(CustomRule.class)
                        .where(CustomRule_Table.name.eq(name)).querySingle();
                if (copy == null) {
                    copy = new CustomRule(name, rule.getRule());
                }
                copy.setRule(rule.getRule());
                copy.setActive(rule.isActive());
                copy.save();
                copied++;
            }
        } catch (Exception e) {
            Log.e(Api.TAG, "Unable to copy direct rules from " + from + " to " + to, e);
        }
        return copied;
    }

    /**
     * The default profile has an empty identifier in some places; its rules use "AFWallPrefs".
     */
    private static String profileId(String profile) {
        String id = normalize(profile);
        return id.isEmpty() ? DEFAULT_PROFILE : id;
    }

    public static void setRulesActiveForUid(int uid, boolean active) {
        for (CustomRule rule : getRulesForUid(uid)) {
            rule.setActive(active);
            rule.save();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String currentProfileName() {
        String profileName = Api.PREFS_NAME;
        if (profileName == null || profileName.trim().isEmpty()) {
            profileName = G.storedProfile();
        }
        return normalize(profileName);
    }

    private static boolean isDefaultProfile() {
        return DEFAULT_PROFILE.equals(currentProfileName());
    }

    private static String legacyRulePrefixForUid(int uid) {
        return RULE_PREFIX + uid + ":";
    }

    private static boolean isLegacyRuleName(String name) {
        int start = RULE_PREFIX.length();
        int separator = name.indexOf(':', start);
        if (separator <= start) {
            return false;
        }
        try {
            Integer.parseInt(name.substring(start, separator));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int uidFromRuleName(String name) {
        if (name == null || !name.startsWith(RULE_PREFIX)) {
            return Integer.MIN_VALUE;
        }
        String currentPrefix = RULE_PREFIX + currentProfileName() + ":";
        if (name.startsWith(currentPrefix)) {
            return parseUidAfterPrefix(name, currentPrefix.length());
        }
        if (isDefaultProfile() && isLegacyRuleName(name)) {
            return parseUidAfterPrefix(name, RULE_PREFIX.length());
        }
        return Integer.MIN_VALUE;
    }

    private static int parseUidAfterPrefix(String name, int start) {
        int end = name.indexOf(':', start);
        if (end <= start) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(name.substring(start, end));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
