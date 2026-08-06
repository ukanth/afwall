package dev.ukanth.ufirewall.util;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.util.regex.Pattern;

/**
 * Input validation utility to prevent injection attacks and validate external data
 */
public class InputValidator {
    private static final String TAG = "InputValidator";
    
    // Patterns for common validation scenarios
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");
    private static final Pattern UID_PATTERN = Pattern.compile("^[0-9]+$");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    private static final Pattern PORT_PATTERN = Pattern.compile("^([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$");
    private static final Pattern INTERFACE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    
    // Dangerous characters that should not appear in most inputs
    private static final Pattern DANGEROUS_CHARS = Pattern.compile("[;&|`$\\r\\n<>\"'\\\\]");
    
    // Maximum lengths for various input types
    private static final int MAX_PACKAGE_NAME_LENGTH = 256;
    private static final int MAX_FILE_PATH_LENGTH = 4096;
    private static final int MAX_RULE_LENGTH = 1024;
    private static final int MAX_PROFILE_NAME_LENGTH = 64;
    
    /**
     * Validate and sanitize a package name
     */
    public static String validatePackageName(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        
        if (packageName.length() > MAX_PACKAGE_NAME_LENGTH) {
            Log.w(TAG, "Package name too long: " + packageName.length() + " characters");
            return null;
        }
        
        if (!PACKAGE_NAME_PATTERN.matcher(packageName).matches()) {
            Log.w(TAG, "Invalid package name format: " + packageName);
            return null;
        }
        
        return packageName.trim();
    }
    
    /**
     * Validate UID string
     */
    public static Integer validateUid(String uidString) {
        if (TextUtils.isEmpty(uidString)) {
            return null;
        }
        
        String trimmed = uidString.trim();
        if (!UID_PATTERN.matcher(trimmed).matches()) {
            Log.w(TAG, "Invalid UID format: " + uidString);
            return null;
        }
        
        try {
            int uid = Integer.parseInt(trimmed);
            if (uid < 0 || uid > 99999) { // Android UID range
                Log.w(TAG, "UID out of valid range: " + uid);
                return null;
            }
            return uid;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Could not parse UID: " + uidString, e);
            return null;
        }
    }
    
    /**
     * Validate IP address
     */
    public static String validateIpAddress(String ipAddress) {
        if (TextUtils.isEmpty(ipAddress)) {
            return null;
        }
        
        String trimmed = ipAddress.trim();
        if (!IP_ADDRESS_PATTERN.matcher(trimmed).matches()) {
            Log.w(TAG, "Invalid IP address format: " + ipAddress);
            return null;
        }
        
        return trimmed;
    }
    
    /**
     * Validate port number
     */
    public static Integer validatePort(String portString) {
        if (TextUtils.isEmpty(portString)) {
            return null;
        }
        
        String trimmed = portString.trim();
        if (!PORT_PATTERN.matcher(trimmed).matches()) {
            Log.w(TAG, "Invalid port format: " + portString);
            return null;
        }
        
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Could not parse port: " + portString, e);
            return null;
        }
    }
    
    /**
     * Validate network interface name
     */
    public static String validateInterfaceName(String interfaceName) {
        if (TextUtils.isEmpty(interfaceName)) {
            return null;
        }
        
        String trimmed = interfaceName.trim();
        if (!INTERFACE_NAME_PATTERN.matcher(trimmed).matches()) {
            Log.w(TAG, "Invalid interface name: " + interfaceName);
            return null;
        }
        
        if (trimmed.length() > 16) { // Linux interface name limit
            Log.w(TAG, "Interface name too long: " + trimmed);
            return null;
        }
        
        return trimmed;
    }
    
    /**
     * Validate and sanitize file path to prevent directory traversal
     */
    public static String validateFilePath(String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return null;
        }
        
        if (filePath.length() > MAX_FILE_PATH_LENGTH) {
            Log.w(TAG, "File path too long: " + filePath.length() + " characters");
            return null;
        }
        
        String normalized = filePath.trim();
        
        // Check for directory traversal attempts
        if (normalized.contains("../") || normalized.contains("..\\") || 
            normalized.contains("/..") || normalized.contains("\\..")) {
            Log.w(TAG, "Directory traversal attempt detected: " + filePath);
            return null;
        }
        
        // Check for dangerous characters
        if (DANGEROUS_CHARS.matcher(normalized).find()) {
            Log.w(TAG, "Dangerous characters in file path: " + filePath);
            return null;
        }
        
        return normalized;
    }
    
    /**
     * Validate custom iptables rule (additional validation beyond sanitizeRule in Api.java)
     */
    public static String validateCustomRule(String rule) {
        if (TextUtils.isEmpty(rule)) {
            return null;
        }
        
        if (rule.length() > MAX_RULE_LENGTH) {
            Log.w(TAG, "Custom rule too long: " + rule.length() + " characters");
            return null;
        }
        
        String trimmed = rule.trim();
        
        // Additional security checks beyond Api.sanitizeRule
        if (trimmed.toLowerCase().contains("exec") || 
            trimmed.toLowerCase().contains("system") ||
            trimmed.toLowerCase().contains("eval") ||
            trimmed.toLowerCase().contains("shell")) {
            Log.w(TAG, "Potentially dangerous custom rule: " + rule);
            return null;
        }
        
        return trimmed;
    }
    
    /**
     * Validate profile name
     */
    public static String validateProfileName(String profileName) {
        if (TextUtils.isEmpty(profileName)) {
            return null;
        }
        
        if (profileName.length() > MAX_PROFILE_NAME_LENGTH) {
            Log.w(TAG, "Profile name too long: " + profileName.length() + " characters");
            return null;
        }
        
        String trimmed = profileName.trim();
        
        // Only allow alphanumeric characters, spaces, hyphens, and underscores
        if (!trimmed.matches("^[a-zA-Z0-9 _-]+$")) {
            Log.w(TAG, "Invalid profile name format: " + profileName);
            return null;
        }
        
        return trimmed;
    }
    
    /**
     * Safely extract string from Intent extras with validation
     */
    public static String getValidatedStringExtra(Intent intent, String key, int maxLength) {
        if (intent == null || TextUtils.isEmpty(key)) {
            return null;
        }
        
        try {
            String value = intent.getStringExtra(key);
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            
            if (value.length() > maxLength) {
                Log.w(TAG, "Intent extra too long for key " + key + ": " + value.length() + " characters");
                return null;
            }
            
            // Basic sanitization - remove control characters
            String sanitized = value.replaceAll("[\\x00-\\x1F\\x7F]", "");
            return sanitized.trim();
            
        } catch (Exception e) {
            Log.w(TAG, "Error extracting intent extra for key: " + key, e);
            return null;
        }
    }
    
    /**
     * Safely extract string from Bundle with validation
     */
    public static String getValidatedBundleString(Bundle bundle, String key, int maxLength) {
        if (bundle == null || TextUtils.isEmpty(key)) {
            return null;
        }
        
        try {
            String value = bundle.getString(key);
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            
            if (value.length() > maxLength) {
                Log.w(TAG, "Bundle value too long for key " + key + ": " + value.length() + " characters");
                return null;
            }
            
            // Basic sanitization - remove control characters
            String sanitized = value.replaceAll("[\\x00-\\x1F\\x7F]", "");
            return sanitized.trim();
            
        } catch (Exception e) {
            Log.w(TAG, "Error extracting bundle value for key: " + key, e);
            return null;
        }
    }
    
    /**
     * General purpose string sanitization
     */
    public static String sanitizeString(String input, int maxLength) {
        if (TextUtils.isEmpty(input)) {
            return null;
        }
        
        if (input.length() > maxLength) {
            Log.w(TAG, "Input too long: " + input.length() + " characters");
            return null;
        }
        
        // Remove control characters and potentially dangerous characters
        String sanitized = input.replaceAll("[\\x00-\\x1F\\x7F]", "");
        
        if (DANGEROUS_CHARS.matcher(sanitized).find()) {
            Log.w(TAG, "Dangerous characters found in input");
            return null;
        }
        
        return sanitized.trim();
    }
    
    /**
     * Check if a string contains only safe characters for use in shell commands
     */
    public static boolean isSafeForShell(String input) {
        if (TextUtils.isEmpty(input)) {
            return false;
        }
        
        // Allow only alphanumeric characters, hyphens, underscores, dots, and forward slashes
        return input.matches("^[a-zA-Z0-9._/-]+$");
    }
}