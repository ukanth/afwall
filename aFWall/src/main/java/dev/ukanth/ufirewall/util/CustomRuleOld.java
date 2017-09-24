package dev.ukanth.ufirewall.util;

/**
 * Created by ukanth on 22/11/16.
 */

public class CustomRuleOld {

    /*private static String loadAssetsFile(Context ctx, String inFile) {
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
                        rule.setId(row.getString("id"));
                        JSONArray v4Array = row.getJSONArray("v4");
                        List<String> list4 = new ArrayList<>();
                        for(int item=0;item < v4Array.length(); item++) {
                            list4.add(v4Array.getString(item));
                        }
                        rule.setIpv4(list4);
                        JSONArray v6Array = row.getJSONArray("v6");
                        List<String> list6 = new ArrayList<>();
                        for(int item=0;item < v4Array.length(); item++) {
                            list6.add(v6Array.getString(item));
                        }
                        rule.setIpv6(list6);
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

    public static List<String> getIds(Context context) {
        List<String> listId = new ArrayList<>();
        for (Rule rule : getRules(context)) {
            listId.add(rule.getId());
        }
        return listId;
    }

    public static List<String> getAllowedIPv4Rules(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(Api.CUSTOM_RULE_PREFS, 0);
        List<String> allowedRules = new ArrayList<>();
        for (Rule rule : getRules(context)) {
            if (prefs.getBoolean(rule.getId(), false)) {
                //toggled rule
                allowedRules.addAll(rule.getIpv4());
            }
        }
        return allowedRules;
    }

    public static List<String> getAllowedIPv6Rules(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(Api.CUSTOM_RULE_PREFS, 0);

        List<String> allowedRules = new ArrayList<>();
        for (Rule rule : getRules(context)) {
            if (prefs.getBoolean(rule.getId(), false)) {
                //toggled rule
                allowedRules.addAll(rule.getIpv6());
            }
        }
        return allowedRules;
    }*/

}
