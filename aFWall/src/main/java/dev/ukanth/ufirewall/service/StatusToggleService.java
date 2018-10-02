package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;

@RequiresApi(api = Build.VERSION_CODES.N)
public class StatusToggleService extends TileService {
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();

        final SharedPreferences prefs = getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0);
        final boolean enabled = !prefs.getBoolean(Api.PREF_ENABLED, true);

        Tile tile = getQsTile(); // this is getQsTile() method form java, used in Kotlin as a property
        tile.setLabel(enabled + "");
        if (!enabled) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.notification_error));
            tile.setState(Tile.STATE_INACTIVE);
        } else {
            tile.setIcon(Icon.createWithResource(this, R.drawable.notification));
            tile.setState(Tile.STATE_ACTIVE);
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        Context context = this;
        //Start main activity
        final SharedPreferences prefs = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0);
        final boolean enabled = !prefs.getBoolean(Api.PREF_ENABLED, true);

        if (enabled) {
            Api.applySavedIptablesRules(context, true, new RootCommand()
                    .setSuccessToast(R.string.toast_enabled)
                    .setFailureToast(R.string.toast_error_enabling)
                    .setReopenShell(true)
                    .setCallback(new RootCommand.Callback() {
                        public void cbFunc(RootCommand state) {
                            // setEnabled always sends us a STATUS_CHANGED_MSG intent to update the icon
                            Api.setEnabled(context, state.exitCode == 0, true);

                            Tile tile = getQsTile(); // this is getQsTile() method form java, used in Kotlin as a property
                            tile.setState(Tile.STATE_ACTIVE);
                            tile.setLabel(true + "");
                            tile.setIcon(Icon.createWithResource(context, R.drawable.notification));
                        }
                    }));
        } else {
            Api.purgeIptables(context, true, new RootCommand()
                    .setSuccessToast(R.string.toast_disabled)
                    .setFailureToast(R.string.toast_error_disabling)
                    .setReopenShell(true)
                    .setCallback(new RootCommand.Callback() {
                        public void cbFunc(RootCommand state) {
                            Api.setEnabled(context, state.exitCode != 0, true);
                            Tile tile = getQsTile(); // this is getQsTil
                            tile.setState(Tile.STATE_INACTIVE);// e() method form java, used in Kotlin as a property
                            tile.setLabel(false + "");
                            tile.setIcon(Icon.createWithResource(context, R.drawable.notification_error));
                        }
                    }));
        }
    }
}
