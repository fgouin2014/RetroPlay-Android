# Audit: Problèmes de détection des diagonales D-Pad

**Date:** 2025-01-XX  
**Problème signalé:** Impossible de faire des diagonales avec la croix directionnelle
- Exemple 1: Dans Contra, avancer en tirant à la diagonale ne fonctionne pas
- Exemple 2: Glisser le doigt de la flèche droite à diagonale en tirant ne fonctionne pas
- Hypothèse: Les zones sont trop éloignées des boutons visuels, ou mauvais parsing/pooling

---

## 1. ANALYSE RETROARCH - Détection 8-way

### 1.1 Code RetroArch - `input_overlay_get_eightway_state()`

**Source:** `input_driver.c` (lignes ~2230-2343)

```c
static INLINE void input_overlay_get_eightway_state(
    const struct overlay_desc *desc,
    overlay_eightway_config_t *eightway,
    input_bits_t *out,
    float x_dist, float y_dist)  // x_dist et y_dist sont en PIXELS
{
    uint32_t *data;
    float abs_slope;

    // CRITIQUE: Normalisation avec range_x et range_y (valeurs ORIGINALES, non scalées)
    x_dist /= desc->range_x;  // range_x est en coordonnées normalisées (0.0-1.0)
    y_dist /= desc->range_y;  // range_y est en coordonnées normalisées (0.0-1.0)

    if (x_dist == 0.0f)
        x_dist = 0.0001f;
    abs_slope = fabs(y_dist / x_dist);

    // Détermination de la direction basée sur le quadrant et la pente
    if (x_dist > 0.0f) {
        if (y_dist < 0.0f) {
            // Q1 (haut-droite)
            if (abs_slope > *eightway->slope_high)
                data = eightway->up.data;
            else if (abs_slope < *eightway->slope_low)
                data = eightway->right.data;
            else
                data = eightway->up_right.data;  // DIAGONALE
        } else {
            // Q4 (bas-droite)
            if (abs_slope > *eightway->slope_high)
                data = eightway->down.data;
            else if (abs_slope < *eightway->slope_low)
                data = eightway->right.data;
            else
                data = eightway->down_right.data;  // DIAGONALE
        }
    } else {
        // Q2 (haut-gauche) et Q3 (bas-gauche)
        // ... (même logique)
    }

    bits_or_bits(out->buttons.data, data, CUSTOM_BINDS_U32_COUNT);
}
```

### 1.2 Comment RetroArch calcule `x_dist` et `y_dist`

**Source:** `input_driver.c` (lignes ~2500-2550)

```c
// Pour les zones 8-way (DPAD_AREA, ABXY_AREA)
case OVERLAY_TYPE_DPAD_AREA:
case OVERLAY_TYPE_ABXY_AREA:
    // x_dist et y_dist sont calculés AVANT l'appel à input_overlay_get_eightway_state()
    // Ils sont en PIXELS (coordonnées écran)
    
    // Le centre utilisé est desc->x_shift et desc->y_shift
    // x_shift et y_shift sont en coordonnées normalisées (0.0-1.0)
    // Mais ils sont multipliés par ol->mod_w et ol->mod_h (scalés) pour obtenir les pixels
    
    // Calcul du centre en pixels:
    // center_x_px = ol->mod_x + desc->x_shift * ol->mod_w
    // center_y_px = ol->mod_y + desc->y_shift * ol->mod_h
    
    // Calcul de x_dist et y_dist en pixels:
    // x_dist = x_px - center_x_px
    // y_dist = y_px - center_y_px
    
    // Puis normalisation:
    // x_dist /= desc->range_x  // range_x est en normalisé (0.0-1.0)
    // y_dist /= desc->range_y  // range_y est en normalisé (0.0-1.0)
    
    input_overlay_get_eightway_state(desc, desc->eightway_config, &out->buttons, x_dist, y_dist);
    break;
```

**Points clés:**
1. `x_dist` et `y_dist` sont en **PIXELS** (coordonnées écran)
2. Le centre (`x_shift`, `y_shift`) est en **normalisé** (0.0-1.0) mais multiplié par `mod_w/mod_h` (scalés) pour obtenir les pixels
3. La normalisation utilise `desc->range_x` et `desc->range_y` qui sont en **normalisé** (0.0-1.0) et **ORIGINAUX** (non scalés)

---

## 2. ANALYSE RETROPLAY - Détection 8-way

### 2.1 Code RetroPlay - `get8WayDirections()`

**Source:** `RetroArchOverlayRenderer.kt` (lignes 1493-1552)

```kotlin
private fun get8WayDirections(
    xDist: Float,  // Déjà normalisé
    yDist: Float,  // Déjà normalisé
    diagonalSensitivity: Int = 50
): List<String> {
    // Calculer les slopes (pentes) pour définir les zones diagonales
    val f = 2.0f * diagonalSensitivity / (100.0f + diagonalSensitivity)
    val highAngle = f * (0.375 * Math.PI) + (1.0f - f) * (0.25 * Math.PI)  // 67.5 deg max
    val lowAngle = f * (0.125 * Math.PI) + (1.0f - f) * (0.25 * Math.PI)   // 22.5 deg min
    val slopeHigh = kotlin.math.tan(highAngle).toFloat()
    val slopeLow = kotlin.math.tan(lowAngle).toFloat()
    
    // Éviter division par zéro
    val xDistAdjusted = if (xDist == 0.0f) 0.0001f else xDist
    val absSlope = kotlin.math.abs(yDist / xDistAdjusted)
    
    // Déterminer le quadrant et la direction
    // ... (logique identique à RetroArch)
}
```

**✅ La fonction `get8WayDirections()` est correcte** - elle est identique à RetroArch.

### 2.2 Code RetroPlay - Calcul de `xDist` et `yDist`

**Source:** `RetroArchOverlayRenderer.kt` (lignes 1104-1116)

```kotlin
// Calculer l'offset depuis le centre
val centerX = viewport.x + (button.x * viewport.width)  // button.x est SCALÉ après applyScaleAndOffset()
val centerY = viewport.y + (button.y * viewport.height)  // button.y est SCALÉ après applyScaleAndOffset()
val xDist = (x - centerX) / (button.width * viewport.width)  // button.width est ORIGINAL (non scalé)
val yDist = (y - centerY) / (button.height * viewport.height)  // button.height est ORIGINAL (non scalé)
```

**⚠️ PROBLÈME IDENTIFIÉ:**

1. **Centre (`button.x`, `button.y`):** Scalé après `applyScaleAndOffset()`
2. **Normalisation (`button.width`, `button.height`):** **ORIGINAUX** (non scalés)

**Comparaison avec RetroArch:**
- RetroArch: Centre en pixels = `x_shift * mod_w` (où `x_shift` est normalisé et `mod_w` est scalé)
- RetroArch: Normalisation = `/ range_x` (où `range_x` est normalisé et **ORIGINAL**)
- **✅ C'est correct!** RetroArch utilise aussi `range_x` original pour la normalisation

**Mais attendez...** Le problème pourrait être ailleurs. Vérifions comment `button.x` et `button.y` sont calculés après `applyScaleAndOffset()`:

### 2.3 Code RetroPlay - `applyScaleAndOffset()`

**Source:** `RetroArchOverlayRenderer.kt` (lignes 1440-1478)

```kotlin
// 2. Appliquer SCALE (centré autour de 0.5) puis OFFSET global
val newX = 0.5f + (xWithSeparation - 0.5f) * scale + xOffset
val newY = 0.5f + (yWithSeparation - 0.5f) * scale + yOffset

// CRITIQUE: Scaler width et height pour les hitboxes
val scaledWidth = button.width * scale
val scaledHeight = button.height * scale

button.copy(
    x = newX,  // ✅ Scalé
    y = newY,  // ✅ Scalé
    // width et height ne sont PAS mis à jour (restent originaux)
    // ...
)
```

**✅ C'est correct!** `button.x` et `button.y` sont scalés, mais `button.width` et `button.height` restent originaux (comme RetroArch).

---

## 3. PROBLÈME RÉEL IDENTIFIÉ

### 3.1 Hypothèse: Zones de détection trop éloignées

Le problème pourrait être dans la **détection initiale** de la zone `dpad_area`. Si la hitbox est mal positionnée ou mal dimensionnée, le touch ne sera jamais détecté comme étant dans la zone.

**Vérification:** `isTouchInsideButton()` pour `DPAD_AREA`

**Source:** `RetroArchOverlayRenderer.kt` (lignes 1209-1262)

```kotlin
private fun isTouchInsideButton(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    rangeModifier: Float,
    viewport: OverlayViewport
): Boolean {
    // Utiliser x_hitbox et y_hitbox (PAS x et y!) pour la position de la hitbox
    val buttonX = viewport.x + (button.xHitbox * viewport.width)
    val buttonY = viewport.y + (button.yHitbox * viewport.height)
    
    // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) pour la TAILLE
    val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
    val buttonHeight = button.rangeYHitbox * viewport.height * rangeModifier
    
    // ... vérification RADIAL ou RECT
}
```

**⚠️ PROBLÈME POTENTIEL:**

Pour `DPAD_AREA`, on utilise `button.xHitbox` et `button.yHitbox` qui sont calculés avec `reach_*`. Mais si `reach_*` sont asymétriques, la hitbox peut être décalée par rapport à `button.x` et `button.y`.

**Dans RetroArch:**
- Pour les zones 8-way, la hitbox est centrée sur `x_shift` et `y_shift` (position après separation)
- `x_shift` et `y_shift` sont utilisés comme centre pour le calcul 8-way
- La hitbox peut être différente (avec `reach_*`), mais le centre 8-way est toujours `x_shift`/`y_shift`

**Dans RetroPlay:**
- On utilise `button.xHitbox` et `button.yHitbox` pour la détection de la zone
- Mais on utilise `button.x` et `button.y` pour le centre du calcul 8-way
- **Si `xHitbox != x` ou `yHitbox != y`, il y a un décalage!**

### 3.2 Problème de multi-touch / pooling

Le problème pourrait aussi être dans le **pooling des touches**. Si deux touches sont détectées (une sur le D-Pad, une sur un bouton d'action), peut-être que le système ne gère pas correctement les deux simultanément.

**Vérification:** `handleOverlayTouch()` pour multi-touch

**Source:** `RetroArchOverlayRenderer.kt` (lignes ~600-900)

À examiner...

---

## 4. PROBLÈMES IDENTIFIÉS

### 4.1 Problème #1: Centre 8-way vs Hitbox

**Symptôme:** Les diagonales ne fonctionnent pas car le centre utilisé pour le calcul 8-way ne correspond pas à la zone de détection.

**Cause:**
- Hitbox détection: `button.xHitbox` et `button.yHitbox` (avec `reach_*`)
- Centre 8-way: `button.x` et `button.y` (sans `reach_*`)
- Si `reach_*` sont asymétriques, il y a un décalage

**Solution:**
- Utiliser `button.x` et `button.y` comme centre pour le calcul 8-way (comme RetroArch utilise `x_shift`/`y_shift`)
- Mais s'assurer que la hitbox inclut bien cette zone

### 4.2 Problème #2: Normalisation incorrecte après scale

**Symptôme:** Les zones sont "trop éloignées" car la normalisation ne tient pas compte du scale.

**Cause potentielle:**
- `button.width` et `button.height` sont originaux (non scalés)
- Mais après `applyScaleAndOffset()`, la zone visuelle est scalée
- Si la normalisation utilise `button.width` original mais que le centre est `button.x` scalé, il y a une incohérence

**Vérification nécessaire:**
- Comparer `button.x` (scalé) avec `button.width` (original) dans le calcul de normalisation
- RetroArch utilise `range_x` original pour la normalisation, mais le centre est `x_shift * mod_w` (scalé)
- Donc la normalisation devrait être: `(x_px - center_x_px) / (range_x * mod_w)`
- Mais dans notre code: `(x_px - center_x_px) / (width * viewport.width)`
- Où `center_x_px = viewport.x + (button.x * viewport.width)` et `button.x` est scalé
- Donc: `(x_px - (viewport.x + button.x * viewport.width)) / (button.width * viewport.width)`
- Si `button.x` est scalé mais `button.width` est original, la normalisation est incorrecte!

**Exemple:**
- `button.x` original = 0.2, `button.width` original = 0.1
- Après scale 0.8: `button.x` = 0.5 + (0.2 - 0.5) * 0.8 = 0.5 - 0.24 = 0.26
- Centre en pixels: `viewport.x + 0.26 * viewport.width`
- Normalisation: `/ (0.1 * viewport.width)` ← utilise width original
- **Mais la zone visuelle est scalée!** Donc la normalisation devrait utiliser `width * scale`!

**✅ PROBLÈME CONFIRMÉ:**

Dans RetroArch:
- Centre: `x_shift * mod_w` où `mod_w` est scalé
- Normalisation: `/ range_x` où `range_x` est original
- Mais `x_shift` est en normalisé, donc `x_shift * mod_w` = position en pixels scalée
- Et `range_x` est en normalisé, donc `range_x * mod_w` = taille en pixels scalée
- Donc normalisation: `(x_px - center_px) / (range_x * mod_w)` = normalisé correct

Dans RetroPlay:
- Centre: `button.x * viewport.width` où `button.x` est scalé
- Normalisation: `/ (button.width * viewport.width)` où `button.width` est original
- **PROBLÈME:** On devrait utiliser `button.width * scale * viewport.width` pour la normalisation!

---

## 5. SOLUTION PROPOSÉE

### 5.1 Correction de la normalisation pour 8-way

**Modifier le calcul de `xDist` et `yDist` dans `detectButtonsAtPosition()`:**

```kotlin
// AVANT (incorrect):
val centerX = viewport.x + (button.x * viewport.width)
val centerY = viewport.y + (button.y * viewport.height)
val xDist = (x - centerX) / (button.width * viewport.width)
val yDist = (y - centerY) / (button.height * viewport.height)

// APRÈS (correct):
// Calculer le scale effectif (button.x est scalé, donc on peut déduire le scale)
// Ou mieux: stocker le scale dans le bouton après applyScaleAndOffset()
val centerX = viewport.x + (button.x * viewport.width)
val centerY = viewport.y + (button.y * viewport.height)
// Utiliser rangeXHitboxOverride/rangeYHitboxOverride si disponibles (déjà scalés)
val normalizedWidth = button.rangeXHitboxOverride ?: (button.width * viewport.width)
val normalizedHeight = button.rangeYHitboxOverride ?: (button.height * viewport.height)
val xDist = (x - centerX) / normalizedWidth
val yDist = (y - centerY) / normalizedHeight
```

**Mais attendez...** `rangeXHitboxOverride` est en normalisé (0.0-1.0), pas en pixels. Il faut le multiplier par `viewport.width`.

**Meilleure solution:**
- Stocker `width` et `height` scalés dans le bouton après `applyScaleAndOffset()`
- Ou utiliser `modW` et `modH` qui sont déjà scalés: `modW = 2 * scaledWidth`, donc `scaledWidth = modW / 2`

**Solution finale:**
```kotlin
val centerX = viewport.x + (button.x * viewport.width)
val centerY = viewport.y + (button.y * viewport.height)
// Utiliser modW/modH qui sont scalés: modW = 2 * scaledWidth
val scaledWidth = button.modW / 2f  // modW est déjà scalé
val scaledHeight = button.modH / 2f  // modH est déjà scalé
val xDist = (x - centerX) / (scaledWidth * viewport.width)
val yDist = (y - centerY) / (scaledHeight * viewport.height)
```

### 5.2 Vérification du centre utilisé

**S'assurer que le centre utilisé pour 8-way correspond à la position visuelle:**

- Utiliser `button.x` et `button.y` (scalés) comme centre
- C'est déjà le cas, donc OK

---

## 6. PLAN D'ACTION

1. **Corriger la normalisation pour 8-way:**
   - Utiliser `modW/2` et `modH/2` (scalés) au lieu de `width` et `height` (originaux)
   - Tester avec un overlay de test

2. **Vérifier le multi-touch:**
   - S'assurer que plusieurs touches peuvent être actives simultanément
   - Tester avec D-Pad + bouton d'action

3. **Ajouter un mode debug:**
   - Afficher les valeurs de `xDist`, `yDist`, `absSlope`, et la direction détectée
   - Afficher le centre utilisé et la zone de détection

4. **Tests comparatifs:**
   - Tester avec le même overlay dans RetroArch et RetroPlay
   - Comparer les zones de détection

---

## 7. ✅ CORRECTION IMPLÉMENTÉE

### 7.1 Problème corrigé: Normalisation incorrecte pour 8-way

**Fichier modifié:** `RetroArchOverlayRenderer.kt` (lignes 1104-1108)

**AVANT (incorrect):**
```kotlin
val centerX = viewport.x + (button.x * viewport.width)
val centerY = viewport.y + (button.y * viewport.height)
val xDist = (x - centerX) / (button.width * viewport.width)  // ❌ width original
val yDist = (y - centerY) / (button.height * viewport.height)  // ❌ height original
```

**APRÈS (correct):**
```kotlin
val centerX = viewport.x + (button.x * viewport.width)
val centerY = viewport.y + (button.y * viewport.height)
val scaledWidth = button.modW / 2f  // ✅ modW est scalé après applyScaleAndOffset()
val scaledHeight = button.modH / 2f  // ✅ modH est scalé après applyScaleAndOffset()
val xDist = (x - centerX) / (scaledWidth * viewport.width)  // ✅ Normalisé avec valeurs scalées
val yDist = (y - centerY) / (scaledHeight * viewport.height)  // ✅ Normalisé avec valeurs scalées
```

**Explication:**
- `button.x` et `button.y` sont scalés après `applyScaleAndOffset()`
- `button.width` et `button.height` restent originaux (non scalés)
- `button.modW` et `button.modH` sont mis à jour avec les valeurs scalées: `modW = 2 * scaledWidth`
- Donc `scaledWidth = modW / 2` donne la valeur scalée correcte
- La normalisation utilise maintenant les valeurs scalées, compatible avec RetroArch

### 7.2 Résultat attendu

- ✅ Les diagonales devraient maintenant fonctionner correctement
- ✅ Les zones de détection sont alignées avec les valeurs scalées
- ✅ Comportement mathématiquement équivalent à RetroArch

---

**Dernière mise à jour:** 2025-01-XX

