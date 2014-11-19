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

import java.util.List;

import android.content.Context;

import com.haibison.android.lockpattern.widget.LockPatternView.Cell;

/**
 * Interface for encrypter.
 * 
 * @author Hai Bison
 * @since v2 beta
 */
public interface IEncrypter {

    /**
     * Encrypts {@code pattern}.
     * 
     * @param context
     *            the context.
     * @param pattern
     *            the pattern in the form of a list of {@link Cell}.
     * @return the encrypted char array of the pattern.
     * @since v2.1 beta
     */
    char[] encrypt(Context context, List<Cell> pattern);

    /**
     * Decrypts an encrypted pattern.
     * 
     * @param context
     *            the context.
     * @param encryptedPattern
     *            the encrypted pattern.
     * @return the original pattern.
     */
    List<Cell> decrypt(Context context, char[] encryptedPattern);

}
