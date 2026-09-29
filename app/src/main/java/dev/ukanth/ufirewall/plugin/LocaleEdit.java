package dev.ukanth.ufirewall.plugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;

public class LocaleEdit extends AppCompatActivity {
    //public static final String LOCALE_BRIGHTNESS = "dev.ukanth.ufirewall.plugin.LocaleEdit.ACTIVE_PROFLE";

    private boolean mIsCancelled = false;

    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);

        BundleScrubber.scrub(getIntent());
        BundleScrubber.scrub(getIntent().getBundleExtra(
                com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));

        setContentView(R.layout.tasker_profile);

        Toolbar toolbar = findViewById(R.id.tasker_toolbar);

        setSupportActionBar(toolbar);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        RadioButton tasker_enable = findViewById(R.id.tasker_enable);
        RadioButton tasker_disable = findViewById(R.id.tasker_disable);
        RadioButton button1 = findViewById(R.id.defaultProfile);

        String name = prefs.getString("default", getString(R.string.defaultProfile));
        button1.setText(name != null && name.length() == 0 ? getString(R.string.defaultProfile) : name);


        RadioGroup profiles = findViewById(R.id.radioProfiles);
        // the layout's fixed Profile 1-3 buttons are from the old profile model; list the saved ones
        profiles.removeView(findViewById(R.id.profile1));
        profiles.removeView(findViewById(R.id.profile2));
        profiles.removeView(findViewById(R.id.profile3));

        for (ProfileData data : ProfileHelper.getProfiles()) {
            if (data != null) {
                RadioButton rdbtn = new RadioButton(this);
                rdbtn.setId(View.generateViewId());
                rdbtn.setText(data.getName());
                rdbtn.setTag(data.getIdentifier());
                profiles.addView(rdbtn);
            }
        }

        setupTitleApi11();

        if (null == paramBundle) {
            final Bundle forwardedBundle = getIntent().getBundleExtra(
                    com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
            if (PluginBundleManager.isBundleValid(forwardedBundle)) {
                // "<index>::<label>"; the index is only stable for the fixed entries, so profiles
                // are matched by identifier (saved since 4.2.0), else by label
                String profileId = forwardedBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_PROFILE_ID);
                String index = forwardedBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
                String label = null;
                if (index != null && index.contains("::")) {
                    String[] parts = index.split("::", 2);
                    index = parts[0];
                    label = parts[1];
                }
                if (index != null) {
                    switch (index) {
                        case "0":
                            tasker_enable.setChecked(true);
                            break;
                        case "1":
                            tasker_disable.setChecked(true);
                            break;
                        case "2":
                            button1.setChecked(true);
                            break;
                        default:
                            for (int i = 0; i < profiles.getChildCount(); i++) {
                                View child = profiles.getChildAt(i);
                                boolean match = profileId != null
                                        ? profileId.equals(child.getTag())
                                        : child instanceof RadioButton && ((RadioButton) child).getText().toString().equals(label);
                                if (child instanceof RadioButton && child != button1 && match) {
                                    ((RadioButton) child).setChecked(true);
                                    break;
                                }
                            }
                    }
                }
            }
        }
    }

    private void setupTitleApi11() {
        CharSequence callingApplicationLabel = null;
        try {
            callingApplicationLabel = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(getCallingPackage(),
                            0));
        } catch (final NameNotFoundException e) {
        }
        if (null != callingApplicationLabel) {
            setTitle(callingApplicationLabel);
        }
    }

    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if(id == android.R.id.home || id == R.id.twofortyfouram_locale_menu_save ) {
            finish();
            return true;
        } else if (id == R.id.twofortyfouram_locale_menu_dontsave) {
            mIsCancelled = true;
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.twofortyfouram_locale_help_save_dontsave, menu);
        return true;
    }

    @Override
    public void finish() {
        if (mIsCancelled) {
            setResult(RESULT_CANCELED);
        } else {
            RadioGroup group = findViewById(R.id.radioProfiles);
            int selectedId = group.getCheckedRadioButtonId();
            RadioButton radioButton = selectedId == View.NO_ID ? null : findViewById(selectedId);
            if (radioButton == null) {
                // nothing chosen: nothing to save
                setResult(RESULT_CANCELED);
            } else {
                String action = radioButton.getText().toString();
                final Intent resultIntent = new Intent();
                int idx = group.indexOfChild(radioButton);
                Object profileId = radioButton.getTag();
                resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, PluginBundleManager.generateBundle(
                        getApplicationContext(), idx + "::" + action, profileId instanceof String ? (String) profileId : null));
                resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, action);
                setResult(RESULT_OK, resultIntent);
            }
        }
        super.finish();
    }
}
