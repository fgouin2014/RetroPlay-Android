# 🔍 AUDIT FUTURE-PROOF - RetroPlay Android
## Méthodologie "Nos Rules"

**Date:** 19 novembre 2025  
**Objectif:** Rendre future-proof la gestion des cores, répertoires, cache ZIP et options de core  
**Principe:** Suivre les spécifications officielles RetroArch/Libretro, pas de simplifications arbitraires

---

## 📋 TABLE DES MATIÈRES

1. [Gestion/Installation des Cores](#1-gestioninstallation-des-cores)
2. [Gestion des Répertoires/Gamelist](#2-gestion-des-répertoiresgamelist)
3. [Ajout de Répertoires de ROM par l'Utilisateur](#3-ajout-de-répertoires-de-rom-par-lutilisateur)
4. [Cache ZIP](#4-cache-zip)
5. [Options de Core](#5-options-de-core)

---

## 1. GESTION/INSTALLATION DES CORES

### 🔴 PROBLÈMES IDENTIFIÉS

#### 1.1 Liste Hardcodée de Cores
**Fichier:** `CoreDownloader.kt` lignes 37-77

```kotlin
fun getAvailableCores(): List<CoreInfo> {
    return listOf(
        CoreInfo("fceumm", "fceumm_libretro_android.so", "Nintendo - NES / Famicom (FCEUmm)"),
        // ... 30+ cores hardcodés
    )
}
```

**Problèmes:**
- ❌ Liste statique, nécessite modification du code pour nouveaux cores
- ❌ Pas de détection automatique depuis `cores.json`
- ❌ Pas de versioning (nightly vs stable)
- ❌ Pas de vérification de compatibilité ABI
- ❌ Pas de gestion des mises à jour

#### 1.2 URL Buildbot Hardcodée
**Fichier:** `CoreDownloader.kt` ligne 19

```kotlin
private const val BUILDBOT_BASE_URL = "https://buildbot.libretro.com/nightly/android"
```

**Problèmes:**
- ❌ Toujours "nightly", pas de choix stable/nightly
- ❌ Pas de fallback si buildbot down
- ❌ Pas de cache local des métadonnées

#### 1.3 Pas de Vérification d'Intégrité
**Fichier:** `CoreDownloader.kt` lignes 94-169

**Problèmes:**
- ❌ Pas de checksum SHA256 (buildbot fournit)
- ❌ Pas de vérification de signature
- ❌ Pas de rollback si installation corrompue

#### 1.4 Pas de Gestion de Versions
**Problèmes:**
- ❌ Pas de tracking de version installée
- ❌ Pas de comparaison version installée vs disponible
- ❌ Pas de système de mise à jour automatique

#### 1.5 Pas de Support Multi-ABI
**Fichier:** `CoreDownloader.kt` lignes 22-32

```kotlin
fun getDeviceABI(): String {
    // Retourne seulement arm64-v8a ou armeabi-v7a
    // Pas de support x86, x86_64
}
```

**Problèmes:**
- ❌ Pas de support émulateurs Android (x86/x86_64)
- ❌ Pas de détection automatique de l'ABI optimal

---

### ✅ SOLUTIONS PROPOSÉES (Nos Rules)

#### Solution 1.1: Détection Automatique depuis `cores.json`

**Référence:** `buildbot.libretro.com/nightly/android/latest/cores.json`

```kotlin
data class CoreMetadata(
    val id: String,
    val display_name: String,
    val core_name: String,
    val system_name: String,
    val system_id: String,
    val manufacturer: String,
    val license: String,
    val display_version: String,
    val supports_no_game: Boolean,
    val database: String?,
    val required_hw_api: List<String>,
    val firmware_count: Int,
    val notes: String?,
    val categories: List<String>
)

object CoreMetadataManager {
    private const val CORES_JSON_URL = "https://buildbot.libretro.com/nightly/android/latest/cores.json"
    private val cacheFile = File(context.cacheDir, "cores_metadata.json")
    
    suspend fun fetchCoresMetadata(): Map<String, CoreMetadata> {
        // 1. Vérifier cache (24h max)
        if (cacheFile.exists() && cacheFile.lastModified() > System.currentTimeMillis() - 86400000) {
            return loadFromCache()
        }
        
        // 2. Télécharger depuis buildbot
        val json = downloadJson(CORES_JSON_URL)
        val cores = parseCoresJson(json)
        
        // 3. Sauvegarder en cache
        saveToCache(cores)
        
        return cores
    }
    
    fun getCoresForABI(abi: String): List<CoreMetadata> {
        // Filtrer selon ABI supporté
        return coresMetadata.values.filter { core ->
            // Vérifier si core disponible pour cet ABI
            checkABISupport(core, abi)
        }
    }
}
```

**Avantages:**
- ✅ Détection automatique de tous les cores disponibles
- ✅ Pas de hardcoding
- ✅ Support de nouveaux cores sans modification du code
- ✅ Métadonnées complètes (version, license, firmware)

#### Solution 1.2: Système de Versioning

```kotlin
data class CoreVersion(
    val coreId: String,
    val installedVersion: String?,
    val availableVersion: String,
    val updateAvailable: Boolean,
    val changelog: String?
)

object CoreVersionManager {
    fun checkForUpdates(context: Context): List<CoreVersion> {
        val installedCores = getInstalledCores(context)
        val availableCores = CoreMetadataManager.fetchCoresMetadata()
        
        return installedCores.map { installed ->
            val available = availableCores[installed.id]
            CoreVersion(
                coreId = installed.id,
                installedVersion = installed.version,
                availableVersion = available?.display_version ?: "unknown",
                updateAvailable = installed.version != available?.display_version,
                changelog = null // TODO: fetch from buildbot
            )
        }
    }
}
```

#### Solution 1.3: Vérification d'Intégrité

**Référence:** Buildbot fournit `SHA256` checksums

```kotlin
object CoreIntegrityManager {
    suspend fun verifyCore(coreFile: File, expectedSHA256: String): Boolean {
        val actualSHA256 = calculateSHA256(coreFile)
        return actualSHA256.equals(expectedSHA256, ignoreCase = true)
    }
    
    suspend fun downloadCoreWithVerification(
        context: Context,
        coreInfo: CoreInfo,
        progressCallback: ((Int, String) -> Unit)?
    ): Boolean {
        // 1. Télécharger .so
        val coreFile = downloadCore(context, coreInfo, progressCallback)
        
        // 2. Télécharger checksum
        val checksum = downloadChecksum(coreInfo)
        
        // 3. Vérifier intégrité
        if (!verifyCore(coreFile, checksum)) {
            Log.e(TAG, "Core integrity check failed for ${coreInfo.id}")
            coreFile.delete()
            return false
        }
        
        return true
    }
}
```

#### Solution 1.4: Support Stable/Nightly

```kotlin
enum class BuildType {
    STABLE,
    NIGHTLY
}

object CoreDownloader {
    private var buildType = BuildType.NIGHTLY // Par défaut
    
    fun setBuildType(type: BuildType) {
        buildType = type
    }
    
    private fun getBuildbotBaseUrl(): String {
        return when (buildType) {
            BuildType.STABLE -> "https://buildbot.libretro.com/stable/android"
            BuildType.NIGHTLY -> "https://buildbot.libretro.com/nightly/android"
        }
    }
}
```

#### Solution 1.5: Support Multi-ABI

```kotlin
fun getOptimalABI(): String {
    val supportedABIs = Build.SUPPORTED_ABIS
    
    // Priorité: arm64-v8a > armeabi-v7a > x86_64 > x86
    return when {
        supportedABIs.contains("arm64-v8a") -> "arm64-v8a"
        supportedABIs.contains("armeabi-v7a") -> "armeabi-v7a"
        supportedABIs.contains("x86_64") -> "x86_64"
        supportedABIs.contains("x86") -> "x86"
        else -> "armeabi-v7a" // Fallback
    }
}
```

---

## 2. GESTION DES RÉPERTOIRES/GAMELIST

### 🔴 PROBLÈMES IDENTIFIÉS

#### 2.1 Découverte Hardcodée des Répertoires
**Fichier:** `GameListActivity.java` lignes 1697-1718

```java
// Ignorer les répertoires système
if (dirName.equals("data") || dirName.equals("emulatorjs") || 
    dirName.equals("vmnes") || dirName.equals("playlists") ||
    dirName.equals("saves") || dirName.equals(".cache") ||
    dirName.equals("cheats") || dirName.equals("media")) {
    continue;
}
```

**Problèmes:**
- ❌ Liste de répertoires système hardcodée
- ❌ Pas de configuration utilisateur
- ❌ Pas de support de répertoires personnalisés
- ❌ Pas de validation de structure

#### 2.2 Gamelist.json Non Standardisé
**Fichier:** `WebServer.java` lignes 901-924

**Problèmes:**
- ❌ Pas de validation du format JSON
- ❌ Pas de schéma défini
- ❌ Auto-génération basique (pas de métadonnées)
- ❌ Pas de synchronisation avec base de données

#### 2.3 Pas de Support Multi-Répertoires
**Problèmes:**
- ❌ Un seul répertoire par console
- ❌ Pas de fusion de plusieurs sources
- ❌ Pas de priorité de répertoires

#### 2.4 Pas de Gestion de Conflits
**Problèmes:**
- ❌ Pas de détection de ROMs dupliquées
- ❌ Pas de résolution automatique
- ❌ Pas de tracking de sources

---

### ✅ SOLUTIONS PROPOSÉES (Nos Rules)

#### Solution 2.1: Configuration Flexible des Répertoires

**Référence:** RetroArch utilise `playlists/` et `content_database/`

```kotlin
data class ConsoleDirectoryConfig(
    val consoleId: String,
    val directories: List<String>, // Multi-répertoires supportés
    val priority: Int, // Ordre de priorité
    val autoScan: Boolean, // Auto-générer gamelist.json
    val excludePatterns: List<String> // Patterns à exclure
)

object DirectoryManager {
    private val configFile = File("/storage/emulated/0/RetroPlay-Data/config/directories.json")
    
    fun loadDirectoryConfigs(): Map<String, ConsoleDirectoryConfig> {
        if (!configFile.exists()) {
            return createDefaultConfigs()
        }
        
        return parseDirectoryConfigs(configFile)
    }
    
    fun addUserDirectory(consoleId: String, directoryPath: String) {
        val configs = loadDirectoryConfigs().toMutableMap()
        val config = configs[consoleId] ?: ConsoleDirectoryConfig(
            consoleId = consoleId,
            directories = emptyList(),
            priority = 0,
            autoScan = true,
            excludePatterns = getDefaultExcludePatterns()
        )
        
        configs[consoleId] = config.copy(
            directories = config.directories + directoryPath
        )
        
        saveDirectoryConfigs(configs)
    }
    
    private fun getDefaultExcludePatterns(): List<String> {
        return listOf(
            "data", "emulatorjs", "vmnes", "playlists",
            "saves", ".cache", "cheats", "media",
            "states", "screenshots", "thumbnails"
        )
    }
}
```

#### Solution 2.2: Format Gamelist Standardisé

**Référence:** Format RetroArch `playlists/*.lpl` et `content_database/`

```kotlin
data class GameEntry(
    val path: String,
    val label: String,
    val core_path: String?,
    val core_name: String?,
    val crc32: String?,
    val db_name: String?,
    val size: Long?,
    val serial: String?,
    val md5: String?,
    val sha1: String?
)

data class Gamelist(
    val version: String = "1.0",
    val console: String,
    val games: List<GameEntry>,
    val metadata: GamelistMetadata?
)

object GamelistManager {
    fun loadGamelist(consoleId: String): Gamelist? {
        // 1. Chercher dans répertoires configurés
        val directories = DirectoryManager.getDirectoriesForConsole(consoleId)
        
        for (directory in directories) {
            val gamelistFile = File(directory, "gamelist.json")
            if (gamelistFile.exists()) {
                return parseGamelist(gamelistFile)
            }
        }
        
        // 2. Auto-générer si autoScan activé
        if (shouldAutoScan(consoleId)) {
            return generateGamelist(consoleId, directories)
        }
        
        return null
    }
    
    fun generateGamelist(consoleId: String, directories: List<String>): Gamelist {
        val games = mutableListOf<GameEntry>()
        
        for (directory in directories) {
            val dir = File(directory)
            if (!dir.exists()) continue
            
            dir.listFiles()?.forEach { file ->
                if (isValidRomFile(file, consoleId)) {
                    games.add(createGameEntry(file, consoleId))
                }
            }
        }
        
        return Gamelist(
            console = consoleId,
            games = games,
            metadata = GamelistMetadata(
                generatedAt = System.currentTimeMillis(),
                sourceDirectories = directories
            )
        )
    }
}
```

#### Solution 2.3: Détection de Conflits

```kotlin
object ConflictResolver {
    data class Conflict(
        val gameName: String,
        val duplicates: List<GameEntry>,
        val resolution: ConflictResolution
    )
    
    enum class ConflictResolution {
        KEEP_FIRST,
        KEEP_LATEST,
        KEEP_LARGEST,
        MANUAL
    }
    
    fun detectConflicts(games: List<GameEntry>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        
        // Grouper par nom de jeu
        val grouped = games.groupBy { it.label }
        
        grouped.forEach { (name, entries) ->
            if (entries.size > 1) {
                conflicts.add(Conflict(
                    gameName = name,
                    duplicates = entries,
                    resolution = ConflictResolution.KEEP_FIRST // Par défaut
                ))
            }
        }
        
        return conflicts
    }
    
    fun resolveConflicts(conflicts: List<Conflict>): List<GameEntry> {
        return conflicts.flatMap { conflict ->
            when (conflict.resolution) {
                ConflictResolution.KEEP_FIRST -> listOf(conflict.duplicates.first())
                ConflictResolution.KEEP_LATEST -> {
                    listOf(conflict.duplicates.maxByOrNull { it.path } ?: conflict.duplicates.first())
                }
                ConflictResolution.KEEP_LARGEST -> {
                    listOf(conflict.duplicates.maxByOrNull { it.size ?: 0L } ?: conflict.duplicates.first())
                }
                ConflictResolution.MANUAL -> conflict.duplicates // L'utilisateur choisit
            }
        }
    }
}
```

---

## 3. AJOUT DE RÉPERTOIRES DE ROM PAR L'UTILISATEUR

### 🔴 PROBLÈMES IDENTIFIÉS

#### 3.1 Pas d'Interface Utilisateur
**Problèmes:**
- ❌ Pas de dialog pour ajouter un répertoire
- ❌ Pas de sélection de répertoire via SAF (Storage Access Framework)
- ❌ Pas de validation du répertoire

#### 3.2 Pas de Persistance
**Problèmes:**
- ❌ Pas de sauvegarde des répertoires utilisateur
- ❌ Pas de restauration après réinstallation
- ❌ Pas de synchronisation

#### 3.3 Pas de Permissions Gérées
**Problèmes:**
- ❌ Pas de gestion des permissions Storage
- ❌ Pas de support Android 11+ (Scoped Storage)
- ❌ Pas de fallback si permission refusée

---

### ✅ SOLUTIONS PROPOSÉES (Nos Rules)

#### Solution 3.1: Interface Utilisateur Complète

```kotlin
@Composable
fun AddDirectoryDialog(
    consoleId: String,
    onDirectoryAdded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPath by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ROM Directory") },
        text = {
            Column {
                Text("Select a directory containing ROMs for $consoleId")
                
                Button(onClick = { 
                    // Ouvrir SAF (Storage Access Framework)
                    openDirectoryPicker(consoleId) { path ->
                        selectedPath = path
                    }
                }) {
                    Text("Browse...")
                }
                
                if (selectedPath != null) {
                    Text("Selected: $selectedPath")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedPath?.let { path ->
                        if (validateDirectory(path, consoleId)) {
                            DirectoryManager.addUserDirectory(consoleId, path)
                            onDirectoryAdded(path)
                            onDismiss()
                        }
                    }
                },
                enabled = selectedPath != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun validateDirectory(path: String, consoleId: String): Boolean {
    val dir = File(path)
    
    // Vérifier existence
    if (!dir.exists() || !dir.isDirectory) {
        return false
    }
    
    // Vérifier permissions
    if (!dir.canRead()) {
        return false
    }
    
    // Vérifier contenu (au moins un fichier ROM valide)
    val extensions = getValidExtensions(consoleId)
    return dir.listFiles()?.any { file ->
        extensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) }
    } ?: false
}
```

#### Solution 3.2: Persistance avec Backup

```kotlin
object DirectoryPersistenceManager {
    private const val BACKUP_FILE = "/storage/emulated/0/RetroPlay-Data/config/directories_backup.json"
    
    fun saveDirectories(configs: Map<String, ConsoleDirectoryConfig>) {
        // Sauvegarder dans config/
        val configFile = File("/storage/emulated/0/RetroPlay-Data/config/directories.json")
        configFile.writeText(Json.encodeToString(configs))
        
        // Backup
        val backupFile = File(BACKUP_FILE)
        backupFile.writeText(Json.encodeToString(configs))
    }
    
    fun restoreDirectories(): Map<String, ConsoleDirectoryConfig>? {
        // Essayer config principal
        val configFile = File("/storage/emulated/0/RetroPlay-Data/config/directories.json")
        if (configFile.exists()) {
            return Json.decodeFromString<Map<String, ConsoleDirectoryConfig>>(
                configFile.readText()
            )
        }
        
        // Fallback sur backup
        val backupFile = File(BACKUP_FILE)
        if (backupFile.exists()) {
            return Json.decodeFromString<Map<String, ConsoleDirectoryConfig>>(
                backupFile.readText()
            )
        }
        
        return null
    }
}
```

---

## 4. CACHE ZIP

### 🔴 PROBLÈMES IDENTIFIÉS

#### 4.1 Pas de Gestion de Taille
**Fichier:** `GameDetailsActivity.java` lignes 720-946

**Problèmes:**
- ❌ Cache illimité (peut remplir le stockage)
- ❌ Pas de nettoyage automatique
- ❌ Pas de LRU (Least Recently Used)
- ❌ Pas de limite par console

#### 4.2 Pas de Vérification de Validité
**Problèmes:**
- ❌ Pas de vérification si fichier source modifié
- ❌ Pas de checksum
- ❌ Cache corrompu peut rester

#### 4.3 Pas de Gestion Multi-Thread
**Problèmes:**
- ❌ Extraction simultanée peut causer conflits
- ❌ Pas de verrous
- ❌ Pas de queue d'extraction

#### 4.4 Pas de Support 7z Optimisé
**Fichier:** `GameDetailsActivity.java` lignes 865-891

**Problèmes:**
- ❌ Extraction complète en mémoire (pas de streaming)
- ❌ Pas de compression pour cache
- ❌ Pas de support multi-volume

---

### ✅ SOLUTIONS PROPOSÉES (Nos Rules)

#### Solution 4.1: Gestion de Taille avec LRU

**Référence:** Android `DiskLruCache` pattern

```kotlin
object ZipCacheManager {
    private const val MAX_CACHE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB
    private const val MAX_CACHE_SIZE_PER_CONSOLE = 500L * 1024 * 1024 // 500MB par console
    
    data class CacheEntry(
        val file: File,
        val console: String,
        val originalPath: String,
        val originalSize: Long,
        val extractedAt: Long,
        val lastAccessed: Long,
        val accessCount: Int
    )
    
    private val cacheEntries = mutableMapOf<String, CacheEntry>()
    private val lock = ReentrantLock()
    
    fun getCachedRom(originalPath: String, console: String): File? {
        lock.lock()
        try {
            val entry = cacheEntries[originalPath]
            
            if (entry != null && entry.file.exists()) {
                // Vérifier validité
                if (isCacheValid(entry, originalPath)) {
                    // Mettre à jour accès
                    cacheEntries[originalPath] = entry.copy(
                        lastAccessed = System.currentTimeMillis(),
                        accessCount = entry.accessCount + 1
                    )
                    return entry.file
                } else {
                    // Cache invalide, supprimer
                    entry.file.delete()
                    cacheEntries.remove(originalPath)
                }
            }
            
            return null
        } finally {
            lock.unlock()
        }
    }
    
    fun cacheRom(originalPath: String, console: String, extractedFile: File) {
        lock.lock()
        try {
            // Vérifier taille totale
            val totalSize = cacheEntries.values.sumOf { it.file.length() }
            if (totalSize + extractedFile.length() > MAX_CACHE_SIZE) {
                // Nettoyer LRU
                cleanupLRU(extractedFile.length())
            }
            
            // Vérifier taille par console
            val consoleSize = cacheEntries.values
                .filter { it.console == console }
                .sumOf { it.file.length() }
            
            if (consoleSize + extractedFile.length() > MAX_CACHE_SIZE_PER_CONSOLE) {
                cleanupConsoleLRU(console, extractedFile.length())
            }
            
            // Ajouter entrée
            cacheEntries[originalPath] = CacheEntry(
                file = extractedFile,
                console = console,
                originalPath = originalPath,
                originalSize = File(originalPath).length(),
                extractedAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis(),
                accessCount = 1
            )
        } finally {
            lock.unlock()
        }
    }
    
    private fun cleanupLRU(requiredSpace: Long) {
        val sorted = cacheEntries.values.sortedBy { it.lastAccessed }
        var freed = 0L
        
        for (entry in sorted) {
            if (freed >= requiredSpace) break
            
            entry.file.delete()
            cacheEntries.remove(entry.originalPath)
            freed += entry.file.length()
        }
    }
    
    private fun isCacheValid(entry: CacheEntry, originalPath: String): Boolean {
        val originalFile = File(originalPath)
        
        // Vérifier existence original
        if (!originalFile.exists()) {
            return false
        }
        
        // Vérifier modification (si original modifié, cache invalide)
        if (originalFile.lastModified() > entry.extractedAt) {
            return false
        }
        
        // Vérifier taille
        if (originalFile.length() != entry.originalSize) {
            return false
        }
        
        return true
    }
}
```

#### Solution 4.2: Queue d'Extraction

```kotlin
object ExtractionQueue {
    private val extractionQueue = LinkedBlockingQueue<ExtractionTask>()
    private val executor = Executors.newFixedThreadPool(2) // 2 extractions simultanées max
    
    data class ExtractionTask(
        val originalPath: String,
        val console: String,
        val targetExtension: String,
        val callback: (File?) -> Unit
    )
    
    init {
        // Démarrer workers
        repeat(2) {
            executor.submit {
                processExtractionQueue()
            }
        }
    }
    
    fun enqueue(task: ExtractionTask) {
        extractionQueue.offer(task)
    }
    
    private fun processExtractionQueue() {
        while (true) {
            try {
                val task = extractionQueue.take()
                extractRom(task)
            } catch (e: InterruptedException) {
                break
            }
        }
    }
    
    private fun extractRom(task: ExtractionTask) {
        // Extraction avec verrou
        val lockFile = File("${task.originalPath}.lock")
        
        try {
            if (lockFile.createNewFile()) {
                // Extraction...
                val extracted = performExtraction(task)
                task.callback(extracted)
            } else {
                // Attendre que l'autre thread termine
                while (lockFile.exists()) {
                    Thread.sleep(100)
                }
                // Réessayer
                val cached = ZipCacheManager.getCachedRom(task.originalPath, task.console)
                task.callback(cached)
            }
        } finally {
            lockFile.delete()
        }
    }
}
```

---

## 5. OPTIONS DE CORE

### 🔴 PROBLÈMES IDENTIFIÉS

#### 5.1 Pas de Validation des Options
**Fichier:** `CoreConfigManager.kt` lignes 39-73

**Problèmes:**
- ❌ Pas de validation des valeurs
- ❌ Pas de vérification des types (int, bool, enum)
- ❌ Pas de valeurs par défaut depuis core
- ❌ Pas de documentation des options

#### 5.2 Pas de Support Per-Game
**Problèmes:**
- ❌ Options partagées par tous les jeux du core
- ❌ Pas de override per-game
- ❌ Pas de profils de configuration

#### 5.3 Pas de Synchronisation avec Core
**Problèmes:**
- ❌ Pas de rechargement après modification
- ❌ Pas de détection de changements
- ❌ Pas de rollback

#### 5.4 Pas de Support des Options Avancées
**Fichier:** `CoreVariableManager.kt`

**Problèmes:**
- ❌ Pas de support des ranges (min/max)
- ❌ Pas de support des catégories
- ❌ Pas de support des dépendances entre options

---

### ✅ SOLUTIONS PROPOSÉES (Nos Rules)

#### Solution 5.1: Validation et Schéma

**Référence:** Format RetroArch core options avec validation

```kotlin
data class CoreOptionSchema(
    val key: String,
    val description: String,
    val info: String?,
    val type: OptionType,
    val default: String,
    val values: List<OptionValue>?,
    val min: Int?,
    val max: Int?,
    val category: String?
)

enum class OptionType {
    BOOL,
    INT,
    FLOAT,
    STRING,
    ENUM
}

data class OptionValue(
    val value: String,
    val label: String
)

object CoreOptionValidator {
    fun validateOption(
        schema: CoreOptionSchema,
        value: String
    ): ValidationResult {
        return when (schema.type) {
            OptionType.BOOL -> {
                if (value in listOf("enabled", "disabled")) {
                    ValidationResult.Valid
                } else {
                    ValidationResult.Invalid("Boolean must be 'enabled' or 'disabled'")
                }
            }
            
            OptionType.INT -> {
                val intValue = value.toIntOrNull()
                if (intValue != null) {
                    if (schema.min != null && intValue < schema.min) {
                        ValidationResult.Invalid("Value must be >= ${schema.min}")
                    } else if (schema.max != null && intValue > schema.max) {
                        ValidationResult.Invalid("Value must be <= ${schema.max}")
                    } else {
                        ValidationResult.Valid
                    }
                } else {
                    ValidationResult.Invalid("Value must be an integer")
                }
            }
            
            OptionType.ENUM -> {
                if (schema.values?.any { it.value == value } == true) {
                    ValidationResult.Valid
                } else {
                    ValidationResult.Invalid("Value must be one of: ${schema.values?.joinToString { it.value }}")
                }
            }
            
            else -> ValidationResult.Valid // Pas de validation pour STRING/FLOAT pour l'instant
        }
    }
}
```

#### Solution 5.2: Support Per-Game

**Référence:** RetroArch `content_database/` et per-game configs

```kotlin
object PerGameConfigManager {
    private fun getPerGameConfigFile(gamePath: String, coreName: String): File {
        // Utiliser CRC32 du jeu comme identifiant
        val gameCrc = calculateCRC32(File(gamePath))
        val configDir = File("/storage/emulated/0/RetroPlay-Data/config/games")
        return File(configDir, "${coreName}_${gameCrc}.cfg")
    }
    
    fun getOptionForGame(
        context: Context,
        coreName: String,
        optionName: String,
        gamePath: String,
        defaultValue: String
    ): String {
        // 1. Chercher dans config per-game
        val perGameFile = getPerGameConfigFile(gamePath, coreName)
        if (perGameFile.exists()) {
            val perGameConfig = CoreConfigManager.loadConfig(context, perGameFile.nameWithoutExtension)
            perGameConfig[optionName]?.let { return it }
        }
        
        // 2. Fallback sur config globale du core
        val globalConfig = CoreConfigManager.loadConfig(context, coreName)
        return globalConfig[optionName] ?: defaultValue
    }
    
    fun setOptionForGame(
        context: Context,
        coreName: String,
        optionName: String,
        value: String,
        gamePath: String
    ) {
        val perGameFile = getPerGameConfigFile(gamePath, coreName)
        val config = if (perGameFile.exists()) {
            CoreConfigManager.loadConfig(context, perGameFile.nameWithoutExtension).toMutableMap()
        } else {
            mutableMapOf()
        }
        
        config[optionName] = value
        CoreConfigManager.saveConfig(context, perGameFile.nameWithoutExtension, config)
    }
}
```

#### Solution 5.3: Hot Reload des Options

```kotlin
object CoreOptionHotReload {
    private val configWatchers = mutableMapOf<String, FileObserver>()
    
    fun watchConfig(coreName: String, onChanged: () -> Unit) {
        val configFile = CoreConfigManager.getCoreConfigFile(context, coreName)
        val parentDir = configFile.parentFile ?: return
        
        val observer = object : FileObserver(parentDir.absolutePath, FileObserver.MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == configFile.name) {
                    onChanged()
                }
            }
        }
        
        observer.startWatching()
        configWatchers[coreName] = observer
    }
    
    fun unwatchConfig(coreName: String) {
        configWatchers[coreName]?.stopWatching()
        configWatchers.remove(coreName)
    }
}
```

---

## 📊 RÉSUMÉ DES PRIORITÉS

### 🥇 PRIORITÉ 1: Quick Wins (Impact Immédiat)

1. **Détection automatique depuis `cores.json`** (4-6h)
   - Impact: ✅ Support automatique de nouveaux cores
   - Effort: Moyen
   
2. **Gestion de taille du cache ZIP** (3-4h)
   - Impact: ✅ Évite remplissage du stockage
   - Effort: Faible

3. **Interface ajout répertoire utilisateur** (2-3h)
   - Impact: ✅ Feature demandée
   - Effort: Faible

### 🥈 PRIORITÉ 2: Améliorations Stabilité (Moyen Terme)

4. **Validation des options de core** (4-5h)
   - Impact: ✅ Évite configurations invalides
   - Effort: Moyen

5. **Support per-game config** (5-6h)
   - Impact: ✅ Flexibilité utilisateur
   - Effort: Moyen

6. **Vérification intégrité cores** (3-4h)
   - Impact: ✅ Sécurité
   - Effort: Faible

### 🥉 PRIORITÉ 3: Features Avancées (Long Terme)

7. **Système de versioning cores** (6-8h)
   - Impact: ✅ Mises à jour automatiques
   - Effort: Élevé

8. **Multi-répertoires avec fusion** (8-10h)
   - Impact: ✅ Organisation flexible
   - Effort: Élevé

9. **Queue d'extraction avec verrous** (4-5h)
   - Impact: ✅ Performance
   - Effort: Moyen

---

## 🎯 PLAN D'IMPLÉMENTATION RECOMMANDÉ

### Phase 1: Foundation (Semaine 1)
- ✅ Détection automatique `cores.json`
- ✅ Gestion taille cache ZIP
- ✅ Interface ajout répertoire

### Phase 2: Robustesse (Semaine 2)
- ✅ Validation options core
- ✅ Vérification intégrité
- ✅ Hot reload options

### Phase 3: Avancé (Semaine 3-4)
- ✅ Versioning cores
- ✅ Per-game config
- ✅ Multi-répertoires

---

**Document créé le:** 19 novembre 2025  
**Méthodologie:** "Nos Rules" - Suivre les specs officielles RetroArch/Libretro  
**Références:** buildbot.libretro.com, RetroArch documentation, Libretro specs

