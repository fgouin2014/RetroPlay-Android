# Comparaison Visuelle: RetroArch vs RetroPlay Turbo

## 1. TIMELINE DU CYCLE TURBO

### RetroArch (10 Hz, Period=6, DutyCycle=3)

```
Time (ms):  0    16   33   50   67   83  100  116  133  150  167  183  200
Frame:      0     1    2    3    4    5    6    7    8    9   10   11   12
            │────────────────│──────────────│────────────────│─────────────
State:      ███████████████████            ███████████████████
            │<-  50ms ON  ->│<- 50ms OFF ->│<-  50ms ON  ->│
            
Button:     PRESS──────────RELEASE────────PRESS──────────RELEASE───────
            
Frequency:  10 Hz (10 presses/second)
Period:     100ms
Duty:       50% (3/6 frames)
```

### RetroPlay Actuel (30 Hz, 33ms ON/OFF)

```
Time (ms):  0    16   33   50   67   83  100  116  133  150  167  183  200
            │─────────│─────────│─────────│─────────│─────────│─────────│
State:      ██████████         ██████████         ██████████
            │<-33ms->│<-33ms->│<-33ms->│<-33ms->│<-33ms->│
            
Button:     PRESS───RELEASE─PRESS───RELEASE─PRESS───RELEASE─
            
Frequency:  30 Hz (30 presses/second)
Period:     33ms
Duty:       50%
```

### Différence visuelle

```
RetroArch:   █████─────█████─────█████─────  (10 Hz - Lent, réaliste)
RetroPlay:   ██─██─██─██─██─██─██─██─██─██─  (30 Hz - Rapide, agressif)
```

---

## 2. ARCHITECTURE DU SYSTÈME

### RetroArch: Intégration Driver

```
┌─────────────────────────────────────────────────────────┐
│                    RetroArch Core                       │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │  input_driver_poll()  [Chaque frame]           │    │
│  │                                                 │    │
│  │  1. Poll hardware devices                      │    │
│  │  2. turbo_btns.count++                         │    │
│  │  3. Detect turbo button (frame_enable)         │    │
│  └─────────────────┬───────────────────────────────┘    │
│                    │                                     │
│  ┌─────────────────▼───────────────────────────────┐    │
│  │  input_state_wrapper()                          │    │
│  │                                                 │    │
│  │  IF turbo enabled for button:                  │    │
│  │    res = (count % period) < duty_cycle         │    │
│  │  ELSE:                                         │    │
│  │    res = hardware_state                        │    │
│  └─────────────────┬───────────────────────────────┘    │
│                    │                                     │
│  ┌─────────────────▼───────────────────────────────┐    │
│  │  Core receives modulated input                  │    │
│  │  (Transparent turbo injection)                  │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘

Global State:
┌──────────────────────────────────────┐
│ turbo_btns {                         │
│   count: 12453 (global frame counter)│
│   enable[0]: 0b0000000000000001  (A) │
│   enable[1]: 0b0000000000010000  (R) │
│   frame_enable[0]: true              │
│   mode1_enable[0]: false             │
│ }                                    │
└──────────────────────────────────────┘
```

### RetroPlay: Event Layer

```
┌─────────────────────────────────────────────────────────┐
│              RetroPlay Overlay Renderer                 │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │  Touch Event Handler                           │    │
│  │                                                 │    │
│  │  ACTION_DOWN on turbo button:                  │    │
│  │    turboState[action] = true                   │    │
│  │    onButtonPress(action)                       │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │  Turbo Coroutine (LaunchedEffect)              │    │
│  │                                                 │    │
│  │  while(true) {                                 │    │
│  │    delay(33) // 33ms                           │    │
│  │    forEach turbo button:                       │    │
│  │      toggle state                              │    │
│  │      if ON:  onButtonPress(action)             │    │
│  │      if OFF: onButtonRelease(action)           │    │
│  │  }                                             │    │
│  └─────────────────┬───────────────────────────────┘    │
│                    │                                     │
│  ┌─────────────────▼───────────────────────────────┐    │
│  │  onButtonPress/Release callbacks                │    │
│  │  → sendKeyEvent() to LibretroDroid              │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘

Per-Button State:
┌──────────────────────────────────────┐
│ turboState {                         │
│   "a": true  (currently ON)          │
│   "b": false (currently OFF)         │
│ }                                    │
│ Each button has independent timer    │
└──────────────────────────────────────┘
```

---

## 3. MODES TURBO RETROARCH

### Mode 0: CLASSIC (Hold)

```
Turbo Button: ████████████████████████████            ████████████
Game Button:        ████████      ████            ████████    ████
Result:             ██  ██        ██  ██          ██  ██      ██
                    ON  OFF       ON  OFF         ON  OFF     ON OFF

Logic: IF turbo_held AND game_button_pressed THEN modulate(game_button)
```

### Mode 1: CLASSIC_TOGGLE

```
Turbo Button: █     █         █
Game Button:    ████████████████████████████████████████████
Turbo State:    [OFF][ON──────────────][OFF──────────]
Result:              ██  ██  ██  ██  ██
                     ON  OFF ON  OFF ON

Logic: Press turbo+button to toggle ON/OFF, stays ON after release
```

### Mode 2: SINGLEBUTTON

```
Turbo Button: █     █         █
Button A:       ████
Button B:                 ████
Button C:                         ████
Turbo State:    A────     B────     C────
Result:         ██  ██    ██  ██    ██  ██

Logic: Only ONE button can have turbo at a time (exclusive)
```

### Mode 3: SINGLEBUTTON_HOLD

```
Turbo Button: █     █         █
Button A:       ██████
Button B:                 ██
Turbo State:    [A ON─] [OFF] [B]
Result:         ██  ██        █

Logic: Like SINGLEBUTTON but stops when game button released
```

---

## 4. FLUX DE DONNÉES

### RetroArch: Modulation au niveau driver

```
Physical Input → Input Driver → Turbo Modulation → Core Input State
                                      ↑
                                Frame Counter
                                (Global, tous ports)

Example pour le bouton A:
Frame 0: Hardware=1 → Turbo Check → (0 % 6) < 3 = TRUE  → Core sees: 1
Frame 1: Hardware=1 → Turbo Check → (1 % 6) < 3 = TRUE  → Core sees: 1
Frame 2: Hardware=1 → Turbo Check → (2 % 6) < 3 = TRUE  → Core sees: 1
Frame 3: Hardware=1 → Turbo Check → (3 % 6) < 3 = FALSE → Core sees: 0
Frame 4: Hardware=1 → Turbo Check → (4 % 6) < 3 = FALSE → Core sees: 0
Frame 5: Hardware=1 → Turbo Check → (5 % 6) < 3 = FALSE → Core sees: 0
Frame 6: Hardware=1 → Turbo Check → (6 % 6) < 3 = TRUE  → Core sees: 1
```

### RetroPlay: Events au niveau overlay

```
Touch Input → Overlay Renderer → Turbo Timer → Press/Release Events → Core
                                      ↑
                                Independent per button

Example pour le bouton A:
T=0ms:   Touch DOWN  → turboState[A]=true  → sendKeyEvent(DOWN, A)
T=33ms:  Timer tick  → toggle to false     → sendKeyEvent(UP, A)
T=66ms:  Timer tick  → toggle to true      → sendKeyEvent(DOWN, A)
T=99ms:  Timer tick  → toggle to false     → sendKeyEvent(UP, A)
T=132ms: Timer tick  → toggle to true      → sendKeyEvent(DOWN, A)
```

---

## 5. PERFORMANCE COMPARISON

### Presses par seconde par fréquence

```
Hz   |  Presses/sec  |  Use Case
-----|---------------|------------------------------------------
 5   |      5        |  Very slow, deliberate (puzzle games)
10   |     10        |  RetroArch default (realistic turbo)
15   |     15        |  Fast turbo (shmups)
20   |     20        |  Very fast (fighting games)
30   |     30        |  RetroPlay current (maximum speed)
60   |     60        |  Theoretical max (1 press per frame)
```

### Visual @ 60 FPS

```
5 Hz:   █████████████           █████████████           
        │<--- 200ms --->│       │<--- 200ms --->│

10 Hz:  ██████     ██████     ██████     ██████
        │<-100ms->│<-100ms->│<-100ms->│

15 Hz:  ████   ████   ████   ████   ████   ████
        │<67ms>│<67ms>│<67ms>│

30 Hz:  ██ ██ ██ ██ ██ ██ ██ ██ ██ ██ ██ ██
        │33│33│33│33│33│33│33│33│33│33│

60 Hz:  ██████████████████████████████████████
        (Every frame, may break some games)
```

---

## 6. ÉTAT MÉMOIRE

### RetroArch: Compact (112 bytes @ MAX_USERS=16)

```c
struct turbo_buttons {
   int32_t turbo_pressed[16];  // 64 bytes (4 * 16)
   unsigned count;             //  4 bytes
   uint16_t enable[16];        // 32 bytes (2 * 16)
   bool frame_enable[16];      // 16 bytes (1 * 16)
   bool mode1_enable[16];      // 16 bytes (1 * 16)
};
// Total: 132 bytes (single global instance)
```

### RetroPlay: Dynamic (HashMap)

```kotlin
val turboState = mutableStateMapOf<String, Boolean>()
// Size: 24 bytes (header) + N * (String + Boolean)
// Example avec 8 boutons turbo: ~200 bytes

// Avantage: Flexible, pas de limite de boutons
// Inconvénient: Allocation dynamique, overhead HashMap
```

---

## 7. RECOMMANDATION D'IMPLÉMENTATION

### Proposition: Mode hybride configurable

```kotlin
data class TurboConfig(
   val mode: TurboMode = TurboMode.RETROARCH_COMPATIBLE,
   val frequency: Int = 10,  // Hz
   val dutyCycle: Float = 0.5f  // 50%
)

enum class TurboMode {
   RETROARCH_COMPATIBLE,  // 10 Hz, frame-based
   FAST,                  // 30 Hz, actuel
   CUSTOM                 // User-defined
}

// Implementation
private var frameCounter = 0L
private val frameDuration = (1000.0 / 60.0).toLong()  // ~16ms

LaunchedEffect(Unit) {
   while (true) {
      delay(frameDuration)
      frameCounter++
      
      turboButtons.forEach { (action, config) ->
         val period = (60 / config.frequency).toLong()
         val dutyCycle = (period * config.dutyCycle).toLong()
         val isPressed = (frameCounter % period) < dutyCycle
         
         if (isPressed) onButtonPress(action)
         else onButtonRelease(action)
      }
   }
}
```

### Configuration dans overlay .cfg

```cfg
# Bouton A: Turbo compatible RetroArch
overlay0_desc42 = "a,200,400,radial,0.1,0.1"
overlay0_desc42_turbo = "true"
overlay0_desc42_turbo_frequency = "10"  # Hz (RetroArch default)

# Bouton B: Turbo rapide
overlay0_desc43 = "b,300,400,radial,0.1,0.1"
overlay0_desc43_turbo = "true"
overlay0_desc43_turbo_frequency = "30"  # Hz (mode rapide)

# Bouton X: Turbo custom
overlay0_desc44 = "x,400,400,radial,0.1,0.1"
overlay0_desc44_turbo = "true"
overlay0_desc44_turbo_frequency = "15"  # Hz (shmups)
overlay0_desc44_turbo_duty_cycle = "0.33"  # 33% ON, 67% OFF
```

---

## 8. TESTS RECOMMANDÉS

### Jeux pour tester le turbo

1. **Contra (NES)** - 10 Hz optimal
   - RetroArch: Confortable, réaliste
   - RetroPlay 30Hz: Trop rapide, peut causer bugs

2. **Street Fighter II (SNES)** - 15-20 Hz optimal
   - RetroArch: Un peu lent pour les combos
   - RetroPlay 30Hz: Bon pour rapid punches

3. **Gradius (NES)** - 10-15 Hz optimal
   - RetroArch: Parfait pour le rapid fire
   - RetroPlay 30Hz: Trop rapide, lag possible

4. **Mega Man (NES)** - 10 Hz optimal
   - RetroArch: Équilibré
   - RetroPlay 30Hz: Facilite trop le jeu

### Métriques à mesurer

```
Test Setup:
- Game: Contra (NES)
- Action: Hold A button (shoot)
- Duration: 10 seconds
- Frame rate: 60 FPS stable

RetroArch (10 Hz):
- Bullets fired: ~100 (10/sec)
- Feel: Realistic, comfortable
- CPU usage: Negligible
- Game compatibility: Excellent

RetroPlay (30 Hz):
- Bullets fired: ~300 (30/sec)
- Feel: Very fast, aggressive
- CPU usage: Low (timer overhead)
- Game compatibility: Some games lag
```

---

## 9. MIGRATION PLAN

### Phase 1: Ajouter support fréquence configurable

```kotlin
// Nouveau champ dans OverlayButton
data class OverlayButton(
   // ... existing fields ...
   val turbo: Boolean = false,
   val turboFrequency: Int = 10,  // NEW: Hz (default RetroArch)
   val turboDutyCycle: Float = 0.5f  // NEW: 0.0-1.0
)
```

### Phase 2: Remplacer timer par frame counter

```diff
- LaunchedEffect(Unit) {
-    while (true) {
-       delay(33)  // Fixed 30 Hz
+ private var frameCounter = 0L
+ LaunchedEffect(Unit) {
+    while (true) {
+       delay(16)  // ~60 FPS
+       frameCounter++
```

### Phase 3: Appliquer modulation RetroArch

```diff
- val newState = !currentState
- turboState[action] = newState
- if (newState) onButtonPress(action) else onButtonRelease(action)

+ val period = (60 / button.turboFrequency).toLong()
+ val duty = (period * button.turboDutyCycle).toLong()
+ val isPressed = (frameCounter % period) < duty
+ if (isPressed) onButtonPress(action) else onButtonRelease(action)
```

### Phase 4: Backward compatibility

```kotlin
// Auto-migration des anciens overlays
if (button.turbo && button.turboFrequency == 0) {
   button.turboFrequency = 30  // Keep current behavior
}
```

---

## 10. CONCLUSION

### Tableau comparatif final

| Critère | RetroArch | RetroPlay Actuel | Recommandation |
|---------|-----------|------------------|----------------|
| **Fréquence** | 10 Hz (configurable) | 30 Hz (fixe) | 10 Hz default, configurable |
| **Réalisme** | Excellent | Moyen | Adopter RetroArch |
| **Performance** | Optimal | Bon | Équivalent |
| **Compatibilité** | Universelle | Bonne | Améliorer |
| **Flexibilité** | 4 modes | 1 mode | Commencer par 1, évoluer |
| **Simplicité** | Moyenne | Excellente | Garder simple |

### Recommandation finale

**Adopter l'approche RetroArch avec simplification:**

1. ✅ Utiliser 10 Hz comme défaut (réalisme)
2. ✅ Permettre configuration par bouton dans .cfg
3. ✅ Utiliser frame counter au lieu de timer
4. ⚠️ Garder 1 mode simple pour commencer (pas les 4 modes)
5. ✅ Backward compatibility avec 30 Hz actuel

**Code minimal suggéré:**

```kotlin
// Simple et compatible
val period = (60 / turboFrequency).coerceAtLeast(2)  // Min 2 frames
val isPressed = (frameCounter % period) < (period / 2)  // 50% duty
if (isPressed != lastState) {
   if (isPressed) onButtonPress(action) else onButtonRelease(action)
   lastState = isPressed
}
```

---

**FIN DE LA COMPARAISON VISUELLE**

