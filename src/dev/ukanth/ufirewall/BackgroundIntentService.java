package dev.ukanth.ufirewall;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BackgroundIntentService extends IntentService {

	public static final String TAG = "AFWall";
	
	public static final String ACTION_BOOT_COMPLETE = "boot_complete";
	public static final String ACTION_CONNECTIVITY_CHANGED = "connectivity_changed";
	
	private static boolean initDone = false;
	private static Context mContext;
	private static SharedPreferences prefs;

	public BackgroundIntentService() {
		// If you forget this one, the app will crash
		super("BackgroundIntentService");
    }

	private static void setupPrefs() {
		prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		final boolean multimode = prefs.getBoolean("enableMultiProfile", false);

		if (Api.isEnabled(mContext)) {
			if (multimode) {
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
		}
	}

	protected static void firstRun(Context context) {
		if (!initDone) {
			mContext = context.getApplicationContext();
			setupPrefs();
			initDone = true;
		}
	}

	public static boolean applyRules(Context context, boolean showErrors) {
		boolean ret;

		firstRun(context);
		if (!Api.isEnabled(mContext)) {
			Log.d(TAG, "applyRules: firewall is disabled, skipping");
			return true;
		}

		ret = Api.applySavedIptablesRules(mContext, showErrors); 
		Log.d(TAG, "applyRules: " + (ret ? "success" : "failed"));
		return ret;
	}

	public static void performAction(Context context, String action) {
		final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(context);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		final boolean enableLAN = prefs.getBoolean("enableLAN", false) && !cfg.isTethered;
		final boolean enableRoam = prefs.getBoolean("enableRoam", false);
		if((cfg.isRoaming && enableRoam) || enableLAN || action.equals( BackgroundIntentService.ACTION_BOOT_COMPLETE)) {
			Intent svc = new Intent(context, BackgroundIntentService.class);
			svc.setAction(action);
			firstRun(context);
			context.startService(svc);
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String action = intent.getAction();

		Log.d(TAG, "received " + action + " intent");
		if (InterfaceTracker.checkForNewCfg(mContext)) {
			if (applyRules(mContext, false) == false) {
				Log.e(TAG, "Unable to apply firewall rules");
				Api.setEnabled(mContext, false, false);
			}
		}
	}	
}