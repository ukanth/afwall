package dev.ukanth.ufirewall.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import java.util.List;

import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;
import dev.ukanth.ufirewall.broadcast.PackageBroadcast;
import dev.ukanth.ufirewall.util.G;

public class FirewallService extends Service {

    private static final int NOTIFICATION_ID = 1000;
    BroadcastReceiver connectivityReciver;
    BroadcastReceiver packageInstallReceiver;
    BroadcastReceiver packageUninstallReceiver;
    IntentFilter filter;

    private static void sendImplicitBroadcast(Context ctxt, Intent i) {
        PackageManager pm = ctxt.getPackageManager();
        List<ResolveInfo> matches = pm.queryBroadcastReceivers(i, 0);

        for (ResolveInfo resolveInfo : matches) {
            Intent explicit = new Intent(i);
            ComponentName cn =
                    new ComponentName(resolveInfo.activityInfo.applicationInfo.packageName,
                            resolveInfo.activityInfo.name);

            explicit.setComponent(cn);
            ctxt.sendBroadcast(explicit);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        addNotification();
    }


    private void addNotification() {
        String NOTIFICATION_CHANNEL_ID = "com.firewall";
        String channelName = "Firewall Service";

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert manager != null;
            manager.createNotificationChannel(chan);
        }


        Intent appIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        notificationBuilder.setContentIntent(resultPendingIntent);

        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_name))
                .setPriority(G.getNotificationPriority())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText(getString(R.string.firewall_service))
                .setSmallIcon(R.drawable.notification)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.notify(NOTIFICATION_ID, notification);
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Let it continue running until it is stopped.

        if (connectivityReciver != null) {
            unregisterReceiver(connectivityReciver);
        }

        if (packageInstallReceiver != null) {
            unregisterReceiver(packageInstallReceiver);
        }

        if (packageUninstallReceiver != null) {
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
