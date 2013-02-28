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

import android.os.Bundle;
import android.preference.PreferenceActivity;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public abstract class UnifiedPreferenceActivity extends PreferenceActivity
		implements UnifiedPreferenceContainer {

	private UnifiedPreferenceHelper mHelper = new UnifiedPreferenceHelper(this);

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if device doesn't have newer APIs like {@link PreferenceFragment},
	 * or if forced via {@link onIsHidingHeaders}, or the device doesn't have an
	 * extra-large screen. In these cases, a single-pane "simplified" settings
	 * UI should be shown.
	 */
	public boolean isSinglePane() {
		return mHelper.isSinglePane();
	}

	/**
	 * Returns the header resource to be used when building headers.
	 * 
	 * @return The id of the header resource
	 */
	@Override
	public int getHeaderRes() {
		return mHelper.getHeaderRes();
	}

	/**
	 * Sets the header resource to be used when building headers.
	 * This must be called before super.onCreate unless overriding both
	 * {@link #onBuildHeaders(List)} and {@link #onBuildLegacyHeaders(List)}
	 * 
	 * @param headerRes The id of the header resource
	 */
	@Override
	public void setHeaderRes(int headerRes) {
		mHelper.setHeaderRes(headerRes);
	}

	/**
	 * Returns the current name of the SharedPreferences file that preferences
	 * managed by this will use.
	 *
	 * @return The name that can be passed to {@link Context#getSharedPreferences(String, int)}
	 * @see UnifiedPreferenceHelper#getSharedPreferencesName()
	 */
	@Override
	public String getSharedPreferencesName() {
		return mHelper.getSharedPreferencesName();
	}

	/**
	 * Sets the name of the SharedPreferences file that preferences managed by
	 * this will use.
	 *
	 * @param sharedPreferencesName The name of the SharedPreferences file.
	 * @see UnifiedPreferenceHelper#setSharedPreferencesName()
	 */
	@Override
	public void setSharedPreferencesName(String sharedPreferencesName) {
		mHelper.setSharedPreferencesName(sharedPreferencesName);
	}

	/**
	 * Returns the current mode of the SharedPreferences file that preferences
	 * managed by this will use.
	 *
	 * @return The mode that can be passed to {@link Context#getSharedPreferences(String, int)}
	 * @see UnifiedPreferenceHelper#getSharedPreferencesMode()
	 */
	@Override
	public int getSharedPreferencesMode() {
		return mHelper.getSharedPreferencesMode();
	}

	/**
	 * Sets the mode of the SharedPreferences file that preferences managed by
	 * this will use.
	 *
	 * @param sharedPreferencesMode The mode of the SharedPreferences file.
	 * @see UnifiedPreferenceHelper#setSharedPreferencesMode()
	 */
	@Override
	public void setSharedPreferencesMode(int sharedPreferencesMode) {
		mHelper.setSharedPreferencesMode(sharedPreferencesMode);
	}

	/** {@inheritDoc} */
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		mHelper.onPostCreate(savedInstanceState);
	}

	/**
	 * Called when the activity needs its list of headers built. By implementing
	 * this and adding at least one item to the list, you will cause the
	 * activity to run in its modern fragment mode. Note that this function may
	 * not always be called; for example, if the activity has been asked to
	 * display a particular fragment without the header list, there is no need
	 * to build the headers.
	 * 
	 * <p>
	 * Typical implementations will use {@link #loadHeadersFromResource} to fill
	 * in the list from a resource. For convenience this is done if a header
	 * resource has been set with {@link #setHeaderRes(int)}.
	 * 
	 * @param target The list in which to place the headers.
	 */
	public void onBuildHeaders(List<Header> target) {
		mHelper.onBuildHeaders(target);
	}

	/**
	 * Called when the activity needs its list of legacy headers built.
	 * 
	 * <p>
	 * Typical implementations will use {@link #loadLegacyHeadersFromResource}
	 * to fill in the list from a resource. For convenience this is done if a
	 * header resource has been set with {@link #setHeaderRes(int)}.
	 * 
	 * @param target The list in which to place the legacy headers.
	 */
	public void onBuildLegacyHeaders(List<LegacyHeader> target) {
		mHelper.onBuildLegacyHeaders(target);
	}

	/**
	 * Bind the summaries of EditText/List/Dialog/Ringtone preferences
	 * to their values. When their values change, their summaries are
	 * updated to reflect the new value, per the Android Design
	 * guidelines.
	 */
	public void onBindPreferenceSummariesToValues() {
		mHelper.onBindPreferenceSummariesToValues();
	}

	/** {@inheritDoc} */
	@Override
	public void loadHeadersFromResource(int resid, List<Header> target) {
		mHelper.loadHeadersFromResource(resid, target);
	}

	/**
	 * Parse the given XML file as a header description, adding each parsed
	 * LegacyHeader into the target list.
	 * 
	 * @param resid The XML resource to load and parse.
	 * @param target The list in which the parsed headers should be placed.
	 */
	public void loadLegacyHeadersFromResource(int resid, List<LegacyHeader> target) {
		mHelper.loadLegacyHeadersFromResource(resid, target);
	}
}
