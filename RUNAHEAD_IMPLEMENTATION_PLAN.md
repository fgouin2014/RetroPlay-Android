# ⚡ RUN AHEAD - Plan d'Implémentation Complet

**Date:** 31 octobre 2025  
**Méthodologie:** "Nos Rules" - Implémentation 100% fidèle RetroArch  
**Sources:** `runahead.c` (1696 lignes), `runahead.h` (98 lignes), `runahead.md` (163 lignes)  
**Durée estimée:** 10-15h (Single-Instance), 20-25h (Two-Instance)

---

## 📚 RECHERCHE APPROFONDIE (NOS RULES)

### Documents Lus Intégralement
- ✅ `c:\repos\docs-master\docs\guides\runahead.md` (163 lignes)
- ✅ `c:\repos\RetroArch-master\runahead.h` (98 lignes)
- ✅ `c:\repos\RetroArch-master\runahead.c` (1696 lignes - ANALYSÉ)

### Concepts Clés Identifiés

#### 1. Input Lag Internal
**Problème:** Jeux ont 1-6 frames de lag interne entre input et affichage

**Exemples:**
- Super Mario Bros (NES): 1 frame (optimal)
- Street Fighter II (SNES): 2-3 frames
- Crash Bandicoot (PSX): 3-4 frames

**Solution Run Ahead:** Calculer frames en avance, afficher frame N+X au lieu de N

---

#### 2. Deux Modes d'Opération

##### Mode Single-Instance (Plus Simple)
```
Frame N:
1. Save state → buffer
2. Run ahead X frames (audio/video disabled)
3. Display frame N+X (avec audio/video)
4. Load state from buffer (rollback)

Frame N+1:
Repeat
```

**Overhead:** 30-40% CPU (save/load constant)

##### Mode Two-Instance (Optimal)
```
Primary Core:
- Audio ONLY
- Save state

Secondary Core:
- Load state from primary
- Run ahead X frames (video ONLY, audio disabled)
- Display frame

Optimization: Si input clean, skip reload state
```

**Overhead:** 20-25% CPU + 2x RAM

---

## 🔍 ALGORITHME DÉTAILLÉ (runahead.c)

### Fonction Principale: `preempt_run()` (ligne 1604-1682)

```c
void preempt_run(preempt_t *preempt, runloop_state_t *runloop_st) {
    // 1. Poll input et détecter si dirty
    preempt_input_poll(preempt, runloop_st, max_users);
    
    // 2. Si input dirty ET assez de frames calculées
    if (INPUT_IS_DIRTY && frame_count >= frames) {
        // 2a. Suspend audio/video
        audio_suspended = true;
        video_active = false;
        
        // 2b. Load state from oldest buffer
        retro_unserialize(buffer[start_ptr], state_size);
        
        // 2c. Run first ahead frame
        retro_run();
        replay_ptr = NEXT(start_ptr);
        
        // 2d. Run remaining ahead frames + save states
        while (replay_ptr != start_ptr) {
            retro_serialize(buffer[replay_ptr], state_size);  // Save
            retro_run();  // Run
            replay_ptr = NEXT(replay_ptr);
        }
        
        // 2e. Re-enable audio/video
        audio_suspended = false;
        video_active = true;
    }
    
    // 3. Save current state (circular buffer)
    retro_serialize(buffer[start_ptr], state_size);
    start_ptr = NEXT(start_ptr);
    
    // 4. Run normal frame (avec audio/video)
    retro_run();
    frame_count++;
}
```

### Input Dirty Detection (ligne 1554-1592)

```c
void preempt_input_poll(preempt_t *preempt, ...) {
    for (each port) {
        // 1. Check joypad (bitmask tous boutons)
        int16_t joypad_state = state_cb(port, JOYPAD, 0, MASK);
        if (joypad_state != preempt->joypad_state[port]) {
            preempt->joypad_state[port] = joypad_state;
            INPUT_IS_DIRTY = true;  // ← Trigger runahead
        }
        
        // 2. Check analogs (si utilisés)
        if (analog_mask[port] && analog_dirty(port)) {
            INPUT_IS_DIRTY = true;
        }
        
        // 3. Check pointer/mouse/lightgun (si utilisés)
        if (ptr_dev_needed[port] && pointer_dirty(port)) {
            INPUT_IS_DIRTY = true;
        }
    }
}
```

**CRITIQUE:** Ne run ahead QUE si input change ! Si idle, skip overhead.

---

## 🎯 ARCHITECTURE RETROPLAY

### Nouvelle Classe: RunAheadManager.kt

```kotlin
package com.retroplay.runahead

import android.util.Log
import com.swordfish.libretrodroid.GLRetroView

/**
 * Run Ahead Manager - Réduit input lag en calculant frames en avance
 * 
 * Basé sur l'implémentation RetroArch officielle (runahead.c)
 * Mode: Single-Instance (Two-Instance TODO)
 * 
 * @param retroView Instance GLRetroView principale
 * @param frames Nombre de frames à calculer en avance (1-6, recommandé: 2)
 */
class RunAheadManager(
    private val retroView: GLRetroView,
    private var frames: Int = 2
) {
    companion object {
        private const val TAG = "RunAheadManager"
        const val MAX_RUNAHEAD_FRAMES = 12  // Comme RetroArch
    }
    
    // Circular buffer pour states (comme RetroArch)
    private val stateBuffers = Array<ByteArray?>(MAX_RUNAHEAD_FRAMES) { null }
    private var startPtr = 0  // Index buffer le plus ancien
    private var stateSize: Long = 0  // Taille d'un state
    private var frameCount: Long = 0  // Frames calculées depuis init
    
    // Input state tracking (dirty detection)
    private data class InputState(
        var joypadButtons: IntArray = IntArray(4) { 0 },  // 4 ports max
        var analogX: FloatArray = FloatArray(4) { 0f },
        var analogY: FloatArray = FloatArray(4) { 0f },
        var pointerX: FloatArray = FloatArray(4) { 0f },
        var pointerY: FloatArray = FloatArray(4) { 0f },
        var pointerPressed: BooleanArray = BooleanArray(4) { false }
    )
    private var lastInputState = InputState()
    private var inputIsDirty = false
    
    // Stats
    private var totalFrames = 0L
    private var skippedFrames = 0L  // Frames où input clean = pas de runahead
    
    /**
     * Initialize Run Ahead
     * Détecte la taille de state et alloue buffers
     */
    fun initialize(): Boolean {
        try {
            // Détecter taille state
            val testState = retroView.serializeState()
            if (testState.isEmpty()) {
                Log.e(TAG, "Failed to serialize state - core may not support savestates")
                return false
            }
            
            stateSize = testState.size.toLong()
            Log.i(TAG, "Run Ahead initialized: state_size=${stateSize} bytes, frames=$frames")
            
            // Pré-allouer buffers (éviter allocations en runtime)
            for (i in 0 until frames + 1) {
                stateBuffers[i] = ByteArray(stateSize.toInt())
            }
            
            frameCount = 0
            startPtr = 0
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Run Ahead: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if input changed depuis last frame
     * Basé sur preempt_input_poll() de RetroArch
     */
    private fun checkInputDirty(
        currentJoypad: IntArray,
        currentAnalogX: FloatArray,
        currentAnalogY: FloatArray
    ): Boolean {
        var dirty = false
        
        // Check joypad buttons (4 ports)
        for (port in 0..3) {
            if (currentJoypad[port] != lastInputState.joypadButtons[port]) {
                Log.v(TAG, "[DIRTY] Joypad port $port changed: ${lastInputState.joypadButtons[port]} → ${currentJoypad[port]}")
                lastInputState.joypadButtons[port] = currentJoypad[port]
                dirty = true
            }
        }
        
        // Check analog sticks (threshold 0.1 pour éviter drift)
        for (port in 0..3) {
            val deltaX = kotlin.math.abs(currentAnalogX[port] - lastInputState.analogX[port])
            val deltaY = kotlin.math.abs(currentAnalogY[port] - lastInputState.analogY[port])
            
            if (deltaX > 0.1f || deltaY > 0.1f) {
                Log.v(TAG, "[DIRTY] Analog port $port changed: ($deltaX, $deltaY)")
                lastInputState.analogX[port] = currentAnalogX[port]
                lastInputState.analogY[port] = currentAnalogY[port]
                dirty = true
            }
        }
        
        return dirty
    }
    
    /**
     * Process un frame avec Run Ahead
     * Appelé depuis le game loop à la place de retroView.step()
     * 
     * Algorithme basé sur preempt_run() - ligne 1604-1682 de runahead.c
     */
    fun processFrame(
        currentJoypad: IntArray,
        currentAnalogX: FloatArray,
        currentAnalogY: FloatArray
    ) {
        totalFrames++
        
        // 1. Check si input dirty
        inputIsDirty = checkInputDirty(currentJoypad, currentAnalogX, currentAnalogY)
        
        // 2. Si input dirty ET buffer prêt, run ahead
        if (inputIsDirty && frameCount >= frames) {
            Log.d(TAG, "[RUNAHEAD] Input dirty! Running $frames frames ahead...")
            
            // 2a. Load state from oldest buffer (rollback)
            val oldestBuffer = stateBuffers[startPtr]
            if (oldestBuffer != null) {
                val success = retroView.unserializeState(oldestBuffer)
                if (!success) {
                    Log.e(TAG, "[RUNAHEAD] Failed to load state - disabling for this session")
                    // TODO: Disable runahead si échec
                    return
                }
            }
            
            // 2b. Run ahead frames (sans audio/video si possible)
            // LibretroDroid n'a pas de disable audio/video, donc on run normalement
            // L'overhead sera un peu plus élevé que RetroArch
            var replayPtr = nextPtr(startPtr)
            
            repeat(frames - 1) {
                // Run frame
                retroView.step(retroView)
                
                // Save intermediate state
                val state = retroView.serializeState()
                stateBuffers[replayPtr] = state
                
                replayPtr = nextPtr(replayPtr)
            }
            
            // 2c. Run final ahead frame (celui qu'on affichera)
            // Ce frame aura audio/video actifs
            
        } else {
            // Input clean - skip runahead overhead
            skippedFrames++
            if (totalFrames % 300 == 0L) {  // Log toutes les 5s à 60fps
                val skipRatio = (skippedFrames * 100 / totalFrames)
                Log.d(TAG, "[RUNAHEAD] Stats: ${skipRatio}% frames skipped (input clean)")
            }
        }
        
        // 3. Save current state (circular buffer)
        val currentState = retroView.serializeState()
        stateBuffers[startPtr] = currentState
        startPtr = nextPtr(startPtr)
        
        // 4. Run normal frame (LibretroDroid gère audio/video automatiquement)
        // NOTE: Ce step est fait APRÈS dans le game loop
        
        frameCount++
    }
    
    /**
     * Circular buffer pointer (modulo)
     */
    private fun nextPtr(current: Int): Int {
        return (current + 1) % (frames + 1)
    }
    
    /**
     * Update nombre de frames
     */
    fun setFrames(newFrames: Int) {
        if (newFrames in 0..MAX_RUNAHEAD_FRAMES) {
            frames = newFrames
            Log.i(TAG, "Run Ahead frames updated: $frames")
            // Reset pour éviter buffer corruption
            initialize()
        }
    }
    
    /**
     * Cleanup
     */
    fun destroy() {
        for (i in stateBuffers.indices) {
            stateBuffers[i] = null
        }
        Log.i(TAG, "Run Ahead destroyed. Stats: $totalFrames total, $skippedFrames skipped (${skippedFrames*100/totalFrames}%)")
    }
}
```

---

## 🎮 INTÉGRATION DANS RETROPLAY

### 1. Modification RetroArchEmulatorActivity.kt

```kotlin
// Ligne ~120 - Propriétés
private var runAheadManager: RunAheadManager? = null
private var runAheadEnabled = false
private var runAheadFrames = 2  // Default: 2 frames (33ms à 60fps)

// Ligne ~830 - Après création retroView
if (runAheadEnabled) {
    runAheadManager = RunAheadManager(retroView, runAheadFrames)
    val initialized = runAheadManager?.initialize() ?: false
    
    if (!initialized) {
        Log.w(TAG, "[RUNAHEAD] Failed to initialize - core may not support savestates")
        Toast.makeText(this, "Run Ahead not available for this core", Toast.LENGTH_LONG).show()
        runAheadEnabled = false
        runAheadManager = null
    } else {
        Log.i(TAG, "[RUNAHEAD] Enabled: $runAheadFrames frames ahead")
        Toast.makeText(this, "Run Ahead: $runAheadFrames frames (-${runAheadFrames * 16}ms @ 60fps)", Toast.LENGTH_SHORT).show()
    }
}

// Ligne ~1000 - Charger settings
runAheadEnabled = prefs.getBoolean("emulation_run_ahead_enabled", false)
runAheadFrames = prefs.getInt("emulation_run_ahead_frames", 2).coerceIn(1, 6)

// Ligne onDestroy()
override fun onDestroy() {
    runAheadManager?.destroy()
    super.onDestroy()
}
```

---

### 2. Integration Game Loop

**PROBLÈME:** LibretroDroid n'expose PAS le game loop !

```kotlin
// GLRetroView.kt gère le loop en interne via GLSurfaceView
// Pas de hook pour intercepter chaque frame
```

**SOLUTION:** Utiliser `GLRetroView.setOnFrameListener()` ?

Vérifions si existe :

<function_calls>
<invoke name="grep">
<parameter name="pattern">onFrame|FrameListener|setOnFrame|frame.*callback
