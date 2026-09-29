package dev.ukanth.ufirewall.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.broadcast.NotificationActionReceiver;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.widget.WidgetActionActivity;

/**
 * All of AFWall+'s notifications: channels, ids and builders in one place.
 * <p>
 * Channel ids are unchanged from earlier versions, so the settings users made for them stay.
 */
public final class Notifications {

    public static final String CHANNEL_SERVICE = "firewall.service";
    public static final String CHANNEL_ERROR = "firewall.error";
    public static final String CHANNEL_APP = "firewall.app.notification";
    public static final String CHANNEL_LOG = "firewall.logservice";

    public static final int ID_STATUS = 1;
    public static final int ID_ERROR = 9;
    public static final int ID_SCRIPT_WARNING = 10;
    public static final int ID_LOG_WATCHER = 11;
    // one notification per new app, tagged with its package name
    private static final int ID_NEW_APP = 100;
    private static final int ID_NEW_APP_SUMMARY = 101;
    public static final int ID_BLOCKED = 109;
    private static final String GROUP_NEW_APPS = "dev.ukanth.ufirewall.NEW_APPS";

    // at most one update of the blocked-connections notification per interval
    private static final long BLOCKED_UPDATE_INTERVAL_MS = 3000;
    private static final int BLOCKED_MAX_APPS = 20;

    // outcome of the last full rule apply, for the status notification and the error details
    private static volatile boolean lastApplyFailed;
    private static volatile String lastFailure;
    private static volatile int lastExitCode;

    private Notifications() {
    }

    // ---- channels ----

    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = manager(ctx);
        if (nm == null) {
            return;
        }
        // status: silent and collapsed for new installs (it is always there)
        createChannel(nm, CHANNEL_SERVICE, ctx.getString(R.string.firewall_service),
                ctx.getString(R.string.channel_service_desc), NotificationManager.IMPORTANCE_LOW, true);
        createChannel(nm, CHANNEL_ERROR, ctx.getString(R.string.firewall_error_notify),
                ctx.getString(R.string.channel_error_desc), NotificationManager.IMPORTANCE_DEFAULT, false);
        createChannel(nm, CHANNEL_APP, ctx.getString(R.string.app_notification),
                ctx.getString(R.string.channel_app_desc), NotificationManager.IMPORTANCE_DEFAULT, false);
        createChannel(nm, CHANNEL_LOG, ctx.getString(R.string.firewall_log_notify),
                ctx.getString(R.string.channel_log_desc), NotificationManager.IMPORTANCE_DEFAULT, false);
    }

    private static void createChannel(NotificationManager nm, String id, String name, String description,
                                      int newImportance, boolean publicOnLockScreen) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        // An existing channel keeps its importance: the user may have changed it, and recreating it
        // with another value would silently change their setup.
        NotificationChannel existing = nm.getNotificationChannel(id);
        int importance = existing != null ? existing.getImportance() : newImportance;
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        channel.setSound(null, null);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(publicOnLockScreen ? Notification.VISIBILITY_PUBLIC : Notification.VISIBILITY_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            channel.setAllowBubbles(false);
        }
        nm.createNotificationChannel(channel);
    }

    // ---- status (the foreground service notification) ----

    /**
     * The status notification, as shown by FirewallService / LogService.
     */
    public static Notification buildStatus(Context ctx, boolean logMonitoring) {
        ensureChannels(ctx);
        boolean enabled = Api.isEnabled(ctx);
        String text;
        if (!enabled) {
            text = ctx.getString(R.string.inactive);
        } else if (Api.isApplyInProgress()) {
            text = ctx.getString(R.string.notif_applying);
        } else if (lastApplyFailed) {
            text = ctx.getString(R.string.notif_apply_failed);
        } else {
            text = ctx.getString(R.string.active);
            if (G.enableMultiProfile()) {
                text += " (" + ProfileHelper.displayName(ctx, G.storedProfile()) + ")";
            }
        }
        if (enabled && logMonitoring) {
            text += " • " + ctx.getString(R.string.log_monitoring);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
                .setSmallIcon(enabled && !lastApplyFailed ? R.drawable.notification : R.drawable.notification_error)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(openApp(ctx, 0))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // actions go through the same confirmation / app lock as the widgets; no Disable here, so
        // the firewall isn't one tap away from being turned off in the notification shade
        if (!enabled) {
            b.addAction(0, ctx.getString(R.string.enable), activity(ctx, 1, WidgetActionActivity.toggleIntent(ctx)));
        }
        if (enabled && G.enableMultiProfile()) {
            b.addAction(0, ctx.getString(R.string.notif_action_profile),
                    activity(ctx, 2, WidgetActionActivity.profileIntent(ctx)));
        }
        if (enabled && logMonitoring) {
            b.addAction(0, ctx.getString(R.string.show_log), activity(ctx, 3, logIntent(ctx)));
        }
        return b.build();
    }

    /**
     * Redraw the status notification. It belongs to FirewallService; if that isn't running it is
     * started (never posted without an owner: it couldn't be dismissed or kept up to date).
     */
    public static void refreshStatus(Context ctx) {
        if (ctx == null) {
            return;
        }
        if (FirewallService.isInstanceRunning()) {
            FirewallService.refreshNotification();
        } else {
            FirewallService.ensureRunning(ctx);
        }
    }

    public static void onApplyStarted(Context ctx) {
        refreshStatus(ctx);
    }

    /**
     * Record the result of a full apply before its callbacks run (they may post the error).
     */
    public static void recordApplyResult(RootCommand state) {
        lastApplyFailed = state.exitCode != 0;
        lastExitCode = state.exitCode;
        if (lastApplyFailed) {
            String result = state.lastCommandResult != null ? state.lastCommandResult.toString().trim() : "";
            String command = state.lastCommand != null ? state.lastCommand : "";
            lastFailure = (command + (result.isEmpty() ? "" : "\n" + result)).trim();
            if (lastFailure.length() > 400) {
                lastFailure = lastFailure.substring(0, 400) + "…";
            }
        } else {
            lastFailure = null;
        }
    }

    public static void onApplyFinished(Context ctx, boolean success) {
        if (ctx == null) {
            return;
        }
        if (success) {
            // the problem is gone: don't leave "error applying rules" behind
            cancel(ctx, ID_ERROR);
        }
        refreshStatus(ctx);
    }

    // ---- errors and warnings ----

    /**
     * "Error applying firewall rules", with the failing command when known.
     */
    public static void showApplyError(Context ctx) {
        String detail = lastFailure;
        if ((detail == null || detail.isEmpty()) && lastExitCode == -1) {
            // the root shell could not be opened: nothing ran
            detail = ctx.getString(R.string.notif_error_no_root);
        }
        String text = ctx.getString(R.string.error_notification_text);
        String big = detail == null || detail.isEmpty() ? text : text + "\n\n" + detail;
        show(ctx, ID_ERROR, ctx.getString(R.string.error_notification_title), text, big, null);
    }

    /**
     * A warning / error on the error channel that opens {@code target} (the app when null).
     */
    public static void show(Context ctx, int id, String title, String text, String bigText, Intent target) {
        ensureChannels(ctx);
        NotificationManager nm = manager(ctx);
        if (nm == null) {
            return;
        }
        PendingIntent content = target != null ? activity(ctx, id, target) : openApp(ctx, id);
        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ERROR)
                .setSmallIcon(R.drawable.notification_warn)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText != null ? bigText : text))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        nm.notify(id, n);
    }

    // ---- new apps ----

    /**
     * A new app with internet access was installed (in any Android user / profile).
     */
    public static void newApp(Context ctx, int uid, String pkg, String label) {
        ensureChannels(ctx);
        NotificationManager nm = manager(ctx);
        if (nm == null) {
            return;
        }
        boolean whitelist = Api.MODE_WHITELIST.equals(G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST));
        String name = label != null ? label : pkg;
        String text = ctx.getString(whitelist ? R.string.notif_new_app_whitelist : R.string.notif_new_app_blacklist);
        // distinct PendingIntent request codes per app and Android user (extras don't make them distinct)
        int requestBase = 1000 + (uid % 100000) * 16 + ((uid / 100000) % 4) * 4;

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_APP)
                .setSmallIcon(R.drawable.notification_quest)
                .setContentTitle(ctx.getString(R.string.notif_new_app_title, name))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openApp(ctx, requestBase))
                .setAutoCancel(true)
                .setGroup(GROUP_NEW_APPS)
                .addAction(0, ctx.getString(R.string.notif_allow),
                        activity(ctx, requestBase + 1, WidgetActionActivity.appRuleIntent(ctx, uid, pkg, name, true)))
                .addAction(0, ctx.getString(R.string.notif_block),
                        activity(ctx, requestBase + 2, WidgetActionActivity.appRuleIntent(ctx, uid, pkg, name, false)));
        nm.notify(newAppTag(uid, pkg), ID_NEW_APP, b.build());

        // groups several installs into one entry
        Notification summary = new NotificationCompat.Builder(ctx, CHANNEL_APP)
                .setSmallIcon(R.drawable.notification_quest)
                .setContentTitle(ctx.getString(R.string.notif_new_apps_summary))
                .setGroup(GROUP_NEW_APPS)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, ID_NEW_APP_SUMMARY))
                .build();
        nm.notify(ID_NEW_APP_SUMMARY, summary);
    }

    public static void cancelNewApp(Context ctx, int uid, String pkg) {
        NotificationManager nm = manager(ctx);
        if (nm == null) {
            return;
        }
        nm.cancel(newAppTag(uid, pkg), ID_NEW_APP);
        // a summary without any app left would stay behind
        try {
            for (android.service.notification.StatusBarNotification sbn : nm.getActiveNotifications()) {
                if (sbn.getId() == ID_NEW_APP) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        nm.cancel(ID_NEW_APP_SUMMARY);
    }

    private static String newAppTag(int uid, String pkg) {
        return pkg + ":" + (uid / 100000);
    }

    // ---- blocked connections ----

    private static final class Blocked {
        final int uid;
        final String name;
        int count;
        String last;

        Blocked(int uid, String name) {
            this.uid = uid;
            this.name = name;
        }
    }

    // most recent last; guarded by the class lock
    private static final LinkedHashMap<Integer, Blocked> blocked = new LinkedHashMap<>();
    private static long lastBlockedPost;
    private static boolean blockedPostScheduled;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * A connection was blocked. Collected into one notification that is updated at most every
     * few seconds (posting per packet runs into Android's rate limit, which then drops the app's
     * other notifications too).
     */
    public static void blocked(Context ctx, LogInfo info) {
        final Context app = ctx.getApplicationContext();
        synchronized (Notifications.class) {
            Blocked b = blocked.remove(info.uid);
            if (b == null) {
                String name = info.appName != null && !info.appName.isEmpty() ? info.appName : info.uidString;
                b = new Blocked(info.uid, name != null ? name : String.valueOf(info.uid));
            }
            b.count++;
            if (info.dst != null) {
                b.last = info.dst + (info.dpt > 0 ? ":" + info.dpt : "")
                        + (info.proto != null ? " " + info.proto.toUpperCase(java.util.Locale.US) : "");
            }
            blocked.put(info.uid, b); // move to the end: most recent
            while (blocked.size() > BLOCKED_MAX_APPS) {
                Iterator<Integer> it = blocked.keySet().iterator();
                it.next();
                it.remove();
            }
            if (blockedPostScheduled) {
                return;
            }
            long wait = BLOCKED_UPDATE_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastBlockedPost);
            blockedPostScheduled = true;
            handler.postDelayed(() -> postBlocked(app), Math.max(0, wait));
        }
    }

    private static void postBlocked(Context ctx) {
        List<Blocked> apps;
        int total = 0;
        synchronized (Notifications.class) {
            blockedPostScheduled = false;
            lastBlockedPost = SystemClock.elapsedRealtime();
            if (blocked.isEmpty()) {
                return;
            }
            apps = new ArrayList<>();
            for (Blocked b : blocked.values()) {
                Blocked copy = new Blocked(b.uid, b.name);
                copy.count = b.count;
                copy.last = b.last;
                apps.add(copy);
                total += b.count;
            }
        }
        ensureChannels(ctx);
        NotificationManager nm = manager(ctx);
        if (nm == null) {
            return;
        }
        Blocked latest = apps.get(apps.size() - 1);
        String title = ctx.getResources().getQuantityString(R.plurals.notif_blocked_title, total, total);
        String text = line(latest);
        NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
        for (int i = apps.size() - 1; i >= 0 && i >= apps.size() - 6; i--) {
            inbox.addLine(line(apps.get(i)));
        }
        if (apps.size() > 6) {
            inbox.setSummaryText(ctx.getString(R.string.notif_blocked_more, apps.size() - 6));
        }
        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_LOG)
                .setSmallIcon(R.drawable.ic_block_black_24dp)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(inbox)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(activity(ctx, ID_BLOCKED, logIntent(ctx)))
                .setDeleteIntent(NotificationActionReceiver.clearBlockedIntent(ctx))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setNumber(total)
                .addAction(0, ctx.getString(R.string.notif_mute, latest.name),
                        NotificationActionReceiver.muteIntent(ctx, latest.uid))
                .build();
        nm.notify(ID_BLOCKED, n);
    }

    private static String line(Blocked b) {
        return b.name + (b.count > 1 ? " (" + b.count + ")" : "") + (b.last != null ? " → " + b.last : "");
    }

    /**
     * Forget the collected blocked connections (the notification was dismissed or opened).
     */
    public static void clearBlocked() {
        synchronized (Notifications.class) {
            blocked.clear();
        }
    }

    /**
     * An app's blocked connections are no longer notified: drop it from the notification.
     */
    public static void forgetBlocked(Context ctx, int uid) {
        boolean empty;
        synchronized (Notifications.class) {
            blocked.remove(uid);
            empty = blocked.isEmpty();
        }
        if (empty) {
            cancel(ctx, ID_BLOCKED);
        } else {
            postBlocked(ctx.getApplicationContext());
        }
    }

    // ---- helpers ----

    public static void cancel(Context ctx, int id) {
        NotificationManager nm = manager(ctx);
        if (nm != null) {
            nm.cancel(id);
        }
    }

    private static NotificationManager manager(Context ctx) {
        return (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static PendingIntent openApp(Context ctx, int requestCode) {
        Intent intent = new Intent(ctx, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return activity(ctx, requestCode, intent);
    }

    private static Intent logIntent(Context ctx) {
        return new Intent(ctx, LogActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private static PendingIntent activity(Context ctx, int requestCode, Intent intent) {
        return PendingIntent.getActivity(ctx, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
