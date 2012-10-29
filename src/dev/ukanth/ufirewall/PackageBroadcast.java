/**
 * Broadcast receiver responsible for removing rules that affect uninstalled apps.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
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
 * @author Rodrigo Zechin Rosauro
 * @version 1.0
 */
package dev.ukanth.ufirewall;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;

/**
 * Broadcast receiver responsible for removing rules that affect uninstalled
 * apps.
 */
public class PackageBroadcast extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
			// Ignore application updates
			final boolean replacing = intent.getBooleanExtra(
					Intent.EXTRA_REPLACING, false);
			if (!replacing) {
				// Update the Firewall if necessary
				final int uid = intent.getIntExtra(Intent.EXTRA_UID, -123);
				Api.applicationRemoved(context, uid);
				// Force app list reload next time
				Api.applications = null;
			}
		} else if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
			// Force app list reload next time
			Api.applications = null;
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(context);
			boolean isNotify = prefs.getBoolean("notifyAppInstall", false);
			if (isNotify) {
				String added_package = intent.getData().toString().substring(8);
				if (PackageManager.PERMISSION_GRANTED == context
						.getPackageManager().checkPermission(
								Manifest.permission.INTERNET, added_package)) {
					notifyApp(context, intent, added_package);
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	public void notifyApp(Context context, Intent intent2, String addedPackage) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(ns);

		int icon = R.drawable.icon;
		CharSequence tickerText = "Open AFWall+";
		long when = System.currentTimeMillis();

		Intent appIntent = new Intent(context, MainActivity.class);

		Notification notification = new Notification(icon, tickerText, when);

		CharSequence contentTitle = "Open AFWall+";

		CharSequence contentText = "New Application Installed.";
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				appIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		final int HELLO_ID = 24556;

		mNotificationManager.notify(HELLO_ID, notification);

	}

}
