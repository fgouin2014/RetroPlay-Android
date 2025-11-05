package com.retroplay.database

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

object DatabaseManager {
    private const val TAG = "DatabaseManager"
    private const val DATABASE_BASE_PATH = "/storage/emulated/0/GameLibrary-Data/database"
    
    private val gameCache = mutableMapOf<String, MutableMap<String, GameInfo>>()
    
    fun calculateCRC32(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return null
            }
            
            // If file is a ZIP, extract and calculate CRC on ROM inside
            if (file.name.endsWith(".zip", ignoreCase = true) || file.name.endsWith(".7z", ignoreCase = true)) {
                return calculateCRC32FromArchive(file)
            }
            
            val crc32 = CRC32()
            val buffer = ByteArray(8192)
            var skipBytes = 0
            
            // NES ROMs: Skip 16-byte iNES header (starts with "NES\x1A")
            if (file.name.endsWith(".nes", ignoreCase = true)) {
                FileInputStream(file).use { fis ->
                    val header = ByteArray(4)
                    if (fis.read(header) == 4 && 
                        header[0] == 'N'.code.toByte() && 
                        header[1] == 'E'.code.toByte() && 
                        header[2] == 'S'.code.toByte() && 
                        header[3] == 0x1A.toByte()) {
                        skipBytes = 16  // Skip iNES header
                        Log.d(TAG, "NES ROM detected, skipping 16-byte iNES header")
                    }
                }
            }
            
            FileInputStream(file).use { fis ->
                // Skip header if needed
                if (skipBytes > 0) {
                    fis.skip(skipBytes.toLong())
                }
                
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    crc32.update(buffer, 0, bytesRead)
                }
            }
            
            val crcValue = crc32.value.toString(16).uppercase().padStart(8, '0')
            Log.d(TAG, "CRC32 calculated for ${file.name}: $crcValue (skipBytes=$skipBytes)")
            return crcValue
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating CRC32 for $filePath: ${e.message}", e)
            null
        }
    }
    
    private fun calculateCRC32FromArchive(archiveFile: File): String? {
        return try {
            if (!archiveFile.name.endsWith(".zip", ignoreCase = true)) {
                Log.w(TAG, "Only .zip archives supported for now (not .7z)")
                return null
            }
            
            ZipFile(archiveFile).use { zip ->
                // Find first ROM file in the archive
                val entry = zip.entries().asSequence().firstOrNull { entry ->
                    !entry.isDirectory && (
                        entry.name.endsWith(".nes", ignoreCase = true) ||
                        entry.name.endsWith(".sfc", ignoreCase = true) ||
                        entry.name.endsWith(".smc", ignoreCase = true) ||
                        entry.name.endsWith(".gb", ignoreCase = true) ||
                        entry.name.endsWith(".gbc", ignoreCase = true) ||
                        entry.name.endsWith(".gba", ignoreCase = true) ||
                        entry.name.endsWith(".bin", ignoreCase = true) ||
                        entry.name.endsWith(".gen", ignoreCase = true) ||
                        entry.name.endsWith(".md", ignoreCase = true)
                    )
                }
                
                if (entry == null) {
                    Log.w(TAG, "No ROM file found in archive: ${archiveFile.name}")
                    return null
                }
                
                Log.d(TAG, "Found ROM in archive: ${entry.name}")
                
                // Read the ROM from the ZIP and calculate CRC
                zip.getInputStream(entry).use { stream ->
                    val crc32 = CRC32()
                    val buffer = ByteArray(8192)
                    var skipBytes = 0
                    
                    // Check if NES ROM with iNES header
                    if (entry.name.endsWith(".nes", ignoreCase = true)) {
                        // Read first 4 bytes to check for iNES header
                        val header = ByteArray(4)
                        val headerRead = stream.read(header)
                        
                        if (headerRead == 4 && 
                            header[0] == 'N'.code.toByte() && 
                            header[1] == 'E'.code.toByte() && 
                            header[2] == 'S'.code.toByte() && 
                            header[3] == 0x1A.toByte()) {
                            // Skip remaining 12 bytes of iNES header (already read 4)
                            stream.skip(12)
                            skipBytes = 16
                            Log.d(TAG, "iNES header detected in ${entry.name}, skipping 16 bytes")
                        } else {
                            // Not iNES, include those 4 bytes we just read
                            crc32.update(header, 0, 4)
                        }
                    }
                    
                    // Calculate CRC on the rest of the data
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        crc32.update(buffer, 0, bytesRead)
                    }
                    
                    val crcValue = crc32.value.toString(16).uppercase().padStart(8, '0')
                    Log.d(TAG, "CRC32 from ZIP: ${archiveFile.name} → ${entry.name} = $crcValue (skipBytes=$skipBytes)")
                    return crcValue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating CRC from archive: ${e.message}", e)
            null
        }
    }
    
    fun lookupGame(crc: String, console: String): GameInfo? {
        val consoleCache = gameCache[console]
        if (consoleCache != null) {
            val cached = consoleCache[crc]
            if (cached != null) {
                Log.d(TAG, "Cache hit for CRC $crc: ${cached.name}")
                return cached
            }
        }
        
        Log.d(TAG, "Cache miss for CRC $crc, loading from DAT files...")
        loadDatabase(console)
        
        return gameCache[console]?.get(crc)
    }
    
    fun loadDatabase(console: String) {
        if (gameCache.containsKey(console)) {
            Log.d(TAG, "Database for $console already loaded (${gameCache[console]?.size} games)")
            return
        }
        
        val consoleName = getConsoleFullName(console)
        Log.i(TAG, "Loading database for $consoleName...")
        
        val startTime = System.currentTimeMillis()
        val consoleMap = mutableMapOf<String, GameInfo>()
        
        val genreDat = File("$DATABASE_BASE_PATH/metadata/genre/$consoleName.dat")
        if (genreDat.exists()) {
            val genreGames = DatParser.parseDatFile(genreDat, console)
            genreGames.forEach { (crc, game) ->
                consoleMap[crc] = game
            }
            Log.i(TAG, "  Loaded ${genreGames.size} games from genre.dat")
        } else {
            Log.w(TAG, "  Genre DAT not found: ${genreDat.absolutePath}")
        }
        
        val developerDat = File("$DATABASE_BASE_PATH/metadata/developer/$consoleName.dat")
        if (developerDat.exists()) {
            val developerGames = DatParser.parseDatFile(developerDat, console)
            developerGames.forEach { (crc, game) ->
                val existing = consoleMap[crc]
                if (existing != null) {
                    consoleMap[crc] = existing.copy(developer = game.developer)
                }
            }
            Log.i(TAG, "  Merged ${developerGames.size} developers")
        }
        
        val yearDat = File("$DATABASE_BASE_PATH/metadata/releaseyear/$consoleName.dat")
        if (yearDat.exists()) {
            val yearGames = DatParser.parseDatFile(yearDat, console)
            yearGames.forEach { (crc, game) ->
                val existing = consoleMap[crc]
                if (existing != null) {
                    consoleMap[crc] = existing.copy(releaseYear = game.releaseYear)
                }
            }
            Log.i(TAG, "  Merged ${yearGames.size} release years")
        }
        
        gameCache[console] = consoleMap
        
        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "✅ Database loaded for $consoleName: ${consoleMap.size} games in ${elapsed}ms")
    }
    
    fun getCheatsPath(gameInfo: GameInfo, console: String): File? {
        val consoleName = getConsoleFullName(console)
        val cheatPath = "$DATABASE_BASE_PATH/cht/$consoleName/${gameInfo.name}.cht"
        val cheatFile = File(cheatPath)
        
        return if (cheatFile.exists()) {
            Log.d(TAG, "✅ Cheat file found for ${gameInfo.name}: ${cheatFile.absolutePath}")
            cheatFile
        } else {
            Log.d(TAG, "⚠️ No cheat file for ${gameInfo.name}")
            null
        }
    }
    
    private fun getConsoleFullName(console: String): String {
        return when (console) {
            "nes" -> "Nintendo - Nintendo Entertainment System"
            "snes" -> "Nintendo - Super Nintendo Entertainment System"
            "n64" -> "Nintendo - Nintendo 64"
            "gba" -> "Nintendo - Game Boy Advance"
            "gbc" -> "Nintendo - Game Boy Color"
            "gb" -> "Nintendo - Game Boy"
            "psx" -> "Sony - PlayStation"
            "psp" -> "Sony - PlayStation Portable"
            "genesis" -> "Sega - Mega Drive - Genesis"
            "gamegear" -> "Sega - Game Gear"
            "mastersystem" -> "Sega - Master System - Mark III"
            "saturn" -> "Sega - Saturn"
            "dreamcast" -> "Sega - Dreamcast"
            "atari2600" -> "Atari - 2600"
            "lynx" -> "Atari - Lynx"
            else -> console
        }
    }
    
    fun clearCache() {
        gameCache.clear()
        Log.i(TAG, "Cache cleared")
    }
    
    fun getCacheStats(): String {
        val totalGames = gameCache.values.sumOf { it.size }
        return "Cached consoles: ${gameCache.size}, Total games: $totalGames"
    }
}
