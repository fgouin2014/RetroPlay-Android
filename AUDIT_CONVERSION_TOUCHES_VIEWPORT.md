# Audit: Conversion des Touches dans le Viewport

**Date:** 2025-01-XX  
**Problème:** Les touches ne sont pas correctement converties dans le viewport, causant des problèmes de détection.

---

## 1. PROBLÈME IDENTIFIÉ

### 1.1 Coordonnées des touches

**Dans RetroArch:**
- Les touches sont converties en coordonnées normalisées (0.0-1.0) AVANT d'être passées à `input_overlay_poll()`
- Dans `input_overlay_poll()`, les coordonnées sont converties: `x = (norm_x + 0x7fff) / 0xffff` (donc [0.0-1.0])
- Puis: `x -= ol->active->mod_x; y -= ol->active->mod_y; x /= ol->active->mod_w; y /= ol->active->mod_h;`
- Donc les coordonnées sont converties dans le système de coordonnées de l'overlay (normalisé 0.0-1.0)

**Dans notre code:**
- Les touches de `pointerInteropFilter` sont en pixels écran (`event.getX()`, `event.getY()`)
- On les utilise directement dans `isTouchInsideButton()` qui projette les hitboxes dans le viewport
- **PROBLÈME:** Les touches ne sont PAS converties dans le viewport!

### 1.2 Comparaison

**Hitboxes (projetées dans viewport):**
```kotlin
val buttonX = viewport.x + (button.xHitbox * viewport.width)  // En pixels viewport
val buttonY = viewport.y + (button.yHitbox * viewport.height)
```

**Touches (actuelles):**
```kotlin
val x = event.getX(pointerIndex)  // En pixels écran!
val y = event.getY(pointerIndex)
```

**Résultat:** Les touches sont en pixels écran, mais les hitboxes sont en pixels viewport. **INCOMPATIBLE!**

---

## 2. SOLUTION

### 2.1 Conversion des touches dans le viewport

**Dans RetroArch:**
- Les touches sont converties en coordonnées normalisées (0.0-1.0) via `video_driver_translate_coord_viewport()`
- Ces coordonnées sont ensuite passées à `input_overlay_poll()`

**Notre équivalent:**
- Convertir les touches de pixels écran en pixels viewport
- Formule: `touchXInViewport = touchX - viewport.x` (si touchX est dans le viewport)
- Mais `pointerInteropFilter` fournit les coordonnées dans le système de coordonnées du composable

### 2.2 Vérification nécessaire

**Question:** `pointerInteropFilter` fournit-il les coordonnées dans le système de coordonnées du composable (pixels écran) ou dans le viewport?

**Hypothèse:** `pointerInteropFilter` fournit les coordonnées dans le système de coordonnées du composable (pixels écran), car le composable peut couvrir tout l'écran.

**Solution:** Convertir les touches de pixels écran en pixels viewport avant de les utiliser dans `isTouchInsideButton()`.

---

## 3. IMPLÉMENTATION

### 3.1 Conversion des touches

**Avant (incorrect):**
```kotlin
val x = event.getX(pointerIndex)  // Pixels écran
val y = event.getY(pointerIndex)
isTouchInsideButton(x, y, button, rangeModifier, viewport)
```

**Après (correct):**
```kotlin
val x = event.getX(pointerIndex)  // Pixels écran
val y = event.getY(pointerIndex)

// Convertir en pixels viewport (si nécessaire)
// Si le composable couvre tout l'écran, les touches sont déjà dans le bon système
// Mais si le viewport est plus petit que l'écran, il faut convertir

// Vérifier si les touches sont dans le viewport
val touchXInViewport = x - viewport.x
val touchYInViewport = y - viewport.y

isTouchInsideButton(touchXInViewport, touchYInViewport, button, rangeModifier, viewport)
```

**Mais attendez:** Si `pointerInteropFilter` est attaché au composable qui couvre tout l'écran, les touches sont en pixels écran. Mais les hitboxes sont projetées dans le viewport qui peut être plus petit que l'écran.

**Solution:** Les touches doivent être converties dans le viewport AVANT d'être comparées avec les hitboxes.

---

## 4. VÉRIFICATION RETROARCH

**RetroArch:**
```c
// video_driver_translate_coord_viewport() convertit les touches en coordonnées normalisées
// Puis input_overlay_poll() reçoit ces coordonnées normalisées
// Et les convertit dans le système de coordonnées de l'overlay

float x = (float)(norm_x + 0x7fff) / 0xffff;  // [0.0-1.0]
float y = (float)(norm_y + 0x7fff) / 0xffff;

x -= ol->active->mod_x;  // Convertir dans le système de l'overlay
y -= ol->active->mod_y;
x /= ol->active->mod_w;
y /= ol->active->mod_h;
```

**Notre équivalent:**
- Les touches sont en pixels écran
- Les hitboxes sont projetées dans le viewport
- Il faut convertir les touches dans le viewport

---

## 5. PLAN D'ACTION

1. **Vérifier le système de coordonnées de `pointerInteropFilter`**
   - Les touches sont-elles en pixels écran ou pixels viewport?
   - Le composable couvre-t-il tout l'écran ou seulement le viewport?

2. **Convertir les touches dans le viewport**
   - Si les touches sont en pixels écran, convertir en pixels viewport
   - Formule: `touchXInViewport = touchX - viewport.x`

3. **Tester avec différents viewports**
   - Viewport = écran complet (pas de conversion nécessaire)
   - Viewport < écran (conversion nécessaire)

---

**Dernière mise à jour:** 2025-01-XX

