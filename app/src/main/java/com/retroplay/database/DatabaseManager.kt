package com.retroplay.database

import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile
import java.util.Locale

object DatabaseManager {
    private const val TAG = "DatabaseManager"
    private const val DATABASE_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/database/rdb"
    private const val CACHE_BASE_PATH = "/storage/emulated/0/RetroPlay-Data/database/cache"
    private val NOINTRO_BASE_PATHS = listOf(
        "/storage/emulated/0/RetroPlay-Data/database/no-intro",
        "/storage/emulated/0/RetroPlay-Data/database/metadat/no-intro"
    )
    
    private val gameCache = mutableMapOf<String, MutableMap<String, GameInfo>>()
    private val noIntroCacheDir = File("$CACHE_BASE_PATH/no-intro")
    private val noIntroCrcPattern = Regex("""crc\s+([0-9a-fA-F]{8})""")
    private val noIntroGameNamePattern = Regex("""game\s*\(\s*name\s+"([^"]+)"""")
    
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
        // Pour les sous-consoles fbneo, utiliser la console parent pour la base de données
        val dbConsole = normalizeConsoleForDatabase(console)
        Log.d(TAG, "lookupGame: console='$console' -> dbConsole='$dbConsole', CRC=$crc")
        
        val consoleCache = gameCache[dbConsole]
        if (consoleCache != null) {
            val cached = consoleCache[crc]
            if (cached != null) {
                Log.d(TAG, "Cache hit for CRC $crc: ${cached.name}")
                return cached
            } else {
                Log.d(TAG, "Cache miss for CRC $crc in loaded database (${consoleCache.size} games loaded)")
            }
        } else {
            Log.d(TAG, "Database not loaded for $dbConsole, loading now...")
        }
        
        loadDatabase(dbConsole)
        
        val result = gameCache[dbConsole]?.get(crc)
        if (result != null) {
            Log.d(TAG, "✅ Found game in database: ${result.name} (CRC: $crc)")
        } else {
            Log.w(TAG, "⚠️ Game not found in database: CRC=$crc, console=$console (dbConsole=$dbConsole, cacheSize=${gameCache[dbConsole]?.size ?: 0})")
        }
        return result
    }
    
    /**
     * Normalise le nom de console pour la base de données
     * Toutes les sous-consoles arcade (fbneo/, cps1, cps2, cps3, dataeast, taito, etc.)
     * utilisent la même base de données FBNeo/Arcade
     */
    private fun normalizeConsoleForDatabase(console: String): String {
        return when {
            // Sous-consoles fbneo (fbneo/cps1, fbneo/taito, etc.)
            console.startsWith("fbneo/") -> "fbneo"
            // Consoles arcade génériques
            console == "arcade" || console == "mame" -> "fbneo"
            // Sous-consoles arcade directes (cps1, cps2, cps3, dataeast, taito, etc.)
            // Toutes ces consoles utilisent la base de données FBNeo/Arcade
            console == "cps1" || console == "cps2" || console == "cps3" || 
            console == "cpiii" || console == "dataeast" || console == "taito" ||
            console == "neogeo" || console == "sega" -> "fbneo"
            else -> console
        }
    }
    
    fun loadDatabase(console: String) {
        // Normaliser la console pour la base de données (sous-consoles → parent)
        val dbConsole = normalizeConsoleForDatabase(console)
        Log.d(TAG, "loadDatabase: console='$console' -> dbConsole='$dbConsole'")
        
        if (gameCache.containsKey(dbConsole)) {
            val cacheSize = gameCache[dbConsole]?.size ?: 0
            Log.d(TAG, "Database for $dbConsole already loaded ($cacheSize games)")
            return
        }
        
        val consoleName = getConsoleFullName(dbConsole)
        Log.d(TAG, "Loading database for console: $console (dbConsole=$dbConsole, consoleName=$consoleName)")
        
        // Look for .rdb file in RetroPlay-Data/database/rdb/
        // Pour FBNeo, essayer plusieurs noms possibles
        val rdbFile = when (dbConsole) {
            "fbneo", "arcade", "mame" -> {
                // Essayer plusieurs noms possibles pour la base de données arcade
                val possibleNames = listOf("FBNeo.rdb", "Arcade.rdb", "MAME.rdb", "fbneo.rdb", "arcade.rdb", "mame.rdb")
                val found = possibleNames.firstOrNull { name ->
                    val file = File("$DATABASE_BASE_PATH/$name")
                    val exists = file.exists()
                    Log.d(TAG, "Checking RDB file: ${file.absolutePath} -> exists=$exists")
                    exists
                }
                if (found != null) {
                    File("$DATABASE_BASE_PATH/$found")
                } else {
                    val fallback = File("$DATABASE_BASE_PATH/$consoleName.rdb")
                    Log.d(TAG, "No RDB file found in possible names, using fallback: ${fallback.absolutePath}")
                    fallback
                }
            }
            else -> {
                val file = File("$DATABASE_BASE_PATH/$consoleName.rdb")
                Log.d(TAG, "Using standard RDB path: ${file.absolutePath}")
                file
            }
        }
        
        if (!rdbFile.exists()) {
            Log.w(TAG, "⚠️ RDB file not found: ${rdbFile.absolutePath}")
            Log.w(TAG, "   Searched in: $DATABASE_BASE_PATH")
            gameCache[dbConsole] = mutableMapOf()
            return
        }
        
        Log.i(TAG, "✅ Using RDB file: ${rdbFile.name} for console $dbConsole (path: ${rdbFile.absolutePath})")
        
        // Check if we have a disk cache
        val cacheDir = File(CACHE_BASE_PATH)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val cacheFile = File("$CACHE_BASE_PATH/$consoleName.cache")
        val cacheValid = cacheFile.exists() && cacheFile.lastModified() >= rdbFile.lastModified()
        
        val games: MutableMap<String, GameInfo> = if (cacheValid) {
            // Load from cache (much faster!)
            Log.i(TAG, "Loading database for $consoleName from disk cache...")
            try {
                loadFromCache(cacheFile, dbConsole).toMutableMap().also {
                    augmentWithNoIntroVariants(it, dbConsole)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache corrupted, re-parsing .rdb: ${e.message}")
                parseAndCache(rdbFile, cacheFile, dbConsole)
            }
        } else {
            // Parse .rdb and save to cache
            Log.i(TAG, "Loading database for $consoleName from .rdb (no cache)...")
            parseAndCache(rdbFile, cacheFile, dbConsole)
        }
        
        gameCache[dbConsole] = games
        
        Log.i(TAG, "✅ Database loaded for $consoleName: ${games.size} games")
    }
    
    private fun parseAndCache(rdbFile: File, cacheFile: File, console: String): MutableMap<String, GameInfo> {
        // Parse .rdb file with RdbParser
        val games = RdbParser.parseRdbFile(rdbFile)
        
        // Update console field for all games
        val consoleMap = games.mapValues { (_, game) ->
            game.copy(console = console)
        }.toMutableMap()
        
        augmentWithNoIntroVariants(consoleMap, console)
        
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
    
    private fun augmentWithNoIntroVariants(
        gamesByCrc: MutableMap<String, GameInfo>,
        console: String
    ) {
        val consoleName = getConsoleFullName(console)
        Log.i(TAG, "No-Intro augmentation started for $consoleName")
        val noIntroFile = resolveNoIntroFile(consoleName) ?: run {
            Log.i(TAG, "No-Intro metadata missing for $consoleName (searched ${NOINTRO_BASE_PATHS.joinToString()})")
            return
        }

        val nameIndex = gamesByCrc.values.groupBy { it.name }
        var currentComment: String? = null
        var currentGameName: String? = null
        var added = 0
        var romLines = 0
        var matchedBase = 0

        noIntroFile.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                when {
                    // Capture official game name from No-Intro block header
                    trimmed.startsWith("game ") -> {
                        val m = noIntroGameNamePattern.find(trimmed)
                        currentGameName = m?.groupValues?.getOrNull(1)
                    }
                    trimmed.startsWith("comment ") -> {
                        currentComment = trimmed.substringAfter("comment")
                            .trim()
                            .trim('"')
                    }
                    trimmed.startsWith("rom ") -> {
                        romLines++
                        val crcMatch = noIntroCrcPattern.find(trimmed) ?: return@forEach
                        val crc = crcMatch.groupValues[1].uppercase(Locale.US)
                        if (gamesByCrc.containsKey(crc)) {
                            return@forEach
                        }
                        // Prefer official game name from 'game (...)', fallback to comment if used
                        val baseKey = currentGameName ?: currentComment ?: return@forEach
                        val baseGame = nameIndex[baseKey]?.firstOrNull()
                        if (baseGame != null) {
                            gamesByCrc[crc] = baseGame.copy(crc = crc)
                            added++
                            matchedBase++
                        }
                    }
                    trimmed == ")" -> {
                        currentComment = null
                        currentGameName = null
                    }
                }
            }
        }

        Log.i(
            TAG,
            "No-Intro augmentation finished for $consoleName, roms=$romLines, matched=$matchedBase, added=$added (source=${noIntroFile.absolutePath})"
        )
    }
    
    private fun resolveNoIntroFile(consoleFullName: String): File? {
        if (!noIntroCacheDir.exists()) {
            noIntroCacheDir.mkdirs()
        }

        NOINTRO_BASE_PATHS.forEach { base ->
            // 1) Exact match (.dat)
            val exactPlain = File("$base/$consoleFullName.dat")
            if (exactPlain.exists()) {
                Log.i(TAG, "Using No-Intro DAT (exact): ${exactPlain.absolutePath}")
                return exactPlain
            }

            // 2) Exact match zipped (.dat.zip)
            val exactZip = File("$base/$consoleFullName.dat.zip")
            if (exactZip.exists()) {
                val cachedFile = File(noIntroCacheDir, "$consoleFullName.dat")
                val needsExtraction = !cachedFile.exists() || cachedFile.lastModified() < exactZip.lastModified()
                if (needsExtraction) {
                    try {
                        unzipDatFile(exactZip, cachedFile)
                        Log.i(TAG, "Extracted ${exactZip.name} to cache for $consoleFullName")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract ${exactZip.name}: ${e.message}", e)
                        if (cachedFile.exists()) cachedFile.delete()
                        return@forEach
                    }
                }
                if (cachedFile.exists()) {
                    Log.i(TAG, "Using No-Intro DAT (cached from zip): ${cachedFile.absolutePath}")
                    return cachedFile
                }
            }

            // 3) Fallback: any DAT whose name starts with or contains the consoleFullName (handles variants)
            val baseDir = File(base)
            if (baseDir.exists() && baseDir.isDirectory) {
                val candidates = baseDir.listFiles { file ->
                    val n = file.name
                    file.isFile && n.endsWith(".dat", ignoreCase = true) &&
                        (n.startsWith(consoleFullName, ignoreCase = true) || n.contains(consoleFullName, ignoreCase = true))
                }?.sortedBy { it.name } ?: emptyList()

                if (candidates.isNotEmpty()) {
                    Log.i(TAG, "Using No-Intro DAT (variant): ${candidates.first().absolutePath}")
                    return candidates.first()
                }

                // 4) Fallback for zipped variants
                val zippedCandidates = baseDir.listFiles { file ->
                    val n = file.name
                    file.isFile && n.endsWith(".dat.zip", ignoreCase = true) &&
                        (n.startsWith(consoleFullName, ignoreCase = true) || n.contains(consoleFullName, ignoreCase = true))
                } ?: emptyArray()
                if (zippedCandidates.isNotEmpty()) {
                    val chosen = zippedCandidates.sortedBy { it.name }.first()
                    val cachedFile = File(noIntroCacheDir, "${consoleFullName}.dat")
                    val needsExtraction = !cachedFile.exists() || cachedFile.lastModified() < chosen.lastModified()
                    if (needsExtraction) {
                        try {
                            unzipDatFile(chosen, cachedFile)
                            Log.i(TAG, "Extracted ${chosen.name} (variant) to cache for $consoleFullName")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to extract ${chosen.name}: ${e.message}", e)
                            if (cachedFile.exists()) cachedFile.delete()
                            return@forEach
                        }
                    }
                    if (cachedFile.exists()) {
                        Log.i(TAG, "Using No-Intro DAT (cached from zip variant): ${cachedFile.absolutePath}")
                        return cachedFile
                    }
                }
            }
        }
        return null
    }
    
    private fun unzipDatFile(zipFile: File, outputFile: File) {
        ZipFile(zipFile).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith(".dat", ignoreCase = true) }
                ?: throw IllegalArgumentException("No .dat entry found in ${zipFile.name}")

            outputFile.outputStream().use { out ->
                zip.getInputStream(entry).use { input ->
                    input.copyTo(out)
                }
            }
            outputFile.setLastModified(zipFile.lastModified())
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
        // Gérer toutes les sous-consoles arcade (fbneo/, cps1, cps2, cps3, dataeast, taito, etc.)
        // Toutes utilisent la même base de données FBNeo/Arcade
        val dbConsole = normalizeConsoleForDatabase(console)
        if (dbConsole == "fbneo") {
            return "FBNeo" // ou "Arcade" selon le nom du fichier .rdb disponible
        }
        
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
