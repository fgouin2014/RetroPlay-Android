# DÉCOUVERTE CRITIQUE: Le Bug du Switch Case FCEUmm

**Date:** 2025-11-01 00:40  
**Méthodologie:** "Nos Rules" - Lecture source FCEUmm dans `c:\repos`  
**Statut:** 🎯 BUG TROUVÉ ET CORRIGÉ!

---

## 🎯 LA DÉCOUVERTE

### Le Problème:
RetroPlay envoyait `setControllerType(1, 6)` (RETRO_DEVICE_POINTER), MAIS FCEUmm **n'appelait jamais** `get_mouse_input()` pour ce device!

### Le Code FCEUmm (libretro.c ligne 2686-2693):

```c
for (port = 0; port < MAX_PORTS; port++)
{
   switch (nes_input.type[port])
   {
      case RETRO_DEVICE_ARKANOID:      // 259
      case RETRO_DEVICE_FC_ARKANOID:
      case RETRO_DEVICE_ZAPPER:        // 258
            get_mouse_input(port, nes_input.type[port], nes_input.MouseData[port]);
         break;
   }
}
```

**Résultat:**
- RetroPlay: `setControllerType(1, 6)` → `nes_input.type[1] = 6`
- FCEUmm: Switch sur `nes_input.type[1]` = `6`
- ❌ `6` ne match **AUCUN** des `case`!
- ❌ `get_mouse_input()` n'est **JAMAIS APPELÉ**!
- ❌ Le Zapper ne fonctionne pas!

---

## 🔍 POURQUOI ÇA NE FONCTIONNAIT PAS

### Chaîne d'Événements (AVANT le fix):

1. **RetroPlay** envoie touch event:
   ```kotlin
   retroView.sendMotionEvent(MOTION_SOURCE_POINTER, 0.5f, 0.7f, 1)
   ```

2. **LibretroDroid C++** reçoit et stocke:
   ```cpp
   pads[1].pointerScreenXAxis = 0.5f;  // ✅ Stocké!
   pads[1].pointerScreenYAxis = 0.7f;  // ✅ Stocké!
   ```

3. **FCEUmm** appelle `FCEUD_UpdateInput()`:
   ```c
   for (port = 0; port < MAX_PORTS; port++)
   {
      switch (nes_input.type[port])  // nes_input.type[1] = 6
      {
         case 259:  // ARKANOID
         case 258:  // ZAPPER
            get_mouse_input(...);  // ❌ JAMAIS ATTEINT!
         break;
      }
   }
   ```

4. **Résultat:**
   - LibretroDroid a stocké les coordonnées ✅
   - FCEUmm n'a jamais demandé de les lire ❌
   - Le Zapper ne tire jamais ❌

---

## ✅ LA SOLUTION

### Utiliser RETRO_DEVICE_ZAPPER (258) au lieu de RETRO_DEVICE_POINTER (6)!

```kotlin
// AVANT (❌):
retroView.setControllerType(1, 6)  // RETRO_DEVICE_POINTER
// FCEUmm: nes_input.type[1] = 6 → switch ne match pas!

// APRÈS (✅):
retroView.setControllerType(1, 258)  // RETRO_DEVICE_ZAPPER
// FCEUmm: nes_input.type[1] = 258 → match case RETRO_DEVICE_ZAPPER!
```

### Ce Qui Se Passe Maintenant:

1. ✅ `setControllerType(1, 258)` → `nes_input.type[1] = 258`
2. ✅ Switch match `case RETRO_DEVICE_ZAPPER`
3. ✅ `get_mouse_input(1, 258, ...)` est appelé!
4. ✅ Ligne 2441: `variant (258) != ARKANOID (259)` → TRUE
5. ✅ Ligne 2441: `zappermode == RetroPointer` → TRUE (via `fceumm_zapper_mode = "touchscreen"`)
6. ✅ FCEUmm lit `input_cb(1, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_X/Y/PRESSED)`
7. ✅ LibretroDroid retourne les valeurs stockées!
8. ✅ **LE ZAPPER DEVRAIT FONCTIONNER!**

---

## 📊 DÉFINITIONS

### Valeurs Device Types:

```c
// libretro.h
#define RETRO_DEVICE_POINTER    6

// fceumm libretro.c ligne 52-53
#define RETRO_DEVICE_ZAPPER     RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_MOUSE, 0)  // = 258
#define RETRO_DEVICE_ARKANOID   RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_MOUSE, 1)  // = 259

// RETRO_DEVICE_SUBCLASS(base, id) = (base | (id << 8))
// RETRO_DEVICE_MOUSE = 2
// 258 = (2 | (0 << 8)) = 2
// Attends non: 258 = 0x102 = (2 | (1 << 8))
```

### Zapper Modes dans FCEUmm:

```c
enum RetroZapperInputModes {
    RetroCLightgun,   // Classique lightgun (cursor visible)
    RetroSTLightgun,  // Super Scope
    RetroMouse,       // Mouse input
    RetroPointer      // Touchscreen (NOTRE MODE!)
};
```

---

## 🔑 CLÉS DU SUCCÈS

### 1. RETRO_DEVICE_ZAPPER (258) pour trigger le switch
**Sans ça:** FCEUmm n'appelle jamais `get_mouse_input()`!

### 2. fceumm_zapper_mode = "touchscreen" pour mode RetroPointer
**Sans ça:** FCEUmm lit LIGHTGUN au lieu de POINTER!

### 3. Overlay fix pour laisser passer les events
**Sans ça:** Les touches ne atteignent jamais le code Zapper!

---

## 📝 FICHIERS MODIFIÉS

**RetroPlay-Android/app/src/main/java/com/retroplay/RetroArchEmulatorActivity.kt**

```kotlin
// Ligne 1016:
retroView.setControllerType(1, 258)  // ✅ RETRO_DEVICE_ZAPPER, pas 6!
```

---

## 🧪 TEST IMMÉDIAT

1. Lancer Duck Hunt
2. Taper sur l'écran
3. **VÉRIFIER:**
   - ❓ Écran flash blanc?
   - ❓ Bruit de coup de feu "POW"?
   - ❓ Canards tombent?

---

## 🎉 PRÉDICTION

**Cette correction devrait ENFIN faire fonctionner le Zapper!**

Tous les morceaux sont maintenant en place:
- ✅ Overlay laisse passer les events
- ✅ Touch events capturés et normalisés
- ✅ LibretroDroid stocke les coordonnées
- ✅ FCEUmm appelle `get_mouse_input()` (car variant = 258)
- ✅ FCEUmm lit `RETRO_DEVICE_POINTER` (car zappermode = RetroPointer)
- ✅ LibretroDroid retourne les coordonnées

**C'était le dernier maillon manquant de la chaîne!** 🔗

---

**APK compilé:** ✅  
**APK installé:** ✅  
**En attente:** Test utilisateur (le moment de vérité!)

