package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.log.Log;

/**
 * Created by ukanth on 22/11/16.
 */

public class CustomRuleOld {

    private static String loadAssetsFile(Context ctx, String inFile) {
        String tContents = "";
        try {
            InputStream stream = ctx.getAssets().open(inFile);
            int size = stream.available();
            byte[] buffer = new byte[size];
            stream.read(buffer);
            stream.close();
            tContents = new String(buffer);
        } catch (IOException e) {
            return null;
        }
        return tContents;
    }

    public static List<Rule> getRules(Context context) {
        String jsonData = loadAssetsFile(context, "rules.json");
        List<Rule> listRule = new ArrayList<>();
        try {
            if (jsonData != null) {
                JSONObject jsonObject = new JSONObject(jsonData);
                JSONArray array = (JSONArray) jsonObject.get("rules");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject row = array.getJSONObject(i);
                        Rule rule = new Rule();
                        rule.setName(row.getString("name"));
                        rule.setDesc(row.getString("desc"));
                        JSONObject v4Obj = row.getJSONObject("v4");
                        JSONObject v6Obj = row.getJSONObject("v6");
                        List<String> listv4On = new ArrayList<>();
                        List<String> listv4Off = new ArrayList<>();

                        List<String> listv6On = new ArrayList<>();
                        List<String> listv6Off = new ArrayList<>();

                        for (int item = 0; item < v4Obj.getJSONArray("on").length(); item++) {
                            listv4On.add(v4Obj.getJSONArray("on").getString(item));
                        }
                        for (int item = 0; item < v4Obj.getJSONArray("off").length(); item++) {
                            listv4Off.add(v4Obj.getJSONArray("off").getString(item));
                        }

                        rule.setIpv4On(listv4On);
                        rule.setIpv4Off(listv4Off);

                        for (int item = 0; item < v6Obj.getJSONArray("on").length(); item++) {
                            listv6On.add(v6Obj.getJSONArray("on").getString(item));
                        }
                        for (int item = 0; item < v6Obj.getJSONArray("off").length(); item++) {
                            listv6Off.add(v6Obj.getJSONArray("off").getString(item));
                        }

                        rule.setIpv6On(listv6On);
                        rule.setIpv6Off(listv6Off);

                        listRule.add(rule);
                    }
                }
            }
        } catch (JSONException e) {
            Log.i(Api.TAG, "Exception in parsing json" + e.getMessage());
        }
        return listRule;
    }

    public static int getRulesSize(Context context) {
        return getRules(context).size();
    }

}
