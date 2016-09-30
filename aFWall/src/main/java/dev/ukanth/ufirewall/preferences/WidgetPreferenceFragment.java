package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.EditText;

import dev.ukanth.ufirewall.R;

public class WidgetPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.widget_preferences);

            EditTextPreference editX = (EditTextPreference) findPreference("widgetX");
            EditTextPreference editY = (EditTextPreference) findPreference("widgetY");

            EditText prefEditTextX = editX.getEditText();
            prefEditTextX.setInputType(InputType.TYPE_CLASS_TEXT);

            EditText prefEditTextY = editY.getEditText();
            prefEditTextY.setInputType(InputType.TYPE_CLASS_TEXT);

            if (editX != null && (editX.getText() == null || editX.getText().equals("")) && editY != null && (editY.getText() == null || editY.getText().equals(""))) {
                DisplayMetrics dm = new DisplayMetrics();
                Context hostActivity = getActivity();
                if (hostActivity != null) {
                    WindowManager wm = (WindowManager) hostActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.getDefaultDisplay().getMetrics(dm);
                    editX.setText(dm.widthPixels + "");
                    editY.setText(dm.heightPixels + "");
                }
            }
        } catch(ClassCastException e) {
            
        }
    }
}
