package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.InputType;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import haibison.android.lockpattern.LockPatternActivity;

import static dev.ukanth.ufirewall.util.G.isDonate;
import static haibison.android.lockpattern.LockPatternActivity.ACTION_COMPARE_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.EXTRA_PATTERN;

/**
 * Created by ukanth on 17/3/18.
 */

public class SecurityUtil {

    private Context context;

    public static final int REQ_ENTER_PATTERN = 9755;
    public static final int LOCK_VERIFICATION = 1212;

    private Activity activity;

    public SecurityUtil(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    private void deviceCheck() {
        if (Build.VERSION.SDK_INT >= 21) {
            if ((G.isDoKey(context) || isDonate())) {
                KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (keyguardManager.isKeyguardSecure()) {
                    Intent createConfirmDeviceCredentialIntent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
                    if (createConfirmDeviceCredentialIntent != null) {
                        try {
                            activity.startActivityForResult(createConfirmDeviceCredentialIntent, LOCK_VERIFICATION);
                        } catch (ActivityNotFoundException e) {
                        }
                    }
                } else {
                    Toast.makeText(activity, context.getText(R.string.android_version), Toast.LENGTH_SHORT).show();
                }
            } else {
                Api.donateDialog(activity, true);
            }
        }
    }

    public boolean passCheck() {
        if (G.enableDeviceCheck()) {
            deviceCheck();
            return true;
        } else {
            switch (G.protectionLevel()) {
                case "p0":
                    return true;
                case "p1":
                    final String oldpwd = G.profile_pwd();
                    if (oldpwd.length() == 0) {
                        return true;
                    } else {
                        // Check the password
                        requestPassword();
                        return true;
                    }
                case "p2":
                    final String pwd = G.sPrefs.getString("LockPassword", "");
                    if (pwd.length() == 0) {
                        return true;
                    } else {
                        requestPassword();
                        return true;
                    }
                case "p3":
                    if (FingerprintUtil.isAndroidSupport() && G.isFingerprintEnabled()) {
                        requestFingerprint();
                        return true;
                    }
                    break;
            }
        }
        return false;
    }


    public boolean isPasswordProtected() {
        return (G.enableDeviceCheck() || (G.protectionLevel().equals("p1") && G.profile_pwd().length() > 0)
                || G.sPrefs.getString("LockPassword", "").length() > 0 || (FingerprintUtil.isAndroidSupport() && G.isFingerprintEnabled()));

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestFingerprint() {
        FingerprintUtil.FingerprintDialog dialog = new FingerprintUtil.FingerprintDialog(activity);
        dialog.setOnFingerprintFailureListener(() -> {
            activity.finish();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        dialog.show();
    }

    private void requestPassword() {
        switch (G.protectionLevel()) {
            case "p1":

                MaterialDialog.Builder builder = new MaterialDialog.Builder(activity).cancelable(false)
                        .title(R.string.pass_titleget).autoDismiss(false)
                        .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        .positiveText(R.string.submit)
                        .negativeText(R.string.Cancel)
                        .onNegative((dialog, which) -> {
                            activity.finish();
                            android.os.Process.killProcess(android.os.Process.myPid());
                        })
                        .input(R.string.enterpass, R.string.password_empty, (dialog, input) -> {
                            String pass = input.toString();
                            boolean isAllowed = false;
                            if (G.isEnc()) {
                                String decrypt = Api.unhideCrypt("AFW@LL_P@SSWORD_PR0T3CTI0N", G.profile_pwd());
                                if (decrypt != null) {
                                    if (decrypt.equals(pass)) {
                                        isAllowed = true;
                                    }
                                }
                            } else {
                                if (pass.equals(G.profile_pwd())) {
                                    isAllowed = true;
                                }
                            }
                            if (isAllowed) {
                                dialog.dismiss();
                            } else {
                                Api.toast(activity, context.getString(R.string.wrong_password));
                            }
                        });

                builder.show();
                break;
            case "p2":
                Intent intent = new Intent(ACTION_COMPARE_PATTERN, null, context, LockPatternActivity.class);
                String savedPattern = G.sPrefs.getString("LockPassword", "");
                intent.putExtra(EXTRA_PATTERN, savedPattern.toCharArray());
                activity.startActivityForResult(intent, REQ_ENTER_PATTERN);
                break;
        }

    }
}
