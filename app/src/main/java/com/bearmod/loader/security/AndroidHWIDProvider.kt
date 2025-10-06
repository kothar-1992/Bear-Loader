package com.bearmod.loader.security

import android.content.Context
import android.os.Build
// HWID provider persists HWID via SecurePrefsAdapterImpl; no direct SecurePreferences import needed
import java.security.MessageDigest

/**
 * Default Android HWID provider.
 * Ported from the Java HWID sample: generates an MD5 hash from stable
 * device properties and returns the hex string. Persists the value via
 * SecurePreferences so it survives app reinstalls.
 */
class AndroidHWIDProvider(
    private val context: Context,
    private val securePreferencesAdapter: com.bearmod.loader.utils.SecurePrefsAdapter = com.bearmod.loader.utils.SecurePrefsAdapterImpl(context)
) : HWIDProvider {

    override fun getHWID(): String {
        // Prefer previously stored HWID
    val existing = securePreferencesAdapter.getStoredHWID()
        if (!existing.isNullOrBlank()) return existing

        // Generate HWID using stable device identifiers
        val hwidBytes = generateHWIDBytes()
        val hwidHex = bytesToHex(hwidBytes)

        // Persist generated HWID
        securePreferencesAdapter.storeHWID(hwidHex)
        return hwidHex
    }

    override fun clearHWID() {
        securePreferencesAdapter.clearStoredHWID()
    }

    private fun generateHWIDBytes(): ByteArray {
        return try {
            val md = MessageDigest.getInstance("MD5")

            val s = StringBuilder()
                .append(System.getProperty("os.name"))
                .append(System.getProperty("os.arch"))
                .append(System.getProperty("os.version"))
                .append(Runtime.getRuntime().availableProcessors())
                .append(Build.BOARD ?: "")
                .append(Build.BRAND ?: "")
                .append(Build.DEVICE ?: "")
                .append(Build.HARDWARE ?: "")
                .append(Build.MODEL ?: "")
                .append(Build.PRODUCT ?: "")

            md.digest(s.toString().toByteArray())
        } catch (t: Throwable) {
            // Fallback to a UUID-based HWID if digest fails
            val fallback = java.util.UUID.randomUUID().toString()
            fallback.toByteArray()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
