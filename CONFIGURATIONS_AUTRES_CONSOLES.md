# Configurations Disponibles pour les Autres Consoles

## Date: 2025-01-XX
## Basé sur: Audit N64 et ConsoleConfigActivity

---

## RÉSUMÉ

Ce document liste les fonctionnalités de configuration disponibles pour chaque console, similaires à ce qui existe pour N64 (extensions contrôleur, options core).

---

## 1. CONSOLES AVEC OPTIONS CORE DÉJÀ IMPLÉMENTÉES

### 1.1 PlayStation (PSX/PS1)

**Options disponibles dans ConsoleConfigActivity:**
- ✅ **D-Pad Mode** (`use_dpad`): Choisir entre Analog Sticks (DualShock) ou D-Pad uniquement
- ✅ **Internal Resolution** (`psx_resolution`): 1x, 2x, 4x, 8x
- ✅ **Texture Filtering** (`psx_texture_filtering`): On/Off
- ✅ **Dithering** (`psx_dithering`): On/Off

**Status:**
- ✅ UI dans `activity_console_config.xml` (lignes 630-689)
- ✅ Sauvegarde dans `console_config` (format: `psx_*`)
- ⚠️ **PROBLÈME:** Lecture depuis `PreferenceManager.getDefaultSharedPreferences()` au lieu de `console_config` dans les activités Compose
- ⚠️ **PROBLÈME:** Application non vérifiée dans RetroArch/Native

**Extensions contrôleur possibles:**
- ❌ Pas d'extensions comme N64
- ✅ Mais configuration DualShock vs D-Pad (déjà implémentée)

**Recommandation:**
- Corriger la lecture depuis `console_config` (comme pour N64)
- Vérifier l'application des options core (resolution, filtering, dithering)

---

### 1.2 Super Nintendo (SNES)

**Options disponibles dans ConsoleConfigActivity:**
- ✅ **Blend Mode** (`snes_blend_mode`): None, Merge, Additive, Subtractive
- ✅ **Hi-Res Mode** (`snes_hires`): 256x239 (On/Off)

**Status:**
- ✅ UI dans `activity_console_config.xml` (lignes 691-738)
- ✅ Sauvegarde dans `console_config` (format: `snes_*`)
- ⚠️ **PROBLÈME:** Lecture depuis `PreferenceManager.getDefaultSharedPreferences()` au lieu de `console_config` dans les activités Compose
- ⚠️ **PROBLÈME:** Application non vérifiée dans RetroArch/Native

**Extensions contrôleur possibles:**
- ❌ Pas d'extensions comme N64
- ✅ Mais support Super Scope (lightgun) - déjà géré via Zapper system

**Recommandation:**
- Corriger la lecture depuis `console_config` (comme pour N64)
- Vérifier l'application des options core (blend mode, hi-res)

---

## 2. CONSOLES SANS OPTIONS CORE SPÉCIFIQUES

### 2.1 Nintendo Entertainment System (NES)

**Options disponibles:**
- ❌ Pas d'options core spécifiques dans ConsoleConfigActivity
- ✅ Support Zapper (lightgun) - déjà implémenté

**Extensions contrôleur possibles:**
- ✅ **Zapper** (lightgun) - DÉJÀ IMPLÉMENTÉ
- ✅ **Arkanoid Paddle** - Pourrait être ajouté (via `setControllerType()`)

**Recommandation:**
- Ajouter configuration Arkanoid Paddle si nécessaire
- Pas d'options core critiques (NES est simple)

---

### 2.2 Game Boy / Game Boy Color / Game Boy Advance

**Options disponibles:**
- ❌ Pas d'options core spécifiques dans ConsoleConfigActivity

**Extensions contrôleur possibles:**
- ❌ Pas d'extensions physiques
- ✅ Mais support Link Cable (GBA) - géré par le core, pas besoin de config

**Recommandation:**
- Pas de configuration spéciale nécessaire

---

### 2.3 Sega Genesis / Mega Drive

**Options disponibles:**
- ❌ Pas d'options core spécifiques dans ConsoleConfigActivity

**Extensions contrôleur possibles:**
- ✅ **6-Button Controller** vs **3-Button Controller** - DÉJÀ GÉRÉ via GamePadLayoutManager
- ✅ **Sega CD** - Géré automatiquement par le core
- ✅ **32X** - Géré automatiquement par le core

**Recommandation:**
- Pas de configuration spéciale nécessaire (déjà géré via layouts)

---

### 2.4 Sega Saturn

**Options disponibles:**
- ❌ Pas d'options core spécifiques dans ConsoleConfigActivity

**Extensions contrôleur possibles:**
- ✅ **3D Control Pad** (analog stick) - Pourrait être configuré via `setControllerType()`
- ✅ **Multi-Tap** (4 joueurs) - Géré automatiquement par le core

**Recommandation:**
- Ajouter configuration 3D Control Pad si nécessaire

---

### 2.5 Sega Dreamcast

**Options disponibles:**
- ❌ Pas d'options core spécifiques dans ConsoleConfigActivity

**Extensions contrôleur possibles:**
- ✅ **VMU** (Visual Memory Unit) - Géré automatiquement par le core
- ✅ **Rumble Pack** - Géré automatiquement par le core

**Recommandation:**
- Pas de configuration spéciale nécessaire

---

### 2.6 PlayStation Portable (PSP)

**Options disponibles:**
- ✅ **Custom Tabs** (`use_custom_tabs`): Pour SharedArrayBuffer support (déjà implémenté)
- ❌ Pas d'options core spécifiques dans ConsoleConfigActivity

**Extensions contrôleur possibles:**
- ❌ Pas d'extensions physiques

**Recommandation:**
- Pas de configuration spéciale nécessaire

---

## 3. CONSOLES AVEC POTENTIEL D'EXTENSIONS

### 3.1 Nintendo DS

**Extensions contrôleur possibles:**
- ✅ **Rumble Pak** - Pourrait être configuré (similaire à N64)
- ✅ **Memory Expansion Pak** - Géré automatiquement

**Recommandation:**
- Ajouter configuration Rumble Pak si nécessaire (similaire à N64)

---

### 3.2 GameCube (si supporté)

**Extensions contrôleur possibles:**
- ✅ **Memory Card** - Géré automatiquement
- ✅ **Rumble** - Géré automatiquement
- ✅ **WaveBird** (wireless) - Géré automatiquement

**Recommandation:**
- Pas de configuration spéciale nécessaire

---

## 4. PATTERN DE CONFIGURATION (basé sur N64)

### 4.1 Extensions Contrôleur

**Pattern:**
```kotlin
// 1. Lire depuis console_config
val prefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
val prefix = "{console}_"

// 2. Mapping spinner positions → IDs Libretro
val extensionValues = intArrayOf(id1, id2, id3)

// 3. Appliquer pour chaque port
for (port in 0..maxPorts) {
    val position = prefs.getInt("${prefix}extension_port${port + 1}", 0)
    if (position >= 0 && position < extensionValues.size) {
        val extensionId = extensionValues[position]
        retroView.setControllerType(port, extensionId)
    }
}
```

**Consoles applicables:**
- ✅ N64 (déjà implémenté)
- ⚠️ Saturn (3D Control Pad)
- ⚠️ DS (Rumble Pak)

---

### 4.2 Options Core

**Pattern:**
```kotlin
// 1. Lire depuis console_config
val consoleConfigPrefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
val consolePrefix = "{console}_"

// 2. Lire les options
val option1 = consoleConfigPrefs.getInt("${consolePrefix}option1", 0)
val option2 = consoleConfigPrefs.getBoolean("${consolePrefix}option2", false)

// 3. Appliquer via Variable dans create()
val variables = arrayOf(
    Variable("core-option1", option1.toString()),
    Variable("core-option2", if (option2) "enabled" else "disabled")
)
```

**Consoles applicables:**
- ✅ N64 (déjà implémenté)
- ⚠️ PSX (déjà dans UI, mais pas appliqué)
- ⚠️ SNES (déjà dans UI, mais pas appliqué)

---

## 5. RECOMMANDATIONS PAR PRIORITÉ

### Priorité 1 (CRITIQUE): Corriger PSX et SNES

**Actions:**
1. Corriger la lecture depuis `console_config` dans RetroArch/Native (comme pour N64)
2. Vérifier l'application des options core (resolution, filtering, dithering pour PSX; blend mode, hi-res pour SNES)

**Impact:**
- Les utilisateurs configurent ces options mais elles ne sont pas appliquées

---

### Priorité 2: Ajouter extensions pour autres consoles

**Consoles candidates:**
1. **Saturn** - 3D Control Pad (analog stick)
2. **DS** - Rumble Pak

**Pattern:**
- Suivre le même pattern que N64
- Ajouter UI dans `activity_console_config.xml`
- Implémenter lecture/application dans RetroArch/Native

---

### Priorité 3: Options core pour autres consoles

**Consoles candidates:**
1. **GBA** - Color correction, frame blending
2. **Genesis** - 6-button vs 3-button (déjà géré via layouts)
3. **Saturn** - Internal resolution, texture filtering

**Pattern:**
- Suivre le même pattern que N64/PSX/SNES
- Ajouter UI dans `activity_console_config.xml`
- Implémenter lecture/application dans RetroArch/Native

---

## 6. TABLEAU RÉCAPITULATIF

| Console | Extensions Contrôleur | Options Core | Status | Action Requise |
|---------|----------------------|--------------|--------|----------------|
| **N64** | ✅ Controller Pak, Rumble Pak, Transfer Pak | ✅ Resolution, AA, Bilinear | ✅ Implémenté | Aucune |
| **PSX** | ❌ (mais DualShock vs D-Pad) | ✅ Resolution, Filtering, Dithering | ⚠️ UI existe, pas appliqué | Corriger lecture `console_config` |
| **SNES** | ❌ (mais Super Scope) | ✅ Blend Mode, Hi-Res | ⚠️ UI existe, pas appliqué | Corriger lecture `console_config` |
| **NES** | ✅ Zapper (implémenté) | ❌ | ✅ OK | Aucune |
| **GB/GBC/GBA** | ❌ | ❌ | ✅ OK | Aucune |
| **Genesis** | ✅ 6-button (via layouts) | ❌ | ✅ OK | Aucune |
| **Saturn** | ⚠️ 3D Control Pad | ⚠️ Resolution, Filtering | ❌ Non implémenté | Ajouter si nécessaire |
| **Dreamcast** | ❌ (VMU auto) | ❌ | ✅ OK | Aucune |
| **PSP** | ❌ | ❌ | ✅ OK | Aucune |
| **DS** | ⚠️ Rumble Pak | ❌ | ❌ Non implémenté | Ajouter si nécessaire |

---

## 7. CODE EXEMPLE: Application PSX (à implémenter)

```kotlin
// Dans RetroArchEmulatorActivity.kt et NativeComposeEmulatorActivity.kt
when (console) {
    "psx", "ps1", "playstation" -> {
        // Lire depuis console_config (comme pour N64)
        val consoleConfigPrefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
        val consolePrefix = "psx_"
        
        val resolution = consoleConfigPrefs.getInt("${consolePrefix}psx_resolution", 0)
        val textureFiltering = consoleConfigPrefs.getBoolean("${consolePrefix}psx_texture_filtering", true)
        val dithering = consoleConfigPrefs.getBoolean("${consolePrefix}psx_dithering", true)
        
        // Appliquer via Variable
        val psxVariables = arrayOf(
            Variable("beetle_psx_internal_resolution", when (resolution) {
                0 -> "1x"
                1 -> "2x"
                2 -> "4x"
                3 -> "8x"
                else -> "1x"
            }),
            Variable("beetle_psx_texture_filtering", if (textureFiltering) "enabled" else "disabled"),
            Variable("beetle_psx_dithering", if (dithering) "enabled" else "disabled")
        )
        
        variables = psxVariables
    }
}
```

---

## CONCLUSION

**Status actuel:**
- ✅ N64: Complètement implémenté et fonctionnel
- ⚠️ PSX: UI existe, mais pas appliqué (même problème que N64 avant correction)
- ⚠️ SNES: UI existe, mais pas appliqué (même problème que N64 avant correction)
- ✅ Autres consoles: Pas d'options spéciales nécessaires

**Action immédiate:**
- Appliquer les mêmes corrections que pour N64 à PSX et SNES


