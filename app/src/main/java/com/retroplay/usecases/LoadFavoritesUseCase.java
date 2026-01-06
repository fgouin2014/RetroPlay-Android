package com.retroplay.usecases;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.retroplay.Game;
import com.retroplay.FavoritesManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Use Case pour le chargement des jeux favoris depuis toutes les consoles.
 * Extracted from GameListActivity and FavoritesActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement des favoris depuis toutes les consoles disponibles
 * - Utilise LoadConsolesUseCase pour obtenir la liste des consoles
 * - Utilise LoadGamesUseCase pour charger les jeux de chaque console
 * - Filtre les favoris via FavoritesManager
 */
public class LoadFavoritesUseCase {
    
    private static final String TAG = "LoadFavoritesUseCase";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public LoadFavoritesUseCase(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
    }
    
    /**
     * Résultat du chargement des favoris
     */
    public static class LoadFavoritesResult {
        public final boolean success;
        public final List<Game> favorites;
        public final String errorMessage;
        
        public LoadFavoritesResult(boolean success, List<Game> favorites, String errorMessage) {
            this.success = success;
            this.favorites = favorites != null ? favorites : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Charge tous les favoris depuis toutes les consoles disponibles
     */
    public LoadFavoritesResult loadAllFavorites() {
        List<Game> allFavorites = new ArrayList<>();
        FavoritesManager favoritesManager = FavoritesManager.getInstance(context);
        
        if (favoritesManager == null) {
            Log.e(TAG, "FavoritesManager is null");
            return new LoadFavoritesResult(false, null, "FavoritesManager is null");
        }
        
        Set<String> favoriteKeys = favoritesManager.getFavoriteKeys();
        Log.i(TAG, "Loading favorites: " + favoriteKeys.size() + " favorite keys found");
        
        // Charger la liste des consoles disponibles depuis le cache
        LoadConsolesUseCase loadConsolesUseCase = new LoadConsolesUseCase(context, prefs);
        LoadConsolesUseCase.CacheLoadResult cacheResult = loadConsolesUseCase.loadFromCache();
        
        List<LoadConsolesUseCase.ConsoleInfo> availableConsoles = cacheResult.consoles;
        if (availableConsoles.isEmpty()) {
            Log.w(TAG, "No consoles found in cache");
            return new LoadFavoritesResult(false, null, "No consoles available");
        }
        
        // Pour chaque console disponible, charger les jeux et filtrer les favoris
        for (LoadConsolesUseCase.ConsoleInfo consoleInfo : availableConsoles) {
            try {
                // Utiliser LoadGamesUseCase pour charger depuis les fichiers locaux
                LoadGamesUseCase loadGamesUseCase = new LoadGamesUseCase(context);
                LoadGamesUseCase.LoadGamesResult result = 
                    loadGamesUseCase.loadGames(consoleInfo.id, consoleInfo.directory);
                
                if (result.success && result.games != null) {
                    // Filtrer les favoris pour cette console
                    for (Game game : result.games) {
                        if (favoritesManager.isFavorite(game)) {
                            game.setFavorite(true);
                            allFavorites.add(game);
                            Log.d(TAG, "Found favorite: " + game.getName() + " (" + consoleInfo.id + ")");
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not load games for console: " + consoleInfo.id, e);
            }
        }
        
        // Trier les favoris alphabétiquement
        allFavorites.sort((g1, g2) -> g1.getName().compareToIgnoreCase(g2.getName()));
        
        Log.i(TAG, "Total favorites loaded: " + allFavorites.size());
        return new LoadFavoritesResult(true, allFavorites, null);
    }
}
















