package dev.ukanth.ufirewall.log;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

/**
 * Created by ukanth on 17/1/16.
 */

@Table(database = LogDatabase.class)
public class LogData extends BaseModel {
    @Column
    @PrimaryKey(autoincrement = true)
    long id;

    @Column private String uid;
    @Column private String appName;
    @Column private String in;
    @Column private String out;
    @Column private String proto;
    @Column private String spt;
    @Column private String dst;
    @Column private String len;
    @Column private String src;
    @Column private String dpt;
    @Column private String timestamp;

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    private long count;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
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

    public String getSpt() {
        return spt;
    }

    public void setSpt(String spt) {
        this.spt = spt;
    }

    public String getDst() {
        return dst;
    }

    public void setDst(String dst) {
        this.dst = dst;
    }

    public String getLen() {
        return len;
    }

    public void setLen(String len) {
        this.len = len;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public String getDpt() {
        return dpt;
    }

    public void setDpt(String dpt) {
        this.dpt = dpt;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
