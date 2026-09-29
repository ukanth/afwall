package dev.ukanth.ufirewall.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import android.os.UserHandle;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;
import dev.ukanth.ufirewall.broadcast.PackageBroadcast;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.Notifications;

public class FirewallService extends Service {

    private static final String TAG = "AFWall";
    private static final int NOTIFICATION_ID = 1;
    private static boolean logServiceActive = false; // Track if LogService is running
    private static FirewallService instance = null; // Track service instance
    BroadcastReceiver connectivityReciver;
    BroadcastReceiver packageReceiver;
    private LauncherApps.Callback profileAppCallback;
    IntentFilter filter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothProfile.ServiceListener btListener;
    private static BluetoothProfile btPanProfile;
    private static boolean btConnectionRequested = false; // Track if connection was requested
    public Context context;
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        instance = this;
        // logServiceActive is not reset here: LogService may have started first (it sets the flag
        // when it starts and clears it when it stops)
        Log.d(TAG, "FirewallService created, log monitoring " + (logServiceActive ? "active" : "inactive"));
    }

    private void registerBTListener() {
        // Only create listener if it doesn't exist to prevent leaks
        if (btListener == null) {
            btListener = new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    Log.d(G.TAG, "BluetoothProfile.ServiceListener connected");
                    btPanProfile = proxy;
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    Log.d(G.TAG, "BluetoothProfile.ServiceListener disconnected");
                    btPanProfile = null; // Clear reference on disconnect
                }
            };
        }
    }


    private void addNotification() {
        // one builder for the status notification (see Notifications); updating a foreground
        // notification in place, without cancelling it first, avoids flicker
        Notification notification = Notifications.buildStatus(this, logServiceActive);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, notification);
        } else if (G.activeNotification()) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    /**
     * New apps in other Android users of this device (work profile, Private Space, clones): their
     * PACKAGE_ADDED broadcasts go to those users only.
     */
    private void registerProfileAppListener() {
        if (profileAppCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        final LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (launcherApps == null) {
            return;
        }
        profileAppCallback = new LauncherApps.Callback() {
            @Override
            public void onPackageAdded(String packageName, UserHandle user) {
                if (android.os.Process.myUserHandle().equals(user)) {
                    return; // handled by PackageBroadcast
                }
                PackageBroadcast.onProfilePackageAdded(getApplicationContext(), launcherApps, packageName, user);
            }

            @Override
            public void onPackageRemoved(String packageName, UserHandle user) {
            }

            @Override
            public void onPackageChanged(String packageName, UserHandle user) {
            }

            @Override
            public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            }

            @Override
            public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
            }
        };
        try {
            launcherApps.registerCallback(profileAppCallback);
        } catch (Exception e) {
            Log.w(TAG, "Unable to watch apps of other profiles: " + e.getMessage());
            profileAppCallback = null;
        }
    }

    /**
     * Update notification when LogService status changes
     */
    public static void setLogServiceActive(boolean active) {
        logServiceActive = active;
        if (instance != null) {
            instance.addNotification();
        }
    }

    /**
     * Check if FirewallService is running
     */
    public static boolean isInstanceRunning() {
        return instance != null;
    }

    /**
     * Start the service if it isn't running. Android 12+ refuses foreground-service starts while
     * the app is in the background (except e.g. a real boot); that is logged, not thrown.
     */
    public static void ensureRunning(Context ctx) {
        if (ctx == null || isInstanceRunning()) {
            return;
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(ctx, new Intent(ctx, FirewallService.class));
        } catch (Exception e) {
            Log.w(TAG, "Unable to start FirewallService: " + e.getMessage());
        }
    }
    
    /**
     * Refresh the notification
     */
    public static void refreshNotification() {
        if (instance != null) {
            Log.d(TAG, "Refreshing FirewallService notification");
            // Ensure we run on the main thread
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (instance != null) {
                    instance.addNotification();
                    Log.d(TAG, "FirewallService notification refreshed");
                }
            });
        } else {
            Log.w(TAG, "Cannot refresh notification - FirewallService instance is null");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        addNotification();
        registerBTListener();
        registerProfileAppListener();

        //incase if it's not null, make sure we unregister it
        if(packageReceiver != null) {
            unregisterReceiver(packageReceiver);
        }

        if (connectivityReciver != null) {
            unregisterReceiver(connectivityReciver);
        }

        connectivityReciver = new ConnectivityChangeReceiver();
        filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityChangeReceiver.TETHER_STATE_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectivityReciver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(connectivityReciver, filter);
        }

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        packageReceiver = new PackageBroadcast();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, intentFilter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, intentFilter);
        }


        intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, intentFilter,RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, intentFilter);
        }

        // TEMPORARY: Bluetooth initialization completely disabled to prevent connection leaks
        Log.d(G.TAG, "Bluetooth initialization disabled to prevent service connection leaks");

        return START_STICKY;
    }

    private BluetoothAdapter getBTAdapter(Context context) {
        BluetoothAdapter bluetoothAdapter = null;
        PackageManager pm = context.getPackageManager();
        boolean hasBluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (hasBluetooth) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            
            // TEMPORARY: Disable Bluetooth profile connection to prevent service leaks
            // TODO: Find better way to handle Bluetooth tethering detection without connection leaks
            Log.d(G.TAG, "Bluetooth PAN profile connection disabled to prevent service leaks");
        } else {
            Log.d(G.TAG, "Device does not support Bluetooth, skipping");
        }
        return bluetoothAdapter;
    }
    @Override
    public void onDestroy() {
        if (profileAppCallback != null) {
            try {
                ((LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE)).unregisterCallback(profileAppCallback);
            } catch (Exception ignored) {
            }
            profileAppCallback = null;
        }
        if (connectivityReciver != null) {
            unregisterReceiver(connectivityReciver);
            connectivityReciver = null;
        }
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
            packageReceiver = null;
        }

        // Close bluetooth profile connection to prevent ServiceConnection leak
        if(bluetoothAdapter != null) {
            try {
                if(btPanProfile != null) {
                    bluetoothAdapter.closeProfileProxy(5, btPanProfile); // BluetoothProfile.PAN
                    btPanProfile = null;
                    Log.d(G.TAG, "Closed Bluetooth PAN profile proxy");
                }
            } catch (Exception e){
                Log.e(G.TAG, "Error closing bt profile", e);
            } finally {
                // Always clean up references regardless of profile state
                btListener = null;
                bluetoothAdapter = null;
                btConnectionRequested = false; // Reset connection flag
                Log.d(G.TAG, "Bluetooth cleanup completed");
            }
        } else if (btConnectionRequested || btPanProfile != null) {
            // Edge case: Clean up even if adapter is null
            Log.w(G.TAG, "Bluetooth adapter is null but connection state exists, cleaning up");
            btPanProfile = null;
            btListener = null;
            btConnectionRequested = false;
        }
        super.onDestroy();
    }



    public static BluetoothProfile getBtPanProfile() {
        // TEMPORARY: Return null to disable Bluetooth tethering detection
        // This prevents service connection leaks while we find a better solution
        Log.d(G.TAG, "Bluetooth PAN profile disabled, returning null");
        return null;
    }
}
