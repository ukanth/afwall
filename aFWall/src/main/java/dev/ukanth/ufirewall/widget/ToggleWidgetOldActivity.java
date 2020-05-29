package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;

import static dev.ukanth.ufirewall.util.SecurityUtil.LOCK_VERIFICATION;
import static dev.ukanth.ufirewall.util.SecurityUtil.REQ_ENTER_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.RESULT_FAILED;
import static haibison.android.lockpattern.LockPatternActivity.RESULT_FORGOT_PATTERN;

public class ToggleWidgetOldActivity extends Activity implements
        OnClickListener {

    private static Button enableButton;
    private static Button disableButton;
    private static Button defaultButton;
    private static Button profButton1;
    private static Button profButton2;
    private static Button profButton3;

    private String profileName;
    private int buttonId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.toggle_widget_old_view);

        enableButton = this.findViewById(R.id.toggle_enable_firewall);
        disableButton = this
                .findViewById(R.id.toggle_disable_firewall);
        defaultButton = this.findViewById(R.id.toggle_default_profile);

        enableButton.setOnClickListener(this);
        disableButton.setOnClickListener(this);
        defaultButton.setOnClickListener(this);

        profButton1 = this.findViewById(R.id.toggle_profile1);
        profButton2 = this.findViewById(R.id.toggle_profile2);
        profButton3 = this.findViewById(R.id.toggle_profile3);

        if (Api.isEnabled(getApplicationContext())) {
            enableOthers();
        } else {
            disableOthers();
        }

        if (!G.isProfileMigrated()) {
            profButton1.setText(G.gPrefs.getString("profile1", getApplicationContext().getString(R.string.profile1)));
            profButton2.setText(G.gPrefs.getString("profile2", getApplicationContext().getString(R.string.profile2)));
            profButton3.setText(G.gPrefs.getString("profile3", getApplicationContext().getString(R.string.profile3)));
        } else {
            //hide by default
            profButton1.setVisibility(View.INVISIBLE);
            profButton2.setVisibility(View.INVISIBLE);
            profButton3.setVisibility(View.INVISIBLE);

            if (ProfileHelper.getProfileByIdentifier("AFWallProfile1") != null) {
                profButton1.setVisibility(View.VISIBLE);
            }
            if (ProfileHelper.getProfileByIdentifier("AFWallProfile2") != null) {
                profButton2.setVisibility(View.VISIBLE);
            }
            if (ProfileHelper.getProfileByIdentifier("AFWallProfile3") != null) {
                profButton3.setVisibility(View.VISIBLE);
            }
            List<ProfileData> listData = ProfileHelper.getProfiles();
            //worst case 10 !
            if (listData.size() <= 20) {
                switch (listData.size()) {
                    case 1:
                        profButton1.setText(listData.get(0).getName());
                        profButton1.setVisibility(View.VISIBLE);
                        break;
                    case 2:
                        profButton1.setText(listData.get(0).getName());
                        profButton1.setVisibility(View.VISIBLE);
                        profButton2.setText(listData.get(1).getName());
                        profButton2.setVisibility(View.VISIBLE);
                    case 3:
                        profButton1.setText(listData.get(0).getName());
                        profButton1.setVisibility(View.VISIBLE);
                        profButton2.setText(listData.get(1).getName());
                        profButton2.setVisibility(View.VISIBLE);
                        profButton3.setText(listData.get(2).getName());
                        profButton3.setVisibility(View.VISIBLE);
                    default:
                        //enable first 3
                        profButton1.setText(listData.get(0).getName());
                        profButton1.setVisibility(View.VISIBLE);
                        profButton2.setText(listData.get(1).getName());
                        profButton2.setVisibility(View.VISIBLE);
                        profButton3.setText(listData.get(2).getName());
                        profButton3.setVisibility(View.VISIBLE);

                }
            }
        }

        profButton1.setOnClickListener(this);
        profButton2.setOnClickListener(this);
        profButton3.setOnClickListener(this);

        if (!G.enableMultiProfile()) {
            profButton1.setEnabled(false);
            profButton2.setEnabled(false);
            profButton3.setEnabled(false);
        } else {
            if (Api.isEnabled(getApplicationContext())) {
                String profileName = G.storedProfile();
                if (profileName.equals(Api.DEFAULT_PREFS_NAME)) {
                    disableDefault();
                } else {
                    disableCustom(profileName);
                }
            }
        }
    }

    private void switchAction() {
        switch (buttonId) {
            case R.id.toggle_enable_firewall:
                startAction(1);
                break;
            case R.id.toggle_disable_firewall:
                startAction(2);
                break;
            case R.id.toggle_default_profile:
                startAction(3);
                break;
            case R.id.toggle_profile1:
                if (!G.isProfileMigrated()) {
                    startAction(4);
                } else {
                    runProfile(profileName);
                }
                break;
            case R.id.toggle_profile2:
                if (!G.isProfileMigrated()) {
                    startAction(5);
                } else {
                    runProfile(profileName);
                }
                break;
            case R.id.toggle_profile3:
                if (!G.isProfileMigrated()) {
                    startAction(6);
                } else {
                    runProfile(profileName);
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case LOCK_VERIFICATION: {
                switch (resultCode) {
                    case RESULT_OK:
                        switchAction();
                        break;
                    default:
                        ToggleWidgetOldActivity.this.finish();
                        android.os.Process.killProcess(android.os.Process.myPid());
                        break;
                }
            }
            break;
            case REQ_ENTER_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        switchAction();
                        break;
                    case RESULT_CANCELED:
                        ToggleWidgetOldActivity.this.finish();
                        break;
                    case RESULT_FAILED:
                        ToggleWidgetOldActivity.this.finish();
                        break;
                    case RESULT_FORGOT_PATTERN:
                        ToggleWidgetOldActivity.this.finish();
                        break;
                    default:
                        ToggleWidgetOldActivity.this.finish();
                        break;
                }
            }
            break;
        }
    }

    @Override
    public void onClick(View button) {
        profileName = ((Button) button).getText().toString();
        buttonId = button.getId();

        SecurityUtil util = new SecurityUtil(ToggleWidgetOldActivity.this);
        boolean passCheck = util.isPasswordProtected();
        if (!passCheck) {
            switchAction();
        } else {
            util.passCheck();
        }
    }

    private void runProfile(final String profileName) {
        final Handler toaster = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.arg1 != 0)
                    Toast.makeText(getApplicationContext(), msg.arg1, Toast.LENGTH_SHORT).show();
            }
        };

        final Context context = getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                ProfileData data = ProfileHelper.getProfileByName(profileName);
                G.setProfile(true, data.getIdentifier());
                Api.applySavedIptablesRules(context, false, new RootCommand()
                        .setCallback(new RootCommand.Callback() {
                            @Override
                            public void cbFunc(RootCommand state) {
                                Message msg = new Message();
                                if (state.exitCode == 0) {
                                    msg.arg1 = R.string.rules_applied;
                                    toaster.sendMessage(msg);
                                    enableOthers();
                                } else {
                                    // error details are already in logcat
                                    msg.arg1 = R.string.error_apply;
                                    toaster.sendMessage(msg);
                                }
                            }
                        }));
                //Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
                Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
            }
        }.start();
        defaultButton.setEnabled(true);
        if (profButton1.getText().equals(profileName)) {
            profButton1.setEnabled(false);
            profButton2.setEnabled(true);
            profButton3.setEnabled(true);
        } else if (profButton2.getText().equals(profileName)) {
            profButton1.setEnabled(true);
            profButton2.setEnabled(false);
            profButton3.setEnabled(true);
        } else if (profButton3.getText().equals(profileName)) {
            profButton1.setEnabled(true);
            profButton2.setEnabled(true);
            profButton3.setEnabled(false);
        }
    }

    private void startAction(final int i) {

        final Handler toaster = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.arg1 != 0)
                    Toast.makeText(getApplicationContext(), msg.arg1,
                            Toast.LENGTH_SHORT).show();
            }
        };
        final Context context = getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                switch (i) {
                    case 1:
                        Api.applySavedIptablesRules(context, false, new RootCommand()
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        Message msg = new Message();
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.rules_applied;
                                            toaster.sendMessage(msg);
                                            enableOthers();
                                            Api.setEnabled(context, true, false);
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.error_apply;
                                            toaster.sendMessage(msg);
                                        }
                                    }
                                }));
                        break;
                    case 2:
                        // validation, check for password
                        Api.purgeIptables(context, true, new RootCommand()
                                .setSuccessToast(R.string.toast_disabled)
                                .setFailureToast(R.string.toast_error_disabling)
                                .setReopenShell(true)
                                .setCallback(new RootCommand.Callback() {
                                    public void cbFunc(RootCommand state) {
                                        final Message msg = new Message();
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.toast_disabled;
                                            Api.setEnabled(context, false, false);
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.toast_error_disabling;
                                        }
                                        toaster.sendMessage(msg);
                                    }
                                }));
                        break;
                    case 3:
                        G.setProfile(G.enableMultiProfile(), "AFWallPrefs");
                        Api.applySavedIptablesRules(context, false, new RootCommand()
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        Message msg = new Message();
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.rules_applied;
                                            toaster.sendMessage(msg);
                                            enableOthers();
                                            disableDefault();
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.error_apply;
                                            toaster.sendMessage(msg);
                                        }
                                    }
                                }));
                       /* if (applyProfileRules(context, msg, toaster)) {
                            disableDefault();
                        }*/
                        break;
                    case 4:
                        G.setProfile(true, "AFWallProfile1");
                        Api.applySavedIptablesRules(context, false, new RootCommand()
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        Message msg = new Message();
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.rules_applied;
                                            toaster.sendMessage(msg);
                                            enableOthers();
                                            disableCustom("AFWallProfile1");
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.error_apply;
                                            toaster.sendMessage(msg);
                                        }
                                    }
                                }));
                        /*if (applyProfileRules(context, msg, toaster)) {
                            disableCustom("AFWallProfile1");
                        }*/
                        break;
                    case 5:
                        G.setProfile(true, "AFWallProfile2");
                        Api.applySavedIptablesRules(context, false, new RootCommand()
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        Message msg = new Message();
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.rules_applied;
                                            toaster.sendMessage(msg);
                                            enableOthers();
                                            disableCustom("AFWallProfile2");
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.error_apply;
                                            toaster.sendMessage(msg);
                                        }
                                    }
                                }));
                        /*if (applyProfileRules(context, msg, toaster)) {
                            disableCustom("AFWallProfile2");
                        }*/
                        break;
                    case 6:
                        G.setProfile(true, "AFWallProfile3");
                        Api.applySavedIptablesRules(context, false, new RootCommand()
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        Message msg = new Message();
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.rules_applied;
                                            toaster.sendMessage(msg);
                                            enableOthers();
                                            disableCustom("AFWallProfile3");
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.error_apply;
                                            toaster.sendMessage(msg);
                                        }
                                    }
                                }));
                       /* if (applyProfileRules(context, msg, toaster)) {
                            disableCustom("AFWallProfile3");
                        }*/
                        break;
                }
                //Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
                Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
            }
        }.start();
    }

    private void enableOthers() {
        runOnUiThread(new Runnable() {
            public void run() {
                enableButton.setEnabled(false);
                disableButton.setEnabled(true);
                defaultButton.setEnabled(true);
                if (G.enableMultiProfile()) {
                    profButton1.setEnabled(true);
                    profButton2.setEnabled(true);
                    profButton3.setEnabled(true);
                }
            }
        });

    }

    private void disableOthers() {
        runOnUiThread(new Runnable() {
            public void run() {
                enableButton.setEnabled(true);
                disableButton.setEnabled(false);
                defaultButton.setEnabled(false);
                profButton1.setEnabled(false);
                profButton2.setEnabled(false);
                profButton3.setEnabled(false);
            }
        });
    }

    private void disableDefault() {
        runOnUiThread(new Runnable() {
            public void run() {
                defaultButton.setEnabled(false);
                if (G.enableMultiProfile()) {
                    profButton1.setEnabled(true);
                    profButton2.setEnabled(true);
                    profButton3.setEnabled(true);
                }
            }
        });
    }

    private void disableCustom(final String code) {
        runOnUiThread(new Runnable() {
            public void run() {
                switch (code) {
                    case "AFWallProfile1":
                        defaultButton.setEnabled(true);
                        profButton1.setEnabled(false);
                        profButton2.setEnabled(true);
                        profButton3.setEnabled(true);
                        break;
                    case "AFWallProfile2":
                        defaultButton.setEnabled(true);
                        profButton1.setEnabled(true);
                        profButton2.setEnabled(false);
                        profButton3.setEnabled(true);
                        break;
                    case "AFWallProfile3":
                        defaultButton.setEnabled(true);
                        profButton1.setEnabled(true);
                        profButton2.setEnabled(true);
                        profButton3.setEnabled(false);
                }
            }
        });
    }

}