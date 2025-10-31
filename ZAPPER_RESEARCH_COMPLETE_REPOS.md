# 🎯 Recherche Exhaustive Zapper/Lightgun - c:\repos

**Date:** 31 octobre 2025  
**Méthodologie:** "Nos Rules" - Focus spécifique contrôleurs  
**Sources:** libretro.h, overlay-pointing-devices.md, input_overlay.h, RetroArch code

---

## 📚 DÉCOUVERTES CRITIQUES

### 🔴 PROBLÈME MAJEUR IDENTIFIÉ

**RetroPlay utilise le MAUVAIS type de device !**

```kotlin
// Code actuel (INCORRECT pour Duck Hunt)
// Note ligne 539 NativeComposeEmulatorActivity:
// "Zapper configuration désactivée car LibretroDroid ne supporte pas RETRO_DEVICE_LIGHTGUN"
```

### ✅ LA VÉRITÉ (de libretro.h)

#### RETRO_DEVICE_POINTER (6) - ✅ CE QU'ON DEVRAIT UTILISER
```c
/**
 * Input Device: Pointer.
 *
 * Abstracts the concept of a pointing mechanism, e.g. touch.
 * Coordinates in X and Y are reported as:
 * [-0x7fff, 0x7fff]: -0x7fff = far left/top, 0x7fff = far right/bottom
 *
 * For multi-touch, the index variable can be used to successively query
 * more presses.
 *
 * IDs:
 * - RETRO_DEVICE_ID_POINTER_X (0)
 * - RETRO_DEVICE_ID_POINTER_Y (1)
 * - RETRO_DEVICE_ID_POINTER_PRESSED (2)
 * - RETRO_DEVICE_ID_POINTER_COUNT (3)
 * - RETRO_DEVICE_ID_POINTER_IS_OFFSCREEN (15)
 */
#define RETRO_DEVICE_POINTER      6
```

**Usage:**
- **Duck Hunt (NES Zapper)** ✅
- Touch screens simples
- Pointage basique

#### RETRO_DEVICE_LIGHTGUN (4) - ⚠️ Pour lightguns avancés
```c
/**
 * Input Device: Light Gun (PlayStation Guncon style)
 *
 * Coordinates: [-0x8000, 0x7fff]
 * Zero = center, -0x8000 = out-of-bounds
 *
 * Full button set:
 * - LIGHTGUN_SCREEN_X (13) / SCREEN_Y (14) - Absolute position
 * - LIGHTGUN_TRIGGER (2)
 * - LIGHTGUN_RELOAD (16) - Forced off-screen shot
 * - LIGHTGUN_AUX_A (3) / AUX_B (4) / AUX_C (8)
 * - LIGHTGUN_START (6) / SELECT (7)
 * - LIGHTGUN_DPAD_UP/DOWN/LEFT/RIGHT (9-12)
 * - LIGHTGUN_IS_OFFSCREEN (15)
 */
#define RETRO_DEVICE_LIGHTGUN     4
```

**Usage:**
- **Super Scope (SNES)** - Lightgun avancé avec boutons
- Guncon (PlayStation)
- Time Crisis, etc.

---

## ✅ CORRECTION IMMÉDIATE REQUISE

### ❌ CODE ACTUEL (Incorrect)

**NativeComposeEmulatorActivity.kt ligne 494-499:**
```kotlin
"nes" -> {
    // Note: Zapper configuration désactivée car LibretroDroid ne supporte pas RETRO_DEVICE_LIGHTGUN
    // L'utilisateur doit utiliser le bouton A du gamepad pour tirer dans Duck Hunt
    if (isZapperGame) {
        Log.i(TAG, "[NES] Zapper game detected: $gameName - use gamepad button A to shoot")
    }
}
```

**Problème:**
1. ❌ Commentaire FAUX - LibretroDroid supporte RETRO_DEVICE_POINTER !
2. ❌ Aucune configuration du controller type
3. ❌ Message trompeur à l'utilisateur

### ✅ CODE CORRIGÉ (Correct)

```kotlin
"nes" -> {
    // Configuration Zapper (NES light gun) via RETRO_DEVICE_POINTER
    if (isZapperGame) {
        Log.i(TAG, "[NES] Zapper game detected: $gameName - Configuring port 2 as POINTER")
        
        // Configurer le port 2 (Player 2) comme RETRO_DEVICE_POINTER
        // RETRO_DEVICE_POINTER = 6 (défini dans libretro.h)
        try {
            retroView.setControllerType(1, 6)  // Port 2 (index 1) = POINTER
            Log.i(TAG, "[NES] Zapper configured successfully on port 2")
        } catch (e: Exception) {
            Log.e(TAG, "[NES] Failed to configure Zapper: ${e.message}")
        }
    }
}
```

**RetroArchEmulatorActivity.kt** a le même problème (lignes 538-543)

---

## 🎮 DIFFÉRENCE POINTER vs LIGHTGUN

### RETRO_DEVICE_POINTER (6)

**Utilisation:** Touch screens, pointeurs basiques, **NES Zapper**

**Inputs:**
```c
ID_POINTER_X (0)           // Position X [-0x7fff, 0x7fff]
ID_POINTER_Y (1)           // Position Y [-0x7fff, 0x7fff]
ID_POINTER_PRESSED (2)     // 1 = pressed, 0 = released
ID_POINTER_COUNT (3)       // Nombre de touches actives (multi-touch)
ID_POINTER_IS_OFFSCREEN (15) // 1 = offscreen
```

**Avantages:**
- ✅ Simple (position + pressed)
- ✅ Multi-touch natif (index 0, 1, 2, 3...)
- ✅ Parfait pour NES Zapper

### RETRO_DEVICE_LIGHTGUN (4)

**Utilisation:** Super Scope, Guncon, Time Crisis

**Inputs:** **15 boutons différents !**
```c
// Position
LIGHTGUN_SCREEN_X/Y (13, 14)  // Absolute
LIGHTGUN_X/Y (0, 1)           // Relative (deprecated)

// Boutons
LIGHTGUN_TRIGGER (2)
LIGHTGUN_RELOAD (16)          // Viser offscreen pour reload
LIGHTGUN_AUX_A/B/C (3, 4, 8)  // Boutons auxiliaires
LIGHTGUN_START/SELECT (6, 7)
LIGHTGUN_DPAD_UP/DOWN/LEFT/RIGHT (9-12)

// Status
LIGHTGUN_IS_OFFSCREEN (15)
```

**Avantages:**
- ✅ Boutons multiples
- ✅ Reload action (offscreen shot)
- ✅ D-Pad navigation
- ⚠️ Plus complexe

---

## 🎯 MULTI-TOUCH LIGHTGUN (De overlay-pointing-devices.md)

### Documentation Officielle
> "Lightgun x, y, and trigger are normally sent together to every lightgun port. More lightgun buttons can be assigned to **2-, 3-, and 4-finger inputs**. The trigger delay is used to wait for the correct multi-touch count. A 1-frame delay is usually enough to distinguish these inputs."

### Configuration RetroArch

```c
// De config.def.h
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_PORT -1  // -1 = tous les ports
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_ON_TOUCH true
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY 1  // Frames
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_MULTI_TOUCH_INPUT 0
    // 0 = Single touch (default)
    // 1 = 2 fingers (action)
    // 2 = 3 fingers (action)
    // 3 = 4 fingers (action)
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_ALLOW_OFFSCREEN true
```

### Mapping Multi-Touch → Actions

**Exemple configuration:**
```
1 finger = LIGHTGUN_TRIGGER (tir principal)
2 fingers = LIGHTGUN_RELOAD (ou AUX_A)
3 fingers = LIGHTGUN_START
4 fingers = LIGHTGUN_SELECT
```

### Implementation Pattern

```kotlin
fun handleLightgunMultiTouch(event: MotionEvent): Int {
    val pointerCount = event.pointerCount
    val delay = lightgunTriggerDelay  // Ex: 1 frame (16ms)
    
    // Attendre trigger_delay pour détecter tous les doigts
    Handler().postDelayed({
        when (pointerCount) {
            1 -> sendLightgunAction(LIGHTGUN_TRIGGER)
            2 -> sendLightgunAction(lightgunTwoTouchInput)  // Config
            3 -> sendLightgunAction(lightgunThreeTouchInput)
            4 -> sendLightgunAction(lightgunFourTouchInput)
        }
    }, delay.toLong())
}
```

---

## 🔍 DIFFÉRENCES CRITIQUES

### Coordonnées

**POINTER:**
```c
[-0x7fff, 0x7fff]  // Range
```

**LIGHTGUN:**
```c
[-0x8000, 0x7fff]  // Range
-0x8000 = out-of-bounds (special value)
```

### Multi-Touch

**POINTER:**
```c
// Multi-touch via index (0, 1, 2, 3...)
retro_input_state_t(port, RETRO_DEVICE_POINTER, index, RETRO_DEVICE_ID_POINTER_X)
retro_input_state_t(port, RETRO_DEVICE_POINTER, index, RETRO_DEVICE_ID_POINTER_PRESSED)
```

**LIGHTGUN:**
```c
// Multi-touch via multitouch_id (actions différentes par nombre de doigts)
// Géré par overlay system, pas directement par core
```

---

## 🛠️ CORRECTIONS REQUISES DANS RETROPLAY

### 1. Activer Configuration POINTER ✅

**Fichiers à modifier:**
- `NativeComposeEmulatorActivity.kt` ligne 494-499
- `RetroArchEmulatorActivity.kt` ligne 538-543

**Correction:**
```kotlin
"nes" -> {
    // Configuration Zapper (NES light gun) via RETRO_DEVICE_POINTER
    if (isZapperGame) {
        Log.i(TAG, "[NES] Zapper game detected: $gameName")
        
        // Port 2 (index 1) = RETRO_DEVICE_POINTER (6)
        try {
            retroView.setControllerType(1, 6)
            Log.i(TAG, "[NES] Zapper configured as POINTER on port 2")
            Toast.makeText(this, "Zapper enabled! Tap screen to shoot", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "[NES] Failed to configure Zapper: ${e.message}")
        }
    }
}
```

### 2. Corriger Zone de Tir (Découverte utilisateur)

**Problème actuel:**
```kotlin
// handleZapperTouch() ligne 174-180
val screenWidth = resources.displayMetrics.widthPixels.toFloat()
val touchX = event.x
val relativeX = touchX / screenWidth
val isInZapperZone = relativeX >= 0.35f && relativeX <= 0.65f
```

**Problèmes:**
- ❌ Hardcodé pour landscape
- ❌ Pas synchronisé avec GLRetroView
- ❌ Ne fonctionne pas en portrait

**Solution (de ZAPPER_ZONE_DISCOVERY.md):**
```kotlin
// Utiliser bounds exacts du GLRetroView
AndroidView(
    factory = { retroView },
    modifier = Modifier
        .layoutId("gameView")
        .onGloballyPositioned { layoutCoordinates ->
            gameViewBounds.value = layoutCoordinates.boundsInWindow()
        }
)

// Dans handleZapperTouch
val bounds = gameViewBounds ?: return false
val isInGameArea = touchX in bounds.left..bounds.right && 
                   touchY in bounds.top..bounds.bottom
```

### 3. Envoyer Position POINTER (Pas juste Button A)

**Problème actuel:**
```kotlin
// handleZapperTouch() ligne 200-213
// Envoie SEULEMENT Button A, PAS la position du tir!
retroView.sendKeyEvent(
    android.view.KeyEvent.ACTION_DOWN,
    android.view.KeyEvent.KEYCODE_BUTTON_A,
    1  // Port 2
)
```

**Correction:**
```kotlin
// 1. Envoyer position POINTER
val normalizedX = ((touchX - bounds.left) / bounds.width * 2f) - 1f  // -1 à +1
val normalizedY = ((touchY - bounds.top) / bounds.height * 2f) - 1f

retroView.sendMotionEvent(
    InputDevice.MOTION_SOURCE_POINTER,  // = 3 (de LibretroDroid.java)
    normalizedX,
    normalizedY,
    1  // Port 2 (Zapper)
)

// 2. Envoyer POINTER_PRESSED (si triggerOnTouch)
// Le core FCEUmm utilise RETRO_DEVICE_ID_POINTER_PRESSED pour détecter le tir
// Actuellement on envoie Button A, il faut envoyer POINTER state
```

**ATTENTION:** LibretroDroid.java ligne 28-31:
```java
public static final int MOTION_SOURCE_DPAD = 0;
public static final int MOTION_SOURCE_ANALOG_LEFT = 1;
public static final int MOTION_SOURCE_ANALOG_RIGHT = 2;
public static final int MOTION_SOURCE_POINTER = 3;
```

**POINTER est supporté !**

### 4. Implémenter Multi-Touch Actions

**De overlay-pointing-devices.md:**
> "More lightgun buttons can be assigned to 2-, 3-, and 4-finger inputs."

**Configuration actuelle (OverlayModels.kt ligne 258-260):**
```kotlin
val lightgunTwoTouchInput: Int = 0,        // ✅ Défini
val lightgunThreeTouchInput: Int = 0,      // ✅ Défini
val lightgunFourTouchInput: Int = 0,       // ✅ Défini
```

**Manquant:** Logic d'implémentation !

**Pattern suggéré:**
```kotlin
fun handleZapperMultiTouch(event: MotionEvent, settings: AdvancedOverlaySettings) {
    val fingerCount = event.pointerCount
    
    // Attendre trigger_delay pour détecter tous les doigts
    if (settings.lightgunTriggerDelay > 0) {
        Handler().postDelayed({
            executeMultiTouchAction(fingerCount, settings)
        }, settings.lightgunTriggerDelay.toLong())
    } else {
        executeMultiTouchAction(fingerCount, settings)
    }
}

fun executeMultiTouchAction(fingerCount: Int, settings: AdvancedOverlaySettings) {
    when (fingerCount) {
        1 -> {
            // Trigger normal (position déjà envoyée via sendMotionEvent)
            // POINTER_PRESSED géré automatiquement par onTouchEvent
        }
        2 -> {
            // Action configurée (reload, start, etc.)
            sendLightgunButton(settings.lightgunTwoTouchInput, port = 1)
        }
        3 -> {
            sendLightgunButton(settings.lightgunThreeTouchInput, port = 1)
        }
        4 -> {
            sendLightgunButton(settings.lightgunFourTouchInput, port = 1)
        }
    }
}

fun sendLightgunButton(actionId: Int, port: Int) {
    val keyCode = when (actionId) {
        1 -> KeyEvent.KEYCODE_BUTTON_START   // LIGHTGUN_START
        2 -> KeyEvent.KEYCODE_BUTTON_SELECT  // LIGHTGUN_SELECT
        3 -> KeyEvent.KEYCODE_BUTTON_A       // LIGHTGUN_AUX_A
        4 -> KeyEvent.KEYCODE_BUTTON_B       // LIGHTGUN_AUX_B
        5 -> KeyEvent.KEYCODE_BUTTON_X       // LIGHTGUN_AUX_C
        6 -> KeyEvent.KEYCODE_DPAD_UP        // LIGHTGUN_DPAD_UP
        // etc.
        else -> return  // 0 = none
    }
    
    retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, port)
    retroView.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, port)
}
```

---

## 📋 CHECKLIST CORRECTIONS AVANT FINALISATION

### 🔴 CRITIQUE (À corriger maintenant)

- [ ] **1. Activer setControllerType(1, 6) pour Zapper**
  - Fichier: NativeComposeEmulatorActivity.kt ligne 494-499
  - Fichier: RetroArchEmulatorActivity.kt ligne 538-543
  - Action: Supprimer commentaire faux, ajouter configuration
  
- [ ] **2. Utiliser sendMotionEvent(MOTION_SOURCE_POINTER)**
  - Fichier: handleZapperTouch() dans les 2 activities
  - Action: Envoyer position via MOTION_SOURCE_POINTER (3)
  - Remplacer: sendKeyEvent(BUTTON_A) par sendMotionEvent()

- [ ] **3. Corriger zone de tir (GLRetroView bounds)**
  - Fichier: handleZapperTouch() lignes 174-180
  - Action: onGloballyPositioned() pour bounds exacts
  - Remplacer: zone hardcodée 0.35-0.65

- [ ] **4. Implémenter POINTER_PRESSED**
  - Actuellement: sendKeyEvent(BUTTON_A) simule le tir
  - Correct: POINTER_PRESSED envoyé via onTouchEvent()
  - Vérifier: LibretroDroid.onTouchEvent() gère déjà pressed?

### 🟡 HAUTE PRIORITÉ (Avant finalisation)

- [ ] **5. Multi-Touch Actions**
  - Fichier: handleZapperTouch()
  - Action: Détecter 2/3/4 doigts
  - Logic: sendLightgunButton() selon config

- [ ] **6. Trigger Delay**
  - Fichier: handleZapperTouch()
  - Action: Handler.postDelayed() avant action
  - Utiliser: settings.lightgunTriggerDelay

- [ ] **7. Allow Offscreen**
  - Fichier: handleZapperTouch()
  - Action: Si false, clamp position aux bounds
  - Utiliser: settings.lightgunAllowOffscreen

### 🟢 POLISH (Nice-to-have)

- [ ] **8. ZapperCrosshair.kt**
  - Créer réticule visuel
  - Afficher au touch
  - Couleur: Rouge

- [ ] **9. Debug Bounds Rectangle**
  - Rectangle vert autour GLRetroView
  - Toggle via debug mode
  - Visualiser zone de jeu

- [ ] **10. Toast informatif**
  - Au lancement jeu Zapper
  - Expliquer contrôles (1 doigt = tir, 2 doigts = ?, etc.)

---

## 📖 GUIDE LIBRETRO.H - Types Devices

### Device Types (Base)
```c
RETRO_DEVICE_NONE       = 0  // Aucun input
RETRO_DEVICE_JOYPAD     = 1  // Gamepad standard (RetroPad)
RETRO_DEVICE_MOUSE      = 2  // Souris (SNES Mouse, Amiga, DOS)
RETRO_DEVICE_KEYBOARD   = 3  // Clavier (Amiga, C64, DOS)
RETRO_DEVICE_LIGHTGUN   = 4  // Lightgun avancé (Guncon, Super Scope)
RETRO_DEVICE_ANALOG     = 5  // Analog controller (DualShock)
RETRO_DEVICE_POINTER    = 6  // Touch/Pointer (NES Zapper)
```

### Subclasses (Exemple)
```c
// Définies par les cores
RETRO_DEVICE_SUPER_SCOPE = RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_LIGHTGUN, 1)
RETRO_DEVICE_JUSTIFIER   = RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_LIGHTGUN, 2)
```

**Note:** Subclasses permettent au frontend de sélectionner le device physique approprié

---

## 🎮 PAR CONSOLE

### NES (FCEUmm)
**Controller:** RETRO_DEVICE_POINTER (6) ✅  
**Raison:** Zapper est un pointeur simple (position + trigger)  
**Boutons:** Trigger seulement  
**Port:** Port 2 (index 1)

### SNES (Snes9x)
**Controller:** RETRO_DEVICE_LIGHTGUN (4) ou RETRO_DEVICE_SUBCLASS  
**Devices:** Super Scope (plus complexe que Zapper)  
**Boutons:** Trigger, Cursor, Turbo, Pause  
**Port:** Port 2

### PlayStation (PCSX ReARMed)
**Controller:** RETRO_DEVICE_LIGHTGUN (4)  
**Devices:** Guncon  
**Boutons:** Trigger, A, B  
**Jeux:** Time Crisis, Point Blank

### Sega (Genesis Plus GX)
**Controller:** RETRO_DEVICE_LIGHTGUN (4)  
**Devices:** Light Phaser, Menacer  
**Boutons:** Varies  
**Jeux:** Lethal Enforcers, etc.

---

## 🚨 ERREURS ACTUELLES IDENTIFIÉES

### Erreur #1: Commentaire Faux
```kotlin
// "Zapper configuration désactivée car LibretroDroid ne supporte pas RETRO_DEVICE_LIGHTGUN"
```

**Vérité:**
- LibretroDroid supporte setControllerType(port, type) ✅
- RETRO_DEVICE_POINTER (6) est supporté ✅
- RETRO_DEVICE_LIGHTGUN (4) est aussi supporté ✅

### Erreur #2: Pas de Configuration Controller
```kotlin
// Aucun appel à setControllerType() pour Zapper
```

**Résultat:** Core utilise JOYPAD par défaut, Zapper ne fonctionne pas correctement

### Erreur #3: Envoi Button A au lieu de POINTER
```kotlin
// handleZapperTouch envoie BUTTON_A (joypad) au lieu de POINTER state
retroView.sendKeyEvent(ACTION_DOWN, KEYCODE_BUTTON_A, 1)
```

**Résultat:** Peut marcher partiellement (fallback), mais pas optimal

### Erreur #4: Zone Hardcodée Landscape
```kotlin
// Zone 0.35-0.65 hardcodée, ne marche pas en portrait
val isInZapperZone = relativeX >= 0.35f && relativeX <= 0.65f
```

**Résultat:** Zapper cassé en portrait

### Erreur #5: Pas de Position Envoyée
```kotlin
// handleZapperTouch() n'envoie PAS la position du touch au core
// Envoie seulement Button A press/release
```

**Résultat:** Core ne sait pas OÙ l'utilisateur vise

---

## ✅ PLAN DE CORRECTION COMPLET

### Phase 1: Corrections Critiques (2h)

1. **Activer setControllerType(1, 6)**
   - 2 fichiers à modifier
   - Supprimer commentaire faux
   - Ajouter configuration + toast
   
2. **Envoyer Position via sendMotionEvent()**
   - Calculer normalizedX/Y depuis bounds
   - Appeler sendMotionEvent(MOTION_SOURCE_POINTER, x, y, port)
   - Tester si pressed est géré automatiquement

3. **Utiliser Bounds GLRetroView**
   - onGloballyPositioned() pour capturer bounds
   - Convertir touch → coordinates relatives bounds
   - Supporter portrait ET landscape

### Phase 2: Multi-Touch (2h)

4. **Détecter Finger Count**
   - event.pointerCount
   - Trigger delay (Handler.postDelayed)
   - executeMultiTouchAction()

5. **Mapper Actions**
   - lightgunTwoTouchInput → KeyCode
   - lightgunThreeTouchInput → KeyCode
   - lightgunFourTouchInput → KeyCode

### Phase 3: Polish (1h)

6. **ZapperCrosshair.kt**
   - Réticule rouge
   - Suit le doigt
   - Visible au touch

7. **Debug Visuel**
   - Rectangle vert bounds
   - Toggle via debug mode

### Phase 4: Tests (1h)

8. **Duck Hunt**
   - Landscape: tir fonctionne
   - Portrait: tir fonctionne
   - Multi-touch: actions custom

9. **Hogan's Alley**
   - Précision
   - Edge cases

**Total:** 6h (au lieu de 4h estimé)

---

## 📊 PRIORITÉ DES CORRECTIONS

| Correction | Gravité | Impact | Estimation |
|------------|---------|--------|------------|
| **setControllerType(1, 6)** | 🔴 CRITIQUE | ⭐⭐⭐⭐⭐ | 15 min |
| **sendMotionEvent(POINTER)** | 🔴 CRITIQUE | ⭐⭐⭐⭐⭐ | 30 min |
| **Bounds GLRetroView** | 🔴 CRITIQUE | ⭐⭐⭐⭐⭐ | 1h |
| **Multi-Touch** | 🟡 HAUTE | ⭐⭐⭐ | 2h |
| **Trigger Delay** | 🟡 HAUTE | ⭐⭐ | 30 min |
| **Crosshair** | 🟢 POLISH | ⭐⭐ | 1h |
| **Debug Visual** | 🟢 POLISH | ⭐ | 30 min |

**Total Critiques:** 2h15  
**Total Complètes:** 6h

---

## 🎯 RECOMMANDATION FINALE

### ❌ NE PAS commencer finalisation Zapper sans ces corrections !

**Les 3 corrections critiques (2h15) sont BLOQUANTES:**
1. Sans setControllerType(1, 6) → Zapper ne fonctionne pas
2. Sans sendMotionEvent() → Position non envoyée au core
3. Sans bounds GLRetroView → Cassé en portrait

### ✅ ORDRE D'IMPLÉMENTATION

**Sprint Correction (2h15):**
1. setControllerType(1, 6) - 15 min
2. sendMotionEvent(POINTER) - 30 min
3. Bounds GLRetroView - 1h30

**Sprint Finalisation (3h45):**
4. Multi-Touch - 2h
5. Trigger Delay - 30 min
6. Crosshair + Debug - 1h15

**Total:** 6h (corrections incluses)

---

## 📝 FICHIERS À MODIFIER

### Critiques
1. ✅ `NativeComposeEmulatorActivity.kt`
   - Ligne 494-499 (activer setControllerType)
   - Ligne 169-232 (handleZapperTouch refactoring complet)
   - Ligne 2021+ (onGloballyPositioned pour bounds)

2. ✅ `RetroArchEmulatorActivity.kt`
   - Lignes identiques

### Nouveaux
3. ✅ `ZapperCrosshair.kt` (nouveau fichier)
4. ✅ `ZapperOverlay.kt` (nouveau fichier - optionnel)

---

**Recherche effectuée le:** 31 octobre 2025  
**Sources:** libretro.h, overlay-pointing-devices.md, input_overlay.h  
**Résultat:** 5 erreurs critiques identifiées + solutions  
**Estimation corrigée:** 6h (au lieu de 4h)

**PROCHAINE ÉTAPE:** Appliquer corrections critiques (2h15) avant finalisation


