package dev.ukanth.ufirewall;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

public class BackgroundIntentService extends IntentService {
	// you could provide more options here, should you need them
	public static final String ACTION_BOOT_COMPLETE = "boot_complete";
	
	private static Context context;
	
	public Context getContext() {
		return context;
	}

	public static void setContext(Context context) {
		BackgroundIntentService.context = context;
	}

	public static void performAction(Context context, String action) {
		performAction(context, action, null);		
	}

	public static void performAction(Context context, String action, Bundle extras) {
		if ((context == null) || (action == null) || action.equals("")) return;
		Intent svc = new Intent(context, BackgroundIntentService.class);
		svc.setAction(action);
		setContext(context.getApplicationContext());
		if (extras != null)	svc.putExtras(extras);
		context.startService(svc);
		
	}
					
	public BackgroundIntentService() {
		// If you forget this one, the app will crash
		super("BackgroundIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String action = intent.getAction();		
		
		if ((action == null) || (action.equals(""))) return;
		
		if (action.equals(ACTION_BOOT_COMPLETE)) {
			onBootComplete();
		}
		// you can define more options here... pass parameters through the "extra" values
	}
	
	protected void onBootComplete() {
		if(context == null) return;
		
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
			//potential fix for rules are not applying
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
				}
			}.start();
		}
	}	
}