package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import android.preference.PreferenceManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import dev.ukanth.ufirewall.InterfaceTracker;

/**
 * Blocked-connection counts kept for weeks, for the log dashboard (backlog C8). The log itself
 * (LogData) is trimmed to a couple of hours, so it can't show a day or a week.
 * <p>
 * Counts per (time bucket, app, destination, port, protocol, network, reason): hourly for the last
 * {@value #HOURLY_DAYS} days, then folded into daily rows, kept for the "Log history" setting
 * (30 days by default). About 0.7 MB for 30 days of a very busy log; a separate database so the
 * log's own isn't touched.
 */
public final class LogHistory extends SQLiteOpenHelper {

    private static final String TAG = "AFWall.LogHistory";
    private static final String DB_NAME = "LogHistory.db";
    private static final int DB_VERSION = 1;

    public static final String PREF_DAYS = "logHistoryDays";
    public static final int DEFAULT_DAYS = 30;
    // hourly rows for this long, daily rows after that
    static final int HOURLY_DAYS = 7;
    // hard limit, so an extreme or broken device can't grow the history without bound
    static final int MAX_ROWS = 200_000;
    private static final long MAINTENANCE_INTERVAL_MS = 12L * 60 * 60 * 1000;
    private static final String PREF_LAST_MAINTENANCE = "logHistoryMaintained";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    // destination ids looked up recently; bounded, the history has a few thousand at most
    private static final int DEST_CACHE_MAX = 2048;

    private static final String KEY_COLUMNS = "start, uid, dest, dpt, proto, net, reason";
    private static final String KEY_MATCH =
            "start = ? AND uid = ? AND dest = ? AND dpt = ? AND proto = ? AND net = ? AND reason = ?";

    private static volatile LogHistory instance;

    private final Context context;
    private final Map<String, Long> destCache = new HashMap<>();

    private LogHistory(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
    }

    public static LogHistory get(Context ctx) {
        if (instance == null) {
            synchronized (LogHistory.class) {
                if (instance == null) {
                    instance = new LogHistory(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE dest (id INTEGER PRIMARY KEY, dst TEXT NOT NULL UNIQUE)");
        for (String table : new String[]{"hourly", "daily"}) {
            // start: epoch minutes of the local hour / day (LogHistoryBuckets)
            db.execSQL("CREATE TABLE " + table + " (start INTEGER NOT NULL, uid INTEGER NOT NULL,"
                    + " dest INTEGER NOT NULL, dpt INTEGER NOT NULL, proto INTEGER NOT NULL,"
                    + " net INTEGER NOT NULL, reason INTEGER NOT NULL, count INTEGER NOT NULL,"
                    + " PRIMARY KEY (" + KEY_COLUMNS + ")) WITHOUT ROWID");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // first version
    }

    /** @return days of history to keep; 0: history off */
    public int days() {
        try {
            return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PREF_DAYS, String.valueOf(DEFAULT_DAYS)));
        } catch (NumberFormatException e) {
            return DEFAULT_DAYS;
        }
    }

    /**
     * Count a batch of new log entries (the log service's batch, on its thread).
     */
    public synchronized void record(List<LogData> batch) {
        if (batch == null || batch.isEmpty() || days() <= 0) {
            return;
        }
        TimeZone tz = TimeZone.getDefault();
        // one row per key in the batch: a batch is mostly the same few apps and destinations
        Map<Key, Integer> counts = new LinkedHashMap<>();
        for (LogData d : batch) {
            String iface = d.getOut() != null && !d.getOut().isEmpty() ? d.getOut() : d.getIn();
            Key key = new Key(LogHistoryBuckets.hourStart(d.getTimestamp(), tz), d.getUid(),
                    d.getDst() != null ? d.getDst() : "", d.getDpt(), LogHistoryBuckets.protoCode(d.getProto()),
                    LogHistoryBuckets.networkCode(iface, InterfaceTracker.ITFS_WIFI, InterfaceTracker.ITFS_3G,
                            InterfaceTracker.ITFS_VPN, InterfaceTracker.ITFS_TETHER),
                    LogHistoryBuckets.REASON_UNKNOWN);
            Integer n = counts.get(key);
            counts.put(key, n == null ? 1 : n + 1);
        }
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                Adder hourly = new Adder(db, "hourly");
                for (Map.Entry<Key, Integer> e : counts.entrySet()) {
                    Key k = e.getKey();
                    hourly.add(k.start, k.uid, destId(db, k.dst), k.dpt, k.proto, k.net, k.reason, e.getValue());
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to record log history", e);
            return;
        }
        maintainIfDue();
    }

    private void maintainIfDue() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(PREF_LAST_MAINTENANCE, 0);
        // also after the clock went back
        if (now - last >= MAINTENANCE_INTERVAL_MS || now < last) {
            maintain(now);
            prefs.edit().putLong(PREF_LAST_MAINTENANCE, now).apply();
        }
    }

    /**
     * Fold hourly rows older than {@value #HOURLY_DAYS} days into daily rows, drop what is older
     * than the history setting, keep under {@value #MAX_ROWS} rows, drop unused destinations.
     */
    public synchronized void maintain(long nowMillis) {
        int days = days();
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (days <= 0) {
                clearTables(db);
                return;
            }
            TimeZone tz = TimeZone.getDefault();
            long hourlyCutoff = LogHistoryBuckets.dayStart(nowMillis - Math.min(days, HOURLY_DAYS) * DAY_MS, tz);
            long dailyCutoff = LogHistoryBuckets.dayStart(nowMillis - days * DAY_MS, tz);
            db.beginTransaction();
            try {
                if (days > HOURLY_DAYS) {
                    foldIntoDaily(db, hourlyCutoff, tz);
                }
                db.delete("hourly", "start < ?", new String[]{String.valueOf(hourlyCutoff)});
                db.delete("daily", "start < ?", new String[]{String.valueOf(dailyCutoff)});
                enforceCap(db);
                db.execSQL("DELETE FROM dest WHERE id NOT IN (SELECT dest FROM hourly)"
                        + " AND id NOT IN (SELECT dest FROM daily)");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                destCache.clear();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to maintain log history", e);
        }
    }

    private void foldIntoDaily(SQLiteDatabase db, long hourlyCutoff, TimeZone tz) {
        Map<Key, Integer> days = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery("SELECT " + KEY_COLUMNS + ", count FROM hourly WHERE start < ?",
                new String[]{String.valueOf(hourlyCutoff)})) {
            while (c.moveToNext()) {
                Key k = new Key(LogHistoryBuckets.dayStart(c.getLong(0) * 60000L, tz), c.getInt(1),
                        String.valueOf(c.getLong(2)), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6));
                Integer n = days.get(k);
                days.put(k, (n == null ? 0 : n) + c.getInt(7));
            }
        }
        Adder daily = new Adder(db, "daily");
        for (Map.Entry<Key, Integer> e : days.entrySet()) {
            Key k = e.getKey();
            // the destination is already an id here
            daily.add(k.start, k.uid, Long.parseLong(k.dst), k.dpt, k.proto, k.net, k.reason, e.getValue());
        }
    }

    /** Over the limit: drop the oldest days, then the oldest hours. */
    private void enforceCap(SQLiteDatabase db) {
        long rows = count(db, "hourly") + count(db, "daily");
        for (String table : new String[]{"daily", "hourly"}) {
            while (rows > MAX_ROWS) {
                long oldest;
                try (Cursor c = db.rawQuery("SELECT MIN(start) FROM " + table, null)) {
                    if (!c.moveToFirst() || c.isNull(0)) {
                        break;
                    }
                    oldest = c.getLong(0);
                }
                rows -= db.delete(table, "start = ?", new String[]{String.valueOf(oldest)});
            }
        }
    }

    private static long count(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    private long destId(SQLiteDatabase db, String dst) {
        Long id = destCache.get(dst);
        if (id != null) {
            return id;
        }
        try (Cursor c = db.rawQuery("SELECT id FROM dest WHERE dst = ?", new String[]{dst})) {
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
        }
        if (id == null) {
            SQLiteStatement insert = db.compileStatement("INSERT INTO dest (dst) VALUES (?)");
            insert.bindString(1, dst);
            id = insert.executeInsert();
        }
        if (destCache.size() >= DEST_CACHE_MAX) {
            destCache.clear();
        }
        destCache.put(dst, id);
        return id;
    }

    /** Delete all history ("Clear log", or the setting turned off). */
    public synchronized void clear() {
        try {
            clearTables(getWritableDatabase());
        } catch (Exception e) {
            Log.e(TAG, "Unable to clear log history", e);
        }
    }

    /** Delete the history of one app (its log was cleared, or it was uninstalled). */
    public synchronized void clearUid(int uid) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            String[] args = {String.valueOf(uid)};
            db.beginTransaction();
            try {
                db.delete("hourly", "uid = ?", args);
                db.delete("daily", "uid = ?", args);
                db.execSQL("DELETE FROM dest WHERE id NOT IN (SELECT dest FROM hourly)"
                        + " AND id NOT IN (SELECT dest FROM daily)");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                destCache.clear();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to clear log history of uid " + uid, e);
        }
    }

    private void clearTables(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.delete("hourly", null, null);
            db.delete("daily", null, null);
            db.delete("dest", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            destCache.clear();
        }
    }

    /** Adds to a row's count, creating the row if it doesn't exist (no UPSERT before Android 11). */
    private static final class Adder {
        private final SQLiteStatement update;
        private final SQLiteStatement insert;

        Adder(SQLiteDatabase db, String table) {
            update = db.compileStatement("UPDATE " + table + " SET count = count + ? WHERE " + KEY_MATCH);
            insert = db.compileStatement("INSERT INTO " + table + " (" + KEY_COLUMNS + ", count)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        }

        void add(long start, int uid, long dest, int dpt, int proto, int net, int reason, int n) {
            update.clearBindings();
            update.bindLong(1, n);
            bindKey(update, 2, start, uid, dest, dpt, proto, net, reason);
            if (update.executeUpdateDelete() == 0) {
                insert.clearBindings();
                bindKey(insert, 1, start, uid, dest, dpt, proto, net, reason);
                insert.bindLong(8, n);
                insert.executeInsert();
            }
        }

        private static void bindKey(SQLiteStatement s, int first, long start, int uid, long dest, int dpt,
                                    int proto, int net, int reason) {
            s.bindLong(first, start);
            s.bindLong(first + 1, uid);
            s.bindLong(first + 2, dest);
            s.bindLong(first + 3, dpt);
            s.bindLong(first + 4, proto);
            s.bindLong(first + 5, net);
            s.bindLong(first + 6, reason);
        }
    }

    private static final class Key {
        final long start;
        final int uid;
        final String dst;
        final int dpt;
        final int proto;
        final int net;
        final int reason;

        Key(long start, int uid, String dst, int dpt, int proto, int net, int reason) {
            this.start = start;
            this.uid = uid;
            this.dst = dst;
            this.dpt = dpt;
            this.proto = proto;
            this.net = net;
            this.reason = reason;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return start == k.start && uid == k.uid && dpt == k.dpt && proto == k.proto && net == k.net
                    && reason == k.reason && dst.equals(k.dst);
        }

        @Override
        public int hashCode() {
            int h = (int) (start ^ (start >>> 32));
            h = 31 * h + uid;
            h = 31 * h + dst.hashCode();
            h = 31 * h + dpt;
            h = 31 * h + proto;
            h = 31 * h + net;
            return 31 * h + reason;
        }
    }
}
