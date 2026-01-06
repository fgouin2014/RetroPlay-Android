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
    
    /**
     * Résout le chemin complet du fichier ROM depuis un objet Game
     * Gère les caractères spéciaux (!, +, &, etc.) via scan de répertoire
     * @param game L'objet Game contenant les informations du jeu
     * @return Le chemin complet du fichier ROM, ou un chemin par défaut si non trouvé
     */
    public static String resolveRomPath(Game game) {
        if (game == null) {
            return null;
        }
        
        final String consoleDir = getRealConsoleDirectory(game.getConsole());
        
        try {
            // 1. Essayer d'abord game.getFile() (chemin local direct)
            String localPath = game.getFile();
            if (localPath != null && !localPath.isEmpty()) {
                File directFile = new File(localPath);
                if (directFile.exists()) {
                    Log.i(TAG, "ROM file found via getFile(): " + directFile.getAbsolutePath());
                    return directFile.getAbsolutePath();
                } else {
                    Log.w(TAG, "ROM file not found via getFile(): " + localPath);
                }
            }
            
            // 2. Fallback: utiliser game.getPath() pour construire le chemin
            String rawPath = game.getPath();
            if (rawPath == null || rawPath.isEmpty()) {
                Log.e(TAG, "Could not resolve ROM path, using default");
                return GameLibraryPaths.getRomsDirForConsole(consoleDir) + "/unknown.rom";
            }
            
            // Nettoyer le chemin (enlever "./" si présent)
            String cleanPath = rawPath.startsWith("./") ? rawPath.substring(2) : rawPath;
            
            // Extraire le nom du fichier depuis cleanPath
            int lastSlash = cleanPath.lastIndexOf('/');
            String fileName = lastSlash >= 0 ? cleanPath.substring(lastSlash + 1) : cleanPath;
            
            // 3. Méthode robuste: chercher le fichier en listant le répertoire
            // Cela gère correctement les caractères spéciaux (!, +, &, etc.)
            File consoleDirFile = new File(GameLibraryPaths.getRomsDirForConsole(consoleDir));
            if (consoleDirFile.exists() && consoleDirFile.isDirectory()) {
                File[] files = consoleDirFile.listFiles();
                if (files != null) {
                    for (File f : files) {
                        // Comparer le nom de fichier exact (gère les caractères spéciaux)
                        if (f.isFile() && f.getName().equals(fileName)) {
                            Log.i(TAG, "ROM file found by directory scan: " + f.getAbsolutePath());
                            return f.getAbsolutePath();
                        }
                    }
                }
            }
            
            // 4. Essayer avec le chemin complet
            File candidate = new File(GameLibraryPaths.ROMS_DIR + "/" + cleanPath);
            if (candidate.exists()) {
                Log.i(TAG, "ROM file found via full path: " + candidate.getAbsolutePath());
                return candidate.getAbsolutePath();
            }
            
            // 5. Essayer avec le répertoire de console
            File remapped = new File(GameLibraryPaths.getRomsDirForConsole(consoleDir), cleanPath);
            if (remapped.exists()) {
                Log.i(TAG, "ROM file found via console dir: " + remapped.getAbsolutePath());
                return remapped.getAbsolutePath();
            }
            
            // 6. Essayer juste le nom de fichier dans le répertoire de console
            File finalCandidate = new File(GameLibraryPaths.getRomsDirForConsole(consoleDir), fileName);
            if (finalCandidate.exists()) {
                Log.i(TAG, "ROM file found by filename only: " + finalCandidate.getAbsolutePath());
                return finalCandidate.getAbsolutePath();
            }
            
            // 7. Dernier recours: retourner le chemin même s'il n'existe pas
            String fallbackPath = GameLibraryPaths.getRomsDirForConsole(consoleDir) + "/" + fileName;
            Log.w(TAG, "ROM file not found, using fallback path: " + fallbackPath);
            return fallbackPath;
            
        } catch (Exception e) {
            Log.e(TAG, "Error resolving ROM path", e);
            return GameLibraryPaths.getRomsDirForConsole(consoleDir) + "/unknown.rom";
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
            
            // Construire le chemin complet (ROMs dans /roms/{console}/)
            return GameLibraryPaths.getRomsDirForConsole(consoleDir) + "/" + fileName;
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
