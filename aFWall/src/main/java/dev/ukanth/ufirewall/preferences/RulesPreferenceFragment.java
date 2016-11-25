package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.events.RulesEvent;
import dev.ukanth.ufirewall.util.G;

public class RulesPreferenceFragment extends PreferenceFragment  implements
		SharedPreferences.OnSharedPreferenceChangeListener {

	private Context ctx;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.rules_preferences);

		//make sure Roaming is disable in Wifi-only Tablets
		if (!Api.isMobileNetworkSupported(getActivity())) {
			CheckBoxPreference roamPreference = (CheckBoxPreference) findPreference("enableRoam");
			roamPreference.setChecked(false);
			roamPreference.setEnabled(false);
		} else {
			CheckBoxPreference roamPreference = (CheckBoxPreference) findPreference("enableRoam");
			roamPreference.setEnabled(true);
		}
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

		if (key.equals("activeRules")) {
			if (!G.activeRules()) {
				//disable service when there is no active rules
				//stopService(new Intent(PreferencesActivity.this, RootShell.class));
				CheckBoxPreference enableRoam = (CheckBoxPreference) findPreference("enableRoam");
				enableRoam.setChecked(false);
				CheckBoxPreference enableLAN = (CheckBoxPreference) findPreference("enableLAN");
				enableLAN.setChecked(false);
				CheckBoxPreference enableVPN = (CheckBoxPreference) findPreference("enableVPN");
				enableVPN.setChecked(false);

				G.enableRoam(false);
				G.enableLAN(false);
				G.enableVPN(false);

			}
		}
		if (key.equals("enableIPv6") || key.equals("blockIPv6"))  {
			File defaultIP6TablesPath = new File("/system/bin/ip6tables");
			if (!defaultIP6TablesPath.exists()) {
				G.enableIPv6(false);
				G.blockIPv6(false);
				CheckBoxPreference enable = (CheckBoxPreference) findPreference("enableIPv6");
				enable.setChecked(false);

				CheckBoxPreference block = (CheckBoxPreference) findPreference("blockIPv6");
				block.setChecked(false);
				if(ctx != null) {
					Api.toast(ctx, getString(R.string.ip6unavailable));
				}
			} else {
				switch (key) {
					case "enableIPv6":
						CheckBoxPreference block = (CheckBoxPreference) findPreference("blockIPv6");
						block.setChecked(false);
						break;
					case "blockIPv6":
						CheckBoxPreference allow = (CheckBoxPreference) findPreference("enableIPv6");
						allow.setChecked(false);
						break;
				}
			}
		}
	}
}
