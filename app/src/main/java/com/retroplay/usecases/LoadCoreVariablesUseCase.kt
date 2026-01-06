package com.retroplay.usecases

import android.content.Context
import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import com.retroplay.CoreVariableManager
import com.retroplay.CoreVariable

/**
 * Use Case pour charger les variables de core (DIP switches et Core Options).
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
class LoadCoreVariablesUseCase(
    private val context: Context,
    private val retroView: GLRetroView
) {
    
    companion object {
        private const val TAG = "LoadCoreVariablesUseCase"
    }
    
    /**
     * Interface pour les callbacks et états
     */
    interface CoreVariablesCallbacks {
        fun getCurrentCoreFilePath(): String?
        fun getGameId(): String
        fun getAllCoreVariables(): MutableList<CoreVariable>
        fun getDipSwitches(): MutableList<CoreVariable>
        fun getCoreOptions(): MutableList<CoreVariable>
    }
    
    private var callbacks: CoreVariablesCallbacks? = null
    
    fun setCallbacks(callbacks: CoreVariablesCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Charge les variables de core (DIP switches et Core Options) et applique les valeurs sauvegardées
     */
    fun loadCoreVariables(romPath: String) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "CoreVariablesCallbacks not set")
            return
        }
        
        try {
            // Extraire le coreId AVANT de récupérer les variables pour vérifier la cohérence
            val expectedCoreId = callbacksRef.getCurrentCoreFilePath()?.let { 
                CoreVariableManager.extractCoreId(it) 
            } ?: "unknown"
            Log.i(TAG, "Loading variables for core: $expectedCoreId")
            
            // Récupérer les variables depuis le core LibretroDroid
            val variables = retroView.getVariables()
            Log.i(TAG, "Retrieved ${variables.size} variables from core")
            
            // Vérifier que les variables correspondent au core attendu
            if (variables.isNotEmpty()) {
                val firstKey = variables.firstOrNull()?.key ?: ""
                Log.i(TAG, "First variable key: $firstKey (expected prefix: $expectedCoreId)")
            }
            
            // Parser les variables
            val parsed = CoreVariableManager.parseVariables(variables)
            
            // Filtrer pour ne garder que les variables du core actuel
            val filtered = CoreVariableManager.filterVariablesForCore(parsed, expectedCoreId)
            
            callbacksRef.getAllCoreVariables().clear()
            callbacksRef.getAllCoreVariables().addAll(filtered)
            
            // Séparer DIP switches et Core Options
            callbacksRef.getDipSwitches().clear()
            callbacksRef.getDipSwitches().addAll(CoreVariableManager.getDipSwitches(filtered))
            callbacksRef.getCoreOptions().clear()
            callbacksRef.getCoreOptions().addAll(CoreVariableManager.getCoreOptions(filtered))
            
            Log.i(TAG, "Parsed: ${callbacksRef.getDipSwitches().size} DIP switches, ${callbacksRef.getCoreOptions().size} core options")
            
            // Extraire le coreId depuis le chemin du fichier
            val coreId = callbacksRef.getCurrentCoreFilePath()?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
            val gameIdFromPath = java.io.File(romPath).nameWithoutExtension
            
            // Charger les valeurs sauvegardées : d'abord en mode Per-Game, puis en mode Global si rien n'est trouvé
            var savedValues = CoreVariableManager.loadVariables(context, gameIdFromPath, coreId)
            if (savedValues.isEmpty()) {
                // Si aucune valeur per-game n'est trouvée, essayer le mode Global
                savedValues = CoreVariableManager.loadVariables(context, "global", coreId)
                if (savedValues.isNotEmpty()) {
                    Log.i(TAG, "No per-game values found, using global values")
                }
            } else {
                Log.i(TAG, "Using per-game values for $gameIdFromPath")
            }
            
            // Variables à utiliser (soit depuis savedValues, soit depuis filtered)
            val updatedVariables = if (savedValues.isNotEmpty()) {
                // Appliquer les valeurs sauvegardées
                val applied = CoreVariableManager.applyLoadedValues(filtered, savedValues)
                Log.i(TAG, "Applied ${savedValues.size} saved values to ${applied.size} variables")
                // Vérifier que les valeurs ont été appliquées
                applied.forEach { variable ->
                    if (savedValues.containsKey(variable.key)) {
                        Log.d(TAG, "Variable ${variable.key}: default=${filtered.find { it.key == variable.key }?.currentValue}, saved=${savedValues[variable.key]}, final=${variable.currentValue}")
                    }
                }
                applied.toMutableList()
            } else {
                // Utiliser les valeurs par défaut
                Log.i(TAG, "No saved values found, using default values for all ${filtered.size} variables")
                filtered.toMutableList()
            }
            
            // Mettre à jour les listes
            callbacksRef.getAllCoreVariables().clear()
            callbacksRef.getAllCoreVariables().addAll(updatedVariables)
            callbacksRef.getDipSwitches().clear()
            callbacksRef.getDipSwitches().addAll(CoreVariableManager.getDipSwitches(updatedVariables))
            callbacksRef.getCoreOptions().clear()
            callbacksRef.getCoreOptions().addAll(CoreVariableManager.getCoreOptions(updatedVariables))
            
            Log.i(TAG, "Updated coreOptions list with ${callbacksRef.getCoreOptions().size} options")
            callbacksRef.getCoreOptions().forEach { opt ->
                Log.d(TAG, "CoreOption ${opt.key}: currentValue=${opt.currentValue}")
            }
            
            // Appliquer au core LibretroDroid (toujours, même sans savedValues)
            val libretroVars = CoreVariableManager.toLibretroVariables(updatedVariables)
            retroView.updateVariables(*libretroVars)
            if (savedValues.isNotEmpty()) {
                Log.i(TAG, "Applied ${savedValues.size} saved variables to core runtime")
            } else {
                Log.i(TAG, "Applied default variables to core runtime")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading core variables", e)
        }
    }
}















