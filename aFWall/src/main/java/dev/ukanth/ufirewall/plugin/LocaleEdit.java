package dev.ukanth.ufirewall.plugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.util.List;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.G;

public class LocaleEdit extends AppCompatActivity {
    //public static final String LOCALE_BRIGHTNESS = "dev.ukanth.ufirewall.plugin.LocaleEdit.ACTIVE_PROFLE";

    private boolean mIsCancelled = false;

    private int CUSTOM_PROFILE_ID = 100;

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


        if (!G.isProfileMigrated()) {

            RadioGroup profiles = findViewById(R.id.radioProfiles);

            RadioButton button2 = findViewById(R.id.profile1);
            RadioButton button3 = findViewById(R.id.profile2);
            RadioButton button4 = findViewById(R.id.profile3);

            List<String> profilesList = G.getAdditionalProfiles();
            //int textColor = Color.parseColor("#000000");

            int counter = CUSTOM_PROFILE_ID;
            for (String profile : profilesList) {
                RadioButton rdbtn = new RadioButton(this);
                rdbtn.setId(counter++);
                rdbtn.setText(profile);
                profiles.addView(rdbtn);
            }

            name = prefs.getString("profile1", getString(R.string.profile1));
            button2.setText(name != null && name.length() == 0 ? getString(R.string.profile1) : name);
            name = prefs.getString("profile2", getString(R.string.profile2));
            button3.setText(name != null && name.length() == 0 ? getString(R.string.profile2) : name);
            name = prefs.getString("profile3", getString(R.string.profile3));
            button4.setText(name != null && name.length() == 0 ? getString(R.string.profile3) : name);

            setupTitleApi11();

            if (null == paramBundle) {
                final Bundle forwardedBundle = getIntent().getBundleExtra(
                        com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
                if (PluginBundleManager.isBundleValid(forwardedBundle)) {
                    String index = forwardedBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
                    if (index.contains("::")) {
                        index = index.split("::")[0];
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
                            case "3":
                                button2.setChecked(true);
                                break;
                            case "4":
                                button3.setChecked(true);
                                break;
                            case "5":
                                button4.setChecked(true);
                                break;
                            default:
                                int diff = CUSTOM_PROFILE_ID + (Integer.parseInt(index) - 6);
                                RadioButton btn = findViewById(diff);
                                if (btn != null) {
                                    btn.setChecked(true);
                                }
                        }
                    }
                }
            }
        } else {
            //TODO: lets do it on new way
            RadioGroup profiles = findViewById(R.id.radioProfiles);
            //remove the existing profiles
            RadioButton button2 = findViewById(R.id.profile1);
            RadioButton button3 = findViewById(R.id.profile2);
            RadioButton button4 = findViewById(R.id.profile3);
            profiles.removeView(button2);
            profiles.removeView(button3);
            profiles.removeView(button4);

            for (ProfileData data : ProfileHelper.getProfiles()) {
                if (data != null) {
                    String profile = data.getName();
                    Long id = data.getId();
                    RadioButton rdbtn = new RadioButton(this);
                    rdbtn.setId(id.intValue());
                    rdbtn.setText(profile);
                    profiles.addView(rdbtn);
                }
            }

            if (null == paramBundle) {
                final Bundle forwardedBundle = getIntent().getBundleExtra(
                        com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
                if (PluginBundleManager.isBundleValid(forwardedBundle)) {
                    String index = forwardedBundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
                    if (index.contains("::")) {
                        index = index.split("::")[0];
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
                                int diff = Integer.parseInt(index);
                                RadioButton btn = findViewById(diff);
                                if (btn != null) {
                                    btn.setChecked(true);
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
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.twofortyfouram_locale_menu_dontsave:
                mIsCancelled = true;
                finish();
                return true;
            case R.id.twofortyfouram_locale_menu_save:
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
            RadioButton radioButton = findViewById(selectedId);
            //int id = Integer.parseInt(radioButton.getHint().toString());
            String action = radioButton.getText().toString();
            final Intent resultIntent = new Intent();
            if (!G.isProfileMigrated()) {
                int idx = group.indexOfChild(radioButton);
                resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, PluginBundleManager.generateBundle(getApplicationContext(), idx + "::" + action));
            } else {
                int idx = group.indexOfChild(radioButton);
                resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, PluginBundleManager.generateBundle(getApplicationContext(), idx + "::" + action));
            }
            resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, action);
            setResult(RESULT_OK, resultIntent);
        }
        super.finish();
    }
}
