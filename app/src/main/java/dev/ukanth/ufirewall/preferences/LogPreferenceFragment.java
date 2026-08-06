package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.service.LogService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;

public class LogPreferenceFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        try {
            //fix for the mess
            //G.logPingTimeout(G.logPingTimeout());
            addPreferencesFromResource(R.xml.log_preferences);
          //  populateLogMessage(findPreference("logDmesg"));
           // populateAppList(findPreference("block_filter"));
            setupLogHostname(findPreference("showHostName"));
            populateLogTarget(findPreference("logTarget"));
        } catch (ClassCastException c) {
            Log.i(Api.TAG, c.getMessage());
            Api.toast(getActivity(), getString(R.string.exception_pref));
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
                    
                    // Add custom listener to intercept preference changes
                    listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            String newLogTarget = (String) newValue;
                            String oldLogTarget = G.logTarget();
                            
                            // If it's the same target, allow change immediately
                            if (newLogTarget.equals(oldLogTarget)) {
                                return true;
                            }
                            
                            // Show confirmation dialog and prevent automatic change
                            showLogTargetChangeDialog(oldLogTarget, newLogTarget, listPreference);
                            return false; // Prevent automatic preference change
                        }
                    });
                }
                //if there is only one entry
                if(items.length == 1) {
                    G.logTarget(items[0]);
                }
            } else {
                //no LOG targets
                ((PreferenceGroup) findPreference("logExperimental")).removePreference(listPreference);
            }
        } else{
            //no LOG targets
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

    /*private void populateLogMessage(Preference logDmesg) {
        if (logDmesg == null) {
            return;
        }
        ArrayList<String> ar = new ArrayList<String>();
        ArrayList<String> val = new ArrayList<String>();
        ar.add("System");
        val.add("OS");

        ListPreference listPreference = (ListPreference) logDmesg;
        if (RootTools.isBusyboxAvailable() || !Api.getBusyBoxPath(ctx,false).isEmpty()) {
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
    }*/

    /*private void populateAppList(Preference list) {
        final ArrayList<CharSequence> entriesList = new ArrayList<CharSequence>();
        final ArrayList<Integer> entryValuesList = new ArrayList<Integer>();

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
    }*/

    private void showLogTargetChangeDialog(String oldLogTarget, String newLogTarget, ListPreference listPreference) {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.log_target_change_title)
                .content(getString(R.string.log_target_change_message, oldLogTarget, newLogTarget))
                .positiveText(R.string.Yes)
                .negativeText(R.string.Cancel)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        // Apply the log target change
                        applyLogTargetChange(newLogTarget, listPreference);
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        // Do nothing - preference change was already prevented by returning false
                        Log.d("LogPreferenceFragment", "Log target change cancelled by user");
                    }
                })
                .show();
    }

    private void applyLogTargetChange(String newLogTarget, ListPreference listPreference) {
        Context ctx = getActivity();
        
        // Set the new log target in preferences
        G.logTarget(newLogTarget);
        
        // Update the ListPreference to show the new value
        listPreference.setValue(newLogTarget);
        
        // Update log rules
        Api.updateLogRules(ctx, new RootCommand()
                .setReopenShell(true)
                .setSuccessToast(R.string.log_target_success)
                .setFailureToast(R.string.log_target_fail));
        
        // Change log target without restarting service
        changeLogTargetInService(ctx, newLogTarget);
    }
    
    /**
     * Change log target in the running service without restarting it
     */
    private void changeLogTargetInService(Context ctx, String newLogTarget) {
        Log.i("LogPreferenceFragment", "Changing log target to: " + newLogTarget);
        
        if (G.enableLogService()) {
            // Send log target change request to the running service
            Intent changeIntent = new Intent(ctx, LogService.class);
            changeIntent.setAction(LogService.ACTION_CHANGE_LOG_TARGET);
            changeIntent.putExtra(LogService.EXTRA_NEW_LOG_TARGET, newLogTarget);
            ctx.startService(changeIntent);
            
            // Show success message after a delay to allow the change to process
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                Toast.makeText(ctx, getString(R.string.log_target_changed_success, newLogTarget), Toast.LENGTH_LONG).show();
            }, 2000);
        } else {
            // Service is not running, just update the preference
            Log.i("LogPreferenceFragment", "Log service disabled, only updating preference");
            Toast.makeText(ctx, getString(R.string.log_target_changed_success, newLogTarget), Toast.LENGTH_SHORT).show();
        }
    }
}
