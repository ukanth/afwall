package dev.ukanth.ufirewall.log;

import com.raizlabs.android.dbflow.annotation.Database;

/**
 * Created by ukanth on 17/1/16.
 */

@Database(name = LogPreferenceDB.NAME, version = LogPreferenceDB.VERSION)
public class LogPreferenceDB {

    public static final String NAME = "LogPreference";

    public static final int VERSION = 1;
}