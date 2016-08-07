package dev.ukanth.ufirewall.preferences;

/**
 * Created by ukanth on 19/7/16.
 */

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.ukanth.ufirewall.BuildConfig;

/**
 * Exposes {@link SharedPreferences} to other apps running on the device.
 *
 * You must extend this class and declare a default constructor which
 * calls the super constructor with the appropriate authority and
 * preference file name parameters. Remember to add your provider to
 * your AndroidManifest.xml file and set the {@code android:exported}
 * property to true.
 *
 * To access the data from a remote process, use {@link SharedPreferences}
 * initialized with the same authority and the desired preference file name.
 *
 * For granular access control, override {@link #checkAccess(String, String, boolean)}
 * and return {@code false} to deny the operation.
 */
public abstract class SharePreferenceProvider extends ContentProvider implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int PREFERENCES_ID = 1;
    private static final int PREFERENCE_ID = 2;

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID;

    private final Uri mBaseUri;
    private final String[] mPrefNames;
    private final Map<String, SharedPreferences> mPreferences;
    private final UriMatcher mUriMatcher;

    /**
     * Initializes the remote preference provider with the specified
     * authority and preference files. The authority must match the
     * {@code android:authorities} property defined in your manifest
     * file. Only the specified preference files will be accessible
     * through the provider.
     *
     * @param prefNames The names of the preference files to expose.
     */
    public SharePreferenceProvider(String[] prefNames) {
        mBaseUri = Uri.parse("content://" + AUTHORITY);
        mPrefNames = prefNames;
        mPreferences = new HashMap<String, SharedPreferences>(prefNames.length);
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(AUTHORITY, "*/", PREFERENCES_ID);
        mUriMatcher.addURI(AUTHORITY, "*/*", PREFERENCE_ID);
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        for (String prefName : mPrefNames) {
            SharedPreferences preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
            preferences.registerOnSharedPreferenceChangeListener(this);
            mPreferences.put(prefName, preferences);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        PrefNameKeyPair nameKeyPair = parseUri(uri);
        SharedPreferences preferences = getPreferences(nameKeyPair, false);
        Map<String, ?> preferenceMap = preferences.getAll();
        MatrixCursor cursor = new MatrixCursor(projection);
        if (nameKeyPair.key.length() == 0) {
            for (Map.Entry<String, ?> entry : preferenceMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                cursor.addRow(buildRow(projection, key, value));
            }
        } else {
            String key = nameKeyPair.key;
            Object value = preferenceMap.get(key);
            cursor.addRow(buildRow(projection, key, value));
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        PrefNameKeyPair nameKeyPair = parseUri(uri);
        String key = nameKeyPair.key;
        if (key.length() == 0) {
            key = values.getAsString(ShareContract.COLUMN_KEY);
        }
        int type = values.getAsInteger(ShareContract.COLUMN_TYPE);
        Object value = ShareUtils.deserialize(values.get(ShareContract.COLUMN_VALUE), type);
        SharedPreferences preferences = getPreferences(nameKeyPair, true);
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) {
            throw new IllegalArgumentException("Attempting to insert preference with null value");
        } else if (value instanceof String) {
            editor.putString(key, (String)value);
        } else if (value instanceof Set<?>) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                editor.putStringSet(key, ShareUtils.toStringSet(value));
            } else {
                throw new IllegalArgumentException("String set preferences not supported on API < 11");
            }
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer)value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long)value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float)value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean)value);
        } else {
            throw new IllegalArgumentException("Cannot set preference with type " + value.getClass());
        }
        editor.commit();
        return uri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        PrefNameKeyPair nameKeyPair = parseUri(uri);
        String key = nameKeyPair.key;
        SharedPreferences preferences = getPreferences(nameKeyPair, true);
        if (key.length() == 0) {
            preferences.edit().clear().commit();
        } else {
            preferences.edit().remove(key).commit();
        }
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        insert(uri, values);
        return 0;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String prefName = getPreferencesName(sharedPreferences);
        Uri uri = mBaseUri.buildUpon().appendPath(prefName).appendPath(key).build();
        getContext().getContentResolver().notifyChange(uri, null);
    }

    private PrefNameKeyPair parseUri(Uri uri) {
        int match = mUriMatcher.match(uri);
        if (match != PREFERENCE_ID && match != PREFERENCES_ID) {
            throw new IllegalArgumentException("Invalid URI: " + uri);
        }
        List<String> pathSegments = uri.getPathSegments();
        String prefName = pathSegments.get(0);
        String prefKey = "";
        if (match == PREFERENCE_ID) {
            prefKey = pathSegments.get(1);
        }
        return new PrefNameKeyPair(prefName, prefKey);
    }

    private int getPrefType(Object value) {
        if (value == null) return ShareContract.TYPE_NULL;
        if (value instanceof String) return ShareContract.TYPE_STRING;
        if (value instanceof Set<?>) return ShareContract.TYPE_STRING_SET;
        if (value instanceof Integer) return ShareContract.TYPE_INT;
        if (value instanceof Long) return ShareContract.TYPE_LONG;
        if (value instanceof Float) return ShareContract.TYPE_FLOAT;
        if (value instanceof Boolean) return ShareContract.TYPE_BOOLEAN;
        throw new AssertionError("Unknown preference type: " + value.getClass());
    }

    private Object[] buildRow(String[] projection, String key, Object value) {
        Object[] row = new Object[projection.length];
        for (int i = 0; i < row.length; ++i) {
            String col = projection[i];
            if (ShareContract.COLUMN_KEY.equals(col)) {
                row[i] = key;
            } else if (ShareContract.COLUMN_TYPE.equals(col)) {
                row[i] = getPrefType(value);
            } else if (ShareContract.COLUMN_VALUE.equals(col)) {
                row[i] = ShareUtils.serialize(value);
            } else {
                throw new IllegalArgumentException("Invalid column name: " + col);
            }
        }
        return row;
    }

    private SharedPreferences getPreferences(PrefNameKeyPair nameKeyPair, boolean write) {
        String prefName = nameKeyPair.name;
        String prefKey = nameKeyPair.key;
        SharedPreferences prefs = mPreferences.get(prefName);
        if (prefs == null) {
            throw new IllegalArgumentException("Unknown preference file name: " + prefName);
        }
        if (!checkAccess(prefName, prefKey, write)) {
            throw new SecurityException("Insufficient permissions to access: " + prefName + "/" + prefKey);
        }
        return prefs;
    }

    private String getPreferencesName(SharedPreferences preferences) {
        for (Map.Entry<String, SharedPreferences> entry : mPreferences.entrySet()) {
            if (entry.getValue() == preferences) {
                return entry.getKey();
            }
        }
        throw new AssertionError("Cannot find name for SharedPreferences");
    }

    /**
     * Checks whether a specific preference is accessible by clients.
     * The default implementation returns {@code true} for all accesses.
     * You may override this method to control which preferences can be
     * read or written.
     *
     * @param prefName The name of the preference file.
     * @param prefKey The preference key. This is an empty string when handling the
     *                {@link SharedPreferences#getAll()} and
     *                {@link SharedPreferences.Editor#clear()} operations.
     * @param write {@code true} for "put" operations; {@code false} for "get" operations.
     * @return {@code true} if the access is allowed; {@code false} otherwise.
     */
    protected boolean checkAccess(String prefName, String prefKey, boolean write) {
        return true;
    }

    private class PrefNameKeyPair {
        private final String name;
        private final String key;

        private PrefNameKeyPair(String prefName, String prefKey) {
            name = prefName;
            key = prefKey;
        }
    }
}