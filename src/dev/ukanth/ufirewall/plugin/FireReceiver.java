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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.Log;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.RootShell.RootCommand;

/**
 * This is the "fire" BroadcastReceiver for a Locale Plug-in setting.
 */
public final class FireReceiver extends BroadcastReceiver
{
	public static final String TAG = "AFWall";

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
		final String newPwd = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0).getString("LockPassword", "");

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
        	//SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        	final boolean multimode = G.enableMultiProfile();
        	final boolean disableToasts = G.disableTaskerToast();
    		final Message msg = new Message();
        	if(index != null){
        		int id = Integer.parseInt(index);
        		if(id == 0) {
	    			if (Api.isEnabled(context))  {
	    				if(applyRules(context,msg,toaster)){
							Api.setEnabled(context, true, false);
							msg.arg1 = R.string.toast_enabled;
							toaster.sendMessage(msg);
						}
	    			} 
        		} else if (id == 1) {
        			if (Api.isEnabled(context)) {
        				if(oldPwd.length() == 0 && newPwd.length() == 0){
            				boolean ret = Api.purgeIptables(context, false, new RootCommand()
        					.setFailureToast(R.string.error_apply)
        					.setCallback(new RootCommand.Callback() {
        						@Override
        						public void cbFunc(RootCommand state) {
        							if (state.exitCode == 0) {
        								Log.i(Api.TAG, "" + ": applied rules");
        							} 
        						}
        					}));
    		        		if (ret) {
    		        			Api.setEnabled(context,  false,  true);
    		        			msg.arg1 = R.string.tasker_disabled;
    		        		}
    		        	} else {
    						msg.arg1 = R.string.widget_disable_fail;
    					}
        			} else {
        				msg.arg1 = R.string.tasker_disabled;
        			}
        			toaster.sendMessage(msg);
				}
        		if(id > 1){
        			if(multimode) {
        				G.setProfile(multimode, (id-2));
        				if (Api.isEnabled(context)) {
                			if(!disableToasts){
                				Toast.makeText(context, R.string.tasker_apply, Toast.LENGTH_SHORT).show();	
                			}
                			if(applyRules(context, msg, toaster)) {
               					msg.arg1 = R.string.tasker_profile_applied;
               					if(!disableToasts) toaster.sendMessage(msg);
                			}
                		} else {
                			msg.arg1 = R.string.tasker_disabled;
                			toaster.sendMessage(msg);
                		}		
        			} else {
        				msg.arg1 = R.string.tasker_muliprofile;
        				toaster.sendMessage(msg);
                	}
        		}
        	} 
        }
    }
    
    
    private static void errorNotification(Context ctx) {
		NotificationManager mNotificationManager =
				(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);

		// Artificial stack so that navigating backward leads back to the Home screen
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
				.addParentStack(MainActivity.class)
				.addNextIntent(new Intent(ctx, MainActivity.class));

		Notification notification = new NotificationCompat.Builder(ctx)
			.setContentTitle(ctx.getString(R.string.error_notification_title))
			.setContentText(ctx.getString(R.string.error_notification_text))
			.setTicker(ctx.getString(R.string.error_notification_ticker))
			.setSmallIcon(R.drawable.widget_on)
			.setAutoCancel(true)
			.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT))
			.build();

		mNotificationManager.notify(InterfaceTracker.ERROR_NOTIFICATION_ID, notification);
	}
 
    private boolean applyRules(final Context context,Message msg, Handler toaster) {
    	boolean ret = Api.fastApply(context, new RootCommand()
		.setFailureToast(R.string.error_apply)
		.setCallback(new RootCommand.Callback() {
			@Override
			public void cbFunc(RootCommand state) {
				if (state.exitCode == 0) {
					Log.i(Api.TAG, "" + ": applied rules");
				} else {
					// error details are already in logcat
					Api.setEnabled(context,  false,  false);
					errorNotification(context);
				}
			}
		}));
		return ret;
	}
}