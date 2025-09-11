package dev.ukanth.ufirewall.service;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;
import dev.ukanth.ufirewall.broadcast.PackageBroadcast;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

public class FirewallService extends Service {

    private static final int NOTIFICATION_ID = 1;
    BroadcastReceiver connectivityReciver;
    BroadcastReceiver packageReceiver;
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
        String NOTIFICATION_CHANNEL_ID = "firewall.service";
        String channelName = getString(R.string.firewall_service);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            assert manager != null;
            if(G.getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            } else {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_LOW);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.enableLights(false);
            notificationChannel.setShowBadge(true);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }


        Intent appIntent = new Intent(this, MainActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);


        /*TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);*/

        int icon;
        String notificationText = "";

        if (Api.isEnabled(this)) {
            if (G.enableMultiProfile()) {
                String profile = "";
                switch (G.storedProfile()) {
                    case "AFWallPrefs":
                        profile = G.gPrefs.getString("default", getString(R.string.defaultProfile));
                        break;
                    case "AFWallProfile1":
                        profile = G.gPrefs.getString("profile1", getString(R.string.profile1));
                        break;
                    case "AFWallProfile2":
                        profile = G.gPrefs.getString("profile2", getString(R.string.profile2));
                        break;
                    case "AFWallProfile3":
                        profile = G.gPrefs.getString("profile3", getString(R.string.profile3));
                        break;
                    default:
                        profile = G.storedProfile();
                        break;
                }
                notificationText = getString(R.string.active) + " (" + profile + ")";
            } else {
                notificationText = getString(R.string.active);
            }
            //notificationText = context.getString(R.string.active);
            icon = R.drawable.notification;
        } else {
            notificationText = getString(R.string.inactive);
            icon = R.drawable.notification_error;
        }


        PendingIntent notifyPendingIntent = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);

        //int notifyType = G.getNotificationPriority();
        Notification notification = notificationBuilder
                .setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_name))
                .setSound(null)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentText(notificationText)
                .setSmallIcon(icon)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            if(G.activeNotification()) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
        /*} else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, notification);
            } else {
                //empty one
                startForeground(NOTIFICATION_ID, new Notification());
            }
        }*/


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        addNotification();
        registerBTListener();

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
