/**
 * Broadcast receiver that set iptable rules on system startup.
 * This is necessary because the iptables rules are not persistent.
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
package dev.ukanth.ufirewall.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.ukanth.ufirewall.log.Log;

/**
 * Broadcast receiver that set iptables rules on system startup. This is
 * necessary because the rules are not persistent.
 */
public class BootBroadcast extends BroadcastReceiver {
    public static final String TAG = "AFWall";
    static final String CHANNEL_ID = "afwall_onboot_apply";
    // private Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(final Context context, final Intent intent) {

        Log.i(TAG,"Starting boot received intent");

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG,"Performing ACTION_BOOT_COMPLETED");


        }
    }
}