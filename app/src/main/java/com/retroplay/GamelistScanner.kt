package com.retroplay

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * GamelistScanner - Gestionnaire de scan automatique des gamelist.json
 * 
 * Fonctionnalités:
 * - Scan automatique au premier démarrage
 * - Scan des nouvelles ROMs aux démarrages suivants (toggle ON par défaut)
 * - Détection intelligente des répertoires de consoles
 * - Génération automatique de gamelist.json
 */
object GamelistScanner {
    private const val TAG = "GamelistScanner"
    private const val PREF_FIRST_LAUNCH = "first_launch_completed"
    private const val PREF_AUTO_SCAN_ENABLED = "auto_scan_enabled"
    private const val PREF_LAST_SCAN_TIME = "last_scan_time"
    
    // Répertoires système à ignorer
    private val SYSTEM_DIRECTORIES = setOf(
        "data", "emulatorjs", "vmnes", "playlists",
        "saves", "states", "cheats", "media",
        "overlays", "cores", "bios", ".cache"
    )
    
    /**
     * Vérifie si c'est le premier démarrage
     */
    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences("gamelist_prefs", Context.MODE_PRIVATE)
        return !prefs.getBoolean(PREF_FIRST_LAUNCH, false)
    }
    
    /**
     * Vérifie si le scan automatique est activé
     */
    fun isAutoScanEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("gamelist_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_AUTO_SCAN_ENABLED, true)  // ON par défaut
    }
    
    /**
     * Active ou désactive le scan automatique
     */
    fun setAutoScanEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("gamelist_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_AUTO_SCAN_ENABLED, enabled).apply()
        Log.i(TAG, "Auto-scan ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Scan tous les répertoires de consoles au premier démarrage
     * Génère gamelist.json pour tous les répertoires trouvés
     */
    suspend fun scanAllConsoles(context: Context, callback: ScanCallback? = null): ScanResult = withContext(Dispatchers.IO) {
        val gamelibraryDir = File("/storage/emulated/0/GameLibrary-Data")
        val scannedConsoles = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        if (!gamelibraryDir.exists() || !gamelibraryDir.isDirectory()) {
            Log.w(TAG, "GameLibrary-Data directory not found")
            return@withContext ScanResult(scannedConsoles, errors, 0)
        }
        
        val consoleDirs = gamelibraryDir.listFiles { it.isDirectory } 
            ?: return@withContext ScanResult(scannedConsoles, errors, 0)
        
        // Filtrer les répertoires système pour compter le total
        val validConsoleDirs = consoleDirs.filter { dir ->
            val dirName = dir.name
            !dirName.startsWith(".") && !SYSTEM_DIRECTORIES.contains(dirName.lowercase())
        }
        
        var totalGames = 0
        var currentIndex = 0
        
        // Notifier le début du scan
        callback?.onProgress(null, 0, validConsoleDirs.size, 0)
        
        for (consoleDir in consoleDirs) {
            val dirName = consoleDir.name
            
            // Ignorer les répertoires système
            if (dirName.startsWith(".") || SYSTEM_DIRECTORIES.contains(dirName.lowercase())) {
                continue
            }
            
            currentIndex++
            
            try {
                // Vérifier si gamelist.json existe
                val gamelistFile = File(consoleDir, "gamelist.json")
                val needsScan = !gamelistFile.exists()
                
                if (needsScan) {
                    Log.i(TAG, "Scanning console directory: $dirName")
                    
                    // Notifier la progression AVANT le scan
                    callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -3) // -3 = scan en cours
                    
                    // Normaliser l'ID de console pour les extensions
                    val consoleId = ConsoleNameMapper.normalizeToCanonical(dirName)
                    
                    // Générer le gamelist (peut prendre du temps)
                    // Utiliser dirName (nom réel du répertoire) pour le consoleId dans le gamelist
                    // afin que les chemins d'images pointent vers le bon répertoire
                    val gamelist = GamelistManager.generateGamelist(
                        consoleDir,
                        dirName, // Utiliser le nom réel du répertoire, pas le nom normalisé
                        includeMetadata = true
                    )
                    
                    if (gamelist.games.isNotEmpty()) {
                        // Sauvegarder le gamelist
                        val success = GamelistManager.saveGamelist(gamelist, consoleDir)
                        
                        if (success) {
                            scannedConsoles.add(dirName)
                            totalGames += gamelist.games.size
                            Log.i(TAG, "Generated gamelist for $dirName: ${gamelist.games.size} games")
                            
                            // Notifier la progression avec le nombre de jeux
                            callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, gamelist.games.size)
                        } else {
                            errors.add("$dirName: Failed to save gamelist")
                            callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -2) // -2 = erreur
                        }
                    } else {
                        Log.d(TAG, "No ROMs found in $dirName, skipping gamelist generation")
                        callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, 0)
                    }
                } else {
                    Log.d(TAG, "Gamelist already exists for $dirName, skipping")
                    callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -1) // -1 = déjà existant
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning $dirName: ${e.message}", e)
                errors.add("$dirName: ${e.message}")
                callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -2) // -2 = erreur
            }
        }
        
        // Marquer premier lancement comme terminé
        val prefs = context.getSharedPreferences("gamelist_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_FIRST_LAUNCH, true).apply()
        prefs.edit().putLong(PREF_LAST_SCAN_TIME, System.currentTimeMillis()).apply()
        
        Log.i(TAG, "Scan completed: ${scannedConsoles.size} consoles, $totalGames games total")
        
        // NOTE: Le scraping des métadonnées se fait maintenant manuellement via le bouton dans ConsoleManagerActivity
        // pour éviter de dépasser les limites de l'API ScreenScraper
        
        return@withContext ScanResult(scannedConsoles, errors, totalGames)
    }
    
    /**
     * Scan les nouvelles ROMs (démarrages suivants)
     * Génère ou met à jour gamelist.json seulement si des ROMs ont été modifiées
     */
    suspend fun scanNewRoms(context: Context, callback: ScanCallback? = null): ScanResult = withContext(Dispatchers.IO) {
        val gamelibraryDir = File("/storage/emulated/0/GameLibrary-Data")
        val scannedConsoles = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        if (!gamelibraryDir.exists() || !gamelibraryDir.isDirectory()) {
            return@withContext ScanResult(scannedConsoles, errors, 0)
        }
        
        val consoleDirs = gamelibraryDir.listFiles { it.isDirectory } 
            ?: return@withContext ScanResult(scannedConsoles, errors, 0)
        
        // Filtrer les répertoires système pour compter le total
        val validConsoleDirs = consoleDirs.filter { dir ->
            val dirName = dir.name
            !dirName.startsWith(".") && !SYSTEM_DIRECTORIES.contains(dirName.lowercase())
        }
        
        var totalGames = 0
        var currentIndex = 0
        
        // Notifier le début du scan
        callback?.onProgress(null, 0, validConsoleDirs.size, 0)
        
        for (consoleDir in consoleDirs) {
            val dirName = consoleDir.name
            
            // Ignorer les répertoires système
            if (dirName.startsWith(".") || SYSTEM_DIRECTORIES.contains(dirName.lowercase())) {
                continue
            }
            
            currentIndex++
            
            try {
                val gamelistFile = File(consoleDir, "gamelist.json")
                
                // Si gamelist.json existe, ajouter seulement les nouvelles ROMs (ne pas regénérer)
                if (gamelistFile.exists()) {
                    Log.i(TAG, "Gamelist exists for $dirName, checking for new ROMs only")
                    
                    // Notifier la progression AVANT le scan
                    callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -3) // -3 = scan en cours
                    
                    // Ajouter seulement les nouvelles ROMs
                    val newRomsCount = GamelistManager.addNewRomsToGamelist(consoleDir, dirName)
                    
                    if (newRomsCount > 0) {
                        scannedConsoles.add(dirName)
                        totalGames += newRomsCount
                        Log.i(TAG, "Added $newRomsCount new ROMs to gamelist for $dirName")
                        callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, newRomsCount)
                    } else {
                        callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -1) // -1 = pas de nouvelles ROMs
                    }
                } else {
                    // Pas de gamelist.json: générer un nouveau (première fois ou copié depuis PC)
                    Log.i(TAG, "No gamelist.json found for $dirName, generating new one")
                    
                    // Notifier la progression AVANT le scan
                    callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -3) // -3 = scan en cours
                    
                    // Normaliser l'ID de console pour les extensions
                    val consoleId = ConsoleNameMapper.normalizeToCanonical(dirName)
                    
                    // Générer le gamelist (peut prendre du temps)
                    val gamelist = GamelistManager.generateGamelist(
                        consoleDir,
                        dirName, // Utiliser le nom réel du répertoire
                        includeMetadata = true
                    )
                    
                    if (gamelist.games.isNotEmpty()) {
                        // Sauvegarder le gamelist
                        val success = GamelistManager.saveGamelist(gamelist, consoleDir)
                        
                        if (success) {
                            scannedConsoles.add(dirName)
                            totalGames += gamelist.games.size
                            Log.i(TAG, "Generated gamelist for $dirName: ${gamelist.games.size} games")
                            callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, gamelist.games.size)
                        } else {
                            errors.add("$dirName: Failed to save gamelist")
                            callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -2) // -2 = erreur
                        }
                    } else {
                        callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescanning $dirName: ${e.message}", e)
                errors.add("$dirName: ${e.message}")
                callback?.onProgress(dirName, currentIndex - 1, validConsoleDirs.size, -2) // -2 = erreur
            }
        }
        
        // Mettre à jour le timestamp du dernier scan
        val prefs = context.getSharedPreferences("gamelist_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(PREF_LAST_SCAN_TIME, System.currentTimeMillis()).apply()
        
        if (scannedConsoles.isNotEmpty()) {
            Log.i(TAG, "Rescan completed: ${scannedConsoles.size} consoles updated, $totalGames games total")
            // NOTE: Le scraping des métadonnées se fait maintenant manuellement via le bouton dans ConsoleManagerActivity
            // pour éviter de dépasser les limites de l'API ScreenScraper
        }
        
        return@withContext ScanResult(scannedConsoles, errors, totalGames)
    }
    
    /**
     * Vérifie si un répertoire doit être rescanné
     * Retourne true si:
     * - Gamelist.json n'existe pas
     * - Des fichiers ROM ont été modifiés après la génération du gamelist
     */
    private fun shouldRescan(consoleDir: File, gamelistFile: File): Boolean {
        // Si pas de gamelist, il faut scanner
        if (!gamelistFile.exists()) {
            return true
        }
        
        val gamelistTime = gamelistFile.lastModified()
        
        // Trouver tous les fichiers ROM dans le répertoire
        val romFiles = findRomFiles(consoleDir)
        
        // Vérifier si des ROMs ont été modifiées après la génération du gamelist
        return romFiles.any { it.lastModified() > gamelistTime }
    }
    
    /**
     * Trouve tous les fichiers ROM dans un répertoire (récursif)
     */
    private fun findRomFiles(dir: File): List<File> {
        val romFiles = mutableListOf<File>()
        
        if (!dir.exists() || !dir.isDirectory()) {
            return romFiles
        }
        
        try {
            // Obtenir l'ID canonique pour les extensions
            val dirName = dir.name
            val consoleId = ConsoleNameMapper.normalizeToCanonical(dirName)
            val extensions = GamelistManager.getDefaultExtensions(consoleId)
            
            // Scanner récursivement (max 2 niveaux pour éviter trop de profondeur)
            dir.walkTopDown().maxDepth(2).forEach { file ->
                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (extensions.any { it.removePrefix(".") == ext }) {
                        romFiles.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding ROM files in ${dir.name}: ${e.message}")
        }
        
        return romFiles
    }
    
    /**
     * Résultat d'un scan
     */
    data class ScanResult(
        val scannedConsoles: List<String>,
        val errors: List<String>,
        val totalGames: Int
    )
    
    /**
     * Méthode helper pour appeler depuis Java
     * Scan complet au premier démarrage
     */
    @JvmStatic
    fun scanAllConsolesAsync(context: Context, callback: ScanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = scanAllConsoles(context, callback)
                callback.onComplete(result)
            } catch (e: Exception) {
                callback.onError(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Méthode helper pour appeler depuis Java
     * Scan des nouvelles ROMs
     */
    @JvmStatic
    fun scanNewRomsAsync(context: Context, callback: ScanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = scanNewRoms(context, callback)
                callback.onComplete(result)
            } catch (e: Exception) {
                callback.onError(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Lance le scraping asynchrone en arrière-plan pour enrichir les métadonnées
     * des gamelist.json déjà générés
     */
    private fun launchMetadataEnrichment(context: Context, scannedConsoles: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Starting background metadata enrichment for ${scannedConsoles.size} consoles")
            
            val gamelibraryDir = File("/storage/emulated/0/GameLibrary-Data")
            for (consoleName in scannedConsoles) {
                try {
                    val consoleDir = File(gamelibraryDir, consoleName)
                    if (consoleDir.exists() && consoleDir.isDirectory) {
                        // Lancer l'enrichissement en arrière-plan (non-bloquant)
                        GamelistManager.enrichGamelistWithMetadata(consoleDir, consoleName)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching metadata enrichment for $consoleName: ${e.message}")
                }
            }
            
            Log.i(TAG, "Background metadata enrichment started for all scanned consoles")
        }
    }
}

