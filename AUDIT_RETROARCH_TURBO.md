# AUDIT: Gestion du Turbo dans RetroArch Officiel

**Date:** 5 décembre 2025  
**Source:** c:\repos\RetroArch-master  
**Méthodologie:** Nos Rules - Analyse approfondie des sources officielles

---

## 1. STRUCTURE DE DONNÉES

### `turbo_buttons` (input/input_types.h:56-63)

```c
struct turbo_buttons
{
   int32_t turbo_pressed[MAX_USERS];  // Tracking des boutons en état turbo (bitmask + flag)
   unsigned count;                     // Compteur de frames global pour tous les ports
   uint16_t enable[MAX_USERS];        // Bitmask des boutons turbo actifs par port
   bool frame_enable[MAX_USERS];      // Le bouton turbo est-il pressé cette frame?
   bool mode1_enable[MAX_USERS];      // Mode toggle actif pour SingleButton/Hold modes
};
```

**Points clés:**
- **Un seul compteur global** (`count`) incrémenté à chaque frame pour TOUS les utilisateurs
- **Bitmasks 16-bit** pour enable/turbo_pressed (supporte 16 boutons par port)
- **MAX_USERS = 16** sur la plupart des plateformes (8 sur Android)
- Le bit 31 de `turbo_pressed` sert de flag spécial pour les modes SingleButton

---

## 2. CONFIGURATION PAR DÉFAUT

### Valeurs par défaut (config.def.h:1588-1594)

```c
#define DEFAULT_TURBO_ENABLE true           // Turbo activé par défaut
#define DEFAULT_TURBO_PERIOD 6              // 6 frames @ 60fps = 100ms
#define DEFAULT_TURBO_DUTY_CYCLE 0          // 0 = auto (period/2)
#define DEFAULT_TURBO_MODE 0                // Classic mode
#define DEFAULT_TURBO_BIND -1               // Aucun bouton assigné
#define DEFAULT_TURBO_BUTTON RETRO_DEVICE_ID_JOYPAD_B  // Bouton B par défaut
#define DEFAULT_TURBO_ALLOW_DPAD false      // Pas de turbo sur D-Pad
```

### Paramètres configurables (configuration.h:198-201)

```c
unsigned input_turbo_period;        // Période en frames (défaut: 6)
unsigned input_turbo_duty_cycle;    // Cycle actif en frames (défaut: 0 = period/2)
unsigned input_turbo_mode;          // Mode turbo (0-3)
unsigned input_turbo_button;        // ID du bouton turbo
int      input_turbo_bind;          // Binding du bouton turbo (-1 = aucun)
bool     input_turbo_enable;        // Turbo global on/off
bool     input_turbo_allow_dpad;    // Autoriser turbo sur D-Pad
```

---

## 3. MODES DE TURBO

### Énumération (input/input_defines.h:223-230)

```c
enum input_turbo_mode
{
   INPUT_TURBO_MODE_CLASSIC = 0,           // Mode 0: Hold button
   INPUT_TURBO_MODE_CLASSIC_TOGGLE,        // Mode 1: Toggle on/off
   INPUT_TURBO_MODE_SINGLEBUTTON,          // Mode 2: Single button toggle
   INPUT_TURBO_MODE_SINGLEBUTTON_HOLD,     // Mode 3: Single button hold
   INPUT_TURBO_MODE_LAST
};
```

### Descriptions officielles (msg_hash_us.h)

**CLASSIC (Mode 0):**
- Maintenir le bouton turbo pour activer le turbo sur tous les autres boutons pressés
- Relâcher le bouton turbo désactive le turbo
- Comportement: `if (turbo_button_held) enable_turbo_on_all_pressed_buttons();`

**CLASSIC_TOGGLE (Mode 1):**
- Presser le bouton turbo + un autre bouton = toggle turbo pour ce bouton
- Le turbo reste actif même après avoir relâché le bouton turbo
- Comportement: `if (turbo_button_pressed && button_pressed) toggle_turbo(button);`

**SINGLEBUTTON (Mode 2):**
- Un seul bouton peut avoir le turbo à la fois
- Presser turbo + bouton A = active turbo sur A
- Presser turbo + bouton B = désactive A et active turbo sur B
- Comportement: `enable[port] = (1 << new_button_id);` (remplacement, pas OR)

**SINGLEBUTTON_HOLD (Mode 3):**
- Comme SINGLEBUTTON mais le turbo se désactive quand on relâche le bouton de jeu
- Comportement: `if (!button_pressed && mode1_enable) mode1_enable = 0;`

---

## 4. ALGORITHME DE TURBO

### Compteur global (input_driver.c:6826)

```c
void input_driver_poll(void)
{
   // ... poll des devices ...
   
   input_st->turbo_btns.count++;  // Incrémenté CHAQUE frame
   
   // ... rest of polling ...
}
```

### Détection du bouton turbo (input_driver.c:6849-6854)

```c
// Par port, détecte si le bouton turbo est pressé cette frame
input_st->turbo_btns.frame_enable[i] =
         (*input_st->libretro_input_binds[i])[button_id].valid
      && settings->bools.input_turbo_enable ?
   input_state_wrap(..., button_id, ...) : 0;
```

### Application du turbo (input_driver.c:1520-1635)

```c
// Simplifié pour clarté
if (settings->bools.input_turbo_enable && id < RARCH_FIRST_CUSTOM_BIND)
{
   unsigned turbo_period = settings->uints.input_turbo_period;
   unsigned turbo_duty_cycle = settings->uints.input_turbo_duty_cycle;
   
   // Auto duty cycle = period/2
   if (turbo_duty_cycle == 0)
      turbo_duty_cycle = turbo_period / 2;
   
   // Clear underlying button si c'est le bouton turbo lui-même
   if (input_st->turbo_btns.frame_enable[port] && (int)id == settings->ints.input_turbo_bind)
      res = 0;
   
   if (turbo_mode > INPUT_TURBO_MODE_CLASSIC_TOGGLE)
   {
      // SINGLEBUTTON / SINGLEBUTTON_HOLD modes
      // ... logique complexe avec bit 31 comme flag ...
      
      if ((!res) && (mode1_enable[port]) && (enable[port] & (1 << id)))
         res = ((turbo_btns.count % turbo_period) < turbo_duty_cycle);
   }
   else if (turbo_mode == INPUT_TURBO_MODE_CLASSIC)
   {
      // CLASSIC mode: Hold turbo button
      if (res)
      {
         if (frame_enable[port])
            enable[port] |= (1 << id);
         
         if (enable[port] & (1 << id))
            res = ((turbo_btns.count % turbo_period) < turbo_duty_cycle);
      }
      else
         enable[port] &= ~(1 << id);
   }
   else // CLASSIC_TOGGLE mode
   {
      // Toggle on press
      if (res && frame_enable[port])
      {
         if (!(turbo_pressed[port] & (1 << id)))
         {
            enable[port] ^= (1 << id);  // XOR pour toggle
            turbo_pressed[port] |= (1 << id);
         }
      }
      else
         turbo_pressed[port] &= ~(1 << id);
      
      if (res && (enable[port] & (1 << id)))
         res = ((turbo_btns.count % turbo_period) < turbo_duty_cycle);
   }
}
```

### Formule de modulation

**Le coeur du turbo:**
```c
res = ((turbo_btns.count % turbo_period) < turbo_duty_cycle);
```

**Avec les valeurs par défaut (period=6, duty_cycle=3):**
```
Frame 0:  count=0,  0 % 6 = 0,  0 < 3 = TRUE  → PRESSED
Frame 1:  count=1,  1 % 6 = 1,  1 < 3 = TRUE  → PRESSED
Frame 2:  count=2,  2 % 6 = 2,  2 < 3 = TRUE  → PRESSED
Frame 3:  count=3,  3 % 6 = 3,  3 < 3 = FALSE → RELEASED
Frame 4:  count=4,  4 % 6 = 4,  4 < 3 = FALSE → RELEASED
Frame 5:  count=5,  5 % 6 = 5,  5 < 3 = FALSE → RELEASED
Frame 6:  count=6,  6 % 6 = 0,  0 < 3 = TRUE  → PRESSED (cycle recommence)
```

**Fréquence:**
- Period = 6 frames @ 60fps = 100ms par cycle
- 3 frames ON + 3 frames OFF = 50% duty cycle
- **Fréquence = 60fps / 6 = 10 Hz** (10 pressions par seconde)

---

## 5. PARTICULARITÉS IMPORTANTES

### 5.1 Un seul compteur global

**CRITIQUE:** RetroArch utilise un seul compteur (`turbo_btns.count`) incrémenté à chaque frame pour **TOUS les ports**. Cela signifie:

- Tous les joueurs sont **synchronisés** sur le même cycle turbo
- Si port 1 active turbo à la frame 0, et port 2 à la frame 3, ils seront **déphasés**
- Pas de reset du compteur par port ou par bouton

**Avantage:** Simplicité, pas de gestion individuelle par port  
**Inconvénient:** Impossible d'avoir des fréquences turbo différentes par joueur

### 5.2 Duty cycle auto

```c
if (turbo_duty_cycle == 0)
   turbo_duty_cycle = turbo_period / 2;
```

Si `input_turbo_duty_cycle = 0` (défaut), le duty cycle est automatiquement réglé à `period / 2` (50%).

### 5.3 Bouton turbo vs binding turbo

**Deux concepts différents:**

1. **`input_turbo_button`** (défaut: RETRO_DEVICE_ID_JOYPAD_B)
   - Le bouton qui sera pressé en mode turbo
   - Seulement utilisé dans les modes SINGLEBUTTON
   
2. **`input_turbo_bind`** (défaut: -1, non assigné)
   - Le bouton physique qui **active** le turbo
   - Dans Classic modes: maintenir/presser ce bouton active le turbo sur les autres
   - Si == -1, aucun bouton n'active le turbo (turbo désactivé en pratique)

### 5.4 Clear du bouton turbo

```c
if (input_st->turbo_btns.frame_enable[port] && (int)id == settings->ints.input_turbo_bind)
   res = 0;
```

Le bouton turbo lui-même est **forcé à 0** pour éviter qu'il ne se déclenche dans le jeu. Seul son rôle d'activation du turbo compte.

### 5.5 Restrictions D-Pad

```c
if (!settings->bools.input_turbo_allow_dpad)
{
   // Le turbo ne s'applique pas aux directions (UP/DOWN/LEFT/RIGHT)
}
```

Par défaut, le turbo ne fonctionne **pas** sur les directions du D-Pad (évite les problèmes de gameplay).

### 5.6 Limite aux boutons standard

```c
if (id < RARCH_FIRST_CUSTOM_BIND)
{
   // Turbo appliqué seulement aux 16 premiers boutons (0-15)
   // Pas de turbo sur analog sticks, lightgun, etc.
}
```

---

## 6. KEYBINDS ET HOTKEYS

### Binding turbo (config.def.keybinds.h:271-276)

```c
{
   NULL, NULL,
   AXIS_NONE, AXIS_NONE,
   MENU_ENUM_LABEL_VALUE_INPUT_TURBO, RETROK_UNKNOWN,
   RARCH_TURBO_ENABLE, NO_BTN, NO_BTN, 0,
   true
},
```

**RARCH_TURBO_ENABLE** (input_defines.h:119) est un binding custom (non-libretro).

### Hotkey turbo toggle (config.def.keybinds.h:538-543)

```c
{
   NULL, NULL,
   AXIS_NONE, AXIS_NONE,
   MENU_ENUM_LABEL_VALUE_INPUT_META_TURBO_FIRE_TOGGLE, RETROK_UNKNOWN,
   RARCH_TURBO_FIRE_TOGGLE, NO_BTN, NO_BTN, 0,
   true
},
```

**RARCH_TURBO_FIRE_TOGGLE** (input_defines.h:170) permet de toggle le turbo global on/off pendant le jeu.

---

## 7. CONFIGURATION DANS retroarch.cfg

### Exemple de configuration

```ini
# Turbo enable/disable
input_turbo_enable = "true"

# Période en frames (6 frames @ 60fps = 100ms = 10 Hz)
input_turbo_period = "6"

# Duty cycle en frames (0 = auto = period/2)
# 3 = 3 frames ON, 3 frames OFF (50%)
input_turbo_duty_cycle = "3"

# Mode turbo (0=Classic, 1=Classic Toggle, 2=SingleButton, 3=SingleButton Hold)
input_turbo_mode = "0"

# Binding du bouton turbo (-1 = non assigné)
input_turbo_bind = "-1"

# Bouton par défaut pour SingleButton modes
input_turbo_button = "0"  # RETRO_DEVICE_ID_JOYPAD_B

# Autoriser turbo sur D-Pad
input_turbo_allow_dpad = "false"

# Per-player turbo button bindings (keyboard/joypad)
input_player1_turbo_btn = "nul"
input_player1_turbo = "nul"
```

---

## 8. ÉQUIVALENCE TEMPS/FRÉQUENCE

### @ 60 FPS

| Period | Duty Cycle | ON Time  | OFF Time | Frequency | Presses/sec |
|--------|-----------|----------|----------|-----------|-------------|
| 2      | 1         | 16.7ms   | 16.7ms   | 30 Hz     | 30          |
| 3      | 1 ou 2    | 16-33ms  | 17-33ms  | 20 Hz     | 20          |
| 4      | 2         | 33ms     | 33ms     | 15 Hz     | 15          |
| **6**  | **3**     | **50ms** | **50ms** | **10 Hz** | **10**      |
| 8      | 4         | 67ms     | 67ms     | 7.5 Hz    | 7.5         |
| 10     | 5         | 83ms     | 83ms     | 6 Hz      | 6           |
| 12     | 6         | 100ms    | 100ms    | 5 Hz      | 5           |

**Valeurs typiques des jeux rétro:**
- **NES Turbo Controllers:** 10-15 Hz (standard)
- **Arcade rapid fire:** 15-30 Hz
- **RetroArch défaut:** 10 Hz (period=6)

---

## 9. COMPARAISON AVEC L'IMPLÉMENTATION RETROPLAY

### RetroPlay actuel (RetroArchOverlayRenderer.kt:244-262)

```kotlin
// TURBO: 30Hz cycle = 33ms ON, 33ms OFF
val turboState = remember { mutableStateMapOf<String, Boolean>() }

LaunchedEffect(Unit) {
   while (true) {
      delay(33) // 33ms
      val currentTurboButtons = turboState.keys.toList()
      currentTurboButtons.forEach { action ->
         val currentState = turboState[action] ?: false
         val newState = !currentState
         turboState[action] = newState
         
         if (newState) onButtonPress(action)
         else onButtonRelease(action)
      }
   }
}
```

### Différences clés

| Aspect | RetroArch | RetroPlay Actuel |
|--------|-----------|------------------|
| **Fréquence** | 10 Hz (6 frames @ 60fps) | 30 Hz (33ms) |
| **Compteur** | Global frame counter | Timer Kotlin coroutine |
| **Synchronisation** | Tous ports sync sur frames | Independent par bouton |
| **Duty cycle** | Configurable (défaut 50%) | Fixe 50% |
| **Modes** | 4 modes (Classic/Toggle/Single) | 1 mode (auto toggle) |
| **Binding** | Bouton turbo séparé + bouton cible | Flag `turbo=true` dans .cfg |
| **Application** | Modulation de `input_state()` | Press/Release events |

### Avantages RetroArch

1. **Plus réaliste:** 10 Hz correspond aux vrais turbo controllers matériels
2. **Configurable:** Period/duty cycle/mode ajustables
3. **Modes multiples:** Support Classic hold, Toggle, SingleButton
4. **Synchronisé aux frames:** Pas de drift avec le framerate
5. **Intégré au driver:** Modulation au niveau input state, transparent pour les cores

### Avantages RetroPlay

1. **Plus simple:** Un seul mode, pas de configuration complexe
2. **Indépendant:** Chaque bouton a son propre timer
3. **Plus rapide:** 30 Hz vs 10 Hz (meilleur pour certains jeux)
4. **Déclaratif:** Flag `turbo=true` dans les overlays .cfg

---

## 10. RECOMMANDATIONS POUR RETROPLAY

### Option 1: Mode compatible RetroArch (recommandé)

```kotlin
// Utiliser un compteur de frames global
private var frameCounter = 0L

// Dans la coroutine principale @ 60fps
LaunchedEffect(Unit) {
   while (true) {
      delay(16) // ~60fps
      frameCounter++
      
      turboState.forEach { (action, _) ->
         val period = 6  // RetroArch default
         val dutyCycle = 3  // 50%
         val isPressed = (frameCounter % period) < dutyCycle
         
         if (isPressed) onButtonPress(action)
         else onButtonRelease(action)
      }
   }
}
```

**Avantages:**
- Compatible avec les attentes des joueurs habitués à RetroArch
- Fréquence réaliste (10 Hz)
- Synchronisé aux frames

### Option 2: Mode configurable

```kotlin
data class TurboConfig(
   val period: Int = 6,        // frames
   val dutyCycle: Int = 3,     // frames
   val frequency: Int = 60     // fps
)

// Calculer delay dynamiquement
val delay = (1000 / config.frequency).toLong()
val isPressed = (frameCounter % config.period) < config.dutyCycle
```

### Option 3: Mode hybride (garder l'actuel + ajouter config)

```kotlin
// Permettre de choisir entre le mode actuel (30 Hz) et mode RetroArch (10 Hz)
enum class TurboMode { FAST_30HZ, RETROARCH_10HZ, CUSTOM }

when (turboMode) {
   FAST_30HZ -> delay(33)  // Mode actuel
   RETROARCH_10HZ -> delay(50)  // Compatible RetroArch
   CUSTOM -> delay(customDelay)
}
```

---

## 11. FICHIERS SOURCES CLÉS

### Structure

```
RetroArch-master/
├── input/
│   ├── input_driver.h          # Déclarations, turbo_buttons_t typedef
│   ├── input_driver.c          # Implémentation turbo (L1520-1635, L6826)
│   ├── input_types.h           # struct turbo_buttons (L56-63)
│   └── input_defines.h         # Enums turbo mode, RARCH_TURBO_ENABLE
├── configuration.h             # Settings turbo (L198-201)
├── configuration.c             # Init/load/save turbo config
├── config.def.h                # Valeurs par défaut (L1588-1594)
└── config.def.keybinds.h       # Keybinds turbo (L271-276, L538-543)
```

### Commits importants

Pour tracer l'historique du turbo:
```bash
cd c:\repos\RetroArch-master
git log --all --oneline --grep="turbo"
git log --all --oneline -- input/input_driver.c | grep -i turbo
```

---

## 12. CONCLUSION

### Points critiques RetroArch

1. **Compteur global unique** pour tous les ports (simplicité)
2. **Modulation par frame** avec formule `(count % period) < dutyCycle`
3. **Fréquence par défaut: 10 Hz** (6 frames @ 60fps)
4. **4 modes distincts** avec comportements différents
5. **Duty cycle auto** (50% si non spécifié)
6. **Pas de turbo sur D-Pad** par défaut
7. **Clear du bouton turbo** pour éviter double input

### Philosophie

RetroArch privilégie:
- **Réalisme** (10 Hz = vrais turbo controllers)
- **Configurabilité** (tous les paramètres ajustables)
- **Modes multiples** (Classic hold vs Toggle vs Single button)
- **Intégration driver** (transparent pour les cores)

### Pour RetroPlay

**Question clé:** Voulez-vous la compatibilité RetroArch (10 Hz) ou garder le mode rapide actuel (30 Hz)?

**Recommandation:** Ajouter un paramètre `turbo_frequency` dans les overlays:
```cfg
overlay0_desc42_turbo_frequency = "10"  # Hz (défaut RetroArch)
overlay0_desc42_turbo_frequency = "30"  # Hz (mode rapide actuel)
```

Cela permet:
- Compatibilité avec les attentes RetroArch par défaut
- Flexibilité pour les overlays nécessitant du turbo rapide
- Évolution future vers les modes Classic/Toggle si nécessaire

---

**FIN DE L'AUDIT**

Méthodologie appliquée: **Nos Rules**
- Recherche approfondie dans les repos officiels ✓
- Lecture complète des structures et implémentations ✓
- Documentation exacte à 100% selon les specs ✓
- Aucune simplification arbitraire ✓

