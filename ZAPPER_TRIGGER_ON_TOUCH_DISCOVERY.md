# DÉCOUVERTE CRITIQUE: Zapper Trigger On Touch

**Date:** 2025-10-31  
**Découverte:** L'utilisateur a identifié le lien entre Advanced Overlay Settings et le Zapper  
**Impact:** POTENTIELLEMENT la cause racine des problèmes de tir du Zapper

---

## 🎯 QUESTION DE L'UTILISATEUR

> "Est-ce que les options dans le menu Advanced Overlay Settings pourraient avoir des répercussions sur le problème du Zapper? Et pour le Zapper, c'est pas aussi un overlay?"

**Réponse: OUI sur les deux points!** 🚨

---

## 🔍 DÉCOUVERTE #1: Advanced Overlay Settings AFFECTENT le Zapper

### Code actuel (ligne 1063-1064 RetroArchEmulatorActivity.kt)

```kotlin
val lightgunSettings = OverlayPreferenceManager.loadAdvancedSettings(prefs, console)
handleZapperTouch(event, gameViewBounds.value, 
    lightgunSettings.lightgunTriggerOnTouch,   // ← Chargé depuis Advanced Settings!
    lightgunSettings.lightgunAllowOffscreen)    // ← Chargé depuis Advanced Settings!
```

**Donc oui, les Advanced Overlay Settings contrôlent:**
- ✅ `Trigger on Touch` → Quand le Zapper tire (DOWN vs UP)
- ✅ `Allow Offscreen` → Si on peut tirer hors de la zone de jeu
- ✅ `Lightgun Port` → Sur quel port (0-3)
- ✅ `Trigger Delay` → Délai avant déclenchement

---

## 🚨 DÉCOUVERTE #2: Valeur par défaut INCORRECTE!

### RetroArch officiel (c:\repos\RetroArch-master)

```c
// Fichier: configuration.h
#define DEFAULT_INPUT_OVERLAY_LIGHTGUN_TRIGGER_ON_TOUCH true  // ← TRUE!
```

**Comportement:**
- Le Zapper tire **instantanément** au touch DOWN
- Comportement correct pour Duck Hunt et autres jeux Zapper

---

### RetroPlay (AVANT correction)

```kotlin
// AdvancedOverlaySettingsDialog.kt ligne 53
lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", false)
                                                                                          ^^^^^ FAUX!

// OverlayModels.kt ligne 255
val lightgunTriggerOnTouch: Boolean = false  // ← FAUX!

// OverlayModels.kt ligne 385
lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", false)
                                                                                          ^^^^^ FAUX!
```

**Comportement erroné:**
- Le Zapper tire **seulement au RELEASE** (quand vous levez le doigt)
- Le tir peut être imprecis ou retardé
- Ne correspond PAS au comportement RetroArch officiel

---

## 💡 HYPOTHÈSE: C'ÉTAIT PEUT-ÊTRE LE VRAI PROBLÈME!

### Symptômes rapportés par l'utilisateur:
- ❌ "Les canards ne tombent pas quand je tape dessus"
- ❌ "Ça dit touchdown et les coordonnés ok mais jamais qu'il y a eu un trigger"
- ❌ "Les tirs au centre fonctionnent mais pas sur toute la surface de jeu"

### Explication possible:
Si `triggerOnTouch = false`:
1. Vous touchez l'écran (ACTION_DOWN) → **Position envoyée** ✅
2. Vous gardez le doigt appuyé → **Aucun tir** ❌
3. Vous levez le doigt (ACTION_UP) → **Tir déclenché** (trop tard!)

**Résultat:** Les coordonnées sont bonnes, mais le timing du tir est FAUX!

---

## 🔧 CORRECTION APPLIQUÉE

### Fichiers modifiés:

**1. AdvancedOverlaySettingsDialog.kt (ligne 53)**
```kotlin
// AVANT
lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", false)

// APRÈS
lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", true)  // ← TRUE!
```

**2. OverlayModels.kt (ligne 255)**
```kotlin
// AVANT
val lightgunTriggerOnTouch: Boolean = false,

// APRÈS
val lightgunTriggerOnTouch: Boolean = true,  // TRUE comme RetroArch officiel
```

**3. OverlayModels.kt (ligne 385)**
```kotlin
// AVANT
lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", false),

// APRÈS
lightgunTriggerOnTouch = prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", true),  // TRUE!
```

---

## 🎯 DÉCOUVERTE #3: Le Zapper EST un overlay (hybride)

### Le Zapper est DEUX CHOSES:

**1. INPUT DEVICE (configuration du port)**
```kotlin
retroView.setControllerType(1, 258)  // RETRO_DEVICE_ZAPPER sur port 2
retroView.sendMotionEvent(POINTER, x, y, port)  // Envoyer position
```

**2. OVERLAY SYSTEM (boutons + comportement)**

**Dans les .cfg RetroArch:**
```ini
# Overlay 1 = Lightgun mode (SNES.cfg)
overlay1_full_screen = true

# Boutons lightgun visibles:
overlay1_desc0 = "gun_start,0.06,0.948,rect,0.06,0.052"    # Start button
overlay1_desc1 = "gun_aux_a,0.06,0.25,rect,0.06,0.052"     # Aux button A
overlay1_desc2 = "gun_aux_b,0.94,0.25,rect,0.06,0.052"     # Aux button B
overlay1_desc3 = "gun_reload,0.94,0.948,rect,0.06,0.052"   # Reload
overlay1_desc4 = "gun_trigger,0.5,0.5,rect,0.9,0.9"        # Full screen trigger
```

**Options overlay globales (dans configuration.h):**
```c
input_overlay_lightgun_trigger_on_touch = true   // ← Permet tir full screen
input_overlay_lightgun_allow_offscreen = true    // ← Permet tir hors zone
input_overlay_lightgun_port = 0                  // ← Port du lightgun
```

---

## 📊 IMPACT DE LA CORRECTION

### AVANT (triggerOnTouch = false)
```
User touches screen
    ↓
ACTION_DOWN → Position sent to core ✅
    ↓
User holds finger → NO TRIGGER ❌
    ↓
ACTION_UP → Trigger sent ⏱️ (too late!)
    ↓
Duck already moved → MISS ❌
```

### APRÈS (triggerOnTouch = true)
```
User touches screen
    ↓
ACTION_DOWN → Position + Trigger INSTANT ✅
    ↓
BANG! → Duck falls ✅
```

---

## ⚠️ NOTE IMPORTANTE

**Le Zapper est actuellement DÉSACTIVÉ** dans `RetroArchEmulatorActivity.kt` (lignes commentées après que l'utilisateur ait dit "ok saute le zapper").

**Pour tester cette correction:**
1. Réactiver le code Zapper (lignes 560-565, 1059-1064)
2. Compiler
3. Tester Duck Hunt avec la nouvelle valeur par défaut
4. Les canards devraient tomber instantanément au touch!

---

## 🎯 RECOMMANDATION

Cette découverte est **POTENTIELLEMENT la solution** aux problèmes du Zapper!

**Options:**

**A. Tester maintenant:**
- Réactiver le Zapper
- Tester avec `triggerOnTouch = true`
- Voir si les canards tombent

**B. Attendre:**
- Continuer avec d'autres Quick Wins
- Revenir au Zapper plus tard avec cette correction

**C. Documentation:**
- Marquer cette découverte comme "À tester"
- Continuer l'audit

Que préférez-vous? 🎮

