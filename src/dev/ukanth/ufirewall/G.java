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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

public class G extends android.app.Application {

	public static final String TAG = "AFWall";

	public static Context ctx;
	public static SharedPreferences gPrefs;
	public static SharedPreferences pPrefs;
	public static SharedPreferences sPrefs;
	public static String[] profiles = { "AFWallPrefs", "AFWallProfile1", "AFWallProfile2", "AFWallProfile3" };

	/* global preferences */
	public static boolean alternateStart() { return gPrefs.getBoolean("alternateStart", false); }
	public static boolean alternateStart(boolean val) { gPrefs.edit().putBoolean("alternateStart", val).commit(); return val; }

	public static boolean isRootAvail() { return gPrefs.getBoolean("isRootAvail", false); }
	public static boolean isRootAvail(boolean val) { gPrefs.edit().putBoolean("isRootAvail", val).commit(); return val; }

	public static boolean fixLeak() { return gPrefs.getBoolean("fixLeak", false); }
	public static boolean fixLeak(boolean val) { gPrefs.edit().putBoolean("fixLeak", val).commit(); return val; }

	public static boolean disableTaskerToast() { return gPrefs.getBoolean("disableTaskerToast", false); }
	public static boolean disableTaskerToast(boolean val) { gPrefs.edit().putBoolean("disableTaskerToast", val).commit(); return val; }

	public static boolean enableRoam() { return gPrefs.getBoolean("enableRoam", true); }
	public static boolean enableRoam(boolean val) { gPrefs.edit().putBoolean("enableRoam", val).commit(); return val; }

	public static boolean enableVPN() { return gPrefs.getBoolean("enableVPN", false); }
	public static boolean enableVPN(boolean val) { gPrefs.edit().putBoolean("enableVPN", val).commit(); return val; }

	public static boolean enableLAN() { return gPrefs.getBoolean("enableLAN", false); }
	public static boolean enableLAN(boolean val) { gPrefs.edit().putBoolean("enableLAN", val).commit(); return val; }

	public static boolean enableIPv6() { return gPrefs.getBoolean("enableIPv6", false); }
	public static boolean enableIPv6(boolean val) { gPrefs.edit().putBoolean("enableIPv6", val).commit(); return val; }

	public static boolean enableInbound() { return gPrefs.getBoolean("enableInbound", false); }
	public static boolean enableInbound(boolean val) { gPrefs.edit().putBoolean("enableInbound", val).commit(); return val; }

	public static boolean enableFirewallLog() { return gPrefs.getBoolean("enableFirewallLog", true); }
	public static boolean enableFirewallLog(boolean val) { gPrefs.edit().putBoolean("enableFirewallLog", val).commit(); return val; }

	public static boolean enableAdmin() { return gPrefs.getBoolean("enableAdmin", false); }
	public static boolean enableAdmin(boolean val) { gPrefs.edit().putBoolean("enableAdmin", val).commit(); return val; }

	public static boolean enableConfirm() { return gPrefs.getBoolean("enableConfirm", false); }
	public static boolean enableConfirm(boolean val) { gPrefs.edit().putBoolean("enableConfirm", val).commit(); return val; }

	public static boolean enableMultiProfile() { return gPrefs.getBoolean("enableMultiProfile", false); }
	public static boolean enableMultiProfile(boolean val) { gPrefs.edit().putBoolean("enableMultiProfile", val).commit(); return val; }

	public static boolean showUid() { return gPrefs.getBoolean("showUid", false); }
	public static boolean showUid(boolean val) { gPrefs.edit().putBoolean("showUid", val).commit(); return val; }

	public static boolean notifyAppInstall() { return gPrefs.getBoolean("notifyAppInstall", false); }
	public static boolean notifyAppInstall(boolean val) { gPrefs.edit().putBoolean("notifyAppInstall", val).commit(); return val; }

	public static boolean disableIcons() { return gPrefs.getBoolean("disableIcons", false); }
	public static boolean disableIcons(boolean val) { gPrefs.edit().putBoolean("disableIcons", val).commit(); return val; }

	public static String ip_path() { return gPrefs.getString("ip_path", "2"); }
	public static String ip_path(String val) { gPrefs.edit().putString("ip_path", val).commit(); return val; }

	public static String bb_path() { return gPrefs.getString("bb_path", "2"); }
	public static String bb_path(String val) { gPrefs.edit().putString("bb_path", val).commit(); return val; }

	public static String locale() { return gPrefs.getString("locale", "en"); }
	public static String locale(String val) { gPrefs.edit().putString("locale", val).commit(); return val; }

	public static int storedPosition() { return gPrefs.getInt("storedPosition", 0); }
	public static int storedPosition(int val) { gPrefs.edit().putInt("storedPosition", val).commit(); return val; }

	public static int sysColor() { return gPrefs.getInt("sysColor", Color.RED); }
	public static int sysColor(int val) { gPrefs.edit().putInt("sysColor", val).commit(); return val; }

	
	public static boolean activeRules() { return gPrefs.getBoolean("activeRules", true); }
	
	public static boolean usePatterns() { return gPrefs.getBoolean("usePatterns", false); }
	
	
	public void onCreate() {
		super.onCreate();
		ctx = this.getApplicationContext();
		reloadPrefs();
	}

	public static void reloadPrefs() {
		gPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

		String profileName;
		int pos = storedPosition();
		if (enableMultiProfile() && pos >= 0 && pos <= 3) {
			profileName = profiles[pos];
		} else {
			profileName = profiles[0];
		}
		Api.PREFS_NAME = profileName;

		pPrefs = ctx.getSharedPreferences(profileName, Context.MODE_PRIVATE);
		sPrefs = ctx.getSharedPreferences("AFWallStaus" /* sic */, Context.MODE_PRIVATE);
	}
	
}
