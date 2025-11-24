package com.retroplay;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utilitaire pour télécharger et stocker les artworks Libretro dans GameLibrary-Data.
 */
public final class ArtworkDownloadHelper {
    private static final String TAG = "ArtworkDownload";

    public static final String GAME_LIBRARY_BASE_DIR = "/storage/emulated/0/GameLibrary-Data";
    public static final String DOWNLOAD_BASE_DIR = GAME_LIBRARY_BASE_DIR + "/media_download";
    // URL principale pour les thumbnails Libretro
    private static final String LIBRETRO_THUMB_BASE_URL = "https://thumbnail.libretro.com/";
    // URL alternative si la principale ne fonctionne pas
    private static final String LIBRETRO_THUMB_ALT_URL = "https://buildbot.libretro.com/assets/thumbnails/";

    private static final String[] SCREENSHOT_DIRS = {
            "media/screenshots/",
            "media/screenshot/",
            "media/screenshott/"
    };

    private static final Map<String, String> PLAYLIST_MAP = new HashMap<>();

    static {
        // Nintendo
        PLAYLIST_MAP.put("nes", "Nintendo - Nintendo Entertainment System");
        PLAYLIST_MAP.put("famicom", "Nintendo - Nintendo Entertainment System");
        PLAYLIST_MAP.put("snes", "Nintendo - Super Nintendo Entertainment System");
        PLAYLIST_MAP.put("sfc", "Nintendo - Super Nintendo Entertainment System");
        PLAYLIST_MAP.put("n64", "Nintendo - Nintendo 64");
        PLAYLIST_MAP.put("gb", "Nintendo - Game Boy");
        PLAYLIST_MAP.put("gbc", "Nintendo - Game Boy Color");
        PLAYLIST_MAP.put("gba", "Nintendo - Game Boy Advance");
        PLAYLIST_MAP.put("nds", "Nintendo - Nintendo DS");
        PLAYLIST_MAP.put("ds", "Nintendo - Nintendo DS");

        // Sega
        PLAYLIST_MAP.put("genesis", "Sega - Mega Drive - Genesis");
        PLAYLIST_MAP.put("megadrive", "Sega - Mega Drive - Genesis");
        PLAYLIST_MAP.put("md", "Sega - Mega Drive - Genesis");
        PLAYLIST_MAP.put("mastersystem", "Sega - Master System - Mark III");
        PLAYLIST_MAP.put("sms", "Sega - Master System - Mark III");
        PLAYLIST_MAP.put("gamegear", "Sega - Game Gear");
        PLAYLIST_MAP.put("gg", "Sega - Game Gear");
        PLAYLIST_MAP.put("segacd", "Sega - Mega-CD - Sega CD");
        PLAYLIST_MAP.put("megacd", "Sega - Mega-CD - Sega CD");
        PLAYLIST_MAP.put("32x", "Sega - 32X");

        // Sony
        PLAYLIST_MAP.put("psx", "Sony - PlayStation");
        PLAYLIST_MAP.put("ps1", "Sony - PlayStation");
        PLAYLIST_MAP.put("playstation", "Sony - PlayStation");
        PLAYLIST_MAP.put("psp", "Sony - PlayStation Portable");

        // NEC / PC Engine
        PLAYLIST_MAP.put("pcengine", "NEC - PC Engine - TurboGrafx 16");
        PLAYLIST_MAP.put("turbografx", "NEC - PC Engine - TurboGrafx 16");
        PLAYLIST_MAP.put("pce", "NEC - PC Engine - TurboGrafx 16");

        // Atari
        PLAYLIST_MAP.put("atari2600", "Atari - 2600");
        PLAYLIST_MAP.put("2600", "Atari - 2600");
        PLAYLIST_MAP.put("atari5200", "Atari - 5200");
        PLAYLIST_MAP.put("5200", "Atari - 5200");
        PLAYLIST_MAP.put("atari7800", "Atari - 7800");
        PLAYLIST_MAP.put("7800", "Atari - 7800");
        PLAYLIST_MAP.put("lynx", "Atari - Lynx");

        // SNK / Neo Geo
        PLAYLIST_MAP.put("neogeo", "SNK - Neo Geo AES - Neo Geo CD");
        PLAYLIST_MAP.put("ngp", "SNK - Neo Geo Pocket");

        // Divers
        PLAYLIST_MAP.put("amiga", "Commodore - Amiga");
        PLAYLIST_MAP.put("c64", "Commodore - 64");
        PLAYLIST_MAP.put("commodore64", "Commodore - 64");
        PLAYLIST_MAP.put("dos", "DOS");
    }

    private ArtworkDownloadHelper() {
    }

    public static class DownloadResult {
        public boolean boxartDownloaded;
        public boolean screenshotDownloaded;
        public boolean skipped;
        public String error;
        public boolean networkError; // Indique si c'est une erreur réseau (DNS, timeout, etc.)

        public boolean hasAnyDownload() {
            return boxartDownloaded || screenshotDownloaded;
        }
    }

    public static DownloadResult downloadArtwork(String consoleId, String baseName, boolean forceReplace, boolean skipIfExists) throws IOException {
        DownloadResult result = new DownloadResult();

        if (consoleId == null || baseName == null) {
            result.error = "Invalid parameters";
            return result;
        }

        String playlist = getPlaylistName(consoleId);
        if (playlist == null) {
            result.error = "No Libretro playlist mapping for console " + consoleId;
            return result;
        }

        String consoleDir = consoleId;
        String sanitizedBaseName = sanitizeBaseName(baseName);
        String fileName = sanitizedBaseName + ".png";

        // Boxart handling
        try {
            handleArtworkDownload(result,
                    playlist,
                    consoleDir,
                    sanitizedBaseName,
                    "Named_Boxarts",
                    new File(GAME_LIBRARY_BASE_DIR + "/" + consoleDir + "/media/box2d/" + fileName),
                    new File(DOWNLOAD_BASE_DIR + "/" + consoleDir + "/media/box2d/" + fileName),
                    forceReplace,
                    skipIfExists);
        } catch (java.net.UnknownHostException e) {
            result.networkError = true;
            result.error = "Cannot connect to thumbnail server. Please check your internet connection.";
        } catch (java.io.IOException e) {
            if (result.error == null) {
                result.error = "Network error: " + e.getMessage();
            }
        }

        // Screenshot handling
        boolean screenshotExistsInMedia = screenshotExistsInMedia(consoleDir, sanitizedBaseName);
        boolean screenshotExistsInDownload = screenshotExistsInDownload(consoleDir, sanitizedBaseName);
        boolean shouldSkipScreenshot = skipIfExists && (screenshotExistsInMedia || screenshotExistsInDownload);

        if (!shouldSkipScreenshot || forceReplace) {
            try {
                File downloadScreenshot = new File(DOWNLOAD_BASE_DIR + "/" + consoleDir + "/media/screenshots/" + fileName);
                File mediaScreenshot = determineScreenshotTarget(consoleDir, fileName);

                boolean downloaded = fetchArtwork(playlist, sanitizedBaseName, "Named_Snaps", downloadScreenshot, forceReplace);
                if (downloaded) {
                    result.screenshotDownloaded = true;
                    copyToMedia(downloadScreenshot, mediaScreenshot, forceReplace);
                }
            } catch (java.net.UnknownHostException e) {
                result.networkError = true;
                result.error = "Cannot connect to thumbnail server. Please check your internet connection.";
            } catch (java.io.IOException e) {
                if (result.error == null) {
                    result.error = "Network error: " + e.getMessage();
                }
            }
        } else {
            result.skipped = true;
        }

        if (!result.hasAnyDownload() && (skipIfExists || forceReplace)) {
            if (result.error == null) {
                result.skipped = true;
            }
        }

        return result;
    }

    private static void handleArtworkDownload(DownloadResult result,
                                              String playlist,
                                              String consoleDir,
                                              String baseName,
                                              String remoteFolder,
                                              File mediaFile,
                                              File downloadFile,
                                              boolean forceReplace,
                                              boolean skipIfExists) throws IOException {
        String fileName = baseName + ".png";
        boolean mediaExists = mediaFile.exists();
        boolean downloadExists = downloadFile.exists();

        boolean shouldSkip = skipIfExists && (mediaExists || downloadExists);

        if (shouldSkip && !forceReplace) {
            result.skipped = true;
            return;
        }

        boolean downloaded = fetchArtwork(playlist, baseName, remoteFolder, downloadFile, forceReplace);
        if (downloaded) {
            copyToMedia(downloadFile, mediaFile, forceReplace);
            result.boxartDownloaded = true;
        }
    }

    private static boolean fetchArtwork(String playlist,
                                        String baseName,
                                        String remoteFolder,
                                        File destination,
                                        boolean forceReplace) throws IOException {
        String fileName = baseName + ".png";
        String encodedName = encodeFileName(fileName);
        
        // Essayer d'abord avec l'URL principale
        String url = LIBRETRO_THUMB_BASE_URL + playlist + "/" + remoteFolder + "/" + encodedName;
        boolean success = tryDownloadArtwork(url, destination, forceReplace);
        
        // Si échec, essayer l'URL alternative
        if (!success) {
            Log.d(TAG, "Primary URL failed, trying alternative: " + LIBRETRO_THUMB_ALT_URL);
            String altUrl = LIBRETRO_THUMB_ALT_URL + playlist + "/" + remoteFolder + "/" + encodedName;
            success = tryDownloadArtwork(altUrl, destination, forceReplace);
        }
        
        return success;
    }
    
    private static boolean tryDownloadArtwork(String url, File destination, boolean forceReplace) throws IOException {
        if (destination.exists()) {
            if (!forceReplace) {
                return false;
            }
            // Remove existing if replacing
            if (!destination.delete()) {
                Log.w(TAG, "Unable to delete existing file: " + destination.getAbsolutePath());
            }
        }

        destination.getParentFile().mkdirs();

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Artwork not found: " + url + " (" + responseCode + ")");
                return false;
            }

            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            Log.i(TAG, "Downloaded artwork: " + destination.getAbsolutePath());
            return true;
        } catch (java.net.UnknownHostException e) {
            Log.w(TAG, "Cannot resolve host for: " + url + " - " + e.getMessage());
            throw new IOException("Unable to resolve host: " + e.getMessage(), e);
        } catch (java.net.SocketTimeoutException e) {
            Log.w(TAG, "Timeout connecting to: " + url + " - " + e.getMessage());
            throw new IOException("Connection timeout: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            Log.w(TAG, "IO error downloading from: " + url + " - " + e.getMessage());
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void copyToMedia(File source, File target, boolean forceReplace) throws IOException {
        if (target.exists() && !forceReplace) {
            return;
        }

        target.getParentFile().mkdirs();

        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private static boolean screenshotExistsInMedia(String consoleDir, String baseName) {
        for (String dir : SCREENSHOT_DIRS) {
            File file = new File(GAME_LIBRARY_BASE_DIR + "/" + consoleDir + "/" + dir + baseName + ".png");
            if (file.exists()) {
                return true;
            }
        }
        return false;
    }

    private static boolean screenshotExistsInDownload(String consoleDir, String baseName) {
        File file = new File(DOWNLOAD_BASE_DIR + "/" + consoleDir + "/media/screenshots/" + baseName + ".png");
        return file.exists();
    }

    private static File determineScreenshotTarget(String consoleDir, String fileName) {
        for (String dir : SCREENSHOT_DIRS) {
            File folder = new File(GAME_LIBRARY_BASE_DIR + "/" + consoleDir + "/" + dir);
            if (folder.exists() && folder.isDirectory()) {
                return new File(folder, fileName);
            }
        }
        return new File(GAME_LIBRARY_BASE_DIR + "/" + consoleDir + "/media/screenshots/" + fileName);
    }

    private static String encodeFileName(String fileName) throws IOException {
        String encoded = URLEncoder.encode(fileName, "UTF-8");
        // URLEncoder encode spaces as '+', replace by %20 for URLs
        return encoded.replace("+", "%20");
    }

    public static String getPlaylistName(String consoleId) {
        if (consoleId == null) return null;
        String sanitized = consoleId.toLowerCase(Locale.US);
        int slashIndex = sanitized.indexOf('/');
        if (slashIndex >= 0) {
            sanitized = sanitized.substring(0, slashIndex);
        }
        return PLAYLIST_MAP.get(sanitized);
    }

    private static String sanitizeConsole(String consoleId) {
        return consoleId.toLowerCase(Locale.US);
    }

    private static String sanitizeBaseName(String baseName) {
        return baseName.trim();
    }
}
