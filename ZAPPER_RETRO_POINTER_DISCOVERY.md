# 🔥 DÉCOUVERTE MAJEURE - FCEUmm RetroPointer Mode

**Date:** 31 octobre 2025  
**Impact:** ⭐⭐⭐⭐⭐ CRITIQUE - TRIGGER ZAPPER FONCTIONNEL  
**Source:** Recherche approfondie `c:\repos\libretro-fceumm-master`  

---

## 🚨 LE PROBLÈME

**Les canards ne tombaient pas**, même avec :
- ✅ Bounds capturés correctement
- ✅ Coordonnées [0-1] envoyées correctement
- ✅ `setControllerType()` appelé
- ✅ Toast "Zapper detected"

**Symptômes:**
- Aucun flash de l'écran
- Aucun son de coup de feu
- Canards ignorent les tirs

---

## 🔍 LA DÉCOUVERTE

### Dans `c:\repos\libretro-fceumm-master\src\drivers\libretro\libretro.c`

**Ligne 163:** FCEUmm supporte 4 modes de Zapper :
```c
enum RetroZapperInputModes {
    RetroCLightgun,   // Hardware lightgun (default)
    RetroSTLightgun,  // SNES Super Scope
    RetroMouse,       // Mouse PC
    RetroPointer      // Touchscreen (Android, iOS)
};
enum RetroZapperInputModes zappermode = RetroCLightgun;  // ← MODE PAR DÉFAUT !
```

**Ligne 2073-2074:** Le mode est sélectionné via core option :
```c
else if (!strcmp(var.value, "touchscreen")) {
    zappermode = RetroPointer;  // ← MODE TOUCHSCREEN !
}
```

**Ligne 2441-2460:** En mode `RetroPointer`, FCEUmm lit `RETRO_DEVICE_POINTER` :
```c
if (zappermode == RetroPointer) {
    int _x = input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_X);
    int _y = input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_Y);
    
    // TRIGGER = POINTER_PRESSED (touch actif)
    if (input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED))
        mousedata[2] |= 0x1;  // ← TIR DU ZAPPER !
}
```

---

## ❌ NOTRE ERREUR

### Ce que nous faisions :

1. **Device Type:** `RETRO_DEVICE_ZAPPER` (258) = `SUBCLASS(MOUSE, 0)`
   - FCEUmm attendait `RETRO_DEVICE_MOUSE` (non supporté par LibretroDroid!)
   
2. **Core Option:** `fceumm_zapper_mode` = **NON configurée** (default = `clightgun`)
   - FCEUmm utilisait `RetroCLightgun` au lieu de `RetroPointer`
   
3. **Trigger:** Envoi de `KEYCODE_BUTTON_B` séparé
   - FCEUmm ignore ce bouton, il lit seulement `POINTER_PRESSED`

**RÉSULTAT:** FCEUmm ne recevait JAMAIS le trigger ni les coordonnées !

---

## ✅ LA SOLUTION

### 1. Configuration Core Variable (CRITIQUE)

**Dans `GLRetroViewData` - AVANT la création du core:**
```kotlin
"nes" -> {
    if (isZapperGame) {
        val nesVariables = arrayOf(
            Variable("fceumm_zapper_mode", "touchscreen")  // ← FORCE RetroPointer mode
        )
        variables = nesVariables
        Log.i(TAG, "[NES] Zapper variables set: fceumm_zapper_mode=touchscreen")
    }
}
```

### 2. Device Type Correct

**APRÈS le chargement du core (Handler 1s):**
```kotlin
retroView.setControllerType(1, 6)  // RETRO_DEVICE_POINTER (PAS 258 !)
```

### 3. Trigger via POINTER_PRESSED

**Dans `handleZapperTouch()`:**
```kotlin
// Envoyer seulement position - trigger automatique via POINTER_PRESSED
retroView.sendMotionEvent(
    LibretroDroid.MOTION_SOURCE_POINTER,
    relativeX,  // [0, 1]
    relativeY,  // [0, 1]
    1           // Port 2
)

// PAS DE sendKeyEvent() ! FCEUmm lit POINTER_PRESSED directement
```

---

## 📊 FLOW COMPLET

```
RetroPlay (Android)
  ↓
  1. Configure fceumm_zapper_mode = "touchscreen" (GLRetroViewData)
  ↓
FCEUmm Core Initialization
  ↓
  2. Lit core option → zappermode = RetroPointer
  ↓
  3. setControllerType(1, 6) → RETRO_DEVICE_POINTER
  ↓
RetroPlay (Touch Event)
  ↓
  4. sendMotionEvent(POINTER, x=[0,1], y=[0,1], port=1)
  ↓
LibretroDroid
  ↓
  5. Stocke dans pointerScreenXAxis/YAxis (>=0 car [0,1])
  ↓
FCEUmm input_cb() en mode RetroPointer
  ↓
  6. Lit POINTER_X → X du Zapper ✅
  7. Lit POINTER_Y → Y du Zapper ✅
  8. Lit POINTER_PRESSED → (X>=0 && Y>=0) = true → TRIGGER ✅
  ↓
  9. mousedata[2] |= 0x1 → TIR DU ZAPPER !
  ↓
  10. Canard touché → TOMBE ✅
```

---

## 🎯 POURQUOI C'ÉTAIT DIFFICILE

1. **4 modes de Zapper** dans FCEUmm (lightgun, stlightgun, mouse, touchscreen)
2. **Mode par défaut = lightgun** (attend RETRO_DEVICE_MOUSE non supporté)
3. **Documentation** ne mentionne pas que `touchscreen` active `RetroPointer`
4. **POINTER_PRESSED** interprété comme trigger (pas juste "touch actif")
5. **Calcul bizarre** dans `input.cpp` : `isXActive = X >= 0` (pas `!= -1`)

---

## 🎉 RÉSULTAT ATTENDU

Avec ce fix :
- 🔊 **Son du coup de feu** ✅
- ⚡ **Flash blanc de l'écran** ✅
- 🦆 **Canards tombent** quand touchés ✅
- 🎯 **Précision parfaite** (coordonnées exactes)

---

## 📝 COMMITS

**Fichier modifié:** `RetroPlay-Android/app/src/main/java/com/retroplay/RetroArchEmulatorActivity.kt`

**Changements:**
1. Ligne 746-759: Configuration `fceumm_zapper_mode = "touchscreen"` dans GLRetroViewData
2. Ligne 911: `setControllerType(1, 6)` POINTER (pas 258)
3. Ligne 454-461: Suppression `sendKeyEvent(BUTTON_B)` - trigger via POINTER_PRESSED
4. Ligne 466-469: Suppression `sendKeyEvent(ACTION_UP)` - release automatique

---

## 🔑 RÈGLE APPRISE ("Nos Rules")

**Leçon:** Toujours lire le code source du CORE, pas seulement libretro.h !

- `libretro.h` définit les standards généraux
- Chaque core a SES PROPRES modes et implémentations
- FCEUmm a 4 modes de Zapper, chacun lit différents device types
- La configuration core option est **AUSSI IMPORTANTE** que le device type !

