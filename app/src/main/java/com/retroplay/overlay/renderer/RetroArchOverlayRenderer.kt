package com.retroplay.overlay.renderer

import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.retroplay.overlay.assets.OverlayAssetManager
import com.retroplay.overlay.models.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Composable principal pour afficher un overlay RetroArch complet
 * 
 * @param layout Le layout d'overlay à afficher
 * @param overlayName Nom de l'overlay (pour charger les images)
 * @param assetManager Manager pour charger les assets
 * @param onButtonPress Callback quand un bouton est pressé
 * @param onButtonRelease Callback quand un bouton est relâché
 * @param onLayoutSwitch Callback pour changer de layout (overlay_next)
 */
/**
 * État d'un stick analogique
 */
data class AnalogStickState(
    val x: Float = 0f,    // -1.0 à 1.0
    val y: Float = 0f,    // -1.0 à 1.0
    val pointerId: Int? = null,  // ID du doigt qui contrôle ce stick
    val isActivated: Boolean = false,  // true si le stick a été touché (permet hitbox étendue)
    val visualOffsetX: Float = 0f,  // Offset visuel pour movable (pixels)
    val visualOffsetY: Float = 0f,   // Offset visuel pour movable (pixels)
    val recenterOffsetX: Float = 0f,  // Offset du centre si recentré (pixels)
    val recenterOffsetY: Float = 0f   // Offset du centre si recentré (pixels)
)

/**
 * Viewport calculé pour un overlay basé sur aspect_ratio et aspect_adjust
 * Compatible RetroArch input_overlay_calculate_viewport()
 */
data class OverlayViewport(
    val x: Float,      // Position X du viewport (pixels)
    val y: Float,      // Position Y du viewport (pixels)
    val width: Float,  // Largeur du viewport (pixels)
    val height: Float  // Hauteur du viewport (pixels)
)

/**
 * Calcule le viewport overlay basé sur aspect_ratio et aspect_adjust
 * Compatible RetroArch input_overlay_calculate_viewport() (lignes 2641-2680)
 * 
 * @param aspectRatio Aspect ratio du layout (overlay0_aspect_ratio) ou null
 * @param aspectAdjust Ajustement aspect (-0.5 à 0.5, 0.0 = pas d'ajustement)
 * @param screenWidth Largeur de l'écran (pixels)
 * @param screenHeight Hauteur de l'écran (pixels)
 * @return Viewport calculé (peut être tout l'écran si aspectRatio == null)
 */
fun calculateOverlayViewport(
    aspectRatio: Float?,
    aspectAdjust: Float,
    screenWidth: Int,
    screenHeight: Int
): OverlayViewport {
    // Si pas d'aspect ratio défini, utiliser tout l'écran
    if (aspectRatio == null || aspectRatio <= 0.0f) {
        return OverlayViewport(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
    }
    
    // Appliquer aspect_adjust (compatible RetroArch ligne 2680-2715)
    // aspect_adjust modifie l'aspect ratio effectif: +0.1 = 10% plus large, -0.1 = 10% plus étroit
    val effectiveAspect = aspectRatio * (1.0f + aspectAdjust)
    
    val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
    
    return if (screenAspect > effectiveAspect) {
        // Écran plus large que l'overlay: letterbox vertical (barres noires haut/bas)
        val viewportHeight = screenHeight.toFloat()
        val viewportWidth = viewportHeight * effectiveAspect
        val viewportX = (screenWidth - viewportWidth) / 2.0f
        OverlayViewport(viewportX, 0f, viewportWidth, viewportHeight)
    } else {
        // Écran plus haut que l'overlay: pillarbox horizontal (barres noires gauche/droite)
        val viewportWidth = screenWidth.toFloat()
        val viewportHeight = viewportWidth / effectiveAspect
        val viewportY = (screenHeight - viewportHeight) / 2.0f
        OverlayViewport(0f, viewportY, viewportWidth, viewportHeight)
    }
}

@Composable
fun RetroArchOverlayScreen(
    layout: OverlayLayout,
    overlayName: String,
    assetManager: OverlayAssetManager,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit,
    onMenuToggle: () -> Unit = {},
    availableLayouts: List<String> = emptyList(),  // Liste des layouts disponibles pour navigation cyclique
    onAnalogMove: (String, Float, Float) -> Unit = { _, _, _ -> },  // Callback pour analog sticks (action, x, y)
    onHotkey: (String) -> Unit = {},  // Callback pour hotkeys RetroArch
    onHotkeyChange: (String, Boolean) -> Unit = { _, _ -> },
    onLightgunAction: (String) -> Unit = {},  // Callback pour actions lightgun (gun_*)
    showDebug: Boolean = false,
    swapAnalogSticks: Boolean = false,
    invertAnalogLeftY: Boolean = false,
    invertAnalogRightY: Boolean = false,
    overlayScale: Float = 1.0f,            // Échelle globale (0.5-1.5)
    overlayXOffset: Float = 0.0f,          // Décalage X (-0.2 à 0.2)
    overlayYOffset: Float = 0.0f,          // Décalage Y (-0.2 à 0.2)
    overlayXSeparation: Float = 0.0f,      // Séparation interne X (-0.2 à 0.2)
    overlayYSeparation: Float = 0.0f,      // Séparation interne Y (-0.2 à 0.2)
    overlayOpacity: Float = 1.0f,          // Opacité globale (0.0-1.0)
    dpadDiagonalSensitivity: Int = 50,     // Sensibilité diagonales D-pad (0-100)
    abxyDiagonalSensitivity: Int = 50,     // Sensibilité diagonales ABXY (0-100)
    showInputsMode: com.retroplay.overlay.models.ShowInputsMode = com.retroplay.overlay.models.ShowInputsMode.NONE,
    hideWhenGamepadConnected: Boolean = false,
    analogRecenterZone: Int = 0,           // Recentrage analog sticks (0-100)
    isZapperGame: Boolean = false,         // Mode Zapper: ne consommer QUE les touches sur boutons
    aspectAdjust: Float = 0.0f,            // Ajustement aspect ratio (-0.5 à 0.5, 0.0 = pas d'ajustement)
    modifier: Modifier = Modifier
) {
    val TAG = "RetroArchOverlay"
    
    // Détecter si un gamepad physique est connecté
    val isGamepadConnected = remember {
        android.view.InputDevice.getDeviceIds().any { deviceId ->
            val device = android.view.InputDevice.getDevice(deviceId)
            device != null && (device.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
        }
    }
    
    // Si hideWhenGamepadConnected est activé et qu'un gamepad est connecté, ne rien afficher
    if (hideWhenGamepadConnected && isGamepadConnected) {
        Log.i(TAG, "Gamepad connected, hiding overlay (hideWhenGamepadConnected=true)")
        return
    }
    
    // Appliquer scale/offset/separation à tous les boutons (pré-calcul pour éviter répétition)
    val scaledLayout = remember(layout, overlayScale, overlayXOffset, overlayYOffset, overlayXSeparation, overlayYSeparation) {
        applyScaleAndOffset(layout, overlayScale, overlayXOffset, overlayYOffset, overlayXSeparation, overlayYSeparation)
    }
    
    // Taille de l'écran
    var screenSize by remember { mutableStateOf(IntSize(1920, 1080)) }
    
    // Calculer viewport overlay basé sur aspect_ratio et aspect_adjust
    // Compatible RetroArch input_overlay_calculate_viewport()
    val viewport = remember(layout.aspectRatio, aspectAdjust, screenSize) {
        calculateOverlayViewport(
            aspectRatio = layout.aspectRatio,
            aspectAdjust = aspectAdjust,
            screenWidth = screenSize.width,
            screenHeight = screenSize.height
        ).also {
            Log.d(TAG, "Viewport calculated: x=${it.x} y=${it.y} w=${it.width} h=${it.height} (aspectRatio=${layout.aspectRatio}, aspectAdjust=$aspectAdjust)")
        }
    }
    
    // État des boutons (tracking multi-touch)
    val pressedButtons = remember { mutableStateMapOf<Int, Set<OverlayButton>>() }
    
    // Touch masks par bouton: Map<OverlayButton, Set<pointerId>>
    // Compatible RetroArch: desc->touch_mask (masque des touches actives pour ce bouton)
    // Permet de maintenir les touches actives même quand le doigt bouge entre zones qui se chevauchent
    val buttonTouchMasks = remember { mutableStateMapOf<OverlayButton, MutableSet<Int>>() }
    
    // État des deltas pour boutons déplaçables (movable)
    // Map<buttonIndex, Pair<deltaX, deltaY>> - delta en coordonnées normalisées
    val movableButtonDeltas = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    
    // P3: Charger positions sauvegardées pour boutons déplaçables au démarrage
    val context = LocalContext.current
    LaunchedEffect(layout.name) {
        val prefs = context.getSharedPreferences("overlay_prefs", android.content.Context.MODE_PRIVATE)
        
        scaledLayout.buttons.forEachIndexed { index, button ->
            if (button.movable && button.type != OverlayButtonType.ANALOG_LEFT && button.type != OverlayButtonType.ANALOG_RIGHT) {
                val keyX = "overlay_movable_${layout.name}_${button.action}_delta_x"
                val keyY = "overlay_movable_${layout.name}_${button.action}_delta_y"
                val savedDeltaX = prefs.getFloat(keyX, 0f)
                val savedDeltaY = prefs.getFloat(keyY, 0f)
                
                if (savedDeltaX != 0f || savedDeltaY != 0f) {
                    movableButtonDeltas[index] = Pair(savedDeltaX, savedDeltaY)
                    Log.d(TAG, "[MOVABLE] Loaded saved position for '${button.action}' in layout '${layout.name}': ($savedDeltaX, $savedDeltaY)")
                }
            }
        }
    }
    
    // État des analog sticks
    val analogLeftState = remember { mutableStateOf(AnalogStickState()) }
    val analogRightState = remember { mutableStateOf(AnalogStickState()) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                screenSize = size
                Log.d(TAG, "Screen size: ${size.width}x${size.height}")
                Log.d(TAG, "Layout: ${scaledLayout.name} | Buttons: ${scaledLayout.buttons.size} | RangeMod: ${scaledLayout.rangeModifier} | AlphaMod: ${scaledLayout.alphaModifier} | Scale: $overlayScale | Offset: ($overlayXOffset, $overlayYOffset)")
                
                // DEBUG: Afficher TOUS les boutons système dans le layout
                val systemButtons = scaledLayout.buttons.filter { 
                    it.action.startsWith("overlay_next") || it.action == "menu_toggle"
                }
                Log.w(TAG, "🔍 SYSTEM BUTTONS IN LAYOUT: ${systemButtons.size} total")
                systemButtons.forEach { btn ->
                    Log.w(TAG, "  → action='${btn.action}' | pos=(${btn.x},${btn.y}) | size=(${btn.width},${btn.height}) | img='${btn.imagePath}' | target='${btn.nextTarget}'")
                }
            }
            .pointerInteropFilter { event ->
                // CRITIQUE: Retourner true SEULEMENT si un bouton est touché
                // Sinon retourner false pour laisser passer au Zapper en dessous
                val handled = handleOverlayTouch(
                    event = event,
                    layout = scaledLayout,
                    screenSize = screenSize,
                    viewport = viewport,
                    pressedButtons = pressedButtons,
                    buttonTouchMasks = buttonTouchMasks,
                    movableButtonDeltas = movableButtonDeltas,
                    analogLeftState = analogLeftState,
                    analogRightState = analogRightState,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease,
                    onLayoutSwitch = onLayoutSwitch,
                    onMenuToggle = onMenuToggle,
                    onAnalogMove = onAnalogMove,
                    onHotkey = onHotkey,
                    onHotkeyChange = onHotkeyChange,
                    onLightgunAction = onLightgunAction,
                    availableLayouts = availableLayouts,
                    currentLayoutName = layout.name,
                    context = context,
                    swapAnalogSticks = swapAnalogSticks,
                    invertAnalogLeftY = invertAnalogLeftY,
                    invertAnalogRightY = invertAnalogRightY,
                    dpadDiagonalSensitivity = dpadDiagonalSensitivity,
                    abxyDiagonalSensitivity = abxyDiagonalSensitivity,
                    analogRecenterZone = analogRecenterZone,
                    isZapperGame = isZapperGame,
                    overlayScale = overlayScale
                )
                // Si un bouton a été touché, capturer l'événement
                // Sinon, laisser passer au Zapper en dessous (pour jeux Duck Hunt)
                if (isZapperGame) {
                    // Mode Zapper: Ne consommer QUE si un bouton est touché
                    // Sinon laisser passer au Zapper en dessous
                    handled
                } else {
                    // Mode normal: toujours consommer
                    true
                }
            }
    ) {
        // Canvas unique pour afficher TOUS les boutons
        Canvas(modifier = Modifier.fillMaxSize()) {
            scaledLayout.buttons.forEach { button ->
                // CRITIQUE: Projeter coordonnées normalisées dans le viewport overlay
                // Compatible RetroArch: x_px = viewport_x + (button.x * viewport_w)
                val xPx = viewport.x + (button.x * viewport.width)
                val yPx = viewport.y + (button.y * viewport.height)
                
                // CRITIQUE: Utiliser button.modW et button.modH pour l'affichage des IMAGES!
                // RetroArch utilise mod_w = 2.0 * range_x et mod_h = 2.0 * range_y
                // C'est pour ça que les images du D-pad se chevauchent et forment un D-pad compact!
                // Projeter dans viewport: width_px = mod_w * viewport_w
                val displayWidthPx = button.modW * viewport.width * overlayScale
                val displayHeightPx = button.modH * viewport.height * overlayScale
                
                // CRITIQUE: Les hitboxes sont déjà scalées via rangeXHitboxOverride (calculé avec scaledWidth)
                // NE PAS appliquer overlayScale ici! Le scale est déjà dans rangeXHitboxOverride
                // Utiliser button.rangeModifier si != 1.0, sinon layout.rangeModifier
                // Projeter dans viewport: hitbox_px = range * viewport_w * range_mod
                val effectiveRangeMod = if (button.rangeModifier != 1.0f) button.rangeModifier else scaledLayout.rangeModifier
                val hitboxWidthPx = button.rangeXHitbox * viewport.width * effectiveRangeMod
                val hitboxHeightPx = button.rangeYHitbox * viewport.height * effectiveRangeMod
                
                // Charger et afficher l'image du bouton
                button.imagePath?.let { path ->
                    val bitmap = assetManager.loadButtonImage(overlayName, path)
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        // IMPORTANT: Utiliser button.alphaModifier si défini, sinon layout.alphaModifier
                        val effectiveAlphaMod = button.alphaModifier ?: scaledLayout.alphaModifier
                        val isPressed = pressedButtons.values.any { it.contains(button) }
                        val baseAlpha = if (isPressed) effectiveAlphaMod else (0.4f * effectiveAlphaMod)
                        val alpha = baseAlpha * overlayOpacity  // Appliquer opacity globale
                        
                        // IMPORTANT: Utiliser modW et modH pour la taille d'affichage!
                        // RetroArch calcule mod_w = 2.0 * range_x et mod_h = 2.0 * range_y
                        // C'est pour ça que les images du D-pad se chevauchent pour former un D-pad compact!
                        
                        // Position mod_x et mod_y (top-left corner de l'image)
                        // Projeter dans viewport: mod_x_px = viewport_x + (mod_x * viewport_w)
                        var modXPx = viewport.x + (button.modX * viewport.width)
                        var modYPx = viewport.y + (button.modY * viewport.height)
                        
                        // MOVABLE: Si le bouton est movable, appliquer visualOffset ou delta
                        // L'image du bouton suit le doigt dans la limite du range
                        if (button.movable) {
                            when (button.type) {
                                OverlayButtonType.ANALOG_LEFT, OverlayButtonType.ANALOG_RIGHT -> {
                                    // Analog sticks: utiliser visualOffset depuis AnalogStickState
                                    val analogState = when (button.type) {
                                        OverlayButtonType.ANALOG_LEFT -> analogLeftState.value
                                        OverlayButtonType.ANALOG_RIGHT -> analogRightState.value
                                        else -> null
                                    }
                                    
                                    if (analogState != null && analogState.isActivated) {
                                        // Appliquer l'offset visuel calculé dans handleTouchEvent
                                        modXPx += analogState.visualOffsetX
                                        modYPx += analogState.visualOffsetY
                                    }
                                }
                                else -> {
                                    // Boutons déplaçables non-analog: utiliser delta depuis movableButtonDeltas
                                    val buttonIndex = scaledLayout.buttons.indexOf(button)
                                    if (buttonIndex >= 0) {
                                        val delta = movableButtonDeltas[buttonIndex]
                                        if (delta != null) {
                                            // Delta est en pixels, appliquer directement
                                            modXPx += delta.first
                                            modYPx += delta.second
                                        }
                                    }
                                }
                            }
                        }
                        
                        val topLeft = Offset(
                            x = modXPx,
                            y = modYPx
                        )
                        
                        drawImage(
                            image = imageBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                x = topLeft.x.toInt(),
                                y = topLeft.y.toInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                width = displayWidthPx.toInt(),
                                height = displayHeightPx.toInt()
                            ),
                            alpha = alpha
                        )
                    }
                }
                
                // MODE DEBUG: Afficher les hitboxes (IDENTIQUES à celles utilisées dans isTouchInsideButton!)
                if (showDebug) {
                    // CRITIQUE: Utiliser xHitbox/yHitbox pour la POSITION (pas x/y!)
                    // Les hitboxes peuvent être décalées si reach asymétrique
                    // Projeter dans viewport: hitbox_x_px = viewport_x + (x_hitbox * viewport_w)
                    val hitboxX = viewport.x + (button.xHitbox * viewport.width)
                    val hitboxY = viewport.y + (button.yHitbox * viewport.height)
                    
                // CRITIQUE: Calculer les hitboxes EXACTEMENT comme dans isTouchInsideButton()
                // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) ET layout.rangeModifier ET overlayScale
                // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod
                // Projeter dans viewport: hitbox_px = range * viewport_w * range_mod * overlayScale
                // CORRECTION: Utiliser rangeModifier SANS range_mod (1.0f) pour correspondre au premier touch
                // La détection utilise rangeModifier=1.0f pour le premier touch, puis rangeModifier=layout.rangeModifier si touch actif
                // Pour le debug, on montre les zones comme elles sont détectées au PREMIER touch (sans range_mod)
                // CORRECTION: Appliquer overlayScale pour que les zones bleues suivent les images
                // CORRECTION: buttonWidth est le rayon, ne PAS diviser par 2
                val debugButtonWidth = button.rangeXHitbox * viewport.width * 1.0f * overlayScale  // Sans range_mod pour correspondre au premier touch
                val debugButtonHeight = button.rangeYHitbox * viewport.height * 1.0f * overlayScale  // Sans range_mod pour correspondre au premier touch
                    
                    val debugColor = when (button.type) {
                        OverlayButtonType.ANALOG_LEFT, OverlayButtonType.ANALOG_RIGHT -> Color.Green
                        else -> Color.Red
                    }
                    
                    when (button.shape) {
                        ButtonShape.RADIAL -> {
                            // CORRECTION: buttonWidth est le rayon, utiliser directement
                            drawCircle(
                                color = debugColor,
                                radius = debugButtonWidth.coerceAtLeast(debugButtonHeight),
                                center = Offset(hitboxX, hitboxY),
                                alpha = 0.5f
                            )
                        }
                        ButtonShape.RECT -> {
                            // CORRECTION: buttonWidth est le rayon, utiliser directement
                            drawRect(
                                color = Color.Blue,
                                topLeft = Offset(hitboxX - debugButtonWidth, hitboxY - debugButtonHeight),
                                size = Size(debugButtonWidth * 2, debugButtonHeight * 2),
                                alpha = 0.5f
                            )
                        }
                        ButtonShape.NONE -> {
                            // Hitbox désactivée - ne rien dessiner
                        }
                    }
                }
            }
            
            // MODE DEBUG: Afficher la position actuelle des analog sticks
            if (showDebug) {
                // Stick gauche
                val leftStick = scaledLayout.buttons.find { it.type == OverlayButtonType.ANALOG_LEFT }
                if (leftStick != null && (analogLeftState.value.x != 0f || analogLeftState.value.y != 0f)) {
                    val centerX = viewport.x + (leftStick.x * viewport.width)
                    val centerY = viewport.y + (leftStick.y * viewport.height)
                    val radius = leftStick.width * viewport.width * scaledLayout.rangeModifier * overlayScale
                    val currentX = centerX + analogLeftState.value.x * radius
                    val currentY = centerY + analogLeftState.value.y * radius
                    
                    drawCircle(
                        color = Color.Yellow,
                        radius = 20f,
                        center = Offset(currentX, currentY),
                        alpha = 0.8f
                    )
                }
                
                // Stick droit
                val rightStick = scaledLayout.buttons.find { it.type == OverlayButtonType.ANALOG_RIGHT }
                if (rightStick != null && (analogRightState.value.x != 0f || analogRightState.value.y != 0f)) {
                    val centerX = viewport.x + (rightStick.x * viewport.width)
                    val centerY = viewport.y + (rightStick.y * viewport.height)
                    val radius = rightStick.width * viewport.width * scaledLayout.rangeModifier * overlayScale
                    val currentX = centerX + analogRightState.value.x * radius
                    val currentY = centerY + analogRightState.value.y * radius
                    
                    drawCircle(
                        color = Color.Cyan,
                        radius = 20f,
                        center = Offset(currentX, currentY),
                        alpha = 0.8f
                    )
                }
            }
            
            // MODE SHOW INPUTS: Afficher visuel des boutons pressés (TOUCHED mode)
            if (showInputsMode == com.retroplay.overlay.models.ShowInputsMode.TOUCHED || 
                showInputsMode == com.retroplay.overlay.models.ShowInputsMode.BOTH) {
                
                // Parcourir tous les boutons pressés
                pressedButtons.values.flatten().toSet().forEach { pressedButton ->
                    // Skip system buttons (overlay, layout_next, etc.)
                    if (!com.retroplay.overlay.models.RetroArchButtonMapping.isOverlayControlAction(pressedButton.action)) {
                        val xPx = viewport.x + (pressedButton.x * viewport.width)
                        val yPx = viewport.y + (pressedButton.y * viewport.height)
                        val wPx = pressedButton.width * viewport.width * scaledLayout.rangeModifier
                        val hPx = pressedButton.height * viewport.height * scaledLayout.rangeModifier
                        
                        // Dessiner un overlay vert semi-transparent sur le bouton pressé
                        when (pressedButton.shape) {
                            ButtonShape.RECT -> {
                                drawRect(
                                    color = Color(0x4400FF00),  // Vert transparent
                                    topLeft = Offset(xPx - wPx / 2, yPx - hPx / 2),
                                    size = Size(wPx, hPx)
                                )
                                drawRect(
                                    color = Color(0xFF00FF00),  // Bordure verte
                                    topLeft = Offset(xPx - wPx / 2, yPx - hPx / 2),
                                    size = Size(wPx, hPx),
                                    style = Stroke(width = 4f)
                                )
                            }
                            else -> {  // "radial" ou défaut
                                drawCircle(
                                    color = Color(0x4400FF00),  // Vert transparent
                                    radius = wPx,
                                    center = Offset(xPx, yPx)
                                )
                                drawCircle(
                                    color = Color(0xFF00FF00),  // Bordure verte
                                    radius = wPx,
                                    center = Offset(xPx, yPx),
                                    style = Stroke(width = 4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gestion des événements touch avec support multi-touch
 */
private fun handleOverlayTouch(
    event: MotionEvent,
    layout: OverlayLayout,
    screenSize: IntSize,
    viewport: OverlayViewport,
    pressedButtons: MutableMap<Int, Set<OverlayButton>>,
    buttonTouchMasks: MutableMap<OverlayButton, MutableSet<Int>>,
    movableButtonDeltas: MutableMap<Int, Pair<Float, Float>>,
    analogLeftState: MutableState<AnalogStickState>,
    analogRightState: MutableState<AnalogStickState>,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit,
    onMenuToggle: () -> Unit,
    onAnalogMove: (String, Float, Float) -> Unit,
    onHotkey: (String) -> Unit,
    onHotkeyChange: (String, Boolean) -> Unit,
    onLightgunAction: (String) -> Unit,
    availableLayouts: List<String> = emptyList(),
    currentLayoutName: String = "",
    context: android.content.Context,
    swapAnalogSticks: Boolean = false,
    invertAnalogLeftY: Boolean = false,
    invertAnalogRightY: Boolean = false,
    dpadDiagonalSensitivity: Int = 50,
    abxyDiagonalSensitivity: Int = 50,
    analogRecenterZone: Int = 0,  // 0-100: zone de recentrage (% du radius)
    isZapperGame: Boolean = false,
    overlayScale: Float = 1.0f
): Boolean {
    val TAG = "TouchHandler"
    val ANALOG_DEADZONE = 0.15f  // 15% dead zone (zone morte)
    
    // Track si un bouton/analog a été touché (pour retourner true/false)
    var buttonOrAnalogTouched = false
    
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            val x = event.getX(pointerIndex)
            val y = event.getY(pointerIndex)
            
            // CRITIQUE: Les touches de pointerInteropFilter sont en pixels écran (composable fillMaxSize)
            // Les hitboxes sont projetées avec viewport.x + (button.xHitbox * viewport.width), donc aussi en pixels écran
            // Pas besoin de conversion! Utiliser directement x, y
            // D'abord vérifier si c'est un analog stick
            // Pour analog sticks : utiliser button.rangeModifier même pour détection initiale (large zone tactile)
            val analogStick = layout.buttons.find { button ->
                (button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT) &&
                isTouchInsideButton(x, y, button, button.rangeModifier, viewport, overlayScale)
            }
            
            // DEBUG: Log pour comprendre pourquoi les analog sticks ne sont pas détectés
            if (analogStick == null) {
                val allAnalogSticks = layout.buttons.filter { it.type == OverlayButtonType.ANALOG_LEFT || it.type == OverlayButtonType.ANALOG_RIGHT }
                if (allAnalogSticks.isNotEmpty()) {
                    Log.w(TAG, "❌ NO ANALOG DETECTED at ($x,$y). Available analog sticks:")
                    allAnalogSticks.forEach { stick ->
                        val centerX = viewport.x + (stick.x * viewport.width)
                        val centerY = viewport.y + (stick.y * viewport.height)
                        val radius = stick.width * viewport.width * stick.rangeModifier
                        val distance = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
                        Log.w(TAG, "  ${stick.type}: center=($centerX,$centerY) radius=$radius distance=$distance action='${stick.action}'")
                    }
                }
            }
            
            if (analogStick != null) {
                // C'est un analog stick
                val centerX = viewport.x + (analogStick.x * viewport.width)
                val centerY = viewport.y + (analogStick.y * viewport.height)
                val radius = analogStick.width * viewport.width * layout.rangeModifier
                
                // Vérifier si c'est le premier touch (pas encore activé) et si dans la zone de recentrage
                val isFirstTouch = when (analogStick.type) {
                    OverlayButtonType.ANALOG_LEFT -> !analogLeftState.value.isActivated
                    OverlayButtonType.ANALOG_RIGHT -> !analogRightState.value.isActivated
                    else -> false
                }
                
                val distanceFromCenter = kotlin.math.sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
                val recenterThreshold = radius * (analogRecenterZone / 100f)
                
                // Calculer l'offset de recentrage (0 si hors zone ou déjà activé)
                val (recenterOffsetX, recenterOffsetY) = if (isFirstTouch && analogRecenterZone > 0 && distanceFromCenter <= recenterThreshold) {
                    // Recentrer: utiliser la position du touch comme nouveau centre
                    val offsetX = x - centerX
                    val offsetY = y - centerY
                    Pair(offsetX, offsetY)
                } else {
                    // Utiliser l'offset existant si déjà activé
                    when (analogStick.type) {
                        OverlayButtonType.ANALOG_LEFT -> Pair(analogLeftState.value.recenterOffsetX, analogLeftState.value.recenterOffsetY)
                        OverlayButtonType.ANALOG_RIGHT -> Pair(analogRightState.value.recenterOffsetX, analogRightState.value.recenterOffsetY)
                        else -> Pair(0f, 0f)
                    }
                }
                
                // Calculer les valeurs avec le centre recentré si nécessaire
                val effectiveCenterX = centerX + recenterOffsetX
                val effectiveCenterY = centerY + recenterOffsetY
                val values = calculateAnalogValues(x, y, analogStick, viewport, ANALOG_DEADZONE, layout.rangeModifier,
                    if (analogStick.type == OverlayButtonType.ANALOG_LEFT) invertAnalogLeftY else invertAnalogRightY,
                    effectiveCenterX, effectiveCenterY)
                
                if (values != null) {
                    // Calculer l'offset visuel pour movable buttons
                    val (visualOffsetX, visualOffsetY) = if (analogStick.movable) {
                        // Range de BASE en pixels (sans modifiers, juste width/height)
                        // RetroArch limite le delta visuel au range de base, pas au range étendu pour hitbox
                        val baseRangeX = analogStick.width * viewport.width
                        val baseRangeY = analogStick.height * viewport.height
                        val dx = x - effectiveCenterX
                        val dy = y - effectiveCenterY
                        // Limiter l'offset visuel au range de base (clamp chaque axe)
                        val clampedDx = dx.coerceIn(-baseRangeX, baseRangeX)
                        val clampedDy = dy.coerceIn(-baseRangeY, baseRangeY)
                        Pair(clampedDx, clampedDy)
                    } else {
                        Pair(0f, 0f)
                    }
                    
                    // Enregistrer le pointerId et activer le stick pour permettre hitbox étendue
                    // Appliquer swap si demandé : LEFT devient RIGHT et RIGHT devient LEFT
                    when (analogStick.type) {
                        OverlayButtonType.ANALOG_LEFT -> {
                            analogLeftState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true, visualOffsetX, visualOffsetY, recenterOffsetX, recenterOffsetY)
                            val actionName = if (swapAnalogSticks) "analog_right" else "analog_left"
                            onAnalogMove(actionName, values.first, values.second)
                            Log.d(TAG, "Analog LEFT (sent as $actionName): x=${String.format("%.2f", values.first)}, y=${String.format("%.2f", values.second)}${if (recenterOffsetX != 0f || recenterOffsetY != 0f) " [RECENTERED: $recenterOffsetX, $recenterOffsetY]" else ""}")
                        }
                        OverlayButtonType.ANALOG_RIGHT -> {
                            analogRightState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true, visualOffsetX, visualOffsetY, recenterOffsetX, recenterOffsetY)
                            val actionName = if (swapAnalogSticks) "analog_left" else "analog_right"
                            onAnalogMove(actionName, values.first, values.second)
                            Log.d(TAG, "Analog RIGHT (sent as $actionName): x=${String.format("%.2f", values.first)}, y=${String.format("%.2f", values.second)}${if (recenterOffsetX != 0f || recenterOffsetY != 0f) " [RECENTERED: $recenterOffsetX, $recenterOffsetY]" else ""}")
                        }
                        else -> {}
                    }
                }
                // Ne pas traiter comme bouton normal
                buttonOrAnalogTouched = true
            }
            
            // Si ce n'est pas un analog stick, traiter comme bouton normal
            // Pour le premier touch (ACTION_DOWN), pas de range_mod actif
            val touchedButtons = detectButtonsAtPosition(
                x, y, layout, viewport, dpadDiagonalSensitivity, abxyDiagonalSensitivity,
                buttonTouchMasks = buttonTouchMasks,
                overlayScale = overlayScale,
                currentPointerId = pointerId,
                previousTouchedButtons = emptySet()  // Pas de boutons précédents
            )
            
            // DEBUG: Log si plusieurs boutons détectés (chevauchement potentiel)
            if (touchedButtons.size > 1) {
                Log.w(TAG, "OVERLAP: ${touchedButtons.size} buttons detected at ($x,$y): ${touchedButtons.joinToString()}")
            }
            
            // Si au moins un bouton touché, marquer comme "bouton touché"
            if (touchedButtons.isNotEmpty()) {
                buttonOrAnalogTouched = true
            }
            
            // Enregistrer les boutons pressés pour ce pointeur
            pressedButtons[pointerId] = touchedButtons
            
            // CRITIQUE: Mettre à jour buttonTouchMasks (compatible RetroArch touch_mask)
            // Marquer ce touch comme actif pour chaque bouton détecté
            touchedButtons.forEach { button ->
                buttonTouchMasks.getOrPut(button) { mutableSetOf() }.add(pointerId)
            }
            
            // Gérer les boutons déplaçables (movable, non-analog)
            touchedButtons.forEachIndexed { index, button ->
                if (button.movable && button.type != OverlayButtonType.ANALOG_LEFT && button.type != OverlayButtonType.ANALOG_RIGHT) {
                    // Bouton déplaçable: calculer le delta initial
                    val buttonIndex = layout.buttons.indexOf(button)
                    if (buttonIndex >= 0) {
                        val centerX = viewport.x + (button.x * viewport.width)
                        val centerY = viewport.y + (button.y * viewport.height)
                        val dx = x - centerX
                        val dy = y - centerY
                        // Clamper le delta au range du bouton (comme RetroArch)
                        val baseRangeX = button.width * viewport.width
                        val baseRangeY = button.height * viewport.height
                        val clampedDx = dx.coerceIn(-baseRangeX, baseRangeX)
                        val clampedDy = dy.coerceIn(-baseRangeY, baseRangeY)
                        // Stocker le delta en pixels (sera converti en normalisé lors du rendu)
                        movableButtonDeltas[buttonIndex] = Pair(clampedDx, clampedDy)
                        Log.d(TAG, "[MOVABLE] Button '${button.action}' (index=$buttonIndex) delta: ($clampedDx, $clampedDy)")
                    }
                }
            }
            
            // Déclencher les callbacks
            touchedButtons.forEach { button ->
                // Actions spéciales (overlay control)
                if (RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                    if (button.action.startsWith("overlay_next")) {
                        if (button.nextTarget != null) {
                            // Utiliser nextTarget si défini (RetroArch officiel)
                            Log.i(TAG, "SYSTEM BUTTON: overlay_next (target) | target='${button.nextTarget}' | Img='${button.imagePath}' | Normalized center: (${button.x}, ${button.y}) | Touch px: ($x, $y)")
                            onLayoutSwitch(button.nextTarget)
                        } else if (availableLayouts.isNotEmpty() && currentLayoutName.isNotEmpty()) {
                            // Navigation cyclique si nextTarget n'est pas défini
                            val currentIndex = availableLayouts.indexOf(currentLayoutName)
                            val nextIndex = if (currentIndex < availableLayouts.size - 1) {
                                currentIndex + 1
                            } else {
                                // Cyclique: revenir au premier layout
                                0
                            }
                            val nextLayout = availableLayouts[nextIndex]
                            Log.i(TAG, "SYSTEM BUTTON: overlay_next (cyclic) | Current='$currentLayoutName' (index=$currentIndex) -> Next='$nextLayout' (index=$nextIndex) | Img='${button.imagePath}' | Touch px: ($x, $y)")
                            onLayoutSwitch(nextLayout)
                        } else {
                            Log.w(TAG, "SYSTEM BUTTON: overlay_next | No nextTarget, available layouts, or current layout name, cannot navigate")
                        }
                    } else if (button.action.startsWith("overlay_prev")) {
                        // Navigation vers le layout précédent (cyclique)
                        if (availableLayouts.isNotEmpty() && currentLayoutName.isNotEmpty()) {
                            val currentIndex = availableLayouts.indexOf(currentLayoutName)
                            val prevIndex = if (currentIndex > 0) {
                                currentIndex - 1
                            } else {
                                // Cyclique: revenir au dernier layout
                                availableLayouts.size - 1
                            }
                            val prevLayout = availableLayouts[prevIndex]
                            Log.i(TAG, "SYSTEM BUTTON: overlay_prev | Current='$currentLayoutName' (index=$currentIndex) -> Prev='$prevLayout' (index=$prevIndex) | Img='${button.imagePath}' | Touch px: ($x, $y)")
                            onLayoutSwitch(prevLayout)
                        } else if (button.nextTarget != null) {
                            // Fallback: utiliser nextTarget si disponible (compatibilité)
                            Log.i(TAG, "SYSTEM BUTTON: overlay_prev (fallback to nextTarget) | target='${button.nextTarget}' | Img='${button.imagePath}' | Touch px: ($x, $y)")
                            onLayoutSwitch(button.nextTarget)
                        } else {
                            Log.w(TAG, "SYSTEM BUTTON: overlay_prev | No available layouts or current layout name, cannot navigate")
                        }
                    } else if (button.action == "menu_toggle") {
                        Log.i(TAG, "SYSTEM BUTTON: MENU | Img='${button.imagePath}' | Normalized center: (${button.x}, ${button.y}) | Touch px: ($x, $y)")
                        onMenuToggle()
                    }
                } else if (RetroArchButtonMapping.isHotkeyAction(button.action)) {
                    // Hotkeys RetroArch (save/load/rewind/fast_forward, etc.)
                    Log.i(TAG, "HOTKEY: ${button.action} | Img='${button.imagePath}' | Touch px: ($x, $y)")
                    onHotkeyChange(button.action, true)
                    if (button.action != "rewind") {
                        onHotkey(button.action)
                    }
                } else if (RetroArchButtonMapping.isLightgunAction(button.action)) {
                    // Actions lightgun RetroArch (gun_trigger, gun_reload, gun_aux_a/b, etc.)
                    Log.i(TAG, "LIGHTGUN ACTION: ${button.action} | Img='${button.imagePath}' | Touch px: ($x, $y)")
                    onLightgunAction(button.action)
                } else {
                    // Actions normales (boutons gamepad)
                    Log.d(TAG, "Button pressed: ${button.action}")
                    onButtonPress(button.action)
                }
            }
        }
        
        MotionEvent.ACTION_MOVE -> {
            // Vérifier chaque pointeur actif
            for (i in 0 until event.pointerCount) {
                val pointerId = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                
                // Vérifier si ce pointeur est sur un analog stick
                var handledByAnalog = false
                
                // Si ce touch est déjà tracked (analog ou bouton), on le gère
                if (analogLeftState.value.pointerId == pointerId) {
                    // Ce doigt contrôle le stick gauche
                        val leftStick = layout.buttons.find { it.type == OverlayButtonType.ANALOG_LEFT }
                    if (leftStick != null) {
                        val centerX = viewport.x + (leftStick.x * viewport.width) + analogLeftState.value.recenterOffsetX
                        val centerY = viewport.y + (leftStick.y * viewport.height) + analogLeftState.value.recenterOffsetY
                        val values = calculateAnalogValues(x, y, leftStick, viewport, ANALOG_DEADZONE, layout.rangeModifier,
                            if (swapAnalogSticks) invertAnalogRightY else invertAnalogLeftY,
                            centerX, centerY)
                        if (values != null) {
                            // Calculer l'offset visuel pour movable buttons
                            val (visualOffsetX, visualOffsetY) = if (leftStick.movable) {
                                // Utiliser le centre effectif (avec recentrage)
                                val baseRangeX = leftStick.width * viewport.width
                                val baseRangeY = leftStick.height * viewport.height
                                val dx = x - centerX
                                val dy = y - centerY
                                // Limiter l'offset visuel au range de base (clamp chaque axe)
                                val clampedDx = dx.coerceIn(-baseRangeX, baseRangeX)
                                val clampedDy = dy.coerceIn(-baseRangeY, baseRangeY)
                                Pair(clampedDx, clampedDy)
                            } else {
                                Pair(0f, 0f)
                            }
                            analogLeftState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true, visualOffsetX, visualOffsetY)
                            val actionName = if (swapAnalogSticks) "analog_right" else "analog_left"
                            onAnalogMove(actionName, values.first, values.second)
                        }
                        handledByAnalog = true
                    }
                }
                
                if (analogRightState.value.pointerId == pointerId) {
                    // Ce doigt contrôle le stick droit
                        val rightStick = layout.buttons.find { it.type == OverlayButtonType.ANALOG_RIGHT }
                    if (rightStick != null) {
                        val centerX = viewport.x + (rightStick.x * viewport.width) + analogRightState.value.recenterOffsetX
                        val centerY = viewport.y + (rightStick.y * viewport.height) + analogRightState.value.recenterOffsetY
                        val values = calculateAnalogValues(x, y, rightStick, viewport, ANALOG_DEADZONE, layout.rangeModifier,
                            if (swapAnalogSticks) invertAnalogLeftY else invertAnalogRightY,
                            centerX, centerY)
                        if (values != null) {
                            // Calculer l'offset visuel pour movable buttons
                            val (visualOffsetX, visualOffsetY) = if (rightStick.movable) {
                                // Utiliser le centre effectif (avec recentrage)
                                val baseRangeX = rightStick.width * viewport.width
                                val baseRangeY = rightStick.height * viewport.height
                                val dx = x - centerX
                                val dy = y - centerY
                                // Limiter l'offset visuel au range de base (clamp chaque axe)
                                val clampedDx = dx.coerceIn(-baseRangeX, baseRangeX)
                                val clampedDy = dy.coerceIn(-baseRangeY, baseRangeY)
                                Pair(clampedDx, clampedDy)
                            } else {
                                Pair(0f, 0f)
                            }
                            analogRightState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true, visualOffsetX, visualOffsetY)
                            val actionName = if (swapAnalogSticks) "analog_left" else "analog_right"
                            onAnalogMove(actionName, values.first, values.second)
                        }
                        handledByAnalog = true
                    }
                }
                
                // Si pas géré par analog, traiter comme bouton normal
                if (!handledByAnalog) {
                    // Pour ACTION_MOVE, buttonTouchMasks est utilisé pour déterminer useRangeMod par bouton
                    val previousButtons = pressedButtons[pointerId] ?: emptySet()
                    val currentButtons = detectButtonsAtPosition(
                        x, y, layout, viewport, dpadDiagonalSensitivity, abxyDiagonalSensitivity,
                        buttonTouchMasks = buttonTouchMasks,
                        currentPointerId = pointerId,
                        overlayScale = overlayScale,
                        previousTouchedButtons = previousButtons  // Boutons précédents pour range_mod_exclusive
                    )
                    
                    // CRITIQUE: Mettre à jour buttonTouchMasks (compatible RetroArch touch_mask)
                    // Retirer ce touch des boutons qui ne sont plus détectés
                    previousButtons.forEach { button ->
                        buttonTouchMasks[button]?.remove(pointerId)
                        // Nettoyer les entrées vides
                        if (buttonTouchMasks[button]?.isEmpty() == true) {
                            buttonTouchMasks.remove(button)
                        }
                    }
                    // Ajouter ce touch aux nouveaux boutons détectés
                    currentButtons.forEach { button ->
                        buttonTouchMasks.getOrPut(button) { mutableSetOf() }.add(pointerId)
                    }
                    
                    // Boutons nouvellement pressés
                    val newlyPressedButtons = currentButtons - previousButtons
                    newlyPressedButtons.forEach { button ->
                        if (RetroArchButtonMapping.isHotkeyAction(button.action)) {
                            Log.i(TAG, "HOTKEY (move): ${button.action}")
                            onHotkeyChange(button.action, true)
                            if (button.action != "rewind") {
                                onHotkey(button.action)
                            }
                        } else if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                            Log.d(TAG, "Button pressed (move): ${button.action}")
                            onButtonPress(button.action)
                        }
                    }
                    
                    // Boutons relâchés
                    val releasedButtons = previousButtons - currentButtons
                    releasedButtons.forEach { button ->
                        if (!RetroArchButtonMapping.isOverlayControlAction(button.action) && !RetroArchButtonMapping.isHotkeyAction(button.action)) {
                            Log.d(TAG, "Button released (move): ${button.action}")
                            onButtonRelease(button.action)
                        }
                    }
                    
                    pressedButtons[pointerId] = currentButtons
                    
                    // Mettre à jour les deltas pour boutons déplaçables (movable, non-analog)
                    currentButtons.forEach { button ->
                        if (button.movable && button.type != OverlayButtonType.ANALOG_LEFT && button.type != OverlayButtonType.ANALOG_RIGHT) {
                            val buttonIndex = layout.buttons.indexOf(button)
                            if (buttonIndex >= 0) {
                                val centerX = viewport.x + (button.x * viewport.width)
                                val centerY = viewport.y + (button.y * viewport.height)
                                val dx = x - centerX
                                val dy = y - centerY
                                // Clamper le delta au range du bouton (comme RetroArch)
                                val baseRangeX = button.width * viewport.width
                                val baseRangeY = button.height * viewport.height
                                val clampedDx = dx.coerceIn(-baseRangeX, baseRangeX)
                                val clampedDy = dy.coerceIn(-baseRangeY, baseRangeY)
                                // Mettre à jour le delta en pixels
                                movableButtonDeltas[buttonIndex] = Pair(clampedDx, clampedDy)
                            }
                        }
                    }
                }
            }
        }
        
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            
            // Vérifier si ce pointeur contrôle un analog stick
            if (analogLeftState.value.pointerId == pointerId) {
                // Relâcher le stick gauche - reset complet (isActivated = false)
                analogLeftState.value = AnalogStickState()  // Reset à 0,0, désactivé
                onAnalogMove("analog_left", 0f, 0f)
                Log.d(TAG, "Analog LEFT released")
            }
            
            if (analogRightState.value.pointerId == pointerId) {
                // Relâcher le stick droit - reset complet (isActivated = false)
                analogRightState.value = AnalogStickState()  // Reset à 0,0, désactivé
                onAnalogMove("analog_right", 0f, 0f)
                Log.d(TAG, "Analog RIGHT released")
            }
            
            // Relâcher tous les boutons de ce pointeur (pas les hotkeys, ils sont one-shot)
            val releasedButtons = pressedButtons[pointerId] ?: emptySet()
            
            // CRITIQUE: Retirer ce touch de buttonTouchMasks (compatible RetroArch touch_mask)
            // Compatible RetroArch: desc->touch_mask = 0 après ACTION_UP
            releasedButtons.forEach { button ->
                buttonTouchMasks[button]?.remove(pointerId)
                // Nettoyer les entrées vides
                if (buttonTouchMasks[button]?.isEmpty() == true) {
                    buttonTouchMasks.remove(button)
                }
            }
            
            releasedButtons.forEach { button ->
                if (RetroArchButtonMapping.isHotkeyAction(button.action)) {
                    onHotkeyChange(button.action, false)
                } else if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                    Log.d(TAG, "Button released: ${button.action}")
                    onButtonRelease(button.action)
                }
                
                // P3: Sauvegarde persistante positions boutons déplaçables
                if (button.movable && button.type != OverlayButtonType.ANALOG_LEFT && button.type != OverlayButtonType.ANALOG_RIGHT) {
                    val buttonIndex = layout.buttons.indexOf(button)
                    if (buttonIndex >= 0) {
                        val delta = movableButtonDeltas[buttonIndex]
                        if (delta != null) {
                            // Sauvegarder la position finale dans SharedPreferences
                            val sharedPrefs = context.getSharedPreferences(
                                "overlay_prefs", android.content.Context.MODE_PRIVATE
                            )
                            val keyX = "overlay_movable_${layout.name}_${button.action}_delta_x"
                            val keyY = "overlay_movable_${layout.name}_${button.action}_delta_y"
                            sharedPrefs.edit()
                                .putFloat(keyX, delta.first)
                                .putFloat(keyY, delta.second)
                                .apply()
                            Log.d(TAG, "[MOVABLE] Saved position for '${button.action}' in layout '${layout.name}': (${delta.first}, ${delta.second})")
                        }
                        // Ne pas réinitialiser le delta - le garder pour la prochaine session
                        // movableButtonDeltas.remove(buttonIndex)  // Commenté pour persistance
                    }
                }
            }
            
            pressedButtons.remove(pointerId)
        }
        
        MotionEvent.ACTION_CANCEL -> {
            // Relâcher tous les analog sticks
            if (analogLeftState.value.pointerId != null) {
                analogLeftState.value = AnalogStickState()
                onAnalogMove("analog_left", 0f, 0f)
                Log.d(TAG, "Analog LEFT released (cancel)")
            }
            if (analogRightState.value.pointerId != null) {
                analogRightState.value = AnalogStickState()
                onAnalogMove("analog_right", 0f, 0f)
                Log.d(TAG, "Analog RIGHT released (cancel)")
            }
            
            // Relâcher tous les boutons (pas les hotkeys, ils sont one-shot)
            pressedButtons.flatMap { it.value }.distinct().forEach { button ->
                if (RetroArchButtonMapping.isHotkeyAction(button.action)) {
                    onHotkeyChange(button.action, false)
                } else if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                    Log.d(TAG, "Button released (cancel): ${button.action}")
                    onButtonRelease(button.action)
                }
                
                // Réinitialiser le delta pour boutons déplaçables (movable, non-analog)
                if (button.movable && button.type != OverlayButtonType.ANALOG_LEFT && button.type != OverlayButtonType.ANALOG_RIGHT) {
                    val buttonIndex = layout.buttons.indexOf(button)
                    if (buttonIndex >= 0) {
                        movableButtonDeltas.remove(buttonIndex)
                    }
                }
            }
            pressedButtons.clear()
        }
    }
    
    // Retourner true seulement si un bouton/analog a été touché lors d'un ACTION_DOWN
    // OU si on est en train de traiter un touch déjà enregistré (MOVE/UP/CANCEL)
    return when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            // Pour DOWN: retourner true seulement si un bouton/analog touché
            buttonOrAnalogTouched
        }
        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
            // Pour MOVE/UP/CANCEL: retourner true si au moins un pointerId est tracked
            val hasTrackedPointers = pressedButtons.isNotEmpty() || 
                                   analogLeftState.value.pointerId != null || 
                                   analogRightState.value.pointerId != null
            hasTrackedPointers
        }
        else -> false
    }
}

/**
 * Calculer les valeurs analogiques depuis une position de toucher
 * Implémentation basée sur RetroArch input_overlay_get_analog_state()
 * @param invertY Si true, inverse l'axe Y (haut devient bas)
 * @return Pair(x, y) normalisées entre -1.0 et 1.0, ou null si hors dead zone
 */
private fun calculateAnalogValues(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    viewport: OverlayViewport,
    deadzone: Float,
    layoutRangeMod: Float = 1.5f,
    invertY: Boolean = false,
    centerX: Float? = null,  // Centre X personnalisé (pour recentrage)
    centerY: Float? = null   // Centre Y personnalisé (pour recentrage)
): Pair<Float, Float>? {
    // Centre du stick en pixels (x_shift, y_shift dans RetroArch)
    // Utiliser le centre personnalisé si fourni (pour recentrage), sinon utiliser le centre du layout
    // Projeter dans viewport: center_x_px = viewport_x + (button.x * viewport_w)
    val effectiveCenterX = centerX ?: (viewport.x + (button.x * viewport.width))
    val effectiveCenterY = centerY ?: (viewport.y + (button.y * viewport.height))
    
    // Range (rayon) en pixels pour le calcul des valeurs
    // Utiliser layout.rangeModifier (même que le rendu visuel) pour cohérence
    // Projeter dans viewport: range_px = button.width * viewport_w * range_mod
    val rangeX = button.width * viewport.width * layoutRangeMod
    val rangeY = button.height * viewport.height * layoutRangeMod
    
    // Distance depuis le centre (x_dist, y_dist dans RetroArch)
    // Utiliser le centre effectif pour recentrage
    val xDist = touchX - effectiveCenterX
    val yDist = touchY - effectiveCenterY
    
    // Valeurs normalisées comme RetroArch
    val xVal = xDist / rangeX
    val yVal = yDist / rangeY
    
    // Saturation (analog_saturate_pct dans RetroArch)
    // Ex: saturate_pct = 0.75 signifie que les 75% internes contiennent toute la plage analog
    // Au-delà de 75%, c'est complètement saturé (valeur max)
    val xValSat = xVal / button.analogSaturatePct
    val yValSat = yVal / button.analogSaturatePct
    
    // Clamp entre -1.0 et 1.0 (comme RetroArch)
    var finalX = xValSat.coerceIn(-1.0f, 1.0f)
    var finalY = yValSat.coerceIn(-1.0f, 1.0f)
    
    // Appliquer inversion Y si demandé
    if (invertY) {
        finalY = -finalY
    }
    
    // Vérifier dead zone simple (magnitude euclidienne)
    val magnitude = sqrt(finalX * finalX + finalY * finalY)
    if (magnitude < deadzone) {
        return Pair(0f, 0f)
    }
    
    return Pair(finalX, finalY)
}

/**
 * Détecter quels boutons sont touchés à une position donnée
 * Exclut les analog sticks (gérés séparément)
 * 
 * Implémente la logique exclusive hitboxes compatible RetroArch:
 * - range_mod_exclusive (priorité 2) : bloque les autres si touché avec range_mod actif
 * - exclusive (priorité 1) : bloque les autres boutons
 * - Les boutons avec priorité plus élevée bloquent ceux avec priorité plus basse
 * 
 * Compatible avec RetroArch input_driver.c lignes 2516-2532
 * 
 * @param buttonTouchMasks Map des touches actives par bouton (compatible RetroArch touch_mask)
 * @param currentPointerId ID du touch actuel (pour vérifier si ce touch était actif pour ce bouton)
 */
private fun detectButtonsAtPosition(
    x: Float,
    y: Float,
    layout: OverlayLayout,
    viewport: OverlayViewport,
    dpadDiagonalSensitivity: Int = 50,
    abxyDiagonalSensitivity: Int = 50,
    buttonTouchMasks: Map<OverlayButton, Set<Int>>,  // Touch masks par bouton (compatible RetroArch)
    currentPointerId: Int,  // ID du touch actuel
    previousTouchedButtons: Set<OverlayButton> = emptySet(),  // Boutons touchés précédemment (pour range_mod_exclusive)
    overlayScale: Float = 1.0f
): Set<OverlayButton> {
    val touched = mutableListOf<Pair<OverlayButton, Int>>()  // (button, priority)
    var highestPriority = -1
    
    layout.buttons.forEach { button ->
        // Skip analog sticks (gérés séparément)
        if (button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT) {
            return@forEach
        }
        
        // Item P1 #11: Skip boutons avec hitbox désactivée (reach_* == 0.0f)
        // Compatible RetroArch task_overlay.c lignes 492-494
        // Si reach_left + reach_right == 0.0f OU reach_up + reach_down == 0.0f, hitbox désactivée
        if ((button.reachLeft + button.reachRight == 0.0f) || 
            (button.reachUp + button.reachDown == 0.0f)) {
            return@forEach  // Skip ce bouton (hitbox désactivée)
        }
        
        // Traiter les zones 8-way (dpad_area, abxy_area)
        // IMPORTANT: Les zones 8-way sont traitées comme les autres boutons pour la logique exclusive
        // Elles ont priorité 0 (pas exclusive) mais peuvent être bloquées par des boutons exclusive
        if (button.type == OverlayButtonType.DPAD_AREA || button.type == OverlayButtonType.ABXY_AREA) {
            // CRITIQUE: Utiliser range_mod seulement si ce touch était actif pour ce bouton précédemment
            // Compatible RetroArch: use_range_mod = (old_touch_idx != -1) && BIT32_GET(desc->old_touch_mask, old_touch_idx)
            val wasActiveForThisButton = buttonTouchMasks[button]?.contains(currentPointerId) ?: false
            val useRangeModForThisButton = wasActiveForThisButton
            val effectiveRangeMod = if (useRangeModForThisButton) layout.rangeModifier else 1.0f
            
            // Vérifier si le touch est dans la zone (avec range_mod si applicable)
            if (isTouchInsideButton(x, y, button, effectiveRangeMod, viewport, overlayScale)) {
                // Calculer la priorité (zones 8-way peuvent avoir exclusive aussi)
                var priority = 0
                if (useRangeModForThisButton && button.rangeModExclusive && previousTouchedButtons.contains(button)) {
                    priority = 2
                } else if (button.exclusive) {
                    priority = 1
                }
                
                // Si ce bouton a priorité plus basse que la plus haute trouvée, le skip
                if (highestPriority > priority) {
                    return@forEach
                }
                
                // Si ce bouton a priorité plus élevée, effacer tous les précédents
                if (priority > highestPriority) {
                    highestPriority = priority
                    touched.clear()
                }
                
                // Calculer l'offset depuis le centre
                // CRITIQUE: Compatible RetroArch input_overlay_get_eightway_state()
                // RetroArch: x_dist /= desc->range_x où x_dist et range_x sont en coordonnées normalisées (0.0-1.0)
                // Notre équivalent:
                // 1. Calculer x_dist en pixels: x - centerX
                // 2. Normaliser par range_x en pixels: range_x = button.width * viewport.width
                // 3. Donc xDist = (x - centerX) / (button.width * viewport.width)
                val centerX = viewport.x + (button.x * viewport.width)
                val centerY = viewport.y + (button.y * viewport.height)
                // Utiliser button.width/height (range_x/y originaux) pour la normalisation
                // Compatible RetroArch: x_dist /= desc->range_x où range_x est original (non scalé pour normalisation)
                val rangeX = button.width * viewport.width  // range_x en pixels
                val rangeY = button.height * viewport.height  // range_y en pixels
                val xDist = (x - centerX) / rangeX  // Normalisé comme RetroArch
                val yDist = (y - centerY) / rangeY  // Normalisé comme RetroArch
                
                // Obtenir les directions 8-way avec la bonne sensitivity
                val sensitivity = if (button.type == OverlayButtonType.DPAD_AREA) {
                    dpadDiagonalSensitivity
                } else {
                    abxyDiagonalSensitivity
                }
                val directions = get8WayDirections(xDist, yDist, sensitivity)
                
                // Mapper les directions vers les actions custom ou par défaut
                // Defaults RetroArch (task_overlay.c lignes 138-156):
                // DPAD_AREA: up=UP, down=DOWN, left=LEFT, right=RIGHT
                // ABXY_AREA: up=X, down=B, left=Y, right=A
                directions.forEach { dir ->
                    val mappedAction = when (dir) {
                        "up" -> button.eightwayUp ?: if (button.type == OverlayButtonType.ABXY_AREA) "x" else "up"
                        "down" -> button.eightwayDown ?: if (button.type == OverlayButtonType.ABXY_AREA) "b" else "down"
                        "left" -> button.eightwayLeft ?: if (button.type == OverlayButtonType.ABXY_AREA) "y" else "left"
                        "right" -> button.eightwayRight ?: if (button.type == OverlayButtonType.ABXY_AREA) "a" else "right"
                        else -> null
                    }
                    
                    // Créer un bouton virtuel pour cette direction avec la même priorité
                    if (mappedAction != null) {
                        val virtualButton = button.copy(action = mappedAction)
                        touched.add(Pair(virtualButton, priority))
                    }
                }
            }
            return@forEach
        }
        
        // Skip boutons purement décoratifs (action="nul" ou "null")
        if (button.action == "nul" || button.action == "null") {
            return@forEach
        }
        
        // CRITIQUE: Utiliser range_mod seulement si ce touch était actif pour ce bouton précédemment
        // Compatible RetroArch: use_range_mod = (old_touch_idx != -1) && BIT32_GET(desc->old_touch_mask, old_touch_idx)
        val wasActiveForThisButton = buttonTouchMasks[button]?.contains(currentPointerId) ?: false
        val useRangeModForThisButton = wasActiveForThisButton
        val effectiveRangeMod = if (useRangeModForThisButton) layout.rangeModifier else 1.0f
        
        // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) ET effectiveRangeMod pour les boutons normaux
        // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod (seulement si use_range_mod)
        if (isTouchInsideButton(x, y, button, effectiveRangeMod, viewport, overlayScale)) {
            // Calculer la priorité (compatible RetroArch lignes 2518-2521)
            var priority = 0
            if (useRangeModForThisButton && button.rangeModExclusive && previousTouchedButtons.contains(button)) {
                // range_mod_exclusive a priorité 2 (plus haute)
                priority = 2
            } else if (button.exclusive) {
                // exclusive a priorité 1
                priority = 1
            }
            
            // Si ce bouton a priorité plus basse que la plus haute trouvée, le skip (compatible RetroArch ligne 2523-2524)
            if (highestPriority > priority) {
                // DEBUG: Log quand un bouton est bloqué par un exclusive de priorité plus élevée
                if (priority > 0 || highestPriority > 0) {
                    val buttonX = viewport.x + (button.xHitbox * viewport.width)
                    val buttonY = viewport.y + (button.yHitbox * viewport.height)
                    Log.d("TouchHandler", "BLOCKED (priority=$priority < highest=$highestPriority): ${button.action} | Touch: ($x,$y) | Center: ($buttonX,$buttonY)")
                }
                return@forEach
            }
            
            // Si ce bouton a priorité plus élevée, effacer tous les précédents (compatible RetroArch lignes 2526-2532)
            if (priority > highestPriority) {
                val blockedCount = touched.size
                if (blockedCount > 0 && priority > 0) {
                    val blockedActions = touched.map { it.first.action }.joinToString(", ")
                    Log.d("TouchHandler", "EXCLUSIVE BLOCK: ${button.action} (priority=$priority) blocks $blockedCount button(s): $blockedActions")
                }
                highestPriority = priority
                touched.clear()  // Effacer tous les boutons précédents
            }
            
            // Ajouter ce bouton (compatible RetroArch ligne 2534)
            touched.add(Pair(button, priority))
            
            // DEBUG: Log détaillé de la détection
            val buttonX = viewport.x + (button.xHitbox * viewport.width)
            val buttonY = viewport.y + (button.yHitbox * viewport.height)
            val buttonWidth = button.rangeXHitbox * viewport.width * layout.rangeModifier
            val buttonHeight = button.rangeYHitbox * viewport.height * layout.rangeModifier
            if (priority > 0) {
                val exclusiveType = when {
                    priority == 2 -> "range_mod_exclusive"
                    priority == 1 -> "exclusive"
                    else -> "normal"
                }
                Log.d("TouchHandler", "EXCLUSIVE HIT (priority=$priority, type=$exclusiveType): ${button.action} | Touch: ($x,$y) | Center: ($buttonX,$buttonY) | Size: ${buttonWidth.toInt()}x${buttonHeight.toInt()} | useRangeMod=$useRangeModForThisButton")
            } else {
                Log.d("TouchHandler", "HIT: ${button.action} | Touch: ($x,$y) | Center: ($buttonX,$buttonY) | Size: ${buttonWidth.toInt()}x${buttonHeight.toInt()} | Shape: ${button.shape}")
            }
        }
    }
    
    // Retourner seulement les boutons non bloqués (extraire les boutons de la liste de paires)
    return touched.map { it.first }.toSet()
}

/**
 * Vérifier si un touch est à l'intérieur d'un bouton
 */
private fun isTouchInsideButton(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    rangeModifier: Float,
    viewport: OverlayViewport,
    overlayScale: Float = 1.0f
): Boolean {
    // Item P1 #11: Skip boutons avec hitbox désactivée (reach_* == 0.0f)
    // Compatible RetroArch task_overlay.c lignes 492-494
    // Si reach_left + reach_right == 0.0f OU reach_up + reach_down == 0.0f, hitbox désactivée
    if ((button.reachLeft + button.reachRight == 0.0f) || 
        (button.reachUp + button.reachDown == 0.0f)) {
        return false  // Hitbox désactivée, toujours retourner false
    }
    
    // CRITIQUE: Utiliser x_hitbox et y_hitbox (PAS x et y!) pour la position de la hitbox
    // La hitbox peut être décalée si reach_left != reach_right ou reach_up != reach_down
    // Identique à RetroArch input_overlay_desc_init_hitbox() lignes 2641-2655
    // Projeter dans viewport: hitbox_x_px = viewport_x + (x_hitbox * viewport_w)
    val buttonX = viewport.x + (button.xHitbox * viewport.width)
    val buttonY = viewport.y + (button.yHitbox * viewport.height)
    
    // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) pour la TAILLE
    // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod
    // Projeter dans viewport: hitbox_px = range * viewport_w * range_mod
    // CORRECTION CRITIQUE: buttonWidth est déjà le rayon (rangeXHitbox), ne PAS diviser par 2
    // RetroArch: x_dist = (x - desc->x_hitbox) / range_x (divise directement par le rayon)
    // CORRECTION: Appliquer overlayScale pour que les hitboxes suivent les images
    val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier * overlayScale
    val buttonHeight = button.rangeYHitbox * viewport.height * rangeModifier * overlayScale
    
    val isInside = when (button.shape) {
        ButtonShape.RADIAL -> {
            // Hitbox elliptique - rangeXHitbox est le rayon, utiliser directement
            val dx = (touchX - buttonX) / buttonWidth
            val dy = (touchY - buttonY) / buttonHeight
            val distance = sqrt(dx * dx + dy * dy)
            distance <= 1.0f
        }
        ButtonShape.RECT -> {
            // Hitbox rectangulaire - rangeXHitbox est le rayon, utiliser directement
            val left = buttonX - buttonWidth
            val right = buttonX + buttonWidth
            val top = buttonY - buttonHeight
            val bottom = buttonY + buttonHeight
            
            touchX >= left && touchX <= right && touchY >= top && touchY <= bottom
        }
        ButtonShape.NONE -> {
            // Hitbox désactivée - compatible RetroArch OVERLAY_HITBOX_NONE
            // Toujours retourner false (bouton non détectable)
            false
        }
    }
    
    // DEBUG: Log détaillé pour les boutons système
    if (isInside && (button.action.startsWith("overlay_next") || button.action == "menu_toggle")) {
        Log.e("TouchHandler", "🔴 HITBOX: action='${button.action}' | normalized=(${button.x},${button.y}) size=(${button.width},${button.height}) | reach=(L:${button.reachLeft} R:${button.reachRight} U:${button.reachUp} D:${button.reachDown}) | hitbox=(${button.rangeXHitbox},${button.rangeYHitbox}) | pixels=($buttonX,$buttonY) size=(${buttonWidth.toInt()}x${buttonHeight.toInt()}) | touch=($touchX,$touchY)")
    }
    
    return isInside
}

/**
 * Composable pour afficher un bouton individuel
 */
@Composable
private fun BoxScope.RetroArchButton(
    button: OverlayButton,
    overlayName: String,
    assetManager: OverlayAssetManager,
    screenSize: IntSize,
    rangeModifier: Float,
    alphaModifier: Float,
    isPressed: Boolean
) {
    // Charger l'image du bouton (mise en cache)
    val bitmap = remember(overlayName, button.imagePath) {
        button.imagePath?.let { 
            assetManager.loadButtonImage(overlayName, it)
        }
    }
    
    if (bitmap != null) {
        // Convertir coordonnées normalisées → pixels
        val xPx = button.x * screenSize.width
        val yPx = button.y * screenSize.height
        val widthPx = button.width * screenSize.width * rangeModifier
        val heightPx = button.height * screenSize.height * rangeModifier
        
        // Alpha (plus opaque quand pressé)
        val alpha = if (isPressed) 1.0f else (0.7f * alphaModifier)
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Dessiner l'image du bouton
            drawImage(
                bitmap = bitmap,
                center = Offset(xPx, yPx),
                size = Size(widthPx, heightPx),
                alpha = alpha
            )
        }
    }
}

/**
 * Extension pour dessiner une image centrée
 */
private fun DrawScope.drawImage(
    bitmap: Bitmap,
    center: Offset,
    size: Size,
    alpha: Float
) {
    val imageBitmap = bitmap.asImageBitmap()
    
    // Position top-left depuis le centre
    val topLeft = Offset(
        x = center.x - size.width / 2,
        y = center.y - size.height / 2
    )
    
    drawImage(
        image = imageBitmap,
        dstOffset = androidx.compose.ui.unit.IntOffset(
            x = topLeft.x.toInt(),
            y = topLeft.y.toInt()
        ),
        dstSize = androidx.compose.ui.unit.IntSize(
            width = size.width.toInt(),
            height = size.height.toInt()
        ),
        alpha = alpha
    )
}

/**
 * Preview/Debug: Afficher les hitboxes des boutons
 */
@Composable
fun RetroArchOverlayDebug(
    layout: OverlayLayout,
    screenSize: IntSize,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        layout.buttons.forEach { button ->
            // Skip zones tactiles et boutons décoratifs
            if (button.type == OverlayButtonType.DPAD_AREA || button.type == OverlayButtonType.ABXY_AREA) {
                return@forEach
            }
            
            // Skip boutons purement décoratifs (action="nul" ou "null")
            if (button.action == "nul" || button.action == "null") {
                return@forEach
            }
            
            val x = button.x * screenSize.width
            val y = button.y * screenSize.height
            // Pour debug hitbox : utiliser button.rangeModifier pour analog sticks (zone de détection)
            // Pour les autres boutons : layout.rangeModifier
            val rangeMod = if (button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT) {
                button.rangeModifier  // 3.5x pour grande zone tactile
            } else {
                layout.rangeModifier  // 1.5x pour boutons normaux
            }
            val width = button.width * screenSize.width * rangeMod
            val height = button.height * screenSize.height * rangeMod
            
            // Dessiner hitbox
            when (button.shape) {
                ButtonShape.RADIAL -> {
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.3f),
                        radius = width / 2,
                        center = Offset(x, y)
                    )
                }
                ButtonShape.RECT -> {
                    drawRect(
                        color = Color.Blue.copy(alpha = 0.3f),
                        topLeft = Offset(x - width / 2, y - height / 2),
                        size = Size(width, height)
                    )
                }
                ButtonShape.NONE -> {
                    // Hitbox désactivée - ne rien dessiner
                }
            }
            
            // Label du bouton (debug)
            // Note: drawText nécessite TextMeasurer (Material3)
        }
    }
}

/**
 * Appliquer scale, offset et separation à tous les boutons du layout
 * Identique à RetroArch input_driver.c ligne 2674-2715
 */
private fun applyScaleAndOffset(
    layout: OverlayLayout,
    scale: Float,
    xOffset: Float,
    yOffset: Float,
    xSeparation: Float,
    ySeparation: Float
): OverlayLayout {
    if (scale == 1.0f && xOffset == 0.0f && yOffset == 0.0f && xSeparation == 0.0f && ySeparation == 0.0f) {
        return layout  // Pas de transformation nécessaire
    }
    
    val transformedButtons = layout.buttons.map { button ->
        // 1. Appliquer SEPARATION interne (RetroArch ligne 2692-2705)
        var xShiftOffset = 0.0f
        var yShiftOffset = 0.0f
        
        // Si bouton à gauche du centre (x < 0.5), décaler vers la gauche
        if (button.x < 0.5f - 0.0001f) {
            xShiftOffset = xSeparation * -1.0f
        }
        // Si bouton à droite du centre (x > 0.5), décaler vers la droite
        else if (button.x > 0.5f + 0.0001f) {
            xShiftOffset = xSeparation
        }
        
        // Pareil pour Y
        if (button.y < 0.5f - 0.0001f) {
            yShiftOffset = ySeparation * -1.0f
        }
        else if (button.y > 0.5f + 0.0001f) {
            yShiftOffset = ySeparation
        }
        
        val xWithSeparation = button.x + xShiftOffset
        val yWithSeparation = button.y + yShiftOffset
        
        // 2. Appliquer SCALE (centré autour de 0.5) puis OFFSET global
        val newX = 0.5f + (xWithSeparation - 0.5f) * scale + xOffset
        val newY = 0.5f + (yWithSeparation - 0.5f) * scale + yOffset
        
        // CRITIQUE: Scaler width et height AVANT de calculer les hitboxes (compatible RetroArch)
        // RetroArch: scale_w = ol->mod_w * desc->range_x où mod_w est DÉJÀ SCALÉ
        // Donc range_x est effectivement scalé via mod_w
        // Notre équivalent: scaledWidth = width * scale
        val scaledWidth = button.width * scale
        val scaledHeight = button.height * scale
        
        // CRITIQUE: Calculer les hitboxes avec width/height SCALÉS (compatible RetroArch)
        // RetroArch: input_overlay_desc_init_hitbox() est appelé APRÈS que mod_w soit scalé
        // Donc range_x est effectivement scalé via mod_w lors de l'utilisation
        // Notre équivalent: Calculer hitboxes avec scaledWidth/scaledHeight
        
        // Recalculer xHitbox/yHitbox avec x_shift (newX) et range_x SCALÉ (scaledWidth)
        // Compatible RetroArch: x_hitbox = ((x_shift + range_x * reach_right) + (x_shift - range_x * reach_left)) / 2.0
        // Où range_x est effectivement scalé via mod_w
        val newXHitbox = ((newX + scaledWidth * button.reachRight) + (newX - scaledWidth * button.reachLeft)) / 2.0f
        val newYHitbox = ((newY + scaledHeight * button.reachDown) + (newY - scaledHeight * button.reachUp)) / 2.0f
        
        // Recalculer rangeXHitbox/rangeYHitbox avec width/height SCALÉS
        // Compatible RetroArch: range_x_hitbox = (range_x * reach_right + range_x * reach_left) / 2.0
        // Où range_x est effectivement scalé via mod_w
        val newRangeXHitbox = (scaledWidth * button.reachRight + scaledWidth * button.reachLeft) / 2.0f
        val newRangeYHitbox = (scaledHeight * button.reachDown + scaledHeight * button.reachUp) / 2.0f
        
        // Recalculer modX/modY avec les valeurs scalées (pour affichage)
        val newModX = newX - scaledWidth
        val newModY = newY - scaledHeight
        
        button.copy(
            x = newX,
            y = newY,
            modX = newModX,
            modY = newModY,
            modW = 2f * scaledWidth,  // modW doit aussi être scalé
            modH = 2f * scaledHeight, // modH doit aussi être scalé
            xHitboxOverride = newXHitbox,
            yHitboxOverride = newYHitbox,
            rangeXHitboxOverride = newRangeXHitbox,
            rangeYHitboxOverride = newRangeYHitbox
        )
    }
    
    return layout.copy(buttons = transformedButtons)
}

/**
 * Calculer la direction 8-way basée sur l'offset (x_dist, y_dist) depuis le centre
 * Identique à RetroArch input_overlay_get_eightway_state()
 * 
 * @param xDist Offset X depuis le centre (normalisé par range_x)
 * @param yDist Offset Y depuis le centre (normalisé par range_y)
 * @param diagonalSensitivity Sensibilité des diagonales (0-100, défaut 50)
 * @return Liste des actions à déclencher (ex: ["left", "up"] pour diagonal up-left)
 */
private fun get8WayDirections(
    xDist: Float,
    yDist: Float,
    diagonalSensitivity: Int = 50
): List<String> {
    // Calculer les slopes (pentes) pour définir les zones diagonales
    val f = 2.0f * diagonalSensitivity / (100.0f + diagonalSensitivity)
    val highAngle = f * (0.375 * Math.PI) + (1.0f - f) * (0.25 * Math.PI)  // 67.5 deg max
    val lowAngle = f * (0.125 * Math.PI) + (1.0f - f) * (0.25 * Math.PI)   // 22.5 deg min
    val slopeHigh = kotlin.math.tan(highAngle).toFloat()
    val slopeLow = kotlin.math.tan(lowAngle).toFloat()
    
    // Éviter division par zéro
    val xDistAdjusted = if (xDist == 0.0f) 0.0001f else xDist
    val absSlope = kotlin.math.abs(yDist / xDistAdjusted)
    
    // Déterminer le quadrant et la direction
    return when {
        xDist > 0.0f -> {
            when {
                yDist < 0.0f -> {
                    // Q1 (haut-droite)
                    when {
                        absSlope > slopeHigh -> listOf("up")
                        absSlope < slopeLow -> listOf("right")
                        else -> listOf("up", "right")  // diagonal up-right
                    }
                }
                else -> {
                    // Q4 (bas-droite)
                    when {
                        absSlope > slopeHigh -> listOf("down")
                        absSlope < slopeLow -> listOf("right")
                        else -> listOf("down", "right")  // diagonal down-right
                    }
                }
            }
        }
        else -> {
            when {
                yDist < 0.0f -> {
                    // Q2 (haut-gauche)
                    when {
                        absSlope > slopeHigh -> listOf("up")
                        absSlope < slopeLow -> listOf("left")
                        else -> listOf("up", "left")  // diagonal up-left
                    }
                }
                else -> {
                    // Q3 (bas-gauche)
                    when {
                        absSlope > slopeHigh -> listOf("down")
                        absSlope < slopeLow -> listOf("left")
                        else -> listOf("down", "left")  // diagonal down-left
                    }
                }
            }
        }
    }
}

