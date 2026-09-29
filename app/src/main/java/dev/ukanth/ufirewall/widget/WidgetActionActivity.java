package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.FirewallActions;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Notifications;
import dev.ukanth.ufirewall.util.SecurityUtil;

/**
 * Firewall actions started outside the app (status widget, quick settings tile, notification
 * actions), after the same checks as in the app: the "confirm before disabling" setting and the
 * app lock. Shows nothing unless there is something to ask.
 * <ul>
 * <li>toggle: enable / disable the firewall</li>
 * <li>profile: pick a profile to switch to</li>
 * <li>app: allow or block a (newly installed) app</li>
 * </ul>
 * Not exported: only our own PendingIntents can start it (the toggle used to be an exported
 * broadcast that any app could send).
 */
public class WidgetActionActivity extends Activity {

    public static final String EXTRA_ACTION = "dev.ukanth.ufirewall.widget.ACTION";
    public static final String ACTION_TOGGLE = "toggle";
    public static final String ACTION_PROFILE = "profile";
    public static final String ACTION_APP = "app";
    private static final String EXTRA_UID = "uid";
    private static final String EXTRA_PACKAGE = "package";
    private static final String EXTRA_LABEL = "label";
    private static final String EXTRA_ALLOW = "allow";

    private SecurityUtil security;

    public static Intent toggleIntent(Context ctx) {
        return base(ctx, ACTION_TOGGLE);
    }

    public static Intent profileIntent(Context ctx) {
        return base(ctx, ACTION_PROFILE);
    }

    public static Intent appRuleIntent(Context ctx, int uid, String pkg, String label, boolean allow) {
        return base(ctx, ACTION_APP)
                .putExtra(EXTRA_UID, uid)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_ALLOW, allow);
    }

    private static Intent base(Context ctx, String action) {
        return new Intent(ctx, WidgetActionActivity.class)
                .putExtra(EXTRA_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // recreated (e.g. rotated) while asking: the prompt is gone, let the user tap again
            finish();
            return;
        }
        security = new SecurityUtil(this);
        String action = getIntent().getStringExtra(EXTRA_ACTION);
        if (ACTION_PROFILE.equals(action)) {
            pickProfile();
        } else if (ACTION_APP.equals(action)) {
            final Intent in = getIntent();
            checkLockThenRun(() -> setAppAccess(in.getIntExtra(EXTRA_UID, -1), in.getStringExtra(EXTRA_PACKAGE),
                    in.getStringExtra(EXTRA_LABEL), in.getBooleanExtra(EXTRA_ALLOW, false)));
        } else {
            toggle();
        }
    }

    // ---- enable / disable ----

    private void toggle() {
        final boolean enable = !Api.isEnabled(this);
        if (!enable && G.enableConfirm()) {
            new MaterialDialog.Builder(this)
                    .title(R.string.confirmMsg)
                    .cancelable(false)
                    .positiveText(R.string.Yes)
                    .negativeText(R.string.No)
                    .onPositive((dialog, which) -> {
                        dialog.dismiss();
                        checkLockThenRun(() -> setEnabled(false));
                    })
                    .onNegative((dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .show();
        } else {
            checkLockThenRun(() -> setEnabled(enable));
        }
    }

    private void setEnabled(boolean enable) {
        final Context app = getApplicationContext();
        StatusWidget.showPending(app, enable);
        FirewallActions.setEnabled(app, enable, true, ok -> StatusWidget.showResult(app, ok));
    }

    // ---- profile switch ----

    private void pickProfile() {
        final List<String> names = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        names.add(ProfileHelper.displayName(this, Api.DEFAULT_PREFS_NAME));
        ids.add(Api.DEFAULT_PREFS_NAME);
        for (ProfileData data : ProfileHelper.getProfiles()) {
            names.add(data.getName());
            ids.add(data.getIdentifier());
        }
        // mark the active profile
        int current = ids.indexOf(G.storedProfile());
        if (current >= 0) {
            names.set(current, names.get(current) + " ✓");
        }
        new MaterialDialog.Builder(this)
                .title(R.string.notif_action_profile)
                .items(names)
                .itemsCallback((dialog, view, which, text) -> {
                    final String identifier = ids.get(which);
                    checkLockThenRun(() -> {
                        FirewallActions.switchProfile(getApplicationContext(), identifier, true, null);
                        Notifications.refreshStatus(getApplicationContext());
                    });
                })
                .negativeText(R.string.Cancel)
                .onNegative((dialog, which) -> finish())
                .cancelListener(dialog -> finish())
                .show();
    }

    // ---- allow / block an app ----

    private void setAppAccess(int uid, String pkg, String label, boolean allow) {
        if (uid < 0) {
            return;
        }
        Context app = getApplicationContext();
        FirewallActions.setAppAccess(app, uid, allow);
        Notifications.cancelNewApp(app, uid, pkg);
        Api.toast(app, getString(allow ? R.string.notif_app_allowed : R.string.notif_app_blocked,
                label != null ? label : pkg));
    }

    private void checkLockThenRun(Runnable action) {
        security.passCheck(allowed -> {
            if (allowed) {
                action.run();
            }
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (security != null) {
            security.handleActivityResult(requestCode, resultCode);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
