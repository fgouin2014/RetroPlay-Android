package com.retroplay

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * GamelistCache - Cache en mémoire pour les gamelist.json
 * 
 * Précharge les gamelist.json existants pour accès rapide
 * Évite de relire les fichiers à chaque fois
 */
object GamelistCache {
    private const val TAG = "GamelistCache"
    
    // Cache en mémoire: consoleId -> JSON string
    private val cache = ConcurrentHashMap<String, String>()
    
    // Cache des GameEntry parsés: consoleId -> List<GameEntry>
    private val parsedCache = ConcurrentHashMap<String, List<GamelistManager.GameEntry>>()
    
    /**
     * Précharge tous les gamelist.json existants
     * Appelé pendant le splashscreen pour optimiser le lancement
     */
    fun preloadAllGamelists(context: Context, callback: PreloadCallback? = null) {
        val gamelibraryDir = File("/storage/emulated/0/GameLibrary-Data")
        
        if (!gamelibraryDir.exists() || !gamelibraryDir.isDirectory()) {
            Log.w(TAG, "GameLibrary-Data directory not found")
            callback?.onComplete(0)
            return
        }
        
        val consoleDirs = gamelibraryDir.listFiles { it.isDirectory } ?: run {
            callback?.onComplete(0)
            return
        }
        
        var loadedCount = 0
        var totalCount = 0
        
        // Compter les répertoires valides
        for (dir in consoleDirs) {
            val dirName = dir.name
            if (!dirName.startsWith(".") && 
                !com.retroplay.GamelistScanner.SYSTEM_DIRECTORIES.contains(dirName.lowercase())) {
                totalCount++
            }
        }
        
        Log.i(TAG, "Preloading gamelist.json for $totalCount consoles...")
        
        // Précharger en parallèle
        for (dir in consoleDirs) {
            val dirName = dir.name
            
            // Ignorer les répertoires système
            if (dirName.startsWith(".") || 
                com.retroplay.GamelistScanner.SYSTEM_DIRECTORIES.contains(dirName.lowercase())) {
                continue
            }
            
            // Précharger en arrière-plan
            Thread {
                try {
                    val gamelistFile = File(dir, "gamelist.json")
                    if (gamelistFile.exists()) {
                        val jsonContent = gamelistFile.readText()
                        cache[dirName] = jsonContent
                        
                        // Parser aussi pour cache supplémentaire
                        try {
                            val gamelist = GamelistManager.loadGamelist(dir)
                            if (gamelist != null) {
                                parsedCache[dirName] = gamelist.games
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing gamelist for $dirName: ${e.message}")
                        }
                        
                        synchronized(this) {
                            loadedCount++
                            callback?.onProgress(dirName, loadedCount, totalCount)
                            
                            if (loadedCount >= totalCount) {
                                Log.i(TAG, "Preload complete: $loadedCount/$totalCount gamelist.json loaded")
                                callback?.onComplete(loadedCount)
                            }
                        }
                    } else {
                        synchronized(this) {
                            loadedCount++
                            if (loadedCount >= totalCount) {
                                callback?.onComplete(loadedCount)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error preloading gamelist for $dirName: ${e.message}")
                    synchronized(this) {
                        loadedCount++
                        if (loadedCount >= totalCount) {
                            callback?.onComplete(loadedCount)
                        }
                    }
                }
            }.start()
        }
        
        // Si aucun répertoire valide, compléter immédiatement
        if (totalCount == 0) {
            callback?.onComplete(0)
        }
    }
    
    /**
     * Récupère un gamelist.json depuis le cache
     */
    fun getGamelistJson(consoleId: String): String? {
        return cache[consoleId]
    }
    
    /**
     * Récupère les GameEntry parsés depuis le cache
     */
    fun getGamelistEntries(consoleId: String): List<GamelistManager.GameEntry>? {
        return parsedCache[consoleId]
    }
    
    /**
     * Vérifie si un gamelist est en cache
     */
    fun isCached(consoleId: String): Boolean {
        return cache.containsKey(consoleId)
    }
    
    /**
     * Met à jour le cache pour une console
     */
    fun updateCache(consoleId: String, jsonContent: String) {
        cache[consoleId] = jsonContent
        
        // Parser aussi pour cache supplémentaire
        try {
            val consoleDir = File("/storage/emulated/0/GameLibrary-Data/$consoleId")
            val gamelist = GamelistManager.loadGamelist(consoleDir)
            if (gamelist != null) {
                parsedCache[consoleId] = gamelist.games
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing updated gamelist for $consoleId: ${e.message}")
        }
    }
    
    /**
     * Vide le cache
     */
    fun clearCache() {
        cache.clear()
        parsedCache.clear()
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * Callback pour le préchargement
     */
    interface PreloadCallback {
        fun onProgress(consoleName: String, current: Int, total: Int)
        fun onComplete(loadedCount: Int)
    }
}

