package dev.ukanth.ufirewall.preferences;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class CustomBinaryPreferenceFragment extends PreferenceFragment {
	private static final String KEY_IPT_PATH = "ipt_path";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.ui_custom_preferences);
		ListPreference ipt = (ListPreference) findPreference(KEY_IPT_PATH);
		if (ipt != null) {
			ipt.setOnPreferenceChangeListener((preference, newValue) -> {
				// an explicit choice: give the built-in binary another chance
				G.setBuiltinIptablesFailed(false);
				// the new value is saved after this returns
				new Handler(Looper.getMainLooper()).post(() -> showIptablesInUse(G.ip_path()));
				return true;
			});
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		showIptablesInUse(G.ip_path());
	}

	/**
	 * The choice is a preference, not a guarantee (a missing system binary falls back to the
	 * built-in one and vice versa), so show the binary that is actually used.
	 */
	private void showIptablesInUse(String choice) {
		final ListPreference ipt = (ListPreference) findPreference(KEY_IPT_PATH);
		final Activity activity = getActivity();
		if (ipt == null || activity == null) {
			return;
		}
		final Context ctx = activity.getApplicationContext();
		final CharSequence chosen = entryFor(ipt, choice);
		new Thread(() -> {
			// may install the built-in binaries, so off the main thread
			String inUse = Api.getBinaryPath(ctx, false);
			String builtinDir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
			boolean usingBuiltin = inUse.startsWith(builtinDir);
			String summary;
			if (choice.equals("system") && usingBuiltin) {
				summary = ctx.getString(R.string.ipt_system_missing, inUse);
			} else if (choice.equals("builtin") && G.isBuiltinIptablesFailed()) {
				summary = ctx.getString(R.string.ipt_builtin_failed, chosen);
			} else {
				summary = ctx.getString(R.string.ipt_in_use, chosen, inUse);
			}
			activity.runOnUiThread(() -> {
				if (isAdded()) {
					ipt.setSummary(summary);
				}
			});
		}, "afwall-ipt-summary").start();
	}

	private static CharSequence entryFor(ListPreference pref, String value) {
		int index = pref.findIndexOfValue(value);
		return index >= 0 ? pref.getEntries()[index] : value;
	}
}
