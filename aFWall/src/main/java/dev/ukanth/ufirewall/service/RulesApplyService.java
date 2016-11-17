package dev.ukanth.ufirewall.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogService;
import dev.ukanth.ufirewall.util.G;

/**
 * Created by ukanth on 14/11/16.
 */

public class RulesApplyService extends IntentService {

    public RulesApplyService() {
        super(RulesApplyService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        Context context = RulesApplyService.this;
        if(Api.isEnabled(context)) {
            if(G.activeRules()) {
                Log.d(Api.TAG, "Applying rules on connectivity change");
                InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.CONNECTIVITY_CHANGE);
            }
            final Intent logIntent = new Intent(context, LogService.class);
            if (G.enableLogService()) {
                //check if the firewall is enabled
                if (!Api.isEnabled(context) || !InterfaceTracker.isNetworkUp(context)) {
                    //make sure kill all pid
                    context.stopService(logIntent);
                    Api.cleanupUid();
                } else {
                    context.startService(logIntent);
                }
            } else {
                //no internet - stop the service
                context.stopService(logIntent);
                Api.cleanupUid();
            }
        }
    }
}
