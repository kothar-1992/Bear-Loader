package com.bearmod.loader.data.storage

/**
 * Minimal abstraction for KeyAuth session and device-related persistent storage.
 * This interface is intentionally small and focused on the operations used by
 * KeyAuthRepository so we can migrate storage usage incrementally and safely.
 */
interface KeyAuthSessionStore {
    fun getSessionToken(): String?
    fun storeSessionToken(sessionToken: String, expiryTimeMillis: Long)
    fun clearSessionToken()
    fun isSessionTokenValid(): Boolean

    fun getRefreshToken(): String?
    fun storeRefreshToken(refreshToken: String)

    fun isDeviceRegistered(): Boolean
    fun setDeviceRegistered(hwid: String, licenseKey: String)
    fun clearDeviceRegistration()
    fun getLastAuthHWID(): String?
    fun getBoundLicenseKey(): String?

    fun getDeviceTrustLevel(): Int
    fun setDeviceTrustLevel(level: Int)

    fun getStoredHWID(): String?
    fun storeHWID(hwid: String)

    fun clearAuthenticationData()
    fun getTokenExpiryTime(): Long
}
