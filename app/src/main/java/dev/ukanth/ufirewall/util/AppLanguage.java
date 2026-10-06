package dev.ukanth.ufirewall.util;

import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * The app language, kept by AndroidX's per-app language support: the system stores it on
 * Android 13+ (where it also shows under Settings › Apps › AFWall+ › Language), AppCompat on older
 * versions. It applies to every AppCompat screen and recreates open screens when it changes.
 * <p>
 * The "locale" preference only backs the list in Preferences. Its values are language tags
 * ("pt-BR"), or "sys" for the system language.
 */
public final class AppLanguage {

    public static final String PREF_KEY = "locale";
    public static final String SYSTEM = "sys";
    // set once the language chosen with an older version has been handed to AppCompat
    private static final String PREF_MIGRATED = "localeMigrated";

    private AppLanguage() {
    }

    /**
     * @return the language tag for a list value, also for the values older versions stored
     * ("pt_BR", "zh_CN", "in"); "" for the system language
     */
    static String toTag(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.isEmpty() || SYSTEM.equals(v)) {
            return "";
        }
        v = v.replace('_', '-');
        // Indonesian: Android's old code, the list uses the standard one
        if (v.equals("in") || v.startsWith("in-")) {
            v = "id" + v.substring(2);
        }
        return v;
    }

    /** Switch the app to the language of a list value ("sys": the system language). */
    public static void apply(String value) {
        String tag = toTag(value);
        AppCompatDelegate.setApplicationLocales(tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(tag));
    }

    /** @return the list value of the current app language ("sys" if it follows the system) */
    public static String current() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty() || locales.get(0) == null) {
            return SYSTEM;
        }
        return locales.get(0).toLanguageTag();
    }

    /** @return the locale the app's text is in, e.g. for relative times in the log */
    public static Locale locale() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        Locale locale = locales.isEmpty() ? null : locales.get(0);
        return locale != null ? locale : Locale.getDefault();
    }

    /**
     * Older versions kept the language only in the preference (and applied it themselves, to some
     * screens). Hand it to AppCompat once. Never set means the system language now (it used to
     * mean English). Call after the activity's onCreate.
     */
    public static void migrate(SharedPreferences prefs) {
        if (prefs.getBoolean(PREF_MIGRATED, false)) {
            return;
        }
        String old = prefs.getString(PREF_KEY, null);
        if (old != null && !toTag(old).isEmpty() && AppCompatDelegate.getApplicationLocales().isEmpty()) {
            apply(old);
        }
        SharedPreferences.Editor edit = prefs.edit().putBoolean(PREF_MIGRATED, true);
        if (old != null) {
            edit.putString(PREF_KEY, old.trim().isEmpty() || SYSTEM.equals(old) ? SYSTEM : toTag(old));
        }
        edit.apply();
    }
}
