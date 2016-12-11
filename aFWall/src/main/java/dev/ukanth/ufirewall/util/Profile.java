package dev.ukanth.ufirewall.util;

/**
 * Created by ukanth on 31/7/15.
 */
public class Profile {

    String profileName;

    public Profile(String name) {
        this.profileName = name;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }
}
