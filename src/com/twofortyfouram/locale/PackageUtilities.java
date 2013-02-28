/*
 * Copyright 2012 two forty four a.m. LLC <http://www.twofortyfouram.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.twofortyfouram.locale;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A simple utility class to find a package that is compatible with hosting the Locale Developer Platform.
 */
/*
 * This class is NOT part of the public Locale Developer Platform API
 */
public final class PackageUtilities
{
    /**
     * A hard-coded set of Android packages that support the Locale Developer Platform.
     */
    /*
     * This is NOT a public field and is subject to change in future releases of the Developer Platform. A
     * conscious design decision was made to use hard-coded package names, rather than dynamic discovery of
     * packages that might be compatible with hosting the Locale Developer Platform API. This is for two
     * reasons: to ensure the host is implemented correctly (hosts must pass the extensive Locale Platform
     * Host compatibility test suite) and to prevent malicious applications from crashing plug-ins by
     * providing bad values. As additional apps implement the Locale Developer Platform, their package names
     * will also be added to this list.
     */
    /*
     * Note: this is implemented as a Set<String> rather than a String[], in order to enforce immutability.
     */
    private static final Set<String> COMPATIBLE_PACKAGES = constructPackageSet();

    /**
     * @return a list wrapped in {@link Collections#unmodifiableList(List)} that represents the set of
     *         Locale-compatible packages.
     */
    private static Set<String> constructPackageSet()
    {
        final HashSet<String> packages = new HashSet<String>();

        packages.add(Constants.LOCALE_PACKAGE);

        /*
         * Note: Tasker is not 100% compatible with Locale's plug-in API, but it is close enough that these
         * packages are enabled. Tasker's known incompatibilities are documented on the Tasker website.
         */
        packages.add("net.dinglisch.android.taskerm"); //$NON-NLS-1$
        packages.add("net.dinglisch.android.tasker"); //$NON-NLS-1$
        packages.add("net.dinglisch.android.taskercupcake"); //$NON-NLS-1$

        return Collections.unmodifiableSet(packages);
    }

    /**
     * Obtains the {@code String} package name of a currently-installed package which implements the host
     * component of the Locale Developer Platform.
     * <p>
     * Note: A TOCTOU error exists, due to the fact that the package could be uninstalled at any time.
     * <p>
     * Note: If there are multiple hosts, this method will return one of them. The interface of this method
     * makes no guarantee which host will returned, nor whether that host will be consistently returned.
     * 
     * @param manager an instance of {@code PackageManager}. Cannot be null.
     * @param packageHint hint as to which package should take precedence. This parameter may be null.
     * @return {@code String} package name of a host for the Locale Developer Platform, such as
     *         "com.twofortyfouram.locale". If no such package is found, returns null.
     */
    public static String getCompatiblePackage(final PackageManager manager, final String packageHint)
    {
        /*
         * The interface for this method makes no guarantees as to which host will be returned. However the
         * implementation is more predictable.
         */

        final List<PackageInfo> installedPackages = manager.getInstalledPackages(0);

        if (COMPATIBLE_PACKAGES.contains(packageHint))
        {
            for (final PackageInfo packageInfo : installedPackages)
            {
                final String temp = packageInfo.packageName;
                if (packageHint.equals(temp))
                {
                    return temp;
                }
            }
        }

        for (final String compatiblePackageName : COMPATIBLE_PACKAGES)
        {
            if (compatiblePackageName.equals(packageHint))
            {
                continue;
            }

            for (final PackageInfo packageInfo : installedPackages)
            {
                final String temp = packageInfo.packageName;
                if (compatiblePackageName.equals(temp))
                {
                    return temp;
                }
            }
        }

        return null;
    }
}