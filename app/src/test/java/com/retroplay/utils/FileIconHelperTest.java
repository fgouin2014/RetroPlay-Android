package com.retroplay.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests unitaires pour FileIconHelper
 */
public class FileIconHelperTest {
    
    // Tests pour getFileIcon
    @Test
    public void testGetFileIcon_NES() {
        String result1 = FileIconHelper.getFileIcon("game.nes");
        assertEquals("\uD83C\uDFAE", result1);
        
        String result2 = FileIconHelper.getFileIcon("game.rom");
        assertEquals("\uD83C\uDFAE", result2);
    }
    
    @Test
    public void testGetFileIcon_ZIP() {
        String result = FileIconHelper.getFileIcon("archive.zip");
        assertEquals("\uD83D\uDCE6", result);
    }
    
    @Test
    public void testGetFileIcon_JS() {
        String result = FileIconHelper.getFileIcon("script.js");
        assertEquals("\uD83D\uDCDC", result);
    }
    
    @Test
    public void testGetFileIcon_HTML() {
        String result = FileIconHelper.getFileIcon("index.html");
        assertEquals("\uD83C\uDF10", result);
    }
    
    @Test
    public void testGetFileIcon_CSS() {
        String result = FileIconHelper.getFileIcon("style.css");
        assertEquals("\uD83C\uDFA8", result);
    }
    
    @Test
    public void testGetFileIcon_JSON() {
        String result = FileIconHelper.getFileIcon("data.json");
        assertEquals("\uD83D\uDCCB", result);
    }
    
    @Test
    public void testGetFileIcon_Image() {
        String result1 = FileIconHelper.getFileIcon("image.png");
        assertEquals("\uD83D\uDDBC", result1);
        
        String result2 = FileIconHelper.getFileIcon("photo.jpg");
        assertEquals("\uD83D\uDDBC", result2);
        
        String result3 = FileIconHelper.getFileIcon("picture.jpeg");
        assertEquals("\uD83D\uDDBC", result3);
        
        String result4 = FileIconHelper.getFileIcon("animation.gif");
        assertEquals("\uD83D\uDDBC", result4);
    }
    
    @Test
    public void testGetFileIcon_TXT() {
        String result = FileIconHelper.getFileIcon("readme.txt");
        assertEquals("\uD83D\uDCC4", result);
    }
    
    @Test
    public void testGetFileIcon_MD() {
        String result = FileIconHelper.getFileIcon("document.md");
        assertEquals("\uD83D\uDCDD", result);
    }
    
    @Test
    public void testGetFileIcon_Unknown() {
        String result = FileIconHelper.getFileIcon("file.unknown");
        assertEquals("\uD83D\uDCC4", result);
    }
    
    @Test
    public void testGetFileIcon_CaseInsensitive() {
        String result = FileIconHelper.getFileIcon("GAME.NES");
        assertEquals("\uD83C\uDFAE", result);
    }
    
    // Tests pour getFileType
    @Test
    public void testGetFileType_NES() {
        String result1 = FileIconHelper.getFileType("game.nes");
        assertEquals("NES Game", result1);
        
        String result2 = FileIconHelper.getFileType("game.rom");
        assertEquals("ROM File", result2);
    }
    
    @Test
    public void testGetFileType_ZIP() {
        String result = FileIconHelper.getFileType("archive.zip");
        assertEquals("Archive", result);
    }
    
    @Test
    public void testGetFileType_JS() {
        String result = FileIconHelper.getFileType("script.js");
        assertEquals("JavaScript", result);
    }
    
    @Test
    public void testGetFileType_HTML() {
        String result = FileIconHelper.getFileType("index.html");
        assertEquals("Web Page", result);
    }
    
    @Test
    public void testGetFileType_CSS() {
        String result = FileIconHelper.getFileType("style.css");
        assertEquals("Stylesheet", result);
    }
    
    @Test
    public void testGetFileType_JSON() {
        String result = FileIconHelper.getFileType("data.json");
        assertEquals("JSON Data", result);
    }
    
    @Test
    public void testGetFileType_Image() {
        String result1 = FileIconHelper.getFileType("image.png");
        assertEquals("Image", result1);
        
        String result2 = FileIconHelper.getFileType("photo.jpg");
        assertEquals("Image", result2);
        
        String result3 = FileIconHelper.getFileType("picture.jpeg");
        assertEquals("Image", result3);
        
        String result4 = FileIconHelper.getFileType("animation.gif");
        assertEquals("Image", result4);
    }
    
    @Test
    public void testGetFileType_TXT() {
        String result = FileIconHelper.getFileType("readme.txt");
        assertEquals("Text File", result);
    }
    
    @Test
    public void testGetFileType_MD() {
        String result = FileIconHelper.getFileType("document.md");
        assertEquals("Markdown", result);
    }
    
    @Test
    public void testGetFileType_Unknown() {
        String result = FileIconHelper.getFileType("file.unknown");
        assertEquals("File", result);
    }
    
    @Test
    public void testGetFileType_CaseInsensitive() {
        String result = FileIconHelper.getFileType("GAME.NES");
        assertEquals("NES Game", result);
    }
    
    @Test
    public void testGetFileType_NoExtension() {
        String result = FileIconHelper.getFileType("file");
        assertEquals("File", result);
    }
}

