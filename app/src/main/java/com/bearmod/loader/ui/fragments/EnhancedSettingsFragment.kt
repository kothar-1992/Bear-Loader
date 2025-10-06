package com.bearmod.loader.ui.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.bearmod.loader.R
import com.bearmod.loader.data.repository.AuthRepository
import com.bearmod.loader.data.model.AuthenticationState
import com.bearmod.loader.network.NetworkFactory
import com.bearmod.loader.ui.LoginActivity
import com.bearmod.loader.ui.MainActivity
import com.bearmod.loader.utils.LanguageManager
import com.bearmod.loader.utils.SessionManager
import com.bearmod.loader.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Settings Fragment with comprehensive user information,
 * license details, and relocated app action buttons
 */
class EnhancedSettingsFragment : Fragment() {

    // UI Components
    private lateinit var tvCurrentLanguage: TextView
    private lateinit var tvLicenseKey: TextView
    private lateinit var tvLicenseExpiry: TextView
    private lateinit var tvTimeRemaining: TextView
    private lateinit var tvLastValidation: TextView
    private lateinit var tvLicenseStatus: TextView

    // Section headers
    private lateinit var tvLicenseInfoHeader: TextView
    private lateinit var tvLanguageSettingsHeader: TextView
    private lateinit var tvLanguageDescription: TextView
    private lateinit var tvAccountManagementHeader: TextView

    // Account management
    private lateinit var tvLogout: TextView

    // Utilities
    private lateinit var languageManager: LanguageManager
    private lateinit var sessionManager: SessionManager
    private lateinit var securePrefsAdapter: com.bearmod.loader.utils.SecurePrefsAdapter
    private lateinit var securePrefsAdapterImpl: com.bearmod.loader.utils.SecurePrefsAdapterImpl
    private lateinit var authViewModel: AuthViewModel
    private lateinit var authRepository: AuthRepository

    // Update handler for countdown
    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // Current authentication state
    private var currentAuthState: AuthenticationState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageManager = LanguageManager(requireContext())
    sessionManager = SessionManager(requireContext())
    securePrefsAdapterImpl = com.bearmod.loader.utils.SecurePrefsAdapterImpl(requireContext())
    securePrefsAdapter = securePrefsAdapterImpl
        authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]
    authRepository = NetworkFactory.createKeyAuthRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        initViews(view)
        setupClickListeners(view)
        setupObservers()
        updateLanguage()
        loadUserInformation()
        startPeriodicUpdates()

        // Add smooth entrance animations
        animateViewsIn()

        return view
    }

    private fun initViews(view: View) {
        // Section headers
        tvLicenseInfoHeader = view.findViewById(R.id.tvLicenseInfoHeader)
        tvLanguageSettingsHeader = view.findViewById(R.id.tvLanguageSettingsHeader)
        tvLanguageDescription = view.findViewById(R.id.tvLanguageDescription)
        tvAccountManagementHeader = view.findViewById(R.id.tvAccountManagementHeader)

        // Language and basic settings
        tvCurrentLanguage = view.findViewById(R.id.tvCurrentLanguage)

        // License information displays
        tvLicenseKey = view.findViewById(R.id.tvLicenseKey)
        tvLicenseExpiry = view.findViewById(R.id.tvLicenseExpiry)
        tvTimeRemaining = view.findViewById(R.id.tvTimeRemaining)
        tvLastValidation = view.findViewById(R.id.tvLastValidation)
        tvLicenseStatus = view.findViewById(R.id.tvLicenseStatus)

        // Account management
        tvLogout = view.findViewById(R.id.tvLogout)
    }

    private fun setupClickListeners(view: View) {
        try {
            // Language selection card click - Safe view access with null checks
            val cardLanguageSelection = view.findViewById<View>(R.id.cardLanguageSelection)
            if (cardLanguageSelection != null) {
                cardLanguageSelection.setOnClickListener {
                    Log.d("EnhancedSettingsFragment", "Language card clicked")
                    if (isAdded && !isDetached) {
                        showLanguageDialog()
                    }
                }
                Log.d("EnhancedSettingsFragment", "Language card click listener set successfully")
            } else {
                Log.w("EnhancedSettingsFragment", "Language card not found in layout")
            }

            // Logout card click
            val cardLogout = view.findViewById<View>(R.id.cardLogout)
            if (cardLogout != null) {
                cardLogout.setOnClickListener {
                    animateButtonPress(it)
                    showLogoutConfirmationDialog()
                }
            }

        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error setting up click listeners", e)
        }
    }

    private fun setupObservers() {
        try {
            // Observe authentication state changes from KeyAuthRepository
            authRepository.authenticationState.asLiveData().observe(viewLifecycleOwner, Observer { authState ->
                Log.d("EnhancedSettingsFragment", "Authentication state changed: $authState")
                currentAuthState = authState
                if (isAdded && !isDetached) {
                    updateLicenseInformation()
                }
            })
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error setting up observers", e)
        }
    }

    private fun loadUserInformation() {
        // Load license information
        updateLicenseInformation()
    }

    private fun updateLicenseInformation() {
        try {
            // Check if fragment is still attached and views are available
            if (!isAdded || isDetached || view == null) {
                Log.w("EnhancedSettingsFragment", "Fragment not attached, skipping license info update")
                return
            }

            // Use current authentication state if available, otherwise fall back to stored data
            val authState = currentAuthState ?: authRepository.getCurrentAuthState()
            val licenseKey = authState.licenseKey ?: securePrefsAdapter.getBoundLicenseKey()
            val sessionToken = authState.sessionToken ?: securePrefsAdapter.getSessionToken()

            // Try to get expiry from subscription data first, then auth state, then stored data
            val tokenExpiry = authState.userInfo?.subscriptions?.firstOrNull()?.let { subscription ->
                try {
                    // Parse subscription expiry if available
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(subscription.expiry)?.time
                } catch (e: Exception) {
                    Log.w("EnhancedSettingsFragment", "Failed to parse subscription expiry: ${subscription.expiry}", e)
                    null
                }
            } ?: authState.expiryTime.takeIf { it > 0 } ?: securePrefsAdapter.getTokenExpiryTime()

            val isAuthenticated = authState.isAuthenticated && authState.isSessionValid()

            Log.d("EnhancedSettingsFragment", "License info update - Key: ${if (licenseKey.isNullOrEmpty()) "null/empty" else "available"}, Token: ${if (sessionToken.isNullOrEmpty()) "null/empty" else "available"}, Expiry: $tokenExpiry, Authenticated: $isAuthenticated")

            // Update license key (masked for security) with null checks
            if (::tvLicenseKey.isInitialized) {
                if (!licenseKey.isNullOrEmpty()) {
                    val maskedKey = if (licenseKey.length >= 8) {
                        "BEAR-" + "*".repeat(4) + "-" + "*".repeat(4) + "-" + licenseKey.takeLast(4)
                    } else {
                        "BEAR-****-****-****" // Fallback for short keys
                    }
                    val keyText = if (languageManager.isChineseEnabled()) {
                        "密钥: $maskedKey"
                    } else {
                        "Key: $maskedKey"
                    }
                    tvLicenseKey.text = keyText
                } else {
                    val noKeyText = if (languageManager.isChineseEnabled()) {
                        "密钥: 不可用"
                    } else {
                        "Key: Not Available"
                    }
                    tvLicenseKey.text = noKeyText
                }
            }

            // Update license status based on actual authentication state
            if (::tvLicenseStatus.isInitialized) {
                if (isAuthenticated) {
                    tvLicenseStatus.text = if (languageManager.isChineseEnabled()) "有效" else "Valid"
                    tvLicenseStatus.setTextColor(resources.getColor(R.color.success_green, null))
                } else {
                    tvLicenseStatus.text = if (languageManager.isChineseEnabled()) "无效" else "Invalid"
                    tvLicenseStatus.setTextColor(resources.getColor(R.color.error_red, null))
                }
            }

            // Update expiry information with real token data
            updateExpiryInformation(tokenExpiry)

            // Update last validation with real timestamp
            if (::tvLastValidation.isInitialized) {
                val lastValidation = if (languageManager.isChineseEnabled()) {
                    "最后验证: ${getCurrentTimestamp()}"
                } else {
                    "Last Validation: ${getCurrentTimestamp()}"
                }
                tvLastValidation.text = lastValidation
            }
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error updating license information", e)
        }
    }

    private fun updateExpiryInformation(expiryTime: Long) {
        try {
            // Check if fragment is still attached and views are available
            if (!isAdded || isDetached || view == null) {
                Log.w("EnhancedSettingsFragment", "Fragment not attached, skipping expiry info update")
                return
            }

            Log.d("EnhancedSettingsFragment", "Updating expiry info - Expiry time: $expiryTime, Current time: ${System.currentTimeMillis()}")

            if (expiryTime > 0) {
                val currentTime = System.currentTimeMillis()
                val timeRemaining = expiryTime - currentTime

                Log.d("EnhancedSettingsFragment", "Time remaining: $timeRemaining ms")

                if (timeRemaining > 0) {
                    // Format expiry date with proper locale
                    val dateFormat = if (languageManager.isChineseEnabled()) {
                        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINESE)
                    } else {
                        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.ENGLISH)
                    }
                    val expiryDate = dateFormat.format(Date(expiryTime))

                    val expiryText = if (languageManager.isChineseEnabled()) {
                        "到期时间: $expiryDate"
                    } else {
                        "Expires: $expiryDate"
                    }

                    // Update expiry text with null check
                    if (::tvLicenseExpiry.isInitialized) {
                        tvLicenseExpiry.text = expiryText
                    }

                    // Format time remaining with real data
                    val remainingText = formatTimeRemaining(timeRemaining)

                    // Update time remaining with null check
                    if (::tvTimeRemaining.isInitialized) {
                        tvTimeRemaining.text = remainingText

                        // Color coding based on time remaining
                        val color = when {
                            timeRemaining < 60 * 60 * 1000 -> R.color.error_red // Less than 1 hour
                            timeRemaining < 24 * 60 * 60 * 1000 -> R.color.warning_yellow // Less than 1 day
                            else -> R.color.accent_orange // More than 1 day
                        }
                        tvTimeRemaining.setTextColor(resources.getColor(color, null))
                    }
                } else {
                    // Expired - update with null checks
                    if (::tvLicenseExpiry.isInitialized) {
                        tvLicenseExpiry.text = if (languageManager.isChineseEnabled()) {
                            "到期时间: 已过期"
                        } else {
                            "Expires: Expired"
                        }
                    }

                    if (::tvTimeRemaining.isInitialized) {
                        tvTimeRemaining.text = if (languageManager.isChineseEnabled()) {
                            "剩余时间: 已过期"
                        } else {
                            "Time Remaining: Expired"
                        }
                        tvTimeRemaining.setTextColor(resources.getColor(R.color.error_red, null))
                    }
                }
            } else {
                // No expiry time available - update with null checks
                Log.w("EnhancedSettingsFragment", "No expiry time available - session may not be properly stored")

                if (::tvLicenseExpiry.isInitialized) {
                    tvLicenseExpiry.text = if (languageManager.isChineseEnabled()) {
                        "到期时间: 未设置"
                    } else {
                        "Expires: Not Set"
                    }
                }

                if (::tvTimeRemaining.isInitialized) {
                    tvTimeRemaining.text = if (languageManager.isChineseEnabled()) {
                        "剩余时间: 未知"
                    } else {
                        "Time Remaining: Unknown"
                    }
                    tvTimeRemaining.setTextColor(resources.getColor(R.color.text_secondary, null))
                }
            }
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error updating expiry information", e)
        }
    }

    private fun formatTimeRemaining(timeMs: Long): String {
        val days = timeMs / (24 * 60 * 60 * 1000)
        val hours = (timeMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        val minutes = (timeMs % (60 * 60 * 1000)) / (60 * 1000)
        
        return if (languageManager.isChineseEnabled()) {
            when {
                days > 0 -> "剩余时间: ${days}天 ${hours}小时"
                hours > 0 -> "剩余时间: ${hours}小时 ${minutes}分钟"
                else -> "剩余时间: ${minutes}分钟"
            }
        } else {
            when {
                days > 0 -> "Time Remaining: ${days}d ${hours}h"
                hours > 0 -> "Time Remaining: ${hours}h ${minutes}m"
                else -> "Time Remaining: ${minutes}m"
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun startPeriodicUpdates() {
        try {
            // Stop any existing updates first
            stopPeriodicUpdates()

            updateRunnable = object : Runnable {
                override fun run() {
                    try {
                        // Only update if fragment is still attached
                        if (isAdded && !isDetached && view != null) {
                            updateLicenseInformation()
                            updateHandler.postDelayed(this, 60000) // Update every minute
                        } else {
                            Log.d("EnhancedSettingsFragment", "Fragment not attached, stopping periodic updates")
                        }
                    } catch (e: Exception) {
                        Log.e("EnhancedSettingsFragment", "Error in periodic update", e)
                    }
                }
            }
            updateHandler.post(updateRunnable!!)
            Log.d("EnhancedSettingsFragment", "Periodic updates started")
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error starting periodic updates", e)
        }
    }

    private fun stopPeriodicUpdates() {
        try {
            updateRunnable?.let {
                updateHandler.removeCallbacks(it)
                Log.d("EnhancedSettingsFragment", "Periodic updates stopped")
            }
            updateRunnable = null
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error stopping periodic updates", e)
        }
    }

    private fun showSuccessToast(message: String) {
        try {
            if (isAdded && !isDetached) {
                // Create a custom toast with success styling
                Toast.makeText(requireContext(), "✓ $message", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error showing success toast", e)
        }
    }

    private fun showLogoutConfirmationDialog() {
        try {
            if (!isAdded || isDetached) {
                Log.w("EnhancedSettingsFragment", "Fragment not attached, cannot show logout dialog")
                return
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (languageManager.isChineseEnabled()) "确认退出" else "Confirm Logout")
                .setMessage(if (languageManager.isChineseEnabled()) {
                    "您确定要退出吗？这将清除所有本地数据。"
                } else {
                    "Are you sure you want to logout? This will clear all local data."
                })
                .setPositiveButton(if (languageManager.isChineseEnabled()) "退出" else "Logout") { _, _ ->
                    performLogout()
                }
                .setNegativeButton(if (languageManager.isChineseEnabled()) "取消" else "Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error showing logout confirmation dialog", e)
        }
    }

    private fun performLogout() {
        try {
            // Clear all user data
            securePrefsAdapter.clearAll()
            sessionManager.clearSession()
            authRepository.logout()

            // Show logout success message
            showToast(if (languageManager.isChineseEnabled()) {
                "已成功退出"
            } else {
                "Logged out successfully"
            })

            // Navigate back to login activity
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            activity?.finish()

            Log.d("EnhancedSettingsFragment", "Logout completed successfully")
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error during logout", e)
            showToast(if (languageManager.isChineseEnabled()) {
                "退出时出错"
            } else {
                "Error during logout"
            })
        }
    }

    private fun showToast(message: String) {
        try {
            if (isAdded && !isDetached) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error showing toast", e)
        }
    }

    private fun showLanguageDialog() {
        try {
            // Check if fragment is still attached
            if (!isAdded || isDetached) {
                Log.w("EnhancedSettingsFragment", "Fragment not attached, cannot show language dialog")
                return
            }

            val languages = if (languageManager.isChineseEnabled()) {
                arrayOf("中文", "English")
            } else {
                arrayOf("Chinese", "English")
            }

            val currentSelection = if (languageManager.isChineseEnabled()) 0 else 1

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (languageManager.isChineseEnabled()) "选择语言" else "Select Language")
                .setSingleChoiceItems(languages, currentSelection) { dialog, which ->
                    try {
                        val enableChinese = (which == 0)
                        val wasChanged = languageManager.isChineseEnabled() != enableChinese

                        if (wasChanged) {
                            Log.d("EnhancedSettingsFragment", "Language changed to: ${if (enableChinese) "Chinese" else "English"}")
                            languageManager.setChineseEnabled(enableChinese)

                            // Update current fragment immediately if still attached
                            if (isAdded && !isDetached) {
                                updateLanguage()

                                // Notify MainActivity to update all fragments with a slight delay
                                updateHandler.postDelayed({
                                    if (activity is MainActivity && isAdded && !isDetached) {
                                        (activity as MainActivity).updateLanguage()
                                    }
                                }, 100)
                            }

                            // Show confirmation toast
                            showToast(if (enableChinese) {
                                "语言已切换为中文"
                            } else {
                                "Language switched to English"
                            })

                            // Force activity recreation for full language update
                            requireActivity().recreate()
                        }

                        dialog.dismiss()
                    } catch (e: Exception) {
                        Log.e("EnhancedSettingsFragment", "Error in language selection", e)
                        dialog.dismiss()
                    }
                }
                .setNegativeButton(if (languageManager.isChineseEnabled()) "取消" else "Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error showing language dialog", e)
        }
    }

    fun updateLanguage() {
        try {
            // Check if fragment is still attached and views are available
            if (!isAdded || isDetached || view == null) {
                Log.w("EnhancedSettingsFragment", "Fragment not attached, skipping language update")
                return
            }

            // Update section headers with null checks
            if (::tvLicenseInfoHeader.isInitialized) {
                tvLicenseInfoHeader.text = if (languageManager.isChineseEnabled()) {
                    "许可证信息 :"
                } else {
                    "License Information :"
                }
            }

            if (::tvLanguageSettingsHeader.isInitialized) {
                tvLanguageSettingsHeader.text = if (languageManager.isChineseEnabled()) {
                    "语言设置"
                } else {
                    "Language Settings"
                }
            }

            if (::tvLanguageDescription.isInitialized) {
                tvLanguageDescription.text = if (languageManager.isChineseEnabled()) {
                    "选择您的首选语言"
                } else {
                    "Choose your preferred language"
                }
            }

            if (::tvAccountManagementHeader.isInitialized) {
                tvAccountManagementHeader.text = if (languageManager.isChineseEnabled()) {
                    "账户管理"
                } else {
                    "Account Management"
                }
            }

            // Update current language display with null check
            if (::tvCurrentLanguage.isInitialized) {
                tvCurrentLanguage.text = if (languageManager.isChineseEnabled()) {
                    "切换语言"
                } else {
                    "Switch Language"
                }
            }

            // Update logout text
            if (::tvLogout.isInitialized) {
                tvLogout.text = if (languageManager.isChineseEnabled()) {
                    "退出登录"
                } else {
                    "Logout"
                }
            }

            // Refresh all user information with new language
            loadUserInformation()
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error updating language", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Log.d("EnhancedSettingsFragment", "onResume called")
            updateLanguage()
            loadUserInformation()
            startPeriodicUpdates()
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Log.d("EnhancedSettingsFragment", "onPause called")
            stopPeriodicUpdates()
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error in onPause", e)
        }
    }

    override fun onDestroyView() {
        try {
            Log.d("EnhancedSettingsFragment", "onDestroyView called")
            stopPeriodicUpdates()
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error in onDestroyView", e)
        }
        super.onDestroyView()
    }

    private fun animateViewsIn() {
        try {
            if (!isAdded || isDetached || view == null) return

            // Get all card views for animation
            val cards = listOf(
                view?.findViewById<View>(R.id.tvLicenseInfoHeader),
                view?.findViewById<View>(R.id.cardLanguageSelection),
                view?.findViewById<View>(R.id.cardLogout)
            ).filterNotNull()

            // Animate cards with staggered entrance
            cards.forEachIndexed { index, card ->
                card.alpha = 0f
                card.translationY = 50f

                val animator = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
                    duration = 300
                    startDelay = (index * 100).toLong()
                    interpolator = AccelerateDecelerateInterpolator()
                }

                val translateAnimator = ObjectAnimator.ofFloat(card, "translationY", 50f, 0f).apply {
                    duration = 300
                    startDelay = (index * 100).toLong()
                    interpolator = AccelerateDecelerateInterpolator()
                }

                AnimatorSet().apply {
                    playTogether(animator, translateAnimator)
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error animating views", e)
        }
    }

    private fun animateButtonPress(button: View) {
        try {
            val scaleDown = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.95f).apply {
                duration = 100
            }
            val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.95f).apply {
                duration = 100
            }
            val scaleUp = ObjectAnimator.ofFloat(button, "scaleX", 0.95f, 1f).apply {
                duration = 100
                startDelay = 100
            }
            val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.95f, 1f).apply {
                duration = 100
                startDelay = 100
            }

            AnimatorSet().apply {
                playTogether(scaleDown, scaleDownY, scaleUp, scaleUpY)
                start()
            }
        } catch (e: Exception) {
            Log.e("EnhancedSettingsFragment", "Error animating button press", e)
        }
    }

    companion object {
        fun newInstance(): EnhancedSettingsFragment {
            return EnhancedSettingsFragment()
        }
    }
}
