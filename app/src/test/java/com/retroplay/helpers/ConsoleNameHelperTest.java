package com.retroplay.helpers;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests unitaires pour ConsoleNameHelper
 */
public class ConsoleNameHelperTest {
    
    // Tests pour getConsoleFullName
    @Test
    public void testGetConsoleFullName_NES() {
        String result1 = ConsoleNameHelper.getConsoleFullName("nes");
        assertEquals("Nintendo Entertainment System", result1);
        
        String result2 = ConsoleNameHelper.getConsoleFullName("famicom");
        assertEquals("Nintendo Entertainment System", result2);
        
        String result3 = ConsoleNameHelper.getConsoleFullName("fc");
        assertEquals("Nintendo Entertainment System", result3);
    }
    
    @Test
    public void testGetConsoleFullName_SNES() {
        String result1 = ConsoleNameHelper.getConsoleFullName("snes");
        assertEquals("Super Nintendo Entertainment System", result1);
        
        String result2 = ConsoleNameHelper.getConsoleFullName("sfc");
        assertEquals("Super Nintendo Entertainment System", result2);
    }
    
    @Test
    public void testGetConsoleFullName_N64() {
        String result = ConsoleNameHelper.getConsoleFullName("n64");
        assertEquals("Nintendo 64", result);
    }
    
    @Test
    public void testGetConsoleFullName_GameBoy() {
        String result1 = ConsoleNameHelper.getConsoleFullName("gb");
        assertEquals("Game Boy", result1);
        
        String result2 = ConsoleNameHelper.getConsoleFullName("gbc");
        assertEquals("Game Boy Color", result2);
        
        String result3 = ConsoleNameHelper.getConsoleFullName("gba");
        assertEquals("Game Boy Advance", result3);
    }
    
    @Test
    public void testGetConsoleFullName_Genesis() {
        String result1 = ConsoleNameHelper.getConsoleFullName("genesis");
        assertEquals("Sega Genesis / Mega Drive", result1);
        
        String result2 = ConsoleNameHelper.getConsoleFullName("megadrive");
        assertEquals("Sega Genesis / Mega Drive", result2);
        
        String result3 = ConsoleNameHelper.getConsoleFullName("md");
        assertEquals("Sega Genesis / Mega Drive", result3);
    }
    
    @Test
    public void testGetConsoleFullName_PlayStation() {
        String result1 = ConsoleNameHelper.getConsoleFullName("ps1");
        assertEquals("PlayStation 1", result1);
        
        String result2 = ConsoleNameHelper.getConsoleFullName("psx");
        assertEquals("PlayStation 1", result2);
        
        String result3 = ConsoleNameHelper.getConsoleFullName("psp");
        assertEquals("PlayStation Portable", result3);
    }
    
    @Test
    public void testGetConsoleFullName_Atari() {
        String result1 = ConsoleNameHelper.getConsoleFullName("atari2600");
        assertEquals("Atari 2600", result1);
        
        String result2 = ConsoleNameHelper.getConsoleFullName("2600");
        assertEquals("Atari 2600", result2);
    }
    
    @Test
    public void testGetConsoleFullName_Unknown() {
        String result = ConsoleNameHelper.getConsoleFullName("unknown");
        assertEquals("UNKNOWN", result);
    }
    
    @Test
    public void testGetConsoleFullName_CaseInsensitive() {
        String result = ConsoleNameHelper.getConsoleFullName("NES");
        assertEquals("Nintendo Entertainment System", result);
    }
    
    // Tests pour getDefaultCore
    @Test
    public void testGetDefaultCore_NES() {
        String result = ConsoleNameHelper.getDefaultCore("nes");
        assertEquals("fceumm", result);
    }
    
    @Test
    public void testGetDefaultCore_SNES() {
        String result = ConsoleNameHelper.getDefaultCore("snes");
        assertEquals("snes9x", result);
    }
    
    @Test
    public void testGetDefaultCore_N64() {
        String result = ConsoleNameHelper.getDefaultCore("n64");
        assertEquals("parallel_n64", result);
    }
    
    @Test
    public void testGetDefaultCore_GameBoy() {
        String result1 = ConsoleNameHelper.getDefaultCore("gb");
        assertEquals("gambatte", result1);
        
        String result2 = ConsoleNameHelper.getDefaultCore("gbc");
        assertEquals("gambatte", result2);
        
        String result3 = ConsoleNameHelper.getDefaultCore("gba");
        assertEquals("mgba", result3);
    }
    
    @Test
    public void testGetDefaultCore_Genesis() {
        String result = ConsoleNameHelper.getDefaultCore("genesis");
        assertEquals("genesis_plus_gx", result);
    }
    
    @Test
    public void testGetDefaultCore_PlayStation() {
        String result1 = ConsoleNameHelper.getDefaultCore("psx");
        assertEquals("pcsx_rearmed", result1);
        
        String result2 = ConsoleNameHelper.getDefaultCore("psp");
        assertEquals("ppsspp", result2);
    }
    
    @Test
    public void testGetDefaultCore_Arcade() {
        String result1 = ConsoleNameHelper.getDefaultCore("arcade");
        assertEquals("fbneo", result1);
        
        String result2 = ConsoleNameHelper.getDefaultCore("mame");
        assertEquals("mame2010", result2);
    }
    
    @Test
    public void testGetDefaultCore_Unknown() {
        String result = ConsoleNameHelper.getDefaultCore("unknown");
        assertEquals("auto", result);
    }
    
    // Tests pour getConsoleColor
    @Test
    public void testGetConsoleColor_NES() {
        String result1 = ConsoleNameHelper.getConsoleColor("nes");
        assertEquals("#E30B5C", result1);
        
        String result2 = ConsoleNameHelper.getConsoleColor("famicom");
        assertEquals("#E30B5C", result2);
    }
    
    @Test
    public void testGetConsoleColor_SNES() {
        String result = ConsoleNameHelper.getConsoleColor("snes");
        assertEquals("#8B5CF6", result);
    }
    
    @Test
    public void testGetConsoleColor_N64() {
        String result = ConsoleNameHelper.getConsoleColor("n64");
        assertEquals("#3B82F6", result);
    }
    
    @Test
    public void testGetConsoleColor_GameBoy() {
        String result1 = ConsoleNameHelper.getConsoleColor("gb");
        assertEquals("#6B7280", result1);
        
        String result2 = ConsoleNameHelper.getConsoleColor("gbc");
        assertEquals("#F59E0B", result2);
        
        String result3 = ConsoleNameHelper.getConsoleColor("gba");
        assertEquals("#10B981", result3);
    }
    
    @Test
    public void testGetConsoleColor_Genesis() {
        String result = ConsoleNameHelper.getConsoleColor("genesis");
        assertEquals("#0066CC", result);
    }
    
    @Test
    public void testGetConsoleColor_PlayStation() {
        String result1 = ConsoleNameHelper.getConsoleColor("psx");
        assertEquals("#6366F1", result1);
        
        String result2 = ConsoleNameHelper.getConsoleColor("psp");
        assertEquals("#4F46E5", result2);
    }
    
    @Test
    public void testGetConsoleColor_Unknown() {
        String result = ConsoleNameHelper.getConsoleColor("unknown");
        assertEquals("#FF3333", result);
    }
    
    @Test
    public void testGetConsoleColor_CaseInsensitive() {
        String result = ConsoleNameHelper.getConsoleColor("NES");
        assertEquals("#E30B5C", result);
    }
}

