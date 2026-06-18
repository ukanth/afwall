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

public final class AppRuleHelper {

    private static final String RULE_PREFIX = "direct-rule:";
    private static final String DEFAULT_PROFILE = "AFWallPrefs";
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:/(?:[0-9]|[1-2][0-9]|3[0-2]))?$");
    private static final Pattern PORT_PATTERN = Pattern.compile(
            "^(?:[1-9][0-9]{0,4})(?::(?:[1-9][0-9]{0,4}))?$");

    private AppRuleHelper() {
    }

    public static String rulePrefixForUid(int uid) {
        return RULE_PREFIX + currentProfileName() + ":" + uid + ":";
    }

    public static String buildAllowRule(int uid, String destinationValue, String protocolValue, String portValue) {
        String destination = normalize(destinationValue);
        String protocol = normalize(protocolValue).toLowerCase(Locale.US);
        String port = normalize(portValue);

        StringBuilder rule = new StringBuilder("-A afwall");
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
        String destination = normalize(destinationValue);
        String protocol = normalize(protocolValue).toLowerCase(Locale.US);
        String port = normalize(portValue);

        StringBuilder name = new StringBuilder(rulePrefixForUid(uid));
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

    public static boolean isValidDestination(String value) {
        return IPV4_PATTERN.matcher(normalize(value)).matches();
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
