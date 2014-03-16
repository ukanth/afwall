/*
 *   Copyright 2012 Hai Bison
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.haibison.android.lockpattern.util;

/**
 * System utilities.
 * 
 * @author Hai Bison
 * 
 */
public class Sys {

    /**
     * The library name.
     */
    public static final String LIB_NAME = "android-lockpattern";

    /**
     * The library version code.
     */
    public static final int LIB_VERSION_CODE = 43;

    /**
     * The library version name.
     */
    public static final String LIB_VERSION_NAME = "3.1";

    /**
     * This unique ID is used for some stuffs such as preferences' file name.
     * 
     * @since v2.6 beta
     */
    public static final String UID = "a6eedbe5-1cf9-4684-8134-ad4ec9f6a131";

    /**
     * This is singleton class.
     */
    private Sys() {
    }// Sys

}
