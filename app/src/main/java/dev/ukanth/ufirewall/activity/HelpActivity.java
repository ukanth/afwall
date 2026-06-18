package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class HelpActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initTheme();

        setContentView(R.layout.help_about);

        Toolbar toolbar = findViewById(R.id.help_toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.help);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupContent();
    }

    private void setupContent() {
        // Setup app title with version
        String version = BuildConfig.VERSION_NAME;
        TextView titleText = findViewById(R.id.afwall_title);
        String versionText = getString(R.string.app_name) + " (v" + version + ")";
        if(G.isDoKey(this) || BuildConfig.APPLICATION_ID.equals("dev.ukanth.ufirewall.donate")) {
            versionText = versionText + " (Donate) " + getString(R.string.donate_thanks) + " :)";
        }
        titleText.setText(versionText);

        View wipeAppliedRules = findViewById(R.id.help_wipe_applied_rules);
        if (wipeAppliedRules != null) {
            wipeAppliedRules.setOnClickListener(v -> confirmWipeAppliedRules());
        }
    }

    private void confirmWipeAppliedRules() {
        FirewallRuleActions.confirmFlushAllRules(this, null);
    }



    private void initTheme() {
        switch(G.getSelectedTheme()) {
            case "D":
                setTheme(R.style.AppDarkTheme);
                break;
            case "L":
                setTheme(R.style.AppLightTheme);
                break;
            case "LHC":
                setTheme(R.style.AppLightHighContrastTheme);
                break;
            case "B":
                setTheme(R.style.AppBlackTheme);
                break;
        }
    }

     @Override
     public boolean onOptionsItemSelected(MenuItem item) {
             switch (item.getItemId()) {
             case android.R.id.home:
                     finish();
                     return true;
             default:
                     return super.onOptionsItemSelected(item);
             }
     }
}
