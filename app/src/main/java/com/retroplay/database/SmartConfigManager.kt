package com.retroplay.database

import android.util.Log

/**
 * Smart configuration manager based on game metadata
 * 
 * Uses genre, console, and other metadata to automatically
 * configure optimal emulation settings:
 * - Run-Ahead frames (input lag reduction)
 * - Rewind buffer size
 * - Overlay selection
 * - Core options
 */
object SmartConfigManager {
    private const val TAG = "SmartConfigManager"
    
    /**
     * Get optimal Run-Ahead frame count based on game genre
     * 
     * Run-Ahead reduces input lag by running the core N frames ahead,
     * then loading state back. Most beneficial for games requiring
     * precise timing.
     * 
     * @param gameInfo Game metadata
     * @param console Console ID
     * @return Recommended frame count (0-4)
     * 
     * RetroArch default: 1 frame
     * Max recommended: 4 frames
     */
    fun getOptimalRunAheadFrames(gameInfo: GameInfo, console: String): Int {
        val frames = when (gameInfo.genre) {
            // Fighting games = CRITICAL input precision
            "Fighting", "Beat 'Em Up" -> 4
            
            // Platformers = precise jump timing
            "Platformer", "Action" -> 2
            
            // Shoot'em Up = quick reactions
            "Shoot'em Up", "Shooter" -> 3
            
            // Racing = steering precision
            "Racing" -> 2
            
            // RPG = turn-based, no lag benefit
            "RPG", "Role-Playing", "Strategy" -> 0
            
            // Puzzle = no benefit
            "Puzzle" -> 0
            
            // Sports = varies
            "Sports" -> 1
            
            // Default conservative
            else -> 1
        }
        
        Log.d(TAG, "[RUN-AHEAD] ${gameInfo.name} (${gameInfo.genre}) → $frames frames")
        return frames
    }
    
    /**
     * Get optimal Rewind buffer size based on game and console
     * 
     * Rewind buffer stores savestates for rewinding gameplay.
     * Size depends on:
     * - Savestate size (varies by console)
     * - How long to keep history (varies by genre)
     * 
     * @return Buffer size in bytes
     * 
     * NES savestate: ~10KB → 20MB = ~2000 frames (33 seconds at 60fps)
     * PSX savestate: ~500KB → 10MB = ~20 frames (0.3 seconds at 60fps)
     */
    fun getOptimalRewindBuffer(gameInfo: GameInfo, console: String): Int {
        // Base size by console (savestate size varies significantly)
        val baseSize = when (console) {
            "nes" -> 20 * 1024 * 1024      // 20MB (small savestates ~10KB)
            "snes" -> 15 * 1024 * 1024     // 15MB
            "gba" -> 12 * 1024 * 1024      // 12MB
            "gb", "gbc" -> 10 * 1024 * 1024 // 10MB
            "genesis" -> 12 * 1024 * 1024  // 12MB
            "psx" -> 10 * 1024 * 1024      // 10MB (large savestates ~500KB)
            "n64" -> 8 * 1024 * 1024       // 8MB (very large savestates)
            "psp" -> 5 * 1024 * 1024       // 5MB
            else -> 10 * 1024 * 1024
        }
        
        // Adjust by genre (how useful rewind is)
        val multiplier = when (gameInfo.genre) {
            // Platformers = very useful (dying = frustrating)
            "Platformer" -> 1.5f
            
            // Puzzles = useful (try different solutions)
            "Puzzle" -> 2.0f
            
            // Action = useful
            "Action" -> 1.2f
            
            // Fighting = less useful (short rounds)
            "Fighting" -> 0.5f
            
            // Racing = less useful (restart race)
            "Racing" -> 0.7f
            
            // RPG = moderate (long cutscenes)
            "RPG", "Role-Playing" -> 1.0f
            
            // Shoot'em Up = useful
            "Shoot'em Up" -> 1.3f
            
            else -> 1.0f
        }
        
        val finalSize = (baseSize * multiplier).toInt()
        Log.d(TAG, "[REWIND] ${gameInfo.name} ($console, ${gameInfo.genre}) → ${finalSize / 1024 / 1024}MB")
        return finalSize
    }
    
    /**
     * Get optimal overlay based on game genre and console
     * 
     * @return Overlay name (e.g. "nes-standard", "psx-analog")
     */
    fun getOptimalOverlay(gameInfo: GameInfo, console: String): String {
        val overlay = when (console) {
            "nes" -> when (gameInfo.genre) {
                "Fighting" -> "flat-nes-6button"        // More buttons visible
                "Racing" -> "flat-nes-minimal"          // Less clutter
                "RPG" -> "flat-nes-transparent"         // More screen visibility
                "Shoot'em Up" -> "flat-nes-standard"    // Balanced
                else -> "flat-nes-standard"
            }
            
            "snes" -> when (gameInfo.genre) {
                "Fighting" -> "flat-snes-6button"
                "Racing" -> "flat-snes-minimal"
                else -> "flat-snes-standard"
            }
            
            "psx" -> when {
                gameInfo.hasAnalog -> "flat-psx-analog"     // Analog sticks required
                gameInfo.genre == "Racing" -> "flat-psx-analog"  // Racing games often need analog
                else -> "flat-psx-digital"
            }
            
            "n64" -> "flat-n64-standard"  // N64 always has analog
            
            "gba" -> when (gameInfo.genre) {
                "Fighting" -> "flat-gba-6button"
                else -> "flat-gba-standard"
            }
            
            else -> "flat-${console}-standard"
        }
        
        Log.d(TAG, "[OVERLAY] ${gameInfo.name} (${gameInfo.genre}) → $overlay")
        return overlay
    }
    
    /**
     * Get optimal fast-forward ratio
     * 
     * RPGs benefit from higher ratios (skip grinding/cutscenes)
     * Action games need lower ratios (maintain control)
     */
    fun getOptimalFastForwardRatio(gameInfo: GameInfo): Float {
        return when (gameInfo.genre) {
            "RPG", "Role-Playing" -> 4.0f          // Skip text/grinding
            "Strategy", "Simulation" -> 3.0f       // Skip waiting
            "Puzzle" -> 2.0f                       // Moderate
            "Fighting", "Action", "Platformer" -> 2.0f  // Keep control
            "Shoot'em Up" -> 2.0f                  // Too fast = unplayable
            else -> 2.0f
        }
    }
    
    /**
     * Determine if game should have Rewind enabled by default
     */
    fun shouldEnableRewindByDefault(gameInfo: GameInfo): Boolean {
        return gameInfo.benefitsFromRewind()
    }
    
    /**
     * Determine if game should have Run-Ahead enabled by default
     */
    fun shouldEnableRunAheadByDefault(gameInfo: GameInfo): Boolean {
        return gameInfo.benefitsFromRunAhead()
    }
    
    /**
     * Get all recommended settings for a game
     */
    fun getSmartConfig(gameInfo: GameInfo, console: String): SmartConfig {
        return SmartConfig(
            runAheadFrames = getOptimalRunAheadFrames(gameInfo, console),
            runAheadEnabled = shouldEnableRunAheadByDefault(gameInfo),
            rewindBufferSize = getOptimalRewindBuffer(gameInfo, console),
            rewindEnabled = shouldEnableRewindByDefault(gameInfo),
            overlayName = getOptimalOverlay(gameInfo, console),
            fastForwardRatio = getOptimalFastForwardRatio(gameInfo)
        )
    }
}

/**
 * Smart configuration recommendations
 */
data class SmartConfig(
    val runAheadFrames: Int,
    val runAheadEnabled: Boolean,
    val rewindBufferSize: Int,
    val rewindEnabled: Boolean,
    val overlayName: String,
    val fastForwardRatio: Float
) {
    fun toLogString(): String {
        return """
            [SMART CONFIG]
              Run-Ahead: ${if (runAheadEnabled) "$runAheadFrames frames" else "disabled"}
              Rewind: ${if (rewindEnabled) "${rewindBufferSize / 1024 / 1024}MB" else "disabled"}
              Overlay: $overlayName
              FF Ratio: ${fastForwardRatio}x
        """.trimIndent()
    }
}

