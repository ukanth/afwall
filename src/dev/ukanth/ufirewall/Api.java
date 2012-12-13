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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.widget.Toast;

import com.devspark.appmsg.AppMsg;

import eu.chainfire.libsuperuser.Shell;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
	/** application version string */
	
	/** special application UID used to indicate "any application" */
	public static final int SPECIAL_UID_ANY	= -10;
	/** special application UID used to indicate the Linux Kernel */
	public static final int SPECIAL_UID_KERNEL	= -11;
	/** root script filename */
	//private static final String SCRIPT_FILE = "afwall.sh";
	
	// Preferences
	public static String PREFS_NAME 			= "AFWallPrefs";
	public static String PREF_FIREWALL_STATUS 	= "AFWallStaus";
	public static final String DEFAULT_PREFS_NAME 			= "AFWallPrefs";
	
	//for import/export rules
	public static final String PREF_3G_PKG			= "AllowedPKG3G";
	public static final String PREF_WIFI_PKG		= "AllowedPKGWifi";
	public static final String PREF_ROAMING_PKG		= "AllowedPKGRoaming";
	
	public static final String PREF_PASSWORD 		= "Password";
	public static final String PREF_CUSTOMSCRIPT 	= "CustomScript";
	public static final String PREF_CUSTOMSCRIPT2 	= "CustomScript2"; // Executed on shutdown
	public static final String PREF_MODE 			= "BlockMode";
	public static final String PREF_ENABLED			= "Enabled";
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
	//private static final int BUFF_LEN = 0;
	
	// Cached applications
	public static DroidApp applications[] = null;

	static enum TOASTTYPE {
		ERROR, INFO, MESSAGE
	}
	//for custom scripts
	//private static final String SCRIPT_FILE = "afwall_custom.sh";
	static Hashtable<String, LogEntry> logEntriesHash = new Hashtable<String, LogEntry>();
    static ArrayList<LogEntry> logEntriesList = new ArrayList<LogEntry>();

	
	public static String ipPath = null;
	
	private static Map<String,Integer> specialApps = new HashMap<String, Integer>();
	
	private static boolean isRooted = false;
	
	//public static boolean isUSBEnable = false;

    public static String getIpPath() {
		return ipPath;
	}

	public static void setIpPath(String ipPath) {
		Api.ipPath = ipPath;
	}

	/**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
	public static void alert(Context ctx, CharSequence msgText, TOASTTYPE error) {
		
		if (ctx != null) {
			SharedPreferences prefs =PreferenceManager
					.getDefaultSharedPreferences(ctx) ;
			boolean showToast = prefs.getBoolean("showToast", false);
			if(showToast){
				final AppMsg.Style style;
				switch (error) {
				case ERROR:
					style = AppMsg.STYLE_ALERT;
					break;
				case MESSAGE:
					style = AppMsg.STYLE_CONFIRM;
					break;
				case INFO:
					style = AppMsg.STYLE_INFO;
					break;
				default:
					return;
				}
				if(ctx instanceof Activity){
					AppMsg msg = AppMsg.makeText((Activity) ctx, msgText,
							style);
					msg.setLayoutGravity(Gravity.BOTTOM);
					msg.setDuration(AppMsg.LENGTH_SHORT);
					msg.show();	
				} else {
					new AlertDialog.Builder(ctx)
		        	.setNeutralButton(android.R.string.ok, null)
		        	.setMessage(msgText)
		        	.show();
				}
			} else{
				new AlertDialog.Builder(ctx)
	        	.setNeutralButton(android.R.string.ok, null)
	        	.setMessage(msgText)
	        	.show();
			}
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
	
	static String customScriptHeader(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		final String myiptables = dir + "/iptables_armv5";
		final String mybusybox = dir + "/busybox_g1";
		return "" +
			"IPTABLES="+ myiptables + "\n" +
			"BUSYBOX="+mybusybox+"\n" +
			"";
	}
	static String scriptHeader(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		final String myiptables = dir + "/iptables_armv5";
		final String mybusybox = dir + "/busybox_g1";
		return "" +
			"IPTABLES="+ myiptables + "\n" +
			"BUSYBOX="+mybusybox+"\n" +
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
			"if " + myiptables + " --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+myiptables+"\n" +
			"fi\n" +
			"";
	}
	
	static void setIpTablePath(Context ctx) {
		if(ipPath == null) {
			SharedPreferences appprefs = PreferenceManager.getDefaultSharedPreferences(ctx);
			final String dir = ctx.getDir("bin",0).getAbsolutePath();
			boolean isaltICSJBenabled =  appprefs.getBoolean("altICSJB", false);
			final String myiptables = dir + "/iptables_armv5 ";
			setIpPath(myiptables);
			
			int sdk = android.os.Build.VERSION.SDK_INT;
			if(isaltICSJBenabled) {
				if(sdk > android.os.Build.VERSION_CODES.HONEYCOMB) {
					String icsJBIptablePath = "/system/bin/iptables ";
					setIpPath(icsJBIptablePath);
				} else {
					Api.displayToasts(ctx,R.string.icsJBOnly,Toast.LENGTH_LONG);
				}
			} 	
		}
	}
	
	static String getBusyBoxPath(Context ctx) {
		final String dir = ctx.getDir("bin",0).getAbsolutePath();
		final String busybox = dir + "/busybox_g1 ";
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
		final String ITFS_WIFI[] = { "eth+", "wlan+", "tiwlan+", "eth0+", "ra+", "wlan0+" };
		SharedPreferences appprefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		boolean disableUSB3gRule = appprefs.getBoolean("disableUSB3gRule", false);
		ArrayList<String> ITFS_3G = new ArrayList<String>();
		//{"ppp+","tap+","tun+"
		ITFS_3G.add("rmnet+");
		ITFS_3G.add("ppp+");
		ITFS_3G.add("pdp+");//ITFS_3G.add("pnp+");
		ITFS_3G.add("rmnet_sdio+");ITFS_3G.add("uwbr+");ITFS_3G.add("wimax+");ITFS_3G.add("vsnet+");ITFS_3G.add("ccmni+");
		ITFS_3G.add("rmnet1+");ITFS_3G.add("rmnet_sdio1+");ITFS_3G.add("qmi+");ITFS_3G.add("wwan0+");ITFS_3G.add("svnet0+");ITFS_3G.add("rmnet_sdio0+");
		ITFS_3G.add("cdma_rmnet+"); ITFS_3G.add("rmnet0+");

		if(!disableUSB3gRule) {
			ITFS_3G.add("usb+");
		}
		
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		final boolean whitelist = prefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
		final boolean blacklist = !whitelist;
		final boolean logenabled = appprefs.getBoolean("enableFirewallLog",false);
		String customScript = ctx.getSharedPreferences(Api.PREFS_NAME, Context.MODE_PRIVATE).getString(Api.PREF_CUSTOMSCRIPT, "");
		boolean isaltICSJBenabled =  appprefs.getBoolean("altICSJB", false);
    	final StringBuilder script = new StringBuilder();
    	final StringBuilder customScriptBuilder = new StringBuilder();
    	
		try {
			int code;
			setIpTablePath(ctx);
			
			String busybox = getBusyBoxPath(ctx);
			String grep = busybox + " grep";
			script.append(
				ipPath + " -L afwall >/dev/null 2>/dev/null || " + ipPath +  " --new afwall || exit 2\n" +
				ipPath + " -L afwall-3g >/dev/null 2>/dev/null ||" + ipPath +  "--new afwall-3g || exit 3\n" +
				ipPath + " -L afwall-wifi >/dev/null 2>/dev/null || " + ipPath +  " --new afwall-wifi || exit 4\n" +
				ipPath + " -L afwall-reject >/dev/null 2>/dev/null || " + ipPath +  " --new afwall-reject || exit 5\n" +
				ipPath + " -L OUTPUT | " + grep + " -q afwall || " + ipPath +  " -A OUTPUT -j afwall || exit 6\n" +
				ipPath + " -F afwall || exit 7\n" +
				ipPath + " -F afwall-3g || exit 8\n" +
				ipPath + " -F afwall-wifi || exit 9\n" +
				ipPath + " -F afwall-reject || exit 10\n" +
				ipPath + " -A afwall -m owner --uid-owner 0 -p udp --dport 53 -j RETURN || exit 11\n"
			);
			
			// Check if logging is enabled
			if (logenabled) {
				script.append(
					ipPath + " -A afwall-reject -j LOG --log-prefix \"[AFWALL] \" --log-uid \n" +
					ipPath + " -A afwall-reject -j REJECT || exit 11\n"
				);
			} else {
				script.append(
					ipPath + " -A afwall-reject -j REJECT || exit 11\n"
				);
			}
			if (customScript.length() > 0) {
				customScript = customScript.replace("$IPTABLES", " "+ ipPath );
				customScriptBuilder.append(customScript);
			}
			
			//workaround for some ICS/JB devices 
			 
			if(isaltICSJBenabled) {
				script.append(
						ipPath + " -D OUTPUT -j afwall\n" +
						ipPath + " -I OUTPUT 2 -j afwall\n"
					);
			}
			
			/*boolean isLocalhost = appprefs.getBoolean("allowOnlyLocalhost",false);
			if(isLocalhost) {
				
				script.append(
						ipPath + " -P INPUT DROP\n" +
						ipPath + " -P OUTPUT ACCEPT\n" +
						ipPath + " -P FORWARD DROP\n" +
						ipPath + " -A INPUT -i lo -j ACCEPT\n" +
						ipPath + " -A OUTPUT -o lo -j ACCEPT\n" +
						ipPath + " -A OUTPUT -p icmp --icmp-type echo-request -j ACCEPT\n" +
						ipPath + " -A INPUT -p icmp --icmp-type echo-reply -j ACCEPT\n");
				
			} else {*/
				for (final String itf : ITFS_3G) {
					script.append(ipPath + " -A afwall -o ").append(itf).append(" -j afwall-3g || exit\n");
				}
				for (final String itf : ITFS_WIFI) {
					script.append(ipPath + " -A afwall -o ").append(itf).append(" -j afwall-wifi || exit\n");
				}
				
				final String targetRule = (whitelist ? "RETURN" : "afwall-reject");
				final boolean any_3g = uids3g.indexOf(SPECIAL_UID_ANY) >= 0;
				final boolean any_wifi = uidsWifi.indexOf(SPECIAL_UID_ANY) >= 0;
				if (whitelist && !any_wifi) {
					// When "white listing" wifi, we need to ensure that the dhcp and wifi users are allowed
					int uid = android.os.Process.getUidForName("dhcp");
					if (uid != -1) {
						script.append(ipPath + " -A afwall-wifi -m owner --uid-owner ").append(uid).append(" -j RETURN || exit\n");
					}
					uid = android.os.Process.getUidForName("wifi");
					if (uid != -1) {
						script.append(ipPath + " -A afwall-wifi -m owner --uid-owner ").append(uid).append(" -j RETURN || exit\n");
					}
				}
				if (any_3g) {
					if (blacklist) {
						/* block any application on this interface */
						script.append(ipPath + " -A afwall-3g -j ").append(targetRule).append(" || exit\n");
					}
				} else {
					/* release/block individual applications on this interface */
					if(isRoaming(ctx)) {
						for (final Integer uid : uidsRoam) {
							if (uid !=null && uid >= 0) script.append(ipPath + " -A afwall-3g -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
						}
						
					} else {
						for (final Integer uid : uids3g) {
							if (uid !=null && uid >= 0) script.append(ipPath + " -A afwall-3g -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
							//Roaming
							//if(isRoaming(ctx)){
								//iptables -A OUTPUT -o pdp0 -j REJECT
							//}
						}
					}
				}
				if (any_wifi) {
					if (blacklist) {
						/* block any application on this interface */
						script.append(ipPath + " -A afwall-wifi -j ").append(targetRule).append(" || exit\n");
					}
				} else {
					/* release/block individual applications on this interface */
					for (final Integer uid : uidsWifi) {
						if (uid !=null && uid >= 0) script.append(ipPath + " -A afwall-wifi -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
					}
				}
				if (whitelist) {
					if (!any_3g) {
						if (uids3g.indexOf(SPECIAL_UID_KERNEL) >= 0) {
							script.append(ipPath + " -A afwall-3g -m owner --uid-owner 0:999999999 -j afwall-reject || exit\n");
						} else {
							script.append(ipPath + " -A afwall-3g -j afwall-reject || exit\n");
						}
					}
					if (!any_wifi) {
						if (uidsWifi.indexOf(SPECIAL_UID_KERNEL) >= 0) {
							script.append(ipPath + " -A afwall-wifi -m owner --uid-owner 0:999999999 -j afwall-reject || exit\n");
						} else {
							script.append(ipPath + " -A afwall-wifi -j afwall-reject || exit\n");
						}
					}
				} else {
					if (uids3g.indexOf(SPECIAL_UID_KERNEL) >= 0) {
						script.append(ipPath + " -A afwall-3g -m owner --uid-owner 0:999999999 -j RETURN || exit\n");
						script.append(ipPath + " -A afwall-3g -j afwall-reject || exit\n");
					}
					if (uidsWifi.indexOf(SPECIAL_UID_KERNEL) >= 0) {
						script.append(ipPath + " -A afwall-wifi -m owner --uid-owner 0:999999999 -j RETURN || exit\n");
						script.append(ipPath + " -A afwall-wifi -j afwall-reject || exit\n");
					}
				}
			
			/*if (whitelist && logenabled) {
				script.append("# Allow DNS lookups on white-list for a better logging (ignore errors)\n");
				script.append(ipPath + " -A afwall -p udp --dport 53 -j RETURN\n");
			}*/
			
	    	final StringBuilder res = new StringBuilder();
			code = runScriptAsRoot(ctx, script.toString(), customScriptBuilder.toString(), res);
			if (showErrors && code != 0) {
				String msg = res.toString();
				Log.e("AFWall+", msg);
				// Remove unnecessary help message from output
				if (msg.indexOf("\nTry `iptables -h' or 'iptables --help' for more information.") != -1) {
					msg = msg.replace("\nTry `iptables -h' or 'iptables --help' for more information.", "");
				}
				alert(ctx, ctx.getString(R.string.error_apply)  + code + "\n\n" + msg.trim() , TOASTTYPE.ERROR);
			} else {
				return true;
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, ctx.getString(R.string.error_refresh) + e, TOASTTYPE.ERROR);
		}
		return false;
    }
	
	private static List<Integer> getUidListFromPref(Context ctx,final String pks) {
		final PackageManager pm = ctx.getPackageManager();
		final List<Integer> uids = new LinkedList<Integer>();
		if (pks.length() > 0) {
			for (String token : pks.split("\\|")) {
				final String pkgName = token;
				if(pkgName != null && pkgName.length() > 0){
					try {
						if(pkgName.startsWith("dev.afwall.special")){
							if(specialApps == null) initSpecial();
							uids.add(specialApps.get(pkgName));
						}
						// add logic here
						ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
						if (ai != null) {
							uids.add(ai.uid);
						}
					} catch (NameNotFoundException ex) {
						Log.d("AFWALL+", "Missing pkg:" + pkgName);
					} catch (Exception ex) {
						Log.d("AFWALL+", "Exception:" + ex);
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
			final StringTokenizer tok = new StringTokenizer(pks, "\\|");
			uids = new int[tok.countTokens()];
			for (int i=0; i<uids.length; i++) {
				final String pkgName = tok.nextToken();
				try {
					if(pkgName != null && pkgName.length() > 0){
						if(pkgName.startsWith("dev.afwall.special")){
							if(specialApps == null) initSpecial();
							uids[i] = specialApps.get(pkgName);
						}
						ai = pm.getApplicationInfo( pkgName, 0);
						if(ai != null) {
							try {
								uids[i] = ai.uid;
							} catch (Exception ex) {
								//selected_wifi[i] = -1;
							}
						}
					}
				} catch (NameNotFoundException e) {
					Log.d("AFWALL", "missing pkg:::" + pkgName);
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
		initSpecial();
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
		initSpecial();
		saveRules(ctx);
		return applySavedIptablesRules(ctx, showErrors);
    }
	
	/**
	 * Save current rules using the preferences storage.
	 * @param ctx application context (mandatory)
	 */
	public static void saveRules(Context ctx) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		final DroidApp[] apps = getApps(ctx);
		// Builds a pipe-separated list of names
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
			String customScript = ctx.getSharedPreferences(Api.PREFS_NAME, Context.MODE_PRIVATE).getString(Api.PREF_CUSTOMSCRIPT2, "");
			final StringBuilder customScriptBuilder = new StringBuilder();
	    	final StringBuilder script = new StringBuilder();
	    	setIpTablePath(ctx);
	    	script.append(
					ipPath + " -F afwall\n" +
					ipPath + " -F afwall-reject\n" +
					ipPath + " -F afwall-3g\n" +
					ipPath + " -F afwall-wifi\n" 
	    			);
	    	if (customScript.length() > 0) {
	    		customScript = customScript.replace("$IPTABLES", " "+ ipPath );
	    		customScriptBuilder.append(customScript);
	    	}
			int code = runScriptAsRoot(ctx, script.toString(), customScript, res);
			if (code == -1) {
				alert(ctx, ctx.getString(R.string.error_purge) + code + "\n" + res, TOASTTYPE.ERROR);
				return false;
			}
			return true;
		} catch (Exception e) {
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
    		setIpTablePath(ctx);
			runScriptAsRoot(ctx, ipPath + " -L -n\n", null,  res);
			return res.toString();
		} catch (Exception e) {
			alert(ctx, "error: " + e, TOASTTYPE.ERROR);
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
			int code = runScriptAsRoot(ctx, "dmesg -c >/dev/null || exit\n", null, res);
			if (code != 0) {
				alert(ctx, res,TOASTTYPE.INFO);
				return false;
			}
			return true;
		} catch (Exception e) {
			alert(ctx, "error: " + e,TOASTTYPE.ERROR);
		}
		return false;
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
	/**
	 * Display logs
	 * @param ctx application context
	 */
	public static String showLog(Context ctx) {
		try {
    		StringBuilder res = new StringBuilder();
    		StringBuilder output = new StringBuilder();
    		String busybox = getBusyBoxPath(ctx);
			String grep = busybox + " grep";
    		
			int code = runScriptAsRoot(ctx, "dmesg | " + grep +" AFWALL\n", null,  res);
			//int code = runScriptAsRoot(ctx, "cat /proc/kmsg", res);
			if (code != 0) {
				if (res.length() == 0) {
					output.append(ctx.getString(R.string.no_log));
				}
				return output.toString();
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
			String appName = "";
			int appId = -1;
			int totalBlocked;
			for(int i = 0; i < map.size(); i++) {
				StringBuilder address = new StringBuilder();
				   id = map.keyAt(i);
				   if (id != unknownUID) {
						for (DroidApp app : apps) {
							if (app.uid == id) {
								appId = id;
								appName = app.names[0];
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
					res.append("AppID :\t" +  appId + "\n"  + "Application Name:\t" + appName + "\n" + "Total Packets Blocked:\t" +  totalBlocked + "\n");
					res.append(address.toString());
					res.append("\n\t---------\n");
				}
			if (res.length() == 0) {
				res.append(ctx.getString(R.string.no_log));
			}
			return res.toString();
			//return output.toString();
			//alert(ctx, res);
		} catch (Exception e) {
			alert(ctx, "error: " + e,TOASTTYPE.ERROR);
		}
		return "";
	}
	
	public static void parseResult(String result) {
	    int pos = 0;
	    String src, dst, len, uid;
	    final BufferedReader r = new BufferedReader(new StringReader(result.toString()));
	    String line;
	    try {
			while ((line = r.readLine()) != null) {
			  int newline = result.indexOf("\n", pos);

			  pos = line.indexOf("SRC=", pos);
			  //if(pos == -1) continue;
			  int space = line.indexOf(" ", pos);
			  //if(space == -1) continue;
			  src = line.substring(pos + 4, space);

			  pos = line.indexOf("DST=", pos);
			  //if(pos == -1) continue;
			  space = line.indexOf(" ", pos);
			 // if(space == -1) continue;
			  dst = line.substring(pos + 4, space);
			  
			  pos = line.indexOf("LEN=", pos);
			  //if(pos == -1) continue;
			  space = line.indexOf(" ", pos);
			  //if(space == -1) continue;
			  len = line.substring(pos + 4, space);
			 
			  pos = line.indexOf("UID=", pos);
			  //if(pos == -1) continue;
			  space = line.indexOf(" ", pos);
			  //if(space == -1) continue;
			  uid = line.substring(pos + 4, space);
			  LogEntry entry = logEntriesHash.get(uid);

			  if(entry == null)
			    entry = new LogEntry();

			  entry.uid = uid;
			  entry.src = src;
			  entry.dst = dst;
			  entry.len = new Integer(len).intValue();
			  entry.packets++;
			  entry.bytes += entry.len * 8;

			  logEntriesHash.put(uid, entry);
			  logEntriesList.add(entry);
			}
		} catch (NumberFormatException e) {
		} catch (IOException e) {
		}
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
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
				new DroidApp(android.os.Process.getUidForName("gps"), "GPS", false, false,false,"dev.afwall.special.gps"),
				new DroidApp(android.os.Process.getUidForName("adb"), "ADB(Android Debug Bridge)", false, false,false,"dev.afwall.special.adb")
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
			alert(ctx, ctx.getString(R.string.error_common) + e, TOASTTYPE.ERROR);
		}
		return null;
	}
	
	private static void initSpecial() {
			specialApps = new HashMap<String, Integer>();
			specialApps.put("dev.afwall.special.any",SPECIAL_UID_ANY);
			specialApps.put("dev.afwall.special.kernel",SPECIAL_UID_KERNEL);
			specialApps.put("dev.afwall.special.root",android.os.Process.getUidForName("root"));
			specialApps.put("dev.afwall.special.media",android.os.Process.getUidForName("media"));
			specialApps.put("dev.afwall.special.vpn",android.os.Process.getUidForName("vpn"));
			specialApps.put("dev.afwall.special.shell",android.os.Process.getUidForName("shell"));
			specialApps.put("dev.afwall.special.gps",android.os.Process.getUidForName("gps"));
			specialApps.put("dev.afwall.special.adb",android.os.Process.getUidForName("adb"));	
	}

	
	private static class RunCommand extends AsyncTask<Object, String, Integer> {

		private int exitCode = -1;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected Integer doInBackground(Object... params) {
			final String script = (String) params[0];
			final StringBuilder res = (StringBuilder) params[1];
			String customScript = (String) params[2];

			try {
				if(!Shell.SU.available()) return exitCode;
				if (script != null && script.length() > 0) {
					List<String> commands = Arrays.asList(script.split("\n"));
					List<String> output = null;
					output = Shell.SU.run(commands);
					if (output != null && output.size() > 0) {
						for (String str : output) {
							res.append(str);
							res.append("\n");
						}
					}
					Log.i("output:::" , res.toString());
					exitCode = 0;
					if (customScript != null && customScript.length() > 0) {
						commands = Arrays.asList(customScript.split("\n"));
						Shell.SU.run(commands);
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
     * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScript(Context ctx, String script, String customScript, StringBuilder res, long timeout, boolean asroot) {
		int returnCode = -1;
		try {
			returnCode = new RunCommand().execute(script, res, customScript)
					.get();
		} catch (RejectedExecutionException r) {
			Log.d("Exception", "Caught RejectedExecutionException");
		} catch (InterruptedException e) {
		} catch (ExecutionException e) {
			Log.d("Exception", "Caught RejectedExecutionException");
		}
		return returnCode;
	}
    /**
     * Runs a script as root (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScriptAsRoot(Context ctx, String script, String customScript,StringBuilder res, long timeout) {
		return runScript(ctx, script, customScript, res, timeout, true);
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
	public static int runScriptAsRoot(Context ctx, String script,  String customScript, StringBuilder res) throws IOException {
		return runScriptAsRoot(ctx, script,customScript, res, 40000);
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
	public static int runScript(Context ctx, String script,String customScript, StringBuilder res) throws IOException {
		return runScript(ctx, script, customScript, res, 40000, false);
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
			file = new File(ctx.getDir("bin",0), "busybox_g1");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.busybox_g1, file, "755");
				changed = true;
			}
			if (changed) {
				displayToasts(ctx, R.string.toast_bin_installed, Toast.LENGTH_LONG);
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, ctx.getString(R.string.error_binary) + e,TOASTTYPE.ERROR);
			return false;
		}
		return true;
	}
	
	public static void displayToasts(Context context, int id, int length) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean showToast = prefs.getBoolean("showToast", false);
		if (showToast){
			AppMsg msg = AppMsg.makeText((Activity)context,id,AppMsg.STYLE_INFO);
			msg.setLayoutGravity(Gravity.BOTTOM);
			msg.setDuration(AppMsg.LENGTH_SHORT);
			msg.show();
		} else {
			Toast.makeText(context, id, length).show();
		}
	}
	
	/**
	 * Check if the firewall is enabled
	 * @param ctx mandatory context
	 * @return boolean
	 */
	public static boolean isEnabled(Context ctx) {
		if (ctx == null) return false;
		return ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
	}
	
	/**
	 * Defines if the firewall is enabled and broadcasts the new status
	 * @param ctx mandatory context
	 * @param enabled enabled flag
	 */
	public static void setEnabled(Context ctx, boolean enabled) {
		if (ctx == null) return;
		final SharedPreferences prefs = ctx.getSharedPreferences(PREF_FIREWALL_STATUS,Context.MODE_PRIVATE);
		if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
			return;
		}
		final Editor edit = prefs.edit();
		edit.putBoolean(PREF_ENABLED, enabled);
		if (!edit.commit()) {
			alert(ctx, ctx.getString(R.string.error_write_pref),TOASTTYPE.ERROR);
			return;
		}
		/* notify */
		final Intent message = new Intent(Api.STATUS_CHANGED_MSG);
        message.putExtra(Api.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
	}
	
	
	private static boolean removePackageRef(Context ctx, String pkg, String pkgRemoved,Editor editor, String store){
		final StringBuilder newuids = new StringBuilder();
		final StringTokenizer tok = new StringTokenizer(pkg, "\\|");
		boolean changed = false;
		while (tok.hasMoreTokens()) {
			final String token = tok.nextToken();
			if (pkgRemoved.equals(token)) {
				Log.d("AFWall", "Removing UID " + token
						+ " from the rules list (package removed)!");
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
	public static void applicationRemoved(Context ctx, String pkgRemoved) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		final Editor editor = prefs.edit();
		// allowed application names separated by pipe '|' (persisted)
		String savedPks_wifi = prefs.getString(PREF_WIFI_PKG, "");
		String savedPks_3g = prefs.getString(PREF_3G_PKG, "");
		String savedPks_roam = prefs.getString(PREF_ROAMING_PKG, "");
		boolean wChanged,rChanged,gChanged = false;
		// look for the removed application in the "wi-fi" list
		wChanged = removePackageRef(ctx,savedPks_wifi,pkgRemoved, editor,PREF_WIFI_PKG); 
		// look for the removed application in the "3g" list
		gChanged = removePackageRef(ctx,savedPks_3g,pkgRemoved, editor,PREF_3G_PKG);
		//// look for the removed application in roaming list
		rChanged = removePackageRef(ctx,savedPks_roam,pkgRemoved, editor,PREF_ROAMING_PKG);
		
		if(wChanged || gChanged || rChanged) {
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
	/*private static final class ScriptRunner extends Thread {
		private final String script;
		private String customScript;
		private final StringBuilder res;
		public int exitcode = -1;
		
		public ScriptRunner(String script, String customScript, StringBuilder res, boolean asroot) {
			this.script = script;
			this.customScript = customScript;
			this.res = res;
		}
		@Override
		public void run() {
			try {
				if(script != null && script.length() > 0) {
					List<String> commands = Arrays.asList(script.split("\n"));
					List<String> output = null;
					output = Shell.SU.run(commands);
					if(output !=null && output.size() > 0) {
						for(String str:output) {
							res.append(str);
							res.append("\n");
						}
					}
					exitcode = 0;
					if(customScript != null && customScript.length() > 0){
						customScript = customScript.trim();
						//treat as a script file
						File file = new File(customScript);
						if(file.exists()){
							StringBuffer fileContent = new StringBuffer("");
							try {
								FileInputStream in = new FileInputStream(file);
								int len = 0;
								byte[] data1 = new byte[1024];
								while (-1 != (len = in.read(data1))) {
									 fileContent.append(new String(data1, 0, len)+"\n");
								}
								String fileCommands = fileContent.toString();
								customScript = fileCommands.replace("$IPTABLES", " "+ ipPath );
								commands = Arrays.asList(customScript.split("\n"));
								Shell.SU.run(commands);
							} catch (FileNotFoundException e) {
								
							}
						} else {
							customScript = customScript.replace("$IPTABLES", " "+ ipPath );
							commands = Arrays.asList(customScript.split("\n"));
							Shell.SU.run(commands);
						}
					}
				}
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
			} 
		}
	}*/
	
	public static boolean clearRules(Context ctx) throws IOException{
		final StringBuilder res = new StringBuilder();
		StringBuilder script = new StringBuilder();
		setIpTablePath(ctx);
		script.append(ipPath + " -F\n");
		script.append(ipPath + " -X\n");
		int code = runScriptAsRoot(ctx, script.toString(), null,  res);
		if (code == -1) {
			alert(ctx, ctx.getString(R.string.error_purge) + code + "\n" + res, TOASTTYPE.ERROR);
			return false;
		}
		return true;
	}
	
	public static boolean applyRulesBeforeShutdown(Context ctx) {
		final StringBuilder res = new StringBuilder();
		StringBuilder script = new StringBuilder();
		setIpTablePath(ctx);
		script.append(ipPath + " -F\n");
		script.append(ipPath + " -X\n");
		script.append(ipPath + " -P INPUT DROP\n");
		script.append(ipPath + " -P OUTPUT DROP\n");
		script.append(ipPath + " -P FORWARD DROP\n");
		try {
			runScriptAsRoot(ctx, script.toString(),null, res);
		} catch (IOException e) {
		}
		return true;
	}
	
	public static void saveSharedPreferencesToFileConfirm(final Context ctx) {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setMessage("Do you want to export rules to sdcard ?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   if(saveSharedPreferencesToFile(ctx)){
		       				Api.alert(ctx, ctx.getString(R.string.export_rules_success) + " " + Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/", TOASTTYPE.INFO);
		       			} else {
		       				Api.alert(ctx, ctx.getString(R.string.export_rules_fail),TOASTTYPE.ERROR );
		        	   }
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
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
	
/*	public static void loadSharedPreferencesToFileConfirm(final Context ctx) {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setMessage("This will override the existing rules ! Do you want to import the rules ? ")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   if(loadSharedPreferencesFromFile(ctx)){
		        		   alert(ctx, ctx.getString(R.string.import_rules_success) +  Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/");
		        	   } else {
		   					Api.alert(ctx, ctx.getString(R.string.import_rules_fail) );
		   				}
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}*/
	

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
			//if(Shell.SU.available()) {
			//	Shell.SU.run("ls /sys/class/net");
			//}
			p = Runtime.getRuntime().exec(
					new String[] { "su", "-c", "ls /sys/class/net" });
			BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String tmp;
			while ((tmp = stdout.readLine()) != null) {
				inputLine.append(tmp);
				inputLine.append(",");
			}
			// use inputLine.toString(); here it would have whole source
			stdout.close();

		} catch (Exception e) {
			Log.d("Exception" ,e.toString());
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
	
	public static boolean hasRootAccess(Context ctx, boolean showErrors) {
		if (isRooted) return true;
		final StringBuilder res = new StringBuilder();
		try {
			// Run an empty script just to check root access
			if (runScriptAsRoot(ctx, "exit 0\n", null, res) == 0) {
				isRooted = true;
				return true;
			}
		} catch (Exception e) {
		}
		if (showErrors) {
			alert(ctx, ctx.getString(R.string.error_su),TOASTTYPE.ERROR);
		}
		return false;
	}
	
}
