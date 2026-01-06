package com.retroplay.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.retroplay.usecases.LoadCoreUseCase
import com.retroplay.helpers.CoreDisplayHelper

/**
 * Manager centralisé pour gérer les cores et la détection de crash.
 * Extracted from RetroArchEmulatorActivity to improve modularity.
 * 
 * Gère:
 * - Chargement des cores
 * - Détection de crash
 * - Gestion des overrides de core
 */
class CoreManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "CoreManager"
    }
    
    private val loadCoreUseCase = LoadCoreUseCase(context)
    
    /**
     * Charge le core approprié pour une console et détecte les crashes
     * 
     * @param console ID de la console
     * @param romPath Chemin de la ROM
     * @param gameName Nom du jeu
     * @return Résultat du chargement avec informations de crash
     */
    fun loadCore(console: String, romPath: String, gameName: String): LoadCoreUseCase.LoadCoreResult {
        return loadCoreUseCase.loadCore(console, romPath, gameName)
    }
    
    /**
     * Obtient le chemin du core pour une console
     * 
     * @param console ID de la console
     * @param romPath Chemin de la ROM
     * @param gameName Nom du jeu
     * @return Chemin du core
     */
    fun getCorePath(console: String, romPath: String, gameName: String): String {
        return loadCoreUseCase.getCorePath(console, romPath, gameName)
    }
    
    /**
     * Nettoie les préférences de crash (appelé après succès)
     */
    fun clearCrashPreferences() {
        loadCoreUseCase.clearCrashPreferences()
        Log.i(TAG, "Cleared crash preferences")
    }
    
    /**
     * Obtient le nom d'affichage d'un core
     * 
     * @param coreFilePath Chemin du fichier core
     * @return Nom d'affichage lisible
     */
    fun getCoreDisplayName(coreFilePath: String): String {
        return CoreDisplayHelper.getCoreDisplayName(coreFilePath)
    }
    
    /**
     * Vérifie si un core est valide (existe)
     * 
     * @param coreFilePath Chemin du fichier core
     * @return true si le core existe
     */
    fun isCoreValid(coreFilePath: String): Boolean {
        // Vérifier si c'est un core embarqué (juste le nom) ou un chemin absolu
        if (!coreFilePath.contains("/")) {
            // Core embarqué, toujours valide
            return true
        }
        
        // Core téléchargé, vérifier l'existence
        val file = java.io.File(coreFilePath)
        return file.exists()
    }
}
















