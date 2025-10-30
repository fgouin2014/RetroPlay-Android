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
    val visualOffsetY: Float = 0f   // Offset visuel pour movable (pixels)
)

@Composable
fun RetroArchOverlayScreen(
    layout: OverlayLayout,
    overlayName: String,
    assetManager: OverlayAssetManager,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit,
    onMenuToggle: () -> Unit = {},
    onAnalogMove: (String, Float, Float) -> Unit = { _, _, _ -> },  // Callback pour analog sticks (action, x, y)
    onHotkey: (String) -> Unit = {},  // Callback pour hotkeys RetroArch
    showDebug: Boolean = false,
    swapAnalogSticks: Boolean = false,
    invertAnalogY: Boolean = false,
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
    
    // État des boutons (tracking multi-touch)
    val pressedButtons = remember { mutableStateMapOf<Int, Set<OverlayButton>>() }
    
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
                handleTouchEvent(
                    event = event,
                    layout = scaledLayout,
                    screenSize = screenSize,
                    pressedButtons = pressedButtons,
                    analogLeftState = analogLeftState,
                    analogRightState = analogRightState,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease,
                    onLayoutSwitch = onLayoutSwitch,
                    onMenuToggle = onMenuToggle,
                    onAnalogMove = onAnalogMove,
                    onHotkey = onHotkey,
                    swapAnalogSticks = swapAnalogSticks,
                    invertAnalogY = invertAnalogY,
                    dpadDiagonalSensitivity = dpadDiagonalSensitivity,
                    abxyDiagonalSensitivity = abxyDiagonalSensitivity
                )
                true
            }
    ) {
        // Calculer le viewport basé sur aspect_ratio
        val viewport = calculateOverlayViewport(screenSize, scaledLayout.aspectRatio)
        
        // Canvas unique pour afficher TOUS les boutons
        Canvas(modifier = Modifier.fillMaxSize()) {
            scaledLayout.buttons.forEach { button ->
                // Convertir coordonnées normalisées → pixels (avec viewport aspect_ratio)
                // Scale/offset déjà appliqués dans scaledLayout!
                val (xPx, yPx) = normalizedToPixels(button.x, button.y, viewport)
                
                // CRITIQUE: Utiliser button.modW et button.modH pour l'affichage des IMAGES!
                // RetroArch utilise mod_w = 2.0 * range_x et mod_h = 2.0 * range_y
                // C'est pour ça que les images du D-pad se chevauchent et forment un D-pad compact!
                // IMPORTANT: Utiliser viewport.width() et height() (pas screenSize) pour respecter aspect_ratio!
                val displayWidthPx = button.modW * viewport.width() * overlayScale
                val displayHeightPx = button.modH * viewport.height() * overlayScale
                
                // Utiliser button.width/height (range_x/y) pour les HITBOXES uniquement
                // IMPORTANT: Utiliser button.rangeModifier si != 1.0, sinon layout.rangeModifier
                val effectiveRangeMod = if (button.rangeModifier != 1.0f) button.rangeModifier else scaledLayout.rangeModifier
                val hitboxWidthPx = button.width * viewport.width() * effectiveRangeMod * overlayScale
                val hitboxHeightPx = button.height * viewport.height() * effectiveRangeMod * overlayScale
                
                // Charger et afficher l'image du bouton
                button.imagePath?.let { path ->
                    val bitmap = assetManager.loadButtonImage(overlayName, path)
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        // IMPORTANT: Utiliser button.alphaModifier si défini, sinon layout.alphaModifier
                        val effectiveAlphaMod = button.alphaModifier ?: scaledLayout.alphaModifier
                        val baseAlpha = if (pressedButtons.values.any { it.contains(button) }) effectiveAlphaMod else (0.7f * effectiveAlphaMod)
                        val alpha = baseAlpha * overlayOpacity  // Appliquer opacity globale
                        
                        // IMPORTANT: Utiliser modW et modH pour la taille d'affichage!
                        // RetroArch calcule mod_w = 2.0 * range_x et mod_h = 2.0 * range_y
                        // C'est pour ça que les images du D-pad se chevauchent pour former un D-pad compact!
                        
                        // Position mod_x et mod_y (top-left corner de l'image)
                        // IMPORTANT: Utiliser viewport (pas screenSize) pour respecter aspect_ratio!
                        val (modXPx_base, modYPx_base) = normalizedToPixels(button.modX, button.modY, viewport)
                        var modXPx = modXPx_base
                        var modYPx = modYPx_base
                        
                        // MOVABLE: Si le bouton est movable (analog sticks), appliquer visualOffset
                        // L'image du stick suit le doigt dans la limite du rayon
                        if (button.movable) {
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
                
                // MODE DEBUG: Afficher les hitboxes (utiliser hitboxWidthPx/hitboxHeightPx, PAS displayWidthPx!)
                if (showDebug) {
                    // CRITIQUE: Utiliser xHitbox/yHitbox pour la POSITION (pas x/y!)
                    // Les hitboxes peuvent être décalées si reach asymétrique
                    val (hitboxX, hitboxY) = normalizedToPixels(button.xHitbox, button.yHitbox, viewport)
                    
                    // Couleur debug selon type:
                    // - ROUGE: range_mod_exclusive = true (zone exclusive, bloque autres touches)
                    // - VERT: analog sticks
                    // - BLEU: boutons normaux
                    val debugColor = when {
                        button.rangeModExclusive -> Color.Red
                        button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT -> Color.Green
                        else -> Color.Blue
                    }
                    
                    when (button.shape) {
                        ButtonShape.RADIAL -> {
                            drawCircle(
                                color = debugColor,
                                radius = (hitboxWidthPx / 2).coerceAtLeast(hitboxHeightPx / 2),
                                center = Offset(hitboxX, hitboxY),
                                alpha = 0.5f
                            )
                        }
                        ButtonShape.RECT -> {
                            drawRect(
                                color = debugColor,
                                topLeft = Offset(hitboxX - hitboxWidthPx / 2, hitboxY - hitboxHeightPx / 2),
                                size = Size(hitboxWidthPx, hitboxHeightPx),
                                alpha = 0.5f
                            )
                        }
                    }
                }
            }
            
            // MODE DEBUG: Afficher la position actuelle des analog sticks
            if (showDebug) {
                // Stick gauche
                val leftStick = scaledLayout.buttons.find { it.type == OverlayButtonType.ANALOG_LEFT }
                if (leftStick != null && (analogLeftState.value.x != 0f || analogLeftState.value.y != 0f)) {
                    val centerX = leftStick.x * screenSize.width
                    val centerY = leftStick.y * screenSize.height
                    val radius = leftStick.width * screenSize.width * scaledLayout.rangeModifier * overlayScale
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
                    val centerX = rightStick.x * screenSize.width
                    val centerY = rightStick.y * screenSize.height
                    val radius = rightStick.width * screenSize.width * scaledLayout.rangeModifier * overlayScale
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
                        val xPx = pressedButton.x * screenSize.width
                        val yPx = pressedButton.y * screenSize.height
                        val wPx = pressedButton.width * screenSize.width * scaledLayout.rangeModifier
                        val hPx = pressedButton.height * screenSize.height * scaledLayout.rangeModifier
                        
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
private fun handleTouchEvent(
    event: MotionEvent,
    layout: OverlayLayout,
    screenSize: IntSize,
    pressedButtons: MutableMap<Int, Set<OverlayButton>>,
    analogLeftState: MutableState<AnalogStickState>,
    analogRightState: MutableState<AnalogStickState>,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit,
    onMenuToggle: () -> Unit,
    onAnalogMove: (String, Float, Float) -> Unit,
    onHotkey: (String) -> Unit,
    swapAnalogSticks: Boolean = false,
    invertAnalogY: Boolean = false,
    dpadDiagonalSensitivity: Int = 50,
    abxyDiagonalSensitivity: Int = 50
): Boolean {
    val TAG = "TouchHandler"
    val ANALOG_DEADZONE = 0.15f  // 15% dead zone (zone morte)
    
    // Calculer le viewport basé sur aspect_ratio (pour tout le touch handling)
    val viewport = calculateOverlayViewport(screenSize, layout.aspectRatio)
    
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            val x = event.getX(pointerIndex)
            val y = event.getY(pointerIndex)
            
            // D'abord vérifier si c'est un analog stick
            // Pour analog sticks : utiliser button.rangeModifier même pour détection initiale (large zone tactile)
            val analogStick = layout.buttons.find { button ->
                (button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT) &&
                isTouchInsideButton(x, y, button, button.rangeModifier, viewport)
            }
            
            // DEBUG: Log pour comprendre pourquoi les analog sticks ne sont pas détectés
            if (analogStick == null) {
                val allAnalogSticks = layout.buttons.filter { it.type == OverlayButtonType.ANALOG_LEFT || it.type == OverlayButtonType.ANALOG_RIGHT }
                if (allAnalogSticks.isNotEmpty()) {
                    Log.w(TAG, "❌ NO ANALOG DETECTED at ($x,$y). Available analog sticks:")
                    allAnalogSticks.forEach { stick ->
                        val centerX = stick.x * screenSize.width
                        val centerY = stick.y * screenSize.height
                        val radius = stick.width * screenSize.width * stick.rangeModifier
                        val distance = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
                        Log.w(TAG, "  ${stick.type}: center=($centerX,$centerY) radius=$radius distance=$distance action='${stick.action}'")
                    }
                }
            }
            
            if (analogStick != null) {
                // C'est un analog stick
                val values = calculateAnalogValues(x, y, analogStick, viewport, ANALOG_DEADZONE, layout.rangeModifier, invertAnalogY)
                if (values != null) {
                    // Calculer l'offset visuel pour movable buttons
                    val (visualOffsetX, visualOffsetY) = if (analogStick.movable) {
                        val (centerX, centerY) = normalizedToPixels(analogStick.x, analogStick.y, viewport)
                        // Range de BASE en pixels (sans modifiers, juste width/height)
                        // RetroArch limite le delta visuel au range de base, pas au range étendu pour hitbox
                        val baseRangeX = analogStick.width * viewport.width()
                        val baseRangeY = analogStick.height * viewport.height()
                        val dx = x - centerX
                        val dy = y - centerY
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
                            analogLeftState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true, visualOffsetX, visualOffsetY)
                            val actionName = if (swapAnalogSticks) "analog_right" else "analog_left"
                            onAnalogMove(actionName, values.first, values.second)
                            Log.d(TAG, "Analog LEFT (sent as $actionName): x=${String.format("%.2f", values.first)}, y=${String.format("%.2f", values.second)}")
                        }
                        OverlayButtonType.ANALOG_RIGHT -> {
                            analogRightState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true, visualOffsetX, visualOffsetY)
                            val actionName = if (swapAnalogSticks) "analog_left" else "analog_right"
                            onAnalogMove(actionName, values.first, values.second)
                            Log.d(TAG, "Analog RIGHT (sent as $actionName): x=${String.format("%.2f", values.first)}, y=${String.format("%.2f", values.second)}")
                        }
                        else -> {}
                    }
                }
                // Ne pas traiter comme bouton normal
                return true
            }
            
            // Si ce n'est pas un analog stick, traiter comme bouton normal
            val touchedButtons = detectButtonsAtPosition(
                x, y, layout, screenSize, viewport, dpadDiagonalSensitivity, abxyDiagonalSensitivity
            )
            
            // DEBUG: Log si plusieurs boutons détectés (chevauchement potentiel)
            if (touchedButtons.size > 1) {
                Log.w(TAG, "OVERLAP: ${touchedButtons.size} buttons detected at ($x,$y): ${touchedButtons.joinToString()}")
            }
            
            // Enregistrer les boutons pressés pour ce pointeur
            pressedButtons[pointerId] = touchedButtons
            
            // Déclencher les callbacks
            touchedButtons.forEach { button ->
                // Actions spéciales (overlay control)
                if (RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                    if (button.action.startsWith("overlay_next") && button.nextTarget != null) {
                        // Tous les boutons overlay_next changent de layout (RetroArch officiel)
                        Log.i(TAG, "SYSTEM BUTTON: target='${button.nextTarget}' | Img='${button.imagePath}' | Normalized center: (${button.x}, ${button.y}) | Touch px: ($x, $y)")
                        onLayoutSwitch(button.nextTarget)
                    } else if (button.action == "menu_toggle") {
                        Log.i(TAG, "SYSTEM BUTTON: MENU | Img='${button.imagePath}' | Normalized center: (${button.x}, ${button.y}) | Touch px: ($x, $y)")
                        onMenuToggle()
                    }
                } else if (RetroArchButtonMapping.isHotkeyAction(button.action)) {
                    // Hotkeys RetroArch (save/load/rewind/fast_forward, etc.)
                    Log.i(TAG, "HOTKEY: ${button.action} | Img='${button.imagePath}' | Touch px: ($x, $y)")
                    onHotkey(button.action)
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
                
                if (analogLeftState.value.pointerId == pointerId) {
                    // Ce doigt contrôle le stick gauche
                    val leftStick = layout.buttons.find { it.type == OverlayButtonType.ANALOG_LEFT }
                    if (leftStick != null) {
                        val values = calculateAnalogValues(x, y, leftStick, viewport, ANALOG_DEADZONE, layout.rangeModifier, invertAnalogY)
                        if (values != null) {
                            // Calculer l'offset visuel pour movable buttons
                            val (visualOffsetX, visualOffsetY) = if (leftStick.movable) {
                                val (centerX, centerY) = normalizedToPixels(leftStick.x, leftStick.y, viewport)
                                val baseRangeX = leftStick.width * viewport.width()
                                val baseRangeY = leftStick.height * viewport.height()
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
                        val values = calculateAnalogValues(x, y, rightStick, viewport, ANALOG_DEADZONE, layout.rangeModifier, invertAnalogY)
                        if (values != null) {
                            // Calculer l'offset visuel pour movable buttons
                            val (visualOffsetX, visualOffsetY) = if (rightStick.movable) {
                                val (centerX, centerY) = normalizedToPixels(rightStick.x, rightStick.y, viewport)
                                val baseRangeX = rightStick.width * viewport.width()
                                val baseRangeY = rightStick.height * viewport.height()
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
                    val currentButtons = detectButtonsAtPosition(x, y, layout, screenSize, viewport, dpadDiagonalSensitivity, abxyDiagonalSensitivity)
                    val previousButtons = pressedButtons[pointerId] ?: emptySet()
                    
                    // Boutons nouvellement pressés
                    val newButtons = currentButtons - previousButtons
                    newButtons.forEach { button ->
                        if (RetroArchButtonMapping.isHotkeyAction(button.action)) {
                            Log.i(TAG, "HOTKEY (move): ${button.action}")
                            onHotkey(button.action)
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
            releasedButtons.forEach { button ->
                if (!RetroArchButtonMapping.isOverlayControlAction(button.action) && !RetroArchButtonMapping.isHotkeyAction(button.action)) {
                    Log.d(TAG, "Button released: ${button.action}")
                    onButtonRelease(button.action)
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
                if (!RetroArchButtonMapping.isOverlayControlAction(button.action) && !RetroArchButtonMapping.isHotkeyAction(button.action)) {
                    Log.d(TAG, "Button released (cancel): ${button.action}")
                    onButtonRelease(button.action)
                }
            }
            pressedButtons.clear()
        }
    }
    
    return true
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
    viewport: android.graphics.RectF,
    deadzone: Float,
    layoutRangeMod: Float = 1.5f,
    invertY: Boolean = false
): Pair<Float, Float>? {
    // Centre du stick en pixels (x_shift, y_shift dans RetroArch)
    val (centerX, centerY) = normalizedToPixels(button.x, button.y, viewport)
    
    // Range (rayon) en pixels pour le calcul des valeurs
    // Utiliser layout.rangeModifier (même que le rendu visuel) pour cohérence
    val rangeX = button.width * viewport.width() * layoutRangeMod
    val rangeY = button.height * viewport.height() * layoutRangeMod
    
    // Distance depuis le centre (x_dist, y_dist dans RetroArch)
    val xDist = touchX - centerX
    val yDist = touchY - centerY
    
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
 */
private fun detectButtonsAtPosition(
    x: Float,
    y: Float,
    layout: OverlayLayout,
    screenSize: IntSize,
    viewport: android.graphics.RectF,
    dpadDiagonalSensitivity: Int = 50,
    abxyDiagonalSensitivity: Int = 50
): Set<OverlayButton> {
    val touched = mutableSetOf<OverlayButton>()
    
    layout.buttons.forEach { button ->
        // Skip analog sticks (gérés séparément)
        if (button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT) {
            return@forEach
        }
        
        // Traiter les zones 8-way (dpad_area, abxy_area)
        if (button.type == OverlayButtonType.DPAD_AREA || button.type == OverlayButtonType.ABXY_AREA) {
            // Vérifier si le touch est dans la zone
            if (isTouchInsideButton(x, y, button, layout.rangeModifier, viewport)) {
                // Calculer l'offset depuis le centre
                val (centerX, centerY) = normalizedToPixels(button.x, button.y, viewport)
                val xDist = (x - centerX) / (button.width * viewport.width())  // Normalisé
                val yDist = (y - centerY) / (button.height * viewport.height())  // Normalisé
                
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
                    
                    // Créer un bouton virtuel pour cette direction
                    if (mappedAction != null) {
                        val virtualButton = button.copy(action = mappedAction)
                        touched.add(virtualButton)
                    }
                }
            }
            return@forEach
        }
        
        // Skip boutons purement décoratifs (action="nul" ou "null")
        if (button.action == "nul" || button.action == "null") {
            return@forEach
        }
        
        // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) ET layout.rangeModifier pour les boutons normaux
        // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod
        if (isTouchInsideButton(x, y, button, layout.rangeModifier, viewport)) {
            touched.add(button)
            
            // DEBUG: Log détaillé de la détection
            val buttonX = button.x * screenSize.width
            val buttonY = button.y * screenSize.height
            val buttonWidth = button.rangeXHitbox * screenSize.width * layout.rangeModifier
            val buttonHeight = button.rangeYHitbox * screenSize.height * layout.rangeModifier
            Log.d("TouchHandler", "HIT: ${button.action} | Touch: ($x,$y) | Center: ($buttonX,$buttonY) | Size: ${buttonWidth.toInt()}x${buttonHeight.toInt()} | Shape: ${button.shape}")
        }
    }
    
    return touched
}

/**
 * Vérifier si un touch est à l'intérieur d'un bouton
 */
private fun isTouchInsideButton(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    rangeModifier: Float,
    viewport: android.graphics.RectF
): Boolean {
    // CRITIQUE: Utiliser x_hitbox et y_hitbox (PAS x et y!) pour la position de la hitbox
    // La hitbox peut être décalée si reach_left != reach_right ou reach_up != reach_down
    // Identique à RetroArch input_overlay_desc_init_hitbox() lignes 2641-2655
    val (buttonX, buttonY) = normalizedToPixels(button.xHitbox, button.yHitbox, viewport)
    
    // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) pour la TAILLE
    // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod
    val buttonWidth = button.rangeXHitbox * viewport.width() * rangeModifier
    val buttonHeight = button.rangeYHitbox * viewport.height() * rangeModifier
    
    val isInside = when (button.shape) {
        ButtonShape.RADIAL -> {
            // Hitbox elliptique
            val dx = (touchX - buttonX) / (buttonWidth / 2)
            val dy = (touchY - buttonY) / (buttonHeight / 2)
            val distance = sqrt(dx * dx + dy * dy)
            distance <= 1.0f
        }
        ButtonShape.RECT -> {
            // Hitbox rectangulaire
            val left = buttonX - buttonWidth / 2
            val right = buttonX + buttonWidth / 2
            val top = buttonY - buttonHeight / 2
            val bottom = buttonY + buttonHeight / 2
            
            touchX >= left && touchX <= right && touchY >= top && touchY <= bottom
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
            }
            
            // Label du bouton (debug)
            // Note: drawText nécessite TextMeasurer (Material3)
        }
    }
}

/**
 * Calculer le viewport rect basé sur aspect_ratio
 * Si aspect_ratio est défini, on contraindra l'overlay dans un rectangle respectant ce ratio
 * 
 * RetroArch comportement:
 * - En landscape: viewport prend toute la largeur, hauteur ajustée selon ratio
 * - En portrait: viewport prend toute la largeur, hauteur ajustée, puis collé sous l'écran
 * 
 * @param screenSize Taille de l'écran en pixels
 * @param aspectRatio Ratio souhaité (ex: 1.77778 pour 16:9), ou null si pas de contrainte
 * @return Rect(x, y, width, height) en pixels du viewport overlay
 */
private fun calculateOverlayViewport(
    screenSize: IntSize,
    aspectRatio: Float?
): android.graphics.RectF {
    if (aspectRatio == null || aspectRatio <= 0f) {
        // Pas de contrainte: utiliser tout l'écran
        android.util.Log.i("OverlayViewport", "No aspect_ratio constraint, using fullscreen: ${screenSize.width}x${screenSize.height}")
        return android.graphics.RectF(0f, 0f, screenSize.width.toFloat(), screenSize.height.toFloat())
    }
    
    val screenAspect = screenSize.width.toFloat() / screenSize.height.toFloat()
    android.util.Log.i("OverlayViewport", "aspect_ratio=$aspectRatio, screen=${screenSize.width}x${screenSize.height}, screenAspect=$screenAspect")
    
    return if (screenAspect > aspectRatio) {
        // Écran plus large que l'overlay aspect → limiter la largeur
        // Ex: overlay 16:9 sur écran ultra-wide
        val viewportWidth = screenSize.height * aspectRatio
        val xOffset = (screenSize.width - viewportWidth) / 2f  // Centrer horizontalement
        val rect = android.graphics.RectF(xOffset, 0f, xOffset + viewportWidth, screenSize.height.toFloat())
        android.util.Log.i("OverlayViewport", "Screen WIDER than aspect → viewport: x=$xOffset, width=$viewportWidth, height=${screenSize.height}")
        rect
    } else {
        // Écran plus haut que l'overlay aspect → limiter la hauteur
        // Ex: overlay 16:9 landscape (1.77778) sur écran portrait (0.5625)
        // Les contrôles seront compressés verticalement
        val viewportHeight = screenSize.width / aspectRatio
        val yOffset = 0f  // Collé en haut (sous l'écran de jeu)
        val rect = android.graphics.RectF(0f, yOffset, screenSize.width.toFloat(), yOffset + viewportHeight)
        android.util.Log.i("OverlayViewport", "Screen TALLER than aspect → viewport: y=$yOffset, width=${screenSize.width}, height=$viewportHeight")
        rect
    }
}

/**
 * Convertir coordonnée normalisée (0.0-1.0) vers pixels en tenant compte du viewport
 */
private fun normalizedToPixels(
    normalizedX: Float,
    normalizedY: Float,
    viewport: android.graphics.RectF
): Pair<Float, Float> {
    val x = viewport.left + normalizedX * viewport.width()
    val y = viewport.top + normalizedY * viewport.height()
    return Pair(x, y)
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
        
        // Recalculer modX/modY ET xHitbox/yHitbox avec les nouvelles positions
        // CRITIQUE: x_hitbox dépend de x_shift (pas x), donc recalculer!
        val newXHitbox = ((newX + button.width * button.reachRight) + (newX - button.width * button.reachLeft)) / 2.0f
        val newYHitbox = ((newY + button.height * button.reachDown) + (newY - button.height * button.reachUp)) / 2.0f
        
        button.copy(
            x = newX,
            y = newY,
            modX = newX - button.width,
            modY = newY - button.height,
            xHitboxOverride = newXHitbox,
            yHitboxOverride = newYHitbox
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

