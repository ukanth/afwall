/*
 * Copyright (C) 2017  Calvin E. Peake, Jr. <cp@absolutedigital.net>
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
 */

package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import dev.ukanth.ufirewall.broadcast.ConnectivityChangeReceiver;

/**
 * Implements a service to monitor for network connectivity changes
 * by way of a system broadcast receiver.
 *
 * This is necessary as of Android 7.0 (API level 24) as declaring
 * a broadcast receiver in the app manifest is no longer sufficient
 * to receive the CONNECTIVITY_ACTION broadcast.
 *
 * @see https://developer.android.com/reference/android/net/ConnectivityManager.html#CONNECTIVITY_ACTION
 * @author Cal Peake
 */
public class ConnectivityChangeService extends Service {
	private BroadcastReceiver receiver;
	private IntentFilter filter;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		this.receiver = new ConnectivityChangeReceiver();
		this.filter = new IntentFilter();
		this.filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
		this.filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
		this.registerReceiver(this.receiver, this.filter);
	}

	@Override
	public void onDestroy() {
		this.unregisterReceiver(this.receiver);
	}
}
