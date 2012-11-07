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
import android.text.format.DateUtils;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.util.Log;

/**
 * ON/OFF Widget implementation
 */
public class StatusWidget extends AppWidgetProvider {

	@Override
	public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);
        if (Api.STATUS_CHANGED_MSG.equals(intent.getAction())) {
        	/* Broadcast sent when the AFWall+ status has changed */
            final Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey(Api.STATUS_EXTRA)) {
                final boolean firewallEnabled = extras.getBoolean(Api.STATUS_EXTRA);
                final AppWidgetManager manager = AppWidgetManager.getInstance(context);
                final int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class));
                showWidget(context, manager, widgetIds, firewallEnabled);
            }
        } else if (Api.TOGGLE_REQUEST_MSG.equals(intent.getAction())) {
        	/* Broadcast sent to request toggling AFWall+ status */
            final SharedPreferences prefs = context.getSharedPreferences(Api.PREFS_NAME, 0);
            final boolean enabled = !prefs.getBoolean(Api.PREF_ENABLED, true);
    		final String pwd = prefs.getString(Api.PREF_PASSWORD, "");
    		if (!enabled && pwd.length() != 0) {
        		Toast.makeText(context, "Cannot disable firewall - password defined!", Toast.LENGTH_SHORT).show();
        		return;
    		}
        	final Handler toaster = new Handler() {
        		public void handleMessage(Message msg) {
        			if (msg.arg1 != 0) Toast.makeText(context, msg.arg1, Toast.LENGTH_SHORT).show();
        		}
        	};
			/* Start a new thread to change the firewall - this prevents ANR */
			new Thread() {
				@Override
				public void run() {
        			final Message msg = new Message();
		            if (enabled) {
		            	if (Api.applySavedIptablesRules(context, false)) {
		        			msg.arg1 = R.string.toast_enabled;
		        			toaster.sendMessage(msg);
		            	} else {
		        			msg.arg1 = R.string.toast_error_enabling;
		        			toaster.sendMessage(msg);
		            		return;
		            	}
		            } else {
		            	if (Api.purgeIptables(context, false)) {
		        			msg.arg1 = R.string.toast_disabled;
		        			toaster.sendMessage(msg);
		            	} else {
		        			msg.arg1 = R.string.toast_error_disabling;
		        			toaster.sendMessage(msg);
		            		return;
		            	}
		            }
		            Api.setEnabled(context, enabled);
				}
			}.start();
        }
	}
   /* @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] ints) {
        super.onUpdate(context, appWidgetManager, ints);
        final SharedPreferences prefs = context.getSharedPreferences(Api.PREFS_NAME, 0);
        boolean enabled = prefs.getBoolean(Api.PREF_ENABLED, true);
        showWidget(context, appWidgetManager, ints, enabled);
    } */
	@Override
        public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
        {

	    	Log.i(TAG, "onUpdate method called, starting service and setting alarm");

	   	 /* Update the widgets via the service */
	   	 startService(context, this.getClass(), appWidgetManager, StatusWidget.class);

	   	 setAlarm(context);

           	 super.onUpdate(context, appWidgetManager, appWidgetIds);

     	}
     	@Override
	public void onReceive(Context context, Intent intent)
	{
	super.onReceive(context, intent);
		/* Get the current Date and Time */
		Log.i(TAG, "onReceive method called, action = '" + intent.getAction() + "' at " + DateUtils.now());

	if ( (WIDGET_UPDATE.equals(intent.getAction())) ||
		intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE") ||
		// intent.getAction().equals("dev.sec.android.widgetapp.APPWIDGET_RESIZE") ||  not implementet jet
		intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS")
	)
	{
	if (AppWidgetProvider.WIDGET_UPDATE.equals(intent.getAction()))
	{
		Log.d(TAG, "Alarm called: updating");
		/* GenericLogger.i(WIDGET_LOG, TAG, "LargeWidgetProvider: Alarm to refresh widget was called"); */
	}
	else
	{
	Log.d(TAG, "APPWIDGET_UPDATE called: updating");
	}

	AppWidgetManager appWidgetManager = AppWidgetManager
		.getInstance(context);
	ComponentName thisAppWidget = new ComponentName(
		context.getPackageName(),
		this.getClass().getName());
	int[] appWidgetIds = appWidgetManager
		.getAppWidgetIds(thisAppWidget);

	if (appWidgetIds.length > 0)
	{
		onUpdate(context, appWidgetManager, appWidgetIds);
	}
	else
	{
		Log.i(TAG, "No widget found to update");
	}

    private void showWidget(Context context, AppWidgetManager manager, int[] widgetIds, boolean enabled) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.onoff_widget);
        final int iconId = enabled ? R.drawable.widget_on : R.drawable.widget_off;
        views.setImageViewResource(R.id.widgetCanvas, iconId);
        final Intent msg = new Intent(Api.TOGGLE_REQUEST_MSG);
        final PendingIntent intent = PendingIntent.getBroadcast(context, -1, msg, PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
        manager.updateAppWidget(widgetIds, views);
    }
    
}
