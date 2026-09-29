package dev.ukanth.ufirewall.broadcast;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Notifications;

/**
 * Actions of the blocked-connections notification that need no screen: mute an app's
 * notifications, forget the collected entries when the notification is dismissed.
 * Not exported: only reached through our own PendingIntents.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String ACTION_MUTE = "dev.ukanth.ufirewall.notification.MUTE_APP";
    private static final String ACTION_CLEAR_BLOCKED = "dev.ukanth.ufirewall.notification.CLEAR_BLOCKED";
    private static final String EXTRA_UID = "uid";

    public static PendingIntent muteIntent(Context ctx, int uid) {
        Intent intent = new Intent(ctx, NotificationActionReceiver.class)
                .setAction(ACTION_MUTE)
                .putExtra(EXTRA_UID, uid);
        return PendingIntent.getBroadcast(ctx, 1, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent clearBlockedIntent(Context ctx) {
        Intent intent = new Intent(ctx, NotificationActionReceiver.class).setAction(ACTION_CLEAR_BLOCKED);
        return PendingIntent.getBroadcast(ctx, 2, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_MUTE.equals(intent.getAction()) && intent.hasExtra(EXTRA_UID)) {
            int uid = intent.getIntExtra(EXTRA_UID, Integer.MIN_VALUE);
            // same as unchecking "notifications" for the app in its details
            G.updateLogNotification(uid, true);
            Notifications.forgetBlocked(context, uid);
        } else if (ACTION_CLEAR_BLOCKED.equals(intent.getAction())) {
            Notifications.clearBlocked();
        }
    }
}
