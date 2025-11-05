# Run-Ahead Implementation Research

**Date:** 2025-11-05  
**Source:** `c:\repos\RetroArch-master\runahead.c` + `runahead.h`

---

## What is Run-Ahead?

**Run-Ahead** is a latency reduction technique that **removes internal input lag** from emulated games.

### How it works:
1. **Save State**: Save the current core state (RAM, registers, etc.)
2. **Run N Frames Ahead**: Execute the core N frames forward with current input
3. **Render**: Display the frame N frames ahead (reduced lag)
4. **Restore State**: Load the saved state back
5. **Run Normally**: Execute one frame with input and repeat

**Result:** Input appears to respond N frames faster (N × 16.67ms at 60 FPS)

---

## Key Parameters (from `configuration.h`)

```c
unsigned run_ahead_frames;              // 0-12 frames (typically 1-4)
bool run_ahead_enabled;                 // Enable/disable feature
bool run_ahead_secondary_instance;      // Use a second core instance (more accurate but slower)
bool run_ahead_hide_warnings;           // Hide OSD warnings for unsupported cores
bool preemptive_frames_enable;          // Alternative implementation (newer)
```

---

## RetroArch Implementation Details

### From `runahead.c`:

**Main Function:**
```c
void runahead_run(
    void *data,
    int runahead_count,
    bool runahead_hide_warnings,
    bool use_secondary);
```

**Preemptive Frames (newer approach):**
```c
bool preempt_init(void *data);
void preempt_deinit(void *data);
void preempt_run(preempt_t *preempt, void *data);
```

### Requirements:
1. ✅ Core must support `retro_serialize()` (save state)
2. ✅ Core must support `retro_unserialize()` (load state)
3. ✅ Core must support `retro_serialize_size()` (get state size)
4. ⚠️ Core must be **deterministic** (same input = same output)

---

## Cores That Support Run-Ahead

**CONFIRMED WORKING:**
- ✅ FCEUmm (NES)
- ✅ Snes9x (SNES)
- ✅ Genesis Plus GX (Genesis/Mega Drive)
- ✅ Gambatte (Game Boy)
- ✅ mGBA (Game Boy Advance)
- ✅ Mupen64Plus-Next (Nintendo 64)

**NOT RECOMMENDED:**
- ❌ PlayStation cores (too slow, savestates are huge)
- ❌ N64 cores (savestate size can be large)
- ❌ Any core without savestate support

---

## Implementation Strategy for RetroPlay

### Phase 1: LibretroDroid Savestate API ✅ (ALREADY EXISTS?)

Check if LibretroDroid already exposes:
```kotlin
fun serializeSize(): Int
fun serialize(): ByteArray
fun unserialize(state: ByteArray): Boolean
```

**Location:** `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt`

---

### Phase 2: Run-Ahead Manager (Kotlin)

```kotlin
class RunAheadManager(
    private val retroView: GLRetroView,
    private val frames: Int = 1
) {
    private var stateBuffer: ByteArray? = null
    
    fun processFrame() {
        if (frames == 0) {
            // Normal execution
            return
        }
        
        // 1. Save current state
        stateBuffer = retroView.serialize()
        
        // 2. Run N frames ahead
        repeat(frames) {
            retroView.step()  // Execute one frame
        }
        
        // 3. Render is automatic (already displayed)
        
        // 4. Restore state
        stateBuffer?.let { retroView.unserialize(it) }
        
        // 5. Run one frame normally
        retroView.step()
    }
}
```

---

### Phase 3: Settings & UI

**Core Options Addition:**
```
fceumm_runahead_frames = "0"  # 0-4
fceumm_runahead_enabled = "disabled"
```

**Advanced Settings Menu:**
- Enable/Disable Run-Ahead
- Frames (0-4 slider)
- Per-core configuration
- Warning if core doesn't support savestates

---

### Phase 4: Smart Config Integration

```kotlin
fun getSmartConfig(gameInfo: GameInfo): SmartConfig {
    return when (gameInfo.genre) {
        "Fighting" -> SmartConfig(runAheadFrames = 2)
        "Platformer" -> SmartConfig(runAheadFrames = 2)
        "Action" -> SmartConfig(runAheadFrames = 1)
        "Shoot'em Up" -> SmartConfig(runAheadFrames = 2)
        "Puzzle", "RPG", "Strategy" -> SmartConfig(runAheadFrames = 0)
        else -> SmartConfig(runAheadFrames = 1)
    }
}
```

---

## Performance Considerations

### Memory Usage:
- **NES (FCEUmm):** ~2 KB per savestate
- **SNES (Snes9x):** ~130 KB per savestate
- **Genesis (Genesis Plus GX):** ~70 KB per savestate
- **Game Boy (Gambatte):** ~10 KB per savestate

**With 2 frames Run-Ahead:**
- NES: 4 KB
- SNES: 260 KB
- Genesis: 140 KB

**Verdict:** ✅ Très acceptable pour la mémoire mobile

### CPU Usage:
- **Impact:** +100% to +400% CPU (running core 2-5× per frame)
- **NES:** Minimal impact (NES emulation is very light)
- **SNES:** Moderate impact (depends on game)
- **N64/PSX:** ❌ Too slow, not recommended

---

## Testing Plan

1. ✅ Verify LibretroDroid has savestate API
2. ✅ Test FCEUmm savestate size (~2 KB expected)
3. ✅ Implement RunAheadManager
4. ✅ Test with Duck Hunt (lightgun benefits greatly from Run-Ahead!)
5. ✅ Test with platformers (Super Mario Bros, Mega Man)
6. ✅ Measure FPS impact (should stay at 60 FPS for NES)
7. ✅ Add UI toggle in Core Options
8. ✅ Integrate with Smart Config

---

## Next Steps

1. Check LibretroDroid savestate API ✅
2. Create RunAheadManager.kt
3. Add to RetroArchEmulatorActivity
4. Test with FCEUmm (NES)
5. Expand to other cores (SNES, Genesis, GB)

---

## References

- RetroArch source: `c:\repos\RetroArch-master\runahead.c`
- Max frames: 12 (`MAX_RUNAHEAD_FRAMES`)
- Recommended: 1-2 frames for most games
- Fighting games: 2-4 frames for competitive play

