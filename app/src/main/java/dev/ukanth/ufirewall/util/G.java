/**
 * A place to store globals
 * <p>
 * Copyright (C) 2013 Kevin Cernekee
 * Copyright (C) 2016 Umakanthan Chandran
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

package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogPreference;
import dev.ukanth.ufirewall.log.LogPreferenceDB;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPref;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPrefDB;

public class G extends Application implements Application.ActivityLifecycleCallbacks{

    private static G instance;

    private static boolean enabledPrivateLink = false;

    static {
        //TODO: Remove this line before release
        //com.topjohnwu.superuser.Shell.enableVerboseLogging = BuildConfig.DEBUG;
        com.topjohnwu.superuser.Shell.setDefaultBuilder(com.topjohnwu.superuser.Shell.Builder.create()
                .setFlags(com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR)
        );
    }

    public static G getInstance() {
        return instance;
    }

    public static Context getContext() {
        return instance;
    }

    public static final String TAG = "AFWall";

    private static final String HAS_ROOT = "hasRoot";
    private static final String FIX_START_LEAK = "fixLeak";
    private static final String DISABLE_TASKER_TOAST = "disableTaskerToast";
    private static final String REG_DO = "ipurchaseddonatekey";
    private static final String ENABLE_ROAM = "enableRoam";
    private static final String ENABLE_VPN = "enableVPN";
    private static final String ENABLE_TETHER = "enableTether";
    private static final String ENABLE_LAN = "enableLAN";
    private static final String ENABLE_TOR = "enableTor";
    private static final String ENABLE_IPV6 = "enableIPv6";
    private static final String CONTROL_IPV6 = "controlIPv6";
    private static final String SELECTED_FILTER = "selectedFilter";
    //private static final String BLOCK_IPV6 = "blockIPv6";
    private static final String ENABLE_INBOUND = "enableInbound";
    private static final String ENABLE_LOG_SERVICE = "enableLogService";
    private static final String LOG_PING_TIMEOUT = "logPingTime";
    private static final String ENABLE_ADMIN = "enableAdmin";
    private static final String DUAL_APPS = "supportDualApps";
    private static final String ENABLE_DEVICE_CHECK = "enableDeviceCheck";
    private static final String ENABLE_CONFIRM = "enableConfirm";
    private static final String ENABLE_MULTI_PROFILE = "enableMultiProfile";
    private static final String SHOW_UID = "showUid";
    private static final String DISABLE_ICONS = "disableIcons";
    private static final String IPTABLES_PATH = "ipt_path";
    private static final String PROTECTION_OPTION = "passSetting";
    private static final String BUSYBOX_PATH = "bb_path";
    private static final String LANGUAGE = "locale";
    //private static final String LOG_DMESG = "logDmesg";
    private static final String SORT_BY = "sort";
    private static final String LAST_STORED_PROFILE = "storedProfile";
    private static final String STARTUP_DELAY = "addDelayStart";
    private static final String SYSTEM_APP_COLOR = "sysColor";
    private static final String ACTIVE_RULES = "activeRules";
    private static final String ADD_DELAY = "addDelay";

    //private static final String ACTIVE_NOTIFICATION = "activeNotification";
    private static final String PROFILE_SWITCH = "applyOnSwitchProfiles";
    private static final String LOG_TARGET = "logTarget";
    private static final String LOG_TARGETS = "logTargets";
    private static final String SHOW_HOST = "showHostName";
    private static final String APP_VERSION = "appVersion";
    private static final String DNS_PROXY = "dns_value";
    private static final String MULTI_USER = "multiUser";
    private static final String MULTI_USER_ID = "multiUserId";
    private static final String SHOW_FILTER = "showFilter";
    private static final String PATTERN_MAX_TRY = "patternMax";
    private static final String PATTERN_STEALTH = "stealthMode";
    private static final String PWD_ENCRYPT = "pwdEncrypt";
    private static final String PROFILE_PWD = "profilePwd";
    private static final String FINGERPRINT_ENABLED = "fingerprintEnabled";
    private static final String CUSTOM_DELAY_SECONDS = "customDelay";
    private static final String NOTIFICATION_PRIORITY = "notification_priority";
    private static final String RUN_NOTIFICATION = "runNotification";
    private static final String COPIED_OLD_EXPORTS = "copyOldExports";

    private static final String SHOW_ALL_APPS = "showAllApps";

    private static final String THEME = "theme";

    private static boolean privateDns = false;
    //private static final String QUICK_RULES = "quickApply";
    /**
     * FIXME
     **/
    private static final String AFWALL_STATUS = "AFWallStaus";
    //private static final String BLOCKED_NOTIFICATION = "block_filter_app";
    /* Profiles */
    private static final String ADDITIONAL_PROFILES = "plusprofiles";
    //private static final String PROFILES = "profiles_json";
    private static final String PROFILES_MIGRATED = "profilesmigrated";
    private static final String WIDGET_X = "widgetX";
    private static final String WIDGET_Y = "widgetY";
    //private static final String XPOSED_FIX_DM_LEAK = "fixDownloadManagerLeak";

    //ippreference
    private static final String IP4_INPUT = "input_chain";
    private static final String IP4_OUTPUT = "output_chain";
    private static final String IP4_FWD = "forward_chain";

    private static final String IP6_INPUT = "input_chain_v6";
    private static final String IP6_OUTPUT = "output_chain_v6";
    private static final String IP6_FWD = "forward_chain_v6";

    private static final String INITPATH = "initPath";

    private static final String AFWALL_PROFILE = "AFWallProfile";
    public static final String[] profiles = {"AFWallPrefs", AFWALL_PROFILE + 1, AFWALL_PROFILE + 2, AFWALL_PROFILE + 3};
    public static final String[] default_profiles = {"AFWallProfile1", "AFWallProfile2", "AFWallProfile3"};
    public static Context ctx;
    public static SharedPreferences gPrefs;
    public static SharedPreferences pPrefs;
    public static SharedPreferences sPrefs;

    public static boolean supportDual() {
        return gPrefs.getBoolean(DUAL_APPS, false);
    }

    public static boolean isRun() {
        return gPrefs.getBoolean(RUN_NOTIFICATION, true);
    }

    public static boolean hasCopyOld() {
        return gPrefs.getBoolean(COPIED_OLD_EXPORTS, false);
    }

    public static boolean hasCopyOldExports(boolean val) {
        gPrefs.edit().putBoolean(COPIED_OLD_EXPORTS, val).commit();
        return val;
    }


    public static boolean showAllApps() {
        return gPrefs.getBoolean(SHOW_ALL_APPS, false);
    }

   /* public static boolean showQuickButton() {
        return gPrefs.getBoolean(QUICK_RULES, false);
    }*/

    public static boolean ipv4Input() {
        return gPrefs.getBoolean(IP4_INPUT, true);
    }

    public static boolean ipv4Input(boolean val) {
        gPrefs.edit().putBoolean(IP4_INPUT, val).commit();
        return val;
    }

    public static boolean ipv4Fwd() {
        return gPrefs.getBoolean(IP4_FWD, true);
    }

    public static boolean ipv4Fwd(boolean val) {
        gPrefs.edit().putBoolean(IP4_FWD, val).commit();
        return val;
    }

    public static boolean ipv4Output() {
        return gPrefs.getBoolean(IP4_OUTPUT, true);
    }

    public static boolean ipv4Output(boolean val) {
        gPrefs.edit().putBoolean(IP4_OUTPUT, val).commit();
        return val;
    }

    public static boolean ipv6Fwd() {
        return gPrefs.getBoolean(IP6_FWD, true);
    }

    public static boolean ipv6Fwd(boolean val) {
        gPrefs.edit().putBoolean(IP6_FWD, val).commit();
        return val;
    }

    public static boolean ipv6Input() {
        return gPrefs.getBoolean(IP6_INPUT, true);
    }

    public static boolean ipv6Input(boolean val) {
        gPrefs.edit().putBoolean(IP6_INPUT, val).commit();
        return val;
    }

    public static boolean ipv6Output() {
        return gPrefs.getBoolean(IP6_OUTPUT, true);
    }

    public static boolean ipv6Output(boolean val) {
        gPrefs.edit().putBoolean(IP6_OUTPUT, val).commit();
        return val;
    }


    public static boolean isEnc() {
        return gPrefs.getBoolean(PWD_ENCRYPT, false);
    }

    public static boolean isEnc(boolean val) {
        gPrefs.edit().putBoolean(PWD_ENCRYPT, val).commit();
        return val;
    }

    public static String initPath() {
        return gPrefs.getString(INITPATH, null);
    }

    public static String initPath(String val) {
        gPrefs.edit().putString(INITPATH, val).commit();
        return val;
    }


    public static String getSelectedTheme() {
        return gPrefs.getString(THEME, "D");
    }

    public static String getSelectedTheme(String val) {
        gPrefs.edit().putString(THEME, val).commit();
        return val;
    }

    public static String profile_pwd() {
        return gPrefs.getString(PROFILE_PWD, "");
    }

    public static String profile_pwd(String val) {
        gPrefs.edit().putString(PROFILE_PWD, val).commit();
        return val;
    }

    public static int getNotificationPriority() {
        return Integer.parseInt(gPrefs.getString(NOTIFICATION_PRIORITY, "0"));
    }


    public static Boolean isFingerprintEnabled() {
        return gPrefs.getBoolean(FINGERPRINT_ENABLED, false);
    }

    public static Boolean isFingerprintEnabled(Boolean val) {
        gPrefs.edit().putBoolean(FINGERPRINT_ENABLED, val).commit();
        return val;
    }

    public static boolean isProfileMigrated() {
        return gPrefs.getBoolean(PROFILES_MIGRATED, false);
    }

    public static boolean isProfileMigrated(boolean val) {
        gPrefs.edit().putBoolean(PROFILES_MIGRATED, val).commit();
        return val;
    }

  /*  public static boolean isXposedDM() {
        return gPrefs.getBoolean(XPOSED_FIX_DM_LEAK, false);
    }

    public static boolean isXposedDM(boolean val) {
        gPrefs.edit().putBoolean(XPOSED_FIX_DM_LEAK, val).commit();
        return val;
    }*/

    public static boolean hasRoot() {
        return gPrefs.getBoolean(HAS_ROOT, false);
    }

    public static boolean hasRoot(boolean val) {
        gPrefs.edit().putBoolean(HAS_ROOT, val).commit();
        return val;
    }

   /* public static boolean activeNotification() {
        return gPrefs.getBoolean(ACTIVE_NOTIFICATION, true);
    }

    public static boolean activeNotification(boolean val) {
        gPrefs.edit().putBoolean(ACTIVE_NOTIFICATION, val).commit();
        return val;
    }

    public static boolean showLogToasts() {
        return gPrefs.getBoolean(SHOW_LOG_TOAST, false);
    }

    public static boolean showLogToasts(boolean val) {
        gPrefs.edit().putBoolean(SHOW_LOG_TOAST, val).commit();
        return val;
    }*/

    public static boolean fixLeak() {
        return gPrefs.getBoolean(FIX_START_LEAK, false);
    }

    public static boolean disableTaskerToast() {
        return gPrefs.getBoolean(DISABLE_TASKER_TOAST, false);
    }

    public static boolean enableIPv6() {
        return gPrefs.getBoolean(ENABLE_IPV6, true);
    }

    public static boolean enableIPv6(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_IPV6, val).commit();
        return val;
    }

    public static boolean controlIPv6() {
        return gPrefs.getBoolean(CONTROL_IPV6, false);
    }



   /* public static boolean blockIPv6() {
        return gPrefs.getBoolean(BLOCK_IPV6, false);
    }

    public static boolean blockIPv6(boolean val) {
        gPrefs.edit().putBoolean(BLOCK_IPV6, val).commit();
        return val;
    }*/

    public static boolean enableInbound() {
        return gPrefs.getBoolean(ENABLE_INBOUND, false);
    }

    public static boolean enableLogService() {
        return gPrefs.getBoolean(ENABLE_LOG_SERVICE, false);
    }

    public static boolean enableLogService(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_LOG_SERVICE, val).commit();
        return val;
    }

    public static int logPingTimeout() {
        return Integer.valueOf(gPrefs.getString(LOG_PING_TIMEOUT, "10"));
    }

    /*public static void logPingTimeout(int logPingTimeout) {
        gPrefs.edit().remove(LOG_PING_TIMEOUT);
        gPrefs.edit().putString(LOG_PING_TIMEOUT, logPingTimeout+"");
    }*/

    public static boolean enableAdmin() {
        return gPrefs.getBoolean(ENABLE_ADMIN, false);
    }

    public static boolean enableAdmin(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_ADMIN, val).commit();
        return val;
    }

    public static boolean showHost() {
        return gPrefs.getBoolean(SHOW_HOST, false);
    }

    public static boolean enableDeviceCheck() {
        return gPrefs.getBoolean(ENABLE_DEVICE_CHECK, false);
    }

    public static boolean enableDeviceCheck(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_DEVICE_CHECK, val).commit();
        return val;
    }

    public static boolean enableConfirm() {
        return gPrefs.getBoolean(ENABLE_CONFIRM, false);
    }

    public static boolean enableMultiProfile() {
        return gPrefs.getBoolean(ENABLE_MULTI_PROFILE, false);
    }

    public static boolean enableMultiProfile(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_MULTI_PROFILE, val).commit();
        return val;
    }

    public static boolean showUid() {
        return gPrefs.getBoolean(SHOW_UID, false);
    }

    public static boolean showFilter() {
        return gPrefs.getBoolean(SHOW_FILTER, false);
    }

    public static boolean disableIcons() {
        return gPrefs.getBoolean(DISABLE_ICONS, false);
    }

    public static String ip_path() {
        return gPrefs.getString(IPTABLES_PATH, "system");
    }

    public static String dns_proxy() {
        return gPrefs.getString(DNS_PROXY, "auto");
    }

    public static String bb_path() {
        return gPrefs.getString(BUSYBOX_PATH, "builtin");
    }

    public static String locale() {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getString(LANGUAGE, "en");
    }

    /*public static String logDmsg() {
        return gPrefs.getString(LOG_DMESG, "OS");
    }

    public static String logDmsg(String val) {
        gPrefs.edit().putString(LOG_DMESG, val).commit();
        return val;
    }*/

    public static String sortBy() {
        return gPrefs.getString(SORT_BY, "s0");
    }

    public static void sortBy(String sort) {
        gPrefs.edit().putString(SORT_BY, sort).commit();
    }

    public static String storedProfile() {
        return gPrefs.getString(LAST_STORED_PROFILE, "AFWallPrefs");
    }

    public static String storedProfile(String val) {
        gPrefs.edit().putString(LAST_STORED_PROFILE, val).commit();
        return val;
    }

    public static int userColor() {
        if (G.getSelectedTheme().equals("L")) {
            return Color.parseColor("#000000");
        } else {
            return Color.parseColor("#FFFFFF");
        }
    }

    public static int sysColor() {
        if (G.getSelectedTheme().equals("L")) {
            return gPrefs.getInt(SYSTEM_APP_COLOR, Color.parseColor("#000000"));
        } else {
            return gPrefs.getInt(SYSTEM_APP_COLOR, Color.parseColor("#0F9D58"));
        }
    }

    /*public static int primaryColor() {
            return gPrefs.getInt(PRIMARY_COLOR, Color.parseColor("#259b24"));
    }

    public static int primaryDarkColor() {
        return gPrefs.getInt(PRIMARY_DARK_COLOR, Color.parseColor("#0a7e07"));
    }*/

    public static boolean activeRules() {
        return gPrefs.getBoolean(ACTIVE_RULES, true);
    }

    public static boolean addDelay() {
        //default enable add delay for Q
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            return gPrefs.getBoolean(ADD_DELAY, true);
        }
        return gPrefs.getBoolean(ADD_DELAY, false);
    }

    public static boolean startupDelay() {
        return gPrefs.getBoolean(STARTUP_DELAY, false);
    }

    public static boolean enableStealthPattern() {
        return gPrefs.getBoolean(PATTERN_STEALTH, false);
    }

    public static int getMaxPatternTry() {
        return Integer.parseInt(gPrefs.getString(PATTERN_MAX_TRY, "3"));
    }

    public static boolean isMultiUser() {
        return gPrefs.getBoolean(MULTI_USER, false);
    }

    public static void setMultiUserId(int val) {
        gPrefs.edit().putLong(MULTI_USER_ID, val).commit();
    }

    public static Long getMultiUserId() {
        return gPrefs.getLong(MULTI_USER_ID, 0);
    }

    public static boolean applyOnSwitchProfiles() {
        return gPrefs.getBoolean(PROFILE_SWITCH, false);
    }

    public static String logTargets() {
        return gPrefs.getString(LOG_TARGETS, null);
    }

    public static String logTargets(String val) {
        gPrefs.edit().putString(LOG_TARGETS, val).commit();
        return val;
    }

    public static String logTarget() {
        return gPrefs.getString(LOG_TARGET, "").trim();
    }

    public static String logTarget(String val) {
        gPrefs.edit().putString(LOG_TARGET, val).commit();
        return val;
    }


    public static void saveSelectedFilter(int i) {
        gPrefs.edit().putInt(SELECTED_FILTER, i).commit();
    }

    public static int selectedFilter() {
        return gPrefs.getInt(SELECTED_FILTER, 99);
    }



    public static int appVersion() {
        return gPrefs.getInt(APP_VERSION, 0);
    }

    public static int appVersion(int val) {
        gPrefs.edit().putInt(APP_VERSION, val).commit();
        return val;
    }

    public static int ruleTextSize() {
        return gPrefs.getInt("ruleTextSize", 32);
    }

    public static int ruleTextSize(int val) {
        gPrefs.edit().putInt("ruleTextSize", val).commit();
        return val;
    }

    public static boolean oldLogView(boolean val) {
        gPrefs.edit().putBoolean("oldLogView", val).commit();
        return val;
    }

    public static boolean oldLogView() {
        return gPrefs.getBoolean("oldLogView", false);
    }

    public static boolean isDo(boolean val) {
        gPrefs.edit().putBoolean(REG_DO, val).commit();
        return val;
    }

    public static boolean enableRoam() {
        return gPrefs.getBoolean(ENABLE_ROAM, false);
    }

    public static boolean enableRoam(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_ROAM, val).commit();
        return val;
    }

    public static boolean enableVPN() {
        return gPrefs.getBoolean(ENABLE_VPN, false);
    }

    public static boolean enableVPN(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_VPN, val).commit();
        return val;
    }

    public static boolean enableTether() {
        return gPrefs.getBoolean(ENABLE_TETHER, false);
    }

    public static boolean enableTether(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_TETHER, val).commit();
        return val;
    }

    public static boolean enableLAN() {
        return gPrefs.getBoolean(ENABLE_LAN, true);
    }

    public static boolean enableLAN(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_LAN, val).commit();
        return val;
    }

    public static boolean enableTor() {
        return gPrefs.getBoolean(ENABLE_TOR, false);
    }

    public static boolean enableTor(boolean val) {
        gPrefs.edit().putBoolean(ENABLE_TOR, val).commit();
        return val;
    }


    public static boolean isDonate() {
        return BuildConfig.APPLICATION_ID.equals("dev.ukanth.ufirewall.donate");
    }

    public static boolean isDoKey(Context ctx) {
        if (!gPrefs.getBoolean(REG_DO, false)) {
            try {
                ApplicationInfo app = ctx.getPackageManager().getApplicationInfo("dev.ukanth.ufirewall.donatekey", 0);
                if (app != null) {
                    gPrefs.edit().putBoolean(REG_DO, true).commit();
                }
            } catch (PackageManager.NameNotFoundException | NullPointerException e) {
                gPrefs.edit().putBoolean(REG_DO, false).commit();
            }
            /*if(BuildConfig.DONATE){
                gPrefs.edit().putBoolean(REG_DO, true).commit();
            }*/
        }
        return gPrefs.getBoolean(REG_DO, false);
    }


    public static int getWidgetX(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        int defaultX = dm.widthPixels;
        String x = gPrefs.getString(WIDGET_X, defaultX + "");
        try {
            defaultX = Integer.parseInt(x);
        } catch (Exception exception) {
        }
        return defaultX;
    }

    public static int getCustomDelay() {
        return gPrefs.getInt(CUSTOM_DELAY_SECONDS, 5) * 1000;
    }

    public static int getWidgetY(Context ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(dm);
        int defaultY = dm.heightPixels;
        String y = gPrefs.getString(WIDGET_Y, defaultY + "");
        try {
            defaultY = Integer.parseInt(y);
        } catch (Exception exception) {
        }
        return defaultY;
    }


    //new protection list
    public static String protectionLevel() {
        if (gPrefs.getString(PROTECTION_OPTION, "p0").equals("Disable")) {
            gPrefs.edit().putString(PROTECTION_OPTION, "p0").commit();
        }
        return gPrefs.getString(PROTECTION_OPTION, "p0");
    }

    /*public static void setBlockedNotifyApps(List<Integer> list) {
        String listString = list.toString();
        listString = listString.substring(1, listString.length() - 1);
        gPrefs.edit().putString(BLOCKED_NOTIFICATION, listString).commit();
    }*/

    public static void storeDefaultConnection(List<Integer> list1, List<Integer> list2, int modeType) {
        // store to DB

        for (Integer uid : list1) {
            DefaultConnectionPref preference = new DefaultConnectionPref();
            preference.setUid(uid);
            preference.setState(true);
            preference.setModeType(modeType);
            FlowManager.getDatabase(DefaultConnectionPrefDB.class).beginTransactionAsync(databaseWrapper -> preference.save(databaseWrapper)).build().execute();
        }
        for (Integer uid : list2) {
            DefaultConnectionPref preference = new DefaultConnectionPref();
            preference.setUid(uid);
            preference.setState(false);
            preference.setModeType(modeType);
            FlowManager.getDatabase(DefaultConnectionPrefDB.class).beginTransactionAsync(databaseWrapper -> preference.save(databaseWrapper)).build().execute();
        }
    }

    public static List<Integer> readDefaultConnection(int modeType) {
        List<DefaultConnectionPref> list = SQLite.select()
                .from(DefaultConnectionPref.class)
                .queryList();
        List<Integer> listSelected = new ArrayList<>();
        for (DefaultConnectionPref pref : list) {
            if (pref.isState() && pref.getModeType() == modeType) {
                listSelected.add(pref.getUid());
            }
        }
        return listSelected;
    }

   /* public static List<String> getBlockedNotifyApps() {
        String blockedApps = gPrefs.getString(BLOCKED_NOTIFICATION, null);
        List<String> data = new ArrayList<String>();
        if (blockedApps != null) {
            for (String id : blockedApps.split(",")) {
                data.add(id.trim());
            }
        }
        return data;
    }*/

    /*public static List<Integer> getBlockedNotifyList() {
        List<Integer> data = new ArrayList<Integer>();
        try {
            String blockedApps = gPrefs.getString(BLOCKED_NOTIFICATION, null);
            if (blockedApps != null) {
                String[] list = blockedApps.split(",");
                if (list.length > 0) {
                    for (String s : list) {
                        if (s != null && s.trim().length() > 0) {
                            try {
                                if (android.text.TextUtils.isDigitsOnly(s.trim())) {
                                    data.add(Integer.parseInt(s.trim()));
                                }
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return data;
    }*/

    //This method is used for Xposed
    public static boolean isXposedEnabled() {
        // will be used by XPosed to return true
        return false;
    }

    @Override
    public void onCreate() {
        instance = this;
        //Shell.setFlags(Shell.ROOT_SHELL);
        //Shell.setFlags(Shell.FLAG_REDIRECT_STDERR);
        //Shell.verboseLogging(BuildConfig.DEBUG);
        registerActivityLifecycleCallbacks(this);
        super.onCreate();
        try {
            FlowManager.init(new FlowConfig.Builder(this)
                    .openDatabasesOnInit(true).build());
        } catch (SQLiteCantOpenDatabaseException e) {
            Log.i(TAG, "unable to open database - exception");
        }
        ctx = this.getApplicationContext();
        reloadPrefs();

        //registerNetworkObserver();
    }


    public static void reloadPrefs() {
        gPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        String profileName = Api.DEFAULT_PREFS_NAME;
        //int pos = storedPosition();
        //int profileCount = getProfileCount();
        if (enableMultiProfile()) {
            profileName = storedProfile();
        }

        Log.i(Api.TAG, "Selected Profile: " + profileName);
        Api.PREFS_NAME = profileName;

        pPrefs = ctx.getSharedPreferences(profileName, Context.MODE_PRIVATE);
        sPrefs = ctx.getSharedPreferences(AFWALL_STATUS/* sic */, Context.MODE_PRIVATE);
    }

    public static void reloadProfile() {
        reloadPrefs();
        Api.applications = null;
    }

    public static void setProfile(boolean newEnableMultiProfile, String profileName) {
        enableMultiProfile(newEnableMultiProfile);
        storedProfile(profileName);
        reloadProfile();
    }

    public static boolean clearSharedPreferences(Context ctx, String preferenceName) {
        File dir = new File(ctx.getFilesDir().getParent() + "/shared_prefs/");
        String[] children = dir.list();
        for (int i = 0; i < children.length; i++) {
            // clear each of the prefrances
            if (children[i].replace(".xml", "").equals(preferenceName)) {
                return new File(dir, children[i]).delete();
            }
        }
        return true;
    }

    public static boolean removeAdditionalProfile(String profileName) {
        //after remove clear all the data inside the custom profile
        if (ctx != null) {
            //actually delete the file from disk
            if (clearSharedPreferences(ctx, profileName)) {
                String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
                if (!previousProfiles.isEmpty()) {
                    List<String> items = new ArrayList<String>(Arrays.asList(previousProfiles.split("\\s*,\\s*")));
                    if (items.remove(profileName)) {
                        gPrefs.edit().putString(ADDITIONAL_PROFILES, TextUtils.join(",", items)).commit();
                        return true;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    public static List<String> getAdditionalProfiles() {
        String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
        List<String> items = new ArrayList<>();
        if (!previousProfiles.isEmpty()) {
            items = new ArrayList<String>(Arrays.asList(previousProfiles.split("\\s*,\\s*")));
        }
        return items;
    }

    public static List<String> getDefaultProfiles() {
        return new ArrayList<String>(Arrays.asList(default_profiles));
    }

    public static void updateLogNotification(int uid, boolean isChecked) {
        //update logic here
        LogPreference preference = new LogPreference();
        preference.setUid(uid);
        preference.setTimestamp(System.currentTimeMillis());
        preference.setDisable(isChecked);
        FlowManager.getDatabase(LogPreferenceDB.class).beginTransactionAsync(databaseWrapper -> preference.save(databaseWrapper)).build().execute();
    }

    /*public static void isNotificationMigrated(boolean b) {
        gPrefs.edit().putBoolean("NewDBNotification", b).commit();
        gPrefs.edit().putString(BLOCKED_NOTIFICATION, "").commit();
    }*/

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    public static void activityResumed() {
        activityVisible = true;
    }

    public static void activityPaused() {
        activityVisible = false;
    }

    private static boolean activityVisible;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    public static boolean getPrivateDnsStatus() {
        return privateDns;
    }

    private static ConnectivityManager.NetworkCallback callback = null;

    public static  void registerPrivateLink() {
        if(!enabledPrivateLink) {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if(callback == null) {
                callback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                        super.onLinkPropertiesChanged(network, linkProperties);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if(linkProperties.isPrivateDnsActive() != privateDns) {
                                Log.i(Api.TAG, "Private DNS status changed: " + privateDns);
                                privateDns = linkProperties.isPrivateDnsActive();
                                InterfaceTracker.applyRules("Private DNS changed.. reapplying rules");
                            }
                        }
                    }
                };
            }
            cm.registerNetworkCallback(new NetworkRequest.Builder().build(), callback);
            enabledPrivateLink = true;
        } else{
            Log.i(TAG, "Private link has registered already");
        }
    }
}
