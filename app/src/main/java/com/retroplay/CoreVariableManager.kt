package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.swordfish.libretrodroid.Variable
import org.json.JSONObject

/**
 * Gère le parsing, la sauvegarde et le chargement des variables de core LibretroDroid.
 * Sépare les DIP switches (hardware arcade) des Core Options (paramètres émulateur).
 */
object CoreVariableManager {
    private const val TAG = "CoreVariableManager"
    private const val PREFS_NAME = "core_variables"
    private const val PREFIX_CORE_VARS = "core_variables_"
    
    /**
     * Parse les Variable LibretroDroid en CoreVariable utilisables.
     * Format attendu: description = "DisplayName; value1|value2|value3"
     */
    fun parseVariables(variables: Array<Variable>): List<CoreVariable> {
        Log.i(TAG, "Parsing ${variables.size} variables from core")
        
        val parsed = variables.mapNotNull { v ->
            try {
                val key = v.key ?: return@mapNotNull null
                val currentValue = v.value ?: ""
                val description = v.description ?: return@mapNotNull null
                
                // Format: "DisplayName; value1|value2|value3"
                val parts = description.split(";")
                if (parts.isEmpty()) return@mapNotNull null
                
                val displayName = parts[0].trim()
                val possibleValues = if (parts.size > 1) {
                    parts[1].trim().split("|").map { it.trim() }
                } else {
                    emptyList()
                }
                
                // Détecter si c'est un DIP switch (clé contient "-dip-" ou "dipswitch-")
                // Exemples: "mame2003_plus-dip-Lives", "fbneo-dipswitch-Difficulty"
                // Mais PAS "mame2003_plus-dipswitch_input" (c'est une option de core)
                val isDipSwitch = key.contains("-dip-", ignoreCase = true) || 
                                 key.contains("-dipswitch-", ignoreCase = true)
                
                CoreVariable(
                    key = key,
                    displayName = displayName,
                    currentValue = currentValue,
                    possibleValues = possibleValues,
                    isDipSwitch = isDipSwitch
                ).also {
                    Log.d(TAG, "Parsed ${if (isDipSwitch) "DIP" else "OPT"}: [$key] $displayName = $currentValue")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse variable: ${v.key}", e)
                null
            }
        }
        
        // Dédupliquer les variables (certains cores MAME retournent les mêmes variables 2x)
        val deduplicated = parsed.distinctBy { it.key }
        if (deduplicated.size < parsed.size) {
            Log.i(TAG, "Removed ${parsed.size - deduplicated.size} duplicate variables")
        }
        
        return deduplicated
    }
    
    /**
     * Filtre les variables pour ne garder que celles du core spécifié
     */
    fun filterVariablesForCore(variables: List<CoreVariable>, coreId: String): List<CoreVariable> {
        val filtered = variables.filter { variable ->
            val key = variable.key.lowercase()
            val corePrefix = coreId.lowercase()
            
            // Gérer les deux formats possibles : underscore et tiret
            // Ex: fichier "mame2003_plus_libretro.so" -> clés "mame2003-plus-xxx"
            val corePrefixWithDash = corePrefix.replace("_", "-")
            
            // Créer les patterns exacts à matcher (avec séparateurs)
            val patterns = listOf(
                corePrefix + "-",           // Ex: "mame2003-dip-xxx"
                corePrefix + "_",           // Ex: "mame2003_xxx"
                corePrefixWithDash + "-",   // Ex: "mame2003-plus-dip-xxx"
                corePrefixWithDash + "_"    // Ex: "mame2003-plus_xxx"
            )
            
            // La clé doit commencer par un des patterns exacts
            val matches = patterns.any { pattern -> key.startsWith(pattern) }
            
            // Exclusion spéciale : si on cherche "mame2003", exclure "mame2003-plus" et "mame2003_plus"
            if (matches && corePrefix == "mame2003") {
                !key.contains("mame2003-plus") && !key.contains("mame2003_plus")
            } else {
                matches
            }
        }
        
        Log.i(TAG, "Filtered ${variables.size} variables to ${filtered.size} for core: $coreId")
        return filtered
    }
    
    /**
     * Filtre pour ne garder que les DIP switches
     */
    fun getDipSwitches(variables: List<CoreVariable>): List<CoreVariable> {
        return variables.filter { it.isDipSwitch }
    }
    
    /**
     * Filtre pour ne garder que les Core Options
     */
    fun getCoreOptions(variables: List<CoreVariable>): List<CoreVariable> {
        return variables.filter { !it.isDipSwitch }
    }
    
    /**
     * Sauvegarde une variable pour un jeu spécifique ET un core spécifique
     * @param coreId Identifiant du core (ex: "mame2010", "fbneo", "snes9x")
     */
    fun saveVariable(context: Context, gameId: String, coreId: String, key: String, value: String) {
        val prefs = getPreferences(context)
        val prefKey = "${PREFIX_CORE_VARS}${gameId}_${coreId}"
        
        // Charger le JSON existant
        val jsonString = prefs.getString(prefKey, "{}")
        val json = JSONObject(jsonString ?: "{}")
        
        // Ajouter/modifier la variable
        json.put(key, value)
        
        // Sauvegarder
        prefs.edit()
            .putString(prefKey, json.toString())
            .apply()
        
        Log.i(TAG, "Saved variable for game $gameId (core: $coreId): $key = $value")
    }
    
    /**
     * Sauvegarde plusieurs variables en une seule fois pour un jeu ET un core spécifiques
     * @param coreId Identifiant du core (ex: "mame2010", "fbneo", "snes9x")
     */
    fun saveVariables(context: Context, gameId: String, coreId: String, variables: Map<String, String>) {
        val prefs = getPreferences(context)
        val prefKey = "${PREFIX_CORE_VARS}${gameId}_${coreId}"
        
        // Charger le JSON existant
        val jsonString = prefs.getString(prefKey, "{}")
        val json = JSONObject(jsonString ?: "{}")
        
        // Ajouter/modifier toutes les variables
        variables.forEach { (key, value) ->
            json.put(key, value)
        }
        
        // Sauvegarder
        prefs.edit()
            .putString(prefKey, json.toString())
            .apply()
        
        Log.i(TAG, "Saved ${variables.size} variables for game $gameId (core: $coreId)")
    }
    
    /**
     * Charge les variables sauvegardées pour un jeu ET un core spécifiques
     * @param coreId Identifiant du core (ex: "mame2010", "fbneo", "snes9x")
     */
    fun loadVariables(context: Context, gameId: String, coreId: String): Map<String, String> {
        val prefs = getPreferences(context)
        val prefKey = "${PREFIX_CORE_VARS}${gameId}_${coreId}"
        
        val jsonString = prefs.getString(prefKey, "{}")
        val json = JSONObject(jsonString ?: "{}")
        
        val result = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            result[key] = json.getString(key)
        }
        
        Log.i(TAG, "Loaded ${result.size} variables for game $gameId (core: $coreId)")
        return result
    }
    
    /**
     * Extrait l'identifiant du core depuis le nom de fichier
     * Ex: "mame2010_libretro_android.so" -> "mame2010"
     */
    fun extractCoreId(coreFilePath: String): String {
        val fileName = coreFilePath.substringAfterLast("/")
        return fileName.replace("_libretro_android.so", "")
                      .replace(".so", "")
    }
    
    /**
     * Applique les valeurs sauvegardées aux CoreVariable
     */
    fun applyLoadedValues(
        variables: List<CoreVariable>,
        loadedValues: Map<String, String>
    ): List<CoreVariable> {
        return variables.map { variable ->
            val savedValue = loadedValues[variable.key]
            if (savedValue != null && variable.possibleValues.contains(savedValue)) {
                variable.copy(currentValue = savedValue)
            } else {
                variable
            }
        }
    }
    
    /**
     * Convertit une liste de CoreVariable en Array<Variable> pour LibretroDroid
     */
    fun toLibretroVariables(variables: List<CoreVariable>): Array<Variable> {
        return variables.map { Variable(it.key, it.currentValue) }.toTypedArray()
    }
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

