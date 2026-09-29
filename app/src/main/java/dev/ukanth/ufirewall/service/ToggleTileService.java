package dev.ukanth.ufirewall.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.FirewallActions;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.widget.WidgetActionActivity;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ToggleTileService extends TileService {

    /**
     * Ask the system to redraw the tile (it calls onStartListening) after the state changed.
     */
    public static void requestRefresh(Context ctx) {
        try {
            requestListeningState(ctx, new ComponentName(ctx, ToggleTileService.class));
        } catch (Exception e) {
            Log.w(G.TAG, "Unable to refresh the quick settings tile: " + e.getMessage());
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        showState(Api.isEnabled(this));
    }

    private void showState(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (enabled) {
            tile.setLabel(getString(R.string.active));
            tile.setIcon(Icon.createWithResource(this, R.drawable.notification));
            tile.setState(Tile.STATE_ACTIVE);
        } else {
            tile.setLabel(getString(R.string.inactive));
            tile.setIcon(Icon.createWithResource(this, R.drawable.notification_error));
            tile.setState(Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        // quick settings are reachable on the lock screen: don't change the firewall there
        if (isLocked()) {
            unlockAndRun(this::toggle);
        } else {
            toggle();
        }
    }

    private void toggle() {
        final boolean enable = !Api.isEnabled(this);
        boolean mustAsk = (!enable && G.enableConfirm()) || isAppLocked();
        if (mustAsk) {
            // the app lock / confirmation need a screen
            launch(WidgetActionActivity.toggleIntent(this));
            return;
        }
        FirewallActions.setEnabled(this, enable, true, ok -> {
            // the tile may no longer be listening: have it redrawn from the real state
            requestRefresh(getApplicationContext());
        });
    }

    private boolean isAppLocked() {
        if (G.enableDeviceCheck()) {
            return true;
        }
        switch (G.protectionLevel()) {
            case "p1":
                return G.profile_pwd().length() > 0;
            case "p2":
                return G.sPrefs.getString("LockPassword", "").length() > 0;
            case "p3":
                return G.isFingerprintEnabled();
            default:
                return false;
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void launch(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
