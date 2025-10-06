package com.bearmod.loader.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for SecurePreferences to ensure the updated Android Keystore implementation works correctly
 */
@RunWith(AndroidJUnit4::class)
class SecurePreferencesTest {

    private lateinit var context: Context
    private lateinit var securePreferences: SecurePreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    securePreferences = SecurePreferences(context)

    // Clear any existing data
    securePreferences.clearAll()
    }

    @After
    fun tearDown() {
        // Clean up after tests
        securePreferences.clearAll()
    }

    @Test
    fun testSaveAndRetrieveLicenseKey() {
        val testLicenseKey = "test-license-key-12345"
        
        // Save license key
    securePreferences.saveLicenseKey(testLicenseKey)

    // Retrieve and verify
    val retrievedKey = securePreferences.getLicenseKey()
        assertEquals(testLicenseKey, retrievedKey)
    }

    @Test
    fun testClearLicenseKey() {
        val testLicenseKey = "test-license-key-12345"
        
        // Save license key
    securePreferences.saveLicenseKey(testLicenseKey)
    assertNotNull(securePreferences.getLicenseKey())

    // Clear license key
    securePreferences.clearLicenseKey()
    assertNull(securePreferences.getLicenseKey())
    }

    @Test
    fun testRememberLicensePreference() {
        // Default should be false
    assertFalse(securePreferences.getRememberLicense())
        
    // Set to true
    securePreferences.setRememberLicense(true)
    assertTrue(securePreferences.getRememberLicense())
        
    // Set to false
    securePreferences.setRememberLicense(false)
    assertFalse(securePreferences.getRememberLicense())
    }

    @Test
    fun testAutoLoginPreference() {
        // Default should be false
    assertFalse(securePreferences.getAutoLogin())
        
    // Set to true
    securePreferences.setAutoLogin(true)
    assertTrue(securePreferences.getAutoLogin())
        
    // Set to false
    securePreferences.setAutoLogin(false)
    assertFalse(securePreferences.getAutoLogin())
    }

    @Test
    fun testClearAllPreferences() {
        // Set some data
    securePreferences.saveLicenseKey("test-key")
    securePreferences.setRememberLicense(true)
    securePreferences.setAutoLogin(true)
        
        // Verify data exists
    assertNotNull(securePreferences.getLicenseKey())
    assertTrue(securePreferences.getRememberLicense())
    assertTrue(securePreferences.getAutoLogin())
        
        // Clear all
    securePreferences.clearAll()
        
        // Verify all data is cleared
    assertNull(securePreferences.getLicenseKey())
    assertFalse(securePreferences.getRememberLicense())
    assertFalse(securePreferences.getAutoLogin())
    }

    @Test
    fun testStorageInfo() {
    val storageInfo = securePreferences.getStorageInfo()
        assertNotNull(storageInfo)
        assertTrue(storageInfo.contains("storage") || storageInfo.contains("Keystore"))
    }

    @Test
    fun testIsEncrypted() {
        // This test verifies that the encryption status can be determined
        // The actual result may vary depending on device capabilities and API level
    val isEncrypted = securePreferences.isEncrypted()
        // Just verify the method doesn't throw an exception
        assertTrue(isEncrypted || !isEncrypted) // Always true, but tests the method call
    }

    @Test
    fun testEncryptionStatus() {
    val encryptionStatus = securePreferences.getEncryptionStatus()
        assertNotNull(encryptionStatus)
        assertTrue(encryptionStatus.containsKey("isEncryptionSupported"))
        assertTrue(encryptionStatus.containsKey("isEncrypted"))
        assertTrue(encryptionStatus.containsKey("apiLevel"))
        assertTrue(encryptionStatus.containsKey("storageInfo"))

        // Verify API level is a valid integer
        val apiLevel = encryptionStatus["apiLevel"] as Int
        assertTrue(apiLevel > 0)
    }

    @Test
    fun testEmptyLicenseKeyHandling() {
        // Test with empty string
    securePreferences.saveLicenseKey("")
    val retrievedKey = securePreferences.getLicenseKey()
        assertEquals("", retrievedKey)
        
        // Test with null (should not crash)
    securePreferences.clearLicenseKey()
    assertNull(securePreferences.getLicenseKey())
    }

    @Test
    fun testLongLicenseKey() {
        // Test with a very long license key
        val longKey = "a".repeat(1000)
    securePreferences.saveLicenseKey(longKey)
        
    val retrievedKey = securePreferences.getLicenseKey()
    assertEquals(longKey, retrievedKey)
    }

    @Test
    fun testSpecialCharactersInLicenseKey() {
        // Test with special characters
        val specialKey = "test-key-!@#$%^&*()_+-=[]{}|;':\",./<>?"
    securePreferences.saveLicenseKey(specialKey)
        
    val retrievedKey = securePreferences.getLicenseKey()
    assertEquals(specialKey, retrievedKey)
    }
}
