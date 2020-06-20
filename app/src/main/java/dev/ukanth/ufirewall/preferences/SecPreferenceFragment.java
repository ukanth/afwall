package dev.ukanth.ufirewall.preferences;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.admin.AdminDeviceReceiver;
import dev.ukanth.ufirewall.util.FingerprintUtil;
import dev.ukanth.ufirewall.util.G;
import haibison.android.lockpattern.LockPatternActivity;
import haibison.android.lockpattern.utils.AlpSettings;

import static android.content.Context.FINGERPRINT_SERVICE;
import static android.content.Context.KEYGUARD_SERVICE;
import static haibison.android.lockpattern.LockPatternActivity.ACTION_COMPARE_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.ACTION_CREATE_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.EXTRA_PATTERN;

public class SecPreferenceFragment extends PreferenceFragment implements
        OnSharedPreferenceChangeListener {

    private SwitchPreference enableAdminPref;
    private CheckBoxPreference enableDeviceCheckPref;

    private static final int REQ_CREATE_PATTERN = 9877;
    private static final int REQ_ENTER_PATTERN = 9755;

    private static final int REQUEST_CODE_ENABLE_ADMIN = 10237; // identifies

    private static ComponentName deviceAdmin;
    private static DevicePolicyManager mDPM;

    private Context globalContext = null;

    //private String passOption = "p0";

    public void setupEnableAdmin(Preference pref) {
        if (pref == null) {
            return;
        }
        enableAdminPref = (SwitchPreference) pref;
        // query the actual device admin status from the system
        enableAdminPref.setChecked(mDPM.isAdminActive(deviceAdmin));
    }

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // update settings with actual device admin setting
        mDPM = (DevicePolicyManager) this.getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        deviceAdmin = new ComponentName(this.getActivity()
                .getApplicationContext(), AdminDeviceReceiver.class);
        super.onCreate(savedInstanceState);

        globalContext = this.getActivity();

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.security_preferences);


        //backward compatibility
        preSelectListForBackward();

        setupDeviceSecurityCheck(findPreference("enableDeviceCheck"));
        setupEnableAdmin(findPreference("enableAdmin"));

        //passOption = G.protectionLevel();

        // Hide Fingerprint option if device not support it.
        if (!FingerprintUtil.isAndroidSupport() || !canUserFingerPrint()) {
            ListPreference itemList = (ListPreference) findPreference("passSetting");
            itemList.setEntries(new String[]{
                    getString(R.string.pref_none),
                    getString(R.string.pref_password),
                    getString(R.string.pref_pattern),
            });
            itemList.setEntryValues(new String[]{
                    "p0", "p1", "p2"
            });
        }
    }

    private void setupDeviceSecurityCheck(Preference pref) {
        PreferenceCategory mCategory = (PreferenceCategory) findPreference("securitySetting");
        enableDeviceCheckPref = (CheckBoxPreference) pref;
        if (Build.VERSION.SDK_INT >= 21) {
            //only for donate version
            if ((G.isDoKey(getActivity()) || G.isDonate())) {
                if (globalContext != null) {
                    KeyguardManager keyguardManager = (KeyguardManager) globalContext.getSystemService(KEYGUARD_SERVICE);
                    //enable only when keyguard has set
                    if (keyguardManager.isKeyguardSecure()) {
                        enableDeviceCheckPref.setEnabled(true);
                    } else {
                        enableDeviceCheckPref.setEnabled(false);
                        enableDeviceCheckPref.setChecked(false);
                    }
                }
            } else {
                enableDeviceCheckPref.setEnabled(false);
                enableDeviceCheckPref.setChecked(false);
            }
        } else {
            //remove this option for older devices
            mCategory.removePreference(enableDeviceCheckPref);
        }
    }


    private void preSelectListForBackward() {

        final ListPreference itemList = (ListPreference) findPreference("passSetting");
        //remove other option
        if (Build.VERSION.SDK_INT < 21) {
            itemList.setEntries(itemList.getEntries());
            itemList.setEntryValues(itemList.getEntryValues());
        }
        if (itemList != null) {
            switch (G.protectionLevel()) {
                case "p0":
                    itemList.setValueIndex(0);
                    break;
                case "p1":
                    itemList.setValueIndex(1);
                    break;
                case "p2":
                    itemList.setValueIndex(2);
                    break;
                case "p3":
                    itemList.setValueIndex(3);
                    break;
                case "Disable":
                    itemList.setValueIndex(0);
                    break;
                default:
                    itemList.setValueIndex(0);
                    break;
            }
        }
    }


    /**
     * Set a new password lock
     *
     * @param pwd new password (empty to remove the lock)
     */
    private void setPassword(String pwd) {
        final Resources res = getResources();
        String msg = "";
        if (pwd.length() > 0) {
            String enc = Api.hideCrypt("AFW@LL_P@SSWORD_PR0T3CTI0N", pwd);
            if (enc != null) {
                G.profile_pwd(enc);
                G.isEnc(true);
                msg = res.getString(R.string.passdefined);
            }
        } /*else {
            G.profile_pwd(pwd);
			G.isEnc(false);
			msg = res.getString(R.string.passremoved);
		}*/
        Api.toast(getActivity(), msg, Toast.LENGTH_SHORT);
    }

    /**
     * Display Password dialog
     *
     * @param itemList
     */
    private void showPasswordActivity(final ListPreference itemList) {

        final MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
        //you should edit this to fit your needs
        builder.title(getString(R.string.pass_titleset));

        final EditText firstPass = new EditText(getActivity());
        firstPass.setHint(getString(R.string.enterpass));//optional
        final EditText secondPass = new EditText(getActivity());
        secondPass.setHint(getString(R.string.reenterpass));//optional

        //in my example i use TYPE_CLASS_NUMBER for input only numbers
        firstPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secondPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout lay = new LinearLayout(getActivity());
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.addView(firstPass);
        lay.addView(secondPass);
        builder.customView(lay, false);
        builder.autoDismiss(false);
        builder.positiveText(R.string.set_password);
        builder.negativeText(R.string.Cancel);


        builder.onPositive(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                //get the two inputs
                if (firstPass.getText().toString().equals(secondPass.getText().toString())) {
                    setPassword(firstPass.getText().toString());
                    G.enableDeviceCheck(false);
                    dialog.dismiss();
                } else {
                    Api.toast(getActivity(), getString(R.string.settings_pwd_not_equal));
                }
            }
        });

        builder.onNegative((dialog, which) -> {
            itemList.setValueIndex(0);
            dialog.dismiss();
        });
        builder.show();
    }

    private void showPatternActivity() {
        Intent intent = new Intent(ACTION_CREATE_PATTERN, null, getActivity(), LockPatternActivity.class);
        startActivityForResult(intent, REQ_CREATE_PATTERN);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {

        if (key.equals("passSetting")) {
            ListPreference itemList = (ListPreference) findPreference("passSetting");
            final ListPreference patternMaxTry = (ListPreference) findPreference("patternMax");
            final CheckBoxPreference stealthMode = (CheckBoxPreference) findPreference("stealthMode");
            stealthMode.setEnabled(false);
            patternMaxTry.setEnabled(false);
            switch (itemList.getValue()) {
                case "p0":
                    //disable password completly -- add reconfirmation based on current index
                    confirmResetPasswords(itemList);
                    break;
                case "p1":
                    //use the existing method to protect password
                    showPasswordActivity(itemList);
                    break;
                case "p2":
                    //use the existing method to protect password
                    showPatternActivity();
                    break;
                case "p3":
                    if (FingerprintUtil.isAndroidSupport()) {
                        checkFingerprintDeviceSupport();
                    }
                    break;
            }
            // check if device support fingerprint,
            // if so check if one fingerprint already existed at least
           /* if (FingerprintUtil.isAndroidSupport()) {
                checkFingerprintDeviceSupport();
            }*/
        }
        if (key.equals("enableAdmin")) {
            boolean value = G.enableAdmin();
            if (value) {
                Log.d("Device Admin Active ?", mDPM.isAdminActive(deviceAdmin) + "");
                if (!mDPM.isAdminActive(deviceAdmin)) {
                    // Launch the activity to have the user enable our admin.
                    Intent intent = new Intent(
                            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            deviceAdmin);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.device_admin_desc));
                    startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
                }
            } else {
                if (mDPM.isAdminActive(deviceAdmin)) {
                    mDPM.removeActiveAdmin(deviceAdmin);
                    Api.toast(this.getActivity().getApplicationContext(),
                            getString(R.string.device_admin_disabled), Toast.LENGTH_LONG);
                }
            }
        }
        if (key.equals("enableStealthPattern")) {
            AlpSettings.Display.setStealthMode(this.getActivity().getApplicationContext(),
                    G.enableStealthPattern());
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private boolean canUserFingerPrint() {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) globalContext.getSystemService(KEYGUARD_SERVICE);
            FingerprintManager fingerprintManager = (FingerprintManager) globalContext.getSystemService(FINGERPRINT_SERVICE);

            return fingerprintManager.isHardwareDetected() &&
                    ActivityCompat.checkSelfPermission(globalContext, Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                    fingerprintManager.hasEnrolledFingerprints() &&
                    keyguardManager.isKeyguardSecure();
        } catch (Exception e) {
            return false;
        }

    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkFingerprintDeviceSupport() {
        // Initializing both Android Keyguard Manager and Fingerprint Manager
        KeyguardManager keyguardManager = (KeyguardManager) globalContext.getSystemService(KEYGUARD_SERVICE);
        FingerprintManager fingerprintManager = (FingerprintManager) globalContext.getSystemService(FINGERPRINT_SERVICE);
        ListPreference itemList = (ListPreference) findPreference("passSetting");

        // Check whether the device has a Fingerprint sensor.
        if (!fingerprintManager.isHardwareDetected()) {
            Api.toast(globalContext, getString(R.string.device_with_no_fingerprint_sensor));
            itemList.setValueIndex(0);
        } else {
            // Checks whether fingerprint permission is set on manifest
            if (ActivityCompat.checkSelfPermission(globalContext, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                Api.toast(globalContext, getString(R.string.fingerprint_permission_manifest_missing));
                itemList.setValueIndex(0);
            } else {
                // Check whether at least one fingerprint is registered
                if (!fingerprintManager.hasEnrolledFingerprints()) {
                    Api.toast(globalContext, getString(R.string.register_at_least_one_fingerprint));
                    itemList.setValueIndex(0);
                } else {
                    // Checks whether lock screen security is enabled or not
                    if (!keyguardManager.isKeyguardSecure()) {
                        Api.toast(globalContext, getString(R.string.lock_screen_not_enabled));
                        itemList.setValueIndex(0);
                    } else {
                        // Anything is ok
                        if (!G.isFingerprintEnabled()) {
                            G.isFingerprintEnabled(true);
                            //make sure we set the index
                            itemList.setValueIndex(3);
                            Api.toast(globalContext, getString(R.string.fingerprint_enabled_successfully));
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * Make sure it's verified before remove passwords
     *
     * @param itemList
     */
    private void confirmResetPasswords(final ListPreference itemList) {
        String pattern = G.sPrefs.getString("LockPassword", "");
        String pwd = G.profile_pwd();
        if (pwd.length() > 0) {
            new MaterialDialog.Builder(getActivity()).cancelable(false)
                    .title(R.string.confirmation).autoDismiss(false)
                    .content(R.string.enterpass)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    .input(R.string.enterpass, R.string.password_empty, new MaterialDialog.InputCallback() {
                        @Override
                        public void onInput(MaterialDialog dialog, CharSequence input) {
                            String pass = input.toString();
                            boolean isAllowed = false;
                            if (G.isEnc()) {
                                String decrypt = Api.unhideCrypt("AFW@LL_P@SSWORD_PR0T3CTI0N", G.profile_pwd());
                                if (decrypt != null) {
                                    if (decrypt.equals(pass)) {
                                        isAllowed = true;
                                        //Api.toast(getActivity(), getString(R.string.wrong_password));
                                    }
                                }
                            } else {
                                if (pass.equals(G.profile_pwd())) {
                                    //reset password
                                    isAllowed = true;
                                    //Api.toast(getActivity(), getString(R.string.wrong_password));
                                }
                            }
                            if (isAllowed) {
                                G.profile_pwd("");
                                G.isEnc(false);
                                itemList.setValueIndex(0);
                                dialog.dismiss();
                            } else {
                                Api.toast(getActivity(), getString(R.string.wrong_password));
                            }
                        }
                    }).show();

        }

        if (pattern.length() > 0) {
            Intent intent = new Intent(ACTION_COMPARE_PATTERN, null, getActivity(), LockPatternActivity.class);
            String savedPattern = G.sPrefs.getString("LockPassword", "");
            intent.putExtra(EXTRA_PATTERN, savedPattern.toCharArray());
            startActivityForResult(intent, REQ_ENTER_PATTERN);
        }

        // check if fingerprint enabled and confirm disable by fingerprint itself
        if (G.isFingerprintEnabled()) {

            final FingerprintUtil.FingerprintDialog dialog;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                dialog = new FingerprintUtil.FingerprintDialog(globalContext);
                dialog.setOnFingerprintFailureListener(() -> {
                    itemList.setValueIndex(3);
                    dialog.dismiss();
                });
                dialog.setOnFingerprintSuccess(() -> {
                    G.isFingerprintEnabled(false);
                    Api.toast(globalContext, getString(R.string.fingerprint_disabled_successfully));
                });
                dialog.show();
            }

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        setupEnableAdmin(findPreference("enableAdmin"));
        switch (requestCode) {

            case REQ_CREATE_PATTERN: {
                ListPreference itemList = (ListPreference) findPreference("passSetting");
                if (resultCode == getActivity().RESULT_OK) {
                    char[] pattern = data.getCharArrayExtra(
                            EXTRA_PATTERN);
                    final SharedPreferences.Editor editor = G.sPrefs.edit();
                    editor.putString("LockPassword", new String(pattern));
                    editor.commit();
                    G.enableDeviceCheck(false);
                    //enable
                    if (itemList != null) {
                        final ListPreference patternMaxTry = (ListPreference) findPreference("patternMax");
                        final CheckBoxPreference stealthMode = (CheckBoxPreference) findPreference("stealthMode");
                        if (stealthMode != null) stealthMode.setEnabled(true);
                        if (patternMaxTry != null) patternMaxTry.setEnabled(true);
                    }

                } else {
                    itemList = (ListPreference) findPreference("passSetting");
                    if (itemList != null) {
                        itemList.setValueIndex(0);
                    }
                }
                break;
            }

            case REQ_ENTER_PATTERN: {
                ListPreference itemList = (ListPreference) findPreference("passSetting");
                if (resultCode == getActivity().RESULT_OK) {
                    final SharedPreferences.Editor editor = G.sPrefs.edit();
                    editor.putString("LockPassword", "");
                    editor.commit();
                    itemList = (ListPreference) findPreference("passSetting");
                    if (itemList != null) {
                        itemList.setValueIndex(0);
                    }
                } else {
                    if (itemList != null) {
                        itemList.setValueIndex(2);
                        G.enableDeviceCheck(false);
                    }
                }
            }
        }
    }
}
