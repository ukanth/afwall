package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Secure cryptographic utility with backward compatibility for DES-encrypted passwords.
 * 
 * This class provides:
 * - Modern AES-256-GCM encryption for new passwords
 * - Backward compatibility for existing DES-encrypted passwords
 * - Automatic migration from DES to AES when passwords are verified
 * - Secure key derivation using PBKDF2
 */
public class SecureCrypto {
    private static final String TAG = "SecureCrypto";
    
    // Modern encryption constants
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final int AES_KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int SALT_LENGTH = 32;
    
    // Legacy DES constants (for backward compatibility)
    private static final String DES_ALGORITHM = "DES";
    private static final String CHARSET_NAME = "UTF-8";
    private static final int BASE64_MODE = Base64.DEFAULT;
    
    // Version identifiers for encrypted data
    private static final String AES_PREFIX = "AES:";
    private static final String DES_PREFIX = "DES:";
    
    private static final String PREF_ENCRYPTION_VERSION = "encryption_version";
    private static final String PREF_PASSWORD_SALT = "password_salt";
    private static final int ENCRYPTION_VERSION_AES = 2;
    private static final int ENCRYPTION_VERSION_DES = 1;

    /**
     * Encrypt data using modern AES-256-GCM encryption
     */
    public static String encryptSecure(Context context, String masterKey, String data) {
        if (masterKey == null || data == null) {
            return null;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences("secure_crypto", Context.MODE_PRIVATE);
            
            // Generate or retrieve salt
            String saltBase64 = prefs.getString(PREF_PASSWORD_SALT, null);
            byte[] salt;
            if (saltBase64 == null) {
                salt = new byte[SALT_LENGTH];
                new SecureRandom().nextBytes(salt);
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP);
                prefs.edit().putString(PREF_PASSWORD_SALT, saltBase64).apply();
            } else {
                salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            }
            
            // Derive key using PBKDF2
            SecretKey key = deriveKey(masterKey, salt);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            // Encrypt data
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            // Mark encryption version and store
            prefs.edit().putInt(PREF_ENCRYPTION_VERSION, ENCRYPTION_VERSION_AES).apply();
            
            return AES_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP);
            
        } catch (Exception e) {
            Log.e(TAG, "AES encryption failed", e);
            return null;
        }
    }
    
    /**
     * Decrypt data - handles both AES and legacy DES formats
     */
    public static String decryptSecure(Context context, String masterKey, String encryptedData) {
        if (masterKey == null || encryptedData == null) {
            return null;
        }
        
        try {
            if (encryptedData.startsWith(AES_PREFIX)) {
                // Modern AES decryption
                return decryptAES(context, masterKey, encryptedData.substring(AES_PREFIX.length()));
            } else if (encryptedData.startsWith(DES_PREFIX)) {
                // Legacy DES decryption (marked format)
                return decryptDES(masterKey, encryptedData.substring(DES_PREFIX.length()));
            } else {
                // Assume legacy DES format (no prefix)
                return decryptDES(masterKey, encryptedData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }
    
    /**
     * Decrypt using modern AES-256-GCM
     */
    private static String decryptAES(Context context, String masterKey, String encryptedData) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("secure_crypto", Context.MODE_PRIVATE);
        
        // Get salt
        String saltBase64 = prefs.getString(PREF_PASSWORD_SALT, null);
        if (saltBase64 == null) {
            throw new IllegalStateException("Salt not found for AES decryption");
        }
        byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
        
        // Derive key
        SecretKey key = deriveKey(masterKey, salt);
        
        // Decode encrypted data
        byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
        
        // Extract IV and encrypted data
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
        
        // Decrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Legacy DES decryption for backward compatibility
     */
    private static String decryptDES(String masterKey, String encryptedData) throws Exception {
        byte[] dataBytes = Base64.decode(encryptedData, BASE64_MODE);
        DESKeySpec desKeySpec = new DESKeySpec(masterKey.getBytes(CHARSET_NAME));
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(DES_ALGORITHM);
        SecretKey secretKey = secretKeyFactory.generateSecret(desKeySpec);
        Cipher cipher = Cipher.getInstance(DES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] dataBytesDecrypted = cipher.doFinal(dataBytes);
        return new String(dataBytesDecrypted, CHARSET_NAME);
    }
    
    /**
     * Derive AES key using PBKDF2
     */
    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
    
    /**
     * Check if data is encrypted with legacy DES
     */
    public static boolean isLegacyEncryption(String encryptedData) {
        return encryptedData != null && 
               !encryptedData.startsWith(AES_PREFIX) && 
               !encryptedData.startsWith(DES_PREFIX);
    }
    
    /**
     * Migrate password from DES to AES encryption
     * This should be called when a legacy password is successfully verified
     */
    public static String migrateToAES(Context context, String masterKey, String plaintext) {
        Log.i(TAG, "Migrating password from DES to AES encryption");
        return encryptSecure(context, masterKey, plaintext);
    }
    
    /**
     * Get current encryption version
     */
    public static int getCurrentEncryptionVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("secure_crypto", Context.MODE_PRIVATE);
        return prefs.getInt(PREF_ENCRYPTION_VERSION, ENCRYPTION_VERSION_DES);
    }
    
    /**
     * Validate that the crypto system is working correctly
     */
    public static boolean validateCrypto(Context context) {
        try {
            String testData = "AFWall+ Security Test";
            String testKey = "TestKey123";
            
            String encrypted = encryptSecure(context, testKey, testData);
            if (encrypted == null) return false;
            
            String decrypted = decryptSecure(context, testKey, encrypted);
            return testData.equals(decrypted);
            
        } catch (Exception e) {
            Log.e(TAG, "Crypto validation failed", e);
            return false;
        }
    }
    
    /**
     * Clear all crypto preferences (for testing or reset purposes)
     */
    public static void clearCryptoPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("secure_crypto", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}