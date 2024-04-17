/**
 * All iptables "communication" is handled by this class.
 * <p>
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
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
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.2
 */

package dev.ukanth.ufirewall;

import static dev.ukanth.ufirewall.util.G.ctx;
import static dev.ukanth.ufirewall.util.G.ipv4Fwd;
import static dev.ukanth.ufirewall.util.G.ipv4Input;
import static dev.ukanth.ufirewall.util.G.ipv6Fwd;
import static dev.ukanth.ufirewall.util.G.ipv6Input;
import static dev.ukanth.ufirewall.util.G.showAllApps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.sql.language.Select;
import com.stericson.roottools.RootTools;
import com.topjohnwu.superuser.Shell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import dev.ukanth.ufirewall.MainActivity.GetAppList;
import dev.ukanth.ufirewall.MultiUser;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPref;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPref_Table;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.JsonHelper;
import dev.ukanth.ufirewall.widget.StatusWidget;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
    /**
     * application logcat tag
     */
    public static final String TAG = "AFWall";

    /**
     * special application UID used to indicate "any application"
     */
    public static final int SPECIAL_UID_ANY = -10;
    /**
     * special application UID used to indicate the Linux Kernel
     */
    public static final int SPECIAL_UID_KERNEL = -11;
    /**
     * special application UID used for dnsmasq DHCP/DNS
     */
    public static final int SPECIAL_UID_TETHER = -12;
    /** special application UID used for netd DNS proxy */
    //public static final int SPECIAL_UID_DNSPROXY	= -13;
    /**
     * special application UID used for NTP
     */
    public static final int SPECIAL_UID_NTP = -14;

    public static final int NOTIFICATION_ID = 1;
    public static final String PREF_FIREWALL_STATUS = "AFWallStaus";
    public static final String DEFAULT_PREFS_NAME = "AFWallPrefs";
    public static final String CACHE_PREFS_NAME = "AFWallCache";
    //for import/export rules
    //revertback to old approach for performance
    public static final String PREF_3G_PKG_UIDS = "AllowedPKG3G_UIDS";
    public static final String PREF_WIFI_PKG_UIDS = "AllowedPKGWifi_UIDS";
    public static final String PREF_ROAMING_PKG_UIDS = "AllowedPKGRoaming_UIDS";
    public static final String PREF_VPN_PKG_UIDS = "AllowedPKGVPN_UIDS";
    public static final String PREF_TETHER_PKG_UIDS = "AllowedPKGTether_UIDS";
    public static final String PREF_LAN_PKG_UIDS = "AllowedPKGLAN_UIDS";
    public static final String PREF_TOR_PKG_UIDS = "AllowedPKGTOR_UIDS";
    public static final String PREF_CUSTOMSCRIPT = "CustomScript";
    public static final String PREF_CUSTOMSCRIPT2 = "CustomScript2"; // Executed on shutdown
    public static final String PREF_MODE = "BlockMode";
    public static final String PREF_ENABLED = "Enabled";
    // Modes
    public static final String MODE_WHITELIST = "whitelist";
    public static final String MODE_BLACKLIST = "blacklist";
    public static final String STATUS_CHANGED_MSG = "dev.ukanth.ufirewall.intent.action.STATUS_CHANGED";
    public static final String TOGGLE_REQUEST_MSG = "dev.ukanth.ufirewall.intent.action.TOGGLE_REQUEST";
    public static final String CUSTOM_SCRIPT_MSG = "dev.ukanth.ufirewall.intent.action.CUSTOM_SCRIPT";
    // Message extras (parameters)
    public static final String STATUS_EXTRA = "dev.ukanth.ufirewall.intent.extra.STATUS";
    public static final String SCRIPT_EXTRA = "dev.ukanth.ufirewall.intent.extra.SCRIPT";
    public static final String SCRIPT2_EXTRA = "dev.ukanth.ufirewall.intent.extra.SCRIPT2";
    public static final int ERROR_NOTIFICATION_ID = 9;
    private static final int WIFI_EXPORT = 0;
    private static final int DATA_EXPORT = 1;
    private static final int ROAM_EXPORT = 2;
    // Messages
    private static final int VPN_EXPORT = 3;
    private static final int TETHER_EXPORT = 6;
    private static final int LAN_EXPORT = 4;
    private static final int TOR_EXPORT = 5;
    private static final String[] ITFS_WIFI = InterfaceTracker.ITFS_WIFI;
    private static final String[] ITFS_3G = InterfaceTracker.ITFS_3G;
    private static final String[] ITFS_VPN = InterfaceTracker.ITFS_VPN;
    private static final String[] ITFS_TETHER = InterfaceTracker.ITFS_TETHER;
    // iptables can exit with status 4 if two processes tried to update the same table
    private static final int IPTABLES_TRY_AGAIN = 4;
    private static final String[] dynChains = {"-3g-postcustom", "-3g-fork", "-wifi-postcustom", "-wifi-fork"};
    private static final String[] natChains = {"", "-tor-check", "-tor-filter"};
    private static final String[] staticChains = {"", "-input", "-3g", "-wifi", "-reject", "-vpn", "-3g-tether", "-3g-home", "-3g-roam", "-wifi-tether", "-wifi-wan", "-wifi-lan", "-tor", "-tor-reject", "-tether"};
    private static boolean globalStatus = false;

    public static List<Integer> getListOfUids() {
        return listOfUids;
    }

    private static List<Integer> listOfUids = new ArrayList<>();



    private static final Pattern dual_pattern = Pattern.compile("package:(.*) uid:(.*)", Pattern.MULTILINE);

    /**
     * @brief Special user/group IDs that aren't associated with
     * any particular app.
     * <p>
     * See:
     * include/private/android_filesystem_config.h
     * in platform/system/core.git.
     * <p>
     * The accounts listed below are the only ones from
     * android_filesystem_config.h that are known to be used as
     * the UID of a process that uses the network.  The other
     * accounts in that .h file are either:
     * * used as supplemental group IDs for granting extra
     * privileges to apps,
     * * used as UIDs of processes that don't need the network,
     * or
     * * have not yet been reported by users as needing the
     * network.
     * <p>
     * The list is sorted in ascending UID order.
     */
    private static final String[] specialAndroidAccounts = {
            "root",
            "adb",
            "media",
            "vpn",
            "drm",
            "gps",
            "shell"
    };
    private static final Pattern p = Pattern.compile("UserHandle\\{(.*)\\}");
    // Preferences
    public static String PREFS_NAME = "AFWallPrefs";
    // Cached applications
    public static List<PackageInfoData> applications = null;
    public static Set<String> recentlyInstalled = new HashSet<>();
    //for custom scripts
    //public static String ipPath = null;
    public static String bbPath = null;
    private static final String charsetName = "UTF8";
    private static final String algorithm = "DES";
    private static final int base64Mode = Base64.DEFAULT;
    private static String AFWALL_CHAIN_NAME = "afwall";
    private static Map<String, Integer> specialApps = null;
    private static boolean rulesUpToDate = false;

    public static void setRulesUpToDate(boolean rulesUpToDate) {
        Api.rulesUpToDate = rulesUpToDate;
    }

    // returns c.getString(R.string.<acct>_item)
    public static String getSpecialDescription(Context ctx, String acct) {
        try {
            int rid = ctx.getResources().getIdentifier(acct + "_item", "string", ctx.getPackageName());
            return ctx.getString(rid);
        } catch (Resources.NotFoundException exception) {
            return null;
        }
    }

    public static String getSpecialDescriptionSystem(Context ctx, String packageName) {
        switch (packageName) {
            case "any":
                return ctx.getString(R.string.all_item);
            case "kernel":
                return ctx.getString(R.string.kernel_item);
            case "tether":
                return ctx.getString(R.string.tethering_item);
            case "ntp":
                return ctx.getString(R.string.ntp_item);
        }
        return "";
    }

    /**
     * Display a simple alert box
     *
     * @param ctx     context
     * @param msgText message
     */
    public static void toast(final Context ctx, final CharSequence msgText) {
        if (ctx != null) {
            Handler mHandler = new Handler(Looper.getMainLooper());
            mHandler.post(() -> Toast.makeText(G.getContext(), msgText, Toast.LENGTH_SHORT).show());
        }
    }

    public static void toast(final Context ctx, final CharSequence msgText, final int toastlen) {
        if (ctx != null) {
            Handler mHandler = new Handler(Looper.getMainLooper());
            mHandler.post(() -> Toast.makeText(G.getContext(), msgText, toastlen).show());
        }
    }

    public static String getBinaryPath(Context ctx, boolean setv6) {
        boolean builtin;
        String ip_path = G.ip_path();

        if (ip_path.equals("system")) {
            builtin = false;
        } else if(ip_path.equals("builtin")) {
            builtin = true;
        } else{
            builtin = false;
        }

        String dir = "";
        if (builtin) {
            dir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
        }

        String ipPath = dir + (setv6 ?  "ip6tables" : "iptables" );

        /*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            dir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
            ipPath = dir + "run_pie " + dir + (setv6 ? "ip6tables" : "iptables");
        }*/
        if (Api.bbPath == null) {
            Api.bbPath = getBusyBoxPath(ctx, true);
        }
        return ipPath;
    }

    /**
     * Determine toybox/busybox or built in
     *
     * @param ctx
     * @param considerSystem
     * @return
     */
    public static String getBusyBoxPath(Context ctx, boolean considerSystem) {

        if (G.bb_path().equals("system") && considerSystem) {
            return "busybox ";
        } else {
            String dir = ctx.getDir("bin", 0).getAbsolutePath();
            return dir + "/busybox ";
        }
    }

    /**
     * Get NFLog Path
     *
     * @param ctx
     * @returnC
     */
    public static String getNflogPath(Context ctx) {
        String dir = ctx.getDir("bin", 0).getAbsolutePath();
        return dir + "/nflog ";
    }

    /**
     * Copies a raw resource file, given its ID to the given location
     *
     * @param ctx   context
     * @param resid resource id
     * @param file  destination file
     * @param mode  file permissions (E.g.: "755")
     * @throws IOException          on error
     * @throws InterruptedException when interrupted
     */
    private static void copyRawFile(Context ctx, int resid, File file, String mode) throws IOException, InterruptedException {
        final String abspath = file.getAbsolutePath();
        // Write the iptables binary
        final FileOutputStream out = new FileOutputStream(file);
        final InputStream is = ctx.getResources().openRawResource(resid);
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        is.close();
        // Change the permissions

        Runtime.getRuntime().exec("chmod " + mode + " " + abspath).waitFor();
    }

    /**
     * Look up uid for each user by name, and if he exists, append an iptables rule.
     *
     * @param listCommands current list of iptables commands to execute
     * @param users        list of users to whom the rule applies
     * @param prefix       "iptables" command and the portion of the rule preceding "-m owner --uid-owner X"
     * @param suffix       the remainder of the iptables rule, following "-m owner --uid-owner X"
     */
    private static void addRuleForUsers(List<String> listCommands, String[] users, String prefix, String suffix) {
        for (String user : users) {
            int uid = android.os.Process.getUidForName(user);
            if (uid != -1)
                listCommands.add(prefix + " -m owner --uid-owner " + uid + " " + suffix);
        }
    }

    private static void addRulesForUidlist(List<String> cmds, List<Integer> uids, String chain, boolean whitelist) {
        String action = whitelist ? " -j RETURN" : " -j " + AFWALL_CHAIN_NAME + "-reject";

        if (uids.contains(SPECIAL_UID_ANY)) {
            if (!whitelist) {
                cmds.add("-A " + chain + action);
            }
            // FIXME: in whitelist mode this blocks everything
        } else {
            for (Integer uid : uids) {
                if (uid != null && uid >= 0) {
                    cmds.add("-A " + chain + " -m owner --uid-owner " + uid + action);
                }
            }

			/*// netd runs as root, and on Android 4.3+ it handles all DNS queries
            if (uids.indexOf(SPECIAL_UID_DNSPROXY) >= 0) {
				addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53",  action);
			}*/

            String pref = G.dns_proxy();

            if (whitelist) {
                if (pref.equals("disable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p tcp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                } else {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p tcp --dport 53", " -j RETURN");
                }
            } else {
                if (pref.equals("disable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p tcp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                } else if (pref.equals("enable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p tcp --dport 53", " -j RETURN");
                }
            }


            // NTP service runs as "system" user
            if (uids.contains(SPECIAL_UID_NTP)) {
                addRuleForUsers(cmds, new String[]{"system"}, "-A " + chain + " -p udp --dport 123", action);
            }


            if (G.getPrivateDnsStatus() && !G.dns_proxy().equals("disable")) {
                cmds.add("-A " + chain + " -p tcp --dport 853" + " -j ACCEPT");
                // disabling HTTPS over DNS
                //cmds.add("-A " + chain + " -p tcp --dport 443" + " -j ACCEPT");
            }

            boolean kernel_checked = uids.contains(SPECIAL_UID_KERNEL);
            if (whitelist) {
                if (kernel_checked) {
                    // reject any other UIDs, but allow the kernel through
                    cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j " + AFWALL_CHAIN_NAME + "-reject");
                } else {
                    // kernel is blocked so reject everything
                    cmds.add("-A " + chain + " -j " + AFWALL_CHAIN_NAME + "-reject");
                }
            } else {
                if (kernel_checked) {
                    // allow any other UIDs, but block the kernel
                    cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j RETURN");
                    cmds.add("-A " + chain + " -j " + AFWALL_CHAIN_NAME + "-reject");
                }
            }
        }
    }

    private static void addRejectRules(List<String> cmds) {
        // set up reject chain to log or not log
        // this can be changed dynamically through the Firewall Logs activity

        if (G.enableLogService()) {
            if (G.logTarget().trim().equals("LOG")) {
                //cmds.add("-A " + AFWALL_CHAIN_NAME  + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL-ALLOW}\" --log-level 4 --log-uid");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL}\" --log-level 4 --log-uid  --log-tcp-options --log-ip-options");
            } else if (G.logTarget().trim().equals("NFLOG")) {
                //cmds.add("-A " + AFWALL_CHAIN_NAME + " -j NFLOG --nflog-prefix \"{AFL-ALLOW}\" --nflog-group 40");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40");
            }
        }
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j REJECT");
    }

    private static void addTorRules(List<String> cmds, List<Integer> uids, Boolean whitelist, Boolean ipv6) {
        for (Integer uid : uids) {
            if (uid != null && uid >= 0) {
                if (G.enableInbound() || ipv6) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-tor-reject -m owner --uid-owner " + uid + " -j afwall-reject");
                }
                if (!ipv6) {
                    cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-check -m owner --uid-owner " + uid + " -j " + AFWALL_CHAIN_NAME + "-tor-filter");
                }
            }
        }
        if (ipv6) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + " -j " + AFWALL_CHAIN_NAME + "-tor-reject");
        } else {
            Integer socks_port = 9050;
            Integer http_port = 8118;
            Integer dns_port = 5400;
            Integer tcp_port = 9040;
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -d 127.0.0.1 -p tcp --dport " + socks_port + " -j RETURN");
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -d 127.0.0.1 -p tcp --dport " + http_port + " -j RETURN");
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -p udp --dport 53 -j REDIRECT --to-ports " + dns_port);
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -p tcp --tcp-flags FIN,SYN,RST,ACK SYN -j REDIRECT --to-ports " + tcp_port);
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -j MARK --set-mark 0x500");
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + " -j " + AFWALL_CHAIN_NAME + "-tor-check");
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-tor -m mark --mark 0x500 -j " + AFWALL_CHAIN_NAME + "-reject");
            cmds.add("-A " + AFWALL_CHAIN_NAME + " -j " + AFWALL_CHAIN_NAME + "-tor");
        }
        if (G.enableInbound()) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-input -j " + AFWALL_CHAIN_NAME + "-tor-reject");
        }
    }

    private static void addCustomRules(String prefName, List<String> cmds) {
        String[] customRules = G.pPrefs.getString(prefName, "").split("[\\r\\n]+");
        for (String s : customRules) {
            if (s.matches(".*\\S.*")) {
                cmds.add("#LITERAL# " + s);
            }
        }
    }

    /**
     * Reconfigure the firewall rules based on interface changes seen at runtime: tethering
     * enabled/disabled, IP address changes, etc.  This should only affect a small number of
     * rules; we want to avoid calling applyIptablesRulesImpl() too often since applying
     * 100+ rules is expensive.
     *
     * @param ctx  application context
     * @param cmds command list
     */
    private static void addInterfaceRouting(Context ctx, List<String> cmds, boolean ipv6) {
        try {
            //force only for v4
            final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx, !ipv6);
            final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
            for (String s : dynChains) {
                cmds.add("-F " + AFWALL_CHAIN_NAME + s);
            }

            if (whitelist) {
                // always allow the DHCP client full wifi access
                addRuleForUsers(cmds, new String[]{"dhcp", "wifi"}, "-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom", "-j RETURN");
            }

            if (cfg.isWifiTethered) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-tether");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-tether");
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-fork");
            }

            // TODO: tether and Usb tether

            if (G.enableLAN() && !cfg.isWifiTethered) {
                if (ipv6) {
                    if (!cfg.lanMaskV6.equals("")) {
                        cmds.add("-A afwall-wifi-fork -d " + cfg.lanMaskV6 + " -j afwall-wifi-lan");
                        cmds.add("-A afwall-wifi-fork '!' -d " + cfg.lanMaskV6 + " -j afwall-wifi-wan");
                    } else {
                        Log.i(TAG, "no ipv6 found: " + G.enableIPv6() + "," + cfg.lanMaskV6);
                    }
                } else {
                    if (!cfg.lanMaskV4.equals("")) {
                        cmds.add("-A afwall-wifi-fork -d " + cfg.lanMaskV4 + " -j afwall-wifi-lan");
                        cmds.add("-A afwall-wifi-fork '!' -d " + cfg.lanMaskV4 + " -j afwall-wifi-wan");
                    } else {
                        Log.i(TAG, "no ipv4 found:" + G.enableIPv6() + "," + cfg.lanMaskV4);
                    }
                }
                if (cfg.lanMaskV4.equals("") && cfg.lanMaskV6.equals("")) {
                    Log.i(TAG, "No ipaddress found for LAN");
                    // lets find one more time
                    //atleast allow internet - don't block completely
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
                }
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
            }

            if (G.enableRoam() && cfg.isRoaming) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-roam");
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-home");
            }


        } catch (Exception e) {
            Log.i(TAG, "Exception while applying shortRules " + e.getMessage());
        }

    }

    public static String getSpecialAppName(int uid) {
        List<PackageInfoData> packageInfoData = getSpecialData();
        for (PackageInfoData infoData : packageInfoData) {
            if (infoData.uid == uid) {
                return infoData.names.get(0);
            }
        }
        return ctx.getString(R.string.unknown_item);
    }


    private static void applyShortRules(Context ctx, List<String> cmds, boolean ipv6) {
        Log.i(TAG, "Setting OUTPUT chain to DROP");
        cmds.add("-P OUTPUT DROP");
        /*FIXME: Adding custom rules might increase the time */
        Log.i(TAG, "Applying custom rules");
        addCustomRules(Api.PREF_CUSTOMSCRIPT, cmds);
        addInterfaceRouting(ctx, cmds, ipv6);
        Log.i(TAG, "Setting OUTPUT chain to ACCEPT");
        cmds.add("-P OUTPUT ACCEPT");
    }


    /**
     * Purge and re-add all rules (internal implementation).
     *
     * @param ctx        application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
    private static boolean applyIptablesRulesImpl(final Context ctx, RuleDataSet ruleDataSet, final boolean showErrors,
                                                  List<String> out, boolean ipv6) {
        if (ctx == null) {
            return false;
        }

        assertBinaries(ctx, showErrors);
        if (G.isMultiUser()) {
            //FIXME: after setting this, we need to flush the iptables ?
            if (G.getMultiUserId() > 0) {
                AFWALL_CHAIN_NAME = "afwall" + G.getMultiUserId();
            }
        }
        final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

        List<String> cmds = new ArrayList<String>();

        Log.i(TAG, "Constructing rules for " + (ipv6 ? "v6": "v4"));

        //check before make them ACCEPT state
        if (ipv4Input() || (ipv6 && ipv6Input())) {
            cmds.add("-P INPUT ACCEPT");
        }

        if (ipv4Fwd() || (ipv6 && ipv6Fwd())) {
            cmds.add("-P FORWARD ACCEPT");
        }

        try {
            // prevent data leaks due to incomplete rules
            cmds.add("-P OUTPUT DROP");

            for (String s : staticChains) {
                cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
                cmds.add("-F " + AFWALL_CHAIN_NAME + s);
            }
            for (String s : dynChains) {
                cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
            }

            cmds.add("#NOCHK# -D OUTPUT -j " + AFWALL_CHAIN_NAME);
            cmds.add("-I OUTPUT 1 -j " + AFWALL_CHAIN_NAME);


            if (G.enableInbound()) {
                cmds.add("#NOCHK# -D INPUT -j " + AFWALL_CHAIN_NAME + "-input");
                cmds.add("-I INPUT 1 -j " + AFWALL_CHAIN_NAME + "-input");
            }

            if (G.enableTor() && !ipv6) {
                for (String s : natChains) {
                    cmds.add("#NOCHK# -t nat -N " + AFWALL_CHAIN_NAME + s);
                    cmds.add("-t nat -F " + AFWALL_CHAIN_NAME + s);
                }
                cmds.add("#NOCHK# -t nat -D OUTPUT -j " + AFWALL_CHAIN_NAME);
                cmds.add("-t nat -I OUTPUT 1 -j " + AFWALL_CHAIN_NAME);
            }

            // custom rules in afwall-{3g,wifi,reject} supersede everything else
            addCustomRules(Api.PREF_CUSTOMSCRIPT, cmds);

            cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g -j " + AFWALL_CHAIN_NAME + "-3g-postcustom");
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi -j " + AFWALL_CHAIN_NAME + "-wifi-postcustom");
            addRejectRules(cmds);

            if (G.enableInbound()) {
                // we don't have any rules in the INPUT chain prohibiting inbound traffic, but
                // local processes can't reply to half-open connections without this rule
                cmds.add("-A afwall -m state --state ESTABLISHED -j RETURN");
                cmds.add("-A afwall-input -m state --state ESTABLISHED -j RETURN");
            }

            addInterfaceRouting(ctx, cmds, ipv6);

            // send wifi, 3G, VPN packets to the appropriate dynamic chain based on interface
            if (G.enableVPN()) {
                // if !enableVPN then we ignore those interfaces (pass all traffic)
                for (final String itf : ITFS_VPN) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-vpn");
                }
                // KitKat policy based routing - see:
                // http://forum.xda-developers.com/showthread.php?p=48703545
                // This covers mark range 0x3c - 0x47.  The official range is believed to be
                // 0x3c - 0x45 but this is close enough.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -m mark --mark 0x3c/0xfffc -g " + AFWALL_CHAIN_NAME + "-vpn");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -m mark --mark 0x40/0xfff8 -g " + AFWALL_CHAIN_NAME + "-vpn");
                }
            }

            if (G.enableTether()) {
                for (final String itf : ITFS_TETHER) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-tether");
                }
            }

            for (final String itf : ITFS_WIFI) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-wifi");
            }

            for (final String itf : ITFS_3G) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-3g");
            }

            // special rules to allow tethering
            // note that this can only blacklist DNS/DHCP services, not all tethered traffic
            String[] users_dhcp = {"root", "nobody", "network_stack"};
            String[] users_dns = {"root", "nobody", "dns_tether"};
            String action = " -j " + (whitelist ? "RETURN" : AFWALL_CHAIN_NAME + "-reject");

            if (containsUidOrAny(ruleDataSet.wifiList, SPECIAL_UID_TETHER)) {
                // DHCP replies to client
                addRuleForUsers(cmds, users_dhcp, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=67 --dport=68" + action);
                // DNS replies to client
                addRuleForUsers(cmds, users_dns, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=53" + action);
                addRuleForUsers(cmds, users_dns, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p tcp --sport=53" + action);

            }
            if (containsUidOrAny(ruleDataSet.tetherList, SPECIAL_UID_TETHER)) {
                // DHCP replies to client
                addRuleForUsers(cmds, users_dhcp, "-A " + AFWALL_CHAIN_NAME + "-tether", "-p udp --sport=67 --dport=68" + action);
                // DNS replies to client
                addRuleForUsers(cmds, users_dns, "-A " + AFWALL_CHAIN_NAME + "-tether", "-p udp --sport=53" + action);
                addRuleForUsers(cmds, users_dns, "-A " + AFWALL_CHAIN_NAME + "-tether", "-p tcp --sport=53" + action);
            }

            // DNS requests to upstream servers
            // TODO: Allow DNS upstream servers from other connection types
            if (containsUidOrAny(ruleDataSet.dataList, SPECIAL_UID_TETHER)) {
                addRuleForUsers(cmds, users_dns, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p udp --dport=53" + action);
                addRuleForUsers(cmds, users_dns, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p tcp --dport=53" + action);
            }

            // if tethered, try to match the above rules (if enabled).  no match -> fall through to the
            // normal 3G/wifi rules
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-tether -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-tether -j " + AFWALL_CHAIN_NAME + "-3g-fork");

            // NOTE: we still need to open a hole to let WAN-only UIDs talk to a DNS server
            // on the LAN
            if (whitelist && !G.dns_proxy().equals("disable")) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-lan -p udp --dport 53 -j RETURN");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-lan -p tcp --dport 53 -j RETURN");

                //bug fix allow dns to be open on Pie for all connection type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-wan" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-home" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-roam" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-vpn" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-tether" + " -p udp --dport 53" + " -j RETURN");

                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-wan" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-home" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-roam" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-vpn" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-tether" + " -p tcp --dport 53" + " -j RETURN");
                }
            }
            // now add the per-uid rules for 3G home, 3G roam, wifi WAN, wifi LAN, VPN
            // in whitelist mode the last rule in the list routes everything else to afwall-reject
            addRulesForUidlist(cmds, ruleDataSet.dataList, AFWALL_CHAIN_NAME + "-3g-home", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.roamList, AFWALL_CHAIN_NAME + "-3g-roam", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.wifiList, AFWALL_CHAIN_NAME + "-wifi-wan", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.lanList, AFWALL_CHAIN_NAME + "-wifi-lan", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.vpnList, AFWALL_CHAIN_NAME + "-vpn", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.tetherList, AFWALL_CHAIN_NAME + "-tether", whitelist);
            if (G.enableTor()) {
                addTorRules(cmds, ruleDataSet.torList, whitelist, ipv6);
            }
            cmds.add("-P OUTPUT ACCEPT");
        } catch (Exception e) {
            Log.e(e.getClass().getName(), e.getMessage(), e);
        }

        iptablesCommands(cmds, out, ipv6);
        Log.i(TAG, "Total # of rules for " + (ipv6 ? "v6": "v4") + " " + cmds.size());
        return true;
    }

    /**
     * Checks if a collection contains specified uid or {@code SPECIAL_UID_ANY}
     *
     * @param uidList    collection of uids
     * @param uidToCheck uid to check
     * @return true if {@code uidList} contains {@code SPECIAL_UID_ANY} or {@code uidToCheck}
     */
    private static boolean containsUidOrAny(Collection<Integer> uidList, int uidToCheck) {
        return uidList.contains(SPECIAL_UID_ANY) || uidList.contains(uidToCheck);
    }

    /**
     * Add the repetitive parts (ipPath and such) to an iptables command list
     *
     * @param in  Commands in the format: "-A foo ...", "#NOCHK# -A foo ...", or "#LITERAL# <UNIX command>"
     * @param out A list of UNIX commands to execute
     */
    private static void iptablesCommands(List<String> in, List<String> out, boolean ipv6) {
        String ipPath = getBinaryPath(G.ctx, ipv6);

        String waitTime = "";
        if(G.ip_path().equals("system") && G.addDelay()) {
            waitTime = " -w 1";
        }
        boolean firstLit = true;
        for (String s : in) {
            s = s + waitTime;
            if (s.matches("#LITERAL# .*")) {
                if (firstLit) {
                    // export vars for the benefit of custom scripts
                    // "true" is a dummy command which needs to return success
                    firstLit = false;
                    out.add("export IPTABLES=\"" + ipPath + "\"; "
                            + "export BUSYBOX=\"" + bbPath + "\"; "
                            + "export IPV6=" + (ipv6 ? "1" : "0") + "; "
                            + "true");
                }
                out.add(s.replaceFirst("^#LITERAL# ", ""));
            } else if (s.matches("#NOCHK# .*")) {
                out.add(s.replaceFirst("^#NOCHK# ", "#NOCHK# " + ipPath + " "));
            } else {
                out.add(ipPath + " " + s);
            }
        }
    }

    private static void fixupLegacyCmds(List<String> cmds) {
        for (int i = 0; i < cmds.size(); i++) {
            String s = cmds.get(i);
            if (s.matches("#NOCHK# .*")) {
                s = s.replaceFirst("^#NOCHK# ", "");
            } else {
                s += " || exit";
            }
            cmds.set(i, s);
        }
    }


    public static void waitAndTerminate(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void applySavedIptablesRules(Context ctx, boolean showErrors, RootCommand callback) {

        if(!globalStatus) {
            Log.i(TAG, "Using applySavedIptablesRules");
            globalStatus = true;
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            RuleDataSet dataSet = getDataSet();

            List<String> ipv4cmds = new ArrayList<>();
            List<String> ipv6cmds = new ArrayList<>();

            executorService.submit(() -> {
                applyIptablesRulesImpl(ctx, dataSet, showErrors, ipv4cmds, false);
                applySavedIp4tablesRules(ctx, ipv4cmds, callback);

            });

            if (G.enableIPv6()) {
                executorService.submit(() -> {
                    applyIptablesRulesImpl(ctx, dataSet, showErrors, ipv6cmds, true);
                    applySavedIp6tablesRules(ctx, ipv6cmds, new RootCommand());
                });

            }
            waitAndTerminate(executorService);
            globalStatus = false;
            rulesUpToDate = true;

        } else {
            Log.i(TAG, "ignore applySavedIptablesRules as existing thread running");
        }

    }


    private static RuleDataSet getDataSet() {
        initSpecial();

        final String savedPkg_wifi_uid = G.pPrefs.getString(PREF_WIFI_PKG_UIDS, "");
        final String savedPkg_3g_uid = G.pPrefs.getString(PREF_3G_PKG_UIDS, "");
        final String savedPkg_roam_uid = G.pPrefs.getString(PREF_ROAMING_PKG_UIDS, "");
        final String savedPkg_vpn_uid = G.pPrefs.getString(PREF_VPN_PKG_UIDS, "");
        final String savedPkg_tether_uid = G.pPrefs.getString(PREF_TETHER_PKG_UIDS, "");
        final String savedPkg_lan_uid = G.pPrefs.getString(PREF_LAN_PKG_UIDS, "");
        final String savedPkg_tor_uid = G.pPrefs.getString(PREF_TOR_PKG_UIDS, "");

        return new RuleDataSet(getListFromPref(savedPkg_wifi_uid),
                getListFromPref(savedPkg_3g_uid),
                getListFromPref(savedPkg_roam_uid),
                getListFromPref(savedPkg_vpn_uid),
                getListFromPref(savedPkg_tether_uid),
                getListFromPref(savedPkg_lan_uid),
                getListFromPref(savedPkg_tor_uid));

    }

    /**
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     *
     * @param ctx      application context (mandatory)
     * @param callback If non-null, use a callback instead of blocking the current thread
     */
    public static boolean applySavedIp4tablesRules(Context ctx, List<String> cmds, RootCommand callback) {
        if (ctx == null) {
            return false;
        }
        try {
            Log.i(TAG, "Using applySaved4IptablesRules");
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Exception while applying rules: " + e.getMessage());
            applyDefaultChains(ctx, callback);
            return false;
        }
    }


    public static boolean applySavedIp6tablesRules(Context ctx, List<String> cmds, RootCommand callback) {
        if (ctx == null) {
            return false;
        }
        try {
            Log.i(TAG, "Using applySavedIp6tablesRules");
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds,true);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Exception while applying rules: " + e.getMessage());
            applyDefaultChains(ctx, callback);
            return false;
        }
    }


    public static boolean fastApply(Context ctx, RootCommand callback) {
        try {
                if (!rulesUpToDate) {
                    Log.i(TAG, "Using full Apply");
                    applySavedIptablesRules(ctx, true, callback);
                } else {
                    Log.i(TAG, "Using fastApply");
                    List<String> out = new ArrayList<String>();
                    List<String> cmds;
                    cmds = new ArrayList<String>();
                    applyShortRules(ctx, cmds, false);
                    iptablesCommands(cmds, out, false);
                    if (G.enableIPv6()) {
                        cmds = new ArrayList<String>();
                        applyShortRules(ctx, cmds, true);
                        iptablesCommands(cmds, out, true);
                    }
                    callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception while applying rules: " + e.getMessage());
            applyDefaultChains(ctx, callback);
        }
        rulesUpToDate = true;
        return true;
    }

    /**
     * Save current rules using the preferences storage.
     *
     * @param ctx application context (mandatory)
     */
    public static RuleDataSet generateRules(Context ctx, List<PackageInfoData> apps, boolean store) {

        rulesUpToDate = false;

        RuleDataSet dataSet = null;

        if (apps != null) {
            // Builds a pipe-separated list of names
            HashSet newpkg_wifi = new HashSet();
            HashSet newpkg_3g = new HashSet();
            HashSet newpkg_roam = new HashSet();
            HashSet newpkg_vpn = new HashSet();
            HashSet newpkg_tether = new HashSet();
            HashSet newpkg_lan = new HashSet();
            HashSet newpkg_tor = new HashSet();

            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i) != null) {
                    if (apps.get(i).selected_wifi) {
                        newpkg_wifi.add(apps.get(i).uid);
                    } else {
                        if (!store) newpkg_wifi.add(-apps.get(i).uid);
                    }
                    if (apps.get(i).selected_3g) {
                        newpkg_3g.add(apps.get(i).uid);
                    } else {
                        if (!store) newpkg_3g.add(-apps.get(i).uid);
                    }
                    if (G.enableRoam()) {
                        if (apps.get(i).selected_roam) {
                            newpkg_roam.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_roam.add(-apps.get(i).uid);
                        }
                    }
                    if (G.enableVPN()) {
                        if (apps.get(i).selected_vpn) {
                            newpkg_vpn.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_vpn.add(-apps.get(i).uid);
                        }
                    }
                    if (G.enableTether()) {
                        if (apps.get(i).selected_tether) {
                            newpkg_tether.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_tether.add(-apps.get(i).uid);
                        }
                    }
                    if (G.enableLAN()) {
                        if (apps.get(i).selected_lan) {
                            newpkg_lan.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_lan.add(-apps.get(i).uid);
                        }
                    }
                    if (G.enableTor()) {
                        if (apps.get(i).selected_tor) {
                            newpkg_tor.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_tor.add(-apps.get(i).uid);
                        }
                    }
                }
            }

            String wifi = android.text.TextUtils.join("|", newpkg_wifi);
            String data = android.text.TextUtils.join("|", newpkg_3g);
            String roam = android.text.TextUtils.join("|", newpkg_roam);
            String vpn = android.text.TextUtils.join("|", newpkg_vpn);
            String tether = android.text.TextUtils.join("|", newpkg_tether);
            String lan = android.text.TextUtils.join("|", newpkg_lan);
            String tor = android.text.TextUtils.join("|", newpkg_tor);
            // save the new list of UIDs
            if (store) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Editor edit = prefs.edit();
                edit.putString(PREF_WIFI_PKG_UIDS, wifi);
                edit.putString(PREF_3G_PKG_UIDS, data);
                edit.putString(PREF_ROAMING_PKG_UIDS, roam);
                edit.putString(PREF_VPN_PKG_UIDS, vpn);
                edit.putString(PREF_TETHER_PKG_UIDS, tether);
                edit.putString(PREF_LAN_PKG_UIDS, lan);
                edit.putString(PREF_TOR_PKG_UIDS, tor);
                edit.apply();
            } else {
                dataSet = new RuleDataSet(new ArrayList<>(newpkg_wifi),
                        new ArrayList<>(newpkg_3g),
                        new ArrayList<>(newpkg_roam),
                        new ArrayList<>(newpkg_vpn),
                        new ArrayList<>(newpkg_tether),
                        new ArrayList<>(newpkg_lan),
                        new ArrayList<>(newpkg_tor));
            }
        }
        return dataSet;

    }

    /**
     * Purge all iptables rules.
     *
     * @param ctx        mandatory context
     * @param showErrors indicates if errors should be alerted
     * @param callback   If non-null, use a callback instead of blocking the current thread
     * @return true if the rules were purged
     */
    public static boolean purgeIptables(Context ctx, boolean showErrors, RootCommand callback) {

        List<String> cmds = new ArrayList<>();
        List<String> cmdsv4 = new ArrayList<>();
        List<String> out = new ArrayList<>();

        for (String s : staticChains) {
            cmds.add("-F " + AFWALL_CHAIN_NAME + s);
        }
        for (String s : dynChains) {
            cmds.add("-F " + AFWALL_CHAIN_NAME + s);
        }
        if (G.enableTor()) {
            for (String s : natChains) {
                cmdsv4.add("-t nat -F " + AFWALL_CHAIN_NAME + s);
            }
            cmdsv4.add("#NOCHK# -t nat -D OUTPUT -j " + AFWALL_CHAIN_NAME);
        } else {
            cmdsv4.add("#NOCHK# -D OUTPUT -j " + AFWALL_CHAIN_NAME);
        }

        //make sure reset the OUTPUT chain to accept state.
        cmds.add("-P OUTPUT ACCEPT");

        //Delete only when the afwall chain exist !
        //cmds.add("-D OUTPUT -j " + AFWALL_CHAIN_NAME);

        if (G.enableInbound()) {
            cmds.add("-D INPUT -j " + AFWALL_CHAIN_NAME + "-input");
        }

        addCustomRules(Api.PREF_CUSTOMSCRIPT2, cmds);

        try {
            assertBinaries(ctx, showErrors);

            // IPv4
            iptablesCommands(cmds, out, false);
            iptablesCommands(cmdsv4, out, false);

            // IPv6
            if (G.enableIPv6()) {
                iptablesCommands(cmds, out, true);
            }

            if (callback != null) {
                callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
            } else {
                fixupLegacyCmds(out);
                if (runScriptAsRoot(ctx, out, new StringBuilder()) == -1) {
                    if (showErrors) toast(ctx, ctx.getString(R.string.error_purge));
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG,e.getMessage(),e);
            return false;
        }
    }


    /**
     * Retrieve the current set of IPv4 or IPv6 rules and pass it to a callback
     *
     * @param ctx      application context
     * @param callback callback to receive rule list
     * @param useIPV6  true to list IPv6 rules, false to list IPv4 rules
     */
    public static void fetchIptablesRules(Context ctx, boolean useIPV6, RootCommand callback) {
        List<String> cmds = new ArrayList<>();
        List<String> out = new ArrayList<>();
        cmds.add("-n -v -L");
        iptablesCommands(cmds, out, false);
        if (useIPV6) {
            iptablesCommands(cmds, out, true);
        }
        callback.run(ctx, out);
    }

    /**
     * Run a list of commands with both iptables and ip6tables
     *
     * @param ctx      application context
     * @param cmds     list of commands to run
     * @param callback callback for completion
     */
    public static void apply46(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();
        iptablesCommands(cmds, out, false);

        if (G.enableIPv6()) {
            iptablesCommands(cmds, out, true);
        }
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    public static void applyIPv6Quick(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();
        ////setBinaryPath(ctx, true);
        iptablesCommands(cmds, out, true);
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    public static void applyQuick(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();

        //setBinaryPath(ctx, false);
        iptablesCommands(cmds, out, false);

        //related to #511, disable ipv6 but use startup leak.
        if (G.enableIPv6() || G.fixLeak()) {
            //setBinaryPath(ctx, true);
            iptablesCommands(cmds, out, true);
        }
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    /**
     * Delete all kingroot firewall rules.  For diagnostic purposes only.
     *
     * @param ctx      application context
     * @param callback callback for completion
     */
    public static void flushAllRules(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("-F");
        cmds.add("-X");
        apply46(ctx, cmds, callback);
    }

    /**
     * Enable or disable logging by rewriting the afwall-reject chain.  Logging
     * will be enabled or disabled based on the preference setting.
     *
     * @param ctx      application context
     * @param callback callback for completion
     */
    public static void updateLogRules(Context ctx, RootCommand callback) {
        if (!isEnabled(ctx)) {
            return;
        }
        List<String> cmds = new ArrayList<String>();
        cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + "-reject");
        cmds.add("-F " + AFWALL_CHAIN_NAME + "-reject");
        addRejectRules(cmds);
        apply46(ctx, cmds, callback);
    }


    //purge 2 hour data
    public static void purgeOldLog() {
        long purgeInterval = System.currentTimeMillis() - 7200000;
        long count = new Select(com.raizlabs.android.dbflow.sql.language.Method.count()).from(LogData.class).count();
        //records are more
        if(count > 5000) {
            new Delete().from(LogData.class).where(LogData_Table.timestamp.lessThan(purgeInterval)).async().execute();
        }
    }

    /**
     * Fetch kernel logs via busybox dmesg.  This will include {AFL} lines from
     * logging rejected packets.
     *
     * @return true if logging is enabled, false otherwise
     */
    public static List<LogData> fetchLogs() {
        //load hour data due to performance issue with old view
        long loadInterval = System.currentTimeMillis() - 3600000;
        List<LogData> log = SQLite.select()
                .from(LogData.class)
                .where(LogData_Table.timestamp.greaterThan(loadInterval))
                .orderBy(LogData_Table.timestamp, true)
                .queryList();
        purgeOldLog();
        //fetch last 100 records
        if (log.size() > 100) {
            return log.subList((log.size() - 100), log.size());
        } else {
            return log;
        }
    }

    /**
     * List all interfaces via "ifconfig -a"
     *
     * @param ctx      application context
     * @param callback Callback for completion status
     */
    public static void runIfconfig(Context ctx, RootCommand callback) {
        callback.run(ctx, getBusyBoxPath(ctx, true) + " ifconfig -a");
    }

    public static void runNetworkInterface(Context ctx, RootCommand callback) {
        callback.run(ctx, getBusyBoxPath(ctx, true) + " ls /sys/class/net");
    }


    public static void fixFolderPermissionsAsync(Context mContext) {
        AsyncTask.execute(() -> {
            try {
                mContext.getFilesDir().setExecutable(true, false);
                mContext.getFilesDir().setReadable(true, false);
                File sharedPrefsFolder = new File(mContext.getFilesDir().getAbsolutePath()
                        + "/../shared_prefs");
                sharedPrefsFolder.setExecutable(true, false);
                sharedPrefsFolder.setReadable(true, false);
            } catch (Exception e) {
                Log.e(Api.TAG, e.getMessage(), e);
            }
        });
    }

    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
    public static List<PackageInfoData> getApps(Context ctx, GetAppList appList) {

        initSpecial();
        if (applications != null && applications.size() > 0) {
            // return cached instance
            return applications;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedPkg_wifi_uid = prefs.getString(PREF_WIFI_PKG_UIDS, "");
        String savedPkg_3g_uid = prefs.getString(PREF_3G_PKG_UIDS, "");
        String savedPkg_roam_uid = prefs.getString(PREF_ROAMING_PKG_UIDS, "");
        String savedPkg_vpn_uid = prefs.getString(PREF_VPN_PKG_UIDS, "");
        String savedPkg_tether_uid = prefs.getString(PREF_TETHER_PKG_UIDS, "");
        String savedPkg_lan_uid = prefs.getString(PREF_LAN_PKG_UIDS, "");
        String savedPkg_tor_uid = prefs.getString(PREF_TOR_PKG_UIDS, "");

        List<Integer> selected_wifi;
        List<Integer> selected_3g;
        List<Integer> selected_roam = new ArrayList<>();
        List<Integer> selected_vpn = new ArrayList<>();
        List<Integer> selected_tether = new ArrayList<>();
        List<Integer> selected_lan = new ArrayList<>();
        List<Integer> selected_tor = new ArrayList<>();


        selected_wifi = getListFromPref(savedPkg_wifi_uid);
        selected_3g = getListFromPref(savedPkg_3g_uid);

        if (G.enableRoam()) {
            selected_roam = getListFromPref(savedPkg_roam_uid);
        }
        if (G.enableVPN()) {
            selected_vpn = getListFromPref(savedPkg_vpn_uid);
        }
        if (G.enableTether()) {
            selected_tether = getListFromPref(savedPkg_tether_uid);
        }
        if (G.enableLAN()) {
            selected_lan = getListFromPref(savedPkg_lan_uid);
        }
        if (G.enableTor()) {
            selected_tor = getListFromPref(savedPkg_tor_uid);
        }
        //revert back to old approach

        SharedPreferences cachePrefs = ctx.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE);

        int count = 0;
        try {
            /*if(G.supportDual()) {
                listOfUids = new ArrayList<>();
                //this code will be executed on devices running ICS or later
                final UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                List<UserHandle> list = um.getUserProfiles();

                for (UserHandle user : list) {
                    Matcher m = p.matcher(user.toString());
                    if (m.find() && m.groupCount() > 0) {
                        int id = Integer.parseInt(m.group(1));
                        if (id > 0) {
                            listOfUids.add(id);
                        }
                    }
                }
            }*/
            PackageManager pkgmanager = ctx.getPackageManager();
            List<PackageInfo> installed;
            if (G.isMultiUser()) {
                installed = MultiUser.getInstalledPackagesFromAllUsers(MultiUser.MATCH_ALL_METADATA);
            } else {
                int pkgManagerFlags = PackageManager.GET_META_DATA;
                // it's useless to iterate over uninstalled packages if we don't support multi-profile apps
                if (G.supportDual()) {
                    pkgManagerFlags |= PackageManager.GET_UNINSTALLED_PACKAGES;
                }
                installed = pkgmanager.getInstalledPackages(pkgManagerFlags);
            }
            SparseArray<PackageInfoData> syncMap = new SparseArray<>();
            Editor edit = cachePrefs.edit();
            boolean changed = false;
            String name;
            String cachekey;
            String cacheLabel = "cache.label.";
            PackageInfoData app;

            Date install = new Date();
            install.setTime(System.currentTimeMillis() - (180000));

            SparseArray<PackageInfoData> multiUserAppsMap = new SparseArray<>();
            HashMap<Integer, String> packagesForUser = new HashMap<>();
            /*if(G.supportDual()) {
                packagesForUser  = getPackagesForUser(listOfUids);
            }*/


            for (PackageInfo pkginfo : installed) {
                ApplicationInfo apinfo = pkginfo.applicationInfo;
                if (apinfo == null) continue;

                int user_id = MultiUser.applicationUserId(apinfo);
                Log.d(TAG, "Processing app info: " + apinfo.packageName + " / user " + user_id + " / uid " + apinfo.uid);
                count = count + 1;

                if (appList != null) {
                    appList.doProgress(count);
                }

                boolean firstseen = false;
                app = syncMap.get(apinfo.uid);
                // filter applications which are not allowed to access the Internet
                if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName) && !showAllApps()) {
                    continue;
                }
                // try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
                cachekey = cacheLabel + apinfo.packageName + Integer.toString(user_id);
                name = prefs.getString(cachekey, "");
                if (name.length() == 0 || isRecentlyInstalled(apinfo.packageName)) {
                    // get label and put on cache
                    if (G.isMultiUser()) {
                        name = pkgmanager.getApplicationLabel(apinfo).toString() + " / user " + Integer.toString(user_id);
                    } else {
                        name = pkgmanager.getApplicationLabel(apinfo).toString();
                    }
                    edit.putString(cachekey, name);
                    changed = true;
                    firstseen = true;
                }
                if (app == null) {
                    app = new PackageInfoData();
                    app.uid = apinfo.uid;
                    app.installTime = new File(apinfo.sourceDir).lastModified();
                    app.names = new ArrayList<String>();
                    app.names.add(name);
                    app.appinfo = apinfo;
                    if (app.appinfo != null && (app.appinfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        //user app
                        app.appType = 1;
                    } else {
                        //system app
                        app.appType = 0;
                    }
                    app.pkgName = apinfo.packageName;
                    syncMap.put(app.uid, app);
                } else {
                    app.names.add(name);
                }

                app.firstseen = firstseen;
                // check if this application is selected
                if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                    app.selected_wifi = true;
                }
                if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                    app.selected_3g = true;
                }
                if (G.enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                    app.selected_roam = true;
                }
                if (G.enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                    app.selected_vpn = true;
                }
                if (G.enableTether() && !app.selected_tether && Collections.binarySearch(selected_tether, app.uid) >= 0) {
                    app.selected_tether = true;
                }
                if (G.enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                    app.selected_lan = true;
                }
                if (G.enableTor() && !app.selected_tor && Collections.binarySearch(selected_tor, app.uid) >= 0) {
                    app.selected_tor = true;
                }
                /*if (G.supportDual()) {
                    checkPartOfMultiUser(apinfo, name, listOfUids, packagesForUser, multiUserAppsMap);
                }*/
            }

            /*if (G.supportDual()) {
                //run through multi user map
                for (int i = 0; i < multiUserAppsMap.size(); i++) {
                    app = multiUserAppsMap.valueAt(i);
                    if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                        app.selected_wifi = true;
                    }
                    if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                        app.selected_3g = true;
                    }
                    if (G.enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                        app.selected_roam = true;
                    }
                    if (G.enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                        app.selected_vpn = true;
                    }
                    if (G.enableTether() && !app.selected_tether && Collections.binarySearch(selected_tether, app.uid) >= 0) {
                        app.selected_tether = true;
                    }
                    if (G.enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                        app.selected_lan = true;
                    }
                    if (G.enableTor() && !app.selected_tor && Collections.binarySearch(selected_tor, app.uid) >= 0) {
                        app.selected_tor = true;
                    }
                    syncMap.put(app.uid, app);
                }
            }*/

            List<PackageInfoData> specialData = getSpecialData();

            if (specialApps == null) {
                specialApps = new HashMap<String, Integer>();
            }
            for (int i = 0; i < specialData.size(); i++) {
                app = specialData.get(i);
                //core apps
                app.appType = 2;
                specialApps.put(app.pkgName, app.uid);
                //default DNS/NTP
                if (app.uid != -1 && syncMap.get(app.uid) == null) {
                    // check if this application is allowed
                    if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                        app.selected_wifi = true;
                    }
                    if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                        app.selected_3g = true;
                    }
                    if (G.enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                        app.selected_roam = true;
                    }
                    if (G.enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                        app.selected_vpn = true;
                    }
                    if (G.enableTether() && !app.selected_tether && Collections.binarySearch(selected_tether, app.uid) >= 0) {
                        app.selected_tether = true;
                    }
                    if (G.enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                        app.selected_lan = true;
                    }
                    if (G.enableTor() && !app.selected_tor && Collections.binarySearch(selected_tor, app.uid) >= 0) {
                        app.selected_tor = true;
                    }
                    syncMap.put(app.uid, app);
                }
            }

            if (changed) {
                edit.apply();
            }
            /* convert the map into an array */
            applications = Collections.synchronizedList(new ArrayList<PackageInfoData>());
            for (int i = 0; i < syncMap.size(); i++) {
                applications.add(syncMap.valueAt(i));
            }
            return applications;
        } catch (Exception e) {
            Log.i(TAG, "Exception in getting app list", e);
        }
        return new ArrayList<>();
    }

   /* public boolean isSuPackage(PackageManager pm, String suPackage) {
        boolean found = false;
        try {
            PackageInfo info = pm.getPackageInfo(suPackage, 0);
            if (info.applicationInfo != null) {
                found = true;
            }
            //found = s + " v" + info.versionName;
        } catch (NameNotFoundException e) {
        }
        return found;
    }*/

    public static List<PackageInfoData> getSpecialData() {
        List<PackageInfoData> specialData = new ArrayList<>();
        specialData.add(new PackageInfoData(SPECIAL_UID_ANY, ctx.getString(R.string.all_item), "dev.afwall.special.any"));
        specialData.add(new PackageInfoData(SPECIAL_UID_KERNEL, ctx.getString(R.string.kernel_item), "dev.afwall.special.kernel"));
        specialData.add(new PackageInfoData(SPECIAL_UID_TETHER, ctx.getString(R.string.tethering_item), "dev.afwall.special.tether"));
        specialData.add(new PackageInfoData(SPECIAL_UID_NTP, ctx.getString(R.string.ntp_item), "dev.afwall.special.ntp"));


        specialData.add(new PackageInfoData(1020, ctx.getString(R.string.mdnslabel), "dev.afwall.special.mdnsr"));

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specialData.add(new PackageInfoData(1029, ctx.getString(R.string.clat), "dev.afwall.special.clat"));
        }

        /*if (additional) {
            specialData.add(new PackageInfoData(1020, "mDNS", "dev.afwall.special.mDNS"));
        }*/
        for (String acct : specialAndroidAccounts) {
            String dsc = getSpecialDescription(ctx, acct);
            if (dsc != null) {
                String pkg = "dev.afwall.special." + acct;
                specialData.add(new PackageInfoData(acct, dsc, pkg));
            }
        }
        return specialData;
    }

    /*private static void checkPartOfMultiUser(ApplicationInfo apinfo, String name, List<Integer> uid1, HashMap<Integer,String> pkgs, SparseArray<PackageInfoData> syncMap) {
        try {
            for (Integer integer : uid1) {
                int appUid = Integer.parseInt(integer + "" + apinfo.uid + "");
                try{
                    //String[] pkgs = pkgmanager.getPackagesForUid(appUid);
                    if (packagesExistForUserUid(pkgs, appUid)) {
                        PackageInfoData app = new PackageInfoData();
                        app.uid = appUid;
                        app.installTime = new File(apinfo.sourceDir).lastModified();
                        app.names = new ArrayList<String>();
                        app.names.add(name + "(M)");
                        app.appinfo = apinfo;
                        if (app.appinfo != null && (app.appinfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                            //user app
                            app.appType = 1;
                        } else {
                            //system app
                            app.appType = 0;
                        }
                        app.pkgName = apinfo.packageName;
                        syncMap.put(appUid, app);
                    }
                }catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }*/

    private static boolean packagesExistForUserUid(HashMap<Integer,String> pkgs, int appUid) {
        if(pkgs.containsKey(appUid)){
            return true;
        }
        return false;
    }

    public static HashMap<Integer, String> getPackagesForUser(List<Integer> userProfile) {
        HashMap<Integer,String> listApps = new HashMap<>();
        for(Integer integer: userProfile) {
            Shell.Result result = Shell.cmd("pm list packages -U --user " + integer).exec();
            List<String> out = result.getOut();
            Matcher matcher;
            for (String item : out) {
                matcher = dual_pattern.matcher(item);
                if (matcher.find() && matcher.groupCount() > 0) {
                    String packageName = matcher.group(1);
                    String packageId = matcher.group(2);
                    Log.i(TAG, packageId + " " + packageName);
                    listApps.put(Integer.parseInt(packageId), packageName);
                }
            }
        }
        return listApps.size() > 0 ? listApps : null;
    }

    private static boolean isRecentlyInstalled(String packageName) {
        boolean isRecent = false;
        if (recentlyInstalled != null && recentlyInstalled.contains(packageName)) {
            isRecent = true;
            recentlyInstalled.remove(packageName);
        }
        return isRecent;
    }

    private static List<Integer> getListFromPref(String savedPkg_uid) {
        StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
        List<Integer> listUids = new ArrayList<>();
        while (tok.hasMoreTokens()) {
            String uid = tok.nextToken();
            if (!uid.equals("")) {
                listUids.add(Integer.parseInt(uid));
            }
        }
        // Sort the array to allow using "Arrays.binarySearch" later
        Collections.sort(listUids);
        return listUids;
    }

    /*public static boolean isAppAllowed(Context context, ApplicationInfo applicationInfo, SharedPreferences sharedPreferences, SharedPreferences pPrefs) {
        InterfaceDetails details = InterfaceTracker.getCurrentCfg(context, true);
        //allow webview to download since webview requires INTERNET permission
        if (applicationInfo.packageName.equals("com.android.webview") || applicationInfo.packageName.equals("com.google.android.webview")) {
            return true;
        }
        if (details != null && details.netEnabled) {
            String mode = pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
            Log.i(TAG, "Calling isAppAllowed method from DM with Mode: " + mode);
            switch ((details.netType)) {
                case ConnectivityManager.TYPE_WIFI:
                    String savedPkg_wifi_uid = pPrefs.getString(PREF_WIFI_PKG_UIDS, "");
                    if (savedPkg_wifi_uid.isEmpty()) {
                        savedPkg_wifi_uid = sharedPreferences.getString(PREF_WIFI_PKG_UIDS, "");
                    }
                    Log.i(TAG, "DM check for UID: " + applicationInfo.uid);
                    Log.i(TAG, "DM allowed UIDs: " + savedPkg_wifi_uid);
                    if (mode.equals(Api.MODE_WHITELIST) && savedPkg_wifi_uid.contains(applicationInfo.uid + "")) {
                        return true;
                    } else return mode.equals(Api.MODE_BLACKLIST) && !savedPkg_wifi_uid.contains(applicationInfo.uid + "");

                case ConnectivityManager.TYPE_MOBILE:
                    String savedPkg_3g_uid = pPrefs.getString(PREF_3G_PKG_UIDS, "");
                    if (details.isRoaming) {
                        savedPkg_3g_uid = pPrefs.getString(PREF_ROAMING_PKG_UIDS, "");
                    }
                    Log.i(TAG, "DM check for UID: " + applicationInfo.uid);
                    Log.i(TAG, "DM allowed UIDs: " + savedPkg_3g_uid);
                    if (mode.equals(Api.MODE_WHITELIST) && savedPkg_3g_uid.contains(applicationInfo.uid + "")) {
                        return true;
                    } else return mode.equals(Api.MODE_BLACKLIST) && !savedPkg_3g_uid.contains(applicationInfo.uid + "");
            }
        }

        return true;
    }*/

    /**
     * Get Default Chain status
     *
     * @param ctx
     * @param callback
     */
    public static void getChainStatus(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("-S INPUT");
        cmds.add("-S OUTPUT");
        cmds.add("-S FORWARD");
        List<String> out = new ArrayList<>();

        iptablesCommands(cmds, out, false);

        ArrayList base = new ArrayList<String>();
        base.add("-S INPUT");
        base.add("-S OUTPUT");
        cmds.add("-S FORWARD");
        iptablesCommands(base, out, true);

        callback.run(ctx, out);
    }

    /**
     * Apply single rule
     *
     * @param ctx
     * @param rule
     * @param isIpv6
     * @param callback
     */
    public static void applyRule(Context ctx, String rule, boolean isIpv6, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        cmds.add(rule);
        //setBinaryPath(ctx, isIpv6);
        List<String> out = new ArrayList<>();
        iptablesCommands(cmds, out, isIpv6);
        callback.run(ctx, out);
    }

    /**
     * Runs a script as root (multiple commands separated by "\n")
     *
     * @param ctx    mandatory context
     * @param script the script to be executed
     * @param res    the script output response (stdout + stderr)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
    public static int runScriptAsRoot(Context ctx, List<String> script, StringBuilder res) throws IOException {
        int returnCode = -1;

        if ((Looper.myLooper() != null) && (Looper.myLooper() == Looper.getMainLooper())) {
            Log.e(TAG, "runScriptAsRoot should not be called from the main thread\nCall Trace:\n");
            for (StackTraceElement e : new Throwable().getStackTrace()) {
                Log.e(TAG, e.toString());
            }
        }

        try {
            returnCode = new RunCommand().execute(script, res, ctx).get();
        } catch (RejectedExecutionException r) {
            Log.e(TAG, "runScript failed: " + r.getLocalizedMessage());
        } catch (InterruptedException e) {
            Log.e(TAG, "Caught InterruptedException");
        } catch (Exception e) {
            Log.e(TAG, "runScript failed: " + e.getLocalizedMessage());
        }

        return returnCode;
    }

    private static boolean installBinary(Context ctx, int resId, String filename) {
        try {
            File f = new File(ctx.getDir("bin", 0), filename);
            if (f.exists()) {
                f.delete();
            }
            copyRawFile(ctx, resId, f, "0755");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "installBinary failed: " + e.getLocalizedMessage());
            return false;
        }
    }

    private static boolean installBinariesX86() {
        if (!installBinary(ctx, R.raw.busybox_x86, "busybox")) return false;
        if (!installBinary(ctx, R.raw.iptables_x86, "iptables")) return false;
        if (!installBinary(ctx, R.raw.ip6tables_x86, "ip6tables")) return false;
        if (!installBinary(ctx, R.raw.nflog_x86, "nflog")) return false;
        //if (!installBinary(ctx, R.raw.run_pie_x86, "run_pie")) return false;
        return true;
    }

    private static boolean installBinariesMips() {
        if (!installBinary(ctx, R.raw.busybox_mips, "busybox")) return false;
        if (!installBinary(ctx, R.raw.iptables_mips, "iptables")) return false;
        if (!installBinary(ctx, R.raw.ip6tables_mips, "ip6tables")) return false;
        if (!installBinary(ctx, R.raw.nflog_mips, "nflog")) return false;
        //if (!installBinary(ctx, R.raw.run_pie_mips, "run_pie")) return false;
        return true;
    }

    private static boolean installBinariesArm() {
        if (!installBinary(ctx, R.raw.busybox_arm, "busybox")) return false;
        if (!installBinary(ctx, R.raw.iptables_arm, "iptables")) return false;
        if (!installBinary(ctx, R.raw.ip6tables_arm, "ip6tables")) return false;
        if (!installBinary(ctx, R.raw.nflog_arm, "nflog")) return false;
        //if (!installBinary(ctx, R.raw.run_pie_arm, "run_pie")) return false;
        return true;
    }

    private static boolean installBinariesForAbi(String abi) {
        if (abi.startsWith("x86")) {
            return installBinariesX86();
        } else if (abi.startsWith("mips")) {
            return installBinariesMips();
        } else {
            return installBinariesArm();
        }
    }

    private static int getPackageVersion() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Can't determine the package version!");
            return -1;
        }
    }

    private static String getAbi() {
        if (Build.VERSION.SDK_INT > 21) {
            return Build.SUPPORTED_ABIS[0];
        } else {
            return Build.CPU_ABI;
        }
    }

    /**
     * Asserts that the binary files are installed in the cache directory.
     *
     * @param ctx        context
     * @param showErrors indicates if errors should be alerted
     * @return false if the binary files could not be installed
     */
    public static boolean assertBinaries(Context ctx, boolean showErrors) {

        int currentVer = getPackageVersion();

        if (G.appVersion() == currentVer) {
            // The version hasn't changed: Use the previously installed binaries.
            return true;
        }

        String abi = getAbi();

        Log.d(TAG, "Installing binaries for " + abi + "...");

        if (!installBinariesForAbi(abi))
        {
            Log.e(TAG, "Installation of the binaries for " + abi + " failed!");
            toast(ctx, ctx.getString(R.string.error_binary), Toast.LENGTH_LONG);
            return false;
        }

        // Arch-independent scripts:
        if (!installBinary(ctx, R.raw.afwallstart, "afwallstart"))
        {
            Log.e(TAG, "Installation of the arch-independent binaries failed!");
            toast(ctx, ctx.getString(R.string.error_binary));
            return false;
        }

        Log.d(TAG, "Installed binaries for " + abi + ".");
        toast(ctx, ctx.getString(R.string.toast_bin_installed), Toast.LENGTH_SHORT);

        G.appVersion(currentVer); // This indicates that the installation of the binaries for this version was successful.

        return true;
    }

    /**
     * Check if the firewall is enabled
     *
     * @param ctx mandatory context
     * @return boolean
     */
    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        return ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
    }

    /**
     * Defines if the firewall is enabled and broadcasts the new status
     *
     * @param ctx     mandatory context
     * @param enabled enabled flag
     */
    public static void setEnabled(Context ctx, boolean enabled, boolean showErrors) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
            return;
        }
        rulesUpToDate = false;

        Editor edit = prefs.edit();
        edit.putBoolean(PREF_ENABLED, enabled);
        if (!edit.commit()) {
            if (showErrors) toast(ctx, ctx.getString(R.string.error_write_pref));
            return;
        }

        //addNotification();
        Intent myService = new Intent(ctx, FirewallService.class);
        ctx.stopService(myService);
        ctx.startService(myService);

        /* notify */
        Intent message = new Intent(ctx, StatusWidget.class);
        message.setAction(STATUS_CHANGED_MSG);
        message.putExtra(Api.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
    }


    public static void errorNotification(Context ctx) {

        String NOTIFICATION_CHANNEL_ID = "firewall.error";
        String channelName = ctx.getString(R.string.firewall_error_notify);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(ERROR_NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            if (G.getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }


        Intent appIntent = new Intent(ctx, MainActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Artificial stack so that navigating backward leads back to the Home screen
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
                .addParentStack(MainActivity.class)
                .addNextIntent(new Intent(ctx, MainActivity.class));

        PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);

        Notification notification = notificationBuilder.setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentTitle(ctx.getString(R.string.error_notification_title))
                .setContentText(ctx.getString(R.string.error_notification_text))
                .setTicker(ctx.getString(R.string.error_notification_ticker))
                .setSmallIcon(R.drawable.notification_warn)
                .setAutoCancel(true)
                .setContentIntent(notifyPendingIntent)
                .build();

        manager.notify(ERROR_NOTIFICATION_ID, notification);
    }

    public static void updateNotification(boolean status, Context ctx) {

        String NOTIFICATION_CHANNEL_ID = "firewall.service";
        String channelName = ctx.getString(R.string.firewall_service);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            if (G.getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }

        Intent appIntent = new Intent(ctx, MainActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int icon = status ? R.drawable.notification : R.drawable.notification_error;
        String notificationText = status ? getNotificationText(ctx) : ctx.getString(R.string.inactive);

        PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);

        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setTicker(ctx.getString(R.string.app_name))
                .setSound(null)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentText(notificationText)
                .setSmallIcon(icon)
                .build();

        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;
        manager.notify(NOTIFICATION_ID, notification);
    }

    private static String getNotificationText(Context ctx) {
        if (G.enableMultiProfile()) {
            String storedProfile = G.storedProfile();
            switch (storedProfile) {
                case "AFWallPrefs":
                    return ctx.getString(R.string.active) + " (" + G.gPrefs.getString("default", ctx.getString(R.string.defaultProfile)) + ")";
                case "AFWallProfile1":
                    return ctx.getString(R.string.active) + " (" + G.gPrefs.getString("profile1", ctx.getString(R.string.profile1)) + ")";
                case "AFWallProfile2":
                    return ctx.getString(R.string.active) + " (" + G.gPrefs.getString("profile2", ctx.getString(R.string.profile2)) + ")";
                case "AFWallProfile3":
                    return ctx.getString(R.string.active) + " (" + G.gPrefs.getString("profile3", ctx.getString(R.string.profile3)) + ")";
                default:
                    return ctx.getString(R.string.active) + " (" + storedProfile + ")";
            }
        } else {
            return ctx.getString(R.string.active);
        }
    }


    private static boolean removePackageRef(Context ctx, String pkg, int pkgRemoved, SharedPreferences.Editor editor, String store) {
        StringBuilder newUids = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(pkg, "|");
        boolean changed = false;
        String uidStr = String.valueOf(pkgRemoved);

        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (!uidStr.equals(token)) {
                if (newUids.length() > 0) {
                    newUids.append('|');
                }
                newUids.append(token);
            } else {
                changed = true;
            }
        }

        if (changed) {
            editor.putString(store, newUids.toString());
            editor.apply();
        }
        return changed;
    }


    /**
     * Remove the cache.label key from preferences, so that next time the app appears on the top
     *
     * @param pkgName
     * @param ctx
     */
    public static void removeCacheLabel(String pkgName, Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);
        try {
            prefs.edit().remove("cache.label." + pkgName).commit();
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    /**
     * Cleansup the uninstalled packages from the cache - will have slight performance
     *
     * @param ctx
     */
    public static void removeAllUnusedCacheLabel(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);
            final String cacheLabel = "cache.label.";
            String pkgName;
            String cacheKey;
            PackageManager pm = ctx.getPackageManager();
            Map<String, ?> allPrefs = prefs.getAll();

            for (Map.Entry<String, ?> prefEntry : allPrefs.entrySet()) {
                String key = prefEntry.getKey();
                if (key.startsWith(cacheLabel)) {
                    cacheKey = key;
                    pkgName = key.replace(cacheLabel, "");
                    if (prefs.getString(cacheKey, "").length() > 0 && !isPackageExists(pm, pkgName)) {
                        prefs.edit().remove(cacheKey).apply();
                    }
                }
            }
        } catch (Exception e) {
            // Handle the exception appropriately (e.g., log or print the stack trace)
        }
    }


    /**
     * Cleanup the cache from profiles - Improve performance.
     *
     * @param pm
     * @param targetPackage
     */

    public static boolean isPackageExists(PackageManager pm, String targetPackage) {
        try {
            pm.getPackageInfo(targetPackage, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public static PackageInfo getPackageDetails(Context ctx, HashMap<Integer, String> listMaps, int uid) {
        try {
            final PackageManager pm = ctx.getPackageManager();
            if (listMaps != null && listMaps.containsKey(uid)) {
                return pm.getPackageInfo(listMaps.get(uid), PackageManager.GET_META_DATA);
            } else {
                return null;
            }
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    private static Map<Integer, ApplicationInfo> uidToApplicationInfoMap = null;

    public static Drawable getApplicationIcon(Context context, int appUid) {
        if (uidToApplicationInfoMap == null) {
            PackageManager packageManager = context.getPackageManager();
            List<ApplicationInfo> installedApplications = packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
            uidToApplicationInfoMap = new HashMap<>();
            for (ApplicationInfo applicationInfo : installedApplications) {
                if (!uidToApplicationInfoMap.containsKey(applicationInfo.uid)) {
                    uidToApplicationInfoMap.put(applicationInfo.uid, applicationInfo);
                }
            }
        }

        ApplicationInfo applicationInfo = uidToApplicationInfoMap.get(appUid);
        if (applicationInfo != null) {
            PackageManager packageManager = context.getPackageManager();
            return applicationInfo.loadIcon(packageManager);        // The application icon.
        } else {
            return context.getDrawable(R.drawable.ic_unknown);      // The default icon.
        }
    }

    /**
     * Called when an application in removed (un-installed) from the system.
     * This will look for that application in the selected list and update the persisted values if necessary
     *
     * @param ctx mandatory app context
     */
    public static void applicationRemoved(Context ctx, int pkgRemoved, RootCommand callback) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        boolean isRuleChanged = false;

        String[] prefKeys = {
                PREF_WIFI_PKG_UIDS,
                PREF_3G_PKG_UIDS,
                PREF_ROAMING_PKG_UIDS,
                PREF_VPN_PKG_UIDS,
                PREF_TETHER_PKG_UIDS,
                PREF_LAN_PKG_UIDS,
                PREF_TOR_PKG_UIDS
        };

        String[] savedPackages = {
                prefs.getString(PREF_WIFI_PKG_UIDS, ""),
                prefs.getString(PREF_3G_PKG_UIDS, ""),
                prefs.getString(PREF_ROAMING_PKG_UIDS, ""),
                prefs.getString(PREF_VPN_PKG_UIDS, ""),
                prefs.getString(PREF_TETHER_PKG_UIDS, ""),
                prefs.getString(PREF_LAN_PKG_UIDS, ""),
                prefs.getString(PREF_TOR_PKG_UIDS, "")
        };

        boolean[] ruleChanged = new boolean[savedPackages.length];

        for (int i = 0; i < savedPackages.length; i++) {
            ruleChanged[i] = removePackageRef(ctx, savedPackages[i], pkgRemoved, editor, prefKeys[i]);
            if (ruleChanged[i]) {
                isRuleChanged = true;
            }
        }

        if (isRuleChanged) {
            editor.apply();
            if (isEnabled(ctx)) {
                applySavedIptablesRules(ctx, false, new RootCommand());
            }
        }
    }


    public static void donateDialog(final Context ctx, boolean showToast) {
        if (showToast) {
            Toast.makeText(ctx, ctx.getText(R.string.donate_only), Toast.LENGTH_LONG).show();
        } else {
            try {
                new MaterialDialog.Builder(ctx).cancelable(false)
                        .title(R.string.buy_donate)
                        .content(R.string.donate_only)
                        .positiveText(R.string.buy_donate)
                        .negativeText(R.string.close)
                        .icon(ctx.getResources().getDrawable(R.drawable.ic_launcher))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("market://search?q=pub:ukpriya"));
                                ctx.startActivity(intent);
                            }
                        })

                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dialog.cancel();
                                G.isDo(false);
                            }
                        })
                        .show();
            } catch (Exception e) {
                Toast.makeText(ctx, ctx.getText(R.string.donate_only), Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void exportRulesToFileConfirm(final Context ctx) {
        String fileName = "afwall-backup-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".json";
        if (exportRules(ctx, fileName)) {
            Api.toast(ctx, ctx.getString(R.string.export_rules_success) + " " + fileName);
        } else {
            Api.toast(ctx, ctx.getString(R.string.export_rules_fail));
        }
    }

    public static void exportAllPreferencesToFileConfirm(final Context ctx) {
        String fileName = "afwall-backup-all-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".json";
        if (exportAll(ctx, fileName)) {
            Api.toast(ctx, ctx.getString(R.string.export_rules_success) + " " + fileName);
        } else {
            Api.toast(ctx, ctx.getString(R.string.export_rules_fail));
        }
    }

    private static void updateExportPackage(Map<String, JSONObject> exportMap, String packageName, int identifier) throws JSONException {
        JSONObject obj;
        if (packageName != null) {
            if (exportMap.containsKey(packageName)) {
                obj = exportMap.get(packageName);
                obj.put(identifier + "", true);
            } else {
                obj = new JSONObject();
                obj.put(identifier + "", true);
                exportMap.put(packageName, obj);
            }
        }

    }

    private static void updatePackage(Context ctx, String savedPkg_uid, Map<String, JSONObject> exportMap, int identifier) throws JSONException {
        StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
        while (tok.hasMoreTokens()) {
            String uid = tok.nextToken();
            if (!uid.equals("")) {
                String packageName = ctx.getPackageManager().getNameForUid(Integer.parseInt(uid));
                updateExportPackage(exportMap, packageName, identifier);
            }
        }
    }

    private static Map<String, JSONObject> getCurrentRulesAsMap(Context ctx) {
        List<PackageInfoData> apps = getApps(ctx, null);
        Map<String, JSONObject> exportMap = new HashMap<>();

        try {
            for (int i = 0; i < apps.size(); i++) {
                PackageInfoData pkginfo = apps.get(i);
                String packageName = pkginfo.pkgName;
                if (G.isMultiUser()) {
                    int user_id = MultiUser.applicationUserId(pkginfo.appinfo);
                    if (user_id > 0) {
                        packageName = packageName + "/" + String.valueOf(user_id);
                    }
                }

                if (apps.get(i).selected_wifi) {
                    updateExportPackage(exportMap, packageName, WIFI_EXPORT);
                }
                if (apps.get(i).selected_3g) {
                    updateExportPackage(exportMap, packageName, DATA_EXPORT);
                }
                if (apps.get(i).selected_roam) {
                    updateExportPackage(exportMap, packageName, ROAM_EXPORT);
                }
                if (apps.get(i).selected_vpn) {
                    updateExportPackage(exportMap, packageName, VPN_EXPORT);
                }
                if (apps.get(i).selected_tether) {
                    updateExportPackage(exportMap, packageName, TETHER_EXPORT);
                }
                if (apps.get(i).selected_lan) {
                    updateExportPackage(exportMap, packageName, LAN_EXPORT);
                }
                if (apps.get(i).selected_tor) {
                    updateExportPackage(exportMap, packageName, TOR_EXPORT);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return exportMap;
    }


    public static boolean exportAll(Context ctx, final String fileName) {
        boolean res = false;
        try {
            File file;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "afwall");
                dir.mkdirs();
                file = new File(dir, fileName);
            } else {
                file = new File(ctx.getExternalFilesDir(null), fileName);
            }

            try (FileOutputStream fOut = new FileOutputStream(file);
                 OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut)) {

                JSONObject exportObject = new JSONObject();
                if (G.enableMultiProfile()) {
                    if (!G.isProfileMigrated()) {
                        JSONObject profileObject = new JSONObject();
                        for (String profile : G.profiles) {
                            profileObject.put(profile, new JSONObject(getRulesForProfile(ctx, profile)));
                        }
                        exportObject.put("profiles", profileObject);

                        JSONObject addProfileObject = new JSONObject();
                        for (String profile : G.getAdditionalProfiles()) {
                            addProfileObject.put(profile, new JSONObject(getRulesForProfile(ctx, profile)));
                        }
                        exportObject.put("additional_profiles", addProfileObject);
                    } else {
                        JSONObject profileObject = new JSONObject();
                        String profileName = "AFWallPrefs";
                        profileObject.put(profileName, new JSONObject(getRulesForProfile(ctx, profileName)));

                        List<ProfileData> profileDataList = ProfileHelper.getProfiles();
                        for (ProfileData profile : profileDataList) {
                            profileName = profile.getName();
                            if (profile.getIdentifier().startsWith("AFWallProfile")) {
                                profileName = profile.getIdentifier();
                            }
                            profileObject.put(profile.getName(), new JSONObject(getRulesForProfile(ctx, profileName)));
                        }
                        exportObject.put("_profiles", profileObject);
                    }
                } else {
                    JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
                    exportObject.put("default", obj);
                }

                exportObject.put("prefs", getAllAppPreferences(ctx, G.gPrefs));

                String mode = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
                exportObject.put("mode", mode);

                myOutWriter.append(exportObject.toString());
                res = true;
            }

        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage(), e);
        }

        return res;
    }


    private static Map<String, JSONObject> getRulesForProfile(Context ctx, String profile) throws JSONException {
        Map<String, JSONObject> exportMap = new HashMap<>();
        SharedPreferences prefs = ctx.getSharedPreferences(profile, Context.MODE_PRIVATE);
        updatePackage(ctx, prefs.getString(PREF_WIFI_PKG_UIDS, ""), exportMap, WIFI_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_3G_PKG_UIDS, ""), exportMap, DATA_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_ROAMING_PKG_UIDS, ""), exportMap, ROAM_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_VPN_PKG_UIDS, ""), exportMap, VPN_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_TETHER_PKG_UIDS, ""), exportMap, TETHER_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_LAN_PKG_UIDS, ""), exportMap, LAN_EXPORT);
        updatePackage(ctx, prefs.getString(PREF_TOR_PKG_UIDS, ""), exportMap, TOR_EXPORT);
        return exportMap;
    }

    private static JSONArray getAllAppPreferences(Context ctx, SharedPreferences gPrefs) throws JSONException {
        Map<String, ?> keys = gPrefs.getAll();
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            JSONObject obj = new JSONObject();
            obj.put(entry.getKey(), entry.getValue().toString());
            arr.put(obj);
        }
        return arr;
    }

    public static boolean exportRules(Context ctx, final String fileName) {
        boolean res = false;

            File file;
            if(Build.VERSION.SDK_INT  < Build.VERSION_CODES.Q ){
                File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/" );
                dir.mkdirs();
                file = new File(dir, fileName);
            } else{
                file = new File(ctx.getExternalFilesDir(null) + "/" + fileName) ;
            }

            try {

                FileOutputStream fOut = new FileOutputStream(file);
                OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

                //default Profile - current one
                JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
                JSONArray jArray = new JSONArray("[" + obj.toString() + "]");

                JSONObject exportObject = new JSONObject();
                exportObject.put("rules", jArray);

                String mode = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
                exportObject.put("mode", mode);

                myOutWriter.append(exportObject.toString());
                res = true;
                myOutWriter.close();
                fOut.close();


            } catch (FileNotFoundException e) {
                Log.e(TAG, e.getLocalizedMessage());
            } catch (JSONException e) {
                Log.e(TAG, e.getLocalizedMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

        return res;
    }


    private static boolean  importRulesRoot(Context ctx, File file, StringBuilder msg) {
        boolean returnVal = false;
        BufferedReader br = null;
        try {
            com.topjohnwu.superuser.Shell.Result result  = com.topjohnwu.superuser.Shell.cmd("cat " + file.getAbsolutePath()).exec();
            List<String> out = result.getOut();
            String data = TextUtils.join("", out);

            try {
                //old export format
                JSONArray array = new JSONArray(data);
                updateRulesFromJson(ctx, (JSONObject) array.get(0), PREFS_NAME);
            } catch (JSONException e) {
                //new exported format
                JSONObject jsonObject = new JSONObject(data);
                //save mode
                if(jsonObject.get("mode") != null) {
                    G.pPrefs.edit().putString(PREF_MODE, jsonObject.getString("mode")).apply();
                }
                JSONArray array = (JSONArray) jsonObject.get("rules");
                updateRulesFromJson(ctx, (JSONObject) array.get(0), PREFS_NAME);
            }
            returnVal = true;
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        }
        return returnVal;
    }
    private static boolean importRules(Context ctx, File file, StringBuilder msg) {
        boolean returnVal = false;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            String data = text.toString();
            JSONObject jsonObject = new JSONObject(data);
            if (jsonObject.has("mode")) {
                G.pPrefs.edit().putString(PREF_MODE, jsonObject.getString("mode")).apply();
            }
            JSONArray array = jsonObject.optJSONArray("rules");
            if (array != null) {
                updateRulesFromJson(ctx, (JSONObject) array.get(0), PREFS_NAME);
            } else {
                updateRulesFromJson(ctx, jsonObject, PREFS_NAME);
            }

            returnVal = true;
        } catch (FileNotFoundException e) {
            if (e.getMessage().contains("EACCES")) {
                return importRulesRoot(ctx, file, msg);
            } else {
                msg.append(ctx.getString(R.string.import_rules_missing));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        return returnVal;
    }


    private static void updateRulesFromJson(Context ctx, JSONObject object, String preferenceName) throws JSONException {
        final StringBuilder[] uidBuilders = new StringBuilder[7];
        uidBuilders[WIFI_EXPORT] = new StringBuilder();
        uidBuilders[DATA_EXPORT] = new StringBuilder();
        uidBuilders[ROAM_EXPORT] = new StringBuilder();
        uidBuilders[VPN_EXPORT] = new StringBuilder();
        uidBuilders[TETHER_EXPORT] = new StringBuilder();
        uidBuilders[LAN_EXPORT] = new StringBuilder();
        uidBuilders[TOR_EXPORT] = new StringBuilder();

        Map<String, Object> json = JsonHelper.toMap(object);
        Map<String, PackageInfoData> muPackages = null;
        final PackageManager pm = ctx.getPackageManager();

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String pkgName = entry.getKey();
            int user_id = 0;
            if (G.isMultiUser()) {
                if (pkgName.contains("/")) {
                    String[] parts = pkgName.split("/");
                    pkgName = parts[0];
                    user_id = Integer.parseInt(parts[1]);
                }
            }
            if (pkgName.contains(":")) {
                pkgName = pkgName.split(":")[0];
            }

            JSONObject jsonObj = (JSONObject) JsonHelper.toJSON(entry.getValue());
            Iterator<?> keys = jsonObj.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                int exportType = Integer.parseInt(key);
                StringBuilder uidBuilder = uidBuilders[exportType];

                if (uidBuilder.length() != 0) {
                    uidBuilder.append('|');
                }

                if (pkgName.startsWith("dev.afwall.special")) {
                    uidBuilder.append(specialApps.get(pkgName));
                } else {
                    if (user_id > 0) {
                        if (muPackages == null) {
                            // build cache of all installed packages
                            muPackages = new HashMap();
                            List<PackageInfoData> apps = getApps(ctx, null);
                            for (PackageInfoData pkginfo : apps) {
                                int user_id_ = MultiUser.applicationUserId(pkginfo.appinfo);
                                if (user_id_ > 0) {
                                    muPackages.put(pkginfo.pkgName + "/" + String.valueOf(user_id_), pkginfo);
                                }
                            }
                        }
                        PackageInfoData pkginfo = muPackages.get(pkgName + "/" + String.valueOf(user_id));
                        if (pkginfo != null) {
                            uidBuilder.append(pkginfo.uid);
                        } else {
                            // Handle not found if needed
                        }
                    } else {
                        try {
                            uidBuilder.append(pm.getApplicationInfo(pkgName, 0).uid);
                        } catch (NameNotFoundException e) {
                            // Handle exception if needed
                        }
                    }
                }
            }
        }

        final SharedPreferences prefs = ctx.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        final Editor edit = prefs.edit();
        edit.putString(PREF_WIFI_PKG_UIDS, uidBuilders[WIFI_EXPORT].toString());
        edit.putString(PREF_3G_PKG_UIDS, uidBuilders[DATA_EXPORT].toString());
        edit.putString(PREF_ROAMING_PKG_UIDS, uidBuilders[ROAM_EXPORT].toString());
        edit.putString(PREF_VPN_PKG_UIDS, uidBuilders[VPN_EXPORT].toString());
        edit.putString(PREF_TETHER_PKG_UIDS, uidBuilders[TETHER_EXPORT].toString());
        edit.putString(PREF_LAN_PKG_UIDS, uidBuilders[LAN_EXPORT].toString());
        edit.putString(PREF_TOR_PKG_UIDS, uidBuilders[TOR_EXPORT].toString());

        edit.apply();
    }

    private static boolean shouldIgnoreKey(String key) {
        String[] ignore = {"appVersion", "fixLeak", "enableLogService", "sort", "storedProfile", "hasRoot", "logChains", "kingDetect", "fingerprintEnabled"};
        return Arrays.asList(ignore).contains(key);
    }

    private static boolean isIntType(String key) {
        String[] intType = {"logPingTime", "customDelay", "patternMax", "widgetX", "widgetY", "notification_priority"};
        return Arrays.asList(intType).contains(key);
    }

    private static void importProfiles(Context ctx, JSONObject profileObject) throws JSONException {
        Iterator<String> keys = profileObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONObject obj = profileObject.getJSONObject(key);
                updateRulesFromJson(ctx, obj, key);
            } catch (JSONException e) {
                if (e.getMessage().contains("No value")) {
                    // continue;
                }
            }
        }
    }
    private static boolean importAll(Context ctx, File file, StringBuilder msg) {
        boolean returnVal = false;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            String data = text.toString();
            JSONObject object = new JSONObject(data);

            // Allow/deny rule
            if (object.has("mode")) {
                G.pPrefs.edit().putString(PREF_MODE, object.getString("mode")).apply();
            }

            JSONArray prefArray = object.getJSONArray("prefs");
            for (int i = 0; i < prefArray.length(); i++) {
                JSONObject prefObj = prefArray.getJSONObject(i);
                Iterator<String> keys = prefObj.keys();

                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = prefObj.getString(key);
                    if (shouldIgnoreKey(key)) {
                        continue;
                    }
                    if (value.equals("true") || value.equals("false")) {
                        G.gPrefs.edit().putBoolean(key, Boolean.parseBoolean(value));
                    } else {
                        try {
                            if (key.equals("multiUserId")) {
                                G.gPrefs.edit().putLong(key, Long.parseLong(value));
                            } else if (isIntType(key)) {
                                G.gPrefs.edit().putString(key, value);
                            } else {
                                int intValue = Integer.parseInt(value);
                                G.gPrefs.edit().putInt(key, intValue);
                            }
                        } catch (NumberFormatException e) {
                            G.gPrefs.edit().putString(key, value);
                        }
                    }
                }
            }

            if (G.enableMultiProfile()) {
                if (G.isProfileMigrated()) {
                    JSONObject profileObject = object.getJSONObject("_profiles");
                    importProfiles(ctx, profileObject);
                } else {
                    JSONObject profileObject = object.getJSONObject("profiles");
                    importProfiles(ctx, profileObject);
                    JSONObject customProfileObject = object.getJSONObject("additional_profiles");
                    importProfiles(ctx, customProfileObject);
                }
            } else {
                JSONObject defaultRules = object.getJSONObject("default");
                updateRulesFromJson(ctx, defaultRules, PREFS_NAME);
            }
            returnVal = true;
        } catch (FileNotFoundException e) {
            msg.append(ctx.getString(R.string.import_rules_missing));
        } catch (IOException | JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        return returnVal;
    }

    public static boolean loadSharedPreferencesFromFile(Context ctx, StringBuilder builder, String fileName, boolean loadAll) {
        boolean res = false;
        File file = new File(fileName);
        if (file.exists()) {
            if (loadAll) {
                res = importAll(ctx, file, builder);
            } else {
                res = importRules(ctx, file, builder);
            }
        }
        return res;
    }

    /**
     * Probe log target
     * @param ctx
     */
    public static void probeLogTarget(final Context ctx) {

    }

    @SuppressLint("InlinedApi")
    public static void showInstalledAppDetails(Context context, String packageName) {
        final String SCHEME = "package";
        Intent intent = new Intent();
        final int apiLevel = Build.VERSION.SDK_INT;
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = Uri.fromParts(SCHEME, packageName, null);
        intent.setData(uri);
        context.startActivity(intent);
    }

    public static boolean isNetfilterSupported() {
        boolean netfiler_exists = new File("/proc/net/netfilter").exists();
        Shell.Result result = Shell.cmd("cat /proc/net/ip_tables_targets").exec();
        return netfiler_exists && result.isSuccess();
    }

    private static void initSpecial() {
        if (specialApps == null || specialApps.size() == 0) {
            specialApps = new HashMap<String, Integer>();
            specialApps.put("dev.afwall.special.any", SPECIAL_UID_ANY);
            specialApps.put("dev.afwall.special.kernel", SPECIAL_UID_KERNEL);
            specialApps.put("dev.afwall.special.tether", SPECIAL_UID_TETHER);
            //specialApps.put("dev.afwall.special.dnsproxy",SPECIAL_UID_DNSPROXY);
            specialApps.put("dev.afwall.special.ntp", SPECIAL_UID_NTP);
            for (String acct : specialAndroidAccounts) {
                String pkg = "dev.afwall.special." + acct;
                int uid = android.os.Process.getUidForName(acct);
                specialApps.put(pkg, uid);
            }
        }
    }

    public static void updateLanguage(Context context, String lang) {
        if (lang.equals("sys")) {
            Locale defaultLocale = Resources.getSystem().getConfiguration().locale;
            Locale.setDefault(defaultLocale);
            Resources res = context.getResources();
            Configuration conf = res.getConfiguration();
            conf.locale = defaultLocale;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                context.createConfigurationContext(conf);
            } else {
                context.getResources().updateConfiguration(conf, context.getResources().getDisplayMetrics());
            }
        } else if (!"".equals(lang)) {
            Locale locale = new Locale(lang);
            if (lang.contains("_")) {
                locale = new Locale(lang.split("_")[0], lang.split("_")[1]);
            }
            Locale.setDefault(locale);
            Resources res = context.getResources();
            Configuration conf = res.getConfiguration();
            conf.locale = locale;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                context.createConfigurationContext(conf);
            } else {
                context.getResources().updateConfiguration(conf, context.getResources().getDisplayMetrics());
            }
        }
    }

    public static void setUserOwner(Context context) {
        if (supportsMultipleUsers(context)) {
            try {
                Method getUserHandle = UserManager.class.getMethod("getUserHandle");
                int userHandle = (Integer) getUserHandle.invoke(context.getSystemService(Context.USER_SERVICE));
                G.setMultiUserId(userHandle);
            } catch (Exception ex) {
                Log.e(TAG, "Exception on setUserOwner " + ex.getMessage());
            }
        }
    }

    @SuppressLint("NewApi")
    public static boolean supportsMultipleUsers(Context context) {
        final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        try {
            Method supportsMultipleUsers = UserManager.class.getMethod("supportsMultipleUsers");
            return (Boolean) supportsMultipleUsers.invoke(um);
        } catch (Exception ex) {
            return false;
        }
    }

    public static String loadData(final Context context,
                                  final String resourceName) throws IOException {
        int resourceIdentifier = context
                .getApplicationContext()
                .getResources()
                .getIdentifier(resourceName, "raw",
                        context.getApplicationContext().getPackageName());
        if (resourceIdentifier != 0) {
            InputStream inputStream = context.getApplicationContext()
                    .getResources().openRawResource(resourceIdentifier);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    inputStream, StandardCharsets.UTF_8));
            String line;
            StringBuffer data = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                data.append(line);
            }
            reader.close();
            return data.toString();
        }
        return null;
    }

    /**
     * Encrypt the password
     *
     * @param key
     * @param data
     * @return
     */
    public static String hideCrypt(String key, String data) {
        if (key == null || data == null)
            return null;
        String encodeStr = null;
        try {
            DESKeySpec desKeySpec = new DESKeySpec(key.getBytes(charsetName));
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);
            byte[] dataBytes = data.getBytes(charsetName);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            encodeStr = Base64.encodeToString(cipher.doFinal(dataBytes), base64Mode);

        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return encodeStr;
    }

    /**
     * Decrypt the password
     *
     * @param key
     * @param data
     * @return
     */
    public static String unhideCrypt(String key, String data) {
        if (key == null || data == null)
            return null;

        String decryptStr = null;
        try {
            byte[] dataBytes = Base64.decode(data, base64Mode);
            DESKeySpec desKeySpec = new DESKeySpec(key.getBytes(charsetName));
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
            SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] dataBytesDecrypted = (cipher.doFinal(dataBytes));
            decryptStr = new String(dataBytesDecrypted);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return decryptStr;
    }

    public static boolean isMobileNetworkSupported(final Context ctx) {
        boolean hasMobileData = true;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
                    hasMobileData = false;
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return hasMobileData;
    }

    public static String getCurrentPackage(Context ctx) {
        PackageInfo pInfo = null;
        try {
            pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            Log.e(Api.TAG, "Package not found", e);
        }
        return pInfo.packageName;
    }

    public static int getConnectivityStatus(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        assert cm != null;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (null != activeNetwork) {

            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                return 1;

            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                return 2;

            if (activeNetwork.getType() == ConnectivityManager.TYPE_BLUETOOTH)
                return 3;
        }
        return 0;
    }

    /**
     * Apply default chains based on preference
     *
     * @param ctx
     */
    public static void applyDefaultChains(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<>();
        cmds.add(G.ipv4Input() ? "-P INPUT ACCEPT" : "-P INPUT DROP");
        cmds.add(G.ipv4Fwd() ? "-P FORWARD ACCEPT" : "-P FORWARD DROP");
        cmds.add(G.ipv4Output() ? "-P OUTPUT ACCEPT" : "-P OUTPUT DROP");
        applyQuick(ctx, cmds, callback);
        applyDefaultChainsv6(ctx, callback);
    }

    public static void applyDefaultChainsv6(Context ctx, RootCommand callback) {
        if (G.controlIPv6()) {
            List<String> cmds = new ArrayList<>();
            cmds.add(G.ipv6Input() ? "-P INPUT ACCEPT" : "-P INPUT DROP");
            cmds.add(G.ipv6Fwd() ? "-P FORWARD ACCEPT" : "-P FORWARD DROP");
            cmds.add(G.ipv6Output() ? "-P OUTPUT ACCEPT" : "-P OUTPUT DROP");
            applyIPv6Quick(ctx, cmds, callback);
        }
    }

    /**
     * Delete all firewall rules.  For diagnostic purposes only.
     *
     * @param ctx      application context
     * @param callback callback for completion
     */
    public static void flushOtherRules(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        cmds.add("-F firewall");
        cmds.add("-X firewall");
        apply46(ctx, cmds, callback);
    }

    // Clipboard
    public static void copyToClipboard(Context context, String val) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", val);
        clipboard.setPrimaryClip(clip);
    }

    public static void sendToastBroadcast(Context ctx, String message) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("TOAST");
        broadcastIntent.putExtra("MSG", message);
        ctx.sendBroadcast(broadcastIntent);
    }

    public static String getFixLeakPath(String fileName) {
        if (G.initPath() != null) {
            return G.initPath() + "/" + fileName;
        }
        return null;
    }

    public static boolean isFixPathFileExist(String fileName) {
        String path = getFixLeakPath(fileName);
        if (path != null) {
            File file = new File(path);
            return file.exists();
        }
        return false;
    }

    public static boolean mountDir(Context context, String path, String mountType) {
        if (path != null) {
            String busyboxPath = Api.getBusyBoxPath(context, true);
            if (!busyboxPath.trim().isEmpty()) {
                return RootTools.remount(path, mountType, busyboxPath);
            } else {
                return false;
            }
        }
        return false;
    }

    public static void checkAndCopyFixLeak(final Context context, final String fileName) {
        if (G.initPath() != null && G.fixLeak() && !isFixPathFileExist(fileName)) {
            final String srcPath = new File(ctx.getDir("bin", 0), fileName)
                    .getAbsolutePath();

            new Thread(() -> {
                String path = G.initPath();
                if (path != null) {
                    File f = new File(path);
                    if (mountDir(context, getFixLeakPath(fileName), "RW")) {
                        //make sure it's executable
                        new RootCommand()
                                .setReopenShell(true)
                                .setLogging(true)
                                .run(ctx, "chmod 755 " + f.getAbsolutePath());
                        RootTools.copyFile(srcPath, (f.getAbsolutePath() + "/" + fileName),
                                true, false);
                        mountDir(context, getFixLeakPath(fileName), "RO");
                    }
                }
            }).start();
        }
    }

    public static Context updateBaseContextLocale(Context context) {
        String language = G.locale(); // Helper method to get saved language from SharedPreferences
        Locale locale = new Locale(language);

        if (language.equals("zh") || language.equals("zh_CN")) {
            locale = Locale.SIMPLIFIED_CHINESE;
        } else if (language.equals("zh_TW")) {
            locale = Locale.TRADITIONAL_CHINESE;
        }

        Locale.setDefault(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResourcesLocale(context, locale);
        }
        return updateResourcesLocaleLegacy(context, locale);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResourcesLocale(Context context, Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    private static Context updateResourcesLocaleLegacy(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }

    public static void setDefaultPermission(ApplicationInfo applicationInfo) {

        boolean isModified = false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Editor edit = prefs.edit();

        // Get the mode type
        int modeType = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST).equals(Api.MODE_WHITELIST) ? 0 : 1;

        // Get the preference list
        List<DefaultConnectionPref> list = SQLite.select().from(DefaultConnectionPref.class)
                .where(DefaultConnectionPref_Table.modeType.eq(modeType))
                .queryList();

        for (DefaultConnectionPref pref : list) {
            if (pref.isState()) {
                int uid = applicationInfo.uid;
                switch (pref.getUid()) {
                    case 0:
                        edit.putString(PREF_LAN_PKG_UIDS, prefs.getString(PREF_LAN_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                    case 1:
                        edit.putString(PREF_WIFI_PKG_UIDS, prefs.getString(PREF_WIFI_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                    case 2:
                        edit.putString(PREF_3G_PKG_UIDS, prefs.getString(PREF_3G_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                    case 3:
                        edit.putString(PREF_ROAMING_PKG_UIDS, prefs.getString(PREF_ROAMING_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                    case 4:
                        edit.putString(PREF_TOR_PKG_UIDS, prefs.getString(PREF_TOR_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                    case 5:
                        edit.putString(PREF_VPN_PKG_UIDS, prefs.getString(PREF_VPN_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                    case 6:
                        edit.putString(PREF_TETHER_PKG_UIDS, prefs.getString(PREF_TETHER_PKG_UIDS, "") + "|" + uid);
                        isModified = true;
                        break;
                }
            }
        }
        if (isModified) {
            edit.apply();
            // Make sure rules are modified flag is set
            Api.setRulesUpToDate(false);
            fastApply(ctx, new RootCommand());
        }
    }

    static class RuleDataSet {

        List<Integer> wifiList;
        List<Integer> dataList;
        List<Integer> lanList;
        List<Integer> roamList;
        List<Integer> vpnList;
        List<Integer> tetherList;
        List<Integer> torList;

        RuleDataSet(List<Integer> uidsWifi, List<Integer> uids3g,
                    List<Integer> uidsRoam, List<Integer> uidsVPN, List<Integer> uidsTether,
                    List<Integer> uidsLAN, List<Integer> uidsTor) {
            this.wifiList = uidsWifi;
            this.dataList = uids3g;
            this.roamList = uidsRoam;
            this.vpnList = uidsVPN;
            this.tetherList = uidsTether;
            this.lanList = uidsLAN;
            this.torList = uidsTor;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((wifiList == null) ? 0 : dataList.hashCode());
            return result;
        }

        @Override
        public String toString() {
            String builder = (wifiList != null ? android.text.TextUtils.join(",", wifiList) : "") +
                    (dataList != null ? android.text.TextUtils.join(",", dataList) : "") +
                    (lanList != null ? android.text.TextUtils.join(",", lanList) : "") +
                    (roamList != null ? android.text.TextUtils.join(",", roamList) : "") +
                    (vpnList != null ? android.text.TextUtils.join(",", vpnList) : "") +
                    (tetherList != null ? android.text.TextUtils.join(",", tetherList) : "") +
                    (torList != null ? android.text.TextUtils.join(",", torList) : "");
            return builder.trim();
        }
    }

    private static class RunCommand extends AsyncTask<Object, List<String>, Integer> {

        private int exitCode = -1;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Integer doInBackground(Object... params) {
            @SuppressWarnings("unchecked")

            List<String> commands = (List<String>) params[0];
            StringBuilder res = (StringBuilder) params[1];
            Log.i(TAG, "Executing root commands of" + commands.size());
            try {
                if (Shell.getShell().isRoot() && !Shell.isAppGrantedRoot())
                    return -1;
                if (commands != null && commands.size() > 0) {
                    List<String> output = Shell.cmd(String.valueOf(commands)).exec().getOut();
                    if (output != null) {
                        exitCode = 0;
                        if (output.size() > 0) {
                            for (String str : output) {
                                res.append(str);
                                res.append("\n");
                            }
                        }
                    } else {
                        exitCode = 1;
                    }
                }
            } catch (Exception ex) {
                if (res != null)
                    res.append("\n").append(ex);
            }
            return exitCode;
        }


    }

    /**
     * Small structure to hold an application info
     */
    public static final class PackageInfoData {

        /**
         * linux user id
         */
        public int uid;
        /**
         * application names belonging to this user id
         */
        public List<String> names;
        /**
         * rules saving & load
         **/
        public String pkgName;

        /**
         * Application Type. 0 for system, 1 for user, 2 for core.
         */
        public int appType;

        /**
         * indicates if this application is selected for wifi
         */
        public boolean selected_wifi;
        /**
         * indicates if this application is selected for 3g
         */
        public boolean selected_3g;
        /**
         * indicates if this application is selected for roam
         */
        public boolean selected_roam;
        /**
         * indicates if this application is selected for vpn
         */
        public boolean selected_vpn;
        /**
         * indicates if this application is selected for tether
         */
        public boolean selected_tether;
        /**
         * indicates if this application is selected for lan
         */
        public boolean selected_lan;
        /**
         * indicates if this application is selected for tor mode
         */
        public boolean selected_tor;
        /**
         * toString cache
         */
        public String tostr;
        /**
         * application info
         */
        public ApplicationInfo appinfo;
        /**
         * cached application icon
         */
        public Drawable cached_icon;
        /**
         * indicates if the icon has been loaded already
         */
        public boolean icon_loaded;

        /* install time */
        public long installTime;

        /**
         * first time seen?
         */
        public boolean firstseen;

        public PackageInfoData() {
        }

        public PackageInfoData(int uid, String name, String pkgNameStr) {
            this.uid = uid;
            this.names = new ArrayList<String>();
            this.names.add(name);
            this.pkgName = pkgNameStr;
        }

        public PackageInfoData(String user, String name, String pkgNameStr) {
            this(android.os.Process.getUidForName(user), name, pkgNameStr);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof PackageInfoData)) {
                return false;
            }

            PackageInfoData pkg = (PackageInfoData) o;

            return pkg.uid == uid &&
                    pkg.pkgName.equals(pkgName);
        }

        @Override
        public int hashCode() {
            int result = 17;
            if (appinfo != null) {
                result = 31 * result + appinfo.hashCode();
            }
            result = 31 * result + uid;
            result = 31 * result + pkgName.hashCode();
            return result;
        }

        /**
         * Screen representation of this application
         */
        @Override
        public String toString() {
            if (tostr == null) {
                StringBuilder s = new StringBuilder();
                //if (uid > 0) s.append(uid + ": ");
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }

        public String toStringWithUID() {
            if (tostr == null) {
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
                s.append(" / ");
                s.append(uid);
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }

    }

    public static void copySharedPreferences(SharedPreferences fromPreferences, SharedPreferences.Editor toEditor) {
        for (Map.Entry<String, ?> entry : fromPreferences.getAll().entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof String) {
                toEditor.putString(key, ((String) value));
            } else if (value instanceof Set) {
                toEditor.putStringSet(key, (Set<String>) value); // EditorImpl.putStringSet already creates a copy of the set
            } else if (value instanceof Integer) {
                toEditor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                toEditor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                toEditor.putFloat(key, (Float) value);
            } else if (value instanceof Boolean) {
                toEditor.putBoolean(key, (Boolean) value);
            }
        }
        toEditor.commit();
    }

    @NonNull
    public static Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }



    @TargetApi(Build.VERSION_CODES.M)
    public static boolean batteryOptimized(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return !pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

}
