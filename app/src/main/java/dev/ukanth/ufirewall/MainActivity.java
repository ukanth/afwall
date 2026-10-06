/**
 * Main application activity.
 * This is the screen displayed when you open the application
 * <p>
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */

package dev.ukanth.ufirewall;

import static dev.ukanth.ufirewall.util.G.TAG;
import static dev.ukanth.ufirewall.util.G.ctx;
import static dev.ukanth.ufirewall.util.G.isDonate;
import static dev.ukanth.ufirewall.util.SecurityUtil.LOCK_VERIFICATION;
import static dev.ukanth.ufirewall.util.SecurityUtil.REQ_ENTER_PATTERN;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.activity.CustomScriptActivity;
import dev.ukanth.ufirewall.activity.HelpActivity;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.activity.OldLogActivity;
import dev.ukanth.ufirewall.activity.RulesActivity;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.preferences.PreferencesActivity;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.FirewallService;
import dev.ukanth.ufirewall.service.LogService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.AppListArrayAdapter;
import dev.ukanth.ufirewall.util.BackupStorage;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.PackageComparator;
import dev.ukanth.ufirewall.util.SecurityUtil;
import dev.ukanth.ufirewall.util.ThemeHelper;
import haibison.android.lockpattern.utils.AlpSettings;
import kotlin.Suppress;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, OnClickListener, SwipeRefreshLayout.OnRefreshListener,
        RadioGroup.OnCheckedChangeListener {

    private static final int SHOW_ABOUT_RESULT = 1200;
    private static final int PREFERENCE_RESULT = 1205;
    private static final int SHOW_CUSTOM_SCRIPT = 1201;
    private static final int SHOW_RULES_ACTIVITY = 1202;
    private static final int SHOW_LOGS_ACTIVITY = 1203;
    private static final int VERIFY_CHECK = 10000;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE_ASSET = 3;
    private static final int PERMISSION_BLUETOOTH = 4;

    private static final int PERMISSION_NOTIFICATION = 5;

    public static boolean dirty = false;

    public static void requireFullApply() {
        dirty = true;
    }

    public static void addToQueue(@NonNull Api.PackageInfoData data) {
        dirty = true;
    }


    private Menu mainMenu;
    private ListView listview = null;
    private MaterialDialog plsWait;
    private ArrayAdapter<String> spinnerAdapter = null;
    private SwipeRefreshLayout mSwipeLayout;
    private int index;
    private int top;
    private List<String> mlocalList = new ArrayList<>(new LinkedHashSet<String>());
    private int initDone = 0;
    private Spinner mSpinner;
    private TextWatcher filterTextWatcher;
    private MaterialDialog runProgress;

    private BroadcastReceiver uiProgressReceiver4, uiProgressReceiver6, toastReceiver, themeRefreshReceiver, uiRefreshReceiver;
    private IntentFilter uiFilter4, uiFilter6;

    //all async reference with context
    private GetAppList getAppList;
    private RunApply runApply;
    private PurgeTask purgeTask;
    private int currentUI = 0;
    private static final int DEFAULT_COLUMN = 2;
    private int selectedColumns = DEFAULT_COLUMN;
    private static final int DEFAULT_VIEW_LIMIT = 4;
    private View view;

    private static final String STATE_PENDING_IMPORT_ALL = "pendingImportAll";
    // which import the file picker was opened for; survives the activity being recreated
    private boolean pendingImportAll;
    private boolean backupMigrationRunning;
    private final ActivityResultLauncher<Intent> importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onImportFilePicked);

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        MainActivity.dirty = dirty;
    }

    /**
     * Called when the activity is first created
     * .
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingImportAll = savedInstanceState.getBoolean(STATE_PENDING_IMPORT_ALL);
        }

        initTheme();
        G.registerPrivateLink();
        checkPermissions();

        try {
            final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class
                    .getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
            getWindow().setFlags(FLAG_HARDWARE_ACCELERATED,
                    FLAG_HARDWARE_ACCELERATED);
        } catch (Exception e) {
        }

        updateSelectedColumns();

        if (selectedColumns <= DEFAULT_VIEW_LIMIT) {
            currentUI = 0;
            setContentView(R.layout.main_old);
        } else {
            currentUI = 1;
            setContentView(R.layout.main);
        }

        Toolbar toolbar = findViewById(R.id.main_toolbar);

        setSupportActionBar(toolbar);


        this.findViewById(R.id.img_wifi).setOnClickListener(this);
        /*
        this.findViewById(R.id.img_reset).setOnClickListener(this);
        this.findViewById(R.id.img_invert).setOnClickListener(this);
        this.findViewById(R.id.img_clone).setOnClickListener(this);
        */
        this.findViewById(R.id.img_action).setOnClickListener(this);

        AlpSettings.Display.setStealthMode(getApplicationContext(), G.enableStealthPattern());
        AlpSettings.Display.setMaxRetries(getApplicationContext(), G.getMaxPatternTry());

        // Move binary assertion to background thread to avoid blocking main thread
        // This includes file I/O and process execution (waitFor) which can cause ANR
        final Context appContext = getApplicationContext();
        AsyncTask.execute(() -> Api.assertBinaries(appContext, true));

        initDone = 0;
        mSwipeLayout = findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);


        if (!G.hasRoot()) {
            (new RootCheck()).setContext(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            //might not have rootshell
            startRootShell();
            new SecurityUtil(MainActivity.this).passCheck();
            registerNetworkObserver();
            // FirewallService is started in onResume()
        }
        registerUIbroadcast4();
        registerUIbroadcast6();
        registerToastbroadcast();
        initTextWatcher();
        registerThemeIntent();
        registerUIRefresh();
        // move old backups out of Android/data before an uninstall can delete them
        migrateOldBackups(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PENDING_IMPORT_ALL, pendingImportAll);
    }

    private void checkPermissions() {
        if (G.enableTether()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                // permissions have not been granted.
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        PERMISSION_BLUETOOTH);
            }
        }
        checkNotificationPermission();
    }

    // shown at most once per app start
    private static boolean notificationsOffReminderShown;

    /**
     * Ask for the notification permission once, explaining why; afterwards, if notifications are
     * off, remind (once per start, until "don't remind me") instead of asking on every launch.
     */
    private void checkNotificationPermission() {
        final String asked = "notifPermissionAsked";
        final String dontRemind = "notifOffDontRemind";
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted
                && !G.gPrefs.getBoolean(asked, false)) {
            new MaterialDialog.Builder(this)
                    .title(R.string.notif_permission_title)
                    .content(R.string.notif_permission_rationale)
                    .positiveText(R.string.OK)
                    .negativeText(R.string.notif_not_now)
                    .onPositive((dialog, which) -> {
                        G.gPrefs.edit().putBoolean(asked, true).apply();
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_NOTIFICATION);
                    })
                    .onNegative((dialog, which) -> G.gPrefs.edit().putBoolean(asked, true).apply())
                    .show();
            return;
        }
        boolean enabled = granted && androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (enabled || notificationsOffReminderShown || G.gPrefs.getBoolean(dontRemind, false)) {
            return;
        }
        notificationsOffReminderShown = true;
        new MaterialDialog.Builder(this)
                .title(R.string.notif_off_title)
                .content(R.string.notif_off_text)
                .positiveText(R.string.notif_open_settings)
                .negativeText(R.string.notif_not_now)
                .neutralText(R.string.notif_dont_remind)
                .onPositive((dialog, which) ->
                        dev.ukanth.ufirewall.preferences.UIPreferenceFragment.openNotificationSettings(MainActivity.this))
                .onNeutral((dialog, which) -> G.gPrefs.edit().putBoolean(dontRemind, true).apply())
                .show();
    }

    private void updateSelectedColumns() {
        selectedColumns = DEFAULT_COLUMN;
        selectedColumns = G.enableLAN() ? selectedColumns + 1 : selectedColumns;
        selectedColumns = G.enableRoam() ? selectedColumns + 1 : selectedColumns;
        selectedColumns = G.enableVPN() ? selectedColumns + 1 : selectedColumns;
        selectedColumns = G.enableTether() ? selectedColumns + 1 : selectedColumns;
        selectedColumns = G.enableTor() ? selectedColumns + 1 : selectedColumns;
    }

    private void registerLogService() {
        if (G.enableLogService()) {
            Log.i(G.TAG, "Starting Log Service");
            // guarded foreground start: a plain startService() throws when the activity is
            // created while the screen is off / locked (the app counts as background then)
            LogService.ensureRunning(getApplicationContext());
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerUIRefresh() {
        IntentFilter filter = new IntentFilter("dev.ukanth.ufirewall.ui.CHECKREFRESH");
        uiRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateSelectedColumns();
                if (selectedColumns <= DEFAULT_VIEW_LIMIT && currentUI == 1) {
                    recreate();
                } else if (selectedColumns > DEFAULT_VIEW_LIMIT && currentUI == 0) {
                    recreate();
                } else {
                    recreate();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiRefreshReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(uiRefreshReceiver, filter);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerThemeIntent() {

        IntentFilter filter = new IntentFilter("dev.ukanth.ufirewall.theme.REFRESH");
        themeRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                initTheme();
                recreate();
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeRefreshReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(themeRefreshReceiver, filter);
        }
    }

    /**
     * Reads which log targets the kernel supports. Done on every start, since a ROM or kernel
     * update can add or remove one; a saved target that is gone is replaced so logging keeps
     * working (or says why it can't).
     */
    private void probeLogTarget() {
        try {
            new RootCommand()
                    .setReopenShell(true)
                    .setCallback(new RootCommand.Callback() {
                        public void cbFunc(RootCommand state) {
                            if (state.exitCode != 0) {
                                return;
                            }
                            List<String> availableLogTargets = new ArrayList<>();
                            for (String str : state.res.toString().split("\n")) {
                                str = str.trim();
                                if ((str.equals("LOG") || str.equals("NFLOG")) && !availableLogTargets.contains(str)) {
                                    availableLogTargets.add(str);
                                }
                            }
                            String joined = TextUtils.join(",", availableLogTargets);
                            if (!joined.equals(G.logTargets())) {
                                Log.i(Api.TAG, "Log targets changed from " + G.logTargets() + " to " + joined);
                                G.logTargets(joined);
                            }
                            String current = G.logTarget();
                            if (current.isEmpty() && availableLogTargets.size() == 1) {
                                // nothing to choose from: same as the log settings do
                                G.logTarget(availableLogTargets.get(0));
                            } else if (!current.isEmpty() && !availableLogTargets.contains(current)) {
                                String replacement = availableLogTargets.isEmpty() ? "" : availableLogTargets.get(0);
                                Log.w(Api.TAG, "Log target " + current + " is no longer supported, using '" + replacement + "'");
                                G.logTarget(replacement);
                            }
                        }
                    }).setLogging(true)
                    .run(ctx, "cat /proc/net/ip_tables_targets");
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception in getting iptables log targets", e);
        }
    }

    private void initTheme() {
        ThemeHelper.applyTheme(this);
    }

    private void initTextWatcher() {
        filterTextWatcher = new TextWatcher() {

            public void afterTextChanged(Editable s) {
                showApplications(s.toString());
            }


            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
                showApplications(s.toString());
            }
        };
    }


    private void registerNetworkObserver() {
        //start log service
        LogService.ensureRunning(getApplicationContext());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (action == null) {
            return;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }


    /*private void migrateNotification() {
        try {
            if (!G.isNotificationMigrated()) {
                List<Integer> idList = G.getBlockedNotifyList();
                for (Integer uid : idList) {
                    LogPreference preference = new LogPreference();
                    preference.setUid(uid);
                    preference.setTimestamp(System.currentTimeMillis());
                    preference.setDisable(true);
                    FlowManager.getDatabase(LogPreferenceDB.class).beginTransactionAsync(databaseWrapper -> preference.save(databaseWrapper)).build().execute();
                }
                G.isNotificationMigrated(true);
            }
        } catch (Exception e) {
            Log.e(G.TAG, "Unable to migrate notification", e);
        }
    }*/


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerToastbroadcast() {
        IntentFilter filter = new IntentFilter("TOAST");
        toastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Api.toast(getApplicationContext(), intent.getExtras().get("MSG") != null ? intent.getExtras().get("MSG").toString() : "", Toast.LENGTH_SHORT);
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toastReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(toastReceiver, filter);
        }
    }

    private void registerUIbroadcast4() {
        uiFilter4 = new IntentFilter("UPDATEUI4");

        uiProgressReceiver4 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle b = intent.getExtras();
                if (runProgress != null) {
                    TextView view = (TextView) runProgress.findViewById(R.id.apply4);
                    view.setText(b.get("INDEX") + "/" + b.get("SIZE"));
                    view.invalidate();
                }
            }
        };
    }

    private void registerUIbroadcast6() {
        uiFilter6 = new IntentFilter("UPDATEUI6");

        uiProgressReceiver6 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle b = intent.getExtras();
                if (runProgress != null) {
                    TextView view = (TextView) runProgress.findViewById(R.id.apply6);
                    view.setText(b.get("INDEX") + "/" + b.get("SIZE"));
                    view.invalidate();
                }
            }
        };
    }

    @Override
    public void onRefresh() {
        index = 0;
        top = 0;
        Api.applications = null;
        showOrLoadApplications();
        mSwipeLayout.setRefreshing(false);
    }

    private void updateRadioFilter() {
        RadioGroup radioGroup = findViewById(R.id.appFilterGroup);
        if (G.showFilter()) {
            switch (G.selectedFilter()) {
                case 0:
                    radioGroup.check(R.id.rpkg_core);
                    break;
                case 1:
                    radioGroup.check(R.id.rpkg_sys);
                    break;
                case 2:
                    radioGroup.check(R.id.rpkg_user);
                    break;
                default:
                    radioGroup.check(R.id.rpkg_all);
                    break;
            }
        } else {
            radioGroup.check(R.id.rpkg_all);
        }
        radioGroup.setOnCheckedChangeListener(this);
    }

    private void selectFilterGroup() {
        if (G.showFilter()) {
            RadioGroup radioGroup = findViewById(R.id.appFilterGroup);
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.rpkg_core) {
                filterApps(2);
            } else if (selectedId == R.id.rpkg_sys) {
                filterApps(0);
            } else if (selectedId == R.id.rpkg_user) {
                filterApps(1);
            } else {
                filterApps(-1);
            }
        } else {
            filterApps(-1);
        }

    }

    /**
     * Filter application based on app tpe
     *
     * @param i
     */
    private void filterApps(int i) {
        // Never run the (slow) app scan on the UI thread. If the cache was never
        // built, load it asynchronously and let onPostExecute re-trigger the filter.
        // (A non-null but empty cache means a scan already ran - show it, don't re-loop.)
        if (Api.applications == null) {
            showOrLoadApplications();
            return;
        }
        Set<PackageInfoData> returnList = new HashSet<>();
        List<PackageInfoData> inputList;
        List<PackageInfoData> allApps = Api.applications;
        if (i >= 0) {
            for (PackageInfoData infoData : allApps) {
                if (infoData != null) {
                    if (infoData.appType == i) {
                        returnList.add(infoData);
                    }
                }
            }
            inputList = new ArrayList<>(returnList);
        } else {
            if (allApps != null && allApps.size() > 0) {
                inputList = allApps;
            } else {
                inputList = new ArrayList<>(returnList);
            }
        }
        if (inputList != null && inputList.size() > 0) {
            try {
                Collections.sort(inputList, new PackageComparator());
            } catch (Exception e) {
            }
            ArrayAdapter appAdapter;
            if (selectedColumns <= DEFAULT_VIEW_LIMIT) {
                appAdapter = new AppListArrayAdapter(this, getApplicationContext(), inputList, true);
            } else {
                appAdapter = new AppListArrayAdapter(this, getApplicationContext(), inputList);
            }
            this.listview.setAdapter(appAdapter);
            appAdapter.notifyDataSetChanged();
            // restore
            this.listview.setSelectionFromTop(index, top);
        } else {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }


    private void updateIconStatus() {
        if (Api.isEnabled(getApplicationContext())) {
            getSupportActionBar().setIcon(R.drawable.notification);
        } else {
            getSupportActionBar().setIcon(R.drawable.notification_error);
        }
    }

    private void startRootShell() {
        List<String> cmds = new ArrayList<String>();
        cmds.add("true");
        new RootCommand().setFailureToast(R.string.error_su)
                .setReopenShell(true)
                .setCallback(new RootCommand.Callback() {
                    public void cbFunc(RootCommand state) {
                        //failed to acquire root
                        if (state.exitCode != 0) {
                            runOnUiThread(() -> {
                                disableFirewall();
                                showRootNotFoundMessage();
                            });
                        }
                    }
                }).run(getApplicationContext(), cmds);
        //check if log targets support
        probeLogTarget();
    }

    private void showRootNotFoundMessage() {
        if (G.isActivityVisible()) {
            try {
                new MaterialDialog.Builder(this).cancelable(false)
                        .title(R.string.error_common)
                        .content(R.string.error_su)
                        .onPositive((dialog, which) -> dialog.dismiss())
                        .onNegative((dialog, which) -> {
                            MainActivity.this.finish();
                            android.os.Process.killProcess(android.os.Process.myPid());
                            dialog.dismiss();
                        })
                        .positiveText(R.string.Continue)
                        .negativeText(R.string.exit)
                        .show();
            } catch (Exception e) {
                Api.toast(this, getString(R.string.error_su_toast), Toast.LENGTH_SHORT);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        /*if (showQuickButton()) {
            fab.setVisibility(View.VISIBLE);
        } else {
            fab.setVisibility(View.GONE);
        }*/
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(uiProgressReceiver4, uiFilter4);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(uiProgressReceiver6, uiFilter6);

        // FirewallService owns the network-change receiver. Its background starts (boot on
        // Android 15 after a force-stop, widget, Tasker) can be refused, so start it while in front.
        FirewallService.ensureRunning(getApplicationContext());

        G.activityResumed();

        //getSupportActionBar().setBackgroundDrawable(new ColorDrawable(G.primaryColor()));
       /* Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(G.primaryDarkColor());*/

    }

    private void reloadPreferences() {

        updateSelectedColumns();

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        G.reloadPrefs();
        checkPreferences();
        //language
        Api.updateLanguage(getApplicationContext(), G.locale());

        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }

        //verifyMultiProfile();
        refreshHeader();
        updateIconStatus();

        clearNotification();

        if (G.disableIcons()) {
            this.findViewById(R.id.imageHolder).setVisibility(View.GONE);
        } else {
            this.findViewById(R.id.imageHolder).setVisibility(View.VISIBLE);
        }

        if (G.showFilter()) {
            this.findViewById(R.id.filerOption).setVisibility(View.VISIBLE);
        } else {
            this.findViewById(R.id.filerOption).setVisibility(View.GONE);
        }

        if (G.enableMultiProfile()) {
            this.findViewById(R.id.profileOption).setVisibility(View.VISIBLE);
        } else {
            this.findViewById(R.id.profileOption).setVisibility(View.GONE);
        }
        if (G.enableRoam()) {
            addColumns(R.id.img_roam);
        } else {
            hideColumns(R.id.img_roam);
        }
        if (G.enableVPN()) {
            addColumns(R.id.img_vpn);
        } else {
            hideColumns(R.id.img_vpn);
        }
        if (G.enableTether()) {
            addColumns(R.id.img_tether);
        } else {
            hideColumns(R.id.img_tether);
        }

        if (!Api.isMobileNetworkSupported(getApplicationContext())) {
            ImageView view = this.findViewById(R.id.img_3g);
            view.setVisibility(View.GONE);

        } else {
            this.findViewById(R.id.img_3g).setOnClickListener(this);
        }

        if (G.enableLAN()) {
            addColumns(R.id.img_lan);
        } else {
            hideColumns(R.id.img_lan);
        }
        if (G.enableTor()) {
            addColumns(R.id.img_tor);
        } else {
            hideColumns(R.id.img_tor);
        }
        updateRadioFilter();

        if (G.enableMultiProfile()) {
            setupMultiProfile();
        }

        // Use async loading to avoid blocking the main thread
        // If the app list is already cached, filterApps will use the cache (fast path)
        // If not cached, showOrLoadApplications will load asynchronously with a progress dialog
        if (Api.applications != null && Api.applications.size() > 0) {
            // Cache is warm - use it directly (fast, non-blocking)
            selectFilterGroup();
        } else {
            // Cache is cold - load asynchronously to avoid ANR
            showOrLoadApplications();
        }
    }

    private void clearNotification() {
        //make sure we cancel notification posted by app notification.
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(100);
    }

    /**
     * This will be used to migrate the profiles to a better one ( get ridoff of default profile )
     */


    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.rpkg_all) {
            filterApps(-1);
            G.saveSelectedFilter(99);
        } else if (checkedId == R.id.rpkg_core) {
            filterApps(2);
            G.saveSelectedFilter(0);
        } else if (checkedId == R.id.rpkg_sys) {
            filterApps(0);
            G.saveSelectedFilter(1);
        } else if (checkedId == R.id.rpkg_user) {
            filterApps(1);
            G.saveSelectedFilter(2);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        initDone = 0;
        reloadPreferences();
    }

    private void addColumns(int id) {
        ImageView view = this.findViewById(id);
        view.setVisibility(View.VISIBLE);
        view.setOnClickListener(this);
    }

    private void hideColumns(int id) {
        ImageView view = this.findViewById(id);
        view.setVisibility(View.GONE);
        view.setOnClickListener(this);
    }

    private void setupMultiProfile() {
        reloadProfileList(true);
        mSpinner = findViewById(R.id.profileGroup);
        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                mlocalList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(spinnerAdapter);
        mSpinner.setOnItemSelectedListener(this);
        String currentProfile = G.storedProfile();
        if (currentProfile != null) {
            if (!currentProfile.equals(Api.DEFAULT_PREFS_NAME)) {
                ProfileData data = ProfileHelper.getProfileByIdentifier(currentProfile);
                if (data != null) {
                    mSpinner.setSelection(spinnerAdapter.getPosition(data.getName()), false);
                }
            } else {
                mSpinner.setSelection(0, false);
            }
        }
    }

    private void reloadProfileList(boolean reset) {
        if (reset) {
            mlocalList = new ArrayList<>(new LinkedHashSet<String>());
        }

        mlocalList.add(G.gPrefs.getString("default", getString(R.string.defaultProfile)));

        List<ProfileData> profilesList = ProfileHelper.getProfiles();
        for (ProfileData data : profilesList) {
            mlocalList.add(data.getName());
        }
    }

    public void deviceCheck() {
        if (Build.VERSION.SDK_INT >= 21) {
            if ((G.isDoKey(getApplicationContext()) || isDonate())) {
                KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (keyguardManager.isKeyguardSecure()) {
                    Intent createConfirmDeviceCredentialIntent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
                    if (createConfirmDeviceCredentialIntent != null) {
                        try {
                            startActivityForResult(createConfirmDeviceCredentialIntent, LOCK_VERIFICATION);
                        } catch (ActivityNotFoundException e) {
                        }
                    }
                } else {
                    Toast.makeText(this, getText(R.string.android_version), Toast.LENGTH_SHORT).show();
                }
            } else {
                Api.donateDialog(MainActivity.this, true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if ((plsWait != null) && plsWait.isShowing()) {
                plsWait.dismiss();
            }

        } catch (final IllegalArgumentException e) {
            // Handle or log or ignore
        } catch (final Exception e) {
            // Handle or log or ignore
        } finally {
            plsWait = null;
        }
        //this.listview.setAdapter(null);
        //mLastPause = Syst em.currentTimeMillis();
        //isOnPause = true;
        //checkForProfile = true;
        index = this.listview.getFirstVisiblePosition();
        View v = this.listview.getChildAt(0);
        top = (v == null) ? 0 : v.getTop();
        G.activityPaused();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(uiProgressReceiver4);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(uiProgressReceiver6);
    }

    /**
     * Check if the stored preferences are OK
     */
    private void checkPreferences() {
        final Editor editor = G.pPrefs.edit();
        boolean changed = false;
        if (G.pPrefs.getString(Api.PREF_MODE, "").length() == 0) {
            editor.putString(Api.PREF_MODE, Api.MODE_WHITELIST);
            changed = true;
        }
        if (changed)
            editor.apply(); // Use apply() instead of commit() to avoid blocking main thread
    }

    /**
     * Refresh informative header
     */
    private void refreshHeader() {
        final String mode = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
        //final TextView labelmode = (TextView) this.findViewById(R.id.label_mode);
        final Resources res = getResources();

        if (mode.equals(Api.MODE_WHITELIST)) {
            if (mainMenu != null) {
                mainMenu.findItem(R.id.allowmode).setChecked(true);
                mainMenu.findItem(R.id.menu_mode).setIcon(R.drawable.ic_allow);
            }
        } else {
            if (mainMenu != null) {
                mainMenu.findItem(R.id.blockmode).setChecked(true);
                mainMenu.findItem(R.id.menu_mode).setIcon(R.drawable.ic_deny);
            }
        }
        //int resid = (mode.equals(Api.MODE_WHITELIST) ? R.string.mode_whitelist: R.string.mode_blacklist);
        //labelmode.setText(res.getString(R.string.mode_header, res.getString(resid)));
    }

    /**
     * If the applications are cached, just show them, otherwise load and show
     */
    private void showOrLoadApplications() {
        //nocache!!
        getAppList = new GetAppList(MainActivity.this);
        if (plsWait == null && (getAppList.getStatus() == AsyncTask.Status.PENDING || getAppList.getStatus() == AsyncTask.Status.FINISHED)) {
            getAppList.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        initDone = initDone + 1;
        if (initDone > 1) {
            Spinner spinner = findViewById(R.id.profileGroup);
            String profileName = spinner.getSelectedItem().toString();
            if (position == 0) {
                G.setProfile(true, Api.DEFAULT_PREFS_NAME);
            } else if (profileName != null) {
                ProfileData data = ProfileHelper.getProfileByName(profileName);
                if (data != null) {
                    G.setProfile(true, data.getIdentifier());
                }
            }
            setDirty(true);
            G.reloadProfile();
            refreshHeader();
            showOrLoadApplications();
            if (G.applyOnSwitchProfiles()) {
                applyOrSaveRules();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    /**
     * Show the list of applications
     */
    private void showApplications(final String searchStr) {

        setDirty(false);

        List<PackageInfoData> searchApp = new ArrayList<>();
        HashSet<Integer> unique = new HashSet<>();
        final List<PackageInfoData> apps = Api.getApps(this, null);
        boolean isResultsFound = false;

        if (searchStr != null && searchStr.length() > 1) {
            for (PackageInfoData app : apps) {
                for (String str : app.names) {
                    if (str != null && searchStr != null) {
                        if (str.contains(searchStr.toLowerCase()) || str.toLowerCase().contains(searchStr.toLowerCase())
                                && !searchApp.contains(app) || (G.showUid() && (str + " " + app.uid).contains(searchStr) && !unique.contains(app.uid))) {
                            searchApp.add(app);
                            unique.add(app.uid);
                            isResultsFound = true;
                        }
                    }
                }
                // Package names are stored separately from labels, so include them in search.
                if (!unique.contains(app.uid) && app.pkgName != null
                        && app.pkgName.toLowerCase().contains(searchStr.toLowerCase())) {
                    searchApp.add(app);
                    unique.add(app.uid);
                    isResultsFound = true;
                }
            }
        }

        List<PackageInfoData> apps2 = null;
        if (searchStr != null && searchStr.equals("")) {
            apps2 = apps;
        } else if (isResultsFound || searchApp.size() > 0) {
            apps2 = searchApp;
        } else {
            apps2 = new ArrayList<>();
        }
        // Sort applications - selected first, then alphabetically
        try {
            if (apps2 != null) {
                Collections.sort(apps2, new PackageComparator());
                ArrayAdapter appAdapter;
                if (selectedColumns <= DEFAULT_VIEW_LIMIT) {
                    appAdapter = new AppListArrayAdapter(this, getApplicationContext(), apps2, true);
                } else {
                    appAdapter = new AppListArrayAdapter(this, getApplicationContext(), apps2);
                }
                this.listview.setAdapter(appAdapter);
                // restore
                this.listview.setSelectionFromTop(index, top);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public boolean onSearchRequested() {
        if (mainMenu != null) {
            MenuItem menuItem = mainMenu.findItem(R.id.menu_search); // R.string.search is the id of the searchview
            if (menuItem != null) {
                if (menuItem.isActionViewExpanded()) {
                    menuItem.collapseActionView();
                } else {
                    menuItem.expandActionView();
                    search(menuItem);
                }
            }
        }
        return super.onSearchRequested();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //language
        Api.updateLanguage(getApplicationContext(), G.locale());
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_bar, menu);

        // Get widget's instance
        mainMenu = menu;
        //make sure we update sort entry
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (G.sortBy()) {
                    case "s0":
                        mainMenu.findItem(R.id.sort_default).setChecked(true);
                        break;
                    case "s1":
                        mainMenu.findItem(R.id.sort_lastupdate).setChecked(true);
                        break;
                    case "s2":
                        mainMenu.findItem(R.id.sort_uid).setChecked(true);
                        break;
                }

                refreshHeader();
            }
        });
        return true;
    }

    public void menuSetApplyOrSave(final Menu menu, final boolean isEnabled) {
        runOnUiThread(() -> {
            if (menu != null) {
                if (isEnabled) {
                    menu.findItem(R.id.menu_toggle).setTitle(R.string.fw_disabled).setIcon(R.drawable.notification_error);
                    menu.findItem(R.id.menu_apply).setTitle(R.string.applyrules);
                    getSupportActionBar().setIcon(R.drawable.notification);
                } else {
                    menu.findItem(R.id.menu_toggle).setTitle(R.string.fw_enabled).setIcon(R.drawable.notification);
                    menu.findItem(R.id.menu_apply).setTitle(R.string.saverules);
                    getSupportActionBar().setIcon(R.drawable.notification_error);
                }
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        //language
        Api.updateLanguage(getApplicationContext(), G.locale());
        if (menu != null) {
            menuSetApplyOrSave(mainMenu, Api.isEnabled(MainActivity.this));
        }
        return true;
    }

    private void disableFirewall() {
        Api.setEnabled(this, false, true);
        menuSetApplyOrSave(mainMenu, false);
    }

    private void disableOrEnable() {
        final boolean enabled = !Api.isEnabled(this);
        Api.setEnabled(this, enabled, true);
        if (enabled) {
            applyOrSaveRules();
        } else {
            if (G.enableConfirm()) {
                confirmDisable();
            } else {
                purgeRules();
            }
        }
        //refreshHeader();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        int selectedItem = item.getItemId();
        if (selectedItem == R.id.menu_toggle) {
            disableOrEnable();
            return true;
        } else if (selectedItem == R.id.allowmode) {
            item.setChecked(true);
            Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
            editor.putString(Api.PREF_MODE, Api.MODE_WHITELIST);
            editor.apply();
            refreshHeader();
            return true;
        } else if (selectedItem == R.id.blockmode) {
            item.setChecked(true);
            Editor editor2 = getSharedPreferences(Api.PREFS_NAME, 0).edit();
            editor2.putString(Api.PREF_MODE, Api.MODE_BLACKLIST);
            editor2.apply();
            refreshHeader();
            return true;
        } else if (selectedItem == R.id.sort_default) {
            G.sortBy("s0");
            item.setChecked(true);
            Api.applications = null;
            showOrLoadApplications();
            return true;
        } else if (selectedItem == R.id.sort_lastupdate) {
            G.sortBy("s1");
            item.setChecked(true);
            Api.applications = null;
            showOrLoadApplications();
            return true;
        } else if (selectedItem == R.id.sort_uid) {
            G.sortBy("s2");
            item.setChecked(true);
            Api.applications = null;
            showOrLoadApplications();
            return true;
        } else if (selectedItem == R.id.menu_apply) {
            applyOrSaveRules();
            return true;
        } else if (selectedItem == R.id.menu_exit) {
            finish();
            return true;
        } else if (selectedItem == R.id.menu_help) {
            showAbout();
            return true;
        } else if (selectedItem == R.id.menu_log) {
            showLog();
            return true;
        } else if (selectedItem == R.id.menu_rules) {
            showRules();
            return true;
        } else if (selectedItem == R.id.menu_setcustom) {
            setCustomScript();
            return true;
        } else if (selectedItem == R.id.menu_preference) {
            showPreferences();
            return true;
        } else if (selectedItem == R.id.menu_search) {
            search(item);
            return true;
        } else if (selectedItem == R.id.menu_export) {
            if (!BackupStorage.needsStoragePermission()) {
                migrateOldBackups(this::showExportDialog);
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // permissions have not been granted.
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
                } else {
                    migrateOldBackups(this::showExportDialog);
                }
            }
            return true;
        } else if (selectedItem == R.id.menu_import) {
            // the system file picker needs no storage permission
            migrateOldBackups(this::showImportDialog);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * One time: copies backups from the folders earlier versions used to Download/AFWall/, so they
     * show up in the import picker and survive an uninstall. Runs again until every file copied.
     */
    private void migrateOldBackups(@Nullable Runnable then) {
        boolean permitted = !BackupStorage.needsStoragePermission()
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        if (G.hasMovedBackupsToDownloads() || !permitted || backupMigrationRunning) {
            if (then != null) {
                then.run();
            }
            return;
        }
        backupMigrationRunning = true;
        final Context appContext = getApplicationContext();
        new Thread(() -> {
            int copied = BackupStorage.migrateLegacyBackups(appContext);
            if (copied >= 0) {
                G.hasMovedBackupsToDownloads(true);
            }
            runOnUiThread(() -> {
                backupMigrationRunning = false;
                if (copied > 0) {
                    Api.toast(appContext, getResources().getQuantityString(
                            R.plurals.backups_moved_to_downloads, copied, copied), Toast.LENGTH_LONG);
                }
                if (then != null && !isFinishing()) {
                    then.run();
                }
            });
        }, "afwall-backup-migration").start();
    }

    private void search(MenuItem item) {
        item.setActionView(R.layout.searchbar);
        final EditText filterText = item.getActionView().findViewById(
                R.id.searchApps);
        filterText.addTextChangedListener(filterTextWatcher);
        filterText.setEllipsize(TruncateAt.END);
        filterText.setSingleLine();

        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                // Do something when collapsed
                selectFilterGroup();
                return true;  // Return true to collapse action view
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                filterText.post(() -> {
                    filterText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(filterText, InputMethodManager.SHOW_IMPLICIT);
                });
                return true;  // Return true to expand action view
            }
        });
    }

    private void showImportDialog() {
        try {
            new MaterialDialog.Builder(this)
                    .title(R.string.imports)
                    .cancelable(false)
                    .items(new String[]{
                            getString(R.string.import_rules),
                            getString(R.string.import_all) + (G.isDoKey(getApplicationContext()) || isDonate() ? "" : " (" + getString(R.string.donate_only_short) + ")")})
                    .itemsCallbackSingleChoice(-1, (dialog, view, which, text) -> {
                    switch (which) {
                        case 0:
                            pickImportFile(false);
                            break;
                        case 1:
                            if (G.isDoKey(getApplicationContext()) || isDonate()) {
                                pickImportFile(true);
                            } else {
                                Api.donateDialog(MainActivity.this, false);
                            }
                            break;
                    }
                    return true;
                })
                .positiveText(R.string.imports)
                .negativeText(R.string.Cancel)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "MaterialDialog failed, likely due to cursor tinting issue on newer Android versions", e);
            pickImportFile(false);
        }
    }

    private void pickImportFile(boolean importAll) {
        pendingImportAll = importAll;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                // file managers report .json as anything from application/json to octet-stream
                .setType("*/*");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, BackupStorage.pickerInitialUri());
        }
        try {
            importFileLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No file picker available", e);
            Api.toast(this, getString(R.string.import_rules_fail));
        }
    }

    private void onImportFilePicked(ActivityResult result) {
        Uri uri = result.getData() != null ? result.getData().getData() : null;
        if (result.getResultCode() != RESULT_OK || uri == null) {
            return;
        }
        final boolean importAll = pendingImportAll;
        String fileName = BackupStorage.displayName(this, uri);
        StringBuilder builder = new StringBuilder();
        boolean imported;
        try {
            imported = Api.importBackup(this, builder, BackupStorage.read(this, uri), importAll);
        } catch (IOException e) {
            Log.e(TAG, "Unable to read " + uri, e);
            builder.append(e.getMessage() != null ? e.getMessage() : getString(R.string.import_rules_fail));
            imported = false;
        }
        if (imported) {
            Api.applications = null;
            // imported rules take effect on the next apply; prompt for it
            setDirty(true);
            showOrLoadApplications();
            // skipped-apps note first: the file name gets truncated
            Api.toast(this, (builder.length() > 0 ? builder + "\n" : "")
                    + getString(R.string.import_rules_success) + fileName);
            if (importAll) {
                Intent intent = getIntent();
                finish();
                startActivity(intent);
            }
        } else if (builder.length() == 0) {
            Api.toast(this, getString(R.string.import_rules_fail));
        } else {
            Api.toast(this, builder.toString());
        }
    }

    private void showExportDialog() {
        try {
            new MaterialDialog.Builder(this)
                    .title(R.string.exports)
                    .cancelable(false)
                    .items(new String[]{
                            getString(R.string.export_rules),
                            getString(R.string.export_all) + (G.isDoKey(getApplicationContext()) || isDonate() ? "" : " (" + getString(R.string.donate_only_short) + ")")})
                    .itemsCallbackSingleChoice(-1, (dialog, view, which, text) -> {
                        switch (which) {
                            case 0:
                                Api.exportRulesToFileConfirm(MainActivity.this);
                                break;
                            case 1:
                                if (G.isDoKey(getApplicationContext()) || isDonate()) {
                                    Api.exportAllPreferencesToFileConfirm(MainActivity.this);
                                } else {
                                    showExportAllWarningDialog();
                                }
                                break;
                        }
                        return true;
                    }).positiveText(R.string.exports)
                    .negativeText(R.string.Cancel)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "MaterialDialog failed, likely due to cursor tinting issue on newer Android versions", e);
            // same fallback as import: the basic option, without the dialog
            Api.exportRulesToFileConfirm(this);
        }
    }

    private void showExportAllWarningDialog() {
        try {
            new MaterialDialog.Builder(this)
                    .title(R.string.export_all)
                    .content(R.string.export_all_warning)
                    .positiveText(R.string.exports)
                    .negativeText(R.string.Cancel)
                    .onPositive((dialog, which) -> {
                        Api.exportAllPreferencesToFileConfirm(MainActivity.this);
                    })
                    .show();
        } catch (Exception e) {
            Log.e(TAG, "MaterialDialog failed, likely due to cursor tinting issue on newer Android versions", e);
            // Fallback: Just show the export directly with a toast warning
            Api.toast(this, getString(R.string.export_all_warning));
            Api.exportAllPreferencesToFileConfirm(MainActivity.this);
        }
    }

    private void showPreferences() {
        Intent i = new Intent(this, PreferencesActivity.class);
        //startActivity(i);
        startActivityForResult(i, PREFERENCE_RESULT);
    }

    private void showAbout() {
        Intent i = new Intent(this, HelpActivity.class);
        startActivityForResult(i, SHOW_ABOUT_RESULT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    migrateOldBackups(this::showExportDialog);
                } else {
                    Toast.makeText(this, R.string.permissiondenied_importexport, Toast.LENGTH_SHORT).show();
                }
                return;
            }

            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE_ASSET: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Api.assertBinaries(this, true);
                } else {
                    Toast.makeText(this, R.string.permissiondenied_asset, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public void confirmDisable() {

        new MaterialDialog.Builder(this)
                .title(R.string.confirmMsg)
                //.content(R.string.confirmMsg)
                .cancelable(false)
                .onPositive((dialog, which) -> {
                    purgeRules();
                    Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
                    dialog.dismiss();
                })
                .onNegative((dialog, which) -> {
                    Api.setEnabled(getApplicationContext(), true, true);
                    dialog.dismiss();
                })
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .show();
    }

    /**
     * Set a new init script
     */
    private void setCustomScript() {
        Intent intent = new Intent();
        intent.setClass(this, CustomScriptActivity.class);
        startActivityForResult(intent, SHOW_CUSTOM_SCRIPT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case LOCK_VERIFICATION:
            case REQ_ENTER_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        showOrLoadApplications();
                        break;
                    default:
                        MainActivity.this.finish();
                        android.os.Process.killProcess(android.os.Process.myPid());
                        break;
                }
            }
            break;

            case VERIFY_CHECK: {
                Log.i(Api.TAG, "In VERIFY_CHECK");
                switch (resultCode) {
                    case RESULT_OK:
                        G.isDo(true);
                        break;
                    case RESULT_CANCELED:
                        G.isDo(false);
                }
            }
            break;
            //isPassVerify = true;
            case PREFERENCE_RESULT: {
                invalidateOptionsMenu();
                //recreate();
            }
            break;
        }
        if (resultCode == RESULT_OK
                && data != null && Api.CUSTOM_SCRIPT_MSG.equals(data.getAction())) {
            final String script = data.getStringExtra(Api.SCRIPT_EXTRA);
            final String script2 = data.getStringExtra(Api.SCRIPT2_EXTRA);
            setCustomScript(script, script2);
        }
    }

    /**
     * Set a new init script
     *
     * @param script  new script (empty to remove)
     * @param script2 new "shutdown" script (empty to remove)
     */
    private void setCustomScript(String script, String script2) {
        final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
        // Remove unnecessary white-spaces, also replace '\r\n' if necessary
        script = script.trim().replace("\r\n", "\n");
        script2 = script2.trim().replace("\r\n", "\n");
        editor.putString(Api.PREF_CUSTOMSCRIPT, script);
        editor.putString(Api.PREF_CUSTOMSCRIPT2, script2);
        int msgid;
        if (editor.commit()) {
            if (script.length() > 0 || script2.length() > 0) {
                msgid = R.string.custom_script_defined;
            } else {
                msgid = R.string.custom_script_removed;
            }
        } else {
            msgid = R.string.custom_script_error;
        }
        Api.toast(MainActivity.this, MainActivity.this.getString(msgid));
        if (Api.isEnabled(this)) {
            // If the firewall is enabled, re-apply the rules
            applyOrSaveRules();
        }
    }

    /**
     * Show iptables rules on a dialog
     */
    private void showRules() {
        Intent i = new Intent(this, RulesActivity.class);
        startActivityForResult(i, SHOW_RULES_ACTIVITY);
    }

    /**
     * Show logs on a dialog
     */
    private void showLog() {
        if (G.oldLogView()) {
            Intent i = new Intent(this, OldLogActivity.class);
            startActivityForResult(i, SHOW_LOGS_ACTIVITY);
        } else {
            Intent i = new Intent(this, LogActivity.class);
            startActivityForResult(i, SHOW_LOGS_ACTIVITY);
        }
    }

    /**
     * Apply or save iptables rules, showing a visual indication
     */
    private void applyOrSaveRules() {
        final boolean enabled = Api.isEnabled(this);
        final Context ctx = getApplicationContext();

        Api.generateRules(ctx, Api.getApps(ctx, null), true);

        if (!enabled) {
            Api.setEnabled(ctx, false, true);
            Api.toast(ctx, ctx.getString(R.string.rules_saved));
            setDirty(false);
            return;
        }
        //Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
        Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
        runApply = new RunApply(MainActivity.this);
        runApply.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Purge iptables rules, showing a visual indication
     */
    private void purgeRules() {
        purgeTask = new PurgeTask(MainActivity.this);
        purgeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.img_wifi || id == R.id.img_3g
                || id == R.id.img_roam
                || id == R.id.img_vpn
                || id == R.id.img_tether
                || id == R.id.img_lan
                || id == R.id.img_tor) {
            selectActionConfirmation(v.getId());
        } else if (id == R.id.img_action) {
            selectAction();
        }
    }

    private void selectAction() {
        new MaterialDialog.Builder(this)
                .title(R.string.confirmation)
                .cancelable(true)
                .negativeText(R.string.Cancel)
                .items(new String[]{
                        getString(R.string.invert_all),
                        getString(R.string.clone),
                        getString(R.string.clear_all)})
                .itemsCallback((dialog, view, which, text) -> {
                    switch (which) {
                        case 0:
                            selectActionConfirmation(getString(R.string.reverse_all), 0);
                            break;
                        case 1:
                            cloneColumn(getString(R.string.legend_clone));
                            break;
                        case 2:
                            selectActionConfirmation(getString(R.string.unselect_all), 1);
                            break;
                    }
                })
                .onNegative((dialog, which) -> dialog.dismiss())
                .show();

    }

    private void selectAllLAN(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_lan = flag;
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectAllTor(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_tor = flag;
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    /**
     * Cache any batch event by user
     *
     * @param
     */
    /*public static void addToQueue(@NonNull PackageInfoData data) {
     *//*if (queue == null) {
            queue = new HashSet<>();
        }
        //add or update based on new data
        queue.add(data);
        getFab().setBackgroundTintList(ColorStateList.valueOf(Color.RED));*//*
    }*/
    private void selectAllVPN(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_vpn = flag;
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectAlltether(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_tether = flag;
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectRevert(int flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    if (flag == R.id.img_wifi) {
                        data.selected_wifi = !data.selected_wifi;
                    } else if (flag == R.id.img_3g) {
                        data.selected_3g = !data.selected_3g;
                    } else if (flag == R.id.img_roam) {
                        data.selected_roam = !data.selected_roam;
                    } else if (flag == R.id.img_vpn) {
                        data.selected_vpn = !data.selected_vpn;
                    } else if (flag == R.id.img_tether) {
                        data.selected_tether = !data.selected_tether;
                    } else if (flag == R.id.img_lan) {
                        data.selected_lan = !data.selected_lan;
                    } else if (flag == R.id.img_tor) {
                        data.selected_tor = !data.selected_tor;
                    }
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectRevert() {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_wifi = !data.selected_wifi;
                    data.selected_3g = !data.selected_3g;
                    data.selected_roam = !data.selected_roam;
                    data.selected_vpn = !data.selected_vpn;
                    data.selected_tether = !data.selected_tether;
                    data.selected_lan = !data.selected_lan;
                    data.selected_tor = !data.selected_tor;
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectAllRoam(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_roam = flag;
                    //addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void clearAll() {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                data.selected_wifi = false;
                data.selected_3g = false;
                data.selected_roam = false;
                data.selected_vpn = false;
                data.selected_tether = false;
                data.selected_lan = false;
                data.selected_tor = false;
                //addToQueue(data);
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectAll3G(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_3g = flag;
                    //addToQueue(data);
                }
                // addToQueue(data);
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }

    }

    private void selectAllWifi(boolean flag) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        int count = adapter.getCount(), item;
        if (adapter != null) {
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    data.selected_wifi = flag;
                    // addToQueue(data);
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MENU:
                    if (mainMenu != null) {
                        mainMenu.performIdentifierAction(R.id.menu_list_item, 0);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_BACK:
                    if (isDirty()) {
                        new MaterialDialog.Builder(this)
                                .title(R.string.confirmation)
                                .cancelable(false)
                                .content(R.string.unsaved_changes_message)
                                .positiveText(R.string.apply)
                                .negativeText(R.string.discard)
                                .onPositive((dialog, which) -> {
                                    applyOrSaveRules();
                                    dialog.dismiss();
                                })
                                .onNegative((dialog, which) -> {
                                    setDirty(false);
                                    Api.applications = null;
                                    finish();
                                    //System.exit(0);
                                    //force reload rules.
                                    MainActivity.super.onKeyDown(keyCode, event);
                                    dialog.dismiss();
                                })
                                .show();
                        return true;

                    } else {
                        setDirty(false);
                        //finish();
                        //System.exit(0);
                    }


            }
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * @param i
     */

    private void selectActionConfirmation(String displayMessage, final int i) {

        new MaterialDialog.Builder(this)
                .title(R.string.confirmation).content(displayMessage)
                .cancelable(true)
                .positiveText(R.string.OK)
                .negativeText(R.string.Cancel)
                .onPositive((dialog, which) -> {
                    switch (i) {
                        case 0:
                            selectRevert();
                            break;
                        case 1:
                            clearAll();
                    }
                    dialog.dismiss();
                })

                .onNegative((dialog, which) -> dialog.dismiss())
                .show();
    }

    private void cloneColumn(String displayMessage) {
        String[] items = new String[]{
                getString(R.string.lan),
                getString(R.string.wifi),
                getString(R.string.data),
                getString(R.string.vpn),
                getString(R.string.roam),
                getString(R.string.tether),
                getString(R.string.tor)};

        new MaterialDialog.Builder(this)
                .title(R.string.confirmation).content(displayMessage)
                .cancelable(true)
                .positiveText(R.string.OK)
                .negativeText(R.string.Cancel)
                .items(items)
                .itemsCallbackSingleChoice(-1, (dialog, view, which, text) -> {
                    switch (which) {
                        default:
                            new MaterialDialog.Builder(this)
                                    .title(R.string.confirmation).content(displayMessage)
                                    .cancelable(true)
                                    .positiveText(R.string.OK)
                                    .negativeText(R.string.Cancel)
                                    .items(items)
                                    .itemsCallbackSingleChoice(-1, (dialog2, view2, which2, text2) -> {
                                        switch (which) {
                                            default:
                                                copyColumns(which, which2);

                                        }
                                        return true;
                                    })
                                    .positiveText(R.string.clone)

                                    .onNegative((dialog2, which2) -> dialog.dismiss())
                                    .show();
                    }
                    return true;
                })
                .positiveText(R.string.from)
                .onNegative((dialog, which) -> dialog.dismiss())
                .show();
    }

    private void copyColumns(int which, int which2) {
        if (this.listview == null) {
            this.listview = this.findViewById(R.id.listview);
        }
        ListAdapter adapter = listview.getAdapter();
        if (adapter != null) {
            int count = adapter.getCount(), item;
            for (item = 0; item < count; item++) {
                PackageInfoData data = (PackageInfoData) adapter.getItem(item);
                if (data.uid != Api.SPECIAL_UID_ANY) {
                    switch (which) {
                        case 0:
                            switch (which2) {
                                case 0:
                                    break;
                                case 1:
                                    data.selected_wifi = data.selected_lan;
                                    break;
                                case 2:
                                    data.selected_3g = data.selected_lan;
                                    break;
                                case 3:
                                    data.selected_vpn = data.selected_lan;
                                    break;
                                case 4:
                                    data.selected_roam = data.selected_lan;
                                    break;
                                case 5:
                                    data.selected_tether = data.selected_lan;
                                    break;
                                case 6:
                                    data.selected_tor = data.selected_lan;
                                    break;
                            }
                            break;
                        case 1:
                            switch (which2) {
                                case 0:
                                    data.selected_lan = data.selected_wifi;
                                    break;
                                case 1:
                                    break;
                                case 2:
                                    data.selected_3g = data.selected_wifi;
                                    break;
                                case 3:
                                    data.selected_vpn = data.selected_wifi;
                                    break;
                                case 4:
                                    data.selected_roam = data.selected_wifi;
                                    break;
                                case 5:
                                    data.selected_tether = data.selected_wifi;
                                    break;
                                case 6:
                                    data.selected_tor = data.selected_wifi;
                                    break;
                            }
                            break;
                        case 2:
                            switch (which2) {
                                case 0:
                                    data.selected_lan = data.selected_3g;
                                    break;
                                case 1:
                                    data.selected_wifi = data.selected_3g;
                                    break;
                                case 2:
                                    break;
                                case 3:
                                    data.selected_vpn = data.selected_3g;
                                    break;
                                case 4:
                                    data.selected_roam = data.selected_3g;
                                    break;
                                case 5:
                                    data.selected_tether = data.selected_3g;
                                    break;
                                case 6:
                                    data.selected_tor = data.selected_3g;
                                    break;
                            }
                            break;
                        case 3:
                            switch (which2) {
                                case 0:
                                    data.selected_lan = data.selected_vpn;
                                    break;
                                case 1:
                                    data.selected_wifi = data.selected_vpn;
                                    break;
                                case 2:
                                    data.selected_3g = data.selected_vpn;
                                    break;
                                case 3:
                                    break;
                                case 4:
                                    data.selected_roam = data.selected_vpn;
                                    break;
                                case 5:
                                    data.selected_tether = data.selected_vpn;
                                    break;
                                case 6:
                                    data.selected_tor = data.selected_vpn;
                                    break;
                            }
                            break;
                        case 4:
                            switch (which2) {
                                case 0:
                                    data.selected_lan = data.selected_roam;
                                    break;
                                case 1:
                                    data.selected_wifi = data.selected_roam;
                                    break;
                                case 2:
                                    data.selected_3g = data.selected_roam;
                                    break;
                                case 3:
                                    data.selected_vpn = data.selected_roam;
                                    break;
                                case 4:
                                    break;
                                case 5:
                                    data.selected_tether = data.selected_roam;
                                    break;
                                case 6:
                                    data.selected_tor = data.selected_roam;
                                    break;
                            }
                            break;
                        case 5:
                            switch (which2) {
                                case 0:
                                    data.selected_lan = data.selected_tether;
                                    break;
                                case 1:
                                    data.selected_wifi = data.selected_tether;
                                    break;
                                case 2:
                                    data.selected_3g = data.selected_tether;
                                    break;
                                case 3:
                                    data.selected_vpn = data.selected_tether;
                                    break;
                                case 4:
                                    data.selected_roam = data.selected_tether;
                                    break;
                                case 5:
                                    break;
                                case 6:
                                    data.selected_tor = data.selected_tether;
                                    break;
                            }
                            break;
                        case 6:
                            switch (which2) {
                                case 0:
                                    data.selected_lan = data.selected_tor;
                                    break;
                                case 1:
                                    data.selected_wifi = data.selected_tor;
                                    break;
                                case 2:
                                    data.selected_3g = data.selected_tor;
                                    break;
                                case 3:
                                    data.selected_vpn = data.selected_tor;
                                    break;
                                case 4:
                                    data.selected_roam = data.selected_tor;
                                    break;
                                case 5:
                                    data.selected_tether = data.selected_tor;
                                    break;
                                case 6:
                                    break;
                            }
                            break;
                    }
                }
                setDirty(true);
            }
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
    }

    private void selectActionConfirmation(final int i) {

        new MaterialDialog.Builder(this)
                .title(R.string.select_action)
                .cancelable(true)
                .items(new String[]{
                        getString(R.string.check_all),
                        getString(R.string.invert_all),
                        getString(R.string.uncheck_all)})
                .itemsCallback((dialog, view, which, text) -> {
                    if (which == 0) {
                        if (i == R.id.img_wifi) {
                            dialog.setTitle(text + getString(R.string.wifi));
                            selectAllWifi(true);
                        } else if (i == R.id.img_3g) {
                            dialog.setTitle(text + getString(R.string.data));
                            selectAll3G(true);
                        } else if (i == R.id.img_roam) {
                            dialog.setTitle(text + getString(R.string.roam));
                            selectAllRoam(true);
                        } else if (i == R.id.img_vpn) {
                            dialog.setTitle(text + getString(R.string.vpn));
                            selectAllVPN(true);
                        } else if (i == R.id.img_tether) {
                            dialog.setTitle(text + getString(R.string.tether));
                            selectAlltether(true);
                        } else if (i == R.id.img_lan) {
                            dialog.setTitle(text + getString(R.string.lan));
                            selectAllLAN(true);
                        } else if (i == R.id.img_tor) {
                            dialog.setTitle(text + getString(R.string.tor));
                            selectAllTor(true);
                        }
                    } else if (which == 1) {
                        if (i == R.id.img_wifi) {
                            dialog.setTitle(text + getString(R.string.wifi));
                        } else if (i == R.id.img_3g) {
                            dialog.setTitle(text + getString(R.string.data));
                        } else if (i == R.id.img_roam) {
                            dialog.setTitle(text + getString(R.string.roam));
                        } else if (i == R.id.img_vpn) {
                            dialog.setTitle(text + getString(R.string.vpn));
                        } else if (i == R.id.img_tether) {
                            dialog.setTitle(text + getString(R.string.tether));
                        } else if (i == R.id.img_lan) {
                            dialog.setTitle(text + getString(R.string.lan));
                        } else if (i == R.id.img_tor) {
                            dialog.setTitle(text + getString(R.string.tor));
                        }
                        selectRevert(i);
                        dirty = true;
                    } else if (which == 2) {

                        if (i == R.id.img_wifi) {
                            dialog.setTitle(text + getString(R.string.wifi));
                            selectAllWifi(false);
                        } else if (i == R.id.img_3g) {
                            dialog.setTitle(text + getString(R.string.data));
                            selectAll3G(false);
                        } else if (i == R.id.img_roam) {
                            dialog.setTitle(text + getString(R.string.roam));
                            selectAllRoam(false);
                        } else if (i == R.id.img_vpn) {
                            dialog.setTitle(text + getString(R.string.vpn));
                            selectAllVPN(false);
                        } else if (i == R.id.img_tether) {
                            dialog.setTitle(text + getString(R.string.tether));
                            selectAlltether(false);
                        } else if (i == R.id.img_lan) {
                            dialog.setTitle(text + getString(R.string.lan));
                            selectAllLAN(false);
                        } else if (i == R.id.img_tor) {
                            dialog.setTitle(text + getString(R.string.tor));
                            selectAllTor(false);
                        }
                    }
                }).show();
    }

    protected boolean isSuPackage(PackageManager pm, String suPackage) {
        try {
            PackageInfo info = pm.getPackageInfo(suPackage, 0);
            return info.applicationInfo != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getAppList != null) {
            getAppList.cancel(true);
        }
        if (runApply != null) {
            runApply.cancel(true);
        }

        if (purgeTask != null) {
            purgeTask.cancel(true);
        }

        if (toastReceiver != null) {
            unregisterReceiver(toastReceiver);
        }
        if (themeRefreshReceiver != null) {
            unregisterReceiver(themeRefreshReceiver);
        }
        if (uiRefreshReceiver != null) {
            unregisterReceiver(uiRefreshReceiver);
        }
        
        // Clean up shell instances to prevent interruption crashes
        try {
            // Force close any existing shell instances
            com.topjohnwu.superuser.Shell shell = com.topjohnwu.superuser.Shell.getCachedShell();
            if (shell != null && !shell.isAlive()) {
                shell.close();
            }
        } catch (Exception e) {
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Api.updateBaseContextLocale(base));
    }

    private class PurgeTask extends AsyncTask<Void, Void, Boolean> {

        private MaterialDialog progress;
        private final WeakReference<MainActivity> activityReference;

        PurgeTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            progress = new MaterialDialog.Builder(activityReference.get())
                    .title(R.string.working)
                    .cancelable(true)
                    .content(R.string.purging_rules)
                    .progress(true, 0)
                    .show();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            if (G.hasRoot()) {
                //store the root value
                G.hasRoot(true);
                Api.purgeIptables(ctx, true, new RootCommand()
                        .setSuccessToast(R.string.rules_deleted)
                        .setFailureToast(R.string.error_purge)
                        .setReopenShell(true)
                        .setCallback(new RootCommand.Callback() {
                            public void cbFunc(RootCommand state) {
                                // error exit -> assume the rules are still enabled
                                // we shouldn't wind up in this situation, but if we do, the user's
                                // best bet is to click Apply then toggle Enabled again
                                try {
                                    progress.dismiss();
                                } catch (Exception ex) {
                                }
                                boolean nowEnabled = state.exitCode != 0;
                                runOnUiThread(() -> {
                                    Api.setEnabled(ctx, nowEnabled, true);
                                    menuSetApplyOrSave(mainMenu, nowEnabled);
                                    refreshHeader();
                                });

                            }
                        }));
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean aVoid) {
            super.onPostExecute(aVoid);
            if (!aVoid) {
                Toast.makeText(ctx, ctx.getString(R.string.error_su_toast), Toast.LENGTH_SHORT).show();
                try {
                    progress.dismiss();
                    progress = null;
                } catch (Exception ex) {
                } finally {
                    assert progress != null;
                    progress.dismiss();
                    progress = null;
                }
            }
        }
    }

    public class GetAppList extends AsyncTask<Void, Integer, Void> {

        private final WeakReference<MainActivity> activityReference;

        GetAppList(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            // Don't enumerate packages here - getInstalledApplications() on the UI thread
            // blocks the main thread before the dialog even shows. Start with a placeholder
            // max and update it from the background scan via doMaxProgress().
            plsWait = new MaterialDialog.Builder(activityReference.get()).cancelable(false).
                    title(getString(R.string.reading_apps)).progress(false, 1, true).show();
            doProgress(0);
        }

        public void doProgress(int value) {
            publishProgress(value);
        }

        public void doMaxProgress(int value) {
            publishProgress(Integer.MIN_VALUE, Math.max(1, value));
        }

        @Override
        protected Void doInBackground(Void... params) {
            Api.getApps(activityReference.get(), this);
            if (isCancelled())
                return null;
            //publishProgress(-1);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            selectFilterGroup();
            doProgress(-1);
            try {
                try {
                    if (plsWait != null && plsWait.isShowing()) {
                        plsWait.dismiss();
                    }
                } catch (final IllegalArgumentException e) {
                    // Handle or log or ignore
                } catch (final Exception e) {
                    // Handle or log or ignore
                } finally {
                    plsWait.dismiss();
                    plsWait = null;
                }
                mSwipeLayout.setRefreshing(false);
            } catch (Exception e) {
                // nothing
                if (plsWait != null) {
                    plsWait.dismiss();
                    plsWait = null;
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

            if (progress[0] == Integer.MIN_VALUE && progress.length > 1) {
                if (plsWait != null && plsWait.isShowing()) {
                    plsWait.setMaxProgress(Math.max(1, progress[1]));
                }
            } else if (progress[0] == 0 || progress[0] == -1) {
                //do nothing
            } else {
                if (plsWait != null) {
                    plsWait.incrementProgress(progress[0]);
                }
            }
        }
    }

    private class RunApply extends AsyncTask<Void, Long, Boolean> {
        boolean enabled = Api.isEnabled(getApplicationContext());

        private final WeakReference<MainActivity> activityReference;

        RunApply(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            runProgress = new MaterialDialog.Builder(activityReference.get())
                    .title(R.string.su_check_title)
                    .cancelable(true)
                    .customView(R.layout.apply_view, false)
                    //.content(enabled ? R.string.applying_rules
                    //        : R.string.saving_rules)
                    .negativeText("Dismiss")
                    .show();
            if (G.enableIPv6()) {
                runProgress.findViewById(R.id.apply6layout).setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            //set the progress
            if (G.hasRoot()) {
                Api.setRulesUpToDate(false);
                RootCommand rootCommand = new RootCommand()
                        .setSuccessToast(R.string.rules_applied)
                        .setFailureToast(R.string.error_apply)
                        .setCallback(new RootCommand.Callback() {
                            public void cbFunc(RootCommand state) {
                                final Context appCtx = getApplicationContext();
                                if (state.exitCode != 0) {
                                    // Keep the enabled state and the unsaved changes so the user can retry:
                                    // flipping to "disabled" here left rules loaded while the UI said off.
                                    Api.errorNotification(appCtx);
                                }
                                runOnUiThread(() -> {
                                    try {
                                        if (runProgress != null) {
                                            runProgress.dismiss();
                                        }
                                    } catch (Exception ex) {
                                        Log.w(Api.TAG, "Unable to dismiss apply dialog: " + ex.getMessage());
                                    }
                                    MainActivity activity = activityReference.get();
                                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                                        if (state.exitCode == 0) {
                                            Api.setEnabled(appCtx, enabled, true);
                                        }
                                        return;
                                    }
                                    if (state.exitCode == 0) {
                                        setDirty(false);
                                        menuSetApplyOrSave(activity.mainMenu, enabled);
                                        Api.setEnabled(activity, enabled, true);
                                        if (enabled && G.enableLogService()) {
                                            LogService.ensureRunning(activity);
                                        }
                                    }
                                    refreshHeader();
                                });
                            }
                        });
                Api.applySavedIptablesRules(activityReference.get(), true, rootCommand);
                return true;
            } else {
                runOnUiThread(() -> {
                    setDirty(false);
                    try {
                        runProgress.dismiss();
                    } catch (Exception ex) {
                    }
                });
                return false;
            }

        }

        @Override
        protected void onPostExecute(Boolean aVoid) {
            super.onPostExecute(aVoid);
            if (!aVoid) {
                Toast.makeText(activityReference.get(), getString(R.string.error_su_toast), Toast.LENGTH_SHORT).show();
                disableFirewall();
                refreshHeader();
                try {
                    runProgress.dismiss();
                } catch (Exception ex) {
                }
            }
        }
    }

    private class RootCheck extends AsyncTask<Void, Void, Void> {
        MaterialDialog suDialog = null;
        boolean unsupportedSU = false;
        boolean[] suGranted = {false};
        private Context context = null;
        //private boolean suAvailable = false;

        public RootCheck setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        protected void onPreExecute() {
            suDialog = new MaterialDialog.Builder(context).
                    cancelable(false).title(getString(R.string.su_check_title)).progress(true, 0).content(context.getString(R.string.su_check_message))
                    .show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            //open shell if required
            Shell.getShell().isRoot();
            suGranted[0] = Shell.isAppGrantedRoot();
            unsupportedSU = isSuPackage(getPackageManager(), "com.kingroot.kinguser");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            try {
                if (suDialog != null) {
                    suDialog.dismiss();
                }
            } catch (final Exception e) {
            } finally {
                suDialog = null;
            }
            if (!Api.isNetfilterSupported() && !isFinishing()) {
                new MaterialDialog.Builder(MainActivity.this).cancelable(false)
                        .title(R.string.error_common)
                        .content(R.string.error_netfilter)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dialog.dismiss();
                            }
                        })
                        .onNegative((dialog, which) -> {
                            MainActivity.this.finish();
                            android.os.Process.killProcess(android.os.Process.myPid());
                            dialog.dismiss();
                        })
                        .positiveText(R.string.Continue)
                        .negativeText(R.string.exit)
                        .show();
            }
            /*// more details on https://github.com/ukanth/afwall/issues/501
            if (isSuPackage(getPackageManager(), "com.kingroot.kinguser")) {
                G.kingDetected(true);
            }*/
            if (!suGranted[0] && !unsupportedSU && !isFinishing()) {
                disableFirewall();
                showRootNotFoundMessage();
            } else {
                G.hasRoot(true);
                startRootShell();
                new SecurityUtil(MainActivity.this).passCheck();
                registerNetworkObserver();
                // Ensure FirewallService is started if firewall is enabled
                if (Api.isEnabled(MainActivity.this)) {
                    Api.setEnabled(MainActivity.this, true, false);
                }
            }
        }
    }

    @RequiresApi(28)
    private static class OnUnhandledKeyEventListenerWrapper implements View.OnUnhandledKeyEventListener {
        private final ViewCompat.OnUnhandledKeyEventListenerCompat mCompatListener;

        OnUnhandledKeyEventListenerWrapper(ViewCompat.OnUnhandledKeyEventListenerCompat listener) {
            this.mCompatListener = listener;
        }

        public boolean onUnhandledKeyEvent(View v, KeyEvent event) {
            return this.mCompatListener.onUnhandledKeyEvent(v, event);
        }
    }

}

