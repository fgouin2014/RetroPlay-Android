package com.retroplay.usecases;

import android.content.Context;
import com.retroplay.Game;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests unitaires pour SearchGamesUseCase
 */
public class SearchGamesUseCaseTest {
    
    @Test
    public void testSearchInCurrentConsole_WithEmptyQuery() {
        Context context = mock(Context.class);
        SearchGamesUseCase useCase = new SearchGamesUseCase(context);
        
        List<Game> games = new ArrayList<>();
        games.add(new Game("1", "Test Game", "/path", "", "", "", "", null, null, null, null));
        
        SearchGamesUseCase.SearchResult result = useCase.searchInCurrentConsole("", games);
        
        assertTrue(result.success);
        assertEquals(games.size(), result.games.size());
    }
    
    @Test
    public void testSearchInCurrentConsole_WithMatchingQuery() {
        Context context = mock(Context.class);
        SearchGamesUseCase useCase = new SearchGamesUseCase(context);
        
        List<Game> games = new ArrayList<>();
        games.add(new Game("1", "Super Mario", "/path", "", "", "", "", null, null, null, null));
        games.add(new Game("2", "Zelda", "/path", "", "", "", "", null, null, null, null));
        
        SearchGamesUseCase.SearchResult result = useCase.searchInCurrentConsole("Mario", games);
        
        assertTrue(result.success);
        assertEquals(1, result.games.size());
        assertEquals("Super Mario", result.games.get(0).getName());
    }
    
    @Test
    public void testSearchResult_Structure() {
        SearchGamesUseCase.SearchResult result = new SearchGamesUseCase.SearchResult(
            false,
            null,
            "Test error"
        );
        
        assertFalse(result.success);
        assertNull(result.games);
        assertEquals("Test error", result.errorMessage);
    }
}

