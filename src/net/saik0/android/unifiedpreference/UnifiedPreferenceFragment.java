/* 
** Copyright 2012, Joel Pedraza
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package net.saik0.android.unifiedpreference;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public abstract class UnifiedPreferenceFragment extends PreferenceFragment {
	public static final String ARG_PREFERENCE_RES = "unifiedpreference_preferenceRes";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Pseudo-inherit sharedPreferencesName and sharedPreferencesMode from Activity
		UnifiedPreferenceContainer container = (UnifiedPreferenceContainer) getActivity();
		String sharedPreferencesName = container.getSharedPreferencesName();
		int sharedPreferencesMode = container.getSharedPreferencesMode();
		PreferenceManager preferenceManager = getPreferenceManager();
		if (!TextUtils.isEmpty(sharedPreferencesName)) {
			preferenceManager.setSharedPreferencesName(sharedPreferencesName);
		}
		if (sharedPreferencesMode != 0) {
			preferenceManager.setSharedPreferencesMode(sharedPreferencesMode);
		}

		// Inflate from preferences.xml file
		int layoutRes = getArguments().getInt(ARG_PREFERENCE_RES, 0);
		if (layoutRes > 0) {
			addPreferencesFromResource(layoutRes);
			onBindPreferenceSummariesToValues();
		}
	}

	/**
	 * Bind the summaries of EditText/List/Dialog/Ringtone preferences
	 * to their values. When their values change, their summaries are
	 * updated to reflect the new value, per the Android Design
	 * guidelines.
	 */
	protected void onBindPreferenceSummariesToValues() {
		UnifiedPreferenceUtils.bindAllPreferenceSummariesToValues(getPreferenceScreen());
	}
}
