package com.retroplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Theme Manager for RetroPlay
 * Manages theme switching between KITT Red, Amber, and Cyan themes
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREFS_NAME = "retroplay_theme_prefs";
    private static final String KEY_THEME = "selected_theme";
    
    public enum Theme {
        KITT_RED("KITT Red", "kitt_red"),
        AMBER("Amber", "amber_primary"),
        CYAN("Cyan", "cyan_primary");
        
        private final String displayName;
        private final String primaryColorName;
        
        Theme(String displayName, String primaryColorName) {
            this.displayName = displayName;
            this.primaryColorName = primaryColorName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getPrimaryColorName() {
            return primaryColorName;
        }
        
        public static Theme fromString(String name) {
            try {
                return Theme.valueOf(name);
            } catch (IllegalArgumentException e) {
                return KITT_RED; // Default
            }
        }
    }
    
    private static ThemeManager instance;
    private SharedPreferences prefs;
    
    private ThemeManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }
    
    public Theme getCurrentTheme() {
        String themeName = prefs.getString(KEY_THEME, Theme.KITT_RED.name());
        return Theme.fromString(themeName);
    }
    
    public void setTheme(Theme theme) {
        prefs.edit().putString(KEY_THEME, theme.name()).apply();
        Log.i(TAG, "Theme changed to: " + theme.getDisplayName());
    }
    
    public int getPrimaryColorResId(Context context) {
        Theme current = getCurrentTheme();
        String colorName = current.getPrimaryColorName();
        return context.getResources().getIdentifier(colorName, "color", context.getPackageName());
    }
    
    public int getPrimaryColor(Context context) {
        return context.getResources().getColor(getPrimaryColorResId(context), null);
    }
}

