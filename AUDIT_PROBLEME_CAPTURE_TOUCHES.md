# Audit: Problème Capture Touches et Hitboxes

**Date:** 2025-01-XX  
**Problème:** L'overlay a de la difficulté à capturer les touches, et les zones bleues (hitboxes) ne correspondent pas.

**Hypothèse:** Les paramètres du menu (scale, offset, separation) affectent incorrectement les hitboxes.

---

## 1. PROBLÈME IDENTIFIÉ: `overlayScale` appliqué deux fois

### 1.1 Application de `overlayScale`

**Dans `applyScaleAndOffset()`:**
```kotlin
val scaledLayout = remember(layout, overlayScale, ...) {
    applyScaleAndOffset(layout, overlayScale, ...)  // overlayScale passé comme paramètre scale
}

// Dans applyScaleAndOffset():
val newX = 0.5f + (xWithSeparation - 0.5f) * scale + xOffset  // scale = overlayScale
// Les hitboxes sont calculées avec newX (déjà scalé)
```

**Dans le calcul des hitboxes pour l'affichage (ligne 292-293):**
```kotlin
val hitboxWidthPx = button.rangeXHitbox * viewport.width * effectiveRangeMod * overlayScale
// ❌ PROBLÈME: overlayScale est appliqué ENCORE!
```

**Dans `isTouchInsideButton()` (ligne 1304-1305):**
```kotlin
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
// ✅ CORRECT: overlayScale n'est PAS appliqué (déjà dans rangeXHitbox via applyScaleAndOffset)
```

### 1.2 Problème

- **Affichage debug:** Utilise `overlayScale` (double scale!)
- **Détection:** N'utilise pas `overlayScale` (correct, car déjà dans `rangeXHitboxOverride`)
- **Résultat:** Les zones bleues ne correspondent pas aux zones de détection

---

## 2. VÉRIFICATION RETROARCH

**RetroArch:**
```c
// input_overlay_scale() applique le scale une fois
ol->mod_w = ol->w * layout->x_scale;  // x_scale = overlayScale
scale_w = ol->mod_w * desc->range_x;  // range_x est multiplié par mod_w (déjà scalé)

// input_overlay_desc_init_hitbox() calcule les hitboxes
desc->range_x_hitbox = (desc->range_x * reach_right + ...) / 2.0f;
// range_x est déjà effectivement scalé via mod_w

// Pour la détection
range_x = desc->range_x_hitbox;  // Utilise range_x_hitbox (déjà scalé)
// Pas de multiplication supplémentaire par x_scale!
```

**Notre implémentation:**
```kotlin
// applyScaleAndOffset() applique le scale
val newX = 0.5f + (xWithSeparation - 0.5f) * scale + xOffset  // scale = overlayScale
val newRangeXHitbox = (button.width * button.reachRight + ...) / 2.0f
// ❌ PROBLÈME: button.width est NON SCALÉ, mais newX est SCALÉ!

// Pour l'affichage debug
val hitboxWidthPx = button.rangeXHitbox * viewport.width * effectiveRangeMod * overlayScale
// ❌ PROBLÈME: overlayScale appliqué ENCORE (double scale!)

// Pour la détection
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
// ✅ CORRECT: Pas de overlayScale (déjà dans rangeXHitboxOverride)
```

---

## 3. PROBLÈME FONDAMENTAL

### 3.1 Les hitboxes ne sont pas scalées correctement

**Dans RetroArch:**
- `range_x` est multiplié par `mod_w` (déjà scalé) pour obtenir `scale_w`
- `input_overlay_desc_init_hitbox()` utilise `desc->range_x` (qui est effectivement scalé via `mod_w`)
- Les hitboxes sont donc scalées correctement

**Dans notre code:**
- `newRangeXHitbox` est calculé avec `button.width` (NON SCALÉ)
- Mais `newX` est SCALÉ
- **Incohérence:** Position scalée mais taille non scalée!

### 3.2 Solution

**Option 1: Scaler `width` et `height` avant de calculer les hitboxes**
```kotlin
val scaledWidth = button.width * scale
val scaledHeight = button.height * scale
val newRangeXHitbox = (scaledWidth * button.reachRight + scaledWidth * button.reachLeft) / 2.0f
```

**Option 2: Ne pas scaler les hitboxes, appliquer le scale lors de la projection**
```kotlin
// Calculer hitboxes avec width/height NON SCALÉS
val newRangeXHitbox = (button.width * button.reachRight + button.width * button.reachLeft) / 2.0f
// Appliquer scale lors de la projection
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier * overlayScale
```

**Problème avec Option 2:** Dans `isTouchInsideButton()`, on n'a pas accès à `overlayScale`!

---

## 4. PROBLÈME DE CAPTURE DES TOUCHES

### 4.1 Conversion des touches dans le viewport

**Vérification nécessaire:**
- Les touches sont-elles correctement converties dans le viewport?
- Les coordonnées touch sont-elles en pixels écran ou en pixels viewport?

**Code actuel:**
```kotlin
val x = event.getX(pointerIndex)  // Coordonnées en pixels écran
val y = event.getY(pointerIndex)

// Utilisé directement dans isTouchInsideButton()
isTouchInsideButton(x, y, button, effectiveRangeMod, viewport)
```

**Dans `isTouchInsideButton()`:**
```kotlin
val buttonX = viewport.x + (button.xHitbox * viewport.width)  // Projeté dans viewport
val buttonY = viewport.y + (button.yHitbox * viewport.height)
```

**Vérification:**
- Si `x` et `y` sont en pixels écran, ils doivent être convertis dans le viewport
- Si `x` et `y` sont déjà dans le viewport, c'est correct

---

## 5. PLAN D'ACTION

1. **Corriger l'application de `overlayScale`:**
   - Retirer `overlayScale` du calcul des hitboxes dans le debug (ligne 292-293)
   - Vérifier que `overlayScale` est appliqué correctement dans `applyScaleAndOffset()`

2. **Vérifier la conversion des touches:**
   - S'assurer que les touches sont correctement converties dans le viewport
   - Ajouter des logs pour vérifier les coordonnées

3. **Tester avec différents paramètres:**
   - Scale = 0.5, 1.0, 1.5
   - Offset = -0.2, 0.0, 0.2
   - Separation = -0.2, 0.0, 0.2

---

**Dernière mise à jour:** 2025-01-XX

