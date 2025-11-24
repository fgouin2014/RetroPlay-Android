package com.retroplay;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * ConsoleNameMapper - Système centralisé de mapping des noms alternatifs de consoles
 * 
 * Normalise tous les noms alternatifs (NES/FC, SNES/SFC, Genesis/Mega Drive, etc.)
 * vers un ID canonique unique pour chaque console.
 * 
 * Suit "Nos Rules": Mapping complet basé sur les conventions RetroArch/Libretro.
 */
public class ConsoleNameMapper {
    private static final String TAG = "ConsoleNameMapper";
    
    // Map: nom alternatif -> ID canonique
    private static final Map<String, String> ALIAS_TO_CANONICAL = new HashMap<>();
    
    // Map: ID canonique -> nom complet
    private static final Map<String, String> CANONICAL_TO_FULL_NAME = new HashMap<>();
    
    static {
        initializeMappings();
    }
    
    /**
     * Initialise tous les mappings de noms alternatifs
     */
    private static void initializeMappings() {
        // === NINTENDO ===
        // NES / Famicom
        addMapping("nes", "nes", "Nintendo Entertainment System");
        addMapping("famicom", "nes", "Nintendo Entertainment System");
        addMapping("fc", "nes", "Nintendo Entertainment System");
        addMapping("nintendo entertainment system", "nes", "Nintendo Entertainment System");
        
        // SNES / Super Famicom
        addMapping("snes", "snes", "Super Nintendo Entertainment System");
        addMapping("sfc", "snes", "Super Nintendo Entertainment System");
        addMapping("superfamicom", "snes", "Super Nintendo Entertainment System");
        addMapping("super famicom", "snes", "Super Nintendo Entertainment System");
        addMapping("super nintendo", "snes", "Super Nintendo Entertainment System");
        
        // N64
        addMapping("n64", "n64", "Nintendo 64");
        addMapping("nintendo64", "n64", "Nintendo 64");
        addMapping("nintendo 64", "n64", "Nintendo 64");
        
        // Game Boy
        addMapping("gb", "gb", "Game Boy");
        addMapping("gameboy", "gb", "Game Boy");
        addMapping("game boy", "gb", "Game Boy");
        
        // Game Boy Color
        addMapping("gbc", "gbc", "Game Boy Color");
        addMapping("gameboycolor", "gbc", "Game Boy Color");
        addMapping("game boy color", "gbc", "Game Boy Color");
        
        // Game Boy Advance
        addMapping("gba", "gba", "Game Boy Advance");
        addMapping("gameboyadvance", "gba", "Game Boy Advance");
        addMapping("game boy advance", "gba", "Game Boy Advance");
        
        // Nintendo DS
        addMapping("nds", "nds", "Nintendo DS");
        addMapping("ds", "nds", "Nintendo DS");
        addMapping("nintendo ds", "nds", "Nintendo DS");
        
        // === SEGA ===
        // Genesis / Mega Drive
        addMapping("genesis", "genesis", "Sega Genesis / Mega Drive");
        addMapping("megadrive", "genesis", "Sega Genesis / Mega Drive");
        addMapping("md", "genesis", "Sega Genesis / Mega Drive");
        addMapping("sega genesis", "genesis", "Sega Genesis / Mega Drive");
        addMapping("sega megadrive", "genesis", "Sega Genesis / Mega Drive");
        addMapping("mega drive", "genesis", "Sega Genesis / Mega Drive");
        
        // Master System
        addMapping("mastersystem", "mastersystem", "Sega Master System");
        addMapping("sms", "mastersystem", "Sega Master System");
        addMapping("sega master system", "mastersystem", "Sega Master System");
        
        // Game Gear
        addMapping("gamegear", "gamegear", "Sega Game Gear");
        addMapping("gg", "gamegear", "Sega Game Gear");
        addMapping("sega game gear", "gamegear", "Sega Game Gear");
        
        // 32X
        addMapping("32x", "32x", "Sega 32X");
        addMapping("sega32x", "32x", "Sega 32X");
        addMapping("sega 32x", "32x", "Sega 32X");
        
        // Sega CD / Mega CD
        addMapping("segacd", "segacd", "Sega CD / Mega CD");
        addMapping("megacd", "segacd", "Sega CD / Mega CD");
        addMapping("sega cd", "segacd", "Sega CD / Mega CD");
        addMapping("mega cd", "segacd", "Sega CD / Mega CD");
        
        // Saturn
        addMapping("saturn", "saturn", "Sega Saturn");
        addMapping("sega saturn", "saturn", "Sega Saturn");
        
        // Dreamcast
        addMapping("dreamcast", "dreamcast", "Sega Dreamcast");
        addMapping("dc", "dreamcast", "Sega Dreamcast");
        addMapping("sega dreamcast", "dreamcast", "Sega Dreamcast");
        
        // === SONY ===
        // PlayStation
        addMapping("ps1", "psx", "PlayStation 1");
        addMapping("psx", "psx", "PlayStation 1");
        addMapping("playstation", "psx", "PlayStation 1");
        addMapping("playstation1", "psx", "PlayStation 1");
        addMapping("ps", "psx", "PlayStation 1");
        
        // PSP
        addMapping("psp", "psp", "PlayStation Portable");
        addMapping("playstation portable", "psp", "PlayStation Portable");
        
        // === ATARI ===
        // Atari 2600
        addMapping("atari2600", "atari2600", "Atari 2600");
        addMapping("2600", "atari2600", "Atari 2600");
        addMapping("atari 2600", "atari2600", "Atari 2600");
        addMapping("a2600", "atari2600", "Atari 2600");
        
        // Atari 5200
        addMapping("atari5200", "atari5200", "Atari 5200");
        addMapping("5200", "atari5200", "Atari 5200");
        addMapping("atari 5200", "atari5200", "Atari 5200");
        addMapping("a5200", "atari5200", "Atari 5200");
        
        // Atari 7800
        addMapping("atari7800", "atari7800", "Atari 7800");
        addMapping("7800", "atari7800", "Atari 7800");
        addMapping("atari 7800", "atari7800", "Atari 7800");
        addMapping("a7800", "atari7800", "Atari 7800");
        
        // Atari Lynx
        addMapping("lynx", "lynx", "Atari Lynx");
        addMapping("atarilynx", "lynx", "Atari Lynx");
        addMapping("atari lynx", "lynx", "Atari Lynx");
        
        // === AUTRES ===
        // Neo Geo Pocket
        addMapping("ngp", "ngp", "Neo Geo Pocket");
        addMapping("ngc", "ngp", "Neo Geo Pocket");
        addMapping("neogeopocket", "ngp", "Neo Geo Pocket");
        addMapping("neo geo pocket", "ngp", "Neo Geo Pocket");
        
        // WonderSwan
        addMapping("wonderswan", "wonderswancolor", "WonderSwan Color");
        addMapping("ws", "wonderswancolor", "WonderSwan Color");
        addMapping("wsc", "wonderswancolor", "WonderSwan Color");
        addMapping("wonderswancolor", "wonderswancolor", "WonderSwan Color");
        
        // PC Engine / TurboGrafx-16
        addMapping("pce", "pce", "PC Engine / TurboGrafx-16");
        addMapping("turbografx", "pce", "PC Engine / TurboGrafx-16");
        addMapping("pcengine", "pce", "PC Engine / TurboGrafx-16");
        addMapping("turbografx-16", "pce", "PC Engine / TurboGrafx-16");
        addMapping("turbografx16", "pce", "PC Engine / TurboGrafx-16");
        
        // Commodore 64
        addMapping("c64", "c64", "Commodore 64");
        addMapping("commodore64", "c64", "Commodore 64");
        addMapping("commodore 64", "c64", "Commodore 64");
        
        // Commodore Amiga
        addMapping("amiga", "amiga", "Commodore Amiga");
        addMapping("commodore amiga", "amiga", "Commodore Amiga");
        
        // DOS
        addMapping("dos", "dos", "DOS");
        
        // ZX Spectrum
        addMapping("zxspectrum", "zxspectrum", "ZX Spectrum");
        addMapping("spectrum", "zxspectrum", "ZX Spectrum");
        addMapping("zx spectrum", "zxspectrum", "ZX Spectrum");
        addMapping("sinclair", "zxspectrum", "ZX Spectrum");
        
        // Arcade / MAME
        // Note: arcade et mame sont gardés séparés car ils ont des configurations différentes
        // (arcade = FBNeo par défaut, mame = MAME2010 par défaut)
        addMapping("arcade", "arcade", "Arcade");
        addMapping("mame", "mame", "Arcade (MAME)");
        addMapping("fbneo", "fbneo", "FBNeo");
        addMapping("neogeo", "fbneo", "FBNeo");
        
        Log.i(TAG, "Initialized " + ALIAS_TO_CANONICAL.size() + " console name mappings");
    }
    
    /**
     * Ajoute un mapping nom alternatif -> ID canonique
     */
    private static void addMapping(String alias, String canonical, String fullName) {
        ALIAS_TO_CANONICAL.put(alias.toLowerCase(), canonical);
        // Stocker le nom complet seulement pour l'ID canonique
        if (!CANONICAL_TO_FULL_NAME.containsKey(canonical)) {
            CANONICAL_TO_FULL_NAME.put(canonical, fullName);
        }
    }
    
    /**
     * Normalise un nom de console vers son ID canonique
     * 
     * @param consoleName Nom de console (peut être un alias)
     * @return ID canonique ou le nom original en lowercase si non trouvé
     */
    public static String normalizeToCanonical(String consoleName) {
        if (consoleName == null || consoleName.isEmpty()) {
            return consoleName;
        }
        
        String normalized = consoleName.toLowerCase().trim();
        String canonical = ALIAS_TO_CANONICAL.get(normalized);
        
        if (canonical != null) {
            Log.d(TAG, "Normalized '" + consoleName + "' -> '" + canonical + "'");
            return canonical;
        }
        
        // Si non trouvé, retourner le nom en lowercase (peut être déjà canonique)
        Log.d(TAG, "No mapping found for '" + consoleName + "', using as-is");
        return normalized;
    }
    
    /**
     * Obtient le nom complet d'une console depuis son ID canonique
     * 
     * @param canonicalId ID canonique de la console
     * @return Nom complet ou null si non trouvé
     */
    public static String getFullName(String canonicalId) {
        if (canonicalId == null) {
            return null;
        }
        
        String fullName = CANONICAL_TO_FULL_NAME.get(canonicalId.toLowerCase());
        if (fullName != null) {
            return fullName;
        }
        
        // Fallback: capitaliser le nom
        return capitalize(canonicalId);
    }
    
    /**
     * Vérifie si un nom de console est reconnu (a un mapping)
     * 
     * @param consoleName Nom de console à vérifier
     * @return true si reconnu
     */
    public static boolean isRecognized(String consoleName) {
        if (consoleName == null || consoleName.isEmpty()) {
            return false;
        }
        
        String normalized = consoleName.toLowerCase().trim();
        return ALIAS_TO_CANONICAL.containsKey(normalized);
    }
    
    /**
     * Obtient tous les alias pour un ID canonique
     * 
     * @param canonicalId ID canonique
     * @return Liste des alias (incluant l'ID canonique lui-même)
     */
    public static java.util.List<String> getAliases(String canonicalId) {
        java.util.List<String> aliases = new java.util.ArrayList<>();
        
        if (canonicalId == null) {
            return aliases;
        }
        
        String normalized = canonicalId.toLowerCase();
        aliases.add(normalized); // Ajouter l'ID canonique lui-même
        
        // Chercher tous les alias qui pointent vers cet ID canonique
        for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
            if (entry.getValue().equals(normalized)) {
                aliases.add(entry.getKey());
            }
        }
        
        return aliases;
    }
    
    /**
     * Capitalise la première lettre d'une chaîne
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

