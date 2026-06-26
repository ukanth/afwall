package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.ApplicationErrorLog;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.ThemeHelper;

public class ApplicationErrorsActivity extends AppCompatActivity {

    private static final int MENU_COPY = 1;
    private static final int MENU_REFRESH = 2;
    private static final int MENU_CLEAR = 3;

    private TextView content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTheme();
        setContentView(R.layout.application_errors);

        Toolbar toolbar = findViewById(R.id.application_errors_toolbar);
        setSupportActionBar(toolbar);
        setTitle(getString(R.string.application_errors_title));
        toolbar.setNavigationOnClickListener(v -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        ThemeHelper.apply(this);

        content = findViewById(R.id.application_errors_text);
        content.setTextIsSelectable(true);
        refreshData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_COPY, 0, R.string.copy).setIcon(R.drawable.ic_copy);
        menu.add(0, MENU_REFRESH, 0, R.string.refresh).setIcon(R.drawable.ic_refresh);
        menu.add(0, MENU_CLEAR, 0, R.string.clear_log).setIcon(R.drawable.ic_clearlog);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case MENU_COPY:
                Api.copyToClipboard(this, content.getText().toString());
                return true;
            case MENU_REFRESH:
                refreshData();
                return true;
            case MENU_CLEAR:
                confirmClear();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void refreshData() {
        String errors = ApplicationErrorLog.get(this);
        content.setText(errors.trim().isEmpty() ? getString(R.string.application_errors_empty) : errors);
    }

    private void confirmClear() {
        new MaterialDialog.Builder(this)
                .title(getString(R.string.clear_log) + " ?")
                .cancelable(true)
                .onPositive((dialog, which) -> {
                    ApplicationErrorLog.clear(this);
                    refreshData();
                    Toast.makeText(this, getString(R.string.log_cleared), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .show();
    }

    private void initTheme() {
        setTheme(G.getSelectedThemeStyle(this));
    }
}
