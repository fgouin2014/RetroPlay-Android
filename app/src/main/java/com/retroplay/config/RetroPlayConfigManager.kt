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
 * Structure (like saves):
 * - Global: /storage/emulated/0/GameLibrary-Data/config/{console}/retroplay.cfg
 * - Per-game: /storage/emulated/0/GameLibrary-Data/config/{console}/{gameName}.cfg
 * 
 * Benefits over SharedPreferences:
 * - Human-readable and editable (via adb or file manager)
 * - Easy to backup/restore
 * - Easy to debug (can view/edit if app crashes)
 * - Compatible with RetroArch config format
 * - Organized by console (like saves system)
 */
object RetroPlayConfigManager {
    private const val TAG = "RetroPlayConfig"
    private const val CONFIG_BASE_DIR = "/storage/emulated/0/GameLibrary-Data/config"
    
    // For testing: allows override of base directory
    @Volatile
    private var testBaseDir: String? = null
    
    /**
     * Set a custom base directory for testing purposes.
     * Should only be called from test code.
     */
    internal fun setTestBaseDir(baseDir: String?) {
        testBaseDir = baseDir
    }
    
    /**
     * Get the effective base directory (test override or default).
     */
    private fun getBaseDir(): String {
        return testBaseDir ?: CONFIG_BASE_DIR
    }
    
    /**
     * Extract core name from core file path.
     * Example: "fceumm_libretro_android.so" -> "fceumm"
     */
    private fun extractCoreName(coreFilePath: String?): String {
        if (coreFilePath == null) return "retroplay" // Fallback
        
        val fileName = coreFilePath.substringAfterLast("/")
        return fileName
            .replace("_libretro_android.so", "")
            .replace(".so", "")
            .takeIf { it.isNotEmpty() } ?: "retroplay"
    }
    
    /**
     * Get config file path for a console (global config).
     * Uses core name if provided, otherwise falls back to "retroplay.cfg"
     */
    private fun getConfigFilePath(console: String, coreName: String? = null): String {
        val configFileName = if (coreName != null) {
            val cleanCoreName = extractCoreName(coreName)
            "$cleanCoreName.cfg"
        } else {
            "retroplay.cfg" // Fallback
        }
        return "${getBaseDir()}/$console/$configFileName"
    }
    
    /**
     * Get per-game config file path.
     */
    private fun getGameConfigFilePath(console: String, gameName: String): String {
        // Sanitize gameName for filename (same as saves system)
        val sanitizedName = gameName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "${getBaseDir()}/$console/$sanitizedName.cfg"
    }
    
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
        val screenshotDirectory: String = "/storage/emulated/0/RetroPlay-Data/screenshots",
        
        // Zapper Configuration
        val zapperEnabled: Boolean = false,           // Activer le zapper pour ce jeu
        val zapperPort: Int = 1,                      // Port zapper (0 = Port 1, 1 = Port 2)
        val zapperTriggerDelay: Int = 0,             // Délai du trigger en ms
        val zapperTriggerOnTouch: Boolean = true,    // Trigger au touch DOWN ou UP
        val zapperPulseDuration: Int = 50,            // Durée du pulse en ms
        val zapperAllowOffscreen: Boolean = false     // Permettre touches hors écran
    ) {
        /**
         * Convert to .cfg file format (RetroArch-compatible).
         */
        fun toCfgString(): String {
            return """
                # RetroPlay Configuration
                # Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}
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
                
                # === ZAPPER CONFIGURATION ===
                # Enable zapper (light gun) for this game
                zapper_enabled = "${zapperEnabled}"
                # Zapper port: 0 = Port 1 NES, 1 = Port 2 NES (default: 1)
                zapper_port = "${zapperPort}"
                # Trigger delay in milliseconds (default: 0)
                zapper_trigger_delay = "${zapperTriggerDelay}"
                # Trigger on touch DOWN (true) or UP (false)
                zapper_trigger_on_touch = "${zapperTriggerOnTouch}"
                # Pulse duration in milliseconds (default: 50)
                zapper_pulse_duration = "${zapperPulseDuration}"
                # Allow offscreen touches (default: false)
                zapper_allow_offscreen = "${zapperAllowOffscreen}"
            """.trimIndent()
        }
    
    /**
         * Convert to .cfg file format with commented values that match global config.
         * This allows users to easily see and edit all values, even those matching global.
         * 
         * @param globalConfig Global config to compare against
         */
        fun toCfgStringWithComments(globalConfig: RetroPlayConfig): String {
            fun formatLine(key: String, value: Any, globalValue: Any, comment: String = ""): String {
                val isSame = value.toString() == globalValue.toString()
                val valueStr = value.toString()
                val prefix = if (isSame) "# " else ""
                val suffix = if (isSame && comment.isNotEmpty()) " # $comment (same as global)" else if (isSame) " # (same as global)" else ""
                return "$prefix$key = \"$valueStr\"$suffix"
            }
            
            return """
                # RetroPlay Per-Game Configuration
                # Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}
                # 
                # This file can be edited manually. Values matching global config are commented out.
                # Uncomment and modify any value to override the global setting for this game.
                # Format: key = "value" (quotes required for strings, not for booleans/numbers)
                
                # === RUN-AHEAD (Input Lag Reduction) ===
                # Runs core N frames ahead, then loads state back to reduce input lag
                # Recommended: 1-2 frames for platformers/fighting games, 0 for RPGs
                ${formatLine("run_ahead_enabled", runAheadEnabled, globalConfig.runAheadEnabled)}
                ${formatLine("run_ahead_frames", runAheadFrames, globalConfig.runAheadFrames)}
                ${formatLine("run_ahead_secondary_instance", runAheadSecondaryInstance, globalConfig.runAheadSecondaryInstance)}
                ${formatLine("run_ahead_hide_warnings", runAheadHideWarnings, globalConfig.runAheadHideWarnings)}
                
                # === REWIND (Gameplay Rewinding) ===
                # Buffer to store savestates for rewinding gameplay
                # Buffer size in bytes (20971520 = 20 MB)
                ${formatLine("rewind_enable", rewindEnable, globalConfig.rewindEnable)}
                ${formatLine("rewind_buffer_size", rewindBufferSize, globalConfig.rewindBufferSize)}
                ${formatLine("rewind_granularity", rewindGranularity, globalConfig.rewindGranularity)}
                
                # === FAST FORWARD ===
                # Speed multiplier when fast-forward is active
                ${formatLine("fastforward_ratio", fastforwardRatio, globalConfig.fastforwardRatio)}
                ${formatLine("fastforward_frameskip", fastforwardFrameskip, globalConfig.fastforwardFrameskip)}
                
                # === AUDIO ===
                ${formatLine("audio_enable", audioEnable, globalConfig.audioEnable)}
                ${formatLine("audio_mute_enable", audioMuted, globalConfig.audioMuted)}
                ${formatLine("audio_volume", audioVolume, globalConfig.audioVolume)}
                
                # === VIDEO ===
                ${formatLine("video_vsync", videoVsync, globalConfig.videoVsync)}
                ${formatLine("video_hard_sync", videoHardSync, globalConfig.videoHardSync)}
                ${formatLine("video_hard_sync_frames", videoHardSyncFrames, globalConfig.videoHardSyncFrames)}
                ${formatLine("video_frame_delay", videoFrameDelay, globalConfig.videoFrameDelay)}
                
                # === SMART CONFIG (Automatic Optimal Settings) ===
                # Uses libretro-database metadata to auto-configure settings
                ${formatLine("smart_config_enabled", smartConfigEnabled, globalConfig.smartConfigEnabled)}
                ${formatLine("smart_config_auto_runahead", smartConfigAutoRunAhead, globalConfig.smartConfigAutoRunAhead)}
                ${formatLine("smart_config_auto_rewind", smartConfigAutoRewind, globalConfig.smartConfigAutoRewind)}
                ${formatLine("smart_config_auto_overlay", smartConfigAutoOverlay, globalConfig.smartConfigAutoOverlay)}
                ${formatLine("smart_config_show_osd", smartConfigShowOSD, globalConfig.smartConfigShowOSD)}
                
                # === INPUT ===
                ${formatLine("input_autodetect_enable", inputAutodetectEnable, globalConfig.inputAutodetectEnable)}
                ${formatLine("input_poll_type_behavior", inputPollTypeInterval, globalConfig.inputPollTypeInterval)}
                
                # === MISC ===
                ${formatLine("savefile_compression", savefileCompressionEnabled, globalConfig.savefileCompressionEnabled)}
                ${formatLine("savestate_compression", savestateCompressionEnabled, globalConfig.savestateCompressionEnabled)}
                ${formatLine("screenshot_directory", screenshotDirectory, globalConfig.screenshotDirectory)}
                
                # === ZAPPER CONFIGURATION ===
                # Enable zapper (light gun) for this game
                ${formatLine("zapper_enabled", zapperEnabled, globalConfig.zapperEnabled)}
                # Zapper port: 0 = Port 1 NES, 1 = Port 2 NES (default: 1)
                ${formatLine("zapper_port", zapperPort, globalConfig.zapperPort)}
                # Trigger delay in milliseconds (default: 0)
                ${formatLine("zapper_trigger_delay", zapperTriggerDelay, globalConfig.zapperTriggerDelay)}
                # Trigger on touch DOWN (true) or UP (false)
                ${formatLine("zapper_trigger_on_touch", zapperTriggerOnTouch, globalConfig.zapperTriggerOnTouch)}
                # Pulse duration in milliseconds (default: 50)
                ${formatLine("zapper_pulse_duration", zapperPulseDuration, globalConfig.zapperPulseDuration)}
                # Allow offscreen touches (default: false)
                ${formatLine("zapper_allow_offscreen", zapperAllowOffscreen, globalConfig.zapperAllowOffscreen)}
            """.trimIndent()
        }
    }
    
    /**
     * Load configuration from core config file for a console.
     * Creates default config if file doesn't exist.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param coreFilePath Optional core file path to determine config file name (e.g. "fceumm_libretro_android.so")
     */
    fun loadConfig(console: String, coreFilePath: String? = null): RetroPlayConfig {
        val configFile = File(getConfigFilePath(console, coreFilePath))
        
        if (!configFile.exists()) {
            Log.i(TAG, "Config file not found, creating default: ${configFile.absolutePath}")
            val defaultConfig = RetroPlayConfig()
            saveConfig(console, defaultConfig, coreFilePath)
            return defaultConfig
        }
        
        return try {
            val config = parseConfigFile(configFile)
            Log.i(TAG, "✅ Loaded config from ${configFile.absolutePath}")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config file, using defaults: ${e.message}", e)
            RetroPlayConfig()
        }
    }
    
    /**
     * Save configuration to core config file for a console.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param config Configuration to save
     * @param coreFilePath Optional core file path to determine config file name
     */
    fun saveConfig(console: String, config: RetroPlayConfig, coreFilePath: String? = null) {
        val configFile = File(getConfigFilePath(console, coreFilePath))
        
        // Create directory if needed
        configFile.parentFile?.mkdirs()
        
        try {
            configFile.writeText(config.toCfgString())
            Log.i(TAG, "💾 Config saved to ${configFile.absolutePath}")
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
            screenshotDirectory = configMap["screenshot_directory"] ?: "/storage/emulated/0/RetroPlay-Data/screenshots",
            
            zapperEnabled = configMap["zapper_enabled"]?.toBoolean() ?: false,
            zapperPort = configMap["zapper_port"]?.toIntOrNull() ?: 1,
            zapperTriggerDelay = configMap["zapper_trigger_delay"]?.toIntOrNull() ?: 0,
            zapperTriggerOnTouch = configMap["zapper_trigger_on_touch"]?.toBoolean() ?: true,
            zapperPulseDuration = configMap["zapper_pulse_duration"]?.toIntOrNull() ?: 50,
            zapperAllowOffscreen = configMap["zapper_allow_offscreen"]?.toBoolean() ?: false
        )
    }
    
    /**
     * Update a single config value and save.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param coreFilePath Optional core file path to determine config file name
     */
    fun updateConfigValue(console: String, key: String, value: Any, coreFilePath: String? = null) {
        val config = loadConfig(console, coreFilePath)
        
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
        
        saveConfig(console, updatedConfig, coreFilePath)
        Log.i(TAG, "Updated config: $key = $value")
    }
    
    /**
     * Reset to default configuration for a console.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param coreFilePath Optional core file path to determine config file name
     */
    fun resetToDefaults(console: String, coreFilePath: String? = null) {
        saveConfig(console, RetroPlayConfig(), coreFilePath)
        Log.i(TAG, "⚠️ Config reset to defaults for $console")
    }
    
    /**
     * Get config file path for external editing.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param coreFilePath Optional core file path to determine config file name
     */
    fun getConfigPath(console: String, coreFilePath: String? = null): String = getConfigFilePath(console, coreFilePath)
    
    // ========================================
    // PER-GAME CONFIG OVERRIDES
    // ========================================
    
    /**
     * Load per-game config override.
     * Returns null if no override exists for this game.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param gameName Game name (will be sanitized for filename)
     * @return RetroPlayConfig with only the overridden values, or null
     */
    fun loadGameConfig(console: String, gameName: String): RetroPlayConfig? {
        val gameConfigFile = File(getGameConfigFilePath(console, gameName))
        
        if (!gameConfigFile.exists()) {
            Log.d(TAG, "No per-game config for $console/$gameName")
            return null
        }
        
        return try {
            val config = parseConfigFile(gameConfigFile)
            Log.i(TAG, "✅ Loaded per-game config for $console/$gameName")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing per-game config for $console/$gameName: ${e.message}", e)
            null
        }
    }
    
    /**
     * Save per-game config override.
     * Saves all values explicitly (no comments).
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param gameName Game name (will be sanitized for filename)
     * @param config RetroPlayConfig with overridden values
     * @param coreFilePath Optional core file path to determine global config file name
     */
    fun saveGameConfig(console: String, gameName: String, config: RetroPlayConfig, coreFilePath: String? = null) {
        val gameConfigFile = File(getGameConfigFilePath(console, gameName))
        
        // Create directory if needed
        gameConfigFile.parentFile?.mkdirs()
        
        try {
            // Save all values explicitly (no comments)
            val configString = config.toCfgString()
            
            gameConfigFile.writeText(configString)
            Log.i(TAG, "💾 Per-game config saved for $console/$gameName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving per-game config for $console/$gameName: ${e.message}", e)
        }
    }
    
    /**
     * Delete per-game config override (revert to global config).
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param gameName Game name (will be sanitized for filename)
     * @return true if file was deleted, false if it didn't exist
     */
    fun deleteGameConfig(console: String, gameName: String): Boolean {
        val gameConfigFile = File(getGameConfigFilePath(console, gameName))
        
        return if (gameConfigFile.exists()) {
            val deleted = gameConfigFile.delete()
            if (deleted) {
                Log.i(TAG, "🗑️ Deleted per-game config for $console/$gameName")
            }
            deleted
        } else {
            Log.d(TAG, "No per-game config to delete for $console/$gameName")
            false
        }
    }
    
    /**
     * Check if a game has a per-game config override.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param gameName Game name (will be sanitized for filename)
     */
    fun hasGameConfig(console: String, gameName: String): Boolean {
        return File(getGameConfigFilePath(console, gameName)).exists()
    }
    
    /**
     * Get effective config for a game (global + per-game override merged).
     * Per-game overrides take precedence over global config.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param gameName Game name (will be sanitized for filename), or null for global config
     * @param coreFilePath Optional core file path to determine global config file name
     * @return Effective RetroPlayConfig for this game
     */
    fun getEffectiveConfig(console: String, gameName: String?, coreFilePath: String? = null): RetroPlayConfig {
        val globalConfig = loadConfig(console, coreFilePath)
        
        if (gameName == null) {
            return globalConfig
        }
        
        val gameConfig = loadGameConfig(console, gameName)
        
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
     * List all games that have per-game config overrides for a console.
     * 
     * @param console Console name (nes, snes, psx, etc.)
     * @param coreFilePath Optional core file path to exclude from list
     * @return List of game names (sanitized filenames without .cfg extension)
     */
    fun listGamesWithOverrides(console: String, coreFilePath: String? = null): List<String> {
        val consoleDir = File("${getBaseDir()}/$console")
        
        if (!consoleDir.exists()) {
            return emptyList()
        }
        
        val coreConfigFileName = if (coreFilePath != null) {
            val coreName = extractCoreName(coreFilePath)
            "$coreName.cfg"
        } else {
            "retroplay.cfg"
        }
        
        return consoleDir.listFiles { file ->
            file.isFile && file.name.endsWith(".cfg") && file.name != coreConfigFileName
        }?.map { file ->
            file.nameWithoutExtension
        } ?: emptyList()
    }
}
