package dev.ukanth.ufirewall.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.AppLanguage;
import dev.ukanth.ufirewall.util.G;

public class LanguagePreferenceFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static CheckBoxPreference checkBoxPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.language_preferences);
        showCurrentLanguage();
        checkXposed(findPreference("fixDownloadManagerLeak"));
        //checkXposed(findPreference("lockScreenNotification"),this.getActivity().getApplicationContext());
    }

    /**
     * Select the current app language in the list: it may have been changed in Android's
     * settings (13+), or by an older version that stored other codes.
     */
    private void showCurrentLanguage() {
        ListPreference list = (ListPreference) findPreference(AppLanguage.PREF_KEY);
        if (list == null) {
            return;
        }
        String current = AppLanguage.current();
        if (list.findIndexOfValue(current) < 0) {
            // e.g. "fr-FR" picked in Android's settings: the list has "fr"
            current = current.split("-")[0];
        }
        if (list.findIndexOfValue(current) >= 0 && !current.equals(list.getValue())) {
            list.setValue(current);
        }
    }

    public static void checkXposed(Preference pref) {
        if (pref == null) {
            return;
        }
        checkBoxPreference = (CheckBoxPreference) pref;
        // gray out the fixDownloadManagerLeak preference if xposed module is not activated
        Log.i(Api.TAG, "Checking Xposed:" + G.isXposedEnabled() + "");
        checkBoxPreference.setEnabled(G.isXposedEnabled());
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
    public void onDestroy() {
        super.onDestroy();
        checkBoxPreference = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AppLanguage.PREF_KEY.equals(key)) {
            // recreates the open screens in the new language
            AppLanguage.apply(sharedPreferences.getString(key, AppLanguage.SYSTEM));
        }
    }
}
