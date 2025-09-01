/**
 * ON/OFF Widget implementation
 * <p>
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2012 Umakanthan Chandran
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

package dev.ukanth.ufirewall.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.TransitionDrawable;

/**
 * ON/OFF Widget implementation
 */
public class StatusWidget extends AppWidgetProvider {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);
        if (Api.STATUS_CHANGED_MSG.equals(intent.getAction())) {
            // Broadcast sent when the DroidWall status has changed
            final Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey(Api.STATUS_EXTRA)) {
                final boolean firewallEnabled = extras.getBoolean(Api.STATUS_EXTRA);
                final AppWidgetManager manager = AppWidgetManager.getInstance(context);
                final int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class));
                showWidget(context, manager, widgetIds, firewallEnabled);
            }
        } else if (Api.TOGGLE_REQUEST_MSG.equals(intent.getAction())) {
            // Broadcast sent to request toggling DroidWall's status

			/*final String oldPwd = G.profile_pwd();
			final String newPwd = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0).getString("LockPassword", "");
			*/
            final SharedPreferences prefs = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0);
            final boolean enabled = !prefs.getBoolean(Api.PREF_ENABLED, true);
            final AppWidgetManager manager = AppWidgetManager.getInstance(context);
            final int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class));

            // Show immediate pending state
            showPendingState(context, manager, widgetIds, enabled);

            Log.d(Api.TAG, "Protection Level: " + G.protectionLevel());
            if (!G.protectionLevel().equals("p0") || G.enableDeviceCheck()) {

                //Toast.makeText(context, R.string.widget_disable_fail, Toast.LENGTH_SHORT).show();
                //return;
            } else {
                if (enabled) {
                    Api.applySavedIptablesRules(context, true, new RootCommand()
                            .setSuccessToast(R.string.toast_enabled)
                            .setFailureToast(R.string.toast_error_enabling)
                            .setReopenShell(true)
                            .setCallback(new RootCommand.Callback() {
                                public void cbFunc(RootCommand state) {
                                    boolean status = (state.exitCode == 0);
                                    if (state.exitCode != 0) {
                                        // Show error state on failure
                                        showErrorState(context, manager, widgetIds);
                                    } else {
                                        // Show success state briefly before final state
                                        showSuccessState(context, manager, widgetIds, status);
                                    }
                                    // setEnabled always sends us a STATUS_CHANGED_MSG intent to update the icon
                                    Api.setEnabled(context, status, true);
                                }
                            }));
                } else {
                    Api.purgeIptables(context, true, new RootCommand()
                            .setSuccessToast(R.string.toast_disabled)
                            .setFailureToast(R.string.toast_error_disabling)
                            .setCallback(new RootCommand.Callback() {
                                public void cbFunc(RootCommand state) {
                                    boolean status = (state.exitCode != 0);
                                    if (state.exitCode != 0 && !status) {
                                        // Show error state on failure (when status doesn't match expected)
                                        showErrorState(context, manager, widgetIds);
                                    } else if (state.exitCode == 0) {
                                        // Show success state briefly before final state
                                        showSuccessState(context, manager, widgetIds, status);
                                    }
                                    Api.setEnabled(context, status,  true);
                                }
                            }));
                }
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] ints) {
        super.onUpdate(context, appWidgetManager, ints);
        final SharedPreferences prefs = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0);
        boolean enabled = prefs.getBoolean(Api.PREF_ENABLED, true);
        showWidget(context, appWidgetManager, ints, enabled);
    }

    private void showWidget(Context context, AppWidgetManager manager,
                            int[] widgetIds, boolean enabled) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.onoff_widget);
        final int iconId = enabled ? R.drawable.widget_on : R.drawable.widget_off;
        views.setInt(R.id.widgetCanvas, "setBackgroundResource", iconId);
        
        // Note: Animation support removed for compatibility
        
        final Intent msg = new Intent(context, StatusWidget.class);
        msg.setAction(Api.TOGGLE_REQUEST_MSG);
        final PendingIntent intent = PendingIntent.getBroadcast(context, -1, msg, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
        manager.updateAppWidget(widgetIds, views);
    }

    private void showPendingState(Context context, AppWidgetManager manager, int[] widgetIds, boolean willEnable) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.onoff_widget);
        try {
            final int iconId = willEnable ? R.drawable.widget_enabling : R.drawable.widget_disabling;
            views.setInt(R.id.widgetCanvas, "setBackgroundResource", iconId);
        } catch (Exception e) {
            // Fallback to original icons if enhanced resources fail
            final int iconId = willEnable ? R.drawable.widget_on : R.drawable.widget_off;
            views.setInt(R.id.widgetCanvas, "setBackgroundResource", iconId);
        }
        final Intent msg = new Intent(context, StatusWidget.class);
        msg.setAction(Api.TOGGLE_REQUEST_MSG);
        final PendingIntent intent = PendingIntent.getBroadcast(context, -1, msg, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
        manager.updateAppWidget(widgetIds, views);
    }

    private void showErrorState(Context context, AppWidgetManager manager, int[] widgetIds) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.onoff_widget);
        try {
            views.setInt(R.id.widgetCanvas, "setBackgroundResource", R.drawable.widget_error);
        } catch (Exception e) {
            // Fallback to off icon if error resource fails
            views.setInt(R.id.widgetCanvas, "setBackgroundResource", R.drawable.widget_off);
        }
        final Intent msg = new Intent(context, StatusWidget.class);
        msg.setAction(Api.TOGGLE_REQUEST_MSG);
        final PendingIntent intent = PendingIntent.getBroadcast(context, -1, msg, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
        manager.updateAppWidget(widgetIds, views);
        
        // Auto-revert to normal state after 2 seconds
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            final SharedPreferences prefs = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0);
            boolean currentEnabled = prefs.getBoolean(Api.PREF_ENABLED, true);
            showWidget(context, manager, widgetIds, currentEnabled);
        }, 2000);
    }

    private void showSuccessState(Context context, AppWidgetManager manager, int[] widgetIds, boolean finalState) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.onoff_widget);
        try {
            views.setInt(R.id.widgetCanvas, "setBackgroundResource", R.drawable.widget_success);
        } catch (Exception e) {
            // Fallback to final state icon if success resource fails
            final int iconId = finalState ? R.drawable.widget_on : R.drawable.widget_off;
            views.setInt(R.id.widgetCanvas, "setBackgroundResource", iconId);
        }
        final Intent msg = new Intent(context, StatusWidget.class);
        msg.setAction(Api.TOGGLE_REQUEST_MSG);
        final PendingIntent intent = PendingIntent.getBroadcast(context, -1, msg, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
        manager.updateAppWidget(widgetIds, views);
        
        // Auto-revert to final state after 1 second
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            showWidget(context, manager, widgetIds, finalState);
        }, 1000);
    }

}
