package com.retroplay

import android.content.Context
import android.util.Log
import java.io.File

/**
 * CoreConfigManager - Gestionnaire de configuration des cores Libretro
 * 
 * Lit et écrit les fichiers .cfg de configuration des cores (format RetroArch).
 * Emplacement: /storage/emulated/0/RetroPlay-Data/config/{CoreName}/{CoreName}.cfg
 * 
 * Format .cfg:
 * option_name = "value"
 * # Commentaires avec #
 */
object CoreConfigManager {
    private const val TAG = "CoreConfigManager"
    
    /**
     * Obtient le chemin du répertoire de configuration d'un core
     */
    private fun getCoreConfigDir(context: Context, coreName: String): File {
        val baseDir = File("/storage/emulated/0/RetroPlay-Data/config")
        return File(baseDir, coreName)
    }
    
    /**
     * Obtient le chemin du fichier de configuration d'un core
     */
    private fun getCoreConfigFile(context: Context, coreName: String): File {
        val configDir = getCoreConfigDir(context, coreName)
        return File(configDir, "$coreName.cfg")
    }
    
    /**
     * Lit un fichier .cfg et retourne un Map des options
     */
    fun loadConfig(context: Context, coreName: String): Map<String, String> {
        val configFile = getCoreConfigFile(context, coreName)
        
        if (!configFile.exists()) {
            Log.i(TAG, "[$coreName] Config file not found: ${configFile.absolutePath}")
            return emptyMap()
        }
        
        val config = mutableMapOf<String, String>()
        
        try {
            configFile.readLines().forEach { line ->
                val trimmed = line.trim()
                
                // Ignorer les commentaires et lignes vides
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    return@forEach
                }
                
                // Parser: option_name = "value"
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"")
                    config[key] = value
                }
            }
            
            Log.i(TAG, "[$coreName] Loaded ${config.size} options from ${configFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "[$coreName] Error loading config: ${e.message}", e)
        }
        
        return config
    }
    
    /**
     * Sauvegarde un Map d'options dans un fichier .cfg
     */
    fun saveConfig(context: Context, coreName: String, config: Map<String, String>) {
        val configFile = getCoreConfigFile(context, coreName)
        
        try {
            // Créer le répertoire si nécessaire
            configFile.parentFile?.mkdirs()
            
            // Écrire le fichier
            configFile.writeText(buildString {
                appendLine("# $coreName Configuration")
                appendLine("# Format: option_name = \"value\"")
                appendLine()
                
                config.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("$key = \"$value\"")
                }
            })
            
            Log.i(TAG, "[$coreName] Saved ${config.size} options to ${configFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "[$coreName] Error saving config: ${e.message}", e)
        }
    }
    
    /**
     * Obtient la valeur d'une option (avec valeur par défaut si inexistante)
     */
    fun getOption(context: Context, coreName: String, optionName: String, defaultValue: String): String {
        val config = loadConfig(context, coreName)
        return config[optionName] ?: defaultValue
    }
    
    /**
     * Définit la valeur d'une option
     */
    fun setOption(context: Context, coreName: String, optionName: String, value: String) {
        val config = loadConfig(context, coreName).toMutableMap()
        config[optionName] = value
        saveConfig(context, coreName, config)
    }
    
    /**
     * Crée un fichier .cfg par défaut s'il n'existe pas
     */
    fun createDefaultConfigIfNeeded(context: Context, coreName: String, defaultConfig: Map<String, String>) {
        val configFile = getCoreConfigFile(context, coreName)
        
        if (!configFile.exists()) {
            Log.i(TAG, "[$coreName] Creating default config...")
            saveConfig(context, coreName, defaultConfig)
        }
    }
    
    /**
     * Obtient les configurations par défaut pour chaque core
     */
    fun getDefaultConfig(coreName: String): Map<String, String> {
        return when (coreName.lowercase()) {
            "fceumm" -> mapOf(
                "fceumm_zapper_mode" to "touchscreen",
                "fceumm_zapper_trigger" to "enabled",
                "fceumm_zapper_sensor" to "enabled",
                "fceumm_show_crosshair" to "enabled",
                "fceumm_zapper_tolerance" to "6"
            )
            
            "snes9x" -> mapOf(
                "snes9x_hires_blend" to "disabled",
                "snes9x_layer_1" to "enabled",
                "snes9x_layer_2" to "enabled",
                "snes9x_layer_3" to "enabled",
                "snes9x_layer_4" to "enabled"
            )
            
            "parallel_n64" -> mapOf(
                "parallel-n64-gfxplugin" to "gliden64",
                "parallel-n64-screensize" to "640x480"
            )
            
            "mupen64plus_next" -> mapOf(
                "mupen64plus-rdp-plugin" to "gliden64"
            )
            
            else -> emptyMap()
        }
    }
}

