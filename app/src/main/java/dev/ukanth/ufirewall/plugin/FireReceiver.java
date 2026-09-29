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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.FirewallActions;
import dev.ukanth.ufirewall.util.G;

/**
 * This is the "fire" BroadcastReceiver for a Locale Plug-in setting.
 */
public final class FireReceiver extends BroadcastReceiver {
    public static final String TAG = "AFWall";
    // goAsync() must be released before the broadcast times out (10 s for foreground broadcasts)
    private static final long ASYNC_TIMEOUT_MS = 9000;

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
        if (!G.allowTaskerControl()) {
            // the receiver has to be exported for Tasker/Locale, so any app can send this
            Log.i(TAG, "Tasker/Locale action ignored: control by other apps is turned off");
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
        final Context app = context.getApplicationContext();

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

    /**
     * @param success success messages follow the "disable Tasker toasts" setting; errors always show
     */
    private static void toast(Context app, int resId, boolean success) {
        if (!success || !G.disableTaskerToast()) {
            Api.toast(app, app.getString(resId));
        }
    }
}