package dev.ukanth.ufirewall.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.log.Log;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileHelper {

    public static List<Profile> getProfiles() {
        String data = G.profilesStored();
        List<Profile> profileList = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(data);
            JSONArray jsonArray = jsonObject.getJSONArray("profiles");
            for(int i=0; i< jsonArray.length(); i++) {
                jsonObject = jsonArray.getJSONObject(i);
                profileList.add(new Profile(jsonObject.getString("profile"),  jsonObject.getString("identifier")));
            }
            Log.i(Api.TAG, jsonArray.toString());
        } catch (JSONException e) {
            return null;
        }
        return profileList;
    }

}
