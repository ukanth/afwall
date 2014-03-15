/**
 * 
 * Log Service
 * 
 * Copyright (C) 2014  Umakanthan Chandran
 * 
 * Originally copied from NetworkLog (C) 2012 Pragmatic Software (MPL2.0)
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
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.log;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;

public class LogService extends Service {
	private final IBinder mBinder = new Binder();

	private ShellCommand loggerCommand;
	
	private Handler handler;

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	public static String grepPath;

	public void onCreate() {
		grepPath = Api.getGrepPath(getApplicationContext());
		handler = new Handler();
		loggerCommand = new ShellCommand(
				new String[] { "su", "-c",  grepPath + "AFL /proc/kmsg" },
				"LogService");
		loggerCommand.start(false);
		if (loggerCommand.error != null) {
		} else {
			NetworkLogger logger = new NetworkLogger();
			new Thread(logger, "NetworkLogger").start();
		}
	}

	public class NetworkLogger implements Runnable {
		boolean running = false;

		public void stop() {
			running = false;
		}

		public void run() {
			String result;
			running = true;

			while (true) {
				while (running && loggerCommand.checkForExit() == false) {
					if (loggerCommand.stdoutAvailable()) {
						result = loggerCommand.readStdout();
					} else {
						try {
							Thread.sleep(500);
						} catch (Exception e) {
						}
						continue;
					}

					if (running == false) {
						break;
					}

					if (result == null) {
						break;
					}
					final String data = result;
					handler.post(new Runnable() {
					    public void run() {
					        Toast toast = Toast.makeText(LogService.this, LogInfo.parseLogs(data, getApplicationContext()), Toast.LENGTH_LONG);
					        toast.show();
					    }
					 });
					
				}

				if (running != false) {
					try {
						Thread.sleep(10000);
					} catch (Exception e) {
						// ignored
					}
				} else {
					break;
				}
			}
		}
	}

}
