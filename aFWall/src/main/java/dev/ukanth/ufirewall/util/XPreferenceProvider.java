package dev.ukanth.ufirewall.util;

import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.BuildConfig;

public class XPreferenceProvider extends RemotePreferenceProvider {
    public XPreferenceProvider() {
        super(BuildConfig.APPLICATION_ID, new String[] {Api.PREFS_NAME, BuildConfig.APPLICATION_ID+ "_preferences"});
    }
}