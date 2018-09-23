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

    static final String CHANNEL_ID = "afwall_onboot_apply";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.applying_rules), NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannel);

        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

        builder.setContentTitle(getString(R.string.applying_rules))
                .setSmallIcon(R.mipmap.round_launcher_free);
        startForeground(15000, builder.build());
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


        InterfaceTracker.applyRulesOnChange(this, InterfaceTracker.BOOT_COMPLETED);
        startService(new Intent(this, FirewallService.class));


        if (G.enableLogService()) {
            //make sure we cleanup existing uid
            final Intent logIntent = new Intent(this, LogService.class);
            startService(logIntent);
            G.storedPid(new HashSet());
        }

        //try applying the rule after few seconds if enabled
        if (G.startupDelay()) {
            //make sure we apply rules after 5 sec
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                // Apply the changes regards if network is up/not
                InterfaceTracker.applyRulesOnChange(this, InterfaceTracker.BOOT_COMPLETED);
            }, G.getCustomDelay());
        }

        if (G.activeNotification()) {
            Api.showNotification(Api.isEnabled(this), this);
        }

        //check if startup script is copied
        Api.checkAndCopyFixLeak(this, "afwallstart");
        stopSelf();
        return START_NOT_STICKY;
    }

}
