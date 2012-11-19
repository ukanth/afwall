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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
	
	/** special application UID used to indicate "any application" */
	public static final int SPECIAL_UID_ANY	= -10;
	/** special application UID used to indicate the Linux Kernel */
	public static final int SPECIAL_UID_KERNEL = -11;
	/** root script filename */
	private static final String SCRIPT_FILE = "afwall.sh";
	
	// Preferences
	public static final String PREFS_NAME 			= "AFWallPrefs";
	//public static final String PREF_3G_UIDS		= "AllowedUids3G";
	//public static final String PREF_WIFI_UIDS		= "AllowedUidsWifi";
	//public static final String PREF_ROAMING_UIDS	        = "AllowedUidsRoaming";
	
	//for import/export rules
	public static final String PREF_3G_PKG			= "AllowedPKG3G";
	public static final String PREF_WIFI_PKG		= "AllowedPKGWifi";
	public static final String PREF_ROAMING_PKG		= "AllowedPKGRoaming";
	
	public static final String PREF_PASSWORD 		= "Password";
	public static final String PREF_CUSTOMSCRIPT 	        = "CustomScript";
	public static final String PREF_CUSTOMSCRIPT2 	        = "CustomScript2"; // Executed on shutdown
	public static final String PREF_MODE 			= "BlockMode";
	public static final String PREF_ENABLED			= "Enabled";
	public static final String PREF_LOGENABLED		= "LogEnabled";
	
	// Modes
	public static final String MODE_WHITELIST = "whitelist";
	public static final String MODE_BLACKLIST = "blacklist";
	
	// Messages	
	public static final String STATUS_CHANGED_MSG 	= "dev.ukanth.ufirewall.intent.action.STATUS_CHANGED";
	public static final String TOGGLE_REQUEST_MSG	= "dev.ukanth.ufirewall.intent.action.TOGGLE_REQUEST";
	public static final String CUSTOM_SCRIPT_MSG	= "dev.ukanth.ufirewall.intent.action.CUSTOM_SCRIPT";
	
	// Message extras (parameters)
	public static final String STATUS_EXTRA			= "dev.ukanth.ufirewall.intent.extra.STATUS";
	public static final String SCRIPT_EXTRA			= "dev.ukanth.ufirewall.intent.extra.SCRIPT";
	public static final String SCRIPT2_EXTRA		= "dev.ukanth.ufirewall.intent.extra.SCRIPT2";
	private static final int BUFF_LEN = 0;
	
	// Cached applications
	public static DroidApp applications[] = null;
	// Do we have root access?
	private static boolean hasroot = false;
	
	private static Map<String,Integer> specialApps = new HashMap<String, Integer>();
	
	//public static boolean isUSBEnable = false;

    /**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
	public static void alert(Context ctx, CharSequence msg) {
    	if (ctx != null) {
        	new AlertDialog.Builder(ctx)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(msg)
        	.show();
    	}
    }

	public static boolean isRoaming(Context context) {
		TelephonyManager localTelephonyManager = (TelephonyManager) context
				.getSystemService("phone");
		try {
			return localTelephonyManager.isNetworkRoaming();
		} catch (Exception i) {
			while (true) {
			}
		}
	}
	/**
	 * Create the generic shell script header used to determine which iptables binary to use.
	 * @param ctx context
	 * @return script header
	 */
	private static String scriptHeader(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		final String myiptables = dir + "/iptables_armv5";
		return "" +
			"IPTABLES=iptables\n" +
			"BUSYBOX=busybox\n" +
			"GREP=grep\n" +
			"ECHO=echo\n" +
			"# Try to find busybox\n" +
			"if " + dir + "/busybox_g1 --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX="+dir+"/busybox_g1\n" +
			"	GREP=\"$BUSYBOX grep\"\n" +
			"	ECHO=\"$BUSYBOX echo\"\n" +
			"elif busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=busybox\n" +
			"elif /system/xbin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/xbin/busybox\n" +
			"elif /system/bin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/bin/busybox\n" +
			"fi\n" +
			"# Try to find grep\n" +
			"if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"	if $ECHO 1 | $BUSYBOX grep -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		GREP=\"$BUSYBOX grep\"\n" +
			"	fi\n" +
			"	# Grep is absolutely required\n" +
			"	if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		$ECHO The grep command is required. AFWall+ will not work.\n" +
			"		exit 1\n" +
			"	fi\n" +
			"fi\n" +
			"# Try to find iptables\n" +
			"# Added if iptables binary already in system then use it, if not use implemented one\n" + 
			"if " + myiptables + " --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+myiptables+"\n" +
			"fi\n" +
			"";
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
    /**
     * Purge and re-add all rules (internal implementation).
     * @param ctx application context (mandatory)
     * @param uidsWifi list of selected UIDs for WIFI to allow or disallow (depending on the working mode)
     * @param uids3g list of selected UIDs for 2G/3G to allow or disallow (depending on the working mode)
     * @param showErrors indicates if errors should be alerted
     */
	private static boolean applyIptablesRulesImpl(Context ctx, List<Integer> uidsWifi, List<Integer> uids3g, List<Integer> uidsRoam, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		assertBinaries(ctx, showErrors);
		final String ITFS_WIFI[] = {"eth+", "wlan+", "tiwlan+", "athwlan+", "ra+","wlan0+"};
		SharedPreferences appprefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		boolean disableUSB3gRule = appprefs.getBoolean("disableUSB3gRule", false);
		ArrayList<String> ITFS_3G = new ArrayList<String>();
		ITFS_3G.add("rmnet+");ITFS_3G.add("ppp+");ITFS_3G.add("pdp+");ITFS_3G.add("pnp+");
		ITFS_3G.add("rmnet_sdio+");ITFS_3G.add("uwbr+");ITFS_3G.add("wimax+");ITFS_3G.add("vsnet+");ITFS_3G.add("ccmni+");
		ITFS_3G.add("rmnet1+");ITFS_3G.add("rmnet_sdio1+");ITFS_3G.add("qmi+");ITFS_3G.add("wwan0+");ITFS_3G.add("svnet0+");ITFS_3G.add("rmnet_sdio0+");

		if(!disableUSB3gRule) {
			ITFS_3G.add("usb+");
		}
		
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final boolean whitelist = prefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
		final boolean blacklist = !whitelist;
		final boolean logenabled = ctx.getSharedPreferences(PREFS_NAME, 0).getBoolean(PREF_LOGENABLED, false);
		final String customScript = ctx.getSharedPreferences(Api.PREFS_NAME, 0).getString(Api.PREF_CUSTOMSCRIPT, "");

    	final StringBuilder script = new StringBuilder();
		try {
			int code;
			script.append(scriptHeader(ctx));
			script.append("" +
				"$IPTABLES --version || exit 1\n" +
				"# Create the afwall chains if necessary\n" +
				"$IPTABLES -L afwall >/dev/null 2>/dev/null || $IPTABLES --new afwall || exit 2\n" +
				"$IPTABLES -L afwall-3g >/dev/null 2>/dev/null || $IPTABLES --new afwall-3g || exit 3\n" +
				"$IPTABLES -L afwall-wifi >/dev/null 2>/dev/null || $IPTABLES --new afwall-wifi || exit 4\n" +
				"$IPTABLES -L afwall-reject >/dev/null 2>/dev/null || $IPTABLES --new afwall-reject || exit 5\n" +
				"# Add afwall chain to OUTPUT chain if necessary\n" +
				"$IPTABLES -L OUTPUT | $GREP -q afwall || $IPTABLES -A OUTPUT -j afwall || exit 6\n" +
				"$IPTABLES -A afwall -p udp --dport 53 -j RETURN || exit 11\n" +
				"$IPTABLES -A afwall -m owner --uid-owner 0 -p udp --dport 53 -j RETURN || exit 11\n" +
				"# Flush existing rules\n" +
				"$IPTABLES -F afwall || exit 7\n" +
				"$IPTABLES -F afwall-3g || exit 8\n" +
				"$IPTABLES -F afwall-wifi || exit 9\n" +
				"$IPTABLES -F afwall-reject || exit 10\n" +
			"");
			// Check if logging is enabled
			if (logenabled) {
				script.append("" +
					"# Create the log and reject rules (ignore errors on the LOG target just in case it is not available)\n" +
					"$IPTABLES -A afwall-reject -j LOG --log-prefix \"[AFWALL] \" --log-uid\n" +
					"$IPTABLES -A afwall-reject -j REJECT || exit 11\n" +
				"");
			} else {
				script.append("" +
					"# Create the reject rule (log disabled)\n" +
					"$IPTABLES -A afwall-reject -j REJECT || exit 11\n" +
				"");
			}
			if (customScript.length() > 0) {
				script.append("\n# BEGIN OF CUSTOM SCRIPT (user-defined)\n");
				script.append(customScript);
				script.append("\n# END OF CUSTOM SCRIPT (user-defined)\n\n");
			}
			/*if (whitelist && logenabled) {
				script.append("# Allow DNS lookups on white-list for a better logging (ignore errors)\n");
				script.append("$IPTABLES -A afwall -p udp --dport 53 -j RETURN\n");
			}*/
			script.append("# Main rules (per interface)\n");
			for (final String itf : ITFS_3G) {
				script.append("$IPTABLES -A afwall -o ").append(itf).append(" -j afwall-3g || exit\n");
			}
			for (final String itf : ITFS_WIFI) {
				script.append("$IPTABLES -A afwall -o ").append(itf).append(" -j afwall-wifi || exit\n");
			}
			
			script.append("# Filtering rules\n");
			final String targetRule = (whitelist ? "RETURN" : "afwall-reject");
			final boolean any_3g = uids3g.indexOf(SPECIAL_UID_ANY) >= 0;
			final boolean any_wifi = uidsWifi.indexOf(SPECIAL_UID_ANY) >= 0;
			if (whitelist && !any_wifi) {
				// When "white listing" wifi, we need to ensure that the dhcp and wifi users are allowed
				int uid = android.os.Process.getUidForName("dhcp");
				if (uid != -1) {
					script.append("# dhcp user\n");
					script.append("$IPTABLES -A afwall-wifi -m owner --uid-owner ").append(uid).append(" -j RETURN || exit\n");
				}
				uid = android.os.Process.getUidForName("wifi");
				if (uid != -1) {
					script.append("# wifi user\n");
					script.append("$IPTABLES -A afwall-wifi -m owner --uid-owner ").append(uid).append(" -j RETURN || exit\n");
				}
			}
			if (any_3g) {
				if (blacklist) {
					/* block any application on this interface */
					script.append("$IPTABLES -A afwall-3g -j ").append(targetRule).append(" || exit\n");
				}
			} else {
				/* release/block individual applications on this interface */
				if(isRoaming(ctx)) {
					for (final Integer uid : uidsRoam) {
						if (uid >= 0) script.append("$IPTABLES -A afwall-3g -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
					}
					
				} else {
					for (final Integer uid : uids3g) {
						if (uid >= 0) script.append("$IPTABLES -A afwall-3g -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
						//Roaming
						if(isRoaming(ctx)){
							//iptables -A OUTPUT -o pdp0 -j REJECT
						}
					}
				}
			}
			if (any_wifi) {
				if (blacklist) {
					/* block any application on this interface */
					script.append("$IPTABLES -A afwall-wifi -j ").append(targetRule).append(" || exit\n");
				}
			} else {
				/* release/block individual applications on this interface */
				for (final Integer uid : uidsWifi) {
					if (uid >= 0) script.append("$IPTABLES -A afwall-wifi -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
				}
			}
			if (whitelist) {
				if (!any_3g) {
					if (uids3g.indexOf(SPECIAL_UID_KERNEL) >= 0) {
						script.append("# hack to allow kernel packets on white-list\n");
						script.append("$IPTABLES -A afwall-3g -m owner --uid-owner 0:999999999 -j afwall-reject || exit\n");
					} else {
						script.append("$IPTABLES -A afwall-3g -j afwall-reject || exit\n");
					}
				}
				if (!any_wifi) {
					if (uidsWifi.indexOf(SPECIAL_UID_KERNEL) >= 0) {
						script.append("# hack to allow kernel packets on white-list\n");
						script.append("$IPTABLES -A afwall-wifi -m owner --uid-owner 0:999999999 -j afwall-reject || exit\n");
					} else {
						script.append("$IPTABLES -A afwall-wifi -j afwall-reject || exit\n");
					}
				}
			} else {
				if (uids3g.indexOf(SPECIAL_UID_KERNEL) >= 0) {
					script.append("# hack to BLOCK kernel packets on black-list\n");
					script.append("$IPTABLES -A afwall-3g -m owner --uid-owner 0:999999999 -j RETURN || exit\n");
					script.append("$IPTABLES -A afwall-3g -j afwall-reject || exit\n");
				}
				if (uidsWifi.indexOf(SPECIAL_UID_KERNEL) >= 0) {
					script.append("# hack to BLOCK kernel packets on black-list\n");
					script.append("$IPTABLES -A afwall-wifi -m owner --uid-owner 0:999999999 -j RETURN || exit\n");
					script.append("$IPTABLES -A afwall-wifi -j afwall-reject || exit\n");
				}
			}
	    	final StringBuilder res = new StringBuilder();
			code = runScriptAsRoot(ctx, script.toString(), res);
			if (showErrors && code != 0) {
				String msg = res.toString();
				Log.e("AFWall+", msg);
				// Remove unnecessary help message from output
				if (msg.indexOf("\nTry `iptables -h' or 'iptables --help' for more information.") != -1) {
					msg = msg.replace("\nTry `iptables -h' or 'iptables --help' for more information.", "");
				}
				alert(ctx, ctx.getString(R.string.error_apply)  + code + "\n\n" + msg.trim());
			} else {
				return true;
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, ctx.getString(R.string.error_refresh) + e);
		}
		return false;
    }
	
	private static List<Integer> getUidListFromPref(Context ctx,String pks) {
		final PackageManager pm = ctx.getPackageManager();
		ApplicationInfo ai;
		final List<Integer> uids = new LinkedList<Integer>();
		if (pks.length() > 0) {
			// Check which applications are allowed on wifi
			final StringTokenizer tok = new StringTokenizer(pks, "|");
			while (tok.hasMoreTokens()) {
				final String pkgName = tok.nextToken();
				if (!pkgName.equals("")) {
					try {
						// add logic here
						ai = pm.getApplicationInfo(pkgName, 0);
						if(ai != null) {
							uids.add(ai.uid);
						}
					} catch (NameNotFoundException ex) {
						Log.d("AFWALL", "missing pkg::::" + pkgName);
					}  
					catch (Exception ex) {
						Log.d("AFWALL+++", "missing pkg" + pkgName);
					} 
				}
			}
		}
		return uids;
		
	}
	
	private static int[] getUidArraysFromPref(Context ctx,String pks) {
		final PackageManager pm = ctx.getPackageManager();
		ApplicationInfo ai;
		int uids[] = new int[0];
		if (pks.length() > 0) {
			// Check which applications are allowed on wifi
			final StringTokenizer tok = new StringTokenizer(pks, "|");
			
			Log.d("AFWALL", "---" + pks);
			uids = new int[tok.countTokens()];
			for (int i=0; i<uids.length; i++) {
				final String pkgName = tok.nextToken();
				if(pkgName.startsWith("dev.afwall.special")){
					uids[i] = specialApps.get(pkgName);
				}
				try {
					ai = pm.getApplicationInfo( pkgName, 0);
					if(ai != null) {
						try {
							uids[i] = ai.uid;
						} catch (Exception ex) {
							//selected_wifi[i] = -1;
						}
					}
				} catch (NameNotFoundException e) {
					Log.d("AFWALL", "missing pkg" + pkgName);
				}
				
			}
			Arrays.sort(uids);
		}
		return uids;
		
	}
    /**
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
	public static boolean applySavedIptablesRules(Context ctx, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final String savedPkg_wifi = prefs.getString(PREF_WIFI_PKG, "");
		final String savedPkg_3g = prefs.getString(PREF_3G_PKG, "");
		final String savedPkg_roam = prefs.getString(PREF_ROAMING_PKG, "");
		
		return applyIptablesRulesImpl(ctx, getUidListFromPref(ctx,savedPkg_wifi), getUidListFromPref(ctx,savedPkg_3g),  getUidListFromPref(ctx,savedPkg_roam), showErrors);
	}
	
    /**
     * Purge and re-add all rules.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
	public static boolean applyIptablesRules(Context ctx, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		saveRules(ctx);
		return applySavedIptablesRules(ctx, showErrors);
    }
	
	/**
	 * Save current rules using the preferences storage.
	 * @param ctx application context (mandatory)
	 */
	public static void saveRules(Context ctx) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final DroidApp[] apps = getApps(ctx);
		// Builds a pipe-separated list of names
		//final StringBuilder newuids_wifi = new StringBuilder();
		//final StringBuilder newuids_3g = new StringBuilder();
		//final StringBuilder newuids_roam = new StringBuilder();
		
		final StringBuilder newpkg_wifi = new StringBuilder();
		final StringBuilder newpkg_3g = new StringBuilder();
		final StringBuilder newpkg_roam = new StringBuilder();
		
		for (int i=0; i<apps.length; i++) {
			if (apps[i].selected_wifi) {
				if (newpkg_wifi.length() != 0) newpkg_wifi.append('|');
				newpkg_wifi.append(apps[i].pkgName);
				
			}
			if (apps[i].selected_3g) {
				if (newpkg_3g.length() != 0) newpkg_3g.append('|');
				newpkg_3g.append(apps[i].pkgName);
			}
			if (apps[i].selected_roam) {
				if (newpkg_roam.length() != 0) newpkg_roam.append('|');
				newpkg_roam.append(apps[i].pkgName);
			}

		}
		// save the new list of UIDs
		final Editor edit = prefs.edit();
		edit.putString(PREF_WIFI_PKG, newpkg_wifi.toString());
		edit.putString(PREF_3G_PKG, newpkg_3g.toString());
		edit.putString(PREF_ROAMING_PKG, newpkg_roam.toString());
		
		edit.commit();
    }
    
    /**
     * Purge all iptables rules.
     * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
     * @return true if the rules were purged
     */
	public static boolean purgeIptables(Context ctx, boolean showErrors) {
    	final StringBuilder res = new StringBuilder();
		try {
			assertBinaries(ctx, showErrors);
			// Custom "shutdown" script
			final String customScript = ctx.getSharedPreferences(Api.PREFS_NAME, 0).getString(Api.PREF_CUSTOMSCRIPT2, "");
	    	final StringBuilder script = new StringBuilder();
	    	script.append(scriptHeader(ctx));
	    	script.append("" +
					"$IPTABLES -F afwall\n" +
					"$IPTABLES -F afwall-reject\n" +
					"$IPTABLES -F afwall-3g\n" +
					"$IPTABLES -F afwall-wifi\n" +
	    			"");
	    	if (customScript.length() > 0) {
				script.append("\n# BEGIN OF CUSTOM SCRIPT (user-defined)\n");
				script.append(customScript);
				script.append("\n# END OF CUSTOM SCRIPT (user-defined)\n\n");
	    	}
			int code = runScriptAsRoot(ctx, script.toString(), res);
			if (code == -1) {
				if (showErrors) alert(ctx, "Error purging iptables. exit code: " + code + "\n" + res);
				return false;
			}
			return true;
		} catch (Exception e) {
			if (showErrors) alert(ctx, "Error purging iptables: " + e);
			return false;
		}
    }
	
	/**
	 * Display iptables rules output
	 * @param ctx application context
	 */
	public static String showIptablesRules(Context ctx) {
		try {
    		final StringBuilder res = new StringBuilder();
			runScriptAsRoot(ctx, scriptHeader(ctx) +
								 "$ECHO $IPTABLES\n" +
								 "$IPTABLES -L -v -n\n", res);
			return res.toString();
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return "";
	}

	/**
	 * Display logs
	 * @param ctx application context
     * @return true if the clogs were cleared
	 */
	public static boolean clearLog(Context ctx) {
		try {
			final StringBuilder res = new StringBuilder();
			int code = runScriptAsRoot(ctx, "dmesg -c >/dev/null || exit\n", res);
			if (code != 0) {
				alert(ctx, res);
				return false;
			}
			return true;
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return false;
	}
	/**
	 * Display logs
	 * @param ctx application context
	 */
	public static String showLog(Context ctx) {
		try {
    		StringBuilder res = new StringBuilder();
			int code = runScriptAsRoot(ctx, scriptHeader(ctx) +
					"dmesg | $GREP AFWALL\n", res);
			if (code != 0) {
				if (res.length() == 0) {
					res.append("Log is empty");
				}
				//alert(ctx, res);
				return res.toString();
			}
			final BufferedReader r = new BufferedReader(new StringReader(res.toString()));
			final Integer unknownUID = -99;
			res = new StringBuilder();
			String line;
			int start, end;
			Integer appid;
			final SparseArray<LogInfo> map = new SparseArray<LogInfo>();
			LogInfo loginfo = null;
			while ((line = r.readLine()) != null) {
				if (line.indexOf("[AFWALL]") == -1) continue;
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
			final DroidApp[] apps = getApps(ctx);
			Integer id;
			for(int i = 0; i < map.size(); i++) {
				   id = map.keyAt(i);
				   res.append("App ID ");
				   if (id != unknownUID) {
						res.append(id);
						for (DroidApp app : apps) {
							if (app.uid == id) {
								res.append(" (").append(app.names[0]);
								if (app.names.length > 1) {
									res.append(", ...)");
								} else {
									res.append(")");
								}
								break;
							}
						}
					} else {
						res.append("(kernel)");
					}
				   loginfo = map.valueAt(i);
				   res.append(" - Blocked ").append(loginfo.totalBlocked).append(" packets");
					if (loginfo.dstBlocked.size() > 0) {
						res.append(" (");
						boolean first = true;
						for (String dst : loginfo.dstBlocked.keySet()) {
							if (!first) {
								res.append(", ");
							}
							res.append(loginfo.dstBlocked.get(dst)).append(" packets for ").append(dst);
							first = false;
						}
						res.append(")");
					}
					res.append("\n\n");
				}
			if (res.length() == 0) {
				res.append("Log is empty");
			}
			return res.toString();
			//alert(ctx, res);
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return "";
	}

    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
	public static DroidApp[] getApps(Context ctx) {
		initSpecial();
		if (applications != null) {
			// return cached instance
			return applications;
		}
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		// allowed application names separated by pipe '|' (persisted)
		final String savedPkg_wifi = prefs.getString(PREF_WIFI_PKG, "");
		final String savedPkg_3g = prefs.getString(PREF_3G_PKG, "");
		final String savedPkg_roam = prefs.getString(PREF_ROAMING_PKG, "");
		int selected_wifi[] = new int[0];
		int selected_3g[] = new int[0];
		int selected_roam[] = new int[0];
		
		selected_wifi = getUidArraysFromPref(ctx,savedPkg_wifi);
		selected_3g = getUidArraysFromPref(ctx,savedPkg_3g);
		selected_roam = getUidArraysFromPref(ctx,savedPkg_roam);

		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
			/*
				0 - Root
				1000 - System
				1001 - Radio
				1002 - Bluetooth
				1003 - Graphics
				1004 - Input
				1005 - Audio
				1006 - Camera
				1007 - Log
				1008 - Compass
				1009 - Mount
				1010 - Wi-Fi
				1011 - ADB
				1012 - Install
				1013 - Media
				1014 - DHCP
				1015 - External Storage
				1016 - VPN
				1017 - Keystore
				1018 - USB Devices
				1019 - DRM
				1020 - Available
				1021 - GPS
				1022 - deprecated
				1023 - Internal Media Storage
				1024 - MTP USB
				1025 - NFC
				1026 - DRM RPC

			 
			 */
			final HashMap<Integer, DroidApp> map = new HashMap<Integer, DroidApp>();
			final Editor edit = prefs.edit();
			boolean changed = false;
			String name = null;
			String cachekey = null;
			DroidApp app = null;
			for (final ApplicationInfo apinfo : installed) {
				boolean firstseem = false;
				app = map.get(apinfo.uid);
				// filter applications which are not allowed to access the Internet
				if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
					continue;
				}
				// try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
				cachekey = "cache.label."+apinfo.packageName;
				name = prefs.getString(cachekey, "");
				if (name.length() == 0) {
					// get label and put on cache
					name = pkgmanager.getApplicationLabel(apinfo).toString();
					edit.putString(cachekey, name);
					changed = true;
					firstseem = true;
				}
				if (app == null) {
					app = new DroidApp();
					app.uid = apinfo.uid;
					app.names = new String[] { name };
					app.appinfo = apinfo;
					app.pkgName = apinfo.packageName;
					map.put(apinfo.uid, app);
				} else {
					final String newnames[] = new String[app.names.length + 1];
					System.arraycopy(app.names, 0, newnames, 0, app.names.length);
					newnames[app.names.length] = name;
					app.names = newnames;
				}
				app.firstseem = firstseem;
				// check if this application is selected
				if (!app.selected_wifi && Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
					app.selected_wifi = true;
				}
				if (!app.selected_3g && Arrays.binarySearch(selected_3g, app.uid) >= 0) {
					app.selected_3g = true;
				}
				if (!app.selected_roam && Arrays.binarySearch(selected_roam, app.uid) >= 0) {
					app.selected_roam = true;
				}
			}
			if (changed) {
				edit.commit();
			}
			/* add special applications to the list */
			final DroidApp special[] = {
				new DroidApp(SPECIAL_UID_ANY,ctx.getString(R.string.all_item), false, false,false,"dev.afwall.special.any"),
				new DroidApp(SPECIAL_UID_KERNEL,"(Kernel) - Linux kernel", false, false,false,"dev.afwall.special.kernel"),
				new DroidApp(android.os.Process.getUidForName("root"), ctx.getString(R.string.root_item), false, false,false,"dev.afwall.special.root"),
				new DroidApp(android.os.Process.getUidForName("media"), "Media server", false, false,false,"dev.afwall.special.media"),
				new DroidApp(android.os.Process.getUidForName("vpn"), "VPN networking", false, false,false,"dev.afwall.special.vpn"),
				new DroidApp(android.os.Process.getUidForName("shell"), "Linux shell", false, false,false,"dev.afwall.special.shell"),
				new DroidApp(android.os.Process.getUidForName("gps"), "GPS", false, false,false,"dev.afwall.special.gps")
			};
			for (int i=0; i<special.length; i++) {
				app = special[i];
				specialApps = new HashMap<String, Integer>();
				specialApps.put(app.pkgName, app.uid);
				if (app.uid != -1 && !map.containsKey(app.uid)) {
					// check if this application is allowed
					if (Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
						app.selected_wifi = true;
					}
					if (Arrays.binarySearch(selected_3g, app.uid) >= 0) {
						app.selected_3g = true;
					}
					if (Arrays.binarySearch(selected_roam, app.uid) >= 0) {
						app.selected_roam = true;
					}
					map.put(app.uid, app);
				}
			}
			/* convert the map into an array */
			applications = map.values().toArray(new DroidApp[map.size()]);;
			return applications;
		} catch (Exception e) {
			alert(ctx, ctx.getString(R.string.error_common) + e);
		}
		return null;
	}
	private static void initSpecial() {
		specialApps.put("dev.afwall.special.any",SPECIAL_UID_ANY);
		specialApps.put("dev.afwall.special.kernel",SPECIAL_UID_KERNEL);
		specialApps.put("dev.afwall.special.root",android.os.Process.getUidForName("root"));
		specialApps.put("dev.afwall.special.media",android.os.Process.getUidForName("media"));
		specialApps.put("dev.afwall.special.vpn",android.os.Process.getUidForName("vpn"));
		specialApps.put("dev.afwall.special.shell",android.os.Process.getUidForName("shell"));
		specialApps.put("dev.afwall.special.gps",android.os.Process.getUidForName("gps"));
	}

	/**
	 * Check if we have root access
	 * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
	 * @return boolean true if we have root
	 */
	public static boolean hasRootAccess(final Context ctx, boolean showErrors) {
		if (hasroot) return true;
		final StringBuilder res = new StringBuilder();
		try {
			// Run an empty script just to check root access
			if (runScriptAsRoot(ctx, "exit 0", res) == 0) {
				hasroot = true;
				return true;
			}
		} catch (Exception e) {
		}
		if (showErrors) {
			alert(ctx, ctx.getString(R.string.error_su) + res.toString());
		}
		return false;
	}
    /**
     * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScript(Context ctx, String script, StringBuilder res, long timeout, boolean asroot) {
		final File file = new File(ctx.getDir("bin",0), SCRIPT_FILE);
		final ScriptRunner runner = new ScriptRunner(file, script, res, asroot);
		runner.start();
		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {}
		return runner.exitcode;
	}
    /**
     * Runs a script as root (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res, long timeout) {
		return runScript(ctx, script, res, timeout, true);
    }
    /**
     * Runs a script as root (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res) throws IOException {
		return runScriptAsRoot(ctx, script, res, 40000);
	}
    /**
     * Runs a script as a regular user (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScript(Context ctx, String script, StringBuilder res) throws IOException {
		return runScript(ctx, script, res, 40000, false);
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
				copyRawFile(ctx, R.raw.iptables_armv5, file, "700");
				changed = true;
			}
			// Check busybox
			file = new File(ctx.getDir("bin",0), "busybox_g1");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.busybox_g1, file, "700");
				changed = true;
			}
			if (changed) {
				displayToasts(ctx, R.string.toast_bin_installed, Toast.LENGTH_LONG);
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, ctx.getString(R.string.error_binary) + e);
			return false;
		}
		return true;
	}
	
	private static void displayToasts(Context context, int id, int length) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean showToast = prefs.getBoolean("showToast", true);
		if (showToast)
			Toast.makeText(context, id, length).show();
	}
	
	/**
	 * Check if the firewall is enabled
	 * @param ctx mandatory context
	 * @return boolean
	 */
	public static boolean isEnabled(Context ctx) {
		if (ctx == null) return false;
		return ctx.getSharedPreferences(PREFS_NAME, 0).getBoolean(PREF_ENABLED, false);
	}
	
	/**
	 * Defines if the firewall is enabled and broadcasts the new status
	 * @param ctx mandatory context
	 * @param enabled enabled flag
	 */
	public static void setEnabled(Context ctx, boolean enabled) {
		if (ctx == null) return;
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
			return;
		}
		final Editor edit = prefs.edit();
		edit.putBoolean(PREF_ENABLED, enabled);
		if (!edit.commit()) {
			alert(ctx, ctx.getString(R.string.error_write_pref));
			return;
		}
		/* notify */
		final Intent message = new Intent(Api.STATUS_CHANGED_MSG);
        message.putExtra(Api.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
	}
	
	
	private static void removePackageRef(Context ctx, String pkg, String removedUid,Editor editor, String store){
		PackageManager pm = ctx.getPackageManager();
		ApplicationInfo ai = null;
		String aUid = "";
		final StringBuilder newuids = new StringBuilder();
		final StringTokenizer tok = new StringTokenizer(pkg, "|");
		boolean changed = false;
		while (tok.hasMoreTokens()) {
			final String token = tok.nextToken();
			try {
				ai = pm.getApplicationInfo( token, 0);
			} catch (NameNotFoundException e) {
			}
			
			if(ai != null) {
				aUid = ai.uid + "";
			}
			if (removedUid.equals(aUid)) {
				Log.d("AFWall", "Removing UID " + token + " from the rules list (package removed)!");
				changed = true;
			} else {
				if (newuids.length() > 0) newuids.append('|');
				newuids.append(token);
			}
		}
		if(changed) {
			editor.putString(store, newuids.toString());
		}
	}
	/**
	 * Called when an application in removed (un-installed) from the system.
	 * This will look for that application in the selected list and update the persisted values if necessary
	 * @param ctx mandatory app context
	 * @param uid UID of the application that has been removed
	 */
	public static void applicationRemoved(Context ctx, int uid) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final Editor editor = prefs.edit();
		// allowed application names separated by pipe '|' (persisted)
		final String savedPks_wifi = prefs.getString(PREF_WIFI_PKG, "");
		final String savedPks_3g = prefs.getString(PREF_3G_PKG, "");
		final String savedPks_roam = prefs.getString(PREF_ROAMING_PKG, "");
		final String removedUid= uid + "";
		boolean changed = false;
		// look for the removed application in the "wi-fi" list
		removePackageRef(ctx,savedPks_wifi,removedUid,editor,PREF_WIFI_PKG); 
		// look for the removed application in the "3g" list
		removePackageRef(ctx,savedPks_3g,removedUid,editor,PREF_3G_PKG);
		// look for the removed application in roaming list
		removePackageRef(ctx,savedPks_roam,removedUid,editor,PREF_ROAMING_PKG);
		
		// if anything has changed, save the new prefs...
		if (changed) {
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
	public static final class DroidApp {
		/** linux user id */
    	int uid;
    	/** application names belonging to this user id */
    	String names[];
    	/** rules saving & load **/
    	String pkgName; 
    	/** indicates if this application is selected for wifi */
    	boolean selected_wifi;
    	/** indicates if this application is selected for 3g */
    	boolean selected_3g;
    	/** indicates if this application is selected for roam */
    	boolean selected_roam;
    	/** toString cache */
    	String tostr;
    	/** application info */
    	ApplicationInfo appinfo;
    	/** cached application icon */
    	Drawable cached_icon;
    	/** indicates if the icon has been loaded already */
    	boolean icon_loaded;
    	/** first time seem? */
    	boolean firstseem;
    	
    	public DroidApp() {
    	}
    	public DroidApp(int uid, String name, boolean selected_wifi, boolean selected_3g,boolean selected_roam, String pkgNameStr) {
    		this.uid = uid;
    		this.names = new String[] {name};
    		this.selected_wifi = selected_wifi;
    		this.selected_3g = selected_3g;
    		this.selected_roam = selected_roam;
    		this.pkgName = pkgNameStr;
    	}
    	/**
    	 * Screen representation of this application
    	 */
    	@Override
    	public String toString() {
    		if (tostr == null) {
        		final StringBuilder s = new StringBuilder();
        		//if (uid > 0) s.append(uid + ": ");
        		for (int i=0; i<names.length; i++) {
        			if (i != 0) s.append(", ");
        			s.append(names[i]);
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
	/**
	 * Internal thread used to execute scripts (as root or not).
	 */
	private static final class ScriptRunner extends Thread {
		private final File file;
		private final String script;
		private final StringBuilder res;
		private final boolean asroot;
		public int exitcode = -1;
		private Process exec;
		
		/**
		 * Creates a new script runner.
		 * @param file temporary script file
		 * @param script script to run
		 * @param res response output
		 * @param asroot if true, executes the script as root
		 */
		public ScriptRunner(File file, String script, StringBuilder res, boolean asroot) {
			this.file = file;
			this.script = script;
			this.res = res;
			this.asroot = asroot;
		}
		@Override
		public void run() {
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 700 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				if (new File("/system/bin/sh").exists()) {
					out.write("#!/system/bin/sh\n");
				}
				out.write(script);
				if (!script.endsWith("\n")) out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath);
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				final InputStream stdout = exec.getInputStream();
				final InputStream stderr = exec.getErrorStream();
				final byte buf[] = new byte[8192];
				int read = 0;
				while (true) {
					final Process localexec = exec;
					if (localexec == null) break;
					try {
						// get the process exit code - will raise IllegalThreadStateException if still running
						this.exitcode = localexec.exitValue();
					} catch (IllegalThreadStateException ex) {
						// The process is still running
					}
					// Read stdout
					if (stdout.available() > 0) {
						read = stdout.read(buf);
						if (res != null) res.append(new String(buf, 0, read));
					}
					// Read stderr
					if (stderr.available() > 0) {
						read = stderr.read(buf);
						if (res != null) res.append(new String(buf, 0, read));
					}
					if (this.exitcode != -1) {
						// finished
						break;
					}
					// Sleep for the next round
					Thread.sleep(50);
				}
			} catch (InterruptedException ex) {
				if (res != null) res.append("\n" + R.string.operation_timeout);
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
			} finally {
				destroy();
			}
		}
		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null) exec.destroy();
			exec = null;
		}
	}
	
	public static boolean clearRules(Context ctx) throws IOException{
		final StringBuilder res = new StringBuilder();
		StringBuilder script = new StringBuilder();
		script.append(scriptHeader(ctx));
		script.append("$IPTABLES -F\n");
		script.append("$IPTABLES -X\n");
		int code = runScriptAsRoot(ctx, script.toString(), res);
		if (code == -1) {
			alert(ctx, ctx.getString(R.string.error_purge) + code + "\n" + res);
			return false;
		}
		return true;
	}
	
	public static boolean applyRulesBeforeShutdown(Context ctx) {
		final StringBuilder res = new StringBuilder();
		StringBuilder script = new StringBuilder();
		script.append(scriptHeader(ctx));
		script.append("$IPTABLES -F\n");
		script.append("$IPTABLES -X\n");
		script.append("$IPTABLES -P INPUT DROP\n");
		script.append("$IPTABLES -P OUTPUT DROP\n");
		script.append("$IPTABLES -P FORWARD DROP\n");
		int code;
		try {
			code = runScriptAsRoot(ctx, script.toString(), res);
		} catch (IOException e) {
		}
		return true;
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
	        SharedPreferences pref = ctx.getSharedPreferences(PREFS_NAME, 0);
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
			Editor prefEdit = ctx.getSharedPreferences(PREFS_NAME, 0).edit();
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
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if (input != null) {
					input.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return res;
	}
	
	public static String showIfaces() {
		Process p;
		StringBuffer inputLine = new StringBuffer();
		try {

			p = Runtime.getRuntime().exec(
					new String[] { "su", "-c", "ls /sys/class/net" });
			DataInputStream stdout = new DataInputStream(p.getInputStream());
			String tmp;
			while ((tmp = stdout.readLine()) != null) {
				inputLine.append(tmp);
				inputLine.append(",");
			}
			// use inputLine.toString(); here it would have whole source
			stdout.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return inputLine.toString();
	}
	
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
}
