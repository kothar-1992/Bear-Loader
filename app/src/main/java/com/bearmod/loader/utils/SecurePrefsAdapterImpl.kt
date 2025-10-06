package com.bearmod.loader.utils

import android.content.Context
import com.bearmod.loader.session.SessionStore

/**
 * Concrete adapter that delegates to the existing `SecurePreferences` implementation.
 *
 * Notes:
 * - This preserves the current keystore-backed behavior and fallback to unencrypted
 *   prefs if Keystore initialization fails. The adapter only forwards calls and does
 *   not change storage formats.
 * - Additionally implements `SessionStore` so it can be passed to services that
 *   expect session-related operations (e.g., SessionService) without creating a
 *   separate SecurePreferences instance.
 */
class SecurePrefsAdapterImpl(context: Context) : SecurePrefsAdapter, SessionStore {
    private val impl = SecurePreferences(context)

    override fun setRememberLicense(rem: Boolean) = impl.setRememberLicense(rem)
    override fun getRememberLicense(): Boolean = impl.getRememberLicense()
    override fun setAutoLogin(value: Boolean) = impl.setAutoLogin(value)
    override fun getAutoLogin(): Boolean = impl.getAutoLogin()
    override fun saveLicenseKey(key: String) = impl.saveLicenseKey(key)
    override fun getLicenseKey(): String? = impl.getLicenseKey()
    override fun clearLicenseKey() = impl.clearLicenseKey()
    override fun clearAuthenticationData() = impl.clearAuthenticationData()

    // Session / token helpers
    override fun storeSessionToken(sessionToken: String, expiryTimeMillis: Long) = impl.storeSessionToken(sessionToken, expiryTimeMillis)
    override fun getSessionToken(): String? = impl.getSessionToken()
    override fun clearSessionToken() = impl.clearSessionToken()
    override fun isDeviceRegistered(): Boolean = impl.isDeviceRegistered()
    override fun clearDeviceRegistration() = impl.clearDeviceRegistration()

    // Additional session-related helpers (kept for adapter consumers)
    override fun getTokenExpiryTime(): Long = impl.getTokenExpiryTime()
    override fun isSessionTokenValid(): Boolean = impl.isSessionTokenValid()

    override fun storeRefreshToken(refreshToken: String) = impl.storeRefreshToken(refreshToken)
    override fun getRefreshToken(): String? = impl.getRefreshToken()
    override fun clearRefreshToken() = impl.clearRefreshToken()

    // Device / license binding
    override fun setDeviceRegistered(hwid: String, licenseKey: String) = impl.setDeviceRegistered(hwid, licenseKey)
    override fun getLastAuthHWID(): String? = impl.getLastAuthHWID()
    override fun getBoundLicenseKey(): String? = impl.getBoundLicenseKey()
    override fun getDeviceTrustLevel(): Int = impl.getDeviceTrustLevel()
    override fun setDeviceTrustLevel(level: Int) = impl.setDeviceTrustLevel(level)

    // HWID helpers
    override fun storeHWID(hwid: String) = impl.storeHWID(hwid)
    override fun getStoredHWID(): String? = impl.getStoredHWID()
    override fun clearStoredHWID() = impl.clearStoredHWID()

    // Misc
    override fun clearAll() = impl.clearAll()
}
