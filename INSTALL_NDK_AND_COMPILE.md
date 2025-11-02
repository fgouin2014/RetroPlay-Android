# 🔧 Installation NDK et Compilation FCEUmm

**Date:** 2025-11-01  
**Problème:** NDK Android non installé  
**Solution:** Installer NDK via Android Studio et compiler FCEUmm

---

## 🚨 SITUATION ACTUELLE

Le dossier NDK existe mais est vide:
```
C:\Users\Quentin\AppData\Local\Android\Sdk\ndk\
```

**Résultat:** `ndk-build` n'est pas disponible → Impossible de compiler FCEUmm.

---

## ✅ SOLUTION 1: Installer NDK via Android Studio (RECOMMANDÉ)

### Étape 1: Ouvrir Android Studio

1. Lancer Android Studio
2. Cliquer sur **File** → **Settings** (ou **Ctrl+Alt+S**)

### Étape 2: Accéder aux SDK Tools

1. Dans le menu de gauche: **Appearance & Behavior** → **System Settings** → **Android SDK**
2. Cliquer sur l'onglet **SDK Tools**

### Étape 3: Installer NDK

1. Cocher **NDK (Side by side)**
2. Cocher **CMake** (optionnel mais utile)
3. Cliquer sur **Apply** → **OK**
4. Attendre le téléchargement et l'installation (~500 MB)

### Étape 4: Vérifier l'installation

```powershell
Get-ChildItem "C:\Users\Quentin\AppData\Local\Android\Sdk\ndk" -Directory
```

**Devrait afficher:** Une version comme `25.1.8937393` ou similaire.

---

## ✅ SOLUTION 2: Télécharger NDK manuellement

### Option A: Depuis le site officiel

1. Télécharger depuis: https://developer.android.com/ndk/downloads
2. Télécharger **NDK r25c** (Windows 64-bit) → `android-ndk-r25c-windows.zip` (~1 GB)
3. Extraire dans: `C:\Users\Quentin\AppData\Local\Android\Sdk\ndk\`
4. Renommer le dossier: `android-ndk-r25c` → `25.1.8937393` (ou garder le nom original)

### Option B: Via SDK Manager en ligne de commande

```powershell
cd C:\Users\Quentin\AppData\Local\Android\Sdk\cmdline-tools\latest\bin
.\sdkmanager.bat "ndk;25.1.8937393"
```

---

## 🔨 APRÈS INSTALLATION: Compiler FCEUmm

### Étape 1: Trouver la version NDK installée

```powershell
$ndkPath = Get-ChildItem "C:\Users\Quentin\AppData\Local\Android\Sdk\ndk" -Directory | Select-Object -First 1
Write-Host "NDK installé: $($ndkPath.FullName)"
```

### Étape 2: Ajouter NDK au PATH

```powershell
$ndkPath = Get-ChildItem "C:\Users\Quentin\AppData\Local\Android\Sdk\ndk" -Directory | Select-Object -First 1
$env:ANDROID_NDK = $ndkPath.FullName
$env:PATH = "$env:ANDROID_NDK;$env:PATH"
```

### Étape 3: Vérifier ndk-build

```powershell
ndk-build --version
```

**Devrait afficher:** 
```
GNU Make 4.x
...
```

### Étape 4: Compiler FCEUmm

```powershell
cd C:\repos\libretro-fceumm-master

# Nettoyer
ndk-build clean

# Compiler pour ARM64
ndk-build -j4 APP_ABI=arm64-v8a
```

**Sortie attendue:**
```
[arm64-v8a] Compile       : retro <= libretro.c
[arm64-v8a] Compile       : retro <= fceu.c
...
[arm64-v8a] SharedLibrary : libretro.so
[arm64-v8a] Install       : libretro.so => libs/arm64-v8a/libretro.so
```

### Étape 5: Vérifier le résultat

```powershell
Test-Path "C:\repos\libretro-fceumm-master\libs\arm64-v8a\libretro.so"
```

**Devrait retourner:** `True`

### Étape 6: Installer dans RetroPlay

```powershell
# Backup
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item "C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so" `
          "C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android_backup_$timestamp.so"

# Installer nouveau .so
Copy-Item "C:\repos\libretro-fceumm-master\libs\arm64-v8a\libretro.so" `
          "C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so" -Force
```

---

## 🚀 SCRIPT AUTOMATIQUE COMPLET

Créer `setup_ndk_and_compile.ps1`:

```powershell
# Script d'installation NDK et compilation FCEUmm
$ErrorActionPreference = "Stop"

Write-Host "=== Setup NDK & Compilation FCEUmm ===" -ForegroundColor Cyan

# 1. Vérifier si NDK est installé
Write-Host "`n[1/7] Vérification NDK..." -ForegroundColor Yellow
$ndkDirs = Get-ChildItem "C:\Users\Quentin\AppData\Local\Android\Sdk\ndk" -Directory -ErrorAction SilentlyContinue
if (!$ndkDirs) {
    Write-Host "ERREUR: NDK non installé!" -ForegroundColor Red
    Write-Host "Installer NDK via Android Studio (SDK Tools → NDK)" -ForegroundColor Yellow
    Write-Host "Ou télécharger depuis: https://developer.android.com/ndk/downloads" -ForegroundColor Yellow
    exit 1
}

$ndkPath = $ndkDirs | Select-Object -First 1
Write-Host "[OK] NDK trouvé: $($ndkPath.Name)" -ForegroundColor Green

# 2. Configurer PATH
Write-Host "`n[2/7] Configuration PATH..." -ForegroundColor Yellow
$env:ANDROID_NDK = $ndkPath.FullName
$env:PATH = "$env:ANDROID_NDK;$env:PATH"
Write-Host "[OK] ANDROID_NDK=$env:ANDROID_NDK" -ForegroundColor Green

# 3. Vérifier ndk-build
Write-Host "`n[3/7] Vérification ndk-build..." -ForegroundColor Yellow
$ndkBuild = Join-Path $env:ANDROID_NDK "ndk-build.cmd"
if (!(Test-Path $ndkBuild)) {
    Write-Host "ERREUR: ndk-build.cmd non trouvé!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] ndk-build.cmd trouvé" -ForegroundColor Green

# 4. Vérifier le fix FCEUmm
Write-Host "`n[4/7] Vérification fix FCEUmm..." -ForegroundColor Yellow
cd C:\repos\libretro-fceumm-master
$fixPresent = Select-String -Path "src\drivers\libretro\libretro.c" -Pattern "mousedata\[3\] = 0"
if (!$fixPresent) {
    Write-Host "AVERTISSEMENT: Fix mousedata[3] non trouvé!" -ForegroundColor Yellow
    Write-Host "Le fix sera appliqué automatiquement..." -ForegroundColor Yellow
    
    # Appliquer le fix
    $content = Get-Content "src\drivers\libretro\libretro.c" -Raw
    $content = $content -replace '(if \(input_cb\(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED\)\)\r?\n\s+mousedata\[2\] \|= 0x1;)',
                                 ('$1' + "`r`n      `r`n      /* Fix: Initialize sensor state (mousedata[3])`r`n       * Used when switchZapper != 0 (alternative mode)`r`n       * 0 = light detected when triggered`r`n       */`r`n      mousedata[3] = 0;")
    Set-Content "src\drivers\libretro\libretro.c" $content -NoNewline
    Write-Host "[OK] Fix appliqué automatiquement" -ForegroundColor Green
} else {
    Write-Host "[OK] Fix mousedata[3] présent" -ForegroundColor Green
}

# 5. Nettoyer
Write-Host "`n[5/7] Nettoyage..." -ForegroundColor Yellow
& $ndkBuild clean
Write-Host "[OK] Nettoyage terminé" -ForegroundColor Green

# 6. Compiler
Write-Host "`n[6/7] Compilation pour ARM64..." -ForegroundColor Yellow
& $ndkBuild -j4 APP_ABI=arm64-v8a

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Compilation échouée!" -ForegroundColor Red
    exit 1
}

$so = "libs\arm64-v8a\libretro.so"
if (!(Test-Path $so)) {
    Write-Host "ERREUR: libretro.so non généré!" -ForegroundColor Red
    exit 1
}
$size = (Get-Item $so).Length / 1MB
Write-Host "[OK] Compilation réussie: $([math]::Round($size, 2)) MB" -ForegroundColor Green

# 7. Installer dans RetroPlay
Write-Host "`n[7/7] Installation dans RetroPlay..." -ForegroundColor Yellow
$retroplayJni = "C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a"

# Backup
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
if (Test-Path "$retroplayJni\fceumm_libretro_android.so") {
    Copy-Item "$retroplayJni\fceumm_libretro_android.so" "$retroplayJni\fceumm_libretro_android_backup_$timestamp.so"
    Write-Host "[OK] Backup: fceumm_libretro_android_backup_$timestamp.so" -ForegroundColor Green
}

# Copier
Copy-Item $so "$retroplayJni\fceumm_libretro_android.so" -Force
Write-Host "[OK] Core installé!" -ForegroundColor Green

Write-Host "`n=== TERMINÉ ===" -ForegroundColor Cyan
Write-Host "Prochaines étapes:" -ForegroundColor Yellow
Write-Host "  1. cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android"
Write-Host "  2. .\gradlew assembleDebug"
Write-Host "  3. adb install -r app\build\outputs\apk\debug\app-debug.apk"
Write-Host "  4. Tester Duck Hunt!"
```

**Exécution:**
```powershell
.\setup_ndk_and_compile.ps1
```

---

## 🎯 RÉSUMÉ

1. **Installer NDK:** Via Android Studio (SDK Tools → NDK)
2. **Vérifier:** `Get-ChildItem "C:\Users\Quentin\AppData\Local\Android\Sdk\ndk" -Directory`
3. **Compiler:** `ndk-build -j4 APP_ABI=arm64-v8a`
4. **Installer:** Copier `libretro.so` vers RetroPlay
5. **Tester:** Duck Hunt avec Zapper!

---

**Date:** 2025-11-01  
**Statut:** 📋 Guide complet - NDK requis pour compilation

