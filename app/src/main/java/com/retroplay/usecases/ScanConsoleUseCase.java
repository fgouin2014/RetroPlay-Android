package com.retroplay.usecases;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Use Case pour scanner une console et générer/mettre à jour le gamelist.json
 * Extrait de ConsoleManagerActivity pour améliorer la modularité
 */
public class ScanConsoleUseCase {
    private static final String TAG = "ScanConsoleUseCase";
    private static final String GAMELIBRARY_DIR = "/storage/emulated/0/GameLibrary-Data";
    
    /**
     * Résultat d'un scan de console
     */
    public static class ScanResult {
        public String consoleId;
        public String consoleName;
        public boolean success;
        public int totalRoms;
        public int newRoms;
        public int updatedRoms;
        public int removedRoms;
        public int missingImages;
        
        public ScanResult() {
            this.success = false;
            this.totalRoms = 0;
            this.newRoms = 0;
            this.updatedRoms = 0;
            this.removedRoms = 0;
            this.missingImages = 0;
        }
    }
    
    /**
     * ROM scannée
     */
    public static class ScannedRom {
        public String id;
        public String name;
        public String path;
        public boolean hasBox2dImage;
        public boolean hasScreenshot;
    }
    
    /**
     * Scanne une console silencieusement (sans statistiques d'audit)
     * Retourne true si le scan a réussi, false sinon
     */
    public boolean scanConsoleSilently(String consoleId, String extensionsStr) {
        try {
            File consoleDir = new File(GAMELIBRARY_DIR + "/" + consoleId);
            if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                return false;
            }
            
            // Charger les extensions depuis console.json si présent
            List<String> extensions = loadExtensionsFromConsoleJson(consoleDir);
            
            // Si pas d'extensions dans console.json, utiliser celles fournies
            if (extensions.isEmpty() && !extensionsStr.isEmpty()) {
                extensions = parseExtensions(extensionsStr);
            }
            
            // Scanner les ROMs dans le répertoire
            List<ScannedRom> scannedRoms = scanRomsInDirectory(consoleDir, extensions);
            
            // Si aucun ROM trouvé, ignorer
            if (scannedRoms.isEmpty()) {
                return false;
            }
            
            // Charger le gamelist.json existant
            File gamelistFile = new File(consoleDir, "gamelist.json");
            JSONArray existingGames = loadExistingGamelist(gamelistFile);
            
            // Créer une map des ROMs existantes
            HashMap<String, JSONObject> existingRomsMap = createExistingRomsMap(existingGames);
            
            // Créer le nouveau JSON en fusionnant les données (sans statistiques)
            JSONArray gamesArray = mergeRomsSilently(scannedRoms, existingRomsMap);
            
            // Écrire le nouveau gamelist.json
            saveGamelist(gamelistFile, gamesArray);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error in scanConsoleSilently: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Fusionne les ROMs sans calculer les statistiques d'audit
     */
    private JSONArray mergeRomsSilently(List<ScannedRom> scannedRoms, 
                                        HashMap<String, JSONObject> existingRomsMap) {
        JSONArray gamesArray = new JSONArray();
        int newId = 1;
        
        for (ScannedRom rom : scannedRoms) {
            try {
                JSONObject gameObj;
                
                // Vérifier si ce ROM existe déjà
                if (existingRomsMap.containsKey(rom.path)) {
                    // GARDER les métadonnées existantes
                    gameObj = existingRomsMap.get(rom.path);
                    // Mettre à jour seulement l'ID pour la cohérence
                    gameObj.put("id", String.valueOf(newId++));
                    
                    // Mettre à jour l'image si elle existe maintenant
                    if (rom.hasBox2dImage && !gameObj.optString("image", "").contains("box2d")) {
                        gameObj.put("image", "./media/box2d/" + rom.name + ".png");
                    }
                } else {
                    // NOUVEAU ROM: créer une entrée basique
                    gameObj = new JSONObject();
                    gameObj.put("id", String.valueOf(newId++));
                    gameObj.put("name", rom.name);
                    gameObj.put("path", rom.path);
                    gameObj.put("image", rom.hasBox2dImage ? "./media/box2d/" + rom.name + ".png" : "");
                    gameObj.put("desc", "");
                    gameObj.put("releasedate", "");
                    gameObj.put("developer", "");
                    gameObj.put("publisher", "");
                    gameObj.put("genre", "");
                    gameObj.put("players", "");
                }
                
                gamesArray.put(gameObj);
            } catch (JSONException e) {
                Log.e(TAG, "Error creating game object for ROM: " + rom.name, e);
            }
        }
        
        return gamesArray;
    }
    
    /**
     * Scanne une console avec audit (merge intelligent avec gamelist.json existant)
     */
    public ScanResult scanConsoleWithAudit(String consoleId, String consoleName, String extensionsStr) {
        ScanResult result = new ScanResult();
        result.consoleId = consoleId;
        result.consoleName = consoleName;
        
        try {
            File consoleDir = new File(GAMELIBRARY_DIR + "/" + consoleId);
            if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                result.success = false;
                return result;
            }
            
            // Charger les extensions depuis console.json si présent
            List<String> extensions = loadExtensionsFromConsoleJson(consoleDir);
            
            // Si pas d'extensions dans console.json, utiliser celles fournies
            if (extensions.isEmpty() && !extensionsStr.isEmpty()) {
                extensions = parseExtensions(extensionsStr);
            }
            
            // Scanner les ROMs dans le répertoire
            List<ScannedRom> scannedRoms = scanRomsInDirectory(consoleDir, extensions);
            
            result.totalRoms = scannedRoms.size();
            
            // Si aucun ROM trouvé, ignorer
            if (scannedRoms.isEmpty()) {
                result.success = false;
                return result;
            }
            
            // AUDIT MODE: Charger le gamelist.json existant et faire un merge intelligent
            File gamelistFile = new File(consoleDir, "gamelist.json");
            JSONArray existingGames = loadExistingGamelist(gamelistFile);
            HashSet<String> existingPaths = extractExistingPaths(existingGames);
            
            // Créer une map des ROMs existantes (path -> JSONObject)
            HashMap<String, JSONObject> existingRomsMap = createExistingRomsMap(existingGames);
            
            // Créer le nouveau JSON en fusionnant les données
            JSONArray gamesArray = mergeRomsWithExisting(scannedRoms, existingRomsMap, result);
            
            // Calculer les ROMs supprimés (dans l'ancien JSON mais plus sur le disque)
            result.removedRoms = existingPaths.size() - (result.totalRoms - result.newRoms);
            if (result.removedRoms < 0) result.removedRoms = 0;
            
            // Écrire le nouveau gamelist.json au format {"games": [...]}
            saveGamelist(gamelistFile, gamesArray);
            
            result.success = true;
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error in scanConsoleWithAudit: " + e.getMessage(), e);
            result.success = false;
            return result;
        }
    }
    
    /**
     * Charge les extensions depuis console.json
     */
    private List<String> loadExtensionsFromConsoleJson(File consoleDir) {
        List<String> extensions = new ArrayList<>();
        File consoleJsonFile = new File(consoleDir, "console.json");
        if (consoleJsonFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(consoleJsonFile);
                byte[] buffer = new byte[(int) consoleJsonFile.length()];
                fis.read(buffer);
                fis.close();
                String json = new String(buffer, "UTF-8");
                JSONObject consoleJson = new JSONObject(json);
                
                if (consoleJson.has("extensions")) {
                    JSONArray extArray = consoleJson.getJSONArray("extensions");
                    for (int i = 0; i < extArray.length(); i++) {
                        extensions.add(extArray.getString(i).toLowerCase());
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, "Could not parse console.json", e);
            } catch (Exception e) {
                Log.w(TAG, "Could not load console.json", e);
            }
        }
        return extensions;
    }
    
    /**
     * Parse les extensions depuis une chaîne séparée par virgules
     */
    private List<String> parseExtensions(String extensionsStr) {
        List<String> extensions = new ArrayList<>();
        String[] parts = extensionsStr.split(",");
        for (String ext : parts) {
            String cleaned = ext.trim().toLowerCase();
            if (!cleaned.isEmpty()) {
                if (!cleaned.startsWith(".")) {
                    cleaned = "." + cleaned;
                }
                extensions.add(cleaned);
            }
        }
        return extensions;
    }
    
    /**
     * Scanne les ROMs dans un répertoire
     */
    private List<ScannedRom> scanRomsInDirectory(File consoleDir, List<String> extensions) {
        List<ScannedRom> scannedRoms = new ArrayList<>();
        File[] files = consoleDir.listFiles();
        int romCounter = 1;
        
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    
                    // Vérifier si c'est un ROM
                    boolean isRom = false;
                    if (!extensions.isEmpty()) {
                        for (String ext : extensions) {
                            if (fileName.toLowerCase().endsWith(ext)) {
                                isRom = true;
                                break;
                            }
                        }
                    } else {
                        isRom = isRomFile(fileName);
                    }
                    
                    if (isRom) {
                        String baseName = getBaseNameFromFile(fileName);
                        
                        // Vérifier si les images existent
                        File box2dImage = new File(consoleDir, "media/box2d/" + baseName + ".png");
                        File screenshotImage = new File(consoleDir, "media/screenshots/" + baseName + ".png");
                        
                        ScannedRom rom = new ScannedRom();
                        rom.id = String.valueOf(romCounter++);
                        rom.name = baseName;
                        rom.path = "./" + fileName;
                        rom.hasBox2dImage = box2dImage.exists();
                        rom.hasScreenshot = screenshotImage.exists();
                        
                        scannedRoms.add(rom);
                    }
                }
            }
        }
        return scannedRoms;
    }
    
    /**
     * Charge le gamelist.json existant
     */
    private JSONArray loadExistingGamelist(File gamelistFile) {
        if (gamelistFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(gamelistFile);
                byte[] buffer = new byte[(int) gamelistFile.length()];
                fis.read(buffer);
                fis.close();
                String existingJson = new String(buffer, "UTF-8");
                JSONObject wrapper = new JSONObject(existingJson);
                
                // Support des deux formats: {"games": [...]} et [...]
                if (wrapper.has("games")) {
                    return wrapper.getJSONArray("games");
                } else {
                    return new JSONArray(existingJson);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Could not parse existing gamelist.json, creating new one", e);
            } catch (Exception e) {
                Log.w(TAG, "Could not load existing gamelist.json, creating new one", e);
            }
        }
        return new JSONArray();
    }
    
    /**
     * Extrait les chemins existants du gamelist
     */
    private HashSet<String> extractExistingPaths(JSONArray existingGames) {
        HashSet<String> existingPaths = new HashSet<>();
        for (int i = 0; i < existingGames.length(); i++) {
            try {
                JSONObject game = existingGames.getJSONObject(i);
                existingPaths.add(game.getString("path"));
            } catch (Exception e) {
                // Ignorer
            }
        }
        return existingPaths;
    }
    
    /**
     * Crée une map des ROMs existantes
     */
    private HashMap<String, JSONObject> createExistingRomsMap(JSONArray existingGames) {
        HashMap<String, JSONObject> existingRomsMap = new HashMap<>();
        for (int i = 0; i < existingGames.length(); i++) {
            try {
                JSONObject game = existingGames.getJSONObject(i);
                String path = game.getString("path");
                existingRomsMap.put(path, game);
            } catch (Exception e) {
                // Ignorer les entrées invalides
            }
        }
        return existingRomsMap;
    }
    
    /**
     * Fusionne les ROMs scannées avec les existantes
     */
    private JSONArray mergeRomsWithExisting(List<ScannedRom> scannedRoms, 
                                            HashMap<String, JSONObject> existingRomsMap, 
                                            ScanResult result) {
        JSONArray gamesArray = new JSONArray();
        int newId = 1;
        
        for (ScannedRom rom : scannedRoms) {
            try {
                JSONObject gameObj;
                
                // Vérifier si ce ROM existe déjà
                if (existingRomsMap.containsKey(rom.path)) {
                    // GARDER les métadonnées existantes
                    gameObj = existingRomsMap.get(rom.path);
                    // Mettre à jour seulement l'ID pour la cohérence
                    gameObj.put("id", String.valueOf(newId++));
                    
                    // Mettre à jour l'image si elle existe maintenant
                    if (rom.hasBox2dImage && !gameObj.optString("image", "").contains("box2d")) {
                        gameObj.put("image", "./media/box2d/" + rom.name + ".png");
                        result.updatedRoms++;
                    }
                } else {
                    // NOUVEAU ROM: créer une entrée basique
                    gameObj = new JSONObject();
                    gameObj.put("id", String.valueOf(newId++));
                    gameObj.put("name", rom.name);
                    gameObj.put("path", rom.path);
                    gameObj.put("image", rom.hasBox2dImage ? "./media/box2d/" + rom.name + ".png" : "");
                    gameObj.put("desc", "");
                    gameObj.put("releasedate", "");
                    gameObj.put("developer", "");
                    gameObj.put("publisher", "");
                    gameObj.put("genre", "");
                    gameObj.put("players", "");
                    result.newRoms++;
                }
                
                // Compter les images manquantes
                if (!rom.hasBox2dImage && !rom.hasScreenshot) {
                    result.missingImages++;
                }
                
                gamesArray.put(gameObj);
            } catch (JSONException e) {
                Log.e(TAG, "Error creating game object for ROM: " + rom.name, e);
            }
        }
        
        return gamesArray;
    }
    
    /**
     * Sauvegarde le gamelist.json
     */
    private void saveGamelist(File gamelistFile, JSONArray gamesArray) throws Exception {
        JSONObject gamelistWrapper = new JSONObject();
        gamelistWrapper.put("games", gamesArray);
        FileWriter writer = new FileWriter(gamelistFile);
        writer.write(gamelistWrapper.toString(2));
        writer.close();
    }
    
    /**
     * Vérifie si un fichier est un ROM selon les extensions communes
     */
    private boolean isRomFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".nes") || lowerName.endsWith(".smc") || lowerName.endsWith(".sfc") ||
               lowerName.endsWith(".z64") || lowerName.endsWith(".n64") || lowerName.endsWith(".v64") ||
               lowerName.endsWith(".bin") || lowerName.endsWith(".md") || lowerName.endsWith(".gen") ||
               lowerName.endsWith(".smd") || lowerName.endsWith(".gba") || lowerName.endsWith(".gb") ||
               lowerName.endsWith(".gbc") || lowerName.endsWith(".nds") || lowerName.endsWith(".pbp") ||
               lowerName.endsWith(".iso") || lowerName.endsWith(".cue") || lowerName.endsWith(".img") ||
               lowerName.endsWith(".cso") || lowerName.endsWith(".zip") || lowerName.endsWith(".7z") ||
               lowerName.endsWith(".rar") || lowerName.endsWith(".chd");
    }
    
    /**
     * Extraire le nom de base d'un fichier ROM (sans extension)
     */
    private String getBaseNameFromFile(String fileName) {
        String baseName = fileName;
        
        // Enlever les extensions connues
        String[] extensions = {".nes", ".smc", ".sfc", ".z64", ".n64", ".v64", ".bin", ".md", 
                               ".gen", ".smd", ".gba", ".gb", ".gbc", ".nds", ".pbp", ".iso", 
                               ".cue", ".img", ".cso", ".zip", ".7z", ".rar", ".chd"};
        
        for (String ext : extensions) {
            if (baseName.toLowerCase().endsWith(ext)) {
                baseName = baseName.substring(0, baseName.length() - ext.length());
                break;
            }
        }
        
        return baseName;
    }
}
