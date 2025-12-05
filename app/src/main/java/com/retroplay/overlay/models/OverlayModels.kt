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
    val fullScreen: Boolean = false,      // DEFAULT = false (compatible RetroArch)
    val normalized: Boolean = false,      // DEFAULT = false (compatible RetroArch), coordonnées pixel par défaut
    val rangeModifier: Float = 1.0f,      // Multiplier pour hit zones
    val alphaModifier: Float = 1.0f,      // Transparence
    val buttons: List<OverlayButton>,
    val rect: OverlayRect? = null,        // overlay0_rect (custom positioning)
    val backgroundImage: String? = null,  // overlay0_overlay (image de fond)
    val aspectRatio: Float? = null,       // overlay0_aspect_ratio
    val blockXSeparation: Boolean = false, // overlay0_block_x_separation
    val blockYSeparation: Boolean = false, // overlay0_block_y_separation
    val autoXSeparation: Boolean = false,  // overlay0_auto_x_separation (défaut calculé conditionnellement)
    val autoYSeparation: Boolean = false   // overlay0_auto_y_separation (défaut false, compatible RetroArch)
)

/**
 * Rectangle de positionnement custom pour un overlay
 * overlay0_rect = "x,y,width,height" (normalized 0.0-1.0)
 */
data class OverlayRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
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
    val nextTarget: String? = null,       // Pour overlay_next buttons (nom de l'overlay cible)
    val nextIndex: Int? = null,          // Index résolu de l'overlay cible (résolu après parsing)
    val type: OverlayButtonType = OverlayButtonType.BUTTONS,  // Type de bouton
    val rangeModifier: Float = 1.0f,      // Multiplier pour analog sticks (sensibilité)
    // Bitmask des boutons RetroPad (format "a|b|c" → Set<Int> des IDs)
    // Compatible RetroArch input_bits_t (256 bits)
    val buttonMask: Set<Int> = emptySet(), // IDs RetroPad (RETRO_DEVICE_ID_JOYPAD_*)
    // Extensions pour compat 100% RetroArch
    val alphaModifier: Float? = null,     // overlayN_descM_alpha_mod
    val exclusive: Boolean = false,       // overlayN_descM_exclusive
    val rangeModExclusive: Boolean = false, // overlayN_descM_range_mod_exclusive
    val movable: Boolean = false,         // overlayN_descM_movable
    val turbo: Boolean = false,           // overlayN_descM_turbo (RetroPlay extension)
    val reachUp: Float = 1.0f,            // overlayN_descM_reach_up/_y
    val reachDown: Float = 1.0f,
    val reachLeft: Float = 1.0f,
    val reachRight: Float = 1.0f,
    val analogSaturatePct: Float = 1.0f,  // overlayN_descM_saturate_pct (analog sticks)
    // Pré-calculs utiles pour rendu/hitbox (mod_x/w/y/h)
    val modX: Float = x - width,
    val modY: Float = y - height,
    val modW: Float = 2f * width,
    val modH: Float = 2f * height,
    // 8-way area custom mappings (pour dpad_area et abxy_area)
    val eightwayUp: String? = null,           // overlayN_descM_up
    val eightwayDown: String? = null,         // overlayN_descM_down
    val eightwayLeft: String? = null,         // overlayN_descM_left
    val eightwayRight: String? = null,        // overlayN_descM_right
    val eightwayUpLeft: String? = null,       // overlayN_descM_up_left
    val eightwayUpRight: String? = null,      // overlayN_descM_up_right
    val eightwayDownLeft: String? = null,     // overlayN_descM_down_left
    val eightwayDownRight: String? = null,    // overlayN_descM_down_right
    // Hitbox position override (recalculée après scale/offset/separation)
    val xHitboxOverride: Float? = null,
    val yHitboxOverride: Float? = null,
    // Hitbox size override (recalculée après scale/offset/separation avec width/height scalés)
    val rangeXHitboxOverride: Float? = null,
    val rangeYHitboxOverride: Float? = null
) {
    /**
     * Calcul de la POSITION de la hitbox (peut être décalée si reach asymétrique)
     * Identique à RetroArch: x_hitbox = ((x + range_x * reach_right) + (x - range_x * reach_left)) / 2.0
     * Utilise xHitboxOverride si défini (après transformations scale/offset)
     */
    val xHitbox: Float = xHitboxOverride ?: ((x + width * reachRight) + (x - width * reachLeft)) / 2.0f
    
    /**
     * Calcul de la POSITION de la hitbox en Y
     * Identique à RetroArch: y_hitbox = ((y + range_y * reach_down) + (y - range_y * reach_up)) / 2.0
     * Utilise yHitboxOverride si défini (après transformations scale/offset)
     */
    val yHitbox: Float = yHitboxOverride ?: ((y + height * reachDown) + (y - height * reachUp)) / 2.0f
    
    /**
     * Calcul de la TAILLE de la hitbox réelle (range_x_hitbox) en appliquant reach_*
     * Identique à RetroArch: range_x_hitbox = (range_x * reach_right + range_x * reach_left) / 2.0
     * Utilise rangeXHitboxOverride si défini (après transformations scale/offset avec width scalé)
     */
    val rangeXHitbox: Float = rangeXHitboxOverride ?: (width * reachRight + width * reachLeft) / 2.0f
    
    /**
     * Calcul de la TAILLE de la hitbox réelle (range_y_hitbox) en appliquant reach_*
     * Identique à RetroArch: range_y_hitbox = (range_y * reach_down + range_y * reach_up) / 2.0
     * Utilise rangeYHitboxOverride si défini (après transformations scale/offset avec height scalé)
     */
    val rangeYHitbox: Float = rangeYHitboxOverride ?: (height * reachDown + height * reachUp) / 2.0f
}

/**
 * Type de hitbox pour un bouton
 */
enum class ButtonShape {
    RADIAL,   // Circular/elliptical hitbox (default pour la plupart des boutons)
    RECT,     // Rectangular hitbox (utilisé pour les zones combo comme "left|up")
    NONE      // Hitbox désactivée (compatible RetroArch OVERLAY_HITBOX_NONE)
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
 * IDs RetroPad (RETRO_DEVICE_ID_JOYPAD_*) selon libretro.h
 * Compatible avec RetroArch input_bits_t bitmask
 */
object RetroPadIds {
    // Face buttons
    const val JOYPAD_B = 0
    const val JOYPAD_Y = 1
    const val JOYPAD_SELECT = 2
    const val JOYPAD_START = 3
    const val JOYPAD_UP = 4
    const val JOYPAD_DOWN = 5
    const val JOYPAD_LEFT = 6
    const val JOYPAD_RIGHT = 7
    const val JOYPAD_A = 8
    const val JOYPAD_X = 9
    const val JOYPAD_L = 10
    const val JOYPAD_R = 11
    const val JOYPAD_L2 = 12
    const val JOYPAD_R2 = 13
    const val JOYPAD_L3 = 14
    const val JOYPAD_R3 = 15
    
    // Special actions (RARCH_*)
    const val RARCH_OVERLAY_NEXT = 16
    const val RARCH_OSK = 17
    // ... autres hotkeys peuvent être ajoutés si nécessaire
}

/**
 * Mapping des actions RetroArch vers Android KeyEvents
 */
object RetroArchButtonMapping {
    
    /**
     * Mapping nom de bouton → ID RetroPad (RETRO_DEVICE_ID_JOYPAD_*)
     * Compatible avec RetroArch input_config_translate_str_to_bind_id()
     */
    val ACTION_TO_RETROPAD_ID = mapOf(
        // Face buttons
        "a" to RetroPadIds.JOYPAD_A,
        "b" to RetroPadIds.JOYPAD_B,
        "x" to RetroPadIds.JOYPAD_X,
        "y" to RetroPadIds.JOYPAD_Y,
        
        // Shoulder buttons
        "l" to RetroPadIds.JOYPAD_L,
        "r" to RetroPadIds.JOYPAD_R,
        "l2" to RetroPadIds.JOYPAD_L2,
        "r2" to RetroPadIds.JOYPAD_R2,
        "l3" to RetroPadIds.JOYPAD_L3,
        "r3" to RetroPadIds.JOYPAD_R3,
        
        // System buttons
        "start" to RetroPadIds.JOYPAD_START,
        "select" to RetroPadIds.JOYPAD_SELECT,
        
        // D-Pad
        "left" to RetroPadIds.JOYPAD_LEFT,
        "right" to RetroPadIds.JOYPAD_RIGHT,
        "up" to RetroPadIds.JOYPAD_UP,
        "down" to RetroPadIds.JOYPAD_DOWN,
        
        // Special actions
        "overlay_next" to RetroPadIds.RARCH_OVERLAY_NEXT,
        "osk" to RetroPadIds.RARCH_OSK
    )
    
    /**
     * Actions standards → KeyCodes Android (pour backward compatibility)
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
     * Parser format "a|b|c" en bitmask (Set<Int> d'IDs RetroPad)
     * Compatible avec RetroArch task_overlay_redefine_eightway_direction()
     * et task_overlay.c:344-353 (parsing button_mask)
     * 
     * @param actionString Format "a|b|c" ou "a" simple
     * @return Set d'IDs RetroPad (RETRO_DEVICE_ID_JOYPAD_*)
     */
    fun parseButtonMask(actionString: String): Set<Int> {
        if (actionString.isBlank()) {
            return emptySet()
        }
        
        // Ignorer "nul" ou "null" (comme RetroArch ligne 351)
        if (actionString.equals("nul", ignoreCase = true) || 
            actionString.equals("null", ignoreCase = true)) {
            return emptySet()
        }
        
        // Split par "|" (comme RetroArch strtok_r avec "|")
        val parts = actionString.split("|").map { it.trim().lowercase() }
        
        // Convertir chaque partie en ID RetroPad
        val mask = parts.mapNotNull { part ->
            ACTION_TO_RETROPAD_ID[part]
        }.toSet()
        
        return mask
    }
    
    /**
     * Parser une action combo (ex: "a|b", "left|up")
     * Retourne la liste des KeyCodes correspondants
     */
    fun parseAction(action: String): List<Int> {
        // Actions spéciales (non-input)
        if (action.startsWith("overlay_next") || 
            action.startsWith("menu_toggle") ||
            action.startsWith("nul") ||
            action.startsWith("null")) {
            return emptyList()
        }
        
        // Split par | pour les combos
        val parts = action.split("|")
        return parts.mapNotNull { ACTION_TO_KEYCODE[it.trim().lowercase()] }
    }
    
    /**
     * Hotkeys RetroArch (actions spéciales)
     */
    val HOTKEY_ACTIONS = setOf(
        // Save/Load states
        "save_state", "load_state",
        "state_slot_increase", "state_slot_decrease",
        // Rewind / Fast forward
        "rewind", "toggle_fast_forward", "hold_fast_forward",
        // Reset / Pause
        "reset", "pause_toggle",
        // Screenshot
        "screenshot",
        // Shader cycling
        "shader_next", "shader_prev",
        // Misc
        "toggle_slowmotion", "frame_advance"
    )
    
    /**
     * Vérifier si c'est une action de contrôle overlay (non-input)
     */
    fun isOverlayControlAction(action: String): Boolean {
        return action.startsWith("overlay_next") || 
               action.startsWith("overlay_prev") ||
               action.startsWith("menu_toggle") ||
               action == "nul" ||
               action == "null"
    }
    
    /**
     * Vérifier si c'est un hotkey RetroArch
     */
    fun isHotkeyAction(action: String): Boolean {
        return HOTKEY_ACTIONS.contains(action)
    }
    
    /**
     * Actions Lightgun RetroArch (gun_*)
     * Mapping selon RetroArch overlay_lightgun_action enum
     */
    val LIGHTGUN_ACTIONS = setOf(
        "gun_trigger",      // 1 - Trigger principal
        "gun_reload",       // 2 - Reload (offscreen shot)
        "gun_aux_a",        // 3 - Bouton auxiliaire A
        "gun_aux_b",        // 4 - Bouton auxiliaire B
        "gun_aux_c",        // 5 - Bouton auxiliaire C
        "gun_start",        // 6 - Start button
        "gun_select",       // 7 - Select button
        "gun_dpad_up",      // 8 - D-Pad Up
        "gun_dpad_down",    // 9 - D-Pad Down
        "gun_dpad_left",    // 10 - D-Pad Left
        "gun_dpad_right"    // 11 - D-Pad Right
    )
    
    /**
     * Convertir une action lightgun (nom) en ID numérique RetroArch
     * @param action Nom de l'action (ex: "gun_trigger", "gun_reload")
     * @return ID numérique (1-11) ou 0 si action inconnue
     */
    fun lightgunActionToId(action: String): Int {
        return when (action) {
            "gun_trigger" -> 1
            "gun_reload" -> 2
            "gun_aux_a" -> 3
            "gun_aux_b" -> 4
            "gun_aux_c" -> 5
            "gun_start" -> 6
            "gun_select" -> 7
            "gun_dpad_up" -> 8
            "gun_dpad_down" -> 9
            "gun_dpad_left" -> 10
            "gun_dpad_right" -> 11
            else -> 0
        }
    }
    
    /**
     * Vérifier si c'est une action lightgun RetroArch
     */
    fun isLightgunAction(action: String): Boolean {
        return LIGHTGUN_ACTIONS.contains(action)
    }
    
    /**
     * Vérifier si c'est une action combo (plusieurs touches)
     */
    fun isComboAction(action: String): Boolean {
        return action.contains("|")
    }
}

/**
 * Show Inputs mode
 */
enum class ShowInputsMode {
    NONE,      // Pas d'affichage
    TOUCHED,   // Afficher les touches tactiles
    PHYSICAL,  // Afficher les touches du gamepad physique
    BOTH       // Afficher les deux
}

/**
 * Advanced overlay settings (options avancées)
 */
data class AdvancedOverlaySettings(
    val dpadDiagonalSensitivity: Int = 50,     // 0-100
    val abxyDiagonalSensitivity: Int = 50,     // 0-100
    val analogRecenterZone: Int = 0,           // 0-100
    val opacity: Float = 1.0f,                 // 0.0-1.0
    val aspectAdjust: Float = 0.0f,            // -0.5 à 0.5
    val hideInMenu: Boolean = false,
    val behindMenu: Boolean = false,
    val hideWhenGamepadConnected: Boolean = false,
    val hideWhenGamepadConnectedPort0Only: Boolean = false,  // Si true, vérifie seulement port 0 (compatible RetroArch, utile multijoueur)
    val showInputs: ShowInputsMode = ShowInputsMode.NONE,
    val showInputsPort: Int = 0,               // Port à afficher (0 = all)
    // Lightgun options
    val lightgunPort: Int = 0,                 // Port du lightgun (0-3)
    val lightgunTriggerOnTouch: Boolean = true,   // Déclencher au touch (vs release) - TRUE comme RetroArch officiel
    val lightgunTriggerDelay: Int = 0,         // Délai avant déclenchement (ms)
    val lightgunAllowOffscreen: Boolean = true,   // Permettre tir hors écran
    val lightgunTwoTouchInput: Int = 0,        // Action pour 2 doigts (0=none, 1=start, 2=select, etc.)
    val lightgunThreeTouchInput: Int = 0,      // Action pour 3 doigts
    val lightgunFourTouchInput: Int = 0,       // Action pour 4 doigts
    // Mouse options
    val mouseSpeed: Float = 1.0f,              // Vitesse souris (0.1-5.0)
    val mouseSwipeThreshold: Float = 1.0f,     // Seuil swipe (pixels, float dans RetroArch)
    val mouseHoldToDrag: Boolean = false,      // Maintenir pour drag
    val mouseHoldMsec: Int = 500,              // Durée hold (ms)
    val mouseDoubleTapToDrag: Boolean = false, // Double-tap pour drag
    val mouseDtapMsec: Int = 300,              // Délai double-tap (ms)
    val showMouseCursor: Boolean = true        // Afficher curseur souris
)

/**
 * Préférences d'overlay pour une console
 */
data class OverlayPreference(
    val enabled: Boolean = false,                // Si false, utilise Lemuroid
    val overlayName: String,                     // "flat-nes", "dual-shock", etc.
    val customCfgName: String? = null,           // Pour customs: "dreamcast.cfg", null pour standards
    val landscapeLayout: String = "landscape-A", // Layout pour landscape
    val portraitLayout: String = "portrait-A",   // Layout pour portrait
    val autoRotate: Boolean = true,              // Auto-switch landscape/portrait
    val swapAnalogSticks: Boolean = false,       // Inverser Left <-> Right sticks
    val invertAnalogLeftY: Boolean = false,      // Inverser haut/bas (Y axis) pour stick gauche
    val invertAnalogRightY: Boolean = false,     // Inverser haut/bas (Y axis) pour stick droit
    val scale: Float = 1.0f,                     // Échelle globale (0.5-1.5)
    val xOffset: Float = 0.0f,                   // Décalage X (-0.2 à 0.2)
    val yOffset: Float = 0.0f,                   // Décalage Y (-0.2 à 0.2)
    val xSeparation: Float = 0.0f,               // Séparation interne X (-0.2 à 0.2)
    val ySeparation: Float = 0.0f                // Séparation interne Y (-0.2 à 0.2)
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
        val editor = prefs.edit()
            .putBoolean("overlay_${console}_enabled", preference.enabled)
            .putString("overlay_${console}_name", preference.overlayName)
            .putString("overlay_${console}_layout_landscape", preference.landscapeLayout)
            .putString("overlay_${console}_layout_portrait", preference.portraitLayout)
            .putBoolean("overlay_${console}_auto_rotate", preference.autoRotate)
            .putBoolean("overlay_${console}_swap_analog_sticks", preference.swapAnalogSticks)
            .putBoolean("overlay_${console}_invert_analog_left_y", preference.invertAnalogLeftY)
            .putBoolean("overlay_${console}_invert_analog_right_y", preference.invertAnalogRightY)
            .putFloat("overlay_${console}_scale", preference.scale)
            .putFloat("overlay_${console}_x_offset", preference.xOffset)
            .putFloat("overlay_${console}_y_offset", preference.yOffset)
            .putFloat("overlay_${console}_x_separation", preference.xSeparation)
            .putFloat("overlay_${console}_y_separation", preference.ySeparation)
        
        // Sauvegarder customCfgName si c'est un custom (sinon supprimer la clé)
        if (preference.customCfgName != null) {
            editor.putString("overlay_${console}_custom_cfg", preference.customCfgName)
        } else {
            editor.remove("overlay_${console}_custom_cfg")
        }
        
        // Nettoyer l'ancienne clé legacy si elle existe encore
        editor.remove("overlay_${console}_invert_analog_y")
        
        editor.commit()
    }
    
    fun load(
        prefs: android.content.SharedPreferences,
        console: String
    ): OverlayPreference? {
        val enabled = prefs.getBoolean("overlay_${console}_enabled", false)
        if (!enabled) return null
        
        val overlayName = prefs.getString("overlay_${console}_name", null) ?: return null
        val customCfgName = prefs.getString("overlay_${console}_custom_cfg", null)
        val landscapeLayout = prefs.getString("overlay_${console}_layout_landscape", "landscape-A") ?: "landscape-A"
        val portraitLayout = prefs.getString("overlay_${console}_layout_portrait", "portrait-A") ?: "portrait-A"
        val autoRotate = prefs.getBoolean("overlay_${console}_auto_rotate", true)
        val swapAnalogSticks = prefs.getBoolean("overlay_${console}_swap_analog_sticks", false)
        val invertAnalogLeftY = prefs.getBoolean("overlay_${console}_invert_analog_left_y", prefs.getBoolean("overlay_${console}_invert_analog_y", false))
        val invertAnalogRightY = prefs.getBoolean("overlay_${console}_invert_analog_right_y", prefs.getBoolean("overlay_${console}_invert_analog_y", false))
        val scale = prefs.getFloat("overlay_${console}_scale", 1.0f)
        val xOffset = prefs.getFloat("overlay_${console}_x_offset", 0.0f)
        val yOffset = prefs.getFloat("overlay_${console}_y_offset", 0.0f)
        val xSeparation = prefs.getFloat("overlay_${console}_x_separation", 0.0f)
        val ySeparation = prefs.getFloat("overlay_${console}_y_separation", 0.0f)
        
        return OverlayPreference(
            enabled = enabled,
            overlayName = overlayName,
            customCfgName = customCfgName,
            landscapeLayout = landscapeLayout,
            portraitLayout = portraitLayout,
            autoRotate = autoRotate,
            swapAnalogSticks = swapAnalogSticks,
            invertAnalogLeftY = invertAnalogLeftY,
            invertAnalogRightY = invertAnalogRightY,
            scale = scale,
            xOffset = xOffset,
            yOffset = yOffset,
            xSeparation = xSeparation,
            ySeparation = ySeparation
        )
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
    
    /**
     * Load advanced settings for a console
     * 
     * @param orientation Optional orientation ("landscape" or "portrait"). If null, uses global settings (backward compatibility).
     *                    If orientation-specific key doesn't exist, falls back to global key, then to default value.
     */
    fun loadAdvancedSettings(
        prefs: android.content.SharedPreferences,
        console: String,
        orientation: String? = null  // null = global (backward compat), "landscape", "portrait"
    ): AdvancedOverlaySettings {
        val suffix = if (orientation != null) "_$orientation" else ""
        
        // Helper functions to read with fallback: per-orientation → global → default
        fun getInt(key: String, default: Int): Int {
            val perOrientationKey = "overlay_${console}_${key}$suffix"
            val globalKey = "overlay_${console}_${key}"
            
            return when {
                prefs.contains(perOrientationKey) -> {
                    try {
                        prefs.getInt(perOrientationKey, default)
                    } catch (e: ClassCastException) {
                        // Migration: Float → Int (rare, mais possible)
                        try {
                            prefs.getFloat(perOrientationKey, default.toFloat()).toInt().also {
                                prefs.edit().putInt(perOrientationKey, it).apply()
                            }
                        } catch (e2: Exception) {
                            default
                        }
                    }
                }
                prefs.contains(globalKey) -> {
                    try {
                        prefs.getInt(globalKey, default)
                    } catch (e: ClassCastException) {
                        try {
                            prefs.getFloat(globalKey, default.toFloat()).toInt().also {
                                prefs.edit().putInt(globalKey, it).apply()
                            }
                        } catch (e2: Exception) {
                            default
                        }
                    }
                }
                else -> default
            }
        }
        
        fun getFloat(key: String, default: Float): Float {
            val perOrientationKey = "overlay_${console}_${key}$suffix"
            val globalKey = "overlay_${console}_${key}"
            
            return when {
                prefs.contains(perOrientationKey) -> {
                    try {
                        prefs.getFloat(perOrientationKey, default)
                    } catch (e: ClassCastException) {
                        // Migration: Int → Float (ex: opacity 70 → 0.7f)
                        // Pour opacity: convertir Int (0-100) → Float (0.0-1.0)
                        // Pour aspectAdjust: Int (0) → Float (0.0f)
                        try {
                            val intValue = prefs.getInt(perOrientationKey, (default * 100).toInt())
                            if (key == "opacity") {
                                // opacity: 0-100 → 0.0-1.0
                                (intValue.coerceIn(0, 100).toFloat() / 100f).also {
                                    // Sauvegarder la valeur convertie pour éviter de reconvertir
                                    prefs.edit().putFloat(perOrientationKey, it).apply()
                                }
                            } else {
                                // aspectAdjust et autres: conversion directe
                                intValue.toFloat().also {
                                    prefs.edit().putFloat(perOrientationKey, it).apply()
                                }
                            }
                        } catch (e2: Exception) {
                            default
                        }
                    }
                }
                prefs.contains(globalKey) -> {
                    try {
                        prefs.getFloat(globalKey, default)
                    } catch (e: ClassCastException) {
                        try {
                            val intValue = prefs.getInt(globalKey, (default * 100).toInt())
                            if (key == "opacity") {
                                (intValue.coerceIn(0, 100).toFloat() / 100f).also {
                                    prefs.edit().putFloat(globalKey, it).apply()
                                }
                            } else {
                                intValue.toFloat().also {
                                    prefs.edit().putFloat(globalKey, it).apply()
                                }
                            }
                        } catch (e2: Exception) {
                            default
                        }
                    }
                }
                else -> default
            }
        }
        
        fun getBoolean(key: String, default: Boolean): Boolean {
            val perOrientationKey = "overlay_${console}_${key}$suffix"
            val globalKey = "overlay_${console}_${key}"
            
            return when {
                prefs.contains(perOrientationKey) -> prefs.getBoolean(perOrientationKey, default)
                prefs.contains(globalKey) -> prefs.getBoolean(globalKey, default)  // Fallback to global
                else -> default
            }
        }
        
        fun getString(key: String, default: String): String {
            val perOrientationKey = "overlay_${console}_${key}$suffix"
            val globalKey = "overlay_${console}_${key}"
            
            return when {
                prefs.contains(perOrientationKey) -> prefs.getString(perOrientationKey, default) ?: default
                prefs.contains(globalKey) -> prefs.getString(globalKey, default) ?: default  // Fallback to global
                else -> default
            }
        }
        
        val showInputsString = getString("show_inputs", "NONE")
        val showInputsMode = try {
            ShowInputsMode.valueOf(showInputsString)
        } catch (e: IllegalArgumentException) {
            ShowInputsMode.NONE
        }
        
        // Handle mouseSwipeThreshold migration (Int → Float) with fallback
        val mouseSwipeThreshold = try {
            val perOrientationKey = "overlay_${console}_mouse_swipe_threshold$suffix"
            val globalKey = "overlay_${console}_mouse_swipe_threshold"
            
            when {
                prefs.contains(perOrientationKey) -> {
                    try {
                        prefs.getFloat(perOrientationKey, 1.0f)
                    } catch (e: ClassCastException) {
                        prefs.getInt(perOrientationKey, 1).toFloat()
                    }
                }
                prefs.contains(globalKey) -> {
                    try {
                        prefs.getFloat(globalKey, 1.0f)
                    } catch (e: ClassCastException) {
                        prefs.getInt(globalKey, 1).toFloat()
                    }
                }
                else -> 1.0f
            }
        } catch (e: Exception) {
            1.0f
        }
        
        return AdvancedOverlaySettings(
            // DEFAULT VALUES from RetroArch config.def.h (c:\repos\RetroArch-master\config.def.h)
            dpadDiagonalSensitivity = getInt("dpad_diagonal_sensitivity", 80),  // DEFAULT_OVERLAY_DPAD_DIAGONAL_SENSITIVITY
            abxyDiagonalSensitivity = getInt("abxy_diagonal_sensitivity", 50),  // DEFAULT_OVERLAY_ABXY_DIAGONAL_SENSITIVITY
            analogRecenterZone = getInt("analog_recenter_zone", 0),  // DEFAULT_INPUT_OVERLAY_ANALOG_RECENTER_ZONE
            opacity = getFloat("opacity", 0.7f),  // DEFAULT_INPUT_OVERLAY_OPACITY
            aspectAdjust = getFloat("aspect_adjust", 0.0f),  // DEFAULT_INPUT_OVERLAY_ASPECT_ADJUST_*
            hideInMenu = getBoolean("hide_in_menu", true),  // DEFAULT_OVERLAY_HIDE_IN_MENU
            behindMenu = getBoolean("behind_menu", false),  // DEFAULT_OVERLAY_BEHIND_MENU
            hideWhenGamepadConnected = getBoolean("hide_when_gamepad", false),  // DEFAULT_OVERLAY_HIDE_WHEN_GAMEPAD_CONNECTED
            hideWhenGamepadConnectedPort0Only = getBoolean("hide_when_gamepad_port0_only", false),  // Mode port 0 seulement (compatible RetroArch, utile multijoueur)
            showInputs = showInputsMode,
            showInputsPort = getInt("show_inputs_port", 0),  // DEFAULT_OVERLAY_SHOW_INPUTS_PORT
            lightgunPort = getInt("lightgun_port", -1),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_PORT (-1 = all ports)
            lightgunTriggerOnTouch = getBoolean("lightgun_trigger_on_touch", true),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_ON_TOUCH
            lightgunTriggerDelay = getInt("lightgun_trigger_delay", 1),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY
            lightgunAllowOffscreen = getBoolean("lightgun_allow_offscreen", true),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_ALLOW_OFFSCREEN
            lightgunTwoTouchInput = getInt("lightgun_two_touch", 0),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_MULTI_TOUCH_INPUT
            lightgunThreeTouchInput = getInt("lightgun_three_touch", 0),
            lightgunFourTouchInput = getInt("lightgun_four_touch", 0),
            mouseSpeed = getFloat("mouse_speed", 1.0f),  // DEFAULT_INPUT_OVERLAY_MOUSE_SPEED
            mouseSwipeThreshold = mouseSwipeThreshold,  // DEFAULT_INPUT_OVERLAY_MOUSE_SWIPE_THRESHOLD
            mouseHoldToDrag = getBoolean("mouse_hold_to_drag", true),  // DEFAULT_INPUT_OVERLAY_MOUSE_HOLD_TO_DRAG
            mouseHoldMsec = getInt("mouse_hold_msec", 200),  // DEFAULT_INPUT_OVERLAY_MOUSE_HOLD_MSEC
            mouseDoubleTapToDrag = getBoolean("mouse_dtap_to_drag", false),  // DEFAULT_INPUT_OVERLAY_MOUSE_DTAP_TO_DRAG
            mouseDtapMsec = getInt("mouse_dtap_msec", 200),  // DEFAULT_INPUT_OVERLAY_MOUSE_DTAP_MSEC
            showMouseCursor = getBoolean("show_mouse_cursor", false)  // DEFAULT_OVERLAY_SHOW_MOUSE_CURSOR
        )
    }
    
    /**
     * Save advanced settings for a console
     * 
     * @param orientation Optional orientation ("landscape" or "portrait"). If null, saves as global (backward compatibility).
     */
    @JvmStatic
    fun saveAdvancedSettings(
        prefs: android.content.SharedPreferences,
        console: String,
        orientation: String?,
        settings: AdvancedOverlaySettings
    ) {
        val suffix = if (orientation != null) "_$orientation" else ""
        val editor = prefs.edit()
        
        editor.putInt("overlay_${console}_dpad_diagonal_sensitivity$suffix", settings.dpadDiagonalSensitivity)
        editor.putInt("overlay_${console}_abxy_diagonal_sensitivity$suffix", settings.abxyDiagonalSensitivity)
        editor.putInt("overlay_${console}_analog_recenter_zone$suffix", settings.analogRecenterZone)
        editor.putFloat("overlay_${console}_opacity$suffix", settings.opacity)
        editor.putFloat("overlay_${console}_aspect_adjust$suffix", settings.aspectAdjust)
        editor.putBoolean("overlay_${console}_hide_in_menu$suffix", settings.hideInMenu)
        editor.putBoolean("overlay_${console}_behind_menu$suffix", settings.behindMenu)
        editor.putBoolean("overlay_${console}_hide_when_gamepad$suffix", settings.hideWhenGamepadConnected)
        editor.putBoolean("overlay_${console}_hide_when_gamepad_port0_only$suffix", settings.hideWhenGamepadConnectedPort0Only)
        editor.putString("overlay_${console}_show_inputs$suffix", settings.showInputs.name)
        editor.putInt("overlay_${console}_show_inputs_port$suffix", settings.showInputsPort)
        editor.putInt("overlay_${console}_lightgun_port$suffix", settings.lightgunPort)
        editor.putBoolean("overlay_${console}_lightgun_trigger_on_touch$suffix", settings.lightgunTriggerOnTouch)
        editor.putInt("overlay_${console}_lightgun_trigger_delay$suffix", settings.lightgunTriggerDelay)
        editor.putBoolean("overlay_${console}_lightgun_allow_offscreen$suffix", settings.lightgunAllowOffscreen)
        editor.putInt("overlay_${console}_lightgun_two_touch$suffix", settings.lightgunTwoTouchInput)
        editor.putInt("overlay_${console}_lightgun_three_touch$suffix", settings.lightgunThreeTouchInput)
        editor.putInt("overlay_${console}_lightgun_four_touch$suffix", settings.lightgunFourTouchInput)
        editor.putFloat("overlay_${console}_mouse_speed$suffix", settings.mouseSpeed)
        editor.putFloat("overlay_${console}_mouse_swipe_threshold$suffix", settings.mouseSwipeThreshold)
        editor.putBoolean("overlay_${console}_mouse_hold_to_drag$suffix", settings.mouseHoldToDrag)
        editor.putInt("overlay_${console}_mouse_hold_msec$suffix", settings.mouseHoldMsec)
        editor.putBoolean("overlay_${console}_mouse_dtap_to_drag$suffix", settings.mouseDoubleTapToDrag)
        editor.putInt("overlay_${console}_mouse_dtap_msec$suffix", settings.mouseDtapMsec)
        editor.putBoolean("overlay_${console}_show_mouse_cursor$suffix", settings.showMouseCursor)
        
        editor.commit()
    }
    
    /**
     * Check if landscape and portrait settings are identical
     */
    fun checkIfSettingsAreSame(
        prefs: android.content.SharedPreferences,
        console: String
    ): Boolean {
        val landscape = loadAdvancedSettings(prefs, console, "landscape")
        val portrait = loadAdvancedSettings(prefs, console, "portrait")
        
        // Compare all fields
        return landscape.dpadDiagonalSensitivity == portrait.dpadDiagonalSensitivity &&
                landscape.abxyDiagonalSensitivity == portrait.abxyDiagonalSensitivity &&
                landscape.analogRecenterZone == portrait.analogRecenterZone &&
                kotlin.math.abs(landscape.opacity - portrait.opacity) < 0.001f &&
                kotlin.math.abs(landscape.aspectAdjust - portrait.aspectAdjust) < 0.001f &&
                landscape.hideInMenu == portrait.hideInMenu &&
                landscape.behindMenu == portrait.behindMenu &&
                landscape.hideWhenGamepadConnected == portrait.hideWhenGamepadConnected &&
                landscape.hideWhenGamepadConnectedPort0Only == portrait.hideWhenGamepadConnectedPort0Only &&
                landscape.showInputs == portrait.showInputs &&
                landscape.showInputsPort == portrait.showInputsPort &&
                landscape.lightgunPort == portrait.lightgunPort &&
                landscape.lightgunTriggerOnTouch == portrait.lightgunTriggerOnTouch &&
                landscape.lightgunTriggerDelay == portrait.lightgunTriggerDelay &&
                landscape.lightgunAllowOffscreen == portrait.lightgunAllowOffscreen &&
                landscape.lightgunTwoTouchInput == portrait.lightgunTwoTouchInput &&
                landscape.lightgunThreeTouchInput == portrait.lightgunThreeTouchInput &&
                landscape.lightgunFourTouchInput == portrait.lightgunFourTouchInput &&
                kotlin.math.abs(landscape.mouseSpeed - portrait.mouseSpeed) < 0.001f &&
                kotlin.math.abs(landscape.mouseSwipeThreshold - portrait.mouseSwipeThreshold) < 0.001f &&
                landscape.mouseHoldToDrag == portrait.mouseHoldToDrag &&
                landscape.mouseHoldMsec == portrait.mouseHoldMsec &&
                landscape.mouseDoubleTapToDrag == portrait.mouseDoubleTapToDrag &&
                landscape.mouseDtapMsec == portrait.mouseDtapMsec &&
                landscape.showMouseCursor == portrait.showMouseCursor
    }
    
    /**
     * Migrate global settings to per-orientation (one-time migration)
     * Copies global settings to both landscape and portrait if per-orientation keys don't exist
     */
    fun migrateAdvancedSettingsToPerOrientation(
        prefs: android.content.SharedPreferences,
        console: String
    ) {
        // Check if migration already done (per-orientation keys exist)
        if (prefs.contains("overlay_${console}_dpad_diagonal_sensitivity_landscape") ||
            prefs.contains("overlay_${console}_dpad_diagonal_sensitivity_portrait")) {
            return  // Already migrated
        }
        
        // Check if global settings exist
        if (!prefs.contains("overlay_${console}_dpad_diagonal_sensitivity") &&
            !prefs.contains("overlay_${console}_opacity")) {
            return  // No settings to migrate
        }
        
        // Load global settings
        val globalSettings = loadAdvancedSettings(prefs, console, null)
        
        // Copy to both orientations
        saveAdvancedSettings(prefs, console, "landscape", globalSettings)
        saveAdvancedSettings(prefs, console, "portrait", globalSettings)
        
        android.util.Log.i("OverlayPreferenceManager", "Migrated global settings to per-orientation for $console")
    }
    
    /**
     * Sauvegarder un overlay custom browsé via file picker
     * Format: "overlayName/cfgFile" (ex: "flat/psx.cfg")
     */
    fun saveCustomBrowsed(
        prefs: android.content.SharedPreferences,
        console: String,
        customPath: String  // Ex: "flat/psx.cfg"
    ) {
        // Ajouter à la liste des customs browsés
        val existingCustoms = getCustomBrowsedList(prefs, console).toMutableSet()
        existingCustoms.add(customPath)
        
        prefs.edit()
            .putStringSet("overlay_${console}_custom_browsed", existingCustoms)
            .commit()
    }
    
    /**
     * Obtenir la liste des overlays customs browsés via file picker
     */
    fun getCustomBrowsedList(
        prefs: android.content.SharedPreferences,
        console: String
    ): Set<String> {
        return prefs.getStringSet("overlay_${console}_custom_browsed", emptySet()) ?: emptySet()
    }
    
    /**
     * Supprimer un custom de la liste
     */
    fun removeCustomBrowsed(
        prefs: android.content.SharedPreferences,
        console: String,
        customPath: String
    ) {
        val existingCustoms = getCustomBrowsedList(prefs, console).toMutableSet()
        existingCustoms.remove(customPath)
        
        prefs.edit()
            .putStringSet("overlay_${console}_custom_browsed", existingCustoms)
            .commit()
    }
}

/**
 * Configuration turbo hybride pour mobile
 * DEFAULT_TURBO_PERIOD = 6 frames @ 60fps = 10 Hz
 */
data class TurboSettings(
    val enabled: Boolean = false,         // Désactivé par défaut
    val frequency: Int = 10,              // Hz (5-30)
    val dutyCycle: Float = 0.5f,          // 0.1-0.9 (50% par défaut)
    val enabledButtons: Set<String> = setOf("a", "b", "x", "y")  // Boutons avec turbo
)

object TurboPreferenceManager {
    private const val KEY_ENABLED = "turbo_enabled"
    private const val KEY_FREQUENCY = "turbo_frequency"
    private const val KEY_DUTY_CYCLE = "turbo_duty_cycle"
    private const val KEY_ENABLED_BUTTONS = "turbo_enabled_buttons"
    
    fun save(prefs: android.content.SharedPreferences, settings: TurboSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_FREQUENCY, settings.frequency)
            .putFloat(KEY_DUTY_CYCLE, settings.dutyCycle)
            .putStringSet(KEY_ENABLED_BUTTONS, settings.enabledButtons)
            .apply()
    }
    
    fun load(prefs: android.content.SharedPreferences): TurboSettings {
        return TurboSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),  // Désactivé par défaut
            frequency = prefs.getInt(KEY_FREQUENCY, 10),
            dutyCycle = prefs.getFloat(KEY_DUTY_CYCLE, 0.5f),
            enabledButtons = prefs.getStringSet(KEY_ENABLED_BUTTONS, setOf("a", "b", "x", "y")) ?: setOf("a", "b", "x", "y")
        )
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

