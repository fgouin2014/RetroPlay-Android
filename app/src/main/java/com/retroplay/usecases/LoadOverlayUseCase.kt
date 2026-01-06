package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.retroplay.helpers.UriFileHelper
import com.retroplay.overlay.models.OverlayPreference
import com.retroplay.overlay.models.OverlayPreferenceManager

/**
 * Use Case pour le chargement des overlays.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement des overlays personnalisés depuis un fichier .cfg
 * - Sauvegarde des préférences d'overlay
 */
class LoadOverlayUseCase(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "LoadOverlayUseCase"
    }
    
    /**
     * Résultat du chargement d'overlay
     */
    data class LoadOverlayResult(
        val success: Boolean,
        val overlayName: String? = null,
        val customPath: String? = null,
        val errorMessage: String? = null
    )
    
    /**
     * Charge un overlay personnalisé depuis un fichier .cfg sélectionné
     * 
     * @param uri URI du fichier .cfg sélectionné
     * @param console ID de la console (pour sauvegarder la préférence)
     * @return Résultat du chargement
     */
    fun loadCustomOverlay(uri: Uri, console: String): LoadOverlayResult {
        return try {
            Log.i(TAG, "Overlay .cfg selected: $uri")
            
            // Obtenir le nom du fichier via ContentResolver
            val fileName = UriFileHelper.getFileNameFromUri(context, uri) ?: run {
                val error = "Cannot get file name"
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                return LoadOverlayResult(success = false, errorMessage = error)
            }
            
            Log.i(TAG, "File name: $fileName")
            
            // Vérifier que c'est bien un .cfg
            if (!fileName.endsWith(".cfg", ignoreCase = true)) {
                val error = "Please select a .cfg file"
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                return LoadOverlayResult(success = false, errorMessage = error)
            }
            
            // Lire le contenu du fichier pour détecter le type d'overlay
            val cfgContent = UriFileHelper.readFileFromUri(context, uri) ?: run {
                val error = "Cannot read file content"
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                return LoadOverlayResult(success = false, errorMessage = error)
            }
            
            // Extraire le nom du dossier parent depuis l'URI
            // Ex: primary:RetroPlay-Data/overlays/gamepads/flat/dreamcast.cfg → "flat"
            val overlayName = UriFileHelper.extractOverlayFolderFromUri(uri) ?: 
                UriFileHelper.detectOverlayNameFromContent(cfgContent, fileName)
            
            if (overlayName.isEmpty()) {
                val error = "Cannot detect overlay type from .cfg"
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                return LoadOverlayResult(success = false, errorMessage = error)
            }
            
            Log.i(TAG, "Detected overlay name: $overlayName")
            
            // Créer le path custom (overlayName/fileName)
            val customPath = "$overlayName/$fileName"
            
            // Sauvegarder dans la liste des customs browsés
            OverlayPreferenceManager.saveCustomBrowsed(prefs, console, customPath)
            
            // Sauvegarder aussi comme preference active avec le nom du .cfg custom!
            val pref = OverlayPreference(
                enabled = true,
                overlayName = overlayName,
                customCfgName = fileName,  // "dreamcast.cfg" pour custom, null pour standard
                landscapeLayout = "landscape-A",
                portraitLayout = "portrait-A",
                autoRotate = true
            )
            OverlayPreferenceManager.save(prefs, console, pref)
            
            Log.i(TAG, "Saved custom overlay: $customPath for console: $console")
            Toast.makeText(context, "Custom overlay '$overlayName' loaded!", Toast.LENGTH_LONG).show()
            
            LoadOverlayResult(
                success = true,
                overlayName = overlayName,
                customPath = customPath
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading overlay .cfg", e)
            val errorMessage = e.message ?: "Unknown error"
            Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
            LoadOverlayResult(success = false, errorMessage = errorMessage)
        }
    }
    
    /**
     * Charge les préférences d'overlay pour une console
     * 
     * @param console ID de la console
     * @return Les préférences d'overlay ou null si non configuré
     */
    fun loadOverlayPreferences(console: String): OverlayPreference? {
        return OverlayPreferenceManager.load(prefs, console)
    }
}

