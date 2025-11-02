# 🔧 Guide de Compilation FCEUmm avec Fix mousedata[3]

**Date:** 2025-11-01  
**Bug corrigé:** `mousedata[3]` non défini dans `get_mouse_input()`  
**Fichier modifié:** `c:\repos\libretro-fceumm-master\src\drivers\libretro\libretro.c` (ligne 2465)

---

## ✅ FIX APPLIQUÉ

**Fichier:** `c:\repos\libretro-fceumm-master\src\drivers\libretro\libretro.c`

**Ligne 2458-2465:**
```c
if (input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED))
    mousedata[2] |= 0x1;

/* Fix: Initialize sensor state (mousedata[3])
 * Used when switchZapper != 0 (alternative mode)
 * 0 = light detected when triggered
 */
mousedata[3] = 0;
```

---

## 📋 PRÉREQUIS

1. **NDK Android** (version compatible avec votre projet)
   - Vérifier: `RetroPlay-Android/local.properties` → `ndk.dir`
   - Probablement NDK r21-r25

2. **Git** (pour cloner FCEUmm si nécessaire)

3. **CMake** (pour build système)

---

## 🔨 MÉTHODE 1: Compiler depuis c:\repos (RECOMMANDÉ)

### Étape 1: Vérifier que le fix est appliqué

```powershell
cd c:\repos\libretro-fceumm-master
Select-String -Path "src\drivers\libretro\libretro.c" -Pattern "mousedata\[3\] = 0"
```

**Devrait afficher:** Le fix avec commentaire.

### Étape 2: Vérifier le système de build

Chercher un fichier `Makefile`, `CMakeLists.txt`, ou `Makefile.libretro`:

```powershell
Get-ChildItem -Path . -Filter "*Makefile*" -Recurse | Select-Object -First 5
Get-ChildItem -Path . -Filter "*CMakeLists*" -Recurse | Select-Object -First 5
```

### Étape 3: Compiler pour Android ARM64

**Option A: Si Makefile.libretro existe:**

```bash
cd c:\repos\libretro-fceumm-master
make -f Makefile.libretro platform=android-arm64
```

**Option B: Si CMakeLists.txt existe:**

```bash
cd c:\repos\libretro-fceumm-master
mkdir build && cd build
cmake .. -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-21
make
```

**Option C: Utiliser libretro-buildbot:**

```bash
cd c:\repos\libretro-super
./libretro-buildbot.sh fceumm android-arm64
```

---

## 🔨 MÉTHODE 2: Copier le fix dans RetroPlay-Android

Si le repo FCEUmm n'a pas de système de build configuré:

1. **Créer un patch:**
   ```powershell
   cd c:\repos\libretro-fceumm-master
   git diff src\drivers\libretro\libretro.c > zapper_mousedata3_fix.patch
   ```

2. **Appliquer le patch lors de la compilation**

---

## 📦 RÉSULTAT ATTENDU

Après compilation réussie:

**Fichier généré:** `fceumm_libretro_android.so` (ou similaire)

**Emplacement cible:** 
```
RetroPlay-Android/app/src/main/jniLibs/arm64-v8a/fceumm_libretro_android.so
```

**Taille typique:** ~1-2 MB

---

## ✅ VALIDATION

### Test 1: Vérifier que le .so contient le fix

```powershell
# Chercher la chaîne "mousedata[3]" dans le .so (si strings disponibles)
strings app/src/main/jniLibs/arm64-v8a/fceumm_libretro_android.so | Select-String "mousedata"
```

### Test 2: Tester le Zapper dans Duck Hunt

1. Compiler RetroPlay avec le nouveau .so
2. Lancer Duck Hunt
3. Toucher l'écran → **Devrait tirer et faire tomber les canards!**

---

## 🐛 SI LA COMPILATION ÉCHOUE

### Erreur: "No rule to make target"

**Solution:** Vérifier le système de build:

```powershell
# Chercher des exemples de compilation Android
Get-ChildItem -Path . -Filter "*.sh" -Recurse | Select-String "android" | Select-Object -First 5
```

### Erreur: "NDK not found"

**Solution:** Configurer `local.properties`:

```properties
ndk.dir=C:\\Users\\[USER]\\AppData\\Local\\Android\\Sdk\\ndk\\[VERSION]
```

### Erreur: "CMake toolchain not found"

**Solution:** Utiliser la méthode Makefile si disponible, ou installer CMake.

---

## 📝 NOTES

- **Le fix est minimal:** Une seule ligne ajoutée (`mousedata[3] = 0;`)
- **Impact:** Seulement si `switchZapper != 0` (mode alternatif)
- **Mode normal:** Utilise `CheckColor()` donc pas affecté par ce bug
- **Sécurité:** Fix ne change pas le comportement existant, seulement initialise une valeur

---

## 🔗 RÉFÉRENCES

- **Source FCEUmm:** `c:\repos\libretro-fceumm-master`
- **Documentation bug:** `ZAPPER_MOUSEDATA_3_BUG.md`
- **Libretro buildbot:** https://buildbot.libretro.com/
- **NDK Android:** https://developer.android.com/ndk

---

**Date de création:** 2025-11-01  
**Statut:** ✅ Fix appliqué au source - En attente de compilation

