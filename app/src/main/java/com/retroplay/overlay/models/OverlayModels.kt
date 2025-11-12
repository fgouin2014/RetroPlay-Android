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
    val buttons: List<OverlayButton>,
    val rect: OverlayRect? = null,        // overlay0_rect (custom positioning)
    val backgroundImage: String? = null,  // overlay0_overlay (image de fond)
    val aspectRatio: Float? = null,       // overlay0_aspect_ratio
    val blockXSeparation: Boolean = false, // overlay0_block_x_separation
    val blockYSeparation: Boolean = false, // overlay0_block_y_separation
    val autoXSeparation: Boolean = true,   // overlay0_auto_x_separation (défaut true)
    val autoYSeparation: Boolean = true    // overlay0_auto_y_separation (défaut true)
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
    val yHitboxOverride: Float? = null
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
     */
    val rangeXHitbox: Float = (width * reachRight + width * reachLeft) / 2.0f
    
    /**
     * Calcul de la TAILLE de la hitbox réelle (range_y_hitbox) en appliquant reach_*
     * Identique à RetroArch: range_y_hitbox = (range_y * reach_down + range_y * reach_up) / 2.0
     */
    val rangeYHitbox: Float = (height * reachDown + height * reachUp) / 2.0f
}

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
     */
    fun loadAdvancedSettings(
        prefs: android.content.SharedPreferences,
        console: String
    ): AdvancedOverlaySettings {
        val showInputsString = prefs.getString("overlay_${console}_show_inputs", "NONE") ?: "NONE"
        val showInputsMode = try {
            ShowInputsMode.valueOf(showInputsString)
        } catch (e: IllegalArgumentException) {
            ShowInputsMode.NONE
        }
        
        return AdvancedOverlaySettings(
            // DEFAULT VALUES from RetroArch config.def.h (c:\repos\RetroArch-master\config.def.h)
            dpadDiagonalSensitivity = prefs.getInt("overlay_${console}_dpad_diagonal_sensitivity", 80),  // DEFAULT_OVERLAY_DPAD_DIAGONAL_SENSITIVITY
            abxyDiagonalSensitivity = prefs.getInt("overlay_${console}_abxy_diagonal_sensitivity", 50),  // DEFAULT_OVERLAY_ABXY_DIAGONAL_SENSITIVITY
            analogRecenterZone = prefs.getInt("overlay_${console}_analog_recenter_zone", 0),  // DEFAULT_INPUT_OVERLAY_ANALOG_RECENTER_ZONE
            opacity = prefs.getFloat("overlay_${console}_opacity", 0.7f),  // DEFAULT_INPUT_OVERLAY_OPACITY
            aspectAdjust = prefs.getFloat("overlay_${console}_aspect_adjust", 0.0f),  // DEFAULT_INPUT_OVERLAY_ASPECT_ADJUST_*
            hideInMenu = prefs.getBoolean("overlay_${console}_hide_in_menu", true),  // DEFAULT_OVERLAY_HIDE_IN_MENU
            behindMenu = prefs.getBoolean("overlay_${console}_behind_menu", false),  // DEFAULT_OVERLAY_BEHIND_MENU
            hideWhenGamepadConnected = prefs.getBoolean("overlay_${console}_hide_when_gamepad", false),  // DEFAULT_OVERLAY_HIDE_WHEN_GAMEPAD_CONNECTED
            showInputs = showInputsMode,
            showInputsPort = prefs.getInt("overlay_${console}_show_inputs_port", 0),  // DEFAULT_OVERLAY_SHOW_INPUTS_PORT
            lightgunPort = prefs.getInt("overlay_${console}_lightgun_port", -1),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_PORT (-1 = all ports)
            lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", true),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_ON_TOUCH
            lightgunTriggerDelay = prefs.getInt("overlay_${console}_lightgun_trigger_delay", 1),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY
            lightgunAllowOffscreen = prefs.getBoolean("overlay_${console}_lightgun_allow_offscreen", true),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_ALLOW_OFFSCREEN
            lightgunTwoTouchInput = prefs.getInt("overlay_${console}_lightgun_two_touch", 0),  // DEFAULT_INPUT_OVERLAY_LIGHTGUN_MULTI_TOUCH_INPUT
            lightgunThreeTouchInput = prefs.getInt("overlay_${console}_lightgun_three_touch", 0),
            lightgunFourTouchInput = prefs.getInt("overlay_${console}_lightgun_four_touch", 0),
            mouseSpeed = prefs.getFloat("overlay_${console}_mouse_speed", 1.0f),  // DEFAULT_INPUT_OVERLAY_MOUSE_SPEED
            mouseSwipeThreshold = try {
                prefs.getFloat("overlay_${console}_mouse_swipe_threshold", 1.0f)
            } catch (e: ClassCastException) {
                // Migration: old value was Int, convert to Float
                prefs.getInt("overlay_${console}_mouse_swipe_threshold", 1).toFloat()
            },  // DEFAULT_INPUT_OVERLAY_MOUSE_SWIPE_THRESHOLD
            mouseHoldToDrag = prefs.getBoolean("overlay_${console}_mouse_hold_to_drag", true),  // DEFAULT_INPUT_OVERLAY_MOUSE_HOLD_TO_DRAG
            mouseHoldMsec = prefs.getInt("overlay_${console}_mouse_hold_msec", 200),  // DEFAULT_INPUT_OVERLAY_MOUSE_HOLD_MSEC
            mouseDoubleTapToDrag = prefs.getBoolean("overlay_${console}_mouse_dtap_to_drag", false),  // DEFAULT_INPUT_OVERLAY_MOUSE_DTAP_TO_DRAG
            mouseDtapMsec = prefs.getInt("overlay_${console}_mouse_dtap_msec", 200),  // DEFAULT_INPUT_OVERLAY_MOUSE_DTAP_MSEC
            showMouseCursor = prefs.getBoolean("overlay_${console}_show_mouse_cursor", false)  // DEFAULT_OVERLAY_SHOW_MOUSE_CURSOR
        )
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

