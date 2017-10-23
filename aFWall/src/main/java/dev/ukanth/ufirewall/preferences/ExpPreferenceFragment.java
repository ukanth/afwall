package dev.ukanth.ufirewall.preferences;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import com.stericson.roottools.RootTools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

public class ExpPreferenceFragment extends PreferenceFragment implements
        OnSharedPreferenceChangeListener {

    private static CheckBoxPreference fixLeakPref;
    private static final String initDirs[] = {"/magisk/.core/service.d", "/magisk/phh/su.d", "/su/su.d", "/system/su.d", "/system/etc/init.d", "/etc/init.d"};
    private static final String initScript = "afwallstart";

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.experimental_preferences);
        setupInitDir(findPreference("initPath"));
    }

    private void setupInitDir(Preference initd) {
        ListPreference listPreference = (ListPreference) initd;
        List<String> listSupportedDir = new ArrayList<>();
        //going through the list of known initDirectories
        for (String dir : initDirs) {
            File file = new File(dir);
            //path exists
            if (file.exists() && file.isDirectory()) {
                listSupportedDir.add(dir);
            }
        }
        //some path exists
        if (listSupportedDir.size() > 0) {
            String[] entries = listSupportedDir.toArray(new String[listSupportedDir.size()]);
            listPreference.setEntries(entries);
            listPreference.setEntryValues(entries);
        }

        if (G.initPath() != null) {
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

            if (enabled != isFixLeakInstalled()) {
                updateFixLeakScript(enabled);
            }
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

    public static void setupFixLeak(Preference pref, Context ctx) {
        if (pref == null) {
            return;
        }
        fixLeakPref = (CheckBoxPreference) pref;

        if (fixLeakPref.isEnabled()) {
            // gray out the fixLeak preference if the ROM doesn't support init.d
            fixLeakPref.setChecked(isFixLeakInstalled());
            fixLeakPref.setEnabled(getFixLeakPath() != null && !isPackageInstalled("com.androguide.universal.init.d", ctx));
        }
    }

    private static boolean isPackageInstalled(String packagename, Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private static boolean isFixLeakInstalled() {
        String path = getFixLeakPath();
        return path != null && new File(path).exists();
    }

    private void updateFixLeakScript(final boolean enabled) {
        Activity activity = getActivity();
        if (activity != null && isAdded()) {
            final Context ctx = activity.getApplicationContext();
            final String srcPath = new File(ctx.getDir("bin", 0), initScript)
                    .getAbsolutePath();

            new AsyncTask<Void, Void, Boolean>() {
                @Override
                public Boolean doInBackground(Void... args) {
                    boolean returnFlag = false;
                    String path = G.initPath();
                    if (path != null) {
                        if (enabled) {
                            File f = new File(path);
                            if (mountDir(getFixLeakPath(), "RW")) {
                                //make sure it's executable
                                new RootCommand()
                                        .setReopenShell(true)
                                        .setLogging(true)
                                        .run(ctx, "chmod 755 " + f.getAbsolutePath());
                                returnFlag = RootTools.copyFile(srcPath, (f.getAbsolutePath() + "/" + initScript),
                                        true, false);
                                mountDir(getFixLeakPath(), "RO");
                            } else {
                                Api.sendToastBroadcast(ctx, ctx.getString(R.string.mount_initd_error));
                            }
                        } else {
                            returnFlag = deleteFiles(ctx);
                        }
                    }
                    return returnFlag;
                }

                @Override
                public void onPostExecute(Boolean success) {
                    int msgid;

                    if (success) {
                        msgid = enabled ? R.string.success_initd
                                : R.string.remove_initd;
                    } else {
                        msgid = enabled ? R.string.unable_initd
                                : R.string.unable_remove_initd;
                        if (fixLeakPref == null) {
                            fixLeakPref = (CheckBoxPreference) findPreference("fixLeak");
                        }
                        fixLeakPref.setChecked(isFixLeakInstalled());
                    }
                    if (getStatus() != Status.RUNNING) {
                        Api.toast(ctx, getString(msgid), Toast.LENGTH_SHORT);
                    }
                }
            }.execute();
        }
    }

    private boolean mountDir(String path, String mountType) {
        if (path != null) {
            String busyboxPath = Api.getBusyBoxPath(this.getActivity().getApplicationContext(), false);
            return RootTools.remount(path, mountType, busyboxPath);
        }
        return false;
    }

    private Boolean deleteFiles(final Context ctx) {
        final boolean[] returnFlag = {false};
        String path = G.initPath();
        File f = new File(path);
        if (f.exists() && f.isDirectory()) {
            String filePath = path + "/" + initScript;
            if (mountDir(getFixLeakPath(), "RW")) {
                new RootCommand()
                        .setReopenShell(true).setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            returnFlag[0] = true;
                            mountDir(getFixLeakPath(), "RO");
                        } else {
                            Api.sendToastBroadcast(ctx, ctx.getString(R.string.delete_initd_error));
                        }
                    }
                }).setLogging(true).run(ctx, "rm -f " + filePath);
            } else {
                Api.sendToastBroadcast(ctx, ctx.getString(R.string.mount_initd_error));
            }

        }
        return returnFlag[0];
    }

    private static String getFixLeakPath() {
        if (G.initPath() != null) {
            return G.initPath() + "/" + initScript;
        }
        return null;
    }

}
