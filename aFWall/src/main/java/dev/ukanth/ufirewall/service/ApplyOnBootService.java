package dev.ukanth.ufirewall.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import java.util.HashSet;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class ApplyOnBootService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        String NOTIFICATION_CHANNEL_ID = "firewall.boot";
        String channelName = getString(R.string.boot_notification);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                   channelName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setSound(null, null);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationManager.createNotificationChannel(notificationChannel);

        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        builder.setContentTitle(getString(R.string.applying_rules))
                .setSmallIcon(R.drawable.ic_apply_notification);
        startForeground(1, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Messenger messenger = null;
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                messenger = (Messenger) extras.get("messenger");
            }
        }

        if (messenger == null) {
            PackageManager pm = getPackageManager();
            pm.setComponentEnabledSetting(new ComponentName(this, MainActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), FirewallService.class));
            if(G.enableLogService()) {
                startForegroundService(new Intent(getApplicationContext(), LogService.class));
            }
        } else {
            startService(new Intent(getApplicationContext(), FirewallService.class));
            if(G.enableLogService()) {
                startService(new Intent(getApplicationContext(), LogService.class));
            }
        }

        InterfaceTracker.applyRulesOnChange(this, InterfaceTracker.BOOT_COMPLETED);



        //try applying the rule after few seconds if enabled
        if (G.startupDelay()) {
            //make sure we apply rules after 5 sec
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                // Apply the changes regards if network is up/not
                InterfaceTracker.applyRulesOnChange(this, InterfaceTracker.BOOT_COMPLETED);
            }, G.getCustomDelay());
        }

        //check if startup script is copied
        Api.checkAndCopyFixLeak(this, "afwallstart");
        stopSelf();
        return START_NOT_STICKY;
    }

}
