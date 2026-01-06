package com.retroplay;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import com.retroplay.helpers.CoreDisplayHelper;
import com.retroplay.helpers.ConsoleNameHelper;
import com.retroplay.utils.WebServerUtils;
import com.retroplay.utils.FileIconHelper;

/**
 * Tests d'intégration pour vérifier que les helpers extraits fonctionnent correctement
 * dans le contexte Android.
 */
@RunWith(AndroidJUnit4.class)
public class HelpersIntegrationTest {
    
    @Test
    public void testCoreDisplayHelper_Integration() {
        // Test que CoreDisplayHelper fonctionne dans le contexte Android
        String result = CoreDisplayHelper.INSTANCE.getCoreDisplayName("fbneo_libretro_android.so");
        assertEquals("FBNeo", result);
        
        result = CoreDisplayHelper.INSTANCE.getCoreDisplayName("snes9x_libretro_android.so");
        assertEquals("Snes9x", result);
    }
    
    @Test
    public void testConsoleNameHelper_Integration() {
        // Test que ConsoleNameHelper fonctionne dans le contexte Android
        String fullName = ConsoleNameHelper.getConsoleFullName("nes");
        assertEquals("Nintendo Entertainment System", fullName);
        
        String defaultCore = ConsoleNameHelper.getDefaultCore("nes");
        assertEquals("fceumm", defaultCore);
        
        String color = ConsoleNameHelper.getConsoleColor("nes");
        assertEquals("#E30B5C", color);
    }
    
    @Test
    public void testWebServerUtils_Integration() {
        // Test que WebServerUtils fonctionne dans le contexte Android
        String mimeType = WebServerUtils.getMimeType("test.html");
        assertEquals("text/html; charset=utf-8", mimeType);
        
        String fileSize = WebServerUtils.formatFileSize(2048);
        assertEquals("2.0 KB", fileSize);
        
        String baseName = WebServerUtils.getBaseNameFromFile("game.nes");
        assertEquals("game", baseName);
    }
    
    @Test
    public void testFileIconHelper_Integration() {
        // Test que FileIconHelper fonctionne dans le contexte Android
        String icon = FileIconHelper.getFileIcon("game.nes");
        assertNotNull(icon);
        
        String fileType = FileIconHelper.getFileType("game.nes");
        assertEquals("NES Game", fileType);
    }
    
    @Test
    public void testWebServerUtils_NormalizeDisplayName() {
        // Test de normalisation des noms de ROMs
        String normalized = WebServerUtils.normalizeDisplayName("Game (U) (V1.2) [!]");
        assertEquals("Game (USA)", normalized);
        
        normalized = WebServerUtils.normalizeDisplayName("Super Mario (E)");
        assertEquals("Super Mario (Europe)", normalized);
    }
    
    @Test
    public void testConsoleNameHelper_MultipleConsoles() {
        // Test avec plusieurs consoles différentes
        assertEquals("Super Nintendo Entertainment System", 
                     ConsoleNameHelper.getConsoleFullName("snes"));
        assertEquals("Sega Genesis / Mega Drive", 
                     ConsoleNameHelper.getConsoleFullName("genesis"));
        assertEquals("PlayStation 1", 
                     ConsoleNameHelper.getConsoleFullName("psx"));
    }
}

