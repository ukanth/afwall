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

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;

import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.util.G.ctx;

public final class InterfaceTracker {

    public static final String TAG = "AFWall";

    public static final String ITFS_WIFI[] = {"eth+", "wlan+", "tiwlan+", "ra+", "bnep+"};

    public static final String ITFS_3G[] = {"rmnet+", "pdp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", "ccemni+",
            "wwan+", "cdma_rmnet+", "usb+", "rmnet_usb+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+", "r_rmnet_data+"};

    public static final String ITFS_VPN[] = {"tun+", "ppp+", "tap+"};

    public static final String BOOT_COMPLETED = "BOOT_COMPLETED";
    public static final String CONNECTIVITY_CHANGE = "CONNECTIVITY_CHANGE";

    public static final int ERROR_NOTIFICATION_ID = 1;
    private static final int NOTIF_ID = 10221;
    public static long LAST_APPLIED_TIMESTAMP;
    private static InterfaceDetails currentCfg = null;

    private static String truncAfter(String in, String regexp) {
        return in.split(regexp)[0];
    }

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
                    Log.e(Api.TAG, android.util.Log.getStackTraceString(e));
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
            if (newCfg.lanMaskV6.equals("") && newCfg.lanMaskV4.equals("")) {
                Log.i(TAG, "No ipaddress found");
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
        applyRules(reason);
    }

    public static void applyRules(final String reason) {
        Api.fastApply(ctx, new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                            Api.applyDefaultChains(ctx, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode != 0) {
                                                errorNotification(ctx);
                                            }
                                        }
                                    }));
                        } else {
                            //lets try applying all rules
                            Api.setRulesUpToDate(false);
                            Api.fastApply(ctx, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode == 0) {
                                                Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                                            } else {
                                                Log.e(TAG, reason + ": applySavedIptablesRules() returned an error");
                                                errorNotification(ctx);
                                            }
                                            Api.applyDefaultChains(ctx, new RootCommand()
                                                    .setFailureToast(R.string.error_apply)
                                                    .setCallback(new RootCommand.Callback() {
                                                        @Override
                                                        public void cbFunc(RootCommand state) {
                                                            if (state.exitCode != 0) {
                                                                errorNotification(ctx);
                                                            }
                                                        }
                                                    }));
                                        }
                                    }));
                        }
                    }
                }));
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
                    if (ret.lanMaskV4.equals("") && ret.lanMaskV6.equals("")) {
                        ret.noIP = true;
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "Error fetching network interface list: " + android.util.Log.getStackTraceString(e));
            }
        }
    }
}
