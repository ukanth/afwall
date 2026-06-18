/**
 * Display firewall rules and interface info
 * <p>
 * Copyright (C) 2011-2013  Kevin Cernekee
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Map;
import java.util.TreeSet;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceDetails;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.ApplicationErrorLog;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;

public class RulesActivity extends DataDumpActivity {

    protected static final int MENU_FLUSH_RULES = 12;
    protected static final int MENU_IPV6_RULES = 19;
    protected static final int MENU_IPV4_RULES = 20;
    protected static final int MENU_SEND_REPORT = 25;

    protected boolean showIPv6 = false;
    protected static StringBuilder result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.showrules_title));

        //coming from shortcut
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            Object data = bundle.get("validate");
            if (data != null) {
                String check = (String) data;
                if (check.equals("yes")) {
                   new SecurityUtil( RulesActivity.this).passCheck();
                }
            }
        }
        //sdDumpFile = "rules.log";
    }


    protected void populateMenu(SubMenu sub) {
        if (G.enableIPv6()) {
            sub.add(0, MENU_IPV6_RULES, 0, R.string.switch_ipv6).setIcon(R.drawable.ic_rules);
            sub.add(0, MENU_IPV4_RULES, 0, R.string.switch_ipv4).setIcon(R.drawable.ic_rules);
        }
        sub.add(0, MENU_FLUSH_RULES, 0, R.string.flush).setIcon(R.drawable.ic_clearlog);
        sub.add(0, MENU_SEND_REPORT, 0, R.string.send_report).setIcon(R.drawable.ic_mail);
    }

    private void writeHeading(StringBuilder res, boolean initialNewline, String title) {
        StringBuilder eq = new StringBuilder();

        for (int i = 0; i < title.length(); i++) {
            eq.append('=');
        }

        if (initialNewline) {
            res.append("\n");
        }
        res.append(eq).append("\n").append(title).append("\n").append(eq).append("\n\n");
    }

    protected void appendPreferences(final Context ctx) {
        // Fifth section: "Preferences"
        writeHeading(result, true, "Preferences");

        try {
            Map<String, ?> prefs = G.gPrefs.getAll();
            for (String s : new TreeSet<String>(prefs.keySet())) {
                Object entry = prefs.get(s);
                result.append(s).append(": ").append(entry.toString()).append("\n");
            }
            //append profile mode & Status
            result.append("Profile Mode : ").append(G.pPrefs.getString(Api.PREF_MODE, "")).append("\n");
            result.append("Status : ").append(Api.isEnabled(ctx) ? "Enabled" : "Disabled").append("\n");
        } catch (NullPointerException e) {
            result.append("Error retrieving preferences\n");
        }

        writeHeading(result, true, getString(R.string.application_errors_title));
        String applicationErrors = ApplicationErrorLog.get(ctx);
        if (applicationErrors.trim().isEmpty()) {
            result.append(getString(R.string.application_errors_empty)).append("\n");
        } else {
            result.append(applicationErrors);
        }

        // Sixth section: "Logcat"
        writeHeading(result, true, "Logcat");
        result.append(Log.getLog());

        // finished: post result to the user
        setData(result.toString());
    }

    protected String getFileInfo(String filename) {
        File f = new File(filename);
        if (f.exists() && f.isFile()) {
            return filename + ": " +
                    f.length() + " bytes\n";
        } else {
            return filename + ": not present\n";
        }
    }

    protected String getSuInfo(PackageManager pm) {
        String[] suPackages = {
                "com.koushikdutta.superuser",
                "com.noshufou.android.su",
                "com.noshufou.android.su.elite",
                "com.koushikdutta.superuser",
                "com.gorserapp.superuser",
                "me.phh.superuser",
                "com.bitcubate.superuser.pro",
                "com.kingroot.kinguser",
                "com.kingroot.master",
                "com.kingouser.com",
                "com.m0narx.su",
                "com.miui.uac",
                "eu.chainfire.supersu",
                "eu.chainfire.supersu.pro",
                "com.topjohnwu.magisk"
        };
        String found = "none found";

        for (String s : suPackages) {
            try {
                PackageInfo info = pm.getPackageInfo(s, 0);
                found = s + " v" + info.versionName;
                break;
            } catch (NameNotFoundException e) {
            }
        }

        return found;
    }

    protected void appendSystemInfo(final Context ctx) {
        // Fourth section: "System info"
        writeHeading(result, true, "System info");

        InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx, false);

        result.append("Android version: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        result.append("Manufacturer: ").append(android.os.Build.MANUFACTURER).append("\n");
        result.append("Model: ").append(android.os.Build.MODEL).append("\n");
        result.append("Build: ").append(android.os.Build.DISPLAY).append("\n");

        if (cfg.netType == ConnectivityManager.TYPE_MOBILE) {
            result.append("Active interface: mobile\n");
        } else if (cfg.netType == ConnectivityManager.TYPE_WIFI) {
            result.append("Active interface: wifi\n");
        } else {
            result.append("Active interface: unknown\n");
        }
        result.append("Wifi Tether status: ").append(cfg.tetherWifiStatusKnown ? (cfg.isWifiTethered ? "yes" : "no") : "unknown").append("\n");
        result.append("Bluetooth Tether status: ").append(cfg.tetherBluetoothStatusKnown ? (cfg.isBluetoothTethered ? "yes" : "no") : "unknown").append("\n");
        result.append("Usb Tether status: ").append(cfg.tetherUsbStatusKnown ? (cfg.isUsbTethered ? "yes" : "no") : "unknown").append("\n");
        result.append("Roam status: ").append(cfg.isRoaming ? "yes" : "no").append("\n");
        result.append("IPv4 subnets: ").append(cfg.lanMaskV4.isEmpty() ? "none" : String.join(", ", cfg.lanMaskV4)).append("\n");
        result.append("IPv6 subnets: ").append(cfg.lanMaskV6.isEmpty() ? "none" : String.join(", ", cfg.lanMaskV6)).append("\n");

        // filesystem calls can block, so run in another thread
        new AsyncTask<Void, Void, String>() {
            @Override
            public String doInBackground(Void... args) {
                StringBuilder ret = new StringBuilder();

                ret.append(getFileInfo("/system/bin/su"));
                ret.append(getFileInfo("/system/xbin/su"));
                ret.append(getFileInfo("/data/magisk/magisk"));
                ret.append(getFileInfo("/data/adb/magisk"));
                ret.append(getFileInfo("/system/app/Superuser.apk"));

                PackageManager pm = ctx.getPackageManager();
                ret.append("Superuser: ").append(getSuInfo(pm));
                ret.append("\n");

                return ret.toString();
            }

            @Override
            public void onPostExecute(String suInfo) {
                result.append(suInfo);
                updateLoadingState(getString(R.string.finalizing));
                appendPreferences(ctx);
            }
        }.execute();

    }

    protected void appendIfconfig(final Context ctx) {
        // Third section: "ifconfig" (for interface info obtained through busybox)
        writeHeading(result, true, "ifconfig");
        updateLoadingState(getString(R.string.loading_system_info));
        Api.runIfconfig(ctx, new RootCommand()
                .setLogging(true)
                .setCallback(new RootCommand.Callback() {
                    public void cbFunc(RootCommand state) {
                        result.append(state.res);
                        appendSystemInfo(ctx);
                    }
                }));
    }

    protected void appendNetworkInterfaces(final Context ctx) {
        // Second section: "Network Interfaces" (for interface info obtained through Android APIs)
        writeHeading(result, true, "Network interfaces");
        Api.runNetworkInterface(ctx, new RootCommand()
                .setLogging(true)
                .setCallback(new RootCommand.Callback() {
                    public void cbFunc(RootCommand state) {
                        String iface = state.res.toString();
                        result.append(iface);
                        appendIfconfig(ctx);
                    }
                }));
    }

    protected void populateData(final Context ctx) {
        result = new StringBuilder();

        // Update loading state for modern layout
        updateLoadingState(getString(R.string.loading));

        // First section: "IPxx Rules"
        writeHeading(result, false, showIPv6 ? getString(R.string.ipv6_rules_title) : getString(R.string.ipv4_rules_title));
        if (showIPv6) {
            sdDumpFile = "IPv6rules.log";
        } else {
            sdDumpFile = "IPv4rules.log";
        }
        Api.fetchIptablesRules(ctx, showIPv6, new RootCommand()
                .setLogging(true)
                .setReopenShell(true)
                .setFailureToast(R.string.error_fetch)
                .setCallback(new RootCommand.Callback() {
                    public void cbFunc(RootCommand state) {
                        result.append(state.res);
                        updateLoadingState(getString(R.string.loading_network_info));
                        appendNetworkInterfaces(ctx);
                    }
                }));
    }

    private void updateLoadingState(String status) {
        runOnUiThread(() -> {
            TextView rulesStatus = findViewById(R.id.rules_status);
            TextView rulesTitle = findViewById(R.id.rules_title);
            if (rulesStatus != null) {
                rulesStatus.setText(status);
            }
            if (rulesTitle != null) {
                String title = showIPv6 ? getString(R.string.ipv6_rules_title) : getString(R.string.ipv4_rules_title);
                rulesTitle.setText(title);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Context ctx = this;

        switch (item.getItemId()) {

            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case MENU_FLUSH_RULES:
                flushAllRules();
                return true;
            case MENU_IPV6_RULES:
                showIPv6 = true;
                updateLoadingState(getString(R.string.loading));
                populateData(this);
                return true;
            case MENU_IPV4_RULES:
                showIPv6 = false;
                updateLoadingState(getString(R.string.loading));
                populateData(this);
                return true;
            case MENU_SEND_REPORT:
                String ver;
                try {
                    ver = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
                } catch (NameNotFoundException e) {
                    ver = "???";
                }
                String body = dataText + "\n\n" + getString(R.string.enter_problem) + "\n\n";
                final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);

                emailIntent.setType("plain/text");
                emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"afwall-report@googlegroups.com"});
                emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "AFWall+ problem report - v" + ver);
                emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
                startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));

                // this shouldn't be necessary, but the default Android email client overrides
                // "body=" from the URI.  See MessageCompose.initFromIntent()
                //email.putExtra(Intent.EXTRA_TEXT, body);

                //startActivity(Intent.createChooser(email, getString(R.string.send_mail)));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void flushAllRules() {
        FirewallRuleActions.confirmFlushAllRules(this, () -> populateData(RulesActivity.this));
    }


}
