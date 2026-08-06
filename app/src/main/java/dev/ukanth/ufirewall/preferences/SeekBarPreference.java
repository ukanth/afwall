package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import dev.ukanth.ufirewall.R;

/**
 * Custom SeekBar preference for selecting numeric values with a slider
 */
public class SeekBarPreference extends Preference implements SeekBar.OnSeekBarChangeListener {

    private static final int DEFAULT_MIN = 0;
    private static final int DEFAULT_MAX = 100;
    private static final int DEFAULT_VALUE = 0;

    private int mMax;
    private int mMin;
    private int mValue;
    private String mSuffix;
    private TextView mValueText;
    private SeekBar mSeekBar;

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SeekBarPreference(Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutResource(R.layout.preference_seekbar);
        
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);
            mMax = a.getInt(R.styleable.SeekBarPreference_android_max, DEFAULT_MAX);
            mMin = a.getInt(R.styleable.SeekBarPreference_min, DEFAULT_MIN);
            mSuffix = a.getString(R.styleable.SeekBarPreference_suffix);
            a.recycle();
        } else {
            mMax = DEFAULT_MAX;
            mMin = DEFAULT_MIN;
            mSuffix = "";
        }
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        return view;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        mSeekBar = view.findViewById(R.id.seekbar);
        mValueText = view.findViewById(R.id.seekbar_value);

        if (mSeekBar != null) {
            mSeekBar.setMax(mMax - mMin);
            mSeekBar.setProgress(mValue - mMin);
            mSeekBar.setOnSeekBarChangeListener(this);
        }

        updateValueText();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, DEFAULT_VALUE);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            try {
                mValue = getPersistedInt(DEFAULT_VALUE);
            } catch (ClassCastException e) {
                // Handle migration from EditTextPreference (String) to SeekBarPreference (Integer)
                String stringValue = getSharedPreferences().getString(getKey(), String.valueOf(DEFAULT_VALUE));
                try {
                    mValue = Integer.parseInt(stringValue);
                    // Remove old String value and migrate to integer storage
                    getSharedPreferences().edit().remove(getKey()).commit();
                    getSharedPreferences().edit().putInt(getKey(), mValue).commit();
                } catch (NumberFormatException nfe) {
                    mValue = DEFAULT_VALUE;
                    getSharedPreferences().edit().remove(getKey()).commit();
                    getSharedPreferences().edit().putInt(getKey(), mValue).commit();
                }
            }
        } else {
            mValue = (Integer) defaultValue;
            persistInt(mValue);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            int newValue = progress + mMin;
            if (callChangeListener(newValue)) {
                setValue(newValue);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Not needed
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        persistInt(mValue);
    }

    public void setValue(int value) {
        if (value < mMin) {
            value = mMin;
        }
        if (value > mMax) {
            value = mMax;
        }

        if (value != mValue) {
            mValue = value;
            persistInt(value);
            updateValueText();
            if (mSeekBar != null) {
                mSeekBar.setProgress(value - mMin);
            }
        }
    }

    public int getValue() {
        return mValue;
    }

    private void updateValueText() {
        if (mValueText != null) {
            String suffix = mSuffix != null ? " " + mSuffix : "";
            mValueText.setText(String.valueOf(mValue) + suffix);
        }
    }

    public void setMax(int max) {
        mMax = max;
        if (mSeekBar != null) {
            mSeekBar.setMax(max - mMin);
        }
    }

    public void setMin(int min) {
        mMin = min;
        if (mSeekBar != null) {
            mSeekBar.setMax(mMax - min);
        }
    }

    public void setSuffix(String suffix) {
        mSuffix = suffix;
        updateValueText();
    }
}
