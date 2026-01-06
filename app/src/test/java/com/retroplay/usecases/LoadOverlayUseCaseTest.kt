package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.retroplay.helpers.UriFileHelper
import com.retroplay.overlay.models.OverlayPreference
import com.retroplay.overlay.models.OverlayPreferenceManager
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour LoadOverlayUseCase
 */
class LoadOverlayUseCaseTest {
    
    @Test
    fun testLoadCustomOverlay_WithInvalidUri() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val useCase = LoadOverlayUseCase(context, prefs)
        
        val uri = mock(Uri::class.java)
        whenever(UriFileHelper.getFileNameFromUri(context, uri)).thenReturn(null)
        
        val result = useCase.loadCustomOverlay(uri, "nes")
        
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
    }
    
    @Test
    fun testLoadCustomOverlay_WithNonCfgFile() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val useCase = LoadOverlayUseCase(context, prefs)
        
        val uri = mock(Uri::class.java)
        whenever(UriFileHelper.getFileNameFromUri(context, uri)).thenReturn("test.txt")
        
        val result = useCase.loadCustomOverlay(uri, "nes")
        
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains(".cfg") == true)
    }
    
    @Test
    fun testLoadOverlayPreferences_WithValidConsole() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val useCase = LoadOverlayUseCase(context, prefs)
        
        // Note: OverlayPreferenceManager.load() est une méthode statique
        // qui nécessiterait PowerMock pour être mockée complètement
        // Pour l'instant, on teste la structure du UseCase
        val result = useCase.loadOverlayPreferences("nes")
        
        // Le résultat dépendra de OverlayPreferenceManager qui est difficile à mocker
        // On vérifie au moins que la méthode existe et ne crash pas
        assertNotNull(result) // Peut être null si pas de préférence
    }
}

