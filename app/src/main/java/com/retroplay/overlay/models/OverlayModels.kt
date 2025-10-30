package com.retroplay.overlay.models

import android.view.KeyEvent

/**
 * Configuration complète d'un overlay RetroArch
 * Parsé depuis un fichier .cfg
 */
data class RetroArchOverlayConfig(
    val totalOverlays: Int,
    val layouts: Map<String, OverlayLayout>  // "landscape-A" -> OverlayLayout
)

/**
 * Un layout d'overlay (ex: landscape-A, portrait-B, hidden)
 */
data class OverlayLayout(
    val name: String,                     // "landscape-A", "portrait-6", etc.
    val fullScreen: Boolean = true,
    val normalized: Boolean = true,       // Coordonnées 0.0-1.0
    val rangeModifier: Float = 1.0f,      // Multiplier pour hit zones
    val alphaModifier: Float = 1.0f,      // Transparence
    val buttons: List<OverlayButton>
)

/**
 * Un bouton d'overlay avec sa position et configuration
 */
data class OverlayButton(
    val action: String,                   // "a", "b", "left", "analog_left", "analog_right", "overlay_next"
    val x: Float,                         // Position X (0.0-1.0 if normalized)
    val y: Float,                         // Position Y (0.0-1.0 if normalized)
    val shape: ButtonShape,               // RADIAL or RECT
    val width: Float,                     // Width (range_x) (0.0-1.0 if normalized)
    val height: Float,                    // Height (range_y) (0.0-1.0 if normalized)
    val imagePath: String? = null,        // "img/A.png"
    val nextTarget: String? = null,       // Pour overlay_next buttons
    val type: OverlayButtonType = OverlayButtonType.BUTTONS,  // Type de bouton
    val rangeModifier: Float = 1.0f,      // Multiplier pour analog sticks (sensibilité)
    // Extensions pour compat 100% RetroArch
    val alphaModifier: Float? = null,     // overlayN_descM_alpha_mod
    val exclusive: Boolean = false,       // overlayN_descM_exclusive
    val rangeModExclusive: Boolean = false, // overlayN_descM_range_mod_exclusive
    val movable: Boolean = false,         // overlayN_descM_movable
    val reachUp: Float = 1.0f,            // overlayN_descM_reach_up/_y
    val reachDown: Float = 1.0f,
    val reachLeft: Float = 1.0f,
    val reachRight: Float = 1.0f,
    // Pré-calculs utiles pour rendu/hitbox (mod_x/w/y/h)
    val modX: Float = x - width,
    val modY: Float = y - height,
    val modW: Float = 2f * width,
    val modH: Float = 2f * height
)

/**
 * Type de hitbox pour un bouton
 */
enum class ButtonShape {
    RADIAL,   // Circular/elliptical hitbox (default pour la plupart des boutons)
    RECT      // Rectangular hitbox (utilisé pour les zones combo comme "left|up")
}

/**
 * Type de bouton d'overlay
 * Basé sur RetroArch overlay_type enum
 */
enum class OverlayButtonType {
    BUTTONS,        // Bouton standard (A, B, Start, etc.)
    ANALOG_LEFT,    // Stick analogique gauche
    ANALOG_RIGHT,   // Stick analogique droit
    DPAD_AREA,      // Zone D-pad (non implémenté pour l'instant)
    ABXY_AREA       // Zone ABXY (non implémenté pour l'instant)
}

/**
 * Mapping des actions RetroArch vers Android KeyEvents
 */
object RetroArchButtonMapping {
    
    /**
     * Actions standards → KeyCodes Android
     */
    val ACTION_TO_KEYCODE = mapOf(
        // Face buttons
        "a" to KeyEvent.KEYCODE_BUTTON_A,
        "b" to KeyEvent.KEYCODE_BUTTON_B,
        "x" to KeyEvent.KEYCODE_BUTTON_X,
        "y" to KeyEvent.KEYCODE_BUTTON_Y,
        
        // Shoulder buttons
        "l" to KeyEvent.KEYCODE_BUTTON_L1,
        "r" to KeyEvent.KEYCODE_BUTTON_R1,
        "l2" to KeyEvent.KEYCODE_BUTTON_L2,
        "r2" to KeyEvent.KEYCODE_BUTTON_R2,
        "l3" to KeyEvent.KEYCODE_BUTTON_THUMBL,
        "r3" to KeyEvent.KEYCODE_BUTTON_THUMBR,
        
        // System buttons
        "start" to KeyEvent.KEYCODE_BUTTON_START,
        "select" to KeyEvent.KEYCODE_BUTTON_SELECT,
        
        // D-Pad
        "left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "up" to KeyEvent.KEYCODE_DPAD_UP,
        "down" to KeyEvent.KEYCODE_DPAD_DOWN
    )
    
    /**
     * Parser une action combo (ex: "a|b", "left|up")
     * Retourne la liste des KeyCodes correspondants
     */
    fun parseAction(action: String): List<Int> {
        // Actions spéciales (non-input)
        if (action.startsWith("overlay_next") || 
            action.startsWith("menu_toggle") ||
            action.startsWith("nul")) {
            return emptyList()
        }
        
        // Split par | pour les combos
        val parts = action.split("|")
        return parts.mapNotNull { ACTION_TO_KEYCODE[it.trim().lowercase()] }
    }
    
    /**
     * Vérifier si c'est une action de contrôle overlay (non-input)
     */
    fun isOverlayControlAction(action: String): Boolean {
        return action.startsWith("overlay_next") || 
               action.startsWith("menu_toggle") ||
               action == "nul"
    }
    
    /**
     * Vérifier si c'est une action combo (plusieurs touches)
     */
    fun isComboAction(action: String): Boolean {
        return action.contains("|")
    }
}

/**
 * Préférences d'overlay pour une console
 */
data class OverlayPreference(
    val enabled: Boolean = false,                // Si false, utilise Lemuroid
    val overlayName: String,                     // "flat-nes", "dual-shock", etc.
    val landscapeLayout: String = "landscape-A", // Layout pour landscape
    val portraitLayout: String = "portrait-A",   // Layout pour portrait
    val autoRotate: Boolean = true               // Auto-switch landscape/portrait
)

/**
 * Extension pour faciliter le stockage/lecture des préférences
 */
object OverlayPreferenceManager {
    
    fun save(
        prefs: android.content.SharedPreferences,
        console: String,
        preference: OverlayPreference
    ) {
        // Utiliser commit() au lieu de apply() pour synchronisation immédiate
        // Cela garantit que le listener se déclenche immédiatement
        prefs.edit()
            .putBoolean("overlay_${console}_enabled", preference.enabled)
            .putString("overlay_${console}_name", preference.overlayName)
            .putString("overlay_${console}_layout_landscape", preference.landscapeLayout)
            .putString("overlay_${console}_layout_portrait", preference.portraitLayout)
            .putBoolean("overlay_${console}_auto_rotate", preference.autoRotate)
            .commit()
    }
    
    fun load(
        prefs: android.content.SharedPreferences,
        console: String
    ): OverlayPreference? {
        val enabled = prefs.getBoolean("overlay_${console}_enabled", false)
        if (!enabled) return null
        
        val overlayName = prefs.getString("overlay_${console}_name", null) ?: return null
        val landscapeLayout = prefs.getString("overlay_${console}_layout_landscape", "landscape-A") ?: "landscape-A"
        val portraitLayout = prefs.getString("overlay_${console}_layout_portrait", "portrait-A") ?: "portrait-A"
        val autoRotate = prefs.getBoolean("overlay_${console}_auto_rotate", true)
        
        return OverlayPreference(enabled, overlayName, landscapeLayout, portraitLayout, autoRotate)
    }
    
    fun disable(
        prefs: android.content.SharedPreferences,
        console: String
    ) {
        // Utiliser commit() pour synchronisation immédiate
        prefs.edit()
            .putBoolean("overlay_${console}_enabled", false)
            .commit()
    }
}

/**
 * Informations sur un package d'overlay disponible
 */
data class OverlayPackageInfo(
    val name: String,                    // "flat-nes"
    val displayName: String,             // "RetroArch Flat - NES"
    val description: String,             // "12 layouts (landscape/portrait/hidden)"
    val compatibleConsoles: List<String>, // ["nes", "gameboy"]
    val layoutCount: Int,                // 12
    val hasAnalogSupport: Boolean = false // Pour dual-shock, etc.
)

