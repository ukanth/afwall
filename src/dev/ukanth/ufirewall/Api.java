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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;
import dev.ukanth.ufirewall.MainActivity.GetAppList;
import dev.ukanth.ufirewall.RootShell.RootCommand;
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

	private static final String allChains[] = { "afwall", "afwall-3g", "afwall-wifi", "afwall-lan", "afwall-vpn", "afwall-reject" };

	// Cached applications
	public static List<PackageInfoData> applications = null;
	
	//for custom scripts
	static Hashtable<String, LogEntry> logEntriesHash = new Hashtable<String, LogEntry>();
    static List<LogEntry> logEntriesList = new ArrayList<LogEntry>();
	public static String ipPath = null;
	public static boolean setv6 = false;
	private static Map<String,Integer> specialApps = null;

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
		final String myiptables = dir + "/iptables_armv5";
		final String mybusybox = dir + "/busybox_g2";
		return "" +
			"IPTABLES="+ myiptables + "\n" +
			"BUSYBOX="+mybusybox+"\n" +
			"";
	}
	
	static void setIpTablePath(Context ctx,boolean setv6) {
		final String dir = ctx.getDir("bin", 0).getAbsolutePath();
		final String defaultPath = "iptables ";
		final String defaultIPv6Path = "ip6tables ";
		final String myiptables = dir + "/iptables_armv5 ";
		final SharedPreferences appprefs = PreferenceManager
				.getDefaultSharedPreferences(ctx);
		final boolean enableIPv6 = appprefs.getBoolean("enableIPv6", false);
		final String ip_preference = appprefs.getString("ip_path", "2");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			Api.ipPath = defaultPath;
		} else {
			if(ip_preference.equals("2")) {
				Api.ipPath = myiptables;	
			} else {
				Api.ipPath = defaultPath;
			}
		}
		if (setv6 && enableIPv6) {
			Api.setv6 = true;
			Api.ipPath = defaultIPv6Path;
		} else {
			Api.setv6 = false;
		}
	}
	
	static String getBusyBoxPath(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		String busybox = "busybox ";
		final SharedPreferences appprefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		final String busybox_preference = appprefs.getString("bb_path", "2");
		if(busybox_preference.equals("2")) {
			busybox = dir + "/busybox_g2 ";
		}
		return busybox;
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
		String action = whitelist ? " -j RETURN" : " -j afwall-reject";

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

			boolean kernel_checked = uids.indexOf(SPECIAL_UID_KERNEL) >= 0;
			if (whitelist) {
				if (kernel_checked) {
					// reject any other UIDs, but allow the kernel through
					cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j afwall-reject");
				} else {
					// kernel is blocked so reject everything
					cmds.add("-A " + chain + " -j afwall-reject");
				}
			} else {
				if (kernel_checked) {
					// allow any other UIDs, but block the kernel
					cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j RETURN");
					cmds.add("-A " + chain + " -j afwall-reject");
				}
			}
		}
	}

	private static void addRejectRules(List<String> cmds) {
		// set up reject chain to log or not log
		// this can be changed dynamically through the Firewall Logs activity
		if (G.enableFirewallLog()) {
			cmds.add("-A afwall-reject -m limit --limit 1000/min -j LOG --log-prefix \"{AFL}\" --log-level 4 --log-uid ");
		}
		cmds.add("-A afwall-reject -j REJECT");
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

		final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx);
		final boolean whitelist = G.pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

		List<String> cmds = new ArrayList<String>();
		cmds.add("-P INPUT ACCEPT");
		cmds.add("-P OUTPUT ACCEPT");
		cmds.add("-P FORWARD ACCEPT");
		for (String s : allChains) {
			cmds.add("#NOCHK# -N " + s);
			cmds.add("-F " + s);
		}
		cmds.add("#NOCHK# -D OUTPUT -j afwall");
		cmds.add("-I OUTPUT 1 -j afwall");
		addRejectRules(cmds);

		// add custom rules before the afwall* chains are populated
		addCustomRules(ctx, Api.PREF_CUSTOMSCRIPT, cmds);

		if (G.enableInbound()) {
			// we don't have any rules in the INPUT chain prohibiting inbound traffic, but
			// local processes can't reply to half-open connections without this rule
			cmds.add("-A afwall -m state --state ESTABLISHED -j RETURN");
		}

		// send 3G, wifi, LAN, VPN packets to the appropriate afwall-* chain
		for (final String itf : ITFS_3G) {
			cmds.add("-A afwall -o " + itf + " -j afwall-3g");
		}
		for (final String itf : ITFS_WIFI) {
			if (G.enableLAN() && !cfg.isTethered) {
				if(setv6 && !cfg.lanMaskV6.equals("")) {
					cmds.add("-A afwall -d " + cfg.lanMaskV6 + " -o " + itf + " -j afwall-lan");
					cmds.add("-A afwall '!' -d " + cfg.lanMaskV6 + " -o " + itf+ " -j afwall-wifi");
				}
				if(!setv6 && !cfg.lanMaskV4.equals("")) {
					cmds.add("-A afwall -d " + cfg.lanMaskV4 + " -o " + itf + " -j afwall-lan");
					cmds.add("-A afwall '!' -d "+ cfg.lanMaskV4 + " -o " + itf + " -j afwall-wifi");
				}
			} else {
				cmds.add("-A afwall -o " + itf + " -j afwall-wifi");
			}
		}
		if (G.enableVPN()) {
			for (final String itf : ITFS_VPN) {
				cmds.add("-A afwall -o " + itf + " -j afwall-vpn");
			}
		}

		final boolean any_wifi = uidsWifi.indexOf(SPECIAL_UID_ANY) >= 0;
		final boolean any_3g = uids3g.indexOf(SPECIAL_UID_ANY) >= 0;

		// special rule to allow DHCP requests out on wifi
		if (whitelist && !any_wifi) {
			addRuleForUsers(cmds, new String[]{"dhcp", "wifi"}, "-A afwall-wifi", "-j RETURN");
		}

		// special rules to allow 3G<->wifi tethering
		// note that this can only blacklist DNS/DHCP services, not all tethered traffic
		if (cfg.isTethered &&
			((!whitelist && (any_wifi || any_3g)) ||
		     (uids3g.indexOf(SPECIAL_UID_TETHER) >= 0) || (uidsWifi.indexOf(SPECIAL_UID_TETHER) >= 0))) {

			String users[] = { "root", "nobody" };
			String action = " -j " + (whitelist ? "RETURN" : "afwall-reject");

			// DHCP replies to client
			addRuleForUsers(cmds, users, "-A afwall-wifi","-p udp --sport=67 --dport=68" + action);

			// DNS replies to client
			addRuleForUsers(cmds, users, "-A afwall-wifi", "-p udp --sport=53" + action);
			addRuleForUsers(cmds, users, "-A afwall-wifi", "-p tcp --sport=53" + action);

			// DNS requests to upstream servers
			addRuleForUsers(cmds, users, "-A afwall-3g", "-p udp --dport=53" + action);
			addRuleForUsers(cmds, users, "-A afwall-3g", "-p tcp --dport=53" + action);
		}

		// now add the per-uid rules for each interface type
		addRulesForUidlist(cmds, cfg.isRoaming && G.enableRoam() ? uidsRoam : uids3g, "afwall-3g", whitelist);
		addRulesForUidlist(cmds, uidsWifi, "afwall-wifi", whitelist);
		addRulesForUidlist(cmds, uidsVPN, "afwall-vpn", whitelist);

		if (cfg.allowWifi) {
			// NOTE: we still need to open a hole for DNS lookups
			// This falls through to the wifi rules if the app is not whitelisted for LAN access
			if (whitelist) {
				cmds.add("-A afwall-lan -p udp --dport 53 -j RETURN");
			}
			addRulesForUidlist(cmds, uidsLAN, "afwall-lan", whitelist);
		} else {
			// no LAN IP -> block everything
			cmds.add("-A afwall-lan -j afwall-reject");
		}

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
		for (String s : in) {
			if (s.matches("#LITERAL# .*")) {
				out.add(s.replaceFirst("^#LITERAL# ", "").replaceAll("\\$IPTABLES", ipPath));
			} else if (s.matches("#NOCHK# .*")) {
					out.add(s.replaceFirst("^#NOCHK# ", "#NOCHK# " + ipPath));
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

		if (callback != null) {
			callback.run(ctx, cmds);
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

	/**
	 * Save current rules using the preferences storage.
	 * @param ctx application context (mandatory)
	 */
	public static void saveRules(Context ctx) {
		final SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

		final boolean enableVPN = defaultPrefs.getBoolean("enableVPN", false);
		final boolean enableLAN = defaultPrefs.getBoolean("enableLAN", false);
		final boolean enableRoam = defaultPrefs.getBoolean("enableRoam", true);

		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		final List<PackageInfoData> apps = getApps(ctx,null);
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

		for (String s : allChains) {
			cmds.add("-F " + s);
		}
		cmds.add("-D OUTPUT -j afwall");
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
				callback.run(ctx, out);
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
		callback.run(ctx, out);
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
		List<String> cmds = new ArrayList<String>();
		cmds.add("-F afwall-reject");
		addRejectRules(cmds);
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
	 */
	public static void fetchDmesg(Context ctx, RootCommand callback) {
		callback.run(ctx, getBusyBoxPath(ctx) + " dmesg");
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
	
	public static class LogEntry {
		    String uid;
		    String src;
		    String dst;
		    int len;
		    int spt;
		    int dpt;
		    int packets;
		    int bytes;
		    
		    @Override
		    public String toString() {
				return dst + ":" + src+ ":" + len + ":"+ packets;
		    	
		    }
	}

	public static String parseLog(Context ctx, String dmesg) {
		final BufferedReader r = new BufferedReader(new StringReader(dmesg.toString()));
		final Integer unknownUID = -99;
		StringBuilder res = new StringBuilder();
		String line;
		int start, end;
		Integer appid;
		final SparseArray<LogInfo> map = new SparseArray<LogInfo>();
		LogInfo loginfo = null;

		try {
			while ((line = r.readLine()) != null) {
				if (line.indexOf("{AFL}") == -1) continue;
				appid = unknownUID;
				if (((start=line.indexOf("UID=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					appid = Integer.parseInt(line.substring(start+4, end));
				}
				loginfo = map.get(appid);
				if (loginfo == null) {
					loginfo = new LogInfo();
					map.put(appid, loginfo);
				}
				loginfo.totalBlocked += 1;
				if (((start=line.indexOf("DST=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					String dst = line.substring(start+4, end);
					if (loginfo.dstBlocked.containsKey(dst)) {
						loginfo.dstBlocked.put(dst, loginfo.dstBlocked.get(dst) + 1);
					} else {
						loginfo.dstBlocked.put(dst, 1);
					}
				}
			}
			final List<PackageInfoData> apps = getApps(ctx,null);
			Integer id;
			String appName = "";
			int appId = -1;
			int totalBlocked;
			for(int i = 0; i < map.size(); i++) {
				StringBuilder address = new StringBuilder();
				   id = map.keyAt(i);
				   if (id != unknownUID) {
						for (PackageInfoData app : apps) {
							if (app.uid == id) {
								appId = id;
								appName = app.names.get(0);
								break;
							}
						}
					} else {
						appName = "Kernel";
					}
				   loginfo = map.valueAt(i);
				   totalBlocked = loginfo.totalBlocked;
					if (loginfo.dstBlocked.size() > 0) {
						for (String dst : loginfo.dstBlocked.keySet()) {
							address.append( dst + "(" + loginfo.dstBlocked.get(dst) + ")");
							address.append("\n");
						}
					}
					res.append("AppID :\t" +  appId + "\n"  + ctx.getString(R.string.LogAppName) +":\t" + appName + "\n" 
					+ ctx.getString(R.string.LogPackBlock) + ":\t" +  totalBlocked  + "\n");
					res.append(address.toString());
					res.append("\n\t---------\n");
				}
		} catch (Exception e) {
			return null;
		}
		if (res.length() == 0) {
			res.append(ctx.getString(R.string.no_log));
		}
		return res.toString();
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
		
		// allowed application names separated by pipe '|' (persisted)
		final String savedPkg_wifi = prefs.getString(PREF_WIFI_PKG, "");
		final String savedPkg_3g = prefs.getString(PREF_3G_PKG, "");
		final String savedPkg_roam = prefs.getString(PREF_ROAMING_PKG, "");
		final String savedPkg_vpn = prefs.getString(PREF_VPN_PKG, "");
		final String savedPkg_lan = prefs.getString(PREF_LAN_PKG, "");
		
		List<Integer> selected_wifi = new ArrayList<Integer>();
		List<Integer> selected_3g = new ArrayList<Integer>();
		List<Integer> selected_roam = new ArrayList<Integer>();
		List<Integer> selected_vpn = new ArrayList<Integer>();
		List<Integer> selected_lan = new ArrayList<Integer>();
		
		
		if (savedPkg_wifi_uid.equals("")) {
			selected_wifi = getUidListFromPref(ctx, savedPkg_wifi);
		} else {
			selected_wifi = getListFromPref(savedPkg_wifi_uid);
		}

		if (savedPkg_3g_uid.equals("")) {
			selected_3g = getUidListFromPref(ctx, savedPkg_3g);
		} else {
			selected_3g = getListFromPref(savedPkg_3g_uid);
		}
		if (enableRoam) {
			if (savedPkg_roam_uid.equals("")) {
				selected_roam = getUidListFromPref(ctx, savedPkg_roam);
			} else {
				selected_roam = getListFromPref(savedPkg_roam_uid);
			}
		}
		if (enableVPN) {
			if (savedPkg_vpn_uid.equals("")) {
				selected_vpn = getUidListFromPref(ctx, savedPkg_vpn);
			} else {
				selected_vpn = getListFromPref(savedPkg_vpn_uid);
			}
		}
		if (enableLAN) {
			if (savedPkg_lan_uid.equals("")) {
				selected_lan = getUidListFromPref(ctx, savedPkg_lan);
			} else {
				selected_lan = getListFromPref(savedPkg_lan_uid);
			}
		}
		
		//revert back to old approach

		int count = 0;
		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(PackageManager.GET_META_DATA);
			SparseArray<PackageInfoData> syncMap = new SparseArray<PackageInfoData>();
			final Editor edit = prefs.edit();
			boolean changed = false;
			String name = null;
			String cachekey = null;
			final String cacheLabel = "cache.label.";
			PackageInfoData app = null;
			
			for (final ApplicationInfo apinfo : installed) {
				count = count+1;
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
			if (changed) {
				edit.commit();
			}
			/* add special applications to the list */

			//initiate special Apps
			
			List<PackageInfoData> specialData = new ArrayList<PackageInfoData>();
			specialData.add(new PackageInfoData(SPECIAL_UID_ANY,ctx.getString(R.string.all_item), "dev.afwall.special.any"));
			specialData.add(new PackageInfoData(SPECIAL_UID_KERNEL,"(Kernel) - Linux kernel", "dev.afwall.special.kernel"));
			specialData.add(new PackageInfoData(SPECIAL_UID_TETHER,"(Tethering) - DHCP+DNS services", "dev.afwall.special.tether"));
			specialData.add(new PackageInfoData("root", ctx.getString(R.string.root_item), "dev.afwall.special.root"));
			specialData.add(new PackageInfoData("media", "Media server", "dev.afwall.special.media"));
			specialData.add(new PackageInfoData("vpn", "VPN networking", "dev.afwall.special.vpn"));
			specialData.add(new PackageInfoData("shell", "Linux shell", "dev.afwall.special.shell"));
			specialData.add(new PackageInfoData("gps", "GPS", "dev.afwall.special.gps"));
			specialData.add(new PackageInfoData("adb", "ADB(Android Debug Bridge)", "dev.afwall.special.adb"));
			
			if(specialApps == null) {
				specialApps = new HashMap<String, Integer>(); 
			}
			for (int i=0; i<specialData.size(); i++) {
				app = specialData.get(i);
				specialApps.put(app.pkgName, app.uid);
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
	/**
	 * Asserts that the binary files are installed in the cache directory.
	 * @param ctx context
     * @param showErrors indicates if errors should be alerted
	 * @return false if the binary files could not be installed
	 */
	public static boolean assertBinaries(Context ctx, boolean showErrors) {
		boolean changed = false;
		try {
			// Check iptables_armv5
			File file = new File(ctx.getDir("bin",0), "iptables_armv5");
			if (!file.exists() || file.length()!=198652) {
				copyRawFile(ctx, R.raw.iptables_armv5, file, "755");
				changed = true;
			}	
			// Check busybox
			file = new File(ctx.getDir("bin",0), "busybox_g2");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.busybox_g2, file, "755");
				changed = true;
			}
			// check script
			file = new File(ctx.getDir("bin",0), "afwallstart");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.afwallstart, file, "755");
				changed = true;
			}
			if (changed && showErrors) {
				displayToasts(ctx, R.string.toast_bin_installed, Toast.LENGTH_LONG);
			}
			
		} catch (Exception e) {
			if (showErrors) alert(ctx, ctx.getString(R.string.error_binary) + e);
			return false;
		}
		return true;
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
		final Editor edit = prefs.edit();
		edit.putBoolean(PREF_ENABLED, enabled);
		if (!edit.commit()) {
			if(showErrors)alert(ctx, ctx.getString(R.string.error_write_pref));
			return;
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
				//Log.d(TAG, "Removing UID " + token + " from the rules list (package removed)!");
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
	 * Called when an application in removed (un-installed) from the system.
	 * This will look for that application in the selected list and update the persisted values if necessary
	 * @param ctx mandatory app context
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
    	int uid;
    	/** application names belonging to this user id */
    	List<String> names;
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
        		if (uid > 0) s.append(uid + ": ");
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
    /**
     * Small internal structure used to hold log information
     */
	private static final class LogInfo {
		private int totalBlocked; // Total number of packets blocked
		private HashMap<String, Integer> dstBlocked; // Number of packets blocked per destination IP address
		private LogInfo() {
			this.dstBlocked = new HashMap<String, Integer>();
		}
	}
	
	public static String runSUCommand(String cmd) throws IOException {
		final StringBuilder res = new StringBuilder();
		Process p  = Runtime.getRuntime().exec(
				new String[] { "su", "-c", cmd });
		BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String tmp;
		while ((tmp = stdout.readLine()) != null) {
			res.append(tmp);
			res.append(",");
		}
		// use inputLine.toString(); here it would have whole source
		stdout.close();
		return res.toString();
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
	
	public static boolean saveSharedPreferencesToFile(Context ctx) {
	    boolean res = false;
	    File sdCard = Environment.getExternalStorageDirectory();
	    File dir = new File (sdCard.getAbsolutePath() + "/afwall/");
	    dir.mkdirs();
	    File file = new File(dir, "backup.rules");
	    
	    
	    ObjectOutputStream output = null;
	    try {
	        output = new ObjectOutputStream(new FileOutputStream(file));
	        saveRules(ctx);
	        SharedPreferences pref = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	        output.writeObject(pref.getAll());
	        res = true;
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }finally {
	        try {
	            if (output != null) {
	                output.flush();
	                output.close();
	            }
	        } catch (IOException ex) {
	            ex.printStackTrace();
	        }
	    }
	    return res;
	}


	@SuppressWarnings("unchecked")
	public static boolean loadSharedPreferencesFromFile(Context ctx) {
		boolean res = false;
		File sdCard = Environment.getExternalStorageDirectory();
		File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
		dir.mkdirs();
		File file = new File(dir, "backup.rules");

		ObjectInputStream input = null;
		try {
			input = new ObjectInputStream(new FileInputStream(file));
			Editor prefEdit = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
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
			//alert(ctx, "Missing back.rules file");
			Log.e(TAG, e.getLocalizedMessage());
		} catch (IOException e) {
			//alert(ctx, "Error reading the backup file");
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

	public static String showIfaces() {
		String output = null;
		try {
			output = runSUCommand("ls /sys/class/net");
		} catch (IOException e1) {
			Log.e(TAG, "IOException: " + e1.getLocalizedMessage());
		}
		if (output != null) {
			output = output.replace(" ", ",");
		}
		return output;
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
	        Uri uri = Uri.fromParts(SCHEME, packageName, null);
	        intent.setData(uri);
	    } else { // below 2.3
	        final String appPkgName = (apiLevel == 8 ? APP_PKG_NAME_22
	                : APP_PKG_NAME_21);
	        intent.setAction(Intent.ACTION_VIEW);
	        intent.setClassName(APP_DETAILS_PACKAGE_NAME,
	                APP_DETAILS_CLASS_NAME);
	        intent.putExtra(appPkgName, packageName);
	    }
	    context.startActivity(intent);
	}
	
	public static boolean hasRootAccess(Context ctx, boolean showErrors) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ctx);
		boolean isRoot = prefs.getBoolean("isRootAvail", false);

		if (!isRoot) {
			try {
				// Run an empty script just to check root access
				int returnCode = new SUCheck().execute(null, null).get();
				if (returnCode == 0) {
					isRoot = true;
					Editor edit = prefs.edit();
					edit.putBoolean("isRootAvail", true);
					edit.commit();
				} else {
					if (showErrors) {
						alert(ctx, ctx.getString(R.string.error_su));
					}		
				}
			} catch (Exception e) {
			}
		}

		return isRoot;
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
			specialApps.put("dev.afwall.special.root",android.os.Process.getUidForName("root"));
			specialApps.put("dev.afwall.special.media",android.os.Process.getUidForName("media"));
			specialApps.put("dev.afwall.special.vpn",android.os.Process.getUidForName("vpn"));
			specialApps.put("dev.afwall.special.shell",android.os.Process.getUidForName("shell"));
			specialApps.put("dev.afwall.special.gps",android.os.Process.getUidForName("gps"));
			specialApps.put("dev.afwall.special.adb",android.os.Process.getUidForName("adb"));	
		}
	}
	
	public static void sendNotification(Context context,String title, String message) {

		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		int icon = R.drawable.notification_icon;
		
		final int HELLO_ID = 24556;

		Intent appIntent = new Intent(context, MainActivity.class);
		PendingIntent in = PendingIntent.getActivity(context, 0, appIntent, 0);
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

		builder.setSmallIcon(icon)
		            .setWhen(System.currentTimeMillis())
		            .setAutoCancel(true)
		            .setContentTitle(title)
		            .setContentText(message);
		
		builder.setContentIntent(in);
		
		mNotificationManager.notify(HELLO_ID, builder.build());

	}
	
	public static void updateLanguage(Context context, String lang) {
	    if (!"".equals(lang)) {
	        Locale locale = new Locale(lang);
	        Locale.setDefault(locale);
	        Configuration config = new Configuration();
	        config.locale = locale;
	        context.getResources().updateConfiguration(config, null);
	    }
	}
	
	
	private static class SUCheck extends AsyncTask<Object, Object, Integer> {
		private int exitCode = -1;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected Integer doInBackground(Object... params) {
			try {
				if (SU.available())
					exitCode = 0;
			} catch (Exception ex) {
			}
			return exitCode;
		}

	}
}
