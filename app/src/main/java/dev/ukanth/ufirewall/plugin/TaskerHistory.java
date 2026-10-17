package dev.ukanth.ufirewall.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The recent Tasker/Locale actions (run and ignored), shown in Preferences, and the rate limit.
 */
public final class TaskerHistory {

    private static final String PREFS = "tasker_history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 30;

    // at most this many actions per window; more is a loop in a task, or another app
    private static final int RATE_MAX = 10;
    private static final long RATE_WINDOW_MS = 60_000;
    private static final ArrayDeque<Long> recent = new ArrayDeque<>();

    private TaskerHistory() {
    }

    /**
     * @return false if the action has to be dropped (too many in the last minute)
     */
    static synchronized boolean underRateLimit() {
        long now = SystemClock.elapsedRealtime();
        while (!recent.isEmpty() && now - recent.peekFirst() > RATE_WINDOW_MS) {
            recent.removeFirst();
        }
        if (recent.size() >= RATE_MAX) {
            return false;
        }
        recent.addLast(now);
        return true;
    }

    /**
     * @param what   the action, as shown to the user
     * @param result what happened (done, failed, ignored: why)
     */
    static synchronized void add(Context ctx, String what, String result) {
        SharedPreferences prefs = prefs(ctx);
        JSONArray entries = read(prefs);
        JSONArray out = new JSONArray();
        try {
            out.put(new JSONObject().put("t", System.currentTimeMillis()).put("w", what).put("r", result));
            for (int i = 0; i < entries.length() && out.length() < MAX_ENTRIES; i++) {
                out.put(entries.get(i));
            }
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString(KEY_ENTRIES, out.toString()).apply();
    }

    /**
     * @return newest first, one line each
     */
    public static synchronized List<String> lines(Context ctx) {
        JSONArray entries = read(prefs(ctx));
        List<String> lines = new ArrayList<>();
        java.text.DateFormat date = DateFormat.getDateFormat(ctx);
        java.text.DateFormat time = DateFormat.getTimeFormat(ctx);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.optJSONObject(i);
            if (e == null) {
                continue;
            }
            Date when = new Date(e.optLong("t"));
            lines.add(date.format(when) + " " + time.format(when) + "\n" + e.optString("w") + ": " + e.optString("r"));
        }
        return lines;
    }

    public static synchronized void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    private static JSONArray read(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(KEY_ENTRIES, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
