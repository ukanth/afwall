/*
 * Copyright (C) 2012-2015 Jorrit "Chainfire" Jongma
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

import android.os.Build;

import java.util.List;
import java.util.Locale;

/**
 * Utility class to decide between toolbox and toybox calls on M.
 * Note that some calls (such as 'ls') are present in both, this
 * class will favor toybox variants.
 *
 * This may not be what you want, as both syntax and output may
 * differ between the variants.
 *
 * Very specific warning, the 'mount' included with toybox tends
 * to segfault, at least on the first few 6.0 firmwares.
 */
public class Toolbox {
    private static final int TOYBOX_SDK = 23;

    private static final Object synchronizer = new Object();
    private static volatile String toybox = null;

    /**
     * Initialize. Asks toybox which commands it supports. Throws an exception if called from
     * the main thread in debug mode.
     */
    public static void init() {
        // already inited ?
        if (toybox != null) return;

        // toybox is M+
        if (Build.VERSION.SDK_INT < TOYBOX_SDK) {
            toybox = "";
        } else {
            if (Debug.getSanityChecksEnabledEffective() && Debug.onMainThread()) {
                Debug.log(ShellOnMainThreadException.EXCEPTION_TOOLBOX);
                throw new ShellOnMainThreadException(ShellOnMainThreadException.EXCEPTION_TOOLBOX);
            }

            // ask toybox which commands it has, and store the info
            synchronized (synchronizer) {
                toybox = "";

                List<String> output = Shell.SH.run("toybox");
                if (output != null) {
                    toybox = " ";
                    for (String line : output) {
                        toybox = toybox + line.trim() + " ";
                    }
                }
            }
        }
    }

    /**
     * Format a command string, deciding on toolbox or toybox for its execution
     *
     * If init() has not already been called, it is called for you, which may throw an exception
     * if we're in the main thread.
     *
     * Example:
     * Toolbox.command("chmod 0.0 %s", "/some/file/somewhere");
     *
     * Output:
     * &lt; M: "toolbox chmod 0.0 /some/file/somewhere"
     * M+ : "toybox chmod 0.0 /some/file/somewhere"
     *
     * @param format String to format. First word is the applet name.
     * @param args   Arguments passed to String.format
     * @return Formatted String prefixed with either toolbox or toybox
     */
    public static String command(String format, Object... args) {
        if (Build.VERSION.SDK_INT < TOYBOX_SDK) {
            return String.format(Locale.ENGLISH, "toolbox " + format, args);
        }

        if (toybox == null) init();

        format = format.trim();
        String applet;
        int p = format.indexOf(' ');
        if (p >= 0) {
            applet = format.substring(0, p);
        } else {
            applet = format;
        }

        if (toybox.contains(" " + applet + " ")) {
            return String.format(Locale.ENGLISH, "toybox " + format, args);
        } else {
            return String.format(Locale.ENGLISH, "toolbox " + format, args);
        }
    }
}
