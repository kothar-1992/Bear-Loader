package com.bearmod.loader.data.storage

import android.content.Context
import com.bearmod.loader.utils.SecurePrefsAdapter
import com.bearmod.loader.utils.SecurePrefsAdapterImpl
import com.bearmod.loader.utils.SecurePreferences

/**
 * Implementation of KeyAuthSessionStore that delegates to a SecurePrefsAdapter.
 * By default it constructs a SecurePrefsAdapterImpl which itself wraps SecurePreferences.
 */
class KeyAuthSessionStoreImpl(private val adapter: SecurePrefsAdapter) : KeyAuthSessionStore {

    constructor(context: Context) : this(SecurePrefsAdapterImpl(context))

    // Delegations
    override fun getSessionToken(): String? = adapter.getSessionToken()
    override fun storeSessionToken(sessionToken: String, expiryTimeMillis: Long) = adapter.storeSessionToken(sessionToken, expiryTimeMillis)
    override fun clearSessionToken() = adapter.clearSessionToken()
    override fun isSessionTokenValid(): Boolean = adapter.isSessionTokenValid()

    override fun getRefreshToken(): String? = adapter.getRefreshToken()
    override fun storeRefreshToken(refreshToken: String) = adapter.storeRefreshToken(refreshToken)

    override fun isDeviceRegistered(): Boolean = adapter.isDeviceRegistered()
    override fun setDeviceRegistered(hwid: String, licenseKey: String) = adapter.setDeviceRegistered(hwid, licenseKey)
    override fun clearDeviceRegistration() = adapter.clearDeviceRegistration()
    override fun getLastAuthHWID(): String? = adapter.getLastAuthHWID()
    override fun getBoundLicenseKey(): String? = adapter.getBoundLicenseKey()

    override fun getDeviceTrustLevel(): Int = adapter.getDeviceTrustLevel()
    override fun setDeviceTrustLevel(level: Int) = adapter.setDeviceTrustLevel(level)

    override fun getStoredHWID(): String? = adapter.getStoredHWID()
    override fun storeHWID(hwid: String) = adapter.storeHWID(hwid)

    override fun clearAuthenticationData() = adapter.clearAuthenticationData()
    override fun getTokenExpiryTime(): Long = adapter.getTokenExpiryTime()
}
