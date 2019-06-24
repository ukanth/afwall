package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class ThemePreferenceFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private Context ctx;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.theme_preference);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ctx = context;
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (ctx == null) {
            ctx = (Context) getActivity();
        }
        if (ctx != null) {
            if (key.equals("theme")) {
                switch (G.getSelectedTheme()){
                    case "D":
                        G.getInstance().setTheme(R.style.AppDarkTheme);
                        break;
                    case "L":
                        G.getInstance().setTheme(R.style.AppLightTheme);
                        break;
                    case "B":
                        G.getInstance().setTheme(R.style.AppBlackTheme);
                        break;
                }
            }
        }
    }
}
