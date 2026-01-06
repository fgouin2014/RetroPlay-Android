package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour LoadCoreUseCase
 */
class LoadCoreUseCaseTest {
    
    @Test
    fun testGetCorePath_NES() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.getCorePath("nes", "/path/to/game.nes", "Test Game")
        assertTrue(result.contains("fceumm_libretro_android.so"))
    }
    
    @Test
    fun testGetCorePath_SNES() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.getCorePath("snes", "/path/to/game.smc", "Test Game")
        assertTrue(result.contains("snes9x_libretro_android.so"))
    }
    
    @Test
    fun testGetCorePath_N64() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.getCorePath("n64", "/path/to/game.n64", "Test Game")
        assertTrue(result.contains("parallel_n64_libretro_android.so"))
    }
    
    @Test
    fun testGetCorePath_PSX() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.getCorePath("psx", "/path/to/game.cue", "Test Game")
        assertTrue(result.contains("pcsx_rearmed_libretro_android.so"))
    }
    
    @Test
    fun testGetCorePath_Genesis() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.getCorePath("genesis", "/path/to/game.md", "Test Game")
        assertTrue(result.contains("genesis_plus_gx_libretro_android.so"))
    }
    
    @Test
    fun testGetCorePath_UnknownConsole() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.getCorePath("unknown", "/path/to/game.rom", "Test Game")
        // Devrait utiliser fceumm comme fallback
        assertTrue(result.contains("fceumm_libretro_android.so"))
    }
    
    @Test
    fun testLoadCore_NoCrash() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString(any(), any())).thenReturn(null)
        whenever(prefs.getLong(any(), any())).thenReturn(0L)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.apply()).thenReturn(Unit)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.loadCore("nes", "/path/to/game.nes", "Test Game")
        
        assertFalse(result.wasCrash)
        assertFalse(result.shouldShowErrorDialog)
        assertNull(result.failedCoreName)
        assertTrue(result.coreFilePath.contains("fceumm"))
    }
    
    @Test
    fun testLoadCore_WithCrash() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        
        val romPath = "/path/to/game.nes"
        val corePath = "/data/data/com.retroplay/lib/fceumm_libretro_android.so"
        val currentTime = System.currentTimeMillis()
        val crashTime = currentTime - 1000L // Il y a 1 seconde
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString("last_game_path", null)).thenReturn(romPath)
        whenever(prefs.getString("last_core_attempted", null)).thenReturn(corePath)
        whenever(prefs.getLong("crash_timestamp", 0L)).thenReturn(crashTime)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        whenever(editor.apply()).thenReturn(Unit)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.loadCore("nes", romPath, "Test Game")
        
        assertTrue(result.wasCrash)
        assertTrue(result.shouldShowErrorDialog)
        assertNotNull(result.failedCoreName)
        assertEquals("FCEUmm", result.failedCoreName)
    }
    
    @Test
    fun testLoadCore_CrashButDifferentCore() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        
        val romPath = "/path/to/game.nes"
        val oldCorePath = "/data/data/com.retroplay/lib/mesen_libretro_android.so"
        val newCorePath = "/data/data/com.retroplay/lib/fceumm_libretro_android.so"
        val currentTime = System.currentTimeMillis()
        val crashTime = currentTime - 1000L
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString("last_game_path", null)).thenReturn(romPath)
        whenever(prefs.getString("last_core_attempted", null)).thenReturn(oldCorePath)
        whenever(prefs.getLong("crash_timestamp", 0L)).thenReturn(crashTime)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        whenever(editor.apply()).thenReturn(Unit)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.loadCore("nes", romPath, "Test Game")
        
        assertTrue(result.wasCrash)
        assertFalse(result.shouldShowErrorDialog) // Pas de dialog car core différent
        assertNull(result.failedCoreName)
        assertTrue(result.coreFilePath.contains("fceumm"))
    }
    
    @Test
    fun testLoadCore_CrashTimeoutExpired() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        
        val romPath = "/path/to/game.nes"
        val corePath = "/data/data/com.retroplay/lib/fceumm_libretro_android.so"
        val currentTime = System.currentTimeMillis()
        val crashTime = currentTime - 5000L // Il y a 5 secondes (timeout expiré)
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.getString("last_game_path", null)).thenReturn(romPath)
        whenever(prefs.getString("last_core_attempted", null)).thenReturn(corePath)
        whenever(prefs.getLong("crash_timestamp", 0L)).thenReturn(crashTime)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.apply()).thenReturn(Unit)
        
        val useCase = LoadCoreUseCase(context)
        val result = useCase.loadCore("nes", romPath, "Test Game")
        
        assertFalse(result.wasCrash) // Timeout expiré, pas considéré comme crash
        assertFalse(result.shouldShowErrorDialog)
    }
    
    @Test
    fun testClearCrashPreferences() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        whenever(editor.apply()).thenReturn(Unit)
        
        val useCase = LoadCoreUseCase(context)
        useCase.clearCrashPreferences()
        
        verify(editor).clear()
        verify(editor).apply()
    }
}

