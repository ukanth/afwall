package dev.ukanth.ufirewall.preferences;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;

public class UIPreferenceFragment extends PreferenceFragment  {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.ui_preferences);

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
}
