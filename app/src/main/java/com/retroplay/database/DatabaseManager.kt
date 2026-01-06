package com.retroplay.database

import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * DatabaseManager - Lookup simplifié hash-based
 * 
 * Utilise uniquement les hashes (CRC32 pour compatibilité RDB) pour lookup
 * Le calcul de hash est déplacé vers HashCalculator
 */
object DatabaseManager {
    private const val TAG = "DatabaseManager"
    private const val DATABASE_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/database/rdb"
    private const val CACHE_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/database/cache"
    
    private val gameCache = mutableMapOf<String, MutableMap<String, GameInfo>>()
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Progress callback for loading
    var onLoadProgress: ((String, Int, Int) -> Unit)? = null
    
    /**
     * DEPRECATED: Utiliser HashCalculator.calculateHash() à la place
     * Gardé pour compatibilité avec code existant
     */
    @Deprecated("Use HashCalculator.calculateHash() instead", ReplaceWith("HashCalculator.calculateHash(File(filePath))"))
    fun calculateCRC32(filePath: String): String? {
            val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "File not found for CRC calculation: $filePath")
            return null
        }
        
        Log.d(TAG, "Calculating CRC32 for: ${file.name} (exists: ${file.exists()}, size: ${file.length()} bytes)")
        val hash = com.retroplay.scraper.HashCalculator.calculateHash(file)
        
        if (hash == null) {
            Log.w(TAG, "HashCalculator returned null for: $filePath")
            return null
        }
        
        val crc = hash.crc32
        if (crc != null) {
            Log.i(TAG, "✅ CRC32 calculated: $crc for ${file.name}")
        } else {
            Log.w(TAG, "⚠️ CRC32 is null in GameHash for: $filePath (MD5: ${hash.md5}, SHA1: ${hash.sha1})")
        }
        
        return crc
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
        if (crc.isBlank()) {
            Log.w(TAG, "lookupGame called with empty CRC for console: $console")
            return null
        }
        
        // Normaliser le CRC (uppercase, pas d'espaces)
        val normalizedCrc = crc.trim().uppercase()
        Log.d(TAG, "Looking up game: CRC=$normalizedCrc, Console=$console")
        
        val consoleCache = gameCache[console]
        if (consoleCache != null) {
            val cached = consoleCache[normalizedCrc]
            if (cached != null) {
                Log.i(TAG, "✅ Cache hit for CRC $normalizedCrc: ${cached.name}")
                return cached
            } else {
                Log.d(TAG, "Cache miss for CRC $normalizedCrc (cache has ${consoleCache.size} entries)")
                // Log first few CRC keys for debugging
                if (consoleCache.isNotEmpty()) {
                    val sampleKeys = consoleCache.keys.take(3)
                    Log.d(TAG, "Sample CRC keys in cache: $sampleKeys")
                }
            }
        }
        
        Log.d(TAG, "Loading database for $console (CRC: $normalizedCrc)...")
        loadDatabase(console)
        
        val result = gameCache[console]?.get(normalizedCrc)
        if (result != null) {
            Log.i(TAG, "✅ Game found in database: ${result.name} (CRC: $normalizedCrc)")
        } else {
            Log.w(TAG, "❌ Game not found in database: CRC=$normalizedCrc, Console=$console (DB has ${gameCache[console]?.size ?: 0} entries)")
        }
        
        return result
    }

    /**
     * Lookup game by Name when CRC is unavailable (e.g. Save States)
     * Exact case-insensitive match has priority, then fuzzy contains.
     */
    fun lookupGameByName(name: String, console: String): GameInfo? {
        // Ensure DB is loaded
        loadDatabase(console)
        
        val consoleCache = gameCache[console] ?: return null
        val normalizedName = name.lowercase().trim()
        
        // 1. Exact match (case insensitive) -> O(N) but N is ~1000-3000
        val exactMatch = consoleCache.values.find { it.name.lowercase() == normalizedName }
        if (exactMatch != null) {
            Log.i(TAG, "Game found by NAME (Exact): ${exactMatch.name} (CRC: ${exactMatch.crc})")
            return exactMatch
        }
        
        // 2. Contains match (if name is "Super Mario Bros" and DB has "Super Mario Bros (USA)")
        // We look for the DB entry that STARTS with the requested name
        val startsWith = consoleCache.values.find { it.name.lowercase().startsWith(normalizedName) }
        if (startsWith != null) {
             Log.i(TAG, "Game found by NAME (StartsWith): ${startsWith.name} (CRC: ${startsWith.crc})")
             return startsWith
        }
        
        Log.w(TAG, "Game lookup by NAME failed for: $name")
        return null
    }

    suspend fun lookupGameByNameAsync(name: String, console: String): GameInfo? = withContext(Dispatchers.IO) {
        lookupGameByName(name, console)
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
        
        val games: MutableMap<String, GameInfo> = if (cacheValid) {
            // Load from cache (much faster!)
            Log.i(TAG, "Loading database for $consoleName from disk cache...")
            try {
                loadFromCache(cacheFile, console).toMutableMap()
            } catch (e: Exception) {
                Log.w(TAG, "Cache corrupted, re-parsing .rdb: ${e.message}")
                parseAndCache(rdbFile, cacheFile, console)
            }
        } else {
            // Parse .rdb and save to cache
            Log.i(TAG, "Loading database for $consoleName from .rdb (no cache)...")
            parseAndCache(rdbFile, cacheFile, console)
        }
        
        gameCache[console] = games
        
        Log.i(TAG, "✅ Database loaded for $consoleName: ${games.size} games")
    }
    
    private fun parseAndCache(rdbFile: File, cacheFile: File, console: String): MutableMap<String, GameInfo> {
        // Parse .rdb file with RdbParser
        val games = RdbParser.parseRdbFile(rdbFile)
        
        // Update console field for all games
        val consoleMap = games.mapValues { (_, game) ->
            game.copy(console = console)
        }.toMutableMap()
        
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
