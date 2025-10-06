package com.bearmod.loader.data.repository

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.bearmod.loader.data.api.KeyAuthApiService
import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.security.HWIDProvider
import com.bearmod.loader.utils.SecurePrefsAdapter
import com.bearmod.loader.data.storage.KeyAuthSessionStoreImpl
import com.bearmod.loader.utils.NetworkResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.eq
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class KeyAuthRepositorySessionStoreTest {

    private lateinit var apiService: KeyAuthApiService
    private lateinit var hwidProvider: HWIDProvider
    private lateinit var adapter: SecurePrefsAdapter
    private lateinit var store: KeyAuthSessionStoreImpl
    private lateinit var repository: KeyAuthRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        apiService = mock()
        hwidProvider = mock()

        adapter = mock()
        store = KeyAuthSessionStoreImpl(adapter)

        repository = KeyAuthRepository(apiService, context, enableLogging = false, hwidProvider = hwidProvider, sessionStore = store)
    }

    @Test
    fun `restoreSession returns HWIDMismatch when HWID differs using injected store`() = runBlocking {
        // Prepare mocked adapter values
        whenever(adapter.getSessionToken()).doReturn("stored-session")
        whenever(adapter.isSessionTokenValid()).doReturn(true)
        whenever(adapter.isDeviceRegistered()).doReturn(true)
        whenever(adapter.getLastAuthHWID()).doReturn("old-hwid")

        whenever(hwidProvider.getHWID()).doReturn("new-hwid")

        val result = repository.restoreSession()

        assertEquals(true, result is com.bearmod.loader.data.model.SessionRestoreResult.HWIDMismatch)
    }

    @Test
    fun `restoreSession succeeds when HWID matches and session valid using injected store`() = runBlocking {
        whenever(adapter.getSessionToken()).doReturn("stored-session")
        whenever(adapter.isSessionTokenValid()).doReturn(true)
        whenever(adapter.isDeviceRegistered()).doReturn(true)
        whenever(adapter.getLastAuthHWID()).doReturn("the-hwid")

        whenever(hwidProvider.getHWID()).doReturn("the-hwid")

        // Mock init and checkSession API calls used during restore
        val successResp = KeyAuthResponse(success = true, message = "ok", sessionId = "stored-session")
    whenever(apiService.init(any(), any(), any(), any(), any())).doReturn(Response.success(successResp))
    whenever(apiService.checkSession(any(), any(), any(), any())).doReturn(Response.success(successResp))

        val result = repository.restoreSession()
        assertTrue(result is com.bearmod.loader.data.model.SessionRestoreResult.Success)
    }

    @Test
    fun `attemptHWIDBasedAuth authenticates with bound license when using injected store`() = runBlocking {
        // store a bound license via adapter
        whenever(adapter.getBoundLicenseKey()).doReturn("licenseKey123")
        whenever(adapter.getStoredHWID()).doReturn("the-hwid")
        whenever(hwidProvider.getHWID()).doReturn("the-hwid")

        val authResp = KeyAuthResponse(success = true, message = "ok", sessionId = "new-session")
    whenever(apiService.init(any(), any(), any(), any(), any())).doReturn(Response.success(authResp))
        whenever(apiService.license(any(), any(), any(), any(), any(), any())).doReturn(Response.success(authResp))

        val result = repository.attemptHWIDBasedAuth()
        assertTrue(result is NetworkResult.Success)
        assertEquals("new-session", (result as NetworkResult.Success).data.sessionId)

        // Verify we stored session token and registration on adapter via the store
        verify(adapter).setDeviceRegistered(eq("the-hwid"), eq("licenseKey123"))
        verify(adapter).storeSessionToken(eq("new-session"), any())
    }
}
