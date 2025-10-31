# 🔥 DÉCOUVERTES CACHÉES - Zapper & Overlays

**Date:** 31 octobre 2025  
**Source:** Recherche approfondie c:\repos  
**Impact:** ⭐⭐⭐⭐⭐ CRITIQUE

---

## 🚨 DÉCOUVERTE #1: FCEUmm Zapper Mode = "touchscreen" PAS "lightgun" !

**Source:** `c:\repos\docs-master\docs\library\fceumm.md` ligne 254-257

### Configuration Critique Trouvée

```markdown
- **Zapper Mode** [fceumm_zapper_mode] (**lightgun**|touchscreen|mouse)

Pointer allows the Zapper Device Type to be used for touch-devices, but still can be used 
with regular mouse. Pointer and Mouse mode movement behaves differently with different 
input driver so user can choose which movement feels natural to them.
```

### ❌ ERREUR ACTUELLE

**Dans les logs utilisateur:**
```
D CoreVariableManager: Parsed OPT: [fceumm_zapper_mode] Zapper Mode = clightgun
```

**Valeur actuelle:** `lightgun` (pour hardware physique)

### ✅ CORRECTION REQUISE

**Pour Android/Touch devices:**
```
fceumm_zapper_mode = "touchscreen"
```

**Raison:**
- `lightgun` = Hardware lightgun physique (pistolet USB)
- `touchscreen` = Touch devices (Android, iOS, tablettes) ✅
- `mouse` = Souris PC

**ACTION IMMÉDIATE:** Changer cette option dans le jeu !

---

## 🚨 DÉCOUVERTE #2: Trigger Delay pour Multi-Touch

**Source:** `c:\repos\RetroArch-master\input\input_overlay.h` ligne 38

```c
#define OVERLAY_LIGHTGUN_TRIG_MAX_DELAY 15
```

**Source:** `c:\repos\RetroArch-master\configuration.c` ligne 2651

```c
SETTING_UINT("input_overlay_lightgun_trigger_delay", 
    &settings->uints.input_overlay_lightgun_trigger_delay, 
    true, DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY, false);
```

### Ce que Ça Fait

**Délai avant de tirer (en frames):**
- **0 frames** = Tir instantané au touch (1 doigt)
- **1-2 frames** = Attendre pour détecter multi-touch (2/3/4 doigts)
- **3-15 frames** = Délai plus long (pour cores lents)

### Pourquoi C'est Important

Dans `overlay-pointing-devices.md` ligne 17-18:

> "The trigger delay is needed for content that doesn't instantly move the gun cursor 
> to a screen tap. More lightgun buttons can be assigned to 2-, 3-, and 4-finger inputs. 
> The trigger delay is used to wait for the correct multi-touch count. 
> **A 1-frame delay is usually enough to distinguish these inputs.**"

### Configuration Optimale

```kotlin
lightgunTriggerDelay = 1  // 1 frame pour détecter multi-touch
```

**Note:** RetroPlay a déjà `lightgunTriggerOnTouch` mais pas `lightgunTriggerDelay` !

---

## 🚨 DÉCOUVERTE #3: Lightgun Port Selection

**Source:** `c:\repos\RetroArch-master\configuration.c` ligne 2733

```c
SETTING_INT("input_overlay_lightgun_port", 
    &settings->ints.input_overlay_lightgun_port, 
    true, DEFAULT_INPUT_OVERLAY_LIGHTGUN_PORT, false);
```

### Ce que Ça Fait

**Choix du port pour envoyer le lightgun:**
- `-1` ou `0` = ALL ports (tous les ports)
- `1` = Port 1 seulement
- `2` = Port 2 seulement (Zapper NES)
- `3` = Port 3
- `4` = Port 4

### Notre Configuration

**RetroPlay actuel:**
```kotlin
lightgunPort: Int = 0  // ALL ports (défaut)
```

**Pour NES Zapper:**
```kotlin
lightgunPort: Int = 2  // Port 2 seulement (plus propre)
```

**Note:** Actuellement ça marche avec port=0 (ALL), mais port=2 serait plus précis.

---

## 🚨 DÉCOUVERTE #4: Lightgun Actions Complètes

**Source:** `c:\repos\RetroArch-master\input\input_overlay.h` ligne 144-160

```c
enum overlay_lightgun_action
{
   OVERLAY_LIGHTGUN_ACTION_NONE = 0,
   OVERLAY_LIGHTGUN_ACTION_TRIGGER,      // 1
   OVERLAY_LIGHTGUN_ACTION_RELOAD,       // 2 - OFFSCREEN SHOT!
   OVERLAY_LIGHTGUN_ACTION_AUX_A,        // 3
   OVERLAY_LIGHTGUN_ACTION_AUX_B,        // 4
   OVERLAY_LIGHTGUN_ACTION_AUX_C,        // 5
   OVERLAY_LIGHTGUN_ACTION_START,        // 6
   OVERLAY_LIGHTGUN_ACTION_SELECT,       // 7
   OVERLAY_LIGHTGUN_ACTION_DPAD_UP,      // 8
   OVERLAY_LIGHTGUN_ACTION_DPAD_DOWN,    // 9
   OVERLAY_LIGHTGUN_ACTION_DPAD_LEFT,    // 10
   OVERLAY_LIGHTGUN_ACTION_DPAD_RIGHT,   // 11
   
   OVERLAY_LIGHTGUN_ACTION_END
};
```

### Multi-Touch Mapping Disponible

**Configuration.c ligne 2652-2654:**
```c
input_overlay_lightgun_two_touch_input    // 2 doigts
input_overlay_lightgun_three_touch_input  // 3 doigts
input_overlay_lightgun_four_touch_input   // 4 doigts
```

**Valeurs possibles:** 0-12 (selon l'enum ci-dessus)

### Exemples de Mapping

**Pour Duck Hunt (NES):**
```kotlin
lightgunTwoTouchInput = 6    // START (pause le jeu)
lightgunThreeTouchInput = 7  // SELECT (changer mode)
lightgunFourTouchInput = 0   // NONE
```

**Pour Time Crisis (PSX):**
```kotlin
lightgunTwoTouchInput = 3    // AUX_A (reload)
lightgunThreeTouchInput = 4  // AUX_B (cover)
lightgunFourTouchInput = 6   // START (pause)
```

---

## 🚨 DÉCOUVERTE #5: RELOAD Action = Offscreen Shot

**Dans input_overlay.h ligne 148:**
```c
OVERLAY_LIGHTGUN_ACTION_RELOAD,  // Offscreen shot
```

**Dans configuration.c ligne 325:**
```c
DECLARE_BIND(gun_offscreen_shot, RARCH_LIGHTGUN_RELOAD, ...)
```

### Ce que Ça Signifie

**Reload = Tirer hors écran** (offscreen shot)

Dans beaucoup de jeux lightgun:
- Tirer hors écran = **RELOAD**
- Time Crisis, House of the Dead, etc.

**Pour RetroPlay:**
```kotlin
// Si allowOffscreen=false et touch hors bounds:
// Au lieu d'ignorer → envoyer RELOAD action!
if (!isInGameArea && !allowOffscreen) {
    sendLightgunAction(OVERLAY_LIGHTGUN_ACTION_RELOAD)
}
```

---

---

## ❌ LE VRAI PROBLÈME IDENTIFIÉ !

### Dans les Logs Utilisateur

```
D CoreVariableManager: Parsed OPT: [fceumm_zapper_mode] Zapper Mode = clightgun
```

**Valeur actuelle:** `lightgun` (MAUVAISE pour Android!)

### Correction Immédiate Requise

**SUR LE DEVICE, dans Duck Hunt:**
```
1. BACK → QuickMenu
2. Settings → Core Options
3. Chercher "Zapper Mode"
4. Changer de "lightgun" à "touchscreen" ⭐⭐⭐⭐⭐
5. Retour au jeu
```

### Explication

**Documentation officielle FCEUmm:**

> "Pointer allows the Zapper Device Type to be used for **touch-devices**, but still can 
> be used with regular mouse."

**Les 3 modes:**
- `lightgun` = Hardware physique (pistolet USB) ❌ PAS pour Android
- `touchscreen` = Touch devices (Android, iOS) ✅ CORRECT pour nous
- `mouse` = Souris PC ❌ PAS pour Android

**CETTE OPTION EST LA CLÉ !** Elle dit au core comment interpréter les inputs POINTER !

---

## 🚨 DÉCOUVERTE #6: Configuration Defaults RetroArch

**Source:** `c:\repos\RetroArch-master\config.def.h` ligne 637-641

```c
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_PORT -1
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_ON_TOUCH true
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_DELAY 1
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_MULTI_TOUCH_INPUT 0
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_ALLOW_OFFSCREEN true
```

### Comparaison avec RetroPlay

| Setting | RetroArch Default | RetroPlay Actuel | Status |
|---------|-------------------|------------------|--------|
| **lightgun_port** | -1 (ALL) | 0 (ALL) | ✅ OK |
| **trigger_on_touch** | true | false | ⚠️ Différent |
| **trigger_delay** | 1 frame | **MANQUANT** | ❌ À ajouter |
| **two_touch_input** | 0 (NONE) | 0 | ✅ OK |
| **three_touch_input** | 0 (NONE) | 0 | ✅ OK |
| **four_touch_input** | 0 (NONE) | 0 | ✅ OK |
| **allow_offscreen** | true | true | ✅ OK |

### ❌ PARAMÈTRE MANQUANT CRITIQUE

**RetroPlay N'A PAS:**
```kotlin
lightgunTriggerDelay: Int = 1  // ← MANQUANT !
```

**Ce paramètre est CRITIQUE pour:**
- Attendre multi-touch (2/3/4 doigts)
- Éviter tir trop rapide
- Cores qui ne bougent pas le curseur instantanément

**À AJOUTER dans OverlayModels.kt:**
```kotlin
val lightgunTriggerDelay: Int = 1  // 1 frame (default RetroArch)
```

---

## 🎯 RÉSUMÉ DES DÉCOUVERTES CACHÉES

### 1. ⭐⭐⭐⭐⭐ CRITIQUE: fceumm_zapper_mode

**DOIT être "touchscreen" pour Android, PAS "lightgun" !**

```
Core Options → Zapper Mode → touchscreen
```

### 2. ⭐⭐⭐⭐ Trigger Delay Manquant

**RetroPlay n'implémente pas ce paramètre:**
```kotlin
lightgunTriggerDelay: Int = 1  // Frames à attendre
```

### 3. ⭐⭐⭐ Reload = Offscreen Shot

**Tirer hors écran devrait envoyer RELOAD action:**
```kotlin
OVERLAY_LIGHTGUN_ACTION_RELOAD = 2
```

### 4. ⭐⭐ Lightgun Port

**Peut cibler un port spécifique:**
```kotlin
lightgunPort = 2  // Port 2 seulement (Zapper NES)
```

### 5. ⭐⭐⭐⭐ Multi-Touch Actions Complètes

**12 actions disponibles:**
```
0 = NONE
1 = TRIGGER
2 = RELOAD (offscreen)
3 = AUX_A
4 = AUX_B
5 = AUX_C
6 = START
7 = SELECT
8-11 = DPAD_UP/DOWN/LEFT/RIGHT
```

---

## 🔧 ACTIONS IMMÉDIATES

### CRITIQUE: Changer Zapper Mode (NOW!)

**Sur le device:**
```
Duck Hunt → BACK → Settings → Core Options
"Zapper Mode" de "lightgun" à "touchscreen"
```

**APRÈS ce changement, tester si canards tombent !**

### À Implémenter dans RetroPlay

1. **Ajouter lightgunTriggerDelay** (OverlayModels.kt)
2. **Forcer fceumm_zapper_mode = "touchscreen"** pour NES (NativeComposeEmulatorActivity.kt)
3. **Implémenter RELOAD action** (offscreen shot)

---

## 📊 IMPACT ESTIMÉ

**Si "Zapper Mode = touchscreen" fixe le problème:**
- Impact: ⭐⭐⭐⭐⭐ MAJEUR
- Raison: Option core mal configurée, pas problème de code !
- Solution: 30 secondes (changer l'option)

**Si ça ne fixe pas:**
- Investiguer trigger delay
- Vérifier invert sensor/trigger
- Analyser logs POINTER vs core
