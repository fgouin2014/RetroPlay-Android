package com.retroplay.overlay.parser

import android.util.Log
import com.retroplay.overlay.models.*
import java.io.File

/**
 * Parser pour les fichiers .cfg d'overlay RetroArch
 * 
 * Format standard:
 * overlays = 12
 * overlay0_name = "landscape-A"
 * overlay0_normalized = true
 * overlay0_desc8 = "a,0.91667,0.85185,radial,0.05000,0.08889"
 * overlay0_desc8_overlay = img/A.png
 */
class RetroArchOverlayParser {
    
    companion object {
        private const val TAG = "RetroArchOverlayParser"
    }
    
    /**
     * Parse un fichier .cfg RetroArch complet
     * @param cfgFile Le fichier .cfg à parser
     * @return Configuration complète de l'overlay, ou null en cas d'erreur
     */
    fun parseConfig(cfgFile: File): RetroArchOverlayConfig? {
        if (!cfgFile.exists()) {
            Log.e(TAG, "Config file not found: ${cfgFile.absolutePath}")
            return null
        }
        
        try {
            val lines = cfgFile.readLines()
            
            // Lire le nombre total d'overlays
            val totalOverlays = lines.find { it.trim().startsWith("overlays = ") }
                ?.substringAfter("= ")?.trim()?.toIntOrNull() ?: 0
            
            if (totalOverlays == 0) {
                Log.w(TAG, "No overlays found in config file")
                return null
            }
            
            Log.i(TAG, "Parsing config with $totalOverlays overlays")
            
            // Parser chaque overlay
            val layouts = mutableMapOf<String, OverlayLayout>()
            for (i in 0 until totalOverlays) {
                val layout = parseOverlay(lines, i)
                if (layout != null) {
                    layouts[layout.name] = layout
                    Log.d(TAG, "Parsed layout: ${layout.name} with ${layout.buttons.size} buttons")
                }
            }
            
            return RetroArchOverlayConfig(totalOverlays, layouts)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config file", e)
            return null
        }
    }
    
    /**
     * Parse un overlay individuel (ex: overlay0, overlay1, etc.)
     */
    private fun parseOverlay(lines: List<String>, index: Int): OverlayLayout? {
        val prefix = "overlay${index}_"
        
        // Lire le nom de l'overlay
        val name = lines.find { it.trim().startsWith("${prefix}name = ") }
            ?.substringAfter("\"")?.substringBefore("\"")
        
        if (name == null) {
            Log.w(TAG, "No name found for overlay $index")
            return null
        }
        
        // Lire les propriétés
        val fullScreen = lines.find { it.trim().startsWith("${prefix}full_screen = ") }
            ?.substringAfter("= ")?.trim()?.toBoolean() ?: true
        
        val normalized = lines.find { it.trim().startsWith("${prefix}normalized = ") }
            ?.substringAfter("= ")?.trim()?.toBoolean() ?: true
        
        val rangeMod = lines.find { it.trim().startsWith("${prefix}range_mod = ") }
            ?.substringAfter("= ")?.trim()?.toFloatOrNull() ?: 1.0f
        
        val alphaMod = lines.find { it.trim().startsWith("${prefix}alpha_mod = ") }
            ?.substringAfter("= ")?.trim()?.toFloatOrNull() ?: 1.0f
        
        // Lire le nombre de descripteurs de boutons
        val descCount = lines.find { it.trim().startsWith("${prefix}descs = ") }
            ?.substringAfter("= ")?.trim()?.toIntOrNull() ?: 0
        
        // Parser chaque bouton
        val buttons = mutableListOf<OverlayButton>()
        for (descIndex in 0 until descCount) {
            val button = parseButton(lines, prefix, descIndex)
            if (button != null) {
                buttons.add(button)
            }
        }
        
        return OverlayLayout(
            name = name,
            fullScreen = fullScreen,
            normalized = normalized,
            rangeModifier = rangeMod,
            alphaModifier = alphaMod,
            buttons = buttons
        )
    }
    
    /**
     * Parse un descripteur de bouton individuel
     * 
     * Format: overlay0_desc8 = "a,0.91667,0.85185,radial,0.05000,0.08889"
     * Optionnel: overlay0_desc8_overlay = img/A.png
     * Optionnel: overlay0_desc8_next_target = "portrait-A"
     */
    private fun parseButton(lines: List<String>, prefix: String, descIndex: Int): OverlayButton? {
        val descKey = "${prefix}desc${descIndex}"
        
        // Trouver la ligne de définition du bouton
        val descLine = lines.find { it.trim().startsWith("$descKey = ") }
        if (descLine == null) {
            Log.w(TAG, "No definition found for $descKey")
            return null
        }
        
        try {
            // Extraire la valeur entre guillemets
            val descValue = descLine.substringAfter("\"").substringBefore("\"")
            val parts = descValue.split(",").map { it.trim() }
            
            if (parts.size < 4) {
                Log.w(TAG, "Invalid button definition: $descValue")
                return null
            }
            
            // Parser les composants
            val action = parts[0]
            
            // Ignorer seulement les actions complètement vides
            if (action.isBlank()) {
                return null
            }
            
            val x = parts[1].toFloatOrNull() ?: return null
            val y = parts[2].toFloatOrNull() ?: return null
            val shape = when (parts[3].lowercase()) {
                "radial" -> ButtonShape.RADIAL
                "rect" -> ButtonShape.RECT
                else -> {
                    Log.w(TAG, "Unknown shape: ${parts[3]}, defaulting to RADIAL")
                    ButtonShape.RADIAL
                }
            }
            
            // Dimensions (optionnelles)
            val width = if (parts.size > 4) parts[4].toFloatOrNull() ?: 0.05f else 0.05f
            val height = if (parts.size > 5) parts[5].toFloatOrNull() ?: 0.05f else 0.05f
            
            // Image overlay (optionnel)
            val overlayLine = lines.find { it.trim().startsWith("${descKey}_overlay = ") }
            val imagePath = overlayLine?.substringAfter("= ")?.trim()
            
            // Target pour overlay_next (optionnel)
            val nextTargetLine = lines.find { it.trim().startsWith("${descKey}_next_target = ") }
            val nextTarget = nextTargetLine?.substringAfter("\"")?.substringBefore("\"")
            
            // Range modifier pour analog sticks (sensibilité)
            val rangeModLine = lines.find { it.trim().startsWith("${descKey}_range_mod = ") }
            val rangeModifier = rangeModLine?.substringAfter("= ")?.trim()?.toFloatOrNull() ?: 1.0f
            
            // Déterminer le type de bouton
            val buttonType = when (action.lowercase()) {
                "analog_left" -> OverlayButtonType.ANALOG_LEFT
                "analog_right" -> OverlayButtonType.ANALOG_RIGHT
                "dpad_area" -> OverlayButtonType.DPAD_AREA
                "abxy_area" -> OverlayButtonType.ABXY_AREA
                else -> OverlayButtonType.BUTTONS
            }
            
            val button = OverlayButton(
                action = action,
                x = x,
                y = y,
                shape = shape,
                width = width,
                height = height,
                imagePath = imagePath,
                nextTarget = nextTarget,
                type = buttonType,
                rangeModifier = rangeModifier
            )
            
            // Log détaillé pour boutons système
            if (action == "overlay_next" || action == "menu_toggle") {
                Log.d(TAG, "PARSED SYSTEM BUTTON: $descKey | action='$action' | pos=($x,$y) | img='$imagePath' | target='$nextTarget'")
            }
            
            return button
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing button $descKey", e)
            return null
        }
    }
    
    /**
     * Valider une configuration parsée
     * @return true si valide, false sinon
     */
    fun validateConfig(config: RetroArchOverlayConfig): Boolean {
        if (config.layouts.isEmpty()) {
            Log.e(TAG, "Config has no layouts")
            return false
        }
        
        // Vérifier que chaque layout a au moins un bouton
        config.layouts.forEach { (name, layout) ->
            if (layout.buttons.isEmpty()) {
                Log.w(TAG, "Layout $name has no buttons")
                return false
            }
        }
        
        return true
    }
    
    /**
     * Obtenir des statistiques sur la configuration
     */
    fun getConfigStats(config: RetroArchOverlayConfig): String {
        val landscapeCount = config.layouts.keys.count { it.contains("landscape") }
        val portraitCount = config.layouts.keys.count { it.contains("portrait") }
        val hiddenCount = config.layouts.keys.count { it.contains("hidden") }
        
        val totalButtons = config.layouts.values.sumOf { it.buttons.size }
        val avgButtonsPerLayout = if (config.layouts.isNotEmpty()) 
            totalButtons / config.layouts.size else 0
        
        return buildString {
            appendLine("Total layouts: ${config.totalOverlays}")
            appendLine("  - Landscape: $landscapeCount")
            appendLine("  - Portrait: $portraitCount")
            appendLine("  - Hidden: $hiddenCount")
            appendLine("Total buttons: $totalButtons")
            appendLine("Avg buttons/layout: $avgButtonsPerLayout")
        }
    }
}

