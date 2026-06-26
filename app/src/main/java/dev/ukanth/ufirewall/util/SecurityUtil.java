package dev.ukanth.ufirewall.util;

import static dev.ukanth.ufirewall.util.G.isDonate;
import static haibison.android.lockpattern.LockPatternActivity.ACTION_COMPARE_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.EXTRA_PATTERN;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.InputType;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import haibison.android.lockpattern.LockPatternActivity;

/**
 * Created by ukanth on 17/3/18.
 */

public class SecurityUtil {

    private final Context context;

    public static final int REQ_ENTER_PATTERN = 9755;
    public static final int LOCK_VERIFICATION = 1212;

    private final Activity activity;
    private final Runnable successCallback;

    public SecurityUtil(Activity activity) {
        this(activity, null);
    }

    public SecurityUtil(Activity activity, Runnable successCallback) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        this.successCallback = successCallback;
    }

    private void deviceCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
                    notifyAuthSuccess();
                }
            } else {
                Api.donateDialog(activity, true);
                notifyAuthSuccess();
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
                        notifyAuthSuccess();
                        return true;
                    } else {
                        // Check the password
                        requestPassword();
                        return true;
                    }
                case "p2":
                    final String pwd = G.sPrefs.getString("LockPassword", "");
                    if (pwd.length() == 0) {
                        notifyAuthSuccess();
                        return true;
                    } else {
                        requestPassword();
                        return true;
                    }
                case "p3":
                    /* TODO: Testing is required before enabling this.
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                        if (BiometricUtil.isAndroidSupport() && G.isFingerprintEnabled()) {
                            requestFingerprintQ();
                            return true;
                        }
                    } else {*/
                        if (FingerprintUtil.isAndroidSupport() && G.isFingerprintEnabled()) {
                            requestFingerprint();
                            return true;
                        }
                    //}
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
            gracefulShutdown();
        });
        dialog.setOnFingerprintSuccess(this::notifyAuthSuccess);
        dialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void requestFingerprintQ() {
        BiometricUtil.FingerprintDialog dialog = new BiometricUtil.FingerprintDialog(activity);
        dialog.setOnFingerprintFailureListener(() -> {
            gracefulShutdown();
        });
        dialog.setOnFingerprintSuccess(this::notifyAuthSuccess);
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
                            gracefulShutdown();
                        })
                        .input(R.string.enterpass, R.string.password_empty, (dialog, input) -> {
                            String pass = InputValidator.sanitizeString(input.toString(), 256);
                            if (pass == null) {
                                Api.toast(activity, context.getString(R.string.wrong_password));
                                return;
                            }
                            
                            // Use secure password manager for verification and auto-migration
                            boolean isAllowed = SecurePasswordManager.verifyPassword(context, pass);
                            
                            if (isAllowed) {
                                dialog.dismiss();
                                notifyAuthSuccess();
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

    /**
     * Perform graceful shutdown instead of abrupt process termination
     * This ensures proper cleanup and prevents firewall rules from being left in inconsistent state
     */
    private void gracefulShutdown() {
        try {
            // Give some time for any pending operations to complete
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    // Attempt to save any pending state or cleanup
                    activity.moveTaskToBack(true);
                    activity.finishAndRemoveTask();
                } catch (Exception e) {
                    // If graceful methods fail, fall back to finish()
                    activity.finish();
                } finally {
                    // Only use process kill as absolute last resort after cleanup attempt
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }, 500); // 500ms delay to allow cleanup
                }
            });
        } catch (Exception e) {
            Log.e("SecurityUtil", "Error during graceful shutdown", e);
            // Emergency fallback
            activity.finish();
        }
    }

    private void notifyAuthSuccess() {
        if (successCallback != null) {
            activity.runOnUiThread(successCallback);
        }
    }
}
