package com.retroplay.helpers

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream

/**
 * Helper class for URI file operations.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
object UriFileHelper {
    
    private const val TAG = "UriFileHelper"
    
    /**
     * Obtenir le nom du fichier depuis un content:// URI
     * 
     * @param context Le contexte Android
     * @param uri L'URI du fichier
     * @return Le nom du fichier ou null si introuvable
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        
        // Méthode 1: Query via ContentResolver
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        // Méthode 2: Fallback - Extraire depuis l'URI
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }
        
        return fileName
    }
    
    /**
     * Lire le contenu d'un fichier depuis son URI
     * 
     * @param context Le contexte Android
     * @param uri L'URI du fichier
     * @return Le contenu du fichier en String ou null en cas d'erreur
     */
    fun readFileFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file from URI", e)
            null
        }
    }
    
    /**
     * Extraire le nom du dossier overlay depuis l'URI
     * Ex: content://.../primary:RetroPlay-Data/overlays/gamepads/flat/nes.cfg → "flat"
     * 
     * @param uri L'URI du fichier overlay
     * @return Le nom du dossier overlay ou null si introuvable
     */
    fun extractOverlayFolderFromUri(uri: Uri): String? {
        return try {
            // DECODE %2F → / pour gérer les URIs encodés!
            val uriString = java.net.URLDecoder.decode(uri.toString(), "UTF-8")
            Log.d(TAG, "Extracting overlay folder from URI: $uriString")
            
            // Chercher pattern /overlays/gamepads/XXX/ ou /overlays/keyboards/XXX/
            val gamepadPattern = Regex("""/overlays/gamepads/([^/]+)/""")
            val keyboardPattern = Regex("""/overlays/keyboards/([^/]+)/""")
            
            val gamepadMatch = gamepadPattern.find(uriString)
            if (gamepadMatch != null) {
                val folder = gamepadMatch.groupValues[1]
                Log.i(TAG, "Extracted overlay folder from URI (gamepads): $folder")
                return folder
            }
            
            val keyboardMatch = keyboardPattern.find(uriString)
            if (keyboardMatch != null) {
                val folder = keyboardMatch.groupValues[1]
                Log.i(TAG, "Extracted overlay folder from URI (keyboards): $folder")
                return folder
            }
            
            Log.w(TAG, "No overlay folder pattern found in URI")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting overlay folder from URI", e)
            null
        }
    }
    
    /**
     * Détecter le nom de l'overlay depuis le contenu du .cfg
     * IMPORTANT: Le overlay name est le NOM DU DOSSIER, pas le nom du fichier!
     * Ex: flat/dreamcast.cfg → overlayName = "flat" (pas "dreamcast")
     * 
     * @param cfgContent Le contenu du fichier .cfg
     * @param fileName Le nom du fichier .cfg
     * @return Le nom de l'overlay détecté
     */
    fun detectOverlayNameFromContent(cfgContent: String, fileName: String): String {
        val baseFileName = fileName.substringBeforeLast(".cfg")
        
        // Méthode 1: Parser le contenu pour trouver le path des images
        val imgPathPattern = Regex("""overlay\d+_desc\d+_overlay\s*=\s*["']?([^"'\r\n]+)""")
        val imgMatch = imgPathPattern.find(cfgContent)
        
        if (imgMatch != null) {
            val imgPath = imgMatch.groupValues[1].trim()
            Log.d(TAG, "Found image path in .cfg: $imgPath")
            
            // Cas 1: Path commence par "../" (remonte d'un dossier)
            if (imgPath.startsWith("../")) {
                val overlayName = imgPath.removePrefix("../").substringBefore("/")
                Log.i(TAG, "Detected overlay from ../ path: $overlayName")
                return overlayName
            }
            
            // Cas 2: Path contient un dossier parent (mais pas img/)
            if (imgPath.contains("/") && !imgPath.startsWith("img/")) {
                val overlayName = imgPath.substringBefore("/")
                Log.i(TAG, "Detected overlay from image path: $overlayName")
                return overlayName
            }
            
            // Cas 3: Path direct = img/A.png
            if (imgPath.startsWith("img/")) {
                Log.i(TAG, "Direct img/ path, using fileName as overlay: $baseFileName")
                return baseFileName
            }
        }
        
        // Méthode 2: Chercher la ligne overlay_name (si elle existe)
        val overlayNamePattern = Regex("""overlay\d+_name\s*=\s*["']([^"']+)["']""")
        val nameMatch = overlayNamePattern.find(cfgContent)
        if (nameMatch != null) {
            Log.d(TAG, "Found overlay0_name in .cfg, using fileName as overlay: $baseFileName")
            return baseFileName
        }
        
        // Méthode 3: Liste des overlays "dossiers" connus
        val knownFolderOverlays = listOf(
            "flat", "dual-shock", "arcade-anim", "lite", "neo-retropad",
            "nes", "nes-small", "snes", "psx", "gba", "n64", "genesis",
            "arcade", "gameboy", "quadpad", "scummvm", "retropad",
            "720-med", "flip_phone", "gb_anim_portrait", "gba-grey"
        )
        
        if (knownFolderOverlays.any { it.equals(baseFileName, ignoreCase = true) }) {
            Log.i(TAG, "FileName matches known folder overlay: $baseFileName")
            return baseFileName
        }
        
        // Fallback: utiliser le nom du fichier sans extension
        return baseFileName
    }
}

