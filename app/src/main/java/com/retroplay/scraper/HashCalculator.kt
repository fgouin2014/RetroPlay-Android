package com.retroplay.scraper

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * HashCalculator - Calcul unifié de hash pour identification des ROMs
 * 
 * Style sselph's scraper: utilise SHA1 > MD5 > CRC32 pour identification
 * Gère automatiquement:
 * - Calcul depuis archives ZIP et .7z (streaming sans extraction complète)
 * - Headers spéciaux (NES iNES, etc.) même dans les archives
 * - Calcul de tous les hashes (CRC32, MD5, SHA1) en une seule passe
 * 
 * Performance:
 * - ZIP: Utilise ZipFile.getInputStream() pour streaming
 * - .7z: Utilise SevenZFile.read() pour streaming
 * - Pas d'extraction complète sur disque, calcul direct depuis l'archive
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
     * Gère automatiquement les archives ZIP/.7z et les headers spéciaux
     * 
     * @param romFile Fichier ROM ou archive ZIP/.7z
     * @return GameHash avec tous les hashes calculés, ou null en cas d'erreur
     * 
     * Note: Pour les archives, le calcul se fait en streaming sans extraction complète
     */
    fun calculateHash(romFile: File): GameHash? {
        return try {
            if (!romFile.exists()) {
                Log.e(TAG, "File not found: ${romFile.absolutePath}")
                return null
            }
            
            Log.d(TAG, "Calculating hash for: ${romFile.name} (${romFile.length()} bytes)")
            
            // Si c'est une archive ZIP, extraire et calculer sur la ROM à l'intérieur
            if (romFile.name.endsWith(".zip", ignoreCase = true) || 
                romFile.name.endsWith(".7z", ignoreCase = true)) {
                Log.d(TAG, "Archive detected: ${romFile.name}, calculating hash from archive...")
                val result = calculateHashFromArchive(romFile)
                if (result == null) {
                    Log.w(TAG, "Failed to calculate hash from archive: ${romFile.name}")
                } else {
                    Log.i(TAG, "✅ Hash calculated from archive: CRC32=${result.crc32}, MD5=${result.md5}, SHA1=${result.sha1}")
                }
                return result
            }
            
            // Fichier ROM direct
            Log.d(TAG, "Direct ROM file, calculating hash...")
            val result = calculateHashFromFile(romFile)
            if (result == null) {
                Log.w(TAG, "Failed to calculate hash from file: ${romFile.name}")
            } else {
                Log.i(TAG, "✅ Hash calculated from file: CRC32=${result.crc32}, MD5=${result.md5}, SHA1=${result.sha1}")
            }
            result
            
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
     * Calcule les hashes pour une ROM dans une archive ZIP ou .7z
     * Calcul en streaming sans extraction complète sur disque
     */
    private fun calculateHashFromArchive(archiveFile: File): GameHash? {
        return try {
            when {
                archiveFile.name.endsWith(".zip", ignoreCase = true) -> {
                    calculateHashFromZip(archiveFile)
                }
                archiveFile.name.endsWith(".7z", ignoreCase = true) -> {
                    calculateHashFrom7z(archiveFile)
                }
                else -> {
                    Log.w(TAG, "Unsupported archive format: ${archiveFile.name}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash from archive: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calcule les hashes pour une ROM dans une archive ZIP
     * Utilise ZipFile.getInputStream() pour streaming sans extraction complète
     */
    private fun calculateHashFromZip(zipFile: File): GameHash? {
        return try {
            ZipFile(zipFile).use { zip ->
                // Trouver le premier fichier ROM dans l'archive
                val entry = zip.entries().asSequence().firstOrNull { entry ->
                    !entry.isDirectory && isRomExtension(entry.name)
                }
                
                if (entry == null) {
                    Log.w(TAG, "No ROM file found in ZIP: ${zipFile.name}")
                    return null
                }
                
                Log.d(TAG, "Found ROM in ZIP: ${entry.name}")
                
                // Calculer les hashes en streaming depuis l'archive (pas d'extraction complète)
                zip.getInputStream(entry).use { stream ->
                    calculateHashFromStream(stream, entry.name, "ZIP")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash from ZIP: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calcule les hashes pour une ROM dans une archive .7z
     * Utilise SevenZFile.read() pour streaming sans extraction complète
     */
    private fun calculateHashFrom7z(sevenZFile: File): GameHash? {
        return try {
            SevenZFile(sevenZFile).use { archive ->
                // Trouver le premier fichier ROM dans l'archive
                var romEntry: org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry? = null
                
                while (true) {
                    val entry = archive.nextEntry ?: break
                    
                    if (!entry.isDirectory && isRomExtension(entry.name)) {
                        romEntry = entry
                        break
                    }
                }
                
                if (romEntry == null) {
                    Log.w(TAG, "No ROM file found in .7z: ${sevenZFile.name}")
                    return null
                }
                
                Log.d(TAG, "Found ROM in .7z: ${romEntry.name}")
                
                // Calculer les hashes directement depuis SevenZFile (streaming)
                val crc32 = CRC32()
                val md5 = MessageDigest.getInstance("MD5")
                val sha1 = MessageDigest.getInstance("SHA-1")
                
                val buffer = ByteArray(BUFFER_SIZE)
                
                // Vérifier si c'est une ROM NES avec header iNES
                if (romEntry.name.endsWith(".nes", ignoreCase = true)) {
                    val header = ByteArray(4)
                    val headerRead = archive.read(header)
                    
                    if (headerRead == 4 && 
                        header[0] == 'N'.code.toByte() && 
                        header[1] == 'E'.code.toByte() && 
                        header[2] == 'S'.code.toByte() && 
                        header[3] == 0x1A.toByte()) {
                        // Skip remaining 12 bytes of iNES header (already read 4)
                        val skipBuffer = ByteArray(12)
                        archive.read(skipBuffer)
                        Log.d(TAG, "iNES header detected in ${romEntry.name}, skipping 16 bytes")
                    } else {
                        // Pas iNES, inclure les bytes lus dans le hash
                        if (headerRead > 0) {
                            crc32.update(header, 0, headerRead)
                            md5.update(header, 0, headerRead)
                            sha1.update(header, 0, headerRead)
                        }
                    }
                }
                
                // Calculer tous les hashes sur le reste des données (streaming depuis .7z)
                var bytesRead: Int
                while (archive.read(buffer).also { bytesRead = it } != -1) {
                    crc32.update(buffer, 0, bytesRead)
                    md5.update(buffer, 0, bytesRead)
                    sha1.update(buffer, 0, bytesRead)
                }
                
                val crc32Value = crc32.value.toString(16).uppercase().padStart(8, '0')
                val md5Value = md5.digest().joinToString("") { "%02x".format(it) }
                val sha1Value = sha1.digest().joinToString("") { "%02x".format(it) }
                
                Log.d(TAG, "Hashes from .7z: ${sevenZFile.name} → ${romEntry.name} = CRC32=$crc32Value, MD5=$md5Value, SHA1=$sha1Value")
                
                GameHash(
                    crc32 = crc32Value,
                    md5 = md5Value,
                    sha1 = sha1Value
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash from .7z: ${e.message}", e)
            null
        }
    }
    
    /**
     * Calcule les hashes depuis un InputStream (générique pour ZIP et .7z)
     * Gère automatiquement les headers spéciaux (iNES, etc.)
     */
    private fun calculateHashFromStream(
        stream: InputStream,
        entryName: String,
        archiveType: String
    ): GameHash? {
        return try {
            val crc32 = CRC32()
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            
            val buffer = ByteArray(BUFFER_SIZE)
            
            // Vérifier si c'est une ROM NES avec header iNES
            if (entryName.endsWith(".nes", ignoreCase = true)) {
                val header = ByteArray(4)
                val headerRead = stream.read(header)
                
                if (headerRead == 4 && 
                    header[0] == 'N'.code.toByte() && 
                    header[1] == 'E'.code.toByte() && 
                    header[2] == 'S'.code.toByte() && 
                    header[3] == 0x1A.toByte()) {
                    // Skip remaining 12 bytes of iNES header (already read 4)
                    stream.skip(12)
                    Log.d(TAG, "iNES header detected in $entryName, skipping 16 bytes")
                } else {
                    // Pas iNES, inclure les 4 bytes lus dans le hash
                    if (headerRead > 0) {
                        crc32.update(header, 0, headerRead)
                        md5.update(header, 0, headerRead)
                        sha1.update(header, 0, headerRead)
                    }
                }
            }
            
            // Calculer tous les hashes sur le reste des données (streaming)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                crc32.update(buffer, 0, bytesRead)
                md5.update(buffer, 0, bytesRead)
                sha1.update(buffer, 0, bytesRead)
            }
            
            val crc32Value = crc32.value.toString(16).uppercase().padStart(8, '0')
            val md5Value = md5.digest().joinToString("") { "%02x".format(it) }
            val sha1Value = sha1.digest().joinToString("") { "%02x".format(it) }
            
            Log.d(TAG, "Hashes from $archiveType: $entryName = CRC32=$crc32Value, MD5=$md5Value, SHA1=$sha1Value")
            
            GameHash(
                crc32 = crc32Value,
                md5 = md5Value,
                sha1 = sha1Value
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash from stream: ${e.message}", e)
            null
        }
    }
    
    /**
     * Vérifie si une extension de fichier est une ROM valide
     * Inclut tous les formats supportés par RetroArch/Libretro
     */
    private fun isRomExtension(fileName: String): Boolean {
        val extensions = listOf(
            // NES
            ".nes", ".unh", ".unf", ".fds",
            // SNES
            ".sfc", ".smc", ".fig",
            // Game Boy
            ".gb", ".gbc", ".gba",
            // Genesis/Mega Drive
            ".bin", ".gen", ".md", ".smd",
            // Master System / Game Gear
            ".sms", ".gg",
            // 32X
            ".32x",
            // Lynx
            ".lnx",
            // Atari
            ".a26", ".a52", ".a78",
            // Neo Geo Pocket
            ".ngp", ".ngc",
            // WonderSwan
            ".ws", ".wsc",
            // PC Engine / TurboGrafx-16
            ".pce", ".sgx", ".sgd",
            // N64
            ".z64", ".n64", ".v64", ".u64",
            // PlayStation
            ".cue", ".bin", ".img", ".iso", ".pbp",
            // Saturn
            ".iso", ".bin", ".cue",

            // PSP
            ".iso", ".cso", ".pbp",
            // Autres
            ".rom", ".bin"
        )
        
        return extensions.any { fileName.endsWith(it, ignoreCase = true) }
    }
}


