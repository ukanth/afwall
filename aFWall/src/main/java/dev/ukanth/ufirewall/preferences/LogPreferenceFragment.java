package dev.ukanth.ufirewall.preferences;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import com.stericson.roottools.RootTools;

import java.util.ArrayList;

import dev.ukanth.ufirewall.R;

public class LogPreferenceFragment extends PreferenceFragment  {

	private static ListPreference listPreference;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.log_preferences);
		populateLogMessage(findPreference("logDmesg"));
	}

	private void populateLogMessage(Preference logDmesg) {
		if (logDmesg == null) {
			return;
		}
		ArrayList<String> ar = new ArrayList<String>();
		ArrayList<String> val = new ArrayList<String>();
		ar.add("System");
		val.add("OS");

		listPreference = (ListPreference) logDmesg;
		PreferenceCategory mCategory = (PreferenceCategory) findPreference("logExperimental");

		if(RootTools.isBusyboxAvailable()){
			ar.add("Busybox");
			val.add("BX");
		}
		
		/*if(RootTools.isToyboxAvailable()) {
			ar.add("Toybox");
			val.add("TB");
		}*/

		if(ar.size() != 1 && listPreference != null ) {
			listPreference.setEntries(ar.toArray(new String[0]));
			listPreference.setEntryValues(val.toArray(new String[0]));

		} else {
			if(listPreference != null && mCategory != null) {
				mCategory.removePreference(listPreference);
			}
		}
	}
}
