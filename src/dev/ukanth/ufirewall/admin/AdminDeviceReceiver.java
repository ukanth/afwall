package dev.ukanth.ufirewall.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.widget.Toast;

import dev.ukanth.ufirewall.Log;
import dev.ukanth.ufirewall.R;

/**
 * This is the component that is responsible for actual device administration.
 * It becomes the receiver when a policy is applied. It is important that we
 * subclass DeviceAdminReceiver class here and to implement its only required
 * method onEnabled().
 */
public class AdminDeviceReceiver extends DeviceAdminReceiver {
    static final String TAG = "AdminDeviceReceiver";

    /** Called when this application is approved to be a device administrator. */
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final Editor editor = prefs.edit();
        editor.putBoolean("enableAdmin", true);
        editor.commit();
        Toast.makeText(context, R.string.device_admin_enabled, Toast.LENGTH_LONG).show();
        Log.d(TAG, "onEnabled");
    }

    /** Called when this application is no longer the device administrator. */
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final Editor editor = prefs.edit();
        editor.putBoolean("enableAdmin", false);
        editor.commit();
        Toast.makeText(context, R.string.device_admin_disabled, Toast.LENGTH_LONG).show();
        Log.d(TAG, "onDisabled");
    }
}
