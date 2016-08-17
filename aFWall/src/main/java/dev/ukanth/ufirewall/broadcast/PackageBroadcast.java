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
package dev.ukanth.ufirewall.broadcast;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;

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
                 // Force app list reload next time
                 Api.applications = null;
			}
		} else if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
			final boolean updateApp = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

			if (updateApp) {
				// dont do anything
				//1 check the package already added in firewall
			} else {
				// Force app list reload next time
				Api.applications = null;
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
				boolean isNotify = prefs.getBoolean("notifyAppInstall", false);
				if (isNotify && Api.isEnabled(context)) {
					String added_package = intent.getData().getSchemeSpecificPart();
					final PackageManager pkgmanager = context.getPackageManager();
					String label = null;
					try {
						label = pkgmanager.getApplicationLabel(pkgmanager.getApplicationInfo(added_package, 0)).toString();
					} catch (NameNotFoundException e) {
					}
					if (PackageManager.PERMISSION_GRANTED == pkgmanager.checkPermission(Manifest.permission.INTERNET,added_package)) {
						notifyApp(context, intent, label);
					}
				}
			}
		}
	}

	//@SuppressWarnings("deprecation")
	public void notifyApp(Context context, Intent intent2, String label) {
		String ns = Context.NOTIFICATION_SERVICE;

		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(ns);

		int icon = R.drawable.question;

		if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
			icon = R.drawable.notification_quest;
		}
		
		final int HELLO_ID = 24556;

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
		
		Intent appIntent = new Intent(context, MainActivity.class);
		PendingIntent in = PendingIntent.getActivity(context, 0, appIntent, 0);
		
		String notificationText = context.getString(R.string.notification_new);
		if(label != null) {
			notificationText = label +  "-" +context.getString(R.string.notification_new_package);
		}
		builder.setSmallIcon(icon)
		            .setAutoCancel(true)
		            .setContentTitle(context.getString(R.string.notification_title))
		            .setTicker(context.getString(R.string.notification_title))
		            .setContentText(notificationText);
		builder.setContentIntent(in);
		
		mNotificationManager.notify(HELLO_ID, builder.build());

	}

}
