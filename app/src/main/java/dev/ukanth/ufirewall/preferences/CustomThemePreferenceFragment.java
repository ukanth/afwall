package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

public class CustomThemePreferenceFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String KEY_CUSTOM_THEME_COLORS = "customThemeColors";
    private static final String[] COLOR_KEYS = {
            "primaryColor",
            "primaryDarkColor",
            "accentColor",
            "backgroundColor",
            "textPrimaryColor",
            "textSecondaryColor",
            "userColor",
            "defaultIconColor"
    };

    private Context ctx;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ctx = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureDonorAccess()) {
            return;
        }
        if (!G.customThemeColorsEnabled()) {
            G.seedCustomThemeColorsFromSelectedThemeIfNeeded();
        }
        addPreferencesFromResource(R.xml.theme_custom_preference);
        setupPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (ctx == null) ctx = getActivity();
        if (ctx != null && (KEY_CUSTOM_THEME_COLORS.equals(key) || isColorKey(key))) {
            broadcastThemeRefresh();
        }
    }

    private void setupPreferences() {
        Preference customThemeColors = findPreference(KEY_CUSTOM_THEME_COLORS);
        if (customThemeColors != null) {
            customThemeColors.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue && !G.canUseDonorFeatures(resolveContext())) {
                    showDonorRequiredToast();
                    return false;
                }
                if ((Boolean) newValue) {
                    G.seedCustomThemeColorsFromSelectedThemeIfNeeded();
                }
                Log.i(G.TAG, "Custom theme colors " + ((Boolean) newValue ? "enabled" : "disabled"));
                return true;
            });
        }

        for (String key : COLOR_KEYS) {
            Preference colorPreference = findPreference(key);
            if (colorPreference != null) {
                colorPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (!G.canUseDonorFeatures(resolveContext())) {
                        showDonorRequiredToast();
                        return false;
                    }
                    Log.i(G.TAG, "Theme color updated: " + preference.getKey());
                    return true;
                });
            }
        }
    }

    private boolean ensureDonorAccess() {
        if (!G.canUseDonorFeatures(resolveContext())) {
            showDonorRequiredToast();
            if (getActivity() != null) {
                getActivity().finish();
            }
            return false;
        }
        return true;
    }

    private boolean isColorKey(String key) {
        for (String colorKey : COLOR_KEYS) {
            if (colorKey.equals(key)) return true;
        }
        return false;
    }

    private Context resolveContext() {
        return ctx != null ? ctx : getActivity();
    }

    private void showDonorRequiredToast() {
        Context context = resolveContext();
        if (context != null) {
            Api.toast(context, context.getText(R.string.donate_only), Toast.LENGTH_LONG);
        }
    }

    private void broadcastThemeRefresh() {
        Context context = resolveContext();
        if (context != null) {
            Intent broadcastIntent = new Intent("dev.ukanth.ufirewall.theme.REFRESH");
            context.sendBroadcast(broadcastIntent);
        }
    }
}
