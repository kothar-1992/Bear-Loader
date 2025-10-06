package com.bearmod.loader.ui.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.data.repository.AuthRepository
import com.bearmod.loader.utils.NetworkResult
import com.bearmod.loader.utils.SecurePrefsAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class LoginViewModelAuthTests {

    private val repository: AuthRepository = mock()
    private val prefsAdapter: SecurePrefsAdapter = mock()
    private lateinit var vm: LoginViewModel

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        // Ensure global KeyAuth initialization flag is set for tests
        com.bearmod.loader.KeyAuthLoaderApplication.setKeyAuthGloballyInitialized(true)
        vm = LoginViewModel(repository, prefsAdapter)
    }

    @After
    fun teardown() {
        com.bearmod.loader.KeyAuthLoaderApplication.setKeyAuthGloballyInitialized(false)
    }

    @Test
    fun `authenticateWithLicense success sets loginState success`() = runBlocking {
        // Mock successful init state on repository
    whenever(repository.isAppInitialized()).thenReturn(true)

        val resp = NetworkResult.Success(KeyAuthResponse(success = true, message = "ok", sessionId = "s1"))
        whenever(repository.authenticateWithLicense(any())).thenReturn(resp)

        vm.authenticateWithLicense("valid-license-123")
        Thread.sleep(50)

        val state = vm.loginState.value
        assertTrue(state is NetworkResult.Success)
    }

    @Test
    fun `authenticateWithLicense session not found triggers error handling`() = runBlocking {
    whenever(repository.isAppInitialized()).thenReturn(true)

        val err = NetworkResult.Error<KeyAuthResponse>("session not found")
        whenever(repository.authenticateWithLicense(any())).thenReturn(err)

        vm.authenticateWithLicense("valid-license-123")
        Thread.sleep(50)

        val state = vm.loginState.value
        assertTrue(state is NetworkResult.Error)
    }
}
