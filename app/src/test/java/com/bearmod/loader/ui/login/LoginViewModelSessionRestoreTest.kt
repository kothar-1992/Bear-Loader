package com.bearmod.loader.ui.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bearmod.loader.data.model.AuthenticationState
import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.data.model.SessionRestoreResult
import com.bearmod.loader.data.repository.AuthRepository
import com.bearmod.loader.utils.NetworkResult
import com.bearmod.loader.utils.SecurePrefsAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginViewModelSessionRestoreTest {

    private val repository: AuthRepository = mock()
    private val prefsAdapter: SecurePrefsAdapter = mock()
    private lateinit var vm: LoginViewModel

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        vm = LoginViewModel(repository, prefsAdapter)
    }

    @Test
    fun `attemptSessionRestore success updates loginState`() = runBlocking {
        val authState = AuthenticationState(isAuthenticated = true, sessionToken = "tok123")
        whenever(repository.restoreSession()).thenReturn(SessionRestoreResult.Success(authState))

        vm.attemptSessionRestore()

        // Wait briefly for coroutine to complete (synchronous under Robolectric/InstantTaskExecutorRule)
        Thread.sleep(50)

        val state = vm.loginState.value
        assertTrue(state is NetworkResult.Success)
    }

    @Test
    fun `attemptSessionRestore hwid mismatch sets sessionRestoreState`() = runBlocking {
        whenever(repository.restoreSession()).thenReturn(SessionRestoreResult.HWIDMismatch)

        vm.attemptSessionRestore()
        Thread.sleep(50)

        val state = vm.sessionRestoreState.value
        assertTrue(state is SessionRestoreResult.HWIDMismatch)
    }

    @Test
    fun `attemptSessionRestore expired sets sessionRestoreState`() = runBlocking {
        whenever(repository.restoreSession()).thenReturn(SessionRestoreResult.SessionExpired)

        vm.attemptSessionRestore()
        Thread.sleep(50)

        val state = vm.sessionRestoreState.value
        assertTrue(state is SessionRestoreResult.SessionExpired)
    }
}
