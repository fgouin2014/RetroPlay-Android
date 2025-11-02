package com.retroplay

/**
 * Mode d'affichage du crosshair pour les jeux Zapper
 */
enum class CrosshairMode(val displayName: String) {
    RETROPLAY_ONLY("RetroPlay"),
    FCEUMM_ONLY("FCEUmm Original"),
    BOTH("Both (Debug)"),
    NONE("None");
    
    /**
     * Retourne le prochain mode dans le cycle
     */
    fun next(): CrosshairMode {
        return when (this) {
            RETROPLAY_ONLY -> FCEUMM_ONLY
            FCEUMM_ONLY -> BOTH
            BOTH -> NONE
            NONE -> RETROPLAY_ONLY
        }
    }
    
    /**
     * Indique si le crosshair FCEUmm (core) doit être affiché
     */
    fun showFCEUmmCrosshair(): Boolean {
        return this == FCEUMM_ONLY || this == BOTH
    }
    
    /**
     * Indique si le crosshair RetroPlay (overlay) doit être affiché
     */
    fun showRetroPlayCrosshair(): Boolean {
        return this == RETROPLAY_ONLY || this == BOTH
    }
}

