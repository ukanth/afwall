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

package com.haibison.android.lockpattern.widget;

import android.view.View;

/**
 * Helper class for {@link View} in API 16+.
 * 
 * @author Hai Bison
 * @since v2.4 beta
 */
public class ViewCompat_v16 {

    /**
     * This is singleton class.
     */
    private ViewCompat_v16() {
    }// ViewCompat_v16

    /**
     * Wrapper for {@link View#announceForAccessibility(CharSequence)}.
     * 
     * @param view
     *            a view.
     * @param text
     *            The announcement text.
     * @see View#announceForAccessibility(CharSequence)
     */
    public static void announceForAccessibility(View view, CharSequence text) {
        view.announceForAccessibility(text);
    }// announceForAccessibility()

}
