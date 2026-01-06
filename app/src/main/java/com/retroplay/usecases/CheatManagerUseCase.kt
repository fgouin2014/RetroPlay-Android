package com.retroplay.usecases

import android.content.Context
import android.util.Log
import com.retroplay.cheat.CheatApplier
import com.retroplay.cheat.CheatManager
import java.io.File

/**
 * Use Case pour gérer les cheats (chargement et sauvegarde).
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
class CheatManagerUseCase(
    private val context: Context,
    private val cheatApplier: CheatApplier
) {
    private val cheatManager = CheatManager(context)
    
    companion object {
        private const val TAG = "CheatManagerUseCase"
    }
    
    /**
     * Interface pour les callbacks et états
     */
    interface CheatManagerCallbacks {
        fun getLoadedCheats(): MutableList<CheatManager.Cheat>
        fun setCheatFile(file: File?)
        fun runOnUiThread(action: Runnable)
        fun showToast(message: String, duration: Int = android.widget.Toast.LENGTH_SHORT)
    }
    
    private var callbacks: CheatManagerCallbacks? = null
    
    fun setCallbacks(callbacks: CheatManagerCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Charge et applique les codes de triche au démarrage
     */
    fun loadAndApplyCheats(console: String, gameName: String, romPath: String) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "CheatManagerCallbacks not set")
            return
        }
        
        try {
            // Utiliser la méthode helper comme dans le backup
            callbacksRef.getLoadedCheats().clear()
            val cheats = cheatManager.loadCheatsForGame(console, gameName, romPath)
            callbacksRef.getLoadedCheats().addAll(cheats)
            
            if (cheats.isNotEmpty()) {
                val enabledCount = cheats.count { it.enabled }
                if (enabledCount > 0) {
                    Log.i(TAG, "[$console] Loading $enabledCount active cheat(s) for $gameName")
                    cheatApplier.applyCheatsList(cheats)
                    
                    callbacksRef.runOnUiThread {
                        callbacksRef.showToast("[$console] $enabledCount cheat(s) active", android.widget.Toast.LENGTH_SHORT)
                    }
                } else {
                    Log.d(TAG, "[$console] No active cheats for $gameName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cheats", e)
        }
    }
    
    /**
     * Sauvegarde l'état des cheats (activés/désactivés) dans le fichier .cht
     */
    fun saveCheatStates(console: String, gameName: String) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "CheatManagerCallbacks not set")
            return
        }
        
        try {
            val cheats = callbacksRef.getLoadedCheats()
            if (cheats.isEmpty()) {
                return
            }
            
            // Utiliser la méthode helper comme dans le backup
            cheatManager.saveEnabledCheats(console, gameName, cheats)
            Log.i(TAG, "[$console] Saved cheat states for $gameName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cheat states", e)
        }
    }
}
















