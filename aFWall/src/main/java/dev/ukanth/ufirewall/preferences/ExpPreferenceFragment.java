package dev.ukanth.ufirewall.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.stericson.roottools.RootTools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.Api.getFixLeakPath;
import static dev.ukanth.ufirewall.Api.mountDir;

public class ExpPreferenceFragment extends PreferenceFragment implements
        OnSharedPreferenceChangeListener {

    private final String[] initDirs = {
            "/magisk/.core/service.d/",
            "/sbin/.core/img/.core/service.d/",
            "/sbin/.magisk/img/.core/service.d/",
            "/magisk/phh/su.d/",
            "/data/adb/service.d/",
            "/sbin/.core/img/phh/su.d/",
            "/su/su.d/",
            "/system/su.d/",
            "/system/etc/init.d/",
            "/etc/init.d/",
            "/sbin/supersu/su.d",
            "/data/adb/su/su.d"};

    private final String initScript = "afwallstart";

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.experimental_preferences);
        setupInitDir(findPreference("initPath"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setupInitDir(Preference initd) {
        ListPreference listPreference = (ListPreference) initd;
        final Context ctx = getActivity().getApplicationContext();
        listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String selected = newValue.toString();
            // fix leak enabled - but user trying to change the path
            if (!selected.equals(G.initPath()) && G.fixLeak()) {
                deleteFiles(ctx, false);
                G.initPath(selected);
                updateFixLeakScript(true);
                return true;
            }
            return true;
        });

        Activity activity = getActivity();
        new Thread(() -> {
            List<String> listSupportedDir = new ArrayList<>();
            //going through the list of known initDirectories
            for (String dir : initDirs) {
                //path exists
                if (RootTools.exists(dir, true)) {
                    listSupportedDir.add(dir);
                }
            }
            //some path exists
            if (listSupportedDir.size() > 0) {
                String[] entries = listSupportedDir.toArray(new String[listSupportedDir.size()]);
                activity.runOnUiThread(() -> {
                    listPreference.setEntries(entries);
                    listPreference.setEntryValues(entries);
                });
            }
        }).start();

        if (G.initPath() != null && !G.initPath().isEmpty()) {
            listPreference.setValue(G.initPath());
        } else {
            CheckBoxPreference fixLeakPref = (CheckBoxPreference) findPreference("fixLeak");
            fixLeakPref.setEnabled(false);
        }
        setupFixLeak(findPreference("fixLeak"), this.getActivity().getApplicationContext());
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
        if (key.equals("fixLeak")) {
            boolean enabled = G.fixLeak();

            Activity activity = getActivity();
            new Thread(() -> {
                if (enabled != isFixLeakInstalled()) {
                    activity.runOnUiThread(() -> updateFixLeakScript(enabled));
                }
            }).start();
        }

        if (key.equals("initPath")) {
            if (G.initPath() != null) {
                CheckBoxPreference fixPath = (CheckBoxPreference) findPreference("fixLeak");
                fixPath.setEnabled(true);
            }
        }

        if (key.equals("multiUser")) {
            if (!Api.supportsMultipleUsers(this.getActivity().getApplicationContext())) {
                CheckBoxPreference multiUserPref = (CheckBoxPreference) findPreference(key);
                multiUserPref.setChecked(false);
            } else {
                Api.setUserOwner(this.getActivity().getApplicationContext());
            }
        }
    }

    public void setupFixLeak(Preference pref, Context ctx) {
        if (pref == null) {
            return;
        }
        CheckBoxPreference fixLeakPref = (CheckBoxPreference) pref;

        if (fixLeakPref.isEnabled()) {
            // gray out the fixLeak preference if the ROM doesn't support init.d
            updateLeakCheckbox();
            fixLeakPref.setEnabled(getFixLeakPath(initScript) != null && !isPackageInstalled("com.androguide.universal.init.d", ctx));
        }
    }

    private boolean isPackageInstalled(String packagename, Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Tests whether the fix leak script is installed.
     *
     * You should call this from an I/O thread, because current api level does not allow usage of futures.
     *
     * @return {@code true} if the fix leak script exists.
     */
    private boolean isFixLeakInstalled() {
        String path = getFixLeakPath(initScript);
        return path != null && RootTools.exists(path);
    }

    private void updateFixLeakScript(final boolean enabled) {
        Activity activity = getActivity();
        if (activity != null && isAdded()) {
            final Context ctx = activity.getApplicationContext();
            final String srcPath = new File(ctx.getDir("bin", 0), initScript)
                    .getAbsolutePath();
            new Thread(() -> {
                String path = G.initPath();
                if (path != null) {
                    if (enabled) {
                        File f = new File(path);
                        if (mountDir(ctx, getFixLeakPath(initScript), "RW")) {
                            //make sure it's executable
                            new RootCommand()
                                    .setReopenShell(true)
                                    .run(ctx, "chmod 755 " + f.getAbsolutePath());
                            if (RootTools.copyFile(srcPath, (f.getAbsolutePath() + "/" + initScript),
                                    true, false)) {
                                Api.sendToastBroadcast(ctx, ctx.getString(R.string.success_initd));
                            }
                            mountDir(ctx, getFixLeakPath(initScript), "RO");
                            activity.runOnUiThread(() -> updateLeakCheckbox());

                        } else {
                            Api.sendToastBroadcast(ctx, ctx.getString(R.string.mount_initd_error));
                        }
                    } else {
                        deleteFiles(ctx, true);
                    }
                }
            }).start();
        }
    }

    private void updateLeakCheckbox() {
        Activity activity = getActivity();
        CheckBoxPreference fixLeakPref = (CheckBoxPreference) findPreference("fixLeak");
        new Thread(() -> {
            boolean isFixLeakInstalled = isFixLeakInstalled();
            activity.runOnUiThread(() -> fixLeakPref.setChecked(isFixLeakInstalled));
        }).start();
    }


    private void deleteFiles(final Context ctx, final boolean updateCheckbox) {
        String path = G.initPath();
        if(path != null) {
            new Thread(() -> {
                if (RootTools.exists(path, true)) {
                    final String filePath = path + "/" + initScript;

                    if (mountDir(ctx, getFixLeakPath(initScript), "RW")) {
                        new RootCommand()
                                .setReopenShell(true).setCallback(new RootCommand.Callback() {
                            @Override
                            public void cbFunc(RootCommand state) {
                                if (state.exitCode == 0) {
                                    Api.sendToastBroadcast(ctx, ctx.getString(R.string.remove_initd));
                                } else {
                                    Api.sendToastBroadcast(ctx, ctx.getString(R.string.delete_initd_error));
                                }
                                if (updateCheckbox) {
                                    getActivity().runOnUiThread(() -> updateLeakCheckbox());
                                }
                            }
                        }).setLogging(true).run(ctx, "rm -f " + filePath);
                        mountDir(ctx, getFixLeakPath(initScript), "RO");
                    } else {
                        Api.sendToastBroadcast(ctx, ctx.getString(R.string.mount_initd_error));
                    }
                }
            }).start();
        } else {
            Api.sendToastBroadcast(ctx, ctx.getString(R.string.delete_initd_error));
        }

    }
}
