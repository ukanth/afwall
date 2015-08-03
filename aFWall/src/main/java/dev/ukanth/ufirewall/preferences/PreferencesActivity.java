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

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.io.File;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.LogService;
import dev.ukanth.ufirewall.util.G;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
	
	private static final boolean ALWAYS_SIMPLE_PREFS = false;
	private Toolbar mToolBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// set language
		Api.updateLanguage(getApplicationContext(), G.locale());
		super.onCreate(savedInstanceState);
		prepareLayout();
	}


	private void prepareLayout() {
		ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
		View content = root.getChildAt(0);
		LinearLayout toolbarContainer = (LinearLayout) View.inflate(this, R.layout.activity_prefs, null);

		root.removeAllViews();
		toolbarContainer.addView(content);
		root.addView(toolbarContainer);

		mToolBar = (Toolbar) toolbarContainer.findViewById(R.id.toolbar);
		mToolBar.setTitle(getTitle());
		mToolBar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
		mToolBar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});


	}

	@Override
	public View onCreateView(String name, Context context, AttributeSet attrs) {
		// Allow super to try and create a view first
		final View result = super.onCreateView(name, context, attrs);
		if (result != null) {
			return result;
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			// If we're running pre-L, we need to 'inject' our tint aware Views in place of the
			// standard framework versions
			switch (name) {
				case "EditText":
					return new AppCompatEditText(this, attrs);
				case "Spinner":
					return new AppCompatSpinner(this, attrs);
				case "CheckBox":
					return new AppCompatCheckBox(this, attrs);
				case "RadioButton":
					return new AppCompatRadioButton(this, attrs);
				case "CheckedTextView":
					return new AppCompatCheckedTextView(this, attrs);
			}
		}

		return null;
	}


	@Override
	public void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);

	}

	@Override
	public void onPause() {
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected boolean isValidFragment(String fragmentName) {
		if (UIPreferenceFragment.class.getName().equals(fragmentName)
				|| LogPreferenceFragment.class.getName().equals(fragmentName)
				|| ExpPreferenceFragment.class.getName().equals(fragmentName)
				|| CustomBinaryPreferenceFragment.class.getName().equals(
						fragmentName)
				|| SecPreferenceFragment.class.getName().equals(fragmentName)
				|| MultiProfilePreferenceFragment.class.getName().equals(fragmentName)) {
			return (true);
		}

		return (false);
	}
	
	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.preferences_headers, target);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
	}
	
	/** {@inheritDoc} */
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this) && !isSimplePreferences(this);
	}

	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
	 * doesn't have newer APIs like {@link PreferenceFragment}, or the device
	 * doesn't have an extra-large screen. In these cases, a single-pane
	 * "simplified" settings UI should be shown.
	 */
	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
				|| !isXLargeTablet(context);
	}



	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		Context ctx = getApplicationContext();
		if (key.equals("activeRules")) {
			if (!G.activeRules()) {
				G.enableRoam(false);
				G.enableLAN(false);
			}
		}

		if (key.equals("enableIPv6")) {
			File defaultIP6TablesPath = new File("/system/bin/ip6tables");
			if (!defaultIP6TablesPath.exists()) {
				CheckBoxPreference connectionPref = (CheckBoxPreference) findPreference(key);
				connectionPref.setChecked(false);
				Api.toast(ctx, getString(R.string.ip6unavailable));
			}
		}
		if (key.equals("showUid") || key.equals("disableIcons") || key.equals("enableVPN")
				|| key.equals("enableLAN") || key.equals("enableRoam")
				|| key.equals("locale") || key.equals("showFilter")) {
			// revert back to Default profile when disabling multi-profile
			// support
			G.reloadProfile();
		}

		if(key.equals("activeNotification")) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			if(enabled) {
				Api.showNotification(Api.isEnabled(ctx),ctx);
			} else {
				NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(33341);
			}
		}

		if (key.equals("enableLog")) {
			Api.setLogging(ctx, G.enableLog());
		}

		if (key.equals("enableLogService")) {
			boolean enabled = sharedPreferences.getBoolean(key, false);
			if (enabled) {
				Intent intent = new Intent(ctx, LogService.class);
				ctx.startService(intent);
			} else {
				Intent intent = new Intent(ctx, LogService.class);
				ctx.stopService(intent);
			}
		}

		if(key.equals("enableMultiProfile")) {
			/*if (!G.enableMultiProfile()) {
				G.storedPosition(0);
			}*/
			G.reloadProfile();
		}

	}
}
