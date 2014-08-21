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
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.haibison.android.lockpattern.util.Settings;
import com.stericson.RootTools.RootTools;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.Log;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.admin.AdminDeviceReceiver;
import dev.ukanth.ufirewall.log.LogService;

public class PreferencesActivity extends UnifiedSherlockPreferenceActivity
		implements OnSharedPreferenceChangeListener {

	private static final int REQUEST_CODE_ENABLE_ADMIN = 10237; // identifies our request ID

	private static final String initDirs[] = { "/system/etc/init.d", "/etc/init.d" };
	private static final String initScript = "afwallstart";

	private static CheckBoxPreference fixLeakPref;
	private static CheckBoxPreference enableAdminPref;

	private static ComponentName deviceAdmin;
	private static DevicePolicyManager mDPM;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// set language
		Api.updateLanguage(getApplicationContext(), G.locale());
		setHeaderRes(R.xml.unified_preferences_headers);
		// update settings with actual device admin setting
		mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		deviceAdmin = new ComponentName(getApplicationContext(), AdminDeviceReceiver.class);
		super.onCreate(savedInstanceState);
	}
	
	/*@Override
	protected boolean isValidFragment (String fragmentName)
	{ 
		return true;
	}*/

	public static void setupFixLeak(Preference pref,Context ctx) {
		if (pref == null) {
			return;
		}
		fixLeakPref = (CheckBoxPreference)pref;

		// gray out the fixLeak preference if the ROM doesn't support init.d
		fixLeakPref.setChecked(isFixLeakInstalled());
		
		fixLeakPref.setEnabled(getFixLeakPath() != null && !isPackageInstalled("com.androguide.universal.init.d",ctx));
	}
	
	private static boolean isPackageInstalled(String packagename, Context ctx) {
	    PackageManager pm = ctx.getPackageManager();
	    try {
	        pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
	        return true;
	    } catch (NameNotFoundException e) {
	        return false;
	    }
	}

	public static void setupEnableAdmin(Preference pref) {
		if (pref == null) {
			return;
		}
		enableAdminPref = (CheckBoxPreference)pref;

		// query the actual device admin status from the system
		enableAdminPref.setChecked(mDPM.isAdminActive(deviceAdmin));
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// these return non-null in the single pane view
		// the PreferenceFragment callbacks need to be used on Honeycomb+ with large screens
		setupFixLeak(findPreference("fixLeak"),getApplicationContext());
		setupEnableAdmin(findPreference("enableAdmin"));
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
	
	public static class LogPreferenceFragment extends
	UnifiedPreferenceFragment {
}

	public static class SecPreferenceFragment extends UnifiedPreferenceFragment {
		@SuppressLint("NewApi")
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setupEnableAdmin(findPreference("enableAdmin"));
		}
	}

	public static class FirewallPreferenceFragment extends
			UnifiedPreferenceFragment {
	}

	public static class MultiProfilePreferenceFragment extends
			UnifiedPreferenceFragment {
	}
	
	public static class CustomBinaryPreferenceFragment extends
	UnifiedPreferenceFragment {
}

	public static class ExpPreferenceFragment extends UnifiedPreferenceFragment {
		@SuppressLint("NewApi")
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setupFixLeak(findPreference("fixLeak"),getActivity().getApplicationContext());
		}
	}

	private static String getFixLeakPath() {
		for (String s : initDirs) {
			File f = new File(s);
			if (f.exists() && f.isDirectory()) {
				return s + "/" + initScript;
			}
		}
		return null;
	}

	private static boolean isFixLeakInstalled() {
		String path = getFixLeakPath();
		return path != null && new File(path).exists();
	}

	private void updateFixLeakScript(final boolean enabled) {
		final Context ctx = getApplicationContext();
		final String srcPath = new File(ctx.getDir("bin", 0), initScript).getAbsolutePath();

		new AsyncTask<Void, Void, Boolean>() {
			@Override
			public Boolean doInBackground(Void... args) {
				return enabled ?
						RootTools.copyFile(srcPath, getFixLeakPath(), true, false) :
						RootTools.deleteFileOrDirectory(getFixLeakPath(), true);
			}

			@Override
			public void onPostExecute(Boolean success) {
				int msgid;

				if (success) {
					msgid = enabled ? R.string.success_initd : R.string.remove_initd;
				} else {
					msgid = enabled ? R.string.unable_initd : R.string.unable_remove_initd;
					fixLeakPref.setChecked(isFixLeakInstalled());
				}
				Api.displayToasts(ctx, msgid, Toast.LENGTH_SHORT);
			}
		}.execute();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		
		if(key.equals("activeRules")){
			if(!G.activeRules()){
				G.enableRoam(false);
				G.enableLAN(false);
			}
		}
		
		if(key.equals("enableIPv6")){
			File defaultIP6TablesPath = new File("/system/bin/ip6tables");
			if(!defaultIP6TablesPath.exists()) {
				CheckBoxPreference connectionPref = (CheckBoxPreference) findPreference(key);
				connectionPref.setChecked(false);
				Api.alert(getApplicationContext(), getString(R.string.ip6unavailable));
			}
		}
		if (key.equals("showUid") || key.equals("enableMultiProfile")
				|| key.equals("disableIcons") || key.equals("enableVPN") || key.equals("enableLAN")
				|| key.equals("enableRoam") || key.equals("locale") || key.equals("showFilter") ) {
			// revert back to Default profile when disabling multi-profile support
			if (!G.enableMultiProfile()) {
				G.storedPosition(0);
			}
			G.reloadProfile();

			setResult(RESULT_OK, new Intent());
		}

		if (key.equals("fixLeak")) {
			boolean enabled = G.fixLeak();

			if (enabled != isFixLeakInstalled()) {
				updateFixLeakScript(enabled);
			}
		}
		
		if (key.equals("enableLog")) {
			Api.setLogging(getApplicationContext(), G.enableLog());
		}

		if (key.equals("enableAdmin")) {
			boolean value = G.enableAdmin();
			if (value) {
				Log.d("Device Admin Active ?", mDPM.isAdminActive(deviceAdmin) + "");
				if (!mDPM.isAdminActive(deviceAdmin)) {
					// Launch the activity to have the user enable our admin.
					Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
					intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,deviceAdmin);
					intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_desc));
					startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
				}
			} else {
				if (mDPM.isAdminActive(deviceAdmin)) {
					mDPM.removeActiveAdmin(deviceAdmin);
					Api.displayToasts(getApplicationContext(), R.string.device_admin_disabled, Toast.LENGTH_LONG);
				}
			}
		}
		
		if(key.equals("multiUser")){
			if(!Api.supportsMultipleUsers(getApplicationContext())) {
				CheckBoxPreference multiUserPref = (CheckBoxPreference) findPreference(key);
				multiUserPref.setChecked(false);
			} else {
				Api.setUserOwner(getApplicationContext());
			}
		}
		
		if(key.equals("enableStealthPattern")){
			Settings.Display.setStealthMode(getApplicationContext(), G.enableStealthPattern());
		}
		if(key.equals("enableLogService")) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			if(enabled) {
				Intent intent = new Intent(getApplicationContext(), LogService.class);
				getApplicationContext().startService(intent);
			} else {
				Intent intent = new Intent(getApplicationContext(), LogService.class);
				getApplicationContext().stopService(intent);
			}
			
		}
		
		if(key.equals("activeNotification")) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			if(enabled) {
				Api.showNotification(Api.isEnabled(getApplicationContext()),getApplicationContext());
			} else {
				NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(33341);
			}
			
		}
	}
}
