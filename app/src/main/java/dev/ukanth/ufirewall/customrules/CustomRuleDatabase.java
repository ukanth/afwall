package dev.ukanth.ufirewall.customrules;

import com.raizlabs.android.dbflow.annotation.Database;

/**
 * Created by ukanth on 24/9/17.
 */

@Database(name = CustomRuleDatabase.NAME, version = CustomRuleDatabase.VERSION)
public class CustomRuleDatabase {
    public static final String NAME = "rules";
    public static final int VERSION = 1;
}