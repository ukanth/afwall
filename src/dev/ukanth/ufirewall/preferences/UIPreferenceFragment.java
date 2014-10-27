package dev.ukanth.ufirewall.preferences;

import java.io.File;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.R;

public class UIPreferenceFragment extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.ui_preferences);
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
		
		Context ctx = this.getActivity().getApplicationContext();
		if (key.equals("activeRules")) {
			if (!G.activeRules()) {
				G.enableRoam(false);
				G.enableLAN(false);
			}
		}

		if (key.equals("enableIPv6")) {
			File defaultIP6TablesPath = new File("/system/bin/ip6tables");
			if (!defaultIP6TablesPath.exists()) {
				CheckBoxPreference connectionPref = (CheckBoxPreference) findPreference(key);
				connectionPref.setChecked(false);
				Api.alert(ctx,getString(R.string.ip6unavailable));
			}
		}
		if (key.equals("showUid") || key.equals("disableIcons") || key.equals("enableVPN")
				|| key.equals("enableLAN") || key.equals("enableRoam")
				|| key.equals("locale") || key.equals("showFilter")) {
			// revert back to Default profile when disabling multi-profile
			// support
			if (!G.enableMultiProfile()) {
				G.storedPosition(0);
			}
			G.reloadProfile();
		}
		
		if(key.equals("activeNotification")) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			if(enabled) {
				Api.showNotification(Api.isEnabled(ctx),ctx);
			} else {
				NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(33341);
			}
		}
	}
}
