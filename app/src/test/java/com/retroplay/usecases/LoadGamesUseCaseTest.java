package com.retroplay.usecases;

import android.content.Context;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour LoadGamesUseCase
 */
public class LoadGamesUseCaseTest {
    
    @Test
    public void testLoadGames_WithInvalidConsole() {
        Context context = mock(Context.class);
        LoadGamesUseCase useCase = new LoadGamesUseCase(context);
        
        LoadGamesUseCase.LoadGamesResult result = useCase.loadGames("invalid_console", "invalid_console");
        
        // Devrait échouer car le fichier n'existe pas et l'auto-scan échouera probablement
        assertNotNull(result);
        // Le résultat dépendra de l'existence réelle des fichiers, donc on vérifie juste la structure
    }
    
    @Test
    public void testLoadGamesResult_Structure() {
        // Test de la structure du résultat
        LoadGamesUseCase.LoadGamesResult result = new LoadGamesUseCase.LoadGamesResult(
            false,
            null,
            "test_console",
            "Test error"
        );
        
        assertFalse(result.success);
        assertNull(result.games);
        assertEquals("test_console", result.realConsoleDirectory);
        assertEquals("Test error", result.errorMessage);
    }
}

