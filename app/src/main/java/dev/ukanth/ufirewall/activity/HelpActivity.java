package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class HelpActivity extends AppCompatActivity {
    private static final int SPECIAL_THANKS_TAP_TARGET = 5;
    private static final long SPECIAL_THANKS_TAP_WINDOW_MS = 3000L;

    private int specialThanksTapCount = 0;
    private long lastSpecialThanksTapMs = 0L;

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
        updateTitle();

        View specialThanks = findViewById(R.id.help_special_thanks);
        if (specialThanks != null) {
            specialThanks.setOnClickListener(v -> handleSpecialThanksTap());
        }

        View wipeAppliedRules = findViewById(R.id.help_wipe_applied_rules);
        if (wipeAppliedRules != null) {
            wipeAppliedRules.setOnClickListener(v -> confirmWipeAppliedRules());
        }
    }

    private void updateTitle() {
        String version = BuildConfig.VERSION_NAME;
        TextView titleText = findViewById(R.id.afwall_title);
        String versionText = getString(R.string.app_name) + " (v" + version + ")";
        if (G.isDoKey(this) || G.isDonate()) {
            versionText = versionText + " (Donate) " + getString(R.string.donate_thanks) + " :)";
        }
        titleText.setText(versionText);
    }

    private void handleSpecialThanksTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSpecialThanksTapMs > SPECIAL_THANKS_TAP_WINDOW_MS) {
            specialThanksTapCount = 0;
        }
        lastSpecialThanksTapMs = now;
        specialThanksTapCount++;
        if (specialThanksTapCount >= SPECIAL_THANKS_TAP_TARGET) {
            G.isDo(true);
            G.donorModeEnabled(true);
            specialThanksTapCount = 0;
            updateTitle();
            Toast.makeText(this, R.string.consider_donating, Toast.LENGTH_SHORT).show();
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
