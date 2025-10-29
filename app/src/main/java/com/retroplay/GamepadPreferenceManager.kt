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
}

