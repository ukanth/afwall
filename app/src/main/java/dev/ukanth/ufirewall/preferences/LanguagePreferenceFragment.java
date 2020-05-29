package dev.ukanth.ufirewall.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class LanguagePreferenceFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static CheckBoxPreference checkBoxPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.language_preferences);
        checkXposed(findPreference("fixDownloadManagerLeak"));
        //checkXposed(findPreference("lockScreenNotification"),this.getActivity().getApplicationContext());
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

    }
}
