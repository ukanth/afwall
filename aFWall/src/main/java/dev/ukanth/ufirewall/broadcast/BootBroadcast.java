/**
 * Broadcast receiver that set iptable rules on system startup.
 * This is necessary because the iptables rules are not persistent.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
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
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */
package dev.ukanth.ufirewall.broadcast;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.log.LogService;
import dev.ukanth.ufirewall.service.NflogService;
import dev.ukanth.ufirewall.util.G;

/**
 * Broadcast receiver that set iptables rules on system startup. This is
 * necessary because the rules are not persistent.
 */
public class BootBroadcast extends BroadcastReceiver {
	// private Handler mHandler = new Handler(Looper.getMainLooper());
	private final int id = 1;
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notiBuilder;

	@Override
	public void onReceive(final Context context, final Intent intent) {

		//hard code 5 seconds delay before apply rules
		/*final int delay = 3;

		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notiBuilder = new NotificationCompat.Builder(context);
		notiBuilder.setContentTitle(context.getString(R.string.applying_rules))
				.setContentText(context.getString(R.string.apply_sequence, delay))
				.setSmallIcon(R.drawable.notification_warn);

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(new Intent(context, MainActivity.class));
		PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		notiBuilder.setContentIntent(pendingIntent);

		new Thread(new Runnable() {
			@Override
			public void run() {
				boolean notification = true;
				for (int i = delay; i >= 0; i--)
					try {
						Thread.sleep(1000);
						if (notification) {
							notiBuilder.setContentText(context.getString(R.string.working)).setProgress(delay, delay - i, false);
							notificationManager.notify(id, notiBuilder.build());
						} else if ((i % 10 == 0 || i == delay) && i != 0) {
						}
					} catch (InterruptedException e) {
					}
				if (notification) {
					notiBuilder.setContentText(context.getString(R.string.rules_applied)).setProgress(0, 0, false);
					notificationManager.notify(id, notiBuilder.build());
				}

				InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.BOOT_COMPLETED);

				if(G.activeNotification()){
					Api.showNotification(Api.isEnabled(context), context);
				}
				//make sure nflog starts after boot
				if(G.enableLog() && "NFLOG".equals(G.logTarget())) {
					context.startService(new Intent(context.getApplicationContext(), NflogService.class));
				}
				if (G.enableLogService()) {
					context.startService(new Intent(context, LogService.class));
				}
				//cleanup the notification after applying rules
				notificationManager.cancel(id);
			}
		}).start(); */

		//TODO:  Old way of applying rules for now

		InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.BOOT_COMPLETED);

		//make sure we start the notification before apply the rules
		Api.showNotification(Api.isEnabled(context), context);

		//make sure nflog starts after boot
		if(G.enableLog() && "NFLOG".equals(G.logTarget())) {
			context.startService(new Intent(context.getApplicationContext(), NflogService.class));
		}
		if (G.enableLogService()) {
			context.startService(new Intent(context, LogService.class));
		}
		//remove the notificaction if user doesn't want.
		if(!G.activeNotification()){
			Api.removeNotification(context);
		}

	}
}