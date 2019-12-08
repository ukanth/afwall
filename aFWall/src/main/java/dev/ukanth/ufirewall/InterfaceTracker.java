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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

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
            "wwan+", "cdma_rmnet+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+", "r_rmnet_data+"};

    public static final String ITFS_VPN[] = {"tun+", "ppp+", "tap+"};

    public static final String ITFS_TETHER[] = {"bt-pan","usb+", "rndis+","rmnet_usb+"};

    public static final String BOOT_COMPLETED = "BOOT_COMPLETED";
    public static final String CONNECTIVITY_CHANGE = "CONNECTIVITY_CHANGE";
    public static final String TETHER_STATE_CHANGED = "TETHER_STATE_CHANGED";


    private static InterfaceDetails currentCfg = null;

    private static String truncAfter(String in, String regexp) {
        return in.split(regexp)[0];
    }

    private static void getTetherStatus(Context context, InterfaceDetails d) {
        getWifiTetherStatus(context, d);
        getBluetoothTetherStatus(context, d);
        getUsbTetherStatus(context, d);
    }

    private static void getWifiTetherStatus(Context context, InterfaceDetails d) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();

        d.isWifiTethered = false;
        d.tetherWifiStatusKnown = false;

        for (Method method : wmMethods) {
            if (method.getName().equals("isWifiApEnabled")) {
                try {
                    d.isWifiTethered = ((Boolean) method.invoke(wifi)).booleanValue();
                    d.tetherWifiStatusKnown = true;
                    Log.d(TAG, "isWifiApEnabled is " + d.isWifiTethered);
                } catch (Exception e) {
                    Log.e(Api.TAG, android.util.Log.getStackTraceString(e));
                }
            }
        }
    }

    private static BluetoothProfile btPanProfile;
    private static BluetoothProfile.ServiceListener btListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG, "BluetoothProfile.ServiceListener connected");
            btPanProfile = proxy;
        }

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "BluetoothProfile.ServiceListener disconected");
        }
    };

    // To get bluetooth tethering, we need valid BluetoothPan instance
    // It is obtainable only in ServiceListener.onServiceConnected callback
    public static void setupBluetoothProfile(Context context) {
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(context, btListener, 5);
    }

    private static void getBluetoothTetherStatus(Context context, InterfaceDetails d) {
        if (btPanProfile != null) {
            Method[] btMethods = btPanProfile.getClass().getDeclaredMethods();

            d.isBluetoothTethered = false;
            d.tetherBluetoothStatusKnown = false;

            for (Method method : btMethods) {
                if (method.getName().equals("isTetheringOn")) {
                    try {
                        d.isBluetoothTethered = ((Boolean) method.invoke(btPanProfile)).booleanValue();
                        d.tetherBluetoothStatusKnown = true;
                        Log.d(TAG, "isBluetoothTetheringOn is " + d.isBluetoothTethered);
                    } catch (Exception e) {
                        Log.e(Api.TAG, android.util.Log.getStackTraceString(e));
                    }
                }
            }
        }
    }

    private static void getUsbTetherStatus(Context context, InterfaceDetails d) {
        // TODO
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
        Log.i(TAG, "Getting interface details...");

        currentCfg = newCfg;

        if (!newCfg.netEnabled) {
            Log.i(TAG, "Now assuming NO connection (all interfaces down)");
        } else {
            if (newCfg.netType == ConnectivityManager.TYPE_WIFI) {
                Log.i(TAG, "Now assuming wifi connection (" +
                        "bluetooth-tethered: " + (newCfg.isBluetoothTethered ? "yes" : "no") + ", " +
                        "usb-tethered: " + (newCfg.isUsbTethered ? "yes" : "no") + ")");
            } else if (newCfg.netType == ConnectivityManager.TYPE_MOBILE) {
                Log.i(TAG, "Now assuming 3G connection (" +
                        "roaming: " + (newCfg.isRoaming ? "yes" : "no") +
                        "wifi-tethered: " + (newCfg.isWifiTethered ? "yes" : "no") + ", " +
                        "bluetooth-tethered: " + (newCfg.isBluetoothTethered ? "yes" : "no") + ", " +
                        "usb-tethered: " + (newCfg.isUsbTethered ? "yes" : "no") + ")");
            } else if (newCfg.netType == ConnectivityManager.TYPE_BLUETOOTH) {
                Log.i(TAG, "Now assuming bluetooth connection (" +
                        "wifi-tethered: " + (newCfg.isWifiTethered ? "yes" : "no") + ", " +
                        "usb-tethered: " + (newCfg.isUsbTethered ? "yes" : "no") + ")");
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

    public static InterfaceDetails getCurrentCfg(Context context, boolean force) {
        if (currentCfg == null || force) {
            currentCfg = getInterfaceDetails(context);
        }
        return currentCfg;
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

        if (reason.equals(InterfaceTracker.BOOT_COMPLETED)) {
            applyBootRules(reason);
        } else {
            applyRules(reason);
        }
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
                                                Api.errorNotification(ctx);
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
                                                Api.errorNotification(ctx);
                                            }
                                            Api.applyDefaultChains(ctx, new RootCommand()
                                                    .setFailureToast(R.string.error_apply)
                                                    .setCallback(new RootCommand.Callback() {
                                                        @Override
                                                        public void cbFunc(RootCommand state) {
                                                            if (state.exitCode != 0) {
                                                                Api.errorNotification(ctx);
                                                            }
                                                        }
                                                    }));
                                        }
                                    }));
                        }
                    }
                }));
    }

    public static void applyBootRules(final String reason) {
        Api.applySavedIptablesRules(ctx, true, new RootCommand()
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
                                                Api.errorNotification(ctx);
                                            }
                                        }
                                    }));
                        } else {
                            //lets try applying all rules
                            Api.setRulesUpToDate(false);
                            Api.applySavedIptablesRules(ctx, true, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode == 0) {
                                                Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                                            } else {
                                                Log.e(TAG, reason + ": applySavedIptablesRules() returned an error");
                                                Api.errorNotification(ctx);
                                            }
                                            Api.applyDefaultChains(ctx, new RootCommand()
                                                    .setFailureToast(R.string.error_apply)
                                                    .setCallback(new RootCommand.Callback() {
                                                        @Override
                                                        public void cbFunc(RootCommand state) {
                                                            if (state.exitCode != 0) {
                                                                Api.errorNotification(ctx);
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
                            Log.i(TAG, "Found ipv4: " + mask );
                            ret.lanMaskV4 = mask;
                        } else if (ip instanceof Inet6Address) {
                            Log.i(TAG, "Found ipv6: " + mask );
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
