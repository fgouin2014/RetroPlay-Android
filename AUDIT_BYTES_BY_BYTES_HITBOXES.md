# Audit "Bytes by Bytes" - Tous les Types de Zones de Détection

**Date:** 2025-01-XX  
**Méthodologie:** "Nos Rules" - Comparaison byte par byte avec RetroArch  
**Objectif:** Vérifier que TOUS les types de zones de détection sont implémentés identiquement à RetroArch

---

## TYPES DE HITBOXES DANS RETROARCH

### Source: `input_driver.c`

```c
enum overlay_hitbox
{
   OVERLAY_HITBOX_RADIAL = 0,
   OVERLAY_HITBOX_RECT,
   OVERLAY_HITBOX_NONE
};
```

**Total:** 3 types de hitboxes

---

## COMPARAISON BYTE PAR BYTE

### 1. OVERLAY_HITBOX_RADIAL (Elliptique)

#### RetroArch (input_driver.c lignes ~2500-2510):

```c
case OVERLAY_HITBOX_RADIAL:
{
   /* Ellipse. */
   float x_dist  = (x - desc->x_hitbox) / range_x;
   float y_dist  = (y - desc->y_hitbox) / range_y;
   float sq_dist = x_dist * x_dist + y_dist * y_dist;
   return (sq_dist <= 1.0f);
}
```

**Où:**
- `x`, `y` = coordonnées touch (normalisées 0.0-1.0)
- `desc->x_hitbox`, `desc->y_hitbox` = position hitbox (normalisées)
- `range_x`, `range_y` = rayon (distance centre-bord) en normalisées
- Retourne `true` si `sq_dist <= 1.0f`

#### RetroPlay (RetroArchOverlayRenderer.kt lignes 1314-1320):

```kotlin
ButtonShape.RADIAL -> {
    // Hitbox elliptique - rangeXHitbox est le rayon, utiliser directement
    val dx = (touchX - buttonX) / buttonWidth
    val dy = (touchY - buttonY) / buttonHeight
    val distance = sqrt(dx * dx + dy * dy)
    distance <= 1.0f
}
```

**Où:**
- `touchX`, `touchY` = coordonnées touch (pixels écran)
- `buttonX`, `buttonY` = position hitbox (pixels écran)
- `buttonWidth`, `buttonHeight` = rayon (pixels écran)
- Retourne `true` si `distance <= 1.0f`

**✅ CORRECT:** Mathématiquement identique (coordonnées converties en pixels)

---

### 2. OVERLAY_HITBOX_RECT (Rectangulaire)

#### RetroArch (input_driver.c lignes ~2511-2514):

```c
case OVERLAY_HITBOX_RECT:
   return
        (fabs(x - desc->x_hitbox) <= range_x)
     && (fabs(y - desc->y_hitbox) <= range_y);
```

**Où:**
- `range_x`, `range_y` = rayon (distance centre-bord) en normalisées
- Retourne `true` si `|x - x_hitbox| <= range_x` ET `|y - y_hitbox| <= range_y`

#### RetroPlay (RetroArchOverlayRenderer.kt lignes 1322-1330):

```kotlin
ButtonShape.RECT -> {
    // Hitbox rectangulaire - rangeXHitbox est le rayon, utiliser directement
    val left = buttonX - buttonWidth
    val right = buttonX + buttonWidth
    val top = buttonY - buttonHeight
    val bottom = buttonY + buttonHeight
    
    touchX >= left && touchX <= right && touchY >= top && touchY <= bottom
}
```

**Où:**
- `buttonWidth`, `buttonHeight` = rayon (pixels écran)
- `left = buttonX - buttonWidth`, `right = buttonX + buttonWidth`
- `top = buttonY - buttonHeight`, `bottom = buttonY + buttonHeight`
- Retourne `true` si touch dans le rectangle

**✅ CORRECT:** Mathématiquement identique
- RetroArch: `|x - x_hitbox| <= range_x` = `x_hitbox - range_x <= x <= x_hitbox + range_x`
- RetroPlay: `left <= touchX <= right` où `left = buttonX - buttonWidth`

---

### 3. OVERLAY_HITBOX_NONE

#### RetroArch:

```c
case OVERLAY_HITBOX_NONE:
   break;
return false;  // Après le switch
```

**Comportement:** Toujours retourne `false` (hitbox désactivée)

#### RetroPlay:

**Vérification:** Chercher si `ButtonShape.NONE` existe

---

## VÉRIFICATION DES TYPES MANQUANTS

### Types dans RetroArch:
1. ✅ `OVERLAY_HITBOX_RADIAL` → `ButtonShape.RADIAL`
2. ✅ `OVERLAY_HITBOX_RECT` → `ButtonShape.RECT`
3. ❓ `OVERLAY_HITBOX_NONE` → Existe-t-il `ButtonShape.NONE`?

### Autres zones de détection (pas des hitboxes):
- `DPAD_AREA` (zone 8-way pour D-pad)
- `ABXY_AREA` (zone 8-way pour ABXY)
- `ANALOG_LEFT` (stick analogique gauche)
- `ANALOG_RIGHT` (stick analogique droit)

**Ces zones utilisent des hitboxes RADIAL ou RECT, mais avec une logique de détection 8-way supplémentaire.**

---

## PROBLÈMES POTENTIELS IDENTIFIÉS

### 1. OVERLAY_HITBOX_NONE manquant?

**Question:** Est-ce que `ButtonShape.NONE` existe dans RetroPlay?

**Impact:** Si un bouton a `hitbox = none` dans le .cfg, il ne devrait pas être détectable.

---

### 2. Vérification des zones 8-way

**DPAD_AREA et ABXY_AREA:**
- Utilisent `isTouchInsideButton()` avec hitbox RADIAL/RECT
- Puis appliquent la détection 8-way avec `get8WayDirections()`

**À vérifier:** La détection 8-way est-elle identique à RetroArch?

---

## PLAN D'AUDIT COMPLET

1. ✅ Vérifier que tous les types de hitboxes sont implémentés
2. ⚠️ Vérifier OVERLAY_HITBOX_NONE
3. ⚠️ Vérifier la détection 8-way (DPAD_AREA, ABXY_AREA)
4. ⚠️ Vérifier les zones analog (ANALOG_LEFT, ANALOG_RIGHT)
5. ⚠️ Comparer byte par byte les calculs de détection

---

**Dernière mise à jour:** 2025-01-XX

