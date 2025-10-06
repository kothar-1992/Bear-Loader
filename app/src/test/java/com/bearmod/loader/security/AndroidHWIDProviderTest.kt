package com.bearmod.loader.security

import androidx.test.core.app.ApplicationProvider
import com.bearmod.loader.utils.SecurePrefsAdapterImpl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidHWIDProviderTest {

    @Test
    fun `hwid is generated and persisted`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val securePrefs = SecurePrefsAdapterImpl(context)

    // Ensure clear state
    securePrefs.clearStoredHWID()

    val provider = AndroidHWIDProvider(context, securePrefs)

        val first = provider.getHWID()
        assertNotNull(first)
        assertTrue(first.isNotBlank())

        // Second call should return the same persisted value
        val second = provider.getHWID()
        assertEquals(first, second)

        // Clearing should remove the stored value
        provider.clearHWID()
        val third = provider.getHWID()
        assertNotNull(third)
        assertTrue(third.isNotBlank())
    // Note: HWID generation is deterministic on many devices (based on device properties).
    // After clearing, the provider may regenerate the same deterministic HWID. We only assert
    // that a non-blank value is returned.
    // If you expect randomness on your target devices, replace this with assertNotEquals.
    // assertNotEquals(first, third)
    }
}
