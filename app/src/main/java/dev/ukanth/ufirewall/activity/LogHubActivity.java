package dev.ukanth.ufirewall.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.util.ApplicationErrorLog;
import dev.ukanth.ufirewall.util.FileDialog;
import dev.ukanth.ufirewall.util.G;

public class LogHubActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;

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
        findViewById(R.id.log_hub_application_log).setOnClickListener(v -> startActivity(new Intent(this, ApplicationLogActivity.class)));
        findViewById(R.id.log_hub_application_errors).setOnClickListener(v -> startActivity(new Intent(this, ApplicationErrorsActivity.class)));
        findViewById(R.id.log_hub_export_logs).setOnClickListener(v -> showExportLogSelection());
    }

    private void openBlockedRequests() {
        Intent intent = new Intent(this, G.oldLogView() ? OldLogActivity.class : LogActivity.class);
        startActivity(intent);
    }

    private void showExportLogSelection() {
        CharSequence[] items = new CharSequence[]{
                getString(R.string.log_hub_blocked_requests),
                getString(R.string.application_log_title),
                getString(R.string.application_errors_title)
        };

        new MaterialDialog.Builder(this)
                .title(R.string.export_logs_title)
                .items(items)
                .itemsCallbackMultiChoice(new Integer[]{0, 1, 2}, (dialog, which, text) -> {
                    if (which == null || which.length == 0) {
                        Api.toast(this, getString(R.string.export_logs_select_one));
                        return false;
                    }
                    selectExportDirectory(which);
                    return true;
                })
                .positiveText(R.string.exports)
                .negativeText(R.string.Cancel)
                .show();
    }

    private void selectExportDirectory(Integer[] selectedSections) {
        File defaultPath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AFWall")
                : new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/");
        if (!defaultPath.exists()) {
            defaultPath.mkdirs();
        }

        try {
            FileDialog fileDialog = new FileDialog(this, defaultPath, true);
            fileDialog.setSelectDirectoryOption(true);
            fileDialog.addDirectoryListener(directory -> exportSelectedLogs(directory, selectedSections));
            fileDialog.showDialog();
        } catch (Exception e) {
            exportSelectedLogs(defaultPath, selectedSections);
        }
    }

    private void exportSelectedLogs(File directory, Integer[] selectedSections) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> writeSelectedLogs(directory, selectedSections));
        executor.shutdown();
    }

    private void writeSelectedLogs(File directory, Integer[] selectedSections) {
        boolean success = false;
        String filename = "";
        try {
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
            File file = new File(directory, "afwall-logs-" + timestamp + ".log");
            FileOutputStream output = new FileOutputStream(file);
            output.write(buildExportContent(selectedSections).getBytes());
            output.flush();
            output.close();
            filename = file.getAbsolutePath();
            success = true;
        } catch (IOException e) {
            dev.ukanth.ufirewall.log.Log.e(Api.TAG, "Unable to export selected logs", e);
        }

        boolean finalSuccess = success;
        String finalFilename = filename;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (finalSuccess) {
                Api.toast(this, getString(R.string.export_rules_success) + finalFilename, Toast.LENGTH_LONG);
            } else {
                Api.toast(this, getString(R.string.export_logs_fail), Toast.LENGTH_LONG);
            }
        });
    }

    private String buildExportContent(Integer[] selectedSections) {
        StringBuilder builder = new StringBuilder();
        for (Integer section : selectedSections) {
            if (section == null) {
                continue;
            }
            switch (section) {
                case 0:
                    appendExportSection(builder, getString(R.string.log_hub_blocked_requests),
                            LogInfo.parseLog(this, Api.fetchLogs()));
                    break;
                case 1:
                    appendExportSection(builder, getString(R.string.application_log_title), Log.getLog());
                    break;
                case 2:
                    String errors = ApplicationErrorLog.get(this);
                    appendExportSection(builder, getString(R.string.application_errors_title),
                            errors.trim().isEmpty() ? getString(R.string.application_errors_empty) : errors);
                    break;
            }
        }
        return builder.toString();
    }

    private void appendExportSection(StringBuilder builder, String title, String content) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("==== ").append(title).append(" ====\n\n");
        if (content == null || content.trim().isEmpty()) {
            builder.append(getString(R.string.no_data_available));
        } else {
            builder.append(content.trim());
        }
        builder.append("\n");
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
