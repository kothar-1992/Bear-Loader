package com.bearmod.loader.ui.login

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bearmod.loader.KeyAuthLoaderApplication
import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.data.model.SessionRestoreResult
import com.bearmod.loader.data.model.AuthFlowState
import com.bearmod.loader.data.repository.AuthRepository
import com.bearmod.loader.utils.SecurePrefsAdapter
import com.bearmod.loader.utils.NetworkResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the login screen
 */
class LoginViewModel(
    private val repository: AuthRepository,
    private val prefsAdapter: SecurePrefsAdapter
) : ViewModel() {
    
    private val _loginState = MutableLiveData<NetworkResult<KeyAuthResponse>?>()
    val loginState: LiveData<NetworkResult<KeyAuthResponse>?> = _loginState

    private val _initState = MutableLiveData<NetworkResult<KeyAuthResponse>?>()
    val initState: LiveData<NetworkResult<KeyAuthResponse>?> = _initState
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _sessionRestoreState = MutableLiveData<SessionRestoreResult?>()
    val sessionRestoreState: LiveData<SessionRestoreResult?> = _sessionRestoreState

    // Preference-backed UI state exposed to the Activity
    private val _rememberLicense = MutableLiveData<Boolean>()
    val rememberLicense: LiveData<Boolean> = _rememberLicense

    private val _autoLogin = MutableLiveData<Boolean>()
    val autoLogin: LiveData<Boolean> = _autoLogin

    private val _savedLicenseKey = MutableLiveData<String?>()
    val savedLicenseKey: LiveData<String?> = _savedLicenseKey

    // Expose auth flow state from repository
    val authFlowState: StateFlow<AuthFlowState> = repository.authFlowState
    
    /**
     * Initialize the KeyAuth application
     * Following KeyAuth C++ library v1.3 pattern: KeyAuthApp.init()
     *
     * CRITICAL: This MUST be called FIRST before any other KeyAuth functions
     * Implements: KeyAuthApp.init() -> check KeyAuthApp.response.success
     */
    fun initializeApp() {
        viewModelScope.launch {
            _isLoading.value = true
            _initState.value = NetworkResult.Loading()

            // Clear any previous session data (following C++ pattern)
            repository.clearSession()

            // Reset global state
            KeyAuthLoaderApplication.setKeyAuthGloballyInitialized(false)

            Log.d("LoginViewModel", "🔄 Starting KeyAuth initialization (C++ v1.3 pattern)...")

            // Call KeyAuth init (equivalent to KeyAuthApp.init() in C++)
            val result = repository.initialize()

            // Handle result following C++ pattern: if (!KeyAuthApp.response.success)
            when (result) {
                is NetworkResult.Success -> {
                    // Initialization successful - equivalent to KeyAuthApp.response.success == true
                    KeyAuthLoaderApplication.setKeyAuthGloballyInitialized(true)
                    Log.d("LoginViewModel", "✅ KeyAuth initialization successful - ready for authentication")
                }
                is NetworkResult.Error -> {
                    // Initialization failed - equivalent to KeyAuthApp.response.success == false
                    KeyAuthLoaderApplication.setKeyAuthGloballyInitialized(false)
                    Log.e("LoginViewModel", "❌ KeyAuth initialization failed: ${result.message}")

                    // Following C++ pattern: prevent further operations on init failure
                    // In C++: Environment.Exit(0) - in Android: prevent authentication
                }
                else -> {
                    KeyAuthLoaderApplication.setKeyAuthGloballyInitialized(false)
                }
            }

            _initState.value = result
            _isLoading.value = false
        }
    }
    
    /**
     * Authenticate with license key
     * Following KeyAuth C++ library v1.3 pattern: KeyAuthApp.license()
     *
     * CRITICAL: Requires successful initialization first
     * Must check KeyAuthApp.response.success == true from init() before calling
     */
    fun authenticateWithLicense(licenseKey: String) {
        if (licenseKey.isBlank()) {
            _loginState.value = NetworkResult.Error("Please enter a license key")
            return
        }

        // STRICT initialization check following C++ pattern
        if (!KeyAuthLoaderApplication.isKeyAuthGloballyInitialized() || !repository.isAppInitialized()) {
            Log.e("LoginViewModel", "❌ Authentication attempted without proper initialization")
            _loginState.value = NetworkResult.Error("Application not initialized. Please restart the app.")

            // Following C++ pattern: re-initialize if not properly initialized
            Log.d("LoginViewModel", "🔄 Attempting re-initialization...")
            initializeApp()
            return
        }

        Log.d("LoginViewModel", "🔐 Starting license authentication...")

        viewModelScope.launch {
            _isLoading.value = true
            _loginState.value = NetworkResult.Loading()

            val result = repository.authenticateWithLicense(licenseKey)

            // Log result for debugging
            when (result) {
                is NetworkResult.Success -> {
                    Log.d("LoginViewModel", "✅ License authentication successful")
                }
                is NetworkResult.Error -> {
                    Log.e("LoginViewModel", "❌ License authentication failed: ${result.message}")

                    // Check for session corruption errors
                    val errorMessage = result.message ?: ""
                    if (errorMessage.contains("session not found", ignoreCase = true) ||
                        errorMessage.contains("last code", ignoreCase = true) ||
                        errorMessage.contains("session expired", ignoreCase = true)) {
                        Log.w("LoginViewModel", "🚨 Session corruption detected, will force clean initialization on next attempt")
                    }
                }
                else -> {}
            }

            _loginState.value = result
            _isLoading.value = false
        }
    }
    
    /**
     * Validate license key format (basic validation)
     */
    fun validateLicenseKey(licenseKey: String): String? {
        return when {
            licenseKey.isBlank() -> "License key cannot be empty"
            licenseKey.length < 10 -> "License key is too short"
            else -> null
        }
    }
    
    /**
     * Check if KeyAuth application is initialized
     */
    fun isAppInitialized(): Boolean {
        return repository.isAppInitialized()
    }

    // ==================== Preference helpers ====================

    /**
     * Load preference values from the adapter and publish to LiveData for the UI.
     * This is synchronous and safe to call from the Activity during onCreate.
     */
    fun loadPreferences() {
        _rememberLicense.value = prefsAdapter.getRememberLicense()
        _autoLogin.value = prefsAdapter.getAutoLogin()
        _savedLicenseKey.value = prefsAdapter.getLicenseKey()
    }

    fun setRememberLicense(value: Boolean) {
        prefsAdapter.setRememberLicense(value)
        _rememberLicense.value = value
        if (!value) {
            prefsAdapter.clearLicenseKey()
            _savedLicenseKey.value = null
            // also clear auto-login when remember is disabled
            prefsAdapter.setAutoLogin(false)
            _autoLogin.value = false
        }
    }

    fun setAutoLogin(value: Boolean) {
        prefsAdapter.setAutoLogin(value)
        _autoLogin.value = value
        if (value && !prefsAdapter.getRememberLicense()) {
            // enabling auto-login should also enable remember license
            prefsAdapter.setRememberLicense(true)
            _rememberLicense.value = true
        }
    }

    fun saveLicenseKeyIfNeeded(key: String) {
        if (prefsAdapter.getRememberLicense()) {
            prefsAdapter.saveLicenseKey(key)
            _savedLicenseKey.value = key
        }
    }

    fun clearAuthenticationData() {
        prefsAdapter.clearAuthenticationData()
        _autoLogin.value = false
        _rememberLicense.value = false
        _savedLicenseKey.value = null
    }

    /**
     * Synchronous getters useful during Activity initialization decisions.
     */
    fun isAutoLoginEnabledSync(): Boolean = prefsAdapter.getAutoLogin()
    fun getSavedLicenseSync(): String? = prefsAdapter.getLicenseKey()
    fun getRememberLicenseSync(): Boolean = prefsAdapter.getRememberLicense()

    // ==================== ENHANCED SESSION MANAGEMENT ====================

    /**
     * Attempt to restore session from stored data
     * This enables automatic login without re-entering license keys
     */
    fun attemptSessionRestore() {
        viewModelScope.launch {
            _isLoading.value = true
            _sessionRestoreState.value = null

            Log.d("LoginViewModel", "🔄 Attempting session restoration...")

            val result = repository.restoreSession()

            when (result) {
                is SessionRestoreResult.Success -> {
                    Log.d("LoginViewModel", "✅ Session restored successfully")
                    _loginState.value = NetworkResult.Success(
                        KeyAuthResponse(
                            success = true,
                            message = "Session restored",
                            sessionId = result.authState.sessionToken,
                            userInfo = result.authState.userInfo
                        )
                    )
                }
                is SessionRestoreResult.NoStoredSession -> {
                    Log.d("LoginViewModel", "ℹ️ No stored session found")
                }
                is SessionRestoreResult.SessionExpired -> {
                    Log.d("LoginViewModel", "⏰ Stored session expired")
                }
                is SessionRestoreResult.HWIDMismatch -> {
                    Log.w("LoginViewModel", "⚠️ HWID mismatch detected")
                }
                is SessionRestoreResult.Failed -> {
                    Log.e("LoginViewModel", "❌ Session restoration failed: ${result.error}")
                }
            }

            _sessionRestoreState.value = result
            _isLoading.value = false
        }
    }

    /**
     * Check if auto-login is possible
     */
    fun canAutoLogin(): Boolean {
        // Combine repository capability and user's stored preference to decide
        // whether auto-login should be attempted. This centralizes pref access
        // in the ViewModel and removes the need for Activities to read prefs.
        return repository.canAutoLogin() && prefsAdapter.getAutoLogin()
    }

    /**
     * Attempt auto-login using stored credentials
     */
    fun attemptAutoLogin() {
        if (!canAutoLogin()) {
            Log.d("LoginViewModel", "❌ Auto-login not available")
            return
        }

        Log.d("LoginViewModel", "🔄 Attempting auto-login...")

        viewModelScope.launch {
            _isLoading.value = true

            // First try session restoration
            val restoreResult = repository.restoreSession()

            when (restoreResult) {
                is SessionRestoreResult.Success -> {
                    Log.d("LoginViewModel", "✅ Auto-login successful via session restoration")
                    _loginState.value = NetworkResult.Success(
                        KeyAuthResponse(
                            success = true,
                            message = "Auto-login successful",
                            sessionId = restoreResult.authState.sessionToken,
                            userInfo = restoreResult.authState.userInfo
                        )
                    )
                }
                else -> {
                    Log.d("LoginViewModel", "❌ Auto-login failed, user must authenticate manually")
                    _sessionRestoreState.value = restoreResult
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Initialize app with session restoration
     * Enhanced: Directly attempts session restoration which handles initialization internally
     */
    fun initializeWithSessionRestore() {
        viewModelScope.launch {
            _isLoading.value = true

            Log.d("LoginViewModel", "🔄 Starting enhanced session restoration...")

            // Directly attempt session restoration - it handles initialization internally
            attemptSessionRestore()
        }
    }

    /**
     * Logout and clear all session data
     */
    fun logout() {
        repository.logout()
        clearStates()
        Log.d("LoginViewModel", "🚪 User logged out")
    }

    /**
     * Clear any previous states
     */
    fun clearStates() {
        _loginState.value = null
        _initState.value = null
        _sessionRestoreState.value = null
        _isLoading.value = false
    }
}
