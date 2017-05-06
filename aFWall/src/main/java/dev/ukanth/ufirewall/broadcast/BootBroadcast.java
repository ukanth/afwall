/**
 * Broadcast receiver that set iptable rules on system startup.
 * This is necessary because the iptables rules are not persistent.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
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
package dev.ukanth.ufirewall.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import java.util.HashSet;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.InterfaceTracker;
import dev.ukanth.ufirewall.service.LogService;
import dev.ukanth.ufirewall.util.G;

/**
 * Broadcast receiver that set iptables rules on system startup. This is
 * necessary because the rules are not persistent.
 */
public class BootBroadcast extends BroadcastReceiver {
	// private Handler mHandler = new Handler(Looper.getMainLooper());

	@Override
	public void onReceive(final Context context, final Intent intent) {

		InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.BOOT_COMPLETED);

		if (G.enableLogService()) {
			//make sure we cleanup existing uid
			final Intent logIntent = new Intent(context, LogService.class);
			context.startService(logIntent);
			G.storedPid(new HashSet());
		}

		//try applying the rule after few seconds if enabled
		if(G.startupDelay()){
			//make sure we apply rules after 5 sec
			Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					// Apply the changes regards if network is up/not
					InterfaceTracker.applyRulesOnChange(context, InterfaceTracker.BOOT_COMPLETED);
				}
			}, G.getCustomDelay());
		}

		if(G.activeNotification()){
			Api.showNotification(Api.isEnabled(context), context);
		}
	}
}