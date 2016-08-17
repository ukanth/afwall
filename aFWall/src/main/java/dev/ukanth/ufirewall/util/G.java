/**
 * A place to store globals
 * 
 * Copyright (C) 2013  Kevin Cernekee
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
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.BuildConfig;

public class G extends android.app.Application {

	public static final String TAG = "AFWall";
	
	private static final String HAS_ROOT = "hasRoot";
	private static final String NO_CHAINS = "noOtherChains";
	private static final String FIX_START_LEAK = "fixLeak";
	private static final String DISABLE_TASKER_TOAST = "disableTaskerToast";
	private static final String REG_DO = "ipurchaseddonatekey";
	
	private static final String ENABLE_ROAM = "enableRoam";
	private static final String ENABLE_VPN = "enableVPN";
	private static final String ENABLE_LAN = "enableLAN";
	private static final String ENABLE_IPV6 = "enableIPv6";
	private static final String ENABLE_INBOUND = "enableInbound";
	//private static final String ENABLE_LOG = "enableLog";
	private static final String ENABLE_LOG_SERVICE = "enableLogService";
	private static final String ENABLE_ADMIN = "enableAdmin";
	private static final String ENABLE_DEVICE_CHECK = "enableDeviceCheck";
	private static final String ENABLE_CONFIRM = "enableConfirm";
	private static final String ENABLE_MULTI_PROFILE =  "enableMultiProfile";
	private static final String SHOW_UID = "showUid"; 
	private static final String NOTIFY_INSTALL = "notifyAppInstall";
	private static final String DISABLE_ICONS = "disableIcons";
	private static final String IPTABLES_PATH = "ip_path";
	private static final String PROTECTION_OPTION = "passSetting";
	private static final String BUSYBOX_PATH = "bb_path";
	private static final String TOAST_POS = "toast_pos";
	private static final String LANGUAGE = "locale";
	private static final String SORT_BY = "sort";
	//private static final String PROFILE_STORED_POSITION = "storedPosition";
	private static final String LAST_STORED_PROFILE = "storedProfile";
	private static final String SYSTEM_APP_COLOR = "sysColor";
	private static final String ACTIVE_RULES = "activeRules";
	private static final String ACTIVE_NOTIFICATION = "activeNotification";
	//private static final String LOCK_NOTIFICATION = "lockScreenNotification";
	
	private static final String PROFILE_SWITCH = "applyOnSwitchProfiles";
	private static final String LOG_TARGET = "logTarget";
	private static final String APP_VERSION = "appVersion";
	private static final String DNS_PROXY = "dns_value";
	
	private static final String MULTI_USER = "multiUser";
	private static final String MULTI_USER_ID = "multiUserId";

	private static final String IS_MIGRATED = "isMigrated";
	
	private static final String SHOW_FILTER = "showFilter";
	
	private static final String USE_PASSWORD_PATTERN = "usePatterns";
	private static final String PATTERN_MAX_TRY = "patternMax";
	private static final String PATTERN_STEALTH = "stealthMode";
	private static final String ISKINGDETECT = "kingDetect";
	
	private static final String PWD_ENCRYPT= "pwdEncrypt";
	
	private static final String profile_Pwd= "profilePwd";
	/** FIXME **/
	private static final String AFWALL_STATUS = "AFWallStaus";
	
	private static final String BLOCKED_NOTIFICATION = "block_filter_app";
	
	/* Profiles */
	private static final String ADDITIONAL_PROFILES = "plusprofiles";
	
	private static String AFWALL_PROFILE = "AFWallProfile";

	private static String SHOW_LOG_TOAST = "showLogToasts";
	
	public static String[] profiles = { "AFWallPrefs" , AFWALL_PROFILE + 1 , AFWALL_PROFILE + 2, AFWALL_PROFILE + 3 };


	private static final String WIDGET_X = "widgetX";
	private static final String WIDGET_Y = "widgetY";

	private static final String  XPOSED_FIX_DM_LEAK = "fixDownloadManagerLeak";
	
	public static Context ctx;
	public static SharedPreferences gPrefs;
	public static SharedPreferences pPrefs;
	public static SharedPreferences sPrefs;
	
	/*public static void main(String p[]) {
		for(String g: profiles) {
			System.out.println(g);
		}
		
	}*/
	
	/* global preferences */
	//public static boolean alternateStart() { return gPrefs.getBoolean("alternateStart", false); }
	//public static boolean alternateStart(boolean val) { gPrefs.edit().putBoolean("alternateStart", val).commit(); return val; }
	
	public static boolean isEnc() { return gPrefs.getBoolean(PWD_ENCRYPT, false); }
	public static boolean isEnc(boolean val) { gPrefs.edit().putBoolean(PWD_ENCRYPT, val).commit(); return val; }
	
	public static String profile_pwd() { return gPrefs.getString(profile_Pwd, ""); }
	public static String profile_pwd(String val) { gPrefs.edit().putString(profile_Pwd, val).commit(); return val; }

	public static boolean isXposedDM() { return gPrefs.getBoolean(XPOSED_FIX_DM_LEAK, false); }
	public static boolean isXposedDM(boolean val) { gPrefs.edit().putBoolean(XPOSED_FIX_DM_LEAK, val).commit(); return val; }


	public static boolean hasRoot() { return gPrefs.getBoolean(HAS_ROOT, false); }
	public static boolean hasRoot(boolean val) { gPrefs.edit().putBoolean(HAS_ROOT, val).commit(); return val; }


	public static boolean activeNotification() { return gPrefs.getBoolean(ACTIVE_NOTIFICATION, false); }
	public static boolean activeNotification(boolean val) { gPrefs.edit().putBoolean(ACTIVE_NOTIFICATION, val).commit(); return val; }

	/*public static boolean lockNotification() { return gPrefs.getBoolean(LOCK_NOTIFICATION, false); }
	public static boolean lockNotification(boolean val) { gPrefs.edit().putBoolean(LOCK_NOTIFICATION, val).commit(); return val; }*/


	public static boolean showLogToasts() { return gPrefs.getBoolean(SHOW_LOG_TOAST, false); }
	public static boolean showLogToasts(boolean val) { gPrefs.edit().putBoolean(SHOW_LOG_TOAST, val).commit(); return val; }


	/*public static boolean noOtherChains() { return gPrefs.getBoolean(NO_CHAINS, false); }
	public static boolean noOtherChains(boolean val) { gPrefs.edit().putBoolean(NO_CHAINS, val).commit(); return val; }*/

	public static boolean fixLeak() { return gPrefs.getBoolean(FIX_START_LEAK, false); }
	//public static boolean fixLeak(boolean val) { gPrefs.edit().putBoolean(FIX_START_LEAK, val).commit(); return val; }

	public static boolean disableTaskerToast() { return gPrefs.getBoolean(DISABLE_TASKER_TOAST, false); }
	//public static boolean disableTaskerToast(boolean val) { gPrefs.edit().putBoolean(DISABLE_TASKER_TOAST, val).commit(); return val; }

	public static boolean isDonate() {
		return BuildConfig.APPLICATION_ID.equals("dev.ukanth.ufirewall.donate");
	}
	public static boolean isDoKey(Context ctx) {
		if(!gPrefs.getBoolean(REG_DO, false))  {
			try {
				ApplicationInfo app = ctx.getPackageManager().getApplicationInfo("dev.ukanth.ufirewall.donatekey", 0);
				if(app!= null) {
					gPrefs.edit().putBoolean(REG_DO, true).commit();
				}

			} catch (PackageManager.NameNotFoundException e) {

			}
		}
		return gPrefs.getBoolean(REG_DO, false);
	}

	public static boolean oldLogView(boolean val) { gPrefs.edit().putBoolean("oldLogView", val).commit(); return val; }
	public static boolean oldLogView() {return gPrefs.getBoolean("oldLogView", false); }

	public static boolean isDo(boolean val) { gPrefs.edit().putBoolean(REG_DO, val).commit(); return val; }
	
	public static boolean enableRoam() { return gPrefs.getBoolean(ENABLE_ROAM, false); }
	public static boolean enableRoam(boolean val) { gPrefs.edit().putBoolean(ENABLE_ROAM, val).commit(); return val; }

	public static boolean enableVPN() { return gPrefs.getBoolean(ENABLE_VPN, false); }
	public static boolean enableVPN(boolean val) { gPrefs.edit().putBoolean(ENABLE_VPN, val).commit(); return val; }

	public static boolean enableLAN() { return gPrefs.getBoolean(ENABLE_LAN, true); }
	public static boolean enableLAN(boolean val) { gPrefs.edit().putBoolean(ENABLE_LAN, val).commit(); return val; }

	public static int getWidgetX(Context ctx) {
		DisplayMetrics dm = new DisplayMetrics();
		WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(dm);
		int defaultX = dm.widthPixels;
		String x = gPrefs.getString(WIDGET_X, defaultX+"");
		try {
			defaultX = Integer.parseInt(x);
		} catch (Exception exception){ }
		return defaultX;
	}

	public static int getWidgetY(Context ctx) {
		DisplayMetrics dm = new DisplayMetrics();
		WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
		wm.getDefaultDisplay().getMetrics(dm);
		int defaultY = dm.heightPixels;
		String y = gPrefs.getString(WIDGET_Y, defaultY+"");
		try {
			defaultY = Integer.parseInt(y);
		} catch (Exception exception){ }
		return defaultY;
	}

	public static boolean enableIPv6() { return gPrefs.getBoolean(ENABLE_IPV6, false); }
	public static boolean enableIPv6(boolean val) { gPrefs.edit().putBoolean(ENABLE_IPV6, val).commit(); return val; }

	public static boolean enableInbound() { return gPrefs.getBoolean(ENABLE_INBOUND, false); }
	//public static boolean enableInbound(boolean val) { gPrefs.edit().putBoolean(ENABLE_INBOUND, val).commit(); return val; }

	/*public static boolean enableLog() { return gPrefs.getBoolean(ENABLE_LOG, false); }
	public static boolean enableLog(boolean val) { gPrefs.edit().putBoolean(ENABLE_LOG, val).commit(); return val; }*/
	
	public static boolean enableLogService() { return gPrefs.getBoolean(ENABLE_LOG_SERVICE, false); }
	public static boolean enableLogService(boolean val) { gPrefs.edit().putBoolean(ENABLE_LOG_SERVICE, val).commit(); return val; }

	public static boolean enableAdmin() { return gPrefs.getBoolean(ENABLE_ADMIN, false); }
	public static boolean enableAdmin(boolean val) { gPrefs.edit().putBoolean(ENABLE_ADMIN, val).commit(); return val; }

	public static boolean enableDeviceCheck() { return gPrefs.getBoolean(ENABLE_DEVICE_CHECK, false); }
	public static boolean enableDeviceCheck(boolean val) { gPrefs.edit().putBoolean(ENABLE_DEVICE_CHECK, val).commit(); return val; }

	public static boolean enableConfirm() { return gPrefs.getBoolean(ENABLE_CONFIRM, false); }
	//public static boolean enableConfirm(boolean val) { gPrefs.edit().putBoolean(ENABLE_CONFIRM, val).commit(); return val; }

	public static boolean enableMultiProfile() { return gPrefs.getBoolean(ENABLE_MULTI_PROFILE, false); }
	public static boolean enableMultiProfile(boolean val) { gPrefs.edit().putBoolean(ENABLE_MULTI_PROFILE, val).commit(); return val; }

	public static boolean showUid() { return gPrefs.getBoolean(SHOW_UID, false); }
	public static boolean showUid(boolean val) { gPrefs.edit().putBoolean(SHOW_UID, val).commit(); return val; }
	
	public static boolean showFilter() { return gPrefs.getBoolean(SHOW_FILTER, false); }
	public static boolean showFilter(boolean val) { gPrefs.edit().putBoolean(SHOW_FILTER, val).commit(); return val; }


	public static boolean kingDetected() { return gPrefs.getBoolean(ISKINGDETECT, false); }
	public static boolean kingDetected(boolean val) { gPrefs.edit().putBoolean(ISKINGDETECT, val).commit(); return val; }

	//public static boolean notifyAppInstall() { return gPrefs.getBoolean(NOTIFY_INSTALL, false); }
	//public static boolean notifyAppInstall(boolean val) { gPrefs.edit().putBoolean(NOTIFY_INSTALL, val).commit(); return val; }

	public static boolean disableIcons() { return gPrefs.getBoolean(DISABLE_ICONS, false); }
	//public static boolean disableIcons(boolean val) { gPrefs.edit().putBoolean(DISABLE_ICONS, val).commit(); return val; }

	public static String ip_path() { return gPrefs.getString(IPTABLES_PATH, "auto"); }
	public static String ip_path(String val) { gPrefs.edit().putString(IPTABLES_PATH, val).commit(); return val; }
	
	public static String dns_proxy() { return gPrefs.getString(DNS_PROXY, "auto"); }
	//public static String dns_proxy(String val) { gPrefs.edit().putString(DNS_PROXY, val).commit(); return val; }

	public static String bb_path() { return gPrefs.getString(BUSYBOX_PATH, "builtin"); }
	public static String bb_path(String val) { gPrefs.edit().putString(BUSYBOX_PATH, val).commit(); return val; }

	public static String toast_pos() { return gPrefs.getString(TOAST_POS, "bottom"); }
	public static String toast_pos(String val) { gPrefs.edit().putString(TOAST_POS, val).commit(); return val; }

	public static String locale() { return gPrefs.getString(LANGUAGE, "en"); }
	public static String locale(String val) { gPrefs.edit().putString(LANGUAGE, val).commit(); return val; }


	public static String sortBy() {
		return gPrefs.getString(SORT_BY, "s0");
	}

	public static void sortBy(String sort) {
		gPrefs.edit().putString(SORT_BY, sort).commit();
	}

	/*public static int storedPosition() { return gPrefs.getInt(LAST_STORED_PROFILE, 0); }
	public static int storedPosition(int val) { gPrefs.edit().putInt(LAST_STORED_PROFILE, val).commit(); return val; }*/


	public static String storedProfile() { return gPrefs.getString(LAST_STORED_PROFILE, "AFWallPrefs"); }
	public static String storedProfile(String val) { gPrefs.edit().putString(LAST_STORED_PROFILE, val).commit(); return val; }


	public static int sysColor() { return gPrefs.getInt(SYSTEM_APP_COLOR, Color.parseColor("#0F9D58")); }
	//public static int sysColor(int val) { gPrefs.edit().putInt(SYSTEM_APP_COLOR, val).commit(); return val; }

	
	public static boolean activeRules() { return gPrefs.getBoolean(ACTIVE_RULES, true); }
	
	public static boolean usePatterns() { return gPrefs.getBoolean(USE_PASSWORD_PATTERN, false); }
	public static boolean setUsePatterns(boolean val) { gPrefs.edit().putBoolean(USE_PASSWORD_PATTERN, val).commit(); return val; }
	
	public static boolean enableStealthPattern() { return gPrefs.getBoolean(PATTERN_STEALTH, false); }
	//public static boolean enableStealthPattern(boolean val) { gPrefs.edit().putBoolean(PATTERN_STEALTH, val).commit(); return val;  }
	
	
	public static int getMaxPatternTry() { return Integer.parseInt(gPrefs.getString(PATTERN_MAX_TRY, "3")); }

	public static boolean isMultiUser() { return gPrefs.getBoolean(MULTI_USER, false); }

	public static void setMultiUserId(int val) { gPrefs.edit().putLong(MULTI_USER_ID, val).commit();}
	public static Long getMultiUserId() { return gPrefs.getLong(MULTI_USER_ID, 0);}
	
	public static boolean applyOnSwitchProfiles() { return gPrefs.getBoolean(PROFILE_SWITCH, false); }
	//public static boolean applyOnSwitchProfiles(boolean val) { gPrefs.edit().putBoolean(PROFILE_SWITCH, val).commit(); return val; }
	
	public static String logTarget() { return gPrefs.getString(LOG_TARGET, ""); }
	public static String logTarget(String val) { gPrefs.edit().putString(LOG_TARGET, val).commit(); return val; }

	public static int appVersion() { return gPrefs.getInt(APP_VERSION, 0); }
	public static int appVersion(int val) { gPrefs.edit().putInt(APP_VERSION, val).commit(); return val; }

	//new protection list
	public static String protectionLevel() {
		if(gPrefs.getString(PROTECTION_OPTION, "p0").equals("Disable")) {
			gPrefs.edit().putString(PROTECTION_OPTION,"p0").commit();
		}
		return gPrefs.getString(PROTECTION_OPTION, "p0");
	}

	public static List<String> getBlockedNotifyApps() {
		String blockedApps = gPrefs.getString(BLOCKED_NOTIFICATION, null);
		List<String> data = new ArrayList<String>();
		if(blockedApps != null) {
			data = Arrays.asList(blockedApps.split(","));
		}
		return data;
	}

	public static boolean isXposedEnabled() {
		// will be used by XPosed to return true
		return false;
	}
	
	public void onCreate() {
		super.onCreate();
		FlowManager.init(new FlowConfig.Builder(this)
				.openDatabasesOnInit(true).build());
		ctx = this.getApplicationContext();
		reloadPrefs();
	}

	public static void reloadPrefs() {
		gPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

		String profileName = Api.DEFAULT_PREFS_NAME;
		//int pos = storedPosition();
		//int profileCount = getProfileCount();
		if(enableMultiProfile() ) {
			profileName = storedProfile();
		}
		Api.PREFS_NAME = profileName;

		pPrefs = ctx.getSharedPreferences(profileName, Context.MODE_PRIVATE);
		sPrefs = ctx.getSharedPreferences(AFWALL_STATUS/* sic */, Context.MODE_PRIVATE);
	}

	public static void reloadProfile() {
		reloadPrefs();
		Api.applications = null;
	}
	
	/*public static Integer getCurrentProfile(){
		return storedPosition();
	}*/

	public static boolean setProfile(boolean newEnableMultiProfile, String profileName) {
		//if (newEnableMultiProfile == enableMultiProfile()) {
		//	return false;
		//}
		enableMultiProfile(newEnableMultiProfile);
		storedProfile(profileName);
		reloadProfile();
		return true;
	}
	
	public static void addAdditionalProfile(String profile) {
		String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
		StringBuilder builder = new StringBuilder();
		if(profile !=null && profile.length() > 0) {
			profile = profile.trim();
			if(previousProfiles.length() == 0){
				builder.append(profile);
			} else {
				builder.append(previousProfiles);
				builder.append(",");
				builder.append(profile);
			}
			gPrefs.edit().putString(ADDITIONAL_PROFILES, builder.toString()).commit();
		}
	}


	public static void clearSharedPreferences(Context ctx, String preferenceName){
		File dir = new File(ctx.getFilesDir().getParent() + "/shared_prefs/");
		String[] children = dir.list();
		for (int i = 0; i < children.length; i++) {
			//String profName = ;
			// clear each of the prefrances
			if(children[i].replace(".xml", "").equals(preferenceName)) {
				new File(dir, children[i]).delete();
			}
		}
	}
	
	public static void removeAdditionalProfile(String profileName) {

		//after remove clear all the data inside the custom profile
		if(ctx!= null) {
			clearSharedPreferences(ctx,profileName);
		}
		String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");

		StringBuilder builder = new StringBuilder();
		if (!previousProfiles.equals("")) {
			for (String profile : previousProfiles.split(",")) {
				if (!profile.equals(profileName)) {
					builder.append(profile);
					builder.append(",");
				}
			}
		}
		String profile = builder.toString();
		if (profile.length() > 0 && profile.charAt(profile.length()-1)==',') {
			profile = profile.substring(0, profile.length()-1);
		}
		gPrefs.edit().putString(ADDITIONAL_PROFILES, profile).commit();

	}
	

	/*public static int getAdditionalProfileCount() {
		int count = 0;
		String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
		if(!previousProfiles.equals("")){
			count = previousProfiles.split(",").length;
		} 
		return count;
	}*/
	
	/*public static int getProfilePosition() {
		int count = 0;
		String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
		if(!previousProfiles.equals("")){
			count = previousProfiles.split(",").length;
		} 
		return count + DEFAULT_PROFILE_COUNT;
	}*/
	
	/*public static int getProfilePosition(String profileName){
		int profilePosition = -1;
		List<String> profileList = getAdditionalProfiles();
		for(int i=0; i < profileList.size(); i++) {
			if(profileName.equals(profileList.get(i))){
				profilePosition = i + 4;
			}
		}
		return profilePosition;
	}*/
	
	/*public static String getProfileName(int position){
		String profileName  = "";
		position = position - 4;
		List<String> profileList = getAdditionalProfiles();
		for(int i=0; i < profileList.size(); i++) {
			if(position == i){
				profileName = profileList.get(i);
			}
		}
		return profileName;
	}*/
	
	/*public static String getActiveProfileName(final Context ctx){
		String profileName = "";
		if(G.enableMultiProfile()){
			int pos = getCurrentProfile();
			if(pos < 4 ) {
				switch(pos){
				case 0: profileName = G.gPrefs.getString("default", ctx.getString(R.string.defaultProfile)); break;
				case 1: profileName = G.gPrefs.getString("profile1", ctx.getString(R.string.profile1));break;
				case 2: profileName = G.gPrefs.getString("profile2", ctx.getString(R.string.profile2));break;
				case 3: profileName = G.gPrefs.getString("profile3", ctx.getString(R.string.profile3));break;
				}
			} else {
				profileName = getProfileName(pos);
			}
		}
		return profileName;
		
	}*/
	public static List<String> getAdditionalProfiles() {
		String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
		List<String> profileList = new ArrayList<>(new LinkedHashSet<String>());
		if(previousProfiles != null && previousProfiles.length() > 0){
			profileList = Arrays.asList(previousProfiles.split(","));
		} 
		return profileList;
	}

	public static boolean isMigrated() { return gPrefs.getBoolean(IS_MIGRATED, false); }
	public static boolean isMigrated(boolean val) { gPrefs.edit().putBoolean(IS_MIGRATED, val).commit(); return val; }

}
