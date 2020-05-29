package dev.ukanth.ufirewall.log;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

/**
 * Created by ukanth on 17/1/16.
 */

@Table(database = LogDatabase.class,cachingEnabled = true)
//,indexGroups = { @IndexGroup(number = 1, name = "uidIndex"),})
public class LogData extends BaseModel {
    @Column
    @PrimaryKey(autoincrement = true)
    long id;

    @Column
    private int uid;

    @Column
    private String appName;

    @Column
    private String in;
    @Column
    private String out;
    @Column
    private String proto;
    @Column
    private int spt;
    @Column
    private String dst;
    @Column
    private int len;
    @Column
    private String src;
    @Column
    private int dpt;
    @Column
    private long timestamp;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Column
    private String hostname;

    @Column
    private int type;

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    private long count;

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

    public String getIn() {
        return in;
    }

    public void setIn(String in) {
        this.in = in;
    }

    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public String getProto() {
        return proto;
    }

    public void setProto(String proto) {
        this.proto = proto;
    }

    public int getSpt() {
        return spt;
    }

    public void setSpt(int spt) {
        this.spt = spt;
    }

    public String getDst() {
        return dst;
    }

    public void setDst(String dst) {
        this.dst = dst;
    }

    public int getLen() {
        return len;
    }

    public void setLen(int len) {
        this.len = len;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public int getDpt() {
        return dpt;
    }

    public void setDpt(int dpt) {
        this.dpt = dpt;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
