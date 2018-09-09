package dev.ukanth.ufirewall.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import dev.ukanth.ufirewall.service.ApplyOnBootService;

public class OnBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context, ApplyOnBootService.class));
            } else {
                context.startService(new Intent(context, ApplyOnBootService.class));
            }
        }
    }
}
