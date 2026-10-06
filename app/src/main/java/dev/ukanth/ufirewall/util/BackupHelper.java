package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.topjohnwu.superuser.Shell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.customrules.CustomRule;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogPreference;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPref;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;

/**
 * Export / import of the "v2" backup section (see {@link BackupCodec}), and the typed import of
 * v1 preferences.
 * <p>
 * The v1 sections are still written next to "v2", so older AFWall+ versions can import new
 * backups; a v2-aware version only reads "v2" when it is present.
 */
public final class BackupHelper {

    private static final String TAG = Api.TAG;
    public static final String V2_KEY = "v2";
    // direct rules that only versions with block / network rules can read correctly
    private static final String DIRECT_RULES2_KEY = "directRules2";

    /**
     * Rule list preferences, indexed by the connection type code used in backups (same codes as
     * the v1 format).
     */
    static final String[] RULE_PREFS = {
            Api.PREF_WIFI_PKG_UIDS,     // 0
            Api.PREF_3G_PKG_UIDS,       // 1
            Api.PREF_ROAMING_PKG_UIDS,  // 2
            Api.PREF_VPN_PKG_UIDS,      // 3
            Api.PREF_LAN_PKG_UIDS,      // 4
            Api.PREF_TOR_PKG_UIDS,      // 5
            Api.PREF_TETHER_PKG_UIDS,   // 6
    };

    // per-profile keys that are carried by the portable rules, or are only a cache
    private static final Set<String> PROFILE_PREFS_EXCLUDED = new HashSet<>(Arrays.asList(RULE_PREFS));
    private static final List<String> PROFILE_PREF_PREFIXES_EXCLUDED = Collections.singletonList("cache.label.");
    // int preferences that versions before v2 import as strings and then crash reading them
    // (getInt); left out of the v1 section, they are in "v2"
    private static final Set<String> V1_UNSAFE_FOR_OLD_VERSIONS = new HashSet<>(Arrays.asList("customDelay", "logPingTime"));

    private BackupHelper() {
    }

    /**
     * Result of an import, for the message shown to the user.
     */
    public static final class ImportStats {
        /** apps (per Android user) in the backup that don't exist on this device */
        final Set<String> skipped = new HashSet<>();
        public int directRules;

        public int skippedApps() {
            return skipped.size();
        }
    }

    // ---- export ----

    /**
     * @return the complete "v2" section: preferences, every profile with its settings and rules,
     * direct rules, log mutes and default connections
     */
    public static JSONObject exportFull(Context ctx) throws JSONException {
        DeviceUidMapper mapper = new DeviceUidMapper(ctx);
        JSONObject v2 = new JSONObject();
        v2.put("version", BackupCodec.VERSION);
        v2.put("global", BackupCodec.encodePrefs(G.gPrefs.getAll(), BackupCodec.NOT_BACKED_UP, null));

        JSONArray profiles = new JSONArray();
        profiles.put(exportProfile(ctx, Api.DEFAULT_PREFS_NAME,
                G.gPrefs.getString("default", Api.DEFAULT_PREFS_NAME), true, mapper));
        for (ProfileData data : ProfileHelper.getProfiles()) {
            profiles.put(exportProfile(ctx, data.getIdentifier(), data.getName(), false, mapper));
        }
        v2.put("profiles", profiles);

        // Block and network rules go in their own array: versions without them would import
        // them from "directRules" as allow rules for all networks. They skip this array instead.
        JSONArray directRules = new JSONArray();
        JSONArray directRules2 = new JSONArray();
        for (CustomRule rule : SQLite.select().from(CustomRule.class).queryList()) {
            AppRuleHelper.ParsedRule parsed = AppRuleHelper.parseRuleName(rule.getName());
            if (parsed == null) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("profile", parsed.profile);
            o.put("target", BackupCodec.encodeUid(parsed.uid, mapper));
            o.put("destination", parsed.destination);
            o.put("protocol", parsed.protocol);
            o.put("port", parsed.port);
            o.put("active", rule.isActive());
            if (parsed.isPlainAllow()) {
                directRules.put(o);
            } else {
                o.put("action", parsed.action);
                o.put("network", parsed.network);
                directRules2.put(o);
            }
        }
        v2.put("directRules", directRules);
        v2.put(DIRECT_RULES2_KEY, directRules2);

        JSONArray logMutes = new JSONArray();
        for (LogPreference pref : SQLite.select().from(LogPreference.class).queryList()) {
            JSONObject o = new JSONObject();
            o.put("target", BackupCodec.encodeUid(pref.getUid(), mapper));
            o.put("disable", pref.isDisable());
            o.put("skip", pref.isSkip());
            o.put("skipInterval", pref.getSkipInterval());
            if (pref.getAppName() != null) {
                o.put("appName", pref.getAppName());
            }
            logMutes.put(o);
        }
        v2.put("logMutes", logMutes);

        JSONArray defaults = new JSONArray();
        for (DefaultConnectionPref pref : SQLite.select().from(DefaultConnectionPref.class).queryList()) {
            JSONObject o = new JSONObject();
            // "uid" is the connection column, not an app UID
            o.put("column", pref.getUid());
            o.put("state", pref.isState());
            o.put("modeType", pref.getModeType());
            if (pref.getConnectionType() != null) {
                o.put("connectionType", pref.getConnectionType());
            }
            defaults.put(o);
        }
        v2.put("defaultConnections", defaults);
        return v2;
    }

    /**
     * @return a "v2" section with the rules of one profile only (the "rules only" export)
     */
    public static JSONObject exportRules(Context ctx, String prefName) throws JSONException {
        SharedPreferences prefs = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        JSONObject v2 = new JSONObject();
        v2.put("version", BackupCodec.VERSION);
        v2.put("rules", BackupCodec.encodeRules(ruleLists(prefs), new DeviceUidMapper(ctx)));
        v2.put("mode", prefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST));
        return v2;
    }

    private static JSONObject exportProfile(Context ctx, String identifier, String name, boolean isDefault,
                                            DeviceUidMapper mapper) throws JSONException {
        SharedPreferences prefs = ctx.getSharedPreferences(identifier, Context.MODE_PRIVATE);
        JSONObject o = new JSONObject();
        o.put("identifier", identifier);
        o.put("name", name);
        o.put("default", isDefault);
        o.put("prefs", BackupCodec.encodePrefs(prefs.getAll(), PROFILE_PREFS_EXCLUDED, PROFILE_PREF_PREFIXES_EXCLUDED));
        o.put("rules", BackupCodec.encodeRules(ruleLists(prefs), mapper));
        return o;
    }

    private static String[] ruleLists(SharedPreferences prefs) {
        String[] lists = new String[RULE_PREFS.length];
        for (int i = 0; i < RULE_PREFS.length; i++) {
            lists[i] = prefs.getString(RULE_PREFS[i], "");
        }
        return lists;
    }

    /**
     * @return v1 "prefs" array of {@code prefs}, leaving out what must not be backed up
     */
    public static JSONArray exportV1Prefs(SharedPreferences prefs) throws JSONException {
        return exportV1Prefs(prefs, null);
    }

    /**
     * @param overrides values to write instead of the stored ones (may add keys); may be null
     */
    public static JSONArray exportV1Prefs(SharedPreferences prefs, Map<String, String> overrides) throws JSONException {
        Map<String, Object> all = new TreeMap<>(prefs.getAll());
        if (overrides != null) {
            all.putAll(overrides);
        }
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            boolean overridden = overrides != null && overrides.containsKey(entry.getKey());
            if ((!overridden && (BackupCodec.NOT_BACKED_UP.contains(entry.getKey())
                    || V1_UNSAFE_FOR_OLD_VERSIONS.contains(entry.getKey()))) || entry.getValue() == null) {
                continue;
            }
            JSONObject obj = new JSONObject();
            obj.put(entry.getKey(), entry.getValue().toString());
            arr.put(obj);
        }
        return arr;
    }

    // ---- import ----

    public static ImportStats importFull(Context ctx, JSONObject v2) throws JSONException {
        ImportStats stats = new ImportStats();
        DeviceUidMapper mapper = new DeviceUidMapper(ctx);

        BackupCodec.decodePrefs(v2.optJSONObject("global"), BackupCodec.NOT_BACKED_UP, null, writer(G.gPrefs));

        // backup identifier -> identifier on this device
        Map<String, String> profileIds = new HashMap<>();
        JSONArray profiles = v2.optJSONArray("profiles");
        for (int i = 0; profiles != null && i < profiles.length(); i++) {
            JSONObject p = profiles.optJSONObject(i);
            if (p == null) {
                continue;
            }
            String srcId = p.optString("identifier", "");
            String identifier = p.optBoolean("default") || Api.DEFAULT_PREFS_NAME.equals(srcId)
                    ? Api.DEFAULT_PREFS_NAME
                    : ProfileHelper.ensureProfile(p.optString("name", srcId), srcId);
            if (identifier == null) {
                continue;
            }
            profileIds.put(srcId, identifier);
            SharedPreferences prefs = ctx.getSharedPreferences(identifier, Context.MODE_PRIVATE);
            BackupCodec.PrefWriter w = writer(prefs);
            BackupCodec.decodePrefs(p.optJSONObject("prefs"), PROFILE_PREFS_EXCLUDED, PROFILE_PREF_PREFIXES_EXCLUDED, w);
            writeRuleLists(prefs, p.optJSONArray("rules"), mapper, stats);
        }

        for (String key : new String[]{"directRules", DIRECT_RULES2_KEY}) {
            JSONArray directRules = v2.optJSONArray(key);
            for (int i = 0; directRules != null && i < directRules.length(); i++) {
                JSONObject o = directRules.optJSONObject(i);
                if (o != null && importDirectRule(o, profileIds, mapper)) {
                    stats.directRules++;
                }
            }
        }

        JSONArray logMutes = v2.optJSONArray("logMutes");
        for (int i = 0; logMutes != null && i < logMutes.length(); i++) {
            JSONObject o = logMutes.optJSONObject(i);
            Integer uid = o == null ? null : BackupCodec.decodeUid(o.optJSONObject("target"), mapper);
            if (uid == null) {
                continue;
            }
            LogPreference pref = new LogPreference();
            pref.setUid(uid);
            pref.setDisable(o.optBoolean("disable"));
            pref.setSkip(o.optBoolean("skip"));
            pref.setSkipInterval(o.optLong("skipInterval"));
            pref.setAppName(o.optString("appName", null));
            pref.setTimestamp(System.currentTimeMillis());
            pref.save();
        }
        G.clearLogMuteCache();

        JSONArray defaults = v2.optJSONArray("defaultConnections");
        for (int i = 0; defaults != null && i < defaults.length(); i++) {
            JSONObject o = defaults.optJSONObject(i);
            if (o == null) {
                continue;
            }
            DefaultConnectionPref pref = new DefaultConnectionPref();
            pref.setUid(o.optInt("column"));
            pref.setState(o.optBoolean("state"));
            pref.setModeType(o.optInt("modeType"));
            pref.setConnectionType(o.optString("connectionType", null));
            pref.save();
        }
        return stats;
    }

    /**
     * Import the rules of a "rules only" v2 section into {@code prefName}.
     */
    public static ImportStats importRules(Context ctx, JSONObject v2, String prefName) {
        ImportStats stats = new ImportStats();
        SharedPreferences prefs = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        String mode = v2.optString("mode", "");
        if (Api.MODE_WHITELIST.equals(mode) || Api.MODE_BLACKLIST.equals(mode)) {
            prefs.edit().putString(Api.PREF_MODE, mode).apply();
        }
        writeRuleLists(prefs, v2.optJSONArray("rules"), new DeviceUidMapper(ctx), stats);
        return stats;
    }

    private static void writeRuleLists(SharedPreferences prefs, JSONArray rules, DeviceUidMapper mapper,
                                       ImportStats stats) {
        String[] lists = BackupCodec.decodeRules(rules, RULE_PREFS.length, mapper, stats.skipped);
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < RULE_PREFS.length; i++) {
            edit.putString(RULE_PREFS[i], lists[i]);
        }
        edit.apply();
    }

    private static boolean importDirectRule(JSONObject o, Map<String, String> profileIds, DeviceUidMapper mapper) {
        String profile = profileIds.get(o.optString("profile", Api.DEFAULT_PREFS_NAME));
        Integer uid = BackupCodec.decodeUid(o.optJSONObject("target"), mapper);
        if (profile == null || uid == null || !AppRuleHelper.supportsUid(uid)) {
            return false;
        }
        // the backup file is untrusted: rebuild the rule from validated fields
        String destination = o.optString("destination", "").trim();
        String port = o.optString("port", "").trim();
        String protocol = AppRuleHelper.normalizeProtocol(o.optString("protocol", "any"));
        String action = AppRuleHelper.normalizeAction(o.optString("action", AppRuleHelper.ACTION_ALLOW));
        String network = AppRuleHelper.normalizeNetwork(o.optString("network", AppRuleHelper.NETWORK_ALL));
        if (action == null || network == null
                || (!destination.isEmpty() && !AppRuleHelper.isValidDestination(destination))
                || (!port.isEmpty() && !AppRuleHelper.isValidPortRange(port))
                || (!port.isEmpty() && "any".equals(protocol))) {
            return false;
        }
        String rule = Api.validateCustomRuleForStorage(
                AppRuleHelper.buildStoredRule(uid, action, network, destination, protocol, port));
        if (rule == null) {
            return false;
        }
        String name = AppRuleHelper.buildRuleName(profile, uid, action, network, destination, protocol, port);
        CustomRule existing = SQLite.select().from(CustomRule.class)
                .where(dev.ukanth.ufirewall.customrules.CustomRule_Table.name.eq(name)).querySingle();
        CustomRule customRule = existing != null ? existing : new CustomRule(name, rule);
        customRule.setRule(rule);
        customRule.setActive(o.optBoolean("active", true));
        customRule.save();
        return true;
    }

    /**
     * Write the preferences of a v1 "prefs" / "profilePrefs" array with the types the app reads
     * them with (v1 stored every value as a string).
     */
    public static void importV1Prefs(JSONArray prefArray, SharedPreferences prefs) throws JSONException {
        importV1Prefs(prefArray, prefs, false);
    }

    /**
     * @param profilePrefs true for a profile's preferences: its rule lists are left out, as they
     *                     hold UIDs of the source device (the rules sections carry them portably)
     */
    public static void importV1Prefs(JSONArray prefArray, SharedPreferences prefs, boolean profilePrefs)
            throws JSONException {
        if (prefArray == null) {
            return;
        }
        Map<String, ?> current = prefs.getAll();
        BackupCodec.PrefWriter w = writer(prefs);
        for (int i = 0; i < prefArray.length(); i++) {
            JSONObject prefObj = prefArray.getJSONObject(i);
            java.util.Iterator<String> keys = prefObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (profilePrefs && (PROFILE_PREFS_EXCLUDED.contains(key)
                        || key.startsWith(PROFILE_PREF_PREFIXES_EXCLUDED.get(0)))) {
                    continue;
                }
                BackupCodec.writeV1Pref(key, prefObj.optString(key, null), current.get(key), w);
            }
        }
    }

    private static BackupCodec.PrefWriter writer(final SharedPreferences prefs) {
        return new BackupCodec.PrefWriter() {
            @Override
            public void putBoolean(String key, boolean value) {
                prefs.edit().putBoolean(key, value).apply();
            }

            @Override
            public void putInt(String key, int value) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void putLong(String key, long value) {
                prefs.edit().putLong(key, value).apply();
            }

            @Override
            public void putFloat(String key, float value) {
                prefs.edit().putFloat(key, value).apply();
            }

            @Override
            public void putString(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }

            @Override
            public void putStringSet(String key, Set<String> value) {
                prefs.edit().putStringSet(key, value).apply();
            }
        };
    }

    // ---- device lookups ----

    /**
     * {@link BackupCodec.UidMapper} for this device. PackageManager doesn't show every package
     * (package visibility), so root "pm list packages -U" is used as a fallback. Root lookups are
     * done lazily and at most once.
     */
    public static final class DeviceUidMapper implements BackupCodec.UidMapper {
        private static final Pattern PACKAGE_UID = Pattern.compile("package:(\\S+) uid:(\\d+)");
        private static final Pattern USER_TYPE = Pattern.compile("id=(\\d+),.*?type=([\\w.]+)");

        private final PackageManager pm;
        private final Map<String, Integer> specialUids;
        private Map<Integer, List<String>> rootPackagesByAppId;
        private Map<String, Integer> rootAppIdByPackage;
        private Map<Integer, String> userTypes;
        private Map<String, Integer> appIdBySharedUser;

        public DeviceUidMapper(Context ctx) {
            pm = ctx.getPackageManager();
            specialUids = Api.getSpecialAppUids();
        }

        /**
         * @return app id of a shared user id ("com.google.uid.shared", ...) on this device, or null
         */
        public Integer appIdForSharedUser(String sharedUserId) {
            if (appIdBySharedUser == null) {
                appIdBySharedUser = new HashMap<>();
                try {
                    for (android.content.pm.PackageInfo info : pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
                        if (info.sharedUserId != null && info.applicationInfo != null) {
                            appIdBySharedUser.put(info.sharedUserId, info.applicationInfo.uid % 100000);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Backup: unable to list shared user ids: " + e.getMessage());
                }
            }
            return appIdBySharedUser.get(sharedUserId);
        }

        @Override
        public List<String> packagesForUid(int uid) {
            List<String> result = new ArrayList<>();
            try {
                String[] pkgs = pm.getPackagesForUid(uid);
                if (pkgs != null) {
                    result.addAll(Arrays.asList(pkgs));
                }
            } catch (Exception ignored) {
            }
            if (result.isEmpty() && uid % 100000 >= android.os.Process.FIRST_APPLICATION_UID) {
                loadRootPackages();
                List<String> pkgs = rootPackagesByAppId.get(uid % 100000);
                if (pkgs != null) {
                    result.addAll(pkgs);
                }
            }
            return result;
        }

        @Override
        public String specialNameForUid(int uid) {
            String found = null;
            for (Map.Entry<String, Integer> e : specialUids.entrySet()) {
                // several names can't share a UID in practice; pick a stable one if they do
                if (e.getValue() != null && e.getValue() == uid && (found == null || e.getKey().compareTo(found) < 0)) {
                    found = e.getKey();
                }
            }
            return found;
        }

        @Override
        public Integer specialUid(String name) {
            Integer uid = specialUids.get(name);
            // -1: the account doesn't exist on this device
            return uid == null || uid == -1 ? null : uid;
        }

        @Override
        public Integer appIdForPackage(String pkg) {
            if (pkg == null || pkg.isEmpty()) {
                return null;
            }
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                return info.uid % 100000;
            } catch (Exception ignored) {
            }
            loadRootPackages();
            return rootAppIdByPackage.get(pkg);
        }

        @Override
        public Set<Integer> users() {
            loadUserTypes();
            return userTypes.keySet();
        }

        @Override
        public String userType(int userId) {
            loadUserTypes();
            return userTypes.get(userId);
        }

        private void loadUserTypes() {
            if (userTypes != null) {
                return;
            }
            userTypes = new HashMap<>();
            try {
                Shell.Result result = Shell.cmd("cmd user list -v").exec();
                for (String line : result.getOut()) {
                    Matcher m = USER_TYPE.matcher(line);
                    if (m.find()) {
                        userTypes.put(Integer.parseInt(m.group(1)), m.group(2));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Backup: unable to list users: " + e.getMessage());
            }
        }

        private void loadRootPackages() {
            if (rootPackagesByAppId != null) {
                return;
            }
            rootPackagesByAppId = new HashMap<>();
            rootAppIdByPackage = new HashMap<>();
            List<String> commands = new ArrayList<>();
            commands.add("pm list packages -U");
            for (Integer user : users()) {
                if (user != 0) {
                    commands.add("pm list packages -U --user " + user);
                }
            }
            for (String command : commands) {
                try {
                    Shell.Result result = Shell.cmd(command).exec();
                    for (String line : result.getOut()) {
                        Matcher m = PACKAGE_UID.matcher(line);
                        if (!m.find()) {
                            continue;
                        }
                        String pkg = m.group(1);
                        int appId = Integer.parseInt(m.group(2)) % 100000;
                        if (!rootAppIdByPackage.containsKey(pkg)) {
                            rootAppIdByPackage.put(pkg, appId);
                            List<String> pkgs = rootPackagesByAppId.get(appId);
                            if (pkgs == null) {
                                pkgs = new ArrayList<>();
                                rootPackagesByAppId.put(appId, pkgs);
                            }
                            pkgs.add(pkg);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Backup: unable to list packages: " + e.getMessage());
                }
            }
        }
    }
}
