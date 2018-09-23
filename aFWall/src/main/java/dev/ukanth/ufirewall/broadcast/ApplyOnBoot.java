package dev.ukanth.ufirewall.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.service.ApplyOnBootService;
import dev.ukanth.ufirewall.service.FirewallService;

public class ApplyOnBoot {


    private static final String TAG = ApplyOnBoot.class.getSimpleName();

    private static boolean sCancel;

    public static void apply(ApplyOnBootService service, final ApplyOnBootListener listener) {
        InterfaceTracker.applyRulesOnChange(service, InterfaceTracker.BOOT_COMPLETED);
        service.startService(new Intent(service, FirewallService.class));

        if (sCancel) {
            sCancel = false;
            listener.onFinish();
            return;
        }
    }

    public interface ApplyOnBootListener {
        void onFinish();
    }

    public static class CancelReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            sCancel = true;
        }

    }
}
