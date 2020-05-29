package dev.ukanth.ufirewall.profiles;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

/**
 * Created by ukanth on 17/1/16.
 */

@Table(database = ProfilesDatabase.class)
public class ProfileData extends BaseModel {

    @Column
    @PrimaryKey(autoincrement = true)
    long id;

    public long getId() {
        return id;
    }

    @Column
    private String name;

    @Column
    private String identifier;

    @Column
    private String attibutes;

    @Column
    private String parentProfile;

    public ProfileData() {
    }

    public ProfileData(String name, String identifier) {
        this.name = name;
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getAttibutes() {
        return attibutes;
    }

    public void setAttibutes(String attibutes) {
        this.attibutes = attibutes;
    }

    public String getParentProfile() {
        return parentProfile;
    }

    public void setParentProfile(String parentProfile) {
        this.parentProfile = parentProfile;
    }

}
