package dev.ukanth.ufirewall.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.StartActivity;
import dev.ukanth.ufirewall.broadcast.ApplyOnBoot;

public class ApplyOnBootService extends Service {

    static final String CHANNEL_ID = "onboot_apply";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.applying_rules), NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannel);

            Notification.Builder builder = new Notification.Builder(
                    this, CHANNEL_ID);
            builder.setContentTitle(getString(R.string.applying_rules))
                    .setSmallIcon(R.mipmap.round_launcher_free);
            startForeground(15000, builder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Messenger messenger = null;
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                messenger = (Messenger) extras.get("messenger");
            }
        }

        if (messenger == null) {
            PackageManager pm = getPackageManager();
            pm.setComponentEnabledSetting(new ComponentName(this, StartActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }

        ApplyOnBoot.apply(this, this::stopSelf);

        if (messenger != null) {
            try {
                Message message = Message.obtain();
                message.arg1 = 1;
                messenger.send(message);
            } catch (RemoteException ignored) {
            }
        }
        stopSelf();
        return START_NOT_STICKY;
    }

}
