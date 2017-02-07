package dev.ukanth.ufirewall.preferences;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stericson.roottools.RootTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.PackageComparator;

public class LogPreferenceFragment extends PreferenceFragment {

    private static ListPreference listPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.log_preferences);
        populateLogMessage(findPreference("logDmesg"));
        populateAppList(findPreference("block_filter"));
    }

    private void populateLogMessage(Preference logDmesg) {
        if (logDmesg == null) {
            return;
        }
        ArrayList<String> ar = new ArrayList<String>();
        ArrayList<String> val = new ArrayList<String>();
        ar.add("System");
        val.add("OS");

        listPreference = (ListPreference) logDmesg;
        PreferenceCategory mCategory = (PreferenceCategory) findPreference("logExperimental");

        if (RootTools.isBusyboxAvailable()) {
            ar.add("Busybox");
            val.add("BX");
        }

        if (ar.size() != 1 && listPreference != null) {
            listPreference.setEntries(ar.toArray(new String[0]));
            listPreference.setEntryValues(val.toArray(new String[0]));

        } else {
            if (listPreference != null && mCategory != null) {
                mCategory.removePreference(listPreference);
            }
        }
    }

    public static int[] convertIntegers(List<Integer> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next().intValue();
        }
        return ret;
    }

    private void populateAppList(Preference list) {

        final ArrayList<CharSequence> entriesList = new ArrayList<CharSequence>();
        final ArrayList<Integer> entryValuesList = new ArrayList<Integer>();

        List<Api.PackageInfoData> apps;
        if (Api.applications == null) {
            apps = Api.getApps(getActivity(), null);
        } else {
            apps = Api.applications;
        }

        try {
            Collections.sort(apps, new PackageComparator());
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception on Sort " + e.getMessage());
        }
        for (int i = 0; i < apps.size(); i++) {
            entriesList.add(apps.get(i).toStringWithUID());
            entryValuesList.add(apps.get(i).uid);
        }

        list.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                //open browser or intent here

                MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                        .title(R.string.filters_apps_title)
                        .itemsIds(convertIntegers(entryValuesList))
                        .items(entriesList)
                        .itemsCallbackMultiChoice(null, new MaterialDialog.ListCallbackMultiChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, Integer[] which, CharSequence[] text) {
                                List<Integer> blockedList = new ArrayList<Integer>();
                                for (int i : which) {
                                    blockedList.add(entryValuesList.get(i));
                                }
                                G.setBlockedNotifyApps(blockedList);
                                return true;
                            }
                        })
                        .positiveText(R.string.OK)
                        .negativeText(R.string.close)
                        .show();

                if (G.getBlockedNotifyList().size() > 0) {
                    dialog.setSelectedIndices(selectItems(entryValuesList));
                }
                return true;
            }
        });
    }

    private Integer[] selectItems(ArrayList<Integer> entryValuesList) {
        List<Integer> items = new ArrayList<>();
        for (Integer in : G.getBlockedNotifyList()) {
            if (entryValuesList.contains(in)) {
                items.add(entryValuesList.indexOf(in));
            }
        }
        return items.toArray(new Integer[0]);
    }
}
