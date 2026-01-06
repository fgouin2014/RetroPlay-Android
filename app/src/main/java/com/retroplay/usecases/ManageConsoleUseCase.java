package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import com.retroplay.helpers.GameLibraryPaths;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Use Case pour la gestion CRUD des consoles.
 * Extracted from ConsoleManagerActivity to improve modularity and testability.
 * 
 * Gère:
 * - Sauvegarde de console.json
 * - Création de répertoires console
 * - Validation des configurations
 */
public class ManageConsoleUseCase {
    
    private static final String TAG = "ManageConsoleUseCase";
    
    private final Context context;
    
    /**
     * Callback pour les dialogs UI
     */
    public interface DialogCallback {
        void showCreateDirectoryDialog(String message, Runnable onConfirm);
        void showGamelistMissingDialog();
        void showSuccess(String message);
        void showError(String message);
        void onConfigSaved(); // Pour recharger la liste
    }
    
    public ManageConsoleUseCase(Context context) {
        this.context = context;
    }
    
    /**
     * Sauvegarder la configuration d'une console
     */
    public void saveConsoleConfig(String id, String name, String fullName, String defaultCore, 
                                   String extensions, String color, DialogCallback callback) {
        new Thread(() -> {
            try {
                // Vérifier si le répertoire de la console existe
                File consoleDir = new File(GameLibraryPaths.getRomsDirForConsole(id));
                
                if (!consoleDir.exists()) {
                    // Demander à l'utilisateur s'il veut créer le répertoire
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        callback.showCreateDirectoryDialog(
                            "The directory '" + id + "/' does not exist.\n\n" +
                            "Path: " + consoleDir.getAbsolutePath() + "\n\n" +
                            "Do you want to create it?",
                            () -> createConsoleDirectory(consoleDir, id, name, fullName, defaultCore, extensions, color, callback)
                        );
                    });
                    return;
                }
                
                // Vérifier si gamelist.json existe
                File gamelistFile = new File(consoleDir, "gamelist.json");
                if (!gamelistFile.exists()) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        callback.showGamelistMissingDialog();
                    });
                    return;
                }
                
                // Créer le contenu console.json
                JSONObject config = new JSONObject();
                config.put("name", name);
                config.put("fullName", fullName);
                config.put("defaultCore", defaultCore);
                config.put("color", color);
                config.put("enabled", true);
                
                // Parse extensions
                JSONArray extsArray = new JSONArray();
                for (String ext : extensions.split(",")) {
                    String trimmed = ext.trim();
                    if (!trimmed.isEmpty()) {
                        extsArray.put(trimmed);
                    }
                }
                config.put("extensions", extsArray);
                
                // Parse cores (for now, just use the default one)
                JSONArray coresArray = new JSONArray();
                coresArray.put(defaultCore);
                config.put("cores", coresArray);
                
                // Sauvegarder dans le fichier
                String filePath = GameLibraryPaths.getConsoleConfigPath(id);
                FileOutputStream fos = new FileOutputStream(filePath);
                fos.write(config.toString(2).getBytes("UTF-8"));
                fos.close();
                
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    callback.showSuccess("Console configuration saved: " + id);
                    callback.onConfigSaved(); // Recharger la liste
                });
                
                Log.i(TAG, "Console config saved: " + filePath);
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving console config", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    callback.showError("Error: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Créer un répertoire console avec structure de base
     */
    private void createConsoleDirectory(File consoleDir, String id, String name, String fullName, 
                                       String defaultCore, String extensions, String color, DialogCallback callback) {
        new Thread(() -> {
            try {
                // Créer le répertoire
                if (!consoleDir.mkdirs()) {
                    throw new Exception("Failed to create directory");
                }
                
                // Créer un gamelist.json vide de base
                File gamelistFile = new File(consoleDir, "gamelist.json");
                JSONObject emptyGamelist = new JSONObject();
                JSONArray emptyGames = new JSONArray();
                emptyGamelist.put("games", emptyGames);
                
                FileOutputStream fos = new FileOutputStream(gamelistFile);
                fos.write(emptyGamelist.toString(2).getBytes("UTF-8"));
                fos.close();
                
                // Créer les répertoires media
                new File(consoleDir, "media/box2d").mkdirs();
                new File(consoleDir, "media/screenshot").mkdirs();
                
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    callback.showSuccess("Directory created: " + id + "\nNow add ROMs and update gamelist.json");
                });
                
                // Maintenant sauvegarder la config
                saveConsoleConfig(id, name, fullName, defaultCore, extensions, color, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating console directory", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    callback.showError("Error creating directory: " + e.getMessage());
                });
            }
        }).start();
    }
}
