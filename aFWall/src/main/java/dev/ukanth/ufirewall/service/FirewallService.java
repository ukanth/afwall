package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.IBinder;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;
import dev.ukanth.ufirewall.broadcast.PackageBroadcast;

public class FirewallService extends Service {

    BroadcastReceiver connectivityReciver;
    BroadcastReceiver packageReceiver;

    IntentFilter filter;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.
        connectivityReciver = new ConnectivityChangeReceiver();
        filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReciver, filter);


        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");

        packageReceiver = new PackageBroadcast();
        registerReceiver(packageReceiver, intentFilter);

        intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");

        registerReceiver(packageReceiver, intentFilter);

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
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
            packageReceiver = null;
        }
    }
}
