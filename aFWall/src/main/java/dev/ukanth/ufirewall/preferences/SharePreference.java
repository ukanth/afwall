package dev.ukanth.ufirewall.preferences;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Created by ukanth on 19/7/16.
 */
public class SharePreference implements SharedPreferences {

    private final Context mContext;
    private final Handler mHandler;
    private final Uri mBaseUri;
    private final WeakHashMap<OnSharedPreferenceChangeListener, PreferenceContentObserver> mListeners;

    /**
     * Initializes a new remote preferences object.
     * You must use the same authority as the preference provider.
     * Note that if you pass invalid parameter values, the
     * constructor will complete successfully, but data accesses
     * will either throw {@link IllegalArgumentException} or return
     * default values.
     *
     * @param context Used to access the preference provider.
     * @param authority The authority of the preference provider.
     * @param prefName The name of the preference file to access.
     */
    public SharePreference(Context context, String authority, String prefName) {
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
        mBaseUri = Uri.parse("content://" + authority).buildUpon().appendPath(prefName).build();
        mListeners = new WeakHashMap<OnSharedPreferenceChangeListener, PreferenceContentObserver>();
    }

    @Override
    public Map<String, ?> getAll() {
        return queryAll();
    }

    @Override
    public String getString(String key, String defValue) {
        return (String)querySingle(key, defValue, ShareContract.TYPE_STRING);
    }

    @Override
    @TargetApi(11)
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return ShareUtils.toStringSet(querySingle(key, defValues, ShareContract.TYPE_STRING_SET));
    }

    @Override
    public int getInt(String key, int defValue) {
        return (Integer)querySingle(key, defValue, ShareContract.TYPE_INT);
    }

    @Override
    public long getLong(String key, long defValue) {
        return (Long)querySingle(key, defValue, ShareContract.TYPE_LONG);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return (Float)querySingle(key, defValue, ShareContract.TYPE_FLOAT);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return (Boolean)querySingle(key, defValue, ShareContract.TYPE_BOOLEAN);
    }

    @Override
    public boolean contains(String key) {
        return containsKey(key);
    }

    @Override
    public Editor edit() {
        return new RemotePreferencesEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (mListeners.containsKey(listener)) return;
        PreferenceContentObserver observer = new PreferenceContentObserver(listener);
        mListeners.put(listener, observer);
        mContext.getContentResolver().registerContentObserver(mBaseUri, true, observer);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        PreferenceContentObserver observer = mListeners.remove(listener);
        if (observer != null) {
            mContext.getContentResolver().unregisterContentObserver(observer);
        }
    }

    private Object querySingle(String key, Object defValue, int expectedType) {
        Uri uri = mBaseUri.buildUpon().appendPath(key).build();
        String[] columns = {ShareContract.COLUMN_TYPE, ShareContract.COLUMN_VALUE};
        Cursor cursor = mContext.getContentResolver().query(uri, columns, null, null, null);
        try {
            if (cursor == null || !cursor.moveToFirst() || cursor.getInt(0) == ShareContract.TYPE_NULL) {
                return defValue;
            } else if (cursor.getInt(0) != expectedType) {
                throw new ClassCastException("Preference type mismatch");
            } else {
                return getValue(cursor, 0, 1);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Map<String, Object> queryAll() {
        Uri uri = mBaseUri.buildUpon().appendPath("").build();
        String[] columns = {ShareContract.COLUMN_KEY, ShareContract.COLUMN_TYPE, ShareContract.COLUMN_VALUE};
        Cursor cursor = mContext.getContentResolver().query(uri, columns, null, null, null);
        try {
            HashMap<String, Object> map = new HashMap<String, Object>(0);
            if (cursor == null) {
                return map;
            }
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                map.put(name, getValue(cursor, 1, 2));
            }
            return map;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean containsKey(String key) {
        Uri uri = mBaseUri.buildUpon().appendPath(key).build();
        String[] columns = {ShareContract.COLUMN_TYPE};
        Cursor cursor = mContext.getContentResolver().query(uri, columns, null, null, null);
        try {
            return (cursor != null && cursor.moveToFirst() && cursor.getInt(0) != ShareContract.TYPE_NULL);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private Object getValue(Cursor cursor, int typeCol, int valueCol) {
        int expectedType = cursor.getInt(typeCol);
        switch (expectedType) {
            case ShareContract.TYPE_STRING:
                return cursor.getString(valueCol);
            case ShareContract.TYPE_STRING_SET:
                return ShareUtils.deserializeStringSet(cursor.getString(valueCol));
            case ShareContract.TYPE_INT:
                return cursor.getInt(valueCol);
            case ShareContract.TYPE_LONG:
                return cursor.getLong(valueCol);
            case ShareContract.TYPE_FLOAT:
                return cursor.getFloat(valueCol);
            case ShareContract.TYPE_BOOLEAN:
                return cursor.getInt(valueCol) != 0;
            default:
                throw new AssertionError("Invalid expected type: " + expectedType);
        }
    }

    private class RemotePreferencesEditor implements Editor {
        private final List<ContentValues> mToAdd = new ArrayList<ContentValues>();
        private final Set<String> mToRemove = new HashSet<String>();

        private ContentValues add(String key, int type) {
            ContentValues values = new ContentValues(3);
            values.put(ShareContract.COLUMN_KEY, key);
            values.put(ShareContract.COLUMN_TYPE, type);
            mToAdd.add(values);
            return values;
        }

        @Override
        public Editor putString(String key, String value) {
            add(key, ShareContract.TYPE_STRING)
                    .put(ShareContract.COLUMN_VALUE, value);
            return this;
        }

        @Override
        @TargetApi(11)
        public Editor putStringSet(String key, Set<String> value) {
            add(key, ShareContract.TYPE_STRING_SET)
                    .put(ShareContract.COLUMN_VALUE, ShareUtils.serializeStringSet(value));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            add(key, ShareContract.TYPE_INT)
                    .put(ShareContract.COLUMN_VALUE, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            add(key, ShareContract.TYPE_LONG)
                    .put(ShareContract.COLUMN_VALUE, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            add(key, ShareContract.TYPE_FLOAT)
                    .put(ShareContract.COLUMN_VALUE, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            add(key, ShareContract.TYPE_BOOLEAN)
                    .put(ShareContract.COLUMN_VALUE, value ? 1 : 0);
            return this;
        }

        @Override
        public Editor remove(String key) {
            mToRemove.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            return remove("");
        }

        @Override
        public boolean commit() {
            for (String key : mToRemove) {
                Uri uri = mBaseUri.buildUpon().appendPath(key).build();
                mContext.getContentResolver().delete(uri, null, null);
            }
            ContentValues[] values = mToAdd.toArray(new ContentValues[mToAdd.size()]);
            Uri uri = mBaseUri.buildUpon().appendPath("").build();
            mContext.getContentResolver().bulkInsert(uri, values);
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }

    private class PreferenceContentObserver extends ContentObserver {
        private final WeakReference<OnSharedPreferenceChangeListener> mListener;

        private PreferenceContentObserver(OnSharedPreferenceChangeListener listener) {
            super(mHandler);
            mListener = new WeakReference<OnSharedPreferenceChangeListener>(listener);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            String prefKey = uri.getLastPathSegment();
            OnSharedPreferenceChangeListener listener = mListener.get();
            if (listener == null) {
                mContext.getContentResolver().unregisterContentObserver(this);
            } else {
                listener.onSharedPreferenceChanged(SharePreference.this, prefKey);
            }
        }
    }

}
