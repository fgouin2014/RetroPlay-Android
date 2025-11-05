package com.retroplay.database

import java.io.Serializable

/**
 * Game information from libretro-database
 * 
 * Compiled from multiple .dat sources:
 * - genre/ - Game genre (Platformer, Fighting, RPG, etc.)
 * - developer/ - Developer name
 * - publisher/ - Publisher name
 * - releaseyear/ - Release year
 * - releasemonth/ - Release month
 * - rumble/ - Rumble support
 * - analog/ - Analog controller support
 * - hacks/ - Fan-made modifications
 * - homebrew/ - Independent/homebrew games
 */
data class GameInfo(
    val name: String,                   // Official name: "Mega Man (USA)"
    val crc: String,                    // CRC32: "74D7BAE1"
    val console: String,                // "nes", "snes", "psx", etc.
    val genre: String? = null,          // "Platformer", "Fighting", "RPG", etc.
    val developer: String? = null,      // "Capcom", "Nintendo", etc.
    val publisher: String? = null,      // May differ from developer
    val releaseYear: Int? = null,       // 1987, 1990, etc.
    val releaseMonth: Int? = null,      // 1-12
    val maxPlayers: Int = 1,            // 1-4 players
    val hasRumble: Boolean = false,     // Rumble/vibration support
    val hasAnalog: Boolean = false,     // Analog sticks required
    val isHack: Boolean = false,        // Fan translation/mod
    val isHomebrew: Boolean = false     // Independent game
) : Serializable {
    /**
     * Check if this game benefits from Run-Ahead
     * (Fighting, Platformer, Shoot'em Up = high priority)
     */
    fun benefitsFromRunAhead(): Boolean {
        return genre in listOf("Fighting", "Platformer", "Action", "Shoot'em Up")
    }
    
    /**
     * Check if this game benefits from Rewind
     * (Platformer, Puzzle = high priority)
     */
    fun benefitsFromRewind(): Boolean {
        return genre in listOf("Platformer", "Puzzle", "Action")
    }
    
    /**
     * Get display string for UI
     */
    fun getDisplayInfo(): String {
        val parts = mutableListOf<String>()
        developer?.let { parts.add("🏢 $it") }
        releaseYear?.let { parts.add("📅 $it") }
        genre?.let { parts.add("🎭 $it") }
        if (maxPlayers > 1) parts.add("👥 $maxPlayers players")
        return parts.joinToString(" • ")
    }
}

