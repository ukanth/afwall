package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.util.Log;

/**
 * Secure password manager that handles encryption, storage, and migration
 * with backward compatibility for existing DES-encrypted passwords
 */
public class SecurePasswordManager {
    private static final String TAG = "SecurePasswordManager";
    private static final String MASTER_KEY = "AFW@LL_P@SSWORD_PR0T3CTI0N";
    
    /**
     * Store a password securely using modern AES encryption
     * 
     * @param context Application context
     * @param password Plain text password to store
     * @return true if password was stored successfully
     */
    public static boolean storePasswordSecure(Context context, String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        
        try {
            String encrypted = SecureCrypto.encryptSecure(context, MASTER_KEY, password);
            if (encrypted != null) {
                // Use G's helper method to store password
                G.profile_pwd(encrypted);
                G.gPrefs.edit()
                    .putBoolean("enc", true)
                    .putBoolean("secure_enc", true) // Flag for new encryption
                    .apply();
                Log.i(TAG, "Password stored with secure encryption");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to store password securely", e);
        }
        return false;
    }
    
    /**
     * Verify a password against stored encrypted password with automatic migration
     * 
     * @param context Application context  
     * @param enteredPassword Password entered by user
     * @return true if password matches
     */
    public static boolean verifyPassword(Context context, String enteredPassword) {
        if (enteredPassword == null) {
            return false;
        }
        
        String storedPassword = G.profile_pwd();
        if (storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        
        try {
            if (G.isEnc()) {
                // Check if using new secure encryption
                if (G.gPrefs.getBoolean("secure_enc", false)) {
                    String decrypted = SecureCrypto.decryptSecure(context, MASTER_KEY, storedPassword);
                    return enteredPassword.equals(decrypted);
                } else {
                    // Try new secure decryption first (for migrated passwords)
                    String decrypted = SecureCrypto.decryptSecure(context, MASTER_KEY, storedPassword);
                    if (decrypted != null && enteredPassword.equals(decrypted)) {
                        return true;
                    }
                    
                    // Fallback to legacy DES decryption
                    decrypted = dev.ukanth.ufirewall.Api.unhideCrypt(MASTER_KEY, storedPassword);
                    if (decrypted != null && enteredPassword.equals(decrypted)) {
                        // Auto-migrate to secure encryption
                        if (migrateToSecureEncryption(context, enteredPassword)) {
                            Log.i(TAG, "Successfully migrated password from DES to AES");
                        }
                        return true;
                    }
                }
            } else {
                // Plain text password - migrate to secure encryption
                if (enteredPassword.equals(storedPassword)) {
                    if (migrateToSecureEncryption(context, enteredPassword)) {
                        Log.i(TAG, "Successfully migrated plaintext password to AES");
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during password verification", e);
        }
        
        return false;
    }
    
    /**
     * Migrate existing password to secure AES encryption
     */
    private static boolean migrateToSecureEncryption(Context context, String plainPassword) {
        try {
            return storePasswordSecure(context, plainPassword);
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate password to secure encryption", e);
            return false;
        }
    }
    
    /**
     * Check if password is encrypted with legacy DES
     */
    public static boolean isLegacyEncryption() {
        return G.isEnc() && !G.gPrefs.getBoolean("secure_enc", false);
    }
    
    /**
     * Get encryption status information for debugging
     */
    public static String getEncryptionStatus(Context context) {
        StringBuilder status = new StringBuilder();
        status.append("Encrypted: ").append(G.isEnc()).append("\n");
        status.append("Secure encryption: ").append(G.gPrefs.getBoolean("secure_enc", false)).append("\n");
        status.append("Legacy encryption: ").append(isLegacyEncryption()).append("\n");
        
        if (G.isEnc()) {
            String stored = G.profile_pwd();
            status.append("Uses AES prefix: ").append(stored != null && stored.startsWith("AES:")).append("\n");
            status.append("Crypto validation: ").append(SecureCrypto.validateCrypto(context)).append("\n");
        }
        
        return status.toString();
    }
    
    /**
     * Force password re-encryption (for testing or security updates)
     */
    public static boolean reencryptPassword(Context context, String currentPassword) {
        if (!verifyPassword(context, currentPassword)) {
            Log.w(TAG, "Cannot re-encrypt: current password verification failed");
            return false;
        }
        
        return storePasswordSecure(context, currentPassword);
    }
    
    /**
     * Clear all password data (for reset/logout)
     */
    public static void clearPassword() {
        G.profile_pwd(""); // Clear password using G's method
        G.gPrefs.edit()
            .remove("enc")
            .remove("secure_enc")
            .apply();
        Log.i(TAG, "Password data cleared");
    }
}