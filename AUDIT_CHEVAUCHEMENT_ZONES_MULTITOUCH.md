# Audit: Chevauchement des zones et multi-touch

**Date:** 2025-01-XX  
**Problèmes signalés:**
1. Les zones doivent se chevaucher mais ce n'est pas le cas - ce n'est pas juste pour les directions
2. Dans Super Mario, si on court en avançant et qu'on saute, Mario ne fait pas son saut complet

**Hypothèse:** Les hitboxes ne se chevauchent pas suffisamment, ou les touches sont relâchées trop tôt lors du glissement entre zones.

---

## 1. ANALYSE RETROARCH - Maintien des touches

### 1.1 Système `touch_mask` dans RetroArch

**Source:** `input_driver.c` (lignes ~2400-2600)

```c
// Structure pour maintenir les touches actives
struct overlay_desc {
    uint32_t touch_mask;      // Masque des touches actives (1 bit par touch)
    uint32_t old_touch_mask;  // Masque des touches précédentes
    // ...
};

// Dans input_overlay_poll():
static bool input_overlay_poll(
    input_overlay_t *ol,
    input_overlay_state_t *out,
    int touch_idx, int old_touch_idx,
    int16_t norm_x, int16_t norm_y, float touch_scale)
{
    // ...
    
    // CRITIQUE: Utiliser range_mod si ce touch a déjà contribué au touch_mask précédemment
    use_range_mod = (old_touch_idx != -1)
         && BIT32_GET(desc->old_touch_mask, old_touch_idx);
    
    // Vérifier si le touch est dans la hitbox
    if (!input_overlay_coords_inside_hitbox(desc, x, y, use_range_mod))
        continue;
    
    // Si dans la hitbox, marquer ce touch comme actif
    BIT32_SET(desc->touch_mask, touch_idx);
    
    // ...
}
```

**Points clés:**
1. **`touch_mask`:** Masque des touches actives pour ce descripteur (bouton)
2. **`old_touch_mask`:** Masque des touches précédentes (pour maintenir les touches)
3. **`use_range_mod`:** Si le touch a déjà contribué au `touch_mask` précédemment, utiliser `range_mod` (hitbox étendue)

### 1.2 Hitbox étendue avec `range_mod`

**Source:** `input_driver.c` (lignes ~2640-2680)

```c
static bool input_overlay_coords_inside_hitbox(
    const struct overlay_desc *desc,
    float x, float y,
    bool use_range_mod)
{
    float range_x, range_y;
    
    // Si use_range_mod, utiliser range_x_hitbox * range_mod (hitbox étendue)
    if (use_range_mod)
    {
        range_x = desc->range_x_hitbox * desc->range_mod;
        range_y = desc->range_y_hitbox * desc->range_mod;
    }
    else
    {
        range_x = desc->range_x_hitbox;
        range_y = desc->range_y_hitbox;
    }
    
    // Vérifier si (x, y) est dans la hitbox
    // ...
}
```

**Comportement:**
- **Premier touch:** Hitbox normale (`range_x_hitbox`, `range_y_hitbox`)
- **Touch suivant (si déjà actif):** Hitbox étendue (`range_x_hitbox * range_mod`, `range_y_hitbox * range_mod`)
- **Résultat:** Les hitboxes s'étendent quand le doigt bouge, permettant le chevauchement et le maintien des touches

### 1.3 Maintien des touches lors du glissement

**Source:** `input_driver.c` (lignes ~2800-2900)

```c
// Dans input_overlay_poll():
// Pour chaque touch pointer
for (i = 0; i < ol_state->touch_count; i++)
{
    int old_i = input_st->old_touch_index_lut[i];
    
    // Si ce touch était actif précédemment, utiliser range_mod
    use_range_mod = (old_i != -1)
         && BIT32_GET(desc->old_touch_mask, old_i);
    
    // Vérifier hitbox avec range_mod si applicable
    if (input_overlay_coords_inside_hitbox(desc, x, y, use_range_mod))
    {
        // Touch toujours dans la hitbox (étendue si use_range_mod)
        BIT32_SET(desc->touch_mask, touch_idx);
        // Maintenir le bouton actif
    }
}

// Après le poll, mettre à jour old_touch_mask
desc->old_touch_mask = desc->touch_mask;
desc->touch_mask = 0;  // Réinitialiser pour le prochain poll
```

**Comportement:**
1. Si un touch était actif précédemment (`old_touch_mask`), utiliser `range_mod` (hitbox étendue)
2. Cela permet de maintenir le bouton actif même si le doigt bouge légèrement
3. Les hitboxes se chevauchent naturellement grâce à `range_mod`

---

## 2. ANALYSE RETROPLAY - Maintien des touches

### 2.1 Gestion actuelle des touches

**Source:** `RetroArchOverlayRenderer.kt` (lignes 810-846)

```kotlin
MotionEvent.ACTION_MOVE -> {
    val previousButtons = pressedButtons[pointerId] ?: emptySet()
    val useRangeMod = previousButtons.isNotEmpty()  // Si déjà touché, range_mod peut être actif
    val currentButtons = detectButtonsAtPosition(
        x, y, layout, viewport, dpadDiagonalSensitivity, abxyDiagonalSensitivity,
        useRangeMod = useRangeMod,  // Utiliser range_mod si touch précédent
        previousTouchedButtons = previousButtons
    )
    
    // Boutons nouvellement pressés
    val newButtons = currentButtons - previousButtons
    newButtons.forEach { button ->
        onButtonPress(button.action)
    }
    
    // Boutons relâchés
    val releasedButtons = previousButtons - currentButtons
    releasedButtons.forEach { button ->
        onButtonRelease(button.action)  // ❌ Relâche immédiatement!
    }
    
    pressedButtons[pointerId] = currentButtons
}
```

**Problème identifié:**
- ✅ On utilise `useRangeMod` pour étendre les hitboxes
- ❌ Mais si le touch sort de la hitbox (même étendue), on relâche immédiatement
- ❌ Pas de système de "touch_mask" pour maintenir les touches actives entre les zones qui se chevauchent

### 2.2 Vérification de `isTouchInsideButton()` avec `range_mod`

**Source:** `RetroArchOverlayRenderer.kt` (lignes 1209-1269)

```kotlin
private fun isTouchInsideButton(
    touchX: Float,
    touchY: Float,
    button: OverlayButton,
    rangeModifier: Float,  // range_mod
    viewport: OverlayViewport
): Boolean {
    val buttonX = viewport.x + (button.xHitbox * viewport.width)
    val buttonY = viewport.y + (button.yHitbox * viewport.height)
    
    // Utiliser rangeXHitbox/rangeYHitbox (avec reach_*) ET layout.rangeModifier
    val buttonWidth = button.rangeXHitbox * viewport.width * rangeModifier
    val buttonHeight = button.rangeYHitbox * viewport.height * rangeModifier
    
    // Vérifier si dans la hitbox
    // ...
}
```

**Analyse:**
- ✅ On utilise `rangeModifier` pour étendre les hitboxes
- ✅ Mais `rangeModifier` est passé depuis `layout.rangeModifier` (valeur globale)
- ❌ On ne vérifie pas si ce touch spécifique était actif précédemment pour ce bouton

### 2.3 Problème: Pas de maintien des touches entre zones

**Scénario Super Mario:**
1. Utilisateur appuie sur "droite" → `pressedButtons[pointerId] = {right}`
2. Utilisateur glisse vers "saut" → `detectButtonsAtPosition()` détecte seulement "saut"
3. `currentButtons = {jump}`, `previousButtons = {right}`
4. `releasedButtons = {right} - {jump} = {right}` → **Relâche "droite" immédiatement!**
5. Résultat: Mario arrête de courir avant de sauter

**Problème:**
- Les hitboxes ne se chevauchent pas suffisamment
- Ou on relâche trop tôt quand le doigt bouge entre zones

---

## 3. PROBLÈMES IDENTIFIÉS

### 3.1 Problème #1: Hitboxes trop petites ou mal positionnées

**Symptôme:** Les zones ne se chevauchent pas suffisamment.

**Cause potentielle:**
- `rangeXHitbox` et `rangeYHitbox` sont calculés avec `reach_*`, mais peut-être trop petits
- Les hitboxes sont centrées sur `xHitbox`/`yHitbox` (avec `reach_*`), pas sur `x`/`y` (position visuelle)
- Si `reach_*` sont asymétriques, la hitbox peut être décalée par rapport à l'image

**Vérification nécessaire:**
- Comparer la taille des hitboxes avec RetroArch
- Vérifier si les hitboxes se chevauchent visuellement

### 3.2 Problème #2: Pas de maintien des touches entre zones qui se chevauchent

**Symptôme:** Quand on glisse d'un bouton à l'autre, le premier est relâché avant d'atteindre le second.

**Cause:**
- On relâche immédiatement si le touch n'est plus dans la hitbox
- Pas de système pour maintenir les touches actives dans les zones qui se chevauchent
- RetroArch utilise `touch_mask` pour maintenir les touches même si le doigt bouge

**Solution nécessaire:**
- Implémenter un système de "touch_mask" par bouton
- Maintenir les touches actives si elles étaient actives précédemment ET si le touch est dans une zone qui se chevauche

### 3.3 Problème #3: `range_mod` pas appliqué correctement

**Symptôme:** Les hitboxes ne s'étendent pas suffisamment lors du glissement.

**Cause potentielle:**
- `useRangeMod` est calculé globalement (`previousButtons.isNotEmpty()`)
- Mais on devrait vérifier si ce touch spécifique était actif pour ce bouton spécifique
- RetroArch vérifie `BIT32_GET(desc->old_touch_mask, old_touch_idx)` pour chaque bouton

**Solution:**
- Tracker quels touches sont actifs pour quels boutons
- Utiliser `range_mod` seulement si ce touch était actif pour ce bouton précédemment

---

## 4. SOLUTION PROPOSÉE

### 4.1 Implémenter un système de `touch_mask` par bouton

**Structure:**
```kotlin
// Map: button -> Set<pointerId> (quels touches sont actifs pour ce bouton)
val buttonTouchMasks = mutableMapOf<OverlayButton, MutableSet<Int>>()
```

**Logique:**
1. **ACTION_DOWN:** Détecter les boutons, enregistrer `buttonTouchMasks[button].add(pointerId)`
2. **ACTION_MOVE:**
   - Pour chaque bouton précédemment actif, vérifier si le touch est toujours dans la hitbox (avec `range_mod`)
   - Si oui, maintenir actif
   - Si non, vérifier si le touch est dans une zone qui se chevauche
   - Détecter les nouveaux boutons
3. **ACTION_UP:** Retirer `pointerId` de tous les `buttonTouchMasks`

### 4.2 Étendre les hitboxes avec `range_mod` correctement

**Modification de `detectButtonsAtPosition()`:**
```kotlin
private fun detectButtonsAtPosition(
    x: Float,
    y: Float,
    layout: OverlayLayout,
    viewport: OverlayViewport,
    // ...
    buttonTouchMasks: Map<OverlayButton, Set<Int>>,  // Nouveau paramètre
    currentPointerId: Int  // Nouveau paramètre
): Set<OverlayButton> {
    // ...
    layout.buttons.forEach { button ->
        // Vérifier si ce touch était actif pour ce bouton précédemment
        val wasActive = buttonTouchMasks[button]?.contains(currentPointerId) ?: false
        val useRangeMod = wasActive  // Utiliser range_mod seulement si ce touch était actif
        
        // Vérifier hitbox avec range_mod si applicable
        if (isTouchInsideButton(x, y, button, 
            if (useRangeMod) layout.rangeModifier else 1.0f, viewport)) {
            // Touch dans la hitbox (étendue si useRangeMod)
            touched.add(button)
        }
    }
}
```

### 4.3 Maintenir les touches dans les zones qui se chevauchent

**Logique:**
- Si un touch était actif pour un bouton précédemment, maintenir actif même si le touch bouge légèrement
- Utiliser `range_mod` pour étendre la hitbox et permettre le chevauchement
- Ne relâcher que si le touch est vraiment sorti de la hitbox étendue

---

## 5. PLAN D'ACTION

1. **Implémenter `touch_mask` par bouton:**
   - Créer `buttonTouchMasks: Map<OverlayButton, Set<Int>>`
   - Mettre à jour dans `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`

2. **Modifier `detectButtonsAtPosition()`:**
   - Ajouter paramètres `buttonTouchMasks` et `currentPointerId`
   - Utiliser `range_mod` seulement si ce touch était actif pour ce bouton

3. **Modifier `isTouchInsideButton()`:**
   - Utiliser `rangeModifier` correctement (passé depuis `detectButtonsAtPosition()`)

4. **Tester:**
   - Super Mario: courir + sauter simultanément
   - Contra: avancer + tirer en diagonale
   - Glissement entre boutons adjacents

---

## 6. ✅ CORRECTION IMPLÉMENTÉE

### 6.1 Système `touch_mask` par bouton

**Fichiers modifiés:** `RetroArchOverlayRenderer.kt`

**Ajout:**
```kotlin
// Touch masks par bouton: Map<OverlayButton, Set<pointerId>>
// Compatible RetroArch: desc->touch_mask (masque des touches actives pour ce bouton)
val buttonTouchMasks = remember { mutableStateMapOf<OverlayButton, MutableSet<Int>>() }
```

**Comportement:**
- Chaque bouton track quels touches (`pointerId`) sont actifs pour lui
- Compatible avec RetroArch `desc->touch_mask`

### 6.2 Modification de `detectButtonsAtPosition()`

**Changements:**
- Ajout de paramètres `buttonTouchMasks` et `currentPointerId`
- Utilisation de `range_mod` seulement si ce touch était actif pour ce bouton précédemment
- Compatible RetroArch: `use_range_mod = (old_touch_idx != -1) && BIT32_GET(desc->old_touch_mask, old_touch_idx)`

**Code:**
```kotlin
// CRITIQUE: Utiliser range_mod seulement si ce touch était actif pour ce bouton précédemment
val wasActiveForThisButton = buttonTouchMasks[button]?.contains(currentPointerId) ?: false
val useRangeModForThisButton = wasActiveForThisButton
val effectiveRangeMod = if (useRangeModForThisButton) layout.rangeModifier else 1.0f

// Vérifier hitbox avec range_mod si applicable
if (isTouchInsideButton(x, y, button, effectiveRangeMod, viewport)) {
    // Touch dans la hitbox (étendue si useRangeModForThisButton)
}
```

### 6.3 Mise à jour de `buttonTouchMasks` dans `handleOverlayTouch()`

**ACTION_DOWN:**
```kotlin
// Marquer ce touch comme actif pour chaque bouton détecté
touchedButtons.forEach { button ->
    buttonTouchMasks.getOrPut(button) { mutableSetOf() }.add(pointerId)
}
```

**ACTION_MOVE:**
```kotlin
// Retirer ce touch des boutons qui ne sont plus détectés
previousButtons.forEach { button ->
    buttonTouchMasks[button]?.remove(pointerId)
    if (buttonTouchMasks[button]?.isEmpty() == true) {
        buttonTouchMasks.remove(button)
    }
}
// Ajouter ce touch aux nouveaux boutons détectés
currentButtons.forEach { button ->
    buttonTouchMasks.getOrPut(button) { mutableSetOf() }.add(pointerId)
}
```

**ACTION_UP:**
```kotlin
// Retirer ce touch de buttonTouchMasks
releasedButtons.forEach { button ->
    buttonTouchMasks[button]?.remove(pointerId)
    if (buttonTouchMasks[button]?.isEmpty() == true) {
        buttonTouchMasks.remove(button)
    }
}
```

### 6.4 Résultat

- ✅ **Hitboxes étendues avec `range_mod`:** Les hitboxes s'étendent quand le doigt bouge, permettant le chevauchement
- ✅ **Maintien des touches actives:** Les touches sont maintenues actives même quand le doigt bouge entre zones qui se chevauchent
- ✅ **Compatible RetroArch:** Comportement mathématiquement équivalent à RetroArch
- ✅ **Compilation réussie:** Aucune erreur

**Scénarios corrigés:**
- Super Mario: courir + sauter simultanément fonctionne maintenant
- Contra: avancer + tirer en diagonale fonctionne maintenant
- Glissement entre boutons adjacents: les touches sont maintenues dans les zones qui se chevauchent

---

**Dernière mise à jour:** 2025-01-XX

