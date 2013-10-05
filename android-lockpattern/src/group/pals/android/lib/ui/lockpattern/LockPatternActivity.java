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

package group.pals.android.lib.ui.lockpattern;

import group.pals.android.lib.ui.lockpattern.util.IEncrypter;
import group.pals.android.lib.ui.lockpattern.util.InvalidEncrypterException;
import group.pals.android.lib.ui.lockpattern.widget.LockPatternUtils;
import group.pals.android.lib.ui.lockpattern.widget.LockPatternView;
import group.pals.android.lib.ui.lockpattern.widget.LockPatternView.Cell;
import group.pals.android.lib.ui.lockpattern.widget.LockPatternView.DisplayMode;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Main activity for this library.
 * 
 * @author Hai Bison
 * @since v1.0
 */
public class LockPatternActivity extends Activity {

    public static final String _ClassName = LockPatternActivity.class.getName();

    /**
     * Sets value of this key to a theme in {@code android.R.style.Theme_*}.<br>
     * Default is:<br>
     * 
     * <li>{@link android.R.style#Theme_DeviceDefault} for {@code SDK >= }
     * {@link Build.VERSION_CODES#ICE_CREAM_SANDWICH}</li>
     * 
     * <li>{@link android.R.style#Theme_Holo} for {@code SDK >= }
     * {@link Build.VERSION_CODES#HONEYCOMB}</li>
     * 
     * <li>{@link android.R.style#Theme} for older systems</li>
     * 
     * @since v1.5.3 beta
     */
    public static final String _Theme = _ClassName + ".theme";

    /**
     * Mode for {@link LockPatternActivity}. Default is
     * {@link LPMode#CreatePattern}<br>
     * Acceptable values:<br>
     * - {@link LPMode#CreatePattern}<br>
     * - {@link LPMode#ComparePattern}
     */
    public static final String _Mode = LPMode.class.getName();

    /**
     * Lock pattern mode for this activity.
     * 
     * @author Hai Bison
     * @since v1.3 alpha
     */
    public static enum LPMode {
        /**
         * Creates new pattern.
         */
        CreatePattern,
        /**
         * Compares to existing pattern.
         */
        ComparePattern
    }

    /**
     * Specify if the pattern will be saved automatically or not. Default =
     * {@code false}
     */
    public static final String _AutoSave = _ClassName + ".auto_save";

    /**
     * Maximum retry times, in mode {@link #ComparePattern}, default is
     * {@code 5}.
     */
    public static final String _MaxRetry = _ClassName + ".max_retry";

    /**
     * Key to hold pattern. Can be a SHA-1 string <i><b>or</b></i> an encrypted
     * string of its (if {@link #_EncrypterClass} is used).
     * 
     * @since v2 beta
     */
    public static final String _Pattern = _ClassName + ".pattern";

    /**
     * Key to hold implemented class of {@link IEncrypter}.<br>
     * If {@code null}, nothing will be used.
     * 
     * @since v2 beta
     */
    public static final String _EncrypterClass = IEncrypter.class.getName();

    private SharedPreferences mPrefs;
    private LPMode mMode;
    private int mMaxRetry;
    private boolean mAutoSave;
    private IEncrypter mEncrypter;

    private TextView mTxtInfo;
    private LockPatternView mLockPatternView;
    private View mFooter;
    private Button mBtnCancel;
    private Button mBtnConfirm;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        /*
         * THEME
         */

        int theme;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            theme = getIntent().getIntExtra(_Theme, android.R.style.Theme_DeviceDefault);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            theme = getIntent().getIntExtra(_Theme, android.R.style.Theme_Holo);
        else
            theme = getIntent().getIntExtra(_Theme, android.R.style.Theme);
        setTheme(theme);

        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(LockPatternActivity.class.getName(), 0);

        mMode = (LPMode) getIntent().getSerializableExtra(_Mode);
        if (mMode == null)
            mMode = LPMode.CreatePattern;

        mMaxRetry = getIntent().getIntExtra(_MaxRetry, 5);

        // set this to false by default, for security enhancement
        mAutoSave = getIntent().getBooleanExtra(_AutoSave, false);
        // if false, clear previous values (currently it is the pattern only)
        if (!mAutoSave)
            mPrefs.edit().clear().commit();

        // encrypter
        Class<?> encrypterClass = (Class<?>) getIntent().getSerializableExtra(_EncrypterClass);
        if (encrypterClass != null) {
            try {
                mEncrypter = (IEncrypter) encrypterClass.newInstance();
            } catch (Throwable t) {
                throw new InvalidEncrypterException();
            }
        }

        init();
    }// onCreate()

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(_ClassName, "onConfigurationChanged()");
        super.onConfigurationChanged(newConfig);
        init();
    }// onConfigurationChanged()

    private void init() {
        // in case screen orientation changed, save all controls' state to
        // restore later
        CharSequence info = mTxtInfo != null ? mTxtInfo.getText() : null;
        CharSequence btnConfirmText = mBtnConfirm != null ? mBtnConfirm.getText() : null;
        Boolean btnConfirmEnabled = mBtnConfirm != null ? mBtnConfirm.isEnabled() : null;
        LockPatternView.DisplayMode lastDisplayMode = mLockPatternView != null ? mLockPatternView.getDisplayMode()
                : null;
        List<Cell> lastPattern = mLockPatternView != null ? mLockPatternView.getPattern() : null;

        setContentView(R.layout.alp_lock_pattern_activity);

        mTxtInfo = (TextView) findViewById(R.id.alp_lpa_text_info);
        mLockPatternView = (LockPatternView) findViewById(R.id.alp_lpa_lockPattern);

        mFooter = findViewById(R.id.alp_lpa_layout_footer);
        mBtnCancel = (Button) findViewById(R.id.alp_lpa_button_cancel);
        mBtnConfirm = (Button) findViewById(R.id.alp_lpa_button_confirm);

        // LOCK PATTERN VIEW

        // haptic feedback
        boolean hapticFeedbackEnabled = false;
        try {
            hapticFeedbackEnabled = Settings.System.getInt(getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0;
        } catch (Throwable t) {
            // ignore it
        }
        mLockPatternView.setTactileFeedbackEnabled(hapticFeedbackEnabled);

        mLockPatternView.setOnPatternListener(mPatternViewListener);
        if (lastPattern != null && lastDisplayMode != null)
            mLockPatternView.setPattern(lastDisplayMode, lastPattern);

        // COMMAND BUTTONS

        switch (mMode) {
        case CreatePattern:
            mBtnCancel.setOnClickListener(mBtnCancelOnClickListener);
            mBtnConfirm.setOnClickListener(mBtnConfirmOnClickListener);

            mFooter.setVisibility(View.VISIBLE);

            if (info != null)
                mTxtInfo.setText(info);
            else
                mTxtInfo.setText(R.string.alp_msg_draw_an_unlock_pattern);

            if (btnConfirmText != null) {
                mBtnConfirm.setText(btnConfirmText);
                mBtnConfirm.setEnabled(btnConfirmEnabled);
            }

            break;// CreatePattern

        case ComparePattern:
            mFooter.setVisibility(View.GONE);

            if (info != null)
                mTxtInfo.setText(info);
            else
                mTxtInfo.setText(R.string.alp_msg_draw_pattern_to_unlock);

            break;// ComparePattern
        }

        setResult(RESULT_CANCELED);
    }// init()

    /**
     * Encodes {@code pattern} to a string.<br>
     * 
     * <li>If {@link #_EncrypterClass} is not set, return SHA-1 of
     * {@code pattern}.</li>
     * 
     * <li>If {@link #_EncrypterClass} is set, calculate SHA-1 of
     * {@code pattern}, then encrypt the SHA-1 value and return the result.</li>
     * 
     * @param pattern
     * @return SHA-1 of {@code pattern}, or encrypted string of its.
     * @since v2 beta
     */
    private String encodePattern(List<Cell> pattern) {
        if (mEncrypter == null) {
            return LockPatternUtils.patternToSha1(pattern);
        } else {
            try {
                return mEncrypter.encrypt(this, LockPatternUtils.patternToSha1(pattern));
            } catch (Throwable t) {
                throw new InvalidEncrypterException();
            }
        }
    }// encodePattern()

    private int mRetryCount = 0;

    private void doComparePattern(List<Cell> pattern) {
        if (pattern == null)
            return;

        mLastPattern = new ArrayList<LockPatternView.Cell>();
        mLastPattern.addAll(pattern);

        String currentPattern = getIntent().getStringExtra(_Pattern);
        if (currentPattern == null)
            currentPattern = mPrefs.getString(_Pattern, null);

        if (encodePattern(pattern).equals(currentPattern)) {
            setResult(RESULT_OK);
            finish();
        } else {
            mRetryCount++;

            if (mRetryCount >= mMaxRetry) {
                setResult(RESULT_CANCELED);
                finish();
            } else {
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                mTxtInfo.setText(R.string.alp_msg_try_again);
            }
        }
    }// doComparePattern()

    private List<Cell> mLastPattern;

    private void doCreatePattern(List<Cell> pattern) {
        if (pattern.size() < 4) {
            mLockPatternView.setDisplayMode(DisplayMode.Wrong);
            mTxtInfo.setText(R.string.alp_msg_connect_4dots);
            return;
        }

        if (mLastPattern == null) {
            mLastPattern = new ArrayList<LockPatternView.Cell>();
            mLastPattern.addAll(pattern);
            mTxtInfo.setText(R.string.alp_msg_pattern_recorded);
            mBtnConfirm.setEnabled(true);
        } else {
            if (encodePattern(mLastPattern).equals(encodePattern(pattern))) {
                mTxtInfo.setText(R.string.alp_msg_your_new_unlock_pattern);
                mBtnConfirm.setEnabled(true);
            } else {
                mTxtInfo.setText(R.string.alp_msg_redraw_pattern_to_confirm);
                mBtnConfirm.setEnabled(false);
                mLockPatternView.setDisplayMode(DisplayMode.Wrong);
            }
        }
    }// doCreatePattern()

    private final LockPatternView.OnPatternListener mPatternViewListener = new LockPatternView.OnPatternListener() {

        @Override
        public void onPatternStart() {
            mLockPatternView.setDisplayMode(DisplayMode.Correct);

            if (mMode == LPMode.CreatePattern) {
                mTxtInfo.setText(R.string.alp_msg_release_finger_when_done);
                mBtnConfirm.setEnabled(false);
                if (getString(R.string.alp_cmd_continue).equals(mBtnConfirm.getText()))
                    mLastPattern = null;
            }
        }// onPatternStart()

        @Override
        public void onPatternDetected(List<Cell> pattern) {
            switch (mMode) {
            case CreatePattern:
                doCreatePattern(pattern);
                break;
            case ComparePattern:
                doComparePattern(pattern);
                break;
            }
        }// onPatternDetected()

        @Override
        public void onPatternCleared() {
            mLockPatternView.setDisplayMode(DisplayMode.Correct);

            switch (mMode) {
            case CreatePattern:
                mBtnConfirm.setEnabled(false);
                if (getString(R.string.alp_cmd_continue).equals(mBtnConfirm.getText())) {
                    mLastPattern = null;
                    mTxtInfo.setText(R.string.alp_msg_draw_an_unlock_pattern);
                } else
                    mTxtInfo.setText(R.string.alp_msg_redraw_pattern_to_confirm);
                break;
            case ComparePattern:
                mTxtInfo.setText(R.string.alp_msg_draw_pattern_to_unlock);
                break;
            }
        }// onPatternCleared()

        @Override
        public void onPatternCellAdded(List<Cell> pattern) {
            // TODO Auto-generated method stub
        }
    };// mPatternViewListener

    private final View.OnClickListener mBtnCancelOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            setResult(RESULT_CANCELED);
            finish();
        }
    };// mBtnCancelOnClickListener

    private final View.OnClickListener mBtnConfirmOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (getString(R.string.alp_cmd_continue).equals(mBtnConfirm.getText())) {
                mLockPatternView.clearPattern();
                mTxtInfo.setText(R.string.alp_msg_redraw_pattern_to_confirm);
                mBtnConfirm.setText(R.string.alp_cmd_confirm);
                mBtnConfirm.setEnabled(false);
            } else {
                if (mAutoSave)
                    mPrefs.edit().putString(_Pattern, encodePattern(mLastPattern)).commit();

                Intent i = new Intent();
                i.putExtra(_Pattern, encodePattern(mLastPattern));
                setResult(RESULT_OK, i);
                finish();
            }
        }
    };// mBtnConfirmOnClickListener
}
