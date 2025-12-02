# Audit Systématique Complet - Tous les Facteurs Potentiels

**Date:** 2025-01-XX  
**Objectif:** Identifier TOUS les problèmes potentiels sans assumptions

---

## 1. POINTS DE VÉRIFICATION SYSTÉMATIQUES

### 1.1 Calcul des hitboxes de base (OverlayModels.kt)

**Code actuel:**
```kotlin
val xHitbox: Float = xHitboxOverride ?: ((x + width * reachRight) + (x - width * reachLeft)) / 2.0f
val yHitbox: Float = yHitboxOverride ?: ((y + height * reachDown) + (y - height * reachUp)) / 2.0f
val rangeXHitbox: Float = rangeXHitboxOverride ?: (width * reachRight + width * reachLeft) / 2.0f
val rangeYHitbox: Float = rangeYHitboxOverride ?: (height * reachDown + height * reachUp) / 2.0f
```

**À vérifier:**
- ✅ Formule identique à RetroArch
- ⚠️ Utilise `width`/`height` originaux (non scalés)

---

### 1.2 Application du scale/offset/separation (applyScaleAndOffset)

**Code actuel:**
```kotlin
val scaledWidth = button.width * scale
val scaledHeight = button.height * scale
val newXHitbox = ((newX + scaledWidth * button.reachRight) + (newX - scaledWidth * button.reachLeft)) / 2.0f
val newYHitbox = ((newY + scaledHeight * button.reachDown) + (newY - scaledHeight * button.reachUp)) / 2.0f
val newRangeXHitbox = (scaledWidth * button.reachRight + scaledWidth * button.reachLeft) / 2.0f
val newRangeYHitbox = (scaledHeight * button.reachDown + scaledHeight * button.reachUp) / 2.0f
```

**À vérifier:**
- ⚠️ Utilise `scaledWidth`/`scaledHeight` (scalés) pour calculer les hitboxes
- ⚠️ `newX` est déjà après scale/offset/separation
- ⚠️ Compatible avec RetroArch? (`scale_w = ol->mod_w * desc->range_x` où `mod_w` est scalé)

---

### 1.3 Projection dans le viewport (isTouchInsideButton)

**Code actuel:**
```kotlin
val buttonX = viewport.x + (button.xHitbox * viewport.width)
val buttonY = viewport.y + (button.yHitbox * viewport.height)
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
val buttonHeight = button.rangeYHitbox * viewport.height * rangeModifier
```

**À vérifier:**
- ⚠️ `button.xHitbox` est en coordonnées normalisées (0.0-1.0)
- ⚠️ `viewport.width` est en pixels
- ⚠️ `buttonWidth` = `rangeXHitbox * viewport.width * rangeModifier`
- ⚠️ `rangeXHitbox` peut être `rangeXHitboxOverride` (scalé) ou calculé avec `width` (non scalé)

---

### 1.4 Détection RADIAL

**Code actuel:**
```kotlin
val dx = (touchX - buttonX) / (buttonWidth / 2)
val dy = (touchY - buttonY) / (buttonHeight / 2)
val distance = sqrt(dx * dx + dy * dy)
distance <= 1.0f
```

**À vérifier:**
- ⚠️ `buttonWidth / 2` = rayon
- ⚠️ Compatible RetroArch: `x_dist = (x - desc->x_hitbox) / range_x` où `range_x` est le rayon

---

### 1.5 Détection RECT

**Code actuel:**
```kotlin
val left = buttonX - buttonWidth / 2
val right = buttonX + buttonWidth / 2
val top = buttonY - buttonHeight / 2
val bottom = buttonY + buttonHeight / 2
```

**À vérifier:**
- ⚠️ `buttonWidth / 2` = rayon
- ⚠️ Compatible RetroArch: `fabs(x - desc->x_hitbox) <= range_x` où `range_x` est le rayon

---

### 1.6 Debug (zones bleues)

**Code actuel:**
```kotlin
val hitboxX = viewport.x + (button.xHitbox * viewport.width)
val hitboxY = viewport.y + (button.yHitbox * viewport.height)
val debugButtonWidth = button.rangeXHitbox * viewport.width * scaledLayout.rangeModifier
val debugButtonHeight = button.rangeYHitbox * viewport.height * scaledLayout.rangeModifier
// RADIAL: radius = (debugButtonWidth / 2)
// RECT: size = Size(debugButtonWidth, debugButtonHeight), topLeft = Offset(hitboxX - debugButtonWidth / 2, ...)
```

**À vérifier:**
- ⚠️ Utilise `scaledLayout.rangeModifier` (toujours)
- ⚠️ Détection utilise `rangeModifier` passé en paramètre (peut être 1.0f)
- ⚠️ **DIFFÉRENCE POTENTIELLE:** Debug toujours avec rangeModifier, détection conditionnelle

---

### 1.7 Affichage des images

**Code actuel:**
```kotlin
val xPx = viewport.x + (button.x * viewport.width)
val yPx = viewport.y + (button.y * viewport.height)
val displayWidthPx = button.modW * viewport.width * overlayScale
val displayHeightPx = button.modH * viewport.height * overlayScale
var modXPx = viewport.x + (button.modX * viewport.width)
var modYPx = viewport.y + (button.modY * viewport.height)
```

**À vérifier:**
- ⚠️ Images utilisent `button.x`/`button.y` (position du bouton)
- ⚠️ Hitboxes utilisent `button.xHitbox`/`button.yHitbox` (position de la hitbox, peut être décalée)
- ⚠️ **DIFFÉRENCE POTENTIELLE:** Image et hitbox peuvent être à des positions différentes si `reach_*` asymétrique

---

## 2. PROBLÈMES POTENTIELS IDENTIFIÉS

### 2.1 Incohérence scale dans rangeXHitbox

**Problème:**
- `rangeXHitboxOverride` est calculé avec `scaledWidth` (scalé)
- Mais `rangeXHitbox` (par défaut) est calculé avec `width` (non scalé)
- Si `rangeXHitboxOverride` n'est pas défini, on utilise la version non scalée

**Impact:** Hitboxes peuvent être de mauvaise taille si override non défini

---

### 2.2 Différence debug vs détection (rangeModifier)

**Problème:**
- Debug utilise toujours `scaledLayout.rangeModifier`
- Détection utilise `rangeModifier` passé en paramètre (peut être 1.0f si `useRangeModForThisButton = false`)

**Impact:** Zones bleues ne correspondent pas aux zones de détection réelles

---

### 2.3 Position image vs hitbox

**Problème:**
- Images utilisent `button.x`/`button.y` (position du bouton)
- Hitboxes utilisent `button.xHitbox`/`button.yHitbox` (position de la hitbox)
- Si `reach_*` asymétrique, hitbox peut être décalée par rapport à l'image

**Impact:** Zones bleues peuvent être décalées par rapport aux images

---

### 2.4 Calcul buttonWidth avec rangeXHitboxOverride

**Problème:**
- `buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier`
- `button.rangeXHitbox` peut être `rangeXHitboxOverride` (scalé) ou calculé avec `width` (non scalé)
- Si `rangeXHitboxOverride` est scalé, alors `buttonWidth` est double-scalé?

**Impact:** Hitboxes peuvent être de mauvaise taille

---

### 2.5 overlayScale appliqué aux images mais pas aux hitboxes

**Problème:**
- Images: `displayWidthPx = button.modW * viewport.width * overlayScale`
- Hitboxes: `buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier` (pas d'overlayScale)

**Impact:** Images et hitboxes peuvent avoir des tailles différentes si `overlayScale != 1.0`

---

## 3. QUESTIONS À RÉSOUDRE

1. **`rangeXHitboxOverride` est-il toujours défini après `applyScaleAndOffset()`?**
   - Si oui, problème 2.1 résolu
   - Si non, problème 2.1 existe

2. **Le debug doit-il utiliser le même `rangeModifier` que la détection?**
   - Si oui, problème 2.2 à corriger
   - Si non, c'est intentionnel

3. **Les images et hitboxes doivent-ils être à la même position?**
   - Si oui, problème 2.3 à corriger
   - Si non, c'est intentionnel (reach asymétrique)

4. **`overlayScale` doit-il être appliqué aux hitboxes?**
   - Si oui, problème 2.5 à corriger
   - Si non, c'est intentionnel

---

**Dernière mise à jour:** 2025-01-XX

