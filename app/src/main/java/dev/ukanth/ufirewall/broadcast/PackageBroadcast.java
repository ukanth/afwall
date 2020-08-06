/**
 * Broadcast receiver responsible for removing rules that affect uninstalled apps.
 * <p>
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */
package dev.ukanth.ufirewall.broadcast;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import java.util.HashSet;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.util.G.isDonate;

/**
 * Broadcast receiver responsible for removing rules that affect uninstalled
 * apps.
 */
public class PackageBroadcast extends BroadcastReceiver {

    public static final String TAG = "AFWall";

    @Override
    public void onReceive(final Context context, final Intent intent) {

        Uri inputUri = Uri.parse(intent.getDataString());

        if (!inputUri.getScheme().equals("package")) {
            Log.d(TAG, "Intent scheme was not 'package'");
            return;
        }

        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
            // Ignore application updates
            final boolean replacing = intent.getBooleanExtra(
                    Intent.EXTRA_REPLACING, false);
            if (!replacing) {
                // Update the Firewall if necessary
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -123);
                //TODO - Remove only that app
                Api.applicationRemoved(context, uid, new RootCommand()
                        .setFailureToast(R.string.error_apply)
                        .setCallback(new RootCommand.Callback() {
                            @Override
                            public void cbFunc(RootCommand state) {
                                if (state.exitCode == 0) {
                                    Api.removeCacheLabel(intent.getData().getSchemeSpecificPart(), context);
                                    Api.removeAllUnusedCacheLabel(context);
                                    // Force app list reload next time
                                    Api.applications = null;
                                }
                            }
                        }));

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
                boolean isNotify = prefs.getBoolean("notifyAppInstall", true);
                if (isNotify && Api.isEnabled(context)) {
                    String added_package = intent.getData().getSchemeSpecificPart();
                    final PackageManager packager = context.getPackageManager();
                    String label = null;
                    try {
                        ApplicationInfo applicationInfo = packager.getApplicationInfo(added_package, 0);
                        label = packager.getApplicationLabel(applicationInfo).toString();
                        if (PackageManager.PERMISSION_GRANTED == packager.checkPermission(Manifest.permission.INTERNET, added_package)) {
                            addNotification(context,label);
                        }
                        if (Api.recentlyInstalled == null) {
                            Api.recentlyInstalled = new HashSet<>();
                        }
                        Api.recentlyInstalled.add(applicationInfo.packageName);
                        //sets default permissions
                        if ((G.isDoKey(context) || isDonate())) {
                            Api.setDefaultPermission(applicationInfo);
                        }
                    } catch (NameNotFoundException e) {
                    }
                }
            }
        }
    }


    private void addNotification(Context context, String label) {
        final int NOTIFICATION_ID = 100;
        String NOTIFICATION_CHANNEL_ID = "firewall.app.notification";
        String channelName = context.getString(R.string.app_notification);

        //cancel existing notification
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            chan.setShowBadge(false);
            chan.setSound(null,null);
            chan.enableLights(false);
            chan.enableVibration(false);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert manager != null;
            manager.createNotificationChannel(chan);
        }


        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent notifyPendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);

        String notificationText = context.getString(R.string.notification_new);
        if (label != null) {
            notificationText = label + "-" + context.getString(R.string.notification_new_package);
        }

        Notification notification = notificationBuilder.setOngoing(false)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSound(null)
                .setSmallIcon(R.drawable.notification_quest)
                .setContentTitle(context.getString(R.string.notification_title))
                .setTicker(context.getString(R.string.notification_title))
                .setContentText(notificationText)
                .build();

        manager.notify(NOTIFICATION_ID, notification);
    }
}
