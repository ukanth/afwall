/*
 * Copyright (C) 2007 The Android Open Source Project
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

package group.pals.android.lib.ui.lockpattern.widget;

import group.pals.android.lib.ui.lockpattern.collect.Lists;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Utilities for the lock pattern and its settings.
 */
public class LockPatternUtils {

    public static final String _Utf8 = "utf-8";
    public static final String _Sha1 = "sha-1";

    /**
     * Deserialize a pattern.
     * 
     * @param string
     *            The pattern serialized with {@link #patternToString}
     * @return The pattern.
     */
    public static List<LockPatternView.Cell> stringToPattern(String string) {
        List<LockPatternView.Cell> result = Lists.newArrayList();

        try {
            final byte[] bytes = string.getBytes(_Utf8);
            for (int i = 0; i < bytes.length; i++) {
                byte b = bytes[i];
                result.add(LockPatternView.Cell.of(b / 3, b % 3));
            }
        } catch (UnsupportedEncodingException e) {
            // never catch this
        }

        return result;
    }// stringToPattern()

    /**
     * Serialize a pattern.
     * 
     * @param pattern
     *            The pattern.
     * @return The pattern in string form.
     */
    public static String patternToString(List<LockPatternView.Cell> pattern) {
        if (pattern == null) {
            return "";
        }
        final int patternSize = pattern.size();

        byte[] res = new byte[patternSize];
        for (int i = 0; i < patternSize; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            res[i] = (byte) (cell.getRow() * 3 + cell.getColumn());
        }
        try {
            return new String(res, _Utf8);
        } catch (UnsupportedEncodingException e) {
            // never catch this
            return "";
        }
    }// patternToString()

    /**
     * Serializes a pattern
     * 
     * @param pattern
     *            The pattern
     * @return The SHA-1 string of the pattern got from
     *         {@link #patternToString(List)}
     */
    public static String patternToSha1(List<LockPatternView.Cell> pattern) {
        try {
            MessageDigest md = MessageDigest.getInstance(_Sha1);
            md.update(patternToString(pattern).getBytes(_Utf8));

            byte[] digest = md.digest();
            BigInteger bi = new BigInteger(1, digest);
            return String.format((Locale) null, "%0" + (digest.length * 2)
                    + "x", bi).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            // never catch this
            return "";
        } catch (UnsupportedEncodingException e) {
            // never catch this
            return "";
        }
    }// patternToSha1()
}
