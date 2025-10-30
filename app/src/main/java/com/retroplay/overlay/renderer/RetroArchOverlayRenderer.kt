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
    val isActivated: Boolean = false  // true si le stick a été touché (permet hitbox étendue)
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
    showDebug: Boolean = false,
    modifier: Modifier = Modifier
) {
    val TAG = "RetroArchOverlay"
    
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
                Log.d(TAG, "Layout: ${layout.name} | Buttons: ${layout.buttons.size} | RangeMod: ${layout.rangeModifier} | AlphaMod: ${layout.alphaModifier}")
                
                // DEBUG: Afficher TOUS les boutons système dans le layout
                val systemButtons = layout.buttons.filter { 
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
                    layout = layout,
                    screenSize = screenSize,
                    pressedButtons = pressedButtons,
                    analogLeftState = analogLeftState,
                    analogRightState = analogRightState,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease,
                    onLayoutSwitch = onLayoutSwitch,
                    onMenuToggle = onMenuToggle,
                    onAnalogMove = onAnalogMove
                )
                true
            }
    ) {
        // Canvas unique pour afficher TOUS les boutons
        Canvas(modifier = Modifier.fillMaxSize()) {
            layout.buttons.forEach { button ->
                // Convertir coordonnées normalisées → pixels
                val xPx = button.x * screenSize.width
                val yPx = button.y * screenSize.height
                // Utiliser layout.rangeModifier pour le rendu visuel (tous les boutons)
                // button.rangeModifier est utilisé UNIQUEMENT pour le calcul des valeurs, pas le rendu
                val widthPx = button.width * screenSize.width * layout.rangeModifier
                val heightPx = button.height * screenSize.height * layout.rangeModifier
                
                // Charger et afficher l'image du bouton
                button.imagePath?.let { path ->
                    val bitmap = assetManager.loadButtonImage(overlayName, path)
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        val alpha = if (pressedButtons.values.any { it.contains(button) }) 1.0f else (0.7f * layout.alphaModifier)
                        
                        // Position top-left depuis le centre
                        val topLeft = Offset(
                            x = xPx - widthPx / 2,
                            y = yPx - heightPx / 2
                        )
                        
                        drawImage(
                            image = imageBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                x = topLeft.x.toInt(),
                                y = topLeft.y.toInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                width = widthPx.toInt(),
                                height = heightPx.toInt()
                            ),
                            alpha = alpha
                        )
                    }
                }
                
                // MODE DEBUG: Afficher les hitboxes
                if (showDebug) {
                    val debugColor = when (button.type) {
                        OverlayButtonType.ANALOG_LEFT, OverlayButtonType.ANALOG_RIGHT -> Color.Green
                        else -> Color.Red
                    }
                    
                    when (button.shape) {
                        ButtonShape.RADIAL -> {
                            drawCircle(
                                color = debugColor,
                                radius = (widthPx / 2).coerceAtLeast(heightPx / 2),
                                center = Offset(xPx, yPx),
                                alpha = 0.5f
                            )
                        }
                        ButtonShape.RECT -> {
                            drawRect(
                                color = Color.Blue,
                                topLeft = Offset(xPx - widthPx / 2, yPx - heightPx / 2),
                                size = Size(widthPx, heightPx),
                                alpha = 0.5f
                            )
                        }
                    }
                }
            }
            
            // MODE DEBUG: Afficher la position actuelle des analog sticks
            if (showDebug) {
                // Stick gauche
                val leftStick = layout.buttons.find { it.type == OverlayButtonType.ANALOG_LEFT }
                if (leftStick != null && (analogLeftState.value.x != 0f || analogLeftState.value.y != 0f)) {
                    val centerX = leftStick.x * screenSize.width
                    val centerY = leftStick.y * screenSize.height
                    val radius = leftStick.width * screenSize.width * layout.rangeModifier
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
                val rightStick = layout.buttons.find { it.type == OverlayButtonType.ANALOG_RIGHT }
                if (rightStick != null && (analogRightState.value.x != 0f || analogRightState.value.y != 0f)) {
                    val centerX = rightStick.x * screenSize.width
                    val centerY = rightStick.y * screenSize.height
                    val radius = rightStick.width * screenSize.width * layout.rangeModifier
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
    onAnalogMove: (String, Float, Float) -> Unit
): Boolean {
    val TAG = "TouchHandler"
    val ANALOG_DEADZONE = 0.15f  // 15% dead zone (zone morte)
    
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
                isTouchInsideButton(x, y, button, button.rangeModifier, screenSize)
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
                val values = calculateAnalogValues(x, y, analogStick, screenSize, ANALOG_DEADZONE, layout.rangeModifier)
                if (values != null) {
                    // Enregistrer le pointerId et activer le stick pour permettre hitbox étendue
                    when (analogStick.type) {
                        OverlayButtonType.ANALOG_LEFT -> {
                            analogLeftState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true)
                            onAnalogMove("analog_left", values.first, values.second)
                            Log.d(TAG, "Analog LEFT: x=${String.format("%.2f", values.first)}, y=${String.format("%.2f", values.second)}")
                        }
                        OverlayButtonType.ANALOG_RIGHT -> {
                            analogRightState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true)
                            onAnalogMove("analog_right", values.first, values.second)
                            Log.d(TAG, "Analog RIGHT: x=${String.format("%.2f", values.first)}, y=${String.format("%.2f", values.second)}")
                        }
                        else -> {}
                    }
                }
                // Ne pas traiter comme bouton normal
                return true
            }
            
            // Si ce n'est pas un analog stick, traiter comme bouton normal
            val touchedButtons = detectButtonsAtPosition(
                x, y, layout, screenSize
            )
            
            // DEBUG: Log si plusieurs boutons détectés (chevauchement potentiel)
            if (touchedButtons.size > 1) {
                Log.w(TAG, "OVERLAP: ${touchedButtons.size} buttons detected at ($x,$y): ${touchedButtons.joinToString()}")
            }
            
            // Enregistrer les boutons pressés pour ce pointeur
            pressedButtons[pointerId] = touchedButtons
            
            // Déclencher les callbacks
            touchedButtons.forEach { button ->
                // Actions spéciales
                if (RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                    if (button.action.startsWith("overlay_next") && button.nextTarget != null) {
                        // Tous les boutons overlay_next changent de layout (RetroArch officiel)
                        Log.i(TAG, "SYSTEM BUTTON: target='${button.nextTarget}' | Img='${button.imagePath}' | Normalized center: (${button.x}, ${button.y}) | Touch px: ($x, $y)")
                        onLayoutSwitch(button.nextTarget)
                    } else if (button.action == "menu_toggle") {
                        Log.i(TAG, "SYSTEM BUTTON: MENU | Img='${button.imagePath}' | Normalized center: (${button.x}, ${button.y}) | Touch px: ($x, $y)")
                        onMenuToggle()
                    }
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
                        val values = calculateAnalogValues(x, y, leftStick, screenSize, ANALOG_DEADZONE, layout.rangeModifier)
                        if (values != null) {
                            analogLeftState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true)
                            onAnalogMove("analog_left", values.first, values.second)
                        }
                        handledByAnalog = true
                    }
                }
                
                if (analogRightState.value.pointerId == pointerId) {
                    // Ce doigt contrôle le stick droit
                    val rightStick = layout.buttons.find { it.type == OverlayButtonType.ANALOG_RIGHT }
                    if (rightStick != null) {
                        val values = calculateAnalogValues(x, y, rightStick, screenSize, ANALOG_DEADZONE, layout.rangeModifier)
                        if (values != null) {
                            analogRightState.value = AnalogStickState(values.first, values.second, pointerId, isActivated = true)
                            onAnalogMove("analog_right", values.first, values.second)
                        }
                        handledByAnalog = true
                    }
                }
                
                // Si pas géré par analog, traiter comme bouton normal
                if (!handledByAnalog) {
                    val currentButtons = detectButtonsAtPosition(x, y, layout, screenSize)
                    val previousButtons = pressedButtons[pointerId] ?: emptySet()
                    
                    // Boutons nouvellement pressés
                    val newButtons = currentButtons - previousButtons
                    newButtons.forEach { button ->
                        if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
                            Log.d(TAG, "Button pressed (move): ${button.action}")
                            onButtonPress(button.action)
                        }
                    }
                    
                    // Boutons relâchés
                    val releasedButtons = previousButtons - currentButtons
                    releasedButtons.forEach { button ->
                        if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
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
            
            // Relâcher tous les boutons de ce pointeur
            val releasedButtons = pressedButtons[pointerId] ?: emptySet()
            releasedButtons.forEach { button ->
                if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
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
            
            // Relâcher tous les boutons
            pressedButtons.flatMap { it.value }.distinct().forEach { button ->
                if (!RetroArchButtonMapping.isOverlayControlAction(button.action)) {
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
 * @return Pair(x, y) normalisées entre -1.0 et 1.0, ou null si hors dead zone
 */
private fun calculateAnalogValues(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    screenSize: IntSize,
    deadzone: Float,
    layoutRangeMod: Float = 1.5f
): Pair<Float, Float>? {
    // Centre du stick en pixels (x_shift, y_shift dans RetroArch)
    val centerX = button.x * screenSize.width
    val centerY = button.y * screenSize.height
    
    // Range (rayon) en pixels pour le calcul des valeurs
    // Utiliser layout.rangeModifier (même que le rendu visuel) pour cohérence
    val rangeX = button.width * screenSize.width * layoutRangeMod
    val rangeY = button.height * screenSize.height * layoutRangeMod
    
    // Distance depuis le centre (x_dist, y_dist dans RetroArch)
    val xDist = touchX - centerX
    val yDist = touchY - centerY
    
    // Valeurs normalisées comme RetroArch
    val xVal = xDist / rangeX
    val yVal = yDist / rangeY
    
    // Saturation (analog_saturate_pct = 1.0 par défaut dans RetroArch)
    // Nous pourrions parser ce paramètre depuis les .cfg si nécessaire
    val saturate_pct = 1.0f
    val xValSat = xVal / saturate_pct
    val yValSat = yVal / saturate_pct
    
    // Clamp entre -1.0 et 1.0 (comme RetroArch)
    val finalX = xValSat.coerceIn(-1.0f, 1.0f)
    val finalY = yValSat.coerceIn(-1.0f, 1.0f)
    
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
    screenSize: IntSize
): Set<OverlayButton> {
    val touched = mutableSetOf<OverlayButton>()
    
    layout.buttons.forEach { button ->
        // Skip analog sticks (gérés séparément)
        if (button.type == OverlayButtonType.ANALOG_LEFT || button.type == OverlayButtonType.ANALOG_RIGHT) {
            return@forEach
        }
        
        // Skip zones tactiles et boutons décoratifs (dpad_area, abxy_area, "nul")
        if (button.type == OverlayButtonType.DPAD_AREA || button.type == OverlayButtonType.ABXY_AREA) {
            return@forEach
        }
        
        // Skip boutons purement décoratifs (action="nul" ou "null")
        if (button.action == "nul" || button.action == "null") {
            return@forEach
        }
        
        // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) ET layout.rangeModifier pour les boutons normaux
        // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod
        if (isTouchInsideButton(x, y, button, layout.rangeModifier, screenSize)) {
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
    screenSize: IntSize
): Boolean {
    // Convertir coordonnées normalisées → pixels
    val buttonX = button.x * screenSize.width
    val buttonY = button.y * screenSize.height
    // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) au lieu de width/height brut
    // Identique à RetroArch: range_x_mod = range_x_hitbox * range_mod
    val buttonWidth = button.rangeXHitbox * screenSize.width * rangeModifier
    val buttonHeight = button.rangeYHitbox * screenSize.height * rangeModifier
    
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

