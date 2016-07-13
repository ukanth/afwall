/**
 * Detect the connectivity changes (for roaming and LAN subnet changes)
 * 
 * Copyright (C) 2011-2012  Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */
package dev.ukanth.ufirewall.broadcast;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogService;
import dev.ukanth.ufirewall.util.G;

public class ConnectivityChangeReceiver extends BroadcastReceiver {

	public static final String TAG = "AFWall";

	// These are marked "@hide" in WifiManager.java
	public static final String WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED";
	public static final String EXTRA_WIFI_AP_STATE = "wifi_state";
	public static final String EXTRA_PREVIOUS_WIFI_AP_STATE = "previous_wifi_state";

	private final int id = 1;
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notiBuilder;

	/*public static final int WIFI_AP_STATE_DISABLING = 10;
	public static final int WIFI_AP_STATE_DISABLED = 11;
	public static final int WIFI_AP_STATE_ENABLING = 12;
	public static final int WIFI_AP_STATE_ENABLED = 13;
	public static final int WIFI_AP_STATE_FAILED = 14;*/

	@Override
	public void onReceive(final Context context, Intent intent) {
		if (intent.getAction().equals(WIFI_AP_STATE_CHANGED_ACTION)) {
			int newState = intent.getIntExtra(EXTRA_WIFI_AP_STATE, -1);
			int oldState = intent.getIntExtra(EXTRA_PREVIOUS_WIFI_AP_STATE, -1);
			Log.d(TAG, "OS reported AP state change: " + oldState + " -> " + newState);
		}
		// NOTE: this gets called for wifi/3G/tether/roam changes but not VPN connect/disconnect
		// This will prevent applying rules when the user disable the option in preferences. This is for low end devices


		//hard code 5 seconds delay before apply rules
		final int delay = 3;

		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notiBuilder = new NotificationCompat.Builder(context);
		notiBuilder.setContentTitle(context.getString(R.string.applying_rules))
				.setContentText(context.getString(R.string.apply))
				.setPriority(NotificationCompat.PRIORITY_MIN)
				.setSmallIcon(R.drawable.widget_bg);

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(new Intent(context, MainActivity.class));
		PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		notiBuilder.setContentIntent(pendingIntent);

		if(G.activeRules()) {

			new Thread(new Runnable() {
				@Override
				public void run() {
					boolean notification = true;
					for (int i = delay; i >= 0; i--)
						try {
							Thread.sleep(1000);
							if (notification) {
								notiBuilder.setContentText(context.getString(R.string.apply)).setProgress(delay, delay - i, false);
								notificationManager.notify(id, notiBuilder.build());
							} else if ((i % 10 == 0 || i == delay) && i != 0) {
							}
						} catch (InterruptedException e) {
						}
					if (notification) {
						notiBuilder.setContentText(context.getString(R.string.rules_applied)).setProgress(0, 0, false);
						notificationManager.notify(id, notiBuilder.build());
					}

					InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.CONNECTIVITY_CHANGE);
					final Intent logIntent = new Intent(context, LogService.class);
					if (G.enableLogService()) {
						//check if the firewall is enabled
						if (!Api.isEnabled(context) || !InterfaceTracker.isNetworkUp(context)) {
							//make sure kill all the klog ripper
							context.stopService(logIntent);
						} else {
							//restart the service
							context.stopService(logIntent);
							context.startService(logIntent);
						}
					} else {
						//no internet - stop the service
						context.stopService(logIntent);
					}
					//cleanup the notification after applying rules
					notificationManager.cancel(id);
				}
			}).start();
		}
	}
}