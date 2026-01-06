package com.retroplay.helpers

/**
 * Helper class for converting core file names to display names.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
object CoreDisplayHelper {
    
    /**
     * Convertit le nom technique du core en nom d'affichage lisible
     * 
     * @param coreFileName Le nom du fichier core (ex: "fbneo_libretro_android.so")
     * @return Le nom d'affichage lisible (ex: "FBNeo")
     */
    fun getCoreDisplayName(coreFileName: String): String {
        val coreName = coreFileName.replace("_libretro_android.so", "")
        return when (coreName.lowercase()) {
            "fbneo" -> "FBNeo"
            "mame2003_plus" -> "MAME 2003 Plus"
            "mame2003" -> "MAME 2003"
            "mame2010" -> "MAME 2010"
            "fceumm" -> "FCEUmm"
            "mesen" -> "Mesen"
            "snes9x" -> "Snes9x"
            "parallel_n64" -> "ParaLLEl N64"
            "mupen64plus_next" -> "Mupen64Plus Next"
            "gambatte" -> "Gambatte"
            "libmgba", "mgba" -> "mGBA"
            "pcsx_rearmed" -> "PCSX ReARMed"
            "ppsspp" -> "PPSSPP"
            "genesis_plus_gx" -> "Genesis Plus GX"
            "picodrive" -> "PicoDrive"
            else -> coreName.uppercase().replace("_", " ")
        }
    }
}

