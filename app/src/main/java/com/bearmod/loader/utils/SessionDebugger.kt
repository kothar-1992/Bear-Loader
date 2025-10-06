package com.bearmod.loader.utils

import android.content.Context
import android.util.Log
// SessionDebugger uses SecurePrefsAdapterImpl for storage access

/**
 * Session debugging utility to help diagnose authentication issues
 * Provides comprehensive logging and state inspection for session management
 */
object SessionDebugger {
    private const val TAG = "SessionDebugger"
    
    /**
     * Log comprehensive session state for debugging
     */
    fun logSessionState(context: Context, prefix: String = "") {
        try {
            val securePreferences = com.bearmod.loader.utils.SecurePrefsAdapterImpl(context)
            
            Log.d(TAG, "==================== SESSION DEBUG $prefix ====================")
            
            // Session Token Info
            val sessionToken = securePreferences.getSessionToken()
            Log.d(TAG, "📱 Session Token: ${if (sessionToken.isNullOrEmpty()) "❌ NONE" else "✅ Present (${sessionToken.take(8)}...)"}")
            
            // Session Validity
            val isTokenValid = securePreferences.isSessionTokenValid()
            Log.d(TAG, "⏰ Token Valid: ${if (isTokenValid) "✅ YES" else "❌ NO"}")
            
            // Device Registration
            val isDeviceRegistered = securePreferences.isDeviceRegistered()
            Log.d(TAG, "📱 Device Registered: ${if (isDeviceRegistered) "✅ YES" else "❌ NO"}")
            
            // HWID Info
            val storedHWID = securePreferences.getStoredHWID()
            val lastAuthHWID = securePreferences.getLastAuthHWID()
            Log.d(TAG, "🔑 Stored HWID: ${if (storedHWID.isNullOrEmpty()) "❌ NONE" else "✅ Present (${storedHWID.take(8)}...)"}")
            Log.d(TAG, "🔑 Last Auth HWID: ${if (lastAuthHWID.isNullOrEmpty()) "❌ NONE" else "✅ Present (${lastAuthHWID.take(8)}...)"}")
            
            // License Key Info
            val boundLicense = securePreferences.getBoundLicenseKey()
            Log.d(TAG, "🎫 Bound License: ${if (boundLicense.isNullOrEmpty()) "❌ NONE" else "✅ Present (${boundLicense.take(8)}...)"}")
            
            // Trust Level
            val trustLevel = securePreferences.getDeviceTrustLevel()
            Log.d(TAG, "🛡️ Trust Level: $trustLevel")
            
            // Auto Login Setting
            val autoLogin = securePreferences.getAutoLogin()
            Log.d(TAG, "🔄 Auto Login: ${if (autoLogin) "✅ ENABLED" else "❌ DISABLED"}")
            
            // Refresh Token
            val refreshToken = securePreferences.getRefreshToken()
            Log.d(TAG, "🔄 Refresh Token: ${if (refreshToken.isNullOrEmpty()) "❌ NONE" else "✅ Present (${refreshToken.take(8)}...)"}")
            
            Log.d(TAG, "================================================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log session state", e)
        }
    }
    
    /**
     * Log authentication flow state
     */
    fun logAuthFlowState(state: String, details: String = "") {
        Log.d(TAG, "🔄 Auth Flow: $state ${if (details.isNotEmpty()) "- $details" else ""}")
    }
    
    /**
     * Log session restoration attempt
     */
    fun logSessionRestoreAttempt(context: Context) {
        Log.d(TAG, "🔄 ==================== SESSION RESTORE ATTEMPT ====================")
        logSessionState(context, "BEFORE RESTORE")
    }
    
    /**
     * Log session restoration result
     */
    fun logSessionRestoreResult(context: Context, success: Boolean, message: String = "") {
        Log.d(TAG, "🔄 ==================== SESSION RESTORE RESULT ====================")
        Log.d(TAG, "${if (success) "✅" else "❌"} Result: ${if (success) "SUCCESS" else "FAILED"} ${if (message.isNotEmpty()) "- $message" else ""}")
        logSessionState(context, "AFTER RESTORE")
    }
    
    /**
     * Log initialization state
     */
    fun logInitializationState(isInitialized: Boolean, sessionId: String?) {
        Log.d(TAG, "🚀 KeyAuth Initialized: ${if (isInitialized) "✅ YES" else "❌ NO"}")
        Log.d(TAG, "🆔 Session ID: ${if (sessionId.isNullOrEmpty()) "❌ NONE" else "✅ Present (${sessionId.take(8)}...)"}")
    }
    
    /**
     * Log network error details
     */
    fun logNetworkError(operation: String, error: String, responseCode: Int? = null) {
        Log.e(TAG, "🌐 Network Error in $operation: $error ${responseCode?.let { "(HTTP $it)" } ?: ""}")
    }
    
    /**
     * Log HWID validation
     */
    fun logHWIDValidation(current: String?, stored: String?, lastAuth: String?) {
        Log.d(TAG, "🔑 ==================== HWID VALIDATION ====================")
        Log.d(TAG, "🔑 Current HWID: ${current?.take(8)}...")
        Log.d(TAG, "🔑 Stored HWID: ${stored?.take(8)}...")
        Log.d(TAG, "🔑 Last Auth HWID: ${lastAuth?.take(8)}...")
        
        val isConsistent = current == stored && stored == lastAuth
        Log.d(TAG, "🔑 HWID Consistent: ${if (isConsistent) "✅ YES" else "❌ NO"}")
        Log.d(TAG, "================================================================")
    }
    
    /**
     * Clear all debug logs (for production)
     */
    fun clearDebugLogs() {
        // In production, this could clear log files or disable debugging
        Log.d(TAG, "🧹 Debug logs cleared")
    }
    
    /**
     * Check for common session issues
     */
    fun diagnoseSessionIssues(context: Context): List<String> {
        val issues = mutableListOf<String>()
        
        try {
            val securePreferences = com.bearmod.loader.utils.SecurePrefsAdapterImpl(context)
            
            // Check for missing session token
            if (securePreferences.getSessionToken().isNullOrEmpty()) {
                issues.add("❌ No session token stored")
            }
            
            // Check for expired token
            if (!securePreferences.isSessionTokenValid()) {
                issues.add("⏰ Session token expired")
            }
            
            // Check for unregistered device
            if (!securePreferences.isDeviceRegistered()) {
                issues.add("📱 Device not registered")
            }
            
            // Check for missing license key
            if (securePreferences.getBoundLicenseKey().isNullOrEmpty()) {
                issues.add("🎫 No bound license key")
            }
            
            // Check for HWID mismatch
            val storedHWID = securePreferences.getStoredHWID()
            val lastAuthHWID = securePreferences.getLastAuthHWID()
            if (storedHWID != null && lastAuthHWID != null && storedHWID != lastAuthHWID) {
                issues.add("🔑 HWID mismatch detected")
            }
            
            // Check auto-login setting
            if (!securePreferences.getAutoLogin()) {
                issues.add("🔄 Auto-login disabled")
            }
            
        } catch (e: Exception) {
            issues.add("❌ Error diagnosing session: ${e.message}")
        }
        
        return issues
    }
}
