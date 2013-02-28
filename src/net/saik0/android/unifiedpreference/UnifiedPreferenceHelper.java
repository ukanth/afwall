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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import net.saik0.android.unifiedpreference.internal.utils.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceActivity.Header;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;
import dev.ukanth.ufirewall.R;

public class UnifiedPreferenceHelper {
	private final PreferenceActivity mActivity;
	private int mHeaderRes;
	private List<LegacyHeader> mLegacyHeaders = new LinkedList<LegacyHeader>();
	private Boolean mSinglePane;
	private String mSharedPreferencesName;
	private int mSharedPreferencesMode;

	public UnifiedPreferenceHelper(PreferenceActivity activity) {
		mActivity = activity;
	}

	/**
	 * Default value for {@link Header#id Header.id} indicating that no
	 * identifier value is set. All other values (including those below -1) are
	 * valid.
	 */
	public static final long HEADER_ID_UNDEFINED = -1;

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if device doesn't have newer APIs like {@link PreferenceFragment},
	 * or if forced via {@link onIsHidingHeaders}, or the device doesn't have an
	 * extra-large screen. In these cases, a single-pane "simplified" settings
	 * UI should be shown.
	 */
	public boolean isSinglePane() {
		if (mSinglePane == null) {
			mSinglePane = Boolean
					.valueOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
							|| !mActivity.onIsMultiPane()
							|| mActivity.onIsHidingHeaders());
		}
		return mSinglePane.booleanValue();
	}

	/**
	 * Returns the header resource to be used when building headers.
	 * 
	 * @return The id of the header resource
	 */
	public int getHeaderRes() {
		return mHeaderRes;
	}

	/**
	 * Sets the header resource to be used when building headers.
	 * This must be called before super.onCreate unless overriding both
	 * {@link #onBuildHeaders(List)} and {@link #onBuildLegacyHeaders(List)}
	 * 
	 * @param headerRes The id of the header resource
	 */
	public void setHeaderRes(int headerRes) {
		mHeaderRes = headerRes;
	}

	/**
	 * Returns the current name of the SharedPreferences file that preferences
	 * managed by this will use. Wraps
	 * {@link PreferenceManager#getSharedPreferencesName()} if single pane,
	 * otherwise returns a locally cached String.
	 * 
	 * @return The name that can be passed to
	 *         {@link Context#getSharedPreferences(String, int)}
	 */
	@SuppressWarnings("deprecation")
	public String getSharedPreferencesName() {
		if (mActivity.getPreferenceManager() != null) {
			return mActivity.getPreferenceManager().getSharedPreferencesName();
		}
		return mSharedPreferencesName;
	}

	/**
	 * Sets the name of the SharedPreferences file that preferences managed by
	 * this will use. Wraps {@link PreferenceManager#setSharedPreferencesName()}
	 * if single pane, otherwise cache it for use by
	 * {@link UnifiedPreferenceFragment}.
	 * 
	 * @param sharedPreferencesName The name of the SharedPreferences file.
	 */
	@SuppressWarnings("deprecation")
	public void setSharedPreferencesName(String sharedPreferencesName) {
		if (mActivity.getPreferenceManager() != null) {
			mActivity.getPreferenceManager().setSharedPreferencesName(
					sharedPreferencesName);
		}
		mSharedPreferencesName = sharedPreferencesName;
	}

	/**
	 * Returns the current mode of the SharedPreferences file that preferences
	 * managed by this will use. Wraps
	 * {@link PreferenceManager#getSharedPreferencesMode()} if single pane,
	 * otherwise returns a locally cached int.
	 * 
	 * @return The mode that can be passed to
	 *         {@link Context#getSharedPreferences(String, int)}
	 */
	@SuppressWarnings("deprecation")
	public int getSharedPreferencesMode() {
		if (mActivity.getPreferenceManager() != null) {
			return mActivity.getPreferenceManager().getSharedPreferencesMode();
		}
		return mSharedPreferencesMode;
	}

	/**
	 * Sets the mode of the SharedPreferences file that preferences managed by
	 * this will use. Wraps {@link PreferenceManager#setSharedPreferencesMode()}
	 * if single pane, otherwise cache it for use by
	 * {@link UnifiedPreferenceFragment}.
	 * 
	 * @param sharedPreferencesMode The mode of the SharedPreferences file.
	 */
	@SuppressWarnings("deprecation")
	public void setSharedPreferencesMode(int sharedPreferencesMode) {
		if (mActivity.getPreferenceManager() != null) {
			mActivity.getPreferenceManager().setSharedPreferencesMode(
					sharedPreferencesMode);
		}
		mSharedPreferencesMode = sharedPreferencesMode;
	}

	/**
	 * Shows the simplified settings UI if the device configuration if the
	 * device configuration dictates that a simplified, single-pane UI should be
	 * shown.
	 */
	@SuppressWarnings("deprecation")
	protected void onPostCreate(Bundle savedInstanceState) {
		if (isSinglePane()) {
			// In the simplified UI, fragments are not used at all and we
			// instead use the older PreferenceActivity APIs.

			// Set PreferenceManager options if the activity received them before onPostCreate
			if (!TextUtils.isEmpty(mSharedPreferencesName)) {
				mActivity.getPreferenceManager().setSharedPreferencesName(mSharedPreferencesName);
			}
			if (mSharedPreferencesMode != 0) {
				mActivity.getPreferenceManager().setSharedPreferencesMode(mSharedPreferencesMode);
			}

			onBuildLegacyHeaders(mLegacyHeaders);

			// Add all preferences, first create a new preference screen if necessary
			if (mActivity.getPreferenceScreen() == null) {
				mActivity.setPreferenceScreen(mActivity.getPreferenceManager()
						.createPreferenceScreen(mActivity));
			}
			for (LegacyHeader header : mLegacyHeaders) {
				PreferenceCategory category = new PreferenceCategory(mActivity);
				category.setTitle(header.getTitle(mActivity.getResources()));
				mActivity.getPreferenceScreen().addPreference(category);
				mActivity.addPreferencesFromResource(header.preferenceRes);
			}

			onBindPreferenceSummariesToValues();
		}
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
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onBuildHeaders(List<Header> target) {
		// Do not build headers unless in single pane mode.
		if (!isSinglePane() && mHeaderRes > 0) {
			loadHeadersFromResource(mHeaderRes, target);
		}
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
		if (mHeaderRes > 0) {
			loadLegacyHeadersFromResource(mHeaderRes, mLegacyHeaders);
		}
	}

	/** 
	 * Bind the summaries of EditText/List/Dialog/Ringtone preferences to their
	 * values. When their values change, their summaries are updated to reflect
	 * the new value, per the Android Design guidelines.
	 */
	@SuppressWarnings("deprecation")
	public void onBindPreferenceSummariesToValues() {
		UnifiedPreferenceUtils.bindAllPreferenceSummariesToValues(mActivity
				.getPreferenceScreen());
	}

	/**
	 * Parse the given XML file as a header description, adding each parsed
	 * Header into the target list.
	 * 
	 * @param resid The XML resource to load and parse.
	 * @param target The list in which the parsed headers should be placed.
	 */
	public void loadHeadersFromResource(int resid, List<Header> target) {
		XmlResourceParser parser = null;
		try {
			parser = mActivity.getResources().getXml(resid);
			AttributeSet attrs = Xml.asAttributeSet(parser);

			int type;
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
					&& type != XmlPullParser.START_TAG) {
				// Parse next until start tag is found
			}

			String nodeName = parser.getName();
			if (!"preference-headers".equals(nodeName)) {
				throw new RuntimeException(
						"XML document must start with <preference-headers> tag; found"
								+ nodeName + " at "
								+ parser.getPositionDescription());
			}

			Bundle curBundle = null;

			final int outerDepth = parser.getDepth();
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
					&& (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
				if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
					continue;
				}

				nodeName = parser.getName();
				if ("header".equals(nodeName)) {
					Header header = new Header();

					TypedArray sa = mActivity.getResources().obtainAttributes(
							attrs, R.styleable.PreferenceHeader);
					header.id = sa.getResourceId(
							R.styleable.PreferenceHeader_id,
							(int) HEADER_ID_UNDEFINED);
					TypedValue tv = sa
							.peekValue(R.styleable.PreferenceHeader_title);
					if (tv != null && tv.type == TypedValue.TYPE_STRING) {
						if (tv.resourceId != 0) {
							header.titleRes = tv.resourceId;
						} else {
							header.title = tv.string;
						}
					}
					tv = sa.peekValue(R.styleable.PreferenceHeader_summary);
					if (tv != null && tv.type == TypedValue.TYPE_STRING) {
						if (tv.resourceId != 0) {
							header.summaryRes = tv.resourceId;
						} else {
							header.summary = tv.string;
						}
					}
					tv = sa.peekValue(R.styleable.PreferenceHeader_breadCrumbTitle);
					if (tv != null && tv.type == TypedValue.TYPE_STRING) {
						if (tv.resourceId != 0) {
							header.breadCrumbTitleRes = tv.resourceId;
						} else {
							header.breadCrumbTitle = tv.string;
						}
					}
					tv = sa.peekValue(R.styleable.PreferenceHeader_breadCrumbShortTitle);
					if (tv != null && tv.type == TypedValue.TYPE_STRING) {
						if (tv.resourceId != 0) {
							header.breadCrumbShortTitleRes = tv.resourceId;
						} else {
							header.breadCrumbShortTitle = tv.string;
						}
					}
					header.iconRes = sa.getResourceId(
							R.styleable.PreferenceHeader_icon, 0);
					header.fragment = sa
							.getString(R.styleable.PreferenceHeader_fragment);

					if (curBundle == null) {
						curBundle = new Bundle();
					}

					// Add preference resource to fragmentArgs
					int preference = sa.getResourceId(
							R.styleable.PreferenceHeader_preferenceRes, 0);
					if (preference > 0) {
						curBundle.putInt(
								UnifiedPreferenceFragment.ARG_PREFERENCE_RES, preference);
					}
					sa.recycle();

					final int innerDepth = parser.getDepth();
					while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
							&& (type != XmlPullParser.END_TAG || parser
									.getDepth() > innerDepth)) {
						if (type == XmlPullParser.END_TAG
								|| type == XmlPullParser.TEXT) {
							continue;
						}

						String innerNodeName = parser.getName();
						if (innerNodeName.equals("extra")) {
							mActivity.getResources().parseBundleExtra("extra",
									attrs, curBundle);
							XmlUtils.skipCurrentTag(parser);

						} else if (innerNodeName.equals("intent")) {
							header.intent = Intent.parseIntent(
									mActivity.getResources(), parser, attrs);

						} else {
							XmlUtils.skipCurrentTag(parser);
						}
					}

					if (curBundle.size() > 0) {
						header.fragmentArguments = curBundle;
						curBundle = null;
					}

					target.add(header);
				} else {
					XmlUtils.skipCurrentTag(parser);
				}
			}

		} catch (XmlPullParserException e) {
			throw new RuntimeException("Error parsing headers", e);
		} catch (IOException e) {
			throw new RuntimeException("Error parsing headers", e);
		} finally {
			if (parser != null)
				parser.close();
		}
	}

	/**
	 * Parse the given XML file as a header description, adding each parsed
	 * LegacyHeader into the target list.
	 * 
	 * @param resid The XML resource to load and parse.
	 * @param target The list in which the parsed headers should be placed.
	 */
	public void loadLegacyHeadersFromResource(int resid, List<LegacyHeader> target) {
		XmlResourceParser parser = null;
		try {
			parser = mActivity.getResources().getXml(resid);
			AttributeSet attrs = Xml.asAttributeSet(parser);

			int type;
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
					&& type != XmlPullParser.START_TAG) {
				// Parse next until start tag is found
			}

			String nodeName = parser.getName();
			if (!"preference-headers".equals(nodeName)) {
				throw new RuntimeException(
						"XML document must start with <preference-headers> tag; found"
								+ nodeName + " at "
								+ parser.getPositionDescription());
			}

			final int outerDepth = parser.getDepth();
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
					&& (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
				if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
					continue;
				}

				nodeName = parser.getName();
				if ("header".equals(nodeName)) {
					LegacyHeader header = new LegacyHeader();

					TypedArray sa = mActivity.getResources().obtainAttributes(
							attrs, R.styleable.PreferenceHeader);
					TypedValue tv = sa
							.peekValue(R.styleable.PreferenceHeader_title);
					if (tv != null && tv.type == TypedValue.TYPE_STRING) {
						if (tv.resourceId != 0) {
							header.titleRes = tv.resourceId;
						} else {
							header.title = tv.string;
						}
					}
					header.preferenceRes = sa.getResourceId(
							R.styleable.PreferenceHeader_preferenceRes, 0);

					sa.recycle();

					target.add(header);
				} else {
					XmlUtils.skipCurrentTag(parser);
				}
			}

		} catch (XmlPullParserException e) {
			throw new RuntimeException("Error parsing headers", e);
		} catch (IOException e) {
			throw new RuntimeException("Error parsing headers", e);
		} finally {
			if (parser != null)
				parser.close();
		}
	}
}
