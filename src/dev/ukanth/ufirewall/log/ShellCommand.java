/**
 * 
 * Shell Command for stream blocked packets information from /proc/kmsg
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

import android.util.Log;

public class ShellCommand {
	Runtime rt;
	String[] command;
	String tag = "";
	Process process;
	BufferedReader stdout;
	public String error;
	public int exitval;

	public ShellCommand(String[] command, String tag) {
		this(command);
		this.tag = tag;
	}

	public ShellCommand(String[] command) {
		this.command = command;
		rt = Runtime.getRuntime();
	}

	public void start(boolean waitForExit) {
		Log.d("AFWALL","ShellCommand: starting [" + tag + "] "+ Arrays.toString(command));

		exitval = -1;
		error = null;

		try {
			process = new ProcessBuilder().command(command).redirectErrorStream(true).start();
			stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
		} catch (Exception e) {
			Log.e("AFWall", "Failure starting shell command [" + tag + "]",
					e);
			error = e.getCause().getMessage();
			return;
		}

		if (waitForExit) {
			waitForExit();
		}
	}

	public void waitForExit() {
		while (checkForExit() == false) {
			if (stdoutAvailable()) {
				Log.d("AFWALL", "ShellCommand waitForExit [" + tag
						+ "] discarding read: " + readStdout());
			} else {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
					Log.d("AFWall", "waitForExit", e);
				}
			}
		}
	}

	public void finish() {
		Log.d("AFWALL","ShellCommand: finishing [" + tag + "] "
				+ Arrays.toString(command));

		try {
			if (stdout != null) {
				stdout.close();
			}
		} catch (Exception e) {
			Log.e("AFWall", "Exception finishing [" + tag + "]", e);
		}

		process.destroy();
		process = null;
	}

	public boolean checkForExit() {
		try {
			exitval = process.exitValue();
			Log.d("AFWALL","ShellCommand exited: [" + tag + "] exit " + exitval);
		} catch (IllegalThreadStateException e) {
			return false;
		}

		finish();
		return true;
	}

	public boolean stdoutAvailable() {
		try {
			/*
			 * if(Log.enabled) { Log.d("stdoutAvailable [" + tag + "]: " +
			 * stdout.ready()); }
			 */
			return stdout.ready();
		} catch (java.io.IOException e) {
			Log.e("AFWall", "stdoutAvailable error", e);
			return false;
		}
	}

	public String readStdoutBlocking() {
		Log.d("AFWALL", "readStdoutBlocking [" + tag + "]");
		String line;

		if (stdout == null) {
			return null;
		}

		try {
			line = stdout.readLine();
		} catch (Exception e) {
			Log.e("AFWall", "readStdoutBlocking error", e);
			return null;
		}

		Log.d("AFWALL", "readStdoutBlocking [" + tag + "] return [" + line + "]");

		if (line == null) {
			return null;
		} else {
			return line + "\n";
		}
	}

	public String readStdout() {
		Log.d("AFWALL", "readStdout [" + tag + "]");

		if (stdout == null) {
			return null;
		}

		try {
			if (stdout.ready()) {
				String line = stdout.readLine();
				Log.d("AFWALL", "read line: [" + line + "]");

				if (line == null) {
					return null;
				} else {
					return line + "\n";
				}
			} else {
				Log.d("AFWALL","readStdout [" + tag + "] no data");
				return "";
			}
		} catch (Exception e) {
			Log.e("AFWall", "readStdout error", e);
			return null;
		}
	}
}