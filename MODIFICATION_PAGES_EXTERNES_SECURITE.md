# ⚠️ PROCÉDURE SÉCURISÉE - Modification des Pages Externes

**IMPORTANT:** Les pages externes (HTML/JS sur le device) sont servies directement par le WebServer. Une erreur peut **casser complètement l'émulation WASM** pour tous les jeux!

---

## 🚨 RÈGLES D'OR

### ❌ NE JAMAIS:

1. **Modifier directement sur le device sans backup**
   - Toujours faire un backup AVANT toute modification

2. **Utiliser les assets comme pages de production**
   - Les assets sont pour les versions par défaut embarquées dans l'APK
   - Les modifications se font UNIQUEMENT dans les fichiers sur le device

3. **Toucher à GameLibrary-Data/data/**
   - C'est l'installation officielle d'EmulatorJS
   - NE JAMAIS MODIFIER ces fichiers
   - Réinstallation complète nécessaire si corrompu

4. **Mélanger les ports**
   - ChatAI = Port 8888
   - GameLibrary = Port 9999
   - RetroPlay = Port 7777

5. **Éditer avec un éditeur qui change l'encodage**
   - Toujours UTF-8 sans BOM
   - Vérifier les fins de lignes (LF, pas CRLF si possible)

---

## ✅ PROCÉDURE OBLIGATOIRE

### 1️⃣ BACKUP SYSTÉMATIQUE

**AVANT TOUTE MODIFICATION:**

```powershell
# Date au format YYYYMMDD_HHMM
$timestamp = Get-Date -Format "yyyyMMdd_HHmm"

# Pour RetroPlay
adb pull /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/index.html `
         "backups/backup_retroplay_index_${timestamp}.html"

adb pull /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/emulator.html `
         "backups/backup_retroplay_emulator_${timestamp}.html"

# Pour ChatAI
adb pull /storage/emulated/0/ChatAI-Files/sites/gamelibrary/index.html `
         "backups/backup_chatai_index_${timestamp}.html"

adb pull /storage/emulated/0/ChatAI-Files/sites/gamelibrary/emulator.html `
         "backups/backup_chatai_emulator_${timestamp}.html"

# Pour GameLibrary
adb pull /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/index.html `
         "backups/backup_gamelibrary_index_${timestamp}.html"

adb pull /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/emulator.html `
         "backups/backup_gamelibrary_emulator_${timestamp}.html"
```

**Créer le dossier backups/ s'il n'existe pas:**
```powershell
New-Item -ItemType Directory -Force -Path "backups"
```

---

### 2️⃣ MODIFICATION LOCALE

**Workflow sécurisé:**

```powershell
# 1. Copier depuis le device vers on_device/ (environnement de dev)
adb pull /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/index.html `
         on_device/RetroPlay-Files/sites/gamelibrary/index.html

# 2. Éditer localement dans on_device/
notepad on_device/RetroPlay-Files/sites/gamelibrary/index.html

# 3. Tester localement si possible (ouvrir dans navigateur)
# Vérifier: syntaxe HTML, scripts JS, liens
```

---

### 3️⃣ VALIDATION AVANT DÉPLOIEMENT

**Checklist obligatoire:**

- [ ] **Syntaxe HTML valide**
  - Balises fermées correctement
  - Pas de caractères spéciaux non échappés

- [ ] **JavaScript sans erreurs**
  - Vérifier dans la console du navigateur
  - Pas d'erreurs de syntaxe

- [ ] **Ports corrects**
  - RetroPlay: 7777
  - ChatAI: 8888
  - GameLibrary: 9999

- [ ] **Chemins corrects**
  - `/gamedata/` pour accéder aux ROMs
  - `/gamelibrary/` pour les loaders
  - Pas de chemins hardcodés avec IP

- [ ] **Encodage UTF-8**
  - Sans BOM
  - Caractères spéciaux corrects

---

### 4️⃣ DÉPLOIEMENT PROGRESSIF

**Déployer sur UNE app à la fois:**

```powershell
# 1. Déployer sur RetroPlay seulement
adb push on_device/RetroPlay-Files/sites/gamelibrary/index.html `
         /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/

# 2. Tester immédiatement
# Ouvrir RetroPlay, naviguer vers la game library

# 3. Si OK, déployer sur les autres apps
# Si ERREUR, restaurer depuis backup (voir section 5)
```

---

### 5️⃣ RESTAURATION EN CAS D'ERREUR

**Si quelque chose ne fonctionne plus:**

```powershell
# Trouver le dernier backup
Get-ChildItem backups/ | Sort-Object LastWriteTime -Descending | Select-Object -First 5

# Restaurer le backup (exemple: backup_retroplay_index_20251026_1345.html)
adb push backups/backup_retroplay_index_20251026_1345.html `
         /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/index.html

# Redémarrer l'app
adb shell am force-stop com.retroplay
adb shell am start -n com.retroplay/.GameListActivity
```

---

## 📋 CHECKLIST PAR TYPE DE MODIFICATION

### Modification de index.html (Bibliothèque)

**Points critiques:**

1. **Sélecteur de console**
   ```javascript
   const consoleSelector = document.getElementById('consoleSelector');
   // Vérifier que les IDs correspondent aux dossiers dans GameLibrary-Data/
   ```

2. **URLs de chargement**
   ```javascript
   const url = `http://${serverIP}:${PORT}/gamedata/${console}/gamelist.json`;
   // PORT doit être correct pour l'app
   ```

3. **Liens vers emulator.html**
   ```javascript
   window.open(`./emulator.html?slug=${slug}&console=${console}`, '_blank');
   // Chemin relatif recommandé
   ```

**Test après modification:**
- [ ] Le sélecteur de console s'affiche
- [ ] Les jeux se chargent pour chaque console
- [ ] Cliquer sur un jeu ouvre l'émulateur
- [ ] Les images de jaquette s'affichent

---

### Modification de emulator.html (Émulateur)

**Points critiques:**

1. **Port du serveur**
   ```javascript
   const port = '7777'; // ATTENTION: Doit correspondre à l'app!
   ```

2. **Chargement du loader**
   ```javascript
   loaderScript.src = `http://${serverIP}:${port}/gamelibrary/loader_${gameConsole}.js`;
   ```

3. **Container de jeu**
   ```html
   <div id="game"></div>
   <!-- Ne PAS changer l'ID, utilisé par EmulatorJS -->
   ```

**Test après modification:**
- [ ] L'émulateur se charge (écran noir = bon signe)
- [ ] Le loader spécifique se charge (pas d'erreur 404)
- [ ] Le jeu démarre après le chargement
- [ ] Les contrôles répondent
- [ ] Le fullscreen fonctionne

---

### Modification d'un loader (loader_*.js)

**Points critiques:**

1. **Nom du core**
   ```javascript
   window.EJS_core = "nes"; // Doit correspondre au core disponible
   ```

2. **Path to data**
   ```javascript
   window.EJS_pathtodata = `http://${serverIP}:${port}/gamedata/data/`;
   // /gamedata/ pointe vers GameLibrary-Data/data/
   ```

3. **Game URL**
   ```javascript
   window.EJS_gameUrl = `http://${serverIP}:${port}/gamedata/${CONSOLE}/${gameFile}`;
   // CONSOLE doit correspondre au dossier (nes, psx, etc.)
   ```

4. **Génération de slug**
   ```javascript
   function generateSlug(name) {
       return name.toLowerCase()
           .replace(/['"`]/g, '')
           .replace(/[^a-z0-9\s-]/g, '')
           .replace(/\s+/g, '-')
           .replace(/-+/g, '-')
           .replace(/^-|-$/g, '');
   }
   // Ne PAS modifier - utilisé pour matcher les jeux
   ```

**Test après modification:**
- [ ] Le core se charge (logs dans console navigateur)
- [ ] Le ROM se télécharge (vérifier Network dans DevTools)
- [ ] Le jeu démarre après "Start Game"
- [ ] Pas d'erreur "Core not found"
- [ ] Pas d'erreur "BIOS missing" (si BIOS requis)

---

## 🔍 DIAGNOSTIC DES PROBLÈMES

### Symptôme: Page blanche

**Causes possibles:**
1. Erreur de syntaxe HTML/JS
2. Port incorrect
3. Fichier corrompu

**Solution:**
```powershell
# 1. Vérifier les logs
adb logcat WebServer:I "*:E"

# 2. Vérifier le Content-Type servi
curl -I http://[IP]:7777/gamelibrary/index.html

# 3. Restaurer depuis backup
adb push backups/backup_retroplay_index_[TIMESTAMP].html ...
```

---

### Symptôme: Jeux ne se chargent pas

**Causes possibles:**
1. Mauvais port dans les URLs
2. Chemin `/gamedata/` incorrect
3. gamelist.json introuvable

**Solution:**
```powershell
# 1. Vérifier que les ROMs existent
adb shell "ls /storage/emulated/0/GameLibrary-Data/nes/"

# 2. Vérifier l'URL dans le code
# Doit être: http://[IP]:7777/gamedata/nes/gamelist.json

# 3. Tester l'URL manuellement
curl http://[IP]:7777/gamedata/nes/gamelist.json
```

---

### Symptôme: "Console not supported"

**Causes possibles:**
1. loader_*.js introuvable
2. Nom de console incorrect
3. Loader corrompu

**Solution:**
```powershell
# 1. Vérifier que le loader existe
adb shell "ls /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/loader_*.js"

# 2. Vérifier l'URL du loader
# Doit être: http://[IP]:7777/gamelibrary/loader_nes.js

# 3. Tester l'accès au loader
curl http://[IP]:7777/gamelibrary/loader_nes.js
```

---

### Symptôme: EmulatorJS ne démarre pas

**Causes possibles:**
1. GameLibrary-Data/data/ manquant
2. Cores WASM absents
3. BIOS manquant (PSX, GBA, NDS)

**Solution:**
```powershell
# 1. Vérifier que EmulatorJS existe
adb shell "ls /storage/emulated/0/GameLibrary-Data/data/"

# 2. Vérifier les cores
adb shell "ls /storage/emulated/0/GameLibrary-Data/data/cores/"

# 3. Vérifier le BIOS (si requis)
adb shell "ls /storage/emulated/0/GameLibrary-Data/data/bios/scph5501.bin"

# 4. Réinstaller EmulatorJS si nécessaire (depuis les assets de l'app)
```

---

## 🛠️ OUTILS DE DÉVELOPPEMENT

### Script de backup automatique

**Fichier: `backup_pages_externes.ps1`**

```powershell
# Créer un backup complet de toutes les pages externes
$timestamp = Get-Date -Format "yyyyMMdd_HHmm"
$backupDir = "backups/$timestamp"
New-Item -ItemType Directory -Force -Path $backupDir

Write-Host "=== BACKUP DES PAGES EXTERNES ===" -ForegroundColor Cyan
Write-Host "Timestamp: $timestamp" -ForegroundColor Yellow

# RetroPlay
Write-Host "`nBackup RetroPlay..." -ForegroundColor Green
adb pull /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/ "$backupDir/RetroPlay/"

# ChatAI
Write-Host "Backup ChatAI..." -ForegroundColor Green
adb pull /storage/emulated/0/ChatAI-Files/sites/gamelibrary/ "$backupDir/ChatAI/"

# GameLibrary
Write-Host "Backup GameLibrary..." -ForegroundColor Green
adb pull /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/ "$backupDir/GameLibrary/"

Write-Host "`n✅ Backup terminé dans: $backupDir" -ForegroundColor Green
```

**Usage:**
```powershell
.\backup_pages_externes.ps1
```

---

### Script de restauration

**Fichier: `restore_pages_externes.ps1`**

```powershell
param(
    [Parameter(Mandatory=$true)]
    [string]$BackupTimestamp,
    
    [Parameter(Mandatory=$true)]
    [ValidateSet("RetroPlay", "ChatAI", "GameLibrary", "All")]
    [string]$App
)

$backupDir = "backups/$BackupTimestamp"

if (-not (Test-Path $backupDir)) {
    Write-Host "❌ Backup introuvable: $backupDir" -ForegroundColor Red
    exit 1
}

Write-Host "=== RESTAURATION DEPUIS BACKUP ===" -ForegroundColor Cyan
Write-Host "Timestamp: $BackupTimestamp" -ForegroundColor Yellow
Write-Host "App: $App" -ForegroundColor Yellow

if ($App -eq "RetroPlay" -or $App -eq "All") {
    Write-Host "`nRestauration RetroPlay..." -ForegroundColor Green
    adb push "$backupDir/RetroPlay/" /storage/emulated/0/RetroPlay-Files/sites/gamelibrary/
}

if ($App -eq "ChatAI" -or $App -eq "All") {
    Write-Host "Restauration ChatAI..." -ForegroundColor Green
    adb push "$backupDir/ChatAI/" /storage/emulated/0/ChatAI-Files/sites/gamelibrary/
}

if ($App -eq "GameLibrary" -or $App -eq "All") {
    Write-Host "Restauration GameLibrary..." -ForegroundColor Green
    adb push "$backupDir/GameLibrary/" /storage/emulated/0/GameLibrary-Files/sites/gamelibrary/
}

Write-Host "`n✅ Restauration terminée" -ForegroundColor Green
```

**Usage:**
```powershell
# Restaurer RetroPlay depuis le backup du 26 octobre à 13h45
.\restore_pages_externes.ps1 -BackupTimestamp "20251026_1345" -App "RetroPlay"

# Restaurer toutes les apps
.\restore_pages_externes.ps1 -BackupTimestamp "20251026_1345" -App "All"
```

---

### Script de validation

**Fichier: `validate_pages_externes.ps1`**

```powershell
param(
    [Parameter(Mandatory=$true)]
    [string]$FilePath
)

Write-Host "=== VALIDATION FICHIER ===" -ForegroundColor Cyan
Write-Host "Fichier: $FilePath" -ForegroundColor Yellow

# Vérifier existence
if (-not (Test-Path $FilePath)) {
    Write-Host "❌ Fichier introuvable" -ForegroundColor Red
    exit 1
}

# Vérifier encodage
$encoding = [System.IO.File]::ReadAllText($FilePath).GetType().Name
Write-Host "`nEncodage: $encoding" -ForegroundColor Green

# Vérifier taille
$size = (Get-Item $FilePath).Length
Write-Host "Taille: $size bytes" -ForegroundColor Green

# Vérifier syntaxe HTML/JS basique
$content = Get-Content $FilePath -Raw

# Balises HTML
if ($FilePath -like "*.html") {
    $openTags = ([regex]::Matches($content, "<(\w+)")).Count
    $closeTags = ([regex]::Matches($content, "</(\w+)>")).Count
    Write-Host "`nBalises ouvrantes: $openTags" -ForegroundColor Yellow
    Write-Host "Balises fermantes: $closeTags" -ForegroundColor Yellow
    
    if ($openTags -ne $closeTags) {
        Write-Host "⚠️ Nombre de balises différent - Vérifier!" -ForegroundColor Red
    }
}

# Recherche de ports
$ports = [regex]::Matches($content, ":(\d{4,5})")
Write-Host "`nPorts trouvés:" -ForegroundColor Yellow
foreach ($match in $ports) {
    Write-Host "  - $($match.Groups[1].Value)" -ForegroundColor Cyan
}

# Recherche de chemins
$paths = [regex]::Matches($content, "(ChatAI-Files|GameLibrary-Files|RetroPlay-Files)")
Write-Host "`nChemins trouvés:" -ForegroundColor Yellow
foreach ($match in $paths) {
    Write-Host "  - $($match.Value)" -ForegroundColor Cyan
}

Write-Host "`n✅ Validation terminée" -ForegroundColor Green
```

**Usage:**
```powershell
.\validate_pages_externes.ps1 -FilePath "on_device/RetroPlay-Files/sites/gamelibrary/index.html"
```

---

## 📚 DOCUMENTATION DE RÉFÉRENCE

### Fichiers à consulter:

1. **EMULATORJS_PAGES_EXTERNES_GUIDE_COMPLET.md**
   - Guide complet sur l'organisation des pages
   - Templates de loaders
   - Consoles supportées

2. **WEBSERVER_ROUTES_FLOW.md**
   - Flux des routes du WebServer
   - Mapping des chemins
   - Logique de serving des fichiers

3. **ARCHITECTURE_2_APPS.md**
   - Architecture globale
   - Partage des données
   - Différences entre apps

---

## ⚠️ CAS PARTICULIERS

### PSX: BIOS obligatoire

Si vous modifiez `loader_psx.js`, **ne touchez JAMAIS** à:
```javascript
window.EJS_biosUrl = `http://${serverIP}:${port}/gamedata/data/bios/scph5501.bin`;
```

Ce fichier DOIT exister dans `GameLibrary-Data/data/bios/`.

---

### PSP: Contrôles complexes

PSP utilise des contrôles spécifiques dans `pspcontrol.js` et `pspcontroldpad.js`.

**Ne modifiez ces fichiers que si:**
- Les contrôles ne fonctionnent pas
- Vous savez exactement ce que vous faites
- Vous avez fait un backup

---

### Arcade (MAME/FBNeo): ROM sets

Les ROMs arcade sont des **ZIP qu'il ne faut PAS décompresser**.

Dans le loader, assurez-vous que:
```javascript
// PAS de décompression automatique
window.EJS_gameUrl = `http://${serverIP}:${port}/gamedata/arcade/${gameFile}`;
// gameFile reste en .zip
```

---

## 🎯 RÉSUMÉ - WORKFLOW COMPLET

```
1. BACKUP
   ↓
2. ÉDITION LOCALE (on_device/)
   ↓
3. VALIDATION (syntax, ports, chemins)
   ↓
4. DÉPLOIEMENT SUR 1 APP
   ↓
5. TEST IMMÉDIAT
   ↓
6. SI OK → Déployer sur autres apps
   SI ERREUR → Restaurer backup
```

---

**DATE DE CRÉATION:** 28 octobre 2025  
**VERSION:** 1.0  
**PROJETS CONCERNÉS:** RetroPlay-Android, ChatAI-Android, GameLibrary-Android

---

**⚠️ TOUJOURS FAIRE UN BACKUP AVANT TOUTE MODIFICATION!**

