package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.retroplay.CoreOverrideManager
import com.retroplay.CoreDownloader
import com.retroplay.helpers.CoreDisplayHelper

/**
 * Use Case pour le chargement et la gestion des cores.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
class LoadCoreUseCase(private val context: Context) {
    
    companion object {
        private const val TAG = "LoadCoreUseCase"
        private const val CRASH_PREFS = "core_crash_detection"
        private const val KEY_LAST_GAME = "last_game_path"
        private const val KEY_LAST_CORE = "last_core_attempted"
        private const val KEY_TIMESTAMP = "crash_timestamp"
        private const val CRASH_TIMEOUT_MS = 2000L // 2 secondes pour considérer un crash
    }
    
    /**
     * Résultat du chargement de core
     */
    data class LoadCoreResult(
        val coreFilePath: String,
        val wasCrash: Boolean,
        val failedCoreName: String? = null,
        val shouldShowErrorDialog: Boolean = false
    )
    
    /**
     * Charge le core approprié pour une console et détecte les crashes précédents
     * 
     * @param console L'ID de la console
     * @param romPath Le chemin de la ROM
     * @param gameName Le nom du jeu
     * @return Le résultat du chargement avec informations de crash
     */
    fun loadCore(console: String, romPath: String, gameName: String): LoadCoreResult {
        val crashPrefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        val lastGamePath = crashPrefs.getString(KEY_LAST_GAME, null)
        val lastCoreAttempted = crashPrefs.getString(KEY_LAST_CORE, null)
        val lastTimestamp = crashPrefs.getLong(KEY_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        
        // Obtenir le chemin du core
        val selectedCore = getCorePath(console, romPath, gameName)
        
        // Vérifier si le dernier lancement a crashé (même jeu, moins de 2s)
        val wasCrash = lastGamePath == romPath && 
                       lastCoreAttempted != null && 
                       (currentTime - lastTimestamp) < CRASH_TIMEOUT_MS
        
        var failedCoreName: String? = null
        var shouldShowErrorDialog = false
        
        // N'afficher le dialog QUE si le core est IDENTIQUE au core qui a crashé
        if (wasCrash && lastCoreAttempted == selectedCore) {
            Log.w(TAG, "⚠️ CRASH DETECTED on previous attempt with core: $lastCoreAttempted")
            failedCoreName = CoreDisplayHelper.getCoreDisplayName(lastCoreAttempted)
            shouldShowErrorDialog = true
            // Nettoyer les prefs pour ne pas re-afficher le dialog
            crashPrefs.edit().clear().apply()
        } else if (wasCrash && lastCoreAttempted != selectedCore) {
            // L'utilisateur a déjà changé le core manuellement, juste nettoyer les prefs
            Log.i(TAG, "✓ User manually changed core from $lastCoreAttempted to $selectedCore")
            crashPrefs.edit().clear().apply()
        }
        
        // Sauvegarder la tentative actuelle pour détecter un crash futur
        crashPrefs.edit().apply {
            putString(KEY_LAST_GAME, romPath)
            putString(KEY_LAST_CORE, selectedCore)
            putLong(KEY_TIMESTAMP, currentTime)
            apply()
        }
        Log.i(TAG, "📝 Core logged: $selectedCore for $gameName")
        
        return LoadCoreResult(
            coreFilePath = selectedCore,
            wasCrash = wasCrash,
            failedCoreName = failedCoreName,
            shouldShowErrorDialog = shouldShowErrorDialog
        )
    }
    
    /**
     * Obtient le chemin du core pour une console donnée
     * 
     * @param console L'ID de la console
     * @param romPath Le chemin de la ROM (pour vérifier les overrides)
     * @param gameName Le nom du jeu (pour logging)
     * @return Le chemin complet du fichier core (.so)
     */
    fun getCorePath(console: String, romPath: String, gameName: String): String {
        // Construire le chemin relatif du jeu (ex: "fbneo/sega/afighter.zip")
        var relativePath = if (romPath.contains("/GameLibrary-Data/")) {
            romPath.substringAfter("/GameLibrary-Data/")
        } else {
            ""
        }
        
        // Remove leading slash if any
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1)
        }
        
        Log.i(TAG, "🔍 Checking core override for key: '$relativePath' (Source: '$romPath')")
        
        // Vérifier l'override
        // Generate potential override keys
        val potentialKeys = mutableListOf<String>()
        
        // 1. Exact relative path from GameLibrary-Data (e.g. "roms/nes/game.zip" or "nes/game.zip")
        if (relativePath.isNotEmpty()) {
            potentialKeys.add(relativePath)
            // 2. Try adding "roms/" prefix if missing
            if (!relativePath.startsWith("roms/")) {
                potentialKeys.add("roms/$relativePath")
            }
        }
        
        // 3. Fallback: Synthetic key for external games or unexpected paths (e.g. "roms/console/game.zip")
        // Get filename
        val fileName = if (romPath.contains("/")) romPath.substringAfterLast("/") else romPath
        val consoleKey = if (console.contains("/")) console.substringBefore("/") else console
        val syntheticKey = "roms/${consoleKey.lowercase()}/$fileName"
        if (!potentialKeys.contains(syntheticKey)) {
            potentialKeys.add(syntheticKey)
        }
        
        Log.i(TAG, "🔍 Checking core overrides for keys: $potentialKeys")
        
        // Check overrides in order
        var coreFileName: String? = null
        val overrideManager = CoreOverrideManager.getInstance()
        
        for (key in potentialKeys) {
            val overrideCoreId = overrideManager.getCoreOverride(key)
            if (overrideCoreId != null) {
                Log.i(TAG, "✅ Override FOUND for key '$key': $overrideCoreId")
                coreFileName = mapCoreIdToFileName(overrideCoreId)
                break
            }
        }
        
        if (coreFileName == null) {
            Log.i(TAG, "⚠️ No override found for any candidate key")
        }

        
        // Si pas d'override, utiliser la logique par défaut basée sur la console
        if (coreFileName == null) {
            // Pour les sous-consoles (ex: fbneo/sega), utiliser le parent (fbneo)
            val consoleKey = if (console.contains("/")) {
                console.substringBefore("/").lowercase()
            } else {
                console.lowercase()
            }
            
            coreFileName = getDefaultCoreForConsole(consoleKey)
        }
        
        // Vérifier si le core est téléchargé dans RetroPlay-Data/cores/
        val downloadedCorePath = CoreDownloader.getCorePathByFileName(context, coreFileName)
        if (downloadedCorePath != null) {
            Log.i(TAG, "Using downloaded core: $downloadedCorePath")
            return downloadedCorePath
        }
        
        // Sinon, utiliser le core embarqué (juste le nom du fichier)
        Log.i(TAG, "Using embedded core: $coreFileName")
        return coreFileName
    }
    
    /**
     * Mappe un coreId vers un nom de fichier .so
     */
    private fun mapCoreIdToFileName(coreId: String): String? {
        // Normaliser l'ID (minuscules, sans underscore/tiret) pour correspondre aux variations (ex: mame_2010 vs mame2010)
        val normalizedId = coreId.lowercase().replace("_", "").replace("-", "")
        return when (normalizedId) {
            "mame2010" -> "mame2010_libretro_android.so"
            "mame2003plus" -> "mame2003_plus_libretro_android.so"
            "mame2003" -> "mame2003_libretro_android.so"
            "fbneo" -> "fbneo_libretro_android.so"
            "fceumm" -> "fceumm_libretro_android.so"
            "mesen" -> "mesen_libretro_android.so"
            "snes9x" -> "snes9x_libretro_android.so"
            "paralleln64" -> "parallel_n64_libretro_android.so"
            "mupen64plusnext" -> "mupen64plus_next_libretro_android.so"
            "mupen64plusnextgles3" -> "mupen64plus_next_libretro_android.so"
            "mupen64plusnextgles2" -> "mupen64plus_next_gles2_libretro_android.so"
            "gambatte" -> "gambatte_libretro_android.so"
            "mgba" -> "mgba_libretro_android.so"
            "pcsxrearmed" -> "pcsx_rearmed_libretro_android.so"
            "ppsspp" -> "ppsspp_libretro_android.so"
            "genesisplusgx" -> "genesis_plus_gx_libretro_android.so"
            "picodrive" -> "picodrive_libretro_android.so"
            "mednafenwswan" -> "mednafen_wswan_libretro_android.so"
            "wonderswancolor" -> "mednafen_wswan_libretro_android.so"
            "wonderswan" -> "mednafen_wswan_libretro_android.so"
            "ws" -> "mednafen_wswan_libretro_android.so"
            "wsc" -> "mednafen_wswan_libretro_android.so"
            "mednafenngp" -> "mednafen_ngp_libretro_android.so"
            "mednafenpce" -> "mednafen_pce_libretro_android.so"
            "mednafenlynx" -> "mednafen_lynx_libretro_android.so"

            else -> null
        }
    }
    
    /**
     * Obtient le core par défaut pour une console
     */
    private fun getDefaultCoreForConsole(consoleKey: String): String {
        return when (consoleKey) {
            // Nintendo
            "nes", "famicom", "fc" -> "fceumm_libretro_android.so"
            "snes", "sfc", "superfamicom", "super famicom" -> "snes9x_libretro_android.so"
            "n64" -> "parallel_n64_libretro_android.so"
            "gb", "gbc" -> "gambatte_libretro_android.so"
            "gba" -> "mgba_libretro_android.so"
            
            // Sony
            "psx", "ps1", "playstation" -> "pcsx_rearmed_libretro_android.so"
            "psp" -> "ppsspp_libretro_android.so"
            
            // Sega
            "genesis", "megadrive", "md" -> "genesis_plus_gx_libretro_android.so"
            "scd", "segacd" -> "genesis_plus_gx_libretro_android.so"
            "mastersystem", "sms", "segasms" -> "genesis_plus_gx_libretro_android.so"
            "gamegear", "gg", "segagg" -> "genesis_plus_gx_libretro_android.so"
            "32x", "sega32x" -> "picodrive_libretro_android.so"

            
            // Atari
            "atari2600", "atari", "a2600" -> "stella2014_libretro_android.so"
            "atari5200", "a5200" -> "a5200_libretro_android.so"
            "atari7800", "a7800" -> "prosystem_libretro_android.so"
            "lynx", "atarilynx" -> "mednafen_lynx_libretro_android.so"
            
            // Other
            "ngp", "ngc", "neogeopocket" -> "mednafen_ngp_libretro_android.so"
            "ws", "wsc", "wonderswan", "wonderswancolor" -> "mednafen_wswan_libretro_android.so"
            "pce", "turbografx", "pcengine" -> "mednafen_pce_libretro_android.so"
            "arcade" -> "fbneo_libretro_android.so"
            "mame" -> "mame2010_libretro_android.so"
            "fbneo", "neogeo", "cps1", "cps2" -> "fbneo_libretro_android.so"
            
            else -> {
                Log.w(TAG, "No native core for console: $consoleKey, using fceumm fallback")
                "fceumm_libretro_android.so"
            }
        }
    }
    
    /**
     * Retourne la liste des cores à essayer dans l'ordre (fallback automatique)
     * Si le premier core crash, on essaie le suivant automatiquement
     * 
     * @param console L'ID de la console
     * @return Liste des cores à essayer dans l'ordre de priorité
     */
    fun getCoreFallbacks(console: String): List<String> {
        val consoleKey = if (console.contains("/")) {
            console.substringBefore("/").lowercase()
        } else {
            console.lowercase()
        }
        
        return when (consoleKey) {
            // Arcade: FBNeo (plus compatible) → MAME2003+ → MAME2003 → MAME2010
            "arcade" -> listOf(
                "fbneo_libretro_android.so",
                "mame2003_plus_libretro_android.so",
                "mame2003_libretro_android.so",
                "mame2010_libretro_android.so"
            )
            "mame" -> listOf(
                "mame2010_libretro_android.so",
                "mame2003_plus_libretro_android.so",
                "mame2003_libretro_android.so",
                "fbneo_libretro_android.so"
            )
            "fbneo", "neogeo", "cps1", "cps2" -> listOf(
                "fbneo_libretro_android.so",
                "mame2003_plus_libretro_android.so"
            )
            
            // N64: ParaLLEl (performant) → Mupen64Plus (compatible)
            "n64" -> listOf(
                "parallel_n64_libretro_android.so",
                "mupen64plus_next_libretro_android.so"
            )
            
            // Pour les autres consoles, un seul core disponible (utiliser getCorePath)
            else -> {
                // Note: getCorePath nécessite romPath et gameName, donc on retourne une liste vide
                // L'appelant devra utiliser getCorePath() directement
                emptyList()
            }
        }
    }
    
    /**
     * Nettoie les préférences de crash (appelé après succès)
     */
    fun clearCrashPreferences() {
        val crashPrefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        crashPrefs.edit().clear().apply()
    }
}
