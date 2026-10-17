package dev.ukanth.ufirewall.plugin;

import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;

/**
 * The Tasker/Locale action editor. Saving signs the action ({@link ActionSigner}), after the
 * screen lock (or, without one, the AFWall+ password if set), so other apps can't create actions:
 * the editor is exported and any app could open it and keep the result.
 */
public class LocaleEdit extends AppCompatActivity {

    private static final int REQ_CONFIRM_CREDENTIAL = 7311;
    // how long Tasker waits for the result (it reports a failure after that)
    private static final int TIMEOUT_MS = 60_000;
    private static final int TIMEOUT_STATUS_MS = 5_000;

    private static final int[] ACTION_BUTTONS = {R.id.tasker_enable, R.id.tasker_disable, R.id.tasker_apply,
            R.id.tasker_profile, R.id.tasker_allow, R.id.tasker_block, R.id.tasker_status};
    private static final String[] ACTION_NAMES = {TaskerAction.ENABLE, TaskerAction.DISABLE, TaskerAction.APPLY,
            TaskerAction.PROFILE, TaskerAction.ALLOW, TaskerAction.BLOCK, TaskerAction.STATUS};

    private RadioGroup actions;
    private RadioGroup profiles;
    private AutoCompleteTextView packageField;
    private Spinner network;
    // "label — package" shown in the suggestions -> package
    private final Map<String, String> suggestions = new HashMap<>();

    private String caller;
    private SecurityUtil security;
    private boolean saving;
    // waiting for the device credential (Android 6-10)
    private TaskerAction pendingAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BundleScrubber.scrub(getIntent());
        BundleScrubber.scrub(getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));

        setContentView(R.layout.tasker_profile);
        Toolbar toolbar = findViewById(R.id.tasker_toolbar);
        setSupportActionBar(toolbar);

        caller = getCallingPackage();
        security = new SecurityUtil(this);
        actions = findViewById(R.id.tasker_actions);
        profiles = findViewById(R.id.radioProfiles);
        packageField = findViewById(R.id.tasker_package);
        network = findViewById(R.id.tasker_network);

        RadioButton defaultProfile = findViewById(R.id.defaultProfile);
        defaultProfile.setText(ProfileHelper.displayName(this, Api.DEFAULT_PREFS_NAME));
        defaultProfile.setTag(Api.DEFAULT_PREFS_NAME);
        for (ProfileData data : ProfileHelper.getProfiles()) {
            if (data != null) {
                RadioButton button = new RadioButton(this);
                button.setId(View.generateViewId());
                button.setText(data.getName());
                button.setTag(data.getIdentifier());
                profiles.addView(button);
            }
        }

        actions.setOnCheckedChangeListener((group, checkedId) -> showPanels());
        packageField.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = suggestions.get(String.valueOf(parent.getItemAtPosition(position)));
            if (pkg != null) {
                packageField.setText(pkg);
                packageField.setSelection(pkg.length());
            }
        });
        loadAppSuggestions();

        ((TextView) findViewById(R.id.tasker_security_hint)).setText(ActionSigner.isDeviceSecure(this)
                ? R.string.tasker_hint_locked : R.string.tasker_hint_no_lock);
        setupTitle();

        if (savedInstanceState == null) {
            restore(getIntent().getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));
        }
        if (actions.getCheckedRadioButtonId() == View.NO_ID) {
            actions.check(R.id.tasker_enable);
        }
        showPanels();

        // back saves, as in Locale's editors
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                save();
            }
        });
    }

    private void setupTitle() {
        CharSequence label = callerLabel();
        if (label != null) {
            setTitle(label);
        }
    }

    private CharSequence callerLabel() {
        if (caller == null) {
            return null;
        }
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(caller, 0));
        } catch (NameNotFoundException e) {
            return caller;
        }
    }

    private void showPanels() {
        String action = selectedAction();
        findViewById(R.id.tasker_profile_panel).setVisibility(TaskerAction.PROFILE.equals(action) ? View.VISIBLE : View.GONE);
        boolean app = TaskerAction.ALLOW.equals(action) || TaskerAction.BLOCK.equals(action);
        findViewById(R.id.tasker_app_panel).setVisibility(app ? View.VISIBLE : View.GONE);
        findViewById(R.id.tasker_status_vars).setVisibility(TaskerAction.STATUS.equals(action) ? View.VISIBLE : View.GONE);
    }

    private String selectedAction() {
        int checked = actions.getCheckedRadioButtonId();
        for (int i = 0; i < ACTION_BUTTONS.length; i++) {
            if (ACTION_BUTTONS[i] == checked) {
                return ACTION_NAMES[i];
            }
        }
        return null;
    }

    private void checkAction(String action) {
        int i = Arrays.asList(ACTION_NAMES).indexOf(action);
        if (i >= 0) {
            actions.check(ACTION_BUTTONS[i]);
        }
    }

    /**
     * Show the action being edited: a signed one, or one saved by an older version.
     */
    private void restore(Bundle bundle) {
        if (!PluginBundleManager.isBundleValid(bundle)) {
            return;
        }
        TaskerAction saved = PluginBundleManager.readSigned(bundle);
        if (saved != null) {
            checkAction(saved.action);
            if (saved.profileId != null) {
                checkProfile(saved.profileId, saved.profileName);
            }
            if (saved.pkg != null) {
                packageField.setText(saved.pkg);
            }
            int net = TaskerAction.NETWORKS.indexOf(saved.network);
            if (net >= 0) {
                network.setSelection(net);
            }
            return;
        }
        if (PluginBundleManager.isSigned(bundle)) {
            return; // malformed
        }
        // before 5.1.0: "<index>::<label>"; 0 enable, 1 disable, 2 default profile, else a profile
        // (by identifier since 4.2.0, else by label)
        String index = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
        String label = null;
        if (index.contains("::")) {
            String[] parts = index.split("::", 2);
            index = parts[0];
            label = parts[1];
        }
        switch (index) {
            case "0":
                checkAction(TaskerAction.ENABLE);
                break;
            case "1":
                checkAction(TaskerAction.DISABLE);
                break;
            case "2":
                checkAction(TaskerAction.PROFILE);
                checkProfile(Api.DEFAULT_PREFS_NAME, null);
                break;
            default:
                checkAction(TaskerAction.PROFILE);
                checkProfile(bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_PROFILE_ID), label);
        }
    }

    private void checkProfile(String identifier, String label) {
        for (int i = 0; i < profiles.getChildCount(); i++) {
            View child = profiles.getChildAt(i);
            if (!(child instanceof RadioButton)) {
                continue;
            }
            boolean match = identifier != null ? identifier.equals(child.getTag())
                    : ((RadioButton) child).getText().toString().equals(label);
            if (match) {
                ((RadioButton) child).setChecked(true);
                return;
            }
        }
    }

    private void loadAppSuggestions() {
        final PackageManager pm = getPackageManager();
        new Thread(() -> {
            List<String> entries = new ArrayList<>();
            Map<String, String> map = new HashMap<>();
            List<PackageInfo> installed;
            try {
                installed = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            } catch (RuntimeException e) {
                Log.w(G.TAG, "Unable to list apps for the Tasker editor: " + e);
                return;
            }
            for (PackageInfo info : installed) {
                if (info.requestedPermissions == null || info.applicationInfo == null
                        || !Arrays.asList(info.requestedPermissions).contains(android.Manifest.permission.INTERNET)) {
                    continue;
                }
                ApplicationInfo ai = info.applicationInfo;
                String entry = pm.getApplicationLabel(ai) + " — " + info.packageName;
                entries.add(entry);
                map.put(entry, info.packageName);
            }
            Collections.sort(entries, String.CASE_INSENSITIVE_ORDER);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                suggestions.putAll(map);
                packageField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, entries));
            });
        }).start();
    }

    // ---- saving ----

    /**
     * @return the action as set up on screen, or null (after saying why) if it can't be saved
     */
    private TaskerAction fromScreen() {
        String action = selectedAction();
        String pkg = null;
        String net = null;
        String profileId = null;
        String profileName = null;
        if (TaskerAction.PROFILE.equals(action)) {
            RadioButton button = findViewById(profiles.getCheckedRadioButtonId());
            if (button == null) {
                Api.toast(this, getString(R.string.tasker_choose_profile));
                return null;
            }
            profileId = String.valueOf(button.getTag());
            profileName = button.getText().toString();
        } else if (TaskerAction.ALLOW.equals(action) || TaskerAction.BLOCK.equals(action)) {
            pkg = packageField.getText().toString().trim();
            String mapped = suggestions.get(pkg);
            if (mapped != null) {
                pkg = mapped; // a suggestion left as typed
            }
            if (pkg.isEmpty() || (!TaskerAction.hasVariable(pkg) && !TaskerAction.isPackageName(pkg))) {
                packageField.setError(getString(R.string.tasker_error_package_field));
                return null;
            }
            int position = network.getSelectedItemPosition();
            net = TaskerAction.NETWORKS.get(position >= 0 && position < TaskerAction.NETWORKS.size() ? position : 0);
        }
        TaskerAction result = new TaskerAction(action, pkg, net, profileId, profileName,
                UUID.randomUUID().toString(), System.currentTimeMillis(), caller);
        String problem = result.check();
        if (problem != null) {
            Log.w(G.TAG, "Tasker action not saved: " + problem);
            return null;
        }
        return result;
    }

    private void save() {
        if (saving) {
            return;
        }
        TaskerAction action = fromScreen();
        if (action == null) {
            return;
        }
        saving = true;
        boolean deviceSecure = ActionSigner.isDeviceSecure(this);
        if (deviceSecure && G.enableDeviceCheck()) {
            authorize(action); // AFWall+'s own lock is the screen lock: ask once
            return;
        }
        // the AFWall+ password / pattern / fingerprint, if set
        security.passCheck(allowed -> {
            if (allowed) {
                authorize(action);
            } else {
                notSaved();
            }
        });
    }

    /**
     * Ask for the screen lock (if there is one), then sign.
     */
    private void authorize(TaskerAction action) {
        if (!ActionSigner.isDeviceSecure(this)) {
            sign(action, null);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            prompt(action);
        } else {
            confirmCredential(action);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void prompt(TaskerAction action) {
        Signature signature;
        try {
            signature = ActionSigner.signature(this);
        } catch (GeneralSecurityException e) {
            failed(e);
            return;
        }
        CharSequence from = callerLabel();
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.tasker_confirm_title))
                .setDescription(getString(R.string.tasker_confirm_text,
                        from != null ? from : getString(R.string.tasker_unknown_caller),
                        FireReceiver.describe(this, action, null)))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(new BiometricPrompt.CryptoObject(signature), new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        sign(action, result.getCryptoObject().getSignature());
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        notSaved();
                    }
                });
    }

    /**
     * Android 6-10: the key may be used for a few seconds after the credential is confirmed.
     */
    @SuppressWarnings("deprecation")
    private void confirmCredential(TaskerAction action) {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        CharSequence from = callerLabel();
        Intent intent = km == null ? null : km.createConfirmDeviceCredentialIntent(getString(R.string.tasker_confirm_title),
                getString(R.string.tasker_confirm_text, from != null ? from : getString(R.string.tasker_unknown_caller),
                        FireReceiver.describe(this, action, null)));
        if (intent == null) {
            sign(action, null); // no credential to confirm
            return;
        }
        pendingAction = action;
        startActivityForResult(intent, REQ_CONFIRM_CREDENTIAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CONFIRM_CREDENTIAL) {
            TaskerAction action = pendingAction;
            pendingAction = null;
            if (resultCode == RESULT_OK && action != null) {
                sign(action, null);
            } else {
                notSaved();
            }
            return;
        }
        security.handleActivityResult(requestCode, resultCode);
    }

    /**
     * @param signature initialized and, with a screen lock on Android 11+, unlocked by the prompt;
     *                  null to get one (no screen lock, or just confirmed on Android 6-10)
     */
    private void sign(TaskerAction action, Signature signature) {
        try {
            Signature s = signature != null ? signature : ActionSigner.signature(this);
            s.update(action.payload());
            String signed = ActionSigner.encode(s.sign());
            Log.i(G.TAG, "Tasker action " + action.id + " (" + action.action + ") saved for " + caller);
            finishWith(action, signed);
        } catch (GeneralSecurityException | RuntimeException e) {
            failed(e);
        }
    }

    private void finishWith(TaskerAction action, String signature) {
        Intent result = new Intent();
        result.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, PluginBundleManager.signedBundle(this, action, signature));
        result.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, FireReceiver.describe(this, action, null));
        boolean status = TaskerAction.STATUS.equals(action.action);
        result.putExtra(PluginBundleManager.TASKER_REQUESTED_TIMEOUT, status ? TIMEOUT_STATUS_MS : TIMEOUT_MS);
        if (status) {
            result.putExtra(PluginBundleManager.TASKER_RELEVANT_VARIABLES, new String[]{
                    "%afw_enabled\n" + getString(R.string.tasker_var_enabled),
                    "%afw_profile\n" + getString(R.string.tasker_var_profile),
                    "%afw_mode\n" + getString(R.string.tasker_var_mode)});
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private void failed(Exception e) {
        Log.e(G.TAG, "Unable to sign the Tasker action", e);
        Api.toast(this, getString(R.string.tasker_sign_failed, e.getClass().getSimpleName()));
        saving = false;
    }

    private void notSaved() {
        Api.toast(this, getString(R.string.tasker_not_saved));
        saving = false;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home || id == R.id.twofortyfouram_locale_menu_save) {
            save();
            return true;
        } else if (id == R.id.twofortyfouram_locale_menu_dontsave) {
            setResult(RESULT_CANCELED);
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
}
