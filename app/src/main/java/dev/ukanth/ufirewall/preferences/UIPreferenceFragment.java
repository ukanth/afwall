package dev.ukanth.ufirewall.preferences;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.util.G.isDonate;

public class UIPreferenceFragment extends PreferenceFragment  implements
		SharedPreferences.OnSharedPreferenceChangeListener {

	private Context ctx;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.ui_preferences);
		if ((G.isDoKey(ctx) || isDonate())) {
			populatePreference(findPreference("default_behavior_allow_mode"), getString(R.string.connection_default_allow), 0);
			populatePreference(findPreference("default_behavior_block_mode"), getString(R.string.connection_default_allow), 1);
		}
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
			ctx = getActivity();
		}
		if(ctx != null) {
			if (key.equals("notification_priority")) {
				NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
				notificationManager.cancel(1);
				//Api.showNotification(Api.isEnabled(ctx), ctx);
				Api.updateNotification(Api.isEnabled(ctx), ctx);
			}
		}
	}

	private void populatePreference(Preference list, String title, int modeType) {
		final ArrayList<CharSequence> entriesList = new ArrayList<CharSequence>();
		final ArrayList<Integer> entryValuesList = new ArrayList<Integer>();

		entriesList.add(getString(R.string.lan));
		entriesList.add(getString(R.string.wifi));
		entriesList.add(getString(R.string.data));
		entriesList.add(getString(R.string.roaming));
		entriesList.add(getString(R.string.tor));
		entriesList.add(getString(R.string.vpn));
		entriesList.add(getString(R.string.tether));

		entryValuesList.add(0);
		entryValuesList.add(1);
		entryValuesList.add(2);
		entryValuesList.add(3);
		entryValuesList.add(4);
		entryValuesList.add(5);
		entryValuesList.add(6);

		list.setOnPreferenceClickListener(preference -> {
			//open browser or intent here

			MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
					.title(title)
					.itemsIds(convertIntegers(entryValuesList))
					.items(entriesList)
					.itemsCallbackMultiChoice(null, (dialog1, which, text) -> {
						List<Integer> listPerf = new ArrayList<Integer>();
						for (int i : which) {
							listPerf.add(entryValuesList.get(i));
						}
						G.storeDefaultConnection(listPerf,modeType);
						return true;
					})
					.positiveText(R.string.OK)
					.negativeText(R.string.close)
					.show();

			if (G.readDefaultConnection(modeType).size() > 0) {
				dialog.setSelectedIndices(selectItems(entryValuesList,modeType));
			}
			return true;
		});
	}

	public static int[] convertIntegers(List<Integer> integers) {
		int[] ret = new int[integers.size()];
		Iterator<Integer> iterator = integers.iterator();
		for (int i = 0; i < ret.length; i++) {
			ret[i] = iterator.next().intValue();
		}
		return ret;
	}


	private Integer[] selectItems(ArrayList<Integer> entryValuesList, int modeType) {
		List<Integer> items = new ArrayList<>();
		for (Integer in : G.readDefaultConnection(modeType)) {
			if (entryValuesList.contains(in)) {
				items.add(entryValuesList.indexOf(in));
			}
		}
		return items.toArray(new Integer[0]);
	}
}
