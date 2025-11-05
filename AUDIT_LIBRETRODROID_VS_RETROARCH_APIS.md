# 🔍 Audit Comparatif : LibretroDroid vs RetroArch APIs

**Date:** 5 novembre 2025  
**Objectif:** Identifier ce que RetroArch fait avec des APIs natives que RetroPlay ne fait pas encore  
**Méthodologie:** Comparaison fonction par fonction entre RetroArch et LibretroDroid

---

## 1. APIs de Base Libretro (✅ DÉJÀ EXPOSÉES)

### Savestates (Save/Load State)
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `core_serialize_size()` | ✅ Utilisé en interne | ✅ EXPOSÉ |
| `core_serialize()` | ✅ `LibretroDroid.serializeState()` | ✅ EXPOSÉ |
| `core_unserialize()` | ✅ `LibretroDroid.unserializeState()` | ✅ EXPOSÉ |
| `core_serialize_special()` | ❌ Non exposé | ⚠️ MANQUANT |
| `core_unserialize_special()` | ❌ Non exposé | ⚠️ MANQUANT |

**Utilisation actuelle dans RetroPlay:**
- Sauvegardes/chargements de slots (fonctionnel)
- Base pour Run-Ahead (savestates disponibles!)

**Manquant:**
- Special serialize (pour cores qui nécessitent handling spécial, ex: PSX)

---

### SRAM (Save RAM - fichiers .srm)
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `core_get_memory(RETRO_MEMORY_SAVE_RAM)` | ✅ `LibretroDroid.serializeSRAM()` | ✅ EXPOSÉ |
| | ✅ `LibretroDroid.unserializeSRAM()` | ✅ EXPOSÉ |

**Utilisation:** Automatique dans LibretroDroid (gestion interne)

---

### Core Lifecycle
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `core_run()` | ✅ `LibretroDroid.step()` (appelé dans boucle) | ✅ EXPOSÉ |
| `core_reset()` | ✅ `LibretroDroid.reset()` | ✅ EXPOSÉ |
| `core_load_game()` | ✅ `LibretroDroid.loadGameFromPath()` | ✅ EXPOSÉ |
| `core_unload_game()` | ✅ `LibretroDroid.destroy()` | ✅ EXPOSÉ |

**Utilisation:** Toutes les fonctions de base lifecycle sont exposées

---

### Cheats
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `core_set_cheat()` | ✅ `LibretroDroid.setCheat()` | ✅ EXPOSÉ |
| `core_reset_cheat()` | ✅ `LibretroDroid.resetCheat()` | ✅ EXPOSÉ |

**Utilisation actuelle:** CheatManager.kt fonctionnel

---

### Input
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `core_set_controller_port_device()` | ✅ `LibretroDroid.setControllerType()` | ✅ EXPOSÉ |
| `input_poll()` | ✅ Automatique dans LibretroDroid | ✅ EXPOSÉ |
| `onKeyEvent()` | ✅ `LibretroDroid.onKeyEvent()` | ✅ EXPOSÉ |
| `onMotionEvent()` | ✅ `LibretroDroid.onMotionEvent()` | ✅ EXPOSÉ |
| `onMouseButton()` | ✅ `LibretroDroid.onMouseButton()` | ✅ EXPOSÉ (nous l'avons ajouté!) |

**Utilisation:** Zapper/Lightgun fonctionnel grâce à onMouseButton()

---

### System Info & Geometry
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `core_get_system_info()` | ✅ Interne | ✅ EXPOSÉ |
| `retro_get_system_av_info()` | ✅ Partiel | ⚠️ PARTIEL |
| `getAspectRatio()` | ✅ `LibretroDroid.getAspectRatio()` | ✅ EXPOSÉ (nous l'avons ajouté!) |
| `getGameGeometryWidth()` | ✅ `LibretroDroid.getGameGeometryWidth()` | ✅ EXPOSÉ (nous l'avons ajouté!) |
| `getGameGeometryHeight()` | ✅ `LibretroDroid.getGameGeometryHeight()` | ✅ EXPOSÉ (nous l'avons ajouté!) |

**Utilisation:** Zapper calibration pixel-perfect fonctionnel

---

## 2. Features Frontend (❌ NON EXPOSÉES - LOGIQUE À IMPLÉMENTER)

### ⚡ Run-Ahead (Input Lag Reduction)

**Ce que RetroArch fait:**
```c
// runahead.c - Fonction principale
void preempt_run(preempt_t *preempt, runloop_state_t *runloop_st) {
    // 1. Poll input
    preempt_input_poll(preempt, runloop_st, max_users);
    
    // 2. Si input dirty, reload state et run ahead
    if (INPUT_IS_DIRTY && frame_count >= frames) {
        audio_suspended = true;
        video_active = false;
        
        retro_unserialize(buffer[start_ptr], state_size);
        retro_run();  // Frame 1
        
        while (replay_ptr != start_ptr) {
            retro_serialize(buffer[replay_ptr], state_size);
            retro_run();  // Frame 2, 3, ...
            replay_ptr = NEXT(replay_ptr);
        }
        
        audio_suspended = false;
        video_active = true;
    }
    
    // 3. Save current state
    retro_serialize(buffer[start_ptr], state_size);
    start_ptr = NEXT(start_ptr);
    frame_count++;
}
```

**Ce que LibretroDroid expose:**
- ✅ `serializeState()` / `unserializeState()` - savestates
- ✅ `step()` - run one frame
- ✅ `setAudioEnabled()` - toggle audio
- ❌ **Pas de wrapper Run-Ahead** - il faut l'implémenter au niveau Kotlin/Java!

**Implémentation possible dans RetroPlay:**
```kotlin
// RunAheadManager.kt (À IMPLÉMENTER)
class RunAheadManager(private val retroView: GLRetroView) {
    private var enabled: Boolean = false
    private var frames: Int = 0
    private var stateBuffer: MutableList<ByteArray> = mutableListOf()
    private var bufferIndex: Int = 0
    
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    fun setFrames(numFrames: Int) {
        this.frames = numFrames.coerceIn(0, 12)
        // Resize buffer
        stateBuffer.clear()
        repeat(frames + 1) {
            stateBuffer.add(ByteArray(0))
        }
    }
    
    // À appeler AVANT retroView.step() dans la boucle de rendu
    fun runAheadFrame() {
        if (!enabled || frames == 0) return
        
        // 1. Save current state
        val currentState = retroView.serializeState()
        stateBuffer[bufferIndex] = currentState
        
        // 2. Run ahead N frames (sans audio/video)
        retroView.audioEnabled = false
        repeat(frames) {
            // step() va appeler LibretroDroid.step() qui run le core
            // Mais on ne veut pas afficher le frame...
            // PROBLÈME: LibretroDroid.step() affiche automatiquement!
        }
        
        // 3. Restore state
        retroView.unserializeState(currentState)
        retroView.audioEnabled = true
        
        bufferIndex = (bufferIndex + 1) % (frames + 1)
    }
}
```

**❌ PROBLÈME CRITIQUE:**
- `LibretroDroid.step()` AFFICHE automatiquement le frame (rendering GL)
- **On ne peut PAS désactiver le rendering pour les frames "ahead"**
- RetroArch a un flag `video_active` qu'ils peuvent toggle
- LibretroDroid n'expose PAS ce flag!

**CE QUI MANQUE DANS LIBRETRODROID:**
```java
// MANQUANT dans LibretroDroid.java
public static native void setVideoEnabled(boolean enabled); // Toggle video rendering
public static native void stepWithoutRendering(); // Run frame sans affichage
```

---

### ⏪ Rewind (Gameplay Rewinding)

**Ce que RetroArch fait:**
```c
// Chaque frame: save state to circular buffer
if (rewind_enabled) {
    retro_serialize(rewind_buffer[rewind_ptr], state_size);
    rewind_ptr = (rewind_ptr + 1) % buffer_frames;
}

// Quand user appuie sur rewind:
if (rewind_requested && rewind_ptr > 0) {
    rewind_ptr--;
    retro_unserialize(rewind_buffer[rewind_ptr], state_size);
}
```

**Ce que LibretroDroid expose:**
- ✅ `serializeState()` / `unserializeState()` - savestates
- ❌ Pas de wrapper Rewind

**Implémentation possible dans RetroPlay:**
```kotlin
// RewindManager.kt (À IMPLÉMENTER - FACILE!)
class RewindManager(private val retroView: GLRetroView) {
    private var enabled: Boolean = false
    private var bufferSize: Int = 20 * 1024 * 1024 // 20 MB
    private var granularity: Int = 1 // Save every N frames
    private val stateBuffer: MutableList<ByteArray> = mutableListOf()
    private var bufferIndex: Int = 0
    private var frameCounter: Int = 0
    
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    fun setBufferSize(sizeBytes: Int) {
        this.bufferSize = sizeBytes
        // Clear buffer si dépassé
        cleanupBuffer()
    }
    
    // À appeler APRÈS chaque retroView.step() dans la boucle de rendu
    fun saveFrameState() {
        if (!enabled) return
        
        frameCounter++
        
        // Save state every N frames (granularity)
        if (frameCounter % granularity == 0) {
            val state = retroView.serializeState()
            stateBuffer.add(state)
            
            // Si buffer trop gros, supprimer les plus vieux
            cleanupBuffer()
        }
    }
    
    // À appeler quand user appuie sur rewind button
    fun rewindOneFrame(): Boolean {
        if (!enabled || stateBuffer.isEmpty()) return false
        
        // Load most recent state
        val state = stateBuffer.removeAt(stateBuffer.size - 1)
        return retroView.unserializeState(state)
    }
    
    private fun cleanupBuffer() {
        var currentSize = stateBuffer.sumOf { it.size }
        
        // Remove oldest states if buffer exceeds limit
        while (currentSize > bufferSize && stateBuffer.isNotEmpty()) {
            val removed = stateBuffer.removeAt(0)
            currentSize -= removed.size
        }
    }
}
```

**✅ FACILE À IMPLÉMENTER:** Rewind peut être fait 100% en Kotlin avec APIs existantes!

---

### ⏩ Fast Forward (Speed Up)

**Ce que RetroArch fait:**
```c
if (fastforward_active) {
    if (settings->bools.fastforward_frameskip) {
        // Skip rendering frames for max speed
        video_active = (frame_count % 4 != 0);
    }
    
    if (settings->bools.audio_fastforward_mute) {
        audio_suspended = true;
    }
    
    // Run multiple frames per render tick
    for (int i = 0; i < settings->floats.fastforward_ratio; i++) {
        retro_run();
    }
}
```

**Ce que LibretroDroid expose:**
- ✅ `setFrameSpeed(int speed)` - déjà exposé!
- ✅ `setAudioEnabled(boolean)` - déjà exposé!

**Utilisation actuelle dans RetroPlay:**
```kotlin
// RetroArchEmulatorActivity.kt - DÉJÀ FONCTIONNEL!
private fun toggleFastForward() {
    if (isFastForwardActive.value) {
        // Désactiver Fast Forward
        retroView.frameSpeed = 1
        retroView.audioEnabled = !audioMuted.value
        isFastForwardActive.value = false
    } else {
        // Activer Fast Forward
        retroView.frameSpeed = fastForwardRatio
        retroView.audioEnabled = false  // Mute audio en fast forward
        isFastForwardActive.value = true
    }
}
```

**✅ DÉJÀ IMPLÉMENTÉ ET FONCTIONNEL!**

---

### 🎨 Shaders (Visual Filters)

**Ce que RetroArch fait:**
```c
// video_driver.c
bool video_driver_set_shader(enum rarch_shader_type type, const char *path) {
    // Load shader from path
    if (!video_shader_parse_type(path, type))
        return false;
    
    // Apply to driver
    if (current_video && current_video->set_shader)
        return current_video->set_shader(current_video_data, type, path);
        
    return false;
}
```

**Ce que LibretroDroid expose:**
- ✅ `setShaderConfig(GLRetroShader)` - built-in shaders
- ✅ Shaders constants: `SHADER_CRT`, `SHADER_LCD`, `SHADER_SHARP`
- ❌ **Pas de support Slang shaders** (.slangp/.slang files)

**Utilisation actuelle dans RetroPlay:**
```kotlin
// ShaderManager.kt - DÉJÀ FONCTIONNEL avec shaders built-in!
enum class ShaderPreset {
    DEFAULT,
    CRT,
    LCD,
    SHARP
    // ...
}

// RetroArchEmulatorActivity.kt
private fun cycleShader() {
    val nextShader = when (currentShader.value) {
        ShaderPreset.DEFAULT -> ShaderPreset.CRT
        ShaderPreset.CRT -> ShaderPreset.LCD
        // ...
    }
    retroView.shader = ShaderManager.getShaderConfig(nextShader)
}
```

**✅ SHADERS BUILT-IN FONCTIONNELS!**

**⚠️ MANQUANT:**
- Support Slang shaders (360+ official shaders from RetroArch)
- Shader presets (.slangp files)
- Shader parameters (runtime tweaking)

**DÉCOUVERTE:** RetroArch a 360+ shaders dans `c:\repos\slang-shaders-master`
- CRT shaders (zfast-crt, crt-royale, crt-easymode, etc.)
- LCD shaders (lcd-grid, lcd-cgwg, etc.)
- Upscalers (xbr, scalehq, super-eagle, etc.)
- Scanline shaders, blur shaders, etc.

---

## 3. Audio/Video Controls (⚠️ PARTIELLEMENT EXPOSÉES)

### Audio
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `audio_driver_enable(bool)` | ✅ `LibretroDroid.setAudioEnabled()` | ✅ EXPOSÉ |
| `audio_driver_set_volume(float)` | ❌ Non exposé | ❌ MANQUANT |
| `audio_driver_mute_enable()` | ✅ Via setAudioEnabled(false) | ✅ WORKAROUND |

**Ce qui manque:**
- Volume control (doit être 0.0-1.0)
- Audio latency control

### Video
| Fonction RetroArch | LibretroDroid | Status |
|-------------------|---------------|---------|
| `video_driver_set_active(bool)` | ❌ Non exposé | ❌ MANQUANT |
| `video_set_nonblock_state(bool)` | ❌ Non exposé | ❌ MANQUANT |
| `setViewport()` | ✅ `LibretroDroid.setViewport()` | ✅ EXPOSÉ |
| `refreshAspectRatio()` | ✅ `LibretroDroid.refreshAspectRatio()` | ✅ EXPOSÉ |

**CRITIQUE pour Run-Ahead:**
- `video_driver_set_active(bool)` est **ESSENTIEL** pour Run-Ahead
- Permet de désactiver le rendering pendant les frames "ahead"
- **MANQUANT dans LibretroDroid!**

---

## 4. Advanced Features (❌ NON IMPLÉMENTÉES)

### Netplay (Online Multiplayer)
| Feature | RetroArch | LibretroDroid | RetroPlay |
|---------|-----------|---------------|-----------|
| Netplay client/server | ✅ `netplay/` (2000+ lignes) | ❌ Non exposé | ❌ Absent |
| Input delay for sync | ✅ `netplay_frontend.c` | ❌ Non exposé | ❌ Absent |
| Spectator mode | ✅ Oui | ❌ Non exposé | ❌ Absent |

**Complexité:** Très élevée (nécessite serveur, protocole, sync)

---

### Achievements (RetroAchievements.org)
| Feature | RetroArch | LibretroDroid | RetroPlay |
|---------|-----------|---------------|-----------|
| Achievement API | ✅ `cheevos/` (6000+ lignes) | ❌ Non exposé | ❌ Absent |
| Memory watching | ✅ `core_get_memory()` | ✅ Exposé | ✅ Possible |
| Achievement unlock | ✅ HTTP API | ❌ Non exposé | ❌ Absent |
| Rich Presence | ✅ Oui | ❌ Non exposé | ❌ Absent |

**Complexité:** Moyenne (nécessite HTTP API + memory watching)

---

### Recording/Streaming
| Feature | RetroArch | LibretroDroid | RetroPlay |
|---------|-----------|---------------|-----------|
| Video recording | ✅ FFmpeg integration | ❌ Non exposé | ❌ Absent |
| Screenshot | ✅ Built-in | ❌ Non exposé | ❌ Absent |
| Streaming (Twitch) | ✅ FFmpeg + RTMP | ❌ Non exposé | ❌ Absent |

**Complexité:** Moyenne (Android MediaRecorder + FFmpeg)

---

### Disk Control (Multi-disk games)
| Feature | RetroArch | LibretroDroid | RetroPlay |
|---------|-----------|---------------|-----------|
| `availableDisks()` | ✅ Oui | ✅ `LibretroDroid.availableDisks()` | ✅ EXPOSÉ |
| `currentDisk()` | ✅ Oui | ✅ `LibretroDroid.currentDisk()` | ✅ EXPOSÉ |
| `changeDisk()` | ✅ Oui | ✅ `LibretroDroid.changeDisk()` | ✅ EXPOSÉ |

**✅ DÉJÀ EXPOSÉ!** (juste pas utilisé dans UI)

---

## 5. Ce que RetroPlay PEUT Implémenter MAINTENANT (sans modifs C++)

### ✅ FACILE (avec APIs existantes)

#### 1. Rewind (Gameplay Rewinding)
**Difficulté:** Facile  
**Temps:** 2-3h  
**APIs requises:** ✅ Toutes disponibles (`serializeState()`, `unserializeState()`)

**Implementation:**
- `RewindManager.kt` (gérer buffer circulaire de savestates)
- Save state chaque frame (ou tous les N frames)
- Rewind = load state précédent
- UI: Bouton rewind dans Quick Menu

**Performance:**
- RAM: ~200 KB par frame × 60 frames = ~12 MB pour 1 seconde
- CPU: Minime (juste serialization)

---

#### 2. Disk Swapper UI (Multi-disk games)
**Difficulté:** Très facile  
**Temps:** 1-2h  
**APIs requises:** ✅ Toutes disponibles

**Implementation:**
- Dialog "Change Disk" dans Quick Menu
- `retroView.availableDisks()` → afficher liste
- `retroView.changeDisk(index)` → changer
- Utile pour PSX multi-disk (Final Fantasy 7-9, Metal Gear Solid, etc.)

---

#### 3. Screenshot System
**Difficulté:** Facile  
**Temps:** 2-3h  
**APIs requises:** ✅ Android Bitmap API

**Implementation:**
- Capturer le GLRetroView canvas
- Sauvegarder en PNG
- Location: `/storage/emulated/0/RetroPlay-Data/screenshots/`
- UI: Bouton screenshot dans Quick Menu

---

#### 4. Savestate Thumbnails
**Difficulté:** Facile  
**Temps:** 2-3h  
**APIs requises:** ✅ Bitmap API + serializeState()

**Implementation:**
- Quand on sauvegarde un slot, capturer screenshot
- Sauvegarder thumbnail: `slot1/{game}.png`
- Afficher thumbnail dans Load State menu
- Comme RetroArch!

---

### ⚠️ DIFFICILE (nécessitent modifs C++ LibretroDroid)

#### 1. Run-Ahead (Input Lag Reduction)
**Difficulté:** Difficile  
**Temps:** 10-15h (Single-Instance), 20-25h (Two-Instance)  
**APIs manquantes:**
```cpp
// REQUIS dans libretrodroid.cpp
void setVideoEnabled(bool enabled) {
    video_active = enabled;
}

void stepWithoutRendering() {
    if (!video_active) {
        // Run frame sans appel à onDrawFrame()
        retro_run();
    }
}
```

**Pourquoi difficile:**
- Nécessite modifier le rendering pipeline
- Gérer audio/video suspension
- Input replay system
- Deux instances de core (pour mode Two-Instance)

---

#### 2. Volume Control
**Difficulté:** Moyenne  
**Temps:** 3-5h  
**APIs manquantes:**
```cpp
void setAudioVolume(float volume) {
    audio_mixer_set_volume(volume);
}
```

---

#### 3. VSync Control
**Difficulté:** Moyenne  
**Temps:** 3-5h  
**APIs manquantes:**
```cpp
void setVSyncEnabled(bool enabled) {
    video_set_vsync(enabled);
}
```

---

## 6. Comparaison Fonctionnalités

### ✅ CE QUE RETROPLAY A (déjà fonctionnel)

| Feature | Status | Implementation |
|---------|--------|----------------|
| **Savestates** | ✅ | 5 slots, auto-save, quick save |
| **Cheats** | ✅ | CheatManager, 24K+ cheat files, UI in-game |
| **Fast Forward** | ✅ | Toggle FF, 1-4x speed |
| **Audio Mute** | ✅ | Toggle mute in-game |
| **Shaders** | ✅ | 6 built-in shaders, cycle in-game |
| **Core Options** | ✅ | `.cfg` system, hot-reload |
| **Zapper/Lightgun** | ✅ | Pixel-perfect calibration |
| **Overlays** | ✅ | 30+ RetroArch overlays, 100% compatible parser |
| **Database Integration** | ✅ | CRC lookup, metadata, Smart Config |
| **Per-Game Config** | ✅ | `.cfg` overrides per-game |

### ❌ CE QUE RETROPLAY N'A PAS (RetroArch features)

#### Bloquées (nécessitent C++ mods):
- ⚡ **Run-Ahead** (input lag reduction)
- 🎬 **Video Enable/Disable toggle** (requis pour Run-Ahead)
- 🔊 **Volume Control** (0.0-1.0 scale)
- 📺 **VSync Toggle**

#### Faciles à implémenter (Kotlin only):
- ⏪ **Rewind** (gameplay rewinding)
- 💿 **Disk Swapper UI** (multi-disk games)
- 📸 **Screenshot System**
- 🖼️ **Savestate Thumbnails**

#### Complexes (nécessitent work significatif):
- 🌐 **Netplay** (online multiplayer)
- 🏆 **Achievements** (RetroAchievements.org)
- 🎞️ **Recording/Streaming** (FFmpeg)
- 🎨 **Slang Shaders** (360+ official shaders)

---

## 7. RECOMMANDATIONS (Priorité Impact/Effort)

### 🥇 PRIORITÉ 1: Quick Wins (Kotlin only)

**1. Rewind Manager (2-3h)**
- ✅ Toutes les APIs disponibles
- Haute valeur utilisateur (puzzle games, mistakes)
- Facile à implémenter
- Consommation RAM modérée

**2. Disk Swapper UI (1-2h)**
- ✅ APIs déjà exposées
- Nécessaire pour PSX multi-disk
- Très facile

**3. Screenshot System (2-3h)**
- ✅ Android Bitmap API suffit
- Feature standard attendue
- Facile

**4. Savestate Thumbnails (2-3h)**
- ✅ APIs disponibles
- Améliore UX significativement
- Facile

**TOTAL: ~10h de dev pour 4 features majeures!**

---

### 🥈 PRIORITÉ 2: Modifs C++ (moyennes)

**1. Video Enable/Disable Toggle (3-5h)**
```cpp
// libretrodroid.cpp - AJOUTER
static bool video_rendering_active = true;

void Java_com_swordfish_libretrodroid_LibretroDroid_setVideoEnabled(
    JNIEnv *env, jclass, jboolean enabled) {
    video_rendering_active = enabled;
}

// Dans step() - MODIFIER
void step() {
    retro_run();  // Run core
    
    if (video_rendering_active) {
        // Render frame normalement
        onDrawFrame();
    }
    // Sinon skip rendering (pour Run-Ahead)
}
```

**Benefit:** Unlock Run-Ahead (input lag reduction)

---

**2. Volume Control (3-5h)**
```cpp
static float audio_volume = 1.0f;

void Java_com_swordfish_libretrodroid_LibretroDroid_setAudioVolume(
    JNIEnv *env, jclass, jfloat volume) {
    audio_volume = fmaxf(0.0f, fminf(1.0f, volume));
}

// Dans audio callback - MODIFIER
void audio_sample_batch(const int16_t *data, size_t frames) {
    if (audio_volume != 1.0f) {
        // Multiply samples by volume
        int16_t *adjusted = malloc(frames * 2 * sizeof(int16_t));
        for (size_t i = 0; i < frames * 2; i++) {
            adjusted[i] = (int16_t)(data[i] * audio_volume);
        }
        audio_write(adjusted, frames);
        free(adjusted);
    } else {
        audio_write(data, frames);
    }
}
```

---

**3. VSync Control (3-5h)**
```cpp
void Java_com_swordfish_libretrodroid_LibretroDroid_setVSyncEnabled(
    JNIEnv *env, jclass, jboolean enabled) {
    vsync_enabled = enabled;
    // Update GL swap interval
    eglSwapInterval(enabled ? 1 : 0);
}
```

---

### 🥉 PRIORITÉ 3: Features Complexes (long-terme)

**1. Run-Ahead (10-25h)**
- Nécessite video toggle (Priorité 2 #1)
- Single-Instance: ~10h
- Two-Instance: ~20h
- Haute valeur (input lag reduction)

**2. Slang Shaders (15-20h)**
- Intégrer slang-shaders (360+ shaders)
- Parser .slangp/.slang files
- Support shader parameters
- OpenGL ES 3.0 required

**3. Achievements (20-30h)**
- Intégration RetroAchievements API
- Memory watching
- HTTP sync
- Rich Presence

**4. Netplay (30-40h)**
- Protocol implementation
- Server/client logic
- Input delay sync
- Lobby system

---

## 8. CONCLUSION

### ✅ LibretroDroid EST DÉJÀ TRÈS COMPLET!

**APIs de base exposées:**
- Savestates (serialize/unserialize)
- Core lifecycle (load/reset/destroy)
- Input (keyboard/gamepad/mouse/touch)
- Cheats (set/reset)
- Audio toggle
- Fast Forward (frameSpeed)
- Shaders (built-in)
- System info (aspect ratio, geometry)

### ⚠️ Ce qui manque pour Run-Ahead:

**Une seule API critique:**
```java
// LibretroDroid.java - À AJOUTER
public static native void setVideoEnabled(boolean enabled);
```

**Avec cette API, Run-Ahead devient possible!**

---

### 🎯 PLAN D'ACTION RECOMMANDÉ

#### Phase 1: Quick Wins Kotlin (2 semaines)
1. RewindManager.kt (2-3h)
2. DiskSwapperDialog.kt (1-2h)
3. ScreenshotManager.kt (2-3h)
4. SavestateThumbnails.kt (2-3h)

**Total: ~10h, 4 features majeures, ZÉRO modif C++**

#### Phase 2: Unlock Run-Ahead (1 semaine)
1. Ajouter `setVideoEnabled()` dans libretrodroid.cpp (3-5h)
2. Ajouter `setAudioVolume()` (3-5h)
3. Tester sur plusieurs cores

**Total: ~10h, unlock Run-Ahead**

#### Phase 3: Implémenter Run-Ahead (2-3 semaines)
1. RunAheadManager.kt - Single-Instance (10-15h)
2. UI Settings (2-3h)
3. Smart Config integration (2-3h)
4. Tests & optimisation (5-10h)

**Total: ~25h, Run-Ahead fonctionnel**

---

## 9. SYNTHÈSE PAR FEATURE

| Feature | Difficulté | Temps | APIs manquantes | Valeur Utilisateur |
|---------|------------|-------|-----------------|-------------------|
| **Rewind** | 🟢 Facile | 2-3h | Aucune | ⭐⭐⭐⭐ Haute |
| **Disk Swapper** | 🟢 Facile | 1-2h | Aucune | ⭐⭐⭐ Moyenne |
| **Screenshot** | 🟢 Facile | 2-3h | Aucune | ⭐⭐⭐ Moyenne |
| **Savestate Thumbnails** | 🟢 Facile | 2-3h | Aucune | ⭐⭐⭐⭐ Haute |
| **Run-Ahead** | 🔴 Difficile | 25h | setVideoEnabled | ⭐⭐⭐⭐⭐ Très haute |
| **Volume Control** | 🟡 Moyenne | 3-5h | setAudioVolume | ⭐⭐⭐ Moyenne |
| **VSync Control** | 🟡 Moyenne | 3-5h | setVSync | ⭐⭐ Faible |
| **Slang Shaders** | 🔴 Difficile | 15-20h | Shader pipeline | ⭐⭐⭐⭐ Haute |
| **Achievements** | 🔴 Difficile | 20-30h | HTTP API, memory | ⭐⭐⭐⭐⭐ Très haute |
| **Netplay** | 🔴 Très difficile | 30-40h | Networking stack | ⭐⭐⭐⭐ Haute |

---

## 10. CE QUI EST DÉJÀ MEILLEUR QUE RETROARCH

### 🏆 Innovations RetroPlay

| Feature | RetroArch | RetroPlay | Avantage |
|---------|-----------|-----------|----------|
| **Smart Config** | ❌ Absent | ✅ Metadata-based recommendations | ⭐⭐⭐ |
| **Per-Game Config UI** | ⚠️ Fichiers manuels | ✅ Dialog visuel avec sliders | ⭐⭐⭐⭐ |
| **Database Integration** | ✅ Basic | ✅ CRC auto-lookup + UI | ⭐⭐⭐ |
| **Zapper Calibration** | ⚠️ Basic | ✅ Pixel-perfect + dual crosshair | ⭐⭐⭐⭐⭐ |
| **Quick Actions Bar** | ❌ Absent | ✅ In-game hotkeys visual | ⭐⭐⭐ |
| **Overlay Auto-Discovery** | ⚠️ Manual | ✅ Fallback system intelligent | ⭐⭐⭐ |

**RetroPlay a des features UX que RetroArch n'a PAS!**

---

## RÉSUMÉ FINAL

### ✅ DÉJÀ FAIT (égal ou mieux que RetroArch):
- Savestates, Cheats, Fast Forward, Audio Mute
- Shaders (built-in), Core Options (.cfg)
- Zapper, Overlays, Database, Per-Game Config
- Smart Config (innovation!)

### 🟢 FACILE À FAIRE (Kotlin only, ~10h total):
- Rewind, Disk Swapper, Screenshot, Savestate Thumbnails

### 🟡 MOYEN (C++ mods, ~15h total):
- Video toggle, Volume control, VSync toggle
- → Unlock Run-Ahead

### 🔴 DIFFICILE (long-terme, 50h+):
- Run-Ahead, Slang Shaders, Achievements, Netplay

---

**RECOMMANDATION:** Commencer par Phase 1 (Quick Wins Kotlin)  
**4 features majeures en 10h sans toucher au C++!** 🚀

