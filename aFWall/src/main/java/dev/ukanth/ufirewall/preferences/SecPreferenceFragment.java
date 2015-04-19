package dev.ukanth.ufirewall.preferences;

import com.haibison.android.lockpattern.util.Settings;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.admin.AdminDeviceReceiver;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

public class SecPreferenceFragment extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {

	private static CheckBoxPreference enableAdminPref;

	private static final int REQUEST_CODE_ENABLE_ADMIN = 10237; // identifies
																// our request
																// ID

	private static ComponentName deviceAdmin;
	private static DevicePolicyManager mDPM;

	public static void setupEnableAdmin(Preference pref) {
		if (pref == null) {
			return;
		}
		enableAdminPref = (CheckBoxPreference) pref;
		// query the actual device admin status from the system
		enableAdminPref.setChecked(mDPM.isAdminActive(deviceAdmin));
	}

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// update settings with actual device admin setting
		mDPM = (DevicePolicyManager) this.getActivity().getSystemService(
				Context.DEVICE_POLICY_SERVICE);
		deviceAdmin = new ComponentName(this.getActivity()
				.getApplicationContext(), AdminDeviceReceiver.class);
		super.onCreate(savedInstanceState);
		setupEnableAdmin(findPreference("enableAdmin"));
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.security_preferences);

	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("enableAdmin")) {
			boolean value = G.enableAdmin();
			if (value) {
				Log.d("Device Admin Active ?", mDPM.isAdminActive(deviceAdmin)
						+ "");
				if (!mDPM.isAdminActive(deviceAdmin)) {
					// Launch the activity to have the user enable our admin.
					Intent intent = new Intent(
							DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
					intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
							deviceAdmin);
					intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
							getString(R.string.device_admin_desc));
					startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
				}
			} else {
				if (mDPM.isAdminActive(deviceAdmin)) {
					mDPM.removeActiveAdmin(deviceAdmin);
					Api.displayToasts(this.getActivity()
							.getApplicationContext(),
							R.string.device_admin_disabled, Toast.LENGTH_LONG);
				}
			}
		}
		
		if (key.equals("enableStealthPattern")) {
			Settings.Display.setStealthMode(this.getActivity().getApplicationContext(),
					G.enableStealthPattern());
		}

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

}
