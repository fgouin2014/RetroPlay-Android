package com.retroplay.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.retroplay.overlay.models.OverlayPreference
import com.retroplay.overlay.models.OverlayPreferenceManager
import com.retroplay.usecases.LoadOverlayUseCase

/**
 * Manager centralisé pour gérer les overlays RetroArch.
 * Extracted from RetroArchEmulatorActivity to improve modularity.
 * 
 * Gère:
 * - Chargement des overlays
 * - Gestion des préférences d'overlay
 * - Visibilité des overlays
 */
class OverlayManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "OverlayManager"
    }
    
    private val loadOverlayUseCase = LoadOverlayUseCase(context, prefs)
    
    /**
     * Charge un overlay personnalisé depuis un fichier .cfg
     * 
     * @param uri URI du fichier .cfg
     * @param console ID de la console
     * @return Résultat du chargement
     */
    fun loadCustomOverlay(uri: android.net.Uri, console: String): LoadOverlayUseCase.LoadOverlayResult {
        return loadOverlayUseCase.loadCustomOverlay(uri, console)
    }
    
    /**
     * Charge les préférences d'overlay pour une console
     * 
     * @param console ID de la console
     * @return Les préférences d'overlay ou null
     */
    fun loadOverlayPreferences(console: String): OverlayPreference? {
        return loadOverlayUseCase.loadOverlayPreferences(console)
    }
    
    /**
     * Sauvegarde les préférences d'overlay pour une console
     * 
     * @param console ID de la console
     * @param preference Les préférences à sauvegarder
     */
    fun saveOverlayPreferences(console: String, preference: OverlayPreference) {
        OverlayPreferenceManager.save(prefs, console, preference)
        Log.i(TAG, "Saved overlay preferences for console: $console")
    }
    
    /**
     * Charge les paramètres avancés d'overlay (lightgun, etc.)
     * 
     * @param console ID de la console
     * @param orientation Orientation (landscape/portrait)
     * @return Les paramètres avancés
     */
    fun loadAdvancedSettings(console: String, orientation: String): com.retroplay.overlay.models.AdvancedOverlaySettings {
        return OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
    }
    
    /**
     * Sauvegarde les paramètres avancés d'overlay
     * 
     * @param console ID de la console
     * @param orientation Orientation (landscape/portrait)
     * @param settings Les paramètres à sauvegarder
     */
    fun saveAdvancedSettings(
        console: String,
        orientation: String,
        settings: com.retroplay.overlay.models.AdvancedOverlaySettings
    ) {
        OverlayPreferenceManager.saveAdvancedSettings(prefs, console, orientation, settings)
        Log.i(TAG, "Saved advanced overlay settings for console: $console, orientation: $orientation")
    }
}
















