package dev.ukanth.ufirewall.preferences;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.R;

public class MultiProfilePreferenceFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.profiles_preferences);
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
		
		if(key.equals("enableMultiProfile")) {
			if (!G.enableMultiProfile()) {
				G.storedPosition(0);
			}
			G.reloadProfile();
		}
        getActivity().setResult(Activity.RESULT_OK);
	} 

}
