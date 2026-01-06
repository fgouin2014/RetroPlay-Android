package com.retroplay.helpers;

import android.util.Log;
import com.retroplay.ConsoleNameMapper;
import com.retroplay.models.ConsoleConfig;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;
import com.retroplay.GamelistManager;

/**
 * Helper pour la gestion des configurations de consoles.
 * Extracted from ConsoleManagerActivity to improve modularity and reusability.
 */
public class ConsoleConfigHelper {

    private static final String TAG = "ConsoleConfigHelper";

    /**
     * Obtenir le nom complet par défaut pour une console
     * Future-proof: Utilise ConsoleNameMapper pour les consoles connues,
     * sinon capitalise le nom du répertoire
     * Gère les sous-consoles (ex: "fbneo/sega" -> "Sega")
     */
    public static String getDefaultFullName(String consoleId) {
        if (consoleId == null || consoleId.isEmpty()) {
            return "Unknown Console";
        }

        // Si c'est une sous-console (contient "/"), extraire juste le nom de la
        // sous-console
        String displayName = consoleId;
        if (consoleId.contains("/")) {
            // Sous-console: "fbneo/sega" -> utiliser "sega" pour le nom
            displayName = consoleId.substring(consoleId.indexOf("/") + 1);
            Log.d(TAG, "Sous-console détectée: " + consoleId + " -> nom: " + displayName);
        }

        // Essayer d'obtenir le nom depuis ConsoleNameMapper (pour les consoles connues)
        // Mais ne pas forcer - chaque répertoire est une console indépendante
        String fullName = ConsoleNameMapper.getFullName(displayName);
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }

        // Fallback générique: Capitaliser le nom du répertoire
        // Exemple: "famicom" -> "Famicom", "megadrive" -> "Megadrive", "sega" -> "Sega"
        String lower = displayName.toLowerCase();
        if (lower.length() > 0) {
            return lower.substring(0, 1).toUpperCase() +
                    (lower.length() > 1 ? lower.substring(1) : "");
        }

        return displayName.toUpperCase();
    }

    /**
     * Charger la configuration d'une console depuis son répertoire
     * Renommé de scanConsoleDirectory pour refléter qu'elle charge, ne scanne pas
     */
    public static ConsoleConfig loadConsoleConfig(File dir, String consoleId) {
        try {
            ConsoleConfig config = new ConsoleConfig();
            config.id = consoleId;

            // Vérifier si console.json existe
            File configFile = new File(dir, "console.json");
            boolean hasConfig = configFile.exists();

            // Vérifier si gamelist.json existe
            File gamelistFile = new File(dir, "gamelist.json");
            config.hasGamelist = gamelistFile.exists();

            if (hasConfig) {
                // Charger depuis console.json
                String jsonContent = new java.util.Scanner(configFile).useDelimiter("\\A").next();
                JSONObject obj = new JSONObject(jsonContent);

                config.name = obj.optString("name", consoleId.toUpperCase());
                config.fullName = obj.optString("fullName", getDefaultFullName(consoleId));
                config.defaultCore = obj.optString("defaultCore", "auto");
                config.color = obj.optString("color", "#FF0000");
                config.isGeneric = obj.optBoolean("isGeneric", false);

                // Parse cores et extensions
                config.cores = new ArrayList<>();
                config.extensions = new ArrayList<>();

                JSONArray coresArray = obj.optJSONArray("cores");
                if (coresArray != null) {
                    for (int j = 0; j < coresArray.length(); j++) {
                        config.cores.add(coresArray.getString(j));
                    }
                }

                JSONArray extsArray = obj.optJSONArray("extensions");
                if (extsArray != null) {
                    for (int j = 0; j < extsArray.length(); j++) {
                        config.extensions.add(extsArray.getString(j));
                    }
                }
            } else {
                // Pas de console.json - utiliser les valeurs par défaut intelligentes
                String baseName = consoleId;
                config.name = baseName.toUpperCase();
                config.fullName = getDefaultFullName(baseName);
                config.defaultCore = ConsoleNameHelper.getDefaultCore(baseName);
                config.color = ConsoleNameHelper.getConsoleColor(baseName);
                config.isGeneric = false;
                config.cores = new ArrayList<>();
                config.extensions = new ArrayList<>(GamelistManager.INSTANCE.getDefaultExtensions(baseName));
            }

            // Marquer comme utilisant le scanner auto si pas de gamelist.json
            config.usesAutoScan = !config.hasGamelist;

            Log.i(TAG, "Console chargée: " + consoleId +
                    (config.hasGamelist ? " (avec gamelist.json)" : " (AUTO SCAN)"));

            return config;

        } catch (Exception e) {
            Log.e(TAG, "Error loading console config: " + consoleId, e);
            return null;
        }
    }
}
