package dev.ukanth.ufirewall.profiles;

import com.raizlabs.android.dbflow.annotation.Database;

/**
 * Created by ukanth .
 */

@Database(name = ProfilesDatabase.NAME, version = ProfilesDatabase.VERSION)
public class ProfilesDatabase {

    public static final String NAME = "profiles";

    public static final int VERSION = 1;
}