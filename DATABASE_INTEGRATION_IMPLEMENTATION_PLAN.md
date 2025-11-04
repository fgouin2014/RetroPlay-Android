# 🚀 PLAN D'IMPLÉMENTATION: Database Integration
## Phase 1 - Foundation (Semaines 1-2)

**Date de début:** 3 novembre 2025  
**Objectif:** Intégrer libretro-database pour auto-load cheats et smart configuration  
**Approche:** "Nos Rules" - Implémentation exacte selon RetroArch officiel

---

## 📋 PHASE 1A: CRC32 System & DAT Parser (Jours 1-3)

### Fichiers à Créer

#### 1. `DatabaseManager.kt`
**Emplacement:** `app/src/main/java/com/retroplay/database/DatabaseManager.kt`

**Responsabilités:**
- Calculer CRC32 des ROMs
- Lookup game info par CRC
- Cache en mémoire (HashMap)
- SQLite storage pour performance

**API:**
```kotlin
object DatabaseManager {
    fun calculateCRC32(filePath: String): String
    fun lookupGame(crc: String, console: String): GameInfo?
    fun loadDatabase(console: String)
    fun getCheatsPath(gameInfo: GameInfo, console: String): File?
}

data class GameInfo(
    val name: String,
    val crc: String,
    val genre: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val releaseMonth: Int?,
    val maxPlayers: Int,
    val hasRumble: Boolean,
    val hasAnalog: Boolean,
    val isHack: Boolean,
    val isHomebrew: Boolean
)
```

---

#### 2. `DatParser.kt`
**Emplacement:** `app/src/main/java/com/retroplay/database/DatParser.kt`

**Responsabilités:**
- Parser fichiers .dat (format clrmamepro)
- Merger metadata de plusieurs sources (genre + developer + year)
- Gérer précédence (dat > metadat)

**Format DAT à parser:**
```dat
game (
    comment "Mega Man (USA)"
    genre "Platformer"
    developer "Capcom"
    releaseyear "1987"
    rom ( crc 74D7BAE1 )
)
```

---

#### 3. `DatabaseCache.kt`
**Emplacement:** `app/src/main/java/com/retroplay/database/DatabaseCache.kt`

**Responsabilités:**
- SQLite database locale
- Indexation CRC pour lookup O(1)
- Éviter parser DAT à chaque lancement

**Schema:**
```sql
CREATE TABLE games (
    crc TEXT PRIMARY KEY,
    console TEXT NOT NULL,
    name TEXT NOT NULL,
    genre TEXT,
    developer TEXT,
    publisher TEXT,
    release_year INTEGER,
    release_month INTEGER,
    max_players INTEGER DEFAULT 1,
    has_rumble INTEGER DEFAULT 0,
    has_analog INTEGER DEFAULT 0,
    is_hack INTEGER DEFAULT 0,
    is_homebrew INTEGER DEFAULT 0
);

CREATE INDEX idx_console ON games(console);
CREATE INDEX idx_genre ON games(genre);
```

---

## 📋 PHASE 1B: Téléchargement Database (Jour 4)

### Fichiers Nécessaires sur Device

**Copier depuis `c:\repos\libretro-database-master\` vers `/storage/emulated/0/GameLibrary-Data/database/`:**

#### Metadata DAT Files (Small, ~50MB total)
```
database/metadata/
├── genre/
│   ├── Nintendo - Nintendo Entertainment System.dat
│   ├── Sony - PlayStation.dat
│   └── ... (toutes consoles)
├── developer/
│   └── ... (même structure)
├── releaseyear/
│   └── ...
└── rumble/
    └── ...
```

#### Cheat Files (Large, ~500MB uncompressed)
```
database/cht/
├── Nintendo - Nintendo Entertainment System/
│   ├── Mega Man (USA).cht
│   ├── Contra (USA).cht
│   └── ... (2,265 fichiers)
├── Sony - PlayStation/
│   └── ... (3,500 fichiers)
└── ... (24,863 fichiers total)
```

**Script de déploiement:**
```powershell
# deploy_database.ps1
$SOURCE = "c:\repos\libretro-database-master"
$DEST = "/storage/emulated/0/GameLibrary-Data/database"

# 1. Copier metadata DAT files
adb push "$SOURCE\metadat\genre\Nintendo - Nintendo Entertainment System.dat" "$DEST/metadata/genre/"
adb push "$SOURCE\metadat\developer\Nintendo - Nintendo Entertainment System.dat" "$DEST/metadata/developer/"
adb push "$SOURCE\metadat\releaseyear\Nintendo - Nintendo Entertainment System.dat" "$DEST/metadata/releaseyear/"

# 2. Copier cheats NES (priorité)
adb push "$SOURCE\cht\Nintendo - Nintendo Entertainment System\" "$DEST/cht/Nintendo - Nintendo Entertainment System/"

# 3. Autres consoles à déployer selon besoin
# adb push "$SOURCE\cht\Sony - PlayStation\" "$DEST/cht/Sony - PlayStation/"
```

---

## 📋 PHASE 1C: Integration Core (Jours 5-7)

### Modifications Existantes

#### 1. `GameDetailsActivity.java` - Ajouter Database Lookup

**Avant le lancement:**
```java
// Dans launchGame() AVANT startActivity()
private void launchGameWithDatabase(String romPath, String console, String gameName) {
    // 1. Calculer CRC
    String crc = DatabaseManager.calculateCRC32(romPath);
    Log.i(TAG, "ROM CRC32: " + crc);
    
    // 2. Lookup dans database
    GameInfo gameInfo = DatabaseManager.lookupGame(crc, console);
    
    if (gameInfo != null) {
        Log.i(TAG, "✅ Game identified from database:");
        Log.i(TAG, "  Name: " + gameInfo.name);
        Log.i(TAG, "  Genre: " + gameInfo.genre);
        Log.i(TAG, "  Developer: " + gameInfo.developer);
        Log.i(TAG, "  Year: " + gameInfo.releaseYear);
        
        // 3. Check cheats disponibles
        File cheatFile = DatabaseManager.getCheatsPath(gameInfo, console);
        if (cheatFile != null && cheatFile.exists()) {
            int cheatCount = CheatParser.countCheats(cheatFile);
            Log.i(TAG, "  Cheats: " + cheatCount + " available");
            
            // Notification optionnelle
            Toast.makeText(this, 
                cheatCount + " cheats available for " + gameInfo.name, 
                Toast.LENGTH_SHORT).show();
        }
        
        // 4. Passer metadata à l'émulateur via Intent
        intent.putExtra("gameInfo_genre", gameInfo.genre);
        intent.putExtra("gameInfo_developer", gameInfo.developer);
        intent.putExtra("gameInfo_crc", gameInfo.crc);
        intent.putExtra("cheatsAvailable", cheatCount);
    } else {
        Log.w(TAG, "⚠️ Game not found in database (CRC: " + crc + ")");
    }
    
    // 5. Lancer normalement
    startActivity(intent);
}
```

---

#### 2. `RetroArchEmulatorActivity.kt` - Auto-Load Cheats

**Dans onCreate():**
```kotlin
// Récupérer metadata depuis Intent
val gameInfoCRC = intent.getStringExtra("gameInfo_crc")
val cheatsAvailable = intent.getIntExtra("cheatsAvailable", 0)

if (gameInfoCRC != null && cheatsAvailable > 0) {
    // Auto-load cheats
    val gameInfo = DatabaseManager.lookupGame(gameInfoCRC, console)
    if (gameInfo != null) {
        val cheatFile = DatabaseManager.getCheatsPath(gameInfo, console)
        if (cheatFile != null) {
            // Charger dans CheatManager (existant)
            CheatManager.loadCheats(cheatFile)
            Log.i(TAG, "✅ Auto-loaded $cheatsAvailable cheats for ${gameInfo.name}")
        }
    }
}
```

---

#### 3. Quick Menu - Afficher Cheats Disponibles

**Nouveau bouton dans QuickMenuDialog:**
```kotlin
// Si cheats disponibles, afficher bouton
if (cheatsAvailable > 0) {
    QuickMenuButton(
        icon = "🎮",
        text = "CHEATS ($cheatsAvailable available)",
        onClick = {
            showCheatSelectionDialog.value = true
        }
    )
}
```

---

### Nouveaux Composables

#### 4. `CheatSelectionDialog.kt`
**Emplacement:** `app/src/main/java/com/retroplay/CheatSelectionDialog.kt`

**UI:**
```kotlin
@Composable
fun CheatSelectionDialog(
    gameName: String,
    availableCheats: List<CheatCode>,
    onApply: (List<CheatCode>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedCheats = remember { mutableStateListOf<CheatCode>() }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column {
                // Header
                Text(
                    text = "CHEATS: $gameName",
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = "${availableCheats.size} cheats available",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Divider(color = Color(0xFF424242))
                
                // Liste scrollable des cheats
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(availableCheats) { cheat ->
                        CheatItem(
                            cheat = cheat,
                            isSelected = selectedCheats.contains(cheat),
                            onToggle = { 
                                if (selectedCheats.contains(cheat)) {
                                    selectedCheats.remove(cheat)
                                } else {
                                    selectedCheats.add(cheat)
                                }
                            }
                        )
                    }
                }
                
                // Footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { selectedCheats.clear() }) {
                        Text("Clear All")
                    }
                    TextButton(onClick = { selectedCheats.addAll(availableCheats) }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { 
                        onApply(selectedCheats.toList())
                        onDismiss()
                    }) {
                        Text("Apply (${selectedCheats.size})", color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}

@Composable
fun CheatItem(
    cheat: CheatCode,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4CAF50)
            )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = cheat.description,
                fontSize = 16.sp,
                color = Color.White
            )
            Text(
                text = cheat.code,
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

data class CheatCode(
    val index: Int,
    val description: String,
    val code: String,
    val enabled: Boolean = false
)
```

---

## 📋 PHASE 2: Auto-Load Cheats (Semaine 3)

### Étape 2A: CheatParser Enhancement

**Modifier:** `app/src/main/java/com/retroplay/cheat/CheatManager.kt`

**Ajouter:**
```kotlin
object CheatParser {
    /**
     * Parse .cht file et retourne liste de tous les cheats
     */
    fun parseCheatFile(cheatFile: File): List<CheatCode> {
        val cheats = mutableListOf<CheatCode>()
        val lines = cheatFile.readLines()
        
        var totalCheats = 0
        var currentIndex = -1
        var currentDesc = ""
        var currentCode = ""
        var currentEnabled = false
        
        lines.forEach { line ->
            when {
                line.startsWith("cheats =") -> {
                    totalCheats = line.substringAfter("=").trim().toInt()
                }
                line.startsWith("cheat") && line.contains("_desc =") -> {
                    currentIndex = line.substringAfter("cheat").substringBefore("_").toInt()
                    currentDesc = line.substringAfter("\"").substringBeforeLast("\"")
                }
                line.startsWith("cheat") && line.contains("_code =") -> {
                    currentCode = line.substringAfter("\"").substringBeforeLast("\"")
                }
                line.startsWith("cheat") && line.contains("_enable =") -> {
                    currentEnabled = line.substringAfter("=").trim() == "true"
                    
                    // Cheat complet, ajouter à la liste
                    cheats.add(CheatCode(
                        index = currentIndex,
                        description = currentDesc,
                        code = currentCode,
                        enabled = currentEnabled
                    ))
                }
            }
        }
        
        return cheats
    }
    
    fun countCheats(cheatFile: File): Int {
        val firstLine = cheatFile.readLines().firstOrNull { it.startsWith("cheats =") }
        return firstLine?.substringAfter("=")?.trim()?.toIntOrNull() ?: 0
    }
}
```

---

### Étape 2B: Integration dans Quick Menu

**Modifier:** `RetroArchEmulatorActivity.kt`

**Ajouter state:**
```kotlin
private val showCheatSelectionDialog = mutableStateOf(false)
private val availableCheats = mutableStateListOf<CheatCode>()
```

**Dans QuickMenuDialog:**
```kotlin
// Charger cheats disponibles au premier affichage du Quick Menu
LaunchedEffect(Unit) {
    val gameInfoCRC = intent.getStringExtra("gameInfo_crc")
    if (gameInfoCRC != null) {
        val gameInfo = DatabaseManager.lookupGame(gameInfoCRC, console)
        if (gameInfo != null) {
            val cheatFile = DatabaseManager.getCheatsPath(gameInfo, console)
            if (cheatFile != null && cheatFile.exists()) {
                availableCheats.clear()
                availableCheats.addAll(CheatParser.parseCheatFile(cheatFile))
                Log.i(TAG, "Loaded ${availableCheats.size} cheats for ${gameInfo.name}")
            }
        }
    }
}

// Bouton dans Quick Menu
if (availableCheats.isNotEmpty()) {
    QuickMenuButton(
        icon = "🎮",
        text = "CHEATS (${availableCheats.size})",
        onClick = {
            showCheatSelectionDialog.value = true
        }
    )
}

// Dialog
if (showCheatSelectionDialog.value) {
    CheatSelectionDialog(
        gameName = gameName,
        availableCheats = availableCheats,
        onApply = { selectedCheats ->
            // Appliquer les cheats sélectionnés
            CheatManager.applySelectedCheats(selectedCheats, retroView)
            Toast.makeText(
                this@RetroArchEmulatorActivity,
                "${selectedCheats.size} cheats activated",
                Toast.LENGTH_SHORT
            ).show()
        },
        onDismiss = { showCheatSelectionDialog.value = false }
    )
}
```

---

## 📋 PHASE 3: Smart Configuration (Semaine 4)

### Genre-Based Run-Ahead Config

**Créer:** `SmartConfigManager.kt`

```kotlin
object SmartConfigManager {
    /**
     * Déterminer config optimale de Run-Ahead basée sur genre
     */
    fun getOptimalRunAheadFrames(gameInfo: GameInfo, console: String): Int {
        return when {
            // Fighting games = input lag CRITIQUE
            gameInfo.genre == "Fighting" -> 4
            
            // Platformers = input précis important
            gameInfo.genre == "Platformer" || gameInfo.genre == "Action" -> 2
            
            // Shoot'em up = réactivité importante
            gameInfo.genre == "Shoot'em Up" -> 3
            
            // RPG/Strategy = input lag moins critique
            gameInfo.genre == "RPG" || gameInfo.genre == "Strategy" -> 0
            
            // Puzzle = pas besoin
            gameInfo.genre == "Puzzle" -> 0
            
            // Racing = important mais dépend du jeu
            gameInfo.genre == "Racing" -> 2
            
            // Default conservateur
            else -> 1
        }
    }
    
    /**
     * Déterminer taille buffer Rewind optimale
     */
    fun getOptimalRewindBuffer(gameInfo: GameInfo, console: String): Int {
        // Taille de base selon console (savestate size)
        val baseSize = when (console) {
            "nes" -> 20 * 1024 * 1024      // 20MB (savestate ~10KB)
            "snes" -> 15 * 1024 * 1024     // 15MB
            "psx" -> 10 * 1024 * 1024      // 10MB (savestate ~500KB)
            "n64" -> 8 * 1024 * 1024       // 8MB
            "gba" -> 12 * 1024 * 1024      // 12MB
            else -> 10 * 1024 * 1024
        }
        
        // Ajustement par genre
        val multiplier = when (gameInfo.genre) {
            "Platformer" -> 1.5f    // Rewind très utile
            "Puzzle" -> 2.0f        // Long temps de réflexion
            "Fighting" -> 0.5f      // Peu utile
            "Racing" -> 0.7f        // Peu utile
            else -> 1.0f
        }
        
        return (baseSize * multiplier).toInt()
    }
    
    /**
     * Sélectionner overlay optimal par genre
     */
    fun getOptimalOverlay(gameInfo: GameInfo, console: String): String {
        return when (console) {
            "nes" -> when (gameInfo.genre) {
                "Fighting" -> "nes-6button"         // Plus de boutons
                "Racing" -> "nes-minimal"           // Moins encombré
                "RPG" -> "nes-transparent"          // Plus visibilité
                "Shoot'em Up" -> "nes-dense"        // Rapid fire
                else -> "nes-standard"
            }
            "snes" -> when (gameInfo.genre) {
                "Fighting" -> "snes-6button"
                "Racing" -> "snes-minimal"
                else -> "snes-standard"
            }
            "psx" -> when {
                gameInfo.hasAnalog -> "psx-analog"  // Analog sticks requis
                else -> "psx-digital"
            }
            else -> "${console}-standard"
        }
    }
}
```

---

### Integration dans Émulateur

**Modifier:** `RetroArchEmulatorActivity.kt`

**Dans onCreate(), APRÈS database lookup:**
```kotlin
val gameInfo = intent.getStringExtra("gameInfo_crc")?.let { crc ->
    DatabaseManager.lookupGame(crc, console)
}

if (gameInfo != null) {
    // Smart configuration
    val runAheadFrames = SmartConfigManager.getOptimalRunAheadFrames(gameInfo, console)
    val rewindBuffer = SmartConfigManager.getOptimalRewindBuffer(gameInfo, console)
    val optimalOverlay = SmartConfigManager.getOptimalOverlay(gameInfo, console)
    
    Log.i(TAG, "[SMART CONFIG] Genre: ${gameInfo.genre}")
    Log.i(TAG, "[SMART CONFIG] Run-Ahead: $runAheadFrames frames")
    Log.i(TAG, "[SMART CONFIG] Rewind Buffer: ${rewindBuffer / 1024 / 1024}MB")
    Log.i(TAG, "[SMART CONFIG] Overlay: $optimalOverlay")
    
    // TODO: Appliquer ces configs quand Run-Ahead/Rewind seront implémentés
}
```

---

## 📋 PHASE 4: Savestate Thumbnails (Semaine 4)

### Étape 4A: Screenshot Capture

**Ajouter dans GLRetroView.kt:**
```kotlin
fun captureScreenshot(): Bitmap? {
    // Capturer le framebuffer actuel
    // LibretroDroid expose déjà video callback
    return currentFrameBitmap
}
```

---

### Étape 4B: Thumbnail Storage

**Format de sauvegarde:**
```
/storage/emulated/0/GameLibrary-Data/states/nes/
├── Mega Man (USA).state0
├── Mega Man (USA).state0.png        ← Thumbnail
├── Mega Man (USA).state0.json       ← Metadata
├── Mega Man (USA).state1
├── Mega Man (USA).state1.png
└── ...
```

**Metadata JSON:**
```json
{
  "gameName": "Mega Man (USA)",
  "developer": "Capcom",
  "genre": "Platformer",
  "timestamp": 1730678400000,
  "levelName": "Elecman Stage",
  "playTime": 1245,
  "version": "1.0"
}
```

---

### Étape 4C: Enhanced Savestate UI

**Créer:** `SaveStateDialog.kt`

```kotlin
@Composable
fun SaveStateDialog(
    gameName: String,
    slots: List<SaveStateSlot>,
    onLoad: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            LazyColumn {
                items(slots) { slot ->
                    SaveStateSlotItem(
                        slot = slot,
                        onLoad = { onLoad(slot.index) },
                        onSave = { onSave(slot.index) }
                    )
                }
            }
        }
    }
}

@Composable
fun SaveStateSlotItem(slot: SaveStateSlot) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        // Thumbnail (si existe)
        if (slot.thumbnailPath != null) {
            AsyncImage(
                model = slot.thumbnailPath,
                modifier = Modifier.size(100.dp, 75.dp)
            )
        } else {
            Box(
                modifier = Modifier.size(100.dp, 75.dp)
                    .background(Color.Gray)
            ) {
                Text("Empty", modifier = Modifier.align(Alignment.Center))
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Info
        Column {
            Text("Slot ${slot.index}", fontSize = 18.sp)
            if (slot.metadata != null) {
                Text(slot.metadata.levelName, fontSize = 14.sp)
                Text(
                    formatTimestamp(slot.metadata.timestamp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

data class SaveStateSlot(
    val index: Int,
    val exists: Boolean,
    val thumbnailPath: String?,
    val metadata: SaveStateMetadata?
)

data class SaveStateMetadata(
    val gameName: String,
    val developer: String?,
    val genre: String?,
    val timestamp: Long,
    val levelName: String?,
    val playTime: Int
)
```

---

## 📊 STRUCTURE FINALE DU PROJET

### Nouveaux Packages

```
app/src/main/java/com/retroplay/
├── database/
│   ├── DatabaseManager.kt        (CRC lookup, cache)
│   ├── DatParser.kt              (Parse .dat files)
│   ├── DatabaseCache.kt          (SQLite storage)
│   └── SmartConfigManager.kt     (Genre-based config)
│
├── cheat/ (EXISTANT - À AMÉLIORER)
│   ├── CheatManager.kt           (Charger/appliquer cheats)
│   ├── CheatParser.kt            (Parser .cht files)
│   └── CheatSelectionDialog.kt   (UI sélection)
│
└── savestate/ (NOUVEAU)
    ├── SaveStateManager.kt       (Gestion savestates)
    ├── SaveStateThumbnail.kt     (Screenshot capture)
    └── SaveStateDialog.kt        (UI améliorée)
```

---

## 🎯 MILESTONES & TESTS

### Milestone 1: CRC Lookup (Jour 3)
**Test:**
```kotlin
val crc = DatabaseManager.calculateCRC32("/path/to/Mega Man (USA).nes")
assertEquals("74D7BAE1", crc)

val gameInfo = DatabaseManager.lookupGame(crc, "nes")
assertNotNull(gameInfo)
assertEquals("Mega Man (USA)", gameInfo.name)
assertEquals("Platformer", gameInfo.genre)
assertEquals("Capcom", gameInfo.developer)
```

---

### Milestone 2: Auto-Load Cheats (Semaine 3)
**Test:**
1. Lancer Mega Man (USA)
2. Vérifier logs: `✅ Auto-loaded 125 cheats for Mega Man (USA)`
3. Ouvrir Quick Menu
4. Cliquer "CHEATS (125)"
5. Voir liste scrollable des 125 cheats
6. Sélectionner "Infinite Energy" + "Walk Through Walls"
7. Appliquer
8. Vérifier qu'ils fonctionnent en jeu

---

### Milestone 3: Smart Config (Jour 5)
**Test:**
```kotlin
// Mega Man (Platformer)
val runAhead = SmartConfigManager.getOptimalRunAheadFrames(megaManInfo, "nes")
assertEquals(2, runAhead)

// Street Fighter II (Fighting)
val runAhead2 = SmartConfigManager.getOptimalRunAheadFrames(sf2Info, "snes")
assertEquals(4, runAhead2)
```

---

### Milestone 4: Savestate Thumbnails (Semaine 4)
**Test:**
1. Jouer Mega Man
2. Quick Menu → Save State → Slot 1
3. Vérifier fichiers créés:
   - `Mega Man (USA).state0`
   - `Mega Man (USA).state0.png`
   - `Mega Man (USA).state0.json`
4. Recharger Quick Menu
5. Voir thumbnail preview du savestate

---

## 📦 DÉPLOIEMENT DATABASE SUR DEVICE

### Script PowerShell de Déploiement

**Créer:** `RetroPlay-Android/scripts/deploy_database.ps1`

```powershell
# Deploy libretro-database to device
param(
    [string]$Console = "nes",  # Default: NES only
    [switch]$All = $false       # Deploy all consoles
)

$SOURCE = "c:\repos\libretro-database-master"
$DEST_BASE = "/storage/emulated/0/GameLibrary-Data/database"

Write-Host "🚀 Deploying libretro-database to device..." -ForegroundColor Cyan

# Check device connected
$device = adb devices | Select-String "device$"
if (-not $device) {
    Write-Host "❌ No device connected!" -ForegroundColor Red
    exit 1
}

# Create directories
adb shell "mkdir -p $DEST_BASE/metadata/genre"
adb shell "mkdir -p $DEST_BASE/metadata/developer"
adb shell "mkdir -p $DEST_BASE/metadata/releaseyear"
adb shell "mkdir -p $DEST_BASE/cht"

if ($All) {
    Write-Host "📦 Deploying ALL consoles (this will take 10-15 minutes)..." -ForegroundColor Yellow
    
    # Deploy all metadata
    adb push "$SOURCE\metadat\genre\" "$DEST_BASE/metadata/genre/"
    adb push "$SOURCE\metadat\developer\" "$DEST_BASE/metadata/developer/"
    adb push "$SOURCE\metadat\releaseyear\" "$DEST_BASE/metadata/releaseyear/"
    
    # Deploy all cheats (24,863 files)
    adb push "$SOURCE\cht\" "$DEST_BASE/cht/"
    
} else {
    # Console-specific deployment
    $consoleName = switch ($Console) {
        "nes" { "Nintendo - Nintendo Entertainment System" }
        "snes" { "Nintendo - Super Nintendo Entertainment System" }
        "psx" { "Sony - PlayStation" }
        "n64" { "Nintendo - Nintendo 64" }
        "gba" { "Nintendo - Game Boy Advance" }
        default { "Nintendo - Nintendo Entertainment System" }
    }
    
    Write-Host "📦 Deploying $consoleName..." -ForegroundColor Yellow
    
    # Metadata
    adb push "$SOURCE\metadat\genre\$consoleName.dat" "$DEST_BASE/metadata/genre/"
    adb push "$SOURCE\metadat\developer\$consoleName.dat" "$DEST_BASE/metadata/developer/"
    adb push "$SOURCE\metadat\releaseyear\$consoleName.dat" "$DEST_BASE/metadata/releaseyear/"
    
    # Cheats
    adb push "$SOURCE\cht\$consoleName\" "$DEST_BASE/cht/$consoleName/"
    
    Write-Host "✅ Deployed $consoleName database" -ForegroundColor Green
}

# Verify
$fileCount = adb shell "find $DEST_BASE -type f | wc -l"
Write-Host "📊 Total files deployed: $fileCount" -ForegroundColor Cyan
```

**Usage:**
```powershell
# Deploy NES only (rapide, ~5 minutes)
.\scripts\deploy_database.ps1 -Console nes

# Deploy all consoles (complet, ~15 minutes)
.\scripts\deploy_database.ps1 -All
```

---

## 🔗 INTÉGRATION AVEC RUN-AHEAD & REWIND (Semaines 5-8)

### Run-Ahead Implementation

**Créer:** `RunAheadManager.kt`

```kotlin
class RunAheadManager(
    private val retroView: GLRetroView,
    private val frames: Int  // 1-4 frames from SmartConfigManager
) {
    private val savestateBuffer = Array<ByteArray?>(frames) { null }
    private var currentFrame = 0
    
    fun runFrame() {
        if (frames == 0) {
            // Run-ahead disabled
            retroView.onFrame()
            return
        }
        
        // 1. Sauvegarder état actuel
        val currentState = retroView.serializeState()
        savestateBuffer[currentFrame % frames] = currentState
        
        // 2. Runner N frames en avance
        repeat(frames) {
            retroView.onFrame()
        }
        
        // 3. Restaurer état sauvegardé
        val stateToRestore = savestateBuffer[currentFrame % frames]
        if (stateToRestore != null) {
            retroView.unserializeState(stateToRestore)
        }
        
        currentFrame++
    }
}
```

**Utilisation:**
```kotlin
// Dans RetroArchEmulatorActivity
val gameInfo = DatabaseManager.getCurrentGame()
val runAheadFrames = SmartConfigManager.getOptimalRunAheadFrames(gameInfo, console)

val runAheadManager = RunAheadManager(retroView, runAheadFrames)

// Dans game loop
runAheadManager.runFrame()
```

---

### Rewind Implementation

**Créer:** `RewindManager.kt`

```kotlin
class RewindManager(
    private val retroView: GLRetroView,
    private val bufferSize: Int  // From SmartConfigManager
) {
    private val stateBuffer = CircularBuffer<ByteArray>(bufferSize)
    private var isRewinding = false
    
    fun saveFrame() {
        if (!isRewinding) {
            val state = retroView.serializeState()
            stateBuffer.add(state)
        }
    }
    
    fun startRewind() {
        isRewinding = true
    }
    
    fun stopRewind() {
        isRewinding = false
    }
    
    fun rewindFrame(): Boolean {
        if (stateBuffer.isEmpty()) return false
        
        val previousState = stateBuffer.removeLast()
        retroView.unserializeState(previousState)
        return true
    }
}

class CircularBuffer<T>(private val maxSize: Int) {
    private val buffer = mutableListOf<T>()
    
    fun add(item: T) {
        if (buffer.size >= maxSize) {
            buffer.removeAt(0)  // Remove oldest
        }
        buffer.add(item)
    }
    
    fun removeLast(): T? {
        return if (buffer.isNotEmpty()) buffer.removeAt(buffer.size - 1) else null
    }
    
    fun isEmpty() = buffer.isEmpty()
}
```

---

## 📈 IMPACT MESURABLE

### Avant Database Integration

| Fonctionnalité | État |
|----------------|------|
| Cheats Mega Man | 0 |
| Cheats Contra | 0 |
| Total cheats NES | ~50 manuels |
| Config Run-Ahead | Global fixe |
| Config Rewind | Global fixe |
| Overlay selection | Manuel |
| Game metadata | Filename seulement |

### Après Database Integration (8 semaines)

| Fonctionnalité | État |
|----------------|------|
| Cheats Mega Man | **125 auto-chargés** ✅ |
| Cheats Contra | **61 auto-chargés** ✅ |
| Total cheats NES | **2,265 jeux** ✅ |
| Config Run-Ahead | **Smart (genre-based)** ✅ |
| Config Rewind | **Smart (10-30MB)** ✅ |
| Overlay selection | **Auto (genre)** ✅ |
| Game metadata | **Complet (dev, year, genre)** ✅ |
| Savestate UI | **Thumbnails + metadata** ✅ |

---

## 🎯 PRIORITÉS D'IMPLÉMENTATION

### Week 1 (Jours 1-7): Foundation
- [x] ~~Créer plan d'implémentation~~ (FAIT)
- [ ] Créer `DatabaseManager.kt`
- [ ] Créer `DatParser.kt`
- [ ] Implémenter CRC32 calculator
- [ ] Tester avec Mega Man (USA)

### Week 2 (Jours 8-14): Database Loading
- [ ] Créer `DatabaseCache.kt` (SQLite)
- [ ] Parser DAT files (genre, developer, year)
- [ ] Déployer metadata NES sur device
- [ ] Integration dans `GameDetailsActivity`

### Week 3 (Jours 15-21): Auto-Load Cheats
- [ ] Déployer 2,265 .cht files NES
- [ ] Améliorer `CheatParser.kt`
- [ ] Créer `CheatSelectionDialog.kt`
- [ ] Integration Quick Menu
- [ ] **TEST: Mega Man = 125 cheats!**

### Week 4 (Jours 22-28): Savestate Thumbnails
- [ ] Screenshot capture system
- [ ] Thumbnail storage (.png)
- [ ] Metadata JSON (.json)
- [ ] Enhanced UI avec preview

### Week 5-6 (Jours 29-42): Run-Ahead
- [ ] `RunAheadManager.kt`
- [ ] Serialize/Deserialize state
- [ ] Smart config par genre
- [ ] UI toggle + frame selector

### Week 7-8 (Jours 43-56): Rewind
- [ ] `RewindManager.kt`
- [ ] Circular buffer
- [ ] Smart buffer size
- [ ] Hold button = rewind

---

## 🔚 CONCLUSION

**Database Integration = Multiplicateur de Force**

Sans database:
- Cheats = recherche manuelle
- Configuration = one-size-fits-all
- Savestates = liste text simple

Avec database:
- **24,863 cheats auto-chargés**
- **Smart config par genre**
- **UI riche avec thumbnails**

**Prêt à commencer?** Je commence par `DatabaseManager.kt` maintenant! 🚀

---

**Document créé le:** 3 novembre 2025  
**Méthodologie:** "Nos Rules" - Plan détaillé avant implémentation  
**Prochaine étape:** Créer DatabaseManager.kt

