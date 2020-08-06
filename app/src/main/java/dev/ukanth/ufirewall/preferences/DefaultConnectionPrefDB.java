package dev.ukanth.ufirewall.preferences;

import com.raizlabs.android.dbflow.annotation.Database;
/**
 * Created by ukanth on 17/1/16.
 */

@Database(name = DefaultConnectionPrefDB.NAME, version = DefaultConnectionPrefDB.VERSION)
public class DefaultConnectionPrefDB {

    public static final String NAME = "DefaultConnectionPref";

    public static final int VERSION = 1;
}