package com.bearmod.loader.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure preferences utility for storing sensitive data using Android Keystore directly
 * Updated to replace deprecated AndroidX Security Crypto APIs with direct Keystore usage
 */
class SecurePreferences(private val context: Context) {

    companion object {
        private const val TAG = "SecurePreferences"
        private const val PREFS_NAME = "keyauth_secure_prefs_v2"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_REMEMBER_LICENSE = "remember_license"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_HWID = "device_hwid"

        // Session and token management keys
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_DEVICE_REGISTERED = "device_registered"
        private const val KEY_LAST_AUTH_HWID = "last_auth_hwid"
        private const val KEY_BOUND_LICENSE = "bound_license_key"
        private const val KEY_DEVICE_TRUST_LEVEL = "device_trust_level"

        // Keystore configuration
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "keyauth_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    private val sharedPreferences: SharedPreferences by lazy {
        createSecureSharedPreferences()
    }

    private val isEncryptionSupported: Boolean by lazy {
        true // minSdk is 24, so Android Keystore is always supported
    }

    /**
     * Creates secure shared preferences with Android Keystore encryption
     */
    private fun createSecureSharedPreferences(): SharedPreferences {
        return if (isEncryptionSupported) {
            try {
                // Initialize the master key in Android Keystore
                initializeMasterKey()
                Log.d(TAG, "Successfully initialized Android Keystore encryption")
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Android Keystore encryption", e)
                Log.w(TAG, "Falling back to unencrypted preferences - data will not be encrypted!")
                createFallbackPreferences()
            }
        } else {
            Log.w(TAG, "Android Keystore not supported on this device (API < 23)")
            Log.w(TAG, "Using unencrypted preferences - data will not be encrypted!")
            createFallbackPreferences()
        }
    }

    /**
     * Initializes or retrieves the master key from Android Keystore
     */
    private fun initializeMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            // Check if key already exists
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                // Generate new key
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.d(TAG, "Generated new master key in Android Keystore")
            } else {
                Log.d(TAG, "Using existing master key from Android Keystore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize master key", e)
            throw e
        }
    }

    /**
     * Creates fallback unencrypted preferences if encryption fails
     */
    private fun createFallbackPreferences(): SharedPreferences {
        return context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
    }
    
    /**
     * Encrypts data using Android Keystore
     */
    private fun encryptData(data: String): String? {
        return if (isEncryptionSupported) {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
                keyStore.load(null)

                val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)

                val iv = cipher.iv
                val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

                // Combine IV and encrypted data
                val combined = ByteArray(iv.size + encryptedData.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)

                Base64.encodeToString(combined, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to encrypt data", e)
                null
            }
        } else {
            // Return data as-is if encryption not supported
            data
        }
    }

    /**
     * Decrypts data using Android Keystore
     */
    private fun decryptData(encryptedData: String): String? {
        return if (isEncryptionSupported) {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
                keyStore.load(null)

                val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
                val combined = Base64.decode(encryptedData, Base64.DEFAULT)

                // Extract IV and encrypted data
                val iv = ByteArray(GCM_IV_LENGTH)
                val encrypted = ByteArray(combined.size - GCM_IV_LENGTH)
                System.arraycopy(combined, 0, iv, 0, iv.size)
                System.arraycopy(combined, iv.size, encrypted, 0, encrypted.size)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

                val decryptedData = cipher.doFinal(encrypted)
                String(decryptedData, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt data", e)
                null
            }
        } else {
            // Return data as-is if encryption not supported
            encryptedData
        }
    }

    /**
     * Save license key securely
     * @param licenseKey The license key to save
     */
    fun saveLicenseKey(licenseKey: String) {
        try {
            val dataToStore = if (isEncryptionSupported) {
                encryptData(licenseKey) ?: run {
                    Log.w(TAG, "Encryption failed, storing unencrypted data")
                    licenseKey
                }
            } else {
                licenseKey
            }

            sharedPreferences.edit()
                .putString(KEY_LICENSE_KEY, dataToStore)
                .apply()
            Log.d(TAG, "License key saved successfully (encrypted: $isEncryptionSupported)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save license key", e)
        }
    }

    /**
     * Get saved license key
     * @return The saved license key or null if not found
     */
    fun getLicenseKey(): String? {
        return try {
            val storedData = sharedPreferences.getString(KEY_LICENSE_KEY, null)
            if (storedData != null && isEncryptionSupported) {
                decryptData(storedData) ?: run {
                    Log.w(TAG, "Decryption failed, returning raw data")
                    storedData
                }
            } else {
                storedData
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve license key", e)
            null
        }
    }

    /**
     * Clear saved license key
     */
    fun clearLicenseKey() {
        try {
            sharedPreferences.edit()
                .remove(KEY_LICENSE_KEY)
                .apply()
            Log.d(TAG, "License key cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear license key", e)
        }
    }

    /**
     * Set remember license preference
     * @param remember Whether to remember the license key
     */
    fun setRememberLicense(remember: Boolean) {
        try {
            sharedPreferences.edit()
                .putBoolean(KEY_REMEMBER_LICENSE, remember)
                .apply()
            Log.d(TAG, "Remember license preference set to: $remember")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set remember license preference", e)
        }
    }

    /**
     * Get remember license preference
     * @return True if license should be remembered, false otherwise
     */
    fun getRememberLicense(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_REMEMBER_LICENSE, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get remember license preference", e)
            false
        }
    }

    /**
     * Set auto login preference
     * @param autoLogin Whether to enable auto login
     */
    fun setAutoLogin(autoLogin: Boolean) {
        try {
            sharedPreferences.edit()
                .putBoolean(KEY_AUTO_LOGIN, autoLogin)
                .apply()
            Log.d(TAG, "Auto login preference set to: $autoLogin")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set auto login preference", e)
        }
    }

    /**
     * Get auto login preference
     * @return True if auto login is enabled, false otherwise
     */
    fun getAutoLogin(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_AUTO_LOGIN, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get auto login preference", e)
            false
        }
    }

    /**
     * Store Hardware ID securely for persistent device identification
     * @param hwid The hardware ID to store
     */
    fun storeHWID(hwid: String) {
        try {
            val dataToStore = if (isEncryptionSupported) {
                encryptData(hwid) ?: run {
                    Log.w(TAG, "HWID encryption failed, storing unencrypted data")
                    hwid
                }
            } else {
                hwid
            }

            sharedPreferences.edit()
                .putString(KEY_HWID, dataToStore)
                .apply()
            Log.d(TAG, "HWID stored successfully (encrypted: $isEncryptionSupported)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store HWID", e)
        }
    }

    /**
     * Get stored Hardware ID
     * @return The stored HWID or null if not found
     */
    fun getStoredHWID(): String? {
        return try {
            val storedData = sharedPreferences.getString(KEY_HWID, null)
            if (storedData != null && isEncryptionSupported) {
                decryptData(storedData) ?: run {
                    Log.w(TAG, "HWID decryption failed, returning raw data")
                    storedData
                }
            } else {
                storedData
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve stored HWID", e)
            null
        }
    }

    /**
     * Clear stored Hardware ID
     */
    fun clearStoredHWID() {
        try {
            sharedPreferences.edit()
                .remove(KEY_HWID)
                .apply()
            Log.d(TAG, "Stored HWID cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear stored HWID", e)
        }
    }

    // ==================== SESSION AND TOKEN MANAGEMENT ====================

    /**
     * Store session token securely
     * @param sessionToken The session token from KeyAuth
     * @param expiryTimeMillis When the token expires (System.currentTimeMillis() + duration)
     */
    fun storeSessionToken(sessionToken: String, expiryTimeMillis: Long = 0L) {
        try {
            val dataToStore = if (isEncryptionSupported) {
                encryptData(sessionToken) ?: run {
                    Log.w(TAG, "Session token encryption failed, storing unencrypted")
                    sessionToken
                }
            } else {
                sessionToken
            }

            sharedPreferences.edit()
                .putString(KEY_SESSION_TOKEN, dataToStore)
                .putLong(KEY_TOKEN_EXPIRY, expiryTimeMillis)
                .apply()
            Log.d(TAG, "Session token stored successfully (encrypted: $isEncryptionSupported)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store session token", e)
        }
    }

    /**
     * Get stored session token
     * @return The session token or null if not found or expired
     */
    fun getSessionToken(): String? {
        return try {
            // Check if token is expired
            val expiryTime = sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0L)
            if (expiryTime > 0L && System.currentTimeMillis() > expiryTime) {
                Log.d(TAG, "Session token expired, clearing stored token")
                clearSessionToken()
                return null
            }

            val storedData = sharedPreferences.getString(KEY_SESSION_TOKEN, null)
            if (storedData != null && isEncryptionSupported) {
                decryptData(storedData) ?: run {
                    Log.w(TAG, "Session token decryption failed, returning raw data")
                    storedData
                }
            } else {
                storedData
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve session token", e)
            null
        }
    }

    /**
     * Check if session token is valid (exists and not expired)
     */
    fun isSessionTokenValid(): Boolean {
        val token = getSessionToken()
        return !token.isNullOrEmpty()
    }

    /**
     * Get the token expiry time in milliseconds
     * @return The expiry time as timestamp or 0L if not set
     */
    fun getTokenExpiryTime(): Long {
        return try {
            sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve token expiry time", e)
            0L
        }
    }

    /**
     * Clear stored session token
     */
    fun clearSessionToken() {
        try {
            sharedPreferences.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .apply()
            Log.d(TAG, "Session token cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session token", e)
        }
    }

    /**
     * Store refresh token for session renewal
     */
    fun storeRefreshToken(refreshToken: String) {
        try {
            val dataToStore = if (isEncryptionSupported) {
                encryptData(refreshToken) ?: run {
                    Log.w(TAG, "Refresh token encryption failed, storing unencrypted")
                    refreshToken
                }
            } else {
                refreshToken
            }

            sharedPreferences.edit()
                .putString(KEY_REFRESH_TOKEN, dataToStore)
                .apply()
            Log.d(TAG, "Refresh token stored successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store refresh token", e)
        }
    }

    /**
     * Get stored refresh token
     */
    fun getRefreshToken(): String? {
        return try {
            val storedData = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
            if (storedData != null && isEncryptionSupported) {
                decryptData(storedData) ?: run {
                    Log.w(TAG, "Refresh token decryption failed, returning raw data")
                    storedData
                }
            } else {
                storedData
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve refresh token", e)
            null
        }
    }

    /**
     * Clear refresh token
     */
    fun clearRefreshToken() {
        try {
            sharedPreferences.edit()
                .remove(KEY_REFRESH_TOKEN)
                .apply()
            Log.d(TAG, "Refresh token cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear refresh token", e)
        }
    }

    // ==================== DEVICE REGISTRATION & LICENSE BINDING ====================

    /**
     * Mark device as registered with KeyAuth servers
     * @param hwid The HWID that was registered
     * @param licenseKey The license key bound to this device
     */
    fun setDeviceRegistered(hwid: String, licenseKey: String) {
        try {
            val encryptedLicense = if (isEncryptionSupported) {
                encryptData(licenseKey) ?: licenseKey
            } else {
                licenseKey
            }

            sharedPreferences.edit()
                .putBoolean(KEY_DEVICE_REGISTERED, true)
                .putString(KEY_LAST_AUTH_HWID, hwid)
                .putString(KEY_BOUND_LICENSE, encryptedLicense)
                .putLong("registration_timestamp", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Device marked as registered with HWID: ${hwid.take(8)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set device registration", e)
        }
    }

    /**
     * Check if device is registered
     */
    fun isDeviceRegistered(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_DEVICE_REGISTERED, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check device registration", e)
            false
        }
    }

    /**
     * Get the HWID that was last used for authentication
     */
    fun getLastAuthHWID(): String? {
        return try {
            sharedPreferences.getString(KEY_LAST_AUTH_HWID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last auth HWID", e)
            null
        }
    }

    /**
     * Get the license key bound to this device
     */
    fun getBoundLicenseKey(): String? {
        return try {
            val storedData = sharedPreferences.getString(KEY_BOUND_LICENSE, null)
            if (storedData != null && isEncryptionSupported) {
                decryptData(storedData) ?: storedData
            } else {
                storedData
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get bound license key", e)
            null
        }
    }

    /**
     * Set device trust level based on successful authentications
     * @param trustLevel 0=New, 1=Verified, 2=Trusted, 3=Highly Trusted
     */
    fun setDeviceTrustLevel(trustLevel: Int) {
        try {
            sharedPreferences.edit()
                .putInt(KEY_DEVICE_TRUST_LEVEL, trustLevel)
                .putLong("last_trust_update", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Device trust level set to: $trustLevel")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set device trust level", e)
        }
    }

    /**
     * Get device trust level
     */
    fun getDeviceTrustLevel(): Int {
        return try {
            sharedPreferences.getInt(KEY_DEVICE_TRUST_LEVEL, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device trust level", e)
            0
        }
    }

    /**
     * Check if HWID has changed since last authentication
     * @param currentHWID The current device HWID
     * @return true if HWID has changed, false if same or no previous HWID
     */
    fun hasHWIDChanged(currentHWID: String): Boolean {
        val lastHWID = getLastAuthHWID()
        return lastHWID != null && lastHWID != currentHWID
    }

    /**
     * Clear all device registration data
     */
    fun clearDeviceRegistration() {
        try {
            sharedPreferences.edit()
                .remove(KEY_DEVICE_REGISTERED)
                .remove(KEY_LAST_AUTH_HWID)
                .remove(KEY_BOUND_LICENSE)
                .remove(KEY_DEVICE_TRUST_LEVEL)
                .remove("registration_timestamp")
                .remove("last_trust_update")
                .apply()
            Log.d(TAG, "Device registration data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear device registration", e)
        }
    }

    /**
     * Clear all preferences (complete reset)
     */
    fun clearAll() {
        try {
            sharedPreferences.edit().clear().apply()
            Log.d(TAG, "All preferences cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all preferences", e)
        }
    }

    /**
     * Clear only authentication-related data (keep user preferences)
     */
    fun clearAuthenticationData() {
        try {
            sharedPreferences.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRY)
                .remove(KEY_DEVICE_REGISTERED)
                .remove(KEY_LAST_AUTH_HWID)
                .remove(KEY_BOUND_LICENSE)
                .remove("registration_timestamp")
                .remove("last_trust_update")
                .apply()
            Log.d(TAG, "Authentication data cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear authentication data", e)
        }
    }

    /**
     * Check if the preferences are encrypted
     * @return True if using encrypted storage, false if using fallback
     */
    fun isEncrypted(): Boolean {
        return isEncryptionSupported && !sharedPreferences.javaClass.simpleName.contains("fallback")
    }

    /**
     * Get storage info for debugging
     * @return String describing the storage type being used
     */
    fun getStorageInfo(): String {
        return when {
            isEncrypted() -> "Using Android Keystore encryption (API ${Build.VERSION.SDK_INT})"
            isEncryptionSupported -> "Using fallback unencrypted storage (Keystore initialization failed)"
            else -> "Using unencrypted storage (API < 23, Keystore not supported)"
        }
    }

    /**
     * Get encryption status details for debugging
     * @return Map containing detailed encryption status information
     */
    fun getEncryptionStatus(): Map<String, Any> {
        return mapOf(
            "isEncryptionSupported" to isEncryptionSupported,
            "isEncrypted" to isEncrypted(),
            "apiLevel" to Build.VERSION.SDK_INT,
            "preferencesName" to PREFS_NAME,
            "keyAlias" to KEY_ALIAS,
            "storageInfo" to getStorageInfo()
        )
    }
}
