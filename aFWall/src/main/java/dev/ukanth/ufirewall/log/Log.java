/**
 * Circular log buffer.
 * This provides timestamped logcat output for the "Rules" page, to aid
 * in diagnosing failures.
 * 
 * Copyright (C) 2013 Kevin Cernekee
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
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class Log {

	private static final int MAX_ENTRIES = 512;

	public static final int LOG_DEBUG = 0;
	public static final int LOG_VERBOSE = 1;
	public static final int LOG_INFO = 2;
	public static final int LOG_WARNING = 3;
	public static final int LOG_ERROR = 4;
	public static final int LOG_WTF = 5;

	public static class LogEntry {
		Date timestamp;
		int level;
		String msg = "";
	}

	private static LinkedList<LogEntry> circ = new LinkedList<LogEntry>();

	private static synchronized void circLog(int level, String msg) {
		LogEntry e = new LogEntry();
		e.timestamp = new Date();
		e.level = level;
		e.msg = msg;

		if (circ.size() >= MAX_ENTRIES) {
			circ.removeFirst();
		}
		circ.addLast(e);
	}

	public static synchronized String getLog() {
		StringBuilder ret = new StringBuilder();

		for (int i = 0; i < circ.size(); i++) {
			LogEntry e = circ.get(i);
			String timestamp = new SimpleDateFormat("HH:mm:ss").format(e.timestamp);
			ret.append(timestamp + " " + e.msg + "\n");
		}

		return ret.toString();
	}

	public static int d(String tag, String msg) {
		circLog(LOG_DEBUG, msg);
		return android.util.Log.d(tag, msg);
	}

	public static int d(String tag, String msg, Exception e) {
		circLog(LOG_DEBUG, msg);
		return android.util.Log.d(tag, msg, e);
	}

	public static int v(String tag, String msg) {
		circLog(LOG_VERBOSE, msg);
		return android.util.Log.v(tag, msg);
	}

	public static int v(String tag, String msg, Exception e) {
		circLog(LOG_VERBOSE, msg);
		return android.util.Log.v(tag, msg, e);
	}

	public static int i(String tag, String msg) {
		circLog(LOG_INFO, msg);
		return android.util.Log.i(tag, msg);
	}

	public static int i(String tag, String msg, Exception e) {
		circLog(LOG_INFO, msg);
		return android.util.Log.i(tag, msg, e);
	}

	public static int w(String tag, String msg) {
		circLog(LOG_WARNING, msg);
		return android.util.Log.w(tag, msg);
	}

	public static int w(String tag, String msg, Exception e) {
		circLog(LOG_WARNING, msg);
		return android.util.Log.w(tag, msg, e);
	}

	public static int e(String tag, String msg) {
		circLog(LOG_ERROR, msg);
		return android.util.Log.e(tag, msg);
	}

	public static int e(String tag, String msg, Exception e) {
		circLog(LOG_ERROR, msg);
		return android.util.Log.e(tag, msg, e);
	}

	public static int wtf(String tag, String msg) {
		circLog(LOG_WTF, msg);
		return android.util.Log.wtf(tag, msg);
	}

	public static int wtf(String tag, String msg, Exception e) {
		circLog(LOG_WTF, msg);
		return android.util.Log.wtf(tag, msg, e);
	}
}
