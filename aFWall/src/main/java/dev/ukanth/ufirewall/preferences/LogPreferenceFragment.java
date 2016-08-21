package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.service.RootShell;
import dev.ukanth.ufirewall.util.G;

public class LogPreferenceFragment extends PreferenceFragment  {

	private static ListPreference listPreference;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.log_preferences);
		//populateLogTarget(findPreference("logTarget"));
	}

	/*public void populateLogTarget(Preference logTargetChoice) {
		if (logTargetChoice == null) {
			return;
		}
		PreferenceCategory mCategory = (PreferenceCategory) findPreference("logExperimental");

		listPreference = (ListPreference) logTargetChoice;
		if(!G.logTargetChose()) {
			mCategory.removePreference(listPreference);
		}
	}*/

}
