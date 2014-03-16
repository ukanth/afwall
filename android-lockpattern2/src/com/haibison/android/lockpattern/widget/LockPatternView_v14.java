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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;

/**
 * Helper class for {@link LockPatternView} in API 14+.
 * <p>
 * <b>Minimum API: 14</b>
 * </p>
 * 
 * @author Hai Bison
 * @since v2.4 beta
 */
public class LockPatternView_v14 extends LockPatternView {

    private final AccessibilityManager mAccessibilityManager;

    public LockPatternView_v14(Context context) {
        this(context, null);
    }// LockPatternView_v14()

    public LockPatternView_v14(Context context, AttributeSet attrs) {
        super(context, attrs);

        mAccessibilityManager = isInEditMode() ? null
                : (AccessibilityManager) context
                        .getSystemService(Context.ACCESSIBILITY_SERVICE);
    }// LockPatternView_v14()

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (mAccessibilityManager != null
                && mAccessibilityManager.isTouchExplorationEnabled()) {
            final int action = event.getAction();
            switch (action) {
            case MotionEvent.ACTION_HOVER_ENTER:
                event.setAction(MotionEvent.ACTION_DOWN);
                break;
            case MotionEvent.ACTION_HOVER_MOVE:
                event.setAction(MotionEvent.ACTION_MOVE);
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                event.setAction(MotionEvent.ACTION_UP);
                break;
            }
            onTouchEvent(event);
            event.setAction(action);
        }

        return super.onHoverEvent(event);
    }// onHoverEvent()

}
