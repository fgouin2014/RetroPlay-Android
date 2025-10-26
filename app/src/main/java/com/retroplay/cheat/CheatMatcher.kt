package com.retroplay.cheat

import android.util.Log
import java.io.File
import java.util.zip.CRC32

/**
 * Matcher intelligent de codes de triche
 * Trouve le bon fichier .cht selon:
 * - CRC32 de la ROM
 * - Région (USA = GameShark, Europe = Action Replay/Game Buster)
 * - Nom approximatif (fuzzy matching)
 */
class CheatMatcher {
    
    companion object {
        private const val TAG = "CheatMatcher"
    }
    
    /**
     * Calculer le CRC32 d'une ROM
     */
    fun calculateRomCRC(romPath: String): String? {
        val romFile = File(romPath)
        if (!romFile.exists()) {
            Log.w(TAG, "ROM not found: $romPath")
            return null
        }
        
        return try {
            val crc = CRC32()
            romFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    crc.update(buffer, 0, bytesRead)
                }
            }
            
            val crcHex = crc.value.toString(16).uppercase().padStart(8, '0')
            Log.i(TAG, "ROM CRC32: $crcHex for ${romFile.name}")
            crcHex
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating CRC", e)
            null
        }
    }
    
    /**
     * Détecter la région depuis le nom de la ROM
     * 
     * Conventions:
     * - (USA) ou (U) = USA
     * - (Europe) ou (E) = Europe
     * - (Japan) ou (J) = Japan
     * - (World) ou (W) = Multi-région
     */
    fun detectRegion(gameName: String): String {
        return when {
            gameName.contains("(USA", ignoreCase = true) || gameName.contains("(U)") -> "USA"
            gameName.contains("(Europe", ignoreCase = true) || gameName.contains("(E)") -> "Europe"
            gameName.contains("(Japan", ignoreCase = true) || gameName.contains("(J)") -> "Japan"
            gameName.contains("(World", ignoreCase = true) || gameName.contains("(W)") -> "World"
            gameName.contains("(USA, Europe") -> "USA, Europe"
            else -> "Unknown"
        }
    }
    
    /**
     * Détecter le type de cheat device selon la région
     * 
     * USA = GameShark
     * Europe = Action Replay / Game Buster
     * Japan = Pro Action Replay / Xploder
     */
    fun detectCheatDevice(region: String): List<String> {
        return when (region) {
            "USA" -> listOf("GameShark", "Game Genie")
            "Europe" -> listOf("Action Replay", "Game Buster", "Xploder")
            "Japan" -> listOf("Pro Action Replay", "Xploder")
            "World", "USA, Europe" -> listOf("GameShark", "Action Replay", "Game Buster")
            else -> listOf("GameShark", "Action Replay")
        }
    }
    
    /**
     * Chercher un fichier .cht compatible avec la ROM
     * 
     * Stratégie:
     * 1. Match exact par nom
     * 2. Match par région
     * 3. Match par début de nom (fuzzy)
     */
    fun findCompatibleCheatFile(
        console: String,
        gameName: String,
        romPath: String,
        cheatDir: File
    ): File? {
        Log.i(TAG, "🔍 findCompatibleCheatFile called!")
        Log.i(TAG, "  cheatDir = ${cheatDir.absolutePath}")
        Log.i(TAG, "  exists = ${cheatDir.exists()}, isDir = ${cheatDir.isDirectory}")
        
        if (!cheatDir.exists() || !cheatDir.isDirectory) {
            Log.w(TAG, "❌ Cheat dir doesn't exist or not a directory")
            return null
        }
        
        val region = detectRegion(gameName)
        val devices = detectCheatDevice(region)
        val baseGameName = gameName.split("(")[0].trim()
        
        Log.i(TAG, "🔍 Searching cheat for: $baseGameName [$region] → devices: $devices")
        
        // Chercher dans tous les sous-répertoires
        cheatDir.listFiles()?.forEach { subdir ->
            if (subdir.isDirectory) {
                // Chercher fichiers .cht correspondants
                val cheatFiles = subdir.listFiles { file ->
                    file.name.endsWith(".cht") && 
                    file.name.contains(baseGameName, ignoreCase = true)
                }
                
                if (cheatFiles != null && cheatFiles.isNotEmpty()) {
                    // Prioriser selon le cheat device
                    for (device in devices) {
                        val deviceFile = cheatFiles.firstOrNull { 
                            it.name.contains(device, ignoreCase = true) 
                        }
                        if (deviceFile != null) {
                            Log.i(TAG, "✅ Found cheat file for $region ($device): ${deviceFile.name}")
                            return deviceFile
                        }
                    }
                    
                    // Si aucun device spécifique, prendre le premier
                    Log.i(TAG, "✅ Found cheat file (fallback): ${cheatFiles[0].name}")
                    return cheatFiles[0]
                }
            }
        }
        
        return null
    }
    
    /**
     * Générer un fichier de mapping CRC → .cht
     * Format: {CRC32}={CheatFilePath}
     */
    fun generateCRCMapping(console: String, romsDir: File, cheatsDir: File): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        
        romsDir.listFiles()?.forEach { romFile ->
            if (romFile.isFile) {
                val crc = calculateRomCRC(romFile.absolutePath)
                if (crc != null) {
                    val cheatFile = findCompatibleCheatFile(
                        console, 
                        romFile.nameWithoutExtension, 
                        romFile.absolutePath, 
                        cheatsDir
                    )
                    
                    if (cheatFile != null) {
                        mapping[crc] = cheatFile.absolutePath
                        Log.d(TAG, "Mapped CRC $crc → ${cheatFile.name}")
                    }
                }
            }
        }
        
        return mapping
    }
}

