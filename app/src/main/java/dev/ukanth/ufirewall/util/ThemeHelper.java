package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.CompoundButtonCompat;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;

public final class ThemeHelper {

    private ThemeHelper() {
    }

    public static void apply(Activity activity) {
        try {
            applyInternal(activity);
        } catch (Exception e) {
            Log.e(G.TAG, "Unable to apply custom theme colors", e);
            ApplicationErrorLog.add(activity, "Unable to apply custom theme colors: " + e.getMessage());
            if (activity != null) {
                Api.toast(activity, activity.getText(R.string.theme_apply_error), Toast.LENGTH_LONG);
            }
        }
    }

    public static Drawable defaultAndroidIcon(Context context) {
        if (context == null) {
            return null;
        }
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_unknown);
        if (drawable == null) {
            return null;
        }
        // The package manager can return the platform Android placeholder for
        // apps without icons, so route every fallback through the themed color.
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, G.defaultIconColor(context));
        return drawable;
    }

    private static void applyInternal(Activity activity) {
        if (activity == null) {
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        applySystemBars(activity, window);

        if (!G.isCustomThemeActive(activity)) {
            return;
        }

        View content = window.getDecorView().findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) content;
            if (root.getChildCount() > 0) {
                root.getChildAt(0).setBackgroundColor(G.backgroundColor(activity));
            } else {
                root.setBackgroundColor(G.backgroundColor(activity));
            }
            applyToChildren(root, activity);
        }
        Log.i(G.TAG, "Applied custom theme colors");
    }

    private static void applySystemBars(Context context, Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Preset themes also need explicit system bar colors because some
            // activities clear or override AppCompat's default status bar handling.
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int systemBarColor = G.primaryDarkColor(context);
            window.setStatusBarColor(systemBarColor);
            window.setNavigationBarColor(systemBarColor);
        }
    }

    private static void applyToChildren(ViewGroup parent, Context context) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            applyToView(child, context);
            if (child instanceof ViewGroup) {
                applyToChildren((ViewGroup) child, context);
            }
        }
    }

    private static void applyToView(View view, Context context) {
        if (view instanceof Toolbar) {
            Toolbar toolbar = (Toolbar) view;
            toolbar.setBackgroundColor(G.primaryColor(context));
            toolbar.setTitleTextColor(G.textPrimaryColor(context));
            toolbar.setSubtitleTextColor(G.textSecondaryColor(context));
        } else if (view instanceof CardView) {
            ((CardView) view).setCardBackgroundColor(G.backgroundColor(context));
        } else if (view instanceof ListView) {
            view.setBackgroundColor(G.backgroundColor(context));
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(G.textPrimaryColor(context));
        }

        if (view instanceof CompoundButton) {
            // Custom colors live in preferences, so widgets inflated from XML need
            // explicit tinting after layout inflation.
            CompoundButtonCompat.setButtonTintList((CompoundButton) view, controlTint(context));
        }
    }

    private static ColorStateList controlTint(Context context) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        G.accentColor(context),
                        G.textSecondaryColor(context)
                });
    }
}
