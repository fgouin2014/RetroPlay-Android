package com.retroplay

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager for emulator mode preferences (NATIVE vs RETROARCH)
 * Stores user's choice per console
 */
object GamepadPreferenceManager {
    
    private const val PREFS_NAME = "emulator_config"
    private const val KEY_PREFIX = "emulator_mode_"
    
    /**
     * Emulator modes available
     */
    enum class EmulatorMode {
        NATIVE,      // NativeComposeEmulatorActivity (Radial/Lemuroid gamepad)
        RETROARCH    // RetroArchEmulatorActivity (Pure RetroArch overlays)
    }
    
    /**
     * Get SharedPreferences instance
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save emulator mode for a specific console
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @param mode EmulatorMode to save
     */
    fun saveMode(context: Context, console: String, mode: EmulatorMode) {
        val prefs = getPrefs(context)
        prefs.edit().putString("$KEY_PREFIX$console", mode.name).apply()
        android.util.Log.i("GamepadPreferenceManager", "Saved mode for $console: $mode")
    }
    
    /**
     * Load emulator mode for a specific console
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @return EmulatorMode (defaults to NATIVE if not set)
     */
    fun loadMode(context: Context, console: String): EmulatorMode {
        val prefs = getPrefs(context)
        val modeString = prefs.getString("$KEY_PREFIX$console", EmulatorMode.NATIVE.name)
        
        return try {
            EmulatorMode.valueOf(modeString ?: EmulatorMode.NATIVE.name)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("GamepadPreferenceManager", "Invalid mode '$modeString' for $console, defaulting to NATIVE")
            EmulatorMode.NATIVE
        }
    }
    
    /**
     * Reset emulator mode to default (NATIVE) for a specific console
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     */
    fun resetToDefault(context: Context, console: String) {
        saveMode(context, console, EmulatorMode.NATIVE)
        android.util.Log.i("GamepadPreferenceManager", "Reset $console to default mode (NATIVE)")
    }
    
    /**
     * Check if console is using default mode (NATIVE)
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @return true if using NATIVE mode, false otherwise
     */
    fun isDefaultMode(context: Context, console: String): Boolean {
        return loadMode(context, console) == EmulatorMode.NATIVE
    }
    
    /**
     * Get human-readable label for emulator mode
     * 
     * @param mode EmulatorMode
     * @return String label
     */
    fun getModeLabel(mode: EmulatorMode): String {
        return when (mode) {
            EmulatorMode.NATIVE -> "NATIVE (Radial Gamepad)"
            EmulatorMode.RETROARCH -> "RETROARCH (RetroArch Overlays)"
        }
    }
    
    // ===== PER-GAME OVERRIDE FUNCTIONS =====
    
    private const val OVERRIDE_PREFIX = "game_override_"
    
    /**
     * Save emulator mode override for a specific game
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @param gameId Game ID (e.g., "duckhunt", "mario")
     * @param mode EmulatorMode to save as override
     */
    fun saveGameOverride(context: Context, console: String, gameId: String, mode: EmulatorMode) {
        val prefs = getPrefs(context)
        val key = "${OVERRIDE_PREFIX}${console}_${gameId}"
        prefs.edit().putString(key, mode.name).apply()
        android.util.Log.i("GamepadPreferenceManager", "Saved game override for $console/$gameId: $mode")
    }
    
    /**
     * Load emulator mode override for a specific game
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @param gameId Game ID (e.g., "duckhunt", "mario")
     * @return EmulatorMode override, or null if no override exists
     */
    fun loadGameOverride(context: Context, console: String, gameId: String): EmulatorMode? {
        val prefs = getPrefs(context)
        val key = "${OVERRIDE_PREFIX}${console}_${gameId}"
        val modeString = prefs.getString(key, null)
        
        return if (modeString != null) {
            try {
                EmulatorMode.valueOf(modeString)
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("GamepadPreferenceManager", "Invalid game override '$modeString' for $console/$gameId")
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Remove emulator mode override for a specific game
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @param gameId Game ID (e.g., "duckhunt", "mario")
     */
    fun removeGameOverride(context: Context, console: String, gameId: String) {
        val prefs = getPrefs(context)
        val key = "${OVERRIDE_PREFIX}${console}_${gameId}"
        prefs.edit().remove(key).apply()
        android.util.Log.i("GamepadPreferenceManager", "Removed game override for $console/$gameId")
    }
    
    /**
     * Get effective emulator mode for a specific game
     * Checks for game override first, then falls back to console default
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @param gameId Game ID (e.g., "duckhunt", "mario")
     * @return Effective EmulatorMode (game override or console default)
     */
    fun getEffectiveMode(context: Context, console: String, gameId: String): EmulatorMode {
        val gameOverride = loadGameOverride(context, console, gameId)
        return gameOverride ?: loadMode(context, console)
    }
    
    /**
     * Check if a game has an override
     * 
     * @param context Android context
     * @param console Console ID (e.g., "nes", "snes", "psx")
     * @param gameId Game ID (e.g., "duckhunt", "mario")
     * @return true if game has an override, false otherwise
     */
    fun hasGameOverride(context: Context, console: String, gameId: String): Boolean {
        return loadGameOverride(context, console, gameId) != null
    }
}

