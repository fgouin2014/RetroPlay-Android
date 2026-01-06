package com.retroplay.usecases;

import android.content.Context;
import android.util.Log;
import com.retroplay.helpers.GameLibraryPaths;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Use Case pour le scan des ROMs et des consoles.
 * Extracted from GameListActivity to improve modularity and testability.
 * 
 * Gère:
 * - Scan des répertoires de consoles
 * - Détection des ROMs (fichiers .zip, .bin, .iso, etc.)
 * - Scan des sous-consoles (ex: fbneo/sega)
 * - Génération des noms d'affichage
 */
public class ScanGamesUseCase {

    private static final String TAG = "ScanGamesUseCase";

    // Extensions de ROMs supportées
    private static final String[] ROM_EXTENSIONS = {
            // Nintendo
            ".nes", ".fds", ".unf", // NES/Famicom
            ".smc", ".sfc", ".fig", // SNES
            ".n64", ".z64", ".v64", // N64
            ".gb", ".gbc", // Game Boy / Color
            ".gba", ".agb", // Game Boy Advance
            ".nds", // Nintendo DS
            // Sega
            ".md", ".smd", ".gen", ".bin", // Genesis/Mega Drive
            ".sms", // Master System
            ".gg", // Game Gear
            ".32x", // 32X
            ".cue", ".chd", // Sega CD
            // Sony
            ".iso", ".img", ".pbp", ".cso", ".PBP", // PSP/PS1
            // Atari
            ".a26", ".a52", ".a78", // Atari 2600/5200/7800
            ".lnx", // Atari Lynx
            ".jag", // Atari Jaguar
            // Autres
            ".zip", ".7z", ".rar", // Archives
            ".rom" // Generic ROM
    };

    // Répertoires système à ignorer
    private static final String[] SYSTEM_DIRECTORIES = {
            "data", "emulatorjs", "vmnes", "playlists",
            "saves", ".cache", "cheats", "media"
    };

    // Sous-répertoires système à ignorer
    private static final String[] SYSTEM_SUBDIRECTORIES = {
            "media", "saves", "data", ".cache",
            "cfg", "hi", "inp", "ctrlr", "diff", "comment",
            "mame2003", "mame2003-plus", "mame2010", "mame2014",
            "fbneo", "fba", "kinst"
    };

    private final Context context;

    public ScanGamesUseCase(Context context) {
        this.context = context;
    }

    /**
     * Résultat du scan de consoles
     */
    public static class ScanResult {
        public final boolean success;
        public final List<ConsoleInfo> consoles;
        public final String errorMessage;

        public ScanResult(boolean success, List<ConsoleInfo> consoles, String errorMessage) {
            this.success = success;
            this.consoles = consoles != null ? consoles : new ArrayList<>();
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Classe pour représenter une console scannée
     */
    public static class ConsoleInfo {
        public final String id;
        public final String name;
        public final String fullName;
        public final String directory;

        public ConsoleInfo(String id, String name, String fullName, String directory) {
            this.id = id;
            this.name = name;
            this.fullName = fullName;
            this.directory = directory;
        }
    }

    /**
     * Scanne les consoles disponibles dans GameLibrary-Data
     * 
     * @return Résultat du scan avec la liste des consoles
     */
    public ScanResult scanConsoles() {
        try {
            Log.i(TAG, "=== DEBUT SCAN CONSOLES ===");
            // Scanner dans /roms/ maintenant que les ROMs ont été déplacées
            File romsDir = new File(GameLibraryPaths.ROMS_DIR);

            if (!romsDir.exists() || !romsDir.isDirectory()) {
                Log.w(TAG, "GameLibrary-Data/roms does not exist. Creating it.");
                romsDir.mkdirs();
            }

            return scanConsolesInDirectory(romsDir);
        } catch (Exception e) {
            Log.e(TAG, "Erreur scan consoles", e);
            return new ScanResult(false, null, "Error scanning consoles: " + e.getMessage());
        }
    }

    private ScanResult scanConsolesInDirectory(File baseDir) {
        try {
            File[] directories = baseDir.listFiles(File::isDirectory);
            if (directories == null) {
                return new ScanResult(true, new ArrayList<>(), null);
            }

            List<ConsoleInfo> scannedConsoles = new ArrayList<>();

            for (File dir : directories) {
                String dirName = dir.getName();

                // Ignorer les répertoires système
                if (isSystemDirectory(dirName)) {
                    continue;
                }

                // Vérifier si gamelist.json existe
                File gamelistFile = new File(dir, "gamelist.json");
                boolean hasGamelist = gamelistFile.exists();

                // Scanner les sous-répertoires (ex: fbneo/sega) AVANT d'ajouter le parent
                File[] subdirectories = dir.listFiles(File::isDirectory);
                boolean hasSubconsoles = false;

                if (subdirectories != null) {
                    for (File subdir : subdirectories) {
                        String subdirName = subdir.getName();

                        // Ignorer les dossiers système
                        if (isSystemSubdirectory(subdirName)) {
                            continue;
                        }

                        File subGamelistFile = new File(subdir, "gamelist.json");
                        boolean subHasGamelist = subGamelistFile.exists();
                        boolean subHasRoms = hasRomFiles(subdir);

                        if (subHasGamelist || subHasRoms) {
                            String subConsolePath = dirName + "/" + subdirName;
                            String subDisplayName = getSubconsoleDisplayName(dirName, subdirName);
                            scannedConsoles.add(
                                    new ConsoleInfo(subConsolePath, subDisplayName, subDisplayName, subConsolePath));
                            Log.i(TAG, "*** AJOUT SOUS-CONSOLE: " + subConsolePath + " ***");
                            hasSubconsoles = true;
                        }
                    }
                }

                // Vérifier si le dossier contient des ROMs (même sans gamelist.json)
                boolean hasRoms = hasRomFiles(dir);

                // Ajouter la console parent si elle a un gamelist.json OU des ROMs
                if (hasGamelist || hasRoms) {
                    String displayName = getDefaultDisplayName(dirName);
                    scannedConsoles.add(new ConsoleInfo(dirName, dirName.toUpperCase(), displayName, dirName));
                    String mode = hasGamelist ? "" : " [AUTO SCAN]";
                    Log.i(TAG, "Console scannée: " + dirName + (hasSubconsoles ? " (avec sous-consoles)" : "") + mode);
                } else if (!hasSubconsoles) {
                    Log.d(TAG, "Dossier ignoré (pas de gamelist ni ROMs ni sous-consoles): " + dirName);
                }
            }

            // Trier alphabétiquement
            scannedConsoles.sort((a, b) -> a.id.compareTo(b.id));

            // Éviter les doublons
            List<ConsoleInfo> uniqueConsoles = removeDuplicates(scannedConsoles);

            Log.i(TAG, "Total consoles scannées (après déduplication): " + uniqueConsoles.size());
            for (ConsoleInfo console : uniqueConsoles) {
                Log.i(TAG, "  Console: " + console.id + " (" + console.directory + ") | Name: " + console.name);
            }

            return new ScanResult(true, uniqueConsoles, null);

        } catch (Exception e) {
            Log.e(TAG, "Erreur scan consoles", e);
            return new ScanResult(false, null, "Error scanning consoles: " + e.getMessage());
        }
    }

    /**
     * Scanne les sous-consoles dans les répertoires existants
     * 
     * @return Liste des sous-consoles trouvées
     */
    public List<ConsoleInfo> scanSubconsoles() {
        try {
            // Scanner dans /roms/ maintenant que les ROMs ont été déplacées
            File romsDir = new File(GameLibraryPaths.ROMS_DIR);
            if (!romsDir.exists() || !romsDir.isDirectory()) {
                // Fallback vers l'ancien chemin
                File gamelibraryDir = new File(GameLibraryPaths.BASE_DIR);
                if (!gamelibraryDir.exists() || !gamelibraryDir.isDirectory()) {
                    return new ArrayList<>();
                }
                return scanSubconsolesInDirectory(gamelibraryDir);
            }

            return scanSubconsolesInDirectory(romsDir);
        } catch (Exception e) {
            Log.e(TAG, "Erreur scan sous-consoles", e);
            return new ArrayList<>();
        }
    }

    private List<ConsoleInfo> scanSubconsolesInDirectory(File baseDir) {
        try {
            File[] directories = baseDir.listFiles(File::isDirectory);
            if (directories == null) {
                return new ArrayList<>();
            }

            List<ConsoleInfo> subconsoles = new ArrayList<>();

            for (File dir : directories) {
                String dirName = dir.getName();

                // Ignorer les répertoires système
                if (isSystemDirectory(dirName)) {
                    continue;
                }

                // Scanner les sous-répertoires
                File[] subdirectories = dir.listFiles(File::isDirectory);
                if (subdirectories != null) {
                    for (File subdir : subdirectories) {
                        String subdirName = subdir.getName();

                        // Ignorer les dossiers système
                        if (isSystemSubdirectory(subdirName)) {
                            continue;
                        }

                        // Vérifier s'il y a des ROMs dans ce sous-dossier
                        if (hasRomFiles(subdir)) {
                            String subConsolePath = dirName + "/" + subdirName;
                            String subDisplayName = dirName.toUpperCase() + " - " + subdirName.toUpperCase();
                            subconsoles.add(
                                    new ConsoleInfo(subConsolePath, subDisplayName, subDisplayName, subConsolePath));
                            Log.i(TAG, "Sous-console trouvée: " + subConsolePath);
                        }
                    }
                }
            }

            return subconsoles;

        } catch (Exception e) {
            Log.e(TAG, "Erreur scan sous-consoles", e);
            return new ArrayList<>();
        }
    }

    /**
     * Vérifie si un répertoire contient des fichiers ROM
     */
    public boolean hasRomFiles(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                for (String ext : ROM_EXTENSIONS) {
                    if (fileName.endsWith(ext)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Vérifie si un répertoire est un répertoire système à ignorer
     */
    private boolean isSystemDirectory(String dirName) {
        for (String sysDir : SYSTEM_DIRECTORIES) {
            if (sysDir.equals(dirName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vérifie si un sous-répertoire est un répertoire système à ignorer
     */
    private boolean isSystemSubdirectory(String subdirName) {
        for (String sysSubdir : SYSTEM_SUBDIRECTORIES) {
            if (sysSubdir.equals(subdirName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Supprime les doublons dans la liste des consoles
     */
    private List<ConsoleInfo> removeDuplicates(List<ConsoleInfo> consoles) {
        List<ConsoleInfo> unique = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenDirs = new HashSet<>();

        for (ConsoleInfo console : consoles) {
            if (!seenIds.contains(console.id) && !seenDirs.contains(console.directory)) {
                unique.add(console);
                seenIds.add(console.id);
                seenDirs.add(console.directory);
            } else {
                Log.d(TAG, "Console dupliquée ignorée: " + console.id + " / " + console.directory);
            }
        }

        return unique;
    }

    /**
     * Obtient le nom d'affichage par défaut pour une console
     */
    public String getDefaultDisplayName(String consoleId) {
        switch (consoleId.toLowerCase()) {
            case "nes":
                return "Nintendo Entertainment System";
            case "snes":
                return "Super Nintendo Entertainment System";
            case "n64":
                return "Nintendo 64";
            case "gba":
                return "Game Boy Advance";
            case "gbc":
                return "Game Boy Color";
            case "gb":
                return "Game Boy";
            case "megadrive":
            case "genesis":
                return "Sega Genesis / Mega Drive";
            case "nds":
                return "Nintendo DS";
            case "ps1":
            case "psx":
                return "PlayStation 1";
            case "psp":
                return "PlayStation Portable";
            case "arcade":
                return "Arcade (MAME/FBNeo)";
            case "mame":
                return "MAME Arcade";
            case "fbneo":
                return "FBNeo Arcade";
            case "atari2600":
                return "Atari 2600";
            case "atari5200":
                return "Atari 5200";
            case "atari7800":
                return "Atari 7800";
            case "atarilynx":
                return "Atari Lynx";
            case "segacd":
                return "Sega CD / Mega CD";
            case "gamegear":
                return "Sega Game Gear";
            case "pce":
                return "PC Engine / TurboGrafx-16";
            default:
                return consoleId.toUpperCase();
        }
    }

    /**
     * Obtient le nom d'affichage pour une sous-console
     */
    public String getSubconsoleDisplayName(String parent, String subconsole) {
        String subLower = subconsole.toLowerCase();
        switch (parent.toLowerCase() + "/" + subLower) {
            // FBNeo sub-systems
            case "fbneo/sega":
                return "Sega";
            case "fbneo/taito":
                return "Taito";
            case "fbneo/cps1":
                return "CPS1 (Capcom)";
            case "fbneo/cps2":
                return "CPS2 (Capcom)";
            case "fbneo/cps3":
                return "CPS3 (Capcom)";
            case "fbneo/cpiii":
                return "CPS3 (Capcom)";
            // Default format
            default:
                return subconsole.toUpperCase();
        }
    }
}
