package dev.ukanth.ufirewall.log;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

/**
 * Created by ukanth on 17/1/16.
 */

@Table(database = LogPreferenceDB.class)
public class LogPreference extends BaseModel {
    @Column
    @PrimaryKey
    private int uid;

    @Column
    private String appName;

    @Column
    private long skipInterval;

    @Column
    private boolean skip;

    @Column
    private long timestamp;

    public boolean isDisable() {
        return disable;
    }

    public void setDisable(boolean disable) {
        this.disable = disable;
    }

    @Column
    private boolean disable;


    public long getSkipInterval() {
        return skipInterval;
    }

    public void setSkipInterval(long skipInterval) {
        this.skipInterval = skipInterval;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }


    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
