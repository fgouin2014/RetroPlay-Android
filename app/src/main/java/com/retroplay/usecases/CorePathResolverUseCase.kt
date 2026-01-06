package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.retroplay.CoreOverrideManager
import com.retroplay.CoreDownloader

/**
 * Use Case pour résoudre les chemins de cores et leurs fallbacks.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Résolution du chemin du core pour une console donnée
 * - Gestion des core overrides (per-game)
 * - Liste des fallbacks pour crash recovery
 */
class CorePathResolverUseCase(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "CorePathResolverUseCase"
    }
    
    /**
     * Retourne le chemin du core pour une console donnée
     * Gère les overrides per-game et les cores téléchargés
     * 
     * @param console Nom de la console
     * @param gameName Nom du jeu (pour override per-game)
     * @param romPath Chemin de la ROM (pour override per-game)
     * @return Chemin du core (fichier téléchargé ou nom du fichier embarqué)
     */
    fun getCorePath(console: String, gameName: String? = null, romPath: String? = null): String {
        // Vérifier d'abord si un override per-game existe
        var coreFileName: String? = null
        if (gameName != null && romPath != null) {
            // Construire le chemin relatif du jeu (ex: "fbneo/sega/afighter.zip")
            val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
                romPath.substringAfter("/GameLibrary-Data/")
            } else {
                ""
            }
            
            if (relativePath.isNotEmpty()) {
                val overrideManager = CoreOverrideManager.getInstance()
                val overrideCoreId = overrideManager.getCoreOverride(relativePath)
                
                if (overrideCoreId != null) {
                    Log.i(TAG, "[CORE_OVERRIDE] Using per-game override: $overrideCoreId for $gameName")
                    // Mapper le coreId vers le fichier .so
                    coreFileName = when (overrideCoreId.lowercase()) {
                        "mame2010" -> "mame2010_libretro_android.so"
                        "mame2003_plus" -> "mame2003_plus_libretro_android.so"
                        "mame2003" -> "mame2003_libretro_android.so"
                        "fbneo" -> "fbneo_libretro_android.so"
                        "fceumm" -> "fceumm_libretro_android.so"
                        "mesen" -> "mesen_libretro_android.so"
                        "snes9x" -> "snes9x_libretro_android.so"
                        "parallel_n64" -> "parallel_n64_libretro_android.so"
                        "mupen64plus_next" -> "mupen64plus_next_libretro_android.so"
                        "mupen64plus_next_gles3" -> "mupen64plus_next_libretro_android.so"
                        "mupen64plus_next_gles2" -> "mupen64plus_next_gles2_libretro_android.so"
                        "gambatte" -> "gambatte_libretro_android.so"
                        "mgba" -> "mgba_libretro_android.so"
                        "pcsx_rearmed" -> "pcsx_rearmed_libretro_android.so"
                        "ppsspp" -> "ppsspp_libretro_android.so"
                        "genesis_plus_gx" -> "genesis_plus_gx_libretro_android.so"
                        "picodrive" -> "picodrive_libretro_android.so"

                        "mednafen_wswan" -> "mednafen_wswan_libretro_android.so"
                        "wonderswancolor" -> "mednafen_wswan_libretro_android.so"
                        "wonderswan" -> "mednafen_wswan_libretro_android.so"
                        "ws" -> "mednafen_wswan_libretro_android.so"
                        "wsc" -> "mednafen_wswan_libretro_android.so"
                        "mednafen_ngp" -> "mednafen_ngp_libretro_android.so"
                        "mednafen_pce" -> "mednafen_pce_libretro_android.so"
                        "mednafen_lynx" -> "mednafen_lynx_libretro_android.so"
                        else -> null
                    }
                }
            }
        }
        
        // Si pas d'override, utiliser la logique par défaut basée sur la console
        if (coreFileName == null) {
            // Pour les sous-consoles (ex: fbneo/sega), utiliser le parent (fbneo)
            val consoleKey = if (console.contains("/")) {
                console.substringBefore("/").lowercase()
            } else {
                console.lowercase()
            }
            
            coreFileName = when (consoleKey) {
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
                
                // Special cases (sub-consoles)
                "picodrive" -> "picodrive_libretro_android.so"

                "mednafen_wswan" -> "mednafen_wswan_libretro_android.so"
                "wonderswancolor" -> "mednafen_wswan_libretro_android.so"
                "wonderswan" -> "mednafen_wswan_libretro_android.so"
                "ws" -> "mednafen_wswan_libretro_android.so"
                "wsc" -> "mednafen_wswan_libretro_android.so"
                "mednafen_ngp" -> "mednafen_ngp_libretro_android.so"
                "mednafen_pce" -> "mednafen_pce_libretro_android.so"
                "mednafen_lynx" -> "mednafen_lynx_libretro_android.so"
                
                else -> {
                    Log.w(TAG, "No native core for console: $console, using fceumm fallback")
                    "fceumm_libretro_android.so"
                }
            }
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
     * Retourne la liste des cores à essayer dans l'ordre (fallback automatique)
     * Si le premier core crash, on essaie le suivant automatiquement
     * 
     * @param console Nom de la console
     * @param gameName Nom du jeu (pour override per-game)
     * @param romPath Chemin de la ROM (pour override per-game)
     * @return Liste des chemins de cores à essayer dans l'ordre
     */
    fun getCoreFallbacks(console: String, gameName: String? = null, romPath: String? = null): List<String> {
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
            
            // Pour les autres consoles, un seul core disponible
            else -> listOf(getCorePath(console, gameName, romPath))
        }
    }
}
















