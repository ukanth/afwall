/*
 * Copyright 2012 two forty four a.m. LLC <http://www.twofortyfouram.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package dev.ukanth.ufirewall.plugin;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.FirewallActions;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Notifications;

/**
 * This is the "fire" BroadcastReceiver for a Locale Plug-in setting.
 * <p>
 * It has to be exported without a permission, so any app can send it actions. Only actions saved
 * in AFWall+'s editor run: they are signed there ({@link ActionSigner}). Actions saved before
 * 5.1.0 aren't signed; they run while {@link G#taskerAcceptUnsigned} is on.
 */
public final class FireReceiver extends BroadcastReceiver {
    public static final String TAG = "AFWall";
    // goAsync() must be released before the broadcast times out (10 s for foreground broadcasts)
    private static final long ASYNC_TIMEOUT_MS = 9000;
    private static final String PREF_RESAVE_SHOWN = "taskerResaveShown";

    /**
     * Result of a signed action.
     */
    private interface Outcome {
        /**
         * @param error why it failed, for the user; null on success
         * @param vars  Tasker variables to return; may be null
         */
        void done(String error, Bundle vars);
    }

    /**
     * @param context {@inheritDoc}.
     * @param intent  the incoming {@link com.twofortyfouram.locale.Intent#ACTION_FIRE_SETTING} Intent. This
     *                should contain the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} that was saved by
     *                {@link } and later broadcast by Locale.
     */
    @Override
    public void onReceive(final Context context, final Intent intent) {
        /*
         * Always be sure to be strict on input parameters! A malicious third-party app could always send an
         * empty or otherwise malformed Intent. And since Locale applies settings in the background, the
         * plug-in definitely shouldn't crash in the background.
         */

        /*
         * Locale guarantees that the Intent action will be ACTION_FIRE_SETTING
         */
        if (!com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING.equals(intent.getAction())) {
            return;
        }

        /*
         * A hack to prevent a private serializable classloader attack
         */
        BundleScrubber.scrub(intent);
        BundleScrubber.scrub(intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));
        final Bundle bundle = intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);

        /*
         * Final verification of the plug-in Bundle before firing the setting.
         */
        if (!PluginBundleManager.isBundleValid(bundle)) {
            return;
        }
        final Context app = context.getApplicationContext();
        final boolean signed = PluginBundleManager.isSigned(bundle);
        if (!G.allowTaskerControl()) {
            Log.i(TAG, "Tasker/Locale action ignored: control by other apps is turned off");
            TaskerHistory.add(app, app.getString(R.string.tasker_action_unknown), app.getString(R.string.tasker_ignored_off));
            if (signed) {
                signalHost(app, intent, app.getString(R.string.tasker_ignored_off), null);
            }
            return;
        }
        if (!TaskerHistory.underRateLimit()) {
            Log.w(TAG, "Tasker/Locale action ignored: too many in the last minute");
            TaskerHistory.add(app, app.getString(R.string.tasker_action_unknown), app.getString(R.string.tasker_ignored_rate));
            if (signed) {
                signalHost(app, intent, app.getString(R.string.tasker_ignored_rate), null);
            }
            return;
        }
        if (signed) {
            runSigned(app, intent, bundle);
            return;
        }
        G.taskerUnsignedSeen(true);
        if (!G.taskerAcceptUnsigned(app)) {
            ignored(app, app.getString(R.string.tasker_ignored_unsigned));
            return;
        }
        String index = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
        String name = null;
        if (index.contains("::")) {
            String[] parts = index.split("::", 2);
            index = parts[0];
            name = parts[1].isEmpty() ? null : parts[1];
        }
        final String profileId = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_PROFILE_ID);
        TaskerHistory.add(app, name != null ? name : index, app.getString(R.string.tasker_ran_old));
        resaveNotice(app);

        // Keep the process alive until the rules are loaded: when AFWall+ isn't running, Android
        // may otherwise kill it as soon as onReceive() returns. Released on completion, or
        // before the broadcast timeout.
        final PendingResult pending = goAsync();
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) {
                pending.finish();
            }
        };
        new Handler(Looper.getMainLooper()).postDelayed(finish, ASYNC_TIMEOUT_MS);

        switch (index) {
            case "0":
                FirewallActions.setEnabled(app, true, false, ok -> {
                    toast(app, ok ? R.string.toast_enabled : R.string.toast_error_enabling, ok);
                    finish.run();
                });
                return;
            case "1":
                if (!G.protectionLevel().equals("p0")) {
                    toast(app, R.string.widget_disable_fail, false);
                    finish.run();
                    return;
                }
                FirewallActions.setEnabled(app, false, false, ok -> {
                    toast(app, ok ? R.string.toast_disabled : R.string.toast_error_disabling, ok);
                    finish.run();
                });
                return;
            default:
                switchProfile(app, index, name, profileId, finish);
        }
    }

    private static void switchProfile(Context app, String index, String name, String profileId, Runnable finish) {
        if (!G.enableMultiProfile()) {
            toast(app, R.string.tasker_muliprofile, false);
            finish.run();
            return;
        }
        String identifier = null;
        if (profileId != null && (Api.DEFAULT_PREFS_NAME.equals(profileId)
                || ProfileHelper.getProfileByIdentifier(profileId) != null)) {
            identifier = profileId; // survives renaming
        } else if (index.equals("2")) {
            identifier = Api.DEFAULT_PREFS_NAME;
        } else if (name != null) {
            ProfileData data = ProfileHelper.getProfileByName(name);
            identifier = data != null ? data.getIdentifier() : null;
        } else if (index.equals("3") || index.equals("4") || index.equals("5")) {
            // very old actions saved only the index: 3-5 were Profile 1-3
            ProfileData data = ProfileHelper.getProfileByIdentifier("AFWallProfile" + (Integer.parseInt(index) - 2));
            identifier = data != null ? data.getIdentifier() : null;
        }
        if (identifier == null) {
            // deleted, or renamed before actions stored the identifier: don't pretend it worked
            Api.toast(app, app.getString(R.string.tasker_profile_not_found, name != null ? name : index));
            finish.run();
            return;
        }
        final boolean enabled = Api.isEnabled(app);
        if (enabled) {
            toast(app, R.string.tasker_apply, true);
        }
        FirewallActions.switchProfile(app, identifier, false, ok -> {
            if (!enabled) {
                toast(app, R.string.tasker_disabled, false);
            } else {
                toast(app, ok ? R.string.tasker_profile_applied : R.string.error_apply, ok);
            }
            Api.updateNotification(Api.isEnabled(app), app);
            finish.run();
        });
    }

    // ---- signed actions (5.1.0+) ----

    private void runSigned(Context app, Intent intent, Bundle bundle) {
        TaskerAction action = PluginBundleManager.readSigned(bundle);
        if (action == null || !ActionSigner.verify(app, action.payload(),
                bundle.getString(PluginBundleManager.EXTRA_SIGNATURE))) {
            ignored(app, app.getString(R.string.tasker_ignored_signature));
            signalHost(app, intent, app.getString(R.string.tasker_ignored_signature), null);
            return;
        }
        final String pkg = action.pkg != null
                ? action.packageToRun(bundle.getString(PluginBundleManager.EXTRA_PACKAGE_VALUE)) : null;
        final String what = describe(app, action, pkg);

        final PendingResult pending = goAsync();
        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) {
                pending.finish();
            }
        };
        new Handler(Looper.getMainLooper()).postDelayed(finish, ASYNC_TIMEOUT_MS);

        Outcome outcome = (error, vars) -> {
            boolean ok = error == null;
            Log.i(TAG, "Tasker action " + action.id + " (" + action.action + "): " + (ok ? "done" : error));
            TaskerHistory.add(app, what, ok ? app.getString(R.string.tasker_done) : error);
            signalHost(app, intent, error, vars);
            if (!ok) {
                Api.toast(app, app.getString(R.string.tasker_failed, what, error));
            } else if (!TaskerAction.STATUS.equals(action.action) && !G.disableTaskerToast()) {
                Api.toast(app, app.getString(R.string.tasker_toast_done, what));
            }
            if (ok && action.loosens()) {
                Notifications.tasker(app, Notifications.ID_TASKER_ACTION,
                        app.getString(R.string.tasker_notify_loosened, callerLabel(app, action.caller)), what);
            }
            Api.updateNotification(Api.isEnabled(app), app);
            finish.run();
        };
        run(app, action, pkg, outcome);
    }

    private static void run(Context app, TaskerAction action, String pkg, Outcome outcome) {
        switch (action.action) {
            case TaskerAction.ENABLE:
            case TaskerAction.DISABLE:
                boolean enable = TaskerAction.ENABLE.equals(action.action);
                FirewallActions.setEnabled(app, enable, false, ok -> outcome.done(ok ? null
                        : app.getString(enable ? R.string.toast_error_enabling : R.string.toast_error_disabling), null));
                return;
            case TaskerAction.APPLY:
                if (!Api.isEnabled(app)) {
                    outcome.done(app.getString(R.string.tasker_error_disabled), null);
                    return;
                }
                Api.applySavedIptablesRules(app, true, new RootCommand().setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        outcome.done(state.exitCode == 0 ? null : app.getString(R.string.error_apply), null);
                    }
                }));
                return;
            case TaskerAction.PROFILE:
                if (!G.enableMultiProfile()) {
                    outcome.done(app.getString(R.string.tasker_muliprofile), null);
                    return;
                }
                String identifier = Api.DEFAULT_PREFS_NAME.equals(action.profileId)
                        || ProfileHelper.getProfileByIdentifier(action.profileId) != null
                        ? action.profileId : FirewallActions.identifierForName(app, action.profileName);
                if (identifier == null) {
                    outcome.done(app.getString(R.string.tasker_profile_not_found, action.profileName), null);
                    return;
                }
                FirewallActions.switchProfile(app, identifier, false,
                        ok -> outcome.done(ok ? null : app.getString(R.string.error_apply), null));
                return;
            case TaskerAction.ALLOW:
            case TaskerAction.BLOCK:
                if (pkg == null) {
                    outcome.done(app.getString(R.string.tasker_error_package), null);
                    return;
                }
                int uid;
                try {
                    uid = app.getPackageManager().getApplicationInfo(pkg, 0).uid;
                } catch (PackageManager.NameNotFoundException e) {
                    outcome.done(app.getString(R.string.tasker_error_not_installed, pkg), null);
                    return;
                }
                if (!FirewallActions.setAppAccess(app, uid, TaskerAction.ALLOW.equals(action.action), action.network,
                        ok -> outcome.done(ok ? null : app.getString(R.string.error_apply), null))) {
                    outcome.done(app.getString(R.string.tasker_error_network_off), null);
                }
                return;
            case TaskerAction.STATUS:
                Bundle vars = new Bundle();
                vars.putString("%afw_enabled", Boolean.toString(Api.isEnabled(app)));
                vars.putString("%afw_profile", ProfileHelper.displayName(app, G.storedProfile()));
                vars.putString("%afw_mode", G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST));
                outcome.done(null, vars);
                return;
            default:
                outcome.done(app.getString(R.string.tasker_ignored_signature), null);
        }
    }

    /**
     * The action as shown in the editor's summary, the recent actions and notifications.
     *
     * @param pkg the package it acts on (for allow / block), or null to show the saved one
     */
    static String describe(Context ctx, TaskerAction action, String pkg) {
        switch (action.action) {
            case TaskerAction.ENABLE:
                return ctx.getString(R.string.enable_tasker);
            case TaskerAction.DISABLE:
                return ctx.getString(R.string.disable_tasker);
            case TaskerAction.APPLY:
                return ctx.getString(R.string.tasker_action_apply);
            case TaskerAction.PROFILE:
                return ctx.getString(R.string.tasker_action_profile_named, action.profileName);
            case TaskerAction.ALLOW:
            case TaskerAction.BLOCK:
                String app = appLabel(ctx, pkg != null ? pkg : action.pkg);
                String net = networkLabel(ctx, action.network);
                return ctx.getString(TaskerAction.ALLOW.equals(action.action)
                        ? R.string.tasker_action_allow_named : R.string.tasker_action_block_named, app, net);
            case TaskerAction.STATUS:
                return ctx.getString(R.string.tasker_action_status);
            default:
                return action.action;
        }
    }

    static String networkLabel(Context ctx, String network) {
        int i = TaskerAction.NETWORKS.indexOf(network);
        String[] labels = ctx.getResources().getStringArray(R.array.tasker_networks);
        return i >= 0 && i < labels.length ? labels[i] : String.valueOf(network);
    }

    private static String appLabel(Context ctx, String pkg) {
        if (!TaskerAction.isPackageName(pkg)) {
            return pkg; // a Tasker variable
        }
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static String callerLabel(Context ctx, String caller) {
        if (caller != null) {
            String label = appLabel(ctx, caller);
            if (label != null) {
                return label;
            }
        }
        return ctx.getString(R.string.tasker_unknown_caller);
    }

    /**
     * Tell Tasker the action finished (it waits when the editor asked for a timeout), with
     * {@code %errmsg} and the returned variables. The completion intent comes from the sender, so
     * it may only go back to another app, and grants nothing.
     *
     * @param error null on success
     */
    private static void signalHost(Context app, Intent fire, String error, Bundle vars) {
        String uri = fire.getStringExtra(PluginBundleManager.TASKER_COMPLETION_INTENT);
        if (uri == null) {
            return;
        }
        Intent done;
        try {
            done = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException e) {
            return;
        }
        String target = done.getComponent() != null ? done.getComponent().getPackageName() : done.getPackage();
        if (target == null || target.equals(app.getPackageName())) {
            return;
        }
        done.setSelector(null);
        done.setFlags(done.getFlags() & ~(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION));
        Bundle out = vars != null ? new Bundle(vars) : new Bundle();
        if (error != null) {
            out.putString("%errmsg", error);
        }
        done.putExtra(PluginBundleManager.TASKER_RESULT_CODE, error == null ? Activity.RESULT_OK : Activity.RESULT_FIRST_USER + 1);
        done.putExtra(PluginBundleManager.TASKER_VARIABLES, out);
        try {
            app.sendBroadcast(done);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to report the Tasker action result: " + e);
        }
    }

    /**
     * An action that didn't run because it wasn't saved in AFWall+ (or was changed since): logged,
     * listed in the recent actions, and one notification (updated, not repeated).
     */
    private static void ignored(Context app, String reason) {
        Log.w(TAG, "Tasker/Locale action " + reason);
        TaskerHistory.add(app, app.getString(R.string.tasker_action_unknown), reason);
        Notifications.tasker(app, Notifications.ID_TASKER_IGNORED, app.getString(R.string.tasker_notify_ignored), reason);
    }

    /**
     * Once: actions saved before 5.1.0 still run, but should be saved again.
     */
    private static void resaveNotice(Context app) {
        if (G.gPrefs.getBoolean(PREF_RESAVE_SHOWN, false)) {
            return;
        }
        G.gPrefs.edit().putBoolean(PREF_RESAVE_SHOWN, true).apply();
        Notifications.tasker(app, Notifications.ID_TASKER_RESAVE, app.getString(R.string.tasker_notify_resave_title),
                app.getString(R.string.tasker_notify_resave_text));
    }

    /**
     * @param success success messages follow the "disable Tasker toasts" setting; errors always show
     */
    private static void toast(Context app, int resId, boolean success) {
        if (!success || !G.disableTaskerToast()) {
            Api.toast(app, app.getString(resId));
        }
    }
}