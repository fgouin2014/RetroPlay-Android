package com.retroplay.cheat

import android.util.Log
import com.swordfish.libretrodroid.GLRetroView

/**
 * Applique les codes de triche au core LibretroDroid
 * 
 * Note: LibretroDroid supporte les cheats via les cores Libretro.
 * Cette classe convertit et applique les codes selon le format du core.
 */
class CheatApplier(private val retroView: GLRetroView) {
    
    companion object {
        private const val TAG = "CheatApplier"
    }
    
    /**
     * Appliquer une liste de codes de triche
     * 
     * @param cheats Liste des codes à appliquer (seulement ceux avec enabled=true)
     * @return true si succès, false sinon
     */
    fun applyCheatsList(cheats: List<CheatManager.Cheat>): Boolean {
        try {
            // Filtrer seulement les codes activés
            val enabledCheats = cheats.filter { it.enabled }
            
            if (enabledCheats.isEmpty()) {
                Log.i(TAG, "No active cheats to apply")
                clearAllCheats()
                return true
            }
            
            Log.i(TAG, "Applying ${enabledCheats.size} cheat(s)")
            
            // Réinitialiser les codes existants
            clearAllCheats()
            
            // Appliquer chaque code
            enabledCheats.forEachIndexed { index, cheat ->
                val converted = convertCheatCode(cheat)
                if (converted != null) {
                    applyCheatCode(index, cheat.description, converted)
                } else {
                    Log.w(TAG, "Failed to convert cheat: ${cheat.description}")
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying cheats", e)
            return false
        }
    }
    
    /**
     * Convertir un code vers le format LibretroDroid/PCSX
     * 
     * IMPORTANT: PCSX ReARMed attend le format LIBRETRO (avec ESPACE, PAS +)
     * "AAAAAAAA VVVV" (ESPACE entre adresse et valeur)
     * 
     * Conversion RetroArch → Libretro :
     * - "8009C6E4+03E7" → "8009C6E4 03E7"
     * - "50001A01+0000+3001F2D2+0001" → "50001A01 0000 3001F2D2 0001"
     * 
     * Cas spéciaux:
     * - Multi-codes : Remplacer TOUS les +
     * - Wildcards : 800A0F34+000? → 800A0F34 000?
     * - Conditions : D00B7328+???? → D00B7328 ????
     */
    private fun convertCheatCode(cheat: CheatManager.Cheat): String? {
        // CONVERSION CRUCIALE : Remplacer + par espace
        // Le core Libretro attend un ESPACE, pas un +
        return cheat.code.trim().replace("+", " ")
    }
    
    /**
     * Appliquer un code individuel au core
     * 
     * Utilise l'API publique GLRetroView.setCheat() de LibretroDroid
     */
    private fun applyCheatCode(index: Int, description: String, code: String) {
        try {
            // LibretroDroid expose setCheat() publiquement !
            // fun setCheat(index : Int, enable : Boolean, code : String)
            
            retroView.setCheat(index, true, code)
            
            Log.i(TAG, "✅ Applied cheat #$index: $description = $code")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying cheat #$index", e)
        }
    }
    
    /**
     * Supprimer tous les codes actifs
     * 
     * Note: Ne fait rien pour l'instant car setCheat(i, false, "")
     * spam le core avec des erreurs "couldn't parse ''"
     * Les codes seront écrasés lors du prochain applyCheatsList()
     */
    fun clearAllCheats() {
        Log.i(TAG, "🧹 Skipping clear (will be overwritten by next apply)")
    }
    
    /**
     * Activer/désactiver un code spécifique
     */
    fun toggleCheat(index: Int, enabled: Boolean, cheat: CheatManager.Cheat) {
        try {
            if (enabled) {
                val converted = convertCheatCode(cheat)
                if (converted != null) {
                    Log.i(TAG, "✅ Enabling cheat #$index: ${cheat.description}")
                    retroView.setCheat(index, true, converted)
                }
            } else {
                Log.i(TAG, "❌ Disabling cheat #$index: ${cheat.description}")
                retroView.setCheat(index, false, "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error toggling cheat #$index", e)
        }
    }
}

/**
 * Extension pour créer facilement un CheatApplier
 */
fun GLRetroView.createCheatApplier(): CheatApplier {
    return CheatApplier(this)
}

