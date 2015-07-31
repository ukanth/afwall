package dev.ukanth.ufirewall.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.ProfileActivity;

public class MultiProfilePreferenceFragment extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.profiles_preferences);
		Preference button = (Preference)findPreference("manage_profiles");
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				//code for what you want it to do
				startActivity(new Intent(getActivity(), ProfileActivity.class));
				return true;
			}
		});

	}
}
