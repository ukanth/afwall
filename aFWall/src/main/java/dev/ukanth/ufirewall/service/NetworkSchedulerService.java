package dev.ukanth.ufirewall.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;
import dev.ukanth.ufirewall.util.G;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NetworkSchedulerService extends JobService implements
        ConnectivityChangeReceiver.ConnectivityReceiverListener {

    private static final String TAG = NetworkSchedulerService.class.getSimpleName();

    private ConnectivityChangeReceiver mConnectivityReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service created");
        mConnectivityReceiver = new ConnectivityChangeReceiver(this);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service destroyed");
    }

    /**
     * When the app's NetworkConnectionActivity is created, it starts this service. This is so that the
     * activity and this service can communicate back and forth. See "setUiCallback()"
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }


    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "onStartJob" + mConnectivityReceiver);
        registerReceiver(mConnectivityReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "onStopJob");
        unregisterReceiver(mConnectivityReceiver);
        return true;
    }

    @Override
    public void onNetworkConnectionChanged(boolean isConnected) {
        Log.i(TAG, "Network is available" + isConnected);

        if (!G.isActivityVisible() && isConnected) {
            Log.i(TAG, "Network is available.. applying rules");
            Toast.makeText(getApplicationContext(), "Change in connectivity, applying rules", Toast.LENGTH_SHORT).show();
            if (Api.isEnabled(getApplicationContext()) && G.activeRules()) {
                InterfaceTracker.applyRulesOnChange(getApplicationContext(), InterfaceTracker.CONNECTIVITY_CHANGE);
            }
        }
    }
}
