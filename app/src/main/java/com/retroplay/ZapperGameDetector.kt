package com.retroplay

import android.util.Log

/**
 * ZapperGameDetector - Détecte automatiquement les jeux NES nécessitant le Zapper
 * 
 * Le Zapper est le pistolet optique de la NES utilisé pour des jeux comme Duck Hunt.
 * Cette classe permet de détecter automatiquement ces jeux et de configurer
 * le core FCEUmm en conséquence (RETRO_DEVICE_POINTER sur le port 2).
 */
object ZapperGameDetector {
    private const val TAG = "ZapperGameDetector"
    
    /**
     * Liste des jeux NES nécessitant le Zapper (light gun)
     * Noms normalisés (lowercase, sans caractères spéciaux)
     */
    private val ZAPPER_GAMES = setOf(
        // Jeux officiels Nintendo
        "duck hunt",
        "duckhunt",
        "hogan's alley",
        "hogans alley",
        "wild gunman",
        "wildgunman",
        
        // Jeux tiers avec support Zapper
        "gumshoe",
        "freedom force",
        "gotcha the sport",
        "gotcha",
        "laser invasion",
        "mechanized attack",
        "operation wolf",
        "shooting range",
        "the adventures of bayou billy",
        "bayou billy",
        "to the earth",
        "tothe earth",
        "town and country surf designs",
        "baby boomer",
        "barker bill's trick shooting",
        "barker bills",
        "chiller",
        "the lone ranger",
        "lone ranger",
        "rescue the embassy mission",
        "track meet",
        "trackfield"
    )
    
    /**
     * Détecte si un jeu nécessite le Zapper
     * 
     * @param romName Nom de la ROM (avec ou sans extension)
     * @param console Nom de la console (doit être "nes" ou équivalent)
     * @return true si le jeu nécessite le Zapper
     */
    fun isZapperGame(romName: String, console: String): Boolean {
        // Le Zapper n'existe que sur NES
        if (!console.lowercase().contains("nes")) {
            return false
        }
        
        // Normaliser le nom de la ROM
        val normalizedName = normalizeRomName(romName)
        
        // Vérifier si le nom contient un des jeux Zapper
        val isZapper = ZAPPER_GAMES.any { normalizedName.contains(it) }
        
        if (isZapper) {
            Log.i(TAG, "Zapper game detected: $romName (normalized: $normalizedName)")
        }
        
        return isZapper
    }
    
    /**
     * Normalise le nom d'une ROM pour la comparaison
     * - Lowercase
     * - Supprime l'extension (.nes, .zip, etc.)
     * - Supprime les caractères spéciaux
     * - Supprime les tags entre parenthèses/crochets (USA), [!], etc.
     */
    private fun normalizeRomName(romName: String): String {
        return romName
            .lowercase()
            // Supprimer l'extension
            .replace(Regex("\\.(nes|zip|7z|rar)$"), "")
            // Supprimer les tags (USA), [!], (E), etc.
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            // Remplacer les underscores et tirets par des espaces
            .replace(Regex("[_-]"), " ")
            // Supprimer les caractères spéciaux sauf espaces
            .replace(Regex("[^a-z0-9\\s]"), "")
            // Supprimer les espaces multiples
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Retourne la liste complète des jeux Zapper connus
     */
    fun getZapperGamesList(): List<String> {
        return ZAPPER_GAMES.sorted()
    }
}

