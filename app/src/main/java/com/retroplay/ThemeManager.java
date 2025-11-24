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
        MATRIX("Matrix Green", "matrix_primary");
        
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
        int resId = getPrimaryColorResId(context);
        if (resId == 0) {
            Log.w(TAG, "Primary color resource not found, using fallback");
            return 0xFFFF3333; // Fallback red color
        }
        return context.getResources().getColor(resId, null);
    }
    
    public int getPrimaryColorResIdByName(Context context, String colorName) {
        return context.getResources().getIdentifier(colorName, "color", context.getPackageName());
    }
    
    public int getMediumColorResId(Context context) {
        Theme current = getCurrentTheme();
        String colorName;
        switch (current) {
            case KITT_RED:
                colorName = "kitt_medium_red";
                break;
            case AMBER:
                colorName = "amber_primary_dark";
                break;
            case MATRIX:
                colorName = "matrix_primary_dark";
                break;
            default:
                colorName = "kitt_medium_red";
        }
        return getPrimaryColorResIdByName(context, colorName);
    }
    
    public int getMediumColor(Context context) {
        int resId = getMediumColorResId(context);
        if (resId == 0) {
            Log.w(TAG, "Medium color resource not found, using fallback");
            return 0xFFFF6666; // Fallback red color
        }
        return context.getResources().getColor(resId, null);
    }
    
    public int getLightColorResId(Context context) {
        Theme current = getCurrentTheme();
        String colorName;
        switch (current) {
            case KITT_RED:
                colorName = "kitt_red_light";
                break;
            case AMBER:
                colorName = "amber_primary_light";
                break;
            case MATRIX:
                colorName = "matrix_primary_light";
                break;
            default:
                colorName = "kitt_red_light";
        }
        return getPrimaryColorResIdByName(context, colorName);
    }
    
    public int getLightColor(Context context) {
        int resId = getLightColorResId(context);
        if (resId == 0) {
            Log.w(TAG, "Light color resource not found, using fallback");
            return 0xFFFF9999; // Fallback red color
        }
        return context.getResources().getColor(resId, null);
    }
    
    public int getTextPrimaryColorResId(Context context) {
        Theme current = getCurrentTheme();
        String colorName;
        switch (current) {
            case KITT_RED:
                colorName = "kitt_red_light";
                break;
            case AMBER:
                colorName = "amber_primary_light";
                break;
            case MATRIX:
                colorName = "matrix_text_primary";
                break;
            default:
                colorName = "kitt_red_light";
        }
        return getPrimaryColorResIdByName(context, colorName);
    }
    
    public int getTextPrimaryColor(Context context) {
        int resId = getTextPrimaryColorResId(context);
        if (resId == 0) {
            Log.w(TAG, "Text primary color resource not found, using fallback");
            return 0xFFFF9999; // Fallback red color
        }
        return context.getResources().getColor(resId, null);
    }
    
    public int getTextSecondaryColorResId(Context context) {
        Theme current = getCurrentTheme();
        String colorName;
        switch (current) {
            case KITT_RED:
                colorName = "kitt_red_light";
                break;
            case AMBER:
                colorName = "amber_primary_light";
                break;
            case MATRIX:
                colorName = "matrix_text_secondary";
                break;
            default:
                colorName = "kitt_red_light";
        }
        return getPrimaryColorResIdByName(context, colorName);
    }
    
    public int getTextSecondaryColor(Context context) {
        int resId = getTextSecondaryColorResId(context);
        if (resId == 0) {
            Log.w(TAG, "Text secondary color resource not found, using fallback");
            return 0xFFFF9999; // Fallback red color
        }
        return context.getResources().getColor(resId, null);
    }
    
    public int getHeaderBackgroundColorResId(Context context) {
        Theme current = getCurrentTheme();
        String colorName;
        switch (current) {
            case KITT_RED:
                colorName = "kitt_black";
                break;
            case AMBER:
                colorName = "amber_surface";
                break;
            case MATRIX:
                colorName = "matrix_surface";
                break;
            default:
                colorName = "kitt_black";
        }
        return getPrimaryColorResIdByName(context, colorName);
    }
    
    public int getHeaderBackgroundColor(Context context) {
        int resId = getHeaderBackgroundColorResId(context);
        if (resId == 0) {
            Log.w(TAG, "Header background color resource not found, using fallback");
            return 0xFF000000; // Fallback black color
        }
        return context.getResources().getColor(resId, null);
    }
}

