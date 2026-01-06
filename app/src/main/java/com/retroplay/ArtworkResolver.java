package com.retroplay;

import android.text.TextUtils;
import android.util.Log;
import com.retroplay.helpers.GameLibraryPaths;

import java.io.File;
import java.util.Locale;

/**
 * Résout les artworks (boxart, screenshots) en respectant les dossiers GameLibrary (media/)
 * ainsi qu'un répertoire de téléchargement dédié (media_download).
 */
public final class ArtworkResolver {
    private static final String TAG = "ArtworkResolver";

    private static final String DOWNLOAD_MEDIA_DIR = "media_download"; // GameLibrary-Data/media_download/<console>/media/...

    private ArtworkResolver() {
        // Utility
    }

    public static String resolveBoxArt(Game game) {
        return resolveArtworkInternal(game, ArtworkType.BOXART);
    }

    public static String resolveScreenshot(Game game) {
        return resolveArtworkInternal(game, ArtworkType.SCREENSHOT);
    }

    private static String resolveArtworkInternal(Game game, ArtworkType type) {
        if (game == null) {
            return fallbackForConsole("nes", "nes", type);
        }

        String console = sanitizeConsole(game.getConsole());
        String canonicalConsole = ConsoleNameMapper.normalizeToCanonical(console);
        
        // PRIORITÉ 1: Utiliser le chemin d'image depuis le gamelist.json s'il existe
        // Les scripts Python génèrent des chemins comme "./media/box2d/GameName.png"
        if (type == ArtworkType.BOXART && game.imagePath != null && !game.imagePath.isEmpty()) {
            String imagePath = game.imagePath;
            Log.d(TAG, "Using image path from gamelist.json: " + imagePath + " for game: " + game.getName());
            
            // Si le chemin commence par "./", construire le chemin complet (dans /roms/{console}/)
            if (imagePath.startsWith("./")) {
                String relativePath = imagePath.substring(2); // Enlever "./"
                String fullPath = GameLibraryPaths.getRomsDirForConsole(console) + "/" + relativePath;
                File imageFile = new File(fullPath);
                if (imageFile.exists()) {
                    Log.d(TAG, "✓ Found image from gamelist.json path: " + fullPath);
                    return "file://" + fullPath;
                } else {
                    Log.w(TAG, "✗ Image from gamelist.json does not exist: " + fullPath + " - will try fallback");
                }
                
                // Essayer avec le nom canonique
                if (!canonicalConsole.equals(console)) {
                    String canonicalPath = GameLibraryPaths.getRomsDirForConsole(canonicalConsole) + "/" + relativePath;
                    File canonicalFile = new File(canonicalPath);
                    if (canonicalFile.exists()) {
                        Log.d(TAG, "✓ Found image from gamelist.json path (canonical): " + canonicalPath);
                        return "file://" + canonicalPath;
                    } else {
                        Log.w(TAG, "✗ Image from gamelist.json (canonical) does not exist: " + canonicalPath);
                    }
                }
            } else {
                // Chemin absolu ou autre format - essayer directement
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    Log.d(TAG, "✓ Found image from gamelist.json (absolute path): " + imagePath);
                    return "file://" + imagePath;
                } else {
                    Log.w(TAG, "✗ Image from gamelist.json (absolute) does not exist: " + imagePath);
                }
            }
        }
        
        if (type == ArtworkType.SCREENSHOT && game.screenshotPath != null && !game.screenshotPath.isEmpty()) {
            String screenshotPath = game.screenshotPath;
            Log.d(TAG, "Using screenshot path from gamelist.json: " + screenshotPath + " for game: " + game.getName());
            
            // Si le chemin commence par "./", construire le chemin complet (dans /roms/{console}/)
            if (screenshotPath.startsWith("./")) {
                String relativePath = screenshotPath.substring(2); // Enlever "./"
                String fullPath = GameLibraryPaths.getRomsDirForConsole(console) + "/" + relativePath;
                File screenshotFile = new File(fullPath);
                if (screenshotFile.exists()) {
                    Log.d(TAG, "✓ Found screenshot from gamelist.json path: " + fullPath);
                    return "file://" + fullPath;
                } else {
                    Log.w(TAG, "✗ Screenshot from gamelist.json does not exist: " + fullPath + " - will try fallback");
                }
                
                // Essayer avec le nom canonique
                if (!canonicalConsole.equals(console)) {
                    String canonicalPath = GameLibraryPaths.getRomsDirForConsole(canonicalConsole) + "/" + relativePath;
                    File canonicalFile = new File(canonicalPath);
                    if (canonicalFile.exists()) {
                        Log.d(TAG, "✓ Found screenshot from gamelist.json path (canonical): " + canonicalPath);
                        return "file://" + canonicalPath;
                    } else {
                        Log.w(TAG, "✗ Screenshot from gamelist.json (canonical) does not exist: " + canonicalPath);
                    }
                }
            } else {
                // Chemin absolu ou autre format - essayer directement
                File screenshotFile = new File(screenshotPath);
                if (screenshotFile.exists()) {
                    Log.d(TAG, "✓ Found screenshot from gamelist.json (absolute path): " + screenshotPath);
                    return "file://" + screenshotPath;
                } else {
                    Log.w(TAG, "✗ Screenshot from gamelist.json (absolute) does not exist: " + screenshotPath);
                }
            }
        }
        
        // PRIORITÉ 2: Reconstruire depuis baseName (fallback si pas de chemin dans JSON ou si le chemin du JSON n'existe pas)
        String baseName = sanitizeBaseName(game.getBaseName());
        Log.d(TAG, "Resolving artwork (fallback to baseName): game=" + game.getName() + ", console=" + console + " (canonical=" + canonicalConsole + "), baseName=" + baseName + ", type=" + type);

        // IMPORTANT: Essayer d'abord avec le nom original (realConsoleDirectory) car c'est là que sont les images
        // Puis essayer avec le nom canonique pour gérer les cas où le répertoire a été renommé
        // 1) GameLibrary-Data/roms/<console>/media/ (essayer nom original puis canonique)
        String direct = resolveFromConsoleMedia(console, canonicalConsole, baseName, type);
        if (!TextUtils.isEmpty(direct)) {
            Log.d(TAG, "Found artwork in console media: " + direct);
            return direct;
        }

        // 2) GameLibrary-Data/media_download/<console>/media/ (essayer nom original puis canonique)
        String downloaded = resolveFromDownloadedMedia(console, canonicalConsole, baseName, type);
        if (!TextUtils.isEmpty(downloaded)) {
            Log.d(TAG, "Found artwork in downloaded media: " + downloaded);
            return downloaded;
        }

        // 3) fallback.png dans le dossier du jeu (GameLibrary-Data/roms/<console>/media/)
        Log.d(TAG, "No artwork found, using fallback for console=" + console + " (canonical=" + canonicalConsole + ")");
        return fallbackForConsole(console, canonicalConsole, type);
    }

    private static String resolveFromConsoleMedia(String console, String canonicalConsole, String baseName, ArtworkType type) {
        String[] candidates = buildMediaCandidates(baseName, type, false);
        
        // IMPORTANT: Essayer d'abord avec le nom original (realConsoleDirectory) car c'est là que sont les images
        // Le nom original est celui stocké dans game.getConsole() qui vient de realConsoleDirectory
        // Médias maintenant dans /roms/{console}/media/
        for (String relative : candidates) {
            File mediaFile = new File(GameLibraryPaths.getRomsDirForConsole(console), relative);
            if (mediaFile.exists()) {
                Log.d(TAG, "Found in original dir: " + mediaFile.getAbsolutePath());
                return "file://" + mediaFile.getAbsolutePath();
            }
        }
        
        // Si non trouvé, essayer avec le nom canonique (pour gérer les cas où le répertoire a été renommé)
        if (!canonicalConsole.equals(console)) {
            for (String relative : candidates) {
                File mediaFile = new File(GameLibraryPaths.getRomsDirForConsole(canonicalConsole), relative);
                if (mediaFile.exists()) {
                    Log.d(TAG, "Found in canonical dir: " + mediaFile.getAbsolutePath());
                    return "file://" + mediaFile.getAbsolutePath();
                }
            }
        }
        
        Log.d(TAG, "Not found in console media: console=" + console + ", canonical=" + canonicalConsole + ", baseName=" + baseName);
        return null;
    }

    private static String resolveFromDownloadedMedia(String console, String canonicalConsole, String baseName, ArtworkType type) {
        String[] candidates = buildMediaCandidates(baseName, type, true);
        
        // IMPORTANT: Essayer d'abord avec le nom original (realConsoleDirectory) car c'est là que sont les images
        for (String relative : candidates) {
            File mediaFile = new File(GameLibraryPaths.MEDIA_DOWNLOAD_DIR + "/" + console, relative);
            if (mediaFile.exists()) {
                Log.d(TAG, "Found in original downloaded dir: " + mediaFile.getAbsolutePath());
                return "file://" + mediaFile.getAbsolutePath();
            }
        }
        
        // Si non trouvé, essayer avec le nom canonique (pour gérer les cas où le répertoire a été renommé)
        if (!canonicalConsole.equals(console)) {
            for (String relative : candidates) {
                File mediaFile = new File(GameLibraryPaths.MEDIA_DOWNLOAD_DIR + "/" + canonicalConsole, relative);
                if (mediaFile.exists()) {
                    Log.d(TAG, "Found in canonical downloaded dir: " + mediaFile.getAbsolutePath());
                    return "file://" + mediaFile.getAbsolutePath();
                }
            }
        }
        
        return null;
    }

    private static String[] buildMediaCandidates(String baseName, ArtworkType type, boolean isDownload) {
        if (type == ArtworkType.BOXART) {
            // Essayer plusieurs variantes pour box2d
            return new String[]{
                    "media/box2d/" + baseName + ".png",
                    "media/box2d/" + baseName + ".jpg",
                    "media/box/" + baseName + ".png",
                    "media/box/" + baseName + ".jpg"
            };
        }
        if (isDownload) {
            return new String[]{
                    "media/screenshots/" + baseName + ".png",
                    "media/screenshot/" + baseName + ".png",
                    "media/screenshott/" + baseName + ".png",
                    "media/screenshots/" + baseName + ".jpg",
                    "media/screenshot/" + baseName + ".jpg"
            };
        }
        return new String[]{
                "media/screenshots/" + baseName + ".png",
                "media/screenshot/" + baseName + ".png",
                "media/screenshott/" + baseName + ".png",
                "media/screenshots/" + baseName + ".jpg",
                "media/screenshot/" + baseName + ".jpg"
        };
    }

    private static String fallbackForConsole(String console, String canonicalConsole, ArtworkType type) {
        if (TextUtils.isEmpty(console)) {
            return "android.resource://android/drawable/ic_menu_gallery";
        }

        String[] candidates;
        if (type == ArtworkType.BOXART) {
            candidates = new String[]{ "media/box2d/fallback.png" };
        } else {
            candidates = new String[]{
                    "media/screenshots/fallback.png",
                    "media/screenshot/fallback.png",
                    "media/screenshott/fallback.png"
            };
        }

        // IMPORTANT: Essayer d'abord avec le nom original (realConsoleDirectory) car c'est là que sont les images
        // Médias maintenant dans /roms/{console}/media/
        for (String relative : candidates) {
            File fallbackFile = new File(GameLibraryPaths.getRomsDirForConsole(console), relative);
            if (fallbackFile.exists()) {
                Log.d(TAG, "Found fallback in original dir: " + fallbackFile.getAbsolutePath());
                return "file://" + fallbackFile.getAbsolutePath();
            }

            File downloadFallback = new File(GameLibraryPaths.MEDIA_DOWNLOAD_DIR + "/" + console, relative);
            if (downloadFallback.exists()) {
                Log.d(TAG, "Found fallback in original downloaded dir: " + downloadFallback.getAbsolutePath());
                return "file://" + downloadFallback.getAbsolutePath();
            }
        }

        // Si non trouvé, essayer avec le nom canonique (pour gérer les cas où le répertoire a été renommé)
        if (!canonicalConsole.equals(console)) {
            for (String relative : candidates) {
                File fallbackFile = new File(GameLibraryPaths.getRomsDirForConsole(canonicalConsole), relative);
                if (fallbackFile.exists()) {
                    Log.d(TAG, "Found fallback in canonical dir: " + fallbackFile.getAbsolutePath());
                    return "file://" + fallbackFile.getAbsolutePath();
                }

                File downloadFallback = new File(GameLibraryPaths.MEDIA_DOWNLOAD_DIR + "/" + canonicalConsole, relative);
                if (downloadFallback.exists()) {
                    Log.d(TAG, "Found fallback in canonical downloaded dir: " + downloadFallback.getAbsolutePath());
                    return "file://" + downloadFallback.getAbsolutePath();
                }
            }
        }

        Log.w(TAG, "Fallback image missing for console=" + console + " (canonical=" + canonicalConsole + "), type=" + type + ". Using Android gallery icon.");
        return "android.resource://android/drawable/ic_menu_gallery";
    }


    private static String sanitizeConsole(String console) {
        if (console == null) {
            return "nes";
        }
        return console.toLowerCase(Locale.US);
    }

    private static String sanitizeBaseName(String baseName) {
        if (TextUtils.isEmpty(baseName)) {
            return "game";
        }
        return baseName;
    }

    private enum ArtworkType {
        BOXART,
        SCREENSHOT
    }
}