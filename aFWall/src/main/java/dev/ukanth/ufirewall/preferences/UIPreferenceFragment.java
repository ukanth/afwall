package dev.ukanth.ufirewall.preferences;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.events.RulesEvent;
import dev.ukanth.ufirewall.util.G;

public class UIPreferenceFragment extends PreferenceFragment  implements
		SharedPreferences.OnSharedPreferenceChangeListener {

	private Context ctx;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.ui_preferences);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		ctx = context;
	}


	@Override
	public void onResume() {
		super.onResume();
		getPreferenceManager().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);

	}

	@Override
	public void onPause() {
		getPreferenceManager().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
										  String key) {
		if(ctx == null) {
			ctx = (Context) getActivity();
		}
		if(ctx != null) {
			if (key.equals("notification_priority")) {
				NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(33341);
				Api.showNotification(Api.isEnabled(ctx), ctx);
			}
		}
	}
}
