# Audit: Problème Hitboxes Debug - Zones bleues incorrectes

**Date:** 2025-01-XX  
**Problème:** Les zones bleues (hitboxes) affichées en debug ne correspondent pas aux zones de détection réelles, et les touches sont problématiques.

---

## 1. PROBLÈME IDENTIFIÉ

### 1.1 Calcul des hitboxes dans le debug

**Code actuel (lignes 368-399):**
```kotlin
// MODE DEBUG: Afficher les hitboxes
if (showDebug) {
    val hitboxX = viewport.x + (button.xHitbox * viewport.width)
    val hitboxY = viewport.y + (button.yHitbox * viewport.height)
    
    // Utilise rangeXHitbox/rangeYHitbox avec layout.rangeModifier
    val debugButtonWidth = button.rangeXHitbox * viewport.width * scaledLayout.rangeModifier
    val debugButtonHeight = button.rangeYHitbox * viewport.height * scaledLayout.rangeModifier
}
```

### 1.2 Calcul des hitboxes dans la détection

**Code actuel (lignes 1295-1304):**
```kotlin
private fun isTouchInsideButton(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    rangeModifier: Float,  // Peut être 1.0f ou layout.rangeModifier
    viewport: OverlayViewport
): Boolean {
    val buttonX = viewport.x + (button.xHitbox * viewport.width)
    val buttonY = viewport.y + (button.yHitbox * viewport.height)
    
    val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
    val buttonHeight = button.rangeYHitbox * viewport.height * rangeModifier
}
```

### 1.3 Problème identifié

**Différence:**
- **Debug:** Utilise toujours `scaledLayout.rangeModifier`
- **Détection:** Utilise `rangeModifier` passé en paramètre (peut être 1.0f si `useRangeModForThisButton = false`)

**Conséquence:**
- Les hitboxes affichées en debug sont **toujours étendues** avec `rangeModifier`
- Les hitboxes utilisées pour la détection sont **conditionnelles** (étendues seulement si `useRangeModForThisButton = true`)

**Résultat:**
- Les zones bleues ne correspondent pas aux zones de détection réelles
- Les touches sont problématiques car les hitboxes affichées ne sont pas celles utilisées

---

## 2. SOLUTION PROPOSÉE

### 2.1 Option 1: Afficher les hitboxes réelles utilisées pour la détection

**Problème:** On ne peut pas savoir quelles hitboxes sont utilisées sans connaître l'état de `buttonTouchMasks`.

**Solution:** Afficher deux types de hitboxes:
- Hitbox normale (sans range_mod)
- Hitbox étendue (avec range_mod) si applicable

### 2.2 Option 2: Utiliser les mêmes calculs que la détection

**Solution:** Passer `buttonTouchMasks` et `currentPointerId` au rendu pour calculer les hitboxes exactement comme dans la détection.

### 2.3 Option 3: Afficher les hitboxes de base (sans range_mod)

**Solution:** Afficher seulement les hitboxes de base (sans `rangeModifier`) pour correspondre aux hitboxes utilisées au premier touch.

---

## 3. VÉRIFICATION RETROARCH

**RetroArch:**
- Les hitboxes sont calculées une fois après les transformations
- `range_x_mod` et `range_y_mod` sont stockés dans `desc`
- La détection utilise `range_x_mod` si `use_range_mod = true`, sinon `range_x_hitbox`

**Notre implémentation:**
- Les hitboxes sont calculées dynamiquement avec `rangeModifier` passé en paramètre
- Pas de stockage de `range_x_mod` et `range_y_mod` dans le bouton

**Différence:**
- RetroArch stocke les hitboxes étendues dans `desc->range_x_mod` et `desc->range_y_mod`
- Notre code calcule les hitboxes étendues à la volée avec `rangeModifier`

---

## 4. PROBLÈME FONDAMENTAL

### 4.1 Les hitboxes ne sont pas stockées correctement

**RetroArch:**
```c
// Après transformations
desc->range_x_mod = desc->range_x_hitbox * desc->range_mod;
desc->range_y_mod = desc->range_y_hitbox * desc->range_mod;

// Pour la détection
if (use_range_mod) {
    range_x = desc->range_x_mod;  // Utilise la valeur stockée
} else {
    range_x = desc->range_x_hitbox;  // Utilise la valeur de base
}
```

**Notre implémentation:**
```kotlin
// Pas de stockage de range_x_mod et range_y_mod
// Calcul à la volée avec rangeModifier passé en paramètre
val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
```

**Problème:**
- On ne stocke pas les hitboxes étendues (`range_x_mod`, `range_y_mod`)
- On les calcule à la volée, ce qui peut causer des incohérences

### 4.2 Solution: Stocker les hitboxes étendues

**Modification proposée:**
1. Ajouter `rangeXMod` et `rangeYMod` dans `OverlayButton`
2. Calculer et stocker ces valeurs après les transformations
3. Utiliser ces valeurs stockées pour la détection et le debug

---

## 5. PLAN D'ACTION

1. **Ajouter `rangeXMod` et `rangeYMod` dans `OverlayButton`**
2. **Calculer et stocker ces valeurs dans `applyScaleAndOffset()`**
3. **Modifier `isTouchInsideButton()` pour utiliser les valeurs stockées**
4. **Modifier le debug pour afficher les hitboxes stockées**

---

**Dernière mise à jour:** 2025-01-XX

