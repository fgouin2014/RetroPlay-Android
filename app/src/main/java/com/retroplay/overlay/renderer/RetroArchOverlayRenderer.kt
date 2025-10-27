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
@Composable
fun RetroArchOverlayScreen(
    layout: OverlayLayout,
    overlayName: String,
    assetManager: OverlayAssetManager,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit,
    onMenuToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val TAG = "RetroArchOverlay"
    
    // Taille de l'écran
    var screenSize by remember { mutableStateOf(IntSize(1920, 1080)) }
    
    // État des boutons (tracking multi-touch)
    val pressedButtons = remember { mutableStateMapOf<Int, Set<String>>() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                screenSize = size
                Log.d(TAG, "Screen size: ${size.width}x${size.height}")
            }
            .pointerInteropFilter { event ->
                handleTouchEvent(
                    event = event,
                    layout = layout,
                    screenSize = screenSize,
                    pressedButtons = pressedButtons,
                    onButtonPress = onButtonPress,
                    onButtonRelease = onButtonRelease,
                    onLayoutSwitch = onLayoutSwitch,
                    onMenuToggle = onMenuToggle
                )
                true
            }
    ) {
        // Afficher chaque bouton
        layout.buttons.forEach { button ->
            RetroArchButton(
                button = button,
                overlayName = overlayName,
                assetManager = assetManager,
                screenSize = screenSize,
                rangeModifier = layout.rangeModifier,
                alphaModifier = layout.alphaModifier,
                isPressed = pressedButtons.values.any { it.contains(button.action) }
            )
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
    pressedButtons: MutableMap<Int, Set<String>>,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit,
    onMenuToggle: () -> Unit
): Boolean {
    val TAG = "TouchHandler"
    
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            val x = event.getX(pointerIndex)
            val y = event.getY(pointerIndex)
            
            // Détecter quels boutons sont touchés
            val touchedButtons = detectButtonsAtPosition(
                x, y, layout, screenSize
            )
            
            // Enregistrer les boutons pressés pour ce pointeur
            pressedButtons[pointerId] = touchedButtons
            
            // Déclencher les callbacks
            touchedButtons.forEach { action ->
                // Actions spéciales
                if (RetroArchButtonMapping.isOverlayControlAction(action)) {
                    val button = layout.buttons.find { it.action == action }
                    if (action.startsWith("overlay_next") && button?.nextTarget != null) {
                        Log.i(TAG, "Layout switch requested: ${button.nextTarget}")
                        onLayoutSwitch(button.nextTarget)
                    } else if (action == "menu_toggle") {
                        Log.i(TAG, "Menu toggle pressed - opening main menu")
                        onMenuToggle()
                    }
                } else {
                    // Actions normales (boutons gamepad)
                    Log.d(TAG, "Button pressed: $action")
                    onButtonPress(action)
                }
            }
        }
        
        MotionEvent.ACTION_MOVE -> {
            // Vérifier chaque pointeur actif
            for (i in 0 until event.pointerCount) {
                val pointerId = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                
                val currentButtons = detectButtonsAtPosition(x, y, layout, screenSize)
                val previousButtons = pressedButtons[pointerId] ?: emptySet()
                
                // Boutons nouvellement pressés
                val newButtons = currentButtons - previousButtons
                newButtons.forEach { action ->
                    if (!RetroArchButtonMapping.isOverlayControlAction(action)) {
                        Log.d(TAG, "Button pressed (move): $action")
                        onButtonPress(action)
                    }
                }
                
                // Boutons relâchés
                val releasedButtons = previousButtons - currentButtons
                releasedButtons.forEach { action ->
                    if (!RetroArchButtonMapping.isOverlayControlAction(action)) {
                        Log.d(TAG, "Button released (move): $action")
                        onButtonRelease(action)
                    }
                }
                
                pressedButtons[pointerId] = currentButtons
            }
        }
        
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)
            
            // Relâcher tous les boutons de ce pointeur
            val releasedButtons = pressedButtons[pointerId] ?: emptySet()
            releasedButtons.forEach { action ->
                if (!RetroArchButtonMapping.isOverlayControlAction(action)) {
                    Log.d(TAG, "Button released: $action")
                    onButtonRelease(action)
                }
            }
            
            pressedButtons.remove(pointerId)
        }
        
        MotionEvent.ACTION_CANCEL -> {
            // Relâcher tous les boutons
            pressedButtons.flatMap { it.value }.distinct().forEach { action ->
                if (!RetroArchButtonMapping.isOverlayControlAction(action)) {
                    Log.d(TAG, "Button released (cancel): $action")
                    onButtonRelease(action)
                }
            }
            pressedButtons.clear()
        }
    }
    
    return true
}

/**
 * Détecter quels boutons sont touchés à une position donnée
 */
private fun detectButtonsAtPosition(
    x: Float,
    y: Float,
    layout: OverlayLayout,
    screenSize: IntSize
): Set<String> {
    val touched = mutableSetOf<String>()
    
    layout.buttons.forEach { button ->
        if (isTouchInsideButton(x, y, button, layout.rangeModifier, screenSize)) {
            touched.add(button.action)
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
    val buttonWidth = button.width * screenSize.width * rangeModifier
    val buttonHeight = button.height * screenSize.height * rangeModifier
    
    return when (button.shape) {
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
            val x = button.x * screenSize.width
            val y = button.y * screenSize.height
            val width = button.width * screenSize.width * layout.rangeModifier
            val height = button.height * screenSize.height * layout.rangeModifier
            
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

