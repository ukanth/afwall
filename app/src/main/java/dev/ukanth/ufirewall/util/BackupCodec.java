package dev.ukanth.ufirewall.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Portable form of the backup data (the "v2" section of an export).
 * <p>
 * A UID is only meaningful on the device it came from, so rules are stored by what the UID
 * stands for: a special entry name (kernel, root, ...), a fixed system app id, or the packages
 * that own it, plus the Android user (work profile, Private Space, ...). Preferences are stored
 * with their type.
 * <p>
 * Also holds the rules for reading the preferences of v1 backups, which were written without
 * types. Kept free of Android dependencies (org.json only) so it can be unit tested.
 */
public final class BackupCodec {

    public static final int VERSION = 2;

    private static final int PER_USER_RANGE = 100000;
    private static final int FIRST_APPLICATION_UID = 10000;

    /**
     * Preferences that are never exported or imported: device state, license, and the app lock
     * (its password/pattern are not in the backup, so restoring only the setting would turn the
     * lock on without a way to unlock, or silently off).
     */
    public static final Set<String> NOT_BACKED_UP = new HashSet<>(Arrays.asList(
            "appVersion", "hasRoot", "storedProfile", "storedPid", "sort", "fixLeak", "enableLogService",
            "logChains", "kingDetect", "fingerprintEnabled", "passSetting", "profilePwd", "pwdEncrypt",
            "ipurchaseddonatekey", "profilesmigrated", "plusprofiles"));

    // v1 wrote every preference as a string; these are known to be stored as other types
    private static final Set<String> V1_INT_KEYS = new HashSet<>(Arrays.asList("customDelay", "logPingTime"));
    private static final Set<String> V1_LONG_KEYS = new HashSet<>(Arrays.asList("multiUserId"));
    // and these are strings even when they look like numbers
    private static final Set<String> V1_STRING_KEYS = new HashSet<>(Arrays.asList(
            "patternMax", "widgetX", "widgetY", "notification_priority", "default", "profile1", "profile2",
            "profile3", "BlockMode", "CustomScript", "CustomScript2"));

    /**
     * Device lookups needed to convert between UIDs and their portable form.
     */
    public interface UidMapper {
        /** @return packages that own {@code uid} (empty if unknown) */
        List<String> packagesForUid(int uid);

        /** @return special entry name ("dev.afwall.special...") of {@code uid}, or null */
        String specialNameForUid(int uid);

        /** @return UID of a special entry on this device, or null if it doesn't exist here */
        Integer specialUid(String name);

        /** @return app id (uid % 100000) of an installed package, or null if not installed */
        Integer appIdForPackage(String pkg);

        /** @return Android user ids on this device; empty if they can't be determined */
        Set<Integer> users();

        /** @return type of an Android user ("full.SYSTEM", "profile.MANAGED", ...), or null */
        String userType(int userId);
    }

    /**
     * Receives decoded preferences.
     */
    public interface PrefWriter {
        void putBoolean(String key, boolean value);

        void putInt(String key, int value);

        void putLong(String key, long value);

        void putFloat(String key, float value);

        void putString(String key, String value);

        void putStringSet(String key, Set<String> value);
    }

    private BackupCodec() {
    }

    // ---- UIDs ----

    public static JSONObject encodeUid(int uid, UidMapper m) throws JSONException {
        JSONObject o = new JSONObject();
        // kept for information and for fixed (negative) special UIDs
        o.put("uid", uid);
        String special = m.specialNameForUid(uid);
        if (special != null) {
            o.put("special", special);
        }
        if (uid < 0) {
            return o;
        }
        int user = uid / PER_USER_RANGE;
        int appId = uid % PER_USER_RANGE;
        if (user != 0) {
            o.put("user", user);
            String type = m.userType(user);
            if (type != null) {
                o.put("userType", type);
            }
        }
        if (appId < FIRST_APPLICATION_UID) {
            // system UIDs are the same on every device
            o.put("appId", appId);
        }
        List<String> pkgs = m.packagesForUid(uid);
        if (pkgs != null && !pkgs.isEmpty()) {
            o.put("packages", new JSONArray(new TreeSet<>(pkgs)));
        }
        return o;
    }

    /**
     * @return the UID of the entry on this device, or null when it doesn't exist here (app not
     * installed, no matching user). An app UID is never taken over as is: on another device it
     * belongs to a different app.
     */
    public static Integer decodeUid(JSONObject o, UidMapper m) {
        if (o == null) {
            return null;
        }
        String special = o.optString("special", "");
        if (!special.isEmpty()) {
            Integer uid = m.specialUid(special);
            if (uid != null) {
                return uid;
            }
        }
        int srcUid = o.optInt("uid", Integer.MIN_VALUE);
        if (srcUid < 0) {
            // special UIDs below 0 are fixed; MIN_VALUE = missing
            return srcUid == Integer.MIN_VALUE ? null : srcUid;
        }
        Integer user = mapUser(o.optInt("user", 0), o.has("userType") ? o.optString("userType") : null, m);
        if (user == null) {
            return null;
        }
        if (o.has("appId")) {
            int appId = o.optInt("appId", -1);
            return appId < 0 ? null : user * PER_USER_RANGE + appId;
        }
        JSONArray pkgs = o.optJSONArray("packages");
        if (pkgs != null) {
            for (int i = 0; i < pkgs.length(); i++) {
                Integer appId = m.appIdForPackage(pkgs.optString(i));
                if (appId != null) {
                    return user * PER_USER_RANGE + appId;
                }
            }
        }
        return null;
    }

    /**
     * Map an Android user of the source device to this device. User ids of profiles differ
     * between devices, so a work profile / Private Space is matched by its type.
     */
    static Integer mapUser(int user, String type, UidMapper m) {
        if (user == 0) {
            return 0;
        }
        Set<Integer> users = m.users();
        if (users == null || users.isEmpty()) {
            return user; // unknown: keep it
        }
        if (users.contains(user)) {
            String localType = m.userType(user);
            if (type == null || localType == null || type.equals(localType)) {
                return user;
            }
        }
        if (type != null) {
            for (Integer candidate : new TreeSet<>(users)) {
                if (candidate != 0 && type.equals(m.userType(candidate))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // ---- rules ----

    /**
     * @param lists "|"-separated UID lists, indexed by connection type (the export type codes)
     * @return one entry per UID: its portable form plus "on", the connection types it is checked for
     */
    public static JSONArray encodeRules(String[] lists, UidMapper m) throws JSONException {
        Map<Integer, Set<Integer>> typesByUid = new TreeMap<>();
        for (int type = 0; type < lists.length; type++) {
            for (int uid : UidListParser.parse(lists[type])) {
                Set<Integer> types = typesByUid.get(uid);
                if (types == null) {
                    types = new TreeSet<>();
                    typesByUid.put(uid, types);
                }
                types.add(type);
            }
        }
        JSONArray rules = new JSONArray();
        for (Map.Entry<Integer, Set<Integer>> e : typesByUid.entrySet()) {
            JSONObject entry = encodeUid(e.getKey(), m);
            entry.put("on", new JSONArray(e.getValue()));
            rules.put(entry);
        }
        return rules;
    }

    /**
     * @param skipped if not null, receives an id (the packages, or the UID) of each entry that
     *                doesn't exist on this device
     * @return "|"-separated UID lists, indexed by connection type
     */
    public static String[] decodeRules(JSONArray rules, int typeCount, UidMapper m, Collection<String> skipped) {
        List<Set<Integer>> uids = new ArrayList<>();
        for (int i = 0; i < typeCount; i++) {
            uids.add(new LinkedHashSet<>());
        }
        if (rules != null) {
            for (int i = 0; i < rules.length(); i++) {
                JSONObject entry = rules.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                Integer uid = decodeUid(entry, m);
                if (uid == null) {
                    if (skipped != null) {
                        JSONArray pkgs = entry.optJSONArray("packages");
                        skipped.add((pkgs != null ? pkgs.toString() : "uid:" + entry.optInt("uid"))
                                + "@" + entry.optString("userType", String.valueOf(entry.optInt("user", 0))));
                    }
                    continue;
                }
                JSONArray on = entry.optJSONArray("on");
                for (int j = 0; on != null && j < on.length(); j++) {
                    int type = on.optInt(j, -1);
                    if (type >= 0 && type < typeCount) {
                        uids.get(type).add(uid);
                    }
                }
            }
        }
        String[] lists = new String[typeCount];
        for (int i = 0; i < typeCount; i++) {
            lists[i] = join(uids.get(i));
        }
        return lists;
    }

    private static String join(Collection<Integer> uids) {
        StringBuilder sb = new StringBuilder();
        for (Integer uid : uids) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(uid);
        }
        return sb.toString();
    }

    // ---- preferences ----

    /**
     * @param exclude  keys to leave out
     * @param prefixes key prefixes to leave out
     * @return {key: {"t": type, "v": value}}
     */
    public static JSONObject encodePrefs(Map<String, ?> all, Set<String> exclude, List<String> prefixes)
            throws JSONException {
        JSONObject out = new JSONObject();
        for (String key : new TreeSet<>(all.keySet())) {
            if (isExcluded(key, exclude, prefixes)) {
                continue;
            }
            Object value = all.get(key);
            JSONObject typed = new JSONObject();
            if (value instanceof Boolean) {
                typed.put("t", "b");
            } else if (value instanceof Integer) {
                typed.put("t", "i");
            } else if (value instanceof Long) {
                typed.put("t", "l");
            } else if (value instanceof Float) {
                typed.put("t", "f");
                value = ((Float) value).doubleValue();
            } else if (value instanceof String) {
                typed.put("t", "s");
            } else if (value instanceof Set) {
                typed.put("t", "set");
                value = new JSONArray(new TreeSet<>(stringSet(value)));
            } else {
                continue;
            }
            typed.put("v", value);
            out.put(key, typed);
        }
        return out;
    }

    /**
     * @return number of preferences written
     */
    public static int decodePrefs(JSONObject prefs, Set<String> exclude, List<String> prefixes, PrefWriter w) {
        int count = 0;
        if (prefs == null) {
            return 0;
        }
        Iterator<String> keys = prefs.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject typed = prefs.optJSONObject(key);
            if (typed == null || isExcluded(key, exclude, prefixes) || !typed.has("v")) {
                continue;
            }
            try {
                switch (typed.optString("t")) {
                    case "b":
                        w.putBoolean(key, typed.getBoolean("v"));
                        break;
                    case "i":
                        w.putInt(key, typed.getInt("v"));
                        break;
                    case "l":
                        w.putLong(key, typed.getLong("v"));
                        break;
                    case "f":
                        w.putFloat(key, (float) typed.getDouble("v"));
                        break;
                    case "s":
                        w.putString(key, typed.getString("v"));
                        break;
                    case "set": {
                        JSONArray arr = typed.getJSONArray("v");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < arr.length(); i++) {
                            set.add(arr.getString(i));
                        }
                        w.putStringSet(key, set);
                        break;
                    }
                    default:
                        continue;
                }
                count++;
            } catch (JSONException e) {
                // wrong value for its type: skip it
            }
        }
        return count;
    }

    /**
     * Write a preference of a v1 backup, which stored every value as a string, with the type the
     * app reads it with.
     *
     * @param existing current value of the key on this device, or null
     * @return false if it was skipped (excluded, or not convertible)
     */
    public static boolean writeV1Pref(String key, String value, Object existing, PrefWriter w) {
        if (key == null || value == null || NOT_BACKED_UP.contains(key)) {
            return false;
        }
        try {
            if (V1_INT_KEYS.contains(key)) {
                w.putInt(key, Integer.parseInt(value.trim()));
            } else if (V1_LONG_KEYS.contains(key)) {
                w.putLong(key, Long.parseLong(value.trim()));
            } else if (V1_STRING_KEYS.contains(key)) {
                w.putString(key, value);
            } else if (existing != null) {
                return writeAsTypeOf(key, value, existing, w);
            } else if (value.equals("true") || value.equals("false")) {
                w.putBoolean(key, Boolean.parseBoolean(value));
            } else if (value.matches("-?\\d{1,10}") && fitsInt(value)) {
                w.putInt(key, Integer.parseInt(value)); // e.g. theme colors
            } else {
                w.putString(key, value);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean writeAsTypeOf(String key, String value, Object existing, PrefWriter w) {
        if (existing instanceof Boolean) {
            if (!value.equals("true") && !value.equals("false")) {
                return false;
            }
            w.putBoolean(key, Boolean.parseBoolean(value));
        } else if (existing instanceof Integer) {
            w.putInt(key, Integer.parseInt(value.trim()));
        } else if (existing instanceof Long) {
            w.putLong(key, Long.parseLong(value.trim()));
        } else if (existing instanceof Float) {
            w.putFloat(key, Float.parseFloat(value.trim()));
        } else if (existing instanceof String) {
            w.putString(key, value);
        } else {
            return false; // string sets can't be restored from their v1 text form
        }
        return true;
    }

    private static boolean fitsInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isExcluded(String key, Set<String> exclude, List<String> prefixes) {
        if (exclude != null && exclude.contains(key)) {
            return true;
        }
        if (prefixes != null) {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(Object value) {
        return (Set<String>) value;
    }
}
