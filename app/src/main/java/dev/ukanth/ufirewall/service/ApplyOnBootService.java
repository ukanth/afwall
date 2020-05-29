package dev.ukanth.ufirewall.service;

import android.app.Notification;
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
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

/*public class ApplyOnBootService extends Service {

    @Nullable
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

        Log.i("AFWall", "Startin boot service");
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i("AFWall", "Starting firewall service onboot");
            getApplicationContext().startForegroundService(new Intent(getApplicationContext(), FirewallService.class));
           if(G.enableLogService()) {
                Log.i("AFWall", "Starting log service onboot");
                getApplicationContext().startForegroundService(new Intent(getApplicationContext(), LogService.class));
            }
        } else {
            getApplicationContext().startService(new Intent(getApplicationContext(), FirewallService.class));
            if(G.enableLogService()) {
                getApplicationContext().startService(new Intent(getApplicationContext(), LogService.class));
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
        return START_NOT_STICKY;
    }

}*/
