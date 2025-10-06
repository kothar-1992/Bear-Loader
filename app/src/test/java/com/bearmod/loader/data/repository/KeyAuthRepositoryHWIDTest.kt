package com.bearmod.loader.data.repository

import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.bearmod.loader.data.api.KeyAuthApiService
import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.security.HWIDProvider
import com.bearmod.loader.utils.SecurePrefsAdapter
import com.bearmod.loader.utils.SecurePrefsAdapterImpl
import com.bearmod.loader.utils.NetworkResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.anyOrNull
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
class KeyAuthRepositoryHWIDTest {

    private lateinit var apiService: KeyAuthApiService
    private lateinit var hwidProvider: HWIDProvider
    private lateinit var securePreferences: SecurePrefsAdapter
    private lateinit var repository: KeyAuthRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    securePreferences = SecurePrefsAdapterImpl(context)

        apiService = mock()
        hwidProvider = mock()

        repository = KeyAuthRepository(apiService, context, enableLogging = false, hwidProvider = hwidProvider)
    }

    @Test
    fun `restoreSession returns HWIDMismatch when HWID differs`() = runBlocking {
        // Prepare stored values
        securePreferences.clearAll()
        securePreferences.storeSessionToken("stored-session", System.currentTimeMillis() + 3600000)
        securePreferences.setDeviceRegistered("old-hwid", "license")
        securePreferences.storeHWID("old-hwid")

        // HWID provider returns a different HWID
    whenever(hwidProvider.getHWID()).doReturn("new-hwid")

        val result = repository.restoreSession()

        assertEquals(true, result is com.bearmod.loader.data.model.SessionRestoreResult.HWIDMismatch)
    }

    @Test
    fun `restoreSession succeeds when HWID matches and session valid`() = runBlocking {
        securePreferences.clearAll()
        securePreferences.storeSessionToken("stored-session", System.currentTimeMillis() + 3600000)
        securePreferences.setDeviceRegistered("the-hwid", "license")
        securePreferences.storeHWID("the-hwid")

    whenever(hwidProvider.getHWID()).doReturn("the-hwid")

        // Mock init and checkSession API calls used during restore
        val successResp = KeyAuthResponse(success = true, message = "ok", sessionId = "stored-session")
    whenever(apiService.init(any<String>(), any<String>(), any<String>(), any<String>(), anyOrNull())).doReturn(Response.success(successResp))
    whenever(apiService.checkSession(any<String>(), any<String>(), any<String>(), any<String>())).doReturn(Response.success(successResp))

        val result = repository.restoreSession()
        assertTrue(result is com.bearmod.loader.data.model.SessionRestoreResult.Success)
    }

    @Test
    fun `attemptHWIDBasedAuth authenticates with bound license`() = runBlocking {
        securePreferences.clearAll()
        // store a bound license
        securePreferences.setDeviceRegistered("the-hwid", "licenseKey123")
        securePreferences.storeHWID("the-hwid")

    whenever(hwidProvider.getHWID()).doReturn("the-hwid")

        val authResp = KeyAuthResponse(success = true, message = "ok", sessionId = "new-session")
    whenever(apiService.init(any<String>(), any<String>(), any<String>(), any<String>(), anyOrNull())).doReturn(Response.success(authResp))
    whenever(apiService.license(any<String>(), any<String>(), any<String>(), any<String>(), any<String>(), any<String>())).doReturn(Response.success(authResp))

        val result = repository.attemptHWIDBasedAuth()
        assertTrue(result is NetworkResult.Success)
        assertEquals("new-session", (result as NetworkResult.Success).data.sessionId)
    }
}
