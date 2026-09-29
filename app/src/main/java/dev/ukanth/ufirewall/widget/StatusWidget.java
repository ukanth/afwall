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
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;

/**
 * ON/OFF Widget implementation. A tap opens {@link WidgetActionActivity}, which does the
 * confirmation / app lock checks and the toggle.
 */
public class StatusWidget extends AppWidgetProvider {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        super.onReceive(context, intent);
        // STATUS_CHANGED: sent by Api.setEnabled(). TOGGLE_REQUEST: the tap of a widget placed by
        // an older version; this receiver is exported, so a toggle is never done from here (any
        // app could send it). Both just redraw from the real state, which also replaces an old
        // tap action with the new one.
        if (Api.STATUS_CHANGED_MSG.equals(intent.getAction()) || Api.TOGGLE_REQUEST_MSG.equals(intent.getAction())) {
            refresh(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] ints) {
        super.onUpdate(context, appWidgetManager, ints);
        show(context, appWidgetManager, ints, Api.isEnabled(context) ? R.drawable.widget_on : R.drawable.widget_off);
    }

    /**
     * Redraw every status widget from the current state.
     */
    public static void refresh(Context context) {
        show(context, Api.isEnabled(context) ? R.drawable.widget_on : R.drawable.widget_off);
    }

    /**
     * Show "enabling..." / "disabling..." while a toggle runs.
     */
    static void showPending(Context context, boolean willEnable) {
        show(context, willEnable ? R.drawable.widget_enabling : R.drawable.widget_disabling);
    }

    /**
     * Flash success / error, then show the real state. May be called from any thread.
     */
    static void showResult(Context context, boolean success) {
        new Handler(Looper.getMainLooper()).post(() -> {
            show(context, success ? R.drawable.widget_success : R.drawable.widget_error);
            new Handler(Looper.getMainLooper()).postDelayed(() -> refresh(context), success ? 1000 : 2000);
        });
    }

    private static void show(Context context, int iconId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, StatusWidget.class));
        if (widgetIds != null && widgetIds.length > 0) {
            show(context, manager, widgetIds, iconId);
        }
    }

    private static void show(Context context, AppWidgetManager manager, int[] widgetIds, int iconId) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.onoff_widget);
        views.setInt(R.id.widgetCanvas, "setBackgroundResource", iconId);
        final PendingIntent intent = PendingIntent.getActivity(context, 0, WidgetActionActivity.toggleIntent(context),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widgetCanvas, intent);
        manager.updateAppWidget(widgetIds, views);
    }
}
