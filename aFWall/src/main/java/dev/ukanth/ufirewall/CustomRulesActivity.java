package dev.ukanth.ufirewall;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import org.json.JSONArray;
import org.json.JSONObject;

public class CustomRulesActivity extends AppCompatActivity {

    final SharedPreferences prefs = getSharedPreferences(Api.CUSTOM_RULE_PREFS, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View view = getLayoutInflater().inflate(R.layout.activity_custom_rules, null);
        setTitle(R.string.custom_rules);
        LinearLayout linearLayout = (LinearLayout) view.findViewById(R.id.activity_custom_rules);
        String data = Api.LoadAssetsFile(getApplicationContext(), "rules.json");
        try {

            if (data != null) {
                JSONObject jsonObject = new JSONObject(data);
                JSONArray array = (JSONArray) jsonObject.get("rules");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject row = array.getJSONObject(i);
                        String name = row.getString("name");
                        final String id = row.getString("id");

                        CardView cardView = new CardView(this);
                        cardView.setRadius(dpToPixels(5));
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.height = dpToPixels(40);
                        //params.gravity = Gravity.CENTER;
                        int margin = dpToPixels(8);
                        params.setMargins(margin, margin, margin, margin);
                        cardView.setLayoutParams(params);


                        Switch switchButton = new Switch(this);
                        switchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                prefs.edit().putBoolean(id, isChecked).commit();
                            }
                        });
                        switchButton.setChecked(prefs.getBoolean(id, false));
                        switchButton.setText(name);
                        switchButton.setTextSize(14);

                        params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.gravity = Gravity.CENTER;
                        margin = dpToPixels(10);
                        params.setMargins(margin+4, margin, margin, margin);

                        switchButton.setLayoutParams(params);

                        cardView.addView(switchButton);
                        linearLayout.addView(cardView);
                    }
                }
            }
        } catch (Exception e) {

        }

        setContentView(view);

        Toolbar toolbar = (Toolbar) findViewById(R.id.custom_toolbar_rules);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public int dpToPixels(int dp) {
        DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }
}
