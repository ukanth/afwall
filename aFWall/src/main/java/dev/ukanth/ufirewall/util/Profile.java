package dev.ukanth.ufirewall.util;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by ukanth on 31/7/15.
 */
public class Profile {

    String name;
    String identifier;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Profile(String name, String identifier) {
        this.name = name;
        this.identifier = identifier;
    }

    public String getProfileName() {
        return name;
    }

    public void setProfileName(String profileName) {
        this.name = profileName;
    }

    public String toJSON() {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", getProfileName());
            jsonObject.put("identifier", getIdentifier());
            return jsonObject.toString();
        } catch (JSONException e) {
            return null;
        }

    }

    public JSONObject getJSON() {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", getProfileName());
            jsonObject.put("identifier", getIdentifier());
            return jsonObject;
        } catch (JSONException e) {
            return null;
        }

    }
}
