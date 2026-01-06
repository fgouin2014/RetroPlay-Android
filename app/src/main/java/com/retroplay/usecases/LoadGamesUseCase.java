package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import com.retroplay.helpers.GameLibraryPaths;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import com.retroplay.Game;

/**
 * Use Case pour le chargement des jeux depuis les fichiers gamelist.json.
 * Extracted from GameListActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement depuis fichier local gamelist.json
 * - Fallback vers AUTO SCAN via API serveur si fichier absent
 * - Parsing du JSON et création des objets Game
 */
public class LoadGamesUseCase {
    
    private static final String TAG = "LoadGamesUseCase";
    
    private final Context context;
    
    public LoadGamesUseCase(Context context) {
        this.context = context;
    }
    
    /**
     * Résultat du chargement des jeux
     */
    public static class LoadGamesResult {
        public final boolean success;
        public final List<Game> games;
        public final String realConsoleDirectory;
        public final String errorMessage;
        
        public LoadGamesResult(boolean success, List<Game> games, String realConsoleDirectory, String errorMessage) {
            this.success = success;
            this.games = games != null ? games : new ArrayList<>();
            this.realConsoleDirectory = realConsoleDirectory;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Charge les jeux pour une console donnée
     * 
     * @param consoleId ID de la console (ex: "nes")
     * @param realConsoleDirectory Nom réel du répertoire (ex: "nes" ou "megadrive")
     * @return Résultat du chargement avec la liste des jeux
     */
    public LoadGamesResult loadGames(String consoleId, String realConsoleDirectory) {
        try {
            Log.i(TAG, "Chargement de la liste des jeux pour " + consoleId.toUpperCase() + "...");
            
            // Construire le chemin du gamelist.json
            String gamelistPath = GameLibraryPaths.getGamelistPath(realConsoleDirectory);
            File gamelistFile = new File(gamelistPath);
            Log.d(TAG, "Looking for gamelist.json at: " + gamelistPath);
            
            // Charger uniquement depuis le fichier local
            // Les gamelist.json sont créés/mis à jour au démarrage par SplashActivity
            // Le WebServer sert uniquement au WASM (EmulatorJS), pas pour les gamelists
            if (!gamelistFile.exists()) {
                Log.w(TAG, "Fichier gamelist.json non trouvé: " + gamelistPath);
                Log.w(TAG, "Le fichier devrait avoir été créé au démarrage par SplashActivity");
                return new LoadGamesResult(
                    false,
                    null,
                    realConsoleDirectory,
                    "No gamelist.json found (should be created at startup)"
                );
            }
            
            String jsonContent = readFileContent(gamelistFile);
            
            if (jsonContent == null || jsonContent.isEmpty()) {
                return new LoadGamesResult(
                    false,
                    null,
                    realConsoleDirectory,
                    "gamelist.json is empty"
                );
            }
            
            // Parser le JSON et créer les objets Game
            List<Game> games = parseGamesFromJson(jsonContent, realConsoleDirectory, consoleId);
            
            return new LoadGamesResult(true, games, realConsoleDirectory, null);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur chargement liste des jeux", e);
            return new LoadGamesResult(
                false,
                null,
                realConsoleDirectory,
                "Error loading games: " + e.getMessage()
            );
        }
    }
    
    /**
     * Lit le contenu d'un fichier
     */
    private String readFileContent(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];
        fis.read(buffer);
        fis.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }
    
    /**
     * Parse le JSON et crée les objets Game
     */
    private List<Game> parseGamesFromJson(String jsonString, String realConsoleDirectory, String consoleId) throws Exception {
        jsonString = jsonString.trim();
        JSONObject jsonObj = new JSONObject(jsonString);
        JSONArray arr = jsonObj.getJSONArray("games");
        
        String consoleForImages = realConsoleDirectory != null ? realConsoleDirectory : consoleId;
        Log.d(TAG, "parseGamesFromJson: Using consoleForImages=" + consoleForImages);
        
        List<Game> games = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            
            // Support format ES: "desc"/"releasedate" (nouveau) et "description"/"releaseDate" (legacy)
            String desc = obj.optString("desc", null);
            if (desc == null || desc.isEmpty()) {
                desc = obj.optString("description", "");  // Fallback legacy
            }
            String releaseDate = obj.optString("releasedate", null);
            if (releaseDate == null || releaseDate.isEmpty()) {
                releaseDate = obj.optString("releaseDate", "");  // Fallback legacy
            }
            
            // Parser les nouveaux champs ES
            String developer = obj.optString("developer", null);
            String publisher = obj.optString("publisher", null);
            String rating = obj.optString("rating", null);
            String hash = obj.optString("hash", null);
            
            // Lire les chemins d'images depuis le JSON (si présents)
            String imagePathFromJson = obj.optString("image", null);
            String screenshotPathFromJson = obj.optString("screenshot", null);
            
            Game game = new Game(
                obj.getString("id"),
                obj.getString("name"),
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
            
            // Définir la console pour ce jeu - Utiliser realConsoleDirectory pour les chemins d'images
            // IMPORTANT: Utiliser le nom réel du répertoire (ex: "megadrive") et non l'ID canonique (ex: "genesis")
            // pour que les chemins d'images pointent vers le bon répertoire
            game.setConsole(consoleForImages);
            
            // Si les chemins d'images sont dans le JSON, les utiliser directement
            // Sinon, initialiser depuis le path (fallback)
            if (imagePathFromJson != null && !imagePathFromJson.isEmpty()) {
                game.imagePath = imagePathFromJson;
            }
            if (screenshotPathFromJson != null && !screenshotPathFromJson.isEmpty()) {
                game.screenshotPath = screenshotPathFromJson;
            }
            
            // Si les chemins n'ont pas été définis depuis le JSON, initialiser depuis le path
            if (game.imagePath == null || game.imagePath.isEmpty()) {
                game.initializePaths(null);
            }
            
            games.add(game);
        }
        
        return games;
    }
}

