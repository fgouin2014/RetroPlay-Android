package com.retroplay.server;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests unitaires pour HtmlGenerator
 */
public class HtmlGeneratorTest {
    
    @Test
    public void testEscapeHtml_Basic() {
        String result = HtmlGenerator.escapeHtml("Hello World");
        assertEquals("Hello World", result);
    }
    
    @Test
    public void testEscapeHtml_WithAmpersand() {
        String result = HtmlGenerator.escapeHtml("A & B");
        assertEquals("A &amp; B", result);
    }
    
    @Test
    public void testEscapeHtml_WithLessThan() {
        String result = HtmlGenerator.escapeHtml("A < B");
        assertEquals("A &lt; B", result);
    }
    
    @Test
    public void testEscapeHtml_WithGreaterThan() {
        String result = HtmlGenerator.escapeHtml("A > B");
        assertEquals("A &gt; B", result);
    }
    
    @Test
    public void testEscapeHtml_WithQuotes() {
        String result = HtmlGenerator.escapeHtml("Say \"Hello\"");
        assertEquals("Say &quot;Hello&quot;", result);
    }
    
    @Test
    public void testEscapeHtml_WithApostrophe() {
        String result = HtmlGenerator.escapeHtml("It's working");
        assertEquals("It&#39;s working", result);
    }
    
    @Test
    public void testEscapeHtml_AllSpecialChars() {
        String result = HtmlGenerator.escapeHtml("<script>alert('XSS')</script>");
        assertEquals("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;", result);
    }
    
    @Test
    public void testEscapeHtml_Null() {
        String result = HtmlGenerator.escapeHtml(null);
        assertEquals("", result);
    }
    
    @Test
    public void testGenerateSimpleDirectoryListing_Empty() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_dir_" + System.currentTimeMillis());
        tempDir.mkdirs();
        
        try {
            String result = HtmlGenerator.generateSimpleDirectoryListing(tempDir, "/test");
            assertNotNull(result);
            assertTrue(result.contains("Directory: /test"));
            assertTrue(result.contains("<!DOCTYPE html>"));
            assertTrue(result.contains("</html>"));
        } finally {
            tempDir.delete();
        }
    }
    
    @Test
    public void testGenerateGameLibraryDirectoryListing_Empty() {
        String result = HtmlGenerator.generateGameLibraryDirectoryListing("test", new String[0]);
        assertNotNull(result);
        assertTrue(result.contains("Game Library - test"));
        assertTrue(result.contains("<!DOCTYPE html>"));
        assertTrue(result.contains("</html>"));
    }
    
    @Test
    public void testGenerateGameLibraryDirectoryListing_WithFiles() {
        String[] files = {"file1.nes", "file2.zip", "file3.html"};
        String result = HtmlGenerator.generateGameLibraryDirectoryListing("test", files);
        assertNotNull(result);
        assertTrue(result.contains("file1.nes"));
        assertTrue(result.contains("file2.zip"));
        assertTrue(result.contains("file3.html"));
    }
    
    @Test
    public void testGenerateDirectoryListing_Empty() {
        List<HtmlGenerator.FileInfo> files = new ArrayList<>();
        String result = HtmlGenerator.generateDirectoryListing("/test", files, false, false, "");
        assertNotNull(result);
        assertTrue(result.contains("Index of /test"));
        assertTrue(result.contains("<!DOCTYPE html>"));
    }
    
    @Test
    public void testGenerateDirectoryListing_WithFiles() {
        List<HtmlGenerator.FileInfo> files = new ArrayList<>();
        files.add(new HtmlGenerator.FileInfo("file1.txt", false, "2024-01-01 12:00", 1024));
        files.add(new HtmlGenerator.FileInfo("dir1", true, "2024-01-01 12:00", -1));
        
        String result = HtmlGenerator.generateDirectoryListing("/test", files, false, false, "");
        assertNotNull(result);
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("dir1"));
    }
    
    @Test
    public void testGenerateDirectoryListing_FoldersFirst() {
        List<HtmlGenerator.FileInfo> files = new ArrayList<>();
        files.add(new HtmlGenerator.FileInfo("file1.txt", false, "2024-01-01 12:00", 1024));
        files.add(new HtmlGenerator.FileInfo("dir1", true, "2024-01-01 12:00", -1));
        
        String result = HtmlGenerator.generateDirectoryListing("/test", files, true, false, "");
        // Vérifier que dir1 apparaît avant file1.txt
        int dirIndex = result.indexOf("dir1");
        int fileIndex = result.indexOf("file1.txt");
        assertTrue(dirIndex < fileIndex);
    }
    
    @Test
    public void testGenerateDirectoryListing_ExactSize() {
        List<HtmlGenerator.FileInfo> files = new ArrayList<>();
        files.add(new HtmlGenerator.FileInfo("file1.txt", false, "2024-01-01 12:00", 1024));
        
        String result = HtmlGenerator.generateDirectoryListing("/test", files, false, true, "");
        assertTrue(result.contains("1024 bytes"));
    }
    
    @Test
    public void testGenerateDirectoryListing_FormattedSize() {
        List<HtmlGenerator.FileInfo> files = new ArrayList<>();
        files.add(new HtmlGenerator.FileInfo("file1.txt", false, "2024-01-01 12:00", 2048));
        
        String result = HtmlGenerator.generateDirectoryListing("/test", files, false, false, "");
        assertTrue(result.contains("2.0 KB"));
    }
    
    @Test
    public void testGenerateDirectoryListing_CustomCSS() {
        List<HtmlGenerator.FileInfo> files = new ArrayList<>();
        String customCSS = "body { background: red; }";
        String result = HtmlGenerator.generateDirectoryListing("/test", files, false, false, customCSS);
        assertTrue(result.contains(customCSS));
    }
    
    @Test
    public void testFileInfo_FromFile() {
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "test_file_" + System.currentTimeMillis() + ".txt");
        try {
            tempFile.createNewFile();
            tempFile.setLastModified(System.currentTimeMillis());
            
            HtmlGenerator.FileInfo info = HtmlGenerator.FileInfo.fromFile(tempFile);
            assertNotNull(info);
            assertEquals("test_file_", info.name.substring(0, 10));
            assertFalse(info.isDirectory);
            assertTrue(info.size >= 0);
            assertNotNull(info.lastModified);
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
        } finally {
            tempFile.delete();
        }
    }
}

