/*
 * Copyright (C) 2012-2013 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.chainfire.libsuperuser;

import android.util.Log;
import dev.ukanth.ufirewall.BuildConfig;

/**
 * Utility class that intentionally does nothing when not in debug mode
 */
public class Debug {

	private static String TAG = "libsuperuser";

	private static boolean disableSanityChecks = false;
	private static boolean disableGeneralLogging = false;
	private static boolean disableCommandLogging = false;
	private static boolean disableOutputLogging = false;

	public static void setDisableSanityChecks(boolean disableSanityChecks) {
		Debug.disableSanityChecks = disableSanityChecks;
	}

	public static void setDisableGeneralLogging(boolean disableGeneralLogging) {
		Debug.disableGeneralLogging = disableGeneralLogging;
	}

	public static void setDisableCommandLogging(boolean disableCommandLogging) {
		Debug.disableCommandLogging = disableCommandLogging;
	}

	public static void setDisableOutputLogging(boolean disableOutputLogging) {
		Debug.disableOutputLogging = disableOutputLogging;
	}

	private static void logCommon(String message) {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "[libsuperuser]" + (!message.startsWith("[") && !message.startsWith(" ") ? " " : "") + message);
		}
	}

	/**
	 * <p>Log a "general" message</p>
	 * 
	 * <p>These messages are infrequent and mostly occur at startup/shutdown or on error</p>
	 * 
	 * @param message The message to log
	 */
	public static void log(String message) {
		if (!disableGeneralLogging) logCommon(message);
	}

	/**
	 * <p>Log a "per-command" message</p>
	 * 
	 * <p>This could produce a lot of output if the client runs many commands in the session</p>
	 * 
	 * @param message The message to log
	 */
	public static void logCommand(String message) {
		if (!disableCommandLogging) logCommon(message);
	}

	/**
	 * <p>Log a line of stdout/stderr output</p>
	 * 
	 * <p>This could produce a lot of output if the shell commands are noisy</p>
	 * 
	 * @param message The message to log
	 */
	public static void logOutput(String message) {
		if (!disableOutputLogging) logCommon(message);
	}

	/**
	 * See if the builtin sanity checks (e.g. "don't run SU from the main thread") are enabled
	 * 
	 * @return true if enabled
	 */
	public static boolean sanityChecksEnabled() {
		return !disableSanityChecks && BuildConfig.DEBUG;
	}
}
