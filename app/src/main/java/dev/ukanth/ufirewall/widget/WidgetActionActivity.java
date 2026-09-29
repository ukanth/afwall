package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.FirewallActions;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;

/**
 * Enables / disables the firewall for the status widget and the quick settings tile, after the
 * same checks as in the app: the "confirm before disabling" setting and the app lock. Shows
 * nothing unless there is something to ask.
 * <p>
 * Not exported: only our own PendingIntents can start it (the toggle used to be an exported
 * broadcast that any app could send).
 */
public class WidgetActionActivity extends Activity {

    public static final String EXTRA_ACTION = "dev.ukanth.ufirewall.widget.ACTION";
    public static final String ACTION_TOGGLE = "toggle";

    private SecurityUtil security;

    public static Intent toggleIntent(Context ctx) {
        return new Intent(ctx, WidgetActionActivity.class)
                .putExtra(EXTRA_ACTION, ACTION_TOGGLE)
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
        final boolean enable = !Api.isEnabled(this);
        if (!enable && G.enableConfirm()) {
            new MaterialDialog.Builder(this)
                    .title(R.string.confirmMsg)
                    .cancelable(false)
                    .positiveText(R.string.Yes)
                    .negativeText(R.string.No)
                    .onPositive((dialog, which) -> {
                        dialog.dismiss();
                        checkLockThenRun(false);
                    })
                    .onNegative((dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .show();
        } else {
            checkLockThenRun(enable);
        }
    }

    private void checkLockThenRun(boolean enable) {
        security.passCheck(allowed -> {
            if (allowed) {
                run(enable);
            }
            finish();
        });
    }

    private void run(boolean enable) {
        final Context app = getApplicationContext();
        StatusWidget.showPending(app, enable);
        FirewallActions.setEnabled(app, enable, true, ok -> StatusWidget.showResult(app, ok));
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
