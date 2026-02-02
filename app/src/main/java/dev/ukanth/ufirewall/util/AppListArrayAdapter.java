package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.AppDetailActivity;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogPreference;
import dev.ukanth.ufirewall.log.LogPreference_Table;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.DataUsageParser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;

public class AppListArrayAdapter extends ArrayAdapter<PackageInfoData> {

    public static final String TAG = "AFWall";
    private final Context context;
    private final List<PackageInfoData> listApps;

    private final Activity activity;

    private boolean useOld = false;
    private Set<Integer> expandedPositions = new HashSet<>();

    //final int color = G.sysColor();
    //final int defaultColor = Color.WHITE;

    public AppListArrayAdapter(MainActivity activity, Context context, List<PackageInfoData> apps, boolean useOld) {
        super(context, R.layout.main_list_old, apps);
        this.useOld = true;
        this.activity = activity;
        this.context = context;
        this.listApps = apps;
    }
    public AppListArrayAdapter(MainActivity activity, Context context, List<PackageInfoData> apps) {
        super(context, R.layout.main_list, apps);
        this.activity = activity;
        this.context = context;
        this.listApps = apps;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        AppStateHolder holder;
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            // Inflate a new view
            if(useOld) {
                convertView = inflater.inflate(R.layout.main_list_old, parent, false);
            } else{
                convertView = inflater.inflate(R.layout.main_list, parent, false);
            }
            holder = new AppStateHolder();
            holder.box_wifi = convertView.findViewById(R.id.itemcheck_wifi);

            if (Api.isMobileNetworkSupported(context)) {
                holder.box_3g = addSupport(convertView, true, R.id.itemcheck_3g);
            } else {
                removeSupport(convertView, R.id.itemcheck_3g);
            }

            if (G.enableRoam()) {
                holder.box_roam = addSupport(convertView, true, R.id.itemcheck_roam);
            }
            if (G.enableVPN()) {
                holder.box_vpn = addSupport(convertView, true, R.id.itemcheck_vpn);
            }
            if (G.enableTether()) {
                holder.box_tether = addSupport(convertView, true, R.id.itemcheck_tether);
            }
            if (G.enableLAN()) {
                holder.box_lan = addSupport(convertView, true, R.id.itemcheck_lan);
            }
            if (G.enableTor()) {
                holder.box_tor = addSupport(convertView, true, R.id.itemcheck_tor);
            }

            holder.text = convertView.findViewById(R.id.itemtext);
            holder.icon = convertView.findViewById(R.id.itemicon);

            if (G.disableIcons()) {
                holder.icon.setVisibility(View.GONE);
                activity.findViewById(R.id.imageHolder).setVisibility(View.GONE);
            }
            convertView.setTag(holder);
        } else {
            // Convert an existing view
            holder = (AppStateHolder) convertView.getTag();
            holder.box_wifi = convertView.findViewById(R.id.itemcheck_wifi);
            if (Api.isMobileNetworkSupported(context)) {
                holder.box_3g = addSupport(convertView, true, R.id.itemcheck_3g);
            } else {
                removeSupport(convertView, R.id.itemcheck_3g);
            }
            if (G.enableRoam()) {
                addSupport(convertView, false, R.id.itemcheck_roam);
            }
            if (G.enableVPN()) {
                addSupport(convertView, false, R.id.itemcheck_vpn);
            }
            if (G.enableTether()) {
                addSupport(convertView, false, R.id.itemcheck_tether);
            }
            if (G.enableLAN()) {
                addSupport(convertView, false, R.id.itemcheck_lan);
            }
            if (G.enableTor()) {
                addSupport(convertView, false, R.id.itemcheck_tor);
            }

            holder.text = convertView.findViewById(R.id.itemtext);
            holder.icon = convertView.findViewById(R.id.itemicon);
            if (G.disableIcons()) {
                holder.icon.setVisibility(View.GONE);
                activity.findViewById(R.id.imageHolder).setVisibility(View.GONE);
            }
        }


        holder.app = listApps.get(position);

        if (G.showUid()) {
            holder.text.setText(holder.app.toStringWithUID());
        } else {
            holder.text.setText(holder.app.toString());
        }

        final int id = holder.app.uid;
        final View finalConvertView = convertView;
        final int finalPosition = position;
        holder.icon.setOnClickListener(v -> toggleExpansion(finalConvertView, finalPosition));
        holder.text.setOnClickListener(v -> toggleExpansion(finalConvertView, finalPosition));


        ApplicationInfo info = holder.app.appinfo;
        if (holder.app.appType == 1) {
            //user app
            holder.text.setTextColor(G.userColor());
        } else {
            //system or core app
            holder.text.setTextColor(G.sysColor());
        }

        if (!G.disableIcons()) {
            if(holder.app.pkgName.startsWith("dev.afwall.special.")) {
                holder.icon.setImageDrawable(context.getDrawable(R.drawable.ic_unknown));
            } else {
                holder.icon.setImageDrawable(holder.app.cached_icon);
                if (!holder.app.icon_loaded && info != null) {
                    // this icon has not been loaded yet - load it on a
                    // separated thread
                    try {
                        new LoadIconTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, holder.app,
                                context.getPackageManager(), convertView);
                    } catch (Exception r) {
                    }
                }
            }

        } else {
            holder.icon.setVisibility(View.GONE);
            activity.findViewById(R.id.imageHolder).setVisibility(View.GONE);
        }

        holder.box_wifi.setTag(holder.app);
        holder.box_wifi.setChecked(holder.app.selected_wifi);


        if (Api.isMobileNetworkSupported(context)) {
            holder.box_3g.setTag(holder.app);
            holder.box_3g.setChecked(holder.app.selected_3g);
        }

        if (G.enableRoam()) {
            holder.box_roam = addSupport(holder.box_roam, holder.app, 0);
        }
        if (G.enableVPN()) {
            holder.box_vpn = addSupport(holder.box_vpn, holder.app, 1);
        }
        if (G.enableTether()) {
            holder.box_tether = addSupport(holder.box_tether, holder.app, 6);
        }
        if (G.enableLAN()) {
            holder.box_lan = addSupport(holder.box_lan, holder.app, 2);
        }
        if (G.enableTor()) {
            holder.box_tor = addSupport(holder.box_tor, holder.app, 3);
        }

        // Apply high contrast checkbox tinting for e-paper displays
        applyHighContrastCheckboxTint(holder);

        setupExpandableView(holder, convertView, position);
        addEventListenter(holder);

        return convertView;
    }

    private void toggleExpansion(View convertView, int position) {
        AppStateHolder holder = (AppStateHolder) convertView.getTag();
        if (expandedPositions.contains(position)) {
            expandedPositions.remove(position);
            holder.expandedOptions.setVisibility(View.GONE);
        } else {
            expandedPositions.add(position);
            holder.expandedOptions.setVisibility(View.VISIBLE);
            updateLogStatistics(holder);
            updateDataUsageStats(holder);
        }
    }

    private void setupExpandableView(AppStateHolder holder, View convertView, int position) {
        holder.expandedOptions = convertView.findViewById(R.id.expanded_options);
        holder.actionToggleLog = convertView.findViewById(R.id.action_toggle_log);
        holder.actionOpenApp = convertView.findViewById(R.id.action_open_app);
        holder.actionViewLogs = convertView.findViewById(R.id.action_view_logs);
        holder.blockedCount = convertView.findViewById(R.id.blocked_count);
        holder.lastActivity = convertView.findViewById(R.id.last_activity);
        holder.lastBlockedDestination = convertView.findViewById(R.id.last_blocked_destination);
        holder.dataUsage = convertView.findViewById(R.id.data_usage);

        if (expandedPositions.contains(position)) {
            holder.expandedOptions.setVisibility(View.VISIBLE);
            updateLogStatistics(holder);
            updateDataUsageStats(holder);
        } else {
            holder.expandedOptions.setVisibility(View.GONE);
        }

        updateLogNotificationIcon(holder);
        updateLogsIconVisibility(holder);
        applyThemeColors(holder);

        holder.actionToggleLog.setOnClickListener(v -> {
            Log.d(TAG, "Notification toggle clicked for UID: " + holder.app.uid);
            toggleLogNotification(holder);
        });
        holder.actionOpenApp.setOnClickListener(v -> {
            Log.d(TAG, "Open app settings clicked for: " + holder.app.pkgName);
            openAppSettings(holder);
        });
        holder.actionViewLogs.setOnClickListener(v -> {
            Log.d(TAG, "View logs clicked for UID: " + holder.app.uid);
            openFirewallLogs(holder);
        });
    }

    private void updateLogNotificationIcon(AppStateHolder holder) {
        try {
            LogPreference logPreference = SQLite.select()
                    .from(LogPreference.class)
                    .where(LogPreference_Table.uid.eq(holder.app.uid)).querySingle();

            boolean isDisabled = logPreference != null && logPreference.isDisable();

            holder.actionToggleLog.setImageResource(
                isDisabled ? R.drawable.ic_notifications_off_black_24dp 
                           : R.drawable.ic_notifications_on_black_24dp
            );
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification icon", e);
            holder.actionToggleLog.setImageResource(R.drawable.ic_notifications_on_black_24dp);
        }
    }

    private void applyThemeColors(AppStateHolder holder) {
        int iconColor = G.userColor();
        int textColor = G.userColor();

        // Apply color filter to icons using setColorFilter on ImageView, not the Drawable
        // This preserves click functionality
        holder.actionToggleLog.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
        holder.actionOpenApp.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
        holder.actionViewLogs.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);

        // Apply text colors
        if (holder.blockedCount != null) {
            holder.blockedCount.setTextColor(textColor);
        }
        if (holder.lastActivity != null) {
            holder.lastActivity.setTextColor(textColor);
        }
        if (holder.lastBlockedDestination != null) {
            holder.lastBlockedDestination.setTextColor(textColor);
        }
        if (holder.dataUsage != null) {
            holder.dataUsage.setTextColor(textColor);
        }
    }

    private void toggleLogNotification(AppStateHolder holder) {
        try {
            LogPreference logPreference = SQLite.select()
                    .from(LogPreference.class)
                    .where(LogPreference_Table.uid.eq(holder.app.uid)).querySingle();

            // Current state: if logPreference exists and isDisable() is true, notifications are disabled
            boolean currentlyDisabled = logPreference != null && logPreference.isDisable();
            
            // Toggle: if currently disabled, enable (false); if currently enabled, disable (true)
            boolean newDisabledState = !currentlyDisabled;

            Log.d(TAG, "Toggling log notification for UID " + holder.app.uid + 
                  ": currently disabled=" + currentlyDisabled + ", new disabled state=" + newDisabledState);

            G.updateLogNotification(holder.app.uid, newDisabledState);
            updateLogNotificationIcon(holder);
            applyThemeColors(holder);
        } catch (Exception e) {
            Log.e(TAG, "Error toggling log notification", e);
        }
    }

    private void openAppSettings(AppStateHolder holder) {
        if (!holder.app.pkgName.startsWith("dev.afwall.special.")) {
            Api.showInstalledAppDetails(context, holder.app.pkgName);
        }
    }

    private void updateLogsIconVisibility(AppStateHolder holder) {
        try {
            // Check if logs exist for this app
            long logCount = SQLite.selectCountOf()
                    .from(LogData.class)
                    .where(LogData_Table.uid.eq(holder.app.uid))
                    .count();

            holder.actionViewLogs.setVisibility(logCount > 0 ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "Error checking log availability", e);
            holder.actionViewLogs.setVisibility(View.GONE);
        }
    }

    private void openFirewallLogs(AppStateHolder holder) {
        try {
            Intent intent = new Intent(context, dev.ukanth.ufirewall.activity.LogDetailActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("DATA", holder.app.uid);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening firewall logs", e);
        }
    }

    private void updateLogStatistics(AppStateHolder holder) {
        try {
            int uid = holder.app.uid;
            
            // Get blocked count
            long blockedCountValue = SQLite.selectCountOf()
                    .from(LogData.class)
                    .where(LogData_Table.uid.eq(uid))
                    .count();

            holder.blockedCount.setText("Blocked: " + blockedCountValue);

            // Get most recent log entry
            LogData lastLogEntry = SQLite.select()
                    .from(LogData.class)
                    .where(LogData_Table.uid.eq(uid))
                    .orderBy(LogData_Table.timestamp, false)
                    .querySingle();

            if (lastLogEntry != null) {
                // Format last activity time
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                String formattedTime = sdf.format(new Date(lastLogEntry.getTimestamp()));
                holder.lastActivity.setText("Last: " + formattedTime);

                // Show last blocked destination
                String destination = lastLogEntry.getDst();
                if (destination != null && !destination.isEmpty()) {
                    String hostname = lastLogEntry.getHostname();
                    String displayDestination = hostname != null && !hostname.isEmpty() ? 
                            hostname : destination;
                    holder.lastBlockedDestination.setText("Last blocked: " + displayDestination);
                } else {
                    holder.lastBlockedDestination.setText("Last blocked: -");
                }
            } else {
                holder.lastActivity.setText("Last activity: -");
                holder.lastBlockedDestination.setText("Last blocked: -");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating log statistics", e);
            holder.blockedCount.setText("Blocked: -");
            holder.lastActivity.setText("Last activity: -");
            holder.lastBlockedDestination.setText("Last blocked: -");
        }
    }

    private void updateDataUsageStats(AppStateHolder holder) {
        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                DataUsageParser.DataUsageStats stats = DataUsageParser.getDataUsageForUID(holder.app.uid);
                String dataUsageText = DataUsageParser.formatWifiMobileUsage(stats);
                
                // Update UI on main thread
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (holder.dataUsage != null) {
                            holder.dataUsage.setText("Data: " + dataUsageText);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating data usage stats", e);
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        if (holder.dataUsage != null) {
                            holder.dataUsage.setText("Data: Not available");
                        }
                    });
                }
            }
        }).start();
    }

    private void StartAppDetailActivityIntent(View v, AppStateHolder holder, Integer id) {
        Intent intent = new Intent(context, AppDetailActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("package", holder.app.pkgName);
        intent.putExtra("appid", id);
        context.startActivity(intent);
    }

    private void addEventListenter(final AppStateHolder holder) {
        if (holder.box_lan != null) {
            holder.box_lan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_lan != isChecked) {
                            holder.app.selected_lan = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                            //MainActivity.addToQueue(holder.app);
                        }
                    }

                }
            });
        }

        if (holder.box_wifi != null) {
            holder.box_wifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_wifi != isChecked) {
                            holder.app.selected_wifi = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                            //MainActivity.addToQueue(holder.app);
                        }
                    }
                }
            });
        }

        if (holder.box_3g != null) {
            holder.box_3g.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_3g != isChecked) {
                            holder.app.selected_3g = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                            //MainActivity.addToQueue(holder.app);
                        }
                    }
                }
            });
        }

        if (holder.box_roam != null) {
            holder.box_roam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_roam != isChecked) {
                            holder.app.selected_roam = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                            //MainActivity.addToQueue(holder.app);
                        }
                    }
                }
            });
        }

        if (holder.box_vpn != null) {
            holder.box_vpn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_vpn != isChecked) {
                            holder.app.selected_vpn = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                           //MainActivity.addToQueue(holder.app);
                        }
                    }
                }
            });
        }

        if (holder.box_tether != null) {
            holder.box_tether.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_tether != isChecked) {
                            holder.app.selected_tether = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                            //MainActivity.addToQueue(holder.app);
                        }
                    }
                }
            });
        }

        if (holder.box_tor != null) {
            holder.box_tor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_tor != isChecked) {
                            holder.app.selected_tor = isChecked;
                            MainActivity.dirty = true;
                            notifyDataSetChanged();
                            //Log.i(TAG, "Application state changed: " + holder.app.pkgName);
                           //MainActivity.addToQueue(holder.app);
                        }
                    }
                }
            });
        }
    }

    private CheckBox addSupport(CheckBox check, PackageInfoData app, int flag) {
        if (check != null) {
            check.setTag(app);
            switch (flag) {
                case 0:
                    check.setChecked(app.selected_roam);
                    break;
                case 1:
                    check.setChecked(app.selected_vpn);
                    break;
                case 6:
                    check.setChecked(app.selected_tether);
                    break;
                case 2:
                    check.setChecked(app.selected_lan);
                    break;
                case 3:
                    check.setChecked(app.selected_tor);
                    break;
            }
        }
        return check;
    }

    private CheckBox addSupport(View convertView, boolean action, int id) {
        CheckBox check = convertView.findViewById(id);
        check.setVisibility(View.VISIBLE);
       /* if (action) {
            check.setOnCheckedChangeListener(this);
        }*/
        return check;
    }

    private CheckBox removeSupport(View convertView, int id) {
        CheckBox check = convertView.findViewById(id);
        check.setVisibility(View.GONE);
        return check;
    }

    /**
     * Apply high contrast checkbox tinting for e-paper displays
     */
    private void applyHighContrastCheckboxTint(AppStateHolder holder) {
        if (!"LHC".equals(G.getSelectedTheme())) {
            return;
        }

        // Pure black color for maximum contrast on e-paper
        ColorStateList colorStateList = ColorStateList.valueOf(Color.BLACK);

        if (holder.box_wifi != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_wifi, colorStateList);
        }
        if (holder.box_3g != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_3g, colorStateList);
        }
        if (holder.box_roam != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_roam, colorStateList);
        }
        if (holder.box_vpn != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_vpn, colorStateList);
        }
        if (holder.box_tether != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_tether, colorStateList);
        }
        if (holder.box_lan != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_lan, colorStateList);
        }
        if (holder.box_tor != null) {
            CompoundButtonCompat.setButtonTintList(holder.box_tor, colorStateList);
        }
    }


    static class AppStateHolder {
        private CheckBox box_lan;
        private CheckBox box_wifi;
        private CheckBox box_3g;
        private CheckBox box_roam;
        private CheckBox box_vpn;
        private CheckBox box_tether;
        private CheckBox box_tor;
        private TextView text;
        private ImageView icon;
        private PackageInfoData app;
        private LinearLayout expandedOptions;
        private ImageView actionToggleLog;
        private ImageView actionOpenApp;
        private ImageView actionViewLogs;
        private TextView blockedCount;
        private TextView lastActivity;
        private TextView lastBlockedDestination;
        private TextView dataUsage;
    }

    /**
     * Asynchronous task used to load icons in a background thread.
     */
    private static class LoadIconTask extends AsyncTask<Object, Void, View> {
        @Override
        protected View doInBackground(Object... params) {
            try {
                final PackageInfoData app = (PackageInfoData) params[0];
                final PackageManager pkgMgr = (PackageManager) params[1];
                final View viewToUpdate = (View) params[2];
                if (!app.icon_loaded) {
                    Drawable d = new ScaleDrawable(pkgMgr.getApplicationIcon(app.appinfo), 0, 32, 32).getDrawable();
                    d.setBounds(0, 0, 32, 32);
                    app.cached_icon = d;
                    app.icon_loaded = true;
                }
                // Return the view to update at "onPostExecute"
                // Note that we cannot be sure that this view still references
                // "app"
                return viewToUpdate;
            } catch (Exception e) {
                Log.e(TAG, "Error loading icon", e);
                return null;
            }
        }

        protected void onPostExecute(View viewToUpdate) {
            try {
                // This is executed in the UI thread, so it is safe to use
                // viewToUpdate.getTag()
                // and modify the UI
                final AppStateHolder entryToUpdate = (AppStateHolder) viewToUpdate.getTag();
                entryToUpdate.icon.setImageDrawable(entryToUpdate.app.cached_icon);
            } catch (Exception e) {
                Log.e(TAG, "Error showing icon", e);
            }
        }

    }

    /**
     * Called an application is check/unchecked
     */
    /*@Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final PackageInfoData app = (PackageInfoData) buttonView.getTag();
        if (app != null) {
            switch (buttonView.getId()) {
                case R.id.itemcheck_wifi:
                    if (app.selected_wifi != isChecked) {
                        app.selected_wifi = isChecked;
                        MainActivity.dirty = true;
                        notifyDataSetChanged();
                    }
                    break;
                case R.id.itemcheck_3g:
                    if (app.selected_3g != isChecked) {
                        app.selected_3g = isChecked;
                        MainActivity.dirty = true;
                        notifyDataSetChanged();
                    }
                    break;
                case R.id.itemcheck_roam:
                    if (app.selected_roam != isChecked) {
                        app.selected_roam = isChecked;
                        MainActivity.dirty = true;
                        notifyDataSetChanged();
                    }
                    break;
                case R.id.itemcheck_vpn:
                    if (app.selected_vpn != isChecked) {
                        app.selected_vpn = isChecked;
                        MainActivity.dirty = true;
                        notifyDataSetChanged();
                    }
                    break;
                case R.id.itemcheck_lan:
                    if (app.selected_lan != isChecked) {
                        app.selected_lan = isChecked;
                        MainActivity.dirty = true;
                        notifyDataSetChanged();
                    }
                    break;
                case R.id.itemcheck_tor:
                    if (app.selected_tor != isChecked) {
                        app.selected_tor = isChecked;
                        MainActivity.dirty = true;
                        notifyDataSetChanged();
                    }
                    break;
            }
            if(buttonView.isPressed()) {
                Log.i(TAG, "Application state changed: " + app.pkgName);
                MainActivity.addToQueue(app);
            }
        }
    }*/

}
