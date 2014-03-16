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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The <b>simple-and-weak</b> encryption utilities.
 * 
 * @author Hai Bison
 * 
 */
public class SimpleWeakEncryption {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_FACTORY_ALGORITHM = "PBEWithMD5AndDES";
    private static final String SECRET_KEY_SPEC_ALGORITHM = "AES";

    private static final int KEY_LEN = 256;
    private static final int IV_LEN = 16;
    private static final int ITERATION_COUNT = 512;
    private static final char SEPARATOR = '@';

    public static final String UTF8 = "UTF-8";
    public static final String SHA256 = "SHA-256";

    /**
     * This is singleton class.
     */
    private SimpleWeakEncryption() {
    }// SimpleWeakEncryption()

    /**
     * Encrypts {@code data} by {@code key}.
     * 
     * @param password
     *            the secret key.
     * @param salt
     *            the salt, can be {@code null}. But it is highly recommended
     *            that you should provide it.
     * @param data
     *            the data.
     * @return the encrypted data.
     * @throws RuntimeException
     *             which wraps the original exception related to cipher process.
     */
    public static String encrypt(final char[] password, byte[] salt,
            final String data) {
        byte[] bytes = null;
        try {
            bytes = data.getBytes(UTF8);
        } catch (UnsupportedEncodingException e) {
            /*
             * Never catch this.
             */
            throw new RuntimeException(e);
        }

        Cipher cipher = null;

        try {
            cipher = Cipher.getInstance(TRANSFORMATION);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

        /*
         * cipher.getIV() doesn't work the same for different API levels. So
         * we're using this technique.
         */
        final byte[] iv = SecureRandom.getSeed(IV_LEN);

        try {
            cipher.init(Cipher.ENCRYPT_MODE, genKey(password, salt),
                    new IvParameterSpec(iv));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }

        try {
            bytes = cipher.doFinal(bytes);
            return String.format("%s%s%s", Base36.toBase36(iv), SEPARATOR,
                    Base36.toBase36(bytes));
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }// encrypt()

    /**
     * Decrypts an encrypted string ({@code data}) by {@code key}.
     * 
     * @param password
     *            the password.
     * @param salt
     *            the salt, can be {@code null}. But it is highly recommended
     *            that you should provide it.
     * @param data
     *            the data.
     * @return the decrypted string, or {@code null} if {@code password} is
     *         invalid.
     * @throws RuntimeException
     *             which wraps the original exception related to cipher process.
     */
    public static String decrypt(final char[] password, byte[] salt,
            final String data) {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance(TRANSFORMATION);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

        final int iSeparator = data.indexOf(SEPARATOR);

        try {
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    genKey(password, salt),
                    new IvParameterSpec(Base36.toBytes(data.substring(0,
                            iSeparator))));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }

        try {
            return new String(cipher.doFinal(Base36.toBytes(data
                    .substring(iSeparator + 1))), UTF8);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            /*
             * Never catch this.
             */
            throw new RuntimeException(e);
        }
    }// decrypt()

    /**
     * Generates secret key.
     * 
     * @param password
     *            the password.
     * @param salt
     *            the salt, can be {@code null}. But it is highly recommended
     *            that you should provide it.
     * @return the secret key.
     * @throws RuntimeException
     *             which wraps the original exception related to cipher process.
     */
    private static Key genKey(char[] password, byte[] salt) {
        SecretKeyFactory factory;
        try {
            factory = SecretKeyFactory
                    .getInstance(SECRET_KEY_FACTORY_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        if (salt != null && salt.length > 0)
            salt = sha256(salt);
        else
            salt = sha256(new String(password));

        KeySpec spec = new PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LEN);

        SecretKey tmp = null;
        try {
            tmp = factory.generateSecret(spec);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }

        return new SecretKeySpec(sha256(tmp.getEncoded()),
                SECRET_KEY_SPEC_ALGORITHM);
    }// genKey()

    /**
     * Calculates SHA-256 of a string.
     * 
     * @param s
     *            the string.
     * @return the SHA-256 of given string.
     * @throws RuntimeException
     *             which wraps {@link UnsupportedEncodingException} in case the
     *             system does not support {@link #UTF8}.
     */
    public static byte[] sha256(String s) {
        try {
            return sha256(s.getBytes(UTF8));
        } catch (UnsupportedEncodingException e) {
            /*
             * Never catch this.
             */
            throw new RuntimeException(e);
        }
    }// sha256()

    /**
     * Calculates SHA-256 of a byte array.
     * 
     * @param bytes
     *            the byte array.
     * @return the SHA-256 of given data.
     * @throws RuntimeException
     *             which wraps {@link NoSuchAlgorithmException} in case the
     *             system does not support calculating message digest of
     *             {@link #SHA256}.
     */
    public static byte[] sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA256);
            md.update(bytes);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            /*
             * Never catch this.
             */
            throw new RuntimeException(e);
        }
    }// sha256()

    /**
     * Base-36 utilities.
     * 
     * @author Hai Bison
     * 
     */
    public static class Base36 {

        /**
         * This is singleton class.
         */
        private Base36() {
        }// Base36()

        /**
         * Converts a byte array to base-36.
         * 
         * @param bytes
         *            the byte array.
         * @return the base-36 string representing the data given.
         */
        public static String toBase36(byte[] bytes) {
            return new BigInteger(bytes).toString(Character.MAX_RADIX);
        }// toBase36()

        /**
         * Converts a base-36 string to its byte array.
         * 
         * @param base36
         *            the base-36 string.
         * @return the original data.
         */
        public static byte[] toBytes(String base36) {
            return new BigInteger(base36, Character.MAX_RADIX).toByteArray();
        }// toBytes()

    }// Base36

}
