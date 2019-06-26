package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.view.Window;
import android.view.WindowManager;

import dev.ukanth.ufirewall.R;

public class ThemeUtils {

    public static void setStatusBarColor(Activity activity, String color) {
        Window window = activity.getWindow();
        int statusBarColor = Color.parseColor(color);
        if (statusBarColor == Color.BLACK && window.getNavigationBarColor() == Color.BLACK) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        window.setStatusBarColor(statusBarColor);
    }
}
