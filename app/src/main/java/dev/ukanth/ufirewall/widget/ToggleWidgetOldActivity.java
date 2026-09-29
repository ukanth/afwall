package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import java.util.List;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.FirewallActions;
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
    private SecurityUtil security;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.toggle_widget_old_view);
        security = new SecurityUtil(this);

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
        final Context context = getApplicationContext();
        if (buttonId == R.id.toggle_enable_firewall) {
            FirewallActions.setEnabled(context, true, true, ok -> {
                if (ok) {
                    enableOthers();
                }
                Api.updateNotification(Api.isEnabled(context), context);
            });
        } else if (buttonId == R.id.toggle_disable_firewall) {
            FirewallActions.setEnabled(context, false, true, ok -> {
                if (ok) {
                    disableOthers();
                }
                Api.updateNotification(Api.isEnabled(context), context);
            });
        } else if (buttonId == R.id.toggle_default_profile) {
            switchProfile(Api.DEFAULT_PREFS_NAME);
        } else if (buttonId == R.id.toggle_profile1 || buttonId == R.id.toggle_profile2
                || buttonId == R.id.toggle_profile3) {
            ProfileData data = ProfileHelper.getProfileByName(profileName);
            if (data != null) {
                switchProfile(data.getIdentifier());
            }
        }
    }

    private void switchProfile(final String identifier) {
        final Context context = getApplicationContext();
        // applies the rules only while the firewall is enabled
        FirewallActions.switchProfile(context, identifier, true, ok -> {
            if (ok) {
                if (Api.DEFAULT_PREFS_NAME.equals(identifier)) {
                    disableDefault();
                } else {
                    disableCustom(identifier);
                }
            }
            Api.updateNotification(Api.isEnabled(context), context);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // device lock / pattern results of the app lock check
        security.handleActivityResult(requestCode, resultCode);
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
        security.passCheck(allowed -> {
            if (allowed) {
                switchAction();
            } else {
                finish();
            }
        });
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