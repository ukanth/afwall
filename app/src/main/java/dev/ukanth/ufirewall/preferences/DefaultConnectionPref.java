package dev.ukanth.ufirewall.preferences;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

import dev.ukanth.ufirewall.log.LogPreferenceDB;

/**
 * Created by ukanth on 17/1/16.
 */

@Table(database = DefaultConnectionPrefDB.class)
public class DefaultConnectionPref extends BaseModel {
    @Column
    @PrimaryKey
    private int uid;

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    public boolean isState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    @Column
    private String connectionType;

    @Column
    private boolean state;

    public int getModeType() {
        return modeType;
    }

    public void setModeType(int modeType) {
        this.modeType = modeType;
    }

    @Column
    private int modeType;

}
