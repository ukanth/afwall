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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.haibison.android.lockpattern.R;

/**
 * All settings for the library. They are stored in {@link SharedPreferences}.
 * <p/>
 * For some options, you can set them directly via tag {@code <meta-data>}
 * inside tag {@code <activity>} in AndroidManifest.xml. Refer to setter methods
 * for details. Note that the values in the manifest get higher priority than
 * the ones from this class.
 * 
 * @author Hai Bison
 * 
 */
public class Settings {

    /**
     * This is singleton class.
     */
    private Settings() {
    }// Settings

    /**
     * Generates global preference filename of this library.
     * 
     * @return the global preference filename.
     */
    public static final String genPreferenceFilename() {
        return String.format("%s_%s", Sys.LIB_NAME, Sys.UID);
    }// genPreferenceFilename()

    /**
     * Generates global database filename. the database filename.
     * 
     * @return the global database filename.
     */
    public static final String genDatabaseFilename(String name) {
        return String.format("%s_%s_%s", Sys.LIB_NAME, Sys.UID, name);
    }// genDatabaseFilename()

    /**
     * Gets new {@link SharedPreferences}
     * 
     * @param context
     *            the context.
     * @return {@link SharedPreferences}
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static SharedPreferences p(Context context) {
        /*
         * Always use application context.
         */
        return context.getApplicationContext().getSharedPreferences(
                genPreferenceFilename(), Context.MODE_MULTI_PROCESS);
    }// p()

    /**
     * Setup {@code pm} to use global unique filename and global access mode.
     * You must use this method if you let the user change preferences via UI
     * (such as {@link PreferenceActivity}, {@link PreferenceFragment}...).
     * 
     * @param context
     *            the context.
     * @param pm
     *            {@link PreferenceManager}.
     * @since v2.6 beta
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setupPreferenceManager(Context context,
            PreferenceManager pm) {
        pm.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        pm.setSharedPreferencesName(genPreferenceFilename());
    }// setupPreferenceManager()

    /**
     * Display preferences.
     * 
     * @author Hai Bison
     * 
     */
    public static class Display {

        /**
         * Name to use for tag {@code <meta-data>} in AndroidManifest.xml.
         * 
         * @see #setStealthMode(Context, boolean)
         */
        public static final String METADATA_STEALTH_MODE = "stealthMode";

        /**
         * Name to use for tag {@code <meta-data>} in AndroidManifest.xml.
         * 
         * @see #setMinWiredDots(Context, int)
         */
        public static final String METADATA_MIN_WIRED_DOTS = "minWiredDots";

        /**
         * Name to use for tag {@code <meta-data>} in AndroidManifest.xml.
         * 
         * @see #setMaxRetries(Context, int)
         */
        public static final String METADATA_MAX_RETRIES = "maxRetries";

        /**
         * Name to use for tag {@code <meta-data>} in AndroidManifest.xml.
         * 
         * @see #setCaptchaWiredDots(Context, int)
         */
        public static final String METADATA_CAPTCHA_WIRED_DOTS = "captchaWiredDots";

        /**
         * This is singleton class.
         */
        private Display() {
        }// Display

        /**
         * Checks if the library is using stealth mode or not.
         * 
         * @param context
         *            the context.
         * @return {@code true} or {@code false}. Default is {@code false}.
         */
        public static boolean isStealthMode(Context context) {
            return p(context)
                    .getBoolean(
                            context.getString(R.string.alp_42447968_pkey_display_stealth_mode),
                            context.getResources()
                                    .getBoolean(
                                            R.bool.alp_42447968_pkey_display_stealth_mode_default));
        }// isStealthMode()

        /**
         * Sets stealth mode.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_STEALTH_MODE}.
         * 
         * @param context
         *            the context.
         * @param v
         *            the value.
         */
        public static void setStealthMode(Context context, boolean v) {
            p(context)
                    .edit()
                    .putBoolean(
                            context.getString(R.string.alp_42447968_pkey_display_stealth_mode),
                            v).commit();
        }// setStealthMode()

        /**
         * Gets minimum wired dots allowed for a pattern.
         * 
         * @param context
         *            the context.
         * @return the minimum wired dots allowed for a pattern. Default is
         *         {@code 4}.
         */
        public static int getMinWiredDots(Context context) {
            return p(context)
                    .getInt(context
                            .getString(R.string.alp_42447968_pkey_display_min_wired_dots),
                            context.getResources()
                                    .getInteger(
                                            R.integer.alp_42447968_pkey_display_min_wired_dots_default));
        }// getMinWiredDots()

        /**
         * Validates min wired dots.
         * 
         * @param context
         *            the context.
         * @param v
         *            the input value.
         * @return the correct value.
         */
        public static int validateMinWiredDots(Context context, int v) {
            if (v <= 0 || v > 9)
                v = context
                        .getResources()
                        .getInteger(
                                R.integer.alp_42447968_pkey_display_min_wired_dots_default);
            return v;
        }// validateMinWiredDots()

        /**
         * Sets minimum wired dots allowed for a pattern.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_MIN_WIRED_DOTS}.
         * 
         * @param context
         *            the context.
         * @param v
         *            the minimum wired dots allowed for a pattern.
         */
        public static void setMinWiredDots(Context context, int v) {
            v = validateMinWiredDots(context, v);
            p(context)
                    .edit()
                    .putInt(context
                            .getString(R.string.alp_42447968_pkey_display_min_wired_dots),
                            v).commit();
        }// setMinWiredDots()

        /**
         * Gets max retries allowed in mode comparing pattern.
         * 
         * @param context
         *            the context.
         * @return the max retries allowed in mode comparing pattern. Default is
         *         {@code 5}.
         */
        public static int getMaxRetries(Context context) {
            return p(context)
                    .getInt(context
                            .getString(R.string.alp_42447968_pkey_display_max_retries),
                            context.getResources()
                                    .getInteger(
                                            R.integer.alp_42447968_pkey_display_max_retries_default));
        }// getMaxRetries()

        /**
         * Validates max retries.
         * 
         * @param context
         *            the context.
         * @param v
         *            the input value.
         * @return the correct value.
         */
        public static int validateMaxRetries(Context context, int v) {
            if (v <= 0)
                v = context
                        .getResources()
                        .getInteger(
                                R.integer.alp_42447968_pkey_display_max_retries_default);
            return v;
        }// validateMaxRetries()

        /**
         * Sets max retries allowed in mode comparing pattern.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_MAX_RETRIES}.
         * 
         * @param context
         *            the context.
         * @param v
         *            the max retries allowed in mode comparing pattern.
         */
        public static void setMaxRetries(Context context, int v) {
            v = validateMaxRetries(context, v);
            p(context)
                    .edit()
                    .putInt(context
                            .getString(R.string.alp_42447968_pkey_display_max_retries),
                            v).commit();
        }// setMaxRetries()

        /**
         * Gets wired dots for a "CAPTCHA" pattern.
         * 
         * @param context
         *            the context.
         * @return the wired dots for a "CAPTCHA" pattern. Default is {@code 4}.
         */
        public static int getCaptchaWiredDots(Context context) {
            return p(context)
                    .getInt(context
                            .getString(R.string.alp_42447968_pkey_display_captcha_wired_dots),
                            context.getResources()
                                    .getInteger(
                                            R.integer.alp_42447968_pkey_display_captcha_wired_dots_default));
        }// getCaptchaWiredDots()

        /**
         * Validates CAPTCHA wired dots.
         * 
         * @param context
         *            the context.
         * @param v
         *            the input value.
         * @return the correct value.
         */
        public static int validateCaptchaWiredDots(Context context, int v) {
            if (v <= 0 || v > 9)
                v = context
                        .getResources()
                        .getInteger(
                                R.integer.alp_42447968_pkey_display_captcha_wired_dots_default);
            return v;
        }// validateCaptchaWiredDots()

        /**
         * Sets wired dots for a "CAPTCHA" pattern.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_CAPTCHA_WIRED_DOTS}.
         * 
         * @param context
         *            the context.
         * @param v
         *            the wired dots for a "CAPTCHA" pattern.
         */
        public static void setCaptchaWiredDots(Context context, int v) {
            v = validateCaptchaWiredDots(context, v);
            p(context)
                    .edit()
                    .putInt(context
                            .getString(R.string.alp_42447968_pkey_display_captcha_wired_dots),
                            v).commit();
        }// setCaptchaWiredDots()

    }// Display

    /**
     * Security preferences.
     * 
     * @author Hai Bison
     * 
     */
    public static class Security {

        /**
         * Name to use for tag {@code <meta-data>} in AndroidManifest.xml.
         * 
         * @see #setEncrypterClass(Context, char[])
         * @see #setEncrypterClass(Context, Class)
         */
        public static final String METADATA_ENCRYPTER_CLASS = "encrypterClass";

        /**
         * Name to use for tag {@code <meta-data>} in AndroidManifest.xml.
         * 
         * @see #setAutoSavePattern(Context, boolean)
         */
        public static final String METADATA_AUTO_SAVE_PATTERN = "autoSavePattern";

        /**
         * This is singleton class.
         */
        private Security() {
        }// Security

        /**
         * Checks if the library is using auto-save pattern mode.
         * 
         * @param context
         *            the context.
         * @return {@code true} or {@code false}. Default is {@code false}.
         */
        public static boolean isAutoSavePattern(Context context) {
            return p(context)
                    .getBoolean(
                            context.getString(R.string.alp_42447968_pkey_sys_auto_save_pattern),
                            context.getResources()
                                    .getBoolean(
                                            R.bool.alp_42447968_pkey_sys_auto_save_pattern_default));
        }// isAutoSavePattern()

        /**
         * Sets auto-save pattern mode.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_AUTO_SAVE_PATTERN}.
         * 
         * @param context
         *            the context.
         * @param v
         *            the auto-save mode.
         */
        public static void setAutoSavePattern(Context context, boolean v) {
            p(context)
                    .edit()
                    .putBoolean(
                            context.getString(R.string.alp_42447968_pkey_sys_auto_save_pattern),
                            v).commit();
            if (!v)
                setPattern(context, null);
        }// setAutoSavePattern()

        /**
         * Gets the pattern.
         * 
         * @param context
         *            the context.
         * @return the pattern. Default is {@code null}.
         */
        public static char[] getPattern(Context context) {
            String pattern = p(context).getString(
                    context.getString(R.string.alp_42447968_pkey_sys_pattern),
                    null);
            return pattern == null ? null : pattern.toCharArray();
        }// getPattern()

        /**
         * Sets the pattern.
         * 
         * @param context
         *            the context.
         * @param pattern
         *            the pattern, can be {@code null} to reset it.
         */
        public static void setPattern(Context context, char[] pattern) {
            p(context)
                    .edit()
                    .putString(
                            context.getString(R.string.alp_42447968_pkey_sys_pattern),
                            pattern != null ? new String(pattern) : null)
                    .commit();
        }// setPattern()

        /**
         * Gets encrypter class.
         * 
         * @param context
         *            the context.
         * @return the full name of encrypter class. Default is {@code null}.
         */
        public static char[] getEncrypterClass(Context context) {
            String clazz = p(context)
                    .getString(
                            context.getString(R.string.alp_42447968_pkey_sys_encrypter_class),
                            null);
            return clazz == null ? null : clazz.toCharArray();
        }// getEncrypterClass()

        /**
         * Sets encrypter class.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_ENCRYPTER_CLASS}.
         * 
         * @param context
         *            the context.
         * @param clazz
         *            the encrypter class, can be {@code null} if you don't want
         *            to use it.
         */
        public static void setEncrypterClass(Context context, Class<?> clazz) {
            setEncrypterClass(context, clazz != null ? clazz.getName()
                    .toCharArray() : null);
        }// setEncrypterClass()

        /**
         * Sets encrypter class.
         * <p/>
         * You can set this value in AndroidManifest.xml with
         * {@link #METADATA_ENCRYPTER_CLASS}.
         * 
         * @param context
         *            the context.
         * @param clazz
         *            the full name of encrypter class, can be {@code null} if
         *            you don't want to use it.
         */
        public static void setEncrypterClass(Context context, char[] clazz) {
            p(context)
                    .edit()
                    .putString(
                            context.getString(R.string.alp_42447968_pkey_sys_encrypter_class),
                            clazz != null ? new String(clazz) : null).commit();
        }// setEncrypterClass()

    }// Security

}
