package dev.ukanth.ufirewall.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.widget.Toast;

import com.stericson.roottools.RootTools;

import java.io.File;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.service.RootShell;
import dev.ukanth.ufirewall.util.G;

public class ExpPreferenceFragment extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {

	private static CheckBoxPreference fixLeakPref;

	private static final String mountPoints[] = { "/system", "/su" };
	private static final String initDirs[] = { "/system/etc/init.d",
			"/etc/init.d" , "/system/su.d", "/su/su.d" };
	private static final String initScript = "afwallstart";

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		setupFixLeak(findPreference("fixLeak"),this.getActivity().getApplicationContext());
		addPreferencesFromResource(R.xml.experimental_preferences);
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
		if (key.equals("fixLeak")) {
			boolean enabled = G.fixLeak();

			if (enabled != isFixLeakInstalled()) {
				updateFixLeakScript(enabled);
			}
		}
		
		if (key.equals("multiUser")) {
			if (!Api.supportsMultipleUsers(this.getActivity().getApplicationContext())) {
				CheckBoxPreference multiUserPref = (CheckBoxPreference) findPreference(key);
				multiUserPref.setChecked(false);
			} else {
				Api.setUserOwner(this.getActivity().getApplicationContext());
			}
		}
	}

	public static void setupFixLeak(Preference pref, Context ctx) {
		if (pref == null) {
			return;
		}
		fixLeakPref = (CheckBoxPreference) pref;

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

	private static boolean isFixLeakInstalled() {
		String path = getFixLeakPath();
		return path != null && new File(path).exists();
	}

	private void updateFixLeakScript(final boolean enabled) {
		final Context ctx = this.getActivity().getApplicationContext();
		final String srcPath = new File(ctx.getDir("bin", 0), initScript)
				.getAbsolutePath();

		new AsyncTask<Void, Void, Boolean>() {
			@Override
			public Boolean doInBackground(Void... args) {
				boolean returnFlag = false;
				for(String mount : mountPoints) {
					RootTools.remount(mount,"RW");
				}
				if(enabled) {
					for(String mount : mountPoints) {
						RootTools.remount(mount,"RW");
					}
					for (String s : initDirs) {
						File f = new File(s);
						if (f.exists() && f.isDirectory()) {
							//make sure it's executable
							new RootShell.RootCommand()
									.setReopenShell(true)
									.setLogging(true)
									.run(ctx, "chmod 755 " + f.getAbsolutePath());
							returnFlag = RootTools.copyFile(srcPath, (f.getAbsolutePath() + "/" + initScript),
									false, false);
							break;
						}
					}

				} else {
					returnFlag = deleteFiles(ctx);
				}

				for(String mount : mountPoints) {
					RootTools.remount(mount,"RO");
				}
				return returnFlag;
			}

			@Override
			public void onPostExecute(Boolean success) {
				int msgid;

				if (success) {
					msgid = enabled ? R.string.success_initd
							: R.string.remove_initd;
				} else {
					msgid = enabled ? R.string.unable_initd
							: R.string.unable_remove_initd;
					if(fixLeakPref == null) {
						fixLeakPref = (CheckBoxPreference) findPreference("fixLeak");
					}
					fixLeakPref.setChecked(isFixLeakInstalled());
				}
				Api.toast(ctx, getString(msgid), Toast.LENGTH_SHORT);
			}
		}.execute();
	}

	private Boolean deleteFiles(Context ctx) {
		final boolean[] returnFlag = {false};
		//mount filesystem
		for(String mount : mountPoints) {
			RootTools.remount(mount,"RW");
		}
		for (String s : initDirs) {
			File f = new File(s);
			if (f.exists() && f.isDirectory()) {
				String filePath  = s + "/" + initScript;
					new RootShell.RootCommand()
						.setReopenShell(true).setCallback(new RootShell.RootCommand.Callback() {
						@Override
						public void cbFunc(RootShell.RootCommand state) {
							if (state.exitCode == 0) {
								returnFlag[0] = true;
							}
						}
					}).setLogging(true).run(ctx, "rm -f " + filePath);
			}
		}
		for(String mount : mountPoints) {
			RootTools.remount(mount,"RO");
		}
		return returnFlag[0];
	}

	private static String getFixLeakPath() {
		for (String s : initDirs) {
			File f = new File(s);
			if (f.exists() && f.isDirectory()) {
				Log.i(Api.TAG, "Found init.d/su.d module support under " + f.getAbsolutePath());
				return s + "/" + initScript;
			}
		}
		return null;
	}

}
