package dev.ukanth.ufirewall.broadcast;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Messenger;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.LogService;
import dev.ukanth.ufirewall.util.BootRuleManager;
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

            // Use BootRuleManager for robust rule application
            BootRuleManager.initializeBootRuleApplication(context);

            //register private DNS change listener


            if (G.enableLogService()) {
                Log.i("AFWall", "Starting log service onboot");
                try {
                    context.startService(new Intent(context, LogService.class));
                } catch (Exception e) {
                }
            }

            try {
                G.registerPrivateLink();
            }catch (Exception e){

            }
        }
    }
}
