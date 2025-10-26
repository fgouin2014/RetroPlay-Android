package com.retroplay;

import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension de GameListActivity pour le scanner automatique de ROMs
 * Méthodes helper pour consoles custom sans gamelist.json
 */
public class RomScanner {
    
    private static final String TAG = "RomScanner";
    
    /**
     * Scanner automatiquement les ROMs dans un répertoire (console custom sans gamelist.json)
     */
    public static List<Game> scanRomsInDirectory(String consoleName) {
        String consolePath = "/storage/emulated/0/GameLibrary-Data/" + consoleName;
        File consoleDir = new File(consolePath);
        
        if (!consoleDir.exists() || !consoleDir.isDirectory()) {
            Log.e(TAG, "Console directory not found: " + consolePath);
            return new ArrayList<>();
        }
        
        // Scanner les fichiers ROM
        File[] files = consoleDir.listFiles();
        List<Game> scannedGames = new ArrayList<>();
        
        if (files != null) {
            int gameId = 1;
            for (File file : files) {
                if (file.isFile() && isRomFile(file.getName())) {
                    Game game = generateGameFromFile(file, consoleName, gameId++);
                    if (game != null) {
                        scannedGames.add(game);
                    }
                }
            }
        }
        
        // Trier par nom
        java.util.Collections.sort(scannedGames, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        
        Log.i(TAG, "Scanned " + scannedGames.size() + " ROM files in " + consoleName);
        
        return scannedGames;
    }
    
    /**
     * Vérifie si un fichier est un fichier ROM
     */
    public static boolean isRomFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        String[] romExtensions = {
            // Nintendo
            ".nes", ".fds", ".unf",                           // NES/Famicom
            ".smc", ".sfc", ".fig",                           // SNES
            ".n64", ".z64", ".v64",                           // N64
            ".gb", ".gbc",                                    // Game Boy / Color
            ".gba", ".agb",                                   // Game Boy Advance
            ".nds", ".dsi",                                   // Nintendo DS
            ".3ds", ".3dsx", ".cia",                          // Nintendo 3DS
            
            // Sega
            ".md", ".gen", ".smd", ".32x",                    // Genesis/Mega Drive
            ".gg",                                            // Game Gear
            ".sms",                                           // Master System
            ".cdi", ".gdi",                                   // Dreamcast
            ".sat",                                           // Saturn
            
            // Sony
            ".iso", ".cue", ".bin", ".img", ".mdf",           // PS1/PS2/etc
            ".pbp", ".cso",                                   // PSP
            
            // Atari
            ".a26", ".a52", ".a78",                           // Atari 2600/5200/7800
            ".st", ".stx",                                    // Atari ST
            ".xex", ".atr", ".bin",                           // Atari 8-bit
            
            // Other
            ".pce", ".sgx",                                   // PC Engine / TurboGrafx-16
            ".ngp", ".ngc",                                   // Neo Geo Pocket
            ".ws", ".wsc",                                    // WonderSwan
            ".lnx",                                           // Atari Lynx
            ".vec",                                           // Vectrex
            ".int",                                           // Intellivision
            ".col",                                           // ColecoVision
            ".min",                                           // Pokemon Mini
            ".vb",                                            // Virtual Boy
            ".d64", ".t64", ".prg",                          // Commodore 64
            ".dsk", ".adf",                                   // Amiga / Amstrad
            
            // Archives
            ".zip", ".7z", ".rar", ".gz",                     // Compressed files
            ".rom"                                            // Generic ROM
        };
        
        for (String ext : romExtensions) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Génère un objet Game à partir d'un fichier ROM
     */
    private static Game generateGameFromFile(File file, String consoleName, int gameId) {
        String fileName = file.getName();
        String baseName = getBaseNameFromFileName(fileName);
        
        // Créer un Game avec les infos basiques
        Game game = new Game(
            String.valueOf(gameId),
            baseName, // Nom du jeu = nom du fichier sans extension
            "./" + fileName, // Chemin relatif
            "Custom ROM - No description available", // Description par défaut
            "Unknown", // Date de sortie
            "Custom", // Genre
            "1-2" // Joueurs par défaut
        );
        
        game.consoleId = consoleName;
        game.initializePaths(null); // Initialiser les chemins d'images
        
        return game;
    }
    
    /**
     * Extrait le nom de base d'un nom de fichier (sans extension)
     */
    private static String getBaseNameFromFileName(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
}

