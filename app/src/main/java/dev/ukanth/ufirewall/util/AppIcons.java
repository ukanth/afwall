package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.LruCache;
import android.view.View;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;

/**
 * App icons by UID for lists that only know the UID (the log). Loaded off the main thread and
 * cached; apps of a work profile / Private Space get the profile badge.
 */
public final class AppIcons {

    private static final int PER_USER_RANGE = 100000;
    private static final LruCache<Integer, Drawable> cache = new LruCache<>(150);
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private AppIcons() {
    }

    /**
     * Show the icon of {@code uid} as the background of {@code view}: the default icon at once,
     * the app's icon when loaded (unless the view shows another UID by then).
     */
    public static void showAsBackground(Context ctx, int uid, View view) {
        final Context app = ctx.getApplicationContext();
        view.setTag(R.id.app_icon, uid);
        Drawable special = special(ctx, uid);
        if (special != null) {
            view.setBackground(special);
            return;
        }
        Drawable cached = cache.get(uid);
        if (cached != null) {
            view.setBackground(copy(app, cached));
            return;
        }
        view.setBackground(ThemeHelper.defaultAndroidIcon(ctx));
        executor.execute(() -> {
            Drawable loaded = load(app, uid);
            if (loaded == null && uid > 0 && uid < android.os.Process.FIRST_APPLICATION_UID) {
                loaded = systemService(app); // a system UID without an app icon (dns, network stack, ...)
            }
            if (loaded == null) {
                return;
            }
            final Drawable icon = loaded;
            cache.put(uid, icon);
            main.post(() -> {
                Object shown = view.getTag(R.id.app_icon);
                if (shown instanceof Integer && (Integer) shown == uid) {
                    view.setBackground(copy(app, icon));
                }
            });
        });
    }

    /**
     * @return the icon of a special entry of the app list (kernel, root, tethering, ...), tinted
     * like the default icon; null if {@code uid} isn't one
     */
    public static Drawable special(Context ctx, int uid) {
        int res;
        switch (uid) {
            case Api.SPECIAL_UID_ANY: res = R.drawable.ic_special_any; break;
            case Api.SPECIAL_UID_KERNEL: res = R.drawable.ic_special_kernel; break;
            case Api.SPECIAL_UID_TETHER: res = R.drawable.ic_tether; break;
            case Api.SPECIAL_UID_NTP: res = R.drawable.ic_special_time; break;
            case 0: // root
            case 1011: // adb
            case 2000: // shell
                res = R.drawable.ic_special_code; break;
            case 1013: res = R.drawable.ic_special_media; break;
            case 1016: res = R.drawable.ic_vpn; break;
            case 1019: res = R.drawable.ic_special_lock; break;
            case 1021: res = R.drawable.ic_special_location; break;
            default:
                return null;
        }
        return tinted(ctx, res);
    }

    /**
     * @return the icon of an Android service without an app (dns, mdnsr, clat, manufacturer
     * services, ...)
     */
    public static Drawable systemService(Context ctx) {
        return tinted(ctx, R.drawable.ic_special_service);
    }

    private static Drawable tinted(Context ctx, int res) {
        Drawable d = ContextCompat.getDrawable(ctx, res);
        if (d == null) {
            return null;
        }
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, G.defaultIconColor(ctx));
        return d;
    }

    /**
     * Run {@code load} off the main thread, then {@code done} on it.
     */
    public static void load(Runnable load, Runnable done) {
        executor.execute(() -> {
            try {
                load.run();
            } catch (Exception e) {
                return;
            }
            main.post(done);
        });
    }

    /**
     * Add the work profile / Private Space badge for an app of another user.
     */
    public static Drawable badge(PackageManager pm, Drawable icon, int uid) {
        if (icon == null || uid < PER_USER_RANGE || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return icon;
        }
        try {
            return pm.getUserBadgedIcon(icon, UserHandle.getUserHandleForUid(uid));
        } catch (Exception e) {
            return icon;
        }
    }

    /**
     * @return the app icon of {@code uid}, null if it has none (special entries, unknown UIDs)
     */
    private static Drawable load(Context ctx, int uid) {
        if (uid < 0) {
            return null;
        }
        PackageManager pm = ctx.getPackageManager();
        ApplicationInfo info = findApp(pm, uid);
        if (info == null && uid >= PER_USER_RANGE) {
            // an app of another user not in the list: the same app of the main user
            info = findApp(pm, uid % PER_USER_RANGE);
        }
        if (info == null || info.icon == 0) {
            return null;
        }
        try {
            return badge(pm, pm.getApplicationIcon(info), uid);
        } catch (Exception e) {
            return null;
        }
    }

    private static ApplicationInfo findApp(PackageManager pm, int uid) {
        // the app list knows the apps PackageManager doesn't show us (read from their APK)
        List<Api.PackageInfoData> apps = Api.applications;
        if (apps != null) {
            try {
                for (Api.PackageInfoData data : apps) {
                    if (data.uid == uid && data.appinfo != null && data.appinfo.icon != 0) {
                        return data.appinfo;
                    }
                }
            } catch (RuntimeException ignored) {
                // the list changed meanwhile: PackageManager below
            }
        }
        try {
            String[] pkgs = pm.getPackagesForUid(uid);
            if (pkgs != null && pkgs.length > 0) {
                return pm.getApplicationInfo(pkgs[0], 0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Drawable copy(Context ctx, Drawable icon) {
        // one drawable must not be shown by two views
        Drawable.ConstantState state = icon.getConstantState();
        return state != null ? state.newDrawable(ctx.getResources()) : icon;
    }
}
