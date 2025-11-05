package com.retroplay.database

import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

object DatabaseManager {
    private const val TAG = "DatabaseManager"
    private const val DATABASE_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/database/rdb"
    private const val CACHE_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/database/cache"
    
    private val gameCache = mutableMapOf<String, MutableMap<String, GameInfo>>()
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Progress callback for loading
    // Kotlin automatically generates setter: onLoadProgress = ... (Java: setOnLoadProgress(...))
    var onLoadProgress: ((String, Int, Int) -> Unit)? = null
    
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
                        entry.name.endsWith(".unh", ignoreCase = true) ||  // UnHeadered NES
                        entry.name.endsWith(".unf", ignoreCase = true) ||  // UnHeadered FDS
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
    
    /**
     * Async version: Load database with coroutines (non-blocking)
     */
    suspend fun loadDatabaseAsync(console: String) = withContext(Dispatchers.IO) {
        loadDatabase(console)
    }
    
    /**
     * Async version: Lookup game with coroutines (non-blocking)
     */
    suspend fun lookupGameAsync(crc: String, console: String): GameInfo? = withContext(Dispatchers.IO) {
        lookupGame(crc, console)
    }
    
    /**
     * Preload all major console databases in background
     */
    fun preloadDatabasesAsync(consoles: List<String>) {
        scope.launch {
            consoles.forEachIndexed { index, console ->
                try {
                    onLoadProgress?.invoke(console, index + 1, consoles.size)
                    loadDatabase(console)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to preload database for $console: ${e.message}", e)
                }
            }
            onLoadProgress?.invoke("", consoles.size, consoles.size) // Complete
        }
    }
    
    /**
     * Java-friendly async lookup with callback (for use from Java/Android Activities)
     * Uses coroutines internally but exposes Java-friendly callback interface
     */
    fun lookupGameAsyncJava(
        crc: String,
        console: String,
        onProgress: ((String) -> Unit)? = null,
        onComplete: (GameInfo?) -> Unit
    ) {
        scope.launch {
            onProgress?.invoke("Loading database...")
            val result = lookupGameAsync(crc, console)
            onComplete(result)
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
        
        Log.d(TAG, "Cache miss for CRC $crc, loading from database...")
        loadDatabase(console)
        
        return gameCache[console]?.get(crc)
    }
    
    fun loadDatabase(console: String) {
        if (gameCache.containsKey(console)) {
            Log.d(TAG, "Database for $console already loaded (${gameCache[console]?.size} games)")
            return
        }
        
        val consoleName = getConsoleFullName(console)
        
        // Look for .rdb file in RetroPlay-Data/database/rdb/
        val rdbFile = File("$DATABASE_BASE_PATH/$consoleName.rdb")
        
        if (!rdbFile.exists()) {
            Log.w(TAG, "⚠️ RDB file not found: ${rdbFile.absolutePath}")
            gameCache[console] = mutableMapOf()
            return
        }
        
        // Check if we have a disk cache
        val cacheDir = File(CACHE_BASE_PATH)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val cacheFile = File("$CACHE_BASE_PATH/$consoleName.cache")
        val cacheValid = cacheFile.exists() && cacheFile.lastModified() >= rdbFile.lastModified()
        
        val games: Map<String, GameInfo> = if (cacheValid) {
            // Load from cache (much faster!)
            Log.i(TAG, "Loading database for $consoleName from disk cache...")
            try {
                loadFromCache(cacheFile, console)
            } catch (e: Exception) {
                Log.w(TAG, "Cache corrupted, re-parsing .rdb: ${e.message}")
                parseAndCache(rdbFile, cacheFile, console)
            }
        } else {
            // Parse .rdb and save to cache
            Log.i(TAG, "Loading database for $consoleName from .rdb (no cache)...")
            parseAndCache(rdbFile, cacheFile, console)
        }
        
        gameCache[console] = games.toMutableMap()
        
        Log.i(TAG, "✅ Database loaded for $consoleName: ${games.size} games")
    }
    
    private fun parseAndCache(rdbFile: File, cacheFile: File, console: String): Map<String, GameInfo> {
        // Parse .rdb file with RdbParser
        val games = RdbParser.parseRdbFile(rdbFile)
        
        // Update console field for all games
        val consoleMap = games.mapValues { (_, game) ->
            game.copy(console = console)
        }
        
        // Save to disk cache
        try {
            saveToCache(cacheFile, consoleMap)
            Log.d(TAG, "💾 Cached ${consoleMap.size} games to ${cacheFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache: ${e.message}")
        }
        
        return consoleMap
    }
    
    private fun saveToCache(cacheFile: File, games: Map<String, GameInfo>) {
        cacheFile.outputStream().use { fos ->
            java.io.ObjectOutputStream(fos).use { oos ->
                oos.writeObject(games)
            }
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun loadFromCache(cacheFile: File, console: String): Map<String, GameInfo> {
        val startTime = System.currentTimeMillis()
        cacheFile.inputStream().use { fis ->
            java.io.ObjectInputStream(fis).use { ois ->
                val games = ois.readObject() as Map<String, GameInfo>
                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "📦 Loaded ${games.size} games from cache in ${elapsed}ms (vs ~3000ms from .rdb)")
                return games
            }
        }
    }
    
    fun getCheatsPath(gameInfo: GameInfo, console: String): File? {
        val consoleName = getConsoleFullName(console)
        
        // Try multiple locations for cheat files
        val cheatLocations = listOf(
            "/storage/emulated/0/GameLibrary-Data/database/cht/$consoleName/${gameInfo.name}.cht",
            "/storage/emulated/0/RetroArch/cheats/$consoleName/${gameInfo.name}.cht",
            "/storage/emulated/0/RetroPlay-Data/database/cht/$consoleName/${gameInfo.name}.cht"
        )
        
        for (path in cheatLocations) {
            val cheatFile = File(path)
            if (cheatFile.exists()) {
                Log.d(TAG, "✅ Cheat file found for ${gameInfo.name}: ${cheatFile.absolutePath}")
                return cheatFile
            }
        }
        
        Log.d(TAG, "⚠️ No cheat file for ${gameInfo.name}")
        return null
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
            "genesis", "megadrive" -> "Sega - Mega Drive - Genesis"
            "gamegear" -> "Sega - Game Gear"
            "mastersystem", "sms" -> "Sega - Master System - Mark III"
            "saturn" -> "Sega - Saturn"
            "dreamcast" -> "Sega - Dreamcast"
            "atari2600" -> "Atari - 2600"
            "lynx", "atarilynx" -> "Atari - Lynx"
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
