# 🎮 GUIDE DE TEST ZAPPER - PRÊT À COMMENCER

**Installation:** ✅ COMPLÈTE  
**Device:** R5CT11TCQ1W  
**APK:** app-debug.apk (BUILD SUCCESSFUL)

---

## 🚀 ÉTAPES À SUIVRE MAINTENANT

### 1️⃣ Préparer Terminal Logs (MAINTENANT)

**Dans un terminal PowerShell SÉPARÉ, exécuter:**
```powershell
adb logcat | Select-String "NativeComposeEmulator|ZAPPER|BOUNDS|POINTER|ComposeEmulator"
```

**Laisser ce terminal OUVERT** pendant tous les tests pour voir les logs en temps réel.

---

### 2️⃣ Lancer Duck Hunt sur Device

**Sur votre device Android:**
1. Ouvrir **RetroPlay**
2. Naviguer vers console **NES**
3. Trouver **Duck Hunt** (ou Duck Hunt (USA).nes)
4. Appuyer **PLAY NATIVE**

**⏱️ Attendre 5-10 secondes** que le jeu charge complètement

---

### 3️⃣ TEST 1: Configuration Automatique ⭐⭐⭐⭐⭐

#### Sur Device
**Observer:** Un Toast devrait apparaître avec le message:
```
"Zapper enabled! Tap screen to shoot"
```

#### Dans Terminal Logs
**Chercher ces lignes:**
```
I/NativeComposeEmulator: [NES] Zapper game detected: Duck Hunt
I/NativeComposeEmulator: [NES] Zapper configured as RETRO_DEVICE_POINTER on port 2
```

#### ✅ RÉSULTAT
- [ ] Toast affiché
- [ ] Log "Zapper detected" vu
- [ ] Log "configured as RETRO_DEVICE_POINTER" vu
- [ ] Aucune erreur

**Si toast ou logs ABSENTS** → ❌ Problème configuration, arrêter tests

---

### 4️⃣ TEST 2: Bounds Capture

#### Dans Terminal Logs
**Chercher:**
```
D/ComposeEmulator: [BOUNDS] GLRetroView bounds: left=XX, top=YY, right=ZZZ, bottom=WWW, size=WIDTHxHEIGHT
```

**Exemple valide:**
```
[BOUNDS] GLRetroView bounds: left=0.0, top=156.0, right=1080.0, bottom=1764.0, size=1080.0x1608.0
```

#### ✅ RÉSULTAT
- [ ] Log "[BOUNDS]" présent
- [ ] Valeurs numériques valides (pas 0 ou NULL)

**Si bounds NULL ou absents** → ⚠️ Problème onGloballyPositioned

---

### 5️⃣ TEST 3: Position POINTER (LE PLUS IMPORTANT!) 🎯

#### Sur Device - Duck Hunt Lancé
1. **Attendre qu'un canard apparaisse** (voler à l'écran)
2. **VISER le canard** avec votre doigt
3. **TAPER** sur le canard

#### Dans Terminal Logs
**Chercher:**
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (XXX, YYY) → POINTER(zzz, www) on port 2
D/NativeComposeEmulator: [ZAPPER] Touch UP - POINTER released
```

**Exemple valide:**
```
[ZAPPER] Touch DOWN at (540, 800) → POINTER(156, 12450) on port 2
[ZAPPER] Touch UP - POINTER released
```

#### Sur Device - Gameplay
**Observer:** 🦆 **LE CANARD DOIT TOMBER !**

**Si le canard tombe:**
- ✅ Animation chute du canard
- ✅ Score +100 points
- ✅ Son "hit"

**Si le canard continue:**
- ❌ Aucune réaction
- ❌ Tir non détecté

#### ✅ RÉSULTAT - TEST CRITIQUE
- [ ] Logs "Touch DOWN → POINTER(x, y)" affichés
- [ ] **Canard tombe quand touché** 🦆
- [ ] Score augmente

**Si canard NE TOMBE PAS** → ❌ PROBLÈME MAJEUR, stopper tests et débugger

---

### 6️⃣ TEST 4: Précision Multiple Tirs (5 min)

#### Sur Device
```
1. Round 1 commence (2 canards)
2. Viser canard #1 précisément
3. Taper
4. Viser canard #2 précisément
5. Taper
6. Continuer jusqu'à fin du round
```

#### Compteur
**Noter votre score:**
- Essais: X/10
- Hits: Y canards tombés
- Précision: Y/X = ZZ%

#### ✅ RÉSULTAT
- [ ] Précision ≥ 60% (minimum acceptable)
- [ ] Précision ≥ 70% (bon)
- [ ] Précision ≥ 80% (excellent)

**Si précision < 60%** → ⚠️ Problème calibration ou normalisation coordonnées

---

### 7️⃣ TEST 5: Test Portrait (10 min)

#### Sur Device
1. **Rotation device en PORTRAIT** (vertical)
2. Duck Hunt doit s'adapter (contrôles en bas)
3. Viser canard au-dessus des contrôles
4. Taper

#### Dans Terminal Logs
**Chercher nouveau bounds:**
```
D/ComposeEmulator: [BOUNDS] GLRetroView bounds: left=X, top=Y, right=Z, bottom=W...
```

**Bounds portrait devraient être différents de landscape !**

#### Gameplay Portrait
**Essayer tirer 3-5 canards**

#### ✅ RÉSULTAT
- [ ] Bounds portrait différents de landscape
- [ ] **Canards tombent en portrait** 🦆
- [ ] Précision portrait ≈ précision landscape (±10%)

**Si portrait cassé** → ❌ Problème synchronisation bounds

---

### 8️⃣ TEST 6: Touch Hors Zone de Jeu (5 min)

#### Sur Device Portrait (plus facile à tester)
1. **Taper sur les CONTRÔLES** (en bas, hors zone de jeu)
2. Observer logs
3. **Taper dans ZONE DE JEU** (au-dessus)
4. Comparer logs

#### Dans Terminal Logs

**Touch sur contrôles (hors bounds):**
```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area, clamping to bounds
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (360, 2000) → POINTER(0, 32767)
```
**OU (si allowOffscreen=false):**
```
D/NativeComposeEmulator: [ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored
```

**Touch dans zone de jeu:**
```
D/NativeComposeEmulator: [ZAPPER] Touch DOWN at (360, 800) → POINTER(0, 5000)
```

#### ✅ RÉSULTAT
- [ ] Touch hors bounds détecté
- [ ] Touch dans bounds fonctionne
- [ ] Comportement cohérent

---

### 9️⃣ TEST 7: Options Advanced Settings (Optionnel - 5 min)

#### Test AllowOffscreen
```
1. Menu → GamePad Settings → Advanced Overlay Settings
2. Chercher "Allow Offscreen"
3. Toggle OFF
4. Retour au jeu
5. Taper hors zone de jeu
6. Vérifier logs "ignored" au lieu de "clamping"
```

#### Test TriggerOnTouch
```
1. Advanced Settings
2. Chercher "Trigger on Touch"
3. Toggle ON
4. Retour au jeu
5. Tap rapide (ne pas maintenir)
6. Vérifier logs "Trigger on touch enabled - instant shot"
```

---

## 📊 RÉSUMÉ DES TESTS

### Tests Critiques (Bloquants)
1. ✅ Configuration POINTER
2. ✅ Bounds Capture
3. ✅ **Canards tombent landscape** 🦆
4. ✅ **Canards tombent portrait** 🦆

**Si UN seul échoue** → DEBUG REQUIS

### Tests Importants
5. ✅ Précision ≥ 60%
6. ✅ Logs POINTER(x, y) corrects

### Tests Nice-to-Have
7. ✅ Touch hors bounds géré
8. ✅ Options fonctionnent

---

## 🎯 VERDICT FINAL

### ✅ SUCCÈS COMPLET (9/9 ou 8/9)
**Actions:**
1. 🎉 Célébrer ! Le Zapper fonctionne !
2. ✅ Commiter: `git commit -m "Fix: Zapper POINTER implementation with bounds"`
3. 🚀 Continuer multi-touch (2h) + crosshair (1h)

### ⚠️ SUCCÈS PARTIEL (6-7/9)
**Actions:**
1. Noter quels tests FAIL
2. Capturer logs complets
3. Ajuster selon problème
4. Re-tester

### ❌ ÉCHEC (< 6/9)
**Actions:**
1. **NE PAS continuer vers multi-touch**
2. Capturer logs complets
3. Analyser erreurs
4. Demander aide si besoin

---

## 📝 RAPPORT RAPIDE

**Après tests, notez:**
```
🧪 Tests Zapper - 31 oct 2025

Device: R5CT11TCQ1W
ROM: Duck Hunt

Tests:
[✅/❌] 1. Configuration POINTER
[✅/❌] 2. Bounds Capture
[✅/❌] 3. Position Landscape
[✅/❌] 4. Précision (XX%)
[✅/❌] 5. Portrait
[✅/❌] 6. Touch Hors Bounds
[✅/❌] 7. Options

Score: X/9

Gameplay:
- Landscape: ✅ Jouable / ❌ Cassé
- Portrait: ✅ Jouable / ❌ Cassé
- Précision: XX%

Verdict: ✅ Ready / ⚠️ Adjust / ❌ Debug
```

---

## 🎮 C'EST PARTI !

**MAINTENANT sur votre device:**

1. 📱 **Ouvrir RetroPlay**
2. 🎮 **NES → Duck Hunt → PLAY NATIVE**
3. 👀 **Observer Toast "Zapper enabled!"**
4. 🦆 **TAPER sur un canard**
5. 🎉 **Vérifier s'il tombe !**

**En parallèle, dans un terminal PowerShell:**
```powershell
adb logcat | Select-String "ZAPPER|BOUNDS|POINTER"
```

---

**Les tests commencent MAINTENANT ! Bonne chance ! 🎯**

Dites-moi ce que vous voyez :
- Le Toast apparaît ?
- Les canards tombent ?
- Que montrent les logs ?
