# Plan - RetroArch Settings Dialog (Amélioration)

**Date:** 2025-01-XX  
**Priorité:** P3 (Court Terme - Améliorations UX)  
**Temps estimé:** 2-3 jours

---

## 🎯 OBJECTIF

Étendre `RetroArchSettingsDialog` pour inclure TOUTES les options RetroArch dans un seul endroit, créant un hub centralisé pour la configuration RetroArch.

**Actuellement:**
- `RetroArchSettingsDialog` → Gère uniquement les overlays
- `EmulationSettingsDialog` → Gère Core Options + Fast Forward + Audio Volume

**Objectif:**
- `RetroArchSettingsDialog` → Hub complet: Overlays + Shaders + Video + Audio + Input + Advanced

---

## 📊 ÉTAT ACTUEL

### Ce qui existe déjà dans RetroArchSettingsDialog
- ✅ Sélection overlay package
- ✅ Sélection layouts (landscape/portrait)
- ✅ Auto-rotate
- ✅ Analog options (swap, invert)
- ✅ Position & Scale (scale, offsets, separation)

### Ce qui manque (géré ailleurs ou pas accessible)
- ❌ Shader Settings (géré via cycle shader dans QuickMenu)
- ❌ Video Settings (VSync, aspect ratio)
- ❌ Audio Settings (volume, latency - partiellement dans EmulationSettingsDialog)
- ❌ Input Settings (hotkeys, remapping)
- ❌ Advanced Settings (rewind, run-ahead, fast forward ratio)

---

## 🏗️ ARCHITECTURE PROPOSÉE

### Structure du Dialog (avec onglets/sections)

```
RetroArch Settings Dialog
├── Overlays (existant)
│   ├── Overlay Package Selection
│   ├── Layout Selection
│   ├── Analog Options
│   └── Position & Scale
│
├── Shaders (NOUVEAU)
│   ├── Shader Preset Selection
│   └── Shader Parameters (si applicable)
│
├── Video (NOUVEAU)
│   ├── VSync Toggle
│   ├── Aspect Ratio
│   └── Video Filter
│
├── Audio (NOUVEAU)
│   ├── Volume Slider
│   ├── Mute Toggle
│   └── Low Latency Audio
│
├── Input (NOUVEAU)
│   ├── Hotkeys Configuration
│   └── Input Remapping
│
└── Advanced (NOUVEAU)
    ├── Fast Forward Ratio
    ├── Rewind Settings
    ├── Run-Ahead Settings
    └── Debug Mode
```

### Option 1: Sections avec Divider (Recommandé)
- Garder le scroll vertical
- Utiliser `HorizontalDivider` pour séparer les sections
- Similaire à `AdvancedOverlaySettingsDialog`

### Option 2: Onglets (Alternative)
- Utiliser `TabRow` avec `Tab`
- Plus organisé mais nécessite navigation

**Recommandation:** Option 1 (sections avec divider) pour cohérence avec les autres dialogs.

---

## 📝 IMPLÉMENTATION DÉTAILLÉE

### Section 1: Shaders

**Fichier:** `app/src/main/java/com/retroplay/shader/ShaderManager.kt` (existe déjà)

**Options:**
- Shader Preset: Dropdown/List avec tous les shaders disponibles
- Liste depuis `ShaderManager.getAvailableShaders()`
- Sauvegarder dans `prefs.putString("emulation_shader_preset", shaderName)`

**Code:**
```kotlin
// Dans RetroArchSettingsDialog
var selectedShader by remember { 
    mutableStateOf(
        prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT"
    )
}

// Section Shaders
HorizontalDivider(color = Color(0xFF444444))
Text("Shaders", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

val availableShaders = remember { ShaderManager.getAvailableShaders() }
DropdownMenu(...) {
    availableShaders.forEach { shader ->
        DropdownMenuItem(
            text = { Text(shader.displayName) },
            onClick = {
                selectedShader = shader.name
                prefs.edit().putString("emulation_shader_preset", shader.name).apply()
                // Appliquer au retroView si possible
            }
        )
    }
}
```

### Section 2: Video Settings

**Options:**
- VSync: Switch (depuis `retroPlayConfig.videoVsync`)
- Aspect Ratio: Dropdown (Auto, 4:3, 16:9, etc.)
- Video Filter: Dropdown (si applicable)

**Code:**
```kotlin
var vsyncEnabled by remember { 
    mutableStateOf(
        RetroPlayConfigManager.loadConfig().videoVsync
    )
}

SwitchRow(
    title = "VSync",
    subtitle = "Vertical synchronization",
    checked = vsyncEnabled,
    onCheckedChange = { 
        vsyncEnabled = it
        val config = RetroPlayConfigManager.loadConfig()
        RetroPlayConfigManager.saveConfig(config.copy(videoVsync = it))
    }
)
```

### Section 3: Audio Settings

**Options:**
- Volume: Slider (0.0f - 1.0f)
- Mute: Switch (déjà géré via `audioMuted`)
- Low Latency Audio: Switch (depuis `preferLowLatencyAudio`)

**Code:**
```kotlin
var audioVolume by remember { 
    mutableStateOf(prefs.getFloat("emulation_audio_volume", 1.0f)) 
}

SliderWithLabel(
    label = "Audio Volume",
    value = audioVolume,
    onValueChange = { 
        audioVolume = it
        prefs.edit().putFloat("emulation_audio_volume", it).apply()
        // Appliquer au retroView
    },
    valueRange = 0.0f..1.0f,
    displayValue = "${(audioVolume * 100).toInt()}%"
)
```

### Section 4: Input Settings

**Options:**
- Hotkeys: Liste des hotkeys configurables
- Input Remapping: (Optionnel - peut être reporté)

**Hotkeys à inclure:**
- Fast Forward
- Rewind
- Save State
- Load State
- Menu
- Screenshot

**Code:**
```kotlin
// Hotkeys section
Text("Hotkeys", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

val hotkeys = listOf(
    "Fast Forward" to "emulation_hotkey_fast_forward",
    "Rewind" to "emulation_hotkey_rewind",
    "Save State" to "emulation_hotkey_save_state",
    "Load State" to "emulation_hotkey_load_state",
    // ...
)

hotkeys.forEach { (name, key) ->
    Row {
        Text(name, color = Color.White)
        // Button pour configurer le hotkey
        Button(onClick = { /* Open key capture dialog */ }) {
            Text("Configure")
        }
    }
}
```

### Section 5: Advanced Settings

**Options:**
- Fast Forward Ratio: Slider (1x - 10x)
- Rewind: Switch + Buffer Size
- Run-Ahead: Switch + Frames (si disponible)
- Debug Mode: Switch (déjà géré via `debugModeState`)

**Code:**
```kotlin
var fastForwardRatio by remember { 
    mutableStateOf(prefs.getFloat("emulation_fast_forward_ratio", 2.0f)) 
}

SliderWithLabel(
    label = "Fast Forward Ratio",
    value = fastForwardRatio,
    onValueChange = { 
        fastForwardRatio = it
        prefs.edit().putFloat("emulation_fast_forward_ratio", it).apply()
    },
    valueRange = 1.0f..10.0f,
    displayValue = "${fastForwardRatio.toInt()}x"
)

// Rewind
var rewindEnabled by remember { 
    mutableStateOf(RetroPlayConfigManager.loadConfig().rewindEnable)
}

SwitchRow(
    title = "Rewind",
    subtitle = "Enable rewind functionality",
    checked = rewindEnabled,
    onCheckedChange = { 
        rewindEnabled = it
        val config = RetroPlayConfigManager.loadConfig()
        RetroPlayConfigManager.saveConfig(config.copy(rewindEnable = it))
    }
)
```

---

## 🔄 INTÉGRATION

### Modifier RetroArchSettingsDialog

**Fichier:** `app/src/main/java/com/retroplay/RetroArchSettingsDialog.kt`

**Changements:**
1. Ajouter les nouvelles sections après la section "Position & Scale"
2. Utiliser le même pattern que les sections existantes
3. Sauvegarder dans SharedPreferences ou RetroPlayConfigManager selon le setting

### Passer les callbacks nécessaires

**Dans RetroArchEmulatorActivity:**
```kotlin
RetroArchSettingsDialog(
    console = console,
    onDismiss = { showGamePadSettings.value = false },
    context = retroView.context,
    prefs = prefs,
    onLoadCustomCfg = onLoadCustomCfg,
    debugModeState = debugModeState,
    // NOUVEAU callbacks
    onShaderChanged = { shaderName ->
        currentShader.value = ShaderManager.fromString(shaderName)
        // Appliquer shader au retroView
    },
    onFastForwardRatioChanged = { ratio ->
        fastForwardRatio = ratio.toInt()
        // Appliquer au retroView si fast forward actif
    },
    // ...
)
```

---

## ✅ TESTS

### Test 1: Shader Selection
- Sélectionner différents shaders
- Vérifier que le shader est appliqué au retroView
- Vérifier que la préférence est sauvegardée

### Test 2: Video Settings
- Toggle VSync
- Vérifier que le setting est sauvegardé
- Vérifier que le setting est appliqué (si API disponible)

### Test 3: Audio Settings
- Modifier le volume
- Toggle mute
- Vérifier que les settings sont appliqués

### Test 4: Advanced Settings
- Modifier Fast Forward Ratio
- Toggle Rewind
- Vérifier que les settings sont sauvegardés et appliqués

---

## 📋 CHECKLIST

- [ ] Ajouter section Shaders avec sélection preset
- [ ] Ajouter section Video (VSync, aspect ratio)
- [ ] Ajouter section Audio (volume, mute, low latency)
- [ ] Ajouter section Input (hotkeys)
- [ ] Ajouter section Advanced (fast forward, rewind, run-ahead, debug)
- [ ] Intégrer callbacks dans RetroArchEmulatorActivity
- [ ] Tester toutes les sections
- [ ] Valider sauvegarde des settings
- [ ] Documenter les nouvelles options

---

## 🔄 PROCHAINES ÉTAPES

Après cette implémentation:
1. Preview Overlay (1-2 jours)
2. N64 Extensions Configuration (1-2 jours)
3. Autres améliorations UX

---

**Note:** Ce dialog devient le hub centralisé pour TOUTES les options RetroArch, complémentant `EmulationSettingsDialog` qui gère les Core Options spécifiques au core.


