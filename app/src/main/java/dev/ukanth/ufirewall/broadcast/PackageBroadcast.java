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

import static dev.ukanth.ufirewall.util.G.isDonate;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.UserHandle;
import android.preference.PreferenceManager;

import java.util.HashSet;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Notifications;
import dev.ukanth.ufirewall.util.UidResolver;

/**
 * Broadcast receiver responsible for removing rules that affect uninstalled
 * apps.
 */
public class PackageBroadcast extends BroadcastReceiver {

    public static final String TAG = "AFWall";

    private static String lastEventKey = "";
    private static long lastEventTime = 0;

    @Override
    public void onReceive(final Context context, final Intent intent) {

        Uri inputUri = Uri.parse(intent.getDataString());

        String eventKey = intent.getAction() + ":" + inputUri.getSchemeSpecificPart() + ":" + intent.getIntExtra(Intent.EXTRA_UID, -1);
        synchronized (PackageBroadcast.class) {
            long now = System.currentTimeMillis();
            if (eventKey.equals(lastEventKey) && now - lastEventTime < 2000) {
                Log.d(TAG, "Ignoring duplicate package event: " + eventKey);
                return;
            }
            lastEventKey = eventKey;
            lastEventTime = now;
        }

        if (!inputUri.getScheme().equals("package")) {
            Log.d(TAG, "Intent scheme was not 'package'");
            return;
        }

        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
            // Ignore application updates (FULLY_REMOVED is never sent for those)
            final boolean replacing = Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            if (!replacing) {
                // Update the Firewall if necessary
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -123);
                if (uid < 0) {
                    Log.w(TAG, "Package removed without a UID, ignoring");
                    return;
                }
                // Another package may still own this UID (shared user id); keep its rules then
                String[] remaining = context.getPackageManager().getPackagesForUid(uid);
                if (remaining != null && remaining.length > 0) {
                    Log.d(TAG, "UID " + uid + " is still used by " + remaining.length + " package(s); keeping rules");
                } else {
                    Api.applicationRemoved(context, uid, new RootCommand()
                            .setFailureToast(R.string.error_apply));
                }

                // Cache cleanup doesn't depend on the rule apply; do it off the main thread
                final String removedPackage = inputUri.getSchemeSpecificPart();
                final PendingResult pendingResult = goAsync();
                new Thread(() -> {
                    try {
                        Api.removeCacheLabel(removedPackage, context);
                        Api.removeAllUnusedCacheLabel(context);
                        // Force app list reload next time
                        Api.applications = null;
                        UidResolver.invalidateUid(uid);
                        Log.d(TAG, "Package removed, invalidated UID cache for: " + uid);
                    } catch (Exception e) {
                        Log.e(TAG, "Error cleaning caches for removed package", e);
                    } finally {
                        pendingResult.finish();
                    }
                }, "AFWall-PackageRemoved").start();
            }
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            final boolean updateApp = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

            if (updateApp) {
                // dont do anything
                //1 check the package already added in firewall
            } else {
                // Force app list reload next time
                Api.applications = null;
                
                // Clear UID resolver cache since new package may get a UID we've seen before
                UidResolver.clearCache();
                Log.d(TAG, "Package added, cleared UID resolver cache");
                
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
                            Notifications.newApp(context, applicationInfo.uid, added_package, label);
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


    /**
     * A package was installed in another Android user of this device (work profile, Private
     * Space, clone profile), reported by LauncherApps while FirewallService runs.
     */
    public static void onProfilePackageAdded(Context context, LauncherApps launcherApps, String pkg, UserHandle user) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return; // the listener is only registered on 8+
        }
        Api.applications = null;
        UidResolver.clearCache();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("notifyAppInstall", true) || !Api.isEnabled(context)) {
            return;
        }
        try {
            ApplicationInfo info = launcherApps.getApplicationInfo(pkg, 0, user);
            if (info == null || !requestsInternet(context, pkg)) {
                return;
            }
            String label = info.loadLabel(context.getPackageManager()).toString();
            Notifications.newApp(context, info.uid, pkg, context.getString(R.string.notif_new_app_profile, label));
        } catch (Exception e) {
            Log.w(TAG, "Unable to read new app " + pkg + " of " + user + ": " + e.getMessage());
        }
    }

    /**
     * @return true unless the package is known not to request INTERNET (packages of other users
     * may not be visible to this user's PackageManager; notify then)
     */
    private static boolean requestsInternet(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        if (pm.checkPermission(Manifest.permission.INTERNET, pkg) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions != null) {
                for (String perm : pi.requestedPermissions) {
                    if (Manifest.permission.INTERNET.equals(perm)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (NameNotFoundException e) {
            return true;
        }
    }
}
