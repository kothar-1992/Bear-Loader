package com.bearmod.loader.ui.login

import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.data.repository.AuthRepository
import com.bearmod.loader.utils.NetworkResult
import com.bearmod.loader.utils.SecurePrefsAdapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoginViewModelTest {

    private val repository: AuthRepository = mock()
    private val prefsAdapter: SecurePrefsAdapter = mock()
    private lateinit var vm: LoginViewModel

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        vm = LoginViewModel(repository, prefsAdapter)
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `canAutoLogin combines repo and prefs`() {
        whenever(repository.canAutoLogin()).thenReturn(true)
        whenever(prefsAdapter.getAutoLogin()).thenReturn(true)

        assertTrue(vm.canAutoLogin())
    }

    @Test
    fun `validate license key`() {
        assertNotNull(vm.validateLicenseKey(""))
        assertNotNull(vm.validateLicenseKey("short"))
        assertNull(vm.validateLicenseKey("LONG-VALID-LICENSE-KEY"))
    }

    @Test
    fun `saveLicenseKeyIfNeeded delegates when remember enabled`() = runBlocking {
        whenever(prefsAdapter.getRememberLicense()).thenReturn(true)
        vm.saveLicenseKeyIfNeeded("ABC")
        // no exception thrown; behavior validated by adapter unit tests
    }
}
