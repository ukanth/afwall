package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.os.IBinder;

import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;
import dev.ukanth.ufirewall.broadcast.PackageBroadcast;

public class FirewallService extends Service {

    BroadcastReceiver connectivityReciver;
    BroadcastReceiver packageInstallReceiver;
    BroadcastReceiver packageUninstallReceiver;

    IntentFilter filter;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private static void sendImplicitBroadcast(Context ctxt, Intent i) {
        PackageManager pm=ctxt.getPackageManager();
        List<ResolveInfo> matches=pm.queryBroadcastReceivers(i, 0);

        for (ResolveInfo resolveInfo : matches) {
            Intent explicit=new Intent(i);
            ComponentName cn=
                    new ComponentName(resolveInfo.activityInfo.applicationInfo.packageName,
                            resolveInfo.activityInfo.name);

            explicit.setComponent(cn);
            ctxt.sendBroadcast(explicit);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.

        if(connectivityReciver != null) {
            unregisterReceiver(connectivityReciver);
        }

        if(packageInstallReceiver != null) {
            unregisterReceiver(packageInstallReceiver);
        }

        if(packageUninstallReceiver != null) {
            unregisterReceiver(packageUninstallReceiver);
        }

        connectivityReciver = new ConnectivityChangeReceiver();
        filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReciver, filter);


        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");

        packageInstallReceiver = new PackageBroadcast();
        registerReceiver(packageInstallReceiver, intentFilter);


        intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        packageUninstallReceiver = new PackageBroadcast();
        intentFilter.addDataScheme("package");

        registerReceiver(packageUninstallReceiver, intentFilter);

        Api.showNotification(true, this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (connectivityReciver != null) {
            unregisterReceiver(connectivityReciver);
            connectivityReciver = null;
        }
        if (packageInstallReceiver != null) {
            unregisterReceiver(packageInstallReceiver);
            packageInstallReceiver = null;
        }
        if (packageUninstallReceiver != null) {
            unregisterReceiver(packageUninstallReceiver);
            packageUninstallReceiver = null;
        }
    }
}
