package com.retroplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * FavoritesManager - Gestion de la persistance des jeux favoris
 * 
 * Utilise SharedPreferences pour stocker les IDs des jeux favoris.
 * Chaque favori est identifié par: console_id + "/" + game_id
 * 
 * Exemple: "nes/super-mario-bros", "snes/super-metroid"
 */
public class FavoritesManager {
    private static final String TAG = "FavoritesManager";
    private static final String PREFS_NAME = "retroplay_favorites";
    private static final String KEY_FAVORITES = "favorite_games";
    
    private static FavoritesManager instance;
    private SharedPreferences prefs;
    private Set<String> favorites;
    
    private FavoritesManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFavorites();
    }
    
    public static synchronized FavoritesManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoritesManager(context);
        }
        return instance;
    }
    
    /**
     * Charge les favoris depuis SharedPreferences
     */
    private void loadFavorites() {
        favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        Log.d(TAG, "Loaded " + favorites.size() + " favorites");
    }
    
    /**
     * Sauvegarde les favoris dans SharedPreferences
     */
    private void saveFavorites() {
        prefs.edit()
             .putStringSet(KEY_FAVORITES, favorites)
             .apply();
        Log.d(TAG, "Saved " + favorites.size() + " favorites");
    }
    
    /**
     * Génère l'ID unique pour un jeu (console + game_id)
     */
    private String generateGameKey(String consoleId, String gameId) {
        return consoleId + "/" + gameId;
    }
    
    /**
     * Génère l'ID unique pour un jeu à partir de l'objet Game
     */
    private String generateGameKey(Game game) {
        return generateGameKey(game.getConsole(), game.getId());
    }
    
    /**
     * Vérifie si un jeu est dans les favoris
     */
    public boolean isFavorite(Game game) {
        String key = generateGameKey(game);
        return favorites.contains(key);
    }
    
    /**
     * Vérifie si un jeu est dans les favoris (par console et ID)
     */
    public boolean isFavorite(String consoleId, String gameId) {
        String key = generateGameKey(consoleId, gameId);
        return favorites.contains(key);
    }
    
    /**
     * Ajoute un jeu aux favoris
     */
    public void addFavorite(Game game) {
        String key = generateGameKey(game);
        if (favorites.add(key)) {
            saveFavorites();
            Log.i(TAG, "Added to favorites: " + game.getName() + " (" + key + ")");
        }
    }
    
    /**
     * Retire un jeu des favoris
     */
    public void removeFavorite(Game game) {
        String key = generateGameKey(game);
        if (favorites.remove(key)) {
            saveFavorites();
            Log.i(TAG, "Removed from favorites: " + game.getName() + " (" + key + ")");
        }
    }
    
    /**
     * Toggle l'état favori d'un jeu
     * @return true si le jeu est maintenant favori, false sinon
     */
    public boolean toggleFavorite(Game game) {
        if (isFavorite(game)) {
            removeFavorite(game);
            return false;
        } else {
            addFavorite(game);
            return true;
        }
    }
    
    /**
     * Obtient le nombre total de favoris
     */
    public int getFavoritesCount() {
        return favorites.size();
    }
    
    /**
     * Obtient l'ensemble des clés des favoris
     */
    public Set<String> getFavoriteKeys() {
        return new HashSet<>(favorites);
    }
    
    /**
     * Efface tous les favoris
     */
    public void clearAllFavorites() {
        favorites.clear();
        saveFavorites();
        Log.i(TAG, "Cleared all favorites");
    }
}

