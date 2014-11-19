/*
 *   Copyright 2012 Hai Bison
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.haibison.android.lockpattern.util;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Window;

import com.haibison.android.lockpattern.BuildConfig;

/**
 * UI utilities.
 * 
 * @author Hai Bison
 */
public class UI {

    private static final String CLASSNAME = UI.class.getName();

    /**
     * The screen sizes.
     * 
     * @author Hai Bison
     * 
     */
    public static enum ScreenSize {
        /**
         * Small.
         */
        SMALL(1, 1, 1, 1),
        /**
         * Normal.
         */
        NORMAL(1, 1, 1, 1),
        /**
         * Large.
         */
        LARGE(.6f, .9f, .6f, .9f),
        /**
         * X-Large.
         */
        XLARGE(.6f, .9f, .5f, .7f),
        /**
         * Undefined.
         */
        UNDEFINED(1, 1, 1, 1);

        /**
         * The desired fixed width for a dialog along the minor axis (the screen
         * is in portrait). This is a fraction.
         */
        public final float fixedWidthMinor,
        /**
         * The desired fixed width for a dialog along the major axis (the screen
         * is in landscape). This is a fraction.
         */
        fixedWidthMajor,
        /**
         * The desired fixed height for a dialog along the minor axis (the
         * screen is in landscape). This is a fraction.
         */
        fixedHeightMinor,
        /**
         * The desired fixed height for a dialog along the major axis (the
         * screen is in portrait). This is a fraction.
         */
        fixedHeightMajor;

        /**
         * Creates new instance.
         * 
         * @param fixedHeightMajor
         *            the fixed height major.
         * @param fixedHeightMinor
         *            the fixed height minor.
         * @param fixedWidthMajor
         *            the fixed width major.
         * @param fixedWidthMinor
         *            the fixed width minor.
         */
        private ScreenSize(float fixedHeightMajor, float fixedHeightMinor,
                float fixedWidthMajor, float fixedWidthMinor) {
            this.fixedHeightMajor = fixedHeightMajor;
            this.fixedHeightMinor = fixedHeightMinor;
            this.fixedWidthMajor = fixedWidthMajor;
            this.fixedWidthMinor = fixedWidthMinor;
        }// ScreenSize()

        /**
         * Gets current screen size.
         * 
         * @param context
         *            the context.
         * @return current screen size.
         */
        public static ScreenSize getCurrent(Context context) {
            switch (context.getResources().getConfiguration().screenLayout
                    & Configuration.SCREENLAYOUT_SIZE_MASK) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                return SMALL;
            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                return NORMAL;
            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                return LARGE;
            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                return XLARGE;
            default:
                return UNDEFINED;
            }
        }// getCurrent()

    }// ScreenSize

    /**
     * This is singleton class.
     */
    private UI() {
    }// UI

    /**
     * Uses a fixed size for {@code dialog} in large screens.
     * 
     * @param dialog
     *            the dialog.
     */
    public static void adjustDialogSizeForLargeScreens(Dialog dialog) {
        adjustDialogSizeForLargeScreens(dialog.getWindow());
    }// adjustDialogSizeForLargeScreens()

    /**
     * Uses a fixed size for {@code dialogWindow} in large screens.
     * 
     * @param dialogWindow
     *            the window <i>of the dialog</i>.
     */
    public static void adjustDialogSizeForLargeScreens(Window dialogWindow) {
        if (BuildConfig.DEBUG)
            Log.d(CLASSNAME, "adjustDialogSizeForLargeScreens()");

        if (!dialogWindow.isFloating())
            return;

        final ScreenSize screenSize = ScreenSize.getCurrent(dialogWindow
                .getContext());
        switch (screenSize) {
        case LARGE:
        case XLARGE:
            break;
        default:
            return;
        }

        final DisplayMetrics metrics = dialogWindow.getContext().getResources()
                .getDisplayMetrics();
        final boolean isPortrait = metrics.widthPixels < metrics.heightPixels;

        int width = metrics.widthPixels;// dialogWindow.getDecorView().getWidth();
        int height = metrics.heightPixels;// dialogWindow.getDecorView().getHeight();
        if (BuildConfig.DEBUG)
            Log.d(CLASSNAME,
                    String.format("width = %,d | height = %,d", width, height));

        width = (int) (width * (isPortrait ? screenSize.fixedWidthMinor
                : screenSize.fixedWidthMajor));
        height = (int) (height * (isPortrait ? screenSize.fixedHeightMajor
                : screenSize.fixedHeightMinor));

        if (BuildConfig.DEBUG)
            Log.d(CLASSNAME, String.format(
                    "NEW >>> width = %,d | height = %,d", width, height));
        dialogWindow.setLayout(width, height);
    }// adjustDialogSizeForLargeScreens()

    /**
     * Convenient method for {@link Context#getTheme()} and
     * {@link Resources.Theme#resolveAttribute(int, TypedValue, boolean)}.
     * 
     * @param context
     *            the context.
     * @param resId
     *            The resource identifier of the desired theme attribute.
     * @return the resource ID that {@link TypedValue#resourceId} points to, or
     *         {@code 0} if not found.
     */
    public static int resolveAttribute(Context context, int resId) {
        return resolveAttribute(context, resId, 0);
    }// resolveAttribute()

    /**
     * Convenient method for {@link Context#getTheme()} and
     * {@link Resources.Theme#resolveAttribute(int, TypedValue, boolean)}.
     * 
     * @param context
     *            the context.
     * @param resId
     *            The resource identifier of the desired theme attribute.
     * @param defaultValue
     *            the default value if cannot resolve {@code resId}.
     * @return the resource ID that {@link TypedValue#resourceId} points to, or
     *         {@code defaultValue} if not found.
     */
    public static int resolveAttribute(Context context, int resId,
            int defaultValue) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(resId, typedValue, true))
            return typedValue.resourceId;
        return defaultValue;
    }// resolveAttribute()

}
