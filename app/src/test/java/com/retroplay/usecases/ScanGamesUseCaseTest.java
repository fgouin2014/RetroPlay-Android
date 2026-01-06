package com.retroplay.usecases;

import android.content.Context;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.File;

/**
 * Tests unitaires pour ScanGamesUseCase
 */
public class ScanGamesUseCaseTest {
    
    @Test
    public void testGetDefaultDisplayName_WithKnownConsole() {
        Context context = mock(Context.class);
        ScanGamesUseCase useCase = new ScanGamesUseCase(context);
        
        String displayName = useCase.getDefaultDisplayName("nes");
        assertEquals("Nintendo Entertainment System", displayName);
    }
    
    @Test
    public void testGetDefaultDisplayName_WithUnknownConsole() {
        Context context = mock(Context.class);
        ScanGamesUseCase useCase = new ScanGamesUseCase(context);
        
        String displayName = useCase.getDefaultDisplayName("unknown");
        assertEquals("UNKNOWN", displayName);
    }
    
    @Test
    public void testGetSubconsoleDisplayName_WithKnownSubconsole() {
        Context context = mock(Context.class);
        ScanGamesUseCase useCase = new ScanGamesUseCase(context);
        
        String displayName = useCase.getSubconsoleDisplayName("fbneo", "sega");
        assertEquals("Sega", displayName);
    }
    
    @Test
    public void testGetSubconsoleDisplayName_WithUnknownSubconsole() {
        Context context = mock(Context.class);
        ScanGamesUseCase useCase = new ScanGamesUseCase(context);
        
        String displayName = useCase.getSubconsoleDisplayName("parent", "unknown");
        assertEquals("UNKNOWN", displayName);
    }
    
    @Test
    public void testScanResult_Structure() {
        ScanGamesUseCase.ScanResult result = new ScanGamesUseCase.ScanResult(
            false,
            null,
            "Test error"
        );
        
        assertFalse(result.success);
        assertNull(result.consoles);
        assertEquals("Test error", result.errorMessage);
    }
    
    @Test
    public void testConsoleInfo_Structure() {
        ScanGamesUseCase.ConsoleInfo console = new ScanGamesUseCase.ConsoleInfo(
            "nes",
            "NES",
            "Nintendo Entertainment System",
            "nes"
        );
        
        assertEquals("nes", console.id);
        assertEquals("NES", console.name);
        assertEquals("Nintendo Entertainment System", console.fullName);
        assertEquals("nes", console.directory);
    }
}

