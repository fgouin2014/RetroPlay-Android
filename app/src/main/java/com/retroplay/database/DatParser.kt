package com.retroplay.database

import android.util.Log
import java.io.File

/**
 * Parser for libretro-database .dat files (clrmamepro format)
 * 
 * Format example:
 * ```
 * game (
 *     comment "Mega Man (USA)"
 *     genre "Platformer"
 *     developer "Capcom"
 *     releaseyear "1987"
 *     rom ( crc 74D7BAE1 )
 * )
 * ```
 */
object DatParser {
    private const val TAG = "DatParser"
    
    /**
     * Parse a .dat file and return map of CRC -> GameInfo
     * 
     * @param datFile .dat file to parse
     * @param console Console ID for GameInfo
     * @return Map of CRC (uppercase hex) to GameInfo
     */
    fun parseDatFile(datFile: File, console: String): Map<String, GameInfo> {
        val games = mutableMapOf<String, GameInfo>()
        
        if (!datFile.exists()) {
            Log.w(TAG, "DAT file not found: ${datFile.absolutePath}")
            return games
        }
        
        Log.d(TAG, "Parsing DAT file: ${datFile.name}")
        val startTime = System.currentTimeMillis()
        
        var currentGame: MutableMap<String, String>? = null
        var currentCRC: String? = null
        var lineNumber = 0
        
        try {
            datFile.forEachLine { line ->
                lineNumber++
                val trimmed = line.trim()
                
                when {
                    // Start of game block
                    trimmed.startsWith("game (") || trimmed == "game (" -> {
                        currentGame = mutableMapOf()
                        currentCRC = null
                    }
                    
                    // Game name (comment field contains the display name)
                    trimmed.startsWith("comment ") || trimmed.startsWith("name ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("name", value)
                        }
                    }
                    
                    // Genre
                    trimmed.startsWith("genre ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("genre", value)
                        }
                    }
                    
                    // Developer
                    trimmed.startsWith("developer ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("developer", value)
                        }
                    }
                    
                    // Publisher
                    trimmed.startsWith("publisher ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("publisher", value)
                        }
                    }
                    
                    // Release year
                    trimmed.startsWith("releaseyear ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("releaseYear", value)
                        }
                    }
                    
                    // Release month
                    trimmed.startsWith("releasemonth ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("releaseMonth", value)
                        }
                    }
                    
                    // Max users/players
                    trimmed.startsWith("users ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("maxPlayers", value)
                        }
                    }
                    
                    // Rumble support
                    trimmed.startsWith("rumble ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("rumble", value)
                        }
                    }
                    
                    // Analog support
                    trimmed.startsWith("analog ") -> {
                        val value = extractQuotedValue(trimmed)
                        if (value != null) {
                            currentGame?.set("analog", value)
                        }
                    }
                    
                    // CRC in rom block
                    trimmed.contains("crc ") && trimmed.contains("rom (") -> {
                        currentCRC = extractCRC(trimmed)
                    }
                    
                    // End of game block
                    trimmed == ")" && currentGame != null && currentCRC != null -> {
                        // Build GameInfo from collected metadata
                        val name = currentGame!!["name"]
                        if (name != null && currentCRC != null) {
                            val gameInfo = GameInfo(
                                name = name,
                                crc = currentCRC!!,
                                console = console,
                                genre = currentGame!!["genre"],
                                developer = currentGame!!["developer"],
                                publisher = currentGame!!["publisher"],
                                releaseYear = currentGame!!["releaseYear"]?.toIntOrNull(),
                                releaseMonth = currentGame!!["releaseMonth"]?.toIntOrNull(),
                                maxPlayers = currentGame!!["maxPlayers"]?.toIntOrNull() ?: 1,
                                hasRumble = currentGame!!["rumble"] == "1",
                                hasAnalog = currentGame!!["analog"] == "1"
                            )
                            games[currentCRC!!] = gameInfo
                        }
                        
                        currentGame = null
                        currentCRC = null
                    }
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ Parsed ${datFile.name}: ${games.size} games in ${elapsed}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DAT file ${datFile.name} at line $lineNumber: ${e.message}", e)
        }
        
        return games
    }
    
    /**
     * Extract value between quotes
     * 
     * Example: 'genre "Platformer"' -> "Platformer"
     */
    private fun extractQuotedValue(line: String): String? {
        return try {
            val firstQuote = line.indexOf('"')
            val lastQuote = line.lastIndexOf('"')
            
            if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
                line.substring(firstQuote + 1, lastQuote)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting quoted value from: $line")
            null
        }
    }
    
    /**
     * Extract CRC from rom line
     * 
     * Example: 'rom ( crc 74D7BAE1 )' -> "74D7BAE1"
     */
    private fun extractCRC(line: String): String? {
        return try {
            val crcIndex = line.indexOf("crc")
            if (crcIndex == -1) return null
            
            // Extract hex value after "crc"
            val afterCrc = line.substring(crcIndex + 3).trim()
            val parts = afterCrc.split(Regex("\\s+"))
            
            if (parts.isNotEmpty()) {
                // CRC is uppercase hex, typically 8 characters
                val crc = parts[0].uppercase().replace("[^0-9A-F]".toRegex(), "")
                if (crc.length == 8) {
                    crc
                } else {
                    Log.w(TAG, "Invalid CRC length: $crc (expected 8 chars)")
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting CRC from: $line")
            null
        }
    }
    
    /**
     * Merge multiple DAT files for a console
     * 
     * Used to combine genre + developer + year + etc.
     * Later DATs override earlier ones for the same CRC.
     */
    fun mergeDatFiles(datFiles: List<File>, console: String): Map<String, GameInfo> {
        val mergedGames = mutableMapOf<String, GameInfo>()
        
        datFiles.forEach { datFile ->
            if (datFile.exists()) {
                val games = parseDatFile(datFile, console)
                games.forEach { (crc, game) ->
                    val existing = mergedGames[crc]
                    if (existing != null) {
                        // Merge metadata (later DAT wins for conflicts)
                        mergedGames[crc] = existing.copy(
                            genre = game.genre ?: existing.genre,
                            developer = game.developer ?: existing.developer,
                            publisher = game.publisher ?: existing.publisher,
                            releaseYear = game.releaseYear ?: existing.releaseYear,
                            releaseMonth = game.releaseMonth ?: existing.releaseMonth,
                            maxPlayers = if (game.maxPlayers > 1) game.maxPlayers else existing.maxPlayers,
                            hasRumble = game.hasRumble || existing.hasRumble,
                            hasAnalog = game.hasAnalog || existing.hasAnalog
                        )
                    } else {
                        mergedGames[crc] = game
                    }
                }
            }
        }
        
        return mergedGames
    }
}

