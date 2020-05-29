package dev.ukanth.ufirewall.preferences;

import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.util.G.isDonate;
import static dev.ukanth.ufirewall.util.SecurityUtil.LOCK_VERIFICATION;

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
            ctx = getActivity();
        }
        if (ctx != null) {
            if (key.equals("theme")) {
                switch (G.getSelectedTheme()){
                    case "D":
                        G.getInstance().setTheme(R.style.AppDarkTheme);
                        break;
                    case "L":
                        if ((G.isDoKey(ctx) || isDonate())) {
                            G.getInstance().setTheme(R.style.AppLightTheme);
                        } else {
                            Api.toast(ctx, ctx.getText(R.string.donate_only), Toast.LENGTH_LONG);
                            G.getSelectedTheme("D");
                        }
                        break;
                    case "B":
                        if ((G.isDoKey(ctx) || isDonate())) {
                            G.getInstance().setTheme(R.style.AppBlackTheme);
                        } else {
                            Api.toast(ctx, ctx.getText(R.string.donate_only), Toast.LENGTH_LONG);
                            G.getSelectedTheme("D");
                        }
                        break;
                }
            }
        }
    }
}
