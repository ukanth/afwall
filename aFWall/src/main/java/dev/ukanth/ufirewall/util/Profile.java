package dev.ukanth.ufirewall.util;

/**
 * Created by ukanth on 31/7/15.
 */
public class Profile {

    public Profile(String name) {
        this.profileName = name;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    String profileName;
}
