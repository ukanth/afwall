/**
 * ON/OFF Widget implementation
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2012 Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */

package dev.ukanth.ufirewall;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.Toast;
import dev.ukanth.ufirewall.RootShell.RootCommand;

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
				final int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context,StatusWidget.class));
				showWidget(context, manager, widgetIds, firewallEnabled);
			}
		} else if (Api.TOGGLE_REQUEST_MSG.equals(intent.getAction())) {
			// Broadcast sent to request toggling DroidWall's status
			
			final SharedPreferences prefs2 = context.getSharedPreferences(Api.PREFS_NAME, 0);
			final String oldPwd = prefs2.getString(Api.PREF_PASSWORD, "");
			final String newPwd = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0).getString("LockPassword", "");
			
			final SharedPreferences prefs = context.getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0);
			final boolean enabled = !prefs.getBoolean(Api.PREF_ENABLED, true);
			
			if (!enabled && (oldPwd.length() != 0 || newPwd.length() != 0)) {
				Toast.makeText(context,R.string.widget_disable_fail,Toast.LENGTH_SHORT).show();
				return;
			}

			if (enabled) {
				Api.applySavedIptablesRules(context, true, new RootCommand()
					.setSuccessToast(R.string.toast_enabled)
					.setFailureToast(R.string.toast_error_enabling)
					.setReopenShell(true)
					.setCallback(new RootCommand.Callback() {
						public void cbFunc(RootCommand state) {
							// setEnabled always sends us a STATUS_CHANGED_MSG intent to update the icon
							Api.setEnabled(context, state.exitCode == 0, true);
						}
					}));
			} else {
				Api.purgeIptables(context, true, new RootCommand()
					.setSuccessToast(R.string.toast_disabled)
					.setFailureToast(R.string.toast_error_disabling)
					.setReopenShell(true)
					.setCallback(new RootCommand.Callback() {
						public void cbFunc(RootCommand state) {
							Api.setEnabled(context, state.exitCode != 0, true);
						}
				}));
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
		final RemoteViews views = new RemoteViews(context.getPackageName(),R.layout.onoff_widget);
		final int iconId = enabled ? R.drawable.widget_on: R.drawable.widget_off;
		views.setInt(R.id.widgetCanvas, "setBackgroundResource", iconId);
		final Intent msg = new Intent(Api.TOGGLE_REQUEST_MSG);
		final PendingIntent intent = PendingIntent.getBroadcast(context, -1,msg, PendingIntent.FLAG_UPDATE_CURRENT);
		views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
		manager.updateAppWidget(widgetIds, views);
	}

}
