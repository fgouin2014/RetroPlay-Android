package com.retroplay

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset

/**
 * PSXSerialExtractor - Extrait les numéros de série PSX depuis les fichiers ROM
 * 
 * Basé sur le code de Lemuroid SerialScanner.kt
 * Format attendu: SLUS-01234, SLES-00567, etc.
 * 
 * Référence: https://github.com/libretro/RetroArch/blob/master/libretro-common/file/file_path.c
 */
object PSXSerialExtractor {
    private const val TAG = "PSXSerialExtractor"
    
    // Liste des préfixes de numéros de série PSX officiels
    private val PSX_BASE_SERIALS = listOf(
        "CPCS",  // Capcom (Japon)
        "SCES",  // Sony Computer Entertainment (Europe)
        "SIPS",  // Sony Interactive (Japon)
        "SLKA",  // Sony (Corée)
        "SLPS",  // Sony (Japon)
        "SLUS",  // Sony (USA) - Le plus commun
        "ESPM",  // Espagne
        "SLED",  // Sony (Europe, Demo)
        "SCPS",  // Sony Computer Entertainment (Japon)
        "SCAJ",  // Sony Computer Entertainment (Japon, Asia)
        "PAPX",  // PlayStation (Europe)
        "SLES",  // Sony (Europe) - Très commun
        "HPS",   // Homebrew
        "LSP",   // Licensed Software Publisher
        "SLPM",  // Sony (Japon, Mini)
        "SCUS",  // Sony Computer Entertainment (USA)
        "SCED"   // Sony Computer Entertainment (Europe, Demo)
    )
    
    private val PS_SERIAL_REGEX = Regex("^([A-Z]+)-?([0-9]+)")
    private val PS_SERIAL_REGEX2 = Regex("^([A-Z]+)_?([0-9]{3})\\.([0-9]{2})")
    private const val PS_SERIAL_MAX_SIZE = 12
    private const val HEADER_SIZE = 64 * 1024  // 64KB - suffisant pour trouver le serial
    
    /**
     * Extrait le numéro de série PSX depuis un fichier
     * 
     * @param file Fichier ROM PSX (.iso, .bin, .pbp, etc.)
     * @return Numéro de série (ex: "SLUS-01234") ou null si non trouvé
     */
    fun extractSerial(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        
        try {
            FileInputStream(file).use { input ->
                // Lire les 64 premiers KB (où se trouve généralement le serial)
                val headerSize = minOf(HEADER_SIZE, file.length().toInt())
                if (headerSize < 1024) {
                    return null  // Fichier trop petit
                }
                
                val header = ByteArray(headerSize)
                val bytesRead = input.read(header)
                if (bytesRead < headerSize) {
                    return null
                }
                
                // Chercher les patterns de numéros de série dans le header
                val serial = textSearch(PSX_BASE_SERIALS, header, PS_SERIAL_MAX_SIZE)
                    .mapNotNull { rawSerial -> parsePSXSerial(rawSerial) }
                    .firstOrNull()
                
                if (serial != null) {
                    Log.d(TAG, "Extracted PSX serial for ${file.name}: $serial")
                }
                
                return serial
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting PSX serial from ${file.name}: ${e.message}")
            return null
        }
    }
    
    /**
     * Cherche les patterns de numéros de série dans un buffer
     */
    private fun textSearch(
        queries: List<String>,
        buffer: ByteArray,
        maxSize: Int,
        searchSize: Int = buffer.size
    ): Sequence<String> {
        val text = String(buffer, 0, minOf(searchSize, buffer.size), Charset.forName("US-ASCII"))
        
        return sequence {
            for (query in queries) {
                var index = 0
                while (true) {
                    val foundIndex = text.indexOf(query, index, ignoreCase = false)
                    if (foundIndex == -1) break
                    
                    // Extraire le numéro de série complet (query + chiffres)
                    val start = foundIndex
                    val end = minOf(start + maxSize, text.length)
                    var candidate = text.substring(start, end)
                    
                    // Nettoyer: prendre seulement jusqu'au premier caractère non valide
                    // Format attendu: SLUS-01234 ou SLUS01234
                    val cleaned = candidate.takeWhile { 
                        it.isLetterOrDigit() || it == '-' || it == '_' || it == '.'
                    }
                    
                    if (cleaned.length >= query.length + 1) {  // Au moins préfixe + 1 chiffre
                        yield(cleaned)
                    }
                    
                    index = foundIndex + 1
                }
            }
        }
    }
    
    /**
     * Parse un numéro de série PSX brut en format standardisé
     * 
     * Formats acceptés:
     * - SLUS-01234
     * - SLUS01234
     * - SLUS_012.34
     */
    private fun parsePSXSerial(rawSerial: String): String? {
        // Format 1: SLUS-01234 ou SLUS01234
        val match1 = PS_SERIAL_REGEX.find(rawSerial)
        if (match1 != null) {
            val prefix = match1.groupValues[1]
            val number = match1.groupValues[2]
            return "$prefix-$number"
        }
        
        // Format 2: SLUS_012.34
        val match2 = PS_SERIAL_REGEX2.find(rawSerial)
        if (match2 != null) {
            val prefix = match2.groupValues[1]
            val number = match2.groupValues[2]
            val subNumber = match2.groupValues[3]
            return "$prefix-$number.$subNumber"
        }
        
        return null
    }
}

