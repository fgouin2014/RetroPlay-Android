package com.retroplay.overlay.parser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.retroplay.overlay.models.*
import java.io.File

/**
 * Callback pour obtenir les dimensions d'une image de fond
 * Utilisé pour la conversion pixel → normalized si !normalized
 */
typealias ImageDimensionsCallback = (imagePath: String, overlayName: String?) -> Pair<Int, Int>?

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
     * @param overlayName Nom de l'overlay (pour charger les images, ex: "flat-nes")
     * @param imageDimensionsCallback Callback pour obtenir les dimensions d'une image (pour conversion pixel → normalized)
     * @return Configuration complète de l'overlay, ou null en cas d'erreur
     */
    fun parseConfig(
        cfgFile: File,
        overlayName: String? = null,
        imageDimensionsCallback: ImageDimensionsCallback? = null
    ): RetroArchOverlayConfig? {
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
                val layout = parseOverlay(lines, i, overlayName, imageDimensionsCallback)
                if (layout != null) {
                    layouts[layout.name] = layout
                    Log.d(TAG, "Parsed layout: ${layout.name} with ${layout.buttons.size} buttons")
                }
            }
            
            // Résoudre les targets (next_target → next_index) après chargement complet
            // Compatible avec RetroArch task_overlay_resolve_targets() (ligne 533-561)
            resolveTargets(layouts, totalOverlays)
            
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
     * 
     * @param lines Lignes du fichier .cfg
     * @param index Index de l'overlay (0, 1, 2, etc.)
     * @param overlayName Nom de l'overlay (pour charger les images)
     * @param imageDimensionsCallback Callback pour obtenir les dimensions d'une image (pour conversion pixel → normalized)
     */
    private fun parseOverlay(
        lines: List<String>,
        index: Int,
        overlayName: String? = null,
        imageDimensionsCallback: ImageDimensionsCallback? = null
    ): OverlayLayout? {
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
        
        // Item P1 #12: Conversion Normalized vs Pixel
        // RetroArch task_overlay.c lignes 368-378: Si !normalized, utiliser width_mod/height_mod
        // width_mod = 1.0f / width, height_mod = 1.0f / height (dimensions image de fond)
        // Puis multiplier x, y, range_x, range_y par les mods pour convertir pixel → normalized
        var widthMod = 1.0f
        var heightMod = 1.0f
        
        if (!normalized && backgroundImage != null && imageDimensionsCallback != null) {
            // Obtenir les dimensions de l'image de fond pour conversion pixel → normalized
            val dimensions = imageDimensionsCallback(backgroundImage, overlayName)
            if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
                widthMod = 1.0f / dimensions.first
                heightMod = 1.0f / dimensions.second
                Log.d(TAG, "Conversion pixel → normalized for overlay $index: image=${dimensions.first}x${dimensions.second}, mods=($widthMod, $heightMod)")
            } else {
                Log.w(TAG, "Cannot get dimensions for background image '$backgroundImage', assuming normalized coordinates")
                // Si on ne peut pas obtenir les dimensions, on assume que c'est déjà normalized (fallback)
            }
        }
        
        // Parser aspect_ratio
        var aspectRatio = lines.find { it.trim().startsWith("${prefix}aspect_ratio = ") }
            ?.substringAfter("= ")?.trim()?.toFloatOrNull()
        
        // Auto-détection aspect ratio depuis name (compatible RetroArch task_overlay.c lignes 1995-2000)
        // Si aspect_ratio <= 0.0f ou null, auto-détecter depuis name
        if (aspectRatio == null || aspectRatio <= 0.0f) {
            aspectRatio = if (name.contains("portrait", ignoreCase = true)) {
                0.5625f  // 1 / 16:9 (portrait)
            } else {
                1.7777778f  // 16:9 (landscape par défaut)
            }
            Log.d(TAG, "Auto-detected aspect ratio for overlay '$name': $aspectRatio (${if (aspectRatio == 0.5625f) "portrait" else "landscape"})")
        }
        
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
        
        // Parser chaque bouton avec width_mod/height_mod pour conversion si !normalized
        val buttons = mutableListOf<OverlayButton>()
        for (descIndex in 0 until descCount) {
            val button = parseButton(lines, prefix, descIndex, normalized, widthMod, heightMod)
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
     * 
     * @param lines Lignes du fichier .cfg
     * @param prefix Préfixe de l'overlay (ex: "overlay0_")
     * @param descIndex Index du descripteur (0, 1, 2, etc.)
     * @param normalized Flag indiquant si les coordonnées sont normalisées (0.0-1.0) ou en pixels
     * @param widthMod Multiplicateur pour conversion pixel → normalized en X (1.0f si normalized)
     * @param heightMod Multiplicateur pour conversion pixel → normalized en Y (1.0f si normalized)
     * 
     * Compatible RetroArch task_overlay.c lignes 377-378:
     *   desc->x = (float)strtod(x, NULL) * width_mod;
     *   desc->y = (float)strtod(y, NULL) * height_mod;
     */
    private fun parseButton(
        lines: List<String>,
        prefix: String,
        descIndex: Int,
        normalized: Boolean,
        widthMod: Float = 1.0f,
        heightMod: Float = 1.0f
    ): OverlayButton? {
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
            
            // Validation stricte (compatible RetroArch task_overlay.c lignes 368-379)
            // Format requis: "action,x,y,shape,range_x,range_y" (6 tokens minimum)
            if (parts.size < 6) {
                Log.e(TAG, "Invalid button definition (expected 6+ tokens, got ${parts.size}): $descValue")
                return null
            }
            
            // Validation des valeurs numériques
            val xRaw = parts[1].toFloatOrNull()
            val yRaw = parts[2].toFloatOrNull()
            val rangeX = parts[4].toFloatOrNull()
            val rangeY = parts[5].toFloatOrNull()
            
            if (xRaw == null || yRaw == null || rangeX == null || rangeY == null) {
                Log.e(TAG, "Invalid numeric values in button definition: x=$xRaw, y=$yRaw, range_x=$rangeX, range_y=$rangeY")
                return null
            }
            
            // Validation des valeurs (doivent être positives pour range_x/range_y)
            if (rangeX <= 0f || rangeY <= 0f) {
                Log.e(TAG, "Invalid range values (must be > 0): range_x=$rangeX, range_y=$rangeY")
                return null
            }
            
            // Parser les composants
            val action = parts[0]
            
            // Ignorer seulement les actions complètement vides
            if (action.isBlank()) {
                return null
            }
            
            // Parser format "a|b|c" en bitmask (compatible RetroArch)
            // RetroArch utilise input_bits_t (256 bits) pour button_mask
            // On stocke comme Set<Int> d'IDs RetroPad (RETRO_DEVICE_ID_JOYPAD_*)
            val buttonMask = RetroArchButtonMapping.parseButtonMask(action)
            
            // Parser coordonnées et appliquer conversion pixel → normalized si nécessaire
            // Compatible RetroArch task_overlay.c lignes 377-378:
            //   desc->x = (float)strtod(x, NULL) * width_mod;
            //   desc->y = (float)strtod(y, NULL) * height_mod;
            // Note: xRaw, yRaw, rangeX, rangeY déjà validés ci-dessus
            val x = xRaw * widthMod
            val y = yRaw * heightMod
            val shape = when (parts[3].lowercase()) {
                "radial" -> ButtonShape.RADIAL
                "rect" -> ButtonShape.RECT
                else -> {
                    Log.w(TAG, "Unknown shape: ${parts[3]}, defaulting to RADIAL")
                    ButtonShape.RADIAL
                }
            }
            
            // Dimensions - aussi converties pixel → normalized si nécessaire
            // Compatible RetroArch task_overlay.c lignes 423-424:
            //   desc->range_x = (float)strtod(elem4, NULL) * width_mod;
            //   desc->range_y = (float)strtod(elem5, NULL) * height_mod;
            // Note: rangeX et rangeY déjà validés ci-dessus (doivent être > 0)
            val width = rangeX * widthMod
            val height = rangeY * heightMod
            
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
            // Format peut être "a|b|c" (bitmask) ou simple "a"
            fun readMapping(key: String): String? =
                lines.find { it.trim().startsWith("${descKey}_${key} = ") }
                    ?.substringAfter("= ")?.trim()
            
            // Parser les mappings 8-way (peuvent être au format "a|b|c")
            val eightwayUp = readMapping("up")
            val eightwayDown = readMapping("down")
            val eightwayLeft = readMapping("left")
            val eightwayRight = readMapping("right")
            val eightwayUpLeft = readMapping("up_left")
            val eightwayUpRight = readMapping("up_right")
            val eightwayDownLeft = readMapping("down_left")
            val eightwayDownRight = readMapping("down_right")
            
            // Parser les bitmasks pour les 8-way mappings (compatible RetroArch)
            val eightwayUpMask = eightwayUp?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayDownMask = eightwayDown?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayLeftMask = eightwayLeft?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayRightMask = eightwayRight?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayUpLeftMask = eightwayUpLeft?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayUpRightMask = eightwayUpRight?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayDownLeftMask = eightwayDownLeft?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            val eightwayDownRightMask = eightwayDownRight?.let { RetroArchButtonMapping.parseButtonMask(it) } ?: emptySet()
            
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
                buttonMask = buttonMask,  // Bitmask parsé depuis "a|b|c"
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
    
    /**
     * Résoudre les targets (next_target → next_index) après chargement complet
     * Compatible avec RetroArch task_overlay_resolve_targets() (ligne 533-561)
     * 
     * Algorithme RetroArch:
     * 1. Pour chaque overlay, parcourir tous les descs
     * 2. Si desc->next_index_name est non vide, chercher l'overlay correspondant
     * 3. Mettre à jour desc->next_index avec l'index trouvé (ou (idx + 1) % len si vide)
     * 
     * @param layouts Map des layouts parsés (sera modifié pour résoudre les nextIndex)
     * @param totalOverlays Nombre total d'overlays
     */
    private fun resolveTargets(layouts: MutableMap<String, OverlayLayout>, totalOverlays: Int) {
        // Créer une liste ordonnée des layouts (pour calculer les index)
        val layoutList = layouts.values.toList()
        
        // Créer un mapping nom → index pour recherche rapide
        val nameToIndex = layoutList.mapIndexed { index, layout -> layout.name to index }.toMap()
        
        // Parcourir tous les layouts
        for ((layoutIndex, layout) in layoutList.withIndex()) {
            // Parcourir tous les boutons de ce layout
            val updatedButtons = layout.buttons.map { button ->
                // Si le bouton a un nextTarget, le résoudre
                if (button.nextTarget != null && button.nextTarget!!.isNotBlank()) {
                    // Chercher l'index de l'overlay cible (compatible RetroArch task_overlay_find_index)
                    val targetIndex = nameToIndex[button.nextTarget]
                    
                    if (targetIndex == null) {
                        // Overlay cible non trouvé (compatible RetroArch ligne 549-554)
                        Log.e(TAG, "[Overlay] Couldn't find overlay called: \"${button.nextTarget}\"")
                        // Garder nextIndex = null pour indiquer l'erreur
                        button.copy(nextIndex = null)
                    } else {
                        // Résoudre le target en index
                        button.copy(nextIndex = targetIndex)
                    }
                } else {
                    // Pas de target spécifié, utiliser (idx + 1) % len comme défaut (compatible RetroArch ligne 543)
                    val defaultNextIndex = (layoutIndex + 1) % totalOverlays
                    button.copy(nextIndex = defaultNextIndex)
                }
            }
            
            // Mettre à jour le layout avec les boutons résolus
            layouts[layout.name] = layout.copy(buttons = updatedButtons)
        }
        
        Log.d(TAG, "Resolved targets for ${layoutList.size} layouts")
    }
}

