/**
 * Keep track of wifi/3G/tethering status and LAN IP ranges.
 *
 * Copyright (C) 2013 Kevin Cernekee
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

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;

import dev.ukanth.ufirewall.RootShell.RootCommand;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import dev.ukanth.ufirewall.Log;

public final class InterfaceTracker {

	public static final String TAG = "AFWall";

	public static final String ITFS_WIFI[] = { "eth+", "wlan+", "tiwlan+", "ra+", "bnep+" };
	
	public static final String ITFS_3G[] = { "rmnet+", "pdp+", "uwbr+","wimax+", "vsnet+", 
											 "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", 
											 "wwan+", "cdma_rmnet+", "usb+", "rmnet_usb+","clat4+", "cc2mni+", "bond1+", "rmnet_smux+" };
	
	public static final String ITFS_VPN[] = { "tun+", "ppp+", "tap+" };

	public static final String BOOT_COMPLETED = "BOOT_COMPLETED";
	public static final String CONNECTIVITY_CHANGE = "CONNECTIVITY_CHANGE";

	public static final int ERROR_NOTIFICATION_ID = 1;

	private static InterfaceDetails currentCfg = null;

	private static class OldInterfaceScanner {

		private static String intToDottedQuad(int ip) {
			return String.format(Locale.US, "%d.%d.%d.%d",
					(ip >>>  0) & 0xff,
					(ip >>>  8) & 0xff,
					(ip >>> 16) & 0xff,
					(ip >>> 24) & 0xff);
		}

		public static void populateLanMasks(Context context, String[] names, InterfaceDetails ret) {
			WifiManager wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
			DhcpInfo dhcp = wifi.getDhcpInfo();

			if (dhcp != null) {
				ret.lanMaskV4 = intToDottedQuad(dhcp.ipAddress) + "/" +
							    intToDottedQuad(dhcp.netmask);
				ret.wifiName = "UNKNOWN";
			}
		}
	}

	private static class NewInterfaceScanner {

		private static String truncAfter(String in, String regexp) {
			return in.split(regexp)[0];
		}

		@TargetApi(Build.VERSION_CODES.GINGERBREAD)
		public static void populateLanMasks(Context context, String[] names, InterfaceDetails ret) {
			try {
				Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

				while (en.hasMoreElements()) {
					NetworkInterface intf = en.nextElement();
					boolean match = false;

					if (!intf.isUp() || intf.isLoopback()) {
						continue;
					}

					for (String pattern : ITFS_WIFI) {
						if (intf.getName().startsWith(truncAfter(pattern, "\\+"))) {
							match = true;
							break;
						}
					}
					if (!match)
						continue;
					ret.wifiName = intf.getName();

					Iterator<InterfaceAddress> addrList = intf.getInterfaceAddresses().iterator();
					while (addrList.hasNext()) {
						InterfaceAddress addr = addrList.next();
						InetAddress ip = addr.getAddress();
						String mask = truncAfter(ip.getHostAddress(), "%") + "/" +
									  addr.getNetworkPrefixLength();

						if (ip instanceof Inet4Address) {
							ret.lanMaskV4 = mask;
						} else if (ip instanceof Inet6Address) {
							ret.lanMaskV6 = mask;
						}
					}
				}
			} catch (SocketException e) {
				Log.e(TAG, "Error fetching network interface list");
			} catch (Exception e) {
				Log.e(TAG, "Error fetching network interface list");
			}
		}
	}

	private static void getTetherStatus(Context context, InterfaceDetails d) {
		WifiManager wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		Method[] wmMethods = wifi.getClass().getDeclaredMethods();

		d.isTethered = false;
		d.tetherStatusKnown = false;

		for(Method method: wmMethods) {
			if(method.getName().equals("isWifiApEnabled")) {
				try {
					d.isTethered = ((Boolean)method.invoke(wifi)).booleanValue();
					d.tetherStatusKnown = true;
					Log.d(TAG, "isWifiApEnabled is " + d.isTethered);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static InterfaceDetails getInterfaceDetails(Context context) {
		InterfaceDetails ret = new InterfaceDetails();

		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = cm.getActiveNetworkInfo();

		if (info == null || info.isConnected() == false) {
			return ret;
		}

		switch (info.getType()) {
		case ConnectivityManager.TYPE_MOBILE:
		case ConnectivityManager.TYPE_MOBILE_DUN:
		case ConnectivityManager.TYPE_MOBILE_HIPRI:
		case ConnectivityManager.TYPE_MOBILE_MMS:
		case ConnectivityManager.TYPE_MOBILE_SUPL:
		case ConnectivityManager.TYPE_WIMAX:
			ret.isRoaming = info.isRoaming();
			ret.netType = ConnectivityManager.TYPE_MOBILE;
			ret.netEnabled = true;
			break;
		case ConnectivityManager.TYPE_WIFI:
		case ConnectivityManager.TYPE_BLUETOOTH:
		case ConnectivityManager.TYPE_ETHERNET:
			ret.netType = ConnectivityManager.TYPE_WIFI;
			ret.netEnabled = true;
			break;
		}
		getTetherStatus(context, ret);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
			OldInterfaceScanner.populateLanMasks(context, ITFS_WIFI, ret);
		} else {
			NewInterfaceScanner.populateLanMasks(context, ITFS_WIFI, ret);
		}

		return ret;
	}
	
	public static boolean isNetworkUp(Context context){
		return getInterfaceDetails(context).netEnabled;
	}

	public static boolean checkForNewCfg(Context context) {
		InterfaceDetails newCfg = getInterfaceDetails(context);

		if (currentCfg != null && currentCfg.equals(newCfg)) {
			return false;
		}
		currentCfg = newCfg;

		if (!newCfg.netEnabled) {
			Log.i(TAG, "Now assuming NO connection (all interfaces down)");
		} else {
			if (newCfg.netType == ConnectivityManager.TYPE_WIFI) {
				Log.i(TAG, "Now assuming wifi connection");
			} else if (newCfg.netType == ConnectivityManager.TYPE_MOBILE) {
				Log.i(TAG, "Now assuming 3G connection (" +
					  (newCfg.isRoaming ? "roaming, " : "") +
					  (newCfg.isTethered ? "tethered" : "non-tethered") + ")");
			}

			if (!newCfg.lanMaskV4.equals("")) {
				Log.i(TAG, "IPv4 LAN netmask on " + newCfg.wifiName + ": " + newCfg.lanMaskV4);
			}
			if (!newCfg.lanMaskV6.equals("")) {
				Log.i(TAG, "IPv6 LAN netmask on " + newCfg.wifiName + ": " + newCfg.lanMaskV6);
			}
		}
		return true;
	}

	public static InterfaceDetails getCurrentCfg(Context context) {
		if (currentCfg == null) {
			currentCfg = getInterfaceDetails(context);
		}
		return currentCfg;
	}

	private static void errorNotification(Context ctx) {
		NotificationManager mNotificationManager =
				(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);

		// Artificial stack so that navigating backward leads back to the Home screen
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
				.addParentStack(MainActivity.class)
				.addNextIntent(new Intent(ctx, MainActivity.class));

		Notification notification = new NotificationCompat.Builder(ctx)
			.setContentTitle(ctx.getString(R.string.error_notification_title))
			.setContentText(ctx.getString(R.string.error_notification_text))
			.setTicker(ctx.getString(R.string.error_notification_ticker))
			.setSmallIcon(R.drawable.warn)
			.setAutoCancel(true)
			.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT))
			.build();

		mNotificationManager.notify(ERROR_NOTIFICATION_ID, notification);
	}

	public static void applyRulesOnChange(Context context, final String reason) {
		final Context ctx = context.getApplicationContext();

		if (!checkForNewCfg(ctx)) {
			Log.d(TAG, reason + ": interface state has not changed, ignoring");
			return;
		} else if (!Api.isEnabled(ctx)) {
			Log.d(TAG, reason + ": firewall is disabled, ignoring");
			return;
		}

		// update Api.PREFS_NAME so we pick up the right profile
		// REVISIT: this can be removed once we're confident that G is in sync with profile changes
		G.reloadPrefs();
		
		boolean ret = Api.fastApply(ctx, new RootCommand()
					.setFailureToast(R.string.error_apply)
					.setCallback(new RootCommand.Callback() {
						@Override
						public void cbFunc(RootCommand state) {
							if (state.exitCode == 0) {
								Log.i(TAG, reason + ": applied rules");
							} else {
								// error details are already in logcat
								//but lets try to run the full rules once
								Api.applySavedIptablesRules(ctx, false,new RootCommand()
								.setFailureToast(R.string.error_apply)
								.setCallback(new RootCommand.Callback() {
									@Override
									public void cbFunc(RootCommand state) {
										if (state.exitCode == 0) {
											Log.i(TAG, reason + ": applied rules");
										} else {
											Api.setEnabled(ctx,  false,  false);
											errorNotification(ctx);
										}
									}
								}));
							}
						}
					}));
		if (!ret) {
			Log.e(TAG, reason + ": applySavedIptablesRules() returned an error");
			errorNotification(ctx);
		}
	}

	public static String matchName(String[] patterns, String name) {
		for (String p : patterns) {
			int minLen = Math.min(p.length(), name.length());

			for (int i = 0; ; i++) {
				if (i == minLen) {
					if (name.length() == p.length()) {
						// exact match
						return p;
					}
					break;
				}
				if (name.charAt(i) != p.charAt(i)) {
					if (p.charAt(i) == '+') {
						// wildcard match
						return p;
					}
					break;
				}
			}
		}
		return null;
	}
}
