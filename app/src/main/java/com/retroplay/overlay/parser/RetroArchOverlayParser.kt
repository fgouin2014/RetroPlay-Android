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
            // Lire le fichier avec support #include (jusqu'à 16 niveaux)
            val lines = readConfigWithIncludes(cfgFile, 0)
            
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
     * Lecture récursive d'un fichier .cfg avec support des directives #include
     * Compatibilité avec RetroArch (MAX_INCLUDE_DEPTH = 16)
     */
    private fun readConfigWithIncludes(file: File, depth: Int): List<String> {
        if (depth >= 16) return emptyList()
        val baseDir = file.parentFile
        val result = mutableListOf<String>()
        val raw = try { file.readLines() } catch (e: Exception) { emptyList<String>() }
        for (line in raw) {
            // Détecter commentaire/directive (#...)
            if (line.trim().startsWith("#")) {
                val comment = line.trim().substring(1).trim()
                if (comment.startsWith("include ")) {
                    // extraire valeur après include (peut être entre guillemets)
                    val value = comment.removePrefix("include").trim()
                    val includePath = value.trim().trim('"')
                    val includeFile = File(baseDir, includePath)
                    if (includeFile.exists()) {
                        result += readConfigWithIncludes(includeFile, depth + 1)
                    } else {
                        Log.w(TAG, "Include file not found: ${includeFile.absolutePath}")
                    }
                }
                // ignorer les autres directives/commentaires
                continue
            }
            result += line
        }
        return result
    }
    
    /**
     * Parse un overlay individuel (ex: overlay0, overlay1, etc.)
     */
    private fun parseOverlay(lines: List<String>, index: Int): OverlayLayout? {
        val prefix = "overlay${index}_"
        
        // Lire le nom de l'overlay (si absent, utiliser "overlay<index>")
        val name = lines.find { it.trim().startsWith("${prefix}name = ") }
            ?.substringAfter("\"")?.substringBefore("\"")
            ?: "overlay$index"  // Fallback pour les overlays sans nom (ex: nes-small)
        
        if (name.isEmpty()) {
            Log.w(TAG, "Empty name for overlay $index, using fallback")
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
        
        // Parser overlay0_rect (custom positioning)
        val rectLine = lines.find { it.trim().startsWith("${prefix}rect = ") }
        val rect = if (rectLine != null) {
            val rectValue = rectLine.substringAfter("\"").substringBefore("\"")
            val rectParts = rectValue.split(",").map { it.trim().toFloatOrNull() ?: 0f }
            if (rectParts.size >= 4) {
                com.retroplay.overlay.models.OverlayRect(
                    x = rectParts[0],
                    y = rectParts[1],
                    width = rectParts[2],
                    height = rectParts[3]
                )
            } else null
        } else null
        
        // Parser overlay0_overlay (background image)
        val backgroundImage = lines.find { it.trim().startsWith("${prefix}overlay = ") }
            ?.substringAfter("= ")?.trim()?.removePrefix("\"")?.removeSuffix("\"")
        
        // Parser aspect_ratio
        val aspectRatio = lines.find { it.trim().startsWith("${prefix}aspect_ratio = ") }
            ?.substringAfter("= ")?.trim()?.toFloatOrNull()
        
        // Parser separation flags
        val blockXSeparation = lines.find { it.trim().startsWith("${prefix}block_x_separation = ") }
            ?.substringAfter("= ")?.trim()?.toBoolean() ?: false
        val blockYSeparation = lines.find { it.trim().startsWith("${prefix}block_y_separation = ") }
            ?.substringAfter("= ")?.trim()?.toBoolean() ?: false
        val autoXSeparation = lines.find { it.trim().startsWith("${prefix}auto_x_separation = ") }
            ?.substringAfter("= ")?.trim()?.toBoolean() ?: true
        val autoYSeparation = lines.find { it.trim().startsWith("${prefix}auto_y_separation = ") }
            ?.substringAfter("= ")?.trim()?.toBoolean() ?: true
        
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
            buttons = buttons,
            rect = rect,
            backgroundImage = backgroundImage,
            aspectRatio = aspectRatio,
            blockXSeparation = blockXSeparation,
            blockYSeparation = blockYSeparation,
            autoXSeparation = autoXSeparation,
            autoYSeparation = autoYSeparation
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
            val imagePath = overlayLine?.substringAfter("= ")?.trim()?.removePrefix("\"")?.removeSuffix("\"")
            
            // Target pour overlay_next (optionnel)
            val nextTargetLine = lines.find { it.trim().startsWith("${descKey}_next_target = ") }
            val nextTarget = nextTargetLine?.substringAfter("\"")?.substringBefore("\"")
            
            // Range modifier pour analog sticks (sensibilité)
            val rangeModLine = lines.find { it.trim().startsWith("${descKey}_range_mod = ") }
            val rangeModifier = rangeModLine?.substringAfter("= ")?.trim()?.toFloatOrNull() ?: 1.0f

            // Extras RetroArch: alpha_mod, exclusive, range_mod_exclusive, movable, reach_*
            val alphaMod = lines.find { it.trim().startsWith("${descKey}_alpha_mod = ") }
                ?.substringAfter("= ")?.trim()?.toFloatOrNull()
            val exclusive = lines.find { it.trim().startsWith("${descKey}_exclusive = ") }
                ?.substringAfter("= ")?.trim()?.toBooleanStrictOrNull() ?: false
            val rangeModExclusive = lines.find { it.trim().startsWith("${descKey}_range_mod_exclusive = ") }
                ?.substringAfter("= ")?.trim()?.toBooleanStrictOrNull() ?: false
            val movable = lines.find { it.trim().startsWith("${descKey}_movable = ") }
                ?.substringAfter("= ")?.trim()?.toBooleanStrictOrNull() ?: false

            fun readFloat(key: String, default: Float): Float =
                lines.find { it.trim().startsWith("${descKey}_${key} = ") }
                    ?.substringAfter("= ")?.trim()?.toFloatOrNull() ?: default

            val reachX = readFloat("reach_x", 1.0f)
            val reachY = readFloat("reach_y", 1.0f)
            val reachUp = readFloat("reach_up", reachY)
            val reachDown = readFloat("reach_down", reachY)
            val reachLeft = readFloat("reach_left", reachX)
            val reachRight = readFloat("reach_right", reachX)
            
            // saturate_pct pour analog sticks (dead zone custom)
            val saturatePct = readFloat("saturate_pct", 1.0f)
            
            // Déterminer le type de bouton
            val buttonType = when (action.lowercase()) {
                "analog_left" -> OverlayButtonType.ANALOG_LEFT
                "analog_right" -> OverlayButtonType.ANALOG_RIGHT
                "dpad_area" -> OverlayButtonType.DPAD_AREA
                "abxy_area" -> OverlayButtonType.ABXY_AREA
                else -> OverlayButtonType.BUTTONS
            }
            
            // Parser 8-way custom mappings pour dpad_area et abxy_area
            fun readMapping(key: String): String? =
                lines.find { it.trim().startsWith("${descKey}_${key} = ") }
                    ?.substringAfter("= ")?.trim()
            
            val eightwayUp = readMapping("up")
            val eightwayDown = readMapping("down")
            val eightwayLeft = readMapping("left")
            val eightwayRight = readMapping("right")
            val eightwayUpLeft = readMapping("up_left")
            val eightwayUpRight = readMapping("up_right")
            val eightwayDownLeft = readMapping("down_left")
            val eightwayDownRight = readMapping("down_right")
            
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
                rangeModifier = rangeModifier,
                alphaModifier = alphaMod,
                exclusive = exclusive,
                rangeModExclusive = rangeModExclusive,
                movable = movable,
                reachUp = reachUp,
                reachDown = reachDown,
                reachLeft = reachLeft,
                reachRight = reachRight,
                analogSaturatePct = saturatePct,
                modX = x - width,
                modY = y - height,
                modW = 2f * width,
                modH = 2f * height,
                eightwayUp = eightwayUp,
                eightwayDown = eightwayDown,
                eightwayLeft = eightwayLeft,
                eightwayRight = eightwayRight,
                eightwayUpLeft = eightwayUpLeft,
                eightwayUpRight = eightwayUpRight,
                eightwayDownLeft = eightwayDownLeft,
                eightwayDownRight = eightwayDownRight
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

