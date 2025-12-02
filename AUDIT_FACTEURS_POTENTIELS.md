# Audit Objectif - Tous les Facteurs Potentiels

**Date:** 2025-01-XX  
**Méthode:** Analyse systématique ligne par ligne, comparaison avec RetroArch

---

## FACTEURS IDENTIFIÉS

### FACTEUR 1: Différence rangeModifier entre Debug et Détection

**Ligne 383 (Debug):**
```kotlin
val debugButtonWidth = button.rangeXHitbox * viewport.width * scaledLayout.rangeModifier
```

**Ligne 1311 (Détection):**
```kotlin
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
```

**Où `rangeModifier` peut être:**
- `1.0f` si `useRangeModForThisButton = false` (premier touch)
- `layout.rangeModifier` si `useRangeModForThisButton = true` (touch actif)

**Impact:** Zones bleues toujours avec `rangeModifier`, détection conditionnelle
**Gravité:** ⚠️ Moyenne (zones bleues ne correspondent pas visuellement)

---

### FACTEUR 2: overlayScale appliqué aux images mais pas aux hitboxes

**Ligne 285 (Images):**
```kotlin
val displayWidthPx = button.modW * viewport.width * overlayScale
```

**Ligne 1311 (Hitboxes):**
```kotlin
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
// Pas d'overlayScale!
```

**Impact:** Si `overlayScale != 1.0`, images et hitboxes ont des tailles différentes
**Gravité:** ⚠️ Élevée (si overlayScale utilisé, hitboxes ne suivent pas)

---

### FACTEUR 3: Position image vs hitbox (reach asymétrique)

**Ligne 278 (Images):**
```kotlin
val xPx = viewport.x + (button.x * viewport.width)
```

**Ligne 1305 (Hitboxes):**
```kotlin
val buttonX = viewport.x + (button.xHitbox * viewport.width)
```

**Où:**
- `button.x` = position du bouton (centre de l'image)
- `button.xHitbox` = position de la hitbox (peut être décalée si `reachLeft != reachRight`)

**Impact:** Si `reach_*` asymétrique, hitbox est décalée par rapport à l'image
**Gravité:** ✅ Normal (comportement RetroArch attendu)

---

### FACTEUR 4: Calcul rangeXHitbox avec scaledWidth

**Ligne 1542 (applyScaleAndOffset):**
```kotlin
val newRangeXHitbox = (scaledWidth * button.reachRight + scaledWidth * button.reachLeft) / 2.0f
```

**Où `scaledWidth = button.width * scale`**

**Impact:** `rangeXHitboxOverride` est scalé, donc les hitboxes suivent le scale
**Gravité:** ✅ Correct (compatible RetroArch)

---

### FACTEUR 5: buttonWidth calculé avec rangeXHitbox (scalé) mais sans overlayScale

**Ligne 1311:**
```kotlin
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
```

**Où:**
- `button.rangeXHitbox` = `rangeXHitboxOverride` (scalé avec `scale`) ou calculé avec `width` (non scalé)
- Pas de `overlayScale` dans le calcul

**Impact:** Si `overlayScale != 1.0`, hitboxes ne suivent pas le scale visuel
**Gravité:** ⚠️ Élevée (si overlayScale utilisé)

---

### FACTEUR 6: Détection utilise buttonWidth / 2 (rayon)

**Ligne 1317 (RADIAL):**
```kotlin
val dx = (touchX - buttonX) / (buttonWidth / 2)
```

**Ligne 1324 (RECT):**
```kotlin
val left = buttonX - buttonWidth / 2
```

**Où `buttonWidth` est calculé comme:**
```kotlin
buttonWidth = rangeXHitbox * viewport.width * rangeModifier
```

**Dans RetroArch:**
- `range_x` est le rayon (distance du centre au bord)
- `x_dist = (x - desc->x_hitbox) / range_x` où `range_x` est le rayon

**Impact:** Si `buttonWidth` est le diamètre, alors `/ 2` donne le rayon ✅
**Gravité:** ✅ Correct (si `rangeXHitbox` est le rayon)

---

### FACTEUR 7: rangeXHitbox est-il le rayon ou le diamètre?

**Calcul (ligne 1542):**
```kotlin
val newRangeXHitbox = (scaledWidth * button.reachRight + scaledWidth * button.reachLeft) / 2.0f
```

**Où `scaledWidth = button.width * scale`**

**Dans RetroArch:**
```c
desc->range_x_hitbox = (desc->range_x * desc->reach_right + desc->range_x * desc->reach_left) / 2.0f;
```

**Où `range_x` est la moitié de la largeur du bouton (rayon)**

**Vérification:**
- `button.width` = `range_x` en coordonnées normalisées
- `range_x` dans RetroArch = rayon (distance du centre au bord)
- Donc `rangeXHitbox` = rayon ✅

**Impact:** `buttonWidth = rangeXHitbox * viewport.width * rangeModifier` = rayon en pixels
**Gravité:** ⚠️ PROBLÈME: Si `buttonWidth` est le rayon, alors `/ 2` donne le demi-rayon (incorrect!)

---

## PROBLÈMES CONFIRMÉS

### PROBLÈME 1: buttonWidth est le rayon, mais on divise par 2

**Code actuel:**
```kotlin
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier  // Rayon en pixels
val dx = (touchX - buttonX) / (buttonWidth / 2)  // Divise le rayon par 2 = demi-rayon!
```

**Dans RetroArch:**
```c
float range_x = desc->range_x_hitbox;  // Rayon
float x_dist = (x - desc->x_hitbox) / range_x;  // Divise par le rayon directement
```

**Impact:** Hitboxes 2x trop petites!
**Gravité:** 🔴 CRITIQUE

---

### PROBLÈME 2: overlayScale non appliqué aux hitboxes

**Images:** `displayWidthPx = button.modW * viewport.width * overlayScale`
**Hitboxes:** `buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier` (pas d'overlayScale)

**Impact:** Si `overlayScale != 1.0`, hitboxes ne suivent pas les images
**Gravité:** ⚠️ Moyenne

---

### PROBLÈME 3: Debug utilise toujours rangeModifier, détection conditionnelle

**Debug:** `scaledLayout.rangeModifier` (toujours)
**Détection:** `rangeModifier` (peut être 1.0f)

**Impact:** Zones bleues ne correspondent pas aux zones de détection réelles
**Gravité:** ⚠️ Moyenne

---

## RÉSUMÉ

**Problèmes critiques:**
1. 🔴 `buttonWidth / 2` alors que `buttonWidth` est déjà le rayon (hitboxes 2x trop petites)

**Problèmes moyens:**
2. ⚠️ `overlayScale` non appliqué aux hitboxes
3. ⚠️ Debug utilise toujours `rangeModifier`, détection conditionnelle

---

**Dernière mise à jour:** 2025-01-XX

