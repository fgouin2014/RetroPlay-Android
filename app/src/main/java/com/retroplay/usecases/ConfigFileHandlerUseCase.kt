package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.retroplay.helpers.UriFileHelper

/**
 * Use Case pour gérer la sélection et le chargement de fichiers de configuration personnalisés (overlays .cfg).
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * IMPORTANT: Cette fonction gère les overlays .cfg, pas les configs de core .cfg
 */
class ConfigFileHandlerUseCase(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val console: String
) {
    
    companion object {
        private const val TAG = "ConfigFileHandlerUseCase"
    }
    
    /**
     * Interface pour les callbacks
     */
    interface ConfigFileCallbacks {
        fun showToast(message: String, duration: Int = android.widget.Toast.LENGTH_SHORT)
    }
    
    private var callbacks: ConfigFileCallbacks? = null
    
    fun setCallbacks(callbacks: ConfigFileCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Gère la sélection d'un fichier overlay .cfg personnalisé
     * Identique à la fonction originale dans le backup (ligne 201-261)
     */
    fun handleCustomCfgSelection(uri: Uri) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ConfigFileCallbacks not set")
            return
        }
        
        try {
            Log.i(TAG, "Overlay .cfg selected: $uri")
            
            // Obtenir le nom du fichier via ContentResolver
            val fileName = UriFileHelper.getFileNameFromUri(context, uri) ?: run {
                callbacksRef.showToast("Cannot get file name", Toast.LENGTH_SHORT)
                return
            }
            
            Log.i(TAG, "File name: $fileName")
            
            // Vérifier que c'est bien un .cfg
            if (!fileName.endsWith(".cfg", ignoreCase = true)) {
                callbacksRef.showToast("Please select a .cfg file", Toast.LENGTH_SHORT)
                return
            }
            
            // Lire le contenu du fichier pour détecter le type d'overlay
            val cfgContent = UriFileHelper.readFileFromUri(context, uri) ?: run {
                callbacksRef.showToast("Cannot read file content", Toast.LENGTH_SHORT)
                return
            }
            
            // Extraire le nom du dossier parent depuis l'URI
            // Ex: primary:RetroPlay-Data/overlays/gamepads/flat/dreamcast.cfg → "flat"
            val overlayName = UriFileHelper.extractOverlayFolderFromUri(uri) ?: 
                UriFileHelper.detectOverlayNameFromContent(cfgContent, fileName)
            
            if (overlayName.isEmpty()) {
                callbacksRef.showToast("Cannot detect overlay type from .cfg", Toast.LENGTH_SHORT)
                return
            }
            
            Log.i(TAG, "Detected overlay name: $overlayName")
            
            // Créer le path custom (overlayName/fileName)
            val customPath = "$overlayName/$fileName"
            
            // Sauvegarder dans la liste des customs browsés
            com.retroplay.overlay.models.OverlayPreferenceManager.saveCustomBrowsed(prefs, console, customPath)
            
            // Sauvegarder aussi comme preference active avec le nom du .cfg custom!
            val pref = com.retroplay.overlay.models.OverlayPreference(
                enabled = true,
                overlayName = overlayName,
                customCfgName = fileName,  // "dreamcast.cfg" pour custom, null pour standard
                landscapeLayout = "landscape-A",
                portraitLayout = "portrait-A",
                autoRotate = true
            )
            com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, pref)
            
            Log.i(TAG, "Saved custom overlay: $customPath for console: $console")
            callbacksRef.showToast("Custom overlay '$overlayName' loaded!", Toast.LENGTH_LONG)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading overlay .cfg", e)
            callbacksRef.showToast("Error: ${e.message}", Toast.LENGTH_SHORT)
        }
    }
}
















