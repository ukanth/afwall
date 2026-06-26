package dev.ukanth.ufirewall.activity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.ApplicationErrorLog;
import dev.ukanth.ufirewall.util.FileDialog;
import dev.ukanth.ufirewall.util.G;

public class LogHubActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;
    private static final int EXPORT_BLOCKED_REQUESTS = 0;
    private static final int EXPORT_IPTABLES_IPV4 = 1;
    private static final int EXPORT_IPTABLES_IPV6 = 2;
    private static final int EXPORT_APPLICATION_LOG = 3;
    private static final int EXPORT_APPLICATION_ERRORS = 4;

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
        List<CharSequence> items = new ArrayList<>();
        List<Integer> itemValues = new ArrayList<>();

        items.add(getString(R.string.log_hub_blocked_requests));
        itemValues.add(EXPORT_BLOCKED_REQUESTS);
        items.add(getString(R.string.export_logs_iptables_ipv4));
        itemValues.add(EXPORT_IPTABLES_IPV4);
        if (G.enableIPv6()) {
            items.add(getString(R.string.export_logs_iptables_ipv6));
            itemValues.add(EXPORT_IPTABLES_IPV6);
        }
        items.add(getString(R.string.application_log_title));
        itemValues.add(EXPORT_APPLICATION_LOG);
        items.add(getString(R.string.application_errors_title));
        itemValues.add(EXPORT_APPLICATION_ERRORS);

        new MaterialDialog.Builder(this)
                .title(R.string.export_logs_title)
                .items(items)
                .itemsCallbackMultiChoice(getDefaultExportSelections(items.size()), (dialog, which, text) -> {
                    if (which == null || which.length == 0) {
                        Api.toast(this, getString(R.string.export_logs_select_one));
                        return false;
                    }
                    showExportDestinationChoice(resolveExportSections(which, itemValues));
                    return true;
                })
                .positiveText(R.string.exports)
                .negativeText(R.string.Cancel)
                .show();
    }

    private Integer[] getDefaultExportSelections(int itemCount) {
        Integer[] selections = new Integer[itemCount];
        for (int i = 0; i < itemCount; i++) {
            selections[i] = i;
        }
        return selections;
    }

    private Integer[] resolveExportSections(Integer[] selectedIndexes, List<Integer> itemValues) {
        Integer[] sections = new Integer[selectedIndexes.length];
        for (int i = 0; i < selectedIndexes.length; i++) {
            sections[i] = itemValues.get(selectedIndexes[i]);
        }
        return sections;
    }

    private void showExportDestinationChoice(Integer[] selectedSections) {
        new MaterialDialog.Builder(this)
                .title(R.string.export_logs_title)
                .items(new CharSequence[]{
                        getString(R.string.send_report),
                        getString(R.string.export_logs_save_to_disk)
                })
                .itemsCallback((dialog, view, which, text) -> {
                    if (which == 0) {
                        sendSelectedLogs(selectedSections);
                    } else {
                        selectExportDirectory(selectedSections);
                    }
                })
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

    private void sendSelectedLogs(Integer[] selectedSections) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String content = buildExportContent(selectedSections);
            new Handler(Looper.getMainLooper()).post(() -> sendLogReport(content));
        });
        executor.shutdown();
    }

    private void sendLogReport(String content) {
        String ver;
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            ver = "???";
        }

        String body = content + "\n\n" + getString(R.string.enter_problem) + "\n\n";
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"afwall-report@googlegroups.com"});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "AFWall+ problem report - v" + ver);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));
        } catch (ActivityNotFoundException e) {
            Api.toast(this, getString(R.string.no_email_clients), Toast.LENGTH_LONG);
        }
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
                case EXPORT_BLOCKED_REQUESTS:
                    appendExportSection(builder, getString(R.string.log_hub_blocked_requests),
                            LogInfo.parseLog(this, Api.fetchLogs()));
                    break;
                case EXPORT_IPTABLES_IPV4:
                    appendExportSection(builder, getString(R.string.export_logs_iptables_ipv4),
                            fetchIptablesExport(false));
                    break;
                case EXPORT_IPTABLES_IPV6:
                    appendExportSection(builder, getString(R.string.export_logs_iptables_ipv6),
                            fetchIptablesExport(true));
                    break;
                case EXPORT_APPLICATION_LOG:
                    appendExportSection(builder, getString(R.string.application_log_title), Log.getApplicationLog());
                    break;
                case EXPORT_APPLICATION_ERRORS:
                    String errors = ApplicationErrorLog.get(this);
                    appendExportSection(builder, getString(R.string.application_errors_title),
                            errors.trim().isEmpty() ? getString(R.string.application_errors_empty) : errors);
                    break;
            }
        }
        return builder.toString();
    }

    private String fetchIptablesExport(boolean ipv6) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder rules = new StringBuilder();
        Api.fetchIptablesRules(this, ipv6, new RootCommand()
                .setLogging(true)
                .setReopenShell(true)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.res != null) {
                            rules.append(state.res);
                        }
                        latch.countDown();
                    }
                }));
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                return getString(R.string.export_logs_iptables_timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return getString(R.string.export_logs_iptables_interrupted);
        }
        return rules.toString();
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
