package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootShellService;
import dev.ukanth.ufirewall.util.G;

public class RulesPreferenceFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private Context ctx;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.rules_preferences);

        /*try {
            updateRuleStatus();
        } catch (Exception e) {
        }*/

        //make sure Roaming is disable in Wifi-only Tablets
        if (!Api.isMobileNetworkSupported(getActivity())) {
            CheckBoxPreference roamPreference = (CheckBoxPreference) findPreference("enableRoam");
            roamPreference.setChecked(false);
            roamPreference.setEnabled(false);
        } else {
            CheckBoxPreference roamPreference = (CheckBoxPreference) findPreference("enableRoam");
            roamPreference.setEnabled(true);
        }
    }

    private void updateRuleStatus() {
        Api.ruleStatus(ctx, false, new RootShellService.RootCommand()
                .setFailureToast(R.string.error_apply)
                .setLogging(true)
                .setCallback(new RootShellService.RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootShellService.RootCommand state) {
                        if (state.exitCode == 0) {
                            StringBuilder result = state.res;
                            if (result != null) {
                                String output = result.toString();

                                final String regexIn = "-P INPUT (\\w+)";
                                final String regexOut = "-P OUTPUT (\\w+)";

                                final Pattern pattern = Pattern.compile(regexIn);
                                final Pattern pattern2 = Pattern.compile(regexOut);

                                final Matcher matcher = pattern.matcher(output);
                                boolean firstTime = true;
                                while (matcher.find()) {
                                    Log.i("AFWall", matcher.group());
                                    if (firstTime) {
                                        G.ipv4Input(matcher.group(1).equals("ACCEPT") ? true : false);
                                        firstTime = false;
                                    } else {
                                        G.ipv6Input(matcher.group(1).equals("ACCEPT") ? true : false);
                                    }
                                }
                                firstTime = true;
                                final Matcher matcher2 = pattern2.matcher(output);
                                while (matcher2.find()) {
                                    Log.i("AFWall", matcher2.group());
                                    if (firstTime) {
                                        G.ipv4Output(matcher2.group(1).equals("ACCEPT") ? true : false);
                                        firstTime = false;
                                    } else {
                                        G.ipv6Output(matcher2.group(1).equals("ACCEPT") ? true : false);
                                    }
                                }
                            }
                            getPreferenceScreen().removeAll();
                            addPreferencesFromResource(R.xml.rules_preferences);
                        }
                    }
                }));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ctx = context;
    }


    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {

        if (key.equals("activeRules")) {
            if (!G.activeRules()) {
                //disable service when there is no active rules
                //stopService(new Intent(PreferencesActivity.this, RootShell.class));
                CheckBoxPreference enableRoam = (CheckBoxPreference) findPreference("enableRoam");
                enableRoam.setChecked(false);
                CheckBoxPreference enableLAN = (CheckBoxPreference) findPreference("enableLAN");
                enableLAN.setChecked(false);
                CheckBoxPreference enableVPN = (CheckBoxPreference) findPreference("enableVPN");
                enableVPN.setChecked(false);

                G.enableRoam(false);
                G.enableLAN(false);
                G.enableVPN(false);

            }
        }
        if (key.equals("enableIPv6") || key.equals("blockIPv6")) {
            File defaultIP6TablesPath = new File("/system/bin/ip6tables");
            if (!defaultIP6TablesPath.exists()) {
                G.enableIPv6(false);
                G.blockIPv6(false);
                CheckBoxPreference enable = (CheckBoxPreference) findPreference("enableIPv6");
                enable.setChecked(false);

                CheckBoxPreference block = (CheckBoxPreference) findPreference("blockIPv6");
                block.setChecked(false);
                if (ctx != null) {
                    Api.toast(ctx, getString(R.string.ip6unavailable));
                }
            } else {
                switch (key) {
                    case "enableIPv6":
                        CheckBoxPreference block = (CheckBoxPreference) findPreference("blockIPv6");
                        block.setChecked(false);
                        break;
                    case "blockIPv6":
                        CheckBoxPreference allow = (CheckBoxPreference) findPreference("enableIPv6");
                        allow.setChecked(false);
                        break;
                }
            }
        }
    }
}
