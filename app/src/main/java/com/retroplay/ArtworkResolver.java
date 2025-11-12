package com.retroplay;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * Résout les artworks (boxart, screenshots) en respectant les dossiers GameLibrary (media/)
 * ainsi qu'un répertoire de téléchargement dédié (media_download).
 */
public final class ArtworkResolver {
    private static final String TAG = "ArtworkResolver";

    private static final String GAME_LIBRARY_BASE_DIR = "/storage/emulated/0/GameLibrary-Data";
    private static final String GAME_LIBRARY_HTTP_BASE = "http://localhost:7777/gamedata/";
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
            return fallbackForConsole(null, type);
        }

        String console = sanitizeConsole(game.getConsole());
        String baseName = sanitizeBaseName(game.getBaseName());

        // 1) GameLibrary-Data/<console>/media/
        String direct = resolveFromConsoleMedia(console, baseName, type);
        if (!TextUtils.isEmpty(direct)) {
            return direct;
        }

        // 2) GameLibrary-Data/media_download/<console>/media/
        String downloaded = resolveFromDownloadedMedia(console, baseName, type);
        if (!TextUtils.isEmpty(downloaded)) {
            return downloaded;
        }

        // 3) fallback.png dans le dossier du jeu (GameLibrary-Data/<console>/media/)
        return fallbackForConsole(console, type);
    }

    private static String resolveFromConsoleMedia(String console, String baseName, ArtworkType type) {
        String[] candidates = buildMediaCandidates(baseName, type, false);
        for (String relative : candidates) {
            File mediaFile = new File(GAME_LIBRARY_BASE_DIR, console + "/" + relative);
            if (mediaFile.exists()) {
                return buildGameLibraryHttp(console, relative);
            }
        }
        return null;
    }

    private static String resolveFromDownloadedMedia(String console, String baseName, ArtworkType type) {
        String[] candidates = buildMediaCandidates(baseName, type, true);
        String prefix = DOWNLOAD_MEDIA_DIR + "/" + console;
        for (String relative : candidates) {
            File mediaFile = new File(GAME_LIBRARY_BASE_DIR, prefix + "/" + relative);
            if (mediaFile.exists()) {
                return buildGameLibraryHttp(prefix, relative);
            }
        }
        return null;
    }

    private static String[] buildMediaCandidates(String baseName, ArtworkType type, boolean isDownload) {
        if (type == ArtworkType.BOXART) {
            return new String[]{ "media/box2d/" + baseName + ".png" };
        }
        if (isDownload) {
            return new String[]{
                    "media/screenshots/" + baseName + ".png",
                    "media/screenshot/" + baseName + ".png",
                    "media/screenshott/" + baseName + ".png"
            };
        }
        return new String[]{
                "media/screenshots/" + baseName + ".png",
                "media/screenshot/" + baseName + ".png",
                "media/screenshott/" + baseName + ".png"
        };
    }

    private static String fallbackForConsole(String console, ArtworkType type) {
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

        for (String relative : candidates) {
            File fallbackFile = new File(GAME_LIBRARY_BASE_DIR, console + "/" + relative);
            if (fallbackFile.exists()) {
                return buildGameLibraryHttp(console, relative);
            }

            File downloadFallback = new File(GAME_LIBRARY_BASE_DIR,
                    DOWNLOAD_MEDIA_DIR + "/" + console + "/" + relative);
            if (downloadFallback.exists()) {
                return buildGameLibraryHttp(DOWNLOAD_MEDIA_DIR + "/" + console, relative);
            }
        }

        Log.w(TAG, "Fallback image missing for console=" + console + ", type=" + type + ". Using Android gallery icon.");
        return "android.resource://android/drawable/ic_menu_gallery";
    }

    private static String buildGameLibraryHttp(String pathPrefix, String relativePath) {
        String encodedPrefix = Uri.encode(pathPrefix, "/");
        int lastSlash = relativePath.lastIndexOf('/');
        String directory = lastSlash >= 0 ? relativePath.substring(0, lastSlash + 1) : "";
        String fileName = lastSlash >= 0 ? relativePath.substring(lastSlash + 1) : relativePath;
        String encodedFile = Uri.encode(fileName);
        return GAME_LIBRARY_HTTP_BASE + encodedPrefix + "/" + directory + encodedFile;
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
