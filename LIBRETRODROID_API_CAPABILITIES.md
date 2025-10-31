# 🔍 LibretroDroid API - Capabilities & Fonctionnalités Disponibles

**Date:** 31 octobre 2025  
**Source:** c:\repos\LibretroDroid\libretrodroid\src\main\java\com\swordfish\libretrodroid\  
**Objectif:** Identifier TOUTES les capabilities LibretroDroid pour implémenter options manquantes

---

## 📊 API LIBRETRODROID - Méthodes Disponibles

### ✅ CORE LIFECYCLE
```java
// LibretroDroid.java
static native void create(...);
static native void loadGameFromPath(String gameFilePath);
static native void loadGameFromBytes(byte[] gameFileBytes);
static native void loadGameFromVirtualFiles(List<DetachedVirtualFile> virtualFiles);
static native void resume();
static native void pause();
static native void destroy();
static native void reset();
static native void step(GLRetroView retroView);
```

**Utilisé dans RetroPlay:** ✅ Complet

---

### ⚡ SPEED CONTROL

#### setFrameSpeed() - ✅ DISPONIBLE
```java
static native void setFrameSpeed(int speed);
```

**Paramètre:**
- `speed = 1` → Vitesse normale (100%)
- `speed = 2` → 2x speed (fast forward)
- `speed = 0` → Pause?
- `speed > 2` → 3x, 4x, etc.

**Dans GLRetroView.kt:**
```kotlin
var frameSpeed: Int by Delegates.observable(1) { _, _, value ->
    LibretroDroid.setFrameSpeed(value)
}
```

#### ✅ IMPLÉMENTABLE: Fast Forward & Slow Motion

**Fast Forward:**
```kotlin
// Quick win!
fun setFastForward(ratio: Float) {
    val speed = when {
        ratio == 0f -> 0  // Unlimited (max speed)
        ratio >= 1f -> ratio.toInt()  // 2x, 3x, 4x
        else -> 1  // Normal
    }
    retroView.frameSpeed = speed
}
```

**Slow Motion:**
```kotlin
// Problème: setFrameSpeed() n'accepte que Int >= 0
// Slow motion (0.5x, 0.25x) nécessiterait speed < 1
```

**Status Slow Motion:** ⚠️ **POSSIBLEMENT NON SUPPORTÉ** (API limitation)  
**Alternative:** Frame advance manuel (step())

**Impact:** ⭐⭐⭐⭐ Fast Forward **FACILEMENT IMPLÉMENTABLE** (2-3h)

---

### 💾 STATE MANAGEMENT

#### Serialize/Unserialize - ✅ DISPONIBLE
```java
static native byte[] serializeState();
static native boolean unserializeState(byte[] state);
```

**Dans GLRetroView.kt:**
```kotlin
fun serializeState() = catchExceptions {
    LibretroDroid.serializeState()
} ?: ByteArray(0)

fun unserializeState(state: ByteArray) = catchExceptions {
    LibretroDroid.unserializeState(state)
} ?: false
```

#### ✅ IMPLÉMENTABLE: Run Ahead & Rewind

**Run Ahead:**
```kotlin
// Pattern RetroArch:
// 1. Save state to memory
// 2. Run N frames ahead (skip video/audio)
// 3. Display frame N
// 4. Load state from memory
// 5. Run frame 1 with video/audio

fun runAheadFrame(frames: Int) {
    // 1. Serialize current state
    val state = retroView.serializeState()
    
    // 2. Run N frames ahead (disable audio/video)
    audioEnabled = false
    repeat(frames) {
        // Run frame without rendering (comment?)
        LibretroDroid.step(retroView)
    }
    audioEnabled = true
    
    // 3. Display frame
    // (déjà visible)
    
    // 4. Reload state
    retroView.unserializeState(state)
    
    // 5. Run real frame (avec audio/video)
    LibretroDroid.step(retroView)
}
```

**Problème:** Comment skip rendering pendant step() ?

**Rewind:**
```kotlin
// Pattern: Ring buffer de states
class RewindManager(val bufferSizeMB: Int, val granularity: Int) {
    private val stateBuffer = mutableListOf<ByteArray>()
    private val maxStates = (bufferSizeMB * 1024 * 1024) / averageStateSize
    
    fun captureState() {
        if (frameCounter % granularity == 0) {
            val state = retroView.serializeState()
            stateBuffer.add(state)
            if (stateBuffer.size > maxStates) {
                stateBuffer.removeAt(0)  // FIFO
            }
        }
        frameCounter++
    }
    
    fun rewindOneStep(): Boolean {
        if (stateBuffer.isEmpty()) return false
        val state = stateBuffer.removeLast()
        return retroView.unserializeState(state)
    }
}
```

**Status Run Ahead:** ⚠️ **PARTIELLEMENT IMPLÉMENTABLE** (nécessite skip rendering)  
**Status Rewind:** ✅ **FACILEMENT IMPLÉMENTABLE** (3-5h)

**Impact:** Rewind ⭐⭐⭐⭐ **TRÈS HAUTE**, Run Ahead ⭐⭐⭐⭐⭐ **CRITIQUE** (si possible)

---

### 🎵 AUDIO CONTROL

#### setAudioEnabled() - ✅ DISPONIBLE
```java
static native void setAudioEnabled(boolean enabled);
```

**Dans GLRetroView.kt:**
```kotlin
var audioEnabled: Boolean by Delegates.observable(true) { _, _, value ->
    LibretroDroid.setAudioEnabled(value)
}
```

#### ✅ UTILISÉ: Mute/Unmute

**Actuellement utilisé pour:**
- Fast forward mute
- Rewind mute

**Manquant:** Volume control (API ne supporte pas volume, seulement mute)

**Impact:** ⭐⭐ **MOYENNE** - Mute OK, mais pas de volume slider  
**Alternative:** Utiliser Android AudioManager pour volume global

---

### 🎮 CONTROLLER CONFIGURATION

#### setControllerType() - ✅ DISPONIBLE
```java
static native void setControllerType(int port, int type);
```

**Types RETRO_DEVICE (de libretro.h):**
```c
#define RETRO_DEVICE_NONE         0
#define RETRO_DEVICE_JOYPAD       1  // Standard gamepad
#define RETRO_DEVICE_MOUSE        2  // Mouse
#define RETRO_DEVICE_KEYBOARD     3  // Keyboard
#define RETRO_DEVICE_LIGHTGUN     4  // Lightgun (NES Zapper, etc.)
#define RETRO_DEVICE_ANALOG       5  // Analog controller
#define RETRO_DEVICE_POINTER      6  // Touch/Pointer
```

**Status dans RetroPlay:**
- Ligne 197 GLRetroView.kt: `fun setControllerType(port: Int, type: Int)`
- ⚠️ TODO NativeComposeEmulatorActivity ligne 670: "Configurer les extensions contrôleur N64"

**Utilisation Zapper:**
```kotlin
// Pour Duck Hunt
retroView.setControllerType(1, 6)  // Port 2 = RETRO_DEVICE_POINTER
```

#### ✅ IMPLÉMENTABLE: Multi-Controller Configs

**Extensions N64:**
```kotlin
// Memory Pak, Rumble Pak, Transfer Pak
fun configureN64Controller(port: Int, pak: Int) {
    // RETRO_DEVICE_JOYPAD avec subtype
    // Nécessite recherche dans docs libretro pour subtypes
}
```

**Impact:** ⭐⭐⭐ **HAUTE** - N64 advanced features

---

### 🎨 SHADER/VIDEO

#### setShaderConfig() - ✅ DISPONIBLE
```java
static native void setShaderConfig(GLRetroShader shader);
```

**Shaders Built-in LibretroDroid:**
- SHADER_DEFAULT (0)
- SHADER_CRT (1)
- SHADER_LCD (2)
- SHADER_SHARP (3)
- SHADER_UPSCALE_CUT (4-6) avec 10+ params chacun

**Status:** ✅ Utilisé dans RetroPlay  
**Manquant:** UI pour sélectionner shaders + params

#### setViewport() - ✅ DISPONIBLE
```java
static native void setViewport(float x, float y, float width, float height);
```

**Dans GLRetroView.kt:**
```kotlin
var viewport: RectF by Delegates.observable(RectF(0f, 0f, 1f, 1f)) { _, _, value ->
    runOnGLThread {
        LibretroDroid.setViewport(value.left, value.top, value.width(), value.height())
    }
}
```

**Status:** ✅ Utilisé pour aspect ratio viewport (commit 606c54d, 7cff551)

#### refreshAspectRatio() - ✅ DISPONIBLE
```java
static native void refreshAspectRatio();
```

**Status:** ✅ Disponible mais probablement pas utilisé

---

### 🎯 VARIABLES (Core Options)

#### updateVariable() & getVariables() - ✅ DISPONIBLE
```java
static native void updateVariable(Variable variable);
static native Variable[] getVariables();
```

**Status:** ✅ **PARFAITEMENT UTILISÉ**
- CoreVariableManager.kt ✅
- CoreOptionsDialog.kt ✅
- Sauvegarde per-game et per-core ✅

---

### 💿 DISK CONTROL

#### Disk Management - ✅ DISPONIBLE
```java
static native int availableDisks();
static native int currentDisk();
static native void changeDisk(int index);
```

**Status:** ✅ Utilisé pour multi-disk games (PSX, Sega CD)  
**UI:** ⚠️ À vérifier si dialog existe

---

### 🎮 INPUT

#### Input Events - ✅ DISPONIBLE
```java
static native void onMotionEvent(int port, int motionSource, float xAxis, float yAxis);
static native void onTouchEvent(float xAxis, float yAxis);
static native void onKeyEvent(int port, int action, int keyCode);
```

**Status:** ✅ Utilisé partout
- Overlays RetroArch ✅
- Lemuroid gamepads ✅
- Physical controllers ✅
- Zapper ✅

---

### 💉 CHEATS

#### Cheat Management - ✅ DISPONIBLE
```java
static native void setCheat(int index, boolean enable, String code);
static native void resetCheat();
```

**Dans GLRetroView.kt:**
```kotlin
fun setCheat(index : Int, enable : Boolean, code : String) = runOnGLThread {
    LibretroDroid.setCheat(index, enable, code)
}

fun resetCheat() = runOnGLThread {
    LibretroDroid.resetCheat()
}
```

**Status:** ✅ **PARFAITEMENT UTILISÉ**
- CheatManager.kt ✅
- CheatApplier.kt ✅
- CheatActivity.kt ✅

---

## 📋 RÉSUMÉ DES CAPABILITIES

### ✅ SUPPORTÉ ET UTILISÉ (9 features)
1. ✅ Core lifecycle (load, pause, resume, destroy)
2. ✅ State management (save/load states)
3. ✅ Cheats (set, enable, reset)
4. ✅ Variables (core options)
5. ✅ Input events (keyboard, touch, motion)
6. ✅ Controller types (joypad, pointer, etc.)
7. ✅ Disk control (multi-disk games)
8. ✅ Shaders (built-in)
9. ✅ Viewport (aspect ratio)

### ⚡ SUPPORTÉ MAIS NON UTILISÉ (4 features)

| Feature | API | Implémentation | Estimation |
|---------|-----|----------------|------------|
| **Fast Forward** | setFrameSpeed(2+) | ✅ Trivial | 2-3h |
| **Rewind** | serialize/unserialize + buffer | ✅ Faisable | 15-20h |
| **Shader Selection UI** | setShaderConfig() | ✅ Moyen | 5-8h |
| **Audio Mute** | setAudioEnabled() | ✅ Utilisé FF | 1h (UI) |

### ⚠️ LIMITATIONS API (3 features)

| Feature | Pourquoi Non Supporté | Alternative |
|---------|----------------------|-------------|
| **Slow Motion** | frameSpeed n'accepte que Int >= 1 | ⏳ Frame advance step() |
| **Audio Volume** | Pas de setAudioVolume() | 🔊 Android AudioManager |
| **Run Ahead** | Pas de skipRendering() flag | ⚠️ Possible mais complexe |

### ❌ NON SUPPORTÉ (2 features)

| Feature | Raison | Workaround |
|---------|--------|------------|
| **Frame Delay** | Pas d'API native | ⚠️ Possiblement via C++ patch |
| **Threaded Video** | Internal LibretroDroid | ❌ Pas accessible |

---

## 🎯 FAISABILITÉ DES FEATURES MANQUANTES

### 🟢 FACILEMENT IMPLÉMENTABLE (Quick Wins)

#### 1. Fast Forward ✅ (2-3h)
```kotlin
// GLRetroView property déjà existe!
var fastForwardRatio by remember { mutableStateOf(1) }

// Dans UI
Slider(
    value = fastForwardRatio.toFloat(),
    onValueChange = { fastForwardRatio = it.toInt() },
    valueRange = 1f..10f
)

// Appliquer
retroView.frameSpeed = fastForwardRatio
```

**API:** ✅ `setFrameSpeed(int)`  
**Complexité:** Très Faible  
**Impact:** ⭐⭐⭐⭐ Très Haute

#### 2. Audio Mute UI (1h)
```kotlin
// Déjà utilisé en interne, manque juste UI
var audioMuted by remember { mutableStateOf(false) }

// Dans Quick Menu
Switch(
    checked = !audioMuted,
    onCheckedChange = { 
        audioMuted = !it
        retroView.audioEnabled = it
    }
)
```

**API:** ✅ `setAudioEnabled(bool)`  
**Complexité:** Très Faible  
**Impact:** ⭐⭐⭐ Haute

#### 3. Shader Selection UI (5-8h)
```kotlin
// Shaders built-in disponibles
val shaders = listOf(
    ShaderConfig(LibretroDroid.SHADER_DEFAULT),
    ShaderConfig(LibretroDroid.SHADER_CRT),
    ShaderConfig(LibretroDroid.SHADER_LCD),
    ShaderConfig(LibretroDroid.SHADER_SHARP),
    ShaderConfig(LibretroDroid.SHADER_UPSCALE_CUT)
)

// Dropdown
DropdownMenu {
    shaders.forEach { shader ->
        DropdownMenuItem(
            onClick = { retroView.shader = shader }
        )
    }
}
```

**API:** ✅ `setShaderConfig(GLRetroShader)`  
**Complexité:** Faible  
**Impact:** ⭐⭐⭐ Haute

---

### 🟡 MOYENNEMENT COMPLEXE (Faisable)

#### 4. Rewind (15-20h)
```kotlin
class RewindManager(
    private val retroView: GLRetroView,
    private val bufferSizeMB: Int = 20,
    private val granularity: Int = 1  // Frames per state
) {
    private val stateBuffer = ArrayDeque<ByteArray>()
    private var frameCounter = 0
    private val maxStates = calculateMaxStates()
    
    fun onFrame() {
        frameCounter++
        if (frameCounter % granularity == 0) {
            captureState()
        }
    }
    
    private fun captureState() {
        val state = retroView.serializeState()
        stateBuffer.addLast(state)
        
        // Limit buffer size
        while (getBufferSizeBytes() > bufferSizeMB * 1024 * 1024) {
            stateBuffer.removeFirst()
        }
    }
    
    fun rewind(): Boolean {
        if (stateBuffer.isEmpty()) return false
        val state = stateBuffer.removeLast()
        return retroView.unserializeState(state)
    }
    
    private fun getBufferSizeBytes() = stateBuffer.sumOf { it.size }
}
```

**API:** ✅ `serializeState()` / `unserializeState()`  
**Complexité:** Moyenne (buffer management)  
**Impact:** ⭐⭐⭐⭐ Très Haute  
**Estimation:** 15-20h

#### 5. Auto-Save States (3-5h)
```kotlin
// onPause() de l'Activity
override fun onPause() {
    super.onPause()
    if (autoSaveEnabled) {
        val state = retroView.serializeState()
        saveToFile("auto_save_${gameId}.state", state)
    }
}

// onResume()
override fun onResume() {
    super.onResume()
    if (autoLoadEnabled) {
        val state = loadFromFile("auto_save_${gameId}.state")
        if (state != null) {
            retroView.unserializeState(state)
        }
    }
}
```

**API:** ✅ `serializeState()` / `unserializeState()`  
**Complexité:** Faible  
**Impact:** ⭐⭐⭐⭐ Très Haute

---

### 🔴 COMPLEXE OU NON SUPPORTÉ

#### 6. Run Ahead avec Second Instance (20-30h)
**Problème:** Nécessite 2 instances du core en parallèle

**Pattern RetroArch:**
```c
// Instance 1: État "réel" (hidden)
// Instance 2: État "avance" (displayed)
// Chaque frame: copier state de 1 → 2, run ahead sur 2
```

**Faisabilité:** ⚠️ **TRÈS COMPLEXE**
- Nécessite charger le core 2 fois
- Synchronisation états
- Double mémoire

**Recommandation:** Run Ahead simple d'abord, second instance plus tard

#### 7. Frame Delay (5-8h?)
**Problème:** Pas d'API native `setFrameDelay(int ms)`

**Possible via:**
- Délai artificiel avant `step()`?
- Thread.sleep() avant rendering?

**Faisabilité:** ⚠️ **INCERTAIN** - Nécessite tests

#### 8. Slow Motion (API limitation)
**Problème:** `setFrameSpeed(int)` n'accepte que >= 1

**Alternative:** Frame Advance manuel
```kotlin
fun frameAdvance() {
    LibretroDroid.step(retroView)  // 1 frame exactement
}
```

**Faisabilité:** ✅ Frame Advance OK, Slow Motion continu ❌

---

## 📊 TABLEAU RÉCAPITULATIF

| Feature | API LibretroDroid | Faisabilité | Estimation | Impact |
|---------|-------------------|-------------|------------|--------|
| **Fast Forward** | setFrameSpeed(2+) | ✅ Trivial | 2-3h | ⭐⭐⭐⭐ |
| **Rewind** | serialize/unserialize | ✅ Faisable | 15-20h | ⭐⭐⭐⭐ |
| **Auto-Save** | serialize/unserialize | ✅ Trivial | 3-5h | ⭐⭐⭐⭐ |
| **Audio Mute UI** | setAudioEnabled() | ✅ Trivial | 1h | ⭐⭐⭐ |
| **Shader UI** | setShaderConfig() | ✅ Facile | 5-8h | ⭐⭐⭐ |
| **Run Ahead (simple)** | serialize/unserialize | ⚠️ Complexe | 20-30h | ⭐⭐⭐⭐⭐ |
| **Run Ahead (second)** | Double core | ⚠️ Très Complexe | 40-50h | ⭐⭐⭐⭐⭐ |
| **Frame Delay** | ❌ Pas d'API | ⚠️ Incertain | 5-10h | ⭐⭐⭐ |
| **Slow Motion** | ❌ API limitation | ❌ Non | - | ⭐⭐⭐ |
| **Audio Volume** | ❌ Pas d'API | 🔊 AudioManager | 2-3h | ⭐⭐⭐⭐ |

---

## 🎯 RECOMMANDATIONS PAR PRIORITÉ

### 🔥 PRIORITÉ 1 - Quick Wins (1 semaine)

**Total: 11-17h pour 4 features majeures**

1. **Fast Forward** (2-3h)
   - setFrameSpeed() déjà existe
   - Juste ajouter UI slider + hotkey
   
2. **Audio Mute UI** (1h)
   - setAudioEnabled() déjà existe
   - Ajouter toggle dans Quick Menu
   
3. **Auto-Save States** (3-5h)
   - serialize/unserialize déjà utilisé
   - onPause/onResume hooks
   
4. **Shader Selection** (5-8h)
   - setShaderConfig() déjà utilisé
   - Créer UI picker (6 shaders built-in)

### ⚡ PRIORITÉ 2 - Major Features (3-4 semaines)

**Total: 15-20h pour 1 feature majeure**

5. **Rewind** (15-20h)
   - Ring buffer de states
   - Hotkey integration
   - UI settings (buffer size, granularity)

### 🚀 PRIORITÉ 3 - Advanced (Si possible après recherche)

6. **Run Ahead Simple** (20-30h)
   - Nécessite skip rendering (recherche requise)
   - Proof of concept d'abord
   
7. **Frame Delay** (5-10h)
   - Expérimentation avec Thread.sleep()
   - Tests de faisabilité

8. **Audio Volume** (2-3h)
   - Via Android AudioManager
   - Per-game settings

---

## ✅ CONCLUSION

### LibretroDroid est TRÈS Capable!

**Score API:** 8/10

- ✅ **Excellent:** State management, Input, Core options
- ✅ **Bon:** Speed control (FF), Shader, Viewport
- ⚠️ **Limitations:** Slow motion, Audio volume, Frame delay

### Quick Wins Disponibles

**11-17h d'implémentation** pour 4 features majeures:
1. Fast Forward
2. Audio Mute UI
3. Auto-Save States
4. Shader Selection

**Impact:** Amélioration UX **MASSIVE** avec effort minimal

### Recommandation Immédiate

**AVANT d'implémenter:**
1. ✅ Vérifier si `dialog_gamepad_settings.xml` est utilisé (probablement obsolète)
2. ✅ Identifier layouts XML obsolètes (vestiges ChatAI)
3. ✅ Créer `EmulationSettingsDialog.kt` pour nouvelles options
4. ✅ Commencer par Fast Forward (2-3h, quick win)

---

**Document créé le:** 31 octobre 2025  
**Méthode:** "Nos Rules" - Analyse API complète  
**Source:** LibretroDroid 0.13.0  
**Prochaine étape:** Implémentation des Quick Wins


