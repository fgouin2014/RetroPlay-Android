package com.retroplay.database

import android.util.Log
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.Value
import org.msgpack.value.ValueType
import java.io.File
import java.io.FileInputStream

/**
 * Parser for libretro .rdb (RetroArch Database) files.
 * 
 * .rdb files are binary MessagePack-encoded databases containing game metadata.
 * Each entry is a map with fields like: crc, name, genre, developer, etc.
 */
object RdbParser {
    private const val TAG = "RdbParser"
    
    /**
     * Parse a .rdb file and extract game information.
     * 
     * @param rdbFile The .rdb file to parse
     * @return Map of CRC (uppercase hex string) to GameInfo
     */
    fun parseRdbFile(rdbFile: File): Map<String, GameInfo> {
        val startTime = System.currentTimeMillis()
        val games = mutableMapOf<String, GameInfo>()
        
        if (!rdbFile.exists()) {
            Log.w(TAG, "RDB file not found: ${rdbFile.absolutePath}")
            return emptyMap()
        }
        
        try {
            FileInputStream(rdbFile).use { fis ->
                val unpacker = MessagePack.newDefaultUnpacker(fis)
                var entryCount = 0
                
                while (unpacker.hasNext()) {
                    try {
                        val entry = unpacker.unpackValue()
                        
                        if (entry.valueType == ValueType.MAP) {
                            val gameInfo = parseGameEntry(entry.asMapValue().map())
                            if (gameInfo != null && gameInfo.crc.isNotEmpty()) {
                                games[gameInfo.crc] = gameInfo
                                entryCount++
                            }
                        }
                    } catch (e: Exception) {
                        // Skip invalid entries
                        Log.d(TAG, "Skipping invalid entry: ${e.message}")
                    }
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                Log.i(TAG, "✅ Parsed ${rdbFile.name}: $entryCount games in ${elapsed}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing RDB file: ${rdbFile.name}", e)
        }
        
        return games
    }
    
    /**
     * Parse a single game entry from the MessagePack map.
     */
    private fun parseGameEntry(entryMap: Map<Value, Value>): GameInfo? {
        var name: String? = null
        var crc: String? = null
        var genre: String? = null
        var developer: String? = null
        var publisher: String? = null
        var releaseYear: Int? = null
        var releaseMonth: Int? = null
        var maxPlayers = 1
        var hasRumble = false
        var hasAnalog = false
        var isHack = false
        var isHomebrew = false
        
        for ((key, value) in entryMap) {
            if (key.valueType != ValueType.STRING) continue
            
            val fieldName = key.asStringValue().asString()
            
            when (fieldName) {
                "name", "description" -> {
                    if (name == null && value.valueType == ValueType.STRING) {
                        name = value.asStringValue().asString()
                    }
                }
                "crc" -> {
                    when (value.valueType) {
                        ValueType.STRING -> {
                            crc = value.asStringValue().asString().uppercase()
                        }
                        ValueType.BINARY -> {
                            // CRC stored as binary (4 bytes)
                            val bytes = value.asBinaryValue().asByteArray()
                            crc = bytesToHex(bytes).uppercase()
                        }
                        ValueType.INTEGER -> {
                            // CRC stored as integer
                            val crcInt = value.asIntegerValue().asLong()
                            crc = crcInt.toString(16).uppercase().padStart(8, '0')
                        }
                        else -> {}
                    }
                }
                "genre" -> {
                    if (value.valueType == ValueType.STRING) {
                        genre = value.asStringValue().asString()
                    }
                }
                "developer" -> {
                    if (value.valueType == ValueType.STRING) {
                        developer = value.asStringValue().asString()
                    }
                }
                "publisher" -> {
                    if (value.valueType == ValueType.STRING) {
                        publisher = value.asStringValue().asString()
                    }
                }
                "releaseyear" -> {
                    if (value.valueType == ValueType.INTEGER) {
                        releaseYear = value.asIntegerValue().asInt()
                    }
                }
                "releasemonth" -> {
                    if (value.valueType == ValueType.INTEGER) {
                        releaseMonth = value.asIntegerValue().asInt()
                    }
                }
                "users" -> {
                    if (value.valueType == ValueType.INTEGER) {
                        maxPlayers = value.asIntegerValue().asInt()
                    }
                }
                "rumble" -> {
                    if (value.valueType == ValueType.BOOLEAN) {
                        hasRumble = value.asBooleanValue().boolean
                    }
                }
                "analog" -> {
                    if (value.valueType == ValueType.BOOLEAN) {
                        hasAnalog = value.asBooleanValue().boolean
                    }
                }
                "enhancement_hw" -> {
                    if (value.valueType == ValueType.STRING) {
                        val hw = value.asStringValue().asString()
                        isHack = hw.contains("hack", ignoreCase = true)
                        isHomebrew = hw.contains("homebrew", ignoreCase = true)
                    }
                }
            }
        }
        
        // Only return if we have at least a name and CRC
        if (name != null && crc != null && crc.isNotEmpty()) {
            return GameInfo(
                name = name,
                crc = crc,
                console = "nes", // Will be set by caller based on which .rdb file
                genre = genre,
                developer = developer,
                publisher = publisher,
                releaseYear = releaseYear,
                releaseMonth = releaseMonth,
                maxPlayers = maxPlayers,
                hasRumble = hasRumble,
                hasAnalog = hasAnalog,
                isHack = isHack,
                isHomebrew = isHomebrew
            )
        }
        
        return null
    }
    
    /**
     * Convert byte array to hex string (for CRC stored as binary).
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}

