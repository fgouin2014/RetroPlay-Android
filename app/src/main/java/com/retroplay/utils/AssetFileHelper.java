package com.retroplay.utils;

import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;

/**
 * Helper pour la copie de fichiers depuis les assets.
 * Extracted from ConsoleManagerActivity to improve modularity and reusability.
 * 
 * Gère:
 * - Copie récursive de dossiers assets
 * - Progression par système (pour cheats)
 * - Callbacks pour la progression
 */
public class AssetFileHelper {
    
    private static final String TAG = "AssetFileHelper";
    
    /**
     * Callback pour la progression de copie de fichiers
     */
    public interface FileCopyCallback {
        void onFileProgress(String fileName);
    }
    
    /**
     * Callback pour la progression par système
     */
    public interface SystemProgressCallback {
        void onSystemStart(String systemName, int systemIndex, int totalSystems);
        void onFileProgress(String fileName);
    }
    
    /**
     * Copie récursivement un dossier cheats avec progression par système
     * Utilisé pour l'installation des cheats depuis assets (fallback si pas de ZIP)
     */
    public static int copyAssetFolderWithSystemCallback(AssetManager assetManager, String assetPath, 
                                                         String destPath, SystemProgressCallback callback) throws Exception {
        int filesCopied = 0;
        
        // Chemin vers retroarch (où sont les systèmes)
        String retroarchPath = assetPath + "/retroarch";
        String[] systems = assetManager.list(retroarchPath);
        
        if (systems == null || systems.length == 0) {
            Log.w(TAG, "No systems found in " + retroarchPath);
            return 0;
        }
        
        // Compter seulement les vrais systèmes (pas overrides)
        int totalSystems = 0;
        for (String system : systems) {
            if (!system.equals("overrides")) {
                totalSystems++;
            }
        }
        
        int systemIndex = 0;
        
        // Copier chaque système
        for (String system : systems) {
            if (system.equals("overrides")) {
                // Copier overrides à la fin sans notification
                filesCopied += copyAssetFolderWithCallback(assetManager, retroarchPath + "/overrides", 
                    destPath + "/retroarch/overrides", callback::onFileProgress);
                continue;
            }
            
            systemIndex++;
            callback.onSystemStart(system, systemIndex, totalSystems);
            
            String systemAssetPath = retroarchPath + "/" + system;
            String systemDestPath = destPath + "/retroarch/" + system;
            
            filesCopied += copyAssetFolderWithCallback(assetManager, systemAssetPath, systemDestPath, callback::onFileProgress);
        }
        
        // Copier aussi le dossier user si présent
        try {
            String userPath = assetPath + "/user";
            String[] userFiles = assetManager.list(userPath);
            if (userFiles != null && userFiles.length > 0) {
                filesCopied += copyAssetFolderWithCallback(assetManager, userPath, destPath + "/user", callback::onFileProgress);
            }
        } catch (Exception e) {
            Log.d(TAG, "No user cheats folder");
        }
        
        return filesCopied;
    }
    
    /**
     * Copie récursivement un dossier depuis assets avec callback
     */
    public static int copyAssetFolderWithCallback(AssetManager assetManager, String assetPath, 
                                                    String destPath, FileCopyCallback callback) throws Exception {
        int filesCopied = 0;
        
        String[] files = assetManager.list(assetPath);
        if (files == null || files.length == 0) {
            return 0;
        }
        
        // Créer le répertoire de destination
        File destDir = new File(destPath);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        for (String fileName : files) {
            String assetFilePath = assetPath + "/" + fileName;
            String destFilePath = destPath + "/" + fileName;
            
            try {
                // Vérifier si c'est un dossier ou un fichier
                String[] subFiles = assetManager.list(assetFilePath);
                if (subFiles != null && subFiles.length > 0) {
                    // C'est un dossier, copie récursive
                    filesCopied += copyAssetFolderWithCallback(assetManager, assetFilePath, destFilePath, callback);
                } else {
                    // C'est un fichier
                    File destFile = new File(destFilePath);
                    if (!destFile.exists()) {
                        InputStream is = assetManager.open(assetFilePath);
                        FileOutputStream fos = new FileOutputStream(destFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        
                        is.close();
                        fos.close();
                        filesCopied++;
                        
                        // Notifier la progression
                        if (callback != null) {
                            callback.onFileProgress(fileName);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error copying " + assetFilePath + ": " + e.getMessage());
            }
        }
        
        return filesCopied;
    }
}
















