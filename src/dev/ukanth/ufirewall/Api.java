/**
 * 
 * All iptables "communication" is handled by this class.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.2
 */


package dev.ukanth.ufirewall;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;
import dev.ukanth.ufirewall.MainActivity.GetAppList;
import dev.ukanth.ufirewall.RootShell.RootCommand;
import dev.ukanth.ufirewall.util.JsonHelper;
import eu.chainfire.libsuperuser.Shell.SU;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
	/** application logcat tag */
	public static final String TAG = "AFWall";
	
	/** special application UID used to indicate "any application" */
	public static final int SPECIAL_UID_ANY	= -10;
	/** special application UID used to indicate the Linux Kernel */
	public static final int SPECIAL_UID_KERNEL	= -11;
	/** special application UID used for dnsmasq DHCP/DNS */
	public static final int SPECIAL_UID_TETHER	= -12;
	/** special application UID used for netd DNS proxy */
	//public static final int SPECIAL_UID_DNSPROXY	= -13;
	/** special application UID used for NTP */
	public static final int SPECIAL_UID_NTP		= -14;
	
	public static final int NOTIFICATION_ID = 24556;
	
	private static String charsetName = "UTF8";
    private static String algorithm = "DES";
    private static int base64Mode = Base64.DEFAULT;
	
	private static final int WIFI_EXPORT = 0;
	private static final int DATA_EXPORT = 1;
	private static final int ROAM_EXPORT = 2;
	private static final int VPN_EXPORT = 3;
	private static final int LAN_EXPORT = 4;

	// Preferences
	public static String PREFS_NAME 				= "AFWallPrefs";
	public static final String PREF_FIREWALL_STATUS = "AFWallStaus";
	public static final String DEFAULT_PREFS_NAME 	= "AFWallPrefs";
	
	//for import/export rules
	public static final String PREF_3G_PKG			= "AllowedPKG3G";
	public static final String PREF_WIFI_PKG		= "AllowedPKGWifi";
	public static final String PREF_ROAMING_PKG		= "AllowedPKGRoaming";
	public static final String PREF_VPN_PKG			= "AllowedPKGVPN";
	public static final String PREF_LAN_PKG			= "AllowedPKGLAN";
	
	//revertback to old approach for performance
	public static final String PREF_3G_PKG_UIDS			= "AllowedPKG3G_UIDS";
	public static final String PREF_WIFI_PKG_UIDS		= "AllowedPKGWifi_UIDS";
	public static final String PREF_ROAMING_PKG_UIDS	= "AllowedPKGRoaming_UIDS";
	public static final String PREF_VPN_PKG_UIDS		= "AllowedPKGVPN_UIDS";
	public static final String PREF_LAN_PKG_UIDS		= "AllowedPKGLAN_UIDS";
	
	
	public static final String PREF_PASSWORD 		= "Password";
	public static final String PREF_CUSTOMSCRIPT 	= "CustomScript";
	public static final String PREF_CUSTOMSCRIPT2 	= "CustomScript2"; // Executed on shutdown
	public static final String PREF_MODE 			= "BlockMode";
	public static final String PREF_ENABLED			= "Enabled";
	// Modes
	public static final String MODE_WHITELIST 		= "whitelist";
	public static final String MODE_BLACKLIST 		= "blacklist";
	// Messages
	
	public static final String STATUS_CHANGED_MSG 	= "dev.ukanth.ufirewall.intent.action.STATUS_CHANGED";
	public static final String TOGGLE_REQUEST_MSG	= "dev.ukanth.ufirewall.intent.action.TOGGLE_REQUEST";
	public static final String CUSTOM_SCRIPT_MSG	= "dev.ukanth.ufirewall.intent.action.CUSTOM_SCRIPT";
	// Message extras (parameters)
	public static final String STATUS_EXTRA			= "dev.ukanth.ufirewall.intent.extra.STATUS";
	public static final String SCRIPT_EXTRA			= "dev.ukanth.ufirewall.intent.extra.SCRIPT";
	public static final String SCRIPT2_EXTRA		= "dev.ukanth.ufirewall.intent.extra.SCRIPT2";
	
	private static final String ITFS_WIFI[] = InterfaceTracker.ITFS_WIFI;
	private static final String ITFS_3G[] = InterfaceTracker.ITFS_3G;
	private static final String ITFS_VPN[] = InterfaceTracker.ITFS_VPN;

	// iptables can exit with status 4 if two processes tried to update the same table
	private static final int IPTABLES_TRY_AGAIN = 4;
	
	private static String AFWALL_CHAIN_NAME = "afwall";

	private static final String dynChains[] = { "-3g-postcustom", "-3g-fork", "-wifi-postcustom", "-wifi-fork" };
	
	private static final String staticChains[] = {  "", "-3g", "-wifi", 
		  "-reject", "-vpn", "-3g-tether",  "-3g-home",  "-3g-roam", 
		  "-wifi-tether", "-wifi-wan", "-wifi-lan" };

	// Cached applications
	public static List<PackageInfoData> applications = null;
	
	//for custom scripts
	public static String ipPath = null;
	public static String bbPath = null;
	public static boolean setv6 = false;
	private static Map<String,Integer> specialApps = null;

	private static boolean rulesUpToDate = false;

    public static String getIpPath() {
		return ipPath;
	}

	/**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
	public static void alert(Context ctx, CharSequence msgText) {
		if (ctx != null) {
			Toast.makeText(ctx, msgText, Toast.LENGTH_SHORT).show();
		}
	}
	
	public static void alertDialog(final Context ctx, String msgText) {
		if (ctx != null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
			builder.setMessage(msgText)
			       .setCancelable(false)
			       .setPositiveButton(ctx.getString(R.string.OK), new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
		}
	}

	static String customScriptHeader(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		final String myiptables = dir + "/iptables";
		final String mybusybox = dir + "/busybox";
		return "" +
			"IPTABLES="+ myiptables + "\n" +
			"BUSYBOX="+mybusybox+"\n" +
			"";
	}
	
	static void setIpTablePath(Context ctx,boolean setv6) {
		boolean builtin;
		String pref = G.ip_path();

		if (pref.equals("system")) {
			builtin = false;
		} else if (pref.equals("builtin")) {
			builtin = true;
		} else {
			// auto setting:
			// IPv4 iptables on ICS+ devices is mostly sane, so we'll use it by default
			// IPv6 ip6tables can return the wrong exit status (bug #215) so default to our fixed version
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !setv6) {
				builtin = false;
			} else {
				builtin = true;
			}
		}

		String dir = "";
		if (builtin) {
			dir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
		}

		Api.setv6 = setv6;
		Api.ipPath = dir + (setv6 ? "ip6tables" : "iptables");
		Api.bbPath = getBusyBoxPath(ctx);
	}
	
	public static String getBusyBoxPath(Context ctx) {
		if (G.bb_path().equals("system")) {
			return "busybox ";
		} else {
			return ctx.getDir("bin",0).getAbsolutePath() + "/busybox";
		}
	}
	
	public static String getKLogPath(Context ctx) {
		return ctx.getDir("bin",0).getAbsolutePath() + "/klogripper ";
	}
	
	static String getNflogPath(Context ctx) {
		return ctx.getDir("bin",0).getAbsolutePath() + "/nflog ";
	}
	/**
	 * Copies a raw resource file, given its ID to the given location
	 * @param ctx context
	 * @param resid resource id
	 * @param file destination file
	 * @param mode file permissions (E.g.: "755")
	 * @throws IOException on error
	 * @throws InterruptedException when interrupted
	 */
	private static void copyRawFile(Context ctx, int resid, File file, String mode) throws IOException, InterruptedException
	{
		final String abspath = file.getAbsolutePath();
		// Write the iptables binary
		final FileOutputStream out = new FileOutputStream(file);
		final InputStream is = ctx.getResources().openRawResource(resid);
		byte buf[] = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		out.close();
		is.close();
		// Change the permissions
		Runtime.getRuntime().exec("chmod "+mode+" "+abspath).waitFor();
	}
	
	public static void replaceAll(StringBuilder builder, String from, String to ) {
		int index = builder.indexOf(from);
	    while (index != -1)
	    {
	        builder.replace(index, index + from.length(), to);
	        index += to.length(); // Move to the end of the replacement
	        index = builder.indexOf(from, index);
	    }
	}

	/**
	 * Look up uid for each user by name, and if he exists, append an iptables rule.
	 * @param listCommands current list of iptables commands to execute
	 * @param users list of users to whom the rule applies
	 * @param prefix "iptables" command and the portion of the rule preceding "-m owner --uid-owner X"
	 * @param suffix the remainder of the iptables rule, following "-m owner --uid-owner X"
	 */
	private static void addRuleForUsers(List<String> listCommands, String users[], String prefix, String suffix) {
		for (String user : users) {
			int uid = android.os.Process.getUidForName(user);
			if (uid != -1)
				listCommands.add(prefix + " -m owner --uid-owner " + uid + " " + suffix);
		}
	}

	private static void addRulesForUidlist(List<String> cmds, List<Integer> uids, String chain, boolean whitelist) {
		String action = whitelist ? " -j RETURN" : " -j " + AFWALL_CHAIN_NAME + "-reject";

		if (uids.indexOf(SPECIAL_UID_ANY) >= 0) {
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
			
			if(whitelist) {
				if (pref.equals("auto")) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
						addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53",  " -j RETURN");
					} else {
						addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");	
					}
				} else if(pref.equals("disable")){
					addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
				} else {
					addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53",  " -j RETURN");
				}
			} else {
				if(pref.equals("disable")){
					addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
				} else if (pref.equals("enable")) {
					addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53",  " -j RETURN");
				}
			}

			// NTP service runs as "system" user
			if (uids.indexOf(SPECIAL_UID_NTP) >= 0) {
				addRuleForUsers(cmds, new String[]{"system"}, "-A " + chain + " -p udp --dport 123", action);
			}

			boolean kernel_checked = uids.indexOf(SPECIAL_UID_KERNEL) >= 0;
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

	private static void addRejectRules(List<String> cmds, Context ctx) {
		// set up reject chain to log or not log
		// this can be changed dynamically through the Firewall Logs activity
		
		if (G.enableLog()) {
			if (G.logTarget().equals("LOG")) {
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL}\" --log-level 4 --log-uid");
			} else if (G.logTarget().equals("NFLOG")) {
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40");
			}
		}
		cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j REJECT");
	}

	private static void addCustomRules(Context ctx, String prefName, List<String> cmds) {
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
	 * @param ctx application context
	 * @param cmds command list
	 */
	private static void addInterfaceRouting(Context ctx, List<String> cmds) {
		final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx);
		final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

		for (String s : dynChains) {
			cmds.add("-F " + AFWALL_CHAIN_NAME + s);
		}

		if (whitelist) {
			// always allow the DHCP client full wifi access
			addRuleForUsers(cmds, new String[]{"dhcp", "wifi"}, "-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom", "-j RETURN");
		}

		if (cfg.isTethered) {
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-tether");
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-tether");
		} else {
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-fork");
		}

		if (G.enableLAN() && !cfg.isTethered) {
			if(setv6 && !cfg.lanMaskV6.equals("")) {
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -d " + cfg.lanMaskV6 + " -j " + AFWALL_CHAIN_NAME + "-wifi-lan");
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork '!' -d " + cfg.lanMaskV6 + " -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
			} else if(!setv6 && !cfg.lanMaskV4.equals("")) {
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -d " + cfg.lanMaskV4 + " -j " + AFWALL_CHAIN_NAME + "-wifi-lan");
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork '!' -d "+ cfg.lanMaskV4 + " -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
			} else {
				// No IP address -> no traffic.  This prevents a data leak between the time
				// the interface gets an IP address, and the time we process the intent
				// (which could be 5+ seconds).  This is likely to catch a little bit of
				// legitimate traffic from time to time, so we won't log the failures.
				cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j REJECT");
			}
		} else {
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
		}

		if (G.enableRoam() && cfg.isRoaming) {
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-roam");
		} else {
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-home");
		}
	}

	private static void applyShortRules(Context ctx, List<String> cmds) {
		cmds.add("-P OUTPUT DROP");
		addInterfaceRouting(ctx, cmds);
		cmds.add("-P OUTPUT ACCEPT");
	}

    /**
     * Purge and re-add all rules (internal implementation).
     * @param ctx application context (mandatory)
     * @param uidsWifi list of selected UIDs for WIFI to allow or disallow (depending on the working mode)
     * @param uids3g list of selected UIDs for 2G/3G to allow or disallow (depending on the working mode)
     * @param showErrors indicates if errors should be alerted
     */
	private static boolean applyIptablesRulesImpl(final Context ctx, List<Integer> uidsWifi, List<Integer> uids3g,
			List<Integer> uidsRoam, List<Integer> uidsVPN, List<Integer> uidsLAN, final boolean showErrors,
			List<String> out) {
		if (ctx == null) {
			return false;
		}
		
		assertBinaries(ctx, showErrors);
		if(G.isMultiUser()) {
			//FIXME: after setting this, we need to flush the iptables ?
			if(G.getMultiUserId() > 0) {
				AFWALL_CHAIN_NAME = "afwall" + G.getMultiUserId();
			}
		}			
		final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

		List<String> cmds = new ArrayList<String>();
		cmds.add("-P INPUT ACCEPT");
		cmds.add("-P FORWARD ACCEPT");

		// prevent data leaks due to incomplete rules
		cmds.add("-P OUTPUT DROP");

		for (String s : staticChains) {
			cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
			cmds.add("-F " + AFWALL_CHAIN_NAME + s);
		}
		for (String s : dynChains) {
			cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
			// addInterfaceRouting() will flush these chains, but not create them
		}

		cmds.add("#NOCHK# -D OUTPUT -j " + AFWALL_CHAIN_NAME );
		cmds.add("-I OUTPUT 1 -j " + AFWALL_CHAIN_NAME );

		// custom rules in afwall-{3g,wifi,reject} supersede everything else
		addCustomRules(ctx, Api.PREF_CUSTOMSCRIPT, cmds);
		cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g -j " + AFWALL_CHAIN_NAME + "-3g-postcustom");
		cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi -j " + AFWALL_CHAIN_NAME + "-wifi-postcustom");
		addRejectRules(cmds,ctx);

		if (G.enableInbound()) {
			// we don't have any rules in the INPUT chain prohibiting inbound traffic, but
			// local processes can't reply to half-open connections without this rule
			cmds.add("-A afwall -m state --state ESTABLISHED -j RETURN");
		}

		addInterfaceRouting(ctx, cmds);

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
		for (final String itf : ITFS_WIFI) {
			cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-wifi");
		}

		for (final String itf : ITFS_3G) {
			cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-3g");
		}

		final boolean any_wifi = uidsWifi.indexOf(SPECIAL_UID_ANY) >= 0;
		final boolean any_3g = uids3g.indexOf(SPECIAL_UID_ANY) >= 0;

		// special rules to allow 3G<->wifi tethering
		// note that this can only blacklist DNS/DHCP services, not all tethered traffic
		if (((!whitelist && (any_wifi || any_3g)) ||
		     (uids3g.indexOf(SPECIAL_UID_TETHER) >= 0) || (uidsWifi.indexOf(SPECIAL_UID_TETHER) >= 0))) {

			String users[] = { "root", "nobody" };
			String action = " -j " + (whitelist ? "RETURN" : AFWALL_CHAIN_NAME + "-reject");

			// DHCP replies to client
			addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=67 --dport=68" + action);

			// DNS replies to client
			addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=53" + action);
			addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p tcp --sport=53" + action);

			// DNS requests to upstream servers
			addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p udp --dport=53" + action);
			addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p tcp --dport=53" + action);
		}

		// if tethered, try to match the above rules (if enabled).  no match -> fall through to the
		// normal 3G/wifi rules
		cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-tether -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
		cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-tether -j " + AFWALL_CHAIN_NAME + "-3g-fork");

		// NOTE: we still need to open a hole to let WAN-only UIDs talk to a DNS server
		// on the LAN
		if (whitelist) {
			cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-lan -p udp --dport 53 -j RETURN");
		}

		// now add the per-uid rules for 3G home, 3G roam, wifi WAN, wifi LAN, VPN
		// in whitelist mode the last rule in the list routes everything else to afwall-reject
		addRulesForUidlist(cmds, uids3g,  AFWALL_CHAIN_NAME + "-3g-home", whitelist);
		addRulesForUidlist(cmds, uidsRoam, AFWALL_CHAIN_NAME + "-3g-roam", whitelist);
		addRulesForUidlist(cmds, uidsWifi, AFWALL_CHAIN_NAME + "-wifi-wan", whitelist);
		addRulesForUidlist(cmds, uidsLAN,  AFWALL_CHAIN_NAME + "-wifi-lan", whitelist);
		addRulesForUidlist(cmds, uidsVPN, AFWALL_CHAIN_NAME + "-vpn", whitelist);

		cmds.add("-P OUTPUT ACCEPT");

		iptablesCommands(cmds, out);
		return true;
    }

	/**
	 * Add the repetitive parts (ipPath and such) to an iptables command list
	 * 
	 * @param in Commands in the format: "-A foo ...", "#NOCHK# -A foo ...", or "#LITERAL# <UNIX command>"
	 * @param out A list of UNIX commands to execute
	 */
	private static void iptablesCommands(List<String> in, List<String> out) {
		boolean firstLit = true;
		for (String s : in) {
			if (s.matches("#LITERAL# .*")) {
				if (firstLit) {
					// export vars for the benefit of custom scripts
					// "true" is a dummy command which needs to return success
					firstLit = false;
					out.add("export IPTABLES=\"" + ipPath + "\"; "
							+ "export BUSYBOX=\"" + bbPath + "\"; "
							+ "export IPV6=" + (setv6 ? "1" : "0") + "; "
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

	@Deprecated
	private static List<Integer> getUidListFromPref(Context ctx,final String pks) {
		initSpecial();
		final PackageManager pm = ctx.getPackageManager();
		final List<Integer> uids = new ArrayList<Integer>();
		final StringTokenizer tok = new StringTokenizer(pks, "|");
		while (tok.hasMoreTokens()) {
			final String pkg = tok.nextToken();
			if (pkg != null && pkg.length() > 0) {
				try {
					if (pkg.startsWith("dev.afwall.special")) {
						uids.add(specialApps.get(pkg));
					} else {
						uids.add(pm.getApplicationInfo(pkg, 0).uid);
					}
				} catch (Exception ex) {
				}
			}

		}

		Collections.sort(uids);	
		return uids;
	}
	

    /**
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     * @param callback If non-null, use a callback instead of blocking the current thread
     */
	public static boolean applySavedIptablesRules(Context ctx, boolean showErrors, RootCommand callback) {
		if (ctx == null) {
			return false;
		}
		initSpecial();
		
		final String savedPkg_wifi_uid = G.pPrefs.getString(PREF_WIFI_PKG_UIDS, "");
		final String savedPkg_3g_uid = G.pPrefs.getString(PREF_3G_PKG_UIDS, "");
		final String savedPkg_roam_uid = G.pPrefs.getString(PREF_ROAMING_PKG_UIDS, "");
		final String savedPkg_vpn_uid = G.pPrefs.getString(PREF_VPN_PKG_UIDS, "");
		final String savedPkg_lan_uid = G.pPrefs.getString(PREF_LAN_PKG_UIDS, "");

		boolean returnValue = false;
		List<String> cmds = new ArrayList<String>();

		setIpTablePath(ctx,false);
		returnValue = applyIptablesRulesImpl(ctx,
					getListFromPref(savedPkg_wifi_uid),
					getListFromPref(savedPkg_3g_uid),
					getListFromPref(savedPkg_roam_uid),
					getListFromPref(savedPkg_vpn_uid),
					getListFromPref(savedPkg_lan_uid),
					showErrors,
					cmds);
		if (returnValue == false) {
			return false;
		}

		if (G.enableIPv6()) {
			setIpTablePath(ctx,true);
			returnValue = applyIptablesRulesImpl(ctx,
					getListFromPref(savedPkg_wifi_uid),
					getListFromPref(savedPkg_3g_uid),
					getListFromPref(savedPkg_roam_uid),
					getListFromPref(savedPkg_vpn_uid),
					getListFromPref(savedPkg_lan_uid),
					showErrors,
					cmds);
			if (returnValue == false) {
				return false;
			}
		}

		rulesUpToDate = true;

		if (G.logTarget().equals("NFLOG")) {
			Intent intent = new Intent(ctx.getApplicationContext(), NflogService.class);
			ctx.startService(intent);
		}

		if (callback != null) {
			callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds);
			return true;
		} else {
			fixupLegacyCmds(cmds);
			try {
				final StringBuilder res = new StringBuilder();
				int code = runScriptAsRoot(ctx, cmds, res);
				if (showErrors && code != 0) {
					String msg = res.toString();
					// Remove unnecessary help message from output
					if (msg.indexOf("\nTry `iptables -h' or 'iptables --help' for more information.") != -1) {
						msg = msg.replace("\nTry `iptables -h' or 'iptables --help' for more information.", "");
					}
					alert(ctx, ctx.getString(R.string.error_apply)  + code + "\n\n" + msg.trim() );
				} else {
					return true;
				}
			} catch (Exception e) {
				Log.e(TAG, "Exception while applying rules: " + e.getMessage());
				if (showErrors) alert(ctx, ctx.getString(R.string.error_refresh) + e);
			}
			return false;
		}
	}

	@Deprecated
	public static boolean applySavedIptablesRules(Context ctx, boolean showErrors) {
		return applySavedIptablesRules(ctx, showErrors, null);
	}

	public static boolean fastApply(Context ctx, RootCommand callback) {
		
		if (!rulesUpToDate) {
			return applySavedIptablesRules(ctx, true, callback);
		}

		List<String> out = new ArrayList<String>();
		List<String> cmds;

		cmds = new ArrayList<String>();
		setIpTablePath(ctx, false);
		applyShortRules(ctx, cmds);
		iptablesCommands(cmds, out);

		if (G.enableIPv6()) {
			setIpTablePath(ctx, true);
			cmds = new ArrayList<String>();
			applyShortRules(ctx, cmds);
			iptablesCommands(cmds, out);
		}
		callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
		return true;
	}

	/**
	 * Save current rules using the preferences storage.
	 * @param ctx application context (mandatory)
	 */
	public static void saveRules(Context ctx) {
		final SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

		rulesUpToDate = false;

		final boolean enableVPN = defaultPrefs.getBoolean("enableVPN", false);
		final boolean enableLAN = defaultPrefs.getBoolean("enableLAN", false);
		final boolean enableRoam = defaultPrefs.getBoolean("enableRoam", true);

		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		final List<PackageInfoData> apps = getApps(ctx,null);
		
		if(apps != null) {
			// Builds a pipe-separated list of names
			final StringBuilder newpkg_wifi = new StringBuilder();
			final StringBuilder newpkg_3g = new StringBuilder();
			final StringBuilder newpkg_roam = new StringBuilder();
			final StringBuilder newpkg_vpn = new StringBuilder();
			final StringBuilder newpkg_lan = new StringBuilder();
			
			for (int i=0; i<apps.size(); i++) {
				
				if (apps.get(i).selected_wifi) {
					if (newpkg_wifi.length() != 0) newpkg_wifi.append('|');
					newpkg_wifi.append(apps.get(i).uid);
					
				}
				if (apps.get(i).selected_3g) {
					if (newpkg_3g.length() != 0) newpkg_3g.append('|');
					newpkg_3g.append(apps.get(i).uid);
				}
				if (enableRoam && apps.get(i).selected_roam) {
					if (newpkg_roam.length() != 0) newpkg_roam.append('|');
					newpkg_roam.append(apps.get(i).uid);
				}
				
				if (enableVPN && apps.get(i).selected_vpn) {
					if (newpkg_vpn.length() != 0) newpkg_vpn.append('|');
					newpkg_vpn.append(apps.get(i).uid);
				}

				if (enableLAN && apps.get(i).selected_lan) {
					if (newpkg_lan.length() != 0) newpkg_lan.append('|');
					newpkg_lan.append(apps.get(i).uid);
				}
			}
			// save the new list of UIDs
			final Editor edit = prefs.edit();
			edit.putString(PREF_WIFI_PKG_UIDS, newpkg_wifi.toString());
			edit.putString(PREF_3G_PKG_UIDS, newpkg_3g.toString());
			edit.putString(PREF_ROAMING_PKG_UIDS, newpkg_roam.toString());
			edit.putString(PREF_VPN_PKG_UIDS, newpkg_vpn.toString());
			edit.putString(PREF_LAN_PKG_UIDS, newpkg_lan.toString());
			
			edit.commit();
		}
		
    }
	
    /**
     * Purge all iptables rules.
     * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
     * @param callback If non-null, use a callback instead of blocking the current thread
     * @return true if the rules were purged
     */
	public static boolean purgeIptables(Context ctx, boolean showErrors, RootCommand callback) {

		List<String> cmds = new ArrayList<String>();
		List<String> out = new ArrayList<String>();

		for (String s : staticChains) {
			cmds.add("-F " + AFWALL_CHAIN_NAME + s);
		}
		for (String s : dynChains) {
			cmds.add("-F " + AFWALL_CHAIN_NAME + s);
		}
		//make sure reset the OUTPUT chain to accept state.
		cmds.add("-P OUTPUT ACCEPT");
		
		//Delete only when the afwall chain exist !
		cmds.add("-D OUTPUT -j " + AFWALL_CHAIN_NAME );
		
		addCustomRules(ctx, Api.PREF_CUSTOMSCRIPT2, cmds);

		try {
			assertBinaries(ctx, showErrors);

			// IPv4
			setIpTablePath(ctx, false);
			iptablesCommands(cmds, out);

			// IPv6
			if(G.enableIPv6()) {
				setIpTablePath(ctx,true);
				iptablesCommands(cmds, out);
			}

			if (callback != null) {
				callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
			} else {
				fixupLegacyCmds(out);
				if (runScriptAsRoot(ctx, out, new StringBuilder()) == -1) {
					if(showErrors) alert(ctx, ctx.getString(R.string.error_purge));
					return false;
				}
			}

			return true;
		} catch (Exception e) {
			return false;
		}
    }

	@Deprecated
	public static boolean purgeIptables(Context ctx, boolean showErrors) {
		// warning: this is a blocking call
		return purgeIptables(ctx, showErrors, null);
	}

	/**
	 * Retrieve the current set of IPv4 or IPv6 rules and pass it to a callback
	 * 
	 * @param ctx application context
	 * @param callback callback to receive rule list
	 * @param useIPV6 true to list IPv6 rules, false to list IPv4 rules
	 */
	public static void fetchIptablesRules(Context ctx, boolean useIPV6, RootCommand callback) {
		List<String> cmds = new ArrayList<String>();
		List<String> out = new ArrayList<String>();
		cmds.add("-n -v -L");
		if (useIPV6) {
			setIpTablePath(ctx, true);
		} else {
			setIpTablePath(ctx, false);
		}
		iptablesCommands(cmds, out);
		callback.run(ctx, out);
	}

	/**
	 * Run a list of commands with both iptables and ip6tables
	 * 
	 * @param ctx application context
	 * @param cmds list of commands to run
	 * @param callback callback for completion
	 */
	public static void apply46(Context ctx, List<String> cmds, RootCommand callback) {
		List<String> out = new ArrayList<String>();

		setIpTablePath(ctx, false);
		iptablesCommands(cmds, out);

		if (G.enableIPv6()) {
			setIpTablePath(ctx, true);
			iptablesCommands(cmds, out);
		}
		callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
	}

	/**
	 * Delete all firewall rules.  For diagnostic purposes only.
	 * 
	 * @param ctx application context
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
	 * @param ctx application context
	 * @param callback callback for completion
	 */
	public static void updateLogRules(Context ctx, RootCommand callback) {
		if (!isEnabled(ctx)) {
			return;
		}
		List<String> cmds = new ArrayList<String>();
		cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + "-reject");
		cmds.add("-F " + AFWALL_CHAIN_NAME + "-reject");
		addRejectRules(cmds,ctx);
		apply46(ctx, cmds, callback);
	}

	/**
	 * Clear firewall logs by purging dmesg
	 * 
	 * @param ctx application context
	 * @param callback Callback for completion status
	 */
	public static void clearLog(Context ctx, RootCommand callback) {
		callback.run(ctx, getBusyBoxPath(ctx) + " dmesg -c");
	}

	/**
	 * Fetch kernel logs via busybox dmesg.  This will include {AFL} lines from
	 * logging rejected packets.
	 * 
	 * @param ctx application context
	 * @param callback Callback for completion status
	 * @return true if logging is enabled, false otherwise
	 */
	public static boolean fetchLogs(Context ctx, RootCommand callback) {
		if(G.logTarget().equals("LOG")) {
			callback.run(ctx, getBusyBoxPath(ctx) + " dmesg");
			return true;
		} else {
			return false;
		}
	}

	/**
	 * List all interfaces via "ifconfig -a"
	 * 
	 * @param ctx application context
	 * @param callback Callback for completion status
	 */
	public static void runIfconfig(Context ctx, RootCommand callback) {
		callback.run(ctx, getBusyBoxPath(ctx) + " ifconfig -a");
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
		
			final SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
			final boolean enableVPN = defaultPrefs.getBoolean("enableVPN", false);
			final boolean enableLAN = defaultPrefs.getBoolean("enableLAN", false);
			final boolean enableRoam = defaultPrefs.getBoolean("enableRoam", true);
			
			final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			
			final String savedPkg_wifi_uid = prefs.getString(PREF_WIFI_PKG_UIDS, "");
			final String savedPkg_3g_uid = prefs.getString(PREF_3G_PKG_UIDS, "");
			final String savedPkg_roam_uid = prefs.getString(PREF_ROAMING_PKG_UIDS, "");
			final String savedPkg_vpn_uid = prefs.getString(PREF_VPN_PKG_UIDS, "");
			final String savedPkg_lan_uid = prefs.getString(PREF_LAN_PKG_UIDS, "");
			
			List<Integer> selected_wifi = new ArrayList<Integer>();
			List<Integer> selected_3g = new ArrayList<Integer>();
			List<Integer> selected_roam = new ArrayList<Integer>();
			List<Integer> selected_vpn = new ArrayList<Integer>();
			List<Integer> selected_lan = new ArrayList<Integer>();
			
			
			selected_wifi = getListFromPref(savedPkg_wifi_uid);
			selected_3g = getListFromPref(savedPkg_3g_uid);
			
			if (enableRoam) {
				selected_roam = getListFromPref(savedPkg_roam_uid);
			}
			if (enableVPN) {
				selected_vpn = getListFromPref(savedPkg_vpn_uid);
			}
			if (enableLAN) {
				selected_lan = getListFromPref(savedPkg_lan_uid);
			}
			//revert back to old approach
			
			//always use the defaul preferences to store cache value - reduces the application usage size
			final SharedPreferences cachePrefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);

			int count = 0;
			try {
				final PackageManager pkgmanager = ctx.getPackageManager();
				final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(PackageManager.GET_META_DATA);
				SparseArray<PackageInfoData> syncMap = new SparseArray<PackageInfoData>();
				final Editor edit = cachePrefs.edit();
				boolean changed = false;
				String name = null;
				String cachekey = null;
				final String cacheLabel = "cache.label.";
				PackageInfoData app = null;
				ApplicationInfo apinfo = null;
				
				for(int i = 0 ; i < installed.size();  i++) {
				//for (final ApplicationInfo apinfo : installed) {
					count = count+1;
					apinfo = installed.get(i);
					
					if(appList != null ){
						appList.doProgress(count);
					}
					
					boolean firstseen = false;
					app = syncMap.get(apinfo.uid);
					// filter applications which are not allowed to access the Internet
					if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
						continue;
					}
					// try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
					cachekey = cacheLabel + apinfo.packageName;
					name = prefs.getString(cachekey, "");
					if (name.length() == 0) {
						// get label and put on cache
						name = pkgmanager.getApplicationLabel(apinfo).toString();
						edit.putString(cachekey, name);
						changed = true;
						firstseen = true;
					} 
					if (app == null) {
						app = new PackageInfoData();
						app.uid = apinfo.uid;
						app.names = new ArrayList<String>();
						app.names.add(name);
						app.appinfo = apinfo;
						app.pkgName = apinfo.packageName;
						syncMap.put(apinfo.uid, app);
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
					if (enableRoam && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
						app.selected_roam = true;
					}
					if (enableVPN && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
						app.selected_vpn = true;
					}
					if (enableLAN && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
						app.selected_lan = true;
					}
					
				}
				
				List<PackageInfoData> specialData = new ArrayList<PackageInfoData>();
				specialData.add(new PackageInfoData(SPECIAL_UID_ANY, ctx.getString(R.string.all_item), "dev.afwall.special.any"));
				specialData.add(new PackageInfoData(SPECIAL_UID_KERNEL, ctx.getString(R.string.kernel_item), "dev.afwall.special.kernel"));
				specialData.add(new PackageInfoData(SPECIAL_UID_TETHER, ctx.getString(R.string.tethering_item), "dev.afwall.special.tether"));
				//specialData.add(new PackageInfoData(SPECIAL_UID_DNSPROXY, ctx.getString(R.string.dnsproxy_item), "dev.afwall.special.dnsproxy"));
				specialData.add(new PackageInfoData(SPECIAL_UID_NTP, ctx.getString(R.string.ntp_item), "dev.afwall.special.ntp"));
				specialData.add(new PackageInfoData("root", ctx.getString(R.string.root_item), "dev.afwall.special.root"));
				specialData.add(new PackageInfoData("media", "Media server", "dev.afwall.special.media"));
				specialData.add(new PackageInfoData("vpn", "VPN networking", "dev.afwall.special.vpn"));
				specialData.add(new PackageInfoData("shell", "Linux shell", "dev.afwall.special.shell"));
				specialData.add(new PackageInfoData("gps", "GPS", "dev.afwall.special.gps"));
				specialData.add(new PackageInfoData("adb", "ADB (Android Debug Bridge)", "dev.afwall.special.adb"));
				
				if(specialApps == null) {
					specialApps = new HashMap<String, Integer>(); 
				}
				for (int i=0; i<specialData.size(); i++) {
					app = specialData.get(i);
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
						if (enableRoam && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
							app.selected_roam = true;
						}
						if (enableVPN && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
							app.selected_vpn = true;
						}
						if (enableLAN && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
							app.selected_lan = true;
						}
						syncMap.put(app.uid, app);
					}
				}
				
				if (changed) {
					edit.commit();
				}
				/* convert the map into an array */
				applications = new ArrayList<PackageInfoData>();
				for (int i = 0; i < syncMap.size(); i++) {
					applications.add(syncMap.valueAt(i));
				}
				
				return applications;
			} catch (Exception e) {
				alert(ctx, ctx.getString(R.string.error_common) + e);
			}
			return null;
	}
	
	private static List<Integer> getListFromPref(String savedPkg_uid) {
		final StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
		List<Integer> listUids = new ArrayList<Integer>();
		while(tok.hasMoreTokens()){
			final String uid = tok.nextToken();
			if (!uid.equals("")) {
				try {
					listUids.add(Integer.parseInt(uid));
				} catch (Exception ex) {

				}
			}
		}
		// Sort the array to allow using "Arrays.binarySearch" later
		Collections.sort(listUids);	
		return listUids;
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
			final List<String> commands = (List<String>) params[0];
			final StringBuilder res = (StringBuilder) params[1];
			try {
				if (!SU.available())
					return exitCode;
				if (commands != null && commands.size() > 0) {
					List<String> output = SU.run(commands);
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
					res.append("\n" + ex);
			}
			return exitCode;
		}

		
	}
    /**
     * Runs a script as root (multiple commands separated by "\n")
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
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
		} catch (ExecutionException e) {
			Log.e(TAG, "runScript failed: " + e.getLocalizedMessage());
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

	private static boolean migrateSettings(Context ctx, int lastVer, int currentVer) {
		if (lastVer <= 138) {
			// migrate busybox/iptables path settings from <= 1.2.7-BETA
			if (G.bb_path().equals("1")) {
				G.bb_path("system");
			} else if (G.bb_path().equals("2")) {
				G.bb_path("builtin");
			}
			if (G.ip_path().equals("1")) {
				G.ip_path("system");
			} else if (G.ip_path().equals("2")) {
				G.ip_path("auto");
			}
		}
		return true;
	}

	/**
	 * Asserts that the binary files are installed in the cache directory.
	 * @param ctx context
     * @param showErrors indicates if errors should be alerted
	 * @return false if the binary files could not be installed
	 */
	public static boolean assertBinaries(Context ctx, boolean showErrors) {
		int currentVer = -1, lastVer = -1;

		try {
			currentVer = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
			lastVer = G.appVersion();
			if (lastVer == currentVer) {
				return true;
			}
		} catch (NameNotFoundException e) {
			Log.e(TAG, "packageManager can't look up versionCode");
		}

		String abi = Build.CPU_ABI;
		boolean ret;
		if (abi.startsWith("x86")) {
			ret = installBinary(ctx, R.raw.busybox_x86, "busybox") &&
					installBinary(ctx, R.raw.iptables_x86, "iptables") &&
					installBinary(ctx, R.raw.ip6tables_x86, "ip6tables") &&
					installBinary(ctx, R.raw.nflog_x86, "nflog") &&
					installBinary(ctx, R.raw.klogripper_x86,"klogripper");
		} else if (abi.startsWith("mips")) {
			ret = installBinary(ctx, R.raw.busybox_mips, "busybox") &&
					  installBinary(ctx, R.raw.iptables_mips, "iptables") &&
					  installBinary(ctx, R.raw.ip6tables_mips, "ip6tables") &&
					  installBinary(ctx, R.raw.nflog_mips, "nflog") &&
					  installBinary(ctx, R.raw.klogripper_mips,"klogripper");
		} else {
			// default to ARM
			ret = installBinary(ctx, R.raw.busybox_arm, "busybox") &&
					  installBinary(ctx, R.raw.iptables_arm, "iptables") &&
					  installBinary(ctx, R.raw.ip6tables_arm, "ip6tables") &&
					  installBinary(ctx, R.raw.nflog_arm, "nflog") &&
					  installBinary(ctx, R.raw.klogripper_arm,"klogripper");
		}

		// arch-independent scripts
		ret &= installBinary(ctx, R.raw.afwallstart, "afwallstart");
		Log.d(TAG, "binary installation for " + abi + (ret ? " succeeded" : " failed"));

		if (showErrors) {
			if (ret) {
				displayToasts(ctx, R.string.toast_bin_installed, Toast.LENGTH_LONG);
			} else {
				alert(ctx, ctx.getString(R.string.error_binary));
			}
		}

		if (currentVer > 0) {
			if (migrateSettings(ctx, lastVer, currentVer) == false && showErrors) {
				alert(ctx, ctx.getString(R.string.error_migration));
			}
		}

		if (ret == true && currentVer > 0) {
			// this indicates that migration from the old version was successful.
			G.appVersion(currentVer);
		}

		return ret;
	}
	
	public static void displayToasts(Context context, int id, int length) {
		Toast.makeText(context, context.getString(id), length).show();
	}
	
	public static void displayToasts(Context context, String text, int length) {
		Toast.makeText(context, text, length).show();
	}
	
	/**
	 * Check if the firewall is enabled
	 * @param ctx mandatory context
	 * @return boolean
	 */
	public static boolean isEnabled(Context ctx) {
		if (ctx == null) return false;
		boolean flag = ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
		//Log.d(TAG, "Checking for IsEnabled, Flag:" + flag);
		return flag;
	}
	
	/**
	 * Defines if the firewall is enabled and broadcasts the new status
	 * @param ctx mandatory context
	 * @param enabled enabled flag
	 */
	public static void setEnabled(Context ctx, boolean enabled, boolean showErrors) {
		if (ctx == null) return;
		final SharedPreferences prefs = ctx.getSharedPreferences(PREF_FIREWALL_STATUS,Context.MODE_PRIVATE);
		if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
			return;
		}
		rulesUpToDate = false;

		final Editor edit = prefs.edit();
		edit.putBoolean(PREF_ENABLED, enabled);
		if (!edit.commit()) {
			if(showErrors)alert(ctx, ctx.getString(R.string.error_write_pref));
			return;
		}
		
		if(G.activeNotification()) {
			Api.showNotification(Api.isEnabled(ctx),ctx);
		}
		
		/* notify */
		final Intent message = new Intent(Api.STATUS_CHANGED_MSG);
        message.putExtra(Api.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
	}
	
	
	private static boolean removePackageRef(Context ctx, String pkg, int pkgRemoved,Editor editor, String store){
		final StringBuilder newuids = new StringBuilder();
		final StringTokenizer tok = new StringTokenizer(pkg, "|");
		boolean changed = false;
		final String uid_str = pkgRemoved + "";
		while (tok.hasMoreTokens()) {
			final String token = tok.nextToken();
			if (uid_str.equals(token)) {
				changed = true;
			} else {
				if (newuids.length() > 0)
					newuids.append('|');
				newuids.append(token);
			}
		}
		if (changed) {
			editor.putString(store, newuids.toString());
		}
		return changed;
	}
	
	/**
	 * Remove the cache.label key from preferences, so that next time the app appears on the top
	 * @param pkgName
	 * @param ctx
	 */
	public static void removeCacheLabel(String pkgName,Context ctx) {
		final SharedPreferences prefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);
		try {
			prefs.edit().remove("cache.label." + pkgName).commit();
		} catch(Exception e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}
	
	/**
	 * Cleansup the uninstalled packages from the cache - will have slight performance
	 * @param ctx
	 */
	@Deprecated
	public static void removeAllUnusedCacheLabel(Context ctx){
		final SharedPreferences prefs = ctx.getSharedPreferences("AFWallPrefs", Context.MODE_PRIVATE);
		final String cacheLabel = "cache.label.";
		String pkgName;
		String cacheKey;
		PackageManager pm = ctx.getPackageManager();
		Map<String,?> keys = prefs.getAll();
		for(Map.Entry<String,?> entry : keys.entrySet()){
			if(entry.getKey().startsWith(cacheLabel)){
				cacheKey = entry.getKey();
				pkgName = entry.getKey().replace(cacheLabel, "");
				if ( prefs.getString(cacheKey, "").length() > 0 && !isPackageExists(pm, pkgName)) {
					prefs.edit().remove(cacheKey).commit();
				}
			}
		 }
	}
	
	/**
	 * Cleanup the cache from profiles - Improve performance.
	 * @param ctx
	 */
	@Deprecated
	public static void removeAllProfileCacheLabel(Context ctx){
		SharedPreferences prefs;
		final String cacheLabel = "cache.label.";
		String cacheKey;
		for(String profileName: G.profiles) {
			prefs = ctx.getSharedPreferences(profileName, Context.MODE_PRIVATE);
			if(prefs != null) {
				Map<String,?> keys = prefs.getAll();
				for(Map.Entry<String,?> entry : keys.entrySet()){
					if(entry.getKey().startsWith(cacheLabel)){
						cacheKey = entry.getKey();
						prefs.edit().remove(cacheKey).commit();
					}
				 }		
			}
		}
	}

	public static boolean isPackageExists(PackageManager pm, String targetPackage) {
		try {
			pm.getPackageInfo(targetPackage,PackageManager.GET_META_DATA);
		} catch (NameNotFoundException e) {
			return false;
		}
		return true;
	}

	/**
	 * Called when an application in removed (un-installed) from the system.
	 * This will look for that application in the selected list and update the persisted values if necessary
	 * @param ctx mandatory app context
	 * @param packageName 
	 * @param uid UID of the application that has been removed
	 */
	public static void applicationRemoved(Context ctx, int pkgRemoved) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		final Editor editor = prefs.edit();
		// allowed application names separated by pipe '|' (persisted)
		String savedPks_wifi = prefs.getString(PREF_WIFI_PKG_UIDS, "");
		String savedPks_3g = prefs.getString(PREF_3G_PKG_UIDS, "");
		String savedPks_roam = prefs.getString(PREF_ROAMING_PKG_UIDS, "");
		String savedPks_vpn = prefs.getString(PREF_VPN_PKG_UIDS, "");
		String savedPks_lan = prefs.getString(PREF_LAN_PKG_UIDS, "");
		boolean wChanged,rChanged,gChanged,vChanged = false;
		// look for the removed application in the "wi-fi" list
		wChanged = removePackageRef(ctx,savedPks_wifi,pkgRemoved, editor,PREF_WIFI_PKG_UIDS); 
		// look for the removed application in the "3g" list
		gChanged = removePackageRef(ctx,savedPks_3g,pkgRemoved, editor,PREF_3G_PKG_UIDS);
		// look for the removed application in roaming list
		rChanged = removePackageRef(ctx,savedPks_roam,pkgRemoved, editor,PREF_ROAMING_PKG_UIDS);
		//  look for the removed application in vpn list
		vChanged = removePackageRef(ctx,savedPks_vpn,pkgRemoved, editor,PREF_VPN_PKG_UIDS);
		//  look for the removed application in lan list
		vChanged = removePackageRef(ctx,savedPks_lan,pkgRemoved, editor,PREF_LAN_PKG_UIDS);
		
		if(wChanged || gChanged || rChanged || vChanged) {
			editor.commit();
			if (isEnabled(ctx)) {
				// .. and also re-apply the rules if the firewall is enabled
				applySavedIptablesRules(ctx, false);
			}
		}
		
	}

    /**
     * Small structure to hold an application info
     */
	public static final class PackageInfoData {
		/** linux user id */
    	public int uid;
    	/** application names belonging to this user id */
    	public List<String> names;
    	/** rules saving & load **/
    	String pkgName; 
    	/** indicates if this application is selected for wifi */
    	boolean selected_wifi;
    	/** indicates if this application is selected for 3g */
    	boolean selected_3g;
    	/** indicates if this application is selected for roam */
    	boolean selected_roam;
    	/** indicates if this application is selected for vpn */
    	boolean selected_vpn;
    	/** indicates if this application is selected for lan */
    	boolean selected_lan;
    	/** toString cache */
    	String tostr;
    	/** application info */
    	ApplicationInfo appinfo;
    	/** cached application icon */
    	Drawable cached_icon;
    	/** indicates if the icon has been loaded already */
    	boolean icon_loaded;
    	/** first time seen? */
    	boolean firstseen;
    	
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
    	
    	/**
    	 * Screen representation of this application
    	 */
    	@Override
    	public String toString() {
    		if (tostr == null) {
        		final StringBuilder s = new StringBuilder();
        		//if (uid > 0) s.append(uid + ": ");
        		for (int i=0; i<names.size(); i++) {
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
        		final StringBuilder s = new StringBuilder();
        		s.append(uid + ": ");
        		for (int i=0; i<names.size(); i++) {
        			if (i != 0) s.append(", ");
        			s.append(names.get(i));
        		}
        		s.append("\n");
        		tostr = s.toString();
    		}
    		return tostr;
    	}
    	
    }
	
	public static void saveSharedPreferencesToFileConfirm(final Context ctx) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setMessage(ctx.getString(R.string.exportConfirm))
		       .setCancelable(false)
		       .setPositiveButton(ctx.getString(R.string.Yes), new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   if(saveSharedPreferencesToFile(ctx)){
		       				Api.alert(ctx, ctx.getString(R.string.export_rules_success) + " " + Environment.getExternalStorageDirectory().getPath() + "/afwall/");
		       			} else {
		       				Api.alert(ctx, ctx.getString(R.string.export_rules_fail) );
		        	   }
		           }
		       })
		       .setNegativeButton(ctx.getString(R.string.No), new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	public static void saveAllPreferencesToFileConfirm(final Context ctx) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setMessage(ctx.getString(R.string.exportConfirm))
		       .setCancelable(false)
		       .setPositiveButton(ctx.getString(R.string.Yes), new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   if(saveAllPreferencesToFile(ctx)){
		       				Api.alert(ctx, ctx.getString(R.string.export_rules_success) + " " + Environment.getExternalStorageDirectory().getPath() + "/afwall/");
		       			} else {
		       				Api.alert(ctx, ctx.getString(R.string.export_rules_fail) );
		        	   }
		           }
		       })
		       .setNegativeButton(ctx.getString(R.string.No), new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	private static void updateExportPackage(Map<String,JSONObject> exportMap, String packageName, int identifier) throws JSONException{
		JSONObject obj;
		if (packageName != null) {
			if (exportMap.containsKey(packageName)) {
				obj = exportMap.get(packageName);
				obj.put(identifier +"", true);
			} else {
				obj = new JSONObject();
				obj.put(identifier +"", true);
				exportMap.put(packageName, obj);
			}
		}

	}
	
	private static void updatePackage(Context ctx,String savedPkg_uid,Map<String,JSONObject> exportMap,int identifier) throws JSONException {
		final StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
		while(tok.hasMoreTokens()){
			final String uid = tok.nextToken();
			if (!uid.equals("")) {
				String packageName = ctx.getPackageManager().getNameForUid(Integer.parseInt(uid));
				updateExportPackage(exportMap,packageName,identifier);
			}
		}
	}
	
	private static Map<String, JSONObject> getCurrentRulesAsMap(Context ctx) {
		final List<PackageInfoData> apps = getApps(ctx,null);
		// Builds a pipe-separated list of names
		Map<String, JSONObject> exportMap = new HashMap<String, JSONObject>();
		try {
			for (int i=0; i<apps.size(); i++) {
				if (apps.get(i).selected_wifi) {
					updateExportPackage(exportMap,apps.get(i).pkgName,WIFI_EXPORT);
				}
				if (apps.get(i).selected_3g) {
					updateExportPackage(exportMap,apps.get(i).pkgName,DATA_EXPORT);
				}
				if (apps.get(i).selected_roam) {
					updateExportPackage(exportMap,apps.get(i).pkgName,ROAM_EXPORT);
				}
				if (apps.get(i).selected_vpn) {
					updateExportPackage(exportMap,apps.get(i).pkgName,VPN_EXPORT);
				}
				if (apps.get(i).selected_lan) {
					updateExportPackage(exportMap,apps.get(i).pkgName,LAN_EXPORT);
				}
			}
		}catch(JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
		return exportMap;
	}
	
	public static boolean saveAllPreferencesToFile(Context ctx) {
	    boolean res = false;
	    File sdCard = Environment.getExternalStorageDirectory();
		if (isExternalStorageWritable()) {
			File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
			dir.mkdirs();
			File file = new File(dir, "backup_all.json");
			
			try {
				FileOutputStream fOut = new FileOutputStream(file);
				OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

				JSONObject exportObject = new JSONObject();
				//if multiprofile is enabled
				if(G.enableMultiProfile()){
					JSONObject profileObject = new JSONObject();
					//store all the profile settings
					for(String profile: G.profiles) {
						Map<String, JSONObject> exportMap = new HashMap<String, JSONObject>();
						final SharedPreferences prefs = ctx.getSharedPreferences(profile, Context.MODE_PRIVATE);
						updatePackage(ctx,prefs.getString(PREF_WIFI_PKG_UIDS, ""),exportMap,WIFI_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_3G_PKG_UIDS, ""),exportMap,DATA_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_ROAMING_PKG_UIDS, ""),exportMap,ROAM_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_VPN_PKG_UIDS, ""),exportMap,VPN_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_LAN_PKG_UIDS, ""),exportMap,LAN_EXPORT);
						profileObject.put(profile, new JSONObject(exportMap));
					}
					exportObject.put("profiles", profileObject);
					
					//if any additional profiles
					int defaultProfileCount = 3;
					JSONObject addProfileObject = new JSONObject();
					for(String profile: G.getAdditionalProfiles()) {
						defaultProfileCount++;
						Map<String, JSONObject> exportMap = new HashMap<String, JSONObject>();
						final SharedPreferences prefs = ctx.getSharedPreferences("AFWallProfile" + defaultProfileCount, Context.MODE_PRIVATE);
						updatePackage(ctx,prefs.getString(PREF_WIFI_PKG_UIDS, ""),exportMap,WIFI_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_3G_PKG_UIDS, ""),exportMap,DATA_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_ROAMING_PKG_UIDS, ""),exportMap,ROAM_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_VPN_PKG_UIDS, ""),exportMap,VPN_EXPORT);
						updatePackage(ctx,prefs.getString(PREF_LAN_PKG_UIDS, ""),exportMap,LAN_EXPORT);
						addProfileObject.put(profile, new JSONObject(exportMap));
					}
					exportObject.put("additional_profiles", addProfileObject);
				} else {
					//default Profile - current one
					JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
					exportObject.put("default", obj);
				}
				
				//now gets all the preferences
				exportObject.put("prefs", getAllAppPreferences(ctx,G.gPrefs));
				
				myOutWriter.append(exportObject.toString());
				res = true;
				myOutWriter.close();
				fOut.close();
			} catch (FileNotFoundException e) {
				Log.d(TAG, e.getLocalizedMessage());
			} catch (IOException e) {
				Log.d(TAG, e.getLocalizedMessage());
			} catch (JSONException e) {
				Log.d(TAG, e.getLocalizedMessage());
			} 
		}
	   
	    return res;
	}
	
	private static JSONArray getAllAppPreferences(Context ctx, SharedPreferences gPrefs) throws JSONException {
		Map<String,?> keys = gPrefs.getAll();
		JSONArray arr = new JSONArray();
		for(Map.Entry<String,?> entry : keys.entrySet()){
			JSONObject obj = new JSONObject();
		    obj.put(entry.getKey(),  entry.getValue().toString());
		    arr.put(obj);
		}
		return arr;
	}

	public static boolean saveSharedPreferencesToFile(Context ctx) {
	    boolean res = false;
	    File sdCard = Environment.getExternalStorageDirectory();
		if (isExternalStorageWritable()) {
			File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
			dir.mkdirs();
			File file = new File(dir, "backup.json");
			try {
				FileOutputStream fOut = new FileOutputStream(file);
				OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);

				//default Profile - current one
				JSONObject obj = new JSONObject(getCurrentRulesAsMap(ctx));
				JSONArray jArray = new JSONArray("[" + obj.toString() + "]");

				myOutWriter.append(jArray.toString());
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
		}
	   
	    return res;
	}
	
	private static boolean importRules(Context ctx,File file, StringBuilder msg) {
		boolean returnVal = false;
		BufferedReader br = null;
		try {
			StringBuilder text = new StringBuilder();
			br = new BufferedReader(new FileReader(file));
			String line;
			while ((line = br.readLine()) != null) {
				text.append(line);
			}
			String data = text.toString();
			JSONArray array = new JSONArray(data);
			updateRulesFromJson(ctx,(JSONObject) array.get(0),PREFS_NAME);
			returnVal = true;
		} catch (FileNotFoundException e) {
			msg.append(ctx.getString(R.string.import_rules_missing));
		} catch (IOException e) {
			Log.e(TAG, e.getLocalizedMessage());
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}  finally {
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
	
	
	private static void updateRulesFromJson(Context ctx,JSONObject object,String preferenceName ) throws JSONException {
		final StringBuilder wifi_uids = new StringBuilder();
		final StringBuilder data_uids = new StringBuilder();
		final StringBuilder roam_uids = new StringBuilder();
		final StringBuilder vpn_uids = new StringBuilder();
		final StringBuilder lan_uids = new StringBuilder();
		
		Map<String,Object> json = JsonHelper.toMap(object);
		final PackageManager pm = ctx.getPackageManager();
		
		for (Map.Entry<String, Object> entry : json.entrySet())
		{
		    String pkgName = entry.getKey();
		    if(pkgName.contains(":")) {
		    	pkgName = pkgName.split(":")[0];
		    }
		    
		    JSONObject jsonObj = (JSONObject) JsonHelper.toJSON(entry.getValue());
		    Iterator<?> keys = jsonObj.keys();
		    while( keys.hasNext() ){
		    	//get wifi/data/lan etc
	            String key = (String)keys.next();
	            switch(Integer.parseInt(key)){
	            case WIFI_EXPORT:
	            	if (wifi_uids.length() != 0) {
	            		wifi_uids.append('|');
	            	}
					if (pkgName.startsWith("dev.afwall.special")) {
						wifi_uids.append(specialApps.get(pkgName));
					} else {
						try {
							wifi_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
						} catch(NameNotFoundException e) {
							
						}
					}
	            	break;
	            case DATA_EXPORT: 
	            	if (data_uids.length() != 0) {
	            		data_uids.append('|');
	            	}
	            	if (pkgName.startsWith("dev.afwall.special")) {
						data_uids.append(specialApps.get(pkgName));
					} else {
						try {
							data_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
						} catch(NameNotFoundException e) {
							
						}
					}
	            	break;
	            case ROAM_EXPORT: 
	            	if (roam_uids.length() != 0) {
	            		roam_uids.append('|');
	            	}
	            	if (pkgName.startsWith("dev.afwall.special")) {
						roam_uids.append(specialApps.get(pkgName));
					} else {
						try{
							roam_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
						} catch(NameNotFoundException e) {
							
						}
					}
	            	break;
	            case VPN_EXPORT:
	            	if (vpn_uids.length() != 0) {
	            		vpn_uids.append('|');
	            	}
	            	if (pkgName.startsWith("dev.afwall.special")) {
						vpn_uids.append(specialApps.get(pkgName));
					} else {
						try {	
							vpn_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
						} catch(NameNotFoundException e) {
						
						}
					}
	            	break;
	            case LAN_EXPORT:
	            	if (lan_uids.length() != 0) {
	            		lan_uids.append('|');
	            	}
	            	if (pkgName.startsWith("dev.afwall.special")) {
						lan_uids.append(specialApps.get(pkgName));
					} else {
						try {	
							lan_uids.append(pm.getApplicationInfo(pkgName, 0).uid);
						} catch(NameNotFoundException e) {
							
						}
					}
	            	break;
	            }
	           
	        }
		}
		final SharedPreferences prefs = ctx.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
		final Editor edit = prefs.edit();
		edit.putString(PREF_WIFI_PKG_UIDS, wifi_uids.toString());
		edit.putString(PREF_3G_PKG_UIDS, data_uids.toString());
		edit.putString(PREF_ROAMING_PKG_UIDS, roam_uids.toString());
		edit.putString(PREF_VPN_PKG_UIDS, vpn_uids.toString());
		edit.putString(PREF_LAN_PKG_UIDS, lan_uids.toString());
		
		edit.commit();
		
	}

	private static boolean importAll(Context ctx,File file, StringBuilder msg) {
		boolean returnVal = false;
		BufferedReader br = null;
		
		try {
			StringBuilder text = new StringBuilder();
			br = new BufferedReader(new FileReader(file));
			String line;
			while ((line = br.readLine()) != null) {
				text.append(line);
			}
			String data = text.toString();
			JSONObject object = new JSONObject(data);
			String[] ignore = { "appVersion", "fixLeak", "enableLogService", "enableLog" };
			List<String> ignoreList = Arrays.asList(ignore);
			JSONArray prefArray = (JSONArray) object.get("prefs");
			for(int i = 0 ; i < prefArray.length(); i++){
				JSONObject prefObj = (JSONObject) prefArray.get(i);
				Iterator<?> keys = prefObj.keys();
				
		        while( keys.hasNext() ){
		            String key = (String)keys.next();
		            String value =  (String) prefObj.get(key);
		            if(!ignoreList.contains(key)) {
		            	//boolean type values
		            	if(value.equals("true") || value.equals("false")) {
		            		G.gPrefs.edit().putBoolean(key, Boolean.parseBoolean(value)).commit();
		            	} else {
		            		try {
		            			//handle Long
		            			if(key.equals("multiUserId")) {
		            				G.gPrefs.edit().putLong(key, Long.parseLong(value)).commit();
		            			} else if(key.equals("patternMax")) {
		            				G.gPrefs.edit().putString(key, value).commit();
		            			} else {
		            				Integer intValue = Integer.parseInt(value);
		            				G.gPrefs.edit().putInt(key, intValue).commit();
		            			}
		            		} catch(NumberFormatException e){
		            			G.gPrefs.edit().putString(key, value).commit();
		            		}
		            	}
		            }
		        }
			}
			if(G.enableMultiProfile()) {
				JSONObject profileObject = object.getJSONObject("profiles");
				Iterator<?> keys = profileObject.keys();
		        while( keys.hasNext() ){
		        	String key = (String)keys.next();
		        	if(!key.equals(PREFS_NAME)) {
		    			updateRulesFromJson(ctx,profileObject.getJSONObject(key),key);
		        	}
		        }
 		        //handle custom/additional profiles
		        JSONObject customProfileObject = object.getJSONObject("additional_profiles");
				keys = customProfileObject.keys();
		        while( keys.hasNext() ){
		        	String key = (String)keys.next();
	    			updateRulesFromJson(ctx,profileObject.getJSONObject(key),key);
		        }
		        
			} else {
				//now restore the default profile
				JSONObject defaultRules = object.getJSONObject("default");
				updateRulesFromJson(ctx,defaultRules,PREFS_NAME);
			}
			returnVal = true;
		} catch (FileNotFoundException e) {
			msg.append(ctx.getString(R.string.import_rules_missing));
		} catch (IOException e) {
			Log.e(TAG, e.getLocalizedMessage());
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}  finally {
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
	
	@Deprecated
	private static boolean importRulesOld(Context ctx, File file) {
		boolean res = false;
		ObjectInputStream input = null;
		try {
			input = new ObjectInputStream(new FileInputStream(file));
			Editor prefEdit = ctx.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).edit();
			prefEdit.clear();
			Map<String, ?> entries = (Map<String, ?>) input.readObject();
			for (Entry<String, ?> entry : entries.entrySet()) {
				Object v = entry.getValue();
				String key = entry.getKey();
				if (v instanceof Boolean)
					prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
				else if (v instanceof Float)
					prefEdit.putFloat(key, ((Float) v).floatValue());
				else if (v instanceof Integer)
					prefEdit.putInt(key, ((Integer) v).intValue());
				else if (v instanceof Long)
					prefEdit.putLong(key, ((Long) v).longValue());
				else if (v instanceof String)
					prefEdit.putString(key, ((String) v));
			}
			prefEdit.commit();
			res = true;
		} catch (FileNotFoundException e) {
			// alert(ctx, "Missing back.rules file");
			Log.e(TAG, e.getLocalizedMessage());
		} catch (IOException e) {
			// alert(ctx, "Error reading the backup file");
			Log.e(TAG, e.getLocalizedMessage());
		} catch (ClassNotFoundException e) {
			Log.e(TAG, e.getLocalizedMessage());
		} finally {
			try {
				if (input != null) {
					input.close();
				}
			} catch (IOException ex) {
				Log.e(TAG, ex.getLocalizedMessage());
			}
		}
		return res;
	}


	@SuppressWarnings("unchecked")
	public static boolean loadSharedPreferencesFromFile(Context ctx,StringBuilder builder) {
		boolean res = false;
		File sdCard = Environment.getExternalStorageDirectory();
		File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
		dir.mkdirs();
		File file = new File(dir, "backup.json");
		//new format
		if(file.exists()) {
			res = importRules(ctx,file,builder);
		} else {
			file = new File(dir, "backup.rules");
			if(file.exists()) {
				res = importRulesOld(ctx,file);
			} else {
				alert(ctx,ctx.getString(R.string.backup_notexist));
			}
		}
		return res;
	}
	
	@SuppressWarnings("unchecked")
	public static boolean loadAllPreferencesFromFile(Context ctx,StringBuilder builder) {
		boolean res = false;
		File sdCard = Environment.getExternalStorageDirectory();
		File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
		dir.mkdirs();
		File file = new File(dir, "backup_all.json");
		//new format
		if(file.exists()) {
			res = importAll(ctx,file,builder);
		} else {
			file = new File(dir, "backup.rules");
			if(file.exists()) {
				res = importRulesOld(ctx,file);
			} else {
				alert(ctx,ctx.getString(R.string.backup_notexist));
			}
		}
		return res;
	}
	
	public static List<String> interfaceInfo(boolean showMatches) {
		List<String> ret = new ArrayList<String>();

		try {
			for (File f : new File("/sys/class/net").listFiles()) {
				String name = f.getName();

				if (!showMatches) {
					ret.add(name);
				} else {
					if (InterfaceTracker.matchName(InterfaceTracker.ITFS_WIFI, name) != null) {
						ret.add(name + ": wifi");
					} else if (InterfaceTracker.matchName(InterfaceTracker.ITFS_3G, name) != null) {
						ret.add(name + ": 3G");
					} else if (InterfaceTracker.matchName(InterfaceTracker.ITFS_VPN, name) != null) {
						ret.add(name + ": VPN");
					} else {
						ret.add(name + ": unknown");
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "can't list network interfaces: " + e.getLocalizedMessage());
		}
		return ret;
	}
	
	private static class LogProbeCallback extends RootCommand.Callback {
		private Context ctx;

		public void cbFunc(RootCommand state) {
			if (state.exitCode != 0) {
				return;
			}

			boolean hasLOG = false, hasNFLOG = false;
			for(String str : state.res.toString().split("\n")) {
				if (str.equals("LOG")) {
					hasLOG = true;
				} else if (str.equals("NFLOG")) {
					hasNFLOG = true;
				}
			}

			if (hasLOG) {
				G.logTarget("LOG");
				Log.d(TAG, "logging using LOG target");
			} else if (hasNFLOG) {
				G.logTarget("NFLOG");
				Log.d(TAG, "logging using NFLOG target");
			} else {
				Log.e(TAG, "could not find LOG or NFLOG target");
				displayToasts(ctx, R.string.log_target_failed, Toast.LENGTH_SHORT);
				G.logTarget("");
				G.enableLog(false);
				return;
			}

			G.enableLog(true);
			updateLogRules(ctx, new RootCommand()
				.setReopenShell(true)
				.setSuccessToast(R.string.log_was_enabled)
				.setFailureToast(R.string.log_toggle_failed));
		}
	}
	

	public static void setLogging(final Context ctx, boolean isEnabled) {
		if (!isEnabled) {
			// easy case: just disable
			G.enableLog(false);
			G.logTarget("");
			updateLogRules(ctx, new RootCommand()
				.setReopenShell(true)
				.setSuccessToast(R.string.log_was_disabled)
				.setFailureToast(R.string.log_toggle_failed));
			return;
		}
		LogProbeCallback cb = new LogProbeCallback();
		cb.ctx = ctx;
		// probe for LOG/NFLOG targets (unfortunately the file must be read by root)
		//check for ip6 enabled from preference and check against the same
		if(G.enableIPv6()) {
			new RootCommand()
			.setReopenShell(true)
			.setFailureToast(R.string.log_toggle_failed)
			.setCallback(cb)
			.setLogging(true)
			.run(ctx, "cat /proc/net/ip6_tables_targets");
		} else {
			new RootCommand()
			.setReopenShell(true)
			.setFailureToast(R.string.log_toggle_failed)
			.setCallback(cb)
			.setLogging(true)
			.run(ctx, "cat /proc/net/ip_tables_targets");
		}
		
	}
	
	@SuppressLint("InlinedApi")
	public static void showInstalledAppDetails(Context context, String packageName) {
		final String SCHEME = "package";
		final String APP_PKG_NAME_21 = "com.android.settings.ApplicationPkgName";
		final String APP_PKG_NAME_22 = "pkg";
		final String APP_DETAILS_PACKAGE_NAME = "com.android.settings";
		final String APP_DETAILS_CLASS_NAME = "com.android.settings.InstalledAppDetails";

	    Intent intent = new Intent();
	    final int apiLevel = Build.VERSION.SDK_INT;
	    if (apiLevel >= 9) { // above 2.3
	        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
	        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        Uri uri = Uri.fromParts(SCHEME, packageName, null);
	        intent.setData(uri);
	    } else { // below 2.3
	        final String appPkgName = (apiLevel == 8 ? APP_PKG_NAME_22
	                : APP_PKG_NAME_21);
	        intent.setAction(Intent.ACTION_VIEW);
	        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        intent.setClassName(APP_DETAILS_PACKAGE_NAME,
	                APP_DETAILS_CLASS_NAME);
	        intent.putExtra(appPkgName, packageName);
	    }
	    context.startActivity(intent);
	}
	
	public static void showAlertDialogActivity(Context ctx,String title, String message) {
		Intent dialog = new Intent(ctx,AlertDialogActivity.class);
		dialog.putExtra("title", title);
		dialog.putExtra("message", message);
		dialog.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		ctx.startActivity(dialog);
	}
	
	public static boolean isNetfilterSupported() {
        if ((new File("/proc/config.gz")).exists() == false) {
                if ((new File("/proc/net/netfilter")).exists() == false)
                        return false;
                if ((new File("/proc/net/ip_tables_targets")).exists() == false) 
                        return false;
        }
        return true;
    }
	
	private static void initSpecial() {
		if(specialApps == null || specialApps.size() == 0){
			specialApps = new HashMap<String, Integer>();
			specialApps.put("dev.afwall.special.any",SPECIAL_UID_ANY);
			specialApps.put("dev.afwall.special.kernel",SPECIAL_UID_KERNEL);
			specialApps.put("dev.afwall.special.tether",SPECIAL_UID_TETHER);
			//specialApps.put("dev.afwall.special.dnsproxy",SPECIAL_UID_DNSPROXY);
			specialApps.put("dev.afwall.special.ntp",SPECIAL_UID_NTP);
			specialApps.put("dev.afwall.special.root",android.os.Process.getUidForName("root"));
			specialApps.put("dev.afwall.special.media",android.os.Process.getUidForName("media"));
			specialApps.put("dev.afwall.special.vpn",android.os.Process.getUidForName("vpn"));
			specialApps.put("dev.afwall.special.shell",android.os.Process.getUidForName("shell"));
			specialApps.put("dev.afwall.special.gps",android.os.Process.getUidForName("gps"));
			specialApps.put("dev.afwall.special.adb",android.os.Process.getUidForName("adb"));	
		}
	}
	
	public static void updateLanguage(Context context, String lang) {
	    if (!"".equals(lang)) {
	        Locale locale = new Locale(lang);
	        Resources res = context.getResources();
			DisplayMetrics dm = res.getDisplayMetrics();
			Configuration conf = res.getConfiguration();
			conf.locale = locale;
			res.updateConfiguration(conf, dm);
	    }
	}
	
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1) public static void setUserOwner(Context context)
	{
		if(supportsMultipleUsers(context)){
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				try
			    {
			        Method getUserHandle = UserManager.class.getMethod("getUserHandle");
			        int userHandle = (Integer) getUserHandle.invoke(context.getSystemService(Context.USER_SERVICE));
			        G.setMultiUserId(userHandle);
			    }
			    catch (Exception ex)
			    {
			    	Log.e(TAG,"Exception on setUserOwner " + ex.getMessage());
			    }
			}
		}
	}
	
	@SuppressLint("NewApi")
    public static boolean supportsMultipleUsers(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			final UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
			try {
				Method supportsMultipleUsers = UserManager.class.getMethod("supportsMultipleUsers");
				return (Boolean)supportsMultipleUsers.invoke(um);
			}
			catch (Exception ex) {
				return false;
			}
		}
		return false;
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
					inputStream, "UTF-8"));
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
	
	
	/* Checks if external storage is available for read and write */
	public static boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}
	
	/* Checks if external storage is available to at least read */
	public static boolean isExternalStorageReadable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)
				|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true;
		}
		return false;
	}
	

	/**
	 * Encrypt the password
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
	
	public static void killLogProcess(final Context ctx){
		Thread thread = new Thread(){
		    @Override
		    public void run() {
		    	try {
		    		new RootCommand().run(ctx, Api.getBusyBoxPath(ctx) + " pkill " + "klogripper");
		    	}catch(Exception e) {
		    		Log.e(TAG,e.getMessage());
		    	}
		    }
		};
		thread.start();
	}

    public static boolean isMobileNetworkSupported(final Context ctx) {
    	boolean hasMobileData = true;
    	ConnectivityManager cm = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    	if (cm != null) {
    		if (cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
    			hasMobileData = false;
    		}
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
    
    
    public static void showNotification(boolean status, Context context) {
    	final int NOTIF_ID = 33341;
    	String notificationText = "";
    	NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
		
		Intent appIntent = new Intent(context, MainActivity.class);
		PendingIntent in = PendingIntent.getActivity(context, 0, appIntent, 0);
		int icon = R.drawable.widget_on;
		
		if(status) { 
			notificationText = context.getString(R.string.active);
			icon = R.drawable.widget_on;
		} else {
			notificationText = context.getString(R.string.inactive);
			icon = R.drawable.widget_off;
		}
		
		builder.setSmallIcon(icon).setOngoing(true)
		       .setAutoCancel(false)
		       .setContentTitle(context.getString(R.string.app_name))
		       .setTicker(context.getString(R.string.app_name))
		       .setContentText(notificationText);
		
		builder.setContentIntent(in);
		
		mNotificationManager.notify(NOTIF_ID, builder.build());
    	
    }
}
