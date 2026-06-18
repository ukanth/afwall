package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ApplicationErrorLog {

    private static final String PREF_NAME = "AFWallApplicationErrors";
    private static final String KEY_LOG = "application_errors";
    private static final int MAX_CHARS = 20000;

    private ApplicationErrorLog() {
    }

    public static synchronized void add(Context context, String message) {
        if (context == null || message == null || message.trim().isEmpty()) {
            return;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String existing = prefs.getString(KEY_LOG, "");
        String updated = timestamp + " - " + message.trim() + "\n" + existing;
        if (updated.length() > MAX_CHARS) {
            updated = updated.substring(0, MAX_CHARS);
        }
        prefs.edit().putString(KEY_LOG, updated).apply();
    }

    public static synchronized String get(Context context) {
        if (context == null) {
            return "";
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOG, "");
    }

    public static synchronized void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOG)
                .apply();
    }
}
