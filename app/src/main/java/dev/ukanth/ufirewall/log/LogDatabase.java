package dev.ukanth.ufirewall.log;

import com.raizlabs.android.dbflow.annotation.Database;
import com.raizlabs.android.dbflow.annotation.Migration;
import com.raizlabs.android.dbflow.sql.SQLiteType;
import com.raizlabs.android.dbflow.sql.migration.AlterTableMigration;

/**
 * Created by ukanth on 17/1/16.
 */

@Database(name = LogDatabase.NAME, version = LogDatabase.VERSION)
public class LogDatabase {

    public static final String NAME = "Logs";

    public static final int VERSION = 2;

    @Migration(version = 2, database = LogDatabase.class)
    public static class Migration2 extends AlterTableMigration<LogData> {


        public Migration2(Class<LogData> table) {
            super(table);
        }

        @Override
        public void onPreMigrate() {
            addColumn(SQLiteType.TEXT, "hostname");
            addColumn(SQLiteType.INTEGER, "type");
        }
    }
}