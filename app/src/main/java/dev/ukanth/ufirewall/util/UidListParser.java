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
}
