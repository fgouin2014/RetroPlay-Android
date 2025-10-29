package com.retroplay.overlay.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.retroplay.overlay.models.OverlayPackageInfo
import com.retroplay.overlay.models.RetroArchOverlayConfig
import com.retroplay.overlay.parser.RetroArchOverlayParser
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
        
        // Répertoire de stockage des overlays sur le device
        const val OVERLAY_DIR = "/storage/emulated/0/RetroPlay-Data/overlays"
        
        // Packages d'overlays disponibles (à copier depuis assets)
        val AVAILABLE_PACKAGES = listOf(
            "dual-shock",
            "flat-arcade",
            "flat-atari2600",
            "flat-atari7800",
            "flat-atarilynx",
            "flat-dreamcast",
            "flat-gameboy",
            "flat-gamecube",
            "flat-gba",
            "flat-genesis",
            "flat-n64",
            "flat-neogeo",
            "flat-nes",
            "flat-ngp",
            "flat-pce",
            "flat-pcfx",
            "flat-pokemini",
            "flat-psp",
            "flat-psx",
            "flat-retropad",
            "flat-saturn",
            "flat-sms",
            "flat-snes",
            "flat-virtualboy",
            "flat-wonderswan",
            "retropad"
        )
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
        
        // Installer chaque package
        var successCount = 0
        AVAILABLE_PACKAGES.forEachIndexed { index, packageName ->
            progressCallback?.onProgress(index + 1, AVAILABLE_PACKAGES.size, packageName)
            if (installOverlayPackage(packageName)) {
                successCount++
            }
        }
        
        progressCallback?.onComplete(successCount, AVAILABLE_PACKAGES.size)
        Log.i(TAG, "Installed $successCount/${AVAILABLE_PACKAGES.size} overlay packages")
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
            
            // Copier le fichier .cfg
            val cfgPath = "overlays/$packageName/$packageName.cfg"
            val cfgExists = try {
                context.assets.open(cfgPath).close()
                true
            } catch (e: Exception) {
                false
            }
            
            if (!cfgExists) {
                Log.w(TAG, "No .cfg file found for $packageName at $cfgPath")
                return false
            }
            
            context.assets.open(cfgPath).use { input ->
                File(targetDir, "$packageName.cfg").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Copier les images
            val imgDir = File(targetDir, "img")
            if (!imgDir.mkdirs() && !imgDir.exists()) {
                Log.e(TAG, "Failed to create img directory for $packageName")
                return false
            }
            
            val imgPath = "overlays/$packageName/img"
            val imageFiles = try {
                context.assets.list(imgPath) ?: emptyArray()
            } catch (e: Exception) {
                Log.w(TAG, "No images found for $packageName")
                emptyArray()
            }
            
            imageFiles.forEach { imageFile ->
                try {
                    context.assets.open("$imgPath/$imageFile").use { input ->
                        File(imgDir, imageFile).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy image $imageFile for $packageName", e)
                }
            }
            
            Log.i(TAG, "Successfully installed $packageName (${imageFiles.size} images)")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error installing overlay package $packageName", e)
            return false
        }
    }
    
    /**
     * Charger une configuration d'overlay
     * @param overlayName Nom de l'overlay (ex: "flat-nes")
     * @return Configuration parsée, ou null si erreur
     */
    fun loadOverlayConfig(overlayName: String): RetroArchOverlayConfig? {
        val cfgFile = File(OVERLAY_DIR, "$overlayName/$overlayName.cfg")
        
        if (!cfgFile.exists()) {
            Log.e(TAG, "Config file not found: ${cfgFile.absolutePath}")
            return null
        }
        
        val config = parser.parseConfig(cfgFile)
        
        if (config != null && parser.validateConfig(config)) {
            Log.i(TAG, "Successfully loaded overlay config: $overlayName")
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
     * @return Liste des noms de layouts (ex: ["landscape-A", "portrait-B"])
     */
    fun getAvailableLayouts(overlayName: String): List<String> {
        val config = loadOverlayConfig(overlayName) ?: return emptyList()
        val layouts = config.layouts.keys.toList().sorted()
        Log.d(TAG, "getAvailableLayouts for '$overlayName': ${layouts.size} layouts found: ${layouts.joinToString()}")
        return layouts
    }
    
    /**
     * Filtrer les overlays compatibles avec une console
     * @param console ID de la console (ex: "nes", "psx")
     * @return Liste des overlays compatibles
     */
    fun getCompatibleOverlays(console: String): List<String> {
        val allOverlays = getAvailableOverlays()
        Log.d(TAG, "getCompatibleOverlays for console='$console': found ${allOverlays.size} total overlays")
        Log.d(TAG, "Available overlays: ${allOverlays.joinToString()}")
        
        // Les retropads universels (compatibles avec TOUTES les consoles)
        val universalOverlays = allOverlays.filter { it.contains("retropad") }
        
        // Correspondances console -> overlays spécifiques
        val specificOverlays = allOverlays.filter { overlay ->
            when (console.lowercase()) {
                "nes", "famicom" -> overlay.contains("nes")
                "snes", "superfamicom" -> overlay.contains("snes")
                "psx", "ps1", "playstation" -> overlay.contains("psx") || overlay.contains("dual-shock")
                "genesis", "megadrive", "md" -> overlay.contains("genesis") || overlay.contains("megadrive")
                "sms", "mastersystem" -> overlay.contains("sms")
                "arcade", "mame", "fbneo", "cps1", "cps2", "cps3", "neogeo" -> 
                    overlay.contains("arcade") || overlay.contains("neogeo")
                "n64", "nintendo64" -> overlay.contains("n64")
                "gb", "gameboy" -> overlay.contains("gameboy") && !overlay.contains("gba")
                "gbc", "gameboycolor" -> overlay.contains("gameboy") && !overlay.contains("gba")
                "gba", "gameboyadvance" -> overlay.contains("gba")
                "psp" -> overlay.contains("psp")
                "pce", "pcengine", "turbografx", "tg16" -> overlay.contains("pce")
                "saturn" -> overlay.contains("saturn")
                "dreamcast", "dc" -> overlay.contains("dreamcast")
                "atari2600", "atari-2600" -> overlay.contains("atari2600")
                "atari7800", "atari-7800" -> overlay.contains("atari7800")
                "atarilynx", "lynx" -> overlay.contains("atarilynx") || overlay.contains("lynx")
                "ngp", "ngpc", "neogeopocket" -> overlay.contains("ngp")
                "wonderswan", "wswan", "ws", "wsc" -> overlay.contains("wonderswan")
                "virtualboy", "vb" -> overlay.contains("virtualboy")
                "pokemini" -> overlay.contains("pokemini")
                "pcfx", "pc-fx" -> overlay.contains("pcfx")
                "gamecube", "ngc", "gc" -> overlay.contains("gamecube")
                else -> false  // Pas d'overlay spécifique pour cette console
            }
        }.filter { !it.contains("retropad") }  // Exclure les retropads des overlays spécifiques
        
        // Combiner : retropads universels EN PREMIER (recommandés), puis overlays spécifiques
        val compatible = (universalOverlays + specificOverlays).distinct()
        
        Log.i(TAG, "Compatible overlays for '$console': ${compatible.joinToString()}")
        return compatible
    }
    
    /**
     * Obtenir les informations d'un package d'overlay
     * @param overlayName Nom de l'overlay
     * @return Informations du package, ou null si non trouvé
     */
    fun getPackageInfo(overlayName: String): OverlayPackageInfo? {
        val config = loadOverlayConfig(overlayName) ?: return null
        
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
        val cfgFile = File(OVERLAY_DIR, "$overlayName/$overlayName.cfg")
        val imgDir = File(OVERLAY_DIR, "$overlayName/img")
        
        return cfgFile.exists() && imgDir.exists() && (imgDir.listFiles()?.isNotEmpty() == true)
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

