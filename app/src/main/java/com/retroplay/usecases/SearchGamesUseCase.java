package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import com.retroplay.Game;

/**
 * Use Case pour la recherche de jeux.
 * Extracted from GameListActivity to improve modularity and testability.
 * 
 * Gère:
 * - Recherche dans la console actuelle
 * - Recherche globale dans toutes les consoles
 * - Filtrage par texte
 */
public class SearchGamesUseCase {
    
    private static final String TAG = "SearchGamesUseCase";
    private static final String AUTO_SCAN_BASE_URL = "http://localhost:7777/gamedata/";
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 2000;
    
    private final Context context;
    
    public SearchGamesUseCase(Context context) {
        this.context = context;
    }
    
    /**
     * Résultat de la recherche
     */
    public static class SearchResult {
        public final boolean success;
        public final List<Game> games;
        public final String errorMessage;
        
        public SearchResult(boolean success, List<Game> games, String errorMessage) {
            this.success = success;
            this.games = games != null ? games : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Recherche des jeux dans la console actuelle
     * 
     * @param query Texte de recherche
     * @param games Liste des jeux de la console actuelle
     * @return Résultat de la recherche
     */
    public SearchResult searchInCurrentConsole(String query, List<Game> games) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new SearchResult(true, new ArrayList<>(games), null);
            }
            
            String lowerQuery = query.toLowerCase().trim();
            List<Game> filtered = new ArrayList<>();
            
            for (Game game : games) {
                if (game.getName().toLowerCase().contains(lowerQuery)) {
                    filtered.add(game);
                }
            }
            
            Log.i(TAG, "Search in current console: '" + query + "' -> " + filtered.size() + " results");
            return new SearchResult(true, filtered, null);
            
        } catch (Exception e) {
            Log.e(TAG, "Error searching in current console", e);
            return new SearchResult(false, null, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Recherche globale dans toutes les consoles disponibles
     * 
     * @param query Texte de recherche
     * @param availableConsoles Liste des consoles disponibles (ConsoleInfo avec id, name, fullName, directory)
     * @return Résultat de la recherche
     */
    public SearchResult searchAllConsoles(String query, List<? extends ConsoleInfoLike> availableConsoles) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new SearchResult(true, new ArrayList<>(), null);
            }
            
            String lowerQuery = query.toLowerCase().trim();
            List<Game> allGames = new ArrayList<>();
            
            // Chercher dans toutes les consoles disponibles
            for (ConsoleInfoLike console : availableConsoles) {
                try {
                    String urlStr = AUTO_SCAN_BASE_URL + console.getId() + "/gamelist.json";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);
                    
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        
                        // Parser et filtrer les jeux
                        JSONObject jsonObj = new JSONObject(response.toString());
                        JSONArray arr = jsonObj.getJSONArray("games");
                        
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String gameName = obj.getString("name");
                            
                            // Filtrer par requête
                            if (gameName.toLowerCase().contains(lowerQuery)) {
                                // Utiliser optString() avec valeurs par défaut pour éviter les erreurs
                                // Support format ES: "desc"/"releasedate" (nouveau) et "description"/"releaseDate" (legacy)
                                String desc = obj.optString("desc", null);
                                if (desc == null || desc.isEmpty()) {
                                    desc = obj.optString("description", "");
                                }
                                String releaseDate = obj.optString("releasedate", null);
                                if (releaseDate == null || releaseDate.isEmpty()) {
                                    releaseDate = obj.optString("releaseDate", "");
                                }
                                
                                // Parser les nouveaux champs ES
                                String developer = obj.optString("developer", null);
                                String publisher = obj.optString("publisher", null);
                                String rating = obj.optString("rating", null);
                                String hash = obj.optString("hash", null);
                                
                                Game game = new Game(
                                    obj.getString("id"),
                                    gameName,
                                    obj.getString("path"),
                                    desc,
                                    releaseDate,
                                    obj.optString("genre", ""),
                                    obj.optString("players", ""),
                                    developer,
                                    publisher,
                                    rating,
                                    hash
                                );
                                game.setConsole(console.getId());
                                game.initializePaths(null);
                                allGames.add(game);
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    // Console inaccessible ou vide, continuer
                    Log.d(TAG, "Cannot search in console " + console.getId() + ": " + e.getMessage());
                }
            }
            
            // Trier les résultats
            allGames.sort((g1, g2) -> g1.getName().compareToIgnoreCase(g2.getName()));
            
            Log.i(TAG, "Global search for '" + query + "': " + allGames.size() + " results");
            return new SearchResult(true, allGames, null);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in global search", e);
            return new SearchResult(false, null, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Interface pour représenter une console (utilisée pour la recherche globale)
     * Permet d'utiliser ConsoleInfo de GameListActivity sans dépendance directe
     */
    public interface ConsoleInfoLike {
        String getId();
        String getName();
        String getFullName();
        String getDirectory();
    }
}

