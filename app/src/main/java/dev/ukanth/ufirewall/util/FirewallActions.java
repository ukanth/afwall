package dev.ukanth.ufirewall.util;

import android.content.Context;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootCommand;

/**
 * Enable / disable / profile switch as started from outside the app (widgets, tile, Tasker).
 * The enabled state is always taken from the result, and a profile switch only applies rules
 * while the firewall is enabled.
 */
public final class FirewallActions {

    /**
     * Called with the result, on the root shell thread.
     */
    public interface Done {
        void done(boolean success);
    }

    private FirewallActions() {
    }

    public static void setEnabled(Context ctx, boolean enable, boolean showToasts, Done done) {
        final Context app = ctx.getApplicationContext();
        RootCommand cmd = new RootCommand()
                .setReopenShell(true)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        boolean ok = state.exitCode == 0;
                        if (ok) {
                            // a failed disable leaves rules loaded: keep the state "enabled" then
                            Api.setEnabled(app, enable, true);
                        }
                        if (done != null) {
                            done.done(ok);
                        }
                    }
                });
        if (showToasts) {
            cmd.setSuccessToast(enable ? R.string.toast_enabled : R.string.toast_disabled)
                    .setFailureToast(enable ? R.string.toast_error_enabling : R.string.toast_error_disabling);
        }
        if (enable) {
            Api.applySavedIptablesRules(app, true, cmd);
        } else {
            Api.purgeIptables(app, true, cmd);
        }
    }

    /**
     * Make {@code identifier} the active profile and, if the firewall is enabled, apply its rules.
     * While the firewall is disabled only the selection changes (applying would load rules while
     * the app says it is off).
     *
     * @param done called with the apply result, or with true right away when nothing is applied
     */
    public static void switchProfile(Context ctx, String identifier, boolean showToasts, Done done) {
        final Context app = ctx.getApplicationContext();
        G.setProfile(true, identifier);
        if (!Api.isEnabled(app)) {
            if (showToasts) {
                Api.toast(app, app.getString(R.string.profile_selected_firewall_off));
            }
            if (done != null) {
                done.done(true);
            }
            return;
        }
        RootCommand cmd = new RootCommand()
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (done != null) {
                            done.done(state.exitCode == 0);
                        }
                    }
                });
        if (showToasts) {
            cmd.setSuccessToast(R.string.rules_applied).setFailureToast(R.string.error_apply);
        }
        Api.applySavedIptablesRules(app, true, cmd);
    }

    /**
     * Allow or block an app on the connection types in use, in the active profile, and apply the
     * rules if the firewall is enabled. In allow-list mode the lists hold allowed apps, in block-list
     * mode blocked ones.
     */
    public static void setAppAccess(Context ctx, int uid, boolean allow) {
        final Context app = ctx.getApplicationContext();
        boolean whitelist = Api.MODE_WHITELIST.equals(G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST));
        boolean listed = whitelist == allow;
        java.util.List<String> keys = new java.util.ArrayList<>(java.util.Arrays.asList(
                Api.PREF_WIFI_PKG_UIDS, Api.PREF_3G_PKG_UIDS));
        if (G.enableRoam()) {
            keys.add(Api.PREF_ROAMING_PKG_UIDS);
        }
        if (G.enableVPN()) {
            keys.add(Api.PREF_VPN_PKG_UIDS);
        }
        if (G.enableLAN()) {
            keys.add(Api.PREF_LAN_PKG_UIDS);
        }
        if (G.enableTether()) {
            keys.add(Api.PREF_TETHER_PKG_UIDS);
        }
        android.content.SharedPreferences.Editor edit = G.pPrefs.edit();
        for (String key : keys) {
            java.util.Set<Integer> uids = new java.util.TreeSet<>(UidListParser.parse(G.pPrefs.getString(key, "")));
            if (listed) {
                uids.add(uid);
            } else {
                uids.remove(uid);
            }
            edit.putString(key, android.text.TextUtils.join("|", uids));
        }
        edit.apply();
        Api.applications = null; // the app list shows the new state
        if (Api.isEnabled(app)) {
            Api.applySavedIptablesRules(app, true, new RootCommand().setFailureToast(R.string.error_apply));
        }
    }

    /**
     * @return identifier of the profile shown as {@code name}; the default profile's name maps to
     * the default profile. Null if there is no such profile.
     */
    public static String identifierForName(Context ctx, String name) {
        if (name == null) {
            return null;
        }
        String defaultName = G.gPrefs.getString("default", ctx.getString(R.string.defaultProfile));
        if (name.equals(defaultName) || name.equals(Api.DEFAULT_PREFS_NAME)) {
            return Api.DEFAULT_PREFS_NAME;
        }
        ProfileData data = ProfileHelper.getProfileByName(name);
        return data != null ? data.getIdentifier() : null;
    }
}
