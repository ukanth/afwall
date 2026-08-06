package dev.ukanth.ufirewall.activity;

import androidx.appcompat.app.AppCompatActivity;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.service.RootCommand;

public final class FirewallRuleActions {

    private FirewallRuleActions() {
    }

    public static void confirmFlushAllRules(final AppCompatActivity activity, final Runnable onComplete) {
        new MaterialDialog.Builder(activity)
                .title(R.string.confirmation)
                .content(R.string.flushRulesConfirm)
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .onPositive((dialog, which) -> {
                    RootCommand command = new RootCommand()
                            .setReopenShell(true)
                            .setSuccessToast(R.string.flushed)
                            .setFailureToast(R.string.error_purge);
                    if (onComplete != null) {
                        command.setCallback(new RootCommand.Callback() {
                            public void cbFunc(RootCommand state) {
                                activity.runOnUiThread(onComplete);
                            }
                        });
                    }
                    Api.flushAllRules(activity, command);
                    dialog.dismiss();
                })
                .onNegative((dialog, which) -> dialog.dismiss())
                .show();
    }
}
