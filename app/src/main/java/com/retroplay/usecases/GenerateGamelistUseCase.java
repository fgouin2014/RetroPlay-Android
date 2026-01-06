package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import com.retroplay.helpers.GameLibraryPaths;
import com.retroplay.helpers.RomFileHelper;
import com.retroplay.models.ScannedRom;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Use Case pour la génération de gamelist.json.
 * Extracted from ConsoleManagerActivity to improve modularity and testability.
 * 
 * Gère:
 * - Scan des ROMs dans un répertoire console
 * - Génération de gamelist.json
 * - Merge/Replace des gamelists existants
 */
public class GenerateGamelistUseCase {

    private static final String TAG = "GenerateGamelistUseCase";

    private final Context context;

    /**
     * Callback pour la progression
     */
    public interface ProgressCallback {
        void showProgress(String title, String message, boolean indeterminate);

        void dismissProgress();

        void showToast(String message, int duration);
    }

    /**
     * Callback pour le preview dialog
     */
    public interface PreviewCallback {
        void showPreview(String consoleId, List<ScannedRom> roms, boolean gamelistExists);
    }

    /**
     * Callback après sauvegarde
     */
    public interface SaveCallback {
        void onGamelistSaved(String consoleId, int romsAdded);
    }

    public GenerateGamelistUseCase(Context context) {
        this.context = context;
    }

    /**
     * Scanner les ROMs et générer un gamelist
     */
    public void scanRomsAndGenerateGamelist(String consoleId, String extensionsStr,
            ProgressCallback progressCallback,
            PreviewCallback previewCallback) {
        progressCallback.showProgress("Scanning ROMs...", "Please wait while scanning directory...", true);

        new Thread(() -> {
            try {
                // Vérifier que le répertoire existe
                File consoleDir = new File(GameLibraryPaths.getRomsDirForConsole(consoleId));
                if (!consoleDir.exists() || !consoleDir.isDirectory()) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        progressCallback.dismissProgress();
                        progressCallback.showToast(
                                "Console directory not found: " + consoleDir.getAbsolutePath(),
                                android.widget.Toast.LENGTH_LONG);
                    });
                    return;
                }

                // Charger les extensions depuis console.json si présent
                List<String> extensions = new ArrayList<>();
                File consoleJsonFile = new File(consoleDir, "console.json");
                if (consoleJsonFile.exists()) {
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
                }

                // Si pas d'extensions dans console.json, utiliser celles fournies
                if (extensions.isEmpty() && extensionsStr != null && !extensionsStr.isEmpty()) {
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
                }

                // Si toujours pas d'extensions, utiliser les extensions par défaut de
                // RomScanner
                if (extensions.isEmpty()) {
                    Log.i(TAG, "No extensions specified, using default ROM extensions");
                }

                // Scanner le répertoire pour les ROMs
                File[] files = consoleDir.listFiles();
                List<ScannedRom> scannedRoms = new ArrayList<>();
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
                                // Utiliser la détection par défaut
                                isRom = RomFileHelper.isRomFile(fileName);
                            }

                            if (isRom) {
                                String baseName = RomFileHelper.getBaseNameFromFile(fileName);

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

                final int romsFound = scannedRoms.size();
                final List<ScannedRom> finalRoms = scannedRoms;

                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    progressCallback.dismissProgress();

                    if (romsFound == 0) {
                        progressCallback.showToast(
                                "No ROMs found in directory",
                                android.widget.Toast.LENGTH_LONG);
                        return;
                    }

                    // Vérifier si un gamelist.json existe déjà
                    File gamelistFile = new File(GameLibraryPaths.getGamelistPath(consoleId));
                    boolean gamelistExists = gamelistFile.exists();

                    // Afficher le dialog de preview
                    previewCallback.showPreview(consoleId, finalRoms, gamelistExists);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error scanning ROMs", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    progressCallback.dismissProgress();
                    progressCallback.showToast(
                            "Error scanning: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG);
                });
            }
        }).start();
    }

    /**
     * Sauvegarder le gamelist.json généré
     */
    public void saveGeneratedGamelist(String consoleId, List<ScannedRom> roms, boolean merge,
            ProgressCallback progressCallback, SaveCallback saveCallback) {
        progressCallback.showProgress("Saving gamelist.json...", "Please wait...", true);

        new Thread(() -> {
            try {
                // Correctly use the standardized path in roms/<console>/gamelist.json
                File gamelistFile = new File(GameLibraryPaths.getGamelistPath(consoleId));

                JSONArray gamesArray = new JSONArray();

                // Si merge, charger l'existant d'abord
                if (merge && gamelistFile.exists()) {
                    FileInputStream fis = new FileInputStream(gamelistFile);
                    byte[] buffer = new byte[(int) gamelistFile.length()];
                    fis.read(buffer);
                    fis.close();
                    String json = new String(buffer, "UTF-8");

                    JSONObject existingGamelist = new JSONObject(json);
                    if (existingGamelist.has("games")) {
                        gamesArray = existingGamelist.getJSONArray("games");
                    }
                }

                // Ajouter les nouveaux ROMs
                for (ScannedRom rom : roms) {
                    // Vérifier si le ROM existe déjà (si merge)
                    boolean exists = false;
                    if (merge) {
                        for (int i = 0; i < gamesArray.length(); i++) {
                            JSONObject game = gamesArray.getJSONObject(i);
                            if (game.getString("path").equals(rom.path)) {
                                exists = true;
                                break;
                            }
                        }
                    }

                    if (!exists) {
                        JSONObject game = new JSONObject();
                        game.put("id", rom.id);
                        game.put("name", rom.name);
                        game.put("path", rom.path);
                        game.put("desc", "Custom ROM - No description available");
                        game.put("image", rom.hasBox2dImage ? "./media/box2d/" + rom.name + ".png"
                                : "./media/box2d/fallback.png");
                        game.put("screenshot", rom.hasScreenshot ? "./media/screenshots/" + rom.name + ".png"
                                : "./media/screenshots/fallback.png");
                        game.put("thumbnail", rom.hasBox2dImage ? "./media/box2d/" + rom.name + ".png"
                                : "./media/box2d/fallback.png");
                        game.put("releasedate", "Unknown");
                        game.put("genre", "Custom");
                        game.put("players", "1-2");

                        gamesArray.put(game);
                    }
                }

                // Créer le gamelist final
                JSONObject gamelist = new JSONObject();
                gamelist.put("games", gamesArray);

                // Sauvegarder
                FileOutputStream fos = new FileOutputStream(gamelistFile);
                fos.write(gamelist.toString(2).getBytes("UTF-8"));
                fos.close();

                final int romsAdded = roms.size();
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    progressCallback.dismissProgress();
                    progressCallback.showToast(
                            "Gamelist saved successfully!\n" + romsAdded + " ROMs added",
                            android.widget.Toast.LENGTH_LONG);
                    saveCallback.onGamelistSaved(consoleId, romsAdded);
                });

                Log.i(TAG, "Gamelist saved: " + gamelistFile.getAbsolutePath() + " (" + romsAdded + " ROMs)");

            } catch (Exception e) {
                Log.e(TAG, "Error saving gamelist", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    progressCallback.dismissProgress();
                    progressCallback.showToast(
                            "Error saving gamelist: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG);
                });
            }
        }).start();
    }
}
