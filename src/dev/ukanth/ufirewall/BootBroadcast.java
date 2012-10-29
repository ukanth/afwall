/**
 * Broadcast receiver that set iptable rules on system startup.
 * This is necessary because the iptables rules are not persistent.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
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
 * @author Rodrigo Zechin Rosauro
 * @version 1.0
 */
package dev.ukanth.ufirewall;

import dev.ukanth.ufirewall.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

/**
 * Broadcast receiver that set iptables rules on system startup.
 * This is necessary because the rules are not persistent.
 */
public class BootBroadcast extends BroadcastReceiver {

	@Override
	public void onReceive(final Context context, final Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			if (Api.isEnabled(context)) {
	        	final Handler toaster = new Handler() {
	        		public void handleMessage(Message msg) {
	        			if (msg.arg1 != 0) Toast.makeText(context, msg.arg1, Toast.LENGTH_SHORT).show();
	        		}
	        	};
				// Start a new thread to enable the firewall - this prevents ANR
				new Thread() {
					@Override
					public void run() {
						if (!Api.applySavedIptablesRules(context, false)) {
							// Error enabling firewall on boot
		        			final Message msg = new Message();
		        			msg.arg1 = R.string.toast_error_enabling;
		        			toaster.sendMessage(msg);
							Api.setEnabled(context, false);
						}
					}
				}.start();
			}
		}
	}

}
