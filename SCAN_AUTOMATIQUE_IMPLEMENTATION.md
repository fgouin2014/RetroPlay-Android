# ✅ Scan Automatique au Démarrage - Implémentation

**Date:** 20 novembre 2025  
**Status:** ✅ Implémenté

---

## 📋 FONCTIONNALITÉS

### 1. **Premier Démarrage**
- ✅ Scan complet de tous les répertoires de consoles
- ✅ Génération automatique de `gamelist.json` pour chaque console
- ✅ Dialog de progression affiché pendant le scan
- ✅ Toast de confirmation avec nombre de consoles et jeux scannés

### 2. **Démarrages Suivants**
- ✅ Scan automatique activé par défaut (toggle ON)
- ✅ Scan uniquement des répertoires avec ROMs modifiées
- ✅ Scan en arrière-plan (non bloquant)
- ✅ Toast de notification si nouvelles ROMs trouvées

### 3. **Détection Intelligente**
- ✅ Ignore les répertoires système (`data`, `emulatorjs`, `saves`, etc.)
- ✅ Détection récursive des ROMs (max 2 niveaux)
- ✅ Utilise les extensions par console via `GamelistManager.getDefaultExtensions()`
- ✅ Vérifie la date de modification pour éviter scans inutiles

---

## 🔧 IMPLÉMENTATION

### Fichiers Créés/Modifiés

#### 1. **GamelistScanner.kt** (NOUVEAU)
```kotlin
object GamelistScanner {
    // Méthodes principales:
    - isFirstLaunch(context): Boolean
    - isAutoScanEnabled(context): Boolean
    - setAutoScanEnabled(context, enabled)
    - scanAllConsoles(context): ScanResult (suspend)
    - scanNewRoms(context): ScanResult (suspend)
    - scanAllConsolesAsync(context, callback) // Pour Java
    - scanNewRomsAsync(context, callback)    // Pour Java
}
```

**Fonctionnalités:**
- Gestion des préférences (premier démarrage, toggle auto-scan)
- Scan complet au premier démarrage
- Scan incrémental des nouvelles ROMs
- Détection des ROMs modifiées (comparaison timestamps)
- Ignore les répertoires système

#### 2. **ScanCallback.java** (NOUVEAU)
```java
public interface ScanCallback {
    void onComplete(GamelistScanner.ScanResult result);
    void onError(String error);
}
```

**Usage:** Interface pour callbacks depuis Java vers Kotlin coroutines

#### 3. **MainActivity.java** (MODIFIÉ)
```java
private void startGamelistScan() {
    // Vérifie premier démarrage
    // Lance scan complet ou scan incrémental
}

private void scanAllConsolesOnFirstLaunch() {
    // Affiche ProgressDialog
    // Appelle GamelistScanner.scanAllConsolesAsync()
    // Affiche Toast avec résultats
}

private void scanNewRomsIfEnabled() {
    // Scan en arrière-plan (non bloquant)
    // Appelle GamelistScanner.scanNewRomsAsync()
    // Toast si nouvelles ROMs trouvées
}
```

**Intégration:**
- Appelé dans `onCreate()` après vérification des permissions
- Ne bloque pas le lancement de `GameListActivity`
- Scan asynchrone en arrière-plan

---

## 📊 FLUX D'EXÉCUTION

### Premier Démarrage
```
MainActivity.onCreate()
  ↓
checkPermissions() ✅
  ↓
installRetroArchOverlays() (en parallèle)
startGamelistScan()
  ↓
isFirstLaunch() = true
  ↓
scanAllConsolesOnFirstLaunch()
  ↓
ProgressDialog: "Scanning ROM directories..."
  ↓
GamelistScanner.scanAllConsoles()
  ↓
Pour chaque répertoire console:
  - Vérifier si gamelist.json existe
  - Si non: scanner ROMs et générer gamelist.json
  - Sauvegarder gamelist.json
  ↓
Toast: "Scanned X consoles (Y games)"
  ↓
GameListActivity lancé (par installRetroArchOverlays)
```

### Démarrages Suivants
```
MainActivity.onCreate()
  ↓
checkPermissions() ✅
  ↓
startGamelistScan()
  ↓
isFirstLaunch() = false
isAutoScanEnabled() = true (par défaut)
  ↓
scanNewRomsIfEnabled()
  ↓
GamelistScanner.scanNewRoms()
  ↓
Pour chaque répertoire console:
  - Vérifier si ROMs modifiées depuis dernier scan
  - Si oui: régénérer gamelist.json
  ↓
Toast (si nouvelles ROMs): "Updated X consoles (Y new games)"
  ↓
GameListActivity lancé
```

---

## ⚙️ PRÉFÉRENCES

### Clés SharedPreferences
- `first_launch_completed`: Boolean (premier démarrage terminé)
- `auto_scan_enabled`: Boolean (toggle scan automatique, défaut: true)
- `last_scan_time`: Long (timestamp dernier scan)

### Fichier
`/data/data/com.retroplay/shared_prefs/gamelist_prefs.xml`

---

## 🎯 DÉTECTION DES NOUVELLES ROMS

### Algorithme
1. Vérifier si `gamelist.json` existe
2. Si non → scan complet
3. Si oui → comparer `gamelist.json.lastModified()` avec ROMs
4. Si ROM modifiée après génération → rescan

### Optimisations
- Scan récursif limité à 2 niveaux (évite trop de profondeur)
- Ignore répertoires système automatiquement
- Utilise extensions par console (pas de scan inutile)

---

## 🔍 RÉPERTOIRES IGNORÉS

```kotlin
SYSTEM_DIRECTORIES = setOf(
    "data", "emulatorjs", "vmnes", "playlists",
    "saves", "states", "cheats", "media",
    "overlays", "cores", "bios", ".cache"
)
```

**Note:** Les répertoires commençant par `.` sont aussi ignorés

---

## 📝 EXEMPLE DE RÉSULTAT

### ScanResult
```kotlin
data class ScanResult(
    val scannedConsoles: List<String>,  // ["nes", "snes", "psx"]
    val errors: List<String>,           // ["genesis: Permission denied"]
    val totalGames: Int                 // 150
)
```

### Toast Messages
- **Premier démarrage:** `"Scanned 5 consoles (150 games)"`
- **Démarrages suivants:** `"Updated 2 consoles (10 new games)"`
- **Aucune nouvelle ROM:** Pas de toast (log seulement)

---

## 🚀 PROCHAINES ÉTAPES

### 1. **Toggle dans Paramètres** (À FAIRE)
- [ ] Créer SettingsActivity ou fragment
- [ ] Ajouter toggle "Auto-scan on startup"
- [ ] Lier à `GamelistScanner.setAutoScanEnabled()`

### 2. **Scan Manuel** (OPTIONNEL)
- [ ] Bouton "Scan Now" dans ConsoleManagerActivity
- [ ] Force rescan complet d'une console
- [ ] Force rescan de toutes les consoles

### 3. **Notifications** (OPTIONNEL)
- [ ] Notification en arrière-plan pendant scan
- [ ] Notification de fin avec résultats
- [ ] Action "View Results"

---

## 🐛 DÉBOGAGE

### Logs
```kotlin
TAG = "GamelistScanner"
```

**Messages clés:**
- `"First launch detected - Starting full console scan"`
- `"Auto-scan enabled - Scanning for new ROMs"`
- `"Scanning console directory: nes"`
- `"Generated gamelist for nes: 25 games"`
- `"Scan completed: 5 consoles, 150 games total"`
- `"Rescan completed: 2 consoles updated, 10 games"`

### Vérifications
1. Vérifier permissions storage
2. Vérifier que `/storage/emulated/0/GameLibrary-Data` existe
3. Vérifier logs pour erreurs de scan
4. Vérifier SharedPreferences pour état premier démarrage

---

## ✅ TESTS

### Test 1: Premier Démarrage
1. Supprimer `gamelist_prefs.xml` (ou réinitialiser app)
2. Lancer l'app
3. Vérifier ProgressDialog apparaît
4. Vérifier Toast avec résultats
5. Vérifier `gamelist.json` créés dans répertoires consoles

### Test 2: Démarrages Suivants
1. Lancer l'app (après premier démarrage)
2. Vérifier pas de ProgressDialog
3. Ajouter une nouvelle ROM
4. Relancer l'app
5. Vérifier Toast "Updated X consoles"

### Test 3: Auto-scan Désactivé
1. Désactiver auto-scan via `GamelistScanner.setAutoScanEnabled(false)`
2. Lancer l'app
3. Vérifier pas de scan (log: "Auto-scan disabled")

---

## 📚 RÉFÉRENCES

- **GamelistScanner.kt**: Gestionnaire principal
- **MainActivity.java**: Intégration au démarrage
- **GamelistManager.kt**: Génération des gamelist.json
- **ConsoleNameMapper.java**: Normalisation IDs consoles






