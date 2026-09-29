package dev.ukanth.ufirewall.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Parses the "|"-separated UID lists stored in the rule preferences.
 * <p>
 * Kept free of Android dependencies so it can be unit tested.
 */
public final class UidListParser {

    private UidListParser() {
    }

    /**
     * @param saved "|"-separated UIDs, e.g. "10123|-10|1000"; may be null
     * @return the valid UIDs, sorted ascending. Malformed entries (for example left behind by an
     * old import) are skipped so a single bad token can't make every rule apply fail.
     */
    public static List<Integer> parse(String saved) {
        List<Integer> uids = new ArrayList<>();
        if (saved == null) {
            return uids;
        }
        StringTokenizer tok = new StringTokenizer(saved, "|");
        while (tok.hasMoreTokens()) {
            String uid = tok.nextToken().trim();
            if (uid.isEmpty()) {
                continue;
            }
            try {
                uids.add(Integer.parseInt(uid));
            } catch (NumberFormatException e) {
                // skip malformed entry
            }
        }
        Collections.sort(uids);
        return uids;
    }

    /**
     * Update a saved list with the state of the apps shown in the app list, keeping every UID
     * that isn't shown (apps of other profiles when dual apps is off, apps without INTERNET when
     * "show all apps" is off, ...): saving must not delete rules the user can't see.
     *
     * @param saved    "|"-separated UIDs as stored
     * @param shown    UIDs of the apps in the app list
     * @param selected those of {@code shown} that are checked
     * @return the updated "|"-separated list, sorted
     */
    public static String merge(String saved, java.util.Collection<Integer> shown, java.util.Collection<Integer> selected) {
        java.util.TreeSet<Integer> result = new java.util.TreeSet<>(parse(saved));
        result.removeAll(shown);
        result.addAll(selected);
        StringBuilder sb = new StringBuilder();
        for (Integer uid : result) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(uid);
        }
        return sb.toString();
    }
}
