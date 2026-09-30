package dev.ukanth.ufirewall.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ImageViewCompat;
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

        // the themes tint images (theme attribute "tint"): show the launcher icon in its colors
        ImageView logo = findViewById(R.id.help_logo);
        if (logo != null) {
            ImageViewCompat.setImageTintList(logo, null);
        }

        link(R.id.help_link_wiki_row, "https://github.com/ukanth/afwall/wiki");
        link(R.id.help_link_source_row, "https://github.com/ukanth/afwall");
        link(R.id.help_link_issues_row, "https://github.com/ukanth/afwall/issues");
    }

    private void link(int rowId, String url) {
        View row = findViewById(rowId);
        if (row == null) {
            return;
        }
        row.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, url, Toast.LENGTH_LONG).show();
            }
        });
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
