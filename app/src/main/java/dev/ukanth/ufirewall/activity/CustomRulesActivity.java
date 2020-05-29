package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import java.util.List;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.CustomRuleOld;
import dev.ukanth.ufirewall.util.Rule;

public class CustomRulesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View view = getLayoutInflater().inflate(R.layout.activity_custom_rules, null);
        setTitle(R.string.custom_rules);

        LinearLayout linearLayout = view.findViewById(R.id.activity_custom_rules);
        try {

            List<Rule> rules = CustomRuleOld.getRules(getApplicationContext());
            for (final Rule rule : rules) {


                CardView cardView = new CardView(this);
                cardView.setElevation(5);
                cardView.setRadius(5);

                cardView.setLayoutParams(new CardView.LayoutParams(
                        CardView.LayoutParams.WRAP_CONTENT, 100));

                cardView.setMinimumHeight(60);


                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.height = 200;
                params.gravity = Gravity.TOP;
                params.setMargins(0, 2, 0, 40);
                cardView.setLayoutParams(params);


                Switch switchButton = new Switch(this);
                switchButton.setTag(rule.getName());
                switchButton.setOnCheckedChangeListener((buttonView, isChecked)
                        -> {

                    Toast.makeText(getApplicationContext(), buttonView.getTag().toString() + isChecked, Toast.LENGTH_SHORT).show();
                });
                switchButton.setChecked(false);
                switchButton.setContentDescription(rule.getDesc());
                StringBuilder builder = new StringBuilder();
                builder.append(rule.getName());
                builder.append("\n");
                builder.append(rule.getDesc());
                builder.append("\n");
                /*for (String r : rule.getIpv4()) {
                    builder.appdend(r);
                    builder.append("\n");
                }
                for (String r : rule.getIpv6()) {
                    builder.append(r);
                    builder.append("\n");
                }*/
                switchButton.setText(builder.toString());
                switchButton.setTextSize(18);

                switchButton.setLayoutParams(params);

               /* cardView.setOnClickListener(v -> {
                    StringBuilder builder2 = new StringBuilder();
                    for (String r : rule.getIpv4()) {
                        builder2.append(r);
                        builder2.append("\n");
                    }
                    for (String r : rule.getIpv6()) {
                        builder2.append(r);
                        builder2.append("\n");
                    }
                    Toast.makeText(this, builder2.toString(), Toast.LENGTH_SHORT).show();
                });*/

                cardView.addView(switchButton);
                linearLayout.addView(cardView);
            }
        } catch (Exception e) {

        }

        setContentView(view);

        Toolbar toolbar = findViewById(R.id.custom_toolbar_rules);
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
