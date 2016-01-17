package dev.ukanth.ufirewall.log;

import com.raizlabs.android.dbflow.annotation.Database;

/**
 * Created by ukanth on 17/1/16.
 */

@Database(name = LogDatabase.NAME, version = LogDatabase.VERSION)
public class LogDatabase {

    public static final String NAME = "Logs";

    public static final int VERSION = 1;
}