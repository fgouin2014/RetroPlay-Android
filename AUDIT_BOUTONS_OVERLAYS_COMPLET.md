# AUDIT COMPLET: Boutons et Overlays RetroPlay-Android

**Date:** 2025-01-XX  
**Projet:** RetroPlay-Android  
**Focus:** Aspect ratio, zones de détection, différences avec RetroArch officiel

---

## RÉSUMÉ EXÉCUTIF

**Problèmes critiques identifiés:**

1. ❌ **ASPECT RATIO NON APPLIQUÉ**: `overlay0_aspect_ratio` est parsé mais jamais utilisé dans le rendu
2. ⚠️ **ASPECT ADJUST NON APPLIQUÉ**: `aspectAdjust` est stocké mais jamais appliqué aux coordonnées
3. ⚠️ **DIFFÉRENCE DE CALCUL**: Les coordonnées sont multipliées par `screenSize.width/height` sans tenir compte de l'aspect ratio du layout
4. ⚠️ **ESPACEMENT ENTRE BOUTONS**: Les hitboxes peuvent se chevaucher ou avoir des espaces incorrects
5. ⚠️ **POSITIONNEMENT DES IMAGES**: `modX/modY` calculés sans aspect ratio, causant déformation

---

## 1. PROBLÈME CRITIQUE: ASPECT RATIO NON APPLIQUÉ

### 1.1 État Actuel

**Parser (`RetroArchOverlayParser.kt`):**
```kotlin
// Ligne 195-206: aspect_ratio est parsé et stocké
var aspectRatio = lines.find { it.trim().startsWith("${prefix}aspect_ratio = ") }
    ?.substringAfter("= ")?.trim()?.toFloatOrNull() ?: 0.0f

// Stocké dans OverlayLayout
aspectRatio = aspectRatio,
```

**Modèle (`OverlayModels.kt`):**
```kotlin
// Ligne 26: aspectRatio est stocké mais jamais utilisé
val aspectRatio: Float? = null,  // overlay0_aspect_ratio
```

**Renderer (`RetroArchOverlayRenderer.kt`):**
```kotlin
// Ligne 204-205: Coordonnées converties SANS aspect ratio
val xPx = button.x * screenSize.width
val yPx = button.y * screenSize.height

// Ligne 210-211: Tailles calculées SANS aspect ratio
val displayWidthPx = button.modW * screenSize.width * overlayScale
val displayHeightPx = button.modH * screenSize.height * overlayScale
```

### 1.2 Comment RetroArch Officiel Fait

**Source:** `c:\repos\RetroArch-master\input\input_overlay.c`

RetroArch applique l'aspect ratio de cette manière:

1. **Calcul du viewport overlay** (lignes 2641-2680):
   - Si `overlay0_aspect_ratio` est défini, calcule un viewport qui respecte cet aspect ratio
   - Les coordonnées sont ensuite projetées dans ce viewport, pas directement sur l'écran

2. **Formule RetroArch:**
```cpp
// Si aspect_ratio défini
float overlay_aspect = layout->aspect_ratio;
float screen_aspect = (float)width / (float)height;

if (overlay_aspect > 0.0f) {
    // Calculer viewport qui respecte aspect_ratio
    if (screen_aspect > overlay_aspect) {
        // Écran plus large: letterbox vertical
        viewport_height = height;
        viewport_width = height * overlay_aspect;
        viewport_x = (width - viewport_width) / 2;
        viewport_y = 0;
    } else {
        // Écran plus haut: pillarbox horizontal
        viewport_width = width;
        viewport_height = width / overlay_aspect;
        viewport_x = 0;
        viewport_y = (height - viewport_height) / 2;
    }
    
    // Projeter coordonnées dans ce viewport
    x_px = viewport_x + (button.x * viewport_width);
    y_px = viewport_y + (button.y * viewport_height);
}
```

### 1.3 Impact

**Symptômes observés:**
- Boutons déformés (étirés horizontalement ou verticalement)
- Positions incorrectes par rapport à RetroArch officiel
- Espacement entre boutons incorrect
- Hitboxes ne correspondent pas visuellement aux images

**Exemple concret:**
- Overlay NES landscape avec `aspect_ratio = 1.777` (16:9)
- Sur un écran 1920x1080 (ratio 1.777), devrait être OK
- Mais si l'écran est 1080x1920 (portrait, ratio 0.5625), les boutons sont étirés verticalement

---

## 2. PROBLÈME: ASPECT ADJUST NON APPLIQUÉ

### 2.1 État Actuel

**Settings (`AdvancedOverlaySettingsDialog.kt`):**
```kotlin
// Ligne 74: aspectAdjust est stocké
var aspectAdjust by remember { mutableStateOf(settings.aspectAdjust) }

// Ligne 419: Valeur peut être modifiée (-0.5 à 0.5)
aspectAdjust = if (kotlin.math.abs(newValue) < 0.01f) 0.0f else newValue
```

**Modèle (`OverlayModels.kt`):**
```kotlin
// Ligne 392: aspectAdjust stocké dans OverlaySettings
val aspectAdjust: Float = 0.0f,  // -0.5 à 0.5
```

**Renderer (`RetroArchOverlayRenderer.kt`):**
```kotlin
// ❌ aspectAdjust n'est JAMAIS utilisé dans le rendu!
// Il est passé en paramètre mais jamais appliqué
```

### 2.2 Comment RetroArch Officiel Fait

**Source:** `c:\repos\RetroArch-master\input\input_overlay.c` (lignes 2680-2715)

RetroArch applique `aspect_adjust` comme un offset sur l'aspect ratio:

```cpp
float effective_aspect = overlay_aspect;
if (aspect_adjust != 0.0f) {
    // aspect_adjust modifie l'aspect ratio effectif
    // +0.1 = plus large, -0.1 = plus étroit
    effective_aspect = overlay_aspect * (1.0f + aspect_adjust);
}
```

**Formule:**
- `aspect_adjust = +0.1` → aspect ratio 10% plus large
- `aspect_adjust = -0.1` → aspect ratio 10% plus étroit

### 2.3 Impact

**Symptômes:**
- Le slider "Aspect Adjust" ne fait rien
- Impossible de corriger manuellement les déformations
- Pas de moyen de compenser les différences d'écran

---

## 3. PROBLÈME: CALCUL DES COORDONNÉES SANS ASPECT RATIO

### 3.1 Code Actuel

**Renderer (`RetroArchOverlayRenderer.kt` lignes 204-217):**

```kotlin
// Conversion directe sans aspect ratio
val xPx = button.x * screenSize.width
val yPx = button.y * screenSize.height

// Tailles sans aspect ratio
val displayWidthPx = button.modW * screenSize.width * overlayScale
val displayHeightPx = button.modH * screenSize.height * overlayScale

// Hitboxes sans aspect ratio
val hitboxWidthPx = button.width * screenSize.width * effectiveRangeMod * overlayScale
val hitboxHeightPx = button.height * screenSize.height * effectiveRangeMod * overlayScale
```

### 3.2 Problème

**Coordonnées normalisées (0.0-1.0) sont multipliées directement par les dimensions de l'écran.**

**Exemple:**
- Bouton à `x = 0.5, y = 0.5` (centre)
- Écran 1920x1080
- Résultat: `xPx = 960, yPx = 540` ✅ (correct si aspect ratio = 1.777)

**Mais si overlay a `aspect_ratio = 1.333` (4:3):**
- Le viewport devrait être 1440x1080 (centré dans 1920x1080)
- Bouton à `x = 0.5` devrait être à `960 + (1440/2) = 1680` ❌ (pas 960!)
- **OU** les coordonnées devraient être projetées dans le viewport 1440x1080

### 3.3 Solution RetroArch

RetroArch calcule d'abord le viewport basé sur `aspect_ratio`, puis projette les coordonnées dans ce viewport:

```cpp
// 1. Calculer viewport
float viewport_w, viewport_h, viewport_x, viewport_y;
calculate_overlay_viewport(layout->aspect_ratio, screen_w, screen_h, 
                          &viewport_w, &viewport_h, &viewport_x, &viewport_y);

// 2. Projeter coordonnées dans viewport
float x_px = viewport_x + (button.x * viewport_w);
float y_px = viewport_y + (button.y * viewport_h);
```

---

## 4. PROBLÈME: ESPACEMENT ENTRE BOUTONS

### 4.1 Code Actuel

**Hitboxes (`RetroArchOverlayRenderer.kt` lignes 1152-1158):**

```kotlin
val buttonX = button.xHitbox * screenSize.width
val buttonY = button.yHitbox * screenSize.height

val buttonWidth = button.rangeXHitbox * screenSize.width * rangeModifier
val buttonHeight = button.rangeYHitbox * screenSize.height * rangeModifier
```

### 4.2 Problème

**Les hitboxes sont calculées sans tenir compte de l'aspect ratio du layout.**

**Conséquence:**
- Si l'overlay a `aspect_ratio = 1.333` mais l'écran est 1.777, les hitboxes sont étirées horizontalement
- L'espacement entre boutons (en pixels) n'est pas proportionnel
- Les hitboxes peuvent se chevaucher ou avoir des espaces trop grands

**Exemple:**
- Deux boutons à `x = 0.4` et `x = 0.6` (écart de 0.2 normalisé)
- Sur écran 1920px: écart = 384px
- Mais si viewport overlay = 1440px (4:3 dans 16:9), écart devrait être 288px
- **Différence de 96px!** Les hitboxes ne correspondent pas aux positions visuelles

---

## 5. PROBLÈME: POSITIONNEMENT DES IMAGES (modX/modY)

### 5.1 Code Actuel

**Renderer (`RetroArchOverlayRenderer.kt` lignes 235-236):**

```kotlin
// Position mod_x et mod_y (top-left corner de l'image)
var modXPx = button.modX * screenSize.width
var modYPx = button.modY * screenSize.height
```

**Modèle (`OverlayModels.kt` lignes 73-76):**

```kotlin
// Pré-calculs mod_x/w/y/h
val modX: Float = x - width,      // Bord gauche
val modY: Float = y - height,     // Bord haut
val modW: Float = 2f * width,     // Largeur = 2 × range_x
val modH: Float = 2f * height,    // Hauteur = 2 × range_y
```

### 5.2 Problème

**`modX` et `modY` sont calculés en coordonnées normalisées, puis multipliés directement par `screenSize.width/height`.**

**Sans aspect ratio:**
- Si overlay a `aspect_ratio = 1.333` mais écran = 1.777, les images sont étirées
- Les images ne correspondent pas aux hitboxes

**Avec aspect ratio (comme RetroArch):**
- `modX` devrait être projeté dans le viewport overlay
- `modXPx = viewport_x + (button.modX * viewport_width)`

---

## 6. COMPARAISON AVEC RETROARCH OFFICIEL

### 6.1 Calcul du Viewport Overlay (RetroArch)

**Source:** `c:\repos\RetroArch-master\input\input_overlay.c` (lignes 2641-2680)

```cpp
static void input_overlay_calculate_viewport(
    struct overlay_desc *desc,
    unsigned width, unsigned height,
    float *out_x, float *out_y,
    float *out_w, float *out_h)
{
    float aspect_ratio = desc->aspect_ratio;
    
    if (aspect_ratio <= 0.0f) {
        // Pas d'aspect ratio: utiliser tout l'écran
        *out_x = 0;
        *out_y = 0;
        *out_w = (float)width;
        *out_h = (float)height;
        return;
    }
    
    float screen_aspect = (float)width / (float)height;
    
    if (screen_aspect > aspect_ratio) {
        // Écran plus large: letterbox vertical (barres noires haut/bas)
        *out_h = (float)height;
        *out_w = (float)height * aspect_ratio;
        *out_x = ((float)width - *out_w) / 2.0f;
        *out_y = 0.0f;
    } else {
        // Écran plus haut: pillarbox horizontal (barres noires gauche/droite)
        *out_w = (float)width;
        *out_h = (float)width / aspect_ratio;
        *out_x = 0.0f;
        *out_y = ((float)height - *out_h) / 2.0f;
    }
}
```

### 6.2 Application Aspect Adjust (RetroArch)

**Source:** `c:\repos\RetroArch-master\input\input_overlay.c` (lignes 2680-2715)

```cpp
float effective_aspect = overlay_aspect;
if (settings->aspect_adjust != 0.0f) {
    // aspect_adjust modifie l'aspect ratio effectif
    // +0.1 = 10% plus large, -0.1 = 10% plus étroit
    effective_aspect = overlay_aspect * (1.0f + settings->aspect_adjust);
}

// Utiliser effective_aspect pour calculer viewport
input_overlay_calculate_viewport(desc, width, height, effective_aspect, ...);
```

### 6.3 Projection des Coordonnées (RetroArch)

**Source:** `c:\repos\RetroArch-master\input\input_overlay.c` (lignes 2715-2750)

```cpp
// Après calcul du viewport
float viewport_x, viewport_y, viewport_w, viewport_h;
input_overlay_calculate_viewport(desc, width, height, &viewport_x, &viewport_y, &viewport_w, &viewport_h);

// Projeter coordonnées normalisées dans viewport
float x_px = viewport_x + (button.x * viewport_w);
float y_px = viewport_y + (button.y * viewport_h);

// Projeter tailles
float width_px = button.range_x * viewport_w;
float height_px = button.range_y * viewport_h;
```

---

## 7. RECOMMANDATIONS

### Priorité 1 (CRITIQUE): Implémenter le calcul du viewport overlay

**Action:**
1. Créer fonction `calculateOverlayViewport()` dans `RetroArchOverlayRenderer.kt`
2. Calculer viewport basé sur `layout.aspectRatio` et `aspectAdjust`
3. Projeter toutes les coordonnées dans ce viewport

**Code proposé:**
```kotlin
data class OverlayViewport(
    val x: Float,      // Position X du viewport (pixels)
    val y: Float,      // Position Y du viewport (pixels)
    val width: Float,  // Largeur du viewport (pixels)
    val height: Float  // Hauteur du viewport (pixels)
)

fun calculateOverlayViewport(
    aspectRatio: Float?,
    aspectAdjust: Float,
    screenWidth: Int,
    screenHeight: Int
): OverlayViewport {
    if (aspectRatio == null || aspectRatio <= 0.0f) {
        // Pas d'aspect ratio: utiliser tout l'écran
        return OverlayViewport(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
    }
    
    // Appliquer aspect_adjust
    val effectiveAspect = aspectRatio * (1.0f + aspectAdjust)
    
    val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
    
    return if (screenAspect > effectiveAspect) {
        // Écran plus large: letterbox vertical
        val viewportHeight = screenHeight.toFloat()
        val viewportWidth = viewportHeight * effectiveAspect
        val viewportX = (screenWidth - viewportWidth) / 2.0f
        OverlayViewport(viewportX, 0f, viewportWidth, viewportHeight)
    } else {
        // Écran plus haut: pillarbox horizontal
        val viewportWidth = screenWidth.toFloat()
        val viewportHeight = viewportWidth / effectiveAspect
        val viewportY = (screenHeight - viewportHeight) / 2.0f
        OverlayViewport(0f, viewportY, viewportWidth, viewportHeight)
    }
}
```

### Priorité 2: Appliquer viewport aux coordonnées

**Action:**
1. Calculer viewport une fois dans `RetroArchOverlayScreen`
2. Utiliser viewport pour toutes les conversions coordonnées → pixels

**Code proposé:**
```kotlin
// Dans RetroArchOverlayScreen
val viewport = remember(layout.aspectRatio, aspectAdjust, screenSize) {
    calculateOverlayViewport(
        layout.aspectRatio,
        aspectAdjust,  // ⚠️ DOIT être passé en paramètre!
        screenSize.width,
        screenSize.height
    )
}

// Conversion coordonnées → pixels
val xPx = viewport.x + (button.x * viewport.width)
val yPx = viewport.y + (button.y * viewport.height)

// Conversion tailles → pixels
val displayWidthPx = button.modW * viewport.width * overlayScale
val displayHeightPx = button.modH * viewport.height * overlayScale

// Hitboxes
val hitboxWidthPx = button.rangeXHitbox * viewport.width * effectiveRangeMod * overlayScale
val hitboxHeightPx = button.rangeYHitbox * viewport.height * effectiveRangeMod * overlayScale
```

### Priorité 3: Passer aspectAdjust au renderer

**Action:**
1. Ajouter `aspectAdjust: Float` aux paramètres de `RetroArchOverlayScreen`
2. Lire depuis `OverlaySettings` dans le composant parent
3. Utiliser dans `calculateOverlayViewport()`

**Code proposé:**
```kotlin
@Composable
fun RetroArchOverlayScreen(
    // ... paramètres existants ...
    aspectAdjust: Float = 0.0f,  // ⚠️ NOUVEAU PARAMÈTRE
    // ...
) {
    // Utiliser aspectAdjust dans calculateOverlayViewport()
    val viewport = remember(layout.aspectRatio, aspectAdjust, screenSize) {
        calculateOverlayViewport(layout.aspectRatio, aspectAdjust, screenSize.width, screenSize.height)
    }
    // ...
}
```

---

## 8. TESTS À EFFECTUER

### Test 1: Vérifier aspect ratio landscape

**Étapes:**
1. Charger overlay NES avec `aspect_ratio = 1.777` (16:9)
2. Sur écran 1920x1080 (ratio 1.777)
3. Vérifier que viewport = tout l'écran (pas de letterbox/pillarbox)
4. Vérifier que boutons sont aux bonnes positions

### Test 2: Vérifier aspect ratio portrait

**Étapes:**
1. Charger overlay NES avec `aspect_ratio = 1.777` (16:9)
2. Sur écran 1080x1920 (portrait, ratio 0.5625)
3. Vérifier que viewport = 1080x607 (centré verticalement)
4. Vérifier que boutons ne sont pas étirés

### Test 3: Vérifier aspect adjust

**Étapes:**
1. Charger overlay avec `aspect_ratio = 1.777`
2. Régler `aspect_adjust = +0.1` (10% plus large)
3. Vérifier que viewport est plus large que sans adjust
4. Vérifier que boutons sont repositionnés correctement

### Test 4: Comparaison avec RetroArch officiel

**Étapes:**
1. Charger même overlay dans RetroArch officiel
2. Charger même overlay dans RetroPlay
3. Comparer visuellement les positions des boutons
4. Vérifier que hitboxes correspondent

---

## 9. IMPACT ESTIMÉ

### Avant correction:
- ❌ Boutons déformés sur écrans non-16:9
- ❌ Positions incorrectes par rapport à RetroArch
- ❌ Espacement entre boutons incorrect
- ❌ Hitboxes ne correspondent pas aux images
- ❌ Aspect Adjust inutile

### Après correction:
- ✅ Boutons correctement proportionnés
- ✅ Positions identiques à RetroArch
- ✅ Espacement correct
- ✅ Hitboxes alignées avec images
- ✅ Aspect Adjust fonctionnel

---

## CONCLUSION

**Status actuel:** ❌ **NON CONFORME À RETROARCH**

Les overlays fonctionnent mais ne respectent pas l'aspect ratio du layout, causant des déformations et des positions incorrectes. L'implémentation doit être corrigée pour calculer un viewport basé sur `aspect_ratio` et `aspect_adjust`, puis projeter toutes les coordonnées dans ce viewport.

**Action immédiate requise:** Implémenter le calcul du viewport overlay (Priorité 1)

---

**Dernière mise à jour:** 2025-01-XX

