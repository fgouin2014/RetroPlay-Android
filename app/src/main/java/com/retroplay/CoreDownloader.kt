package com.retroplay

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Télécharge et installe les cores Libretro depuis le buildbot officiel
 * Supporte ARM 32-bit (armeabi-v7a) et 64-bit (arm64-v8a)
 */
object CoreDownloader {
    private const val TAG = "CoreDownloader"
    
    // Buildbot Libretro officiel
    private const val BUILDBOT_BASE_URL = "https://buildbot.libretro.com/nightly/android"
    
    // Détection automatique de l'architecture
    fun getDeviceABI(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            else -> {
                Log.w(TAG, "Unknown ABI, defaulting to armeabi-v7a: ${abis.joinToString()}")
                "armeabi-v7a"
            }
        }
    }
    
    /**
     * Liste des cores disponibles avec leurs noms de fichiers buildbot
     * 
     * NOTE: Cette méthode est maintenue pour compatibilité.
     * Pour une détection automatique depuis cores.json, utiliser CoreMetadataManager.
     * 
     * @deprecated Utiliser CoreMetadataManager.fetchCoresMetadata() pour détection automatique
     */
    @Deprecated("Use CoreMetadataManager.fetchCoresMetadata() for automatic detection")
    fun getAvailableCores(): List<CoreInfo> {
        return listOf(
            // Nintendo
            CoreInfo("fceumm", "fceumm_libretro_android.so", "Nintendo - NES / Famicom (FCEUmm)"),
            CoreInfo("mesen", "mesen_libretro_android.so", "Nintendo - NES / Famicom (Mesen)"),
            CoreInfo("snes9x", "snes9x_libretro_android.so", "Nintendo - SNES (Snes9x)"),
            CoreInfo("parallel_n64", "parallel_n64_libretro_android.so", "Nintendo - N64 (ParaLLEl N64)"),
            CoreInfo("mupen64plus_next", "mupen64plus_next_libretro_android.so", "Nintendo - N64 (Mupen64Plus Next)"),
            CoreInfo("mupen64plus_next_gles2", "mupen64plus_next_gles2_libretro_android.so", "Nintendo - N64 (Mupen64Plus GLES2)"),
            CoreInfo("gambatte", "gambatte_libretro_android.so", "Nintendo - GB/GBC (Gambatte)"),
            CoreInfo("mgba", "mgba_libretro_android.so", "Nintendo - GBA (mGBA)"),
            CoreInfo("melonds", "melonds_libretro_android.so", "Nintendo - DS (melonDS)"),
            CoreInfo("desmume", "desmume_libretro_android.so", "Nintendo - DS (DeSmuME)"),
            
            // Sega
            CoreInfo("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Sega - Genesis/MD/MS/GG (Genesis Plus GX)"),
            CoreInfo("picodrive", "picodrive_libretro_android.so", "Sega - Genesis/32X/SegaCD (PicoDrive)"),
            
            // Sony
            CoreInfo("pcsx_rearmed", "pcsx_rearmed_libretro_android.so", "Sony - PlayStation (PCSX ReARMed)"),
            CoreInfo("ppsspp", "ppsspp_libretro_android.so", "Sony - PSP (PPSSPP)"),
            
            // Arcade
            CoreInfo("fbneo", "fbneo_libretro_android.so", "Arcade - FBNeo"),
            CoreInfo("mame2003_plus", "mame2003_plus_libretro_android.so", "Arcade - MAME 2003 Plus"),
            CoreInfo("mame2003", "mame2003_libretro_android.so", "Arcade - MAME 2003"),
            CoreInfo("mame2010", "mame2010_libretro_android.so", "Arcade - MAME 2010"),
            CoreInfo("fbalpha2012_cps1", "fbalpha2012_cps1_libretro_android.so", "Arcade - CPS1 (FBAlpha 2012)"),
            CoreInfo("fbalpha2012_cps2", "fbalpha2012_cps2_libretro_android.so", "Arcade - CPS2 (FBAlpha 2012)"),
            
            // Atari
            CoreInfo("stella2014", "stella2014_libretro_android.so", "Atari - 2600 (Stella 2014)"),
            CoreInfo("a5200", "a5200_libretro_android.so", "Atari - 5200 (a5200)"),
            CoreInfo("prosystem", "prosystem_libretro_android.so", "Atari - 7800 (ProSystem)"),
            CoreInfo("handy", "handy_libretro_android.so", "Atari - Lynx (Handy)"),
            
            // Autres
            CoreInfo("mednafen_pce", "mednafen_pce_libretro_android.so", "NEC - PC Engine / TurboGrafx-16 (Mednafen)"),
            CoreInfo("mednafen_ngp", "mednafen_ngp_libretro_android.so", "SNK - Neo Geo Pocket (Mednafen)"),
            CoreInfo("mednafen_wswan", "mednafen_wswan_libretro_android.so", "Bandai - WonderSwan (Mednafen)"),

            
            // Commodore
            CoreInfo("vice_x64", "vice_x64_libretro_android.so", "Commodore - 64 (VICE x64)"),
            CoreInfo("vice_x64sc", "vice_x64sc_libretro_android.so", "Commodore - 64 (VICE x64sc)"),
            CoreInfo("vice_x128", "vice_x128_libretro_android.so", "Commodore - 128 (VICE x128)"),
            CoreInfo("puae", "puae_libretro_android.so", "Commodore - Amiga (PUAE)"),
            CoreInfo("dosbox_pure", "dosbox_pure_libretro_android.so", "DOS (DOSBox Pure)")
        )
    }
    
    /**
     * Obtient les cores disponibles depuis cores.json (détection automatique)
     * 
     * @param context Context Android
     * @param buildType Type de build (NIGHTLY ou STABLE)
     * @return Liste des CoreInfo disponibles
     */
    suspend fun getAvailableCoresFromMetadata(
        context: Context,
        buildType: CoreMetadataManager.BuildType = CoreMetadataManager.BuildType.NIGHTLY
    ): List<CoreInfo> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val metadata = CoreMetadataManager.fetchCoresMetadata(context, buildType)
            return@withContext metadata.values.map { CoreMetadataManager.toCoreInfo(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cores from metadata, falling back to hardcoded list: ${e.message}", e)
            // Fallback sur liste hardcodée en cas d'erreur
            return@withContext getAvailableCores()
        }
    }
    
    /**
     * Obtient le répertoire pour stocker les cores téléchargés
     * Utilise le répertoire privé de l'app pour être accessible au linker Android
     */
    fun getCoresDirectory(context: Context): String {
        return "${context.filesDir.absolutePath}/cores"
    }
    
    /**
     * Télécharge un core depuis le buildbot
     * @param context Context Android
     * @param coreInfo Info du core à télécharger
     * @param progressCallback Callback pour la progression (0-100)
     * @return true si succès
     */
    fun downloadCore(
        context: Context,
        coreInfo: CoreInfo,
        progressCallback: ((Int, String) -> Unit)? = null
    ): Boolean {
        try {
            val abi = getDeviceABI()
            val abiPath = if (abi == "arm64-v8a") "latest/arm64-v8a" else "latest/armeabi-v7a"
            val url = "$BUILDBOT_BASE_URL/$abiPath/${coreInfo.fileName}.zip"
            
            Log.i(TAG, "Downloading core from: $url")
            progressCallback?.invoke(0, "Connecting...")
            
            // Créer le répertoire de destination (dans le répertoire privé de l'app)
            val destDir = File(getCoresDirectory(context))
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            
            // Télécharger le fichier ZIP
            val connection = URL(url).openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()
            
            val fileLength = connection.contentLength
            val inputStream = connection.getInputStream()
            
            progressCallback?.invoke(10, "Downloading ${coreInfo.fileName}...")
            
            // Lire le ZIP et extraire le .so
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            
            while (entry != null) {
                if (entry.name.endsWith(".so")) {
                    val destFile = File(destDir, coreInfo.fileName)
                    
                    Log.i(TAG, "Extracting ${entry.name} to ${destFile.absolutePath}")
                    progressCallback?.invoke(50, "Extracting...")
                    
                    // Extraire le fichier
                    val outputStream = FileOutputStream(destFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (zipStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.close()
                    
                    // Rendre le fichier exécutable
                    destFile.setExecutable(true, false)
                    destFile.setReadable(true, false)
                    
                    progressCallback?.invoke(100, "Installed!")
                    Log.i(TAG, "Core installed successfully: ${destFile.absolutePath}")
                    
                    zipStream.closeEntry()
                    zipStream.close()
                    inputStream.close()
                    return true
                }
                entry = zipStream.nextEntry
            }
            
            zipStream.close()
            inputStream.close()
            
            Log.e(TAG, "No .so file found in ZIP")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading core ${coreInfo.id}: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Vérifie si un core est déjà installé (téléchargé ou intégré)
     */
    fun isCoreInstalled(context: Context, coreInfo: CoreInfo): Boolean {
        // 1. Vérifier si téléchargé
        val coresDir = File(getCoresDirectory(context))
        val coreFile = File(coresDir, coreInfo.fileName)
        if (coreFile.exists()) return true

        // 2. Vérifier si intégré (bibliothèque native de l'app)
        return isCoreEmbedded(context, coreInfo)
    }

    /**
     * Vérifie si un core est intégré à l'application (lib native)
     */
    fun isCoreEmbedded(context: Context, coreInfo: CoreInfo): Boolean {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val nativeFile = File(nativeLibraryDir, coreInfo.fileName)
        return nativeFile.exists()
    }
    
    /**
     * Supprime un core installé
     */
    fun deleteCore(context: Context, coreInfo: CoreInfo): Boolean {
        try {
            val coresDir = File(getCoresDirectory(context))
            val coreFile = File(coresDir, coreInfo.fileName)
            
            if (coreFile.exists()) {
                val deleted = coreFile.delete()
                Log.i(TAG, "Core deleted: ${coreInfo.fileName} (success=$deleted)")
                return deleted
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting core: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Obtient le chemin complet vers un core installé
     * @return Chemin absolu vers le .so ou null si non installé
     */
    fun getCorePath(context: Context, coreInfo: CoreInfo): String? {
        // 1. Priorité aux cores téléchargés
        val coresDir = File(getCoresDirectory(context))
        val coreFile = File(coresDir, coreInfo.fileName)
        if (coreFile.exists()) return coreFile.absolutePath

        // 2. Fallback aux cores intégrés
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val nativeFile = File(nativeLibraryDir, coreInfo.fileName)
        if (nativeFile.exists()) return nativeFile.absolutePath

        return null
    }
    
    /**
     * Obtient le chemin complet vers un core par son nom de fichier
     */
    fun getCorePathByFileName(context: Context, fileName: String): String? {
        // 1. Priorité aux cores téléchargés
        val coresDir = File(getCoresDirectory(context))
        val coreFile = File(coresDir, fileName)
        if (coreFile.exists()) return coreFile.absolutePath

        // 2. Fallback aux cores intégrés
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val nativeFile = File(nativeLibraryDir, fileName)
        if (nativeFile.exists()) return nativeFile.absolutePath

        return null
    }
    
    data class CoreInfo(
        val id: String,           // ID du core (ex: "fceumm")
        val fileName: String,     // Nom du fichier .so
        val displayName: String   // Nom d'affichage
    )
}

