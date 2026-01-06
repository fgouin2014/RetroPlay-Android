package com.retroplay.helpers

import android.content.Context
import androidx.compose.ui.geometry.Rect
import com.swordfish.libretrodroid.GLRetroView
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour ZapperCoordinateHelper
 * 
 * ⚠️ CRITIQUE: Ces tests vérifient uniquement la structure et les calculs de base.
 * Les tests d'intégration réels nécessiteraient un contexte Android complet (Robolectric).
 */
class ZapperCoordinateHelperTest {
    
    @Test
    fun testCalculateZapperCoordinates_WithNullBounds() {
        val retroView = mock(GLRetroView::class.java)
        val context = mock(Context::class.java)
        
        val result = ZapperCoordinateHelper.calculateZapperCoordinates(
            touchX = 100f,
            touchY = 200f,
            gameViewBounds = null,
            retroView = retroView,
            console = "nes",
            context = context,
            allowOffscreen = true
        )
        
        assertNull(result)
    }
    
    @Test
    fun testCalculateZapperCoordinates_WithValidBounds() {
        val retroView = mock(GLRetroView::class.java)
        val context = mock(Context::class.java)
        
        // Mock aspect ratio et geometry
        whenever(retroView.getAspectRatio()).thenReturn(256f / 240f)
        whenever(retroView.getGameGeometryWidth()).thenReturn(256)
        whenever(retroView.getGameGeometryHeight()).thenReturn(240)
        
        val bounds = Rect(0f, 0f, 800f, 600f)
        
        val result = ZapperCoordinateHelper.calculateZapperCoordinates(
            touchX = 400f,
            touchY = 300f,
            gameViewBounds = bounds,
            retroView = retroView,
            console = "nes",
            context = context,
            allowOffscreen = true
        )
        
        assertNotNull(result)
        assertTrue(result!!.relativeX >= 0f && result.relativeX <= 1f)
        assertTrue(result.relativeY >= 0f && result.relativeY <= 1f)
        assertTrue(result.fceummX >= 0 && result.fceummX < 256)
        assertTrue(result.fceummY >= 0 && result.fceummY < 240)
    }
    
    @Test
    fun testCalculateZapperCoordinates_OutsideBounds_AllowOffscreen() {
        val retroView = mock(GLRetroView::class.java)
        val context = mock(Context::class.java)
        
        whenever(retroView.getAspectRatio()).thenReturn(256f / 240f)
        whenever(retroView.getGameGeometryWidth()).thenReturn(256)
        whenever(retroView.getGameGeometryHeight()).thenReturn(240)
        
        val bounds = Rect(0f, 0f, 800f, 600f)
        
        // Touch hors bounds mais allowOffscreen = true
        val result = ZapperCoordinateHelper.calculateZapperCoordinates(
            touchX = 1000f,  // Hors bounds
            touchY = 300f,
            gameViewBounds = bounds,
            retroView = retroView,
            console = "nes",
            context = context,
            allowOffscreen = true
        )
        
        assertNotNull(result)
        // Devrait être clampé aux bounds
    }
    
    @Test
    fun testCalculateZapperCoordinates_OutsideBounds_DisallowOffscreen() {
        val retroView = mock(GLRetroView::class.java)
        val context = mock(Context::class.java)
        
        whenever(retroView.getAspectRatio()).thenReturn(256f / 240f)
        whenever(retroView.getGameGeometryWidth()).thenReturn(256)
        whenever(retroView.getGameGeometryHeight()).thenReturn(240)
        
        val bounds = Rect(0f, 0f, 800f, 600f)
        
        // Touch hors bounds et allowOffscreen = false
        val result = ZapperCoordinateHelper.calculateZapperCoordinates(
            touchX = 1000f,  // Hors bounds
            touchY = 300f,
            gameViewBounds = bounds,
            retroView = retroView,
            console = "nes",
            context = context,
            allowOffscreen = false
        )
        
        assertNull(result)  // Devrait retourner null
    }
}

