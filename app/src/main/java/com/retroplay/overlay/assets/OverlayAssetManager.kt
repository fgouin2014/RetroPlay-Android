package com.retroplay.overlay.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.retroplay.overlay.models.OverlayPackageInfo
import com.retroplay.overlay.models.RetroArchOverlayConfig
import com.retroplay.overlay.parser.RetroArchOverlayParser
import com.retroplay.overlay.parser.ImageDimensionsCallback
import java.io.File

/**
 * Gestionnaire des assets d'overlay RetroArch
 * - Installation depuis assets vers storage externe
 * - Chargement de configurations
 * - Gestion d'images PNG
 */
class OverlayAssetManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OverlayAssetManager"
        
        // Répertoire de stockage des overlays sur le device (structure RetroArch officielle)
        const val OVERLAY_DIR = "/storage/emulated/0/RetroPlay-Data/overlays/gamepads"
        
        // Racine des overlays dans les assets (structure identique aux repos RetroArch)
        private const val ASSETS_OVERLAYS_ROOT = "overlays/gamepads"
    }
    
    private val parser = RetroArchOverlayParser()
    
    /**
     * Callback pour rapporter la progression de l'installation
     */
    interface ProgressCallback {
        fun onProgress(current: Int, total: Int, packageName: String)
        fun onComplete(successCount: Int, total: Int)
    }
    
    /**
     * Installer les overlays depuis assets vers storage externe
     * Appelé au premier lancement ou si manquants
     */
    fun installOverlaysIfNeeded(progressCallback: ProgressCallback? = null): Boolean {
        val overlayDir = File(OVERLAY_DIR)
        
        // Si déjà installé et contient des overlays, skip
        if (overlayDir.exists() && overlayDir.listFiles()?.isNotEmpty() == true) {
            Log.i(TAG, "Overlays already installed")
            return true
        }
        
        Log.i(TAG, "Installing overlays for first time...")
        
        // Créer le répertoire
        if (!overlayDir.mkdirs() && !overlayDir.exists()) {
            Log.e(TAG, "Failed to create overlay directory")
            return false
        }
        
        // Lister dynamiquement les packages disponibles dans assets/overlays
        val packages = try {
            context.assets.list(ASSETS_OVERLAYS_ROOT)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list assets overlays", e)
            emptyList()
        }

        // Installer chaque package (copie récursive complète pour préserver la structure officielle)
        var successCount = 0
        packages.forEachIndexed { index, packageName ->
            progressCallback?.onProgress(index + 1, packages.size, packageName)
            if (installOverlayPackage(packageName)) successCount++
        }

        progressCallback?.onComplete(successCount, packages.size)
        Log.i(TAG, "Installed $successCount/${packages.size} overlay packages")
        return successCount > 0
    }
    
    /**
     * Installer un package d'overlay individuel
     * @param packageName Nom du package (ex: "flat-nes")
     * @return true si succès
     */
    private fun installOverlayPackage(packageName: String): Boolean {
        try {
            val targetDir = File(OVERLAY_DIR, packageName)
            if (!targetDir.mkdirs() && !targetDir.exists()) {
                Log.e(TAG, "Failed to create directory for $packageName")
                return false
            }
            
            // Copie récursive complète du dossier assets/overlays/<packageName> vers storage
            fun copyAssetDirRecursively(assetPath: String, dest: File) {
                val list = context.assets.list(assetPath) ?: emptyArray()
                if (list.isEmpty()) {
                    // Fichier
                    context.assets.open(assetPath).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    // Dossier
                    if (!dest.exists()) dest.mkdirs()
                    list.forEach { child ->
                        copyAssetDirRecursively(
                            "$assetPath/$child",
                            File(dest, child)
                        )
                    }
                }
            }

            copyAssetDirRecursively("$ASSETS_OVERLAYS_ROOT/$packageName", targetDir)
            Log.i(TAG, "Successfully installed $packageName (recursive copy)")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing overlay package $packageName", e)
            return false
        }
    }
    
    /**
     * Charger une configuration d'overlay
     * @param overlayName Nom de l'overlay (ex: "flat-nes")
     * @param customCfgName Nom du .cfg custom (ex: "dreamcast.cfg"), null pour auto-detect
     * @return Configuration parsée, ou null si erreur
     */
    fun loadOverlayConfig(overlayName: String, console: String, customCfgName: String? = null): RetroArchOverlayConfig? {
        // Si customCfgName spécifié (custom browsé), l'utiliser directement
        var cfgFile = if (customCfgName != null) {
            File(OVERLAY_DIR, "$overlayName/$customCfgName")
        } else {
            // 1) Essayer <overlay>/<overlay>.cfg (packages avec cfg unique)
            File(OVERLAY_DIR, "$overlayName/$overlayName.cfg")
        }
        
        // 2) Sinon, essayer <overlay>/<console>.cfg (ex: flat/nes.cfg)
        if (!cfgFile.exists() && customCfgName == null) {
            val consoleCfg = mapConsoleToCfg(console)
            val alt = File(OVERLAY_DIR, "$overlayName/$consoleCfg.cfg")
            if (alt.exists()) cfgFile = alt
        }
        
        // 3) Sinon, prendre le PREMIER .cfg trouvé (ex: nes-small/nes-small-ab.cfg)
        if (!cfgFile.exists() && customCfgName == null) {
            val overlayDir = File(OVERLAY_DIR, overlayName)
            val cfgFiles = overlayDir.listFiles { file -> file.extension == "cfg" }
            if (!cfgFiles.isNullOrEmpty()) {
                cfgFile = cfgFiles.first()
                Log.i(TAG, "Using first .cfg found: ${cfgFile.name}")
            }
        }
        
        if (!cfgFile.exists()) {
            Log.e(TAG, "Config file not found for overlay='$overlayName' console='$console' customCfg='$customCfgName' in ${File(OVERLAY_DIR, overlayName).absolutePath}")
            return null
        }
        
        Log.i(TAG, "Loading config: ${cfgFile.absolutePath}")
        
        // Item P1 #12: Conversion Normalized vs Pixel
        // Callback pour obtenir les dimensions de l'image de fond (pour conversion pixel → normalized)
        val imageDimensionsCallback: ImageDimensionsCallback = { imagePath, overlayNameParam ->
            val imageFile = File(OVERLAY_DIR, "${overlayNameParam ?: overlayName}/$imagePath")
            if (imageFile.exists()) {
                try {
                    // Charger l'image pour obtenir ses dimensions sans décoder complètement (optimisé)
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true  // Ne décoder que les métadonnées (dimensions)
                    }
                    BitmapFactory.decodeFile(imageFile.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        Pair(options.outWidth, options.outHeight)
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting dimensions for image: ${imageFile.absolutePath}", e)
                    null
                }
            } else {
                null
            }
        }
        
        val config = parser.parseConfig(cfgFile, overlayName, imageDimensionsCallback)
        
        if (config != null && parser.validateConfig(config)) {
            Log.i(TAG, "Successfully loaded overlay config: $overlayName (cfg: ${cfgFile.name})")
            Log.d(TAG, parser.getConfigStats(config))
            return config
        }
        
        Log.e(TAG, "Invalid or empty config for $overlayName")
        return null
    }
    
    /**
     * Charger une image de bouton
     * @param overlayName Nom de l'overlay
     * @param imagePath Chemin relatif de l'image (ex: "img/A.png")
     * @return Bitmap, ou null si non trouvé
     */
    fun loadButtonImage(overlayName: String, imagePath: String): Bitmap? {
        val imageFile = File(OVERLAY_DIR, "$overlayName/$imagePath")
        
        if (!imageFile.exists()) {
            Log.w(TAG, "Image not found: ${imageFile.absolutePath}")
            return null
        }
        
        return try {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: ${imageFile.absolutePath}", e)
            null
        }
    }
    
    /**
     * Obtenir la liste des overlays installés
     * @return Liste des noms d'overlays
     */
    fun getAvailableOverlays(): List<String> {
        val overlayDir = File(OVERLAY_DIR)
        
        if (!overlayDir.exists()) {
            Log.w(TAG, "Overlay directory does not exist")
            return emptyList()
        }
        
        return overlayDir.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }
    
    /**
     * Obtenir les layouts disponibles pour un overlay
     * @param overlayName Nom de l'overlay
     * @param customCfgName Nom du .cfg custom si applicable
     * @return Liste des noms de layouts (ex: ["landscape-A", "portrait-B"])
     */
    fun getAvailableLayouts(overlayName: String, console: String, customCfgName: String? = null): List<String> {
        val config = loadOverlayConfig(overlayName, console, customCfgName) ?: return emptyList()
        val layouts = config.layouts.keys.toList().sorted()
        Log.d(TAG, "getAvailableLayouts for '$overlayName' customCfg='$customCfgName': ${layouts.size} layouts found: ${layouts.joinToString()}")
        return layouts
    }
    
    /**
     * Filtrer les overlays compatibles avec une console
     * @param console ID de la console (ex: "nes", "psx")
     * @return Liste des overlays compatibles (universels + spécifiques à la console)
     */
    fun getCompatibleOverlays(console: String): List<String> {
        val allOverlays = getAvailableOverlays()
        Log.d(TAG, "getCompatibleOverlays for console='$console': found ${allOverlays.size} total overlays")
        Log.d(TAG, "Available overlays: ${allOverlays.joinToString()}")
        
        val consoleLower = console.lowercase()
        val compatible = mutableListOf<String>()
        
        allOverlays.forEach { overlay ->
            val overlayLower = overlay.lowercase()
            
            // 1. OVERLAYS UNIVERSELS (compatibles TOUTES consoles) - EN PREMIER
            val isUniversal = overlayLower.contains("retropad") || 
                             overlayLower == "flat" ||
                             overlayLower.startsWith("flat-") && isGenericFlat(overlayLower)
            
            if (isUniversal) {
                compatible.add(overlay)
                return@forEach
            }
            
            // 2. OVERLAYS SPÉCIFIQUES À LA CONSOLE - Ordre important pour éviter faux positifs!
            val isCompatible = when (consoleLower) {
                // IMPORTANT: Tester les plus spécifiques D'ABORD pour éviter contains() ambiguités
                
                // SNES (tester AVANT nes pour éviter "nes" dans "snes")
                "snes", "superfamicom", "super famicom" -> 
                    overlayLower.contains("snes") && !overlayLower.contains("nes-")
                
                // NES (APRÈS snes, exclure genesis aussi!)
                "nes", "famicom", "fc" -> 
                    (overlayLower.contains("nes") && !overlayLower.contains("snes") && !overlayLower.contains("genesis")) ||
                    overlayLower == "nes" || overlayLower.startsWith("nes-")
                
                // GameBoy Advanced (tester AVANT gameboy)
                "gba", "gameboyadvance", "gameboy advance" -> 
                    overlayLower.contains("gba") || overlayLower.contains("gameboy") && overlayLower.contains("advance")
                
                // GameBoy/Color (APRÈS gba)
                "gb", "gameboy", "game boy" -> 
                    (overlayLower.contains("gameboy") || overlayLower.contains("game-boy") || overlayLower == "gb") && 
                    !overlayLower.contains("gba") && !overlayLower.contains("advance")
                
                "gbc", "gameboycolor", "gameboy color" -> 
                    (overlayLower.contains("gameboy") || overlayLower.contains("game-boy") || overlayLower.contains("gbc")) && 
                    !overlayLower.contains("gba") && !overlayLower.contains("advance")
                
                // PlayStation
                "psx", "ps1", "playstation", "playstation 1" -> 
                    overlayLower.contains("psx") || overlayLower.contains("ps-") || 
                    overlayLower.contains("dual-shock") || overlayLower.contains("dualshock") ||
                    (overlayLower.contains("playstation") && !overlayLower.contains("2"))
                
                // PSP (tester avant "ps")
                "psp", "playstation portable" -> 
                    overlayLower.contains("psp")
                
                // Genesis/MegaDrive
                "genesis", "megadrive", "md", "mega drive" -> 
                    overlayLower.contains("genesis") || overlayLower.contains("megadrive") || overlayLower.contains("mega-drive")
                
                // Sega Master System
                "sms", "mastersystem", "master system" -> 
                    overlayLower.contains("sms") || overlayLower.contains("master")
                
                // Arcade (large compatibilité)
                "arcade", "mame", "mame2003", "mame2010", "fbneo", "cps1", "cps2", "cps3" -> 
                    overlayLower.contains("arcade") || overlayLower.contains("mame") || 
                    overlayLower.contains("cps") || overlayLower.contains("fighter")
                
                // Neo Geo (séparer Pocket de console)
                "neogeo", "neo-geo", "neo geo" -> 
                    overlayLower.contains("neogeo") && !overlayLower.contains("pocket")
                
                "ngp", "ngpc", "neogeopocket", "neo geo pocket" -> 
                    overlayLower.contains("ngp") || (overlayLower.contains("neogeo") && overlayLower.contains("pocket"))
                
                // Nintendo 64
                "n64", "nintendo64", "nintendo 64" -> 
                    overlayLower.contains("n64") || overlayLower.contains("nintendo64") || overlayLower.contains("nintendo-64")
                
                // PC Engine / TurboGrafx (tester AVANT pcfx)
                "pce", "pcengine", "pc engine", "turbografx", "tg16", "tg-16" -> 
                    (overlayLower.contains("pce") || overlayLower.contains("pcengine") || 
                     overlayLower.contains("turbografx") || overlayLower.contains("tg")) && 
                    !overlayLower.contains("pcfx") && !overlayLower.contains("pc-fx")
                
                // PC-FX (APRÈS pce)
                "pcfx", "pc-fx", "pc fx" -> 
                    overlayLower.contains("pcfx") || overlayLower.contains("pc-fx")
                
                // Sega Saturn
                "saturn", "sega saturn" -> 
                    overlayLower.contains("saturn")
                
                // Dreamcast
                "dreamcast", "dc" -> 
                    overlayLower.contains("dreamcast") || overlayLower == "dc"
                
                // Atari 7800 (tester AVANT 2600 pour éviter substring)
                "atari7800", "atari-7800", "atari 7800" -> 
                    overlayLower.contains("7800") || (overlayLower.contains("atari") && overlayLower.contains("78"))
                
                // Atari 2600 (APRÈS 7800)
                "atari2600", "atari-2600", "atari 2600", "atari" -> 
                    (overlayLower.contains("2600") || overlayLower.contains("26")) && !overlayLower.contains("7800")
                
                // Atari Lynx
                "atarilynx", "atari-lynx", "lynx" -> 
                    overlayLower.contains("lynx") || (overlayLower.contains("atari") && overlayLower.contains("lynx"))
                
                // WonderSwan
                "wonderswan", "wswan", "ws", "wsc", "wonderswancolor" -> 
                    overlayLower.contains("wonderswan") || overlayLower.contains("wswan") || 
                    overlayLower == "ws" || overlayLower == "wsc"
                
                // VirtualBoy
                "virtualboy", "vb", "virtual boy" -> 
                    overlayLower.contains("virtualboy") || overlayLower.contains("virtual-boy") || 
                    overlayLower == "vb"
                
                // PokeMini
                "pokemini", "pokemon mini" -> 
                    overlayLower.contains("pokemini") || overlayLower.contains("pokemon-mini")
                
                // GameCube
                "gamecube", "ngc", "gc", "nintendo gamecube" -> 
                    overlayLower.contains("gamecube") || overlayLower.contains("game-cube")
                
                else -> false  // Pas de correspondance spécifique
            }
            
            if (isCompatible) {
                compatible.add(overlay)
            }
        }
        
        Log.i(TAG, "Compatible overlays for '$console': ${compatible.joinToString()}")
        return compatible.distinct()
    }
    
    /**
     * Vérifier si un overlay "flat-xxx" est générique (pas spécifique à une console)
     */
    private fun isGenericFlat(overlayName: String): Boolean {
        // flat-retropad, flat-one-handed, etc. sont génériques
        // flat-nes, flat-psx, etc. sont spécifiques
        val consoleSpecificKeywords = listOf(
            "nes", "snes", "psx", "genesis", "n64", "gba", "gameboy",
            "arcade", "saturn", "dreamcast", "psp", "atari", "lynx"
        )
        return !consoleSpecificKeywords.any { overlayName.contains(it) }
    }
    
    /**
     * Obtenir les informations d'un package d'overlay
     * @param overlayName Nom de l'overlay
     * @return Informations du package, ou null si non trouvé
     */
    fun getPackageInfo(overlayName: String): OverlayPackageInfo? {
        // Essayer de charger une config représentative: d'abord <name>.cfg, sinon première *.cfg trouvée
        val dir = File(OVERLAY_DIR, overlayName)
        val preferred = File(dir, "$overlayName.cfg")
        val cfgForInfo: RetroArchOverlayConfig? = when {
            preferred.exists() -> parser.parseConfig(preferred)
            else -> {
                val firstCfg = dir.listFiles { f -> f.isFile && f.name.endsWith(".cfg", true) }?.firstOrNull()
                if (firstCfg != null) parser.parseConfig(firstCfg) else null
            }
        }
        val config = cfgForInfo ?: return null
        
        // Déterminer les consoles compatibles depuis le nom
        val compatibleConsoles = mutableListOf<String>()
        when {
            overlayName.contains("nes") && !overlayName.contains("snes") -> compatibleConsoles.add("nes")
            overlayName.contains("snes") -> compatibleConsoles.add("snes")
            overlayName.contains("psx") -> compatibleConsoles.addAll(listOf("psx", "ps1"))
            overlayName.contains("genesis") -> compatibleConsoles.addAll(listOf("genesis", "megadrive"))
            overlayName.contains("sms") -> compatibleConsoles.add("sms")
            overlayName.contains("arcade") -> compatibleConsoles.addAll(listOf("arcade", "mame", "fbneo"))
            overlayName.contains("neogeo") && !overlayName.contains("pocket") -> compatibleConsoles.add("neogeo")
            overlayName.contains("dual-shock") -> compatibleConsoles.addAll(listOf("psx", "ps1"))
            overlayName.contains("n64") -> compatibleConsoles.add("n64")
            overlayName.contains("gba") -> compatibleConsoles.add("gba")
            overlayName.contains("gameboy") -> compatibleConsoles.addAll(listOf("gb", "gbc"))
            overlayName.contains("psp") -> compatibleConsoles.add("psp")
            overlayName.contains("pce") -> compatibleConsoles.addAll(listOf("pce", "tg16"))
            overlayName.contains("saturn") -> compatibleConsoles.add("saturn")
            overlayName.contains("dreamcast") -> compatibleConsoles.add("dreamcast")
            overlayName.contains("atari2600") -> compatibleConsoles.add("atari2600")
            overlayName.contains("atari7800") -> compatibleConsoles.add("atari7800")
            overlayName.contains("atarilynx") || overlayName.contains("lynx") -> compatibleConsoles.add("atarilynx")
            overlayName.contains("ngp") -> compatibleConsoles.add("ngp")
            overlayName.contains("wonderswan") -> compatibleConsoles.addAll(listOf("wonderswan", "wswan"))
            overlayName.contains("virtualboy") -> compatibleConsoles.add("virtualboy")
            overlayName.contains("pokemini") -> compatibleConsoles.add("pokemini")
            overlayName.contains("pcfx") -> compatibleConsoles.add("pcfx")
            overlayName.contains("gamecube") -> compatibleConsoles.add("gamecube")
        }
        
        // Générer une description
        val landscapeCount = config.layouts.keys.count { it.contains("landscape") }
        val portraitCount = config.layouts.keys.count { it.contains("portrait") }
        val hiddenCount = config.layouts.keys.count { it.contains("hidden") }
        
        val description = buildString {
            append("${config.totalOverlays} layouts")
            if (landscapeCount > 0) append(", ${landscapeCount} landscape")
            if (portraitCount > 0) append(", ${portraitCount} portrait")
            if (hiddenCount > 0) append(", ${hiddenCount} hidden")
        }
        
        val hasAnalog = overlayName.contains("dual") || overlayName.contains("analog")
        
        val displayName = overlayName
            .replace("flat-", "RetroArch Flat - ")
            .replace("dual-shock", "RetroArch DualShock")
            .replaceFirstChar { it.uppercase() }
        
        return OverlayPackageInfo(
            name = overlayName,
            displayName = displayName,
            description = description,
            compatibleConsoles = compatibleConsoles,
            layoutCount = config.totalOverlays,
            hasAnalogSupport = hasAnalog
        )
    }
    
    /**
     * Vérifier si un overlay est installé
     * @param overlayName Nom de l'overlay
     * @return true si installé et valide
     */
    fun isOverlayInstalled(overlayName: String): Boolean {
        val dir = File(OVERLAY_DIR, overlayName)
        return dir.exists() && dir.isDirectory && dir.listFiles()?.isNotEmpty() == true
    }

    private fun mapConsoleToCfg(console: String): String {
        return when (console.lowercase()) {
            "nes", "famicom" -> "nes"
            "snes", "superfamicom" -> "snes"
            "psx", "ps1", "playstation" -> "psx"
            "n64", "nintendo64" -> "nintendo64"
            "genesis", "megadrive", "md" -> "genesis"
            "sms", "mastersystem" -> "sms"
            "gb", "gameboy" -> "gameboy"
            "gbc", "gameboycolor" -> "gameboy" // souvent partagé
            "gba", "gameboyadvance" -> "gba"
            "psp" -> "psp"
            "pce", "pcengine", "turbografx", "tg16" -> "pc-fx" // selon packages
            "saturn" -> "saturn"
            "dreamcast", "dc" -> "dreamcast"
            "arcade", "mame", "fbneo" -> "arcade"
            "neogeo" -> "neogeo"
            "virtualboy" -> "virtualboy"
            "pokemini" -> "pokemini"
            "atarilynx", "lynx" -> "atari_lynx"
            "atari2600" -> "atari2600"
            "atari7800" -> "atari7800"
            "ngp", "ngpc" -> "neogeo_pocket"
            else -> console.lowercase()
        }
    }
    
    /**
     * Obtenir la taille totale des overlays installés
     * @return Taille en bytes
     */
    fun getTotalSize(): Long {
        val overlayDir = File(OVERLAY_DIR)
        if (!overlayDir.exists()) return 0
        
        return overlayDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}

