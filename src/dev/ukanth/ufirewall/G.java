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

package dev.ukanth.ufirewall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

public class G extends android.app.Application {

	public static final String TAG = "AFWall";
	
	private static final String IS_ROOT_AVAIL = "isRootAvail";
	private static final String FIX_START_LEAK = "fixLeak";
	private static final String DISABLE_TASKER_TOAST = "disableTaskerToast";
	
	private static final String ENABLE_ROAM = "enableRoam";
	private static final String ENABLE_VPN = "enableVPN";
	private static final String ENABLE_LAN = "enableLAN";
	private static final String ENABLE_IPV6 = "enableIPv6";
	private static final String ENABLE_INBOUND = "enableInbound";
	private static final String ENABLE_LOG = "enableLog";
	private static final String ENABLE_ADMIN = "enableAdmin";
	private static final String ENABLE_CONFIRM = "enableConfirm";
	private static final String ENABLE_MULTI_PROFILE =  "enableMultiProfile";
	private static final String SHOW_UID = "showUid"; 
	private static final String NOTIFY_INSTALL = "notifyAppInstall";
	private static final String DISABLE_ICONS = "disableIcons";
	private static final String IPTABLES_PATH = "ip_path";
	private static final String BUSYBOX_PATH = "bb_path";
	private static final String LANGUAGE = "locale";
	private static final String PROFILE_STORED_POSITION = "storedPosition";
	private static final String SYSTEM_APP_COLOR = "sysColor";
	private static final String ACTIVE_RULES = "activeRules";
	private static final String USE_PASSWORD_PATTERN = "usePatterns";
	private static final String PROFILE_SWITCH = "applyOnSwitchProfiles";
	private static final String LOG_TARGET = "logTarget";
	private static final String APP_VERSION = "appVersion";
	
	private static final String MULTI_USER = "multiUser";
	private static final String MULTI_USER_ID = "multiUserId";
	
	private static final String AFWALL_STATUS = "AFWallStaus";
	
	private static final String PROFILES = "profiles";
	
	public static Context ctx;
	public static SharedPreferences gPrefs;
	public static SharedPreferences pPrefs;
	public static SharedPreferences sPrefs;
	public static String[] profiles = { "AFWallPrefs", "AFWallProfile1", "AFWallProfile2", "AFWallProfile3" };
	
	/* global preferences */
	//public static boolean alternateStart() { return gPrefs.getBoolean("alternateStart", false); }
	//public static boolean alternateStart(boolean val) { gPrefs.edit().putBoolean("alternateStart", val).commit(); return val; }

	public static boolean isRootAvail() { return gPrefs.getBoolean(IS_ROOT_AVAIL, false); }
	public static boolean isRootAvail(boolean val) { gPrefs.edit().putBoolean(IS_ROOT_AVAIL, val).commit(); return val; }
	
	public static boolean fixLeak() { return gPrefs.getBoolean(FIX_START_LEAK, false); }
	public static boolean fixLeak(boolean val) { gPrefs.edit().putBoolean(FIX_START_LEAK, val).commit(); return val; }

	public static boolean disableTaskerToast() { return gPrefs.getBoolean(DISABLE_TASKER_TOAST, false); }
	public static boolean disableTaskerToast(boolean val) { gPrefs.edit().putBoolean(DISABLE_TASKER_TOAST, val).commit(); return val; }

	public static boolean enableRoam() { return gPrefs.getBoolean(ENABLE_ROAM, true); }
	public static boolean enableRoam(boolean val) { gPrefs.edit().putBoolean(ENABLE_ROAM, val).commit(); return val; }

	public static boolean enableVPN() { return gPrefs.getBoolean(ENABLE_VPN, false); }
	public static boolean enableVPN(boolean val) { gPrefs.edit().putBoolean(ENABLE_VPN, val).commit(); return val; }

	public static boolean enableLAN() { return gPrefs.getBoolean(ENABLE_LAN, false); }
	public static boolean enableLAN(boolean val) { gPrefs.edit().putBoolean(ENABLE_LAN, val).commit(); return val; }

	public static boolean enableIPv6() { return gPrefs.getBoolean(ENABLE_IPV6, false); }
	public static boolean enableIPv6(boolean val) { gPrefs.edit().putBoolean(ENABLE_IPV6, val).commit(); return val; }

	public static boolean enableInbound() { return gPrefs.getBoolean(ENABLE_INBOUND, false); }
	public static boolean enableInbound(boolean val) { gPrefs.edit().putBoolean(ENABLE_INBOUND, val).commit(); return val; }

	public static boolean enableLog() { return gPrefs.getBoolean(ENABLE_LOG, false); }
	public static boolean enableLog(boolean val) { gPrefs.edit().putBoolean(ENABLE_LOG, val).commit(); return val; }

	public static boolean enableAdmin() { return gPrefs.getBoolean(ENABLE_ADMIN, false); }
	public static boolean enableAdmin(boolean val) { gPrefs.edit().putBoolean(ENABLE_ADMIN, val).commit(); return val; }

	public static boolean enableConfirm() { return gPrefs.getBoolean(ENABLE_CONFIRM, false); }
	public static boolean enableConfirm(boolean val) { gPrefs.edit().putBoolean(ENABLE_CONFIRM, val).commit(); return val; }

	public static boolean enableMultiProfile() { return gPrefs.getBoolean(ENABLE_MULTI_PROFILE, false); }
	public static boolean enableMultiProfile(boolean val) { gPrefs.edit().putBoolean(ENABLE_MULTI_PROFILE, val).commit(); return val; }

	public static boolean showUid() { return gPrefs.getBoolean(SHOW_UID, false); }
	public static boolean showUid(boolean val) { gPrefs.edit().putBoolean(SHOW_UID, val).commit(); return val; }

	public static boolean notifyAppInstall() { return gPrefs.getBoolean(NOTIFY_INSTALL, false); }
	public static boolean notifyAppInstall(boolean val) { gPrefs.edit().putBoolean(NOTIFY_INSTALL, val).commit(); return val; }

	public static boolean disableIcons() { return gPrefs.getBoolean(DISABLE_ICONS, false); }
	public static boolean disableIcons(boolean val) { gPrefs.edit().putBoolean(DISABLE_ICONS, val).commit(); return val; }

	public static String ip_path() { return gPrefs.getString(IPTABLES_PATH, "auto"); }
	public static String ip_path(String val) { gPrefs.edit().putString(IPTABLES_PATH, val).commit(); return val; }

	public static String bb_path() { return gPrefs.getString(BUSYBOX_PATH, "builtin"); }
	public static String bb_path(String val) { gPrefs.edit().putString(BUSYBOX_PATH, val).commit(); return val; }

	public static String locale() { return gPrefs.getString(LANGUAGE, "en"); }
	public static String locale(String val) { gPrefs.edit().putString(LANGUAGE, val).commit(); return val; }

	public static int storedPosition() { return gPrefs.getInt(PROFILE_STORED_POSITION, 0); }
	public static int storedPosition(int val) { gPrefs.edit().putInt(PROFILE_STORED_POSITION, val).commit(); return val; }

	public static int sysColor() { return gPrefs.getInt(SYSTEM_APP_COLOR, Color.RED); }
	public static int sysColor(int val) { gPrefs.edit().putInt(SYSTEM_APP_COLOR, val).commit(); return val; }

	
	public static boolean activeRules() { return gPrefs.getBoolean(ACTIVE_RULES, true); }
	
	public static boolean usePatterns() { return gPrefs.getBoolean(USE_PASSWORD_PATTERN, false); }
	
	
	public static boolean isMultiUser() { return gPrefs.getBoolean(MULTI_USER, false); }

	public static void setMultiUserId(int val) { gPrefs.edit().putLong(MULTI_USER_ID, val).commit();}
	public static Long getMultiUserId() { return gPrefs.getLong(MULTI_USER_ID, 0);}
	
	public static boolean applyOnSwitchProfiles() { return gPrefs.getBoolean(PROFILE_SWITCH, false); }
	public static boolean applyOnSwitchProfiles(boolean val) { gPrefs.edit().putBoolean(PROFILE_SWITCH, val).commit(); return val; }
	
	public static String logTarget() { return gPrefs.getString(LOG_TARGET, ""); }
	public static String logTarget(String val) { gPrefs.edit().putString(LOG_TARGET, val).commit(); return val; }

	public static int appVersion() { return gPrefs.getInt(APP_VERSION, 0); }
	public static int appVersion(int val) { gPrefs.edit().putInt(APP_VERSION, val).commit(); return val; }

	
	public void onCreate() {
		super.onCreate();
		ctx = this.getApplicationContext();
		reloadPrefs();
	}

	public static void reloadPrefs() {
		gPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

		String profileName = Api.DEFAULT_PREFS_NAME;
		int pos = storedPosition();
		//int profileCount = getProfileCount();
		if (enableMultiProfile() && (pos >= 0 && pos <= 3)) {
			profileName = profiles[pos];	
			/*if(pos <= 3) {
				profileName = profiles[pos];	
			} else if(pos > 3 && pos <= (profileCount + 3)) {
				profileName = "AFWallPrefsCustom" + pos;
			} */
		}
		else {
			profileName = profiles[0];
		} 
		Api.PREFS_NAME = profileName;

		pPrefs = ctx.getSharedPreferences(profileName, Context.MODE_PRIVATE);
		sPrefs = ctx.getSharedPreferences(AFWALL_STATUS/* sic */, Context.MODE_PRIVATE);
	}

	public static void reloadProfile() {
		reloadPrefs();
		Api.applications = null;
	}
	
	public Integer getCurrentProfile(){
		return storedPosition();
	}

	public static boolean setProfile(boolean newEnableMultiProfile, int newStoredPosition) {
		if (newEnableMultiProfile == enableMultiProfile() && newStoredPosition == storedPosition()) {
			return false;
		}
		enableMultiProfile(newEnableMultiProfile);
		storedPosition(newEnableMultiProfile ? newStoredPosition : 0);
		reloadProfile();
		return true;
	}
	
	public static void addProfile(String profile) {
		String previousProfiles = gPrefs.getString(PROFILES, "");
		int profileCount = previousProfiles.split(",").length; 
		StringBuilder builder = new StringBuilder();
		if(previousProfiles.equals("")){
			builder.append(profile);
		} else {
			builder.append(previousProfiles);
			builder.append(",");
			builder.append(profile + ":" + profileCount);
		}
		gPrefs.edit().putString(PROFILES, builder.toString()).commit(); 
	}
	
	public static void removeProfile(int itemPosition,String profileName) {
		if(itemPosition > 4) {
			
		} else {
			String previousProfiles = gPrefs.getString(PROFILES, "");
			
			StringBuilder builder = new StringBuilder();
			if(!previousProfiles.equals("")){
				for(String profile:previousProfiles.split(",")) {
					if(!profile.equals(profileName)) {
						builder.append(profile);
						builder.append(",");
					}
				}
			}
			gPrefs.edit().putString(PROFILES, builder.toString()).commit();	
		}
		 
	}
	

	public static int getProfileCount() {
		int count = 0;
		String previousProfiles = gPrefs.getString(PROFILES, "");
		if(!previousProfiles.equals("")){
			count = previousProfiles.split(",").length;
		} 
		return count;
	}
	
	public static List<String> getProfiles() {
		String previousProfiles = gPrefs.getString(PROFILES, "");
		List<String> profileList = new ArrayList<String>();
		if(!previousProfiles.equals("")){
			profileList = Arrays.asList(previousProfiles.split(","));
		} 
		return profileList;
	}
}
