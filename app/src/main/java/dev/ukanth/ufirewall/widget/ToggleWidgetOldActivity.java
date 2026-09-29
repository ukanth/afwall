package dev.ukanth.ufirewall.widget;

import static dev.ukanth.ufirewall.util.SecurityUtil.LOCK_VERIFICATION;
import static dev.ukanth.ufirewall.util.SecurityUtil.REQ_ENTER_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.RESULT_FAILED;
import static haibison.android.lockpattern.LockPatternActivity.RESULT_FORGOT_PATTERN;

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

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;

public class ToggleWidgetOldActivity extends Activity implements
        OnClickListener {

    private Button enableButton;
    private Button disableButton;
    private Button defaultButton;
    private Button profButton1;
    private Button profButton2;
    private Button profButton3;

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

        // this widget has room for the first three profiles only
        Button[] profButtons = {profButton1, profButton2, profButton3};
        List<ProfileData> listData = ProfileHelper.getProfiles();
        for (int i = 0; i < profButtons.length; i++) {
            if (i < listData.size()) {
                profButtons[i].setText(listData.get(i).getName());
                profButtons[i].setVisibility(View.VISIBLE);
            } else {
                profButtons[i].setVisibility(View.INVISIBLE);
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
        if(buttonId == R.id.toggle_enable_firewall) {
            startAction(1);
        } else if(buttonId == R.id.toggle_disable_firewall) {
            startAction(2);
        } else if(buttonId == R.id.toggle_default_profile) {
            startAction(3);
        } else if (buttonId == R.id.toggle_profile1 || buttonId == R.id.toggle_profile2
                || buttonId == R.id.toggle_profile3) {
            runProfile(profileName);
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
                    case RESULT_FAILED:
                    case RESULT_FORGOT_PATTERN:
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

        if (buttonId == R.id.toggle_disable_firewall && G.enableConfirm()) {
            confirmDisableFromWidget();
            return;
        }
        continueClickAfterConfirmation();
    }

    private void confirmDisableFromWidget() {
        new MaterialDialog.Builder(this)
                .title(R.string.confirmMsg)
                .cancelable(false)
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .onPositive((dialog, which) -> {
                    Log.i(Api.TAG, "Legacy widget firewall disable confirmed");
                    dialog.dismiss();
                    continueClickAfterConfirmation();
                })
                .onNegative((dialog, which) -> {
                    Log.i(Api.TAG, "Legacy widget firewall disable canceled");
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void continueClickAfterConfirmation() {
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
                if (data == null) {
                    return;
                }
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

    /**
     * @param identifier identifier of the active profile; its button is disabled
     */
    private void disableCustom(final String identifier) {
        ProfileData data = ProfileHelper.getProfileByIdentifier(identifier);
        final String name = data != null ? data.getName() : null;
        runOnUiThread(new Runnable() {
            public void run() {
                defaultButton.setEnabled(true);
                profButton1.setEnabled(!profButton1.getText().toString().equals(name));
                profButton2.setEnabled(!profButton2.getText().toString().equals(name));
                profButton3.setEnabled(!profButton3.getText().toString().equals(name));
            }
        });
    }

}