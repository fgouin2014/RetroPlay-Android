# Fix Zapper - Duck Hunt ne fonctionne pas

**Date:** 2025-11-25  
**Problème:** Impossible de tirer dans Duck Hunt  
**Status:** ✅ Corrigé

---

## 🐛 Problèmes Identifiés

### 1. Zapper Désactivé dans NativeComposeEmulatorActivity ❌

**Code avant:**
```kotlin
// ⚠️ ZAPPER DÉSACTIVÉ DANS NATIVE MODE
if (isZapperGame) {
    Toast.makeText(..., "⚠️ Duck Hunt detected!\nPlease use PLAY (not PLAY NATIVE) for Zapper games")
}
```

**Problème:** Le Zapper était complètement désactivé avec un Toast disant d'utiliser "PLAY" au lieu de "PLAY NATIVE".

---

### 2. Configuration `fceumm_zapper_mode` Manquante ❌

**Code avant:**
```kotlin
"nes" -> {
    // Configuration Zapper sera faite APRÈS la création de retroView
}
```

**Problème:** Aucune variable core n'était configurée dans `GLRetroViewData`. FCEUmm utilisait le mode par défaut `clightgun` (hardware) au lieu de `touchscreen` (touch).

**Impact:** FCEUmm ne lisait pas `RETRO_DEVICE_POINTER` car il était en mode `RetroCLightgun` au lieu de `RetroPointer`.

---

### 3. Port Non Configuré comme RETRO_DEVICE_POINTER ❌

**Problème:** Aucun appel à `setControllerType(1, 6)` pour configurer le port 2 comme `RETRO_DEVICE_POINTER`.

**Impact:** Le core ne savait pas qu'il devait lire les inputs POINTER sur le port 2.

---

### 4. Coordonnées Incorrectes ❌

**Code avant:**
```kotlin
val normalizedX = (relativeX * 2f - 1f) * 0x7fff
val normalizedY = (relativeY * 2f - 1f) * 0x7fff
retroView.sendMotionEvent(..., normalizedX / 0x7fff, normalizedY / 0x7fff, ...)
```

**Problème:** 
- Calculait `normalizedX/Y` en `[-0x7fff, +0x7fff]` puis divisait par `0x7fff` pour obtenir `[-1, 1]`
- LibretroDroid attend `[0, 1]` et fait la conversion lui-même
- `POINTER_PRESSED = (X >= 0 && Y >= 0)` donc avec `[-1, 1]` le trigger était toujours false!

**Impact:** Le trigger ne se déclenchait jamais car les coordonnées étaient négatives.

---

## ✅ Corrections Appliquées

### 1. Activation du Zapper ✅

**Code après:**
```kotlin
if (isZapperGame) {
    Handler().postDelayed({
        retroView.setControllerType(1, 6)  // RETRO_DEVICE_POINTER
        Toast.makeText(..., "Zapper detected! Touch game area to shoot")
    }, 1000)
}
```

**Résultat:** Le Zapper est maintenant activé et configuré correctement.

---

### 2. Configuration `fceumm_zapper_mode = "touchscreen"` ✅

**Code après:**
```kotlin
"nes" -> {
    if (isZapperGame) {
        val nesVariables = arrayOf(
            Variable("fceumm_zapper_mode", "touchscreen"),  // ← CRITIQUE!
            Variable("fceumm_zapper_trigger", "enabled"),
            Variable("fceumm_zapper_sensor", "enabled"),
            Variable("fceumm_show_crosshair", "enabled"),
            Variable("fceumm_zapper_tolerance", "6")
        )
        variables = nesVariables
    }
}
```

**Résultat:** FCEUmm est maintenant en mode `RetroPointer` et lit `RETRO_DEVICE_POINTER`.

---

### 3. Configuration du Port ✅

**Code après:**
```kotlin
retroView.setControllerType(1, 6)  // Port 2 (index 1) = RETRO_DEVICE_POINTER (6)
```

**Note:** En mode `RetroPointer` (touchscreen), FCEUmm lit `RETRO_DEVICE_POINTER` (6), PAS `RETRO_DEVICE_ZAPPER` (258).

**Résultat:** Le port 2 est configuré correctement pour recevoir les inputs POINTER.

---

### 4. Correction des Coordonnées ✅

**Code après:**
```kotlin
// CRITIQUE: LibretroDroid attend [0, 1] et fait la conversion [-0x7fff, +0x7fff] lui-même
val relativeX = (clampedX - bounds.left) / bounds.width  // [0, 1]
val relativeY = (clampedY - bounds.top) / bounds.height  // [0, 1]

retroView.sendMotionEvent(
    LibretroDroid.MOTION_SOURCE_POINTER,
    relativeX,  // 0.0 à 1.0 (LibretroDroid convertit)
    relativeY,  // 0.0 à 1.0
    1  // Port 2
)
```

**ACTION_UP:**
```kotlin
android.view.MotionEvent.ACTION_UP -> {
    // Release POINTER - envoyer valeurs négatives pour désactiver POINTER_PRESSED
    retroView.sendMotionEvent(..., -1f, -1f, 1)
}
```

**Résultat:** 
- Coordonnées envoyées en `[0, 1]` ✅
- `POINTER_PRESSED = (X >= 0 && Y >= 0)` = true quand touch actif ✅
- `POINTER_PRESSED = false` quand ACTION_UP avec (-1, -1) ✅

---

## 🔍 Explication Technique

### Mode RetroPointer (touchscreen)

**FCEUmm lit:**
```c
if (zappermode == RetroPointer) {
    int _x = input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_X);
    int _y = input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_Y);
    
    // TRIGGER = POINTER_PRESSED (touch actif)
    if (input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED))
        mousedata[2] |= 0x1;  // ← TIR DU ZAPPER !
}
```

**LibretroDroid définit POINTER_PRESSED:**
```cpp
// input.cpp
POINTER_PRESSED = (pointerScreenXAxis >= 0 && pointerScreenYAxis >= 0)
```

**Donc:**
- Envoyer `[0, 1]` → `POINTER_PRESSED = true` ✅
- Envoyer `[-1, 1]` → `POINTER_PRESSED = false` ❌

---

## 📋 Checklist de Test

### Avant de Tester

1. ✅ Compiler l'APK avec les corrections
2. ✅ Installer sur le device
3. ✅ Lancer Duck Hunt via "PLAY NATIVE"

### Pendant le Test

1. ✅ Vérifier les logs: `[ZAPPER] Zapper game detected EARLY`
2. ✅ Vérifier les logs: `[NES] Zapper variables set: fceumm_zapper_mode=touchscreen`
3. ✅ Vérifier les logs: `[NES] Zapper configured as RETRO_DEVICE_POINTER (6)`
4. ✅ Vérifier les logs: `[ZAPPER] Touch DOWN` avec coordonnées `[0, 1]`
5. ✅ Toucher l'écran de jeu → Vérifier que le tir fonctionne

### Logs Attendus

```
[ZAPPER] Zapper game detected EARLY: Duck Hunt
[NES] Zapper variables set: fceumm_zapper_mode=touchscreen
[NES] Zapper configured as RETRO_DEVICE_POINTER (6) on port 2 (index 1)
[ZAPPER] Touch DOWN at (960, 540) → POINTER(0.5, 0.5) on port 2
[ZAPPER] Bounds: left=0, top=0, width=1920, height=1080
[ZAPPER] Relative: (0.5, 0.5) → LibretroDroid converts to [0, 0]
[ZAPPER] Trigger on touch enabled - POINTER_PRESSED will be true
```

---

## ⚠️ Notes Importantes

### 1. Mode Native vs RetroArch

**NativeComposeEmulatorActivity (PLAY NATIVE):**
- ✅ Maintenant supporte le Zapper (corrigé)
- Utilise `RETRO_DEVICE_POINTER` (6)

**RetroArchEmulatorActivity (PLAY):**
- ✅ Supporte le Zapper (déjà fonctionnel)
- Utilise `RETRO_DEVICE_ZAPPER` (258) mais en mode `touchscreen`

### 2. Core Option Obligatoire

**IMPORTANT:** `fceumm_zapper_mode = "touchscreen"` est **OBLIGATOIRE** pour que le Zapper fonctionne avec touch.

**Valeurs possibles:**
- `lightgun` = Hardware physique ❌ (ne fonctionne pas avec touch)
- `touchscreen` = Touch devices ✅ (fonctionne avec écran tactile)
- `mouse` = Souris PC ❌ (ne fonctionne pas avec touch)

### 3. Port Configuration

**En mode RetroPointer (touchscreen):**
- Utiliser `RETRO_DEVICE_POINTER` (6) ✅
- PAS `RETRO_DEVICE_ZAPPER` (258) ❌

**En mode RetroCLightgun (hardware):**
- Utiliser `RETRO_DEVICE_ZAPPER` (258)
- Mais ce mode ne fonctionne pas avec touch

---

## 🎯 Résultat Attendu

Après ces corrections, Duck Hunt devrait fonctionner correctement:
- ✅ Toucher l'écran → Tir instantané
- ✅ Flash de l'écran au tir
- ✅ Son de coup de feu
- ✅ Canards tombent quand touchés

---

**Dernière mise à jour:** 2025-11-25


