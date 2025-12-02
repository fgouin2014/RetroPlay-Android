# Audit Complet: Tous les Types de Zones de Détection

**Date:** 2025-01-XX  
**Objectif:** Vérifier que TOUS les types de zones de détection sont corrects selon RetroArch

---

## 1. TYPES DE ZONES À VÉRIFIER

1. **RADIAL** (elliptique) - Boutons circulaires
2. **RECT** (rectangulaire) - Boutons rectangulaires
3. **DPAD_AREA** (zone D-pad avec détection 8-way)
4. **ABXY_AREA** (zone ABXY avec détection 8-way)
5. **ANALOG_LEFT** et **ANALOG_RIGHT** (sticks analogiques)

---

## 2. VÉRIFICATION RETROARCH

### 2.1 Types de Hitboxes dans RetroArch

**Source:** `c:\repos\RetroArch-master\input\input_driver.c`

```c
enum overlay_hitbox
{
   OVERLAY_HITBOX_RADIAL = 0,
   OVERLAY_HITBOX_RECT,
   OVERLAY_HITBOX_NONE
};
```

**Détection:**
```c
switch (desc->hitbox)
{
   case OVERLAY_HITBOX_RADIAL:
   {
      float x_dist  = (x - desc->x_hitbox) / range_x;
      float y_dist  = (y - desc->y_hitbox) / range_y;
      float sq_dist = x_dist * x_dist + y_dist * y_dist;
      return (sq_dist <= 1.0f);
   }
   case OVERLAY_HITBOX_RECT:
      return
           (fabs(x - desc->x_hitbox) <= range_x)
        && (fabs(y - desc->y_hitbox) <= range_y);
}
```

**IMPORTANT:** `range_x` et `range_y` sont des **rayons** (distance du centre au bord), pas des diamètres!

---

## 3. VÉRIFICATION PAR TYPE

### 3.1 RADIAL (Elliptique)

**Notre code actuel:**
```kotlin
ButtonShape.RADIAL -> {
    val dx = (touchX - buttonX) / buttonWidth
    val dy = (touchY - buttonY) / buttonHeight
    val distance = sqrt(dx * dx + dy * dy)
    distance <= 1.0f
}
```

**Où:**
- `buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier` (rayon en pixels)
- `buttonHeight = button.rangeYHitbox * viewport.height * rangeModifier` (rayon en pixels)

**✅ CORRECT:** Compatible RetroArch

---

### 3.2 RECT (Rectangulaire)

**Notre code actuel:**
```kotlin
ButtonShape.RECT -> {
    val left = buttonX - buttonWidth
    val right = buttonX + buttonWidth
    val top = buttonY - buttonHeight
    val bottom = buttonY + buttonHeight
    
    touchX >= left && touchX <= right && touchY >= top && touchY <= bottom
}
```

**Où:**
- `buttonWidth` et `buttonHeight` sont des rayons en pixels

**✅ CORRECT:** Compatible RetroArch (`fabs(x - x_hitbox) <= range_x`)

---

### 3.3 DPAD_AREA et ABXY_AREA (Zones 8-way)

**Notre code actuel:**
```kotlin
if (button.type == OverlayButtonType.DPAD_AREA || button.type == OverlayButtonType.ABXY_AREA) {
    if (isTouchInsideButton(x, y, button, effectiveRangeMod, viewport)) {
        val centerX = viewport.x + (button.x * viewport.width)
        val centerY = viewport.y + (button.y * viewport.height)
        val scaledWidth = button.modW / 2f
        val scaledHeight = button.modH / 2f
        val xDist = (x - centerX) / (scaledWidth * viewport.width)
        val yDist = (y - centerY) / (scaledHeight * viewport.height)
        val directions = get8WayDirections(xDist, yDist, sensitivity)
    }
}
```

**Vérification:**
- `isTouchInsideButton` utilise `xHitbox/yHitbox` et `rangeXHitbox/rangeYHitbox` ✅
- `centerX/centerY` utilisent `button.x/y` (position du bouton) ✅
- `scaledWidth = button.modW / 2f` où `modW = 2 * scaledWidth` ✅
- Normalisation: `xDist = (x - centerX) / (scaledWidth * viewport.width)` ✅

**⚠️ À VÉRIFIER:** Est-ce que `scaledWidth * viewport.width` est correct?

**Dans RetroArch:**
- `x_dist = (x - desc->x_shift) / desc->range_x`
- Où `x` est en coordonnées normalisées (0.0-1.0)
- Et `range_x` est aussi en coordonnées normalisées

**Notre équivalent:**
- `x` est en pixels écran
- `centerX` est en pixels écran
- `scaledWidth` est en coordonnées normalisées (0.0-1.0)
- Donc `xDist = (x - centerX) / (scaledWidth * viewport.width)` devrait être correct ✅

---

### 3.4 ANALOG_LEFT et ANALOG_RIGHT (Sticks analogiques)

**Notre code actuel:**
```kotlin
private fun calculateAnalogValues(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    viewport: OverlayViewport,
    deadzone: Float,
    layoutRangeMod: Float = 1.5f,
    ...
): Pair<Float, Float>? {
    val effectiveCenterX = centerX ?: (viewport.x + (button.x * viewport.width))
    val effectiveCenterY = centerY ?: (viewport.y + (button.y * viewport.height))
    
    val rangeX = button.width * viewport.width * layoutRangeMod
    val rangeY = button.height * viewport.height * layoutRangeMod
    
    val xDist = touchX - effectiveCenterX
    val yDist = touchY - effectiveCenterY
    
    val xVal = xDist / rangeX
    val yVal = yDist / rangeY
    ...
}
```

**Vérification:**
- `effectiveCenterX/Y` utilisent `button.x/y` (position du bouton) ✅
- `rangeX = button.width * viewport.width * layoutRangeMod` ✅
- `xVal = xDist / rangeX` où `xDist` est en pixels et `rangeX` est en pixels ✅

**⚠️ À VÉRIFIER:** Est-ce que `button.width` est le bon paramètre?

**Dans RetroArch:**
- `x_val = (x - desc->x_shift) / desc->range_x`
- Où `range_x` est la taille du stick en coordonnées normalisées

**Notre équivalent:**
- `button.width` est `range_x` en coordonnées normalisées ✅
- `rangeX = button.width * viewport.width * layoutRangeMod` est le rayon en pixels ✅

**✅ CORRECT:** Compatible RetroArch

---

## 4. PROBLÈMES IDENTIFIÉS

### 4.1 Zones 8-way (DPAD_AREA, ABXY_AREA)

**Problème potentiel:**
- `scaledWidth = button.modW / 2f` où `modW = 2 * scaledWidth`
- Donc `scaledWidth = (2 * scaledWidth) / 2 = scaledWidth` (tautologie!)

**Vérification:**
- `modW = 2f * scaledWidth` (dans `applyScaleAndOffset`)
- `scaledWidth = button.width * scale`
- Donc `modW = 2f * button.width * scale`
- Et `scaledWidth = modW / 2f = button.width * scale` ✅

**Mais:** `button.width` est en coordonnées normalisées (0.0-1.0)
- `scaledWidth` devrait être `button.width * scale` (normalisé)
- Pour normaliser `xDist`, on devrait utiliser `scaledWidth` directement, pas `scaledWidth * viewport.width`

**Correction nécessaire:**
```kotlin
val scaledWidth = button.modW / 2f  // En coordonnées normalisées
val scaledHeight = button.modH / 2f
val xDist = (x - centerX) / (scaledWidth * viewport.width)  // ✅ Correct
```

**Mais attendez:** `x` et `centerX` sont en pixels, donc `x - centerX` est en pixels.
Pour normaliser, on doit diviser par la taille en pixels: `scaledWidth * viewport.width` ✅

**✅ CORRECT:** Pas de correction nécessaire

---

## 5. DEBUG POUR TOUS LES TYPES

### 5.1 RADIAL

**Code actuel:**
```kotlin
ButtonShape.RADIAL -> {
    drawCircle(
        color = debugColor,
        radius = debugButtonWidth.coerceAtLeast(debugButtonHeight),
        center = Offset(hitboxX, hitboxY),
        alpha = 0.5f
    )
}
```

**✅ CORRECT:** `debugButtonWidth` est le rayon, utilisé directement

---

### 5.2 RECT

**Code actuel:**
```kotlin
ButtonShape.RECT -> {
    drawRect(
        color = Color.Blue,
        topLeft = Offset(hitboxX - debugButtonWidth, hitboxY - debugButtonHeight),
        size = Size(debugButtonWidth * 2, debugButtonHeight * 2),
        alpha = 0.5f
    )
}
```

**✅ CORRECT:** `debugButtonWidth` est le rayon, donc `size = rayon * 2` (diamètre)

---

### 5.3 DPAD_AREA et ABXY_AREA

**Vérification:** Les zones 8-way utilisent `isTouchInsideButton` qui dessine déjà les hitboxes ✅

---

### 5.4 ANALOG_LEFT et ANALOG_RIGHT

**Code actuel:**
```kotlin
OverlayButtonType.ANALOG_LEFT, OverlayButtonType.ANALOG_RIGHT -> Color.Green
```

**Vérification:** Les analog sticks utilisent `isTouchInsideButton` qui dessine déjà les hitboxes ✅

---

## 6. RÉSUMÉ

### ✅ CORRECT
- **RADIAL:** Détection et debug corrects
- **RECT:** Détection et debug corrects
- **ANALOG_LEFT/RIGHT:** Calcul des valeurs correct

### ⚠️ À VÉRIFIER
- **DPAD_AREA/ABXY_AREA:** Normalisation pour 8-way detection

---

**Dernière mise à jour:** 2025-01-XX

