package com.retroplay.usecases;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import com.retroplay.utils.AssetFileHelper;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/**
 * Use Case pour l'installation de la base de données de cheats.
 * Extracted from ConsoleManagerActivity to improve modularity and testability.
 * 
 * Gère:
 * - Installation ZIP (mode lecture à la volée)
 * - Extraction complète depuis ZIP
 * - Extraction partielle par système
 * - Copie depuis assets (fallback)
 */
public class InstallCheatsUseCase {
    
    private static final String TAG = "InstallCheatsUseCase";
    private static final String CHEATS_ZIP_PATH = "/storage/emulated/0/GameLibrary-Data/cheats.zip";
    private static final String CHEATS_DIR_PATH = "/storage/emulated/0/GameLibrary-Data/cheats";
    
    private final Context context;
    
    /**
     * Callback pour les dialogs UI
     */
    public interface DialogCallback {
        void showInstallOptions();
        void showSystemSelector();
        void showReinstallDialog(String message, Runnable onReinstall);
        void showAlreadyInstalledDialog(String message, Runnable onReinstall);
    }
    
    /**
     * Callback pour la progression
     */
    public interface ProgressCallback {
        void showProgress(String title, String message, int max, boolean indeterminate);
        void updateProgress(int progress, String message);
        void dismissProgress();
        void showToast(String message, int duration);
    }
    
    /**
     * Callback pour la progression par système (utilisé par AssetFileHelper)
     */
    public interface SystemProgressCallback {
        void onSystemStart(String systemName, int systemIndex, int totalSystems);
        void onFileProgress(String fileName);
    }
    
    /**
     * Copier les cheats depuis assets (fallback si pas de ZIP)
     */
    private void copyCheatsFromAssets(ProgressCallback progressCallback, SystemProgressCallback systemCallback) {
        try {
            AssetManager assetManager = context.getAssets();
            final int[] fileCount = {0};
            
            AssetFileHelper.SystemProgressCallback assetCallback = new AssetFileHelper.SystemProgressCallback() {
                @Override
                public void onSystemStart(String systemName, int systemIndex, int totalSystems) {
                    if (systemCallback != null) {
                        systemCallback.onSystemStart(systemName, systemIndex, totalSystems);
                    }
                    progressCallback.updateProgress((systemIndex * 100) / totalSystems, 
                        "Installing cheats...\n(" + systemIndex + "/" + totalSystems + ") " + systemName.toUpperCase());
                }
                
                @Override
                public void onFileProgress(String fileName) {
                    fileCount[0]++;
                    if (systemCallback != null) {
                        systemCallback.onFileProgress(fileName);
                    }
                }
            };
            
            int filesCopied = AssetFileHelper.copyAssetFolderWithSystemCallback(
                assetManager, "GameLibrary-Data/cheats", CHEATS_DIR_PATH, assetCallback);
            
            progressCallback.updateProgress(100, "Complete");
            progressCallback.dismissProgress();
            progressCallback.showToast("Cheats installed: " + filesCopied + " files", android.widget.Toast.LENGTH_LONG);
            
            Log.i(TAG, "Cheats database installed: " + filesCopied + " files");
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying cheats from assets", e);
            progressCallback.dismissProgress();
            progressCallback.showToast("Error installing cheats: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
        }
    }
    
    public InstallCheatsUseCase(Context context) {
        this.context = context;
    }
    
    /**
     * Vérifie si les cheats sont déjà installés et affiche les options appropriées
     */
    public void checkAndInstallCheats(DialogCallback dialogCallback, ProgressCallback progressCallback) {
        File cheatsZip = new File(CHEATS_ZIP_PATH);
        File cheatsDir = new File(CHEATS_DIR_PATH);
        boolean hasExtractedCheats = cheatsDir.exists() && cheatsDir.listFiles() != null && cheatsDir.listFiles().length > 10;
        
        if (cheatsZip.exists() || hasExtractedCheats) {
            String message = cheatsZip.exists() ? 
                "Cheats ZIP is already installed (on-the-fly mode).\n\nDo you want to reinstall?" :
                "Cheats are already extracted.\n\nDo you want to reinstall?";
            dialogCallback.showAlreadyInstalledDialog(message, () -> dialogCallback.showInstallOptions());
        } else {
            // Première installation - Afficher les options
            dialogCallback.showInstallOptions();
        }
    }
    
    /**
     * Copie cheats.zip sur le device (mode ZIP, instantané)
     */
    public void copyZipToDevice(ProgressCallback progressCallback) {
        progressCallback.showProgress("Installing Cheats", "Copying cheats.zip...", 0, true);
        
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("cheats.zip");
                File destFile = new File(CHEATS_ZIP_PATH);
                FileOutputStream fos = new FileOutputStream(destFile);
                
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                
                fos.close();
                is.close();
                
                progressCallback.dismissProgress();
                progressCallback.showToast("Cheats ready (ZIP mode)!", android.widget.Toast.LENGTH_LONG);
                
                Log.i(TAG, "Cheats ZIP copied to device");
                
            } catch (Exception e) {
                Log.e(TAG, "Error copying cheats ZIP", e);
                progressCallback.dismissProgress();
                progressCallback.showToast("Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
            }
        }).start();
    }
    
    /**
     * Effectue l'installation complète des cheats
     */
    public void performCheatsInstallation(ProgressCallback progressCallback, SystemProgressCallback systemCallback) {
        progressCallback.showProgress("Installing Cheats", "Preparing...", 100, false);
        
        new Thread(() -> {
            try {
                // Vérifier si cheats.zip existe dans les assets
                boolean zipExists = false;
                try {
                    context.getAssets().open("cheats.zip").close();
                    zipExists = true;
                    Log.i(TAG, "Found cheats.zip in assets, using fast extraction (ZIP method)");
                    progressCallback.showToast("Using ZIP extraction (fast)", android.widget.Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    Log.w(TAG, "cheats.zip not found in assets, using folder copy method (slow)");
                    progressCallback.showToast("Using folder copy (slow)", android.widget.Toast.LENGTH_SHORT);
                }
                
                if (zipExists) {
                    // Méthode rapide : Extraire le ZIP
                    extractCheatsZip(progressCallback);
                } else {
                    // Méthode lente : Copier fichier par fichier (fallback)
                    copyCheatsFromAssets(progressCallback, systemCallback);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error installing cheats", e);
                progressCallback.dismissProgress();
                progressCallback.showToast("Error installing cheats: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
            }
        }).start();
    }
    
    /**
     * Extraire cheats.zip depuis les assets (méthode rapide)
     */
    private void extractCheatsZip(ProgressCallback progressCallback) throws Exception {
        String destPath = CHEATS_DIR_PATH;
        File destDir = new File(destPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        progressCallback.updateProgress(10, "Extracting cheats.zip...");
        
        InputStream assetStream = context.getAssets().open("cheats.zip");
        ZipInputStream zipStream = new ZipInputStream(assetStream);
        
        int fileCount = 0;
        String currentSystem = "";
        ZipEntry entry;
        
        while ((entry = zipStream.getNextEntry()) != null) {
            String entryName = entry.getName();
            File outputFile = new File(destDir, entryName);
            
            if (entry.isDirectory()) {
                outputFile.mkdirs();
                
                // Détecter les changements de système (ex: retroarch/nes/, retroarch/snes/)
                if (entryName.startsWith("retroarch/") && entryName.endsWith("/")) {
                    String[] parts = entryName.split("/");
                    if (parts.length >= 2) {
                        String newSystem = parts[1];
                        if (!newSystem.equals(currentSystem) && !newSystem.equals("overrides")) {
                            currentSystem = newSystem;
                            progressCallback.updateProgress(-1, "Extracting cheats...\n" + currentSystem.toUpperCase());
                        }
                    }
                }
            } else {
                // Créer les répertoires parents si nécessaires
                outputFile.getParentFile().mkdirs();
                
                // Extraire le fichier avec buffer 64KB pour performance
                FileOutputStream fos = new FileOutputStream(outputFile);
                byte[] buffer = new byte[65536]; // 64KB buffer
                int bytesRead;
                while ((bytesRead = zipStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                fileCount++;
                
                // Mettre à jour la progression tous les 1000 fichiers (moins fréquent = plus rapide)
                if (fileCount % 1000 == 0) {
                    // Progression approximative (environ 10,000 fichiers total)
                    int progress = Math.min(90, 10 + (fileCount / 110));
                    progressCallback.updateProgress(progress, "Extracting cheats...\n" + currentSystem.toUpperCase());
                }
            }
            
            zipStream.closeEntry();
        }
        
        zipStream.close();
        assetStream.close();
        
        progressCallback.updateProgress(100, "Complete");
        progressCallback.dismissProgress();
        progressCallback.showToast("Cheats installed: " + fileCount + " files (from ZIP)", android.widget.Toast.LENGTH_LONG);
        
        Log.i(TAG, "Cheats extracted from ZIP: " + fileCount + " files");
    }
    
    /**
     * Mapper le nom d'affichage au nom de dossier
     * Future-proof: Utilise un mapping mais peut être étendu
     */
    public static String getSystemFolderName(String displayName) {
        switch (displayName) {
            // Arcade systems
            case "MAME 2003": return "mame2003";
            case "MAME 2003 Plus": return "mame2003_plus";
            case "MAME 2010": return "mame2010";
            case "MAME 2015": return "mame2015";
            case "MAME 2016": return "mame2016";
            case "FBNeo": return "fbneo";
            
            // Console systems
            case "NES": return "nes";
            case "SNES": return "snes";
            case "N64": return "n64";
            case "GB": return "gb";
            case "GBC": return "gbc";
            case "GBA": return "gba";
            case "Genesis": return "genesis";
            case "Master System": return "mastersystem";
            case "Game Gear": return "gamegear";
            case "Sega CD": return "segacd";
            case "32X": return "32x";
            case "PSX": return "psx";
            case "PSP": return "psp";
            case "Atari 2600": return "atari2600";
            case "Atari 5200": return "atari5200";
            case "Atari 7800": return "atari7800";
            case "Lynx": return "atarilynx";
            case "Neo Geo": return "neogeo";
            case "WonderSwan": return "wonderswan";
            case "PC Engine": return "pce";
            case "Virtual Boy": return "virtualboy";
            default: return displayName.toLowerCase();
        }
    }
    
    /**
     * Liste des systèmes disponibles pour le sélecteur
     */
    public static String[] getAvailableSystems() {
        return new String[]{
            "MAME 2003", "MAME 2003 Plus", "MAME 2010", "MAME 2015", "MAME 2016", "FBNeo",
            "NES", "SNES", "N64", "GB", "GBC", "GBA",
            "Genesis", "Master System", "Game Gear", "Sega CD", "32X",
            "PSX", "PSP",
            "Atari 2600", "Atari 5200", "Atari 7800", "Lynx",
            "Neo Geo", "WonderSwan", "PC Engine", "Virtual Boy"
        };
    }
    
    /**
     * Extraire seulement les systèmes sélectionnés depuis le ZIP
     */
    public void extractSelectedSystems(List<String> systems, ProgressCallback progressCallback) {
        progressCallback.showProgress("Installing Cheats", "Extracting selected systems...", systems.size(), false);
        
        new Thread(() -> {
            try {
                File destDir = new File(CHEATS_DIR_PATH);
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }
                
                InputStream assetStream = context.getAssets().open("cheats.zip");
                ZipInputStream zipStream = new ZipInputStream(assetStream);
                
                int filesExtracted = 0;
                int currentSystemIndex = 0;
                String lastSystem = "";
                ZipEntry entry;
                
                while ((entry = zipStream.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    
                    // Vérifier si cette entrée appartient à un système sélectionné
                    boolean shouldExtract = false;
                    String currentSystem = "";
                    
                    for (String system : systems) {
                        if (entryName.startsWith("retroarch/" + system + "/")) {
                            shouldExtract = true;
                            currentSystem = system;
                            break;
                        }
                    }
                    
                    // Extraire aussi les overrides et user
                    if (entryName.startsWith("retroarch/overrides/") || entryName.startsWith("user/")) {
                        shouldExtract = true;
                    }
                    
                    if (shouldExtract) {
                        // Détecter changement de système
                        if (!currentSystem.equals(lastSystem) && !currentSystem.isEmpty()) {
                            lastSystem = currentSystem;
                            currentSystemIndex++;
                            progressCallback.updateProgress(currentSystemIndex, "Extracting " + currentSystem.toUpperCase() + "...");
                        }
                        
                        File outputFile = new File(destDir, entryName);
                        
                        if (entry.isDirectory()) {
                            outputFile.mkdirs();
                        } else {
                            outputFile.getParentFile().mkdirs();
                            
                            FileOutputStream fos = new FileOutputStream(outputFile);
                            byte[] buffer = new byte[65536];
                            int bytesRead;
                            while ((bytesRead = zipStream.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }
                            fos.close();
                            filesExtracted++;
                        }
                    }
                    
                    zipStream.closeEntry();
                }
                
                zipStream.close();
                assetStream.close();
                
                progressCallback.updateProgress(systems.size(), "Complete");
                progressCallback.dismissProgress();
                progressCallback.showToast("Cheats installed: " + filesExtracted + " files (" + systems.size() + " systems)", android.widget.Toast.LENGTH_LONG);
                
                Log.i(TAG, "Selected systems cheats extracted: " + filesExtracted + " files");
                
            } catch (Exception e) {
                Log.e(TAG, "Error extracting selected systems", e);
                progressCallback.dismissProgress();
                progressCallback.showToast("Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG);
            }
        }).start();
    }
}