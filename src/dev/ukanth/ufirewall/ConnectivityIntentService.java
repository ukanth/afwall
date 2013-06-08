package dev.ukanth.ufirewall;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class ConnectivityIntentService extends IntentService {

	public static final String TAG = "AFWall";
	
	public static final String ACTION_CONNECTIVITY_CHANGED = "connectivity_changed";
	
	private static Context mContext = null;

	public ConnectivityIntentService() {
		// If you forget this one, the app will crash
		super("ConnectivityIntentService");
    }

	public static void performAction(Context context, String action) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		//final boolean enableRoam = prefs.getBoolean("enableRoam", false);
			initContext(context);
			Intent svc = new Intent(context, ConnectivityIntentService.class);
			svc.setAction(action);
			context.startService(svc);
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
	
	protected static void initContext(Context context) {
		if(mContext == null) {
			mContext = context.getApplicationContext();	
		}
	}

	
	public static boolean applyRules(Context context, boolean showErrors) {
		boolean ret = false;
		if(mContext != null) {
			if (!Api.isEnabled(mContext)) {
				Log.d(TAG, "applyRules: firewall is disabled, skipping");
				return true;
			}
			ret = Api.applySavedIptablesRules(mContext, showErrors); 
			Log.d(TAG, "applyRules: " + (ret ? "success" : "failed"));	
		}
		return ret;
	}
}