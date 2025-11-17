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
        return context.getResources().getColor(getPrimaryColorResId(context), null);
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
        return context.getResources().getColor(getMediumColorResId(context), null);
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
        return context.getResources().getColor(getLightColorResId(context), null);
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
        return context.getResources().getColor(getTextPrimaryColorResId(context), null);
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
        return context.getResources().getColor(getTextSecondaryColorResId(context), null);
    }
}

