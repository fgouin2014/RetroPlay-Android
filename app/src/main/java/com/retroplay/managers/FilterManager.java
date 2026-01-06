package com.retroplay.managers;

import android.content.Context;
import android.util.Log;
import com.retroplay.Game;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager pour gérer les filtres de jeux (genre, favoris, recherche).
 * Extracted from GameListActivity to improve modularity and reusability.
 * 
 * Gère:
 * - Filtrage par genre
 * - Filtrage par favoris
 * - État des filtres actifs
 * - Application combinée des filtres
 */
public class FilterManager {
    
    private static final String TAG = "FilterManager";
    
    private String currentGenreFilter = "Tous";
    private boolean showOnlyFavorites = false;
    private String searchQuery = "";
    private boolean searchAllConsoles = false;
    
    /**
     * Interface pour les callbacks de mise à jour UI
     */
    public interface FilterCallbacks {
        List<Game> getAllGames();
        List<Game> getFavoriteGames();
        void onFilterChanged(List<Game> filteredGames);
    }
    
    private final FilterCallbacks callbacks;
    
    public FilterManager(FilterCallbacks callbacks) {
        this.callbacks = callbacks;
    }
    
    /**
     * Obtenir tous les genres uniques disponibles
     */
    public Set<String> getAvailableGenres() {
        Set<String> genres = new HashSet<>();
        genres.add("Tous");
        List<Game> allGames = callbacks.getAllGames();
        for (Game game : allGames) {
            String genre = game.getGenre();
            if (genre != null && !genre.isEmpty()) {
                genres.add(genre);
            }
        }
        return genres;
    }
    
    /**
     * Appliquer le filtre par genre
     */
    public List<Game> applyGenreFilter(String genre) {
        if (genre == null) {
            genre = "Tous";
        }
        currentGenreFilter = genre;
        Log.i(TAG, "Genre filter applied: " + genre);
        return applyAllFilters();
    }
    
    /**
     * Toggle le filtre favoris
     */
    public List<Game> toggleFavoritesFilter() {
        showOnlyFavorites = !showOnlyFavorites;
        Log.i(TAG, "Favorites filter toggled: " + showOnlyFavorites);
        return applyAllFilters();
    }
    
    /**
     * Définir le filtre favoris
     */
    public List<Game> setFavoritesFilter(boolean enabled) {
        showOnlyFavorites = enabled;
        Log.i(TAG, "Favorites filter set: " + enabled);
        return applyAllFilters();
    }
    
    /**
     * Appliquer la recherche textuelle
     */
    public List<Game> applySearchQuery(String query) {
        searchQuery = query != null ? query : "";
        Log.i(TAG, "Search query applied: '" + searchQuery + "'");
        return applyAllFilters();
    }
    
    /**
     * Définir le scope de recherche (console actuelle vs toutes les consoles)
     */
    public void setSearchScope(boolean allConsoles) {
        searchAllConsoles = allConsoles;
        Log.i(TAG, "Search scope set: " + (allConsoles ? "ALL CONSOLES" : "CURRENT CONSOLE"));
    }
    
    /**
     * Appliquer tous les filtres combinés
     */
    private List<Game> applyAllFilters() {
        List<Game> baseGames;
        
        // Base: tous les jeux ou favoris uniquement
        if (showOnlyFavorites) {
            baseGames = callbacks.getFavoriteGames();
        } else {
            baseGames = new ArrayList<>(callbacks.getAllGames());
        }
        
        // Filtre par genre
        if (!"Tous".equals(currentGenreFilter)) {
            List<Game> genreFiltered = new ArrayList<>();
            for (Game game : baseGames) {
                if (currentGenreFilter.equals(game.getGenre())) {
                    genreFiltered.add(game);
                }
            }
            baseGames = genreFiltered;
        }
        
        // Note: La recherche textuelle est gérée par SearchGamesUseCase dans l'Activity
        // Ici on retourne juste la liste filtrée par genre/favoris
        // L'Activity appliquera ensuite la recherche via SearchGamesUseCase si nécessaire
        
        return baseGames;
    }
    
    /**
     * Réinitialiser tous les filtres
     */
    public List<Game> resetAllFilters() {
        currentGenreFilter = "Tous";
        showOnlyFavorites = false;
        searchQuery = "";
        Log.i(TAG, "All filters reset");
        return applyAllFilters();
    }
    
    /**
     * Obtenir le filtre genre actuel
     */
    public String getCurrentGenreFilter() {
        return currentGenreFilter;
    }
    
    /**
     * Obtenir l'état du filtre favoris
     */
    public boolean isShowOnlyFavorites() {
        return showOnlyFavorites;
    }
    
    /**
     * Obtenir la requête de recherche actuelle
     */
    public String getSearchQuery() {
        return searchQuery;
    }
    
    /**
     * Obtenir le scope de recherche
     */
    public boolean isSearchAllConsoles() {
        return searchAllConsoles;
    }
}
















