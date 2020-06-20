package dev.ukanth.ufirewall.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;


/**
 * This is the component that is responsible for actual device administration.
 * It becomes the receiver when a policy is applied. It is important that we
 * subclass DeviceAdminReceiver class here and to implement its only required
 * method onEnabled().
 */
public class AdminDeviceReceiver extends DeviceAdminReceiver {
	static final String TAG = "AdminDeviceReceiver";

	@Override
	public void onEnabled(Context context, Intent intent) {
		super.onEnabled(context, intent);
		G.enableAdmin(true);
		Toast.makeText(context, R.string.device_admin_enabled ,Toast.LENGTH_LONG).show();
		Log.d(TAG, "onEnabled");
	}

	@Override
	public void onDisabled(Context context, Intent intent) {
		super.onDisabled(context, intent);
		G.enableAdmin(false);
		Toast.makeText(context, R.string.device_admin_disabled,Toast.LENGTH_LONG).show();
		Log.d(TAG, "onDisabled");
	}
}
