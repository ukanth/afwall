package dev.ukanth.ufirewall.preferences;

import android.app.Activity;
import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import com.stericson.roottools.RootTools;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

/**
 * The "Boot" and "Advanced" settings of the Rules screen (formerly the Experimental screen):
 * boot leak protection, the startup delay, multi-user and dual apps support.
 * <p>
 * The Rules screen reloads its preferences after reading the chain policies, so {@link #bind()}
 * has to run after every load.
 */
final class BootAdvancedPreferences {

    static final String KEY_FIX_LEAK = "fixLeak";
    static final String KEY_INIT_PATH = "initPath";
    static final String KEY_DELAY = "customDelay";
    static final String KEY_MULTI_USER = "multiUser";
    static final String KEY_DUAL_APPS = "supportDualApps";
    private static final String KEY_BOOT_CATEGORY = "bootCategory";

    private static final String SCRIPT = "afwallstart";

    /**
     * Where root solutions run boot scripts, best first. The script is meant to run early
     * (post-fs-data); the others are kept for older root solutions (SuperSU, old Magisk, init.d).
     */
    private static final String[] INIT_DIRS = {
            "/data/adb/post-fs-data.d/",
            "/data/adb/service.d/",
            "/data/adb/su/su.d",
            "/sbin/supersu/su.d",
            "/su/su.d/",
            "/system/su.d/",
            "/magisk/.core/service.d/",
            "/sbin/.core/img/.core/service.d/",
            "/sbin/.magisk/img/.core/service.d/",
            "/magisk/phh/su.d/",
            "/sbin/.core/img/phh/su.d/",
            "/system/etc/init.d/",
            "/etc/init.d/"};

    // startup directories found on this device; null until detected (root check)
    private static volatile List<String> detectedDirs;

    private final PreferenceFragment fragment;

    BootAdvancedPreferences(PreferenceFragment fragment) {
        this.fragment = fragment;
    }

    void bind() {
        bindDelay();
        bindDualApps();
        bindMultiUser();
        bindFixLeak();
    }

    // ---- startup delay: one slider, 0 = off ----

    private void bindDelay() {
        SeekBarPreference delay = (SeekBarPreference) fragment.findPreference(KEY_DELAY);
        if (delay == null) {
            return;
        }
        delay.setZeroText(fragment.getString(R.string.off));
        if (!G.startupDelay()) {
            delay.setValue(0);
        }
        delay.setOnPreferenceChangeListener((preference, newValue) -> {
            G.startupDelay((Integer) newValue > 0);
            return true;
        });
    }

    // ---- dual apps: defaults to on when the device has other profiles ----

    private void bindDualApps() {
        CheckBoxPreference dual = (CheckBoxPreference) fragment.findPreference(KEY_DUAL_APPS);
        if (dual != null) {
            dual.setChecked(G.supportDual());
            dual.setOnPreferenceChangeListener((preference, newValue) -> {
                Api.applications = null; // the app list changes
                return true;
            });
        }
    }

    private void bindMultiUser() {
        CheckBoxPreference multiUser = (CheckBoxPreference) fragment.findPreference(KEY_MULTI_USER);
        if (multiUser == null) {
            return;
        }
        multiUser.setOnPreferenceChangeListener((preference, newValue) -> {
            Context ctx = fragment.getActivity().getApplicationContext();
            if ((Boolean) newValue) {
                if (!Api.supportsMultipleUsers(ctx)) {
                    return false;
                }
                Api.setUserOwner(ctx);
            }
            return true;
        });
    }

    // ---- boot leak protection ----

    private void bindFixLeak() {
        CheckBoxPreference fixLeak = (CheckBoxPreference) fragment.findPreference(KEY_FIX_LEAK);
        ListPreference initPath = (ListPreference) fragment.findPreference(KEY_INIT_PATH);
        if (fixLeak == null || initPath == null) {
            return;
        }
        fixLeak.setOnPreferenceChangeListener((preference, newValue) -> {
            setFixLeak((Boolean) newValue);
            return true;
        });
        initPath.setOnPreferenceChangeListener((preference, newValue) -> {
            changeInitPath(newValue.toString());
            return true;
        });

        if (detectedDirs != null) {
            showDirs(detectedDirs);
        } else {
            fixLeak.setEnabled(false); // until we know whether there is a startup directory
            new Thread(() -> {
                List<String> found = new ArrayList<>();
                for (String dir : INIT_DIRS) {
                    if (RootTools.exists(dir, true)) {
                        found.add(dir);
                    }
                }
                detectedDirs = found;
                runOnUi(() -> showDirs(found));
            }).start();
        }
        refreshInstalledState();
    }

    /**
     * The picker is only shown when there is a choice; with one directory it is used as is.
     */
    private void showDirs(List<String> dirs) {
        CheckBoxPreference fixLeak = (CheckBoxPreference) fragment.findPreference(KEY_FIX_LEAK);
        ListPreference initPath = (ListPreference) fragment.findPreference(KEY_INIT_PATH);
        if (fixLeak == null || initPath == null) {
            return;
        }
        String current = G.initPath();
        List<String> choices = new ArrayList<>(dirs);
        if (current != null && !current.isEmpty() && !choices.contains(current)) {
            choices.add(0, current); // keep a path chosen earlier visible
        }
        fixLeak.setEnabled(!choices.isEmpty());
        if (choices.isEmpty()) {
            fixLeak.setSummary(R.string.fixleak_unsupported);
        }
        if (choices.size() > 1) {
            String[] entries = choices.toArray(new String[0]);
            initPath.setEntries(entries);
            initPath.setEntryValues(entries);
            if (current != null && !current.isEmpty()) {
                initPath.setValue(current);
            }
        } else {
            PreferenceCategory boot = (PreferenceCategory) fragment.findPreference(KEY_BOOT_CATEGORY);
            if (boot != null) {
                boot.removePreference(initPath);
            }
        }
    }

    private void setFixLeak(boolean enable) {
        final Context ctx = fragment.getActivity().getApplicationContext();
        if (enable && (G.initPath() == null || G.initPath().isEmpty())) {
            List<String> dirs = detectedDirs;
            if (dirs == null || dirs.isEmpty()) {
                return;
            }
            G.initPath(dirs.get(0)); // best available
        }
        new Thread(() -> {
            boolean ok = enable ? Api.installFixLeakScript(ctx, SCRIPT) : Api.removeFixLeakScript(ctx, SCRIPT);
            Api.toast(ctx, ctx.getString(ok ? (enable ? R.string.success_initd : R.string.remove_initd)
                    : (enable ? R.string.mount_initd_error : R.string.delete_initd_error)));
            refreshInstalledState();
        }).start();
    }

    private void changeInitPath(String newPath) {
        final Context ctx = fragment.getActivity().getApplicationContext();
        String oldPath = G.initPath();
        if (newPath.equals(oldPath)) {
            return;
        }
        if (!G.fixLeak()) {
            G.initPath(newPath);
            return;
        }
        // move the installed script; the list saves the new path right after this listener, so
        // remember where the old script is now
        final String oldScript = Api.getFixLeakPath(SCRIPT);
        new Thread(() -> {
            if (!Api.removeFixLeakScriptAt(ctx, oldScript)) {
                Log.w(G.TAG, "Unable to remove the fix leak script from " + oldPath);
            }
            G.initPath(newPath);
            boolean ok = Api.installFixLeakScript(ctx, SCRIPT);
            Api.toast(ctx, ctx.getString(ok ? R.string.success_initd : R.string.mount_initd_error));
            refreshInstalledState();
        }).start();
    }

    /**
     * Show whether the script is really installed (the setting alone can be stale).
     */
    private void refreshInstalledState() {
        new Thread(() -> {
            String path = Api.getFixLeakPath(SCRIPT);
            boolean installed = path != null && RootTools.exists(path);
            runOnUi(() -> {
                CheckBoxPreference fixLeak = (CheckBoxPreference) fragment.findPreference(KEY_FIX_LEAK);
                if (fixLeak != null && fixLeak.isChecked() != installed) {
                    fixLeak.setChecked(installed);
                }
            });
        }).start();
    }

    private void runOnUi(Runnable r) {
        Activity activity = fragment.getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (fragment.isAdded()) {
                    r.run();
                }
            });
        }
    }
}
