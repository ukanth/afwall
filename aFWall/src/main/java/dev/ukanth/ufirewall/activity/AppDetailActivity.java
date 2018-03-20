package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.raizlabs.android.dbflow.sql.language.SQLite;

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
        setTitle(getString(R.string.traffic_detail_title));
        setContentView(R.layout.app_detail);

        int appid = getIntent().getIntExtra("appid", -1);
        String packageName = getIntent().getStringExtra("package");
        try {
            CheckBox logOption = (CheckBox) findViewById(R.id.notification_p);

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

        Toolbar toolbar = (Toolbar) findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final Context ctx = getApplicationContext();

        ImageView image = (ImageView) findViewById(R.id.app_icon);
        TextView textView = (TextView) findViewById(R.id.app_title);
        TextView textView2 = (TextView) findViewById(R.id.app_package);
        TextView up = (TextView) findViewById(R.id.up);
        TextView down = (TextView) findViewById(R.id.down);

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
                image.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_android_white_24dp));
                if(appid > 0) {
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

    private void setTotalBytesManual(TextView down, TextView up, int localUid) {
        File dir = new File("/proc/uid_stat/");
        down.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
        up.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
        if (dir.exists()) {
            String[] children = dir.list();
            if (!Arrays.asList(children).contains(String.valueOf(localUid))) {
                down.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
                up.setText(" : " + humanReadableByteCount(Long.parseLong("0"), false));
                return;
            }
            File uidFileDir = new File("/proc/uid_stat/" + String.valueOf(localUid));
            if (uidFileDir.exists()) {
                File uidActualFileReceived = new File(uidFileDir, "tcp_rcv");
                File uidActualFileSent = new File(uidFileDir, "tcp_snd");
                String textReceived = "0";
                String textSent = "0";
                try {
                    if (uidActualFileReceived.exists() && uidActualFileSent.exists()) {
                        BufferedReader brReceived = new BufferedReader(new FileReader(uidActualFileReceived));
                        BufferedReader brSent = new BufferedReader(new FileReader(uidActualFileSent));
                        String receivedLine;
                        String sentLine;
                        if ((receivedLine = brReceived.readLine()) != null) {
                            textReceived = receivedLine;
                        }
                        if ((sentLine = brSent.readLine()) != null) {
                            textSent = sentLine;
                        }
                        down.setText(" : " + humanReadableByteCount(Long.parseLong(textReceived), false));
                        up.setText(" : " + humanReadableByteCount(Long.parseLong(textSent), false));
                        brReceived.close();
                        brSent.close();
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
