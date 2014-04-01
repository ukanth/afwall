/**
 * Broadcast receiver responsible for removing rules that affect uninstalled apps.
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
package dev.ukanth.ufirewall;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import dev.ukanth.ufirewall.Log;

/**
 * Broadcast receiver responsible for removing rules that affect uninstalled
 * apps.
 */
public class PackageBroadcast extends BroadcastReceiver {

	enum NotificationType {
		newinstall, update
	};

	@Override
	public void onReceive(Context context, Intent intent) {

		Uri inputUri = Uri.parse(intent.getDataString());

		if (!inputUri.getScheme().equals("package")) {
			Log.d("AFWall+", "Intent scheme was not 'package'");
			return;
		}

		if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
			// Ignore application updates
			final boolean replacing = intent.getBooleanExtra(
					Intent.EXTRA_REPLACING, false);
			if (!replacing) {
				// Update the Firewall if necessary
				 final int uid = intent.getIntExtra(Intent.EXTRA_UID, -123);
                 Api.applicationRemoved(context, uid);
                 Api.removeCacheLabel(intent.getData().getSchemeSpecificPart(),context);
                 /*Api.applicationRemoved(context,
						inputUri.getSchemeSpecificPart());*/
                 // Force app list reload next time
                 Api.applications = null;
			}
		} else if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {

			final boolean updateApp = intent.getBooleanExtra(
					Intent.EXTRA_REPLACING, false);

			if (updateApp) {
				// dont do anything
				//1 check the package already added in firewall

			} else {
				// Force app list reload next time
				Api.applications = null;
				SharedPreferences prefs = PreferenceManager
						.getDefaultSharedPreferences(context);
				boolean isNotify = prefs.getBoolean("notifyAppInstall", false);
				if (isNotify && Api.isEnabled(context)) {
					String added_package = intent.getData()
							.getSchemeSpecificPart();
					if (PackageManager.PERMISSION_GRANTED == context
							.getPackageManager()
							.checkPermission(Manifest.permission.INTERNET,
									added_package)) {
						notifyApp(context, intent, added_package);
					}
				}
			}

		}
	}

	//@SuppressWarnings("deprecation")
	public void notifyApp(Context context, Intent intent2, String addedPackage) {
		String ns = Context.NOTIFICATION_SERVICE;

		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(ns);

		int icon = R.drawable.widget_on;
		
		final int HELLO_ID = 24556;

		Intent appIntent = new Intent(context, MainActivity.class);
		PendingIntent in = PendingIntent.getActivity(context, 0, appIntent, 0);
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

		builder.setSmallIcon(icon)
		            //.setWhen(System.currentTimeMillis())
		            .setAutoCancel(true)
		            //.addAction(R.drawable.on, "Enable", in)
		            //.addAction(R.drawable.off, "disable", in)
		            .setContentTitle(context.getString(R.string.notification_title))
		            .setTicker(context.getString(R.string.notification_title))
		            .setContentText(context.getString(R.string.notification_new));
		
		//Notification n = builder.build();

		//Notification notification = new Notification(icon, tickerText, when);
		
		builder.setContentIntent(in);
		
		/*notification.flags |= Notification.FLAG_AUTO_CANCEL
				| Notification.FLAG_SHOW_LIGHTS;

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				appIntent, 0);

		notification.setLatestEventInfo(context, tickerText,
				context.getString(R.string.notification_new), contentIntent);*/

		mNotificationManager.notify(HELLO_ID, builder.build());

	}

}
