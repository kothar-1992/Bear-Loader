package com.bearmod.loader.data.storage

import com.bearmod.loader.utils.SecurePrefsAdapter
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.eq

class KeyAuthSessionStoreImplTest {

    @Test
    fun `delegates calls to underlying SecurePrefsAdapter`() {
        val adapter = mock<SecurePrefsAdapter>()
        val store = KeyAuthSessionStoreImpl(adapter)

        whenever(adapter.getSessionToken()).thenReturn("token-123")
        whenever(adapter.isSessionTokenValid()).thenReturn(true)
        whenever(adapter.getRefreshToken()).thenReturn("refresh-abc")
        whenever(adapter.isDeviceRegistered()).thenReturn(true)
        whenever(adapter.getLastAuthHWID()).thenReturn("hwid-1")
        whenever(adapter.getBoundLicenseKey()).thenReturn("lic-1")
        whenever(adapter.getDeviceTrustLevel()).thenReturn(2)
        whenever(adapter.getStoredHWID()).thenReturn("stored-hwid")
        whenever(adapter.getTokenExpiryTime()).thenReturn(12345L)

        // Read delegations
        assertEquals("token-123", store.getSessionToken())
        assertTrue(store.isSessionTokenValid())
        assertEquals("refresh-abc", store.getRefreshToken())
        assertTrue(store.isDeviceRegistered())
        assertEquals("hwid-1", store.getLastAuthHWID())
        assertEquals("lic-1", store.getBoundLicenseKey())
        assertEquals(2, store.getDeviceTrustLevel())
        assertEquals("stored-hwid", store.getStoredHWID())
        assertEquals(12345L, store.getTokenExpiryTime())

        // Write delegations - just call and verify adapter received them
        store.storeSessionToken("tok", 9999L)
        verify(adapter).storeSessionToken(eq("tok"), eq(9999L))

        store.clearSessionToken()
        verify(adapter).clearSessionToken()

        store.storeRefreshToken("r1")
        verify(adapter).storeRefreshToken(eq("r1"))

        store.setDeviceRegistered("hw", "lic")
        verify(adapter).setDeviceRegistered(eq("hw"), eq("lic"))

        store.clearDeviceRegistration()
        verify(adapter).clearDeviceRegistration()

        store.setDeviceTrustLevel(3)
        verify(adapter).setDeviceTrustLevel(eq(3))

        store.storeHWID("hw2")
        verify(adapter).storeHWID(eq("hw2"))

        store.clearAuthenticationData()
        verify(adapter).clearAuthenticationData()
    }
}
