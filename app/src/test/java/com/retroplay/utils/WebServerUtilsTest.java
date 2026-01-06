package com.retroplay.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests unitaires pour WebServerUtils
 */
public class WebServerUtilsTest {
    
    // Tests pour getBaseNameFromFile
    @Test
    public void testGetBaseNameFromFile_WithExtension() {
        String result = WebServerUtils.getBaseNameFromFile("game.nes");
        assertEquals("game", result);
    }
    
    @Test
    public void testGetBaseNameFromFile_WithMultipleDots() {
        String result = WebServerUtils.getBaseNameFromFile("game.v1.2.nes");
        assertEquals("game.v1.2", result);
    }
    
    @Test
    public void testGetBaseNameFromFile_WithoutExtension() {
        String result = WebServerUtils.getBaseNameFromFile("game");
        assertEquals("game", result);
    }
    
    @Test
    public void testGetBaseNameFromFile_StartsWithDot() {
        String result = WebServerUtils.getBaseNameFromFile(".hidden");
        assertEquals(".hidden", result);
    }
    
    // Tests pour normalizeDisplayName
    @Test
    public void testNormalizeDisplayName_USA() {
        String result = WebServerUtils.normalizeDisplayName("Game (U)");
        assertEquals("Game (USA)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_Europe() {
        String result = WebServerUtils.normalizeDisplayName("Game (E)");
        assertEquals("Game (Europe)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_Japan() {
        String result = WebServerUtils.normalizeDisplayName("Game (J)");
        assertEquals("Game (Japan)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_WithVersion() {
        String result = WebServerUtils.normalizeDisplayName("Game (U) (V1.2)");
        assertEquals("Game (USA)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_WithBrackets() {
        String result = WebServerUtils.normalizeDisplayName("Game (U) [!]");
        assertEquals("Game (USA)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_Complex() {
        String result = WebServerUtils.normalizeDisplayName("Super Mario (U) (V1.2) [!] [h1]");
        assertEquals("Super Mario (USA)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_UE() {
        String result = WebServerUtils.normalizeDisplayName("Game (UE)");
        assertEquals("Game (USA, Europe)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_JU() {
        String result = WebServerUtils.normalizeDisplayName("Game (JU)");
        assertEquals("Game (Japan, USA)", result);
    }
    
    @Test
    public void testNormalizeDisplayName_NoRegion() {
        String result = WebServerUtils.normalizeDisplayName("Game");
        assertEquals("Game", result);
    }
    
    // Tests pour getMimeType
    @Test
    public void testGetMimeType_HTML() {
        String result = WebServerUtils.getMimeType("index.html");
        assertEquals("text/html; charset=utf-8", result);
    }
    
    @Test
    public void testGetMimeType_HTM() {
        String result = WebServerUtils.getMimeType("index.htm");
        assertEquals("text/html; charset=utf-8", result);
    }
    
    @Test
    public void testGetMimeType_CSS() {
        String result = WebServerUtils.getMimeType("style.css");
        assertEquals("text/css; charset=utf-8", result);
    }
    
    @Test
    public void testGetMimeType_JS() {
        String result = WebServerUtils.getMimeType("script.js");
        assertEquals("application/javascript; charset=utf-8", result);
    }
    
    @Test
    public void testGetMimeType_JSON() {
        String result = WebServerUtils.getMimeType("data.json");
        assertEquals("application/json; charset=utf-8", result);
    }
    
    @Test
    public void testGetMimeType_PNG() {
        String result = WebServerUtils.getMimeType("image.png");
        assertEquals("image/png", result);
    }
    
    @Test
    public void testGetMimeType_JPEG() {
        String result1 = WebServerUtils.getMimeType("image.jpg");
        assertEquals("image/jpeg", result1);
        
        String result2 = WebServerUtils.getMimeType("image.jpeg");
        assertEquals("image/jpeg", result2);
    }
    
    @Test
    public void testGetMimeType_GIF() {
        String result = WebServerUtils.getMimeType("animation.gif");
        assertEquals("image/gif", result);
    }
    
    @Test
    public void testGetMimeType_SVG() {
        String result = WebServerUtils.getMimeType("icon.svg");
        assertEquals("image/svg+xml", result);
    }
    
    @Test
    public void testGetMimeType_WebP() {
        String result = WebServerUtils.getMimeType("image.webp");
        assertEquals("image/webp", result);
    }
    
    @Test
    public void testGetMimeType_ICO() {
        String result = WebServerUtils.getMimeType("favicon.ico");
        assertEquals("image/x-icon", result);
    }
    
    @Test
    public void testGetMimeType_TXT() {
        String result = WebServerUtils.getMimeType("readme.txt");
        assertEquals("text/plain; charset=utf-8", result);
    }
    
    @Test
    public void testGetMimeType_Unknown() {
        String result = WebServerUtils.getMimeType("file.unknown");
        assertEquals("application/octet-stream", result);
    }
    
    @Test
    public void testGetMimeType_CaseInsensitive() {
        String result = WebServerUtils.getMimeType("IMAGE.PNG");
        assertEquals("image/png", result);
    }
    
    // Tests pour formatFileSize
    @Test
    public void testFormatFileSize_Bytes() {
        String result = WebServerUtils.formatFileSize(512);
        assertEquals("512 B", result);
    }
    
    @Test
    public void testFormatFileSize_KB() {
        String result = WebServerUtils.formatFileSize(2048);
        assertEquals("2.0 KB", result);
    }
    
    @Test
    public void testFormatFileSize_MB() {
        String result = WebServerUtils.formatFileSize(2 * 1024 * 1024);
        assertEquals("2.0 MB", result);
    }
    
    @Test
    public void testFormatFileSize_GB() {
        String result = WebServerUtils.formatFileSize(2L * 1024 * 1024 * 1024);
        assertEquals("2.0 GB", result);
    }
    
    @Test
    public void testFormatFileSize_FractionalKB() {
        String result = WebServerUtils.formatFileSize(1536);
        assertEquals("1.5 KB", result);
    }
    
    @Test
    public void testFormatFileSize_Zero() {
        String result = WebServerUtils.formatFileSize(0);
        assertEquals("0 B", result);
    }
    
    // Tests pour getContentType
    @Test
    public void testGetContentType_HTML() {
        String result = WebServerUtils.getContentType("index.html");
        assertEquals("text/html; charset=utf-8", result);
    }
    
    @Test
    public void testGetContentType_JS() {
        String result = WebServerUtils.getContentType("script.js");
        assertEquals("application/javascript; charset=utf-8", result);
    }
    
    @Test
    public void testGetContentType_JSON() {
        String result = WebServerUtils.getContentType("data.json");
        assertEquals("application/json; charset=utf-8", result);
    }
    
    @Test
    public void testGetContentType_NES() {
        String result = WebServerUtils.getContentType("game.nes");
        assertEquals("application/octet-stream", result);
    }
    
    @Test
    public void testGetContentType_CSS() {
        String result = WebServerUtils.getContentType("style.css");
        assertEquals("text/css; charset=utf-8", result);
    }
    
    @Test
    public void testGetContentType_PNG() {
        String result = WebServerUtils.getContentType("image.png");
        assertEquals("image/png", result);
    }
    
    @Test
    public void testGetContentType_JPEG() {
        String result1 = WebServerUtils.getContentType("image.jpg");
        assertEquals("image/jpeg", result1);
        
        String result2 = WebServerUtils.getContentType("image.jpeg");
        assertEquals("image/jpeg", result2);
    }
    
    @Test
    public void testGetContentType_GIF() {
        String result = WebServerUtils.getContentType("animation.gif");
        assertEquals("image/gif", result);
    }
    
    @Test
    public void testGetContentType_Unknown() {
        String result = WebServerUtils.getContentType("file.unknown");
        assertEquals("application/octet-stream", result);
    }
}

