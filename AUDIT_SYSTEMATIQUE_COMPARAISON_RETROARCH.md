# Audit Systématique: Comparaison RetroArch vs RetroPlay

**Date:** 2025-01-XX  
**Objectif:** Identifier TOUTES les différences entre notre implémentation et RetroArch officiel

**Pourquoi ce problème n'a pas été détecté avant:**
- Les audits précédents se concentraient sur les **calculs de hitboxes** (positions, tailles, scaling)
- Ils ne couvraient pas la **gestion du multi-touch** et le **maintien des touches actives**
- La logique de `range_mod` était partiellement implémentée mais pas utilisée correctement
- On vérifiait les **formules mathématiques** mais pas le **flux de données** (touch_mask, old_touch_mask)

---

## 1. MÉTHODOLOGIE D'AUDIT

### 1.1 Approche précédente (incomplète)

**Focus:**
- ✅ Calculs de hitboxes (x_hitbox, y_hitbox, range_x_hitbox, range_y_hitbox)
- ✅ Scaling et transformations (applyScaleAndOffset)
- ✅ Formules mathématiques (reach_*, range_mod)
- ✅ Détection 8-way (diagonales)

**Manqué:**
- ❌ Gestion du multi-touch (touch_mask, old_touch_mask)
- ❌ Flux de données entre les frames (maintien des touches)
- ❌ Application conditionnelle de range_mod (par touch, pas global)
- ❌ Système de tracking des touches actives par bouton

### 1.2 Nouvelle approche (systématique)

**Méthode:**
1. Examiner **TOUTES** les structures de données RetroArch
2. Examiner **TOUS** les flux de données (poll, update, clear)
3. Comparer **chaque fonction** avec notre implémentation
4. Identifier **chaque différence** même si elle semble mineure

---

## 2. STRUCTURES DE DONNÉES RETROARCH

### 2.1 `struct overlay_desc` (descripteur de bouton)

**Source:** `input_driver.c` (lignes ~2000-2200)

```c
struct overlay_desc {
    // Position et taille
    float x, y;                    // Position centre (normalisé 0.0-1.0)
    float range_x, range_y;         // Taille (normalisé 0.0-1.0)
    float x_shift, y_shift;        // Position après separation (normalisé)
    
    // Hitboxes
    float x_hitbox, y_hitbox;      // Position hitbox (avec reach_*)
    float range_x_hitbox, range_y_hitbox;  // Taille hitbox (avec reach_*)
    float range_x_mod, range_y_mod;  // Taille hitbox avec range_mod
    
    // Touch tracking
    uint32_t touch_mask;           // Masque des touches actives (1 bit par touch)
    uint32_t old_touch_mask;       // Masque des touches précédentes
    
    // Flags
    unsigned flags;                // OVERLAY_DESC_EXCLUSIVE, OVERLAY_DESC_RANGE_MOD_EXCLUSIVE, etc.
    
    // Range mod
    float range_mod;               // Multiplicateur pour hitbox étendue
    
    // ...
};
```

**Points clés:**
- `touch_mask`: Masque des touches actives pour ce bouton (1 bit par touch, max 32 touches)
- `old_touch_mask`: Masque des touches précédentes (pour déterminer use_range_mod)
- `range_x_mod`, `range_y_mod`: Hitbox étendue avec range_mod

### 2.2 Notre équivalent

**Source:** `OverlayButton` dans `OverlayModels.kt`

```kotlin
data class OverlayButton(
    val x: Float,                  // ✅ Position centre
    val y: Float,                  // ✅ Position centre
    val width: Float,               // ✅ Taille (range_x)
    val height: Float,             // ✅ Taille (range_y)
    val xHitbox: Float,            // ✅ Position hitbox (calculée)
    val yHitbox: Float,            // ✅ Position hitbox (calculée)
    val rangeXHitbox: Float,       // ✅ Taille hitbox (calculée)
    val rangeYHitbox: Float,       // ✅ Taille hitbox (calculée)
    // ❌ PAS de touch_mask par bouton (ajouté maintenant)
    // ❌ PAS de old_touch_mask par bouton (ajouté maintenant)
    // ❌ PAS de range_x_mod, range_y_mod stockés (calculés à la volée)
)
```

**Différences identifiées:**
- ✅ **Corrigé:** `buttonTouchMasks` ajouté pour tracker les touches actives par bouton
- ⚠️ **À vérifier:** Stockage de `range_x_mod`/`range_y_mod` (calculés à la volée actuellement)

---

## 3. FLUX DE DONNÉES RETROARCH

### 3.1 Cycle de vie d'un touch

**RetroArch:**
```
1. input_overlay_poll() appelé pour chaque touch
   ├── use_range_mod = (old_touch_idx != -1) && BIT32_GET(desc->old_touch_mask, old_touch_idx)
   ├── Vérifier hitbox avec use_range_mod
   ├── Si touché: BIT32_SET(desc->touch_mask, touch_idx)
   └── Retourner true si touché

2. input_overlay_post_poll() appelé après tous les polls
   ├── Mettre à jour alpha mods pour boutons pressés
   ├── desc->old_touch_mask = desc->touch_mask
   └── desc->touch_mask = 0 (réinitialiser pour prochain frame)

3. Prochain frame: old_touch_mask utilisé pour déterminer use_range_mod
```

**Notre implémentation (avant correction):**
```
1. ACTION_DOWN: detectButtonsAtPosition()
   ├── useRangeMod = false (global)
   ├── Détecter boutons
   └── Enregistrer dans pressedButtons[pointerId]

2. ACTION_MOVE: detectButtonsAtPosition()
   ├── useRangeMod = previousButtons.isNotEmpty() (global, incorrect!)
   ├── Détecter boutons
   ├── newButtons = currentButtons - previousButtons
   ├── releasedButtons = previousButtons - currentButtons
   └── Relâcher immédiatement si touch sort de hitbox

3. ACTION_UP: Relâcher tous les boutons
```

**Problèmes identifiés:**
- ❌ **Corrigé:** `useRangeMod` était global, pas par bouton
- ❌ **Corrigé:** Pas de tracking des touches actives par bouton
- ❌ **Corrigé:** Relâchement immédiat au lieu de maintenir avec range_mod

### 3.2 Application de `range_mod`

**RetroArch:**
```c
// Pour chaque bouton, pour chaque touch
use_range_mod = (old_touch_idx != -1) && BIT32_GET(desc->old_touch_mask, old_touch_idx);

if (use_range_mod) {
    range_x = desc->range_x_mod;  // Hitbox étendue
    range_y = desc->range_y_mod;
} else {
    range_x = desc->range_x_hitbox;  // Hitbox normale
    range_y = desc->range_y_hitbox;
}
```

**Notre implémentation (avant correction):**
```kotlin
// Global pour tous les boutons
val useRangeMod = previousButtons.isNotEmpty()

// Pour tous les boutons
val buttonWidth = button.rangeXHitbox * viewport.width * (if (useRangeMod) layout.rangeModifier else 1.0f)
```

**Problèmes identifiés:**
- ❌ **Corrigé:** `useRangeMod` était global, pas par bouton
- ❌ **Corrigé:** Tous les boutons utilisaient le même `useRangeMod`

---

## 4. AUTRES DIFFÉRENCES POTENTIELLES

### 4.1 Gestion des touches multiples

**RetroArch:**
- Support jusqu'à 32 touches simultanées (touch_mask = uint32_t, 32 bits)
- Chaque touch est tracké indépendamment
- `old_touch_idx` permet de suivre un touch entre les frames

**Notre implémentation:**
- Support multi-touch Android natif (illimité théoriquement)
- Tracking par `pointerId` (Android)
- ⚠️ **À vérifier:** Gestion correcte des touches multiples simultanées

### 4.2 Exclusive hitboxes

**RetroArch:**
```c
// Priorité 2: range_mod_exclusive
if (use_range_mod && (desc->flags & OVERLAY_DESC_RANGE_MOD_EXCLUSIVE))
    desc_prio = 2;
// Priorité 1: exclusive
else if (desc->flags & OVERLAY_DESC_EXCLUSIVE)
    desc_prio = 1;

// Si priorité plus élevée, effacer tous les précédents
if (desc_prio > highest_prio) {
    memset(out, 0, sizeof(*out));
    for (j = 0; j < i; j++)
        BIT32_CLEAR(descs[j].touch_mask, touch_idx);
}
```

**Notre implémentation:**
```kotlin
var priority = 0
if (useRangeModForThisButton && button.rangeModExclusive && previousTouchedButtons.contains(button)) {
    priority = 2
} else if (button.exclusive) {
    priority = 1
}

if (priority > highestPriority) {
    highestPriority = priority
    touched.clear()  // Effacer tous les précédents
}
```

**Analyse:**
- ✅ **Correct:** Logique de priorité identique
- ⚠️ **À vérifier:** Effacement correct des touch_mask dans RetroArch (BIT32_CLEAR)

### 4.3 Analog sticks avec range_mod

**RetroArch:**
```c
input_overlay_get_analog_state(
    out, desc, base, x, y,
    &x_dist, &y_dist, !use_range_mod);  // Inversé: !use_range_mod
```

**Notre implémentation:**
```kotlin
// Analog sticks: pas de range_mod pour les valeurs analogiques
// (range_mod est seulement pour les hitboxes)
```

**Analyse:**
- ✅ **Correct:** Analog sticks n'utilisent pas range_mod pour les valeurs
- ⚠️ **À vérifier:** Comportement exact de `!use_range_mod` dans RetroArch

### 4.4 Movable buttons

**RetroArch:**
```c
if (desc->flags & OVERLAY_DESC_MOVABLE) {
    desc->delta_x = clamp_float(x_dist, -desc->range_x, desc->range_x) * ol->active->mod_w;
    desc->delta_y = clamp_float(y_dist, -desc->range_y, desc->range_y) * ol->active->mod_h;
}
```

**Notre implémentation:**
```kotlin
if (button.movable) {
    val clampedDx = dx.coerceIn(-baseRangeX, baseRangeX)
    val clampedDy = dy.coerceIn(-baseRangeY, baseRangeY)
    movableButtonDeltas[buttonIndex] = Pair(clampedDx, clampedDy)
}
```

**Analyse:**
- ✅ **Correct:** Logique de clamp identique
- ⚠️ **À vérifier:** Application correcte de mod_w/mod_h dans RetroArch

---

## 5. CHECKLIST SYSTÉMATIQUE

### 5.1 Structures de données

- [x] `touch_mask` par bouton (corrigé)
- [x] `old_touch_mask` par bouton (corrigé via buttonTouchMasks)
- [ ] `range_x_mod`, `range_y_mod` stockés (calculés à la volée actuellement)
- [ ] `delta_x`, `delta_y` pour movable (stockés dans movableButtonDeltas)

### 5.2 Flux de données

- [x] `use_range_mod` par bouton et par touch (corrigé)
- [x] Maintien des touches actives (corrigé)
- [ ] Mise à jour de `old_touch_mask` après chaque frame
- [ ] Réinitialisation de `touch_mask` après chaque frame

### 5.3 Fonctions

- [x] `detectButtonsAtPosition()` avec `buttonTouchMasks` (corrigé)
- [x] `isTouchInsideButton()` avec `rangeModifier` (corrigé)
- [ ] `input_overlay_post_poll()` équivalent (mise à jour old_touch_mask)
- [ ] `input_overlay_poll_clear()` équivalent (nettoyage si pas de touches)

### 5.4 Comportements spécifiques

- [x] Exclusive hitboxes (priorité 1 et 2)
- [x] Range_mod_exclusive (priorité 2)
- [ ] Analog sticks avec `!use_range_mod`
- [ ] Movable buttons avec mod_w/mod_h

---

## 6. PROBLÈMES POTENTIELS RESTANTS

### 6.1 Mise à jour de `old_touch_mask`

**RetroArch:**
```c
// Après tous les polls
desc->old_touch_mask = desc->touch_mask;
desc->touch_mask = 0;  // Réinitialiser pour prochain frame
```

**Notre implémentation:**
- `buttonTouchMasks` est mis à jour en temps réel (ACTION_DOWN, ACTION_MOVE, ACTION_UP)
- ⚠️ **À vérifier:** Pas de réinitialisation entre les frames (Android gère les events différemment)

**Analyse:**
- Android envoie des events (ACTION_DOWN, ACTION_MOVE, ACTION_UP) au lieu de polls
- Notre approche est correcte pour Android, mais peut différer de RetroArch

### 6.2 Gestion des frames

**RetroArch:**
- Poll-based: `input_overlay_poll()` appelé pour chaque touch à chaque frame
- `old_touch_mask` mis à jour après tous les polls

**Notre implémentation:**
- Event-based: `handleOverlayTouch()` appelé pour chaque event
- `buttonTouchMasks` mis à jour en temps réel

**Analyse:**
- Différence d'architecture (poll vs event), mais comportement équivalent

### 6.3 Analog sticks avec range_mod

**RetroArch:**
```c
input_overlay_get_analog_state(
    out, desc, base, x, y,
    &x_dist, &y_dist, !use_range_mod);  // Inversé!
```

**Notre implémentation:**
```kotlin
// Analog sticks n'utilisent pas range_mod
```

**Analyse:**
- ✅ **Correct:** Analog sticks n'utilisent pas range_mod pour les valeurs
- ⚠️ **À vérifier:** Comportement exact de `!use_range_mod` dans RetroArch

---

## 7. RECOMMANDATIONS

### 7.1 Vérifications à faire

1. **Tester le maintien des touches:**
   - Super Mario: courir + sauter simultanément
   - Contra: avancer + tirer en diagonale
   - Glissement entre boutons adjacents

2. **Vérifier les hitboxes étendues:**
   - Les hitboxes doivent s'étendre avec `range_mod` quand le doigt bouge
   - Les zones doivent se chevaucher suffisamment

3. **Vérifier le multi-touch:**
   - Plusieurs touches simultanées sur différents boutons
   - Plusieurs touches simultanées sur le même bouton (si supporté)

### 7.2 Améliorations potentielles

1. **Stockage de `range_x_mod`/`range_y_mod`:**
   - Actuellement calculés à la volée
   - Pourrait être stockés dans `OverlayButton` pour performance

2. **Réinitialisation entre frames:**
   - Android gère les events différemment de RetroArch
   - Peut-être pas nécessaire, mais à vérifier

3. **Debug mode amélioré:**
   - Afficher `buttonTouchMasks` en temps réel
   - Afficher `useRangeModForThisButton` pour chaque bouton
   - Afficher les hitboxes étendues vs normales

---

## 8. CONCLUSION

**Problèmes identifiés et corrigés:**
- ✅ `touch_mask` par bouton (buttonTouchMasks)
- ✅ `use_range_mod` par bouton et par touch
- ✅ Maintien des touches actives avec range_mod

**Différences d'architecture (acceptables):**
- Poll-based (RetroArch) vs Event-based (Android)
- Gestion des frames différente mais équivalente

**À vérifier:**
- Comportement exact avec plusieurs touches simultanées
- Performance avec hitboxes étendues
- Cas limites (touches très rapides, glissements très courts)

---

**Dernière mise à jour:** 2025-01-XX

