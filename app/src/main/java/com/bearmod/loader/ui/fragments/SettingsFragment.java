package com.bearmod.loader.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bearmod.loader.R;
import com.bearmod.loader.ui.LoginActivity;
import com.bearmod.loader.ui.MainActivity;
import com.bearmod.loader.utils.LanguageManager;
import com.bearmod.loader.utils.SessionManager;
import com.bearmod.loader.viewmodel.AuthViewModel;

public class SettingsFragment extends Fragment {

    private TextView tvCurrentLanguage;
    private TextView tvLicenseKey;
    private TextView tvLicenseExpiry;
    private TextView tvTimeRemaining;
    private TextView tvLastValidation;
    private TextView tvLicenseStatus;

    private LanguageManager languageManager;
    private SessionManager sessionManager;
    private com.bearmod.loader.utils.SecurePrefsAdapter securePrefsAdapter;
    private com.bearmod.loader.utils.SecurePrefsAdapterImpl securePrefsAdapterImpl;
    private AuthViewModel authViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        languageManager = new LanguageManager(requireContext());
    sessionManager = new SessionManager(requireContext());
    // Use adapter to access secure preferences in UI code
    securePrefsAdapterImpl = new com.bearmod.loader.utils.SecurePrefsAdapterImpl(requireContext());
    securePrefsAdapter = securePrefsAdapterImpl;
        authViewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        initViews(view);
        setupClickListeners();
        setupObservers();
        updateLanguage();

        return view;
    }

    private void initViews(View view) {
        tvCurrentLanguage = view.findViewById(R.id.tvCurrentLanguage);
        tvLicenseKey = view.findViewById(R.id.tvLicenseKey);
        tvLicenseExpiry = view.findViewById(R.id.tvLicenseExpiry);
        tvTimeRemaining = view.findViewById(R.id.tvTimeRemaining);
        tvLastValidation = view.findViewById(R.id.tvLastValidation);
        tvLicenseStatus = view.findViewById(R.id.tvLicenseStatus);

        // Load user information
        loadUserInformation();
    }

    private void setupClickListeners() {
        // Language selection card click - Fix: Ensure proper view reference
        View cardLanguageSelection = requireView().findViewById(R.id.cardLanguageSelection);
        if (cardLanguageSelection != null) {
            cardLanguageSelection.setOnClickListener(v -> {
                android.util.Log.d("SettingsFragment", "Language card clicked");
                showLanguageDialog();
            });
        } else {
            android.util.Log.w("SettingsFragment", "Language card not found in layout");
        }
    }

    private void setupObservers() {
        // No observers needed for remaining UI elements
    }

    private void showLanguageDialog() {
        String[] languages = languageManager.isChineseEnabled() ? 
            new String[]{"中文", "English"} : 
            new String[]{"Chinese", "English"};
        
        int currentSelection = languageManager.isChineseEnabled() ? 0 : 1;
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(languageManager.isChineseEnabled() ? "选择语言" : "Select Language")
            .setSingleChoiceItems(languages, currentSelection, (dialog, which) -> {
                boolean enableChinese = (which == 0);
                languageManager.setChineseEnabled(enableChinese);
                
                // Update current fragment
                updateLanguage();
                
                // Notify MainActivity to update all fragments
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).updateLanguage();
                }
                
                dialog.dismiss();
            })
            .setNegativeButton(languageManager.isChineseEnabled() ? "取消" : "Cancel", null)
            .show();
    }

    private void showLogoutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(languageManager.isChineseEnabled() ? "确认退出" : "Confirm Logout")
            .setMessage(languageManager.isChineseEnabled() ? 
                "您确定要退出登录吗？" : "Are you sure you want to logout?")
            .setPositiveButton(languageManager.isChineseEnabled() ? "退出" : "Logout", (dialog, which) -> {
                performLogout();
            })
            .setNegativeButton(languageManager.isChineseEnabled() ? "取消" : "Cancel", null)
            .show();
    }

    private void performLogout() {
        // Clear session
        sessionManager.clearSession();
        
        // Navigate to login
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void loadUserInformation() {
        // Load license information
        updateLicenseInformation();
    }



    private void updateLicenseInformation() {
        // Try to get license key from adapter for better reliability
        String licenseKey = securePrefsAdapter.getLicenseKey();
        if (licenseKey == null || licenseKey.isEmpty()) {
            licenseKey = securePrefsAdapter.getBoundLicenseKey();
        }

        String sessionToken = securePrefsAdapter.getSessionToken();
        long tokenExpiry = securePrefsAdapter.getTokenExpiryTime();

        android.util.Log.d("SettingsFragment", "License info update - Key: " +
            (licenseKey == null || licenseKey.isEmpty() ? "null/empty" : "available") +
            ", Token: " + (sessionToken == null || sessionToken.isEmpty() ? "null/empty" : "available") +
            ", Expiry: " + tokenExpiry);

        if (tvLicenseKey != null) {
            if (licenseKey != null && !licenseKey.isEmpty()) {
                // Mask the license key for security (show first 4 and last 4 characters)
                String maskedKey;
                if (licenseKey.length() >= 8) {
                    maskedKey = licenseKey.substring(0, 4) + "-****-****-" +
                               licenseKey.substring(licenseKey.length() - 4);
                } else {
                    maskedKey = "****-****-****-****"; // Fallback for short keys
                }
                String keyText = languageManager.isChineseEnabled() ?
                    "密钥: " + maskedKey : "Key: " + maskedKey;
                tvLicenseKey.setText(keyText);
            } else {
                String keyText = languageManager.isChineseEnabled() ?
                    "密钥: 不可用" : "Key: Not Available";
                tvLicenseKey.setText(keyText);
            }
        }

        // Update expiry information based on actual token data
    boolean isValid = sessionToken != null && !sessionToken.isEmpty() && securePrefsAdapterImpl.isSessionTokenValid();

        if (tvLicenseExpiry != null) {
            if (tokenExpiry > 0) {
                java.text.SimpleDateFormat dateFormat = languageManager.isChineseEnabled() ?
                    new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINESE) :
                    new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.ENGLISH);
                String expiryDate = dateFormat.format(new java.util.Date(tokenExpiry));
                String expiryText = languageManager.isChineseEnabled() ?
                    "到期时间: " + expiryDate : "Expires: " + expiryDate;
                tvLicenseExpiry.setText(expiryText);
            } else {
                String expiryText = languageManager.isChineseEnabled() ?
                    "到期时间: 未设置" : "Expires: Not Set";
                tvLicenseExpiry.setText(expiryText);
            }
        }

        if (tvTimeRemaining != null) {
            if (tokenExpiry > 0) {
                long currentTime = System.currentTimeMillis();
                long timeRemaining = tokenExpiry - currentTime;

                if (timeRemaining > 0) {
                    long days = timeRemaining / (24 * 60 * 60 * 1000);
                    long hours = (timeRemaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
                    long minutes = (timeRemaining % (60 * 60 * 1000)) / (60 * 1000);

                    String timeText;
                    if (languageManager.isChineseEnabled()) {
                        if (days > 0) {
                            timeText = "剩余时间: " + days + "天 " + hours + "小时";
                        } else if (hours > 0) {
                            timeText = "剩余时间: " + hours + "小时 " + minutes + "分钟";
                        } else {
                            timeText = "剩余时间: " + minutes + "分钟";
                        }
                    } else {
                        if (days > 0) {
                            timeText = "Time Remaining: " + days + "d " + hours + "h";
                        } else if (hours > 0) {
                            timeText = "Time Remaining: " + hours + "h " + minutes + "m";
                        } else {
                            timeText = "Time Remaining: " + minutes + "m";
                        }
                    }
                    tvTimeRemaining.setText(timeText);
                } else {
                    String timeText = languageManager.isChineseEnabled() ?
                        "剩余时间: 已过期" : "Time Remaining: Expired";
                    tvTimeRemaining.setText(timeText);
                }
            } else {
                String timeText = languageManager.isChineseEnabled() ?
                    "剩余时间: 未知" : "Time Remaining: Unknown";
                tvTimeRemaining.setText(timeText);
            }
        }

        if (tvLastValidation != null) {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
            String currentTime = dateFormat.format(new java.util.Date());
            String validationText = languageManager.isChineseEnabled() ?
                "最后验证: " + currentTime : "Last Validation: " + currentTime;
            tvLastValidation.setText(validationText);
        }

        if (tvLicenseStatus != null) {
            String statusText = languageManager.isChineseEnabled() ?
                (isValid ? "有效" : "无效") : (isValid ? "Valid" : "Invalid");
            tvLicenseStatus.setText(statusText);
            int colorRes = isValid ? R.color.success_green : R.color.error_red;
            tvLicenseStatus.setTextColor(getResources().getColor(colorRes, null));
        }
    }



    public void updateLanguage() {
        if (tvCurrentLanguage != null) {
            String currentLang = languageManager.isChineseEnabled() ? "中文" : "English";
            tvCurrentLanguage.setText(languageManager.isChineseEnabled() ?
                "切换语言" : "Switch Language");
        }

        // Reload all information with new language
        loadUserInformation();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLanguage();
    }
}
