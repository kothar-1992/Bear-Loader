package com.bearmod.loader.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.bearmod.loader.config.KeyAuthConfig
import com.bearmod.loader.data.api.KeyAuthApiService
import com.bearmod.loader.data.model.KeyAuthResponse
import com.bearmod.loader.data.model.AuthenticationState
import com.bearmod.loader.data.model.SessionRestoreResult
import com.bearmod.loader.data.model.HWIDValidationResult
import com.bearmod.loader.data.model.AuthFlowState
import com.bearmod.loader.data.model.AuthError
import com.bearmod.loader.data.model.AuthErrorType
import com.bearmod.loader.utils.NetworkResult
import com.bearmod.loader.utils.SecurePreferences
import com.bearmod.loader.security.AndroidHWIDProvider
import com.bearmod.loader.security.HWIDProvider
import com.bearmod.loader.utils.SessionDebugger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response
import java.security.MessageDigest
import com.bearmod.loader.data.storage.KeyAuthSessionStore
/**
 * Repository for KeyAuth API operations
 */
class KeyAuthRepository(
    private val apiService: KeyAuthApiService,
    private val context: Context,
    private val enableLogging: Boolean = true,
    private val hwidProvider: HWIDProvider = AndroidHWIDProvider(context),
    // Optional session store for incremental migration. If provided, repository will
    // use it for session/device persistence. Otherwise falls back to legacy SecurePreferences.
    private val sessionStore: KeyAuthSessionStore? = null
) {

    // KeyAuth application configuration from config
    private val appName = KeyAuthConfig.APP_NAME
    private val ownerId = KeyAuthConfig.OWNER_ID
    private val version = KeyAuthConfig.APP_VERSION
    private val customHash = KeyAuthConfig.CUSTOM_HASH
    private val apiBaseUrl = KeyAuthConfig.API_BASE_URL

    // Secure preferences and a session service bridge for session/token operations
    private val securePreferences = SecurePreferences(context, hwidProvider = hwidProvider)

    // Small typed bridge so callers in this class can call clear/get methods without
    // anonymous-object typing issues. The bridge delegates to either the new
    // session store based SessionService or the legacy util SessionService.
    private interface SessionServiceBridge {
        fun clearSessionToken()
        fun clearCorruptedSession()
        fun getSessionToken(): String?
    }

    private val sessionService: SessionServiceBridge = if (sessionStore != null) {
        // Adapter to satisfy com.bearmod.loader.session.SessionStore expected by the
        // new SessionService implementation
        val sessionStoreAdapter = object : com.bearmod.loader.session.SessionStore {
            override fun getSessionToken(): String? = sessionStore.getSessionToken()
            override fun storeSessionToken(sessionToken: String, expiryTimeMillis: Long) = sessionStore.storeSessionToken(sessionToken, expiryTimeMillis)
            override fun clearSessionToken() = sessionStore.clearSessionToken()
            override fun isDeviceRegistered(): Boolean = sessionStore.isDeviceRegistered()
            override fun clearDeviceRegistration() = sessionStore.clearDeviceRegistration()
        }

        val legacyService = com.bearmod.loader.session.SessionService(sessionStoreAdapter)

        object : SessionServiceBridge {
            override fun clearSessionToken() {
                // Prefer clearing directly on the injected session store
                try {
                    sessionStore.clearSessionToken()
                } catch (e: Exception) {
                    // Fallback to legacy service if anything goes wrong
                    try { legacyService.clearCorruptedSession() } catch (_: Exception) {}
                }
            }

            override fun clearCorruptedSession() {
                try {
                    legacyService.clearCorruptedSession()
                } catch (e: Exception) {
                    // As a fallback, clear token directly
                    try { sessionStore.clearSessionToken() } catch (_: Exception) {}
                }
            }

            override fun getSessionToken(): String? = try { sessionStore.getSessionToken() } catch (e: Exception) { null }
        }
    } else {
        val utilService = com.bearmod.loader.utils.SessionService(context)
        object : SessionServiceBridge {
            override fun clearSessionToken() = try { utilService.clearSessionToken() } catch (_: Exception) {}
            override fun clearCorruptedSession() = try { utilService.clearSessionToken() } catch (_: Exception) {}
            override fun getSessionToken(): String? = try { securePreferences.getSessionToken() } catch (_: Exception) { null }
        }
    }

    private var sessionId: String? = null
    @Volatile
    private var isInitialized = false
    private val initializationLock = Any()

    // Enhanced authentication state management
    private val _authenticationState = MutableStateFlow(AuthenticationState())
    val authenticationState: StateFlow<AuthenticationState> = _authenticationState.asStateFlow()

    private val _authFlowState = MutableStateFlow(AuthFlowState.IDLE)
    val authFlowState: StateFlow<AuthFlowState> = _authFlowState.asStateFlow()

    private var currentHWID: String? = null
    
    /**
     * Initialize the KeyAuth application
     * Following KeyAuth C++ library v1.3 pattern: KeyAuthApp.init()
     *
     * CRITICAL: This MUST be called FIRST before any other KeyAuth functions
     * and success MUST be checked using KeyAuthApp.response.success pattern
     * Enhanced: Supports session preservation during restoration
     */
    suspend fun initialize(preserveSession: Boolean = false): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            // CRITICAL FIX: Always clear session state before initialization
            // KeyAuth API v1.3 init() must NEVER receive existing session tokens
            synchronized(initializationLock) {
                if (!preserveSession) {
                    sessionId = null
                }
                isInitialized = false
            }

            // Call KeyAuth init API (equivalent to KeyAuthApp.init() in C++)
            // Include custom hash for integrity checking
            if (enableLogging) {
                Log.d("KeyAuthRepository", "🔄 Calling KeyAuth API init() with parameters:")
                Log.d("KeyAuthRepository", "   - type: init")
                Log.d("KeyAuthRepository", "   - version: $version")
                Log.d("KeyAuthRepository", "   - name: $appName")
                Log.d("KeyAuthRepository", "   - ownerId: $ownerId")
                Log.d("KeyAuthRepository", "   - hash: ${customHash?.take(8)}...")
                Log.d("KeyAuthRepository", "   - preserveSession: $preserveSession")
                Log.d("KeyAuthRepository", "   - current sessionId: ${sessionId?.take(8) ?: "null"}")
                Log.d("KeyAuthRepository", "   - API endpoint: $apiBaseUrl")
            }

            val response = apiService.init(
                version = version,
                name = appName,
                ownerId = ownerId,
                hash = customHash
            )

            // Check response following C++ pattern: if (!KeyAuthApp.response.success)
            if (enableLogging) {
                Log.d("KeyAuthRepository", "📡 KeyAuth API response received:")
                Log.d("KeyAuthRepository", "   - HTTP Status: ${response.code()}")
                Log.d("KeyAuthRepository", "   - isSuccessful: ${response.isSuccessful}")
            }

            if (response.isSuccessful) {
                val body = response.body()

                if (enableLogging) {
                    Log.d("KeyAuthRepository", "📋 Response body:")
                    Log.d("KeyAuthRepository", "   - success: ${body?.success}")
                    Log.d("KeyAuthRepository", "   - message: ${body?.message}")
                    Log.d("KeyAuthRepository", "   - sessionId: ${body?.sessionId?.take(8) ?: "null"}")
                }

                // STRICT success checking following C++ library v1.3 pattern
                if (body != null && body.success == true) {
                    // Initialization successful - set state
                    synchronized(initializationLock) {
                        sessionId = body.sessionId
                        isInitialized = true
                    }

                    if (enableLogging) Log.d("KeyAuthRepository", "✅ KeyAuth initialization successful")
                    NetworkResult.Success(body)
                } else {
                    // Initialization failed - following C++ pattern behavior
                    val errorMessage = body?.message ?: "KeyAuth initialization failed"

                    // ENHANCED ERROR HANDLING: Check for specific KeyAuth errors
                    if (errorMessage.contains("session not found", ignoreCase = true) ||
                        errorMessage.contains("last code", ignoreCase = true)) {
                        if (enableLogging) {
                            Log.e("KeyAuthRepository", "🚨 CRITICAL: KeyAuth session error during init")
                            Log.e("KeyAuthRepository", "   This suggests session state corruption")
                            Log.e("KeyAuthRepository", "   Clearing all session data...")
                        }

                        // Clear all session data to force fresh authentication
                        clearSessionState()
                    }

                    // Ensure state remains uninitialized
                    synchronized(initializationLock) {
                        sessionId = null
                        isInitialized = false
                    }

                    if (enableLogging) Log.e("KeyAuthRepository", "❌ KeyAuth Init Failed: $errorMessage")
                    NetworkResult.Error("KeyAuth Init Failed: $errorMessage")
                }
            } else {
                // Network error - ensure state remains uninitialized
                synchronized(initializationLock) {
                    sessionId = null
                    isInitialized = false
                }

                // Enhanced error debugging for KeyAuth API issues
                val errorBody = try {
                    response.errorBody()?.string()
                } catch (e: Exception) {
                    "Unable to read error body: ${e.message}"
                }

                val errorMessage = "HTTP ${response.code()}: ${response.message()}"

                if (enableLogging) {
                    Log.e("KeyAuthRepository", "❌ KeyAuth Init HTTP Error: $errorMessage")
                    Log.e("KeyAuthRepository", "   - Error Body: $errorBody")
                    Log.e("KeyAuthRepository", "   - Request URL: ${response.raw().request.url}")

                    // Check if this is the "Token given" error
                    if (errorBody?.contains("Token", ignoreCase = true) == true) {
                        Log.e("KeyAuthRepository", "🚨 CRITICAL: KeyAuth API received unexpected token during init()")
                        Log.e("KeyAuthRepository", "   This suggests session state is interfering with initialization")
                        Log.e("KeyAuthRepository", "   Current sessionId state: ${sessionId?.take(8) ?: "null"}")
                    }
                }

                NetworkResult.Error("KeyAuth Init Failed: $errorMessage")
            }
        } catch (e: Exception) {
            // Connection error - ensure state remains uninitialized
            synchronized(initializationLock) {
                sessionId = null
                isInitialized = false
            }

            val errorMessage = "Connection error during initialization: ${e.message}"
            if (enableLogging) Log.e("KeyAuthRepository", "❌ $errorMessage")
            NetworkResult.Error(errorMessage)
        }
    }
    
    /**
     * Authenticate with license key
     * Following KeyAuth C++ library v1.3 pattern: KeyAuthApp.license()
     *
     * CRITICAL: Must be called AFTER successful initialization
     * Requires KeyAuthApp.response.success == true from init() first
     */
    suspend fun authenticateWithLicense(licenseKey: String): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            // STRICT initialization check following C++ pattern
            synchronized(initializationLock) {
                if (!isInitialized) {
                    if (enableLogging) Log.e("KeyAuthRepository", "❌ Authentication attempted without initialization")
                    return@withContext NetworkResult.Error("Application not initialized. Please restart the app.")
                }
            }

            val hwid = hwidProvider.getHWID()
            val currentSessionId = sessionId ?: run {
                if (enableLogging) Log.e("KeyAuthRepository", "❌ No session ID available after initialization")
                return@withContext NetworkResult.Error("No session ID available")
            }

            if (enableLogging) Log.d("KeyAuthRepository", "🔐 Attempting license authentication...")

            val response = apiService.license(
                licenseKey = licenseKey,
                hwid = hwid,
                sessionId = currentSessionId,
                name = appName,
                ownerId = ownerId
            )

            if (response.isSuccessful) {
                val body = response.body()

                // STRICT success checking following C++ pattern
                if (body != null && body.success == true) {
                    // Update session ID if provided
                    body.sessionId?.let {
                        synchronized(initializationLock) {
                            sessionId = it
                        }
                    }

                    if (enableLogging) Log.d("KeyAuthRepository", "✅ License authentication successful")

                    // Enhanced session persistence
                    handleSuccessfulAuthentication(licenseKey, hwid, body)

                    NetworkResult.Success(body)
                } else {
                    // Authentication failed - following C++ pattern
                    val errorMessage = body?.message ?: "License authentication failed"

                    // ENHANCED ERROR HANDLING: Check for specific KeyAuth errors
                    if (errorMessage.contains("session not found", ignoreCase = true) ||
                        errorMessage.contains("last code", ignoreCase = true)) {
                        if (enableLogging) {
                            Log.e("KeyAuthRepository", "🚨 CRITICAL: KeyAuth session error during authentication")
                            Log.e("KeyAuthRepository", "   Error: $errorMessage")
                            Log.e("KeyAuthRepository", "   This suggests session state corruption or invalid session ID")
                            Log.e("KeyAuthRepository", "   Clearing session state and forcing re-initialization...")
                        }

                        // Clear all session data and force re-initialization
                        clearSessionState()

                        // Return specific error message for session issues
                        return@withContext NetworkResult.Error("Session expired or invalid. Please restart the app and try again.")
                    }

                    if (enableLogging) Log.e("KeyAuthRepository", "❌ License Auth Failed: $errorMessage")
                    NetworkResult.Error(errorMessage)
                }
            } else {
                val errorMessage = "Network error during authentication: ${response.code()}"
                if (enableLogging) Log.e("KeyAuthRepository", "❌ $errorMessage")
                NetworkResult.Error(errorMessage)
            }
        } catch (e: Exception) {
            val errorMessage = "Connection error during authentication: ${e.message}"
            if (enableLogging) Log.e("KeyAuthRepository", "❌ $errorMessage")
            NetworkResult.Error(errorMessage)
        }
    }
    
    /**
     * Check if current session is valid
     * Enhanced with comprehensive debugging
     */
    suspend fun checkSession(): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val currentSessionId = sessionId ?: run {
                if (enableLogging) Log.e("KeyAuthRepository", "❌ Session check failed: No active session ID")
                return@withContext NetworkResult.Error("No active session")
            }

            if (enableLogging) {
                Log.d("KeyAuthRepository", "🔍 Checking session validity: ${currentSessionId.take(8)}...")
                Log.d("KeyAuthRepository", "📡 API call parameters:")
                Log.d("KeyAuthRepository", "   - sessionId: ${currentSessionId.take(8)}...")
                Log.d("KeyAuthRepository", "   - name: $appName")
                Log.d("KeyAuthRepository", "   - ownerId: $ownerId")
                Log.d("KeyAuthRepository", "   - endpoint: $apiBaseUrl")
            }

            val response = apiService.checkSession(
                sessionId = currentSessionId,
                name = appName,
                ownerId = ownerId
            )

            if (enableLogging) {
                Log.d("KeyAuthRepository", "📥 checkSession() API response:")
                Log.d("KeyAuthRepository", "   - HTTP Status: ${response.code()}")
                Log.d("KeyAuthRepository", "   - isSuccessful: ${response.isSuccessful}")
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) {
                    if (enableLogging) Log.d("KeyAuthRepository", "✅ Session validation successful")
                    NetworkResult.Success(body)
                } else {
                    val errorMsg = body?.message ?: "Session invalid"

                    // Check for specific KeyAuth session errors and clear corrupted session
                    if (errorMsg.contains("session not found", ignoreCase = true) ||
                        errorMsg.contains("last code", ignoreCase = true)) {
                        if (enableLogging) Log.w("KeyAuthRepository", "❌ Session not found error, clearing stored session...")
                        // Delegate stored-token clearing to SessionService
                        sessionService.clearSessionToken()

                        // Clear in-memory session ID
                        synchronized(initializationLock) {
                            sessionId = null
                        }

                        return@withContext NetworkResult.Error("Session expired or invalid. Please login again.")
                    }

                    if (enableLogging) Log.e("KeyAuthRepository", "❌ Session validation failed: $errorMsg")
                    NetworkResult.Error(errorMsg)
                }
            } else {
                val errorMsg = "Network error: ${response.code()}"
                if (enableLogging) Log.e("KeyAuthRepository", "❌ Session check network error: $errorMsg")
                NetworkResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Network error: ${e.message}"
            if (enableLogging) Log.e("KeyAuthRepository", "❌ Session check exception: $errorMsg", e)
            NetworkResult.Error(errorMsg)
        }
    }
    
    /**
     * Generate Hardware ID for device identification
     * Uses persistent device characteristics to ensure HWID remains consistent
     * across app reinstallations, preventing KeyAuth HWID reset requirements
     */
    private fun generateHWID(): String {
        // First, try to retrieve existing HWID from secure storage (prefer injected sessionStore)
        val existingHWID = sessionStore?.getStoredHWID() ?: securePreferences.getStoredHWID()
        if (existingHWID != null && existingHWID.isNotBlank()) {
            return existingHWID
        }

        // Generate new HWID using persistent device characteristics
        val hwid = generatePersistentHWID()

        // Store the generated HWID for future use
        if (sessionStore != null) {
            sessionStore.storeHWID(hwid)
        } else {
            securePreferences.storeHWID(hwid)
        }

        return hwid
    }

    /**
     * Generate a persistent HWID using device characteristics that survive app reinstallation
     * Combines multiple hardware identifiers to create a unique, stable device fingerprint
     */
    private fun generatePersistentHWID(): String {
        try {
            // Use multiple persistent device identifiers
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

            // Hardware-based identifiers that persist across installations
            val manufacturer = Build.MANUFACTURER ?: "unknown"
            val model = Build.MODEL ?: "unknown"
            val device = Build.DEVICE ?: "unknown"
            val board = Build.BOARD ?: "unknown"
            val brand = Build.BRAND ?: "unknown"
            val hardware = Build.HARDWARE ?: "unknown"

            // CPU architecture info (persistent) - minSdk is 24, so always use SUPPORTED_ABIS
            val cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull() ?: "unknown"

            // Screen characteristics (generally persistent)
            val displayMetrics = context.resources.displayMetrics
            val screenDensity = displayMetrics.densityDpi.toString()
            val screenWidth = displayMetrics.widthPixels.toString()
            val screenHeight = displayMetrics.heightPixels.toString()

            // Combine all persistent identifiers
            val deviceFingerprint = listOf(
                manufacturer,
                model,
                device,
                board,
                brand,
                hardware,
                cpuAbi,
                screenDensity,
                screenWidth,
                screenHeight,
                androidId
            ).joinToString("-")

            // Generate SHA-256 hash of the combined fingerprint
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceFingerprint.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }

        } catch (e: Exception) {
            // Fallback to a simpler but still persistent method
            val fallbackInfo = "${Build.MANUFACTURER}-${Build.MODEL}-${Build.DEVICE}"
            return fallbackInfo.hashCode().toString()
        }
    }
    
    /**
     * Get current session ID
     */
    fun getSessionId(): String? = sessionId
    
    /**
     * Check if application is initialized (thread-safe)
     */
    fun isAppInitialized(): Boolean {
        synchronized(initializationLock) {
            return isInitialized
        }
    }

    // ==================== ENHANCED SESSION MANAGEMENT ====================

    /**
     * Attempt to restore session from stored data
     * This enables automatic login without re-entering license keys
     * Enhanced with comprehensive debugging
     */
    suspend fun restoreSession(): SessionRestoreResult = withContext(Dispatchers.IO) {
        try {
            _authFlowState.value = AuthFlowState.CHECKING_STORED_SESSION

            if (enableLogging) {
                Log.d("KeyAuthRepository", "🔄 Attempting session restoration...")
                SessionDebugger.logSessionRestoreAttempt(context)
            }

            // Check if we have stored session data (prefer injected sessionStore during migration)
            val storedToken = sessionStore?.getSessionToken() ?: securePreferences.getSessionToken()
            if (storedToken.isNullOrEmpty()) {
                if (enableLogging) {
                    Log.d("KeyAuthRepository", "❌ No stored session token found")
                    SessionDebugger.logSessionRestoreResult(context, false, "No stored session token")
                }
                _authFlowState.value = AuthFlowState.IDLE
                return@withContext SessionRestoreResult.NoStoredSession
            }

                // Enhanced logging for session token details
                if (enableLogging) {
                    Log.d("KeyAuthRepository", "📦 Using stored session token: ${storedToken.take(8)}...")
                    Log.d("KeyAuthRepository", "⏳ Token valid: ${sessionStore?.isSessionTokenValid() ?: securePreferences.isSessionTokenValid()}")
                    Log.d("KeyAuthRepository", "📱 Device registered: ${sessionStore?.isDeviceRegistered() ?: securePreferences.isDeviceRegistered()}")
                    Log.d("KeyAuthRepository", "🔑 Trust level: ${sessionStore?.getDeviceTrustLevel() ?: securePreferences.getDeviceTrustLevel()}")
                }

            // Check if device is registered
            if (!(sessionStore?.isDeviceRegistered() ?: securePreferences.isDeviceRegistered())) {
                if (enableLogging) Log.d("KeyAuthRepository", "❌ Device not registered")
                _authFlowState.value = AuthFlowState.IDLE
                return@withContext SessionRestoreResult.NoStoredSession
            }

            // Validate HWID consistency
            val currentHwid = hwidProvider.getHWID()
            val lastAuthHwid = sessionStore?.getLastAuthHWID() ?: securePreferences.getLastAuthHWID()

            if (lastAuthHwid != null && lastAuthHwid != currentHwid) {
                if (enableLogging) Log.w("KeyAuthRepository", "⚠️ HWID mismatch detected")
                _authFlowState.value = AuthFlowState.HWID_MISMATCH
                return@withContext SessionRestoreResult.HWIDMismatch
            }

            // Check if session token is expired
            if (!(sessionStore?.isSessionTokenValid() ?: securePreferences.isSessionTokenValid())) {
                if (enableLogging) Log.d("KeyAuthRepository", "⏰ Session token expired")

                // Try to refresh token if available
                val refreshResult = attemptTokenRefresh()
                if (refreshResult is NetworkResult.Success) {
                    if (enableLogging) Log.d("KeyAuthRepository", "✅ Token refreshed successfully")
                    return@withContext createSessionRestoreSuccess()
                } else {
                    if (enableLogging) Log.d("KeyAuthRepository", "❌ Token refresh failed")
                    _authFlowState.value = AuthFlowState.SESSION_EXPIRED
                    return@withContext SessionRestoreResult.SessionExpired
                }
            }

            // CRITICAL FIX: Always perform CLEAN initialization first
            // KeyAuth API v1.3 init() should NEVER receive session tokens
            if (!isAppInitialized()) {
                if (enableLogging) Log.d("KeyAuthRepository", "🔄 Performing clean initialization for session restoration...")

                // Perform clean initialization (no session preservation)
                // This ensures KeyAuth API receives proper init() call without tokens
                val initResult = initialize(preserveSession = false)
                if (initResult !is NetworkResult.Success) {
                    if (enableLogging) Log.e("KeyAuthRepository", "❌ Clean initialization failed during session restore")
                    _authFlowState.value = AuthFlowState.FAILED
                    return@withContext SessionRestoreResult.Failed("Initialization failed")
                }

                if (enableLogging) Log.d("KeyAuthRepository", "✅ Clean initialization successful, now validating stored session...")

                // AFTER successful initialization, set the stored session for validation
                synchronized(initializationLock) {
                    sessionId = storedToken
                }

                if (enableLogging) {
                    Log.d("KeyAuthRepository", "🔄 Session ID set for validation: ${storedToken.take(8)}...")
                    Log.d("KeyAuthRepository", "📡 About to call checkSession() API...")
                }
            }

            // Validate session with server
            val sessionCheckResult = checkSession()
            when (sessionCheckResult) {
                is NetworkResult.Success -> {
                    if (enableLogging) Log.d("KeyAuthRepository", "✅ Session restored successfully")

                    // Update trust level
                    val currentTrust = sessionStore?.getDeviceTrustLevel() ?: securePreferences.getDeviceTrustLevel()
                    if (sessionStore != null) {
                        sessionStore.setDeviceTrustLevel(minOf(currentTrust + 1, 3))
                    } else {
                        securePreferences.setDeviceTrustLevel(minOf(currentTrust + 1, 3))
                    }

                    _authFlowState.value = AuthFlowState.AUTHENTICATED
                    return@withContext createSessionRestoreSuccess()
                }
                is NetworkResult.Error -> {
                    if (enableLogging) Log.w("KeyAuthRepository", "❌ Invalid session detected, clearing stored session...")
                        sessionService.clearSessionToken()

                    // Also clear the in-memory session ID
                    synchronized(initializationLock) {
                        sessionId = null
                    }

                    // Try HWID-based authentication as fallback
                    val hwidAuthResult = attemptHWIDBasedAuth()
                    if (hwidAuthResult is NetworkResult.Success) {
                        return@withContext createSessionRestoreSuccess()
                    }

                    _authFlowState.value = AuthFlowState.FAILED
                    return@withContext SessionRestoreResult.Failed(sessionCheckResult.message ?: "Session validation failed")
                }
                else -> {
                    _authFlowState.value = AuthFlowState.FAILED
                    return@withContext SessionRestoreResult.Failed("Unknown session validation error")
                }
            }

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ Session restoration failed", e)
            _authFlowState.value = AuthFlowState.FAILED
            return@withContext SessionRestoreResult.Failed("Session restoration error: ${e.message}")
        }
    }

    /**
     * Create successful session restore result
     */
    private fun createSessionRestoreSuccess(): SessionRestoreResult {
        val authState = AuthenticationState(
            isAuthenticated = true,
            sessionToken = sessionStore?.getSessionToken() ?: securePreferences.getSessionToken(),
            refreshToken = sessionStore?.getRefreshToken() ?: securePreferences.getRefreshToken(),
            hwid = currentHWID,
            licenseKey = sessionStore?.getBoundLicenseKey() ?: securePreferences.getBoundLicenseKey(),
            deviceTrustLevel = sessionStore?.getDeviceTrustLevel() ?: securePreferences.getDeviceTrustLevel(),
            isDeviceRegistered = sessionStore?.isDeviceRegistered() ?: securePreferences.isDeviceRegistered()
        )

        _authenticationState.value = authState
        return SessionRestoreResult.Success(authState)
    }

    private suspend fun attemptTokenRefresh(): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            _authFlowState.value = AuthFlowState.REFRESHING_TOKEN
            val refreshToken = sessionStore?.getRefreshToken() ?: securePreferences.getRefreshToken()
            if (refreshToken.isNullOrEmpty()) {
                if (enableLogging) Log.d("KeyAuthRepository", "❌ No refresh token available")
                return@withContext NetworkResult.Error("No refresh token available")
            }

            // Note: KeyAuth API v1.3 doesn't have native refresh token support
            // This is a placeholder for future enhancement or custom implementation
            // For now, we'll attempt HWID-based re-authentication

            if (enableLogging) Log.d("KeyAuthRepository", "🔄 Attempting HWID-based token refresh...")
            return@withContext attemptHWIDBasedAuth()

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ Token refresh failed", e)
            return@withContext NetworkResult.Error("Token refresh failed: ${e.message}")
        }
    }

    /**
     * Attempt HWID-based authentication using stored license key
     */
    internal suspend fun attemptHWIDBasedAuth(): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            _authFlowState.value = AuthFlowState.AUTHENTICATING_WITH_LICENSE

            val boundLicense = sessionStore?.getBoundLicenseKey() ?: securePreferences.getBoundLicenseKey()
            if (boundLicense.isNullOrEmpty()) {
                if (enableLogging) Log.d("KeyAuthRepository", "❌ No bound license key found")
                return@withContext NetworkResult.Error("No bound license key")
            }

            val hwid = hwidProvider.getHWID()
            if (enableLogging) Log.d("KeyAuthRepository", "🔐 Attempting HWID-based authentication...")

            // Ensure clean initialization for HWID-based authentication
            if (!isAppInitialized()) {
                if (enableLogging) Log.d("KeyAuthRepository", "🔄 Performing clean initialization for HWID auth...")

                val initResult = initialize(preserveSession = false)
                if (initResult !is NetworkResult.Success) {
                    return@withContext NetworkResult.Error("Initialization failed")
                }
            }

            // Authenticate with stored license
            val authResult = authenticateWithLicense(boundLicense)

            when (authResult) {
                is NetworkResult.Success -> {
                    if (enableLogging) Log.d("KeyAuthRepository", "✅ HWID-based authentication successful")

                    // Persistence (storing session token / device registration) is handled
                    // by authenticateWithLicense -> handleSuccessfulAuthentication(), so
                    // avoid duplicating those calls here to prevent double-writes.
                    return@withContext authResult
                }
                is NetworkResult.Error -> {
                    if (enableLogging) Log.e("KeyAuthRepository", "❌ HWID-based authentication failed: ${authResult.message}")
                    return@withContext authResult
                }
                else -> {
                    return@withContext NetworkResult.Error("Unknown authentication error")
                }
            }

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ HWID-based authentication failed", e)
            return@withContext NetworkResult.Error("HWID authentication failed: ${e.message}")
        }
    }

    /**
     * Validate HWID consistency
     */
    fun validateHWID(): HWIDValidationResult {
        return try {
            val currentHwid = hwidProvider.getHWID()
            val lastAuthHwid = sessionStore?.getLastAuthHWID() ?: securePreferences.getLastAuthHWID()

            when {
                lastAuthHwid == null -> {
                    if (enableLogging) Log.d("KeyAuthRepository", "ℹ️ No previous HWID found (new device)")
                    HWIDValidationResult.Valid
                }
                lastAuthHwid == currentHwid -> {
                    if (enableLogging) Log.d("KeyAuthRepository", "✅ HWID validation successful")
                    HWIDValidationResult.Valid
                }
                else -> {
                    if (enableLogging) Log.w("KeyAuthRepository", "⚠️ HWID changed: ${lastAuthHwid.take(8)}... -> ${currentHwid.take(8)}...")
                    HWIDValidationResult.Changed
                }
            }
        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ HWID validation failed", e)
            HWIDValidationResult.Error("HWID validation failed: ${e.message}")
        }
    }

    /**
     * Handle successful authentication with session persistence
     */
    private fun handleSuccessfulAuthentication(licenseKey: String, hwid: String, response: KeyAuthResponse) {
        try {
            // Store session token with expiry
            val sessionToken = response.sessionId ?: ""
            val trustLevel = sessionStore?.getDeviceTrustLevel() ?: securePreferences.getDeviceTrustLevel()

            // Calculate expiry based on trust level (higher trust = longer sessions)
            val expiryDuration = when (trustLevel) {
                0 -> 2 * 60 * 60 * 1000L      // 2 hours for new devices
                1 -> 8 * 60 * 60 * 1000L      // 8 hours for verified devices
                2 -> 24 * 60 * 60 * 1000L     // 24 hours for trusted devices
                else -> 48 * 60 * 60 * 1000L  // 48 hours for highly trusted devices
            }

            val expiryTime = System.currentTimeMillis() + expiryDuration
            val newTrustLevel = minOf(trustLevel + 1, 3)
            if (sessionStore != null) {
                sessionStore.storeSessionToken(sessionToken, expiryTime)
                // Register device and bind license
                sessionStore.setDeviceRegistered(hwid, licenseKey)
                sessionStore.setDeviceTrustLevel(newTrustLevel)
            } else {
                securePreferences.storeSessionToken(sessionToken, expiryTime)
                // Register device and bind license
                securePreferences.setDeviceRegistered(hwid, licenseKey)
                // Increase trust level
                securePreferences.setDeviceTrustLevel(newTrustLevel)
            }

            // Update current HWID
            currentHWID = hwid

            // Update authentication state
            val authState = AuthenticationState(
                isAuthenticated = true,
                sessionToken = sessionToken,
                hwid = hwid,
                licenseKey = licenseKey,
                userInfo = response.userInfo,
                deviceTrustLevel = newTrustLevel,
                isDeviceRegistered = true
            )

            _authenticationState.value = authState
            _authFlowState.value = AuthFlowState.AUTHENTICATED

            if (enableLogging) {
                Log.d("KeyAuthRepository", "✅ Session persistence configured:")
                Log.d("KeyAuthRepository", "   - Trust Level: $newTrustLevel")
                Log.d("KeyAuthRepository", "   - Session Expiry: ${expiryDuration / (60 * 60 * 1000)}h")
                Log.d("KeyAuthRepository", "   - Device Registered: true")
            }

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ Failed to handle session persistence", e)
        }
    }

    /**
     * Get current authentication state
     */
    fun getCurrentAuthState(): AuthenticationState {
        return _authenticationState.value
    }

    /**
     * Check if user can auto-login (has valid session or bound license)
     */
    fun canAutoLogin(): Boolean {
     return (sessionStore?.isDeviceRegistered() ?: securePreferences.isDeviceRegistered()) &&
         !((sessionStore?.getBoundLicenseKey() ?: securePreferences.getBoundLicenseKey()).isNullOrEmpty()) &&
         (sessionStore?.getDeviceTrustLevel() ?: securePreferences.getDeviceTrustLevel()) > 0
    }

    /**
     * Logout and clear all session data
     */
    fun logout() {
        try {
            if (enableLogging) Log.d("KeyAuthRepository", "🚪 Logging out and clearing session data...")

            // Clear session data
            synchronized(initializationLock) {
                sessionId = null
                isInitialized = false
            }

            // Clear stored authentication data (prefer sessionStore if present)
            if (sessionStore != null) {
                sessionStore.clearAuthenticationData()
            } else {
                securePreferences.clearAuthenticationData()
            }

            // Reset authentication state
            _authenticationState.value = AuthenticationState()
            _authFlowState.value = AuthFlowState.IDLE

            currentHWID = null

            if (enableLogging) Log.d("KeyAuthRepository", "✅ Logout completed")

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ Error during logout", e)
        }
    }

    /**
     * Clear session data (thread-safe)
     */
    fun clearSession() {
        synchronized(initializationLock) {
            sessionId = null
            isInitialized = false
        }
    }

    /**
     * Clear all session state and stored data (comprehensive cleanup)
     * Used when session corruption is detected
     */
    private fun clearSessionState() {
        try {
            if (enableLogging) Log.d("KeyAuthRepository", "🧹 Clearing all session state due to corruption...")

            // Clear in-memory session data
            synchronized(initializationLock) {
                sessionId = null
                isInitialized = false
            }

            // Clear stored session data via SessionService (keeps fallback behavior centralized)
            sessionService.clearSessionToken()

            // Reset authentication state
            _authenticationState.value = AuthenticationState()
            _authFlowState.value = AuthFlowState.IDLE

            // Reset current HWID to force regeneration
            currentHWID = null

            if (enableLogging) Log.d("KeyAuthRepository", "✅ Session state cleared successfully")

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ Error clearing session state", e)
        }
    }

    /**
     * Validate KeyAuth configuration
     * Ensures all required parameters are correctly set
     */
    fun validateConfiguration(): ConfigurationValidationResult {
        val issues = mutableListOf<String>()

        // Check required parameters
        if (ownerId.isBlank()) issues.add("Owner ID is missing")
        if (appName.isBlank()) issues.add("App name is missing")
        if (version.isBlank()) issues.add("Version is missing")
        if (apiBaseUrl.isBlank()) issues.add("Base URL is missing")

        // Validate specific values
        if (ownerId != "yLoA9zcOEF") issues.add("Owner ID mismatch (expected: yLoA9zcOEF)")
        if (appName != "com.keyauth.loader") issues.add("App name mismatch (expected: com.keyauth.loader)")
        if (version != "1.3") issues.add("Version mismatch (expected: 1.3)")
        if (!apiBaseUrl.contains("keyauth.win/api/1.3")) issues.add("API endpoint mismatch (expected: keyauth.win/api/1.3)")

        // Check custom hash
        if (customHash != "60885a0cf06010794575da6896370413") {
            issues.add("Custom hash mismatch (expected: 60885a0cf06010794575da6896370413)")
        }

        return if (issues.isEmpty()) {
            ConfigurationValidationResult.Valid
        } else {
            ConfigurationValidationResult.Invalid(issues)
        }
    }

    /**
     * Get diagnostic information for troubleshooting
     */
    fun getDiagnosticInfo(): Map<String, String> {
        return mapOf(
            "isInitialized" to isAppInitialized().toString(),
            "hasSessionId" to (sessionId != null).toString(),
            "sessionIdLength" to (sessionId?.length?.toString() ?: "0"),
            "ownerId" to ownerId,
            "appName" to appName,
            "version" to version,
            "baseUrl" to apiBaseUrl,
            "customHash" to (customHash?.take(8) ?: "null"),
            "hasStoredToken" to (!((sessionStore?.getSessionToken() ?: securePreferences.getSessionToken()).isNullOrEmpty())).toString(),
            "isTokenValid" to (sessionStore?.isSessionTokenValid() ?: securePreferences.isSessionTokenValid()).toString(),
            "isDeviceRegistered" to (sessionStore?.isDeviceRegistered() ?: securePreferences.isDeviceRegistered()).toString(),
            "deviceTrustLevel" to (sessionStore?.getDeviceTrustLevel() ?: securePreferences.getDeviceTrustLevel()).toString(),
            "currentHWID" to (currentHWID?.take(8) ?: "null")
        )
    }

    /**
     * Force clean initialization - clears all session state and performs fresh init
     * Use this when session corruption is detected
     */
    suspend fun forceCleanInitialization(): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            if (enableLogging) Log.d("KeyAuthRepository", "🔄 Forcing clean initialization due to session corruption...")

            // Clear all session state
            clearSessionState()

            // Perform fresh initialization
            val initResult = initialize(preserveSession = false)

            if (initResult is NetworkResult.Success) {
                if (enableLogging) Log.d("KeyAuthRepository", "✅ Clean initialization successful")
            } else {
                if (enableLogging) Log.e("KeyAuthRepository", "❌ Clean initialization failed: ${(initResult as? NetworkResult.Error)?.message}")
            }

            return@withContext initResult

        } catch (e: Exception) {
            val errorMessage = "Clean initialization failed: ${e.message}"
            if (enableLogging) Log.e("KeyAuthRepository", "❌ $errorMessage", e)
            return@withContext NetworkResult.Error(errorMessage)
        }
    }

    /**
     * Authenticate with license key but allow a single HWID/session update retry.
     *
     * Behavior:
     *  - Ensures the client is initialized (clean init).
     *  - Attempts license authentication once.
     *  - If server responds with "session not found" / HWID mismatch style errors,
     *    clears the persisted session token, resets in-memory session state,
     *    performs a fresh initialization and retries authentication once.
     *
     * This preserves the KeyAuth requirement that init() must run before license()
     * and that init() must never receive stored session tokens.
     */
    suspend fun authenticateWithLicenseAllowHwidUpdate(licenseKey: String): NetworkResult<KeyAuthResponse> = withContext(Dispatchers.IO) {
        try {
            // Ensure clean initialization first (never pass stored tokens into init)
            if (!isAppInitialized()) {
                if (enableLogging) Log.d("KeyAuthRepository", "🔄 authenticateWithLicenseAllowHwidUpdate: performing clean initialization...")
                val initResult = initialize(preserveSession = false)
                if (initResult !is NetworkResult.Success) {
                    if (enableLogging) Log.e("KeyAuthRepository", "❌ Initialization failed before license auth: ${(initResult as? NetworkResult.Error)?.message}")
                    return@withContext NetworkResult.Error("Initialization failed: ${(initResult as? NetworkResult.Error)?.message}")
                }
            }

            // First attempt
            val firstAttempt = authenticateWithLicense(licenseKey)
            if (firstAttempt is NetworkResult.Success) return@withContext firstAttempt

            // Inspect error for session/HWID-specific messages
            val errMsg = (firstAttempt as? NetworkResult.Error)?.message ?: ""
            val indicatesSessionIssue = errMsg.contains("session not found", ignoreCase = true) ||
                    errMsg.contains("last code", ignoreCase = true) ||
                    errMsg.contains("Session expired or invalid", ignoreCase = true)

            if (indicatesSessionIssue) {
                if (enableLogging) Log.w("KeyAuthRepository", "⚠️ Session/HWID issue detected during license auth, attempting clear + re-init + retry")

                // Clear persisted session token and reset in-memory state
                try {
                    sessionService.clearSessionToken()
                } catch (e: Exception) {
                    if (enableLogging) Log.w("KeyAuthRepository", "⚠️ Failed to clear persisted session token: ${e.message}")
                }

                synchronized(initializationLock) {
                    sessionId = null
                    isInitialized = false
                }

                // Perform fresh initialization and retry once
                val retryInit = initialize(preserveSession = false)
                if (retryInit !is NetworkResult.Success) {
                    if (enableLogging) Log.e("KeyAuthRepository", "❌ Re-initialization failed: ${(retryInit as? NetworkResult.Error)?.message}")
                    return@withContext NetworkResult.Error("Re-initialization failed: ${(retryInit as? NetworkResult.Error)?.message}")
                }

                if (enableLogging) Log.d("KeyAuthRepository", "🔁 Re-initialization successful, retrying license authentication")
                val retryAttempt = authenticateWithLicense(licenseKey)
                return@withContext retryAttempt
            }

            // Otherwise return original error
            return@withContext firstAttempt

        } catch (e: Exception) {
            if (enableLogging) Log.e("KeyAuthRepository", "❌ authenticateWithLicenseAllowHwidUpdate failed", e)
            return@withContext NetworkResult.Error("Authentication error: ${e.message}")
        }
    }
}

/**
 * Configuration validation result
 */
sealed class ConfigurationValidationResult {
    object Valid : ConfigurationValidationResult()
    data class Invalid(val issues: List<String>) : ConfigurationValidationResult()
}
