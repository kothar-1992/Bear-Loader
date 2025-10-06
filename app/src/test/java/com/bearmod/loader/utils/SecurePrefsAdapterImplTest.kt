package com.bearmod.loader.utils

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurePrefsAdapterImplTest {

    private lateinit var adapter: SecurePrefsAdapterImpl

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        adapter = SecurePrefsAdapterImpl(ctx)
        // Ensure clean state
        adapter.clearAuthenticationData()
        adapter.clearLicenseKey()
    }

    @Test
    fun `remember license and auto login flags`() {
        adapter.setRememberLicense(true)
        assertTrue(adapter.getRememberLicense())

        adapter.setAutoLogin(true)
        assertTrue(adapter.getAutoLogin())

        adapter.setAutoLogin(false)
        assertFalse(adapter.getAutoLogin())
    }

    @Test
    fun `save and retrieve license key`() {
        val key = "TEST-LICENSE-123"
        adapter.saveLicenseKey(key)
        assertEquals(key, adapter.getLicenseKey())

        adapter.clearLicenseKey()
        assertTrue(adapter.getLicenseKey().isNullOrEmpty())
    }

    @Test
    fun `clear authentication data resets flags`() {
        adapter.setRememberLicense(true)
        adapter.setAutoLogin(true)
        adapter.saveLicenseKey("k")

        adapter.clearAuthenticationData()

        assertFalse(adapter.getRememberLicense())
        assertFalse(adapter.getAutoLogin())
        assertTrue(adapter.getLicenseKey().isNullOrEmpty())
    }
}
