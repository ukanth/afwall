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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.widget.Toast;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.R;

/**
 * This is the "fire" BroadcastReceiver for a Locale Plug-in setting.
 */
public final class FireReceiver extends BroadcastReceiver
{

    /**
     * @param context {@inheritDoc}.
     * @param intent the incoming {@link com.twofortyfouram.locale.Intent#ACTION_FIRE_SETTING} Intent. This
     *            should contain the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} that was saved by
     *            {@link EditActivity} and later broadcast by Locale.
     */
    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        /*
         * Always be sure to be strict on input parameters! A malicious third-party app could always send an
         * empty or otherwise malformed Intent. And since Locale applies settings in the background, the
         * plug-in definitely shouldn't crash in the background.
         */

        /*
         * Locale guarantees that the Intent action will be ACTION_FIRE_SETTING
         */
        if (!com.twofortyfouram.locale.Intent.ACTION_FIRE_SETTING.equals(intent.getAction()))
        {
            return;
        }

        /*
         * A hack to prevent a private serializable classloader attack
         */
        BundleScrubber.scrub(intent);
        BundleScrubber.scrub(intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE));
        final Bundle bundle = intent.getBundleExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE);
        final SharedPreferences prefs2 = context.getSharedPreferences(Api.PREFS_NAME, 0);
        final String oldPwd = prefs2.getString(Api.PREF_PASSWORD, "");
        final String newPwd = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0).getString(
                "LockPassword", "");

        final Handler toaster = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.arg1 != 0) Toast.makeText(context, msg.arg1, Toast.LENGTH_SHORT).show();
            }
        };
        /*
         * Final verification of the plug-in Bundle before firing the setting.
         */
        if (PluginBundleManager.isBundleValid(bundle))
        {
            String index = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean multimode = prefs.getBoolean("enableMultiProfile", false);
            final boolean disableToasts = prefs.getBoolean("disableTaskerToast", false);
            SharedPreferences.Editor editor = prefs.edit();
            final Message msg = new Message();
            if (index != null) {
                int id = Integer.parseInt(index);
                switch (id) {
                    case 0:
                        if (applyRules(context, msg, toaster)) {
                            Api.setEnabled(context, true, false);
                        }
                        break;

                    case 1:
                        if (oldPwd.length() == 0 && newPwd.length() == 0) {
                            if (Api.purgeIptables(context, false)) {
                                msg.arg1 = R.string.toast_disabled;
                                toaster.sendMessage(msg);
                                Api.setEnabled(context, false, false);
                            } else {
                                msg.arg1 = R.string.toast_error_disabling;
                                toaster.sendMessage(msg);
                            }
                        } else {
                            msg.arg1 = R.string.widget_disable_fail;
                            toaster.sendMessage(msg);
                        }
                        break;
                    case 2:
                        G.setProfile(multimode, 0);
                        break;
                    case 3:
                        G.setProfile(multimode, 1);
                        break;
                    case 4:
                        G.setProfile(multimode, 2);
                        break;
                    case 5:
                        G.setProfile(multimode, 3);
                        break;
                    default:
                        break;
                }
                if (id > 1) {
                    editor.putInt("storedPosition", (id - 2));
                    editor.commit();
                    if (multimode) {
                        if (Api.isEnabled(context)) {
                            if (!disableToasts) {
                                Toast.makeText(context, R.string.tasker_apply, Toast.LENGTH_SHORT).show();
                            }
                            Api.applySavedIptablesRules(context, true);
                        } else {
                            Toast.makeText(context, R.string.tasker_disabled, Toast.LENGTH_SHORT).show();
                        }
                    }
                    else {
                        Toast.makeText(context, R.string.tasker_muliprofile, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    private boolean applyRules(Context context, Message msg, Handler toaster) {
        boolean success = false;
        if (Api.applySavedIptablesRules(context, false)) {
            msg.arg1 = R.string.toast_enabled;
            toaster.sendMessage(msg);
            success = true;
        } else {
            msg.arg1 = R.string.toast_error_enabling;
            toaster.sendMessage(msg);
        }
        return success;
    }
}
