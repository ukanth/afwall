package dev.ukanth.ufirewall.preferences;

import dev.ukanth.ufirewall.Api;

/**
 * Created by ukanth on 19/7/16.
 */
public class ShareProfilePreference extends SharePreferenceProvider {
    public ShareProfilePreference() {
        super("dev.ukanth.ufirewall", new String[] {Api.PREFS_NAME});
    }
}
