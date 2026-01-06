package com.retroplay.helpers

import android.util.Log
import com.retroplay.config.RetroPlayConfigManager
import com.retroplay.database.DatabaseManager
import com.retroplay.database.GameInfo
import com.retroplay.database.SmartConfigManager
import com.retroplay.rewind.RewindManager
import com.retroplay.runahead.RunAheadManager

/**
 * Helper class to handle Emulator Configuration (Rewind, Run-Ahead, Per-Game Configs).
 * Extracted from RetroArchEmulatorActivity to improve modularity.
 */
object EmulatorConfigHelper {

    private const val TAG = "EmulatorConfigHelper"

    /**
     * Apply per-game configuration overrides (if exists).
     * Loads functionality like Run-Ahead, Rewind, etc.
     *
     * @return The effective RetroPlayConfig that was loaded and applied.
     */
    fun applyPerGameConfig(
        gameCRC: String?,
        gameName: String,
        console: String,
        rewindManager: RewindManager?,
        runAheadManager: RunAheadManager?,
        prefs: android.content.SharedPreferences? = null,
        coreFilePath: String? = null  // Optional core file path for config file name
    ): RetroPlayConfigManager.RetroPlayConfig {
        
        val config: RetroPlayConfigManager.RetroPlayConfig

        // Load effective config (global + per-game merged)
        // Use gameName for per-game config (like saves system)
        // Use coreFilePath to determine global config file name (e.g. fceumm.cfg instead of retroplay.cfg)
        config = RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
        val hasOverride = RetroPlayConfigManager.hasGameConfig(console, gameName)

        if (hasOverride) {
            Log.i(TAG, "[Config] ✅ Per-game config loaded for CRC: $gameCRC")
            Log.i(TAG, "[Config]   Run-Ahead: ${if (config.runAheadEnabled) "${config.runAheadFrames} frames" else "Disabled"}")
            Log.i(TAG, "[Config]   Rewind: ${if (config.rewindEnable) "${config.rewindBufferSize / (1024 * 1024)}MB" else "Disabled"}")
            Log.i(TAG, "[Config]   Fast Forward: ${config.fastforwardRatio}x")
            Log.i(TAG, "[Config]   VSync: ${if (config.videoVsync) "ON" else "OFF"}")
            Log.i(TAG, "[Config]   Video Frame Delay: ${config.videoFrameDelay}")
            Log.i(TAG, "[Config]   Video Hard Sync: ${if (config.videoHardSync) "${config.videoHardSyncFrames} frames" else "OFF"}")
            Log.i(TAG, "[Config]   Input Poll Interval: ${config.inputPollTypeInterval}")
        } else {
            Log.d(TAG, "[Config] Using global config for CRC: $gameCRC")
        }

        applyRewindSettings(config, rewindManager, gameCRC, gameName, console, prefs)
        applyRunAheadSettings(config, runAheadManager)
        
        return config
    }

    fun applyRunAheadSettings(
        config: RetroPlayConfigManager.RetroPlayConfig,
        runAheadManager: RunAheadManager?
    ) {
        val enabled = config.runAheadEnabled
        val frames = config.runAheadFrames
        runAheadManager?.configure(enabled, frames)

        if (enabled) {
            Log.i(TAG, "[RUN_AHEAD] Configured: enabled=true frames=$frames")
        } else {
            Log.i(TAG, "[RUN_AHEAD] Configured: enabled=false")
        }
    }

    fun applyRewindSettings(
        config: RetroPlayConfigManager.RetroPlayConfig,
        rewindManager: RewindManager?,
        gameCRC: String?,
        gameName: String,
        console: String,
        prefs: android.content.SharedPreferences? = null
    ) {
        val manager = rewindManager ?: return

        // PRIORITÉ 1: Vérifier les préférences directes (compatibilité avec ancien code)
        // Si "enable_rewind" existe dans prefs, l'utiliser en priorité
        val useLegacyPrefs = prefs != null && prefs.contains("enable_rewind")
        
        if (useLegacyPrefs) {
            // Ancienne logique: utiliser préférences directes
            val defaultEnabled = true
            val isEnabled = prefs!!.getBoolean("enable_rewind", defaultEnabled)
            
            // USER-CONFIGURABLE REWIND DURATION
            // Preference: "rewind_duration_seconds" with options: 10, 30, 60
            // Default: 10 seconds for safety
            val rewindDurationSeconds = prefs.getInt("rewind_duration_seconds", 10).coerceIn(10, 60)
            
            // Determine if this is a heavy console
            val isHeavyConsole = console.equals("psx", ignoreCase = true) || 
                                console.equals("n64", ignoreCase = true) ||
                                console.equals("files", ignoreCase = true) // Arcade/MAME potentially
            
            // Granularity: How many frames to skip between states
            // Heavy consoles: 60 frames = 1 second per state
            // Light consoles: 1 frame = smooth rewind
            val granularity = if (isHeavyConsole) 60 else 1
            
            // Buffer size: Calculate based on user's chosen duration
            // PSX/N64: duration(s) × 2.5MB/state = buffer size
            // Light consoles: Fixed 10MB (good for 3+ seconds)
            val bufferSize = if (isHeavyConsole) {
                // Calculate: states needed = duration in seconds (since granularity=60 = 1 state/sec)
                // PSX: ~2.5MB per state
                val statesNeeded = rewindDurationSeconds // 1 state per second
                val bytesPerState = (2.5 * 1024 * 1024).toInt() // 2.5MB
                (statesNeeded * bytesPerState).coerceAtLeast(25 * 1024 * 1024) // Min 25MB
            } else {
                10 * 1024 * 1024 // 10MB for light consoles
            }
            
            val statusMsg = if (isEnabled) {
                if (isHeavyConsole) {
                    "ENABLED (${rewindDurationSeconds}s max, user-configurable)"
                } else {
                    "ENABLED (3+ seconds)"
                }
            } else {
                "DISABLED by user"
            }
            
            Log.i(TAG, "[REWIND] Configuring (LEGACY): $statusMsg, granularity=$granularity, buffer=${bufferSize/1024/1024}MB (heavy=$isHeavyConsole)")
            
            manager.configure(
                enabled = isEnabled,
                bufferSizeBytes = bufferSize,
                granularity = granularity
            )
            return
        }

        // PRIORITÉ 2: Utiliser RetroPlayConfigManager (nouvelle logique)
        // SAFETY: Calculate optimal settings to prevent OOM crashes
        // Try to get REAL GameInfo from DB for accurate SmartConfig (Genre-based)
        var realGameInfo: GameInfo? = null
        if (!gameCRC.isNullOrEmpty()) {
            realGameInfo = DatabaseManager.lookupGame(gameCRC, console)
        }
        if (realGameInfo == null) {
            realGameInfo = DatabaseManager.lookupGameByName(gameName, console)
        }

        if (realGameInfo != null) {
            Log.i(TAG, "[SmartConfig] Using Metadata: ${realGameInfo.name} [${realGameInfo.genre}]")
        } else {
            Log.w(TAG, "[SmartConfig] Metadata not found, using generic Action profile")
        }

        val gameInfo = realGameInfo ?: GameInfo(
            name = gameName,
            crc = "",  // CRC not needed for buffer calculation
            console = console,
            genre = "Action"  // Use neutral genre (1.2x multiplier)
        )

        // Get optimal values from SmartConfigManager (safe limits for each console)
        val optimalGranularity = SmartConfigManager.getOptimalRewindGranularity(console)

        // Calculate ABSOLUTE maximum buffer size (60 seconds) to prevent OOM
        // This is different from optimalBufferSize which is a recommendation based on genre
        val avgSavestateKB = when (console) {
            "nes", "gb", "gbc" -> 10
            "snes", "gba" -> 50
            "genesis", "sms", "gg" -> 70
            "psx", "ps1", "playstation" -> 500
            "n64" -> 800
            "saturn" -> 1000
            "psp" -> 1200
            else -> 200
        }
        val maxCaptures60s = (60 * 60) / optimalGranularity  // 60 seconds at 60 fps
        val absoluteMaxBufferBytes = maxCaptures60s.toLong() * avgSavestateKB * 1024L

        // Use SmartConfig values if enabled, otherwise apply SAFE LIMITS to prevent OOM
        val effectiveGranularity = if (config.smartConfigEnabled && config.smartConfigAutoRewind) {
            val optimalBufferSize = SmartConfigManager.getOptimalRewindBuffer(gameInfo, console)
            Log.i(TAG, "[REWIND] SmartConfig ENABLED: using optimal granularity for $console: $optimalGranularity frames, buffer: ${optimalBufferSize / 1024 / 1024}MB")
            optimalGranularity
        } else {
            // Apply MINIMUM granularity to prevent crash (never lower than optimal)
            val safeGranularity = maxOf(config.rewindGranularity, optimalGranularity)
            if (safeGranularity != config.rewindGranularity) {
                Log.w(TAG, "[REWIND] SAFETY: Granularity increased from ${config.rewindGranularity} to $safeGranularity for $console (prevent OOM)")
            }
            safeGranularity
        }

        val effectiveBufferSize = if (config.smartConfigEnabled && config.smartConfigAutoRewind) {
            val optimalBufferSize = SmartConfigManager.getOptimalRewindBuffer(gameInfo, console)
            Log.i(TAG, "[REWIND] SmartConfig ENABLED: using optimal buffer for $console: ${optimalBufferSize / 1024 / 1024}MB (~60s max)")
            optimalBufferSize
        } else {
            // Apply ABSOLUTE MAXIMUM buffer size (60 seconds) to prevent crash
            // Only cap if config exceeds the 60s safety limit, otherwise keep user's choice
            val safeBufferSize = minOf(config.rewindBufferSize.toLong(), absoluteMaxBufferBytes).toInt()
            if (safeBufferSize != config.rewindBufferSize) {
                Log.w(TAG, "[REWIND] SAFETY: Buffer capped from ${config.rewindBufferSize / 1024 / 1024}MB to ${safeBufferSize / 1024 / 1024}MB for $console (60s limit: ${absoluteMaxBufferBytes / 1024 / 1024}MB)")
            } else {
                Log.i(TAG, "[REWIND] Manual config: ${config.rewindBufferSize / 1024 / 1024}MB buffer, granularity=$effectiveGranularity for $console (max allowed: ${absoluteMaxBufferBytes / 1024 / 1024}MB)")
            }
            safeBufferSize
        }

        manager.configure(
            enabled = config.rewindEnable || (config.smartConfigEnabled && config.smartConfigAutoRewind),
            bufferSizeBytes = effectiveBufferSize,
            granularity = effectiveGranularity
        )
    }
}