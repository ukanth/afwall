package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.stericson.rootshell.RootShell;
import com.stericson.rootshell.execution.Command;
import com.stericson.rootshell.execution.Shell;
import com.stericson.roottools.RootTools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogPreference;
import dev.ukanth.ufirewall.log.LogPreference_Table;
import dev.ukanth.ufirewall.util.G;

public class AppDetailActivity extends AppCompatActivity {
    public static final String TAG = "AFWall";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTheme();
        setTitle(getString(R.string.traffic_detail_title));
        setContentView(R.layout.app_detail);

        int appid = getIntent().getIntExtra("appid", -1);
        String packageName = getIntent().getStringExtra("package");
        try {
            CheckBox logOption = findViewById(R.id.notification_p);
            LogPreference logPreference = SQLite.select()
                    .from(LogPreference.class)
                    .where(LogPreference_Table.uid.eq(appid)).querySingle();

            if (logPreference != null) {
                logOption.setChecked(logPreference.isDisable());
            }

            logOption.setOnCheckedChangeListener((buttonView, isChecked) -> {
                //only use when triggered by user
                if (buttonView.isPressed()) {
                    // write the logic here
                    G.updateLogNotification(appid, isChecked);
                }
            });
        } catch (Exception e) {
        }

        Toolbar toolbar = findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final Context ctx = getApplicationContext();

        ImageView image = findViewById(R.id.app_icon);
        TextView textView = findViewById(R.id.app_title);
        TextView textView2 = findViewById(R.id.app_package);
        TextView up = findViewById(R.id.up);
        TextView down = findViewById(R.id.down);

        /**/

        final PackageManager packageManager = getApplicationContext().getPackageManager();
        final String[] packageNameList = ctx.getPackageManager().getPackagesForUid(appid);

        final String pName = packageName;
        Button button = findViewById(R.id.app_settings);
        button.setOnClickListener(v -> Api.showInstalledAppDetails(getApplicationContext(), pName));
        ApplicationInfo applicationInfo;

        try {
            if (!packageName.startsWith("dev.afwall.special.")) {
                applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                if (applicationInfo != null) {
                    image.setImageDrawable(applicationInfo.loadIcon(packageManager));
                    String name = packageManager.getApplicationLabel(applicationInfo).toString();
                    textView.setText(name);
                    setTotalBytesManual(down, up, applicationInfo.uid);
                }
            } else {
                image.setImageDrawable(getApplicationContext().getDrawable(R.drawable.ic_unknown));
                if(appid >= 0) {
                    textView.setText(Api.getSpecialDescription(getApplicationContext(), packageName.replace("dev.afwall.special.", "")));
                } else {
                    textView.setText(Api.getSpecialDescriptionSystem(getApplicationContext(), packageName.replace("dev.afwall.special.", "")));
                }
                down.setText(" : " + humanReadableByteCount(0, false));
                up.setText(" : " + humanReadableByteCount(0, false));
                button.setEnabled(false);
            }

            if (packageNameList != null && packageNameList.length > 1) {
                textView2.setText(Arrays.toString(packageNameList));
                button.setEnabled(false);
            } else {
                textView2.setText(packageName);
            }

        } catch (final NameNotFoundException e) {
            down.setText(" : " + humanReadableByteCount(0, false));
            up.setText(" : " + humanReadableByteCount(0, false));
            button.setEnabled(false);
        }
    }

    private void initTheme() {
        switch(G.getSelectedTheme()) {
            case "D":
                setTheme(R.style.AppDarkTheme);
                break;
            case "L":
                setTheme(R.style.AppLightTheme);
                //set other colors
                break;
            case "B":
                setTheme(R.style.AppBlackTheme);
                break;
        }
    }

    private void setTotalBytesManual(TextView down, TextView up, int localUid) {
        File dir = new File("/proc/uid_stat/");
        down.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
        up.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
        if (dir.exists()) {
            String[] children = dir.list();
            if (children != null) {
                if (!Arrays.asList(children).contains(String.valueOf(localUid))) {
                    down.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
                    up.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
                    return;
                }
            } else {
                down.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
                up.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
                return;
            }

            File uidFileDir = new File("/proc/uid_stat/" + localUid);
            if (uidFileDir.exists()) {
                File uidActualFileReceived = new File(uidFileDir, "tcp_rcv");
                File uidActualFileSent = new File(uidFileDir, "tcp_snd");
                String textReceived = "0";
                String textSent = "0";
                try {
                    if (uidActualFileReceived.exists() && uidActualFileSent.exists()) {
                        Command command = new Command(0, "cat " + uidActualFileReceived.getAbsolutePath())
                        {
                            @Override
                            public void commandOutput(int id, String line) {
                                down.setText(" : " + humanReadableByteCount(Long.parseLong(line), false));
                                super.commandOutput(id, line);
                            }
                        };
                        Command command1 = new Command(1, "cat " + uidActualFileSent.getAbsolutePath())
                        {
                            @Override
                            public void commandOutput(int id, String line) {
                                up.setText(" : " + humanReadableByteCount(Long.parseLong(line), false));
                                super.commandOutput(id, line);
                            }
                        };
                        Shell shell = RootTools.getShell(true);
                        shell.add(command);
                        shell.add(command1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception while reading tx bytes: " + e.getLocalizedMessage());
                }
            }
        }
        // return Long.valueOf(textReceived).longValue() + Long.valueOf(textReceived).longValue();
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < 0) return "0 B";
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
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
}
