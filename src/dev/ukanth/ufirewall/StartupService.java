package dev.ukanth.ufirewall;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

public class StartupService extends Service {
	private Context context;
	static final int NOTIFICATION_ID = 24556;
	
	@Override
	public IBinder onBind(Intent arg0) {
		 return(null);
	}

	public void startForegroundService(Notification n) {
		startForeground(NOTIFICATION_ID, n);
	}

	public void stopForegroundService() {
		stopForeground(true);
	}

	@SuppressWarnings("deprecation")
	public Notification createNotification(String title) {
		Notification notification = new Notification(R.drawable.notification_icon, title, System.currentTimeMillis());
		Intent i = new Intent(this, MainActivity.class);
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_AUTO_CANCEL;
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
		notification.setLatestEventInfo(this, title ,getString(R.string.startup_service) , pi);
		return notification;
	}


	@Override
	public void onCreate() {
	    Notification notification = createNotification("AFWall+");
	    startForegroundService(notification);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		context = this;
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		final boolean multimode = prefs.getBoolean("enableMultiProfile", false);
		
		if (Api.isEnabled(context.getApplicationContext())) {
			if(multimode){
				int itemPosition = prefs.getInt("storedPosition", 0);
				switch (itemPosition) {
				case 0:
					Api.PREFS_NAME = "AFWallPrefs";
					break;
				case 1:
					Api.PREFS_NAME = "AFWallProfile1";
					break;
				case 2:
					Api.PREFS_NAME = "AFWallProfile2";
					break;
				case 3:
					Api.PREFS_NAME = "AFWallProfile3";
					break;
				default:
					break;
				}
			}
			
			new Thread() {
				public void run() {
					Looper.prepare();

					boolean isApplied = Api.applySavedIptablesRules(
							context.getApplicationContext(), false);
					if (!isApplied) {
						Log.d("Unable to apply the rules in AFWall+", "");
						Api.setEnabled(context.getApplicationContext(), false,
								false);
					}
					stopForegroundService();

				}
			}.start();
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					stopSelf();
				}
			}, 180000);
			
		}
		 return(START_NOT_STICKY);
	}

	@Override
	public void onDestroy() {
		stopForegroundService();
	}

}
