/**
 * Preference Interface.
 * All iptables "communication" is handled by this class.
 * <p>
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
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.preferences;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Random;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.events.LogChangeEvent;
import dev.ukanth.ufirewall.events.RulesEvent;
import dev.ukanth.ufirewall.events.RxEvent;
import dev.ukanth.ufirewall.service.LogService;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;
import io.reactivex.rxjava3.disposables.Disposable;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final boolean ALWAYS_SIMPLE_PREFS = false;
    private Toolbar mToolBar;

    private RxEvent rxEvent;
    private Disposable disposable;




    private void initTheme() {
        switch(G.getSelectedTheme()) {
            case "D":
                setTheme(R.style.AppDarkTheme);
                break;
            case "L":
                setTheme(R.style.AppLightTheme);
                break;
            case "B":
                setTheme(R.style.AppBlackTheme);
                break;
        }
    }
    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // set language
        Api.updateLanguage(getApplicationContext(), G.locale());
        initTheme();

        super.onCreate(savedInstanceState);
        prepareLayout();
        subscribe();

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            Object data = bundle.get("validate");
            if (data != null) {
                String check = (String) data;
                if (check.equals("yes")) {
                    new SecurityUtil(PreferencesActivity.this).passCheck();
                }
            }
        }
    }

    private void subscribe() {
        rxEvent = new RxEvent();
        disposable = rxEvent.subscribe(event -> {
            if (event instanceof RulesEvent) {
                ruleChangeApplyRules((RulesEvent) event);
            } else if (event instanceof LogChangeEvent) {
                logDmesgChangeApplyRules((LogChangeEvent) event);
            }
        });
    }

    private void ruleChangeApplyRules(RulesEvent rulesEvent) {
        final Context context = rulesEvent.ctx;
        Api.applySavedIptablesRules(context, false, new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            Log.i(Api.TAG, "Rules applied successfully during preference change");
                        } else {
                            // error details are already in logcat
                            Log.i(Api.TAG, "Error applying rules during preference change");
                        }
                    }
                }));
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void prepareLayout() {
        ViewGroup root = findViewById(android.R.id.content);
        View content = root.getChildAt(0);
        LinearLayout toolbarContainer = (LinearLayout) View.inflate(this, R.layout.activity_prefs, null);

        root.removeAllViews();
        toolbarContainer.addView(content);
        root.addView(toolbarContainer);

        mToolBar = toolbarContainer.findViewById(R.id.toolbar);
        mToolBar.setTitle(getTitle() + " " + getString(R.string.preferences));
        mToolBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }


    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        theme.applyStyle(resid, true);
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        // Allow super to try and create a view first
        final View result = super.onCreateView(name, context, attrs);
        if (result != null) {
            return result;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // If we're running pre-L, we need to 'inject' our tint aware Views in place of the
            // standard framework versions
            switch (name) {
                case "EditText":
                    return new AppCompatEditText(this, attrs);
                case "Spinner":
                    return new AppCompatSpinner(this, attrs);
                case "CheckBox":
                    return new AppCompatCheckBox(this, attrs);
                case "RadioButton":
                    return new AppCompatRadioButton(this, attrs);
                case "CheckedTextView":
                    return new AppCompatCheckedTextView(this, attrs);
            }
        }

        Api.fixFolderPermissionsAsync(context);

        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return UIPreferenceFragment.class.getName().equals(fragmentName)
                || ThemePreferenceFragment.class.getName().equals(fragmentName)
                || RulesPreferenceFragment.class.getName().equals(fragmentName)
                || LogPreferenceFragment.class.getName().equals(fragmentName)
                || ExpPreferenceFragment.class.getName().equals(fragmentName)
                || CustomBinaryPreferenceFragment.class.getName().equals(
                fragmentName)
                || SecPreferenceFragment.class.getName().equals(fragmentName)
                || MultiProfilePreferenceFragment.class.getName().equals(fragmentName)
                || WidgetPreferenceFragment.class.getName().equals(fragmentName)
                || LanguagePreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preferences_headers, target);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    public void logDmesgChangeApplyRules(LogChangeEvent logChangeEvent) {
        if (logChangeEvent != null) {
            final Context context = logChangeEvent.ctx;
            final Intent logIntent = new Intent(context, LogService.class);
            if (G.enableLogService()) {
                //restart service
                context.stopService(logIntent);
                Api.cleanupUid();
                context.startService(logIntent);
            } else {
                //log service disabled
                context.stopService(logIntent);
                Api.cleanupUid();
            }
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Context ctx = getApplicationContext();
        boolean isRefreshRequired = false;

        if (key.equals("showUid") || key.equals("disableIcons") || key.equals("enableVPN")
                || key.equals("enableTether")
                || key.equals("enableLAN") || key.equals("enableRoam")
                || key.equals("locale") || key.equals("showFilter")) {
            G.reloadProfile();
            isRefreshRequired = true;
        }

        if (key.equals("ip_path") || key.equals("dns_value")) {
            rxEvent.publish(new RulesEvent("", ctx));
        }

        if (key.equals("logDmesg")) {
            rxEvent.publish(new LogChangeEvent("", ctx));
        }

        if (key.equals("notification_priority")) {
            NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
            Api.updateNotification(Api.isEnabled(ctx), ctx);
        }

        if(key.equals("activeNotification")) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            if(!enabled) {
                NotificationManager notificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancelAll();
            } else {
                Api.updateNotification(Api.isEnabled(ctx), ctx);
            }
        }

        if(key.equals("logTarget")) {
            Api.updateLogRules(ctx, new RootCommand()
                    .setReopenShell(true)
                    .setSuccessToast(R.string.log_target_success)
                    .setFailureToast(R.string.log_target_fail));
            Intent intent = new Intent(ctx, LogService.class);
            ctx.stopService(intent);
            Api.cleanupUid();
            ctx.startService(intent);
        }
        if (key.equals("enableLogService")) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            if (enabled) {
                //Api.setLogTarget(ctx, true);
                Intent intent = new Intent(ctx, LogService.class);
                ctx.stopService(intent);
                Api.cleanupUid();
                ctx.startService(intent);
            } else {
                //Api.setLogTarget(ctx, false);
                Intent intent = new Intent(ctx, LogService.class);
                ctx.stopService(intent);
                Api.cleanupUid();
            }
        }
        if (key.equals("enableMultiProfile")) {
            G.reloadProfile();
        }
        if (key.equals("theme")) {
            initTheme();
            recreate();
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("dev.ukanth.ufirewall.theme.REFRESH");
            ctx.sendBroadcast(broadcastIntent);
        }

        if (isRefreshRequired) {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("dev.ukanth.ufirewall.ui.CHECKREFRESH");
            ctx.sendBroadcast(broadcastIntent);
        }
    }


    @Override
    public void onDestroy() {
        if (rxEvent != null && disposable != null) {
            disposable.dispose();
        }
        super.onDestroy();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Api.updateBaseContextLocale(base));
    }


}
