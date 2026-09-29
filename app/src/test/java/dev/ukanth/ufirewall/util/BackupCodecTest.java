package dev.ukanth.ufirewall.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackupCodecTest {

    /** A device: packages by uid, special entries, users with their types. */
    private static final class FakeDevice implements BackupCodec.UidMapper {
        final Map<Integer, List<String>> packages = new HashMap<>();
        final Map<String, Integer> specials = new HashMap<>();
        final Map<Integer, String> users = new HashMap<>();

        FakeDevice() {
            users.put(0, "full.SYSTEM");
        }

        FakeDevice pkg(int uid, String... names) {
            packages.put(uid, Arrays.asList(names));
            return this;
        }

        @Override
        public List<String> packagesForUid(int uid) {
            List<String> p = packages.get(uid);
            if (p == null) {
                p = packages.get(uid % 100000); // installed in every user
            }
            return p == null ? Collections.<String>emptyList() : p;
        }

        @Override
        public String specialNameForUid(int uid) {
            for (Map.Entry<String, Integer> e : specials.entrySet()) {
                if (e.getValue() == uid) {
                    return e.getKey();
                }
            }
            return null;
        }

        @Override
        public Integer specialUid(String name) {
            return specials.get(name);
        }

        @Override
        public Integer appIdForPackage(String pkg) {
            for (Map.Entry<Integer, List<String>> e : packages.entrySet()) {
                if (e.getValue().contains(pkg)) {
                    return e.getKey() % 100000;
                }
            }
            return null;
        }

        @Override
        public Set<Integer> users() {
            return users.keySet();
        }

        @Override
        public String userType(int userId) {
            return users.get(userId);
        }
    }

    private static Integer roundTrip(int uid, FakeDevice from, FakeDevice to) throws Exception {
        // through text, like a real backup file
        JSONObject encoded = new JSONObject(BackupCodec.encodeUid(uid, from).toString());
        return BackupCodec.decodeUid(encoded, to);
    }

    @Test
    public void appIsMatchedByPackageNotUid() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10150, "com.example.chat");
        FakeDevice to = new FakeDevice().pkg(10150, "com.other.app").pkg(10321, "com.example.chat");
        assertEquals(Integer.valueOf(10321), roundTrip(10150, from, to));
    }

    @Test
    public void missingAppIsSkippedNotMappedToSameUid() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10150, "com.example.chat");
        FakeDevice to = new FakeDevice().pkg(10150, "com.other.app");
        assertNull(roundTrip(10150, from, to));
    }

    @Test
    public void sharedUidMatchesAnyOfItsPackages() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10144, "com.google.android.gms", "com.google.android.gsf");
        FakeDevice to = new FakeDevice().pkg(10200, "com.google.android.gsf");
        assertEquals(Integer.valueOf(10200), roundTrip(10144, from, to));
    }

    @Test
    public void systemUidsAreKept() throws Exception {
        FakeDevice device = new FakeDevice();
        assertEquals(Integer.valueOf(1000), roundTrip(1000, device, new FakeDevice()));
        assertEquals(Integer.valueOf(2000), roundTrip(2000, device, new FakeDevice()));
    }

    @Test
    public void specialEntriesAreMatchedByName() throws Exception {
        FakeDevice from = new FakeDevice();
        from.specials.put("dev.afwall.special.kernel", -11);
        from.specials.put("dev.afwall.special.gps", 1021);
        FakeDevice to = new FakeDevice();
        to.specials.put("dev.afwall.special.kernel", -11);
        to.specials.put("dev.afwall.special.gps", 1023);
        assertEquals(Integer.valueOf(-11), roundTrip(-11, from, to));
        assertEquals(Integer.valueOf(1023), roundTrip(1021, from, to));
    }

    @Test
    public void unnamedNegativeSpecialIsKept() throws Exception {
        assertEquals(Integer.valueOf(-14), roundTrip(-14, new FakeDevice(), new FakeDevice()));
    }

    @Test
    public void workProfileMapsToWorkProfileOfTarget() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10150, "com.example.chat");
        from.users.put(10, "profile.MANAGED");
        from.users.put(11, "profile.PRIVATE");
        FakeDevice to = new FakeDevice().pkg(10300, "com.example.chat");
        to.users.put(12, "profile.PRIVATE");
        to.users.put(13, "profile.MANAGED");
        // work profile 10 -> 13, Private Space 11 -> 12, main user stays 0
        assertEquals(Integer.valueOf(1310300), roundTrip(1010150, from, to));
        assertEquals(Integer.valueOf(1210300), roundTrip(1110150, from, to));
        assertEquals(Integer.valueOf(10300), roundTrip(10150, from, to));
    }

    @Test
    public void profileRulesAreNotMovedToTheMainUser() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10150, "com.example.chat");
        from.users.put(10, "profile.MANAGED");
        FakeDevice to = new FakeDevice().pkg(10300, "com.example.chat"); // no work profile
        assertNull(roundTrip(1010150, from, to));
    }

    @Test
    public void sameUserIdOfOtherTypeIsNotUsed() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10150, "com.example.chat");
        from.users.put(10, "profile.MANAGED");
        FakeDevice to = new FakeDevice().pkg(10150, "com.example.chat");
        to.users.put(10, "full.SECONDARY");
        assertNull(roundTrip(1010150, from, to));
    }

    @Test
    public void rulesRoundTripPerConnectionType() throws Exception {
        FakeDevice from = new FakeDevice().pkg(10150, "a").pkg(10151, "b").pkg(10152, "gone");
        from.specials.put("dev.afwall.special.kernel", -11);
        FakeDevice to = new FakeDevice().pkg(10400, "a").pkg(10401, "b");
        to.specials.put("dev.afwall.special.kernel", -11);

        String[] lists = {"10150|-11|10152", "10151", "", "", "", "", "10150"};
        JSONArray rules = new JSONArray(BackupCodec.encodeRules(lists, from).toString());
        Set<String> skipped = new HashSet<>();
        String[] out = BackupCodec.decodeRules(rules, 7, to, skipped);

        assertEquals(1, skipped.size());
        assertEquals(new HashSet<>(Arrays.asList(-11, 10400)), new HashSet<>(UidListParser.parse(out[0])));
        assertEquals("10401", out[1]);
        assertEquals("", out[2]);
        assertEquals("10400", out[6]);
    }

    @Test
    public void decodeIgnoresUnknownConnectionTypes() throws Exception {
        JSONArray rules = new JSONArray("[{\"uid\":1000,\"appId\":1000,\"on\":[0,42,-1]}]");
        String[] out = BackupCodec.decodeRules(rules, 7, new FakeDevice(), null);
        assertEquals("1000", out[0]);
    }

    @Test
    public void prefsRoundTripWithTypes() throws Exception {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("bool", true);
        all.put("int", -16777216);
        all.put("long", 12345678901L);
        all.put("float", 1.5f);
        all.put("numericString", "2024");
        all.put("set", new HashSet<>(Arrays.asList("a", "b")));
        all.put("profilePwd", "secret");
        all.put("cache.label.x", "X");

        JSONObject encoded = new JSONObject(BackupCodec.encodePrefs(all, BackupCodec.NOT_BACKED_UP,
                Collections.singletonList("cache.label.")).toString());
        RecordingWriter w = new RecordingWriter();
        BackupCodec.decodePrefs(encoded, null, null, w);

        assertEquals(true, w.values.get("bool"));
        assertEquals(-16777216, w.values.get("int"));
        assertEquals(12345678901L, w.values.get("long"));
        assertEquals(1.5f, w.values.get("float"));
        assertEquals("2024", w.values.get("numericString"));
        assertEquals(new HashSet<>(Arrays.asList("a", "b")), w.values.get("set"));
        assertFalse(w.values.containsKey("profilePwd"));
        assertFalse(w.values.containsKey("cache.label.x"));
    }

    @Test
    public void v1PrefsGetTheTypesTheAppReads() {
        RecordingWriter w = new RecordingWriter();
        // slider settings are ints (were imported as strings, which crashed getInt())
        BackupCodec.writeV1Pref("customDelay", "7", null, w);
        BackupCodec.writeV1Pref("logPingTime", "20", null, w);
        // list settings are strings even when numeric
        BackupCodec.writeV1Pref("patternMax", "3", null, w);
        BackupCodec.writeV1Pref("notification_priority", "0", null, w);
        BackupCodec.writeV1Pref("multiUserId", "12", null, w);
        // unknown numeric keys stay ints (theme colors)
        BackupCodec.writeV1Pref("primaryColor", "-16777216", null, w);
        BackupCodec.writeV1Pref("enableIPv6", "true", null, w);
        // a key the device already has keeps its type
        BackupCodec.writeV1Pref("someName", "2024", "old", w);

        assertEquals(7, w.values.get("customDelay"));
        assertEquals(20, w.values.get("logPingTime"));
        assertEquals("3", w.values.get("patternMax"));
        assertEquals("0", w.values.get("notification_priority"));
        assertEquals(12L, w.values.get("multiUserId"));
        assertEquals(-16777216, w.values.get("primaryColor"));
        assertEquals(true, w.values.get("enableIPv6"));
        assertEquals("2024", w.values.get("someName"));
    }

    @Test
    public void v1PrefsSkipSecretsLicenseAndSets() {
        RecordingWriter w = new RecordingWriter();
        assertFalse(BackupCodec.writeV1Pref("ipurchaseddonatekey", "true", null, w));
        assertFalse(BackupCodec.writeV1Pref("passSetting", "p2", null, w));
        assertFalse(BackupCodec.writeV1Pref("profilePwd", "x", null, w));
        assertFalse(BackupCodec.writeV1Pref("storedPid", "[123]", null, w));
        assertFalse(BackupCodec.writeV1Pref("someSet", "[a]", new HashSet<String>(), w));
        assertFalse(BackupCodec.writeV1Pref("customDelay", "abc", null, w));
        assertTrue(w.values.isEmpty());
    }

    private static final class RecordingWriter implements BackupCodec.PrefWriter {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }

        @Override
        public void putInt(String key, int value) {
            values.put(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            values.put(key, value);
        }

        @Override
        public void putFloat(String key, float value) {
            values.put(key, value);
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void putStringSet(String key, Set<String> value) {
            values.put(key, new HashSet<>(value));
        }
    }
}
