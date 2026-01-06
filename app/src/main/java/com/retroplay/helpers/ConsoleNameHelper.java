package com.retroplay.helpers;

import com.retroplay.ConsoleNameMapper;

/**
 * Helper class for console name operations.
 * Extracted from WebServer.java to improve modularity and testability.
 */
public class ConsoleNameHelper {

    /**
     * Obtient le nom complet d'une console à partir de son ID
     * 
     * @param consoleId L'ID de la console (ex: "nes", "snes", "genesis")
     * @return Le nom complet de la console (ex: "Nintendo Entertainment System")
     */
    public static String getConsoleFullName(String consoleId) {
        // Utiliser ConsoleNameMapper pour obtenir le nom complet
        String fullName = ConsoleNameMapper.getFullName(consoleId);
        if (fullName != null) {
            return fullName;
        }

        // Fallback sur l'ancien système si non trouvé dans le mapper
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom":
            case "fc":
                return "Nintendo Entertainment System";
            case "snes":
            case "sfc":
            case "superfamicom":
                return "Super Nintendo Entertainment System";
            case "n64":
                return "Nintendo 64";
            case "gb":
                return "Game Boy";
            case "gbc":
                return "Game Boy Color";
            case "gba":
                return "Game Boy Advance";
            case "nds":
            case "ds":
                return "Nintendo DS";

            // Sega
            case "genesis":
            case "megadrive":
            case "md":
            case "mega drive":
                return "Sega Genesis / Mega Drive";
            case "mastersystem":
            case "sms":
                return "Sega Master System";
            case "gamegear":
            case "gg":
                return "Sega Game Gear";
            case "32x":
            case "sega32x":
                return "Sega 32X";
            case "segacd":
            case "megacd":
                return "Sega CD / Mega CD";
            case "saturn":
                return "Sega Saturn";

            // Sony
            case "ps1":
            case "psx":
            case "playstation":
                return "PlayStation 1";
            case "psp":
                return "PlayStation Portable";

            // Sega - Autres

            // Atari
            case "atari2600":
            case "2600":
                return "Atari 2600";
            case "atari5200":
            case "5200":
                return "Atari 5200";
            case "atari7800":
            case "7800":
                return "Atari 7800";
            case "lynx":
                return "Atari Lynx";
            case "jaguar":
                return "Atari Jaguar";

            // Autres
            case "3do":
                return "3DO";
            case "arcade":
            case "mame":
                return "Arcade (MAME)";
            case "neogeo":
            case "ngp":
                return "Neo Geo Pocket";
            case "wonderswan":
            case "ws":
            case "wsc":
                return "WonderSwan";
            case "pcengine":
            case "turbografx":
            case "pce":
                return "PC Engine / TurboGrafx-16";
            case "virtualboy":
            case "vb":
                return "Virtual Boy";
            case "colecovision":
            case "coleco":
                return "ColecoVision";
            case "dos":
                return "DOS";
            case "amiga":
                return "Commodore Amiga";
            case "c64":
            case "commodore64":
                return "Commodore 64";

            default:
                return consoleId.toUpperCase();
        }
    }

    /**
     * Obtient le core par défaut pour une console
     * 
     * @param consoleId L'ID de la console
     * @return Le nom du core par défaut (ex: "fceumm", "snes9x")
     */
    public static String getDefaultCore(String consoleId) {
        // Normaliser avec ConsoleNameMapper
        String canonicalId = ConsoleNameMapper.normalizeToCanonical(consoleId);

        switch (canonicalId) {
            // Nintendo
            case "nes":
                return "fceumm";
            case "snes":
                return "snes9x";
            case "n64":
                return "parallel_n64";
            case "gb":
                return "gambatte";
            case "gbc":
                return "gambatte";
            case "gba":
                return "mgba";
            case "nds":
            case "ds":
                return "melonds";

            // Sega
            case "genesis":
                return "genesis_plus_gx";
            case "mastersystem":
                return "genesis_plus_gx";
            case "gamegear":
                return "genesis_plus_gx";
            case "32x":
                return "picodrive";
            case "segacd":
                return "genesis_plus_gx";
            case "saturn":
                return "yabause";

            // Sony
            case "psx":
                return "pcsx_rearmed";
            case "psp":
                return "ppsspp";

            // Atari
            case "atari2600":
                return "stella2014";
            case "atari5200":
            case "5200":
                return "a5200";
            case "atari7800":
            case "7800":
                return "prosystem";
            case "lynx":
                return "handy";
            case "jaguar":
                return "virtualjaguar";

            // Autres
            case "3do":
                return "opera";

            // Arcade
            case "arcade":
                return "fbneo";
            case "mame":
                return "mame2010";
            case "fbneo":
                return "fbneo";
            case "fbneo/cps1":
            case "cps1":
                return "fbalpha2012_cps1";
            case "fbneo/cps2":
            case "cps2":
                return "fbalpha2012_cps2";
            case "fbneo/cps3":
            case "fbneo/cpiii":
            case "cps3":
                return "fbneo";
            case "fbneo/sega":
                return "fbneo";
            case "fbneo/taito":
                return "fbneo";

            case "neogeo":
                return "fbneo";
            case "ngp":
                return "mednafen_ngp";
            case "wonderswan":
            case "ws":
            case "wsc":
                return "mednafen_wswan";
            case "pcengine":
            case "turbografx":
            case "pce":
                return "mednafen_pce";
            case "virtualboy":
            case "vb":
                return "beetle_vb";
            case "colecovision":
            case "coleco":
                return "gearcoleco";
            case "dos":
                return "dosbox_pure";
            case "amiga":
                return "puae";
            case "c64":
            case "commodore64":
                return "vice_x64";

            default:
                return "auto";
        }
    }

    /**
     * Obtient la couleur associée à une console (pour l'affichage UI)
     * 
     * @param consoleId L'ID de la console
     * @return Le code couleur hexadécimal (ex: "#E30B5C")
     */
    public static String getConsoleColor(String consoleId) {
        switch (consoleId.toLowerCase()) {
            // Nintendo
            case "nes":
            case "famicom":
                return "#E30B5C";
            case "snes":
            case "sfc":
                return "#8B5CF6";
            case "n64":
                return "#3B82F6";
            case "gb":
                return "#6B7280";
            case "gbc":
                return "#F59E0B";
            case "gba":
                return "#10B981";
            case "nds":
            case "ds":
                return "#059669";

            // Sega
            case "genesis":
            case "megadrive":
            case "md":
                return "#0066CC";
            case "mastersystem":
            case "sms":
                return "#DC2626";
            case "gamegear":
            case "gg":
                return "#7C3AED";
            case "32x":
                return "#6366F1";
            case "segacd":
            case "megacd":
                return "#2563EB";
            case "saturn":
                return "#1E40AF";

            // Sony
            case "ps1":
            case "psx":
            case "playstation":
                return "#6366F1";
            case "psp":
                return "#4F46E5";

            // Atari
            case "atari2600":
            case "2600":
                return "#DC2626";
            case "atari5200":
            case "5200":
                return "#EF4444";
            case "atari7800":
            case "7800":
                return "#F87171";
            case "lynx":
                return "#FB923C";
            case "jaguar":
                return "#F59E0B";

            // Autres
            case "3do":
                return "#8B5CF6";
            case "arcade":
            case "mame":
                return "#EC4899";
            case "neogeo":
            case "ngp":
                return "#F43F5E";
            case "wonderswan":
            case "ws":
            case "wsc":
                return "#A855F7";
            case "pcengine":
            case "turbografx":
            case "pce":
                return "#F97316";
            case "virtualboy":
            case "vb":
                return "#DC2626";
            case "colecovision":
            case "coleco":
                return "#0891B2";
            case "dos":
                return "#64748B";
            case "amiga":
                return "#DC2626";
            case "c64":
            case "commodore64":
                return "#7C2D12";

            default:
                return "#FF3333";
        }
    }
}
