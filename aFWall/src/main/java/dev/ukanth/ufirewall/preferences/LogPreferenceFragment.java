package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.LogService;

public class LogPreferenceFragment extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.log_preferences);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		Context ctx = this.getActivity().getApplicationContext();
		if (key.equals("enableLog")) {
			Api.setLogging(ctx, G.enableLog());
		}

		if (key.equals("enableLogService")) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			if (enabled) {
				Intent intent = new Intent(ctx, LogService.class);
				ctx.startService(intent);
			} else {
				Intent intent = new Intent(ctx, LogService.class);
				ctx.stopService(intent);
			}
		}
	}

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
}
