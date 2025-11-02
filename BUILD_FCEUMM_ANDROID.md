# 🔨 Compilation FCEUmm pour Android (avec fix mousedata[3])

**Date:** 2025-11-01  
**Fix appliqué:** `mousedata[3] = 0;` dans `libretro.c` ligne 2465  
**Méthode:** ndk-build (Android NDK)

---

## ✅ PRÉREQUIS

### 1. Android NDK installé

Vérifier dans votre `local.properties`:
```properties
ndk.dir=C:\\Users\\[USER]\\AppData\\Local\\Android\\Sdk\\ndk\\[VERSION]
```

Ou télécharger depuis: https://developer.android.com/ndk/downloads

**Versions recommandées:** r21 - r25

### 2. Variables d'environnement

```powershell
# Ajouter le NDK au PATH
$env:ANDROID_NDK="C:\Users\[USER]\AppData\Local\Android\Sdk\ndk\[VERSION]"
$env:PATH="$env:ANDROID_NDK;$env:PATH"
```

### 3. Vérifier que ndk-build fonctionne

```powershell
ndk-build --version
```

**Devrait afficher:** GNU Make + version NDK

---

## 🔨 COMPILATION

### Méthode: ndk-build (RECOMMANDÉ)

```powershell
cd C:\repos\libretro-fceumm-master

# Compiler pour ARM64 (arm64-v8a)
ndk-build -j4 APP_ABI=arm64-v8a
```

**Sortie attendue:**
```
[arm64-v8a] Compile       : retro <= libretro.c
[arm64-v8a] Compile       : retro <= fceu.c
[arm64-v8a] Compile       : retro <= cart.c
...
[arm64-v8a] SharedLibrary : libretro.so
[arm64-v8a] Install       : libretro.so => libs/arm64-v8a/libretro.so
```

### Fichier généré

```
C:\repos\libretro-fceumm-master\libs\arm64-v8a\libretro.so
```

**Taille typique:** 1.5 - 2 MB

---

## 📦 INSTALLATION DANS RETROPLAY

### Étape 1: Renommer le fichier

```powershell
cd C:\repos\libretro-fceumm-master\libs\arm64-v8a
Copy-Item libretro.so fceumm_libretro_android.so
```

### Étape 2: Sauvegarder l'ancien .so

```powershell
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a

# Backup avec timestamp
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item fceumm_libretro_android.so "fceumm_libretro_android_backup_$timestamp.so"
```

### Étape 3: Copier le nouveau .so

```powershell
Copy-Item C:\repos\libretro-fceumm-master\libs\arm64-v8a\libretro.so `
          C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so -Force
```

---

## 🔍 VALIDATION

### Test 1: Vérifier la taille

```powershell
Get-Item app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so | Select-Object Name, Length, LastWriteTime
```

**Devrait être:** ~1.5-2 MB, avec timestamp récent.

### Test 2: Vérifier que le fix est présent

```powershell
# Chercher "mousedata" dans les strings du .so
strings app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so | Select-String "mousedata"
```

### Test 3: Tester dans RetroPlay

1. Compiler RetroPlay:
   ```powershell
   cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
   .\gradlew assembleDebug
   ```

2. Installer l'APK:
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

3. Lancer Duck Hunt et tester le Zapper!

---

## 🐛 RÉSOLUTION DE PROBLÈMES

### Erreur: "ndk-build: command not found"

**Solution:**
```powershell
# Trouver le chemin NDK
$ndkPath = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\ndk" -Directory | Select-Object -Last 1
$env:ANDROID_NDK = $ndkPath.FullName
$env:PATH = "$env:ANDROID_NDK;$env:PATH"
```

### Erreur: "Android.mk:7: Cannot find file '../Makefile.common'"

**Solution:** Vérifier que vous êtes dans le bon répertoire:
```powershell
cd C:\repos\libretro-fceumm-master
Test-Path Makefile.common  # Devrait retourner True
```

### Erreur: "error: unknown target ABI 'arm64-v8a'"

**Solution:** Votre NDK est trop ancien. Télécharger NDK r21+ depuis:
https://developer.android.com/ndk/downloads

### Warning: "ld: warning: shared library text segment is not shareable"

**C'est normal**, le .so fonctionne quand même.

---

## 🚀 COMPILATION POUR AUTRES ARCHITECTURES

### ARMv7 (32-bit)

```powershell
ndk-build -j4 APP_ABI=armeabi-v7a
```

**Résultat:** `libs/armeabi-v7a/libretro.so`

### x86_64

```powershell
ndk-build -j4 APP_ABI=x86_64
```

**Résultat:** `libs/x86_64/libretro.so`

### Toutes les architectures

```powershell
ndk-build -j4 APP_ABI=all
```

**Résultat:** Génère `.so` pour toutes les architectures (arm64-v8a, armeabi-v7a, x86, x86_64)

---

## 📝 SCRIPT AUTOMATIQUE

Créer `compile_fceumm.ps1`:

```powershell
# Script automatique de compilation FCEUmm

$ErrorActionPreference = "Stop"

# Chemins
$FCEUMM_DIR = "C:\repos\libretro-fceumm-master"
$RETROPLAY_JNILIBS = "C:\androidProject\ChatAI-Android-beta\RetroPlay-Android\app\src\main\jniLibs\arm64-v8a"

Write-Host "=== Compilation FCEUmm avec fix mousedata[3] ===" -ForegroundColor Cyan

# 1. Vérifier que le fix est présent
cd $FCEUMM_DIR
$fixPresent = Select-String -Path "src\drivers\libretro\libretro.c" -Pattern "mousedata\[3\] = 0"
if (!$fixPresent) {
    Write-Host "ERREUR: Fix mousedata[3] non trouvé!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Fix mousedata[3] présent" -ForegroundColor Green

# 2. Clean
Write-Host "`nNettoyage..." -ForegroundColor Yellow
ndk-build clean

# 3. Compiler
Write-Host "`nCompilation pour ARM64..." -ForegroundColor Yellow
ndk-build -j4 APP_ABI=arm64-v8a

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Compilation échouée!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Compilation réussie" -ForegroundColor Green

# 4. Vérifier le fichier
$so = "$FCEUMM_DIR\libs\arm64-v8a\libretro.so"
if (!(Test-Path $so)) {
    Write-Host "ERREUR: libretro.so non trouvé!" -ForegroundColor Red
    exit 1
}
$size = (Get-Item $so).Length / 1MB
Write-Host "[OK] libretro.so généré: $([math]::Round($size, 2)) MB" -ForegroundColor Green

# 5. Backup ancien .so
Write-Host "`nBackup ancien core..." -ForegroundColor Yellow
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backup = "$RETROPLAY_JNILIBS\fceumm_libretro_android_backup_$timestamp.so"
if (Test-Path "$RETROPLAY_JNILIBS\fceumm_libretro_android.so") {
    Copy-Item "$RETROPLAY_JNILIBS\fceumm_libretro_android.so" $backup
    Write-Host "[OK] Backup créé: fceumm_libretro_android_backup_$timestamp.so" -ForegroundColor Green
}

# 6. Copier le nouveau .so
Write-Host "`nInstallation dans RetroPlay..." -ForegroundColor Yellow
Copy-Item $so "$RETROPLAY_JNILIBS\fceumm_libretro_android.so" -Force
Write-Host "[OK] Core installé!" -ForegroundColor Green

Write-Host "`n=== COMPILATION TERMINÉE ===" -ForegroundColor Cyan
Write-Host "Prochaines étapes:" -ForegroundColor Yellow
Write-Host "  1. Compiler RetroPlay: cd RetroPlay-Android && .\gradlew assembleDebug"
Write-Host "  2. Installer l'APK: adb install -r app\build\outputs\apk\debug\app-debug.apk"
Write-Host "  3. Tester Duck Hunt avec le Zapper!"
```

**Exécution:**
```powershell
.\compile_fceumm.ps1
```

---

## 🎯 RÉSUMÉ

1. **Fix appliqué:** `mousedata[3] = 0;` dans `libretro.c`
2. **Compilation:** `ndk-build -j4 APP_ABI=arm64-v8a`
3. **Résultat:** `libs/arm64-v8a/libretro.so`
4. **Installation:** Copier vers `RetroPlay-Android/app/src/main/jniLibs/arm64-v8a/fceumm_libretro_android.so`
5. **Test:** Lancer Duck Hunt et tirer avec le Zapper!

---

**Date:** 2025-11-01  
**Statut:** ✅ Procédure de compilation documentée et prête à l'emploi

