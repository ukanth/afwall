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
import android.app.Activity;

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
import java.io.OutputStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import dev.ukanth.ufirewall.MainActivity.GetAppList;
import dev.ukanth.ufirewall.customrules.CustomRule;
import dev.ukanth.ufirewall.customrules.CustomRule_Table;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPref;
import dev.ukanth.ufirewall.preferences.DefaultConnectionPref_Table;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.service.RootShellService;
import dev.ukanth.ufirewall.util.ApplicationErrorLog;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.ThemeHelper;
import dev.ukanth.ufirewall.util.JsonHelper;
import dev.ukanth.ufirewall.util.UidResolver;
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

    /**
     * special application UID used for NTP
     */
    public static final int SPECIAL_UID_NTP = -14;

    public static final int NOTIFICATION_ID = 1;
    public static final String PREF_FIREWALL_STATUS = "AFWallStatus";
    public static final String DEFAULT_PREFS_NAME = "AFWallPrefs";
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
    private static final String[] staticChains = {"", "-input", "-3g", "-wifi", "-reject", "-vpn", "-3g-tether", "-3g-home", "-3g-roam", "-wifi-tether", "-wifi-wan", "-wifi-lan", "-usb-tether", "-tor", "-tor-reject", "-tether", "-3g-home-reject", "-3g-roam-reject", "-wifi-wan-reject", "-wifi-lan-reject", "-vpn-reject", "-tether-reject"};
    private static final String[] LOCAL_RESERVED_IPV4_RANGES = {"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16"};
    private static final String[] LOCAL_RESERVED_IPV6_RANGES = {"fc00::/7", "fe80::/10"};
    private static volatile boolean globalStatus = false;

    private static final Object GLOBAL_STATUS_LOCK = new Object();
    
    /**
     * Check if rules are currently being applied
     * @return true if rules application is in progress
     */
    public static boolean isRulesBeingApplied() {
        return globalStatus;
    }

    public static List<Integer> getListOfUids() {
        return listOfUids;
    }

    private static List<Integer> listOfUids = new ArrayList<>();


    private static Map<Integer, ApplicationInfo> uidToApplicationInfoMap = null;


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
    //private static volatile String AFWALL_CHAIN_NAME = "afwall";
    private static final Object CHAIN_NAME_LOCK = new Object();
    private static Map<String, Integer> specialApps = null;
    private static volatile boolean rulesUpToDate = false;
    private static final Object RULES_LOCK = new Object();
    public static void setRulesUpToDate(boolean rulesUpToDate) {
        synchronized (RULES_LOCK) {
            Api.rulesUpToDate = rulesUpToDate;
        }
    }
    public static boolean getRulesUpToDate() {
        synchronized (RULES_LOCK) {
            return Api.rulesUpToDate;
        }
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
        String ip_path = G.ip_path();
        String binaryName = setv6 ? "ip6tables" : "iptables";
        
        // If built-in binaries have previously failed with exit 126, prefer system binaries
        if (G.isBuiltinIptablesFailed() && !ip_path.equals("builtin")) {
            Log.i(TAG, "Built-in iptables previously failed, preferring system binary for " + binaryName);
            String systemBinaryPath = findSystemBinary(binaryName);
            if (systemBinaryPath != null) {
                if (Api.bbPath == null) {
                    Api.bbPath = getBusyBoxPath(ctx, true);
                }
                return systemBinaryPath;
            }
            Log.w(TAG, "System binary " + binaryName + " not found despite previous built-in failure");
        }
        
        // First priority: check system binary if preference is "system" or "auto"
        if (ip_path.equals("system") || ip_path.equals("auto")) {
            String systemBinaryPath = findSystemBinary(binaryName);
            if (systemBinaryPath != null) {
                if (Api.bbPath == null) {
                    Api.bbPath = getBusyBoxPath(ctx, true);
                }
                return systemBinaryPath;
            }
            
            // If system binary not found and preference is "system", log warning
            if (ip_path.equals("system")) {
                Log.w(TAG, "System binary " + binaryName + " not found, falling back to built-in");
            }
        }
        
        // Second priority: use built-in binary
        // Check if built-in binary exists for current architecture
        String builtinDir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
        String builtinPath = builtinDir + binaryName;
        
        File builtinFile = new File(builtinPath);
        if (builtinFile.exists() && builtinFile.canExecute()) {
            if (Api.bbPath == null) {
                Api.bbPath = getBusyBoxPath(ctx, true);
            }
            return builtinPath;
        }
        
        // Fallback: try to install built-in binaries if they don't exist
        Log.w(TAG, "Built-in binary " + binaryName + " not found, attempting to install binaries");
        if (assertBinaries(ctx, false)) {
            if (Api.bbPath == null) {
                Api.bbPath = getBusyBoxPath(ctx, true);
            }
            return builtinPath;
        }
        
        // Last resort: return the path even if binary doesn't exist (will likely fail at runtime)
        Log.e(TAG, "No working " + binaryName + " binary found, returning built-in path anyway");
        if (Api.bbPath == null) {
            Api.bbPath = getBusyBoxPath(ctx, true);
        }
        return builtinPath;
    }

    /**
     * Find system binary by checking common system paths
     *
     * @param binaryName the name of the binary to find
     * @return full path to the binary if found, null otherwise
     */
    public static String findSystemBinary(String binaryName) {
        // Common paths where system iptables/ip6tables binaries are located
        String[] systemPaths = {
            "/system/bin/" + binaryName,
            "/system/xbin/" + binaryName,
            "/vendor/bin/" + binaryName,
            "/sbin/" + binaryName,
            "/usr/bin/" + binaryName,
            "/bin/" + binaryName
        };
        
        for (String path : systemPaths) {
            File binaryFile = new File(path);
            if (binaryFile.exists() && binaryFile.canExecute()) {
                Log.i(TAG, "Found system binary: " + path);
                return path;
            }
        }
        
        // Also try using 'which' command if available
        try {
            Shell.Result result = Shell.cmd("which " + binaryName).exec();
            if (result.isSuccess() && !result.getOut().isEmpty()) {
                String whichPath = result.getOut().get(0).trim();
                File whichFile = new File(whichPath);
                if (whichFile.exists() && whichFile.canExecute()) {
                    Log.i(TAG, "Found system binary via 'which': " + whichPath);
                    return whichPath;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Unable to use 'which' command to find " + binaryName + ": " + e.getMessage());
        }
        
        Log.d(TAG, "System binary " + binaryName + " not found in any standard location");
        return null;
    }

    /**
     * Determine toybox/busybox or built in
     *
     * @param ctx
     * @param considerSystem
     * @return
     */
    public static String getBusyBoxPath(Context ctx, boolean considerSystem) {
        String bb_path = G.bb_path();
        
        // First priority: check system busybox if preference is "system" or "auto" and considerSystem is true
        if (considerSystem && (bb_path.equals("system") || bb_path.equals("auto"))) {
            String systemBusybox = findSystemBinary("busybox");
            if (systemBusybox != null) {
                return systemBusybox + " ";
            }
            
            // If system busybox not found and preference is "system", log warning and fall back
            if (bb_path.equals("system")) {
                Log.w(TAG, "System busybox not found, falling back to built-in");
            }
        }
        
        // Second priority: use built-in busybox
        String dir = ctx.getDir("bin", 0).getAbsolutePath();
        String builtinPath = dir + "/busybox";
        
        File builtinFile = new File(builtinPath);
        if (builtinFile.exists() && builtinFile.canExecute()) {
            return builtinPath + " ";
        }
        
        // Fallback: return built-in path even if it doesn't exist yet (may be installed later)
        if (!builtinFile.exists()) {
            Log.w(TAG, "Built-in busybox not found at " + builtinPath + ", returning path anyway");
        } else {
            Log.w(TAG, "Built-in busybox exists but not executable at " + builtinPath + ", permissions: " + 
                  (builtinFile.canRead() ? "R" : "-") + 
                  (builtinFile.canWrite() ? "W" : "-") + 
                  (builtinFile.canExecute() ? "X" : "-"));
        }
        return builtinPath + " ";
    }

    /**
     * Get NFLog Path - Enhanced version with fallback support
     *
     * @param ctx Context
     * @return path to best available nflog binary
     */
    public static String getNflogPath(Context ctx) {
        String dir = ctx.getDir("bin", 0).getAbsolutePath();
        String originalPath = dir + "/nflog";
        File originalFile = new File(originalPath);
        
        if (!originalFile.exists()) {
            Log.w(TAG, "No NFLOG binary found at: " + originalPath);
            return null;
        }
        
        if (!originalFile.canExecute()) {
            Log.w(TAG, "NFLOG binary not executable at: " + originalPath);
            // Try to make it executable
            try {
                originalFile.setExecutable(true);
                if (!originalFile.canExecute()) {
                    Log.e(TAG, "Failed to make nflog executable");
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to make nflog executable: " + e.getMessage());
                return null;
            }
        }
        
        Log.i(TAG, "Using original NFLOG binary");
        return originalPath;
    }
    
    /**
     * Get enhanced NFLOG command with optimized parameters
     * 
     * @param ctx Context
     * @param queueNum NFLOG queue number
     * @return complete command string with optimizations
     */
    public static String getEnhancedNflogCommand(Context ctx, int queueNum) {
        String nflogPath = getNflogPath(ctx);
        if (nflogPath == null) {
            return null;
        }
        
        // Use standard nflog command with queue number
        return nflogPath + " " + queueNum;
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

        executeSecureCommand(new String[]{"chmod", mode, abspath});
    }

    /**
     * Execute system commands securely using ProcessBuilder to prevent command injection
     * 
     * @param command Array of command and arguments (prevents shell interpretation)
     * @throws IOException if command execution fails
     * @throws InterruptedException if command is interrupted
     */
    private static void executeSecureCommand(String[] command) throws IOException, InterruptedException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
        
        // Validate command and arguments don't contain dangerous characters
        for (String arg : command) {
            if (arg == null || arg.contains("\n") || arg.contains("\r") || 
                arg.contains(";") || arg.contains("&") || arg.contains("|") || 
                arg.contains("`") || arg.contains("$")) {
                Log.w(TAG, "Rejecting command with potentially dangerous characters: " + java.util.Arrays.toString(command));
                throw new SecurityException("Command contains illegal characters");
            }
        }
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().clear(); // Clear environment to prevent injection via env vars
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            // For chmod commands, permission denied is expected on Android - don't fail the installation
            if (command.length > 0 && "chmod".equals(command[0])) {
                Log.w(TAG, "chmod command failed (expected on Android without root): exit code " + exitCode + " for " + java.util.Arrays.toString(command));
                return; // Don't throw exception for chmod failures
            }
            Log.w(TAG, "Command failed with exit code " + exitCode + ": " + java.util.Arrays.toString(command));
            throw new IOException("Command execution failed with exit code: " + exitCode);
        }
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
        String action = whitelist ? " -j RETURN" : " -j " + chain + "-reject";

        if (uids.contains(SPECIAL_UID_ANY)) {
            if (!whitelist) {
                cmds.add("-A " + chain + action);
            } else {
                cmds.add("-A " + chain + " -j RETURN");
            }
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

            if (whitelist) {
                addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p tcp --dport 53", " -j RETURN");
            } else {
                addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p tcp --dport 53", " -j RETURN");
            }


            // NTP service runs as "system" user
            if (uids.contains(SPECIAL_UID_NTP)) {
                addRuleForUsers(cmds, new String[]{"system"}, "-A " + chain + " -p udp --dport 123", action);
            }


            if (G.getPrivateDnsStatus()) {
                cmds.add("-A " + chain + " -p tcp --dport 853" + " -j ACCEPT");
                // disabling HTTPS over DNS
                //cmds.add("-A " + chain + " -p tcp --dport 443" + " -j ACCEPT");
            }

            boolean kernel_checked = uids.contains(SPECIAL_UID_KERNEL);
            
            if (whitelist) {
                if (kernel_checked) {
                    // reject any other UIDs, but allow the kernel through
                    // Use fallback rule if owner module is not available
                    if (G.hasOwnerModule()) {
                        Log.d(TAG, "Adding whitelist kernel rule with owner module for chain " + chain);
                        cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j " + chain + "-reject");
                    } else {
                        Log.w(TAG, "Owner module not available, using fallback rule for chain " + chain);
                        cmds.add("-A " + chain + " -j " + chain + "-reject");
                    }
                } else {
                    // kernel is blocked so reject everything
                    String rejectRule = "-A " + chain + " -j " + chain + "-reject";
                    cmds.add(rejectRule);
                }
            } else {
                if (kernel_checked) {
                    // allow any other UIDs, but block the kernel
                    if (G.hasOwnerModule()) {
                        cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j RETURN");
                        cmds.add("-A " + chain + " -j " + chain + "-reject");
                    } else {
                        Log.w(TAG, "Owner module not available, using fallback rule for chain " + chain);
                        cmds.add("-A " + chain + " -j " + chain + "-reject");
                    }
                }
            }

            //add 1052 for LAN
            if(G.enableLAN() && G.hasOwnerModule()) {
                cmds.add("-A " + "afwall-wifi-lan" + " -m owner --uid-owner 1052 -j RETURN");
            }

            if (G.hasOwnerModule()) {
                cmds.add("-A " + "afwall-wifi-wan" + " -m owner --uid-owner 1052 -j RETURN");
            }
        }
    }

    private static void addUidDeltaForChain(List<String> cmds, String chain, int uid, boolean selected, boolean whitelist) {
        cmds.add("#NOCHK# -D " + chain + " -m owner --uid-owner " + uid + " -j RETURN");
        cmds.add("#NOCHK# -D " + chain + " -m owner --uid-owner " + uid + " -j " + chain + "-reject");
        if (selected) {
            String action = whitelist ? "RETURN" : chain + "-reject";
            cmds.add("-I " + chain + " 1 -m owner --uid-owner " + uid + " -j " + action);
        }
    }

    private static void addChangedUidRules(List<String> cmds, PackageInfoData app, boolean ipv6, String chainName, boolean whitelist) {
        int uid = app.uid;
        addUidDeltaForChain(cmds, chainName + "-3g-home", uid, app.selected_3g, whitelist);
        if (G.enableRoam()) {
            addUidDeltaForChain(cmds, chainName + "-3g-roam", uid, app.selected_roam, whitelist);
        }
        addUidDeltaForChain(cmds, chainName + "-wifi-wan", uid, app.selected_wifi, whitelist);
        if (G.enableLAN()) {
            addUidDeltaForChain(cmds, chainName + "-wifi-lan", uid, app.selected_lan, whitelist);
            addLanReservedUidDelta(cmds, uid, app.selected_lan, chainName, whitelist, ipv6);
        }
        if (G.enableVPN()) {
            addUidDeltaForChain(cmds, chainName + "-vpn", uid, app.selected_vpn, whitelist);
        }
        if (G.enableTether()) {
            addUidDeltaForChain(cmds, chainName + "-tether", uid, app.selected_tether, whitelist);
        }
        if (G.enableTor()) {
            cmds.add("#NOCHK# -D " + chainName + "-tor-reject -m owner --uid-owner " + uid + " -j " + chainName + "-reject");
            if (app.selected_tor && (G.enableInbound() || ipv6)) {
                cmds.add("-I " + chainName + "-tor-reject 1 -m owner --uid-owner " + uid + " -j " + chainName + "-reject");
            }
            if (!ipv6) {
                cmds.add("#NOCHK# -t nat -D " + chainName + "-tor-check -m owner --uid-owner " + uid + " -j " + chainName + "-tor-filter");
                if (app.selected_tor) {
                    cmds.add("-t nat -I " + chainName + "-tor-check 1 -m owner --uid-owner " + uid + " -j " + chainName + "-tor-filter");
                }
            }
        }
    }

    private static void addRejectRules(List<String> cmds, String chainName) {
        // set up reject chain to log or not log
        // this can be changed dynamically through the Firewall Logs activity

        if (G.enableLogService()) {
            if (G.logTarget().trim().equals("LOG")) {
                //cmds.add("-A " + chainName  + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL-ALLOW}\" --log-level 4 --log-uid");
                String logRule = "-A " + chainName + "-reject" + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL}\" --log-level 4 --log-uid  --log-tcp-options --log-ip-options";
                Log.d(TAG, "Adding LOG rule to reject chain: " + logRule);
                cmds.add(logRule);
            } else if (G.logTarget().trim().equals("NFLOG")) {
                //cmds.add("-A " + chainName + " -j NFLOG --nflog-prefix \"{AFL-ALLOW}\" --nflog-group 40");
                String nflogRule = "-A " + chainName + "-reject" + " -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40";
                Log.d(TAG, "Adding NFLOG rule to reject chain: " + nflogRule);
                cmds.add(nflogRule);
            }
        }
        String rejectRule = "-A " + chainName + "-reject" + " -j REJECT";
        Log.d(TAG, "Adding final REJECT rule: " + rejectRule);
        cmds.add(rejectRule);
        
        // Also populate individual reject chains that are used by whitelist mode
        String[] rejectChainSuffixes = {"-3g-home-reject", "-3g-roam-reject", "-wifi-wan-reject", 
                                       "-wifi-lan-reject", "-vpn-reject", "-tether-reject"};
        for (String suffix : rejectChainSuffixes) {
            String individualRejectChain = chainName + suffix;
            Log.d(TAG, "Populating individual reject chain: " + individualRejectChain);
            if (G.enableLogService() && G.logTarget().trim().equals("NFLOG")) {
                String nflogRule = "-A " + individualRejectChain + " -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40";
                Log.d(TAG, "Adding NFLOG to individual reject chain: " + nflogRule);
                cmds.add(nflogRule);
            }
            String individualRejectRule = "-A " + individualRejectChain + " -j REJECT";
            Log.d(TAG, "Adding REJECT to individual reject chain: " + individualRejectRule);
            cmds.add(individualRejectRule);
        }
    }

    private static void addTorRules(List<String> cmds, List<Integer> uids, Boolean whitelist, Boolean ipv6, String chainName) {
        for (Integer uid : uids) {
            if (uid != null && uid >= 0) {
                if (G.enableInbound() || ipv6) {
                    cmds.add("-A " + chainName + "-tor-reject -m owner --uid-owner " + uid + " -j " + chainName + "-reject");
                }
                if (!ipv6) {
                    cmds.add("-t nat -A " + chainName + "-tor-check -m owner --uid-owner " + uid + " -j " + chainName + "-tor-filter");
                }
            }
        }
        if (ipv6) {
            cmds.add("-A " + chainName + " -j " + chainName + "-tor-reject");
        } else {
            Integer socks_port = 9050;
            Integer http_port = 8118;
            Integer dns_port = 5400;
            Integer tcp_port = 9040;
            cmds.add("-t nat -A " + chainName + "-tor-filter -d 127.0.0.1 -p tcp --dport " + socks_port + " -j RETURN");
            cmds.add("-t nat -A " + chainName + "-tor-filter -d 127.0.0.1 -p tcp --dport " + http_port + " -j RETURN");
            cmds.add("-t nat -A " + chainName + "-tor-filter -p udp --dport 53 -j REDIRECT --to-ports " + dns_port);
            cmds.add("-t nat -A " + chainName + "-tor-filter -p tcp --tcp-flags FIN,SYN,RST,ACK SYN -j REDIRECT --to-ports " + tcp_port);
            cmds.add("-t nat -A " + chainName + "-tor-filter -j MARK --set-mark 0x500");
            cmds.add("-t nat -A " + chainName + " -j " + chainName + "-tor-check");
            cmds.add("-A " + chainName + "-tor -m mark --mark 0x500 -j " + chainName + "-reject");
            cmds.add("-A " + chainName + " -j " + chainName + "-tor");
        }
        if (G.enableInbound()) {
            cmds.add("-A " + chainName + "-input -j " + chainName + "-tor-reject");
        }
    }

    private static String sanitizeRule(String rule) {
        String trimmed = rule.trim();

        // Check for dangerous command chaining/substitution
        if (trimmed.contains("&&") || trimmed.contains("||") || trimmed.contains(";") ||
                trimmed.contains("|") || trimmed.contains("`")) {
            Log.w(TAG, "Rejecting potentially dangerous custom rule (command chaining): " + rule);
            return null;
        }

        // Check for dangerous commands
        if (trimmed.contains("rm ") || trimmed.contains("dd ") ||
                trimmed.contains("chmod ") || trimmed.contains("chown ") ||
                trimmed.contains("su ") || trimmed.contains("sudo ")) {
            Log.w(TAG, "Rejecting potentially dangerous custom rule (system modification): " + rule);
            return null;
        }

        // Allow $ only for whitelisted variables
        if (trimmed.contains("$")) {
            // Check if it's using allowed variables
            String tempRule = trimmed;
            tempRule = tempRule.replace("$IPTABLES", "");
            tempRule = tempRule.replace("$IP6TABLES", "");
            tempRule = tempRule.replace("$BUSYBOX", "");
            tempRule = tempRule.replace("$IPV6", "");

            if (tempRule.contains("$")) {
                Log.w(TAG, "Rejecting custom rule with non-whitelisted variables: " + rule);
                return null;
            }
        }

        // Reject file sourcing (dot-source) - potential command injection vector
        if (trimmed.startsWith(". ") || trimmed.startsWith("source ")) {
            Log.w(TAG, "Rejecting file sourcing in custom rule (security risk): " + rule);
            return null;
        }

        // Allow basic iptables commands (keep existing check)
        if (!trimmed.startsWith("iptables ") && !trimmed.startsWith("ip6tables ") &&
                !trimmed.startsWith("$IPTABLES ") && !trimmed.startsWith("$IP6TABLES ") &&
                !trimmed.startsWith("-A ") && !trimmed.startsWith("-I ") &&
                !trimmed.startsWith("-D ") && !trimmed.startsWith("-F ") &&
                !trimmed.startsWith("-P ") && !trimmed.startsWith("-N ")) {
            Log.w(TAG, "Rejecting non-iptables rule: " + rule);
            return null;
        }

        return trimmed;
    }

    public static String validateCustomRuleForStorage(String rule) {
        if (rule == null) {
            return null;
        }
        return sanitizeRule(rule);
    }

    private static void addCustomRules(String prefName, List<String> cmds) {
        addCustomRules(prefName, cmds, false);
    }

    private static void addCustomRules(String prefName, List<String> cmds, boolean ipv6) {
        String customRulesStr = G.pPrefs.getString(prefName, "");
        if (!customRulesStr.isEmpty()) {
            String[] customRules = customRulesStr.split("[\\r\\n]+");
            for (String rule : customRules) {
                if (rule.matches(".*\\S.*")) {
                    // Sanitize the rule to prevent command injection
                    String sanitizedRule = sanitizeRule(rule.trim());
                    if (sanitizedRule != null && !sanitizedRule.isEmpty()) {
                        cmds.add("#LITERAL# " + sanitizedRule);
                    }
                }
            }
        }

        if (PREF_CUSTOMSCRIPT.equals(prefName) && !ipv6) {
            addDatabaseCustomRules(cmds);
        }
    }

    private static void addDatabaseCustomRules(List<String> cmds) {
        try {
            List<CustomRule> customRules = SQLite.select()
                    .from(CustomRule.class)
                    .where(CustomRule_Table.active.eq(true))
                    .queryList();

            for (CustomRule customRule : customRules) {
                if (!dev.ukanth.ufirewall.util.AppRuleHelper.belongsToCurrentProfile(customRule)) {
                    continue;
                }
                String rule = customRule.getRule();
                if (rule != null && rule.matches(".*\\S.*")) {
                    String sanitizedRule = sanitizeRule(rule.trim());
                    if (sanitizedRule != null && !sanitizedRule.isEmpty()) {
                        cmds.add(sanitizedRule);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to load database custom rules", e);
        }
    }

    private static Set<String> getLanDestinationRanges(InterfaceDetails cfg, boolean ipv6) {
        LinkedHashSet<String> ranges = new LinkedHashSet<>();
        if (ipv6) {
            if (cfg != null) {
                ranges.addAll(cfg.lanMaskV6);
            }
            ranges.addAll(Arrays.asList(LOCAL_RESERVED_IPV6_RANGES));
        } else {
            if (cfg != null) {
                ranges.addAll(cfg.lanMaskV4);
            }
            ranges.addAll(Arrays.asList(LOCAL_RESERVED_IPV4_RANGES));
        }
        return ranges;
    }

    private static List<String> getLocalReservedRanges(boolean ipv6) {
        return Arrays.asList(ipv6 ? LOCAL_RESERVED_IPV6_RANGES : LOCAL_RESERVED_IPV4_RANGES);
    }

    private static void addLanReservedAllowRulesForUidlist(List<String> cmds, List<Integer> uids, String chainName,
                                                          boolean whitelist, boolean ipv6) {
        if (!whitelist || uids == null || uids.isEmpty()) {
            return;
        }

        String chain = chainName + "-wifi-fork";
        List<String> ranges = getLocalReservedRanges(ipv6);
        if (uids.contains(SPECIAL_UID_ANY)) {
            for (String range : ranges) {
                cmds.add("-A " + chain + " -d " + range + " -j RETURN");
            }
            return;
        }

        for (Integer uid : uids) {
            if (uid != null && uid >= 0) {
                for (String range : ranges) {
                    cmds.add("-A " + chain + " -d " + range + " -m owner --uid-owner " + uid + " -j RETURN");
                }
            }
        }
    }

    private static void addLanReservedUidDelta(List<String> cmds, int uid, boolean selected, String chainName,
                                               boolean whitelist, boolean ipv6) {
        if (!whitelist) {
            return;
        }

        String chain = chainName + "-wifi-fork";
        for (String range : getLocalReservedRanges(ipv6)) {
            cmds.add("#NOCHK# -D " + chain + " -d " + range + " -m owner --uid-owner " + uid + " -j RETURN");
            if (selected) {
                cmds.add("-I " + chain + " 1 -d " + range + " -m owner --uid-owner " + uid + " -j RETURN");
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
    private static void addInterfaceRouting(Context ctx, List<String> cmds, boolean ipv6, String chainName) {
        addInterfaceRouting(ctx, cmds, ipv6, chainName, null);
    }

    private static void addInterfaceRouting(Context ctx, List<String> cmds, boolean ipv6, String chainName, RuleDataSet ruleDataSet) {
        try {
            //force only for v4
            final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx, !ipv6);
            final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
            for (String s : dynChains) {
                cmds.add("-F " + chainName + s);
            }

            if (whitelist) {
                // always allow the DHCP client full wifi access
                addRuleForUsers(cmds, new String[]{"dhcp", "wifi"}, "-A " + chainName + "-wifi-postcustom", "-j RETURN");
            }

            if (cfg.isWifiTethered || cfg.isUsbTethered) {
                if (cfg.isWifiTethered) {
                    cmds.add("-A " + chainName + "-wifi-postcustom -j " + chainName + "-wifi-tether");
                } else {
                    cmds.add("-A " + chainName + "-wifi-postcustom -j " + chainName + "-wifi-fork");
                }
                
                if (cfg.isUsbTethered) {
                    cmds.add("-A " + chainName + "-3g-postcustom -j " + chainName + "-usb-tether");
                } else {
                    cmds.add("-A " + chainName + "-3g-postcustom -j " + (cfg.isWifiTethered ? chainName + "-3g-tether" : chainName + "-3g-fork"));
                }
            } else {
                cmds.add("-A " + chainName + "-wifi-postcustom -j " + chainName + "-wifi-fork");
                cmds.add("-A " + chainName + "-3g-postcustom -j " + chainName + "-3g-fork");
            }

            if (G.enableLAN() && !cfg.isWifiTethered) {
                // Support multiple LAN subnets (Issue #1362)
                // Subnet-specific rules are added first, then a catch-all routes remaining traffic to WAN.
                // iptables evaluates rules top-to-bottom, so LAN subnets are matched before the catch-all.
                if (ruleDataSet != null) {
                    addLanReservedAllowRulesForUidlist(cmds, ruleDataSet.lanList, chainName, whitelist, ipv6);
                }
                Set<String> lanRanges = getLanDestinationRanges(cfg, ipv6);
                if (lanRanges.isEmpty()) {
                    Log.i(TAG, "no LAN ranges found: " + G.enableIPv6() + "," + (ipv6 ? cfg.lanMaskV6 : cfg.lanMaskV4));
                }
                for (String subnet : lanRanges) {
                    cmds.add("-A " + chainName + "-wifi-fork -d " + subnet + " -j " + chainName + "-wifi-lan");
                }
                // Catch-all: route everything not matching a LAN subnet to WAN
                cmds.add("-A " + chainName + "-wifi-fork -j " + chainName + "-wifi-wan");
            } else {
                cmds.add("-A " + chainName + "-wifi-fork -j " + chainName + "-wifi-wan");
            }

            if (G.enableRoam() && cfg.isRoaming) {
                cmds.add("-A " + chainName + "-3g-fork -j " + chainName + "-3g-roam");
            } else {
                cmds.add("-A " + chainName + "-3g-fork -j " + chainName + "-3g-home");
            }


        } catch (Exception e) {
            Log.i(TAG, "Exception while applying shortRules " + e.getMessage());
        }

    }

    public static String getSpecialAppName(int uid) {
        // First, try special apps (AFWall+ specific entries)
        List<PackageInfoData> packageInfoData = getSpecialData();
        for (PackageInfoData infoData : packageInfoData) {
            if (infoData.uid == uid) {
                return infoData.names.get(0);
            }
        }
        
        // If not found in special apps, use comprehensive UID resolver
        return UidResolver.resolveUid(ctx, uid);
    }


    private static void applyShortRules(Context ctx, List<String> cmds, boolean ipv6) {
        Log.i(TAG, "Setting OUTPUT chain to DROP");
        cmds.add("-P OUTPUT DROP");
        /*FIXME: Adding custom rules might increase the time */
        Log.i(TAG, "Applying custom rules");
        addCustomRules(Api.PREF_CUSTOMSCRIPT, cmds, ipv6);
        String chainName = getThreadSafeChainName();
        addInterfaceRouting(ctx, cmds, ipv6, chainName);
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
        return applyIptablesRulesImpl(ctx, ruleDataSet, showErrors, out, ipv6, null);
    }
    
    private static boolean applyIptablesRulesImpl(final Context ctx, RuleDataSet ruleDataSet, final boolean showErrors,
                                                  List<String> out, boolean ipv6, String threadSafeChainName) {
        if (ctx == null) {
            return false;
        }

        assertBinaries(ctx, showErrors);

        final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx, !ipv6);
        
        // Use thread-safe chain name if provided, otherwise determine it safely
        final String chainName;
        if (threadSafeChainName != null) {
            chainName = threadSafeChainName;
        } else {
            chainName = getThreadSafeChainName();
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

            // Create and flush all chains first to ensure they exist
            // Use NOCHK to avoid errors if chain already exists, then flush to ensure clean state
            for (String s : staticChains) {
                cmds.add("#NOCHK# -N " + chainName + s);
                cmds.add("#NOCHK# -F " + chainName + s);
            }
            for (String s : dynChains) {
                cmds.add("#NOCHK# -N " + chainName + s);
                cmds.add("#NOCHK# -F " + chainName + s);
            }
            

            cmds.add("#NOCHK# -D OUTPUT -j " + chainName);
            cmds.add("-I OUTPUT 1 -j " + chainName);


            if (G.enableInbound()) {
                cmds.add("#NOCHK# -D INPUT -j " + chainName + "-input");
                cmds.add("-I INPUT 1 -j " + chainName + "-input");
            }

            if (G.enableTor() && !ipv6) {
                for (String s : natChains) {
                    cmds.add("#NOCHK# -t nat -N " + chainName + s);
                    cmds.add("-t nat -F " + chainName + s);
                }
                cmds.add("#NOCHK# -t nat -D OUTPUT -j " + chainName);
                cmds.add("-t nat -I OUTPUT 1 -j " + chainName);
            }

            // custom rules in afwall-{3g,wifi,reject} supersede everything else
            addCustomRules(Api.PREF_CUSTOMSCRIPT, cmds, ipv6);

            cmds.add("-A " + chainName + "-3g -j " + chainName + "-3g-postcustom");
            cmds.add("-A " + chainName + "-wifi -j " + chainName + "-wifi-postcustom");
            addRejectRules(cmds, chainName);

            if (G.enableInbound()) {
                // we don't have any rules in the INPUT chain prohibiting inbound traffic, but
                // local processes can't reply to half-open connections without this rule
                cmds.add("-A " + chainName + " -m state --state ESTABLISHED -j RETURN");
                cmds.add("-A " + chainName + "-input -m state --state ESTABLISHED -j RETURN");
            }

            addInterfaceRouting(ctx, cmds, ipv6, chainName, ruleDataSet);

            // send wifi, 3G, VPN packets to the appropriate dynamic chain based on interface
            if (G.enableVPN()) {
                // if !enableVPN then we ignore those interfaces (pass all traffic)
                for (final String itf : ITFS_VPN) {
                    cmds.add("#NOCHK# -A " + chainName + " -o " + itf + " -j " + chainName + "-vpn");
                }
                // KitKat policy based routing - see:
                // http://forum.xda-developers.com/showthread.php?p=48703545
                // This covers mark range 0x3c - 0x47.  The official range is believed to be
                // 0x3c - 0x45 but this is close enough.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    cmds.add("-A " + chainName + " -m mark --mark 0x3c/0xfffc -g " + chainName + "-vpn");
                    cmds.add("-A " + chainName + " -m mark --mark 0x40/0xfff8 -g " + chainName + "-vpn");
                }
            }

            if (G.enableTether()) {
                for (final String itf : ITFS_TETHER) {
                    cmds.add("#NOCHK# -A " + chainName + " -o " + itf + " -j " + chainName + "-tether");
                }
            }

            /*if (G.enableLAN()) {
                // Allow all Android system UIDs (0-9999) on loopback unconditionally
                cmds.add("-A " + chainName + " -o lo -m owner --uid-owner 0:9999 -j RETURN");
                // Route remaining loopback traffic through the LAN chain for per-app control
                cmds.add("-A " + chainName + " -o lo -j " + chainName + "-wifi-lan");
            }*/

            for (final String itf : ITFS_WIFI) {
                cmds.add("#NOCHK# -A " + chainName + " -o " + itf + " -j " + chainName + "-wifi");
            }

            for (final String itf : ITFS_3G) {
                cmds.add("#NOCHK# -A " + chainName + " -o " + itf + " -j " + chainName + "-3g");
            }

            // special rules to allow tethering
            // note that this can only blacklist DNS/DHCP services, not all tethered traffic
            String[] users_dhcp = {"root", "nobody", "network_stack"};
            String[] users_dns = {"root", "nobody", "dns_tether"};
            String action = " -j " + (whitelist ? "RETURN" : chainName + "-reject");

            if (containsUidOrAny(ruleDataSet.wifiList, SPECIAL_UID_TETHER)) {
                // DHCP replies to client
                addRuleForUsers(cmds, users_dhcp, "-A " + chainName + "-wifi-tether", "-p udp --sport=67 --dport=68" + action);
                // DNS replies to client
                addRuleForUsers(cmds, users_dns, "-A " + chainName + "-wifi-tether", "-p udp --sport=53" + action);
                addRuleForUsers(cmds, users_dns, "-A " + chainName + "-wifi-tether", "-p tcp --sport=53" + action);

            }
            
            // USB tethering rules
            if (containsUidOrAny(ruleDataSet.wifiList, SPECIAL_UID_TETHER) || containsUidOrAny(ruleDataSet.tetherList, SPECIAL_UID_TETHER)) {
                // DHCP replies to USB tethered client
                addRuleForUsers(cmds, users_dhcp, "-A " + chainName + "-usb-tether", "-p udp --sport=67 --dport=68" + action);
                // DNS replies to USB tethered client  
                addRuleForUsers(cmds, users_dns, "-A " + chainName + "-usb-tether", "-p udp --sport=53" + action);
                addRuleForUsers(cmds, users_dns, "-A " + chainName + "-usb-tether", "-p tcp --sport=53" + action);
            }
            if (containsUidOrAny(ruleDataSet.tetherList, SPECIAL_UID_TETHER)) {
                // DHCP replies to client
                addRuleForUsers(cmds, users_dhcp, "-A " + chainName + "-tether", "-p udp --sport=67 --dport=68" + action);
                // DNS replies to client
                addRuleForUsers(cmds, users_dns, "-A " + chainName + "-tether", "-p udp --sport=53" + action);
                addRuleForUsers(cmds, users_dns, "-A " + chainName + "-tether", "-p tcp --sport=53" + action);
            }

            // DNS requests to upstream servers - support all connection types
            if (containsUidOrAny(ruleDataSet.dataList, SPECIAL_UID_TETHER)) {
                // Define all tethering chains that need DNS upstream access
                String[] tetherChains = {"-3g-tether", "-wifi-tether", "-usb-tether", "-tether"};
                
                for (String chain : tetherChains) {
                    addRuleForUsers(cmds, users_dns, "-A " + chainName + chain, "-p udp --dport=53" + action);
                    addRuleForUsers(cmds, users_dns, "-A " + chainName + chain, "-p tcp --dport=53" + action);
                }
            }

            // if tethered, try to match the above rules (if enabled).  no match -> fall through to the
            // normal 3G/wifi rules
            cmds.add("-A " + chainName + "-wifi-tether -j " + chainName + "-wifi-fork");
            cmds.add("-A " + chainName + "-3g-tether -j " + chainName + "-3g-fork");

            // NOTE: we still need to open a hole to let WAN-only UIDs talk to a DNS server
            // on the LAN - use specific DNS servers instead of opening to all LAN hosts
            if (whitelist) {
                // Add rules for specific DNS servers instead of all LAN hosts
                addDnsServerRules(cmds, cfg, chainName + "-wifi-lan", ipv6);
                
                // Fallback: if no specific DNS servers found, use the old broad rule
                if (cfg.dnsServersV4.isEmpty() && cfg.dnsServersV6.isEmpty()) {
                    cmds.add("-A " + chainName + "-wifi-lan -p udp --dport 53 -j RETURN");
                    cmds.add("-A " + chainName + "-wifi-lan -p tcp --dport 53 -j RETURN");
                }

                //bug fix allow dns to be open on Pie for all connection type
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cmds.add("-A " + chainName + "-wifi-wan" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-3g-home" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-3g-roam" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-vpn" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-tether" + " -p udp --dport 53" + " -j RETURN");

                    cmds.add("-A " + chainName + "-wifi-wan" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-3g-home" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-3g-roam" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-vpn" + " -p tcp --dport 53" + " -j RETURN");
                    cmds.add("-A " + chainName + "-tether" + " -p tcp --dport 53" + " -j RETURN");
                }
            }
            // now add the per-uid rules for 3G home, 3G roam, wifi WAN, wifi LAN, VPN
            // in whitelist mode the last rule in the list routes everything else to afwall-reject
            addRulesForUidlist(cmds, ruleDataSet.dataList, chainName + "-3g-home", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.roamList, chainName + "-3g-roam", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.wifiList, chainName + "-wifi-wan", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.lanList, chainName + "-wifi-lan", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.vpnList, chainName + "-vpn", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.tetherList, chainName + "-tether", whitelist);
            if (G.enableTor()) {
                addTorRules(cmds, ruleDataSet.torList, whitelist, ipv6, chainName);
            }

            cmds.add("-P OUTPUT ACCEPT");
        } catch (Exception e) {
            Log.e(e.getClass().getName(), e.getMessage(), e);
        }

        iptablesCommands(cmds, out, ipv6);
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
        if(G.ip_path().equals("system")) {
            // Always use wait flag with system iptables to prevent lock contention
            waitTime = " -w 5";
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

    /**
     * Get thread-safe chain name, handling multi-user scenarios safely
     */
    private static String getThreadSafeChainName() {
        synchronized (CHAIN_NAME_LOCK) {
            if (G.isMultiUser() && G.getMultiUserId() > 0) {
                return "afwall" + G.getMultiUserId();
            }
            return "afwall";
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

    private static void completeRootCommandFailure(Context ctx, RootCommand callback, String command, Throwable throwable) {
        String message = command + " failed";
        if (throwable != null && throwable.getMessage() != null) {
            message += ": " + throwable.getMessage();
        }
        if (throwable instanceof Exception) {
            Log.e(TAG, message, (Exception) throwable);
        } else {
            Log.e(TAG, message);
        }
        ApplicationErrorLog.add(ctx, message);
        if (callback == null || callback.done) {
            return;
        }
        callback.lastCommand = command;
        if (throwable != null && throwable.getMessage() != null) {
            callback.lastCommandResult = new StringBuilder(throwable.getMessage());
        }
        callback.exitCode = 1;
        callback.done = true;
        if (ctx != null && callback.failureToast != RootShellService.NO_TOAST) {
            sendToastBroadcast(ctx.getApplicationContext(), ctx.getString(callback.failureToast));
        }
        if (callback.cb != null) {
            callback.cb.cbFunc(callback);
        }
    }

    private static RootCommand wrapApplyCompletionCallback(RootCommand callback) {
        final RootCommand completionCallback = callback == null ? new RootCommand() : callback;
        final RootCommand.Callback originalCallback = completionCallback.cb;
        completionCallback.setCallback(new RootCommand.Callback() {
            @Override
            public void cbFunc(RootCommand state) {
                try {
                    if (originalCallback != null) {
                        originalCallback.cbFunc(state);
                    }
                } finally {
                    synchronized (GLOBAL_STATUS_LOCK) {
                        globalStatus = false;
                        setRulesUpToDate(state.exitCode == 0);
                    }
                }
            }
        });
        return completionCallback;
    }

    private static RootCommand newIntermediateApplyCommand(RootCommand finalCallback) {
        return new RootCommand()
                .setFailureToast(finalCallback.failureToast)
                .setReopenShell(finalCallback.reopenShell);
    }

    public static void applySavedIptablesRules(Context ctx, boolean showErrors, RootCommand callback) {
        synchronized (GLOBAL_STATUS_LOCK) {
            if(!globalStatus) {
                globalStatus = true;
                final RootCommand completionCallback = wrapApplyCompletionCallback(callback);
                
                try {
                    Log.i(TAG, "Starting full firewall rules apply");
                    RuleDataSet dataSet = getDataSet();
                    List<String> ipv4cmds = new ArrayList<>();
                    List<String> ipv6cmds = new ArrayList<>();
                    
                    // Create thread-safe chain name for this execution
                    final String chainName = getThreadSafeChainName();
                    
                    // Apply IPv4 rules first. When IPv6 is enabled, wait for IPv4
                    // completion before starting IPv6 so the apply dialog and final
                    // callback represent the entire ruleset, not only IPv4.
                    try {
                        Log.i(TAG, "Applying IPv4 rules");
                        applyIptablesRulesImpl(ctx, dataSet, showErrors, ipv4cmds, false, chainName);
                        if (G.enableIPv6()) {
                            final List<String> finalIpv6cmds = ipv6cmds;
                            RootCommand ipv4Callback = newIntermediateApplyCommand(completionCallback)
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode != 0) {
                                                completionCallback.cb.cbFunc(state);
                                                return;
                                            }
                                            try {
                                                Log.i(TAG, "Applying IPv6 rules");
                                                applyIptablesRulesImpl(ctx, dataSet, showErrors, finalIpv6cmds, true, chainName);
                                                if (applySavedIp6tablesRules(ctx, finalIpv6cmds, completionCallback)) {
                                                    Log.i(TAG, "Submitted IPv6 rule commands");
                                                } else {
                                                    completeRootCommandFailure(ctx, completionCallback, "applySavedIp6tablesRules", null);
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error applying IPv6 rules", e);
                                                completeRootCommandFailure(ctx, completionCallback, "applySavedIp6tablesRules", e);
                                            }
                                        }
                                    });
                            if (applySavedIp4tablesRules(ctx, ipv4cmds, ipv4Callback)) {
                                Log.i(TAG, "Submitted IPv4 rule commands");
                            } else {
                                completeRootCommandFailure(ctx, completionCallback, "applySavedIp4tablesRules", null);
                            }
                        } else {
                            if (applySavedIp4tablesRules(ctx, ipv4cmds, completionCallback)) {
                                Log.i(TAG, "Submitted IPv4 rule commands");
                            } else {
                                completeRootCommandFailure(ctx, completionCallback, "applySavedIp4tablesRules", null);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error applying IPv4 rules", e);
                        throw new RuntimeException(e);
                    }
                    
                    Log.i(TAG, "Submitted firewall rule command sequence");

                } catch (Exception e) {
                    completeRootCommandFailure(ctx, completionCallback, "applySavedIptablesRules", e);
                }
            } else {
                Log.w(TAG, "Full apply ignored because another apply is already running");
                completeRootCommandFailure(ctx, callback, "applySavedIptablesRules", null);
            }
        }
    }

    public static boolean applyChangedUidRules(Context ctx, List<PackageInfoData> changedApps, boolean showErrors, RootCommand callback) {
        if (ctx == null || changedApps == null || changedApps.isEmpty()) {
            Log.w(TAG, "Changed UID apply skipped because context or changed app list is missing");
            return false;
        }
        synchronized (GLOBAL_STATUS_LOCK) {
            if (globalStatus) {
                Log.w(TAG, "Changed UID apply ignored because another apply is already running");
                return false;
            }
            globalStatus = true;
            try {
                Log.i(TAG, "Starting changed UID apply for " + changedApps.size() + " app(s)");
                assertBinaries(ctx, showErrors);
                final String chainName = getThreadSafeChainName();
                final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
                List<String> out = new ArrayList<>();
                List<String> cmds = new ArrayList<>();

                for (PackageInfoData app : changedApps) {
                    if (app != null && app.uid > 0) {
                        addChangedUidRules(cmds, app, false, chainName, whitelist);
                    }
                }
                iptablesCommands(cmds, out, false);

                if (G.enableIPv6()) {
                    cmds = new ArrayList<>();
                    for (PackageInfoData app : changedApps) {
                        if (app != null && app.uid > 0) {
                            addChangedUidRules(cmds, app, true, chainName, whitelist);
                        }
                    }
                    iptablesCommands(cmds, out, true);
                }

                if (callback == null) {
                    callback = new RootCommand();
                }
                setRulesUpToDate(false);
                callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
                setRulesUpToDate(true);
                Log.i(TAG, "Submitted changed UID rule commands for " + changedApps.size() + " app(s)");
                return true;
            } catch (Exception e) {
                String message = "Error applying changed UID rules: " + e.getMessage();
                Log.e(TAG, message, e);
                ApplicationErrorLog.add(ctx, message);
                return false;
            } finally {
                globalStatus = false;
            }
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


        List<Integer> wifiList = getListFromPref(savedPkg_wifi_uid);
        List<Integer> dataList = getListFromPref(savedPkg_3g_uid);
        
        
        // Warn if no applications are configured - this means no blocking will occur
        if (wifiList.isEmpty() && dataList.isEmpty()) {
            Log.w(TAG, "WARNING: No applications configured for firewall rules - firewall will not block any traffic!");
            Log.w(TAG, "Please configure applications in AFWall+ main screen and apply rules.");
        }
        
        return new RuleDataSet(wifiList,
                dataList,
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
            Log.w(TAG, "IPv4 rule apply skipped because context is null");
            return false;
        }
        try {
            Log.i(TAG, "Submitting " + cmds.size() + " IPv4 rule command(s)");
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds);
            return true;
        } catch (Exception e) {
            String message = "Exception while applying IPv4 rules: " + e.getMessage();
            Log.e(TAG, message, e);
            ApplicationErrorLog.add(ctx, message);
            // Only apply default chains if it's a critical failure
            // Avoid overriding user chain preferences unnecessarily
            if (e.getMessage() != null && !e.getMessage().contains("Chain") && !e.getMessage().contains("policy")) {
                Log.w(TAG, "Applying default chains due to rule application failure");
                applyDefaultChains(ctx, callback);
            } else {
                Log.w(TAG, "Skipping default chains application to preserve user chain preferences");
            }
            return false;
        }
    }


    public static boolean applySavedIp6tablesRules(Context ctx, List<String> cmds, RootCommand callback) {
        if (ctx == null) {
            Log.w(TAG, "IPv6 rule apply skipped because context is null");
            return false;
        }
        try {
            Log.i(TAG, "Submitting " + cmds.size() + " IPv6 rule command(s)");
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds,true);
            return true;
        } catch (Exception e) {
            String message = "Exception while applying IPv6 rules: " + e.getMessage();
            Log.e(TAG, message, e);
            ApplicationErrorLog.add(ctx, message);
            // Only apply default chains if it's a critical failure
            // Avoid overriding user chain preferences unnecessarily
            if (e.getMessage() != null && !e.getMessage().contains("Chain") && !e.getMessage().contains("policy")) {
                Log.w(TAG, "Applying default chains due to rule application failure");
                applyDefaultChains(ctx, callback);
            } else {
                Log.w(TAG, "Skipping default chains application to preserve user chain preferences");
            }
            return false;
        }
    }


    public static boolean fastApply(Context ctx, RootCommand callback) {
        try {
                if (!getRulesUpToDate()) {
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
            String message = "Exception in fastApply: " + e.getMessage();
            Log.e(TAG, message, e);
            ApplicationErrorLog.add(ctx, message);
            // Only apply default chains if it's a critical failure
            // Avoid overriding user chain preferences unnecessarily
            if (e.getMessage() != null && !e.getMessage().contains("Chain") && !e.getMessage().contains("policy")) {
                Log.w(TAG, "Applying default chains due to fastApply failure");
                applyDefaultChains(ctx, callback);
            } else {
                Log.w(TAG, "Skipping default chains application in fastApply to preserve user chain preferences");
            }
        }
        setRulesUpToDate(true);
        return true;
    }

    /**
     * Save current rules using the preferences storage.
     *
     * @param ctx application context (mandatory)
     */
    public static RuleDataSet generateRules(Context ctx, List<PackageInfoData> apps, boolean store) {

        setRulesUpToDate(false);
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
    public static void purgeIptables(Context ctx, boolean showErrors, RootCommand callback) {
        String chainName = getThreadSafeChainName();

        List<String> cmds = new ArrayList<>();
        List<String> cmdsv4 = new ArrayList<>();
        List<String> out = new ArrayList<>();

        for (String s : staticChains) {
            cmds.add("-F " + chainName + s);
        }
        for (String s : dynChains) {
            cmds.add("-F " + chainName + s);
        }
        if (G.enableTor()) {
            for (String s : natChains) {
                cmdsv4.add("-t nat -F " + chainName + s);
            }
            cmdsv4.add("#NOCHK# -t nat -D OUTPUT -j " + chainName);
        } else {
            cmdsv4.add("#NOCHK# -D OUTPUT -j " + chainName);
        }

        //make sure reset the OUTPUT chain to accept state.
        cmds.add("-P OUTPUT ACCEPT");

        //Delete only when the afwall chain exist !
        //cmds.add("-D OUTPUT -j " + chainName);

        if (G.enableInbound()) {
            cmds.add("-D INPUT -j " + chainName + "-input");
        }

        addCustomRules(Api.PREF_CUSTOMSCRIPT2, cmds);
        
        // Execute the purge commands and call the callback
        Log.i(TAG, "Executing purge commands for IPv4");
        cmds.addAll(cmdsv4);
        iptablesCommands(cmds, out, false);
        
        if (G.enableIPv6()) {
            Log.i(TAG, "Executing purge commands for IPv6");
            List<String> cmdsv6 = new ArrayList<>();
            for (String s : staticChains) {
                cmdsv6.add("-F " + chainName + s);
            }
            for (String s : dynChains) {
                cmdsv6.add("-F " + chainName + s);
            }
            cmdsv6.add("#NOCHK# -D OUTPUT -j " + chainName);
            cmdsv6.add("-P OUTPUT ACCEPT");
            if (G.enableInbound()) {
                cmdsv6.add("-D INPUT -j " + chainName + "-input");
            }
            iptablesCommands(cmdsv6, out, true);
        }
        
        Log.i(TAG, "Purge completed, calling callback");
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }
    
    /**
     * Add DNS-specific iptables rules for identified DNS servers instead of broad LAN access
     */
    private static void addDnsServerRules(List<String> cmds, InterfaceDetails cfg, String chain, boolean ipv6) {
        String protocol = ipv6 ? "ip6tables" : "iptables";
        java.util.List<String> dnsServers = ipv6 ? cfg.dnsServersV6 : cfg.dnsServersV4;
        
        for (String dnsServer : dnsServers) {
            if (dnsServer != null && !dnsServer.isEmpty()) {
                // Add rules for both UDP and TCP DNS traffic to specific servers
                cmds.add("-A " + chain + " -d " + dnsServer + " -p udp --dport 53 -j RETURN");
                cmds.add("-A " + chain + " -d " + dnsServer + " -p tcp --dport 53 -j RETURN");
            }
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
        if (useIPV6) {
            iptablesCommands(cmds, out, true);
        } else {
            iptablesCommands(cmds, out, false);
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
        String chainName = getThreadSafeChainName();
        List<String> cmds = new ArrayList<String>();
        cmds.add("#NOCHK# -N " + chainName + "-reject");
        cmds.add("-F " + chainName + "-reject");
        addRejectRules(cmds, chainName);
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
        // Try system ifconfig first, then busybox for all versions
        callback.run(ctx, "ifconfig -a || " + getBusyBoxPath(ctx, true) + " ifconfig -a");
    }

    public static void runNetworkInterface(Context ctx, RootCommand callback) {
        // Try Android API method first for all versions
        try {
            StringBuilder result = new StringBuilder();
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                result.append(networkInterface.getName()).append("\n");
            }
            if (result.length() > 0) {
                // Create a mock RootCommand with API results
                RootCommand apiResult = new RootCommand();
                apiResult.res = result;
                apiResult.exitCode = 0;
                apiResult.done = true;
                if (callback.cb != null) {
                    callback.cb.cbFunc(apiResult);
                }
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Android API network interface detection failed: " + e.getMessage());
        }
        
        // Fallback to shell commands with multiple options
        String cmd = "ls /sys/class/net 2>/dev/null || " + 
                    getBusyBoxPath(ctx, true) + " ls /sys/class/net 2>/dev/null || " +
                    "ip link show 2>/dev/null";
        callback.run(ctx, cmd);
    }


    public static void fixFolderPermissionsAsync(Context mContext) {
        AsyncTask.execute(() -> {
            try {
                mContext.getFilesDir().setExecutable(true, true);
                mContext.getFilesDir().setReadable(true, true);
                File sharedPrefsFolder = new File(mContext.getFilesDir().getAbsolutePath()
                        + "/../shared_prefs");
                sharedPrefsFolder.setExecutable(true, true);
                sharedPrefsFolder.setReadable(true, true);
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

        Set<Integer> selected_wifi;
        Set<Integer> selected_3g;
        Set<Integer> selected_roam = new HashSet<>();
        Set<Integer> selected_vpn = new HashSet<>();
        Set<Integer> selected_tether = new HashSet<>();
        Set<Integer> selected_lan = new HashSet<>();
        Set<Integer> selected_tor = new HashSet<>();


        selected_wifi = new HashSet<>(getListFromPref(savedPkg_wifi_uid));
        selected_3g = new HashSet<>(getListFromPref(savedPkg_3g_uid));

        if (G.enableRoam()) {
            selected_roam = new HashSet<>(getListFromPref(savedPkg_roam_uid));
        }
        if (G.enableVPN()) {
            selected_vpn = new HashSet<>(getListFromPref(savedPkg_vpn_uid));
        }
        if (G.enableTether()) {
            selected_tether = new HashSet<>(getListFromPref(savedPkg_tether_uid));
        }
        if (G.enableLAN()) {
            selected_lan = new HashSet<>(getListFromPref(savedPkg_lan_uid));
        }
        if (G.enableTor()) {
            selected_tor = new HashSet<>(getListFromPref(savedPkg_tor_uid));
        }
        //revert back to old approach

        //always use the defaul preferences to store cache value - reduces the application usage size
        SharedPreferences cachePrefs = ctx.getSharedPreferences(DEFAULT_PREFS_NAME, Context.MODE_PRIVATE);

        int count = 0;
        try {
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
            //use pm list packages -f -U --user 10
            int pkgManagerFlags = PackageManager.GET_META_DATA;
            // it's useless to iterate over uninstalled packages if we don't support multi-profile apps
            if (G.supportDual()) {
                pkgManagerFlags |= PackageManager.GET_UNINSTALLED_PACKAGES;
            }
            PackageManager pkgmanager = ctx.getPackageManager();
            if (appList != null) {
                appList.doStageProgress(1);
            }
            List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(pkgManagerFlags);

            // On Android 11+ (API 30+), PackageManager may not return all apps without
            // QUERY_ALL_PACKAGES. Supplement using root shell "pm list packages -U" to discover
            // any packages not visible to PackageManager, including headless system services
            // like Captive Portal Login, OsuLogin, WebView, Remote Provisioner, etc.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (appList != null) {
                    appList.doStageProgress(2);
                }
                Set<String> visiblePackages = new HashSet<>();
                for (ApplicationInfo ai : installed) {
                    visiblePackages.add(ai.packageName);
                }

                try {
                    Shell.Result result = Shell.cmd("pm list packages -U").exec();
                    List<String> out = result.getOut();
                    for (String line : out) {
                        // Format: "package:<pkgname> uid:<uid>"
                        if (line.startsWith("package:")) {
                            String rest = line.substring(8).trim();
                            String pkg;
                            int uid = -1;
                            int uidIdx = rest.indexOf(" uid:");
                            if (uidIdx > 0) {
                                pkg = rest.substring(0, uidIdx);
                                try {
                                    uid = Integer.parseInt(rest.substring(uidIdx + 5));
                                } catch (NumberFormatException ignored) {}
                            } else {
                                pkg = rest;
                            }
                            if (!visiblePackages.contains(pkg)) {
                                try {
                                    ApplicationInfo ai = pkgmanager.getApplicationInfo(pkg, pkgManagerFlags);
                                    installed.add(ai);
                                } catch (NameNotFoundException e) {
                                    // PackageManager can't see this app (no QUERY_ALL_PACKAGES).
                                    // Check INTERNET permission via shell before adding.
                                    if (uid >= 0 && hasInternetPermissionViaShell(pkg)) {
                                        ApplicationInfo ai = new ApplicationInfo();
                                        ai.packageName = pkg;
                                        ai.uid = uid;
                                        ai.flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
                                        installed.add(ai);
                                    }
                                }
                                visiblePackages.add(pkg);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Shell-based package discovery failed: " + e.getMessage());
                }
            }
            SparseArray<PackageInfoData> syncMap = new SparseArray<>();
            Editor edit = cachePrefs.edit();
            boolean changed = false;
            String name;
            String cachekey;
            String cacheLabel = "cache.label.";
            PackageInfoData app;
            ApplicationInfo apinfo;

            Date install = new Date();
            install.setTime(System.currentTimeMillis() - (180000));

            SparseArray<PackageInfoData> multiUserAppsMap = new SparseArray<>();
            HashMap<Integer, String> packagesForUser = new HashMap<>();
            if(G.supportDual()) {
                if (appList != null) {
                    appList.doStageProgress(3);
                }
                packagesForUser  = getPackagesForUser(listOfUids);
            }

            if (appList != null) {
                appList.doStageProgress(4);
                appList.doMaxProgress(installed.size());
            }

            for (int i = 0; i < installed.size(); i++) {
                //for (ApplicationInfo apinfo : installed) {
                count = count + 1;
                apinfo = installed.get(i);

                if (appList != null) {
                    appList.doProgress(count);
                }

                boolean firstseen = false;
                app = syncMap.get(apinfo.uid);
                // filter applications which are not allowed to access the Internet
                if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName) && !showAllApps()) {
                    // For shell-discovered apps, checkPermission may return DENIED since
                    // PackageManager can't see them — they were already filtered by
                    // hasInternetPermissionViaShell() during discovery, so let them through.
                    if (apinfo.sourceDir != null) {
                        continue;
                    }
                }
                // try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
                cachekey = cacheLabel + apinfo.packageName;
                name = cachePrefs.getString(cachekey, "");
                if (name.length() == 0 || isRecentlyInstalled(apinfo.packageName)) {
                    // get label and put on cache
                    try {
                        name = pkgmanager.getApplicationLabel(apinfo).toString();
                    } catch (Exception e) {
                        // For apps invisible to PackageManager, use package name as label
                        name = apinfo.packageName;
                    }
                    edit.putString(cachekey, name);
                    changed = true;
                    firstseen = true;
                }
                if (app == null) {
                    app = new PackageInfoData();
                    app.uid = apinfo.uid;

                    // Handle null sourceDir to prevent NullPointerException
                    if (apinfo.sourceDir != null) {
                        app.installTime = new File(apinfo.sourceDir).lastModified();
                    } else {
                        // Try to get install time from PackageInfo as fallback
                        try {
                            PackageInfo pkgInfo = pkgmanager.getPackageInfo(apinfo.packageName, 0);
                            app.installTime = pkgInfo.firstInstallTime;
                        } catch (PackageManager.NameNotFoundException e) {
                            // Shell-discovered apps invisible to PackageManager — use 0
                            app.installTime = 0;
                        }
                    }

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
                    if ((apinfo.flags & ApplicationInfo.FLAG_INSTALLED) != 0)
                        syncMap.put(apinfo.uid, app);
                } else {
                    app.names.add(name);
                }

                app.firstseen = firstseen;
                applySelectedStates(app, selected_wifi, selected_3g, selected_roam, selected_vpn,
                        selected_tether, selected_lan, selected_tor);
                if (G.supportDual()) {
                    checkPartOfMultiUser(apinfo, name, listOfUids, packagesForUser, multiUserAppsMap);
                }
            }

            if (G.supportDual()) {
                //run through multi user map
                for (int i = 0; i < multiUserAppsMap.size(); i++) {
                    app = multiUserAppsMap.valueAt(i);
                    applySelectedStates(app, selected_wifi, selected_3g, selected_roam, selected_vpn,
                            selected_tether, selected_lan, selected_tor);
                    syncMap.put(app.uid, app);
                }
            }

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
                    applySelectedStates(app, selected_wifi, selected_3g, selected_roam, selected_vpn,
                            selected_tether, selected_lan, selected_tor);
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

    private static void checkPartOfMultiUser(ApplicationInfo apinfo, String name, List<Integer> uid1, HashMap<Integer,String> pkgs, SparseArray<PackageInfoData> syncMap) {
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
    }

    private static boolean packagesExistForUserUid(HashMap<Integer,String> pkgs, int appUid) {
        if(pkgs.containsKey(appUid)){
            return true;
        }
        return false;
    }

    private static void applySelectedStates(PackageInfoData app,
                                            Set<Integer> selectedWifi,
                                            Set<Integer> selected3g,
                                            Set<Integer> selectedRoam,
                                            Set<Integer> selectedVpn,
                                            Set<Integer> selectedTether,
                                            Set<Integer> selectedLan,
                                            Set<Integer> selectedTor) {
        int uid = app.uid;
        app.selected_wifi = app.selected_wifi || selectedWifi.contains(uid);
        app.selected_3g = app.selected_3g || selected3g.contains(uid);
        app.selected_roam = app.selected_roam || (G.enableRoam() && selectedRoam.contains(uid));
        app.selected_vpn = app.selected_vpn || (G.enableVPN() && selectedVpn.contains(uid));
        app.selected_tether = app.selected_tether || (G.enableTether() && selectedTether.contains(uid));
        app.selected_lan = app.selected_lan || (G.enableLAN() && selectedLan.contains(uid));
        app.selected_tor = app.selected_tor || (G.enableTor() && selectedTor.contains(uid));
    }

    public static HashMap<Integer, String> getPackagesForUser(List<Integer> userProfile) {
        HashMap<Integer,String> listApps = new HashMap<>();
        for(Integer integer: userProfile) {
            try {
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
            } catch (java.util.concurrent.RejectedExecutionException e) {
                Log.w(TAG, "Package listing rejected for user " + integer + ": " + e.getMessage());
                break; // Stop processing other users if execution rejected
            } catch (Exception e) {
                Log.e(TAG, "Failed to list packages for user " + integer + ": " + e.getMessage());
                // Continue with next user on other errors
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
            RunCommand runCommand = new RunCommand();
            returnCode = runCommand.execute(script, res, ctx).get();
        } catch (RejectedExecutionException r) {
            Log.w(TAG, "Shell execution rejected, likely due to app shutdown: " + r.getLocalizedMessage());
            returnCode = -1;
        } catch (InterruptedException e) {
            Log.w(TAG, "Shell execution was interrupted: " + e.getLocalizedMessage());
            Thread.currentThread().interrupt(); // Restore interrupted status
            returnCode = -1;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.io.InterruptedIOException) {
                Log.w(TAG, "Shell execution interrupted (IO): " + cause.getMessage());
            } else if (cause instanceof java.util.concurrent.RejectedExecutionException) {
                Log.w(TAG, "Shell execution rejected in wrapped exception: " + cause.getMessage());
            } else {
                Log.e(TAG, "Shell execution failed with ExecutionException: " + e.getLocalizedMessage());
            }
            returnCode = -1;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during shell execution: " + e.getLocalizedMessage());
            returnCode = -1;
        }

        return returnCode;
    }

    private static boolean installBinary(Context ctx, int resId, String filename) {
        try {
            File binDir = ctx.getDir("bin", 0);
            File f = new File(binDir, filename);
            
            Log.d(TAG, "Installing binary: " + filename + " to " + f.getAbsolutePath());
            
            if (f.exists()) {
                Log.d(TAG, "Removing existing binary: " + filename);
                if (!f.delete()) {
                    Log.w(TAG, "Failed to delete existing binary: " + filename);
                }
            }
            
            copyRawFile(ctx, resId, f, "0755");
            
            // Verify the binary was installed correctly
            if (!f.exists()) {
                Log.e(TAG, "Binary installation failed - file does not exist: " + filename);
                return false;
            }
            
            if (!f.canExecute()) {
                Log.w(TAG, "Binary installed but not executable: " + filename);
                // Try to fix permissions manually
                try {
                    f.setExecutable(true, false);
                    Log.d(TAG, "Fixed permissions for: " + filename);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fix permissions for: " + filename + " - " + e.getMessage());
                }
            }
            
            Log.d(TAG, "Successfully installed binary: " + filename + 
                  " (size: " + f.length() + " bytes, executable: " + f.canExecute() + ")");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "installBinary failed for " + filename + ": " + e.getClass().getSimpleName() + 
                  " - " + e.getLocalizedMessage(), e);
            return false;
        }
    }

    /**
     * Install binary if the resource exists, using reflection to check for resource availability
     * @param ctx Context
     * @param resourceName Name of the resource (e.g., "busybox_arm64")  
     * @param filename Target filename
     * @return true if installed successfully or resource doesn't exist, false on installation error
     */
    private static boolean installBinaryIfExists(Context ctx, String resourceName, String filename) {
        try {
            // Use reflection to check if the resource exists
            Class<?> rawClass = R.raw.class;
            java.lang.reflect.Field field = rawClass.getDeclaredField(resourceName);
            int resId = field.getInt(null);
            
            // Resource exists, try to install it
            return installBinary(ctx, resId, filename);
        } catch (NoSuchFieldException e) {
            // Resource doesn't exist - this is expected when binaries are not yet added
            Log.d(TAG, "Resource " + resourceName + " not found - this is expected if binary is not yet available");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking/installing binary " + resourceName + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean installBinariesX86(Context ctx) {
        if (!installBinary(ctx, R.raw.busybox_x86, "busybox")) return false;
        if (!installBinary(ctx, R.raw.iptables_x86, "iptables")) return false;
        if (!installBinary(ctx, R.raw.ip6tables_x86, "ip6tables")) return false;
        if (!installBinary(ctx, R.raw.nflog_x86, "nflog")) return false;
        
        
        return true;
    }


    private static boolean installBinariesArm64(Context ctx) {
        if (!installBinary(ctx, R.raw.busybox_arm64, "busybox")) return false;
        if (!installBinary(ctx, R.raw.iptables_arm64, "iptables")) return false;
        if (!installBinary(ctx, R.raw.ip6tables_arm64, "ip6tables")) return false;
        if (!installBinary(ctx, R.raw.nflog_arm64, "nflog")) return false;
        

        return true;
    }

    private static boolean installBinariesArm(Context ctx) {
        if (!installBinary(ctx, R.raw.busybox_arm, "busybox")) return false;
        if (!installBinary(ctx, R.raw.iptables_arm, "iptables")) return false;
        if (!installBinary(ctx, R.raw.ip6tables_arm, "ip6tables")) return false;
        if (!installBinary(ctx, R.raw.nflog_arm, "nflog")) return false;
        
        
        return true;
    }

    private static boolean installBinariesForAbi(Context ctx, String abi) {
        if (abi.startsWith("x86")) {
            return installBinariesX86(ctx);
        } else if (abi.startsWith("arm64")) {
            return installBinariesArm64(ctx);
        } else {
            return installBinariesArm(ctx);
        }
    }

    private static int getPackageVersion(Context ctx) {
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

    // Static lock object for synchronizing binary installation
    private static final Object BINARY_INSTALL_LOCK = new Object();
    
    /**
     * Asserts that the binary files are installed in the cache directory.
     *
     * @param ctx        context
     * @param showErrors indicates if errors should be alerted
     * @return false if the binary files could not be installed
     */
    public static boolean assertBinaries(Context ctx, boolean showErrors) {
        synchronized (BINARY_INSTALL_LOCK) {
        Log.d(TAG, "assertBinaries() called - Entry point");

        int currentVer = getPackageVersion(ctx);
        boolean wasAlreadyInstalled = (G.appVersion() == currentVer);
        Log.d(TAG, "assertBinaries() - currentVer=" + currentVer + ", storedVer=" + G.appVersion() + ", wasAlreadyInstalled=" + wasAlreadyInstalled);

        if (wasAlreadyInstalled) {
            // The version hasn't changed: Check if binaries are still functional
            Log.d(TAG, "assertBinaries() - Verifying existing binaries...");
            if (verifyBinaries(ctx)) {
                Log.d(TAG, "assertBinaries() - Verification passed, returning true (no reinstall needed)");
                return true;
            } else {
                Log.w(TAG, "Binaries verification failed, forcing reinstallation");
            }
        }

        String abi = getAbi();

        Log.d(TAG, "Installing binaries for " + abi + " (currentVer=" + currentVer + 
                  ", storedVer=" + G.appVersion() + ", wasAlreadyInstalled=" + wasAlreadyInstalled + ")...");

        if (!installBinariesForAbi(ctx, abi))
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
        
        // Only show toast for actual new installations (not verification failures)
        if (!wasAlreadyInstalled) {
            Log.d(TAG, "New installation completed - showing toast");
            toast(ctx, ctx.getString(R.string.toast_bin_installed), Toast.LENGTH_SHORT);
        } else {
            Log.d(TAG, "Binaries reinstalled (wasAlreadyInstalled=true) - no toast shown");
        }

        G.appVersion(currentVer); // This indicates that the installation of the binaries for this version was successful.

        return true;
        } // End synchronized block
    }

    /**
     * Force reinstallation of binaries regardless of version
     *
     * @param ctx Context
     * @param showErrors indicates if errors should be alerted
     * @return true if installation successful
     */
    public static boolean forceReinstallBinaries(Context ctx, boolean showErrors) {
        Log.i(TAG, "Forcing binary reinstallation...");
        
        // Clear the version to force reinstallation
        G.appVersion(-1);
        
        return assertBinaries(ctx, showErrors);
    }

    /**
     * Verify that installed binaries are functional
     *
     * @param ctx Context
     * @return true if binaries are functional, false if they need reinstallation
     */
    private static boolean verifyBinaries(Context ctx) {
        Log.d(TAG, "verifyBinaries() called - Starting verification");
        String dir = ctx.getDir("bin", 0).getAbsolutePath();
        Log.d(TAG, "verifyBinaries() - Binary directory: " + dir);
        
        // Check if busybox exists and is executable
        File busybox = new File(dir, "busybox");
        boolean exists = busybox.exists();
        boolean canExecute = busybox.canExecute();
        boolean canRead = busybox.canRead();
        long size = busybox.length();
        Log.d(TAG, "verifyBinaries() - Checking busybox: exists=" + exists + ", canExecute=" + canExecute + ", canRead=" + canRead + ", size=" + size + " bytes");
        if (!exists || !canExecute) {
            Log.w(TAG, "Busybox binary missing or not executable");
            return false;
        }
        
        // Test busybox functionality by running a simple command
        // Note: On modern Android, binaries in app private directories may not be executable
        // from the app context, but they will work when executed with root privileges
        try {
            Log.d(TAG, "verifyBinaries() - Testing busybox functionality with 'echo test'");
            ProcessBuilder pb = new ProcessBuilder(busybox.getAbsolutePath(), "echo", "test");
            pb.environment().clear();
            Process process = pb.start();
            int exitCode = process.waitFor();
            Log.d(TAG, "verifyBinaries() - Busybox test exitCode: " + exitCode);
            
            if (exitCode != 0) {
                Log.w(TAG, "Busybox test command failed with exit code: " + exitCode);
                return false;
            }
            
            // Read and verify output
            java.util.Scanner scanner = new java.util.Scanner(process.getInputStream());
            if (scanner.hasNextLine()) {
                String output = scanner.nextLine().trim();
                Log.d(TAG, "verifyBinaries() - Busybox test output: '" + output + "'");
                scanner.close();
                if (!"test".equals(output)) {
                    Log.w(TAG, "Busybox test output unexpected: " + output);
                    return false;
                }
            } else {
                scanner.close();
                Log.w(TAG, "Busybox test produced no output");
                return false;
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Permission denied") || errorMsg.contains("error=13"))) {
                Log.w(TAG, "Busybox execution test failed due to Android security restrictions (expected behavior)");
                Log.w(TAG, "Binary will be available for root execution. Skipping direct execution test.");
                // Don't fail verification for permission denied - the binary will work with root
                // Just log the issue and continue with other checks
            } else {
                Log.w(TAG, "Busybox verification failed: " + errorMsg);
                return false;
            }
        }
        
        // Check other critical binaries exist
        Log.d(TAG, "verifyBinaries() - Checking other required binaries");
        String[] requiredBinaries = {"iptables", "ip6tables"};
        for (String binary : requiredBinaries) {
            File binaryFile = new File(dir, binary);
            Log.d(TAG, "verifyBinaries() - Checking " + binary + ": exists=" + binaryFile.exists() + ", canExecute=" + binaryFile.canExecute());
            if (!binaryFile.exists() || !binaryFile.canExecute()) {
                Log.w(TAG, "Required binary missing or not executable: " + binary);
                return false;
            }
        }
        
        Log.d(TAG, "Binary verification successful - All checks passed");
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
        setRulesUpToDate(false);

        Editor edit = prefs.edit();
        edit.putBoolean(PREF_ENABLED, enabled);
        if (!edit.commit()) {
            if (showErrors) toast(ctx, ctx.getString(R.string.error_write_pref));
            return;
        }

        Intent myService = new Intent(ctx, FirewallService.class);
        ctx.stopService(myService);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(myService);
        } else {
            ctx.startService(myService);
        }

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
            
            // Android 16+ specific notification channel configurations
            if (Build.VERSION.SDK_INT >= 36) {
                notificationChannel.setAllowBubbles(false);
            }
            
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
            
            // Android 16+ specific notification channel configurations
            if (Build.VERSION.SDK_INT >= 36) {
                notificationChannel.setAllowBubbles(false);
            }
            
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


    public static Drawable getApplicationIcon(Context context, int appUid) {
        if (uidToApplicationInfoMap == null) {
            PackageManager packageManager = context.getPackageManager();
            List<ApplicationInfo> installedApplications = new ArrayList<>(packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES));

            // On Android 11+, supplement with shell-based discovery
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Set<String> visiblePackages = new HashSet<>();
                for (ApplicationInfo ai : installedApplications) {
                    visiblePackages.add(ai.packageName);
                }
                try {
                    Shell.Result result = Shell.cmd("pm list packages").exec();
                    List<String> out = result.getOut();
                    for (String line : out) {
                        if (line.startsWith("package:")) {
                            String pkg = line.substring(8).trim();
                            if (!visiblePackages.contains(pkg)) {
                                try {
                                    ApplicationInfo ai = packageManager.getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
                                    installedApplications.add(ai);
                                } catch (NameNotFoundException ignored) {
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Shell-based icon lookup supplement failed: " + e.getMessage());
                }
            }

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
            if (applicationInfo.icon == 0) {
                return ThemeHelper.defaultAndroidIcon(context);
            }
            return applicationInfo.loadIcon(packageManager);        // The application icon.
        } else {
            return ThemeHelper.defaultAndroidIcon(context);         // The default icon.
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

    public static String getBackupFileName(boolean exportAll) {
        return "afwall-backup" + (exportAll ? "-all" : "") + "-"
                + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".json";
    }

    public static void exportRulesToFileWithPicker(final Context ctx) {
        showExportFileDialog(ctx, false);
    }

    public static void exportAllPreferencesToFileWithPicker(final Context ctx) {
        showExportFileDialog(ctx, true);
    }

    private static void showExportFileDialog(final Context ctx, final boolean exportAll) {
        try {
            File defaultPath;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                File extDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (extDir != null) {
                    extDir.mkdirs();
                    defaultPath = extDir;
                } else {
                    defaultPath = new File(ctx.getExternalFilesDir(null), "/");
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                defaultPath = new File(ctx.getExternalFilesDir(null), "/");
            } else {
                defaultPath = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/");
                defaultPath.mkdirs();
            }

            dev.ukanth.ufirewall.util.FileDialog fileDialog = new dev.ukanth.ufirewall.util.FileDialog((Activity) ctx, defaultPath, true);
            fileDialog.setSelectDirectoryOption(true);
            fileDialog.addDirectoryListener(directory -> {
                String fileName = getBackupFileName(exportAll);
                File fullPath = new File(directory, fileName);
                
                boolean success;
                if (exportAll) {
                    success = exportAllToFile(ctx, fullPath);
                } else {
                    success = exportRulesToFile(ctx, fullPath);
                }
                
                if (success) {
                    Api.toast(ctx, ctx.getString(R.string.export_rules_success) + " " + fullPath.getAbsolutePath());
                } else {
                    Api.toast(ctx, ctx.getString(R.string.export_rules_fail));
                }
            });
            fileDialog.showDialog();
        } catch (Exception e) {
            // Fallback to original method if file dialog fails
            if (exportAll) {
                exportAllPreferencesToFileConfirm(ctx);
            } else {
                exportRulesToFileConfirm(ctx);
            }
        }
    }

    public static boolean exportRulesToUri(Context ctx, Uri uri) {
        try {
            writeExportToUri(ctx, uri, buildRulesExportJson(ctx));
            Log.i(TAG, "Successfully exported rules to URI: " + uri);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error exporting rules to URI: " + uri, e);
            return false;
        }
    }

    public static boolean exportAllPreferencesToUri(Context ctx, Uri uri) {
        try {
            writeExportToUri(ctx, uri, buildAllPreferencesExportJson(ctx));
            Log.i(TAG, "Successfully exported all preferences to URI: " + uri);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error exporting all preferences to URI: " + uri, e);
            return false;
        }
    }

    private static boolean exportRulesToFile(Context ctx, File file) {
        try {
            writeExportToFile(file, buildRulesExportJson(ctx));
            Log.i(TAG, "Successfully exported rules to: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error exporting rules to file: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    private static boolean exportAllToFile(Context ctx, File file) {
        try {
            writeExportToFile(file, buildAllPreferencesExportJson(ctx));
            Log.i(TAG, "Successfully exported all preferences to: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error exporting all preferences to file: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    private static void writeExportToFile(File file, String exportJson) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fOut = new FileOutputStream(file);
             OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut, StandardCharsets.UTF_8)) {
            myOutWriter.write(exportJson);
            myOutWriter.flush();
        }
    }

    private static void writeExportToUri(Context ctx, Uri uri, String exportJson) throws IOException {
        OutputStream out = ctx.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) {
            throw new IOException("Unable to open export URI for writing: " + uri);
        }
        try (OutputStreamWriter myOutWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            myOutWriter.write(exportJson);
            myOutWriter.flush();
        }
    }

    private static String buildRulesExportJson(Context ctx) throws JSONException {
        JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
        JSONArray jArray = new JSONArray("[" + obj.toString() + "]");
        JSONObject exportObject = new JSONObject();
        exportObject.put("rules", jArray);
        String mode = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
        exportObject.put("mode", mode);
        return exportObject.toString();
    }

    private static String buildAllPreferencesExportJson(Context ctx) throws JSONException {
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
        if (G.pPrefs != null) {
            exportObject.put("profilePrefs", getAllAppPreferences(ctx, G.pPrefs));
            String mode = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
            exportObject.put("mode", mode);
        }
        return exportObject.toString();
    }

    private static void updateExportPackage(Map<String, JSONObject> exportMap, String packageName, boolean isChecked, int identifier) throws JSONException {
        if (!isChecked) {
            return;
        }
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
            if (!uid.isEmpty()) {
                String packageName = ctx.getPackageManager().getNameForUid(Integer.parseInt(uid));
                updateExportPackage(exportMap, packageName, /*is_checked=*/ true, identifier);
            }
        }
    }

    private static Map<String, JSONObject> getCurrentRulesAsMap(Context ctx) {
        List<PackageInfoData> apps = getApps(ctx, null);
        Map<String, JSONObject> exportMap = new HashMap<>();

        try {
            for (PackageInfoData app : apps) {
                updateExportPackage(exportMap, app.pkgName, app.selected_wifi, WIFI_EXPORT);
                updateExportPackage(exportMap, app.pkgName, app.selected_3g, DATA_EXPORT);
                updateExportPackage(exportMap, app.pkgName, app.selected_roam, ROAM_EXPORT);
                updateExportPackage(exportMap, app.pkgName, app.selected_vpn, VPN_EXPORT);
                updateExportPackage(exportMap, app.pkgName, app.selected_tether, TETHER_EXPORT);
                updateExportPackage(exportMap, app.pkgName, app.selected_lan, LAN_EXPORT);
                updateExportPackage(exportMap, app.pkgName, app.selected_tor, TOR_EXPORT);
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return exportMap;
    }


    public static boolean exportAll(Context ctx, final String fileName) {
        try {
            File file = getDefaultExportFile(ctx, fileName);
            return exportAllToFile(ctx, file);
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage(), e);
            return false;
        }
    }

    private static File getDefaultExportFile(Context ctx, final String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new File(ctx.getExternalFilesDir(null), fileName);
        } else {
            File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "afwall");
            dir.mkdirs();
            return new File(dir, fileName);
        }
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
        try {
            File file = getDefaultExportFile(ctx, fileName);
            return exportRulesToFile(ctx, file);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            return false;
        }
    }


    private static boolean  importRulesRoot(Context ctx, File file, StringBuilder msg) {
        boolean returnVal = false;
        BufferedReader br = null;
        try {
            // Use shell-safe quoting to prevent path injection
            String safePath = "'" + file.getAbsolutePath().replace("'", "'\\''" ) + "'";
            com.topjohnwu.superuser.Shell.Result result  = com.topjohnwu.superuser.Shell.cmd("cat " + safePath).exec();
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
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "Import rules file read rejected: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error during import: " + e.getLocalizedMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to import rules from file: " + e.getLocalizedMessage());
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
            returnVal = importRulesFromString(ctx, readImportData(br), msg);
        } catch (FileNotFoundException e) {
            if (e.getMessage().contains("EACCES")) {
                return importRulesRoot(ctx, file, msg);
            } else {
                msg.append(ctx.getString(R.string.import_rules_missing));
            }
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        return returnVal;
    }

    private static boolean importRulesFromString(Context ctx, String data, StringBuilder msg) {
        try {
            if (data.trim().isEmpty()) {
                msg.append("Import file contains no data");
                return false;
            }

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
            return true;
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return false;
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
        final PackageManager pm = ctx.getPackageManager();

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String pkgName = entry.getKey();
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
                    try {
                        uidBuilder.append(pm.getApplicationInfo(pkgName, 0).uid);
                    } catch (NameNotFoundException e) {
                        // Handle exception if needed
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
            returnVal = importAllFromString(ctx, readImportData(br), msg);
        } catch (FileNotFoundException e) {
            msg.append(ctx.getString(R.string.import_rules_missing));
        } catch (IOException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        return returnVal;
    }

    private static boolean importAllFromString(Context ctx, String data, StringBuilder msg) {
        boolean returnVal = false;
        try {
            if (data.trim().isEmpty()) {
                msg.append("Import file contains no data");
                return false;
            }
            
            JSONObject object = new JSONObject(data);
            // Basic validation of expected JSON structure
            if (!object.has("prefs") && !object.has("profiles") && !object.has("_profiles") && !object.has("default")) {
                msg.append("Import file does not contain valid AFWall+ data");
                Log.w(TAG, "Invalid import file structure - missing expected keys");
                return false;
            }

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
                        G.gPrefs.edit().putBoolean(key, Boolean.parseBoolean(value)).apply();
                    } else {
                        try {
                            if (key.equals("multiUserId")) {
                                G.gPrefs.edit().putLong(key, Long.parseLong(value)).apply();
                            } else if (isIntType(key)) {
                                G.gPrefs.edit().putString(key, value).apply();
                            } else {
                                int intValue = Integer.parseInt(value);
                                G.gPrefs.edit().putInt(key, intValue).apply();
                            }
                        } catch (NumberFormatException e) {
                            G.gPrefs.edit().putString(key, value).apply();
                        }
                    }
                }
            }

            // Import profile-specific preferences if available
            if (object.has("profilePrefs")) {
                JSONArray profilePrefArray = object.getJSONArray("profilePrefs");
                for (int i = 0; i < profilePrefArray.length(); i++) {
                    JSONObject prefObj = profilePrefArray.getJSONObject(i);
                    Iterator<String> keys = prefObj.keys();

                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = prefObj.getString(key);
                        if (shouldIgnoreKey(key)) {
                            continue;
                        }
                        if (value.equals("true") || value.equals("false")) {
                            G.pPrefs.edit().putBoolean(key, Boolean.parseBoolean(value)).apply();
                        } else {
                            try {
                                if (key.equals("multiUserId")) {
                                    G.pPrefs.edit().putLong(key, Long.parseLong(value)).apply();
                                } else if (isIntType(key)) {
                                    G.pPrefs.edit().putString(key, value).apply();
                                } else {
                                    int intValue = Integer.parseInt(value);
                                    G.pPrefs.edit().putInt(key, intValue).apply();
                                }
                            } catch (NumberFormatException e) {
                                G.pPrefs.edit().putString(key, value).apply();
                            }
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
        } catch (JSONException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        return returnVal;
    }

    private static String readImportData(BufferedReader br) throws IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        int maxImportSize = 50 * 1024 * 1024;
        while ((read = br.read(buffer)) != -1) {
            text.append(buffer, 0, read);
            if (text.length() > maxImportSize) {
                throw new IOException("Import file is too large (>50MB)");
            }
        }
        return text.toString();
    }

    public static boolean loadSharedPreferencesFromUri(Context ctx, StringBuilder builder, Uri uri, boolean loadAll) {
        try (InputStream inputStream = ctx.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                builder.append(ctx.getString(R.string.import_rules_missing));
                Log.w(TAG, "Import URI could not be opened: " + uri);
                return false;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String data = readImportData(br);
                if (loadAll) {
                    return importAllFromString(ctx, data, builder);
                }
                return importRulesFromString(ctx, data, builder);
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains(">50MB")) {
                builder.append("Import file is too large (>50MB)");
            }
            Log.e(TAG, "Unable to import from URI: " + uri, e);
        }
        return false;
    }

    public static boolean loadSharedPreferencesFromFile(Context ctx, StringBuilder builder, String fileName, boolean loadAll) {
        boolean res = false;
        File file = new File(fileName);
        if (file.exists()) {
            // Basic file validation
            if (file.length() == 0) {
                builder.append("Import file is empty");
                Log.w(TAG, "Import file is empty: " + fileName);
                return false;
            }
            if (file.length() > 50 * 1024 * 1024) { // 50MB limit
                builder.append("Import file is too large (>50MB)");
                Log.w(TAG, "Import file is too large: " + fileName + " (" + file.length() + " bytes)");
                return false;
            }
            
            Log.i(TAG, "Importing from file: " + fileName + " (loadAll: " + loadAll + ")");
            if (loadAll) {
                res = importAll(ctx, file, builder);
            } else {
                res = importRules(ctx, file, builder);
            }
        } else {
            builder.append("Import file does not exist: " + fileName);
            Log.w(TAG, "Import file does not exist: " + fileName);
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
        try {
            Shell.Result result = Shell.cmd("cat /proc/net/ip_tables_targets").exec();
            return netfiler_exists && result.isSuccess();
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "Netfilter check rejected: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to check netfilter support: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a package has android.permission.INTERNET via shell.
     * Used for packages invisible to PackageManager due to package visibility restrictions.
     */
    private static boolean hasInternetPermissionViaShell(String packageName) {
        try {
            Shell.Result result = Shell.cmd("dumpsys package " + packageName + " | grep android.permission.INTERNET").exec();
            List<String> out = result.getOut();
            for (String line : out) {
                if (line.contains("android.permission.INTERNET")) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check INTERNET permission for " + packageName + ": " + e.getMessage());
        }
        return false;
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
     * Encrypt the password - DEPRECATED: Use SecureCrypto.encryptSecure() for new code
     * This method is kept for backward compatibility only
     *
     * @param key
     * @param data
     * @return
     * @deprecated Use SecureCrypto.encryptSecure() instead for better security
     */
    @Deprecated
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
     * Decrypt the password - DEPRECATED: Use SecureCrypto.decryptSecure() for new code  
     * This method is kept for backward compatibility only
     *
     * @param key
     * @param data
     * @return
     * @deprecated Use SecureCrypto.decryptSecure() instead for better security
     */
    @Deprecated
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

    /**
     * Safe shell command execution that handles library-level crashes
     */
    private static List<String> executeSafeShellCommand(String command) {
        // First try the primary libsu approach
        try {
            // Check if we can get a valid shell
            if (Shell.getShell() == null || !Shell.getShell().isAlive()) {
                Log.w(TAG, "Shell is not available or not alive, trying fallback");
                return executeFallbackShellCommand(command);
            }

            // Execute with timeout and proper error handling
            Shell.Result result = Shell.cmd(command).exec();
            return result != null ? result.getOut() : null;
            
        } catch (java.util.concurrent.RejectedExecutionException e) {
            Log.w(TAG, "Shell execution rejected - trying fallback: " + e.getMessage());
            return executeFallbackShellCommand(command);
        } catch (RuntimeException e) {
            // Check for wrapped ExecutionException with InterruptedIOException
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.ExecutionException) {
                java.util.concurrent.ExecutionException execEx = (java.util.concurrent.ExecutionException) cause;
                if (execEx.getCause() instanceof java.io.InterruptedIOException) {
                    Log.w(TAG, "Shell execution interrupted at library level - trying fallback: " + execEx.getCause().getMessage());
                    return executeFallbackShellCommand(command);
                }
            }
            // Re-throw if it's not a known interruption issue
            throw e;
        } catch (Exception e) {
            Log.w(TAG, "Unexpected error in safe shell execution, trying fallback: " + e.getMessage());
            return executeFallbackShellCommand(command);
        }
    }
    
    /**
     * Fallback shell execution using the legacy RootShell library
     * This provides an alternative when libsu fails due to interruptions
     */
    private static List<String> executeFallbackShellCommand(String command) {
        try {
            Log.d(TAG, "Using fallback shell execution for command: " + command);
            
            // Use the legacy RootShell library as fallback
            final java.util.List<String> output = new java.util.ArrayList<>();
            final boolean[] completed = {false};
            
            com.stericson.rootshell.execution.Command cmd = new com.stericson.rootshell.execution.Command(0, command) {
                @Override
                public void commandCompleted(int id, int exitcode) {
                    super.commandCompleted(id, exitcode);
                    completed[0] = true;
                }
                
                @Override
                public void commandOutput(int id, String line) {
                    super.commandOutput(id, line);
                    if (line != null) {
                        output.add(line);
                    }
                }
            };
            
            // Execute with timeout
            com.stericson.roottools.RootTools.getShell(true, 0).add(cmd);
            
            // Wait for completion with timeout
            long startTime = System.currentTimeMillis();
            while (!completed[0] && (System.currentTimeMillis() - startTime) < 30000) {
                Thread.sleep(100);
            }
            
            if (completed[0]) {
                Log.d(TAG, "Fallback shell execution completed successfully");
                return output;
            } else {
                Log.w(TAG, "Fallback shell execution timed out");
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Fallback shell execution also failed: " + e.getMessage());
            return null;
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
                // Check if task is cancelled before proceeding
                if (isCancelled()) {
                    Log.d(TAG, "RunCommand task was cancelled, aborting execution");
                    return -1;
                }
                
                if (Shell.getShell().isRoot() && !Shell.isAppGrantedRoot())
                    return -1;
                if (commands != null && commands.size() > 0) {
                    // Check again before executing shell command
                    if (isCancelled()) {
                        Log.d(TAG, "RunCommand task was cancelled before shell execution");
                        return -1;
                    }
                    
                    // Use a safe shell execution wrapper
                    List<String> output = executeSafeShellCommand(String.valueOf(commands));
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
            } catch (java.util.concurrent.RejectedExecutionException e) {
                Log.w(TAG, "Shell execution rejected, likely due to app shutdown: " + e.getMessage());
                exitCode = -1;
            } catch (RuntimeException ex) {
                // Check if this is a wrapped ExecutionException with InterruptedIOException
                Throwable cause = ex.getCause();
                if (cause instanceof java.util.concurrent.ExecutionException) {
                    java.util.concurrent.ExecutionException execEx = (java.util.concurrent.ExecutionException) cause;
                    if (execEx.getCause() instanceof java.io.InterruptedIOException) {
                        Log.w(TAG, "Shell command execution was interrupted: " + execEx.getCause().getMessage());
                        exitCode = -1;
                        return exitCode;
                    }
                } else if (ex.getCause() instanceof java.io.InterruptedIOException) {
                    Log.w(TAG, "Shell command execution was interrupted: " + ex.getCause().getMessage());
                    exitCode = -1;
                    return exitCode;
                }
                Log.e(TAG, "Shell command execution failed: " + ex.getMessage());
                if (res != null)
                    res.append("\n").append(ex);
                exitCode = -1;
            } catch (Exception ex) {
                Log.e(TAG, "Shell command execution failed with unexpected exception: " + ex.getMessage());
                if (res != null)
                    res.append("\n").append(ex);
                exitCode = -1;
            }
            return exitCode;
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "RunCommand task was cancelled");
            super.onCancelled();
        }

        @Override
        protected void onCancelled(Integer result) {
            Log.d(TAG, "RunCommand task was cancelled with result: " + result);
            super.onCancelled(result);
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
         * Application Type
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
                s.append("[ ");
                s.append(uid);
                s.append(" ] ");
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
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
