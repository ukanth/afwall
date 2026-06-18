package dev.ukanth.ufirewall.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class LogHubActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTheme();
        setContentView(R.layout.log_hub);

        Toolbar toolbar = findViewById(R.id.log_hub_toolbar);
        setSupportActionBar(toolbar);
        setTitle(getString(R.string.log_hub_title));
        toolbar.setNavigationOnClickListener(v -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        findViewById(R.id.log_hub_blocked_requests).setOnClickListener(v -> openBlockedRequests());
        findViewById(R.id.log_hub_iptables).setOnClickListener(v -> startActivity(new Intent(this, RulesActivity.class)));
        findViewById(R.id.log_hub_diagnostics).setOnClickListener(v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.log_hub_application_errors).setOnClickListener(v -> startActivity(new Intent(this, ApplicationErrorsActivity.class)));
    }

    private void openBlockedRequests() {
        Intent intent = new Intent(this, G.oldLogView() ? OldLogActivity.class : LogActivity.class);
        startActivity(intent);
    }

    private void initTheme() {
        switch (G.getSelectedTheme()) {
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
}
