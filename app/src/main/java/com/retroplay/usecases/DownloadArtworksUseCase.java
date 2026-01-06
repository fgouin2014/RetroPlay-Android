package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import com.retroplay.ArtworkDownloadHelper;
import com.retroplay.helpers.GameLibraryPaths;
import com.retroplay.helpers.RomFileHelper;
import com.retroplay.models.ConsoleConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Use Case pour le téléchargement des artworks.
 * Extracted from ConsoleManagerActivity to improve modularity and testability.
 * 
 * Gère:
 * - Téléchargement d'artworks pour une ou plusieurs consoles
 * - Mode "missing only" ou "refresh all"
 * - Progression et rapports d'erreurs
 */
public class DownloadArtworksUseCase {
    
    private static final String TAG = "DownloadArtworksUseCase";
    
    private final Context context;
    
    /**
     * Callback pour la progression
     */
    public interface ProgressCallback {
        void showProgress(String title, String message, int max, boolean indeterminate);
        void updateProgress(int progress, String message);
        void dismissProgress();
        void showToast(String message, int duration);
        void showResultDialog(String title, String message);
    }
    
    public DownloadArtworksUseCase(Context context) {
        this.context = context;
    }
    
    /**
     * Télécharger les artworks pour une liste de consoles
     */
    public void downloadArtworksForConsoles(List<ConsoleConfig> targetConsoles, boolean missingOnly, 
                                             ProgressCallback progressCallback) {
        if (targetConsoles == null || targetConsoles.isEmpty()) {
            progressCallback.showToast("No consoles selected", android.widget.Toast.LENGTH_SHORT);
            return;
        }
        
        progressCallback.showProgress(
            missingOnly ? "Downloading Missing Art" : "Refreshing Art", 
            "Preparing...", 
            0, 
            true);
        
        new Thread(() -> {
            try {
                Map<ConsoleConfig, List<String>> romMap = new HashMap<>();
                int totalGames = 0;
                for (ConsoleConfig console : targetConsoles) {
                    List<String> roms = getRomBaseNames(console.id);
                    romMap.put(console, roms);
                    totalGames += roms.size();
                }
                
                if (totalGames == 0) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        progressCallback.dismissProgress();
                        progressCallback.showToast("No ROMs found for selected consoles", android.widget.Toast.LENGTH_LONG);
                    });
                    return;
                }
                
                final int maxProgress = totalGames;
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    progressCallback.updateProgress(0, "Starting download...");
                });
                
                final int[] processed = {0};
                final int[] downloaded = {0};
                final int[] skipped = {0};
                final int[] errors = {0};
                final StringBuilder errorLog = new StringBuilder();
                
                for (ConsoleConfig console : targetConsoles) {
                    List<String> roms = romMap.get(console);
                    if (roms == null || roms.isEmpty()) {
                        continue;
                    }
                    
                    for (String baseName : roms) {
                        final String progressMessage = console.name + " - " + baseName;
                        mainHandler.post(() -> {
                            progressCallback.updateProgress(processed[0], progressMessage);
                        });
                        
                        try {
                            ArtworkDownloadHelper.DownloadResult result = ArtworkDownloadHelper.downloadArtwork(
                                console.id, baseName, !missingOnly, missingOnly);
                            if (result.error != null) {
                                errors[0]++;
                                errorLog.append(console.name).append(" / ").append(baseName).append(": ").append(result.error).append("\n");
                            } else if (result.hasAnyDownload()) {
                                downloaded[0]++;
                            } else if (result.skipped) {
                                skipped[0]++;
                            }
                        } catch (Exception e) {
                            errors[0]++;
                            errorLog.append(console.name).append(" / ").append(baseName).append(": ").append(e.getMessage()).append("\n");
                            Log.e(TAG, "Artwork download failed", e);
                        }
                        
                        processed[0]++;
                        final int currentProgress = processed[0];
                        mainHandler.post(() -> {
                            progressCallback.updateProgress(currentProgress, progressMessage);
                        });
                    }
                }
                
                final int processedFinal = processed[0];
                final int downloadedFinal = downloaded[0];
                final int skippedFinal = skipped[0];
                final int errorsFinal = errors[0];
                final String errorSummary = errorLog.toString();
                
                mainHandler.post(() -> {
                    progressCallback.dismissProgress();
                    StringBuilder summary = new StringBuilder();
                    summary.append("Games processed: ").append(processedFinal).append("\n");
                    summary.append("Downloaded: ").append(downloadedFinal).append("\n");
                    summary.append("Skipped: ").append(skippedFinal).append("\n");
                    if (errorsFinal > 0) {
                        summary.append("Errors: ").append(errorsFinal).append("\n\n").append(errorSummary);
                    }
                    progressCallback.showResultDialog("Artwork Download", summary.toString());
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading artworks", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    progressCallback.dismissProgress();
                    progressCallback.showToast("Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
                });
            }
        }).start();
    }
    
    /**
     * Obtenir la liste des noms de base des ROMs pour une console
     */
    private List<String> getRomBaseNames(String consoleId) {
        List<String> roms = new ArrayList<>();
        File consoleDir = new File(GameLibraryPaths.getRomsDirForConsole(consoleId));
        if (!consoleDir.exists() || !consoleDir.isDirectory()) {
            return roms;
        }
        File[] files = consoleDir.listFiles();
        if (files == null) {
            return roms;
        }
        for (File file : files) {
            if (file.isFile() && RomFileHelper.isRomFile(file.getName())) {
                roms.add(RomFileHelper.getBaseNameFromFile(file.getName()));
            }
        }
        return roms;
    }
}














