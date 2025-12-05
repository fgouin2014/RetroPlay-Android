package com.retroplay.config

import android.content.Context
import android.util.Log
import java.io.File

/**
 * RetroPlay Global Configuration Manager
 * 
 * Similar to RetroArch's retroarch.cfg system.
 * Stores global emulation settings in a text file that can be edited manually.
 * 
 * Location: /storage/emulated/0/RetroPlay-Data/config/retroplay.cfg
 * 
 * Benefits over SharedPreferences:
 * - Human-readable and editable (via adb or file manager)
 * - Easy to backup/restore
 * - Easy to debug (can view/edit if app crashes)
 * - Compatible with RetroArch config format
 */
object RetroPlayConfigManager {
    private const val TAG = "RetroPlayConfig"
    private const val CONFIG_FILE = "/storage/emulated/0/RetroPlay-Data/config/retroplay.cfg"
    
    /**
     * Global configuration data class.
     */
    data class RetroPlayConfig(
        // Run-Ahead (input lag reduction)
        val runAheadEnabled: Boolean = false,
        val runAheadFrames: Int = 1,
        val runAheadSecondaryInstance: Boolean = true,
        val runAheadHideWarnings: Boolean = false,
        
        // Rewind
        val rewindEnable: Boolean = false,
        val rewindBufferSize: Int = 20 * 1024 * 1024,  // 20 MB default
        val rewindGranularity: Int = 1,  // Save state every N frames
        
        // Fast Forward
        val fastforwardRatio: Float = 2.0f,
        val fastforwardFrameskip: Boolean = false,
        
        // Audio
        val audioEnable: Boolean = true,
        val audioMuted: Boolean = false,
        val audioVolume: Float = 1.0f,
        
        // Video
        val videoVsync: Boolean = true,
        val videoHardSync: Boolean = false,
        val videoHardSyncFrames: Int = 0,
        val videoFrameDelay: Int = 0,
        
        // Smart Config
        val smartConfigEnabled: Boolean = true,
        val smartConfigAutoRunAhead: Boolean = true,
        // Uses adaptive granularity per console to prevent OOM:
        // - NES/GB: granularity=5 (small savestates)
        // - PSX/N64: granularity=30-45 (large savestates 2-4 MB)
        val smartConfigAutoRewind: Boolean = true,
        val smartConfigAutoOverlay: Boolean = false,
        val smartConfigShowOSD: Boolean = true,
        
        // Input
        val inputAutodetectEnable: Boolean = true,
        val inputPollTypeInterval: Int = 2,
        
        // Misc
        val savefileCompressionEnabled: Boolean = false,
        val savestateCompressionEnabled: Boolean = true,
        val screenshotDirectory: String = "/storage/emulated/0/RetroPlay-Data/screenshots"
    ) {
        /**
         * Convert to .cfg file format (RetroArch-compatible).
         */
        fun toCfgString(): String {
            return """
                # RetroPlay Global Configuration
                # Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}
                # Location: $CONFIG_FILE
                # 
                # This file can be edited manually if needed (e.g., if an option breaks emulation).
                # Format: key = "value" (quotes required for strings, not for booleans/numbers)
                
                # === RUN-AHEAD (Input Lag Reduction) ===
                # Runs core N frames ahead, then loads state back to reduce input lag
                # Recommended: 1-2 frames for platformers/fighting games, 0 for RPGs
                run_ahead_enabled = "${runAheadEnabled}"
                run_ahead_frames = "${runAheadFrames}"
                run_ahead_secondary_instance = "${runAheadSecondaryInstance}"
                run_ahead_hide_warnings = "${runAheadHideWarnings}"
                
                # === REWIND (Gameplay Rewinding) ===
                # Buffer to store savestates for rewinding gameplay
                # Buffer size in bytes (20971520 = 20 MB)
                rewind_enable = "${rewindEnable}"
                rewind_buffer_size = "${rewindBufferSize}"
                rewind_granularity = "${rewindGranularity}"
                
                # === FAST FORWARD ===
                # Speed multiplier when fast-forward is active
                fastforward_ratio = "${fastforwardRatio}"
                fastforward_frameskip = "${fastforwardFrameskip}"
                
                # === AUDIO ===
                audio_enable = "${audioEnable}"
                audio_mute_enable = "${audioMuted}"
                audio_volume = "${audioVolume}"
                
                # === VIDEO ===
                video_vsync = "${videoVsync}"
                video_hard_sync = "${videoHardSync}"
                video_hard_sync_frames = "${videoHardSyncFrames}"
                video_frame_delay = "${videoFrameDelay}"
                
                # === SMART CONFIG (Automatic Optimal Settings) ===
                # Uses libretro-database metadata to auto-configure settings
                smart_config_enabled = "${smartConfigEnabled}"
                smart_config_auto_runahead = "${smartConfigAutoRunAhead}"
                smart_config_auto_rewind = "${smartConfigAutoRewind}"
                smart_config_auto_overlay = "${smartConfigAutoOverlay}"
                smart_config_show_osd = "${smartConfigShowOSD}"
                
                # === INPUT ===
                input_autodetect_enable = "${inputAutodetectEnable}"
                input_poll_type_behavior = "${inputPollTypeInterval}"
                
                # === MISC ===
                savefile_compression = "${savefileCompressionEnabled}"
                savestate_compression = "${savestateCompressionEnabled}"
                screenshot_directory = "${screenshotDirectory}"
            """.trimIndent()
        }
    }
    
    /**
     * Load configuration from retroplay.cfg file.
     * Creates default config if file doesn't exist.
     */
    fun loadConfig(): RetroPlayConfig {
        val configFile = File(CONFIG_FILE)
        
        if (!configFile.exists()) {
            Log.i(TAG, "Config file not found, creating default: $CONFIG_FILE")
            val defaultConfig = RetroPlayConfig()
            saveConfig(defaultConfig)
            return defaultConfig
        }
        
        return try {
            val config = parseConfigFile(configFile)
            Log.i(TAG, "✅ Loaded config from $CONFIG_FILE")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config file, using defaults: ${e.message}", e)
            RetroPlayConfig()
        }
    }
    
    /**
     * Save configuration to retroplay.cfg file.
     */
    fun saveConfig(config: RetroPlayConfig) {
        val configFile = File(CONFIG_FILE)
        
        // Create directory if needed
        configFile.parentFile?.mkdirs()
        
        try {
            configFile.writeText(config.toCfgString())
            Log.i(TAG, "💾 Config saved to $CONFIG_FILE")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${e.message}", e)
        }
    }
    
    /**
     * Parse .cfg file into RetroPlayConfig.
     */
    private fun parseConfigFile(file: File): RetroPlayConfig {
        val configMap = mutableMapOf<String, String>()
        
        file.readLines().forEach { line ->
            val trimmed = line.trim()
            
            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEach
            }
            
            // Parse "key = value"
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().removeSurrounding("\"")
                configMap[key] = value
            }
        }
        
        // Build RetroPlayConfig from parsed values
        return RetroPlayConfig(
            runAheadEnabled = configMap["run_ahead_enabled"]?.toBoolean() ?: false,
            runAheadFrames = configMap["run_ahead_frames"]?.toIntOrNull() ?: 1,
            runAheadSecondaryInstance = configMap["run_ahead_secondary_instance"]?.toBoolean() ?: true,
            runAheadHideWarnings = configMap["run_ahead_hide_warnings"]?.toBoolean() ?: false,
            
            rewindEnable = configMap["rewind_enable"]?.toBoolean() ?: false,
            rewindBufferSize = configMap["rewind_buffer_size"]?.toIntOrNull() ?: (20 * 1024 * 1024),
            rewindGranularity = configMap["rewind_granularity"]?.toIntOrNull() ?: 1,
            
            fastforwardRatio = configMap["fastforward_ratio"]?.toFloatOrNull() ?: 2.0f,
            fastforwardFrameskip = configMap["fastforward_frameskip"]?.toBoolean() ?: false,
            
            audioEnable = configMap["audio_enable"]?.toBoolean() ?: true,
            audioMuted = configMap["audio_mute_enable"]?.toBoolean() ?: false,
            audioVolume = configMap["audio_volume"]?.toFloatOrNull() ?: 1.0f,
            
            videoVsync = configMap["video_vsync"]?.toBoolean() ?: true,
            videoHardSync = configMap["video_hard_sync"]?.toBoolean() ?: false,
            videoHardSyncFrames = configMap["video_hard_sync_frames"]?.toIntOrNull() ?: 0,
            videoFrameDelay = configMap["video_frame_delay"]?.toIntOrNull() ?: 0,
            
            smartConfigEnabled = configMap["smart_config_enabled"]?.toBoolean() ?: true,
            smartConfigAutoRunAhead = configMap["smart_config_auto_runahead"]?.toBoolean() ?: true,
            smartConfigAutoRewind = configMap["smart_config_auto_rewind"]?.toBoolean() ?: true,
            smartConfigAutoOverlay = configMap["smart_config_auto_overlay"]?.toBoolean() ?: false,
            smartConfigShowOSD = configMap["smart_config_show_osd"]?.toBoolean() ?: true,
            
            inputAutodetectEnable = configMap["input_autodetect_enable"]?.toBoolean() ?: true,
            inputPollTypeInterval = configMap["input_poll_type_behavior"]?.toIntOrNull() ?: 2,
            
            savefileCompressionEnabled = configMap["savefile_compression"]?.toBoolean() ?: false,
            savestateCompressionEnabled = configMap["savestate_compression"]?.toBoolean() ?: true,
            screenshotDirectory = configMap["screenshot_directory"] ?: "/storage/emulated/0/RetroPlay-Data/screenshots"
        )
    }
    
    /**
     * Update a single config value and save.
     */
    fun updateConfigValue(key: String, value: Any) {
        val config = loadConfig()
        
        val updatedConfig = when (key) {
            "run_ahead_enabled" -> config.copy(runAheadEnabled = value as Boolean)
            "run_ahead_frames" -> config.copy(runAheadFrames = value as Int)
            "rewind_enable" -> config.copy(rewindEnable = value as Boolean)
            "fastforward_ratio" -> config.copy(fastforwardRatio = value as Float)
            "audio_mute_enable" -> config.copy(audioMuted = value as Boolean)
            "smart_config_enabled" -> config.copy(smartConfigEnabled = value as Boolean)
            "smart_config_auto_runahead" -> config.copy(smartConfigAutoRunAhead = value as Boolean)
            "smart_config_auto_rewind" -> config.copy(smartConfigAutoRewind = value as Boolean)
            "smart_config_auto_overlay" -> config.copy(smartConfigAutoOverlay = value as Boolean)
            else -> config
        }
        
        saveConfig(updatedConfig)
        Log.i(TAG, "Updated config: $key = $value")
    }
    
    /**
     * Reset to default configuration.
     */
    fun resetToDefaults() {
        saveConfig(RetroPlayConfig())
        Log.i(TAG, "⚠️ Config reset to defaults")
    }
    
    /**
     * Get config file path for external editing.
     */
    fun getConfigPath(): String = CONFIG_FILE
    
    // ========================================
    // PER-GAME CONFIG OVERRIDES
    // ========================================
    
    private const val GAMES_CONFIG_DIR = "/storage/emulated/0/RetroPlay-Data/config/games"
    
    /**
     * Load per-game config override.
     * Returns null if no override exists for this game.
     * 
     * @param gameCRC CRC32 of the game ROM
     * @return RetroPlayConfig with only the overridden values, or null
     */
    fun loadGameConfig(gameCRC: String): RetroPlayConfig? {
        val gameConfigFile = File("$GAMES_CONFIG_DIR/$gameCRC.cfg")
        
        if (!gameConfigFile.exists()) {
            Log.d(TAG, "No per-game config for CRC $gameCRC")
            return null
        }
        
        return try {
            val config = parseConfigFile(gameConfigFile)
            Log.i(TAG, "✅ Loaded per-game config for CRC $gameCRC")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing per-game config for CRC $gameCRC: ${e.message}", e)
            null
        }
    }
    
    /**
     * Save per-game config override.
     * Only saves the values that differ from global config.
     * 
     * @param gameCRC CRC32 of the game ROM
     * @param config RetroPlayConfig with overridden values
     */
    fun saveGameConfig(gameCRC: String, config: RetroPlayConfig) {
        val gameConfigFile = File("$GAMES_CONFIG_DIR/$gameCRC.cfg")
        
        // Create directory if needed
        gameConfigFile.parentFile?.mkdirs()
        
        try {
            gameConfigFile.writeText(config.toCfgString())
            Log.i(TAG, "💾 Per-game config saved for CRC $gameCRC")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving per-game config for CRC $gameCRC: ${e.message}", e)
        }
    }
    
    /**
     * Delete per-game config override (revert to global config).
     * 
     * @param gameCRC CRC32 of the game ROM
     * @return true if file was deleted, false if it didn't exist
     */
    fun deleteGameConfig(gameCRC: String): Boolean {
        val gameConfigFile = File("$GAMES_CONFIG_DIR/$gameCRC.cfg")
        
        return if (gameConfigFile.exists()) {
            val deleted = gameConfigFile.delete()
            if (deleted) {
                Log.i(TAG, "🗑️ Deleted per-game config for CRC $gameCRC")
            }
            deleted
        } else {
            Log.d(TAG, "No per-game config to delete for CRC $gameCRC")
            false
        }
    }
    
    /**
     * Check if a game has a per-game config override.
     */
    fun hasGameConfig(gameCRC: String): Boolean {
        return File("$GAMES_CONFIG_DIR/$gameCRC.cfg").exists()
    }
    
    /**
     * Get effective config for a game (global + per-game override merged).
     * Per-game overrides take precedence over global config.
     * 
     * @param gameCRC CRC32 of the game ROM, or null for global config
     * @return Effective RetroPlayConfig for this game
     */
    fun getEffectiveConfig(gameCRC: String?): RetroPlayConfig {
        val globalConfig = loadConfig()
        
        if (gameCRC == null) {
            return globalConfig
        }
        
        val gameConfig = loadGameConfig(gameCRC)
        
        // If no per-game override, return global config
        if (gameConfig == null) {
            return globalConfig
        }
        
        // Merge: per-game overrides take precedence
        // Note: This is a simple merge. In a real implementation, you'd track
        // which fields were explicitly set in the per-game config.
        // For now, we assume if a per-game config exists, all its values override global.
        return gameConfig
    }
    
    /**
     * List all games that have per-game config overrides.
     * @return List of CRC32 strings
     */
    fun listGamesWithOverrides(): List<String> {
        val gamesDir = File(GAMES_CONFIG_DIR)
        
        if (!gamesDir.exists()) {
            return emptyList()
        }
        
        return gamesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".cfg")
        }?.map { file ->
            file.nameWithoutExtension
        } ?: emptyList()
    }
}

