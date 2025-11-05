package com.retroplay.smartconfig

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Smart Configuration Preferences Manager
 * 
 * Manages user preferences for Smart Config auto-apply:
 * - Global enable/disable
 * - Per-game overrides
 * - Per-feature toggles (Run-Ahead, Rewind, Overlay)
 */
object SmartConfigPreferences {
    private const val TAG = "SmartConfigPrefs"
    private const val PREFS_NAME = "smart_config_preferences"
    
    // Global settings
    private const val KEY_ENABLED = "smart_config_enabled"
    private const val KEY_AUTO_APPLY_RUNAHEAD = "smart_config_auto_runahead"
    private const val KEY_AUTO_APPLY_REWIND = "smart_config_auto_rewind"
    private const val KEY_AUTO_APPLY_OVERLAY = "smart_config_auto_overlay"
    private const val KEY_SHOW_OSD_NOTIFICATIONS = "smart_config_show_osd"
    
    /**
     * Check if Smart Config is globally enabled.
     */
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)  // Default: enabled
    }
    
    /**
     * Enable/disable Smart Config globally.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.i(TAG, "Smart Config globally ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    /**
     * Check if auto-apply Run-Ahead is enabled.
     */
    fun isAutoApplyRunAheadEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_APPLY_RUNAHEAD, true)
    }
    
    /**
     * Check if auto-apply Rewind is enabled.
     */
    fun isAutoApplyRewindEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_APPLY_REWIND, true)
    }
    
    /**
     * Check if auto-apply Overlay selection is enabled.
     */
    fun isAutoApplyOverlayEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_APPLY_OVERLAY, false)  // Default: disabled (user choice)
    }
    
    /**
     * Check if OSD notifications should be shown.
     */
    fun shouldShowOSDNotifications(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_OSD_NOTIFICATIONS, true)
    }
    
    /**
     * Get per-game override for Run-Ahead frames.
     * 
     * Returns null if no override (use Smart Config default).
     */
    fun getGameRunAheadOverride(context: Context, gameCRC: String): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "game_${gameCRC}_runahead"
        return if (prefs.contains(key)) {
            prefs.getInt(key, -1).takeIf { it >= 0 }
        } else {
            null
        }
    }
    
    /**
     * Set per-game override for Run-Ahead frames.
     * 
     * @param frames 0-4, or null to remove override
     */
    fun setGameRunAheadOverride(context: Context, gameCRC: String, frames: Int?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "game_${gameCRC}_runahead"
        
        if (frames == null) {
            prefs.edit().remove(key).apply()
            Log.d(TAG, "Removed Run-Ahead override for game $gameCRC")
        } else {
            prefs.edit().putInt(key, frames.coerceIn(0, 4)).apply()
            Log.d(TAG, "Set Run-Ahead override for game $gameCRC: $frames frames")
        }
    }
    
    /**
     * Get all Smart Config settings at once.
     */
    fun getSettings(context: Context): SmartConfigSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SmartConfigSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            autoApplyRunAhead = prefs.getBoolean(KEY_AUTO_APPLY_RUNAHEAD, true),
            autoApplyRewind = prefs.getBoolean(KEY_AUTO_APPLY_REWIND, true),
            autoApplyOverlay = prefs.getBoolean(KEY_AUTO_APPLY_OVERLAY, false),
            showOSDNotifications = prefs.getBoolean(KEY_SHOW_OSD_NOTIFICATIONS, true)
        )
    }
    
    /**
     * Save all Smart Config settings.
     */
    fun saveSettings(context: Context, settings: SmartConfigSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, settings.enabled)
            putBoolean(KEY_AUTO_APPLY_RUNAHEAD, settings.autoApplyRunAhead)
            putBoolean(KEY_AUTO_APPLY_REWIND, settings.autoApplyRewind)
            putBoolean(KEY_AUTO_APPLY_OVERLAY, settings.autoApplyOverlay)
            putBoolean(KEY_SHOW_OSD_NOTIFICATIONS, settings.showOSDNotifications)
            apply()
        }
        Log.i(TAG, "Smart Config settings saved: $settings")
    }
}

/**
 * Smart Config settings data class.
 */
data class SmartConfigSettings(
    val enabled: Boolean,
    val autoApplyRunAhead: Boolean,
    val autoApplyRewind: Boolean,
    val autoApplyOverlay: Boolean,
    val showOSDNotifications: Boolean
)

