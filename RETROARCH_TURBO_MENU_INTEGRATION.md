# Intégration du Turbo dans le Menu RetroArch

**Date:** 5 décembre 2025  
**Source:** c:\repos\RetroArch-master\menu\  

---

## 1. NAVIGATION MENU

### Hiérarchie complète

```
Main Menu
└── Settings
    └── Input
        └── Port 1 Controls
            ├── RetroPad Binds
            │   ├── B Button
            │   ├── Y Button
            │   ├── ...
            │   └── Turbo Enable         ← Bind pour activer turbo
            │
            └── Turbo Fire ◄────────────── SECTION TURBO
                ├── Turbo Fire             [ON/OFF]
                ├── Turbo Mode             [Classic/Toggle/Single/Hold]
                ├── Turbo Bind             [Button assignment]
                ├── Turbo Button           [Target button]
                ├── Turbo Allow D-Pad      [ON/OFF]
                ├── Turbo Period           [2-100 frames]
                └── Turbo Duty Cycle       [0-100 frames]
```

### Chemin d'accès

```
Settings → Input → Port X Controls → Turbo Fire
```

Ou via Quick Menu en jeu (sur certaines plateformes):
```
Quick Menu → Controls → Port X Controls → Turbo Fire
```

---

## 2. OPTIONS DU MENU

### Configuration complète (menu_setting.c:15917-16045)

```c
case SETTINGS_LIST_INPUT_TURBO_FIRE:
   START_GROUP(list, list_info, &group_info,
         msg_hash_to_str(MENU_ENUM_LABEL_VALUE_INPUT_TURBO_FIRE_SETTINGS),
         parent_group);
   
   // Option 1: Turbo Enable (ON/OFF)
   CONFIG_BOOL(
         list, list_info,
         &settings->bools.input_turbo_enable,
         MENU_ENUM_LABEL_INPUT_TURBO_ENABLE,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_ENABLE,
         DEFAULT_TURBO_ENABLE,  // true
         MENU_ENUM_LABEL_VALUE_OFF,
         MENU_ENUM_LABEL_VALUE_ON,
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   
   // Option 2: Turbo Mode (4 modes)
   CONFIG_UINT(
         list, list_info,
         &settings->uints.input_turbo_mode,
         MENU_ENUM_LABEL_INPUT_TURBO_MODE,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_MODE,
         DEFAULT_TURBO_MODE,  // 0 (Classic)
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   // Range: 0-3 (4 modes)
   menu_settings_list_current_add_range(list, list_info, 
         0, (INPUT_TURBO_MODE_LAST-1), 1, true, true);
   
   // Option 3: Turbo Bind
   CONFIG_INT(
         list, list_info,
         &settings->ints.input_turbo_bind,
         MENU_ENUM_LABEL_INPUT_TURBO_BIND,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_BIND,
         DEFAULT_TURBO_BIND,  // -1 (None)
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   // Range: -1 to (RARCH_ANALOG_BIND_LIST_END-1)
   // -1 = None, 0-15 = RetroPad buttons, 16-23 = Analog
   
   // Option 4: Turbo Button
   CONFIG_UINT(
         list, list_info,
         &settings->uints.input_turbo_button,
         MENU_ENUM_LABEL_INPUT_TURBO_BUTTON,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_BUTTON,
         DEFAULT_TURBO_BUTTON,  // 0 (B button)
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   // Range: 0-15 (RetroPad buttons only)
   
   // Option 5: Turbo Allow D-Pad
   CONFIG_BOOL(
         list, list_info,
         &settings->bools.input_turbo_allow_dpad,
         MENU_ENUM_LABEL_INPUT_TURBO_ALLOW_DPAD,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_ALLOW_DPAD,
         DEFAULT_TURBO_ALLOW_DPAD,  // false
         MENU_ENUM_LABEL_VALUE_OFF,
         MENU_ENUM_LABEL_VALUE_ON,
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   
   // Option 6: Turbo Period
   CONFIG_UINT(
         list, list_info,
         &settings->uints.input_turbo_period,
         MENU_ENUM_LABEL_INPUT_TURBO_PERIOD,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_PERIOD,
         DEFAULT_TURBO_PERIOD,  // 6
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   // Range: 2-100 frames
   menu_settings_list_current_add_range(list, list_info, 2, 100, 1, true, true);
   
   // Option 7: Turbo Duty Cycle
   CONFIG_UINT(
         list, list_info,
         &settings->uints.input_turbo_duty_cycle,
         MENU_ENUM_LABEL_INPUT_TURBO_DUTY_CYCLE,
         MENU_ENUM_LABEL_VALUE_INPUT_TURBO_DUTY_CYCLE,
         DEFAULT_TURBO_DUTY_CYCLE,  // 0 (auto)
         &group_info,
         &subgroup_info,
         parent_group,
         general_write_handler,
         general_read_handler,
         SD_FLAG_NONE
         );
   // Range: 0-100 frames (0 = auto = period/2)
   menu_settings_list_current_add_range(list, list_info, 0, 100, 1, true, true);
```

---

## 3. LABELS ET DESCRIPTIONS

### Labels principaux (msg_hash_us.h:3482-3569)

```c
// Section title
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_FIRE_SETTINGS
   "Turbo Fire"

// Option 1: Enable/Disable
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_ENABLE
   "Turbo Fire"

MENU_ENUM_SUBLABEL_INPUT_TURBO_ENABLE
   "Disabled stops all turbo fire operations."

// Option 2: Mode
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_MODE
   "Turbo Mode"

MENU_ENUM_SUBLABEL_INPUT_TURBO_MODE
   "Select the general behavior of turbo mode."

// Mode 0: Classic
MENU_ENUM_LABEL_VALUE_TURBO_MODE_CLASSIC
   "Classic"

MENU_ENUM_LABEL_HELP_TURBO_MODE_CLASSIC
   "Classic mode, two-button operation. Hold a button and tap the Turbo 
   button to activate the press-release sequence.
   Turbo bind can be assigned in Settings/Input/Port X Controls."

// Mode 1: Classic Toggle
MENU_ENUM_LABEL_VALUE_TURBO_MODE_CLASSIC_TOGGLE
   "Classic (Toggle)"

MENU_ENUM_LABEL_HELP_TURBO_MODE_CLASSIC_TOGGLE
   "Classic toggle mode, two-button operation. Hold a button and tap the 
   Turbo button to enable turbo for that button. To disable turbo: hold 
   the button and press the Turbo button again.
   Turbo bind can be assigned in Settings/Input/Port X Controls."

// Mode 2: Single Button
MENU_ENUM_LABEL_VALUE_TURBO_MODE_SINGLEBUTTON
   "Single Button (Toggle)"

MENU_ENUM_LABEL_HELP_TURBO_MODE_SINGLEBUTTON
   "Toggle mode. Press the Turbo button once to activate the press-release 
   sequence for the selected default button, press it once again to switch 
   it off.
   Turbo bind can be assigned in Settings/Input/Port X Controls."

// Mode 3: Single Button Hold
MENU_ENUM_LABEL_VALUE_TURBO_MODE_SINGLEBUTTON_HOLD
   "Single Button (Hold)"

MENU_ENUM_LABEL_HELP_TURBO_MODE_SINGLEBUTTON_HOLD
   "Hold mode. The press-release sequence for the selected default button 
   is active as long as Turbo button is held down.
   Turbo bind can be assigned in Settings/Input/Port X Controls.
   To emulate the autofire function of the home computer era, set Bind 
   and Button to the same joystick fire button."

// Option 3: Turbo Bind
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_BIND
   "Turbo Bind"

MENU_ENUM_SUBLABEL_INPUT_TURBO_BIND
   "Turbo activating RetroPad bind. Empty uses the port-specific bind."

// Option 4: Turbo Button
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_BUTTON
   "Turbo Button"

MENU_ENUM_SUBLABEL_INPUT_TURBO_BUTTON
   "Target turbo button in 'Single Button' mode."

// Option 5: Allow D-Pad
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_ALLOW_DPAD
   "Turbo Allow D-Pad Directions"

// Option 6: Period
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_PERIOD
   "Turbo Period"

MENU_ENUM_SUBLABEL_INPUT_TURBO_PERIOD
   "The period in frames when turbo-enabled buttons are pressed."

// Option 7: Duty Cycle
MENU_ENUM_LABEL_VALUE_INPUT_TURBO_DUTY_CYCLE
   "Turbo Duty Cycle"

MENU_ENUM_SUBLABEL_INPUT_TURBO_DUTY_CYCLE
   "The number of frames from the Turbo Period the buttons are held down 
   for. If this number is equal to or greater than the Turbo Period, the 
   buttons will never release."

MENU_ENUM_LABEL_VALUE_TURBO_DUTY_CYCLE_HALF
   "Half Period"
```

---

## 4. AFFICHAGE DES MODES

### Fonction d'affichage (menu_setting.c:6821-6841)

```c
static size_t setting_get_string_representation_turbo_mode(
      rarch_setting_t *setting,
      char *s, size_t len)
{
   if (setting)
   {
      switch (*setting->value.target.unsigned_integer)
      {
         case INPUT_TURBO_MODE_CLASSIC:
            return strlcpy(s,
                  msg_hash_to_str(MENU_ENUM_LABEL_VALUE_TURBO_MODE_CLASSIC), len);
         case INPUT_TURBO_MODE_CLASSIC_TOGGLE:
            return strlcpy(s,
                  msg_hash_to_str(MENU_ENUM_LABEL_VALUE_TURBO_MODE_CLASSIC_TOGGLE), len);
         case INPUT_TURBO_MODE_SINGLEBUTTON:
            return strlcpy(s,
                  msg_hash_to_str(MENU_ENUM_LABEL_VALUE_TURBO_MODE_SINGLEBUTTON), len);
         case INPUT_TURBO_MODE_SINGLEBUTTON_HOLD:
            return strlcpy(s,
                  msg_hash_to_str(MENU_ENUM_LABEL_VALUE_TURBO_MODE_SINGLEBUTTON_HOLD), len);
      }
   }
   return 0;
}
```

**Affichage dans le menu:**
```
Turbo Mode: Classic
Turbo Mode: Classic (Toggle)
Turbo Mode: Single Button (Toggle)
Turbo Mode: Single Button (Hold)
```

---

## 5. DISPLAYLIST CONFIGURATION

### Entrée menu (menu_displaylist.c:7398-7406)

```c
case DISPLAYLIST_INPUT_TURBO_FIRE_SETTINGS_LIST:
   {
      static const menu_displaylist_build_info_t build_list[] = {
         {MENU_ENUM_LABEL_INPUT_TURBO_ENABLE,         PARSE_ONLY_BOOL},
         {MENU_ENUM_LABEL_INPUT_TURBO_MODE,           PARSE_ONLY_UINT},
         {MENU_ENUM_LABEL_INPUT_TURBO_BIND,           PARSE_ONLY_INT},
         {MENU_ENUM_LABEL_INPUT_TURBO_BUTTON,         PARSE_ONLY_UINT},
         {MENU_ENUM_LABEL_INPUT_TURBO_ALLOW_DPAD,     PARSE_ONLY_BOOL},
         {MENU_ENUM_LABEL_INPUT_TURBO_PERIOD,         PARSE_ONLY_UINT},
         {MENU_ENUM_LABEL_INPUT_TURBO_DUTY_CYCLE,     PARSE_ONLY_UINT},
      };
      
      for (i = 0; i < ARRAY_SIZE(build_list); i++)
      {
         if (menu_displaylist_build_info(&build_list[i], list))
            count++;
      }
   }
   break;
```

### Intégration dans Input Settings (menu_displaylist.c:8108-8112)

```c
if (menu_entries_append(list,
         msg_hash_to_str(MENU_ENUM_LABEL_VALUE_INPUT_TURBO_FIRE_SETTINGS),
         msg_hash_to_str(MENU_ENUM_LABEL_INPUT_TURBO_FIRE_SETTINGS),
         MENU_ENUM_LABEL_INPUT_TURBO_FIRE_SETTINGS,
         MENU_SETTING_ACTION, 0, 0, NULL))
   count++;
```

---

## 6. INTERFACE VISUELLE

### Menu XMB (style PlayStation)

```
Settings
  > Input
      > Port 1 Controls
          > RetroPad Binds          →
          > Turbo Fire              →  ◄── ACCÈS TURBO
          > Hotkey Binds            →
          > Menu Controls           →
          
  [Quand on entre dans Turbo Fire]
  
  Turbo Fire
    ┌─────────────────────────────────────┐
    │ Turbo Fire               [ON]       │ ◄── Toggle principal
    │ Turbo Mode               Classic    │ ◄── Sélection mode
    │ Turbo Bind               None        │ ◄── Assigner bouton
    │ Turbo Button             B          │ ◄── Cible (Single mode)
    │ Turbo Allow D-Pad        [OFF]      │ ◄── Restrictions
    │ Turbo Period             6          │ ◄── Fréquence (frames)
    │ Turbo Duty Cycle         3          │ ◄── Temps ON (frames)
    └─────────────────────────────────────┘
```

### Menu Ozone (style moderne)

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  SETTINGS > INPUT > PORT 1 CONTROLS > TURBO FIRE       │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Turbo Fire                               [ON]   │  │
│  │  ───────────────────────────────────────────────  │  │
│  │  Disabled stops all turbo fire operations.      │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Turbo Mode                        Classic       │  │
│  │  ───────────────────────────────────────────────  │  │
│  │  Select the general behavior of turbo mode.     │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Turbo Period                            6       │  │
│  │  ───────────────────────────────────────────────  │  │
│  │  The period in frames when turbo-enabled        │  │
│  │  buttons are pressed.                            │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Menu MaterialUI (Android/Mobile)

```
Settings
  Input
    Port 1 Controls
      Turbo Fire  >
      
[Click pour ouvrir]

──────────────────────────────────────
  TURBO FIRE

  ⚡ Turbo Fire                  [ON]
  
  🔧 Turbo Mode              Classic
  
  🎮 Turbo Bind                 None
  
  🎯 Turbo Button                  B
  
  📊 Turbo Period                  6
  
  ⏱️ Turbo Duty Cycle              3
  
  🕹️ Turbo Allow D-Pad         [OFF]
──────────────────────────────────────
```

---

## 7. WORKFLOW UTILISATEUR

### Scénario 1: Configuration basique (Classic mode)

```
1. Settings → Input → Port 1 Controls → Turbo Fire
2. Turbo Fire: [ON]
3. Turbo Mode: Classic (défaut)
4. Turbo Bind: Assigner un bouton (ex: L2)
5. Sauvegarder et quitter
6. En jeu: Maintenir L2 + presser A = turbo sur A
```

### Scénario 2: Single Button Toggle

```
1. Settings → Input → Port 1 Controls → Turbo Fire
2. Turbo Fire: [ON]
3. Turbo Mode: Single Button (Toggle)
4. Turbo Bind: Assigner (ex: R2)
5. Turbo Button: B (bouton qui sera en turbo)
6. En jeu: Presser R2 = active turbo sur B
7. Presser R2 à nouveau = désactive turbo sur B
```

### Scénario 3: Ajustement de la fréquence

```
1. Turbo Fire → Turbo Period: 6 (défaut = 10 Hz)
2. Pour turbo plus rapide: Period = 3 (20 Hz)
3. Pour turbo plus lent: Period = 12 (5 Hz)
4. Duty Cycle: 0 (auto = 50%)
   Ou ajuster manuellement pour ratio différent
```

### Scénario 4: Turbo sur D-Pad (jeux shmup)

```
1. Turbo Fire → Turbo Allow D-Pad: [ON]
2. Mode: Classic Toggle
3. En jeu: Maintenir UP + presser Turbo = rapid UP presses
4. Utile pour: menu scrolling, shmups avec UP/DOWN fire
```

---

## 8. VALIDATION ET CONTRAINTES

### Ranges et limites

```c
// Turbo Mode
menu_settings_list_current_add_range(list, list_info, 
   0,                          // Min: Classic
   (INPUT_TURBO_MODE_LAST-1),  // Max: SingleButton Hold (3)
   1,                          // Step
   true,                       // Enforce enum
   true);                      // Wrap around

// Turbo Bind
menu_settings_list_current_add_range(list, list_info, 
   -1,                                // Min: None
   (RARCH_ANALOG_BIND_LIST_END-1),   // Max: Analog buttons (23)
   1,                                 // Step
   true,                              // Enforce range
   true);                             // Wrap

// Turbo Button
menu_settings_list_current_add_range(list, list_info, 
   0,                               // Min: B button
   (RARCH_FIRST_CUSTOM_BIND-1),    // Max: R3 (15)
   1,                               // Step
   true,                            // Enforce range
   true);                           // Wrap

// Turbo Period
menu_settings_list_current_add_range(list, list_info, 
   2,      // Min: 2 frames (très rapide)
   100,    // Max: 100 frames (très lent)
   1,      // Step
   true,   // Enforce range
   true);  // Wrap

// Turbo Duty Cycle
menu_settings_list_current_add_range(list, list_info, 
   0,      // Min: 0 (auto = period/2)
   100,    // Max: 100 frames
   1,      // Step
   true,   // Enforce range
   true);  // Wrap
```

### Validations automatiques

```c
// Dans input_driver.c:1526-1527
if (turbo_duty_cycle == 0)
   turbo_duty_cycle = turbo_period / 2;  // Auto 50%

// Si duty_cycle >= period, bouton toujours pressé
if (turbo_duty_cycle >= turbo_period)
   // Button never releases (always ON)
```

---

## 9. PERSISTANCE

### Sauvegarde dans retroarch.cfg

```ini
# Quand l'utilisateur change les settings, automatiquement sauvegardé:

input_turbo_enable = "true"
input_turbo_period = "6"
input_turbo_duty_cycle = "3"
input_turbo_mode = "0"
input_turbo_bind = "4"  # L2
input_turbo_button = "0"  # B
input_turbo_allow_dpad = "false"

# Per-player turbo binds
input_player1_turbo_btn = "4"  # Joypad button
input_player1_turbo = "nul"    # Keyboard
```

### Chargement au démarrage

```c
// Dans configuration.c:6243-6247
CONFIG_GET_INT_BASE(conf, settings, uints.input_turbo_mode, "input_turbo_mode");
CONFIG_GET_INT_BASE(conf, settings, ints.input_turbo_bind, "input_turbo_bind");
CONFIG_GET_INT_BASE(conf, settings, uints.input_turbo_button, "input_turbo_button");
CONFIG_GET_INT_BASE(conf, settings, uints.input_turbo_period, "input_turbo_period");
CONFIG_GET_INT_BASE(conf, settings, uints.input_turbo_duty_cycle, "input_turbo_duty_cycle");
```

---

## 10. HOTKEYS ET RACCOURCIS

### Hotkey: Toggle Turbo global

```c
// Défini dans config.def.keybinds.h:538-543
{
   NULL, NULL,
   AXIS_NONE, AXIS_NONE,
   MENU_ENUM_LABEL_VALUE_INPUT_META_TURBO_FIRE_TOGGLE,
   RETROK_UNKNOWN,                // Pas de binding par défaut
   RARCH_TURBO_FIRE_TOGGLE,       // ID hotkey
   NO_BTN, NO_BTN, 0,
   true
},
```

**Fonction:**
- Permet de toggle `input_turbo_enable` ON/OFF pendant le jeu
- Accessible via: Settings → Input → Hotkeys → Turbo Fire Toggle
- Pratique pour activer/désactiver turbo rapidement

### Per-player turbo bindings

```
Settings → Input → Port 1 Controls → RetroPad Binds → Turbo Enable

C'est le bouton physique qui active le turbo en mode Classic
(différent de input_turbo_bind qui est global)
```

---

## 11. MULTILINGUE

### Support complet

RetroArch supporte 30+ langues pour tous les labels turbo:

- **Anglais:** "Turbo Fire", "Turbo Period"
- **Français:** "Tir Turbo", "Période Turbo"
- **Espagnol:** "Disparo Turbo", "Período Turbo"
- **Allemand:** "Turbo-Feuer", "Turbo-Periode"
- **Japonais:** "ターボ射撃", "ターボ周期"
- **Chinois:** "连发", "连发周期"
- **Russe:** "Турбо-огонь", "Период турбо"

Tous définis dans: `intl/msg_hash_XX.h` (XX = code langue)

---

## 12. COMPARAISON: RetroArch vs RetroPlay

### RetroArch: Menu complet

```
✅ 7 options configurables
✅ 4 modes de turbo
✅ Help text détaillé pour chaque mode
✅ Ranges validés (2-100 frames)
✅ Sublabels explicatifs
✅ Hotkey toggle global
✅ Per-player bindings
✅ Sauvegarde persistante
✅ Multilingue (30+ langues)
```

### RetroPlay: Configuration overlay

```
✅ Flag simple dans .cfg: turbo=true
⚠️ Pas d'UI de configuration
⚠️ 1 seul mode (auto toggle)
⚠️ Fréquence fixe 30 Hz
⚠️ Pas de duty cycle configurable
⚠️ Pas de binding per-player
⚠️ Pas de hotkeys
```

---

## 13. RECOMMANDATIONS POUR RETROPLAY

### Option 1: Quick Menu minimal

```kotlin
// Ajouter un simple menu in-game
QuickMenuDialog(
   onDismiss = { ... }
) {
   SettingsSection("Turbo Settings") {
      
      // Toggle global
      SettingSwitch(
         title = "Turbo Enabled",
         checked = turboEnabled,
         onCheckedChange = { turboEnabled = it }
      )
      
      // Fréquence
      SettingSlider(
         title = "Turbo Frequency",
         value = turboFrequency, // 5-30 Hz
         range = 5f..30f,
         onValueChange = { turboFrequency = it },
         valueText = "$turboFrequency Hz"
      )
      
      // Mode (simplifié)
      SettingDropdown(
         title = "Turbo Mode",
         options = listOf("Auto Toggle", "Hold", "Classic"),
         selected = turboMode,
         onSelect = { turboMode = it }
      )
   }
}
```

### Option 2: Configuration par overlay

```cfg
# Dans les fichiers .cfg overlay
overlay0_desc42_turbo = "true"
overlay0_desc42_turbo_frequency = "10"  # Hz (compatible RetroArch)
overlay0_desc42_turbo_duty_cycle = "0.5"  # 50%
overlay0_desc42_turbo_mode = "auto_toggle"  # ou "hold", "classic"
```

### Option 3: Settings dédiés

```
RetroPlay Settings
  ├── Video
  ├── Audio
  ├── Controls
  │   └── Turbo Settings  ◄── Nouvelle section
  │       ├── Enable Turbo [ON/OFF]
  │       ├── Default Frequency [10 Hz]
  │       ├── Default Duty Cycle [50%]
  │       └── Default Mode [Auto Toggle]
  └── Advanced
```

---

## 14. IMPLÉMENTATION RECOMMANDÉE

### Phase 1: Configuration de base

```kotlin
data class TurboSettings(
   val enabled: Boolean = true,
   val frequency: Int = 10,  // Hz (RetroArch default)
   val dutyCycle: Float = 0.5f,  // 50%
   val allowDpad: Boolean = false
)

// SharedPreferences
class TurboPreferences(context: Context) {
   private val prefs = context.getSharedPreferences("turbo_settings", MODE_PRIVATE)
   
   var turboSettings: TurboSettings
      get() = TurboSettings(
         enabled = prefs.getBoolean("turbo_enabled", true),
         frequency = prefs.getInt("turbo_frequency", 10),
         dutyCycle = prefs.getFloat("turbo_duty_cycle", 0.5f),
         allowDpad = prefs.getBoolean("turbo_allow_dpad", false)
      )
      set(value) = prefs.edit {
         putBoolean("turbo_enabled", value.enabled)
         putInt("turbo_frequency", value.frequency)
         putFloat("turbo_duty_cycle", value.dutyCycle)
         putBoolean("turbo_allow_dpad", value.allowDpad)
      }
}
```

### Phase 2: UI Settings

```kotlin
@Composable
fun TurboSettingsScreen(
   settings: TurboSettings,
   onSettingsChange: (TurboSettings) -> Unit
) {
   Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      
      // Enable/Disable
      SwitchPreference(
         title = "Turbo Fire",
         summary = "Enable turbo functionality",
         checked = settings.enabled,
         onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) }
      )
      
      // Frequency slider
      SliderPreference(
         title = "Turbo Frequency",
         summary = "${settings.frequency} Hz (${1000/settings.frequency}ms cycle)",
         value = settings.frequency.toFloat(),
         valueRange = 5f..30f,
         steps = 24,  // 5-30 Hz par pas de 1
         enabled = settings.enabled,
         onValueChange = { onSettingsChange(settings.copy(frequency = it.toInt())) }
      )
      
      // Duty cycle slider
      SliderPreference(
         title = "Duty Cycle",
         summary = "${(settings.dutyCycle * 100).toInt()}% ON time",
         value = settings.dutyCycle,
         valueRange = 0.1f..0.9f,
         steps = 7,  // 10%, 20%, ..., 90%
         enabled = settings.enabled,
         onValueChange = { onSettingsChange(settings.copy(dutyCycle = it)) }
      )
      
      // Allow D-Pad
      SwitchPreference(
         title = "Allow D-Pad Turbo",
         summary = "Enable turbo on directional buttons",
         checked = settings.allowDpad,
         enabled = settings.enabled,
         onCheckedChange = { onSettingsChange(settings.copy(allowDpad = it)) }
      )
   }
}
```

### Phase 3: Quick Menu in-game

```kotlin
// Accessible pendant le jeu (pause menu)
@Composable
fun QuickTurboMenu(
   currentFrequency: Int,
   onFrequencyChange: (Int) -> Unit,
   onClose: () -> Unit
) {
   Dialog(onDismissRequest = onClose) {
      Card {
         Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick Turbo Adjust", style = MaterialTheme.typography.titleLarge)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Presets rapides
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
               QuickPresetButton("Slow\n5 Hz", 5, currentFrequency, onFrequencyChange)
               QuickPresetButton("Normal\n10 Hz", 10, currentFrequency, onFrequencyChange)
               QuickPresetButton("Fast\n15 Hz", 15, currentFrequency, onFrequencyChange)
               QuickPresetButton("Rapid\n30 Hz", 30, currentFrequency, onFrequencyChange)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
               Text("Close")
            }
         }
      }
   }
}
```

---

## 15. CONCLUSION

### Points clés de l'intégration menu RetroArch

1. **Accessibilité:** Menu dédié "Turbo Fire" dans Input settings
2. **Complétude:** 7 options configurables avec ranges validés
3. **Aide contextuelle:** Sublabels et help text pour chaque option
4. **Modes multiples:** 4 modes avec descriptions détaillées
5. **Persistance:** Sauvegarde auto dans retroarch.cfg
6. **Hotkeys:** Toggle global accessible en jeu
7. **Multilingue:** Support 30+ langues
8. **Validation:** Ranges et contraintes appliqués automatiquement

### Pour RetroPlay

**Minimum viable:**
- Ajouter TurboSettings dans app settings
- Fréquence configurable (5-30 Hz)
- Toggle ON/OFF global

**Idéal:**
- Quick menu in-game pour ajustement rapide
- Presets (Slow/Normal/Fast/Rapid)
- Configuration par overlay (.cfg)
- Hotkey toggle

**Le plus important:**
- **Adopter 10 Hz comme défaut** pour compatibilité RetroArch
- Permettre ajustement pour les power users
- Documenter clairement dans l'UI

---

**FIN DU DOCUMENT**

Méthodologie: Nos Rules - Recherche exhaustive dans les sources officielles RetroArch

