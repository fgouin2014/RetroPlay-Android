# Audit Nos Rules - Gamepads (Bytes par Bytes)

**Date:** 2025-12-01  
**Méthodologie:** Nos Rules - Comparaison exhaustive bytes par bytes avec RetroArch  
**Objectif:** Vérifier que le parsing, mapping et gestion des gamepads sont identiques à RetroArch

**Sources analysées:**
- `c:\repos\RetroArch-master\input\drivers\android_input.c` (handle_hotplug, parsing autoconfig)
- `c:\repos\RetroArch-master\input\drivers_joypad\android_joypad.c` (joypad state, axis, button)
- `c:\repos\docs-master\docs\guides\controller-autoconfiguration.md` (912 lignes - spécifications officielles)
- `c:\repos\retroarch-joypad-autoconfig-master\android\` (209 fichiers .cfg - exemples réels)
- `RetroPlay-Android/app/src/main/java/com/retroplay/input/AutoconfigManager.kt` (parsing autoconfig)
- `RetroPlay-Android/libretrodroid/src/main/cpp/input.cpp` (gestion inputs natifs)
- `RetroPlay-Android/app/src/main/java/com/retroplay/overlay/models/OverlayModels.kt` (RetroArchButtonMapping)

---

## PLAN D'AUDIT COMPLET

### 1. PARSING AUTOCONFIG (.cfg)

#### 1.1 Format du fichier .cfg

**RetroArch (controller-autoconfiguration.md):**
```
input_driver = "android"
input_device = "Device Name"
input_device_display_name = "Display Name"
input_vendor_id = "1118"
input_product_id = "654"
input_*_btn = "96"
input_*_axis = "+0"
input_*_label = "Label"
```

**RetroPlay (AutoconfigManager.kt lignes 330-500):**
- ✅ Parse `input_driver`, `input_device`, `input_device_display_name`
- ✅ Parse `input_vendor_id`, `input_product_id`
- ✅ Parse `input_*_btn`, `input_*_axis`, `input_*_label`
- ⚠️ **À VÉRIFIER:** Support alternatives (`input_device_alt1`, `input_vendor_id_alt1`, etc.)
- ⚠️ **À VÉRIFIER:** Parsing des valeurs (guillemets, espaces, format numérique)

#### 1.2 Matching algorithm

**RetroArch (android_input.c:1041-1363):**
- Score de matching basé sur:
  1. VID/PID exact match (score le plus élevé)
  2. Device name match
  3. Alternatives (alt1, alt2, etc.)
- Hacks spéciaux pour devices connus (Shield, Xperia Play, etc.)

**RetroPlay (AutoconfigManager.kt):**
- ⚠️ **À VÉRIFIER:** Algorithme de matching identique
- ⚠️ **À VÉRIFIER:** Support des hacks spéciaux

---

### 2. MAPPING BOUTONS ANDROID → RETROPAD

#### 2.1 Conversion KeyCode → RetroPad ID

**RetroArch (android_input.c):**
- Mapping direct via autoconfig (`input_*_btn = "96"`)
- KeyCode Android → RetroPad ID via table de mapping

**RetroPlay (input.cpp:convertAndroidToLibretroKey):**
- ⚠️ **À VÉRIFIER:** Table de mapping identique
- ⚠️ **À VÉRIFIER:** Gestion des boutons spéciaux (L2/R2, L3/R3)

#### 2.2 Mapping axes analogiques

**RetroArch (android_joypad.c:74-97):**
- `android_joypad_axis_state()` gère AXIS_NEG_GET et AXIS_POS_GET
- Range: [-0x7fff, +0x7fff]

**RetroPlay (input.cpp:86-123):**
- ⚠️ **À VÉRIFIER:** Conversion identique
- ⚠️ **À VÉRIFIER:** Range identique (MAX_RANGE_MOTION = 0x7fff)

---

### 3. GESTION DIAGONALES (8-WAY)

#### 3.1 D-Pad diagonales

**RetroArch (input_driver.c):**
- Détection diagonales via combinaison UP+LEFT, UP+RIGHT, DOWN+LEFT, DOWN+RIGHT
- IDs spéciaux: RETRO_DEVICE_ID_JOYPAD_UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT

**RetroPlay (input.cpp:41-83):**
- ✅ Gestion diagonales dans `getInputState()` pour RETRO_DEVICE_ID_JOYPAD_LEFT/RIGHT/UP/DOWN
- ✅ Utilise `anyPressed()` avec IDs diagonaux
- ⚠️ **À VÉRIFIER:** Logique identique (axis || buttons)

#### 3.2 ABXY diagonales (overlays)

**RetroPlay (RetroArchOverlayRenderer.kt:get8WayDirections):**
- ⚠️ **À VÉRIFIER:** Comparaison avec RetroArch pour overlays

---

### 4. GESTION TRIGGERS (L2/R2)

#### 4.1 Triggers analogiques

**RetroArch (android_input.c):**
- `analog_state[port][6]` = L2 (range [0, 0x7fff])
- `analog_state[port][7]` = R2 (range [0, 0x7fff])

**RetroPlay (input.cpp:106-119):**
- ✅ `triggerL2` et `triggerR2` dans `GamePadState`
- ✅ Conversion dans `RETRO_DEVICE_INDEX_ANALOG_BUTTON`
- ⚠️ **À VÉRIFIER:** Range identique [0, 0x7fff]

---

### 5. PARSING BUTTON MASK (OVERLAYS)

#### 5.1 Format "a|b|c"

**RetroArch (task_overlay.c:344-353):**
```c
const char *tmp = strtok_r(key, "|", &save);
for (; tmp; tmp = strtok_r(NULL, "|", &save)) {
    if (!string_is_equal(tmp, "nul"))
        BIT256_SET(desc->button_mask, input_config_translate_str_to_bind_id(tmp));
}
```

**RetroPlay (OverlayModels.kt:248-268):**
- ✅ Split par "|"
- ✅ Ignore "nul" et "null"
- ⚠️ **À VÉRIFIER:** Conversion `input_config_translate_str_to_bind_id()` identique

#### 5.2 Mapping string → RetroPad ID

**RetroArch (input_config_translate_str_to_bind_id):**
- Table de mapping complète: "a", "b", "x", "y", "l", "r", "l2", "r2", "l3", "r3", "start", "select", "up", "down", "left", "right", "menu_toggle", etc.

**RetroPlay (OverlayModels.kt:ACTION_TO_RETROPAD_ID):**
- ⚠️ **À VÉRIFIER:** Table complète et identique

---

### 6. GESTION GAMEPADS PHYSIQUES vs OVERLAYS

#### 6.1 Hide when gamepad connected

**RetroArch (input_driver.c:5333):**
```c
if (settings->bools.input_overlay_hide_when_gamepad_connected)
{
   // Cache overlay si gamepad physique détecté
}
```

**Détection gamepad RetroArch:**
- Vérifie si un gamepad est connecté via le système d'input
- Utilise `input_autoconfigure_get_device_name()` ou équivalent

**RetroPlay (RetroArchOverlayRenderer.kt:142-153):**
```kotlin
val isGamepadConnected = remember {
    android.view.InputDevice.getDeviceIds().any { deviceId ->
        val device = android.view.InputDevice.getDevice(deviceId)
        device != null && (device.sources and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
    }
}

if (hideWhenGamepadConnected && isGamepadConnected) {
    return  // Cache overlay
}
```

**⚠️ À VÉRIFIER:**
- Détection gamepad identique (Android InputDevice vs RetroArch)
- Timing de la détection (remember vs polling)
- Gestion des gamepads connectés/déconnectés dynamiquement

#### 6.2 Show inputs mode

**RetroArch:**
- Option `input_overlay_show_inputs` avec valeurs: "none", "touch", "physical", "both"
- Affiche visuellement les inputs touchés (overlay) ou gamepad physique

**RetroPlay (OverlayModels.kt:ShowInputsMode):**
```kotlin
enum class ShowInputsMode {
    NONE,      // Ne rien afficher
    TOUCH,     // Afficher seulement les inputs touch (overlay)
    PHYSICAL,  // Afficher seulement les inputs gamepad physique
    BOTH       // Afficher les deux
}
```

**⚠️ À VÉRIFIER:**
- Comportement visuel identique
- Intégration avec `showInputs` dans le renderer
- Affichage des inputs gamepad physique (nécessite tracking des inputs)

#### 6.3 Interaction overlay touch vs gamepad physique

**RetroArch:**
- Les overlays touch et gamepads physiques peuvent fonctionner simultanément
- Les inputs sont combinés (OR logic) pour chaque RetroPad ID
- Pas de conflit: touch → overlay → RetroPad, gamepad → RetroPad directement

**RetroPlay (RetroArchOverlayRenderer.kt:pointerInteropFilter):**
```kotlin
.pointerInteropFilter { event ->
    val handled = handleOverlayTouch(...)
    // Mode Zapper: Ne consommer QUE si un bouton est touché
    if (isZapperGame) {
        handled  // Retourne true seulement si bouton touché
    } else {
        true     // Mode normal: toujours consommer
    }
}
```

**⚠️ À VÉRIFIER:**
- Logique de combinaison identique (OR vs AND)
- Gestion des conflits (même bouton touché sur overlay ET gamepad)
- Priorité des inputs (touch vs gamepad)

#### 6.4 Mapping boutons overlay → RetroPad

**RetroArch (task_overlay.c:parseButtonMask):**
- Parse format "a|b|c" → button_mask (bitmask)
- Utilise `input_config_translate_str_to_bind_id()` pour convertir string → RetroPad ID

**RetroPlay (OverlayModels.kt:RetroArchButtonMapping):**
```kotlin
fun parseButtonMask(actionString: String): Set<Int> {
    val parts = actionString.split("|").map { it.trim().lowercase() }
    val mask = parts.mapNotNull { part ->
        ACTION_TO_RETROPAD_ID[part]
    }.toSet()
    return mask
}
```

**⚠️ À VÉRIFIER:**
- Table `ACTION_TO_RETROPAD_ID` complète et identique à `input_config_translate_str_to_bind_id()`
- Tous les boutons supportés: "a", "b", "x", "y", "l", "r", "l2", "r2", "l3", "r3", "start", "select", "up", "down", "left", "right", "menu_toggle", "overlay_next", etc.

---

### 7. GESTION DES TOUCHES (TOUCHSCREEN)

#### 7.1 Événements touch (ACTION_DOWN, ACTION_MOVE, ACTION_UP)

**RetroArch (input_driver.c:input_overlay_poll):**
- Gère les touches via `input_overlay_pointer_state_t` (max 16 touches)
- Tracking des touches: `input_overlay_track_touch_inputs()`
- Conversion coordonnées: `video_driver_translate_coord_viewport()`

**RetroPlay (RetroArchOverlayRenderer.kt:handleOverlayTouch):**
```kotlin
when (event.actionMasked) {
    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { ... }
    MotionEvent.ACTION_MOVE -> { ... }
    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { ... }
    MotionEvent.ACTION_CANCEL -> { ... }
}
```

**⚠️ À VÉRIFIER:**
- Gestion multi-touch identique (max 16 touches)
- Tracking des touches (pointerId, index)
- Gestion ACTION_CANCEL

#### 7.2 Conversion coordonnées touch

**RetroArch (input_driver.c:video_driver_translate_coord_viewport):**
- Convertit coordonnées écran → coordonnées viewport normalisées [0.0-1.0]
- Puis convertit dans le système de coordonnées overlay (mod_x, mod_y, mod_w, mod_h)

**RetroPlay (RetroArchOverlayRenderer.kt:550-555):**
```kotlin
// CRITIQUE: Les touches de pointerInteropFilter sont en pixels écran (composable fillMaxSize)
// Les hitboxes sont projetées avec viewport.x + (button.xHitbox * viewport.width), donc aussi en pixels écran
// Pas besoin de conversion! Utiliser directement x, y
val x = event.getX(pointerIndex)
val y = event.getY(pointerIndex)
```

**⚠️ À VÉRIFIER:**
- Conversion coordonnées identique (écran → viewport → overlay)
- Gestion du viewport (letterboxing/pillarboxing)
- Projection des hitboxes dans le viewport

#### 7.3 Multi-touch et touch_mask

**RetroArch (input_overlay.h:OVERLAY_MAX_TOUCH = 16):**
- Support jusqu'à 16 touches simultanées
- `touch_mask` par bouton: bitmask des touches actives
- `desc->touch_mask |= (1 << touch_idx)` pour activer une touch

**RetroPlay (RetroArchOverlayRenderer.kt:buttonTouchMasks):**
```kotlin
val buttonTouchMasks = remember { mutableStateMapOf<OverlayButton, MutableSet<Int>>() }
// Map<OverlayButton, Set<pointerId>>
```

**⚠️ À VÉRIFIER:**
- Max touches identique (16)
- Gestion touch_mask identique (Set<Int> vs bitmask)
- Tracking des touches par bouton

#### 7.4 Détection touch dans hitboxes

**RetroArch (input_overlay.c:isTouchInsideButton):**
- Vérifie si touch est dans hitbox (RADIAL, RECT, DPAD_AREA, ABXY_AREA)
- Utilise `range_mod` pour étendre hitbox après premier touch
- Gère `reach_*` pour hitboxes asymétriques

**RetroPlay (RetroArchOverlayRenderer.kt:isTouchInsideButton):**
```kotlin
fun isTouchInsideButton(
    x: Float, y: Float,
    button: OverlayButton,
    rangeModifier: Float,
    viewport: OverlayViewport,
    overlayScale: Float
): Boolean
```

**⚠️ À VÉRIFIER:**
- Calcul hitbox identique (RADIAL, RECT, DPAD_AREA, ABXY_AREA)
- Gestion `range_mod` identique (1.0f initial, puis étendu)
- Gestion `reach_*` identique (hitboxes asymétriques)

#### 7.5 Mapping touch → RetroPad

**RetroArch (task_overlay.c:parseButtonMask):**
- Parse action "a|b|c" → button_mask (bitmask)
- Envoie les inputs via `input_driver_state_t`

**RetroPlay (RetroArchOverlayRenderer.kt:onButtonPress):**
```kotlin
onButtonPress(button.action)  // "a", "b", "a|b", etc.
// → RetroArchButtonMapping.parseButtonMask() → Set<RetroPadIds>
// → Envoie via callback vers input.cpp
```

**⚠️ À VÉRIFIER:**
- Mapping touch → RetroPad identique
- Gestion combos ("a|b", "left|up")
- Envoi des inputs vers le core

---

### 8. PARSING SETTINGS GAMEPAD

#### 8.1 SharedPreferences

**RetroPlay (GamePadLayoutManager.kt, OverlayPreferenceManager.kt):**
- ✅ Déjà audité dans `AUDIT_GAMEPAD_SETTINGS_PARSING.md`
- ✅ Parsing correct

---

## PROBLÈMES IDENTIFIÉS (À VÉRIFIER)

### 1. ⚠️ PARSING AUTOCONFIG - Alternatives

**Question:** Le parsing des alternatives (`input_device_alt1`, `input_vendor_id_alt1`, etc.) est-il identique à RetroArch?

**À vérifier:**
- Format des alternatives dans AutoconfigManager.kt
- Algorithme de matching avec alternatives
- Support de multiples alternatives (alt1, alt2, alt3, etc.)

---

### 2. ⚠️ MAPPING KEYCODE → RETROPAD

**Question:** La table de conversion Android KeyCode → RetroPad ID est-elle identique?

**À vérifier:**
- `input.cpp:convertAndroidToLibretroKey()`
- Comparaison avec RetroArch `android_input.c`

---

### 3. ⚠️ RANGE AXES ANALOGIQUES

**Question:** Les ranges des axes analogiques sont-ils identiques?

**À vérifier:**
- MAX_RANGE_MOTION = 0x7fff (RetroPlay)
- Range RetroArch: [-0x7fff, +0x7fff]
- Conversion float → int16_t identique

---

### 4. ⚠️ TRIGGERS L2/R2

**Question:** La gestion des triggers analogiques est-elle identique?

**À vérifier:**
- Range [0, 0x7fff] pour L2/R2
- Conversion dans RETRO_DEVICE_INDEX_ANALOG_BUTTON
- Index dans analog_state (6 pour L2, 7 pour R2)

---

### 5. ⚠️ BUTTON MASK MAPPING

**Question:** La table ACTION_TO_RETROPAD_ID est-elle complète et identique?

**À vérifier:**
- Tous les boutons RetroPad supportés
- Mapping string → ID identique à `input_config_translate_str_to_bind_id()`

---

## PROCHAINES ÉTAPES

### Phase 1: Parsing et Mapping
1. **Vérifier parsing autoconfig alternatives** (AutoconfigManager.kt vs android_input.c)
2. **Vérifier mapping KeyCode → RetroPad** (input.cpp vs android_input.c)
3. **Vérifier ranges axes analogiques** (input.cpp vs android_joypad.c)
4. **Vérifier triggers L2/R2** (input.cpp vs android_input.c)
5. **Vérifier button mask mapping** (OverlayModels.kt vs input_config_translate_str_to_bind_id)

### Phase 2: Overlays + Gamepads
6. **Vérifier hide when gamepad** (détection gamepad physique - `input_config_get_device_name(0)` vs `InputDevice.getDeviceIds()`)
7. **Vérifier show inputs mode** (OVERLAY_SHOW_INPUT_* vs ShowInputsMode enum)
8. **Vérifier interaction overlay touch vs gamepad** (combinaison OR/AND, priorité)
9. **Vérifier mapping boutons overlay → RetroPad** (table complète ACTION_TO_RETROPAD_ID)
10. **Vérifier détection dynamique** (gamepad connecté/déconnecté en temps réel)

### Phase 3: Touches (Touchscreen)
11. **Vérifier événements touch** (ACTION_DOWN/MOVE/UP/CANCEL, multi-touch max 16)
12. **Vérifier conversion coordonnées** (écran → viewport → overlay)
13. **Vérifier touch_mask** (Set<Int> vs bitmask, tracking par bouton)
14. **Vérifier détection touch dans hitboxes** (RADIAL, RECT, DPAD_AREA, ABXY_AREA, range_mod, reach_*)
15. **Vérifier mapping touch → RetroPad** (parseButtonMask, combos, envoi vers core)

---

## RÉFÉRENCES

- **RetroArch android_input.c:** `c:\repos\RetroArch-master\input\drivers\android_input.c`
- **RetroArch android_joypad.c:** `c:\repos\RetroArch-master\input\drivers_joypad\android_joypad.c`
- **Spécifications autoconfig:** `c:\repos\docs-master\docs\guides\controller-autoconfiguration.md`
- **Exemples .cfg:** `c:\repos\retroarch-joypad-autoconfig-master\android\`

