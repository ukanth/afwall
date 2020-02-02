package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stericson.roottools.RootTools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.util.G.ctx;

public class LogPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        try {
            //fix for the mess
            //G.logPingTimeout(G.logPingTimeout());
            addPreferencesFromResource(R.xml.log_preferences);
            populateLogMessage(findPreference("logDmesg"));
            populateAppList(findPreference("block_filter"));
            setupLogHostname(findPreference("showHostName"));
            populateLogTarget(findPreference("logTarget"));
        } catch (ClassCastException c) {
            Log.i(Api.TAG, c.getMessage());
            Api.toast((Context) getActivity(), getString(R.string.exception_pref));
        }
    }

    private void populateLogTarget(Preference logTarget) {
        if (logTarget == null) {
            return;
        }
        ListPreference listPreference = (ListPreference) logTarget;
        if(G.logTargets() != null && G.logTargets().length() > 0) {
            String [] items = G.logTargets().split(",");
            if(items != null && items.length > 0) {
                if (listPreference != null) {
                    listPreference.setEntries(items);
                    listPreference.setEntryValues(items);
                }
            } else {
                ((PreferenceGroup) findPreference("logExperimental")).removePreference(listPreference);
            }
        } else{
            ((PreferenceGroup) findPreference("logExperimental")).removePreference(listPreference);
        }
    }

    private void setupLogHostname(Preference showHostName) {
        CheckBoxPreference showHost = (CheckBoxPreference) showHostName;
        if (G.isDoKey(getActivity()) || G.isDonate()) {
            showHost.setEnabled(true);
        }
       /* if(!Api.isAFWallAllowed((Context) getActivity())){
            showHost.setChecked(false);
        }*/
    }

    private void populateLogMessage(Preference logDmesg) {
        if (logDmesg == null) {
            return;
        }
        ArrayList<String> ar = new ArrayList<String>();
        ArrayList<String> val = new ArrayList<String>();
        ar.add("System");
        val.add("OS");

        ListPreference listPreference = (ListPreference) logDmesg;
        if (RootTools.isBusyboxAvailable()) {
            ar.add("Busybox");
            val.add("BX");
        }

        if (listPreference != null) {
            listPreference.setEntries(ar.toArray(new String[0]));
            listPreference.setEntryValues(val.toArray(new String[0]));
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

            /*if (Api.applications == null) {
                apps = Api.getApps(getActivity(), null);
            } else {
                apps = Api.applications;
            }*/

        List<Api.PackageInfoData> apps = new ArrayList<>();
        //List<Api.PackageInfoData> apps = Api.getSpecialData(true);

        Api.PackageInfoData info = new Api.PackageInfoData();
        info.uid = 1020;
        info.pkgName = "dev.afwall.special.mDNS";
        info.names = new ArrayList<String>();
        info.names.add("mDNS");
        info.appinfo = new ApplicationInfo();
        //TODO: better way to handle this
        //manually add mDNS for now
        if (!apps.contains(info)) {
            apps.add(info);
        }
       /* try {
            Collections.sort(apps, new PackageComparator());
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception on Sort " + e.getMessage());
        }*/
        for (int i = 0; i < apps.size(); i++) {
            entriesList.add(apps.get(i).toStringWithUID());
            entryValuesList.add(apps.get(i).uid);
        }

        list.setOnPreferenceClickListener(preference -> {
            //open browser or intent here

            MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                    .title(R.string.filters_apps_title)
                    .itemsIds(convertIntegers(entryValuesList))
                    .items(entriesList)
                    .itemsCallbackMultiChoice(null, (dialog1, which, text) -> {
                        List<Integer> blockedList = new ArrayList<Integer>();
                        for (int i : which) {
                            blockedList.add(entryValuesList.get(i));
                        }
                        G.storeBlockedApps(blockedList);
                        return true;
                    })
                    .positiveText(R.string.OK)
                    .negativeText(R.string.close)
                    .show();

            if (G.readBlockedApps().size() > 0) {
                dialog.setSelectedIndices(selectItems(entryValuesList));
            }
            return true;
        });
    }

    private Integer[] selectItems(ArrayList<Integer> entryValuesList) {
        List<Integer> items = new ArrayList<>();
        for (Integer in : G.readBlockedApps()) {
            if (entryValuesList.contains(in)) {
                items.add(entryValuesList.indexOf(in));
            }
        }
        return items.toArray(new Integer[0]);
    }
}
