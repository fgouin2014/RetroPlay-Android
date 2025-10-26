package com.retroplay;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager pour gérer les overrides de cores par jeu
 * Stocke les associations dans /storage/emulated/0/GameLibrary-Data/core_overrides.json
 * 
 * Format JSON:
 * {
 *   "overrides": [
 *     {
 *       "gamePath": "fbneo/sega/afighter.zip",
 *       "coreId": "mame2010",
 *       "reason": "Missing .key file"
 *     }
 *   ]
 * }
 */
public class CoreOverrideManager {
    private static final String TAG = "CoreOverrideManager";
    private static final String OVERRIDE_FILE = "/storage/emulated/0/GameLibrary-Data/core_overrides.json";
    
    private static CoreOverrideManager instance;
    private Map<String, CoreOverride> overrides;
    
    private CoreOverrideManager() {
        overrides = new HashMap<>();
        loadOverrides();
    }
    
    public static synchronized CoreOverrideManager getInstance() {
        if (instance == null) {
            instance = new CoreOverrideManager();
        }
        return instance;
    }
    
    /**
     * Charge les overrides depuis le fichier JSON
     */
    private void loadOverrides() {
        File file = new File(OVERRIDE_FILE);
        if (!file.exists()) {
            Log.i(TAG, "No core overrides file found, starting fresh");
            return;
        }
        
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            
            String jsonString = new String(data, "UTF-8");
            JSONObject root = new JSONObject(jsonString);
            JSONArray overridesArray = root.getJSONArray("overrides");
            
            for (int i = 0; i < overridesArray.length(); i++) {
                JSONObject obj = overridesArray.getJSONObject(i);
                String gamePath = obj.getString("gamePath");
                String coreId = obj.getString("coreId");
                String reason = obj.optString("reason", "");
                
                CoreOverride override = new CoreOverride(gamePath, coreId, reason);
                overrides.put(gamePath, override);
            }
            
            Log.i(TAG, "Loaded " + overrides.size() + " core overrides");
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading core overrides: " + e.getMessage());
        }
    }
    
    /**
     * Sauvegarde les overrides dans le fichier JSON
     */
    private void saveOverrides() {
        try {
            JSONObject root = new JSONObject();
            JSONArray overridesArray = new JSONArray();
            
            for (CoreOverride override : overrides.values()) {
                JSONObject obj = new JSONObject();
                obj.put("gamePath", override.getGamePath());
                obj.put("coreId", override.getCoreId());
                if (override.getReason() != null && !override.getReason().isEmpty()) {
                    obj.put("reason", override.getReason());
                }
                overridesArray.put(obj);
            }
            
            root.put("overrides", overridesArray);
            
            File file = new File(OVERRIDE_FILE);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(root.toString(2).getBytes("UTF-8"));
            fos.close();
            
            Log.i(TAG, "Saved " + overrides.size() + " core overrides");
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving core overrides: " + e.getMessage());
        }
    }
    
    /**
     * Obtient le core override pour un jeu spécifique
     * @param gamePath Chemin relatif du jeu (ex: "fbneo/sega/afighter.zip")
     * @return Le core ID à utiliser, ou null si pas d'override
     */
    public String getCoreOverride(String gamePath) {
        CoreOverride override = overrides.get(gamePath);
        if (override != null) {
            Log.i(TAG, "Core override found for " + gamePath + ": " + override.getCoreId());
            return override.getCoreId();
        }
        return null;
    }
    
    /**
     * Définit un override de core pour un jeu
     * @param gamePath Chemin relatif du jeu
     * @param coreId Core à utiliser
     * @param reason Raison optionnelle
     */
    public void setOverride(String gamePath, String coreId, String reason) {
        CoreOverride override = new CoreOverride(gamePath, coreId, reason);
        overrides.put(gamePath, override);
        saveOverrides();
        Log.i(TAG, "Set core override: " + gamePath + " -> " + coreId);
    }
    
    /**
     * Supprime un override de core pour un jeu
     * @param gamePath Chemin relatif du jeu
     */
    public void removeOverride(String gamePath) {
        if (overrides.remove(gamePath) != null) {
            saveOverrides();
            Log.i(TAG, "Removed core override for: " + gamePath);
        }
    }
    
    /**
     * Vérifie si un jeu a un override défini
     * @param gamePath Chemin relatif du jeu
     * @return true si un override existe
     */
    public boolean hasOverride(String gamePath) {
        return overrides.containsKey(gamePath);
    }
    
    /**
     * Obtient l'override complet pour un jeu
     * @param gamePath Chemin relatif du jeu
     * @return L'objet CoreOverride ou null
     */
    public CoreOverride getOverride(String gamePath) {
        return overrides.get(gamePath);
    }
    
    /**
     * Obtient tous les overrides
     * @return Map de tous les overrides
     */
    public Map<String, CoreOverride> getAllOverrides() {
        return new HashMap<>(overrides);
    }
}

