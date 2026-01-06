package com.retroplay.helpers

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Rect
import com.retroplay.CoreConfigManager
import com.swordfish.libretrodroid.GLRetroView

/**
 * Helper pour les calculs de coordonnées Zapper/Lightgun.
 * Extracted from RetroArchEmulatorActivity to improve modularity.
 * 
 * ⚠️ CRITIQUE: Cette logique est très sensible. Toute modification peut casser la précision du zapper.
 * Ne modifier que si absolument nécessaire et tester intensivement.
 * 
 * Cette classe encapsule UNIQUEMENT les calculs de coordonnées, pas la logique de trigger/button.
 * La logique principale reste dans RetroArchEmulatorActivity pour éviter les régressions.
 */
object ZapperCoordinateHelper {
    
    private const val TAG = "ZapperCoordinateHelper"
    
    /**
     * Résultat du calcul de coordonnées zapper
     */
    data class ZapperCoordinates(
        val relativeX: Float,  // [0-1] pour LibretroDroid
        val relativeY: Float,  // [0-1] pour LibretroDroid
        val libretroX: Int,     // Int16 pour LibretroDroid
        val libretroY: Int,     // Int16 pour LibretroDroid
        val fceummX: Int,       // Coordonnées NES [0-255]
        val fceummY: Int,       // Coordonnées NES [0-239]
        val isInGameArea: Boolean,
        val clampedX: Float,
        val clampedY: Float,
        val gameWidth: Int,
        val gameHeight: Int,
        val coreAspectRatio: Float,
        val actualViewport: android.graphics.RectF
    )
    
    /**
     * Calcule les coordonnées zapper depuis un touch event
     * 
     * @param touchX Coordonnée X du touch (écran)
     * @param touchY Coordonnée Y du touch (écran)
     * @param gameViewBounds Bounds du GLRetroView
     * @param retroView La vue RetroArch (pour aspect ratio, geometry, viewport)
     * @param console L'ID de la console (pour config FCEUmm)
     * @param context Context Android (pour CoreConfigManager)
     * @param allowOffscreen Si true, clamp aux bounds si touch hors zone
     * @return Les coordonnées calculées ou null si invalide
     */
    fun calculateZapperCoordinates(
        touchX: Float,
        touchY: Float,
        gameViewBounds: Rect?,
        retroView: GLRetroView,
        console: String,
        context: Context,
        allowOffscreen: Boolean = true
    ): ZapperCoordinates? {
        // Vérifier si bounds disponibles
        val bounds = gameViewBounds ?: run {
            Log.w(TAG, "[ZAPPER] GLRetroView bounds not available yet")
            return null
        }
        
        // Vérifier si touch est DANS le GLRetroView (zone de jeu)
        val isInGameArea = touchX >= bounds.left && touchX <= bounds.right &&
                          touchY >= bounds.top && touchY <= bounds.bottom
        
        if (!isInGameArea && !allowOffscreen) {
            Log.d(TAG, "[ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored")
            return null
        }
        
        if (!isInGameArea) {
            Log.d(TAG, "[ZAPPER] Touch OUTSIDE game area, clamping to bounds")
        }
        
        // Convertir touchY (coordonnées ÉCRAN) en coordonnées VIEW
        val touchXInView = touchX - bounds.left
        val touchYInView = touchY - bounds.top
        
        // Récupérer le VRAI ratio d'aspect depuis le core
        val coreAspectRatio = try {
            retroView.getAspectRatio()
        } catch (e: Exception) {
            Log.w(TAG, "[ZAPPER] Cannot get aspect ratio from core, using NES default (256:240)")
            256f / 240f  // Fallback NES
        }
        
        // Récupérer les dimensions de rendu du core
        val gameWidth = try { retroView.getGameGeometryWidth() } catch (e: Exception) { 256 }
        val gameHeight = try { retroView.getGameGeometryHeight() } catch (e: Exception) { 240 }
        
        val screenAspectRatio = bounds.width / bounds.height
        
        // Calculer le viewport avec letterboxing
        val actualViewport = if (screenAspectRatio > coreAspectRatio) {
            // Écran plus large que le jeu → Bandes noires à gauche/droite
            val gameWidth = bounds.height * coreAspectRatio
            val letterboxWidth = (bounds.width - gameWidth) / 2f
            val left = letterboxWidth / bounds.width
            val right = 1f - left
            android.graphics.RectF(left, 0f, right, 1f)
        } else {
            // Écran plus haut que le jeu → Bandes noires en haut/bas (portrait typique)
            val gameHeight = bounds.width / coreAspectRatio
            val letterboxHeight = (bounds.height - gameHeight) / 2f
            val top = letterboxHeight / bounds.height
            val bottom = 1f - top
            android.graphics.RectF(0f, top, 1f, bottom)
        }
        
        // Appliquer le viewport CORRIGÉ (si letterboxing)
        val viewportTop = actualViewport.top * bounds.height
        val viewportBottom = actualViewport.bottom * bounds.height
        val viewportLeft = actualViewport.left * bounds.width
        val viewportRight = actualViewport.right * bounds.width
        
        val clampedX = touchXInView.coerceIn(viewportLeft, viewportRight)
        val clampedY = touchYInView.coerceIn(viewportTop, viewportBottom)
        
        val viewportWidth = viewportRight - viewportLeft
        val viewportHeight = viewportBottom - viewportTop
        
        val relativeX = (clampedX - viewportLeft) / viewportWidth
        val relativeY = (clampedY - viewportTop) / viewportHeight
        
        // LibretroDroid ATTEND [0, 1] et fait la conversion [-0x7fff, +0x7fff] lui-même
        val libretroX = ((relativeX - 0.5f) * 2.0f * 32767f).toInt()
        val libretroY = ((relativeY - 0.5f) * 2.0f * 32767f).toInt()
        
        // Conversion FCEUmm (pour debug et validation)
        val config = if (console == "nes") {
            CoreConfigManager.loadConfig(context, "FCEUmm")
        } else {
            emptyMap()
        }
        val cropTop = config["fceumm_overscan_v_top"]?.toIntOrNull() ?: 8
        val cropLeft = config["fceumm_overscan_h_left"]?.toIntOrNull() ?: 0
        
        val fceummOffsetX = (cropLeft * 0x120) - 1
        val fceummOffsetY = (cropTop * 0x133) + 1
        
        val maxWidth = gameWidth
        val maxHeight = gameHeight
        
        val fceummX = ((libretroX + (32767 + fceummOffsetX)) * maxWidth) / ((32767 + fceummOffsetX) * 2)
        val fceummY = ((libretroY + (32767 + fceummOffsetY)) * maxHeight) / ((32767 + fceummOffsetY) * 2)
        
        return ZapperCoordinates(
            relativeX = relativeX,
            relativeY = relativeY,
            libretroX = libretroX,
            libretroY = libretroY,
            fceummX = fceummX,
            fceummY = fceummY,
            isInGameArea = isInGameArea,
            clampedX = clampedX,
            clampedY = clampedY,
            gameWidth = gameWidth,
            gameHeight = gameHeight,
            coreAspectRatio = coreAspectRatio,
            actualViewport = actualViewport
        )
    }
    
    /**
     * Log les conversions de coordonnées pour debug
     * 
     * @param touchX Coordonnée X du touch (écran)
     * @param touchY Coordonnée Y du touch (écran)
     * @param bounds Bounds du GLRetroView
     * @param coords Coordonnées calculées
     * @param config Configuration FCEUmm (pour crop)
     */
    fun logCoordinateConversions(
        touchX: Float,
        touchY: Float,
        bounds: Rect,
        coords: ZapperCoordinates,
        config: Map<String, String>
    ) {
        val touchXInView = touchX - bounds.left
        val touchYInView = touchY - bounds.top
        val cropTop = config["fceumm_overscan_v_top"]?.toIntOrNull() ?: 8
        
        Log.d(TAG, "[ZAPPER CONVERSIONS]")
        Log.d(TAG, "  1. Touch écran (raw):      (${touchX.toInt()}, ${touchY.toInt()})")
        Log.d(TAG, "  2. GLRetroView bounds:     left=${bounds.left.toInt()}, top=${bounds.top.toInt()}, right=${bounds.right.toInt()}, bottom=${bounds.bottom.toInt()}")
        Log.d(TAG, "  3. Touch in View coords:   (${touchXInView.toInt()}, ${touchYInView.toInt()})")
        Log.d(TAG, "  4. Game Geometry:          ${coords.gameWidth}x${coords.gameHeight} (ratio: ${coords.coreAspectRatio}, crop: top=$cropTop bottom=${config["fceumm_overscan_v_bottom"] ?: "8"})")
        Log.d(TAG, "  6. Viewport CORRECTED:     left=${coords.actualViewport.left}, top=${coords.actualViewport.top}, right=${coords.actualViewport.right}, bottom=${coords.actualViewport.bottom}")
        Log.d(TAG, "  8. Touch dans viewport?    ${coords.isInGameArea}")
        Log.d(TAG, "  9. Clamped to viewport:    (${coords.clampedX.toInt()}, ${coords.clampedY.toInt()})")
        Log.d(TAG, "  10. Relative [0-1]:        (${coords.relativeX}, ${coords.relativeY})")
        Log.d(TAG, "  11. Libretro int16:        (${coords.libretroX}, ${coords.libretroY})")
        Log.d(TAG, "  13. FCEUmm NES [0-${coords.gameWidth-1}]x[0-${coords.gameHeight-1}]: (${coords.fceummX}, ${coords.fceummY})")
        val nesValid = coords.fceummX in 0 until coords.gameWidth && coords.fceummY in 0 until coords.gameHeight
        Log.d(TAG, "  14. NES coords valid?      $nesValid")
    }
}

