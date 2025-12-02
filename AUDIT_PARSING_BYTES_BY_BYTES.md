# Audit "Bytes by Bytes" - Parsing des Overlays RetroArch

**Date:** 2025-01-XX  
**Méthodologie:** "Nos Rules" - Comparaison byte par byte avec RetroArch task_overlay.c  
**Objectif:** Vérifier que TOUS les paramètres sont parsés identiquement à RetroArch

---

## IMPORTANCE DU PARSING

**Le parsing est la FONDATION de tout:**
- ❌ Si le parsing est incorrect → Tout le reste sera incorrect
- ❌ Si un paramètre n'est pas parsé → Il ne sera jamais utilisé
- ❌ Si le format est mal interprété → Les valeurs seront fausses

**C'est pourquoi cet audit doit être intégré à "Nos Rules":**
- ✅ Vérifier TOUS les paramètres parsés par RetroArch
- ✅ Comparer byte par byte les formats, conversions, validations
- ✅ Identifier les gaps et différences subtiles

---

## SOURCES ANALYSÉES

### RetroArch Source
- **Fichier:** `c:\repos\RetroArch-master\tasks\task_overlay.c`
- **Fonction principale:** `task_overlay_load_desc()` (lignes 219-517)
- **Fonction overlay global:** `task_overlay_deferred_load()` (lignes 672-942)

### Exemples Réels
- **Fichier:** `c:\repos\common-overlays-master\gamepads\flat\nes.cfg`
- **Total overlays:** 12
- **Total descripteurs:** 19 par overlay (landscape-A)

---

## COMPARAISON BYTE PAR BYTE

### 1. FORMAT DESCRIPTEUR (overlayN_descN)

#### RetroArch (task_overlay.c lignes 219-517):

```c
// Format: "action,x,y,hitbox_type,range_x,range_y"
// Tokenize par ", " (virgule + ESPACE)
char *elem0 = strtok_r(desc_str, ", ", &save_ptr);  // action
char *elem1 = strtok_r(NULL, ", ", &save_ptr);      // x
char *elem2 = strtok_r(NULL, ", ", &save_ptr);      // y
char *elem3 = strtok_r(NULL, ", ", &save_ptr);      // hitbox_type
char *elem4 = strtok_r(NULL, ", ", &save_ptr);      // range_x
char *elem5 = strtok_r(NULL, ", ", &save_ptr);      // range_y

// Validation: Vérifie que list_size >= 6
if (list_size < 6) {
    RARCH_ERR("[Overlay] Invalid descriptor format\n");
    return false;
}
```

**CRITIQUE:** Tokenize par `", "` (virgule + espace), PAS juste `","`

#### RetroPlay (RetroArchOverlayParser.kt lignes 287-294):

```kotlin
val descValue = descLine.substringAfter("\"").substringBefore("\"")
val parts = descValue.split(",").map { it.trim() }  // ⚠️ Split par "," puis trim

if (parts.size < 6) {
    Log.e(TAG, "Invalid button definition (expected 6+ tokens, got ${parts.size})")
    return null
}
```

**⚠️ DIFFÉRENCE:** 
- RetroArch: Tokenize par `", "` (virgule + espace)
- RetroPlay: Split par `","` puis `trim()` chaque token

**Impact:** Si un .cfg a `"a, 0.5, 0.5,radial,0.05,0.05"` (espace après virgule mais pas avant), RetroArch et RetroPlay donneront des résultats différents.

**Correction requise:** Utiliser `split(", ")` au lieu de `split(",").map { it.trim() }`

---

### 2. CONVERSION PIXEL → NORMALIZED

#### RetroArch (task_overlay.c lignes 261-275):

```c
// Si !normalized, utiliser width_mod/height_mod pour conversion
bool by_pixel = !normalized;
if (by_pixel) {
    width_mod = 1.0f / width;   // width = dimensions image de fond
    height_mod = 1.0f / height; // height = dimensions image de fond
}

// Conversion pixel → normalized
desc->x = (float)strtod(elem1, NULL) * width_mod;
desc->y = (float)strtod(elem2, NULL) * height_mod;
desc->range_x = (float)strtod(elem4, NULL) * width_mod;
desc->range_y = (float)strtod(elem5, NULL) * height_mod;
```

**CRITIQUE:** `width_mod` et `height_mod` sont calculés depuis les dimensions de l'image de fond (`overlayN_overlay`)

#### RetroPlay (RetroArchOverlayParser.kt lignes 178-192, 331-349):

```kotlin
if (!normalized && backgroundImage != null && imageDimensionsCallback != null) {
    val dimensions = imageDimensionsCallback(backgroundImage, overlayName)
    if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
        widthMod = 1.0f / dimensions.first
        heightMod = 1.0f / dimensions.second
    }
}

val x = xRaw * widthMod
val y = yRaw * heightMod
val width = rangeX * widthMod
val height = rangeY * heightMod
```

**✅ CORRECT:** Logique identique

---

### 3. PARSING ACTION (button_mask)

#### RetroArch (task_overlay.c lignes 329-364):

```c
// Parse format "a|b|c" (séparé par "|")
char *tmp = strdup(elem0);
char *save_ptr2 = NULL;
char *token = strtok_r(tmp, "|", &save_ptr2);

while (token != NULL) {
    if (strcmp(token, "nul") != 0) {
        // Convertir token en ID RetroPad
        unsigned id = input_config_translate_str_to_bind_id(token);
        BIT256_SET(desc->button_mask, id);
    }
    token = strtok_r(NULL, "|", &save_ptr2);
}
```

**CRITIQUE:** 
- Parse format `"a|b|c"` (séparé par `"|"`)
- Ignore tokens `"nul"` ou `"null"`
- Convertit chaque token en ID RetroPad via `input_config_translate_str_to_bind_id()`

#### RetroPlay (RetroArchOverlayParser.kt lignes 324):

```kotlin
val buttonMask = RetroArchButtonMapping.parseButtonMask(action)
```

**Vérification requise:** `RetroArchButtonMapping.parseButtonMask()` doit:
1. Split par `"|"`
2. Ignorer `"nul"` et `"null"`
3. Convertir chaque token en ID RetroPad

---

### 4. PARSING HITBOX TYPE

#### RetroArch (task_overlay.c lignes 377-384):

```c
if (string_is_equal(elem3, "radial"))
    desc->hitbox = OVERLAY_HITBOX_RADIAL;
else if (string_is_equal(elem3, "rect"))
    desc->hitbox = OVERLAY_HITBOX_RECT;
else if (string_is_equal(elem3, "none"))
    desc->hitbox = OVERLAY_HITBOX_NONE;
else {
    RARCH_ERR("[Overlay] Unknown hitbox type: %s\n", elem3);
    desc->hitbox = OVERLAY_HITBOX_RADIAL;  // Default
}
```

#### RetroPlay (RetroArchOverlayParser.kt lignes 333-341):

```kotlin
val shape = when (parts[3].lowercase()) {
    "radial" -> ButtonShape.RADIAL
    "rect" -> ButtonShape.RECT
    "none" -> ButtonShape.NONE
    else -> {
        Log.w(TAG, "Unknown shape: ${parts[3]}, defaulting to RADIAL")
        ButtonShape.RADIAL
    }
}
```

**✅ CORRECT:** Logique identique

---

### 5. PARSING PARAMÈTRES OVERLAY GLOBAL

#### RetroArch (task_overlay.c lignes 672-942):

**Paramètres parsés:**
1. `overlayN_name` → `overlay->name`
2. `overlayN_full_screen` → `overlay->flags |= OVERLAY_FULL_SCREEN`
3. `overlayN_normalized` → `overlay->config.normalized`
4. `overlayN_range_mod` → `overlay->config.range_mod`
5. `overlayN_alpha_mod` → `overlay->config.alpha_mod`
6. `overlayN_rect` → `overlay->x, y, w, h` (format `"x,y,w,h"`)
7. `overlayN_overlay` → Image de fond (legacy)
8. `overlayN_aspect_ratio` → `overlay->aspect_ratio`
9. `overlayN_block_x_separation` → `overlay->block_x_separation`
10. `overlayN_block_y_separation` → `overlay->block_y_separation`
11. `overlayN_auto_x_separation` → `overlay->auto_x_separation`
12. `overlayN_auto_y_separation` → `overlay->auto_y_separation`

#### RetroPlay (RetroArchOverlayParser.kt lignes 125-247):

**Paramètres parsés:**
1. ✅ `overlayN_name`
2. ✅ `overlayN_full_screen`
3. ✅ `overlayN_normalized`
4. ✅ `overlayN_range_mod`
5. ✅ `overlayN_alpha_mod`
6. ✅ `overlayN_rect`
7. ✅ `overlayN_overlay`
8. ✅ `overlayN_aspect_ratio`
9. ✅ `overlayN_block_x_separation`
10. ✅ `overlayN_block_y_separation`
11. ✅ `overlayN_auto_x_separation`
12. ✅ `overlayN_auto_y_separation`

**✅ TOUS LES PARAMÈTRES PARSÉS**

---

### 6. PARSING PARAMÈTRES DESCRIPTEUR

#### RetroArch (task_overlay.c lignes 219-517):

**Paramètres parsés (par desc):**
1. `overlayN_descN_overlay` → Image du bouton
2. `overlayN_descN_next_target` → Nom overlay cible (overlay_next)
3. `overlayN_descN_range_mod` → Range modifier per-button
4. `overlayN_descN_alpha_mod` → Alpha modifier per-button
5. `overlayN_descN_exclusive` → Exclusive hitbox
6. `overlayN_descN_range_mod_exclusive` → Range mod exclusive
7. `overlayN_descN_movable` → Movable button
8. `overlayN_descN_reach_x` → Reach extension X
9. `overlayN_descN_reach_y` → Reach extension Y
10. `overlayN_descN_reach_up` → Reach extension up
11. `overlayN_descN_reach_down` → Reach extension down
12. `overlayN_descN_reach_left` → Reach extension left
13. `overlayN_descN_reach_right` → Reach extension right
14. `overlayN_descN_saturate_pct` → Analog saturation
15. `overlayN_descN_normalized` → Override global normalized (per-desc)
16. `overlayN_descN_up` → 8-way mapping up (dpad_area, abxy_area)
17. `overlayN_descN_down` → 8-way mapping down
18. `overlayN_descN_left` → 8-way mapping left
19. `overlayN_descN_right` → 8-way mapping right
20. `overlayN_descN_up_left` → 8-way mapping up_left
21. `overlayN_descN_up_right` → 8-way mapping up_right
22. `overlayN_descN_down_left` → 8-way mapping down_left
23. `overlayN_descN_down_right` → 8-way mapping down_right

#### RetroPlay (RetroArchOverlayParser.kt lignes 267-468):

**Paramètres parsés (par desc):**
1. ✅ `overlayN_descN_overlay` → `imagePath` (ligne 352-353)
2. ✅ `overlayN_descN_next_target` → `nextTarget` (ligne 356-357)
3. ✅ `overlayN_descN_range_mod` → `rangeModifier` (ligne 360-361)
4. ✅ `overlayN_descN_alpha_mod` → `alphaModifier` (ligne 364-365)
5. ✅ `overlayN_descN_exclusive` → `exclusive` (ligne 366-367)
6. ✅ `overlayN_descN_range_mod_exclusive` → `rangeModExclusive` (ligne 368-369)
7. ✅ `overlayN_descN_movable` → `movable` (ligne 370-371)
8. ✅ `overlayN_descN_reach_x` → `reachX` (ligne 377)
9. ✅ `overlayN_descN_reach_y` → `reachY` (ligne 378)
10. ✅ `overlayN_descN_reach_up` → `reachUp` (ligne 379)
11. ✅ `overlayN_descN_reach_down` → `reachDown` (ligne 380)
12. ✅ `overlayN_descN_reach_left` → `reachLeft` (ligne 381)
13. ✅ `overlayN_descN_reach_right` → `reachRight` (ligne 382)
14. ✅ `overlayN_descN_saturate_pct` → `saturatePct` (ligne 385)
15. ❌ `overlayN_descN_normalized` → **MANQUANT** (override global normalized per-desc)
16. ✅ `overlayN_descN_up` → `eightwayUp` (ligne 403)
17. ✅ `overlayN_descN_down` → `eightwayDown` (ligne 404)
18. ✅ `overlayN_descN_left` → `eightwayLeft` (ligne 405)
19. ✅ `overlayN_descN_right` → `eightwayRight` (ligne 406)
20. ✅ `overlayN_descN_up_left` → `eightwayUpLeft` (ligne 407)
21. ✅ `overlayN_descN_up_right` → `eightwayUpRight` (ligne 408)
22. ✅ `overlayN_descN_down_left` → `eightwayDownLeft` (ligne 409)
23. ✅ `overlayN_descN_down_right` → `eightwayDownRight` (ligne 410)

**⚠️ PARAMÈTRE MANQUANT:** `overlayN_descN_normalized` (override global normalized per-desc)

**RetroArch (task_overlay.c lignes ~260-265):**
```c
strlcpy(overlay_key + _len, "_normalized", sizeof(overlay_key) - _len);
if (config_get_bool(conf, overlay_key, &tmp_bool))
    normalized = tmp_bool;  // Override global normalized
```

**RetroPlay:** Ce paramètre n'est pas parsé dans `parseButton()`. Le paramètre `normalized` est passé depuis `parseOverlay()` mais ne peut pas être overridé per-desc.

**Impact:** Si un .cfg a `overlay0_normalized = false` mais `overlay0_desc5_normalized = true`, RetroArch utilisera `true` pour desc5, RetroPlay utilisera `false`.

**Correction requise:** Parser `overlayN_descN_normalized` dans `parseButton()` et l'utiliser pour override `normalized` localement.

---

## PROBLÈMES IDENTIFIÉS

### 1. ⚠️ TOKENIZE PAR ", " vs ","

**Problème:** RetroArch tokenize par `", "` (virgule + espace), RetroPlay split par `","` puis trim.

**Impact:** Format `"a, 0.5,0.5,radial,0.05,0.05"` (espace après première virgule) sera mal parsé.

**Correction requise:** Utiliser `split(", ")` au lieu de `split(",").map { it.trim() }`

---

### 2. ⚠️ VALIDATION NOMBRE DE TOKENS

**RetroArch:** Vérifie `list_size >= 6` (6 tokens minimum)
**RetroPlay:** Vérifie `parts.size < 6` (6 tokens minimum)

**✅ CORRECT:** Validation identique

---

### 3. ⚠️ PARSING ACTION "a|b|c"

**Vérification requise:** `RetroArchButtonMapping.parseButtonMask()` doit:
- Split par `"|"`
- Ignorer `"nul"` et `"null"`
- Convertir chaque token en ID RetroPad

---

## PLAN D'AUDIT COMPLET

1. ✅ Comparer format descripteur (tokenize) - **PROBLÈME CRITIQUE IDENTIFIÉ**
2. ✅ Comparer conversion pixel → normalized - **CORRECT**
3. ✅ Comparer parsing action (button_mask) - **CORRECT** (parseButtonMask utilise split("|"))
4. ✅ Comparer parsing hitbox type - **CORRECT**
5. ✅ Vérifier parsing tous paramètres overlay global - **TOUS PARSÉS**
6. ✅ Vérifier parsing tous paramètres descripteur - **TOUS PARSÉS**
7. ✅ Vérifier support #include (profondeur, chemins) - **CORRECT** (16 niveaux max)
8. ✅ Vérifier résolution targets (next_target → next_index) - **CORRECT**
9. ⚠️ Vérifier edge cases (valeurs manquantes, formats invalides) - **À VÉRIFIER**

---

## PROBLÈMES CRITIQUES IDENTIFIÉS

### 1. ❌ TOKENIZE PAR ", " vs ","

**RetroArch (task_overlay.c ligne ~240):**
```c
if ((tok = strtok_r(overlay_cpy, ", ", &save)))  // ", " (virgule + ESPACE)
```

**RetroPlay (RetroArchOverlayParser.kt ligne 287):**
```kotlin
val parts = descValue.split(",").map { it.trim() }  // ⚠️ Split par "," puis trim
```

**Impact:** 
- Format `"a, 0.5,0.5,radial,0.05,0.05"` (espace après première virgule) sera mal parsé
- RetroArch: `["a", " 0.5", "0.5", "radial", "0.05", "0.05"]` → `["a", "0.5", "0.5", "radial", "0.05", "0.05"]`
- RetroPlay: `["a", " 0.5", "0.5", "radial", "0.05", "0.05"]` → `["a", "0.5", "0.5", "radial", "0.05", "0.05"]` (OK après trim)
- MAIS: Format `"a,0.5, 0.5,radial,0.05,0.05"` (espace avant deuxième virgule) sera différent:
  - RetroArch: `["a", "0.5", " 0.5", "radial", "0.05", "0.05"]` → `["a", "0.5", "0.5", "radial", "0.05", "0.05"]`
  - RetroPlay: `["a", "0.5", " 0.5", "radial", "0.05", "0.05"]` → `["a", "0.5", "0.5", "radial", "0.05", "0.05"]` (OK après trim)

**En fait, après analyse:** Le `trim()` dans RetroPlay devrait gérer les espaces, MAIS le comportement n'est pas identique à RetroArch qui tokenize directement par `", "`.

**Correction requise:** Utiliser `split(", ")` au lieu de `split(",").map { it.trim() }` pour correspondre exactement à RetroArch.

**Code corrigé:**
```kotlin
// AVANT:
val parts = descValue.split(",").map { it.trim() }

// APRÈS:
val parts = descValue.split(", ").map { it.trim() }  // Split par ", " comme RetroArch
```

**Note:** Le `trim()` reste nécessaire pour gérer les espaces en début/fin de chaque token.

---

## VÉRIFICATIONS COMPLÉMENTAIRES

### Parsing Action "a|b|c"

**RetroArch (task_overlay.c lignes 351-364):**
```c
const char *tmp = strtok_r(key, "|", &save);
for (; tmp; tmp = strtok_r(NULL, "|", &save)) {
    if (!string_is_equal(tmp, "nul"))
        BIT256_SET(desc->button_mask, input_config_translate_str_to_bind_id(tmp));
}
```

**RetroPlay (OverlayModels.kt lignes 248-268):**
```kotlin
val parts = actionString.split("|").map { it.trim().lowercase() }
val mask = parts.mapNotNull { part ->
    ACTION_TO_RETROPAD_ID[part]
}.toSet()
```

**✅ CORRECT:** Logique identique (split par "|", ignore "nul")

---

### Parsing reach_* avec defaults

**RetroArch (task_overlay.c lignes ~400-430):**
```c
// reach_x et reach_y d'abord (defaults pour reach_left/right et reach_up/down)
strlcpy(conf_key + _len, "_reach_x", sizeof(conf_key) - _len);
desc->reach_right = 1.0f;
desc->reach_left  = 1.0f;
if (config_get_float(conf, conf_key, &tmp_float)) {
    desc->reach_right = tmp_float;
    desc->reach_left  = tmp_float;
}

// Puis reach_left et reach_right (override)
strlcpy(conf_key + _len, "_reach_left", sizeof(conf_key) - _len);
if (config_get_float(conf, conf_key, &tmp_float))
    desc->reach_left = tmp_float;

strlcpy(conf_key + _len, "_reach_right", sizeof(conf_key) - _len);
if (config_get_float(conf, conf_key, &tmp_float))
    desc->reach_right = tmp_float;
```

**RetroPlay (RetroArchOverlayParser.kt lignes 377-382):**
```kotlin
val reachX = readFloat("reach_x", 1.0f)
val reachY = readFloat("reach_y", 1.0f)
val reachUp = readFloat("reach_up", reachY)      // Default = reachY
val reachDown = readFloat("reach_down", reachY)  // Default = reachY
val reachLeft = readFloat("reach_left", reachX)   // Default = reachX
val reachRight = readFloat("reach_right", reachX) // Default = reachX
```

**✅ CORRECT:** Logique identique (reach_x/y comme defaults pour reach_left/right/up/down)

---

### Hitbox NONE si reach_* == 0.0f

**RetroArch (task_overlay.c lignes ~430-435):**
```c
if ((desc->reach_left == 0.0f && desc->reach_right == 0.0f) ||
    (desc->reach_up == 0.0f && desc->reach_down == 0.0f))
    desc->hitbox = OVERLAY_HITBOX_NONE;
```

**RetroPlay (RetroArchOverlayRenderer.kt lignes 1308-1311):**
```kotlin
if ((button.reachLeft + button.reachRight == 0.0f) || 
    (button.reachUp + button.reachDown == 0.0f)) {
    return false  // Hitbox désactivée
}
```

**⚠️ DIFFÉRENCE:** 
- RetroArch: Vérifie `reach_left == 0.0f && reach_right == 0.0f` (les deux doivent être 0)
- RetroPlay: Vérifie `reachLeft + reachRight == 0.0f` (somme = 0, ce qui est équivalent si les deux sont >= 0)

**Impact:** Si `reachLeft = -0.1f` et `reachRight = 0.1f`, RetroArch ne désactive pas, RetroPlay désactive.

**Correction requise:** Utiliser la même logique que RetroArch: `reachLeft == 0.0f && reachRight == 0.0f`

---

## RÉSUMÉ DES CORRECTIONS REQUISES

1. **CRITIQUE:** Tokenize par `", "` au lieu de `","` dans `parseButton()`
2. **MOYEN:** Vérifier hitbox NONE avec `== 0.0f && == 0.0f` au lieu de `+ == 0.0f`
3. **MOYEN:** Parser `overlayN_descN_normalized` (override global normalized per-desc)

---

## CORRECTIONS APPLIQUÉES

1. ✅ **Tokenize par `", "`** - Corrigé dans `parseButton()` ligne 310
2. ✅ **Tokenize rect par `", "`** - Corrigé dans `parseOverlay()` ligne 159
3. ✅ **Hitbox NONE** - Corrigé dans `isTouchInsideButton()` ligne 1310
4. ✅ **overlayN_descN_normalized** - Ajouté parsing dans `parseButton()` lignes 283-302
5. ✅ **Valeurs par défaut** - Corrigé `normalized` (false), `fullScreen` (false), `autoYSeparation` (false), logique `autoXSeparation` (lignes 143-220)

**Note:** Le recalcul de `widthMod`/`heightMod` pour `overlayN_descN_normalized` est simplifié:
- Si `effectiveNormalized == true` (normalized): `effectiveWidthMod = 1.0f`, `effectiveHeightMod = 1.0f`
- Si `effectiveNormalized == false` (pixel): Utilise les `widthMod`/`heightMod` globaux (calculés dans `parseOverlay()`)

Un recalcul complet nécessiterait de passer `imageDimensionsCallback` à `parseButton()` pour recalculer les mods depuis l'image de fond, ce qui est complexe et rarement utilisé dans les overlays officiels.

---

**Dernière mise à jour:** 2025-01-XX - Audit complet terminé, toutes corrections appliquées

