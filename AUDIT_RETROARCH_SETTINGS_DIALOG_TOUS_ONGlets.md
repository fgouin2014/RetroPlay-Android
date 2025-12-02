# Audit Parsing RetroArch Settings Dialog - Tous les Onglets

## CONTEXTE

`RetroArchSettingsDialog` a **3 onglets**:
1. **Onglet 0: "Overlays"** - Configuration des overlays RetroArch
2. **Onglet 1: "Advanced"** - Paramètres avancés des overlays (sensitivity, opacity, lightgun, mouse)
3. **Onglet 2: "General"** - Paramètres généraux RetroArch (shaders, video, audio, advanced, input)

---

## ONGLET 0: OVERLAYS

**Fichier:** `RetroArchSettingsDialog.kt` lignes 89-117

**Chargement:**
```kotlin
val currentOverlayPref = remember { OverlayPreferenceManager.load(prefs, console) }
```

**Parsing:** Via `OverlayPreferenceManager.load()` (déjà vérifié dans `AUDIT_GAMEPAD_SETTINGS_DIALOG_PARSING.md`)

**Valeurs par défaut:**
- `selectedOverlay`: `overlayPackages.firstOrNull().orEmpty()` ✅
- `selectedLandscapeLayout`: `"landscape-A"` ✅
- `selectedPortraitLayout`: `"portrait-A"` ✅
- `autoRotate`: `true` ✅
- `swapAnalogSticks`: `false` ✅
- `invertAnalogLeftY`: `false` ✅
- `invertAnalogRightY`: `false` ✅
- `scale`: `1.0f` ✅
- `xOffset`, `yOffset`, `xSeparation`, `ySeparation`: `0.0f` ✅

**Analyse:**
- ✅ Utilise `OverlayPreferenceManager.load()` (déjà vérifié)
- ✅ Valeurs par défaut cohérentes
- ✅ Pas de problème de parsing (SharedPreferences automatique)

---

## ONGLET 1: ADVANCED

**Fichier:** `RetroArchSettingsDialog.kt` lignes 144-152

**Chargement:**
```kotlin
var advancedSettings by remember(orientation, useSameSettings) { 
    mutableStateOf(
        if (useSameSettings) {
            OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                ?: OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
        } else {
            OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
        }
    )
}
```

**Parsing:** Via `OverlayPreferenceManager.loadAdvancedSettings()` (déjà vérifié dans `AUDIT_GAMEPAD_SETTINGS_DIALOG_PARSING.md`)

**Analyse:**
- ✅ Utilise `OverlayPreferenceManager.loadAdvancedSettings()` (déjà vérifié)
- ✅ Gestion per-orientation (landscape/portrait) ✅
- ✅ Fallback global → landscape si `useSameSettings` ✅
- ⚠️ Gestion `ClassCastException` incomplète (seulement `mouseSwipeThreshold`) - déjà documenté

---

## ONGLET 2: GENERAL

**Fichier:** `RetroArchSettingsDialog.kt` lignes 1504-2067

### 2.1 Shaders

**Chargement:**
```kotlin
var selectedShaderName by remember { 
    mutableStateOf(
        prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT"
    )
}
```

**Analyse:**
- ✅ Default: `"DEFAULT"` ✅
- ✅ Pas de problème de parsing (SharedPreferences automatique)

---

### 2.2 Video Settings

**Chargement:**
```kotlin
// VSync
var vsyncEnabled by remember { 
    mutableStateOf(
        RetroPlayConfigManager.loadConfig().videoVsync
    )
}

// Aspect Ratio
var selectedAspectRatio by remember { 
    mutableStateOf(
        prefs.getString("emulation_video_aspect_ratio", "AUTO") ?: "AUTO"
    )
}
```

**Analyse:**
- ✅ VSync: Via `RetroPlayConfigManager.loadConfig()` (fichier `.cfg` texte)
- ✅ Aspect Ratio: Default `"AUTO"` ✅
- ⚠️ **MÉLANGE:** VSync depuis `.cfg` (RetroPlayConfigManager), Aspect Ratio depuis SharedPreferences

---

### 2.3 Audio Settings

**Chargement:**
```kotlin
var audioVolume by remember { 
    mutableStateOf(prefs.getFloat("emulation_audio_volume", 1.0f)) 
}
var audioMuted by remember { 
    mutableStateOf(prefs.getBoolean("emulation_audio_muted", false)) 
}
var lowLatencyAudio by remember { 
    mutableStateOf(
        prefs.getBoolean("emulation_audio_low_latency", false)
    )
}
```

**Analyse:**
- ✅ Defaults: `1.0f`, `false`, `false` ✅
- ✅ Pas de problème de parsing (SharedPreferences automatique)

---

### 2.4 Advanced Settings

**Chargement:**
```kotlin
// Fast Forward Ratio
var fastForwardRatio by remember { 
    mutableStateOf(prefs.getFloat("emulation_fast_forward_ratio", 2.0f)) 
}

// Rewind
var rewindEnabled by remember { 
    mutableStateOf(
        RetroPlayConfigManager.loadConfig().rewindEnable
    )
}

// Run-Ahead
var runAheadEnabled by remember { 
    mutableStateOf(
        RetroPlayConfigManager.loadConfig().runAheadEnabled
    )
}
var runAheadFrames by remember { 
    mutableStateOf(
        RetroPlayConfigManager.loadConfig().runAheadFrames.toFloat()
    )
}
```

**Analyse:**
- ✅ Fast Forward: Default `2.0f` ✅
- ⚠️ **MÉLANGE:** Rewind/Run-Ahead depuis `.cfg` (RetroPlayConfigManager), Fast Forward depuis SharedPreferences

---

### 2.5 Controller Ports

**Chargement:**
```kotlin
var selectedControllerType by remember { 
    mutableStateOf(
        prefs.getInt("controller_port_${console}_port${port}", -1) // -1 = Auto
    )
}
```

**Analyse:**
- ✅ Default: `-1` (Auto) ✅
- ✅ Pas de problème de parsing (SharedPreferences automatique)

---

## PROBLÈME IDENTIFIÉ: Mélange de sources de configuration

### Impact

**Sévérité:** MOYEN

**Problème:**
L'onglet "General" mélange deux sources de configuration:
1. **SharedPreferences** (`compose_gamepad_settings`):
   - Shader preset
   - Aspect ratio
   - Audio volume/muted/low latency
   - Fast forward ratio
   - Controller ports

2. **Fichier `.cfg`** (`RetroPlayConfigManager`):
   - VSync
   - Rewind
   - Run-Ahead

**Conséquence:**
- Incohérence potentielle si les deux sources ne sont pas synchronisées
- Difficile de savoir où chercher/modifier un paramètre
- Risque de désynchronisation entre les deux sources

---

## VÉRIFICATION: Parsing RetroPlayConfigManager

**Fichier:** `RetroPlayConfigManager.kt` lignes 178-196

**Parsing:**
```kotlin
val parts = trimmed.split("=", limit = 2)
if (parts.size == 2) {
    val key = parts[0].trim()
    val value = parts[1].trim().removeSurrounding("\"")
    configMap[key] = value
}
```

**Analyse:**
- ✅ Parsing simple `key = value` (pas de tokenize complexe)
- ✅ Gestion des guillemets (`removeSurrounding("\"")`)
- ✅ Gestion des commentaires (`startsWith("#")`)
- ✅ Gestion des lignes vides
- ✅ Try/catch avec fallback vers defaults (ligne 152-155)

**Valeurs par défaut:**
- ✅ Toutes les valeurs ont des defaults via `?: default`
- ✅ Gestion d'erreur: retourne `RetroPlayConfig()` (defaults) si parsing échoue

**Comparaison avec RetroArch:**
- ⚠️ **DIFFÉRENCE:** RetroArch utilise `config_get_*()` qui gère automatiquement les types
- ✅ **SIMILARITÉ:** Parsing simple `key = value` comme RetroArch

---

## CONCLUSION

### Onglets vérifiés:

1. ✅ **Onglet 0: Overlays** - Parsing OK (via `OverlayPreferenceManager.load()`)
2. ✅ **Onglet 1: Advanced** - Parsing OK (via `OverlayPreferenceManager.loadAdvancedSettings()`)
3. ⚠️ **Onglet 2: General** - Parsing OK mais **mélange de sources** (SharedPreferences + `.cfg`)

### Problèmes identifiés:

1. **MOYEN:** Mélange SharedPreferences + `.cfg` dans l'onglet General
   - Impact: Incohérence potentielle, difficile à maintenir
   - Recommandation: Unifier vers une seule source (SharedPreferences ou `.cfg`)

2. **BAS:** Parsing RetroPlayConfigManager simple mais fonctionnel
   - Pas de problème de parsing identifié
   - Gestion d'erreur présente (try/catch avec defaults)

---

**Dernière mise à jour:** 2025-01-XX - Audit tous onglets terminé

