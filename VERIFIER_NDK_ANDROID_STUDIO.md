# 🔍 Vérifier NDK dans Android Studio

**Date:** 2025-11-01  
**Objectif:** Vérifier si NDK est installé et l'installer si nécessaire

---

## 📋 ÉTAPES

### 1. Ouvrir Android Studio

Lancer Android Studio (n'importe quel projet)

### 2. Accéder aux Settings

**Deux méthodes:**

**Méthode A:** Menu
- **File** → **Settings** (sur Windows/Linux)
- Ou **Android Studio** → **Preferences** (sur Mac)

**Méthode B:** Raccourci clavier
- Windows/Linux: `Ctrl + Alt + S`
- Mac: `Cmd + ,`

### 3. Naviguer vers SDK Tools

Dans la fenêtre Settings:

1. **Panneau gauche:** Cliquer sur:
   ```
   Appearance & Behavior
     → System Settings
       → Android SDK
   ```

2. **En haut:** Cliquer sur l'onglet **"SDK Tools"**

### 4. Vérifier NDK

Dans la liste des outils, chercher:

```
☐ NDK (Side by side)
```

**Cas 1: Case COCHÉE ✅**
```
☑ NDK (Side by side)  [Version: 25.x.xxxx ou similaire]
```
→ NDK est installé! Notez la version.

**Cas 2: Case DÉCOCHÉE ❌**
```
☐ NDK (Side by side)
```
→ NDK n'est PAS installé.

### 5. Installer NDK (si absent)

1. **Cocher** la case `☐ NDK (Side by side)`
2. Optionnel: Cocher aussi `☐ CMake` (utile pour builds natifs)
3. Cliquer sur **"Apply"** en bas à droite
4. Cliquer sur **"OK"** dans la fenêtre de confirmation
5. Attendre le téléchargement (~500 MB - 1 GB)
6. Cliquer sur **"Finish"** quand terminé

### 6. Vérifier l'installation

Après installation, vérifier le chemin:

**Dans Android Studio:**
- Toujours dans **SDK Tools**
- Le chemin s'affiche en haut: `Android SDK Location: C:\Users\Quentin\AppData\Local\Android\Sdk`
- Le NDK sera dans: `[SDK Location]\ndk\[version]\`

**Exemple:**
```
C:\Users\Quentin\AppData\Local\Android\Sdk\ndk\25.1.8937393\
```

---

## 🔍 VÉRIFICATION PAR POWERSHELL

Après installation, vérifier dans PowerShell:

```powershell
# Chemin vers SDK (depuis Settings → Android SDK)
$SDK_DIR = "C:\Users\Quentin\AppData\Local\Android\Sdk"

# Lister les versions NDK installées
Get-ChildItem "$SDK_DIR\ndk" -Directory | Select-Object Name, FullName

# Vérifier ndk-build
$ndkPath = Get-ChildItem "$SDK_DIR\ndk" -Directory | Select-Object -First 1
$ndkBuild = Join-Path $ndkPath.FullName "ndk-build.cmd"
Test-Path $ndkBuild
```

**Devrait afficher:**
```
Name           FullName
----           --------
25.1.8937393   C:\Users\Quentin\AppData\Local\Android\Sdk\ndk\25.1.8937393

True
```

---

## 📸 CAPTURES D'ÉCRAN (Repères visuels)

### Settings → Android SDK → SDK Tools

```
┌─────────────────────────────────────────────────┐
│  SDK Platforms  |  SDK Tools  | SDK Update Sites│
├─────────────────────────────────────────────────┤
│                                                 │
│  ☑ Android SDK Build-Tools 34                  │
│  ☑ Android SDK Command-line Tools              │
│  ☑ Android Emulator                            │
│  ☑ Android SDK Platform-Tools                  │
│  ☐ CMake                          ← Optionnel  │
│  ☑ Google Play services                        │
│  ☐ NDK (Side by side)             ← IMPORTANT! │
│                                                 │
│  [X] Show Package Details                      │
│                                                 │
└─────────────────────────────────────────────────┘
        [Cancel]  [Apply]  [OK]
```

### Avec NDK installé:

```
┌─────────────────────────────────────────────────┐
│  ☑ NDK (Side by side)                          │
│    ☑ 25.1.8937393                              │
│    ☐ 23.1.7779620  (si plusieurs versions)     │
└─────────────────────────────────────────────────┘
```

---

## ⚡ RACCOURCIS UTILES

| Action | Windows/Linux | Mac |
|--------|---------------|-----|
| Settings | `Ctrl + Alt + S` | `Cmd + ,` |
| Recherche Settings | Taper "SDK" après ouverture | Taper "SDK" |

---

## 🐛 PROBLÈMES COURANTS

### "Apply" est grisé

**Cause:** Aucun changement détecté

**Solution:** Cocher/décocher une case pour activer "Apply"

### "Download interrupted"

**Cause:** Connexion instable

**Solution:** 
1. Cliquer sur "Retry"
2. Ou fermer Settings et recommencer

### "Insufficient disk space"

**Cause:** Pas assez d'espace (NDK = ~500 MB)

**Solution:** Libérer de l'espace disque

### NDK installé mais pas trouvé

**Cause:** Plusieurs SDK installés

**Solution:** Vérifier le chemin exact dans:
- **File** → **Project Structure** → **SDK Location**

---

## ✅ APRÈS INSTALLATION

Une fois NDK installé, vous pouvez:

### 1. Compiler FCEUmm

```powershell
# Configurer PATH
$SDK_DIR = "C:\Users\Quentin\AppData\Local\Android\Sdk"
$ndkPath = Get-ChildItem "$SDK_DIR\ndk" -Directory | Select-Object -First 1
$env:ANDROID_NDK = $ndkPath.FullName
$env:PATH = "$env:ANDROID_NDK;$env:PATH"

# Compiler
cd C:\repos\libretro-fceumm-master
ndk-build -j4 APP_ABI=arm64-v8a
```

### 2. Vérifier le résultat

```powershell
Test-Path "C:\repos\libretro-fceumm-master\libs\arm64-v8a\libretro.so"
```

---

**Date:** 2025-11-01  
**Statut:** ✅ Guide complet pour Android Studio

