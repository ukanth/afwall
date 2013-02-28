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
import android.preference.PreferenceManager;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;
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
        	if(index != null && multimode){
        		int id = Integer.parseInt(index);
        		editor.putInt("storedPosition", id);
       			editor.commit();
        		switch(id){
        		case 0:
    				Api.PREFS_NAME = "AFWallPrefs";
    				break;
    			case 1:
    				Api.PREFS_NAME = "AFWallProfile1";
    				break;
    			case 2:
    				Api.PREFS_NAME = "AFWallProfile2";
    				break;
    			case 3:
    				Api.PREFS_NAME = "AFWallProfile3";
    				break;
    			default:
    				break;
				}
        		if (Api.isEnabled(context)) {
        			if(!disableToasts){
        				Toast.makeText(context, R.string.tasker_apply, Toast.LENGTH_SHORT).show();	
        			}
        			Api.applySavedIptablesRules(context, true);
        		} else {
       				Toast.makeText(context, R.string.tasker_disabled, Toast.LENGTH_SHORT).show();
        		}
        	} else {
        		Toast.makeText(context, R.string.tasker_muliprofile, Toast.LENGTH_LONG).show();
        	}
        }
    }
}