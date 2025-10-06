package com.bearmod.loader.utils;

import android.content.Context;

public class SessionManager {
    
    private final com.bearmod.loader.utils.SecurePrefsAdapterImpl securePreferences;
    
    public SessionManager(Context context) {
    securePreferences = new com.bearmod.loader.utils.SecurePrefsAdapterImpl(context);
    }
    
    public void clearSession() {
    securePreferences.clearAll();
    }

    /**
     * Clear only the stored session token and refresh token (preserves other prefs)
     */
    public void clearSessionToken() {
    securePreferences.clearSessionToken();
    securePreferences.clearRefreshToken();
    }

    /**
     * Clear only authentication-related data (keeps user preferences)
     */
    public void clearAuthenticationData() {
    securePreferences.clearAuthenticationData();
    }
    
    public boolean isLoggedIn() {
    return securePreferences.getLicenseKey() != null && !securePreferences.getLicenseKey().isEmpty();
    }
}
