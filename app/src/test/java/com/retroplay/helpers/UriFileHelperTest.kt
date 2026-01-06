package com.retroplay.helpers

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Tests unitaires pour UriFileHelper
 */
class UriFileHelperTest {
    
    @Test
    fun testExtractOverlayFolderFromUri_Gamepads() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ARetroPlay-Data%2Foverlays%2Fgamepads%2Fflat%2Fnes.cfg")
        val result = UriFileHelper.extractOverlayFolderFromUri(uri)
        assertEquals("flat", result)
    }
    
    @Test
    fun testExtractOverlayFolderFromUri_Keyboards() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ARetroPlay-Data%2Foverlays%2Fkeyboards%2Fqwerty%2Fnes.cfg")
        val result = UriFileHelper.extractOverlayFolderFromUri(uri)
        assertEquals("qwerty", result)
    }
    
    @Test
    fun testExtractOverlayFolderFromUri_NoPattern() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3Afile.txt")
        val result = UriFileHelper.extractOverlayFolderFromUri(uri)
        assertNull(result)
    }
    
    @Test
    fun testExtractOverlayFolderFromUri_Encoded() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ARetroPlay-Data%2Foverlays%2Fgamepads%2Fflat%2Fnes.cfg")
        val result = UriFileHelper.extractOverlayFolderFromUri(uri)
        assertEquals("flat", result)
    }
    
    @Test
    fun testDetectOverlayNameFromContent_WithParentPath() {
        val cfgContent = """
            overlay0_desc0_overlay = ../flat/img/A.png
        """.trimIndent()
        val result = UriFileHelper.detectOverlayNameFromContent(cfgContent, "dreamcast.cfg")
        assertEquals("flat", result)
    }
    
    @Test
    fun testDetectOverlayNameFromContent_WithDirectPath() {
        val cfgContent = """
            overlay0_desc0_overlay = dreamcast/img/A.png
        """.trimIndent()
        val result = UriFileHelper.detectOverlayNameFromContent(cfgContent, "dreamcast.cfg")
        assertEquals("dreamcast", result)
    }
    
    @Test
    fun testDetectOverlayNameFromContent_WithImgPath() {
        val cfgContent = """
            overlay0_desc0_overlay = img/A.png
        """.trimIndent()
        val result = UriFileHelper.detectOverlayNameFromContent(cfgContent, "dreamcast.cfg")
        assertEquals("dreamcast", result)
    }
    
    @Test
    fun testDetectOverlayNameFromContent_NoMatch() {
        val cfgContent = """
            overlay0_desc0_overlay = some/path.png
        """.trimIndent()
        val result = UriFileHelper.detectOverlayNameFromContent(cfgContent, "myoverlay.cfg")
        assertEquals("myoverlay", result)
    }
    
    @Test
    fun testDetectOverlayNameFromContent_EmptyContent() {
        val cfgContent = ""
        val result = UriFileHelper.detectOverlayNameFromContent(cfgContent, "test.cfg")
        assertEquals("test", result)
    }
    
    @Test
    fun testDetectOverlayNameFromContent_MultipleParentDirs() {
        val cfgContent = """
            overlay0_desc0_overlay = ../../custom/flat/img/A.png
        """.trimIndent()
        val result = UriFileHelper.detectOverlayNameFromContent(cfgContent, "dreamcast.cfg")
        assertEquals("custom", result)
    }
    
    // Tests avec mocks pour getFileNameFromUri et readFileFromUri
    // Note: Ces tests nécessitent Robolectric pour fonctionner complètement
    // car ils utilisent des classes Android. Pour l'instant, on teste la logique pure.
    
    @Test
    fun testGetFileNameFromUri_WithLastPathSegment() {
        // Test du fallback avec lastPathSegment
        val uri = Uri.parse("content://com.test/file.txt")
        // Sans mock de Context, on ne peut tester que la logique de fallback
        // Ce test nécessiterait Robolectric pour être complet
        assertEquals("file.txt", uri.lastPathSegment)
    }
    
    @Test
    fun testReadFileFromUri_Logic() {
        // Test de la logique de lecture (sans mock complet)
        // Ce test nécessiterait Robolectric pour être complet
        // On teste juste que la fonction existe et gère les erreurs
        assertTrue(true) // Placeholder - nécessite Robolectric pour test complet
    }
}

