/**
 * Preference Interface.
 * All iptables "communication" is handled by this class.
 * 
 * Copyright (C) 2011-2012  Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.preferences;

import java.io.File;

import net.saik0.android.unifiedpreference.UnifiedPreferenceFragment;
import net.saik0.android.unifiedpreference.UnifiedSherlockPreferenceActivity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.admin.AdminDeviceReceiver;

public class PreferencesActivity extends UnifiedSherlockPreferenceActivity
		implements OnSharedPreferenceChangeListener {

	private static final int REQUEST_CODE_ENABLE_ADMIN = 10237; // identifies
																// our request
																// id
	private ComponentName deviceAdmin;
	private DevicePolicyManager mDPM;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		deviceAdmin = new ComponentName(getApplicationContext(),
				AdminDeviceReceiver.class);
		setHeaderRes(R.xml.unified_preferences_headers);
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		final Editor editor = prefs.edit();
		if (!mDPM.isAdminActive(deviceAdmin)) {
			editor.putBoolean("enableAdmin", false);
			editor.commit();
		} else {
			editor.putBoolean("enableAdmin", true);
			editor.commit();
		}
		
		
		//language
		String lang = prefs.getString("locale", "en");
		Api.updateLanguage(getApplicationContext(), lang);
		
		super.onCreate(savedInstanceState);

	}

	@Override
	protected void onResume() {
		super.onResume();
		// Set up a listener whenever a key changes
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		// Unregister the listener whenever a key changes
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	public static class GeneralPreferenceFragment extends
			UnifiedPreferenceFragment {
	}

	public static class SecPreferenceFragment extends UnifiedPreferenceFragment {
	}

	public static class FirewallPreferenceFragment extends
			UnifiedPreferenceFragment {
	}

	public static class MultiProfilePreferenceFragment extends
			UnifiedPreferenceFragment {
	}

	public static class ExpPreferenceFragment extends UnifiedPreferenceFragment {
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		
		if(key.equals("enableIPv6")){
			File defaultIP6TablesPath = new File("/system/bin/ip6tables");
			if(!defaultIP6TablesPath.exists()) {
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				SharedPreferences.Editor editor = prefs.edit();
				editor.putBoolean("enableIPv6", false);
				editor.commit();
				Api.alert(getApplicationContext(), getString(R.string.ip6unavailable));
			}
		}
		if (key.equals("showUid") || key.equals("enableMultiProfile")
				|| key.equals("disableIcons") || key.equals("enableVPN") || key.equals("enableLAN")
				|| key.equals("enableRoam") || key.equals("locale") ) {
			Api.applications = null;
			Intent returnIntent = new Intent();
			boolean value = sharedPreferences.getBoolean("enableMultiProfile", false);
			if(!value){
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt("storedPosition", 0);
				editor.commit();
			}
			//reset values of vpn profile
			/*boolean vpnStatus = sharedPreferences.getBoolean("enableVPN", false);
			if(!vpnStatus) {
				
				returnIntent.putExtra("reset", true);
			}*/
			
			setResult(RESULT_OK, returnIntent);
		}

		if (key.equals("fixLeak")) {
			boolean value = sharedPreferences.getBoolean("fixLeak", false);
			File file = new File(getApplicationContext().getDir("bin", 0),
					"afwallstart");
			if (value) {
				if (file.exists()) {
					// hard coded init.d paths
					File initPath = new File("/system/etc/init.d");
					File initPath2 = new File("/etc/init.d");
					if (initPath.exists() && initPath.isDirectory()) {
						RootTools.copyFile(file.getAbsolutePath(),
								initPath.getAbsolutePath(), true, false);
						Toast.makeText(getApplicationContext(),
								R.string.success_initd, Toast.LENGTH_SHORT)
								.show();
					} else if (initPath2.exists() && initPath2.isDirectory()) {
						RootTools.copyFile(file.getAbsolutePath(),
								initPath2.getAbsolutePath(), true, false);
						Toast.makeText(getApplicationContext(),
								R.string.success_initd, Toast.LENGTH_SHORT)
								.show();
					} else {
						Toast.makeText(getApplicationContext(),
								R.string.unable_initd, Toast.LENGTH_SHORT)
								.show();
					}
				}
			} else {
				Toast.makeText(getApplicationContext(), R.string.remove_initd,
						Toast.LENGTH_LONG).show();
			}
		}

		if (key.equals("enableAdmin")) {
			boolean value = sharedPreferences.getBoolean("enableAdmin", false);
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
							"Additional Security");
					startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
				}
			} else {
				if (mDPM.isAdminActive(deviceAdmin)) {
					Api.displayToasts(getApplicationContext(), R.string.device_admin_on_disable, Toast.LENGTH_LONG);
				}
			}
		}
	}

}
