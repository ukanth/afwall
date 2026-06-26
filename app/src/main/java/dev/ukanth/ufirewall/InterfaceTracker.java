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

import static dev.ukanth.ufirewall.util.G.ctx;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;

import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

public final class InterfaceTracker {

    public static final String TAG = "AFWall";

    public static final String[] ITFS_WIFI = {"eth+", "wlan+", "tiwlan+", "ra+", "bnep+"};

    public static final String[] ITFS_3G = {"rmnet+", "pdp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", "ccemni+",
            "wwan+", "cdma_rmnet+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+", "r_rmnet_data+"};

    public static final String[] ITFS_VPN = {"tun+", "ppp+", "tap+", "wg+"};

    public static final String[] ITFS_TETHER = {"bt-pan", "bnep+", "usb+", "rndis+", "rmnet_usb+"};

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
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
                    Log.e(G.TAG, "Exception in getting Wifi tether status");
                    Log.e(Api.TAG, android.util.Log.getStackTraceString(e));
                }
            }
        }
    }




    // To get bluetooth tethering, we need valid BluetoothPan instance
    // It is obtainable only in ServiceListener.onServiceConnected callback


    public static BluetoothProfile getBtProfile() {
        return FirewallService.getBtPanProfile();
    }

    private static void getBluetoothTetherStatus(Context context, InterfaceDetails d) {
        if (FirewallService.getBtPanProfile() != null) {
            Method[] btMethods = FirewallService.getBtPanProfile().getClass().getDeclaredMethods();

            d.isBluetoothTethered = false;
            d.tetherBluetoothStatusKnown = false;

            for (Method method : btMethods) {
                if (method.getName().equals("isTetheringOn")) {
                    try {
                        d.isBluetoothTethered = ((Boolean) method.invoke(FirewallService.getBtPanProfile())).booleanValue();
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
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            d.isUsbTethered = false;
            d.tetherUsbStatusKnown = false;
            return;
        }

        d.isUsbTethered = false;
        d.tetherUsbStatusKnown = false;

        try {
            // Use reflection to access hidden getTetheredIfaces() method
            Method getTetheredIfaces = cm.getClass().getDeclaredMethod("getTetheredIfaces");
            getTetheredIfaces.setAccessible(true);
            String[] tetheredIfaces = (String[]) getTetheredIfaces.invoke(cm);
            
            if (tetheredIfaces != null) {
                for (String iface : tetheredIfaces) {
                    // USB tethering typically uses interfaces like "rndis0", "usb0", etc.
                    if (iface != null && (iface.startsWith("rndis") || iface.startsWith("usb"))) {
                        d.isUsbTethered = true;
                        break;
                    }
                }
            }
            d.tetherUsbStatusKnown = true;
            Log.d(TAG, "USB tethering status: " + d.isUsbTethered);
            
        } catch (Exception e) {
            Log.e(G.TAG, "Exception in getting USB tether status");
            Log.e(Api.TAG, android.util.Log.getStackTraceString(e));
            
            // Fallback: Check if USB tethering interface exists using NetworkInterface
            try {
                d.isUsbTethered = isUsbTetherInterfaceUp();
                d.tetherUsbStatusKnown = true;
                Log.d(TAG, "USB tethering status (fallback): " + d.isUsbTethered);
            } catch (Exception fallbackException) {
                Log.e(Api.TAG, "Fallback USB tether detection failed: " + android.util.Log.getStackTraceString(fallbackException));
            }
        }
    }
    
    private static boolean isUsbTetherInterfaceUp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                String name = networkInterface.getName();
                if (name != null && (name.startsWith("rndis") || name.startsWith("usb")) && networkInterface.isUp()) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(Api.TAG, "Error checking network interfaces: " + android.util.Log.getStackTraceString(e));
        }
        return false;
    }

    private static InterfaceDetails getInterfaceDetails(Context context) {

        InterfaceDetails ret = new InterfaceDetails();

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info = cm.getActiveNetworkInfo();

        if (info == null || !info.isConnected()) {
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
            if(G.enableTether()) {
                getTetherStatus(context, ret);
            }
        } catch (Exception e) {
            Log.i(Api.TAG, "Exception in  getInterfaceDetails.checkTether" + e.getLocalizedMessage());
        }
        NewInterfaceScanner.populateLanMasks(context, ret);
        getDnsServers(context, ret);
        return ret;
    }
    
    private static void getDnsServers(Context context, InterfaceDetails d) {
        d.dnsServersV4.clear();
        d.dnsServersV6.clear();
        
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            
            // Get active network
            android.net.Network activeNetwork = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                activeNetwork = cm.getActiveNetwork();
            }
            
            if (activeNetwork != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Modern approach using LinkProperties (API 23+)
                android.net.LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
                if (linkProperties != null) {
                    for (java.net.InetAddress dns : linkProperties.getDnsServers()) {
                        if (dns instanceof java.net.Inet4Address) {
                            d.dnsServersV4.add(dns.getHostAddress());
                        } else if (dns instanceof java.net.Inet6Address) {
                            d.dnsServersV6.add(dns.getHostAddress());
                        }
                    }
                }
            }
            
            // Fallback: Try system properties (older Android versions or backup method)
            if (d.dnsServersV4.isEmpty() && d.dnsServersV6.isEmpty()) {
                getDnsFromSystemProperties(d);
            }
            
            Log.d(TAG, "DNS servers IPv4: " + d.dnsServersV4);
            Log.d(TAG, "DNS servers IPv6: " + d.dnsServersV6);
            
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception getting DNS servers: " + android.util.Log.getStackTraceString(e));
        }
    }
    
    private static void getDnsFromSystemProperties(InterfaceDetails d) {
        try {
            // Try common system properties for DNS servers
            String[] dnsProps = {"net.dns1", "net.dns2", "net.dns3", "net.dns4"};
            
            for (String prop : dnsProps) {
                String dnsServer = System.getProperty(prop);
                if (dnsServer != null && !dnsServer.isEmpty() && !dnsServer.equals("0.0.0.0")) {
                    try {
                        java.net.InetAddress addr = java.net.InetAddress.getByName(dnsServer);
                        if (addr instanceof java.net.Inet4Address) {
                            if (!d.dnsServersV4.contains(dnsServer)) {
                                d.dnsServersV4.add(dnsServer);
                            }
                        } else if (addr instanceof java.net.Inet6Address) {
                            if (!d.dnsServersV6.contains(dnsServer)) {
                                d.dnsServersV6.add(dnsServer);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Invalid DNS server address: " + dnsServer);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception getting DNS from system properties: " + android.util.Log.getStackTraceString(e));
        }
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

            if (!newCfg.lanMaskV4.isEmpty()) {
                Log.i(TAG, "IPv4 LAN netmasks on " + newCfg.wifiName + ": " + String.join(", ", newCfg.lanMaskV4));
            }
            if (!newCfg.lanMaskV6.isEmpty()) {
                Log.i(TAG, "IPv6 LAN netmasks on " + newCfg.wifiName + ": " + String.join(", ", newCfg.lanMaskV6));
            }
            if (newCfg.lanMaskV6.isEmpty() && newCfg.lanMaskV4.isEmpty()) {
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
        Log.d(TAG, reason + " applying rules");
        // update Api.PREFS_NAME so we pick up the right profile
        // REVISIT: this can be removed once we're confident that G is in sync with profile changes
        G.reloadPrefs();

        if (reason.equals(InterfaceTracker.BOOT_COMPLETED) || reason.startsWith(InterfaceTracker.BOOT_COMPLETED)) {
            Log.i(TAG, "Applying boot-specific rules for reason: " + reason);
            applyBootRules(ctx, reason);
        } else {
            Log.i(TAG, "Applying regular rules for reason: " + reason);
            applyRules(ctx, reason);
        }
    }

    public static void applyRules(final Context appCtx, final String reason) {
        final Context safeCtx = appCtx != null ? appCtx : ctx;
        if (safeCtx == null) {
            Log.e(TAG, "Cannot apply rules: no context available");
            return;
        }
        Api.fastApply(safeCtx, new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                            Api.applyDefaultChains(safeCtx, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode != 0) {
                                                Api.errorNotification(safeCtx);
                                            }
                                        }
                                    }));
                        } else {
                            //lets try applying all rules
                            Api.setRulesUpToDate(false);
                            Api.fastApply(safeCtx, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode == 0) {
                                                Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                                            } else {
                                                Log.e(TAG, reason + ": applySavedIptablesRules() returned an error");
                                                Api.errorNotification(safeCtx);
                                            }
                                            Api.applyDefaultChains(safeCtx, new RootCommand()
                                                    .setFailureToast(R.string.error_apply)
                                                    .setCallback(new RootCommand.Callback() {
                                                        @Override
                                                        public void cbFunc(RootCommand state) {
                                                            if (state.exitCode != 0) {
                                                                Api.errorNotification(safeCtx);
                                                            }
                                                        }
                                                    }));
                                        }
                                    }));
                        }
                    }
                }));
    }

    public static void applyBootRules(final Context appCtx, final String reason) {
        final Context safeCtx = appCtx != null ? appCtx : ctx;
        if (safeCtx == null) {
            Log.e(TAG, "Cannot apply boot rules: no context available");
            return;
        }
        Api.applySavedIptablesRules(safeCtx, true, new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                            Api.applyDefaultChains(safeCtx, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode != 0) {
                                                Api.errorNotification(safeCtx);
                                            }
                                        }
                                    }));
                        } else {
                            //lets try applying all rules
                            Api.setRulesUpToDate(false);
                            Api.applySavedIptablesRules(safeCtx, true, new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode == 0) {
                                                Log.i(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                                            } else {
                                                Log.e(TAG, reason + ": applySavedIptablesRules() returned an error");
                                                Api.errorNotification(safeCtx);
                                            }
                                            Api.applyDefaultChains(safeCtx, new RootCommand()
                                                    .setFailureToast(R.string.error_apply)
                                                    .setCallback(new RootCommand.Callback() {
                                                        @Override
                                                        public void cbFunc(RootCommand state) {
                                                            if (state.exitCode != 0) {
                                                                Api.errorNotification(safeCtx);
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

        private static boolean isWifiLikeInterface(String name) {
            if (name == null) {
                return false;
            }
            for (String pattern : ITFS_WIFI) {
                if (name.startsWith(truncAfter(pattern, "\\+"))) {
                    return true;
                }
            }
            return false;
        }

        private static void addMask(InterfaceDetails ret, InetAddress ip, int prefixLength) {
            if (ip == null) {
                return;
            }
            String mask = truncAfter(ip.getHostAddress(), "%") + "/" + prefixLength;
            if (ip instanceof Inet4Address) {
                if (!ret.lanMaskV4.contains(mask)) {
                    ret.lanMaskV4.add(mask);
                }
            } else if (ip instanceof Inet6Address) {
                if (!ret.lanMaskV6.contains(mask)) {
                    ret.lanMaskV6.add(mask);
                }
            }
        }

        private static void populateLanMasksFromRoutes(Context context, InterfaceDetails ret) {
            if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return;
            }
            try {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm == null) {
                    return;
                }
                for (android.net.Network network : cm.getAllNetworks()) {
                    LinkProperties linkProperties = cm.getLinkProperties(network);
                    if (linkProperties == null) {
                        continue;
                    }
                    if (!isWifiLikeInterface(linkProperties.getInterfaceName())) {
                        continue;
                    }
                    for (RouteInfo route : linkProperties.getRoutes()) {
                        if (route == null || route.isDefaultRoute() || route.getDestination() == null) {
                            continue;
                        }
                        String mask = route.getDestination().toString();
                        InetAddress addr = route.getDestination().getAddress();
                        if (addr instanceof Inet4Address) {
                            if (!ret.lanMaskV4.contains(mask)) {
                                ret.lanMaskV4.add(mask);
                            }
                        } else if (addr instanceof Inet6Address) {
                            if (!ret.lanMaskV6.contains(mask)) {
                                ret.lanMaskV6.add(mask);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "Error fetching LAN routes: " + android.util.Log.getStackTraceString(e));
            }
        }

        public static void populateLanMasks(Context context, InterfaceDetails ret) {
            try {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

                while (en.hasMoreElements()) {
                    NetworkInterface intf = en.nextElement();
                    boolean match = false;

                    if (!intf.isUp() || intf.isLoopback()) {
                        continue;
                    }

                    match = isWifiLikeInterface(intf.getName());
                    if (!match)
                        continue;
                    ret.wifiName = intf.getName();

                    // Collect ALL subnets from this interface (Issue #1362)
                    Iterator<InterfaceAddress> addrList = intf.getInterfaceAddresses().iterator();
                    while (addrList.hasNext()) {
                        InterfaceAddress addr = addrList.next();
                        InetAddress ip = addr.getAddress();
                        addMask(ret, ip, addr.getNetworkPrefixLength());
                    }
                    if (ret.lanMaskV4.isEmpty() && ret.lanMaskV6.isEmpty()) {
                        ret.noIP = true;
                    }
                }
                populateLanMasksFromRoutes(context, ret);
            } catch (Exception e) {
                Log.i(TAG, "Error fetching network interface list: " + android.util.Log.getStackTraceString(e));
            }
        }
    }
}
