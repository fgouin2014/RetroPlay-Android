# 🎯 QUICK WINS ROADMAP - RetroPlay Audit Suite

**Date:** 31 octobre 2025  
**Source:** Audit complet + Analyse CORE_OPTIONS_EMULATION  
**Objectif:** Implémenter 4 features à fort impact en 8-13h total

---

## 📊 VUE D'ENSEMBLE

### Les 4 Quick Wins Identifiés

| Feature | Impact | Complexité | Durée | API Ready |
|---------|--------|------------|-------|-----------|
| **1. Fast Forward** | ⭐⭐⭐⭐⭐ | Faible | 2-3h | ✅ setFrameSpeed() |
| **2. Audio Mute** | ⭐⭐⭐⭐ | Très faible | 1-2h | ✅ retroView.audioEnabled |
| **3. Auto-Save States** | ⭐⭐⭐⭐⭐ | Moyenne | 3-5h | ✅ serializeState() |
| **4. Shader Selection** | ⭐⭐⭐ | Faible | 2-3h | ✅ retroView.shader |

**TOTAL:** 8-13h pour 4 features majeures

---

## 🚀 QUICK WIN #1: FAST FORWARD

### Pourquoi c'est un Quick Win

✅ **API déjà disponible** - `LibretroDroid.setFrameSpeed(speed: Int)`  
✅ **Pas de refactoring** - Juste ajouter UI + hotkey  
✅ **Impact énorme** - Accelerer grind, intro, cutscenes  
✅ **Simple à tester** - Résultat visible immédiatement  

### Implémentation

#### 1. Ajouter Property dans Activity (30 min)

```kotlin
// RetroArchEmulatorActivity.kt
private var fastForwardActive = false

private fun toggleFastForward() {
    fastForwardActive = !fastForwardActive
    val speed = if (fastForwardActive) 2 else 1  // 2x ou normal
    retroView.frameSpeed = speed
    Log.i(TAG, "[FAST_FORWARD] ${if (fastForwardActive) "ENABLED (2x)" else "DISABLED (1x)"}")
    
    // Toast optionnel
    runOnUiThread {
        Toast.makeText(this, 
            if (fastForwardActive) "Fast Forward: 2x" else "Normal Speed", 
            Toast.LENGTH_SHORT
        ).show()
    }
}
```

#### 2. Ajouter Hotkey (30 min)

```kotlin
// Dans ComposeEmulatorScreen - onHotkey callback
"fast_forward_toggle" -> {
    toggleFastForward()
}
"fast_forward_hold" -> {
    // Hold version: fast while holding button
    retroView.frameSpeed = 2
}
```

#### 3. Ajouter Bouton dans QuickMenu (1h)

```kotlin
// QuickMenuDialog.kt
Button(
    onClick = {
        onHotkey("fast_forward_toggle")
        onDismiss()
    }
) {
    Icon(painter = painterResource(...), contentDescription = "Fast Forward")
    Spacer(modifier = Modifier.width(8.dp))
    Text(if (fastForwardActive) "Normal Speed" else "Fast Forward (2x)")
}
```

#### 4. Ajouter à Advanced Settings (30 min)

```kotlin
// AdvancedOverlaySettingsDialog.kt
// Section: "Emulation Speed"
var fastForwardRatio by remember { 
    mutableStateOf(prefs.getInt("emulation_fast_forward_ratio", 2))
}

Slider(
    value = fastForwardRatio.toFloat(),
    onValueChange = { fastForwardRatio = it.toInt() },
    valueRange = 2f..4f,  // 2x, 3x, 4x
    steps = 2
)
Text("Fast Forward: ${fastForwardRatio}x")
```

### Résultat Attendu

- ✅ Hotkey L2 + Right = Fast Forward toggle
- ✅ Bouton dans QuickMenu
- ✅ Setting pour ratio (2x/3x/4x)
- ✅ Toast informatif
- ✅ Logs clairs

**Durée:** 2-3h | **Impact:** ⭐⭐⭐⭐⭐

---

## 🔇 QUICK WIN #2: AUDIO MUTE TOGGLE

### Pourquoi c'est un Quick Win

✅ **Property déjà disponible** - `retroView.audioEnabled`  
✅ **Implémentation triviale** - 1 boolean toggle  
✅ **Impact UX** - Jouer sans son (transports, nuit)  
✅ **Pas de side effects** - Juste mute/unmute  

### Implémentation

#### 1. Ajouter Property (15 min)

```kotlin
// RetroArchEmulatorActivity.kt
private var audioMuted = false

private fun toggleAudioMute() {
    audioMuted = !audioMuted
    retroView.audioEnabled = !audioMuted
    Log.i(TAG, "[AUDIO] ${if (audioMuted) "MUTED" else "UNMUTED"}")
}
```

#### 2. Ajouter Hotkey (15 min)

```kotlin
"audio_mute_toggle" -> {
    toggleAudioMute()
}
```

#### 3. Bouton dans QuickMenu (30 min)

```kotlin
// QuickMenuDialog.kt
Button(
    onClick = {
        onHotkey("audio_mute_toggle")
    },
    colors = ButtonDefaults.buttonColors(
        containerColor = if (audioMuted) Color.Red else Color(0xFF424242)
    )
) {
    Icon(
        painter = painterResource(if (audioMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_on),
        contentDescription = "Audio"
    )
    Text(if (audioMuted) "Unmute" else "Mute")
}
```

#### 4. Sauvegarder State (15 min)

```kotlin
// Sauvegarder dans SharedPreferences
prefs.edit()
    .putBoolean("emulation_${console}_audio_muted", audioMuted)
    .apply()

// Restaurer au lancement
audioMuted = prefs.getBoolean("emulation_${console}_audio_muted", false)
retroView.audioEnabled = !audioMuted
```

### Résultat Attendu

- ✅ Toggle rapide audio on/off
- ✅ Visual feedback (bouton rouge si muted)
- ✅ State sauvegardé par console
- ✅ Hotkey L2 + Down (exemple)

**Durée:** 1-2h | **Impact:** ⭐⭐⭐⭐

---

## 💾 QUICK WIN #3: AUTO-SAVE STATES

### Pourquoi c'est un Quick Win

✅ **API disponible** - `serializeState()` / `unserializeState()`  
✅ **Système de saves existant** - Juste ajouter timer auto  
✅ **Impact UX majeur** - Jamais perdre progression  
✅ **Pattern simple** - Handler + interval check  

### Implémentation

#### 1. Ajouter Auto-Save Timer (1h)

```kotlin
// RetroArchEmulatorActivity.kt
private var autoSaveHandler: Handler? = null
private var autoSaveEnabled = false
private var autoSaveIntervalMs = 300000L  // 5 min default

private fun startAutoSaveTimer() {
    if (!autoSaveEnabled) return
    
    autoSaveHandler = Handler(Looper.getMainLooper())
    autoSaveHandler?.postDelayed(object : Runnable {
        override fun run() {
            performAutoSave()
            autoSaveHandler?.postDelayed(this, autoSaveIntervalMs)
        }
    }, autoSaveIntervalMs)
    
    Log.i(TAG, "[AUTO_SAVE] Timer started: ${autoSaveIntervalMs / 60000} minutes")
}

private fun performAutoSave() {
    try {
        val state = retroView.serializeState()
        if (state.isNotEmpty()) {
            // Sauvegarder dans slot spécial "auto"
            val autoSaveFile = File(savesDir, "${gameId}_auto.state")
            autoSaveFile.writeBytes(state)
            Log.i(TAG, "[AUTO_SAVE] Auto-saved to ${autoSaveFile.name} (${state.size} bytes)")
            
            // Toast discret (optionnel)
            runOnUiThread {
                Toast.makeText(this, "Auto-saved", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "[AUTO_SAVE] Failed: ${e.message}")
    }
}

override fun onPause() {
    super.onPause()
    autoSaveHandler?.removeCallbacksAndMessages(null)
    performAutoSave()  // Save immédiat avant pause
}
```

#### 2. Settings UI (1h)

```kotlin
// AdvancedOverlaySettingsDialog.kt ou nouveau EmulationSettingsDialog.kt
var autoSaveEnabled by remember { 
    mutableStateOf(prefs.getBoolean("emulation_auto_save_enabled", true))
}

var autoSaveInterval by remember {
    mutableStateOf(prefs.getInt("emulation_auto_save_interval_min", 5))
}

// Toggle
Switch(
    checked = autoSaveEnabled,
    onCheckedChange = {
        autoSaveEnabled = it
        prefs.edit().putBoolean("emulation_auto_save_enabled", it).apply()
    }
)
Text("Auto-Save States")

// Interval slider (si enabled)
if (autoSaveEnabled) {
    Slider(
        value = autoSaveInterval.toFloat(),
        onValueChange = { autoSaveInterval = it.toInt() },
        valueRange = 1f..30f,  // 1 à 30 minutes
        steps = 29
    )
    Text("Interval: $autoSaveInterval min")
}
```

#### 3. Auto-Load on Game Launch (1h)

```kotlin
// Dans onCreate() après loadGame()
val autoSaveFile = File(savesDir, "${gameId}_auto.state")
if (autoSaveFile.exists()) {
    // Dialog: "Auto-save found, load it?"
    showAutoLoadDialog.value = true
} else {
    startAutoSaveTimer()
}

// Nouveau Dialog
if (showAutoLoadDialog.value) {
    AlertDialog(
        title = { Text("Auto-Save Found") },
        text = { Text("Load auto-saved state from ${formatTime(autoSaveFile.lastModified())}?") },
        confirmButton = {
            Button(onClick = {
                loadAutoSave(autoSaveFile)
                showAutoLoadDialog.value = false
            }) { Text("Load") }
        },
        dismissButton = {
            Button(onClick = {
                showAutoLoadDialog.value = false
                startAutoSaveTimer()
            }) { Text("New Game") }
        }
    )
}
```

### Résultat Attendu

- ✅ Auto-save toutes les X minutes (configurable)
- ✅ Auto-save au pause/quit
- ✅ Prompt au lancement si auto-save existe
- ✅ Slot séparé "_auto.state" (ne touche pas slots 1-9)
- ✅ Toast discret "Auto-saved"

**Durée:** 3-5h | **Impact:** ⭐⭐⭐⭐⭐

---

## 🎨 QUICK WIN #4: SHADER SELECTION

### Pourquoi c'est un Quick Win

✅ **Shaders déjà disponibles** - SHADER_DEFAULT, SHADER_CRT, SHADER_LCD  
✅ **API simple** - `retroView.shader = ShaderConfig(...)`  
✅ **Impact visuel** - CRT, LCD, Scanlines  
✅ **UI basique** - Dropdown dans QuickMenu  

### Implémentation

#### 1. Liste des Shaders (30 min)

```kotlin
// ShaderManager.kt (nouveau)
object ShaderManager {
    enum class ShaderPreset {
        DEFAULT,
        CRT,
        LCD,
        SHARP,
        UPSCALE_CUT,
        UPSCALE_CUT2,
        UPSCALE_CUT3
    }
    
    fun getShaderConfig(preset: ShaderPreset): ShaderConfig {
        return when (preset) {
            ShaderPreset.DEFAULT -> ShaderConfig.Default
            ShaderPreset.CRT -> ShaderConfig.CRT
            ShaderPreset.LCD -> ShaderConfig.LCD
            ShaderPreset.SHARP -> ShaderConfig.Sharp
            // etc...
        }
    }
    
    fun getShaderName(preset: ShaderPreset): String {
        return when (preset) {
            ShaderPreset.DEFAULT -> "None (Fast)"
            ShaderPreset.CRT -> "CRT (Scanlines)"
            ShaderPreset.LCD -> "LCD (Handheld)"
            ShaderPreset.SHARP -> "Sharp (Pixels)"
            // etc...
        }
    }
}
```

#### 2. UI dans QuickMenu (1h)

```kotlin
// QuickMenuDialog.kt
var currentShader by remember { 
    mutableStateOf(ShaderManager.ShaderPreset.DEFAULT)
}

// Dropdown
ExposedDropdownMenuBox(...) {
    ShaderManager.ShaderPreset.values().forEach { preset ->
        DropdownMenuItem(
            text = { Text(ShaderManager.getShaderName(preset)) },
            onClick = {
                currentShader = preset
                // Appliquer immédiatement
                retroView.shader = ShaderManager.getShaderConfig(preset)
                // Sauvegarder
                prefs.edit()
                    .putString("emulation_${console}_shader", preset.name)
                    .apply()
            }
        )
    }
}
```

#### 3. Hotkeys shader_next/prev (30 min)

```kotlin
// Dans onHotkey callback
"shader_next" -> {
    val presets = ShaderManager.ShaderPreset.values()
    val currentIndex = presets.indexOf(currentShader)
    val nextIndex = (currentIndex + 1) % presets.size
    currentShader = presets[nextIndex]
    retroView.shader = ShaderManager.getShaderConfig(currentShader)
    Toast.makeText(this, "Shader: ${ShaderManager.getShaderName(currentShader)}", Toast.LENGTH_SHORT).show()
}

"shader_prev" -> {
    val presets = ShaderManager.ShaderPreset.values()
    val currentIndex = presets.indexOf(currentShader)
    val nextIndex = if (currentIndex == 0) presets.size - 1 else currentIndex - 1
    currentShader = presets[nextIndex]
    retroView.shader = ShaderManager.getShaderConfig(currentShader)
    Toast.makeText(this, "Shader: ${ShaderManager.getShaderName(currentShader)}", Toast.LENGTH_SHORT).show()
}
```

### Résultat Attendu

- ✅ 7 shaders disponibles (DEFAULT, CRT, LCD, SHARP, 3x UPSCALE)
- ✅ Hotkeys L2+L1/R1 pour naviguer
- ✅ Dropdown dans QuickMenu
- ✅ Visual immédiat (CRT scanlines, LCD grid)
- ✅ Sauvegardé par console

**Durée:** 2-3h | **Impact:** ⭐⭐⭐

---

## 📋 ORDRE D'IMPLÉMENTATION SUGGÉRÉ

### Session 1 (2-3h)
1. ✅ **Audio Mute** (1-2h) - Le plus simple
2. ✅ **Fast Forward** (2-3h) - API simple, impact énorme

**Pause/Test** - Vérifier que tout fonctionne

### Session 2 (2-3h)
3. ✅ **Shader Selection** (2-3h) - Visual, fun à tester

**Pause/Test** - Tester shaders CRT, LCD

### Session 3 (3-5h)
4. ✅ **Auto-Save States** (3-5h) - Plus complexe, timer + dialog

**Test Final** - Vérifier auto-save fonctionne sur 30 min de jeu

---

## 🎯 BÉNÉFICES APRÈS QUICK WINS

### Avant (Score actuel: 9.5/10)
- ✅ Overlays parfaits
- ✅ Hotkeys complets
- ❌ Pas de Fast Forward
- ❌ Pas de Audio Mute
- ❌ Pas d'Auto-Save
- ❌ Shaders figés

### Après Quick Wins (Score: 9.8/10)
- ✅ Overlays parfaits
- ✅ Hotkeys complets
- ✅ **Fast Forward 2x/3x/4x**
- ✅ **Audio Mute toggle**
- ✅ **Auto-Save toutes les 5 min**
- ✅ **7 shaders disponibles**

**Impact utilisateur:** MASSIF pour 8-13h de travail !

---

## 📝 FICHIERS À MODIFIER

### Quick Win #1-2 (Audio Mute + Fast Forward)
1. `RetroArchEmulatorActivity.kt` - Ajouter toggles
2. `NativeComposeEmulatorActivity.kt` - Même chose
3. `QuickMenuDialog.kt` - Ajouter boutons
4. `strings.xml` - Textes UI

**Total:** 4 fichiers

### Quick Win #3 (Auto-Save)
1. `RetroArchEmulatorActivity.kt` - Timer + performAutoSave()
2. `NativeComposeEmulatorActivity.kt` - Même chose
3. Nouveau: `AutoSaveDialog.kt` - Prompt load auto-save
4. `AdvancedOverlaySettingsDialog.kt` - Settings auto-save

**Total:** 4 fichiers (1 nouveau)

### Quick Win #4 (Shader Selection)
1. Nouveau: `ShaderManager.kt` - Enum + helpers
2. `QuickMenuDialog.kt` - Dropdown shaders
3. `RetroArchEmulatorActivity.kt` - Hotkeys shader_next/prev

**Total:** 3 fichiers (1 nouveau)

---

## 🚦 PRÊT À COMMENCER ?

**Par quel Quick Win voulez-vous commencer ?**

1. **Audio Mute** - Le plus simple (1-2h) 🔇
2. **Fast Forward** - Impact énorme (2-3h) ⚡
3. **Shader Selection** - Visual fun (2-3h) 🎨
4. **Auto-Save States** - Plus complexe (3-5h) 💾

**OU les faire dans l'ordre suggéré:** Audio → Fast Forward → Shader → Auto-Save

Dites-moi par lequel on commence ! 🎮

