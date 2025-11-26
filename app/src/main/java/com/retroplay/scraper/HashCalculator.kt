package com.retroplay.scraper

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * HashCalculator - Calcul unifié de hash pour identification des ROMs
 * 
 * Style sselph's scraper: utilise SHA1 > MD5 > CRC32 pour identification
 * Gère automatiquement:
 * - Extraction depuis archives ZIP
 * - Headers spéciaux (NES iNES, etc.)
 * - Calcul de tous les hashes en une seule passe
 */
object HashCalculator {
    private const val TAG = "HashCalculator"
    private const val BUFFER_SIZE = 8192
    
    /**
     * Représente les hashes calculés pour une ROM
     * Priorité: SHA1 > MD5 > CRC32 (comme sselph)
     */
    data class GameHash(
        val crc32: String? = null,
        val md5: String? = null,
        val sha1: String? = null
    ) {
        /**
         * Retourne le hash principal (priorité: SHA1 > MD5 > CRC32)
         */
        fun getPrimaryHash(): String? {
            return sha1 ?: md5 ?: crc32
        }
        
        /**
         * Retourne le hash principal avec son type
         */
        fun getPrimaryHashWithType(): Pair<String, String>? {
            return when {
                sha1 != null -> Pair(sha1, "sha1")
                md5 != null -> Pair(md5, "md5")
                crc32 != null -> Pair(crc32, "crc32")
                else -> null
            }
        }
    }
    
    /**
     * Calcule tous les hashes (CRC32, MD5, SHA1) pour un fichier ROM
     * Gère automatiquement les archives ZIP et les headers spéciaux
     * 
     * @param romFile Fichier ROM ou archive ZIP
     * @return GameHash avec tous les hashes calculés, ou null en cas d'erreur
     */
    fun calculateHash(romFile: File): GameHash? {
        return try {
            if (!romFile.exists()) {
                Log.e(TAG, "File not found: ${romFile.absolutePath}")
                return null
            }
            
            // Si c'est une archive ZIP, extraire et calculer sur la ROM à l'intérieur
            if (romFile.name.endsWith(".zip", ignoreCase = true) || 
                romFile.name.endsWith(".7z", ignoreCase = true)) {
                return calculateHashFromArchive(romFile)
            }
            
            // Fichier ROM direct
            calculateHashFromFile(romFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash for ${romFile.name}: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calcule les hashes pour un fichier ROM direct
     */
    private fun calculateHashFromFile(romFile: File): GameHash? {
        return try {
            val crc32 = CRC32()
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            
            val buffer = ByteArray(BUFFER_SIZE)
            var skipBytes = 0
            
            // NES ROMs: Skip 16-byte iNES header (starts with "NES\x1A")
            if (romFile.name.endsWith(".nes", ignoreCase = true)) {
                FileInputStream(romFile).use { fis ->
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
            
            // Calculer tous les hashes en une seule passe
            FileInputStream(romFile).use { fis ->
                // Skip header if needed
                if (skipBytes > 0) {
                    fis.skip(skipBytes.toLong())
                }
                
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    crc32.update(buffer, 0, bytesRead)
                    md5.update(buffer, 0, bytesRead)
                    sha1.update(buffer, 0, bytesRead)
                }
            }
            
            val crc32Value = crc32.value.toString(16).uppercase().padStart(8, '0')
            val md5Value = md5.digest().joinToString("") { "%02x".format(it) }
            val sha1Value = sha1.digest().joinToString("") { "%02x".format(it) }
            
            Log.d(TAG, "Hashes calculated for ${romFile.name}: CRC32=$crc32Value, MD5=$md5Value, SHA1=$sha1Value")
            
            GameHash(
                crc32 = crc32Value,
                md5 = md5Value,
                sha1 = sha1Value
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash from file: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calcule les hashes pour une ROM dans une archive ZIP
     */
    private fun calculateHashFromArchive(archiveFile: File): GameHash? {
        return try {
            if (!archiveFile.name.endsWith(".zip", ignoreCase = true)) {
                Log.w(TAG, "Only .zip archives supported for now (not .7z)")
                return null
            }
            
            ZipFile(archiveFile).use { zip ->
                // Trouver le premier fichier ROM dans l'archive
                val entry = zip.entries().asSequence().firstOrNull { entry ->
                    !entry.isDirectory && isRomExtension(entry.name)
                }
                
                if (entry == null) {
                    Log.w(TAG, "No ROM file found in archive: ${archiveFile.name}")
                    return null
                }
                
                Log.d(TAG, "Found ROM in archive: ${entry.name}")
                
                // Calculer les hashes sur la ROM extraite
                zip.getInputStream(entry).use { stream ->
                    val crc32 = CRC32()
                    val md5 = MessageDigest.getInstance("MD5")
                    val sha1 = MessageDigest.getInstance("SHA-1")
                    
                    val buffer = ByteArray(BUFFER_SIZE)
                    var skipBytes = 0
                    
                    // Vérifier si c'est une ROM NES avec header iNES
                    if (entry.name.endsWith(".nes", ignoreCase = true)) {
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
                            // Pas iNES, inclure les 4 bytes lus
                            crc32.update(header, 0, 4)
                            md5.update(header, 0, 4)
                            sha1.update(header, 0, 4)
                        }
                    }
                    
                    // Calculer tous les hashes sur le reste des données
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        crc32.update(buffer, 0, bytesRead)
                        md5.update(buffer, 0, bytesRead)
                        sha1.update(buffer, 0, bytesRead)
                    }
                    
                    val crc32Value = crc32.value.toString(16).uppercase().padStart(8, '0')
                    val md5Value = md5.digest().joinToString("") { "%02x".format(it) }
                    val sha1Value = sha1.digest().joinToString("") { "%02x".format(it) }
                    
                    Log.d(TAG, "Hashes from ZIP: ${archiveFile.name} → ${entry.name} = CRC32=$crc32Value, MD5=$md5Value, SHA1=$sha1Value")
                    
                    GameHash(
                        crc32 = crc32Value,
                        md5 = md5Value,
                        sha1 = sha1Value
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash from archive: ${e.message}", e)
            null
        }
    }
    
    /**
     * Vérifie si une extension de fichier est une ROM valide
     */
    private fun isRomExtension(fileName: String): Boolean {
        val extensions = listOf(
            ".nes", ".unh", ".unf",  // NES
            ".sfc", ".smc",          // SNES
            ".gb", ".gbc", ".gba",   // Game Boy
            ".bin", ".gen", ".md",   // Genesis/Mega Drive
            ".sms", ".gg",           // Master System / Game Gear
            ".32x",                   // 32X
            ".lnx",                   // Lynx
            ".a26", ".a52", ".a78",   // Atari
            ".ngp", ".ngc",           // Neo Geo Pocket
            ".ws", ".wsc",            // WonderSwan
            ".pce", ".sgx",           // PC Engine
            ".z64", ".n64", ".v64"    // N64
        )
        
        return extensions.any { fileName.endsWith(it, ignoreCase = true) }
    }
}


