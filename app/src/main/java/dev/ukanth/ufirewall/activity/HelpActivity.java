package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.ThemeHelper;

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
        String version = BuildConfig.VERSION_NAME;
        TextView titleText = findViewById(R.id.afwall_title);
        String versionText = getString(R.string.app_name) + " (v" + version + ")";
        if (G.isDonate() || G.isDoKey(this)) {
            versionText = versionText + " (Donate) " + getString(R.string.donate_thanks) + " :)";
        }
        titleText.setText(versionText);
    }



    private void initTheme() {
        ThemeHelper.applyTheme(this);
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
