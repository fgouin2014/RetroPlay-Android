package com.retroplay.usecases;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Use Case pour le chargement et la gestion du cache des consoles.
 * Extracted from GameListActivity to improve modularity and testability.
 * 
 * Gère:
 * - Chargement depuis le cache SharedPreferences
 * - Parsing et sauvegarde du cache
 * - Validation de l'âge du cache
 */
public class LoadConsolesUseCase {
    
    private static final String TAG = "LoadConsolesUseCase";
    private static final String PREF_CONSOLES_CACHE = "consoles_cache";
    private static final String PREF_CONSOLES_CACHE_TIMESTAMP = "consoles_cache_timestamp";
    private static final long CACHE_MAX_AGE_MS = 5 * 60 * 1000; // 5 minutes
    
    private final Context context;
    private final SharedPreferences prefs;
    
    /**
     * Classe pour représenter une console
     */
    public static class ConsoleInfo {
        public final String id;
        public final String name;
        public final String fullName;
        public final String directory;
        
        public ConsoleInfo(String id, String name, String fullName, String directory) {
            this.id = id;
            this.name = name;
            this.fullName = fullName;
            this.directory = directory;
        }
    }
    
    /**
     * Résultat du chargement depuis le cache
     */
    public static class CacheLoadResult {
        public final boolean fromCache;
        public final List<ConsoleInfo> consoles;
        public final boolean cacheValid;
        
        public CacheLoadResult(boolean fromCache, List<ConsoleInfo> consoles, boolean cacheValid) {
            this.fromCache = fromCache;
            this.consoles = consoles != null ? consoles : new ArrayList<>();
            this.cacheValid = cacheValid;
        }
    }
    
    public LoadConsolesUseCase(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
    }
    
    /**
     * Charger les consoles depuis le cache si valide
     */
    public CacheLoadResult loadFromCache() {
        String cachedJson = prefs.getString(PREF_CONSOLES_CACHE, null);
        long cacheTimestamp = prefs.getLong(PREF_CONSOLES_CACHE_TIMESTAMP, 0);
        long cacheAge = System.currentTimeMillis() - cacheTimestamp;
        
        if (cachedJson != null && cacheAge < CACHE_MAX_AGE_MS) {
            Log.i(TAG, "Loading consoles from cache (age: " + (cacheAge / 1000) + "s)");
            try {
                List<ConsoleInfo> consoles = parseConsolesFromJson(cachedJson);
                return new CacheLoadResult(true, consoles, true);
            } catch (Exception e) {
                Log.w(TAG, "Error loading consoles from cache, falling back to scan", e);
                return new CacheLoadResult(false, null, false);
            }
        }
        
        Log.i(TAG, "No valid cache (age: " + (cacheAge / 1000) + "s, max: " + (CACHE_MAX_AGE_MS / 1000) + "s)");
        return new CacheLoadResult(false, null, false);
    }
    
    /**
     * Parser les consoles depuis un JSON
     */
    public List<ConsoleInfo> parseConsolesFromJson(String jsonString) {
        try {
            JSONObject jsonObj = new JSONObject(jsonString);
            JSONArray consolesArray = jsonObj.getJSONArray("consoles");
            
            List<ConsoleInfo> consoles = new ArrayList<>();
            for (int i = 0; i < consolesArray.length(); i++) {
                JSONObject consoleObj = consolesArray.getJSONObject(i);
                consoles.add(new ConsoleInfo(
                    consoleObj.getString("id"),
                    consoleObj.getString("name"),
                    consoleObj.getString("fullName"),
                    consoleObj.getString("directory")
                ));
            }
            
            Log.i(TAG, "Parsed " + consoles.size() + " consoles from JSON");
            return consoles;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing consoles from JSON", e);
            throw new RuntimeException("Failed to parse consoles JSON", e);
        }
    }
    
    /**
     * Sauvegarder les consoles dans le cache
     */
    public void saveToCache(List<ConsoleInfo> consoles) {
        try {
            JSONObject jsonObj = new JSONObject();
            JSONArray consolesArray = new JSONArray();
            
            for (ConsoleInfo console : consoles) {
                JSONObject consoleObj = new JSONObject();
                consoleObj.put("id", console.id);
                consoleObj.put("name", console.name);
                consoleObj.put("fullName", console.fullName);
                consoleObj.put("directory", console.directory);
                consolesArray.put(consoleObj);
            }
            
            jsonObj.put("consoles", consolesArray);
            String jsonString = jsonObj.toString();
            
            prefs.edit()
                .putString(PREF_CONSOLES_CACHE, jsonString)
                .putLong(PREF_CONSOLES_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply();
            
            Log.i(TAG, "Saved " + consoles.size() + " consoles to cache");
        } catch (Exception e) {
            Log.e(TAG, "Error saving consoles to cache", e);
        }
    }
}
















