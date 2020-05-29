package dev.ukanth.ufirewall.broadcast;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Messenger;
import android.support.v4.app.NotificationCompat;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.LogService;
import dev.ukanth.ufirewall.util.G;

public class OnBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Messenger messenger = null;
            if (intent != null) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    messenger = (Messenger) extras.get("messenger");
                }
            }

            if (messenger == null) {
                PackageManager pm = context.getPackageManager();
                pm.setComponentEnabledSetting(new ComponentName(context, MainActivity.class),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }

            Log.i("AFWall", "Startin boot service");
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.i("AFWall", "Starting firewall service onboot");
                context.startForegroundService(new Intent(context, FirewallService.class));
            } else {
                context.startService(new Intent(context, FirewallService.class));
            }

            InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.BOOT_COMPLETED);

            if (G.enableLogService()) {
                Log.i("AFWall", "Starting log service onboot");
                try {
                    context.startService(new Intent(context, FirewallService.class));
                } catch (Exception e) {
                }
            }
        }

        //try applying the rule after few seconds if enabled
        if (G.startupDelay()) {
            //make sure we apply rules after 5 sec
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                // Apply the changes regards if network is up/not
                InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.BOOT_COMPLETED);
            }, G.getCustomDelay());
        }

        //check if startup script is copied
        Api.checkAndCopyFixLeak(context, "afwallstart");
    }
}
