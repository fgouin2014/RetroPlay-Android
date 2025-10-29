package com.retroplay

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * CheatManager - Gestion des cheats RetroArch
 * 
 * Supporte 2 modes :
 * 1. ZIP MODE : Lecture à la volée depuis cheats.zip (rapide, économe)
 * 2. EXTRACTED MODE : Lecture depuis fichiers extraits (plus rapide à l'usage)
 */
object CheatManager {
    private const val TAG = "CheatManager"
    
    private const val CHEATS_ZIP_PATH = "/storage/emulated/0/GameLibrary-Data/cheats.zip"
    private const val CHEATS_DIR_PATH = "/storage/emulated/0/GameLibrary-Data/cheats"
    
    /**
     * Obtenir le contenu d'un fichier de cheats
     * 
     * @param system Nom du système (ex: "nes", "snes", "psx")
     * @param romName Nom de la ROM sans extension (ex: "Super Mario Bros")
     * @return Contenu du fichier .cht ou null si non trouvé
     */
    fun getCheatFile(system: String, romName: String): String? {
        // Essayer d'abord le mode extrait (plus rapide)
        val extractedFile = File("$CHEATS_DIR_PATH/retroarch/$system/$romName.cht")
        if (extractedFile.exists()) {
            Log.d(TAG, "Loading cheat from extracted file: ${extractedFile.path}")
            return try {
                extractedFile.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading extracted cheat file", e)
                null
            }
        }
        
        // Sinon, lire depuis le ZIP (mode à la volée)
        val zipFile = File(CHEATS_ZIP_PATH)
        if (!zipFile.exists()) {
            Log.w(TAG, "No cheats found (neither ZIP nor extracted)")
            return null
        }
        
        Log.d(TAG, "Loading cheat from ZIP: retroarch/$system/$romName.cht")
        return readCheatFromZip(zipFile, "retroarch/$system/$romName.cht")
    }
    
    /**
     * Obtenir la liste des ROMs avec cheats pour un système
     * 
     * @param system Nom du système
     * @return Liste des noms de ROMs (sans extension)
     */
    fun getAvailableCheats(system: String): List<String> {
        val cheats = mutableListOf<String>()
        
        // D'abord vérifier le mode extrait
        val extractedDir = File("$CHEATS_DIR_PATH/retroarch/$system")
        if (extractedDir.exists() && extractedDir.isDirectory) {
            extractedDir.listFiles()?.forEach { file ->
                if (file.extension == "cht") {
                    cheats.add(file.nameWithoutExtension)
                }
            }
            return cheats.sorted()
        }
        
        // Sinon, lister depuis le ZIP
        val zipFile = File(CHEATS_ZIP_PATH)
        if (!zipFile.exists()) {
            return emptyList()
        }
        
        try {
            ZipFile(zipFile).use { zip ->
                val prefix = "retroarch/$system/"
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".cht") }
                    .forEach { entry ->
                        val fileName = entry.name.removePrefix(prefix).removeSuffix(".cht")
                        cheats.add(fileName)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing cheats from ZIP", e)
        }
        
        return cheats.sorted()
    }
    
    /**
     * Vérifier si des cheats existent pour une ROM
     */
    fun hasCheats(system: String, romName: String): Boolean {
        // Vérifier mode extrait
        val extractedFile = File("$CHEATS_DIR_PATH/retroarch/$system/$romName.cht")
        if (extractedFile.exists()) {
            return true
        }
        
        // Vérifier dans le ZIP
        val zipFile = File(CHEATS_ZIP_PATH)
        if (!zipFile.exists()) {
            return false
        }
        
        try {
            ZipFile(zipFile).use { zip ->
                val entryPath = "retroarch/$system/$romName.cht"
                return zip.getEntry(entryPath) != null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ZIP for cheats", e)
            return false
        }
    }
    
    /**
     * Obtenir des informations sur le mode d'installation
     */
    fun getCheatInfo(): CheatInfo {
        val zipFile = File(CHEATS_ZIP_PATH)
        val extractedDir = File(CHEATS_DIR_PATH)
        
        val hasZip = zipFile.exists()
        val hasExtracted = extractedDir.exists() && (extractedDir.listFiles()?.size ?: 0) > 10
        
        val mode = when {
            hasExtracted -> CheatMode.EXTRACTED
            hasZip -> CheatMode.ZIP
            else -> CheatMode.NONE
        }
        
        val size = when (mode) {
            CheatMode.ZIP -> zipFile.length()
            CheatMode.EXTRACTED -> calculateDirSize(extractedDir)
            CheatMode.NONE -> 0L
        }
        
        return CheatInfo(mode, size)
    }
    
    /**
     * Lire un fichier depuis le ZIP
     */
    private fun readCheatFromZip(zipFile: File, entryPath: String): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryPath) ?: return null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from ZIP: $entryPath", e)
            null
        }
    }
    
    /**
     * Calculer la taille d'un répertoire
     */
    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
}

/**
 * Mode d'installation des cheats
 */
enum class CheatMode {
    NONE,       // Pas installé
    ZIP,        // ZIP compressé (lecture à la volée)
    EXTRACTED   // Fichiers extraits
}

/**
 * Informations sur l'installation des cheats
 */
data class CheatInfo(
    val mode: CheatMode,
    val sizeBytes: Long
) {
    val sizeMB: Float
        get() = sizeBytes / (1024f * 1024f)
    
    val description: String
        get() = when (mode) {
            CheatMode.NONE -> "Not installed"
            CheatMode.ZIP -> "ZIP mode (%.1f MB) - On-the-fly".format(sizeMB)
            CheatMode.EXTRACTED -> "Extracted (%.1f MB)".format(sizeMB)
        }
}

