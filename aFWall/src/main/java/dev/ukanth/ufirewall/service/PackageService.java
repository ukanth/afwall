package dev.ukanth.ufirewall.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import dev.ukanth.ufirewall.broadcast.PackageBroadcast;

public class PackageService extends JobService {

    private static final String TAG = PackageService.class.getSimpleName();

    private BroadcastReceiver receiver;

    public PackageService() {
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");

        receiver = new PackageBroadcast();
        registerReceiver(receiver, intentFilter);

        intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");

        registerReceiver(receiver, intentFilter);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        unregisterReceiver(receiver);
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {

    }

    //ensure that we unregister the receiver once it's done.
    @Override
    public void onDestroy() {
        if(receiver != null) {
            unregisterReceiver(receiver);
        }
    }

}
