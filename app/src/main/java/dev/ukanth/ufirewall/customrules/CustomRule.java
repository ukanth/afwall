package dev.ukanth.ufirewall.customrules;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

/**
 * Created by ukanth on 24/9/17.
 */

@Table(database = CustomRuleDatabase.class)
public class CustomRule extends BaseModel {

    @Column
    @PrimaryKey(autoincrement = true)
    long id;

    public long getId() {
        return id;
    }

    @Column
    private String name;

    @Column
    private String rule;

    @Column
    private long timestamp;

    @Column
    private boolean active;

    public CustomRule() {
    }

    public CustomRule(String name, String rule) {
        this.name = name;
        this.rule = rule;
        this.timestamp = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRule() {
        return rule;
    }

    public void setRule(String rule) {
        this.rule = rule;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
