package com.retroplay

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * CoreMetadataManager - Gestionnaire de métadonnées des cores Libretro
 * 
 * Détecte automatiquement les cores disponibles depuis cores.json du buildbot officiel.
 * Suit les spécifications officielles Libretro/RetroArch.
 * 
 * Référence: https://buildbot.libretro.com/nightly/android/latest/cores.json
 */
object CoreMetadataManager {
    private const val TAG = "CoreMetadataManager"
    
    // URLs officielles buildbot
    private const val BUILDBOT_NIGHTLY_BASE = "https://buildbot.libretro.com/nightly/android"
    private const val BUILDBOT_STABLE_BASE = "https://buildbot.libretro.com/stable/android"
    
    // Cache local (24h)
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    
    enum class BuildType {
        NIGHTLY,
        STABLE
    }
    
    data class CoreMetadata(
        val id: String,                    // ID unique du core (ex: "fceumm")
        val displayName: String,           // Nom d'affichage (ex: "Nintendo - NES / Famicom (FCEUmm)")
        val coreName: String,              // Nom technique (ex: "FCEUmm")
        val systemName: String,            // Nom système (ex: "Nintendo Entertainment System")
        val systemId: String,              // ID système (ex: "nes")
        val manufacturer: String,           // Fabricant (ex: "Nintendo")
        val license: String,               // Licence (ex: "GPLv2")
        val displayVersion: String,        // Version affichée (ex: "1.0.0")
        val supportsNoGame: Boolean,       // Supporte mode sans jeu
        val database: String?,             // Nom de la base de données (ex: "Nintendo - Nintendo Entertainment System")
        val requiredHwApi: List<String>,   // APIs matériel requises
        val firmwareCount: Int,            // Nombre de firmwares requis
        val notes: String?,                 // Notes additionnelles
        val categories: List<String>,      // Catégories (ex: ["Emulator"])
        val fileName: String                // Nom du fichier .so (ex: "fceumm_libretro_android.so")
    )
    
    private var cachedMetadata: Map<String, CoreMetadata>? = null
    private var cacheTimestamp: Long = 0
    private val cacheLock = java.util.concurrent.locks.ReentrantLock()
    
    /**
     * Obtient les métadonnées de tous les cores disponibles
     * Utilise le cache si disponible et récent (< 24h)
     */
    suspend fun fetchCoresMetadata(
        context: Context,
        buildType: BuildType = BuildType.NIGHTLY,
        forceRefresh: Boolean = false
    ): Map<String, CoreMetadata> = withContext(Dispatchers.IO) {
        cacheLock.lock()
        try {
            // Vérifier cache
            if (!forceRefresh && cachedMetadata != null) {
                val cacheAge = System.currentTimeMillis() - cacheTimestamp
                if (cacheAge < CACHE_DURATION_MS) {
                    Log.i(TAG, "Using cached cores metadata (age: ${cacheAge / 1000}s)")
                    return@withContext cachedMetadata!!
                }
            }
            
            // Charger depuis cache local si disponible
            val localCache = loadFromLocalCache(context)
            if (localCache != null && !forceRefresh) {
                val cacheAge = System.currentTimeMillis() - getLocalCacheTimestamp(context)
                if (cacheAge < CACHE_DURATION_MS) {
                    Log.i(TAG, "Using local cached cores metadata")
                    cachedMetadata = localCache
                    cacheTimestamp = System.currentTimeMillis()
                    return@withContext localCache
                }
            }
            
            // Télécharger depuis buildbot
            val metadata = downloadCoresMetadata(buildType)
            
            // Sauvegarder en cache
            cachedMetadata = metadata
            cacheTimestamp = System.currentTimeMillis()
            saveToLocalCache(context, metadata)
            
            Log.i(TAG, "Fetched ${metadata.size} cores from buildbot")
            return@withContext metadata
            
        } finally {
            cacheLock.unlock()
        }
    }
    
    /**
     * Télécharge les métadonnées depuis le buildbot officiel
     */
    private suspend fun downloadCoresMetadata(buildType: BuildType): Map<String, CoreMetadata> = withContext(Dispatchers.IO) {
        val baseUrl = when (buildType) {
            BuildType.NIGHTLY -> BUILDBOT_NIGHTLY_BASE
            BuildType.STABLE -> BUILDBOT_STABLE_BASE
        }
        
        val coresJsonUrl = "$baseUrl/latest/cores.json"
        val abi = CoreDownloader.getDeviceABI()
        
        Log.i(TAG, "Downloading cores metadata from: $coresJsonUrl")
        
        try {
            val url = URL(coresJsonUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.connect()
            
            if (connection.responseCode != 200) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }
            
            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonText)
            
            val metadataMap = mutableMapOf<String, CoreMetadata>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val coreObj = jsonArray.getJSONObject(i)
                    val metadata = parseCoreMetadata(coreObj, abi)
                    if (metadata != null) {
                        metadataMap[metadata.id] = metadata
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing core at index $i: ${e.message}")
                }
            }
            
            connection.disconnect()
            return@withContext metadataMap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading cores metadata: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Parse un objet JSON de core en CoreMetadata
     */
    private fun parseCoreMetadata(coreObj: JSONObject, abi: String): CoreMetadata? {
        try {
            val id = coreObj.optString("id", "")
            val coreName = coreObj.optString("core_name", "")
            val displayName = coreObj.optString("display_name", coreName)
            val systemName = coreObj.optString("system_name", "")
            val systemId = coreObj.optString("system_id", "")
            val manufacturer = coreObj.optString("manufacturer", "")
            val license = coreObj.optString("license", "")
            val displayVersion = coreObj.optString("display_version", "unknown")
            val supportsNoGame = coreObj.optBoolean("supports_no_game", false)
            val database = coreObj.optString("database", null)
            val notes = coreObj.optString("notes", null)
            
            // Required HW API
            val requiredHwApi = mutableListOf<String>()
            val hwApiArray = coreObj.optJSONArray("required_hw_api")
            if (hwApiArray != null) {
                for (j in 0 until hwApiArray.length()) {
                    requiredHwApi.add(hwApiArray.getString(j))
                }
            }
            
            // Firmware count
            val firmwareCount = coreObj.optInt("firmware_count", 0)
            
            // Categories
            val categories = mutableListOf<String>()
            val categoriesArray = coreObj.optJSONArray("categories")
            if (categoriesArray != null) {
                for (j in 0 until categoriesArray.length()) {
                    categories.add(categoriesArray.getString(j))
                }
            }
            
            // Construire le nom de fichier .so
            // Format: {core_name}_libretro_android.so
            val fileName = "${coreName}_libretro_android.so"
            
            return CoreMetadata(
                id = id,
                displayName = displayName,
                coreName = coreName,
                systemName = systemName,
                systemId = systemId,
                manufacturer = manufacturer,
                license = license,
                displayVersion = displayVersion,
                supportsNoGame = supportsNoGame,
                database = database,
                requiredHwApi = requiredHwApi,
                firmwareCount = firmwareCount,
                notes = notes,
                categories = categories,
                fileName = fileName
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing core metadata: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Obtient les cores disponibles pour un ABI spécifique
     */
    suspend fun getCoresForABI(
        context: Context,
        abi: String,
        buildType: BuildType = BuildType.NIGHTLY
    ): List<CoreMetadata> = withContext(Dispatchers.IO) {
        val allCores = fetchCoresMetadata(context, buildType)
        
        // Filtrer selon ABI supporté
        // Note: Pour l'instant, on assume que tous les cores Android supportent arm64-v8a et armeabi-v7a
        // Dans le futur, on pourrait vérifier les fichiers disponibles sur le buildbot
        return@withContext allCores.values.toList()
    }
    
    /**
     * Convertit CoreMetadata en CoreInfo (pour compatibilité avec CoreDownloader)
     */
    fun toCoreInfo(metadata: CoreMetadata): CoreDownloader.CoreInfo {
        return CoreDownloader.CoreInfo(
            id = metadata.id,
            fileName = metadata.fileName,
            displayName = metadata.displayName
        )
    }
    
    /**
     * Sauvegarde les métadonnées en cache local
     */
    private fun saveToLocalCache(context: Context, metadata: Map<String, CoreMetadata>) {
        try {
            val cacheFile = File(context.cacheDir, "cores_metadata.json")
            val timestampFile = File(context.cacheDir, "cores_metadata.timestamp")
            
            // Convertir en JSON
            val jsonArray = JSONArray()
            metadata.values.forEach { core ->
                val obj = JSONObject().apply {
                    put("id", core.id)
                    put("display_name", core.displayName)
                    put("core_name", core.coreName)
                    put("system_name", core.systemName)
                    put("system_id", core.systemId)
                    put("manufacturer", core.manufacturer)
                    put("license", core.license)
                    put("display_version", core.displayVersion)
                    put("supports_no_game", core.supportsNoGame)
                    put("database", core.database ?: JSONObject.NULL)
                    put("required_hw_api", JSONArray(core.requiredHwApi))
                    put("firmware_count", core.firmwareCount)
                    put("notes", core.notes ?: JSONObject.NULL)
                    put("categories", JSONArray(core.categories))
                    put("fileName", core.fileName)
                }
                jsonArray.put(obj)
            }
            
            cacheFile.writeText(jsonArray.toString())
            timestampFile.writeText(System.currentTimeMillis().toString())
            
            Log.i(TAG, "Saved ${metadata.size} cores to local cache")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving local cache: ${e.message}", e)
        }
    }
    
    /**
     * Charge les métadonnées depuis le cache local
     */
    private fun loadFromLocalCache(context: Context): Map<String, CoreMetadata>? {
        try {
            val cacheFile = File(context.cacheDir, "cores_metadata.json")
            if (!cacheFile.exists()) {
                return null
            }
            
            val jsonText = cacheFile.readText()
            val jsonArray = JSONArray(jsonText)
            
            val metadataMap = mutableMapOf<String, CoreMetadata>()
            val abi = CoreDownloader.getDeviceABI()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val coreObj = jsonArray.getJSONObject(i)
                    val metadata = parseCoreMetadata(coreObj, abi)
                    if (metadata != null) {
                        metadataMap[metadata.id] = metadata
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing cached core at index $i: ${e.message}")
                }
            }
            
            Log.i(TAG, "Loaded ${metadataMap.size} cores from local cache")
            return metadataMap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local cache: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Obtient le timestamp du cache local
     */
    private fun getLocalCacheTimestamp(context: Context): Long {
        try {
            val timestampFile = File(context.cacheDir, "cores_metadata.timestamp")
            if (timestampFile.exists()) {
                return timestampFile.readText().toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache timestamp: ${e.message}", e)
        }
        return 0L
    }
    
    /**
     * Invalide le cache (force refresh au prochain fetch)
     */
    fun invalidateCache() {
        cacheLock.lock()
        try {
            cachedMetadata = null
            cacheTimestamp = 0
            Log.i(TAG, "Cache invalidated")
        } finally {
            cacheLock.unlock()
        }
    }
}

