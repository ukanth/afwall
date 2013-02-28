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

package dev.ukanth.ufirewall.plugin;

import android.content.Intent;
import android.os.Bundle;

/**
 * Helper class to scrub Bundles of invalid extras. This is a workaround for an Android bug:
 * <http://code.google.com/p/android/issues/detail?id=16006>.
 */
public final class BundleScrubber
{

    /**
     * Scrubs Intents for private serializable subclasses in the Intent extras. If the Intent's extras contain
     * a private serializable subclass, the Bundle is cleared. The Bundle will not be set to null. If the
     * Bundle is null, has no extras, or the extras do not contain a private serializable subclass, the Bundle
     * is not mutated.
     * 
     * @param intent {@code Intent} to scrub. This parameter may be mutated if scrubbing is necessary. This
     *            parameter may be null.
     * @return true if the Intent was scrubbed, false if the Intent was not modified.
     */
    public static boolean scrub(final Intent intent)
    {
        if (null == intent)
        {
            return false;
        }

        return scrub(intent.getExtras());
    }

    /**
     * Scrubs Bundles for private serializable subclasses in the extras. If the Bundle's extras contain a
     * private serializable subclass, the Bundle is cleared. If the Bundle is null, has no extras, or the
     * extras do not contain a private serializable subclass, the Bundle is not mutated.
     * 
     * @param bundle {@code Bundle} to scrub. This parameter may be mutated if scrubbing is necessary. This
     *            parameter may be null.
     * @return true if the Bundle was scrubbed, false if the Bundle was not modified.
     */
    public static boolean scrub(final Bundle bundle)
    {
        if (null == bundle)
        {
            return false;
        }

        /*
         * Note: This is a hack to work around a private serializable classloader attack
         */
        try
        {
            // if a private serializable exists, this will throw an exception
            bundle.containsKey(null);
        }
        catch (final Exception e)
        {
            bundle.clear();
            return true;
        }

        return false;
    }
}