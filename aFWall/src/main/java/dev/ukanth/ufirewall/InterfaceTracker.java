/**
 * Keep track of wifi/3G/tethering status and LAN IP ranges.
 * <p/>
 * Copyright (C) 2013 Kevin Cernekee
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.Formatter;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Iterator;

import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootShellService.RootCommand;
import dev.ukanth.ufirewall.util.G;

public final class InterfaceTracker {

    public static final String TAG = "AFWall";

    public static final String ITFS_WIFI[] = {"eth+", "wlan+", "tiwlan+", "ra+", "bnep+"};

    public static final String ITFS_3G[] = {"rmnet+", "pdp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", "ccemni+",
            "wwan+", "cdma_rmnet+", "usb+", "rmnet_usb+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+"};

    public static final String ITFS_VPN[] = {"tun+", "ppp+", "tap+"};

    public static final String BOOT_COMPLETED = "BOOT_COMPLETED";
    public static final String CONNECTIVITY_CHANGE = "CONNECTIVITY_CHANGE";

    public static final int ERROR_NOTIFICATION_ID = 1;

    public static long LAST_APPLIED_TIMESTAMP;

    private static final int NOTIF_ID = 10221;
    private static InterfaceDetails currentCfg = null;

    private static String truncAfter(String in, String regexp) {
        return in.split(regexp)[0];
    }

    private static class NewInterfaceScanner {

        public static void populateLanMasks(InterfaceDetails ret) {
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

    /*public static boolean isIPv6() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (!intf.isUp() || intf.isLoopback()) {
                    continue;
                }
                Iterator<InterfaceAddress> addrList = intf.getInterfaceAddresses().iterator();
                while (addrList.hasNext()) {
                    InterfaceAddress addr = addrList.next();
                    InetAddress ip = addr.getAddress();
                    if (ip instanceof Inet6Address) {
                       Log.i(TAG, "Found ipv6 type: "  + ip);
                       return true;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, "Exception in Get IP Address: " + ex.toString());
        } catch (Exception ex) {
            Log.e(TAG, "Exception : " + ex.toString());
        }
        return false;
    }*/

    private static void getTetherStatus(Context context, InterfaceDetails d) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();

        d.isTethered = false;
        d.tetherStatusKnown = false;

        for (Method method : wmMethods) {
            if (method.getName().equals("isWifiApEnabled")) {
                try {
                    d.isTethered = ((Boolean) method.invoke(wifi)).booleanValue();
                    d.tetherStatusKnown = true;
                    Log.d(TAG, "isWifiApEnabled is " + d.isTethered);
                } catch (Exception e) {
                    Log.e(Api.TAG, e.getMessage());
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
        try {
            getTetherStatus(context, ret);
        } catch (Exception e) {
            Log.i(Api.TAG, "Exception in  getInterfaceDetails.checkTether" + e.getLocalizedMessage());
        }
        NewInterfaceScanner.populateLanMasks(ret);
        return ret;
    }

    public static boolean isNetworkUp(Context context) {
        return getInterfaceDetails(context).netEnabled;
    }

    public static boolean checkForNewCfg(Context context) {
        InterfaceDetails newCfg = getInterfaceDetails(context);

        //always check for new config
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void errorNotification(Context ctx) {
        NotificationManager mNotificationManager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.cancel(NOTIF_ID);
        // Artificial stack so that navigating backward leads back to the Home screen
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
                .addParentStack(MainActivity.class)
                .addNextIntent(new Intent(ctx, MainActivity.class));

        Notification notification = new NotificationCompat.Builder(ctx)
                .setContentTitle(ctx.getString(R.string.error_notification_title))
                .setContentText(ctx.getString(R.string.error_notification_text))
                .setTicker(ctx.getString(R.string.error_notification_ticker))
                .setSmallIcon(R.drawable.notification_warn)
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
                            Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                        } else {
                            // error details are already in logcat
                            // but lets try to run the full rules once
                            Log.i(TAG, reason + ": applying full rules at " + System.currentTimeMillis());
                            Api.applySavedIptablesRules(ctx, false, new RootCommand()
                                    .setFailureToast(R.string.error_apply)
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode == 0) {
                                                LAST_APPLIED_TIMESTAMP = System.currentTimeMillis();
                                                Log.d(TAG, LAST_APPLIED_TIMESTAMP + " time of apply");
                                                Log.i(TAG, reason + ": applied rules");
                                            } else {
                                                Log.i(TAG, reason + ": applying rules with full flush at " + System.currentTimeMillis());
                                                Api.flushAllRules(ctx, new RootCommand());
                                                Api.applySavedIptablesRules(ctx, false, new RootCommand()
                                                        .setFailureToast(R.string.error_apply)
                                                        .setCallback(new RootCommand.Callback() {
                                                            @Override
                                                            public void cbFunc(RootCommand state) {
                                                                if (state.exitCode == 0) {
                                                                    LAST_APPLIED_TIMESTAMP = System.currentTimeMillis();
                                                                    Log.i(TAG, LAST_APPLIED_TIMESTAMP + " time of apply");
                                                                    Log.i(TAG, reason + ": applied rules");
                                                                } else {
                                                                    errorNotification(ctx);
                                                                    Api.allowDefaultChains(ctx);
                                                                }
                                                            }
                                                        }));
                                            }
                                        }
                                    }));
                        }
                    }
                }));
        if (!ret) {
            Log.e(TAG, reason + ": applySavedIptablesRules() returned an error");
            Api.allowDefaultChains(ctx);
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
