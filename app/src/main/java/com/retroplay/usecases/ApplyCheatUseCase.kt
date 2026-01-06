package com.retroplay.usecases

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.retroplay.cheat.CheatApplier
import com.retroplay.cheat.CheatManager
import com.swordfish.libretrodroid.GLRetroView

/**
 * Use Case pour l'application des cheats.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement des cheats depuis les fichiers .cht
 * - Application des cheats activés au core
 * - Sauvegarde de l'état des cheats (enabled/disabled)
 */
class ApplyCheatUseCase(
    private val context: Context,
    private val cheatApplier: CheatApplier
) {
    
    companion object {
        private const val TAG = "ApplyCheatUseCase"
    }
    
    /**
     * Résultat du chargement et de l'application des cheats
     */
    data class ApplyCheatResult(
        val success: Boolean,
        val loadedCount: Int = 0,
        val enabledCount: Int = 0,
        val errorMessage: String? = null
    )
    
    /**
     * Charge et applique les cheats pour un jeu
     * 
     * @param console ID de la console
     * @param gameName Nom du jeu
     * @param romPath Chemin vers la ROM
     * @return Résultat du chargement et de l'application, avec la liste des cheats chargés
     */
    fun loadAndApplyCheats(console: String, gameName: String, romPath: String): Pair<ApplyCheatResult, List<CheatManager.Cheat>> {
        return try {
            val cheatManager = CheatManager(context)
            val loadedCheats = cheatManager.loadCheatsForGame(console, gameName, romPath).toMutableList()
            
            if (loadedCheats.isNotEmpty()) {
                val enabledCount = loadedCheats.count { it.enabled }
                if (enabledCount > 0) {
                    Log.i(TAG, "[$console] Loading $enabledCount active cheat(s) for $gameName")
                    cheatApplier.applyCheatsList(loadedCheats)
                    
                    // Afficher un toast sur le thread UI
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "[$console] $enabledCount cheat(s) active",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    Pair(
                        ApplyCheatResult(
                            success = true,
                            loadedCount = loadedCheats.size,
                            enabledCount = enabledCount
                        ),
                        loadedCheats
                    )
                } else {
                    Log.d(TAG, "[$console] No active cheats for $gameName")
                    Pair(
                        ApplyCheatResult(
                            success = true,
                            loadedCount = loadedCheats.size,
                            enabledCount = 0
                        ),
                        loadedCheats
                    )
                }
            } else {
                Pair(ApplyCheatResult(success = true, loadedCount = 0, enabledCount = 0), emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cheats", e)
            Pair(
                ApplyCheatResult(
                    success = false,
                    errorMessage = e.message ?: "Unknown error"
                ),
                emptyList()
            )
        }
    }
    
    /**
     * Sauvegarde l'état des cheats (enabled/disabled) dans le fichier .cht
     * 
     * @param console ID de la console
     * @param gameName Nom du jeu
     * @param loadedCheats Liste des cheats avec leur état (enabled/disabled)
     */
    fun saveCheatStates(console: String, gameName: String, loadedCheats: List<CheatManager.Cheat>) {
        try {
            val cheatManager = CheatManager(context)
            cheatManager.saveEnabledCheats(console, gameName, loadedCheats)
            Log.i(TAG, "[$console] Saved cheat states for $gameName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cheat states", e)
        }
    }
}

