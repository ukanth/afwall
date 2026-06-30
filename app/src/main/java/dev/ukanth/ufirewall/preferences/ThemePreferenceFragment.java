package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

public class ThemePreferenceFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String KEY_THEME = "theme";
    private static final String KEY_CUSTOM_THEME_COLORS_SCREEN = "customThemeColorsScreen";

    private Context ctx;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ctx = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.theme_preference);
        enforceDonorThemeAccess();
        setupThemePreference();
        setupCustomThemePreferences();
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
        // PreferencesActivity handles recreate + broadcast; nothing needed here
    }

    private void setupThemePreference() {
        ListPreference themePreference = (ListPreference) findPreference(KEY_THEME);
        if (themePreference == null) return;

        themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String theme = String.valueOf(newValue);
            if (!G.isThemeAvailable(theme, resolveContext())) {
                showDonorRequiredToast();
                return false;
            }
            G.customThemeColorsEnabled(false);
            String savedTheme = G.getSelectedTheme(theme);
            themePreference.setValue(savedTheme);
            themePreference.setSummary(themePreference.getEntry());
            Log.i(G.TAG, "Theme changed to " + savedTheme);
            broadcastThemeRefresh();
            return false;
        });
    }

    private void setupCustomThemePreferences() {
        Preference customScreen = findPreference(KEY_CUSTOM_THEME_COLORS_SCREEN);
        if (customScreen == null) return;

        customScreen.setOnPreferenceClickListener(preference -> {
            if (!G.canUseDonorFeatures(resolveContext())) {
                showDonorRequiredToast();
                return true;
            }
            Intent intent = new Intent(getActivity(), PreferencesActivity.class);
            intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                    CustomThemePreferenceFragment.class.getName());
            intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
            intent.putExtra(PreferencesActivity.EXTRA_TOOLBAR_TITLE,
                    getString(R.string.custom_theme_colors_title));
            intent.putExtra(PreferencesActivity.EXTRA_FINISH_ON_BACK, true);
            startActivity(intent);
            return true;
        });
    }

    private void enforceDonorThemeAccess() {
        if (ctx == null) ctx = getActivity();
        if (ctx == null) return;
        if (!G.isThemeAvailable(G.getSelectedTheme(), ctx)) {
            G.getSelectedTheme("D");
            Log.i(G.TAG, "Unavailable donor-only theme reset to default");
        }
        if (G.customThemeColorsEnabled() && !G.canUseDonorFeatures(ctx)) {
            G.customThemeColorsEnabled(false);
            Log.i(G.TAG, "Custom theme colors disabled: no donor access");
        }
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
