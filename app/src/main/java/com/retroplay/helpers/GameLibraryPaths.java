package com.retroplay.helpers;

/**
 * Classe utilitaire centralisée pour tous les chemins GameLibrary-Data.
 * Évite la duplication et facilite la maintenance.
 */
public final class GameLibraryPaths {

    private GameLibraryPaths() {
        // Utility class - no instantiation
    }

    /**
     * Chemin de base pour GameLibrary-Data
     */
    public static final String BASE_DIR = "/storage/emulated/0/GameLibrary-Data";

    /**
     * Chemin pour les ROMs (GameLibrary-Data/roms/)
     */
    public static final String ROMS_DIR = BASE_DIR + "/roms";

    /**
     * Chemin pour les sauvegardes (GameLibrary-Data/saves/)
     */
    public static final String SAVES_DIR = BASE_DIR + "/saves";

    /**
     * Chemin pour les save states (GameLibrary-Data/states/)
     */
    public static final String STATES_DIR = BASE_DIR + "/states";

    /**
     * Chemin pour les données EmulatorJS (GameLibrary-Data/data/)
     */
    public static final String DATA_DIR = BASE_DIR + "/data";

    /**
     * Chemin pour les fichiers système (GameLibrary-Data/system/)
     * Les BIOS sont directement dans ce répertoire
     */
    public static final String SYSTEM_DIR = BASE_DIR + "/system";

    /**
     * Chemin pour les BIOS (GameLibrary-Data/system/)
     * Les BIOS sont directement dans system/, pas dans un sous-dossier bios/
     */
    public static final String BIOS_DIR = SYSTEM_DIR;

    /**
     * Chemin pour les cores (GameLibrary-Data/data/cores/)
     */
    public static final String CORES_DIR = DATA_DIR + "/cores";

    /**
     * Chemin pour les cheats (GameLibrary-Data/cheats/)
     */
    public static final String CHEATS_DIR = BASE_DIR + "/cheats";

    /**
     * Chemin pour les cheats ZIP (GameLibrary-Data/cheats.zip)
     */
    public static final String CHEATS_ZIP = BASE_DIR + "/cheats.zip";

    /**
     * Chemin pour les médias téléchargés (GameLibrary-Data/media_download/)
     */
    public static final String MEDIA_DOWNLOAD_DIR = BASE_DIR + "/media_download";

    /**
     * Chemin pour les configs (GameLibrary-Data/config/)
     */
    public static final String CONFIG_DIR = BASE_DIR + "/config";

    /**
     * Obtenir le chemin du répertoire ROMs pour une console
     * 
     * @param consoleId ID de la console (ex: "nes", "fbneo/sega")
     * @return Chemin complet vers le répertoire ROMs de la console
     */
    public static String getRomsDirForConsole(String consoleId) {
        return ROMS_DIR + "/" + consoleId;
    }

    /**
     * Obtenir le chemin du gamelist.json pour une console
     * 
     * @param consoleId ID de la console
     * @return Chemin complet vers le gamelist.json
     */
    public static String getGamelistPath(String consoleId) {
        return getRomsDirForConsole(consoleId) + "/gamelist.json";
    }

    /**
     * Obtenir le chemin du console.json pour une console
     * 
     * @param consoleId ID de la console
     * @return Chemin complet vers le console.json (à la racine, pas dans roms/)
     */
    public static String getConsoleConfigPath(String consoleId) {
        return getRomsDirForConsole(consoleId) + "/console.json";
    }

    /**
     * Obtenir le chemin du répertoire de sauvegardes pour une console
     * 
     * @param consoleId ID de la console
     * @return Chemin complet vers le répertoire de sauvegardes
     */
    public static String getSavesDirForConsole(String consoleId) {
        return SAVES_DIR + "/" + consoleId;
    }

    /**
     * Obtenir le chemin du répertoire de sauvegardes pour un jeu
     * 
     * @param consoleId ID de la console
     * @param gameName  Nom du jeu
     * @return Chemin complet vers le répertoire de sauvegardes du jeu
     */
    public static String getSavesDirForGame(String consoleId, String gameName) {
        return getSavesDirForConsole(consoleId) + "/" + gameName;
    }

    /**
     * Obtenir le chemin du répertoire media pour une console
     * 
     * @param consoleId ID de la console
     * @return Chemin complet vers le répertoire media
     */
    public static String getMediaDirForConsole(String consoleId) {
        return getRomsDirForConsole(consoleId) + "/media";
    }

    /**
     * Obtenir le chemin du répertoire box2d pour une console
     * 
     * @param consoleId ID de la console
     * @return Chemin complet vers le répertoire box2d
     */
    public static String getBox2dDirForConsole(String consoleId) {
        return getMediaDirForConsole(consoleId) + "/box2d";
    }

    /**
     * Obtenir le chemin du répertoire screenshots pour une console
     * 
     * @param consoleId ID de la console
     * @return Chemin complet vers le répertoire screenshots
     */
    public static String getScreenshotsDirForConsole(String consoleId) {
        return getMediaDirForConsole(consoleId) + "/screenshot";
    }
}
