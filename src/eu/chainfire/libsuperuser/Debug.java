/*
 * Copyright (C) 2012 Jorrit "Chainfire" Jongma
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
	/**
	 * Log a message if we are in debug mode
	 * @param message The message to log
	 */
	public static void log(String message) {
		if (BuildConfig.DEBUG) {
			Log.d("libsuperuser", "[libsuperuser]" + (!message.startsWith("[") && !message.startsWith(" ") ? " " : "") + message);
		}
	}
}
