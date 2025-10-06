package com.bearmod.loader.utils

/**
 * Adapter interface to abstract secure preference operations.
 *
 * Rationale:
 * - Provides a small, testable surface for ViewModels and UI layers to access
 *   persisted settings without tightly coupling to Android Context or concrete
 *   keystore-backed implementations.
 * - Helps reduce duplicate direct usages of `SecurePreferences` across Activities
 *   and Fragments. Use the adapter in places where only getters/setters are
 *   required (Login screen, settings UI, tests).
 */
interface SecurePrefsAdapter {
    fun setRememberLicense(rem: Boolean)
    fun getRememberLicense(): Boolean
    fun setAutoLogin(value: Boolean)
    fun getAutoLogin(): Boolean
    fun saveLicenseKey(key: String)
    fun getLicenseKey(): String?
    fun clearLicenseKey()
    fun clearAuthenticationData()

    // Session / token helpers
    fun storeSessionToken(sessionToken: String, expiryTimeMillis: Long)
    fun getSessionToken(): String?
    fun clearSessionToken()
    fun getTokenExpiryTime(): Long
    fun isSessionTokenValid(): Boolean

    fun storeRefreshToken(refreshToken: String)
    fun getRefreshToken(): String?
    fun clearRefreshToken()

    // Device / license binding
    fun setDeviceRegistered(hwid: String, licenseKey: String)
    fun isDeviceRegistered(): Boolean
    fun clearDeviceRegistration()
    fun getLastAuthHWID(): String?
    fun getBoundLicenseKey(): String?
    fun getDeviceTrustLevel(): Int
    fun setDeviceTrustLevel(level: Int)

    // HWID helpers
    fun storeHWID(hwid: String)
    fun getStoredHWID(): String?
    fun clearStoredHWID()

    // Misc
    fun clearAll()
}
