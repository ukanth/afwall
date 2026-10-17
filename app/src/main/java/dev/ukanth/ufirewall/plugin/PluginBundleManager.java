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

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

/**
 * Class for managing the {@link com.twofortyfouram.locale.Intent#EXTRA_BUNDLE} for this plug-in.
 */
public final class PluginBundleManager
{
    /**
     * Type: {@code String}.
     * <p>
     * String message to display in a Toast message.
     */
    public static final String BUNDLE_EXTRA_STRING_MESSAGE = "dev.ukanth.ufirewall.plugin.APPLY_PROFILE"; //$NON-NLS-1$

    /**
     * Type: {@code int}
     * <p>
     * versionCode of the plug-in that saved the Bundle.
     */
    /*
     * This extra is not strictly required, however it makes backward and forward compatibility significantly
     * easier. For example, suppose a bug is found in how some version of the plug-in stored its Bundle. By
     * having the version, the plug-in can better detect when such bugs occur.
     */
    public static final String BUNDLE_EXTRA_INT_VERSION_CODE = "dev.ukanth.ufirewall.plugin.extra.INT_VERSION_CODE"; //$NON-NLS-1$

    /**
     * Type: {@code String}, optional.
     * <p>
     * Identifier of the profile to switch to, so the action keeps working after the profile is
     * renamed. Actions saved by older versions only have the name in the message.
     */
    public static final String BUNDLE_EXTRA_STRING_PROFILE_ID = "dev.ukanth.ufirewall.plugin.extra.PROFILE_ID"; //$NON-NLS-1$

    // Signed actions (5.1.0+); see TaskerAction for the fields. Strings only, so every host can
    // store them.
    static final String EXTRA_ACTION = "dev.ukanth.ufirewall.plugin.extra.ACTION";
    static final String EXTRA_PACKAGE = "dev.ukanth.ufirewall.plugin.extra.PACKAGE";
    /**
     * Copy of {@link #EXTRA_PACKAGE} that Tasker replaces variables in; the signed original stays.
     */
    static final String EXTRA_PACKAGE_VALUE = "dev.ukanth.ufirewall.plugin.extra.PACKAGE_VALUE";
    static final String EXTRA_NETWORK = "dev.ukanth.ufirewall.plugin.extra.NETWORK";
    static final String EXTRA_PROFILE_NAME = "dev.ukanth.ufirewall.plugin.extra.PROFILE_NAME";
    static final String EXTRA_ID = "dev.ukanth.ufirewall.plugin.extra.ID";
    static final String EXTRA_CREATED = "dev.ukanth.ufirewall.plugin.extra.CREATED";
    static final String EXTRA_CALLER = "dev.ukanth.ufirewall.plugin.extra.CALLER";
    static final String EXTRA_FORMAT = "dev.ukanth.ufirewall.plugin.extra.FORMAT";
    static final String EXTRA_SIGNATURE = "dev.ukanth.ufirewall.plugin.extra.SIGNATURE";

    // Tasker's plug-in extensions (also understood by some other hosts)
    static final String TASKER_VARIABLE_REPLACE_KEYS = "net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS";
    static final String TASKER_REQUESTED_TIMEOUT = "net.dinglisch.android.tasker.extras.REQUESTED_TIMEOUT";
    static final String TASKER_RELEVANT_VARIABLES = "net.dinglisch.android.tasker.RELEVANT_VARIABLES";
    static final String TASKER_COMPLETION_INTENT = "net.dinglisch.android.tasker.extras.COMPLETION_INTENT";
    static final String TASKER_RESULT_CODE = "net.dinglisch.android.tasker.extras.RESULT_CODE";
    static final String TASKER_VARIABLES = "net.dinglisch.android.tasker.extras.VARIABLES";

    /**
     * Method to verify the content of the bundle are correct.
     * <p>
     * This method will not mutate {@code bundle}.
     * 
     * @param bundle bundle to verify. May be null, which will always return false.
     * @return true if the Bundle is valid, false if the bundle is invalid.
     */
    public static boolean isBundleValid(final Bundle bundle)
    {
        if (null == bundle)
        {
            return false;
        }
        if (isSigned(bundle)) {
            return true; // checked in full by readSigned() and the signature
        }

        /*
         * Make sure the expected extras exist
         */
        if (!bundle.containsKey(BUNDLE_EXTRA_STRING_MESSAGE))
        {
            return false;
        }
        /*
         * Make sure the extra is a numeric action/profile id. Locale/Tasker callers can send
         * arbitrary exported extras, so reject malformed values before parseInt() call sites.
         */
        String message = bundle.getString(BUNDLE_EXTRA_STRING_MESSAGE);
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String index = message;
        if (message.contains("::")) {
            index = message.split("::", 2)[0];
        }
        return !TextUtils.isEmpty(index) && TextUtils.isDigitsOnly(index);
    }

    /**
     * @param context Application context.
     * @param message The toast message to be displayed by the plug-in. Cannot be null.
     * @return A plug-in bundle.
     */
    public static Bundle generateBundle(final Context context, final String message)
    {
        final Bundle result = new Bundle();
        result.putString(BUNDLE_EXTRA_STRING_MESSAGE, message);
        return result;
    }

    /**
     * @param profileId identifier of the profile the action switches to; may be null
     */
    public static Bundle generateBundle(final Context context, final String message, final String profileId) {
        final Bundle result = generateBundle(context, message);
        if (profileId != null) {
            result.putString(BUNDLE_EXTRA_STRING_PROFILE_ID, profileId);
        }
        return result;
    }

    /**
     * @return true for actions saved by 5.1.0+ (they may still fail the signature check)
     */
    static boolean isSigned(final Bundle bundle) {
        return bundle != null && bundle.getString(EXTRA_ACTION) != null && bundle.getString(EXTRA_SIGNATURE) != null;
    }

    /**
     * The bundle for a saved action; Tasker variables are only allowed in the app of allow / block.
     */
    static Bundle signedBundle(final Context context, final TaskerAction action, final String signature) {
        final Bundle result = new Bundle();
        result.putString(EXTRA_FORMAT, TaskerAction.FORMAT);
        result.putString(EXTRA_ACTION, action.action);
        putIfSet(result, EXTRA_PACKAGE, action.pkg);
        putIfSet(result, EXTRA_NETWORK, action.network);
        putIfSet(result, BUNDLE_EXTRA_STRING_PROFILE_ID, action.profileId);
        putIfSet(result, EXTRA_PROFILE_NAME, action.profileName);
        result.putString(EXTRA_ID, action.id);
        result.putString(EXTRA_CREATED, Long.toString(action.created));
        putIfSet(result, EXTRA_CALLER, action.caller);
        result.putString(EXTRA_SIGNATURE, signature);
        if (TaskerAction.hasVariable(action.pkg)) {
            result.putString(EXTRA_PACKAGE_VALUE, action.pkg);
            result.putString(TASKER_VARIABLE_REPLACE_KEYS, EXTRA_PACKAGE_VALUE);
        }
        try {
            result.putInt(BUNDLE_EXTRA_INT_VERSION_CODE,
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
        }
        return result;
    }

    /**
     * @return the action in a signed bundle (signature not checked yet), or null if malformed
     */
    static TaskerAction readSigned(final Bundle bundle) {
        if (!isSigned(bundle) || !TaskerAction.FORMAT.equals(bundle.getString(EXTRA_FORMAT))) {
            return null;
        }
        long created;
        try {
            created = Long.parseLong(bundle.getString(EXTRA_CREATED, ""));
        } catch (NumberFormatException e) {
            return null;
        }
        TaskerAction action = new TaskerAction(bundle.getString(EXTRA_ACTION), bundle.getString(EXTRA_PACKAGE),
                bundle.getString(EXTRA_NETWORK), bundle.getString(BUNDLE_EXTRA_STRING_PROFILE_ID),
                bundle.getString(EXTRA_PROFILE_NAME), bundle.getString(EXTRA_ID), created,
                bundle.getString(EXTRA_CALLER));
        return action.check() == null ? action : null;
    }

    private static void putIfSet(Bundle b, String key, String value) {
        if (value != null) {
            b.putString(key, value);
        }
    }

    /**
     * Private constructor prevents instantiation
     * 
     * @throws UnsupportedOperationException because this class cannot be instantiated.
     */
    private PluginBundleManager()
    {
        throw new UnsupportedOperationException("This class is non-instantiable"); //$NON-NLS-1$
    }
}