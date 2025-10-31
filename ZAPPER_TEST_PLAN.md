# 🧪 Plan de Tests Zapper - Option A

**Date:** 31 octobre 2025  
**Objectif:** Valider les 4 corrections critiques appliquées  
**Durée estimée:** 30-45 minutes  
**Prérequis:** ROM Duck Hunt (NES)

---

## 📋 PRÉPARATION (10 minutes)

### Étape 1: Compilation & Installation
```bash
cd c:\androidProject\ChatAI-Android-beta\RetroPlay-Android

# Compilation
.\gradlew clean
.\gradlew assembleDebug

# Installation
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Vérifier installation
adb shell pm list packages | Select-String "retroplay"
```

**Résultat attendu:** Package `com.retroplay` installé ✅

---

### Étape 2: Préparer ADB Logcat
```powershell
# Dans un terminal séparé (laisser tourner pendant les tests)
adb logcat -c  # Clear logs
adb logcat | Select-String "NativeComposeEmulator|ZAPPER|BOUNDS|POINTER"
```

**Résultat attendu:** Logs filtré prêt à capturer events Zapper

---

### Étape 3: Lancer Duck Hunt
```
1. Ouvrir RetroPlay sur device
2. Naviguer vers console "NES"
3. Sélectionner "Duck Hunt"
4. Appuyer "PLAY NATIVE"
```

**Résultat attendu:** Jeu se lance

---

## 🎯 TEST 1: Configuration Automatique POINTER (5 min)

### Objectif
Vérifier que `setControllerType(1, 6)` est appelé automatiquement

### Procédure
1. Duck Hunt se lance
2. Observer le Toast message
3. Vérifier les logs

### Résultats Attendus

#### Toast ✅
```
"Zapper enabled! Tap screen to shoot"
```
**Durée:** 2-3 secondes après lancement

#### Logs ✅
```
I/NativeComposeEmulator: [NES] Zapper game detected: Duck Hunt
I/NativeComposeEmulator: [NES] Zapper configured as RETRO_DEVICE_POINTER on port 2
```

#### Si Erreur ❌
```
E/NativeComposeEmulator: [NES] Failed to configure Zapper: [message]
```
**Action:** Noter le message d'erreur, vérifier LibretroDroid API

### Critères de Succès
- [✅] Toast affiché
- [✅] Log "configured as RETRO_DEVICE_POINTER"
- [✅] Aucune erreur

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 2: Capture Bounds GLRetroView (5 min)

### Objectif
Vérifier que `onGloballyPositioned()` capture les bounds correctement

### Procédure
1. Duck Hunt lancé en **LANDSCAPE**
2. Vérifier logs bounds
3. Rotation device en **PORTRAIT**
4. Vérifier logs bounds mis à jour

### Résultats Attendus

#### Logs Landscape ✅
```
D/ComposeEmulator: [BOUNDS] GLRetroView bounds: 
  left=XXX, top=YYY, right=ZZZ, bottom=WWW, 
  size=WIDTHxHEIGHT
```

**Valeurs approximatives landscape:**
- left ≈ 20-50 (petite marge)
- top ≈ 0-50 (haut écran)
- right ≈ largeur écran - 50
- bottom ≈ hauteur écran
- width ≈ largeur écran (quasi plein)
- height ≈ hauteur écran (quasi plein)

#### Logs Portrait (Après Rotation) ✅
```
D/ComposeEmulator: [BOUNDS] GLRetroView bounds: 
  left=XXX, top=YYY, right=ZZZ, bottom=WWW, 
  size=WIDTHxHEIGHT
```

**Valeurs approximatives portrait:**
- left ≈ 0-20
- top ≈ 100-200 (offset vertical -20%)
- right ≈ largeur écran
- bottom ≈ hauteur écran * 0.60-0.70 (laisse place aux contrôles)

#### Si Bounds NULL ❌
```
W/NativeComposeEmulator: [ZAPPER] GLRetroView bounds not available yet
```
**Action:** onGloballyPositioned() pas appelé, vérifier code

### Critères de Succès
- [✅] Bounds logs affichés
- [✅] Bounds changent lors rotation
- [✅] Bounds jamais NULL après 2-3 secondes

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 3: Position POINTER Envoyée (Landscape) (10 min)

### Objectif
Vérifier que `sendMotionEvent(MOTION_SOURCE_POINTER)` envoie position correcte

### Procédure
1. Duck Hunt en **LANDSCAPE**
2. **Taper au CENTRE** de l'écran de jeu (sur un canard)
3. Observer logs
4. **Taper en HAUT À GAUCHE** de l'écran
5. Observer logs
6. **Taper en BAS À DROITE** de l'écran
7. Observer logs

### Résultats Attendus

#### Touch au CENTRE ✅
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (540, 480) → POINTER(0, 0) on port 2
```
**Valeurs attendues:**
- POINTER(0, 0) = Centre exact
- ou POINTER proche de (0, 0)

#### Touch HAUT GAUCHE ✅
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (100, 100) → POINTER(-25000, -20000) on port 2
```
**Valeurs attendues:**
- POINTER(X négatif, Y négatif)
- X proche de -0x7fff (-32767)
- Y proche de -0x7fff

#### Touch BAS DROITE ✅
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (1000, 900) → POINTER(28000, 25000) on port 2
```
**Valeurs attendues:**
- POINTER(X positif, Y positif)
- X proche de +0x7fff (+32767)
- Y proche de +0x7fff

#### Touch Release ✅
```
D/NativeComposeEmulator: [ZAPPER] Touch UP - POINTER released
```

### Gameplay Test
**Action:** Viser un canard et taper  
**Résultat attendu:** 🦆 **Canard tombe si touché !**

### Critères de Succès
- [✅] Logs POINTER avec coordonnées
- [✅] Coordonnées varient selon position touch
- [✅] Centre ≈ POINTER(0, 0)
- [✅] **Canards tombent quand touchés**

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 4: Position POINTER Portrait (10 min)

### Objectif
Valider que bounds synchronisés fonctionnent en portrait

### Procédure
1. **Rotation en PORTRAIT** (device vertical)
2. Vérifier nouveau bounds dans logs
3. **Taper au CENTRE** de la zone de jeu (au-dessus des contrôles)
4. Observer logs
5. **Taper sur les CONTRÔLES** (en bas)
6. Observer logs

### Résultats Attendus

#### Touch CENTRE (Zone de Jeu) ✅
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (360, 400) → POINTER(0, -5000) on port 2
```
**Comportement:** Tir envoyé au core

#### Touch sur CONTRÔLES (Hors Bounds) ✅
```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area, clamping to bounds
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (360, 1800) → POINTER(0, 32767) on port 2
```
**Comportement:** Position clampée au bottom edge

**OU si allowOffscreen=false:**
```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored
```
**Comportement:** Touch ignoré

### Gameplay Test Portrait
**Action:** Viser canard en portrait et taper  
**Résultat attendu:** 🦆 **Canard tombe si touché !**

### Critères de Succès
- [✅] Bounds portrait différents de landscape
- [✅] Touch dans zone jeu → POINTER envoyé
- [✅] Touch hors zone → clamped ou ignored
- [✅] **Canards tombent en portrait aussi**

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 5: AllowOffscreen Option (5 min)

### Objectif
Vérifier que l'option allowOffscreen fonctionne

### Procédure

#### Test A: Allow Offscreen = TRUE (default)
```
1. Advanced Overlay Settings
2. Vérifier "Allow Offscreen" = ON
3. Taper HORS de la zone de jeu (sur overlay, bars)
4. Vérifier logs
```

**Résultat attendu:**
```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area, clamping to bounds
D/NativeComposeEmulator: [ZAPPER] Touch DOWN → POINTER(clamped values)
```
**Comportement:** Position clampée, tir envoyé

#### Test B: Allow Offscreen = FALSE
```
1. Advanced Overlay Settings
2. Toggle "Allow Offscreen" = OFF
3. Taper HORS de la zone de jeu
4. Vérifier logs
```

**Résultat attendu:**
```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored
```
**Comportement:** Touch ignoré, pas de tir

### Critères de Succès
- [✅] allowOffscreen=true → clamp et shoot
- [✅] allowOffscreen=false → ignore
- [✅] Option dans settings fonctionne

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 6: TriggerOnTouch Option (5 min)

### Objectif
Vérifier l'option tir instantané

### Procédure

#### Test A: Trigger On Touch = FALSE (default - hold)
```
1. Advanced Overlay Settings
2. Vérifier "Trigger on Touch" = OFF
3. Taper et MAINTENIR sur canard
4. Vérifier logs
5. Relâcher
```

**Résultat attendu:**
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN → POINTER(x, y)
[... maintien ...]
D/NativeComposeEmulator: [ZAPPER] Touch UP - POINTER released
```
**Comportement:** Tir au release (hold and release)

#### Test B: Trigger On Touch = TRUE (instant)
```
1. Advanced Overlay Settings
2. Toggle "Trigger on Touch" = ON
3. Taper rapidement (tap) sur canard
4. Vérifier logs
```

**Résultat attendu:**
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN → POINTER(x, y)
D/NativeComposeEmulator: [ZAPPER] Trigger on touch enabled - instant shot
```
**Comportement:** Tir instantané au touch (pas besoin de relâcher)

### Critères de Succès
- [✅] triggerOnTouch=false → hold and release
- [✅] triggerOnTouch=true → instant shot
- [✅] Option dans settings fonctionne

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 7: Précision du Tir (5 min)

### Objectif
Valider que les canards tombent quand touchés précisément

### Procédure
```
1. Lancer Duck Hunt (Round 1)
2. Attendre qu'un canard apparaisse
3. Viser le CENTRE du canard
4. Taper
5. Observer résultat
6. Répéter 5 fois
```

### Résultats Attendus

**Si touché précisément:**
- 🦆 Canard tombe
- 💥 Animation hit
- Score +100 points

**Si raté:**
- 🦆 Canard continue
- Pas d'animation
- Score inchangé

**Précision attendue:** 4/5 = 80% (si visée correcte)

### Critères de Succès
- [✅] Canards touchés tombent
- [✅] Canards ratés continuent
- [✅] Précision ≥ 70%
- [✅] **Duck Hunt est JOUABLE !**

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 8: Edge Cases (5 min)

### Test A: Touch Multiple Rapide
```
Procédure:
1. Taper rapidement 5 fois (spam)
2. Observer logs et gameplay
```

**Résultat attendu:**
- Tous les tirs enregistrés
- Pas de freeze
- Pas de crash

### Test B: Touch et Drag
```
Procédure:
1. Taper et glisser le doigt
2. Observer logs ACTION_MOVE
```

**Résultat attendu:**
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN → POINTER(x1, y1)
[... glisser ...]
D/NativeComposeEmulator: [ZAPPER] Touch UP - POINTER released
```
**Comportement:** Position mise à jour pendant drag

### Test C: Touch Hors Écran puis Dans
```
Procédure:
1. Taper sur barre status (hors jeu)
2. Observer logs
3. Taper dans zone jeu
4. Observer logs
```

**Résultat attendu:**
- Touch hors bounds → clamped ou ignored
- Touch dans bounds → POINTER envoyé

### Critères de Succès
- [✅] Spam touch géré correctement
- [✅] Drag met à jour position
- [✅] Hors bounds géré (clamp/ignore)

**Status:** ✅ PASS / ❌ FAIL

---

## 🎯 TEST 9: Comparaison Landscape vs Portrait (5 min)

### Objectif
Confirmer que portrait fonctionne aussi bien que landscape

### Procédure
```
Landscape:
1. Jouer 1 round Duck Hunt landscape
2. Compter hits/misses
3. Noter précision

Portrait:
4. Rotation en portrait
5. Jouer 1 round Duck Hunt portrait
6. Compter hits/misses
7. Comparer précision
```

### Résultats Attendus

**Précision similaire:**
- Landscape: 70-80% hits
- Portrait: 70-80% hits
- Différence < 10%

**Logs similaires:**
- Même format POINTER(x, y)
- Même comportement touch

### Critères de Succès
- [✅] Précision landscape OK
- [✅] Précision portrait OK
- [✅] Différence < 10%
- [✅] **Portrait aussi jouable que landscape**

**Status:** ✅ PASS / ❌ FAIL

---

## 📊 CHECKLIST COMPLÈTE

### Configuration (Automatique)
- [ ] Toast "Zapper enabled!" affiché
- [ ] Log "configured as RETRO_DEVICE_POINTER"
- [ ] Pas d'erreurs de configuration

### Bounds Capture
- [ ] Logs "[BOUNDS]" affichés
- [ ] Bounds landscape valides
- [ ] Bounds portrait valides (après rotation)
- [ ] Bounds jamais NULL après init

### Position POINTER
- [ ] Touch centre → POINTER(≈0, ≈0)
- [ ] Touch haut-gauche → POINTER(négatif, négatif)
- [ ] Touch bas-droite → POINTER(positif, positif)
- [ ] Touch release → "POINTER released"

### Gameplay Landscape
- [ ] Canards touchés tombent
- [ ] Canards ratés continuent
- [ ] Précision ≥ 70%
- [ ] Duck Hunt jouable

### Gameplay Portrait
- [ ] Rotation fonctionne
- [ ] Bounds mis à jour
- [ ] Canards touchés tombent
- [ ] Précision similaire à landscape

### Options
- [ ] allowOffscreen=true → clamp
- [ ] allowOffscreen=false → ignore
- [ ] triggerOnTouch=true → instant
- [ ] triggerOnTouch=false → hold-release

### Edge Cases
- [ ] Spam touch OK
- [ ] Drag OK
- [ ] Touch hors bounds OK

**Total:** 28 points de vérification

---

## 🐛 DEBUGGING SI ÉCHEC

### Problème: Canards ne Tombent Pas

#### Debug Step 1: Vérifier Controller Type
```
Chercher dans logs:
✅ "[NES] Zapper configured as RETRO_DEVICE_POINTER"

Si absent:
- setControllerType() pas appelé
- Vérifier isZapperGame = true
```

#### Debug Step 2: Vérifier Position Envoyée
```
Chercher dans logs:
✅ "[ZAPPER] Touch DOWN... → POINTER(x, y)"

Si absent:
- sendMotionEvent() pas appelé
- Vérifier handleZapperTouch() appelé
```

#### Debug Step 3: Vérifier Bounds
```
Chercher dans logs:
✅ "[BOUNDS] GLRetroView bounds: ..."

Si NULL:
- onGloballyPositioned() pas appelé
- Vérifier imports
```

#### Debug Step 4: Vérifier Coordonnées
```
Valider que:
- Centre ≈ POINTER(0, 0)
- Coins = POINTER(±32767, ±32767)

Si mauvaises valeurs:
- Erreur de normalisation
- Vérifier formule: (relativeX * 2f - 1f) * 0x7fff
```

---

### Problème: Touch Ignorés

#### Vérifier Logs
```
W/NativeComposeEmulator: [ZAPPER] GLRetroView bounds not available yet
```
**Solution:** Attendre 2-3 secondes après lancement

```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area...
```
**Solution:** Vérifier bounds, taper dans la zone correcte

---

### Problème: Crash au Touch

#### Chercher Stack Trace
```bash
adb logcat | Select-String "FATAL|AndroidRuntime"
```

**Causes possibles:**
- NullPointerException sur bounds
- Division par zéro (bounds.width = 0)

**Solution:** Vérifier bounds != null et size > 0

---

## 📈 CRITÈRES DE VALIDATION GLOBALE

### ✅ SUCCÈS COMPLET
- **Tous les tests PASS** (9/9)
- **Canards tombent** landscape + portrait
- **Précision ≥ 70%**
- **Aucune erreur logs**

**Verdict:** ✅ Zapper 100% fonctionnel, passer à multi-touch + crosshair

### ⚠️ SUCCÈS PARTIEL
- **7-8 tests PASS** (77-88%)
- **Canards tombent** mais précision < 70%
- **Quelques warnings** mais pas de crash

**Verdict:** ⚠️ Ajustements mineurs nécessaires, mais utilisable

### ❌ ÉCHEC
- **< 6 tests PASS** (< 66%)
- **Canards ne tombent pas**
- **Erreurs ou crashes**

**Verdict:** ❌ Debug requis, ne pas continuer vers multi-touch

---

## 🚀 APRÈS LES TESTS

### Si SUCCÈS ✅
**Options:**
1. **Continuer implémentation** (multi-touch 2h + crosshair 1h)
2. **Commiter corrections** `git commit -m "Fix: Zapper POINTER implementation"`
3. **Documenter résultats** dans ZAPPER_TEST_RESULTS.md

### Si ÉCHEC ❌
**Actions:**
1. Noter tous les tests qui FAIL
2. Capturer logs complets
3. Identifier pattern d'erreur
4. Ajuster code selon findings
5. Re-tester

---

## 📝 TEMPLATE RAPPORT DE TEST

```markdown
# Zapper Test Results - [DATE]

## Configuration
- Device: [modèle]
- Android: [version]
- RetroPlay: [version/commit]
- ROM: Duck Hunt

## Tests
1. Configuration POINTER: ✅ PASS / ❌ FAIL
2. Bounds Capture: ✅ PASS / ❌ FAIL
3. Position Landscape: ✅ PASS / ❌ FAIL
4. Position Portrait: ✅ PASS / ❌ FAIL
5. AllowOffscreen: ✅ PASS / ❌ FAIL
6. TriggerOnTouch: ✅ PASS / ❌ FAIL
7. Précision Tir: ✅ PASS / ❌ FAIL
8. Edge Cases: ✅ PASS / ❌ FAIL
9. Landscape vs Portrait: ✅ PASS / ❌ FAIL

## Score: X/9 (XX%)

## Gameplay
- Landscape playable: ✅ / ❌
- Portrait playable: ✅ / ❌
- Précision: XX%

## Issues
[Lister problèmes rencontrés]

## Logs
[Copier logs pertinents]

## Verdict
✅ Ready for multi-touch
⚠️ Minor adjustments needed
❌ Debug required
```

---

## ⏱️ TIMELINE ESTIMÉE

| Étape | Durée | Cumulé |
|-------|-------|--------|
| Préparation (compile + install) | 10 min | 10 min |
| Test 1: Configuration | 5 min | 15 min |
| Test 2: Bounds | 5 min | 20 min |
| Test 3: Position Landscape | 10 min | 30 min |
| Test 4: Position Portrait | 10 min | 40 min |
| Test 5: AllowOffscreen | 5 min | 45 min |
| Test 6: TriggerOnTouch | 5 min | 50 min |
| Test 7: Précision | 5 min | 55 min |
| Test 8: Edge Cases | 5 min | 60 min |
| Test 9: Comparaison | 5 min | 65 min |
| **TOTAL** | **~1h** | - |

**Note:** Tests peuvent être plus rapides si tout fonctionne parfaitement (30-45 min)

---

**Plan créé le:** 31 octobre 2025  
**Tests à effectuer par:** Utilisateur (sur device physique)  
**Objectif:** Valider corrections avant multi-touch/crosshair  
**Résultat attendu:** 9/9 PASS ✅


