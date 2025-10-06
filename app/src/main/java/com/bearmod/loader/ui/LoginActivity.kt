package com.bearmod.loader.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.bearmod.loader.R
import com.bearmod.loader.databinding.ActivityLoginBinding
import com.bearmod.loader.network.NetworkFactory
import com.bearmod.loader.ui.login.LoginViewModel
import com.bearmod.loader.data.model.SessionRestoreResult
import com.bearmod.loader.data.model.AuthFlowState
import com.bearmod.loader.utils.NetworkResult
import com.bearmod.loader.utils.SecurePreferences
import com.bearmod.loader.utils.PreferencesMigration
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var securePreferences: com.bearmod.loader.utils.SecurePreferences
    private lateinit var sessionService: com.bearmod.loader.session.SessionService
    
    private val viewModel: LoginViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = NetworkFactory.createKeyAuthRepository(this@LoginActivity)
                return LoginViewModel(repository) as T
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
    securePreferences = com.bearmod.loader.utils.SecurePreferences(this)
    sessionService = com.bearmod.loader.session.SessionService(securePreferences)

        // Migrate preferences from old implementation if needed
        PreferencesMigration.migrateIfNeeded(this, securePreferences)

    // Clear any corrupted session data that might cause "Session not found" errors
    // Use SessionService to centralize the clearing sequence
    sessionService.clearCorruptedSession()

        setupUI()
        setupObservers()
        loadSavedPreferences()

    // Run entry animations for premium feel
    runEntryAnimations()
    // Setup parallax and theme toggle
    setupParallaxAndThemeToggle()

    // Apply subtle backdrop blur for a glassmorphism effect on supported devices (Android 12L+ / API 31+)
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val parallaxView = findViewById<View>(R.id.parallaxContainer)
            // 8f blur radius for subtle frosted effect
            val radius = 8f
            val renderEffect = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
            parallaxView.setRenderEffect(renderEffect)
        }
    } catch (e: Exception) {
        // RenderEffect may fail on some OEM devices - fallback is the semi-translucent card drawable
        Log.d("LoginActivity", "RenderEffect not applied: ${e.message}")
    }

        // Enhanced initialization with session restoration
        Log.d("LoginActivity", "🚀 Starting enhanced KeyAuth initialization with session restoration")

        // Check if we can attempt auto-login
        if (viewModel.canAutoLogin() && securePreferences.getAutoLogin()) {
            Log.d("LoginActivity", "🔄 Auto-login available, attempting session restoration...")
            viewModel.initializeWithSessionRestore()
        } else {
            Log.d("LoginActivity", "🔄 Standard initialization (no auto-login)")
            viewModel.initializeApp()
        }
    }

    private fun runEntryAnimations() {
        try {
            val slideIn = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_up)
            binding.cardLogo.startAnimation(slideIn)
            binding.tvAppTitle.startAnimation(slideIn)
            binding.cardLicenseInput.startAnimation(slideIn)
            binding.btnLogin.startAnimation(slideIn)

            // Button touch feedback to scale slightly on press/release
            binding.btnLogin.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_press_scale))
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.button_release_scale))
                    }
                }
                // Return false so click still propagates
                false
            }
            // Start logo shimmer animation if available. Use dynamic id lookup so compilation
            // doesn't fail if the resource was removed (some builds use a raster logo only).
            try {
                val shimmerResId = resources.getIdentifier("ivLogoShimmer", "id", packageName)
                if (shimmerResId != 0) {
                    val shimmerView = binding.root.findViewById<android.widget.ImageView>(shimmerResId)
                    val drawable = shimmerView?.drawable
                    if (drawable is android.graphics.drawable.AnimatedVectorDrawable) {
                        drawable.start()
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        } catch (e: Exception) {
            // Ignore if animations fail on older devices
        }
    }

    private fun setupParallaxAndThemeToggle() {
        // Parallax background subtle movement based on touch
        val parallax = binding.root.findViewById<android.view.View>(R.id.parallaxContainer)
        parallax?.setOnTouchListener { v, event ->
            try {
                when (event.action) {
                    android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_DOWN -> {
                        val cx = v.width / 2f
                        val cy = v.height / 2f
                        val dx = (event.x - cx) / cx
                        val dy = (event.y - cy) / cy

                        // Apply a subtle translation to background layers
                        val maxTranslate = 8f // px
                        binding.cardLogo.translationX = -dx * maxTranslate
                        binding.cardLogo.translationY = -dy * maxTranslate
                        binding.cardLicenseInput.translationX = -dx * (maxTranslate / 2f)
                        binding.cardLicenseInput.translationY = -dy * (maxTranslate / 2f)
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        // Smoothly return to center
                        binding.cardLogo.animate().translationX(0f).translationY(0f).setDuration(220).start()
                        binding.cardLicenseInput.animate().translationX(0f).translationY(0f).setDuration(220).start()
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            false
        }

        // Theme toggle
        binding.root.findViewById<android.widget.ImageButton>(R.id.btnThemeToggle)?.setOnClickListener {
            // Toggle between light/dark
            val current = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
            if (current == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }
    
    private fun setupUI() {
        // Initially disable login button until initialization completes
        binding.btnLogin.isEnabled = false

        // Login button click
        binding.btnLogin.setOnClickListener {
            // Provide haptic feedback for better touch affordance
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            binding.btnLogin.text = getString(R.string.login_button)
            val licenseKey = binding.etLicenseKey.text.toString().trim()



            val validationError = viewModel.validateLicenseKey(licenseKey)

            if (validationError != null) {
                showError(validationError)
                return@setOnClickListener
            }

            // Double-check initialization before authentication
            if (!viewModel.isAppInitialized()) {
                showError("KeyAuth not initialized. Retrying initialization...")
                viewModel.initializeApp()
                return@setOnClickListener
            }

            viewModel.authenticateWithLicense(licenseKey)
        }
        
        // Paste icon click (TextInputLayout end icon)
        binding.tilLicenseKey.setEndIconOnClickListener {
            pasteFromClipboard()
        }
        
        // Remember key checkbox
        binding.cbRememberKey.setOnCheckedChangeListener { _, isChecked ->
            securePreferences.setRememberLicense(isChecked)
            if (!isChecked) {
                securePreferences.clearLicenseKey()
                binding.cbAutoLogin.isChecked = false
                securePreferences.setAutoLogin(false)
            }
        }
        
        // Auto login checkbox
        binding.cbAutoLogin.setOnCheckedChangeListener { _, isChecked ->
            securePreferences.setAutoLogin(isChecked)
            if (isChecked && !binding.cbRememberKey.isChecked) {
                binding.cbRememberKey.isChecked = true
                securePreferences.setRememberLicense(true)
            }
        }
    }
    
    private fun setupObservers() {
        // Observe initialization state
        viewModel.initState.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    showStatus("Initializing KeyAuth application...")
                    // Disable login button during initialization
                    binding.btnLogin.isEnabled = false
                    binding.btnLogin.text = "Initializing..."
                }
                is NetworkResult.Success -> {
                    showStatus("✅ KeyAuth initialized successfully")
                    // Enable login button after successful initialization
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "LOGIN"

                    // Check if auto login is enabled and we have a saved key
                    if (securePreferences.getAutoLogin()) {
                        val savedKey = securePreferences.getLicenseKey()
                        if (!savedKey.isNullOrEmpty()) {
                            binding.etLicenseKey.setText(savedKey)
                            showStatus("🔄 Auto-login with saved key...")
                            viewModel.authenticateWithLicense(savedKey)
                        }
                    }
                }
                is NetworkResult.Error -> {
                    showError("❌ KeyAuth initialization failed: ${result.message}")
                    // Enable retry button
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "RETRY INIT"

                    // Set click listener for retry
                    binding.btnLogin.setOnClickListener {
                        showStatus("Retrying KeyAuth initialization...")
                        viewModel.initializeApp()
                    }
                }
                null -> {
                    // Initial state - keep button disabled
                    binding.btnLogin.isEnabled = false
                }
            }
        }
        
        // Observe login state
        viewModel.loginState.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    showStatus("🔐 Authenticating with KeyAuth...")
                }
                is NetworkResult.Success -> {
                    showStatus("✅ Authentication successful!")

                    // Save license key if remember is checked
                    if (binding.cbRememberKey.isChecked) {
                        securePreferences.saveLicenseKey(binding.etLicenseKey.text.toString().trim())
                    }

                    // Navigate to main activity
                    navigateToMainActivity()
                }
                is NetworkResult.Error -> {
                    val errorMessage = result.message ?: "Unknown error"
                    showError("❌ Authentication failed: $errorMessage")

                    // Clear corrupted session data for session-related errors
                    if (errorMessage.contains("session not found", ignoreCase = true) ||
                        errorMessage.contains("last code", ignoreCase = true) ||
                        errorMessage.contains("session expired", ignoreCase = true)) {
                        sessionService.clearCorruptedSession()
                    }
                }
                null -> {
                    // Initial state
                }
            }
        }
        
        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Only disable button during loading if app is initialized
            if (viewModel.isAppInitialized()) {
                binding.btnLogin.isEnabled = !isLoading
                if (!isLoading && binding.btnLogin.text != "LOGIN") {
                    binding.btnLogin.text = "LOGIN"
                    // Restore normal login click listener
                    setupLoginClickListener()
                }
            }
        }

        // Observe session restoration state
        viewModel.sessionRestoreState.observe(this) { result ->
            when (result) {
                is SessionRestoreResult.Success -> {
                    showStatus("✅ Session restored successfully!")
                    navigateToMainActivity()
                }
                is SessionRestoreResult.NoStoredSession -> {
                    Log.d("LoginActivity", "ℹ️ No stored session, showing login form")
                    showStatus("Please enter your license key")
                }
                is SessionRestoreResult.SessionExpired -> {
                    showStatus("⏰ Session expired, please login again")
                    // Clear auto-login if session expired
                    binding.cbAutoLogin.isChecked = false
                    securePreferences.setAutoLogin(false)
                }
                is SessionRestoreResult.HWIDMismatch -> {
                    showError("⚠️ Device changed detected. Please re-authenticate.")
                    // Clear stored data for security
                    securePreferences.clearAuthenticationData()
                    binding.cbAutoLogin.isChecked = false
                    securePreferences.setAutoLogin(false)
                }
                is SessionRestoreResult.Failed -> {
                    showError("❌ Session restoration failed: ${result.error}")
                }
                null -> {
                    // Initial state
                }
            }
        }

        // Observe auth flow state for detailed feedback
        lifecycleScope.launch {
            viewModel.authFlowState.collect { state ->
                when (state) {
                    AuthFlowState.CHECKING_STORED_SESSION -> {
                        showStatus("🔍 Checking stored session...")
                    }
                    AuthFlowState.VALIDATING_HWID -> {
                        showStatus("🔐 Validating device identity...")
                    }
                    AuthFlowState.REFRESHING_TOKEN -> {
                        showStatus("🔄 Refreshing authentication...")
                    }
                    AuthFlowState.AUTHENTICATING_WITH_LICENSE -> {
                        showStatus("🔐 Authenticating with stored credentials...")
                    }
                    AuthFlowState.AUTHENTICATED -> {
                        showStatus("✅ Authentication successful!")
                    }
                    AuthFlowState.HWID_MISMATCH -> {
                        showError("⚠️ Device identity changed")
                    }
                    AuthFlowState.SESSION_EXPIRED -> {
                        showStatus("⏰ Session expired")
                    }
                    AuthFlowState.FAILED -> {
                        showError("❌ Authentication failed")
                    }
                    else -> {
                        // Other states handled elsewhere
                    }
                }
            }
        }
    }

    /**
     * Setup normal login click listener
     */
    private fun setupLoginClickListener() {
        binding.btnLogin.setOnClickListener {
            val licenseKey = binding.etLicenseKey.text.toString().trim()
            val validationError = viewModel.validateLicenseKey(licenseKey)

            if (validationError != null) {
                showError(validationError)
                return@setOnClickListener
            }

            // Double-check initialization before authentication
            if (!viewModel.isAppInitialized()) {
                showError("KeyAuth not initialized. Retrying initialization...")
                viewModel.initializeApp()
                return@setOnClickListener
            }

            viewModel.authenticateWithLicense(licenseKey)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check initialization state when activity resumes
        if (!viewModel.isAppInitialized()) {
            showStatus("Re-initializing KeyAuth...")
            viewModel.initializeApp()
        } else {
            // Ensure UI is in correct state if already initialized
            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = "LOGIN"
            setupLoginClickListener()
        }
    }

    private fun loadSavedPreferences() {
        // Load saved preferences
        binding.cbRememberKey.isChecked = securePreferences.getRememberLicense()
        binding.cbAutoLogin.isChecked = securePreferences.getAutoLogin()
        
        // Load saved license key if remember is enabled
        if (securePreferences.getRememberLicense()) {
            val savedKey = securePreferences.getLicenseKey()
            if (!savedKey.isNullOrEmpty()) {
                binding.etLicenseKey.setText(savedKey)
            }
        }
    }
    
    private fun pasteFromClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val clipText = clipData.getItemAt(0).text?.toString()
            if (!clipText.isNullOrEmpty()) {
                binding.etLicenseKey.setText(clipText)
                Toast.makeText(this, "License key pasted", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showStatus(message: String) {
        // Determine snackbar type and duration based on message content
        when {
            message.contains("Initializing") || message.contains("初始化") -> {
                showInfoSnackbar(message, Snackbar.LENGTH_SHORT)
            }
            message.contains("successful") || message.contains("成功") -> {
                showSuccessSnackbar(message, Snackbar.LENGTH_LONG)
            }
            message.contains("Authenticating") || message.contains("认证") -> {
                showInfoSnackbar(message, Snackbar.LENGTH_SHORT)
            }
            else -> {
                showInfoSnackbar(message, Snackbar.LENGTH_SHORT)
            }
        }
    }

    private fun showError(message: String) {
        showErrorSnackbar(message, Snackbar.LENGTH_LONG)
    }

    private fun showSuccess(message: String) {
        showSuccessSnackbar(message, Snackbar.LENGTH_LONG)
    }

    // Material Design 3 Snackbar helper methods
    private fun showSuccessSnackbar(message: String, duration: Int) {
        val snackbar = Snackbar.make(binding.root, message, duration)
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.success_green))
        snackbar.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.setActionTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.show()
    }

    private fun showErrorSnackbar(message: String, duration: Int) {
        val snackbar = Snackbar.make(binding.root, message, duration)
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.error_red))
        snackbar.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.setActionTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.show()
    }

    private fun showInfoSnackbar(message: String, duration: Int) {
        val snackbar = Snackbar.make(binding.root, message, duration)
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.md3_primary))
        snackbar.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.setActionTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.show()
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)

        // Shared element transition for premium feel (logo)
        val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
            this,
            binding.ivBearLogo as android.view.View,
            "bear_logo_transition"
        )

        startActivity(intent, options.toBundle())
        finish()
    }

    /**
     * Clear corrupted session data that might cause "Session not found" errors
     * This forces a fresh authentication instead of trying to restore a broken session
     */
    // ...existing code... (clearCorruptedSessionData logic moved to SessionService)

}
