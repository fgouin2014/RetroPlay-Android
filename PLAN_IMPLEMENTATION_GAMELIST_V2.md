# 📋 PLAN D'IMPLÉMENTATION - Gamelist.json V2

**Date:** 20 novembre 2025  
**Objectif:** Réimplémentation complète du système de gamelist.json

---

## ✅ DÉCISIONS PRISES

### 1. **Format Standard**
- ✅ **Nouveau format** : `description`, `releaseDate` (camelCase)
- ✅ **Impact minimal** : "desc" (4 chars) vs "description" (11 chars) = +7 bytes par jeu
- ✅ **Migration automatique** : Parser supporte les deux formats, génère toujours le nouveau

### 2. **Archives (ZIP/7Z)**
- ✅ **Mix des deux** : Extraire automatiquement si core compatible
- ✅ **Logique existante** : Déjà implémentée dans `GameDetailsActivity.extractToCacheAsync()`
  - Arcade (MAME/FBNeo) : `.zip` chargé directement (ROM sets)
  - Autres consoles : `.zip`/`.7z` extraits automatiquement
  - Formats natifs : `.pbp`, `.chd`, `.cso` chargés directement

### 3. **PSX Identification**
- ✅ **Numéros de série** : Utiliser SLUS/SLES/etc. au lieu de CRC32
- ✅ **Référence** : Code Lemuroid `SerialScanner.kt` pour extraction depuis headers
- ✅ **Format** : `SLUS-01234` ou `SLES-00567`

### 4. **Scan Automatique**
- ✅ **Premier démarrage** : Scan automatique de tous les répertoires
- ✅ **Démarrages suivants** : Toggle ON par défaut pour scan des nouvelles ROMs
- ✅ **Option utilisateur** : Toggle dans les paramètres

---

## 🎯 PHASE 1: Format Standardisé et Migration

### 1.1 Mettre à jour GamelistManager.kt
```kotlin
// TOUJOURS générer le nouveau format
private fun gameToJson(game: GameEntry): JSONObject {
    return JSONObject().apply {
        put("id", game.id)
        put("name", game.name)
        put("path", game.path)
        // NOUVEAU FORMAT (camelCase)
        put("description", game.description ?: "")
        put("genre", game.genre ?: "")
        put("releaseDate", game.releaseDate ?: "")
        put("players", game.players ?: "")
        // ... reste
    }
}
```

### 1.2 Mettre à jour parseGameEntry() pour migration
```kotlin
// Support legacy ET nouveau format
val description = obj.optString("description", null) 
    ?: obj.optString("desc", null)  // Fallback legacy
val releaseDate = obj.optString("releaseDate", null) 
    ?: obj.optString("releasedate", null)  // Fallback legacy
```

### 1.3 Mettre à jour GameListActivity.parseAndDisplayGames()
```java
// Support des deux formats avec priorité au nouveau
String desc = obj.optString("description", "");
if (desc.isEmpty()) {
    desc = obj.optString("desc", "");  // Fallback legacy
}
String releaseDate = obj.optString("releaseDate", "");
if (releaseDate.isEmpty()) {
    releaseDate = obj.optString("releasedate", "");  // Fallback legacy
}
```

**Impact estimé :**
- Poids : +7 bytes par jeu (négligeable)
- Exécution : Aucun (même parsing)
- Lectures : Aucun (même structure JSON)

---

## 🎯 PHASE 2: PSX Serial Numbers

### 2.1 Créer PSXSerialExtractor.kt
```kotlin
object PSXSerialExtractor {
    private val PSX_BASE_SERIALS = listOf(
        "CPCS", "SCES", "SIPS", "SLKA", "SLPS", 
        "SLUS", "ESPM", "SLED", "SCPS", "SCAJ",
        "PAPX", "SLES", "HPS", "LSP", "SLPM",
        "SCUS", "SCED"
    )
    
    private val PS_SERIAL_REGEX = Regex("^([A-Z]+)-?([0-9]+)")
    private const val PS_SERIAL_MAX_SIZE = 12
    private const val HEADER_SIZE = 64 * 1024  // 64KB
    
    fun extractSerial(file: File): String? {
        // Lire les 64 premiers KB
        // Chercher les patterns SLUS-01234, SLES-00567, etc.
        // Retourner le numéro de série trouvé
    }
}
```

### 2.2 Intégrer dans GamelistManager
```kotlin
private fun createGameEntryFromFile(
    file: File,
    consoleId: String,
    baseDir: File
): GameEntry? {
    // ...
    
    // Pour PSX, utiliser serial au lieu de CRC32
    val serial = if (consoleId == "psx") {
        PSXSerialExtractor.extractSerial(file)
    } else {
        null
    }
    
    // CRC32 seulement si pas PSX (ou optionnel)
    val crc32 = if (consoleId != "psx") {
        try {
            calculateCRC32(file)
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }
    
    return GameEntry(
        // ...
        serial = serial,
        crc32 = crc32,
        // ...
    )
}
```

---

## 🎯 PHASE 3: Scan Automatique au Démarrage

### 3.1 Créer GamelistScanner.kt
```kotlin
object GamelistScanner {
    private const val TAG = "GamelistScanner"
    private const val PREF_FIRST_LAUNCH = "first_launch_completed"
    private const val PREF_AUTO_SCAN_ENABLED = "auto_scan_enabled"
    
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
     * Scan tous les répertoires de consoles
     */
    suspend fun scanAllConsoles(context: Context): List<String> = withContext(Dispatchers.IO) {
        val gamelibraryDir = File("/storage/emulated/0/GameLibrary-Data")
        val scannedConsoles = mutableListOf<String>()
        
        if (!gamelibraryDir.exists()) {
            return@withContext scannedConsoles
        }
        
        val consoleDirs = gamelibraryDir.listFiles { it.isDirectory } ?: return@withContext scannedConsoles
        
        for (consoleDir in consoleDirs) {
            // Ignorer les répertoires système
            if (consoleDir.name.startsWith(".")) continue
            
            // Vérifier si gamelist.json existe
            val gamelistFile = File(consoleDir, "gamelist.json")
            val needsScan = !gamelistFile.exists() || 
                          shouldRescan(consoleDir, gamelistFile)
            
            if (needsScan) {
                try {
                    val consoleId = ConsoleNameMapper.normalizeToCanonical(consoleDir.name)
                    val gamelist = GamelistManager.generateGamelist(
                        consoleDir, 
                        consoleId,
                        includeMetadata = true
                    )
                    
                    GamelistManager.saveGamelist(gamelist, consoleDir)
                    scannedConsoles.add(consoleDir.name)
                    Log.i(TAG, "Generated gamelist for ${consoleDir.name}: ${gamelist.games.size} games")
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning ${consoleDir.name}: ${e.message}", e)
                }
            }
        }
        
        // Marquer premier lancement comme terminé
        val prefs = context.getSharedPreferences("gamelist_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_FIRST_LAUNCH, true).apply()
        
        return@withContext scannedConsoles
    }
    
    /**
     * Vérifie si un répertoire doit être rescanné
     */
    private fun shouldRescan(consoleDir: File, gamelistFile: File): Boolean {
        // Vérifier si des fichiers ROM ont été modifiés après la génération du gamelist
        val gamelistTime = gamelistFile.lastModified()
        val romFiles = findRomFiles(consoleDir)
        
        return romFiles.any { it.lastModified() > gamelistTime }
    }
    
    /**
     * Trouve tous les fichiers ROM dans un répertoire
     */
    private fun findRomFiles(dir: File): List<File> {
        val romFiles = mutableListOf<File>()
        val extensions = GamelistManager.getDefaultExtensions(dir.name)
        
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (extensions.any { it.removePrefix(".") == ext }) {
                    romFiles.add(file)
                }
            }
        }
        
        return romFiles
    }
}
```

### 3.2 Intégrer dans MainActivity
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ... code existant
    
    // Scan automatique au démarrage
    if (GamelistScanner.INSTANCE.isFirstLaunch(this)) {
        // Premier démarrage : scan complet
        scanAllConsolesOnFirstLaunch();
    } else if (GamelistScanner.INSTANCE.isAutoScanEnabled(this)) {
        // Démarrages suivants : scan seulement si nouvelles ROMs
        scanNewRomsIfEnabled();
    }
}

private void scanAllConsolesOnFirstLaunch() {
    ProgressDialog progress = new ProgressDialog(this);
    progress.setTitle("Initial Scan");
    progress.setMessage("Scanning ROM directories...");
    progress.setCancelable(false);
    progress.show();
    
    // Utiliser coroutines depuis Java via Kotlin
    kotlinx.coroutines.CoroutineScope scope = kotlinx.coroutines.CoroutinesKt.CoroutineScope(
        kotlinx.coroutines.Dispatchers.getIO()
    );
    
    scope.launch(() -> {
        List<String> scanned = GamelistScanner.INSTANCE.scanAllConsoles(MainActivity.this);
        
        runOnUiThread(() -> {
            progress.dismiss();
            Toast.makeText(
                MainActivity.this,
                "Scanned " + scanned.size() + " consoles",
                Toast.LENGTH_SHORT
            ).show();
        });
    });
}
```

---

## 🎯 PHASE 4: Scanner les Archives

### 4.1 Améliorer scanDirectory() pour archives
```kotlin
private fun scanDirectory(
    dir: File,
    consoleId: String,
    extensions: List<String>,
    games: MutableList<GameEntry>,
    baseDir: File
) {
    // ... code existant
    
    for (file in files) {
        if (file.isFile) {
            val fileName = file.name.lowercase()
            
            // Vérifier si c'est une archive
            if (fileName.endsWith(".zip") || fileName.endsWith(".7z")) {
                // Scanner le contenu de l'archive
                scanArchive(file, consoleId, extensions, games, baseDir)
            } else {
                // Fichier ROM normal
                val isValidRom = extensions.any { ext ->
                    fileName.endsWith(ext.lowercase())
                }
                
                if (isValidRom) {
                    val game = createGameEntryFromFile(file, consoleId, baseDir)
                    if (game != null) {
                        games.add(game)
                    }
                }
            }
        }
    }
}

private fun scanArchive(
    archiveFile: File,
    consoleId: String,
    extensions: List<String>,
    games: MutableList<GameEntry>,
    baseDir: File
) {
    try {
        if (archiveFile.name.endsWith(".zip")) {
            java.util.zip.ZipFile(archiveFile).use { zip ->
                zip.entries().forEach { entry ->
                    if (!entry.isDirectory) {
                        val entryName = entry.name.lowercase()
                        val isValidRom = extensions.any { ext ->
                            entryName.endsWith(ext.lowercase())
                        }
                        
                        if (isValidRom) {
                            // Créer une entrée pour cette ROM dans l'archive
                            val game = createGameEntryFromArchive(
                                archiveFile,
                                entry,
                                consoleId,
                                baseDir
                            )
                            if (game != null) {
                                games.add(game)
                            }
                        }
                    }
                }
            }
        } else if (archiveFile.name.endsWith(".7z")) {
            // Utiliser Apache Commons Compress pour .7z
            org.apache.commons.compress.archivers.sevenz.SevenZFile(archiveFile).use { sevenZ ->
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name.lowercase()
                        val isValidRom = extensions.any { ext ->
                            entryName.endsWith(ext.lowercase())
                        }
                        
                        if (isValidRom) {
                            val game = createGameEntryFromArchive(
                                archiveFile,
                                entry,
                                consoleId,
                                baseDir
                            )
                            if (game != null) {
                                games.add(game)
                            }
                        }
                    }
                    entry = sevenZ.nextEntry
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error scanning archive ${archiveFile.name}: ${e.message}")
    }
}

private fun createGameEntryFromArchive(
    archiveFile: File,
    entry: Any,  // ZipEntry ou SevenZArchiveEntry
    consoleId: String,
    baseDir: File
): GameEntry? {
    val entryName = when (entry) {
        is java.util.zip.ZipEntry -> entry.name
        is org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry -> entry.name
        else -> return null
    }
    
    val baseName = entryName.substringBeforeLast(".")
    val archivePath = archiveFile.relativeTo(baseDir).path.replace("\\", "/")
    val path = "./$archivePath#$entryName"  // Format: ./archive.zip#rom.nes
    
    // Pour PSX, extraire le serial depuis l'archive
    val serial = if (consoleId == "psx") {
        extractSerialFromArchiveEntry(archiveFile, entry)
    } else {
        null
    }
    
    return GameEntry(
        id = "0",
        name = baseName,
        path = path,
        description = null,
        genre = null,
        releaseDate = null,
        players = null,
        crc32 = null,  // Pas de CRC32 pour archives (trop lent)
        size = when (entry) {
            is java.util.zip.ZipEntry -> entry.size
            is org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry -> entry.size
            else -> null
        },
        serial = serial,
        md5 = null,
        sha1 = null,
        corePath = null,
        coreName = null
    )
}
```

---

## 🎯 PHASE 5: Toggle dans Paramètres

### 5.1 Ajouter préférence dans Settings
```java
// Dans SettingsActivity ou MainActivity
private void setupAutoScanToggle() {
    Switch autoScanToggle = findViewById(R.id.autoScanToggle);
    SharedPreferences prefs = getSharedPreferences("gamelist_prefs", MODE_PRIVATE);
    
    boolean enabled = prefs.getBoolean("auto_scan_enabled", true);
    autoScanToggle.setChecked(enabled);
    
    autoScanToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
        prefs.edit().putBoolean("auto_scan_enabled", isChecked).apply();
        Toast.makeText(
            this,
            "Auto-scan " + (isChecked ? "enabled" : "disabled"),
            Toast.LENGTH_SHORT
        ).show();
    });
}
```

---

## 📝 ORDRE D'IMPLÉMENTATION

1. ✅ **Phase 1** : Format standardisé (description, releaseDate)
2. ✅ **Phase 2** : PSX Serial Numbers
3. ✅ **Phase 3** : Scan automatique au démarrage
4. ✅ **Phase 4** : Scanner les archives
5. ✅ **Phase 5** : Toggle dans paramètres

---

## 🔍 TESTS À EFFECTUER

1. **Premier démarrage** : Vérifier scan complet de tous les répertoires
2. **Démarrages suivants** : Vérifier scan seulement si nouvelles ROMs
3. **Archives** : Vérifier que chaque ROM dans archive est listée
4. **PSX** : Vérifier extraction des numéros de série (SLUS, SLES, etc.)
5. **Format** : Vérifier migration depuis ancien format
6. **Performance** : Vérifier que scan ne bloque pas l'UI

---

## 📚 RÉFÉRENCES

- **SerialScanner.kt (Lemuroid)** : Extraction numéros de série PSX
- **GameDetailsActivity.java** : Logique extraction archives
- **GamelistManager.kt** : Génération gamelist actuelle








