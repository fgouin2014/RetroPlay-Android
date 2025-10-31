# 🔍 RECHERCHE COMPLÈTE - Run Ahead Implementation

**Date:** 31 octobre 2025  
**Méthodologie:** "Nos Rules" - Lecture intégrale sources RetroArch + LibretroDroid  
**Objectif:** Plan d'implémentation 100% fidèle aux specs officielles

---

## 📚 DOCUMENTS LUS (NOS RULES)

### ✅ Sources Officielles Analysées
1. **runahead.md** (163 lignes) - Documentation utilisateur ✅ LUE
2. **runahead.h** (98 lignes) - Header/API ✅ LUE
3. **runahead.c** (1696 lignes) - Implémentation complète ✅ ANALYSÉE
4. **latency.md** (122 lignes) - Context latence ✅ LUE
5. **config.def.h** - Defaults RetroArch ✅ CONSULTÉ
6. **GLRetroView.kt** - API LibretroDroid ✅ ANALYSÉE

**Total:** 3100+ lignes lues et comprises

---

## 🎯 ALGORITHME RETROARCH (100% COMPRIS)

### Mode Single-Instance (preempt_run - ligne 1604-1682)

```
CHAQUE FRAME:

1. Poll input et check si dirty
   - Compare joypad buttons vs frame précédente
   - Compare analog sticks (threshold)
   - Compare pointer/mouse (si utilisés)
   
2. SI input dirty ET frameCount >= runahead_frames:
   a. Suspend audio (AUDIO_FLAG_SUSPENDED)
   b. Désactiver video (VIDEO_FLAG_ACTIVE = false)
   c. Load state from oldest buffer (rollback)
   d. Run 1st ahead frame
   e. Loop: Save state → Run frame (repeat X-1 times)
   f. Re-enable audio/video
   
3. Save current state dans circular buffer
4. Run normal frame (avec audio/video)
5. frameCount++

SI input clean:
  Skip étapes 2a-2f (économise CPU)
  Juste save state + run frame normal
```

**Optimisation critique:** Skip runahead si input identique (idle)

---

### Circular Buffer (Pourquoi ?)

**Buffer size:** `runahead_frames + 1` slots

**Exemple avec 2 frames ahead:**
```
Buffer[3]:
- Slot 0: State frame N-2
- Slot 1: State frame N-1
- Slot 2: State frame N (current)

start_ptr pointe vers le PLUS ANCIEN (Slot 0)

Quand input dirty:
1. Load Slot 0 (rollback 2 frames)
2. Run 2 frames ahead
3. Display frame N+2 (mais logiquement frame N)
```

**Bénéfice:** Latence réduite de 2 frames (33ms @ 60fps)

---

## 🚨 PROBLÈME MAJEUR LIBRETRODROID

### Limitation Découverte

**Dans GLRetroView.kt ligne 289-296:**
```kotlin
override fun onDrawFrame(gl: GL10?) = catchExceptions {
    if (isEmulationReady) {
        LibretroDroid.step(this@GLRetroView)  // ← Appelé automatiquement
        lifecycle?.coroutineScope?.launch {
            retroGLEventsSubject.emit(GLRetroEvents.FrameRendered)
        }
    }
}
```

### ❌ Problèmes Identifiés

1. **Pas de contrôle du game loop**
   - `LibretroDroid.step()` appelé par GL thread automatiquement
   - Impossible d'intercepter AVANT le step
   - Event `FrameRendered` émis APRÈS rendering

2. **Pas de disable audio/video**
   - RetroArch suspend audio/video pendant ahead frames
   - LibretroDroid n'a pas ces flags
   - Overhead sera plus élevé

3. **Thread GL exclusif**
   - Rendering sur GL thread uniquement
   - Serialization/Unserialization doivent être thread-safe
   - Synchronization complexe

---

## 💡 SOLUTIONS POSSIBLES

### Option A: Hook après FrameRendered (COMPLEXE)

```kotlin
lifecycleScope.launch {
    retroView.getGLRetroEvents().collect { event ->
        when (event) {
            is GLRetroEvents.FrameRendered -> {
                // TOO LATE! Frame déjà rendered
                // Il faudrait:
                // 1. Pause rendering
                // 2. Run ahead
                // 3. Resume
                // Mais pas de pause() API!
            }
        }
    }
}
```

❌ **NON VIABLE** - Trop tard dans le pipeline

---

### Option B: Modifier LibretroDroid (LOURD)

**Ajout dans GLRetroView.kt:**
```kotlin
var runAheadCallback: ((GLRetroView) -> Unit)? = null

override fun onDrawFrame(gl: GL10?) = catchExceptions {
    if (isEmulationReady) {
        // AVANT step
        runAheadCallback?.invoke(this)  // ← Hook custom
        
        LibretroDroid.step(this@GLRetroView)
        lifecycle?.coroutineScope?.launch {
            retroGLEventsSubject.emit(GLRetroEvents.FrameRendered)
        }
    }
}
```

**Avantages:**
- ✅ Contrôle complet du timing
- ✅ Peut run ahead AVANT display

**Inconvénients:**
- ❌ Nécessite fork LibretroDroid
- ❌ Maintenance compliquée
- ❌ Updates upstream difficiles

---

### Option C: Run Ahead "Post-Frame" (PRAGMATIQUE)

**Idée:** Run ahead APRÈS la frame, préparer pour frame suivante

```kotlin
lifecycleScope.launch {
    retroView.getGLRetroEvents().collect { event ->
        when (event is GLRetroEvents.FrameRendered) {
            // Frame N vient d'être rendered
            
            if (runAheadEnabled && inputIsDirty) {
                // Save state actuel
                val currentState = retroView.serializeState()
                
                // Run ahead silencieusement (prochain frame sera ahead)
                queueEvent {
                    // Sur GL thread
                    repeat(runAheadFrames) {
                        LibretroDroid.step(retroView)  // Ahead
                    }
                    // Prochain onDrawFrame() affichera frame N+X
                }
            }
        }
    }
}
```

❌ **PROBLÈME:** Timing incorrect, double rendering

---

### ✅ Option D: Hybrid Approach (RECOMMANDÉ)

**Idée:** Combiner state management SANS modifier game loop

```kotlin
// PRINCIPE:
// Ne PAS intercepter le game loop
// MAIS utiliser save/load states INTELLIGEMMENT

class RunAheadHybrid(private val retroView: GLRetroView) {
    
    private var pendingLoadState: ByteArray? = null
    private val stateHistory = ArrayDeque<ByteArray>(maxSize = 3)
    
    // Appelé depuis lifecycleScope (hors GL thread)
    suspend fun processFrame() {
        // 1. Si load pending, load
        pendingLoadState?.let { state ->
            retroView.queueEvent {
                retroView.unserializeState(state)
            }
            pendingLoadState = null
        }
        
        // 2. Save state current (async)
        val currentState = withContext(Dispatchers.IO) {
            retroView.serializeState()
        }
        
        // 3. Ajouter à history
        stateHistory.addFirst(currentState)
        if (stateHistory.size > 3) stateHistory.removeLast()
        
        // 4. Si input dirty, prepare rollback pour next frame
        if (inputDirtyThisFrame) {
            // Load le state d'il y a 2 frames au PROCHAIN frame
            pendingLoadState = stateHistory[2]  // State from frame N-2
        }
    }
}
```

⚠️ **PROBLÈME:** Rollback au frame SUIVANT, pas immédiat

---

## 🚨 CONCLUSION RECHERCHE

### Faisabilité Run Ahead dans RetroPlay

**API LibretroDroid disponible:**
- ✅ `serializeState()` - Fonctionne
- ✅ `unserializeState()` - Fonctionne
- ✅ `step()` - Existe
- ❌ **Hook pre-frame** - N'EXISTE PAS
- ❌ **Disable audio/video** - N'EXISTE PAS
- ❌ **Game loop control** - N'EXISTE PAS

**Status:** ⚠️ **PARTIELLEMENT FAISABLE**

---

## 💡 ALTERNATIVES VIABLES

### Alternative 1: Run Ahead "Best Effort"

**Approche:**
- Calculer ahead frames en background thread
- Espérer que timing align
- Accepter imperfections

**Bénéfice:** 50-70% du bénéfice Run Ahead complet  
**Effort:** 15-20h  
**Risk:** Timing issues, glitches

---

### Alternative 2: Préemptive Frames Simplifié

**Approche:**
- Seulement sauvegarder states (pas de ahead)
- Rollback rapide si input change
- Mode "save buffering"

**Bénéfice:** 20-30% du bénéfice  
**Effort:** 8-10h  
**Risk:** Faible

---

### Alternative 3: Fork LibretroDroid

**Approche:**
- Créer LibretroDroid-RunAhead fork
- Ajouter hooks nécessaires
- Full control

**Bénéfice:** 100% du bénéfice (comme RetroArch)  
**Effort:** 30-40h (fork + impl + maintenance)  
**Risk:** Maintenance long-terme

---

## 🎯 RECOMMANDATION

### Pour Implémenter Run Ahead Correctement

**IL FAUT:**
1. ✅ Hook pre-frame dans GLRetroView
2. ✅ Disable audio/video pendant ahead frames
3. ✅ Control exact du game loop timing

**ACTUELLEMENT:**
❌ LibretroDroid ne permet AUCUN de ces 3 requis

### Options Réalistes

**A. Abandonner Run Ahead** ❌ (feature trop importante)

**B. Fork LibretroDroid** ✅ (effort élevé mais résultat parfait)
- Créer `LibretroDroid-RetroPlay` fork
- Ajouter `onPreFrame` callback
- Ajouter `audioEnabled` / `videoEnabled` runtime flags
- Estimation: 30-40h initial + maintenance

**C. Implémenter alternative "Best Effort"** ⚠️ (compromis)
- Background state buffering
- Timing imparfait
- 50-70% du bénéfice
- Estimation: 15-20h

**D. Contacter auteur LibretroDroid** 💡 (long terme)
- Proposer PR avec Run Ahead support
- Collaboration upstream
- Bénéfice pour Lemuroid aussi

---

## 📊 COMPARAISON OPTIONS

| Option | Effort | Bénéfice | Maintenance | Risk |
|--------|--------|----------|-------------|------|
| **A. Abandonner** | 0h | 0% | - | - |
| **B. Fork** | 30-40h | 100% | Haute | Moyen |
| **C. Best Effort** | 15-20h | 50-70% | Basse | Élevé (glitches) |
| **D. PR Upstream** | 40-60h | 100% | Nulle | Faible |

---

## 🎮 QUE FAIRE MAINTENANT ?

**Mes recommandations:**

### Court Terme
**Implémenter les Quick Wins d'abord** (Audio Mute + Fast Forward)
- Résultat immédiat
- Pas de blocage technique
- 3-5h de travail

### Moyen Terme
**Évaluer fork LibretroDroid** pour Run Ahead
- Lire code complet LibretroDroid
- Identifier modifications minimales requises
- Prototype sur branche séparée

### Long Terme
**Proposer PR à Swordfish90** (auteur LibretroDroid)
- Run Ahead bénéficie aussi à Lemuroid
- Feature demandée par communauté
- Collaboration win-win

---

**Voulez-vous:**

1. **Continuer recherche Run Ahead** (approfondir fork feasibility)
2. **Implémenter Quick Wins** (Audio + Fast Forward) 
3. **Autre chose** (dites-moi)

**Ma suggestion:** Faire les Quick Wins maintenant, puis évaluer fork LibretroDroid après. 🎮

