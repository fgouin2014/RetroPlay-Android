package com.retroplay.helpers;

import android.util.Log;
import com.retroplay.Game;
import java.io.File;

/**
 * Helper pour résoudre les chemins ROM
 * Extrait de GameDetailsActivity pour améliorer la modularité
 */
public class RomPathResolver {
    private static final String TAG = "RomPathResolver";
    private static final String GAMELIBRARY_DIR = "/storage/emulated/0/GameLibrary-Data";
    
    /**
     * Résout le chemin complet du fichier ROM depuis un objet Game
     * @param game L'objet Game contenant les informations du jeu
     * @return Le chemin complet du fichier ROM, ou null si non trouvé
     */
    public static String resolveRomPath(Game game) {
        if (game == null) {
            return null;
        }
        
        try {
            String gamePath = game.getPath();
            if (gamePath == null || gamePath.isEmpty()) {
                Log.w(TAG, "Game path is null or empty");
                return null;
            }
            
            // Normaliser le chemin (enlever "./" si présent)
            if (gamePath.startsWith("./")) {
                gamePath = gamePath.substring(2);
            }
            
            // Extraire le nom de fichier
            String fileName = gamePath;
            int lastSlash = fileName.lastIndexOf("/");
            if (lastSlash >= 0) {
                fileName = fileName.substring(lastSlash + 1);
            }
            
            // Obtenir le répertoire de la console
            String consoleName = game.getConsole().toLowerCase();
            String consoleDir = getRealConsoleDirectory(consoleName);
            
            // Construire le chemin complet
            String fullPath = GAMELIBRARY_DIR + "/" + consoleDir + "/" + fileName;
            
            // Vérifier que le fichier existe
            File romFile = new File(fullPath);
            if (romFile.exists()) {
                return fullPath;
            } else {
                Log.w(TAG, "ROM file not found: " + fullPath);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving ROM path", e);
            return null;
        }
    }
    
    /**
     * Résout le chemin ROM pour les métadonnées (peut retourner un chemin même si le fichier n'existe pas)
     */
    public static String resolveRomPathForMetadata(Game game) {
        if (game == null) {
            return null;
        }
        
        try {
            String gamePath = game.getPath();
            if (gamePath == null || gamePath.isEmpty()) {
                return null;
            }
            
            // Normaliser le chemin
            if (gamePath.startsWith("./")) {
                gamePath = gamePath.substring(2);
            }
            
            // Extraire le nom de fichier
            String fileName = gamePath;
            int lastSlash = fileName.lastIndexOf("/");
            if (lastSlash >= 0) {
                fileName = fileName.substring(lastSlash + 1);
            }
            
            // Obtenir le répertoire de la console
            String consoleName = game.getConsole().toLowerCase();
            String consoleDir = getRealConsoleDirectory(consoleName);
            
            // Construire le chemin complet
            return GAMELIBRARY_DIR + "/" + consoleDir + "/" + fileName;
        } catch (Exception e) {
            Log.e(TAG, "Error resolving ROM path for metadata", e);
            return null;
        }
    }
    
    /**
     * Mappe le nom de console au vrai nom de répertoire sur le device
     * Résout les problèmes de duplication (lynx/atarilynx, sms/mastersystem, etc.)
     */
    private static String getRealConsoleDirectory(String consoleName) {
        // Retourner le nom original du répertoire
        // L'ID canonique est utilisé pour la configuration (cores, extensions, etc.)
        // mais le nom du répertoire reste celui de l'utilisateur
        return consoleName.toLowerCase();
    }
}
