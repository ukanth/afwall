/**
 * Keep a persistent root shell running in the background
 * <p>
 * Copyright (C) 2013  Kevin Cernekee
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
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Kept only for the manifest entry. IPv6 commands now run on the same queue as everything else
 * (see {@link RootShellService}), so IPv4/IPv6 and purge/apply scripts can never interleave.
 */
public class RootShellService2 extends Service {

    public static final String TAG = "AFWall6";
    public static final int NOTIFICATION_ID = 1;
    public static final int EXIT_NO_ROOT_ACCESS = RootShellEngine.EXIT_NO_ROOT_ACCESS;
    public static final int NO_TOAST = -1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
