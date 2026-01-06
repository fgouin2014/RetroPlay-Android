package com.retroplay

import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter

/**
 * ZapperCrosshair - Réticule de visée pour le mode Zapper (NES light gun)
 * 
 * Affiche un réticule rouge qui suit le doigt de l'utilisateur.
 * S'affiche uniquement pendant que l'utilisateur touche l'écran.
 * 
 * @param onTouch Callback appelé pour chaque événement tactile
 * @param visible Si false, le crosshair est masqué (pour mode FCEUmm Only ou None)
 */
@Composable
fun ZapperCrosshair(
    onTouch: (MotionEvent) -> Boolean,
    visible: Boolean = true
) {
    // État pour suivre la position du doigt
    var touchX by remember { mutableStateOf(0f) }
    var touchY by remember { mutableStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                // Mettre à jour la position du réticule
                touchX = event.x
                touchY = event.y
                isTouching = event.actionMasked == MotionEvent.ACTION_DOWN || 
                             event.actionMasked == MotionEvent.ACTION_MOVE
                
                // Envoyer l'événement au handler et RETOURNER sa valeur
                // CRITIQUE: Retourner la valeur pour que le pointerInteropFilter consomme ou laisse passer l'événement
                val handled = onTouch(event)
                Log.d("ZapperCrosshair", "[CROSSHAIR] Touch event: action=${event.actionMasked}, handled=$handled")
                handled
            }
    ) {
        // Afficher le réticule uniquement quand l'utilisateur touche l'écran ET que visible=true
        if (isTouching && visible) {
            val crosshairSize = 50f // Longueur des lignes du réticule
            val strokeWidth = 4f
            val centerDotRadius = 6f
            
            // Couleur rouge vif pour la visibilité
            val crosshairColor = Color.Red
            
            // Ligne horizontale (gauche)
            drawLine(
                color = crosshairColor,
                start = Offset(touchX - crosshairSize, touchY),
                end = Offset(touchX - 15f, touchY), // Gap au centre
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Ligne horizontale (droite)
            drawLine(
                color = crosshairColor,
                start = Offset(touchX + 15f, touchY),
                end = Offset(touchX + crosshairSize, touchY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Ligne verticale (haut)
            drawLine(
                color = crosshairColor,
                start = Offset(touchX, touchY - crosshairSize),
                end = Offset(touchX, touchY - 15f), // Gap au centre
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Ligne verticale (bas)
            drawLine(
                color = crosshairColor,
                start = Offset(touchX, touchY + 15f),
                end = Offset(touchX, touchY + crosshairSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Point central (pour une visée précise)
            drawCircle(
                color = crosshairColor,
                radius = centerDotRadius,
                center = Offset(touchX, touchY)
            )
            
            // Cercle externe (optionnel, pour l'esthétique)
            drawCircle(
                color = crosshairColor,
                radius = 12f,
                center = Offset(touchX, touchY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

