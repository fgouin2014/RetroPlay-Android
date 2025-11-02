# Guide de test FINAL - Zapper avec options branchées

**Date:** 2025-10-31  
**Version:** Toutes les options lightgun entièrement branchées  
**Commit:** `d4aa93b`

---

## 🎮 PRÉPARATION

### 1. Lancer Duck Hunt (NES)
```
1. Ouvrez RetroPlay
2. Sélectionnez "Duck Hunt.nes"
3. L'app devrait détecter automatiquement le Zapper
```

### 2. Vérifier le toast de détection
**Toast attendu:**
```
Zapper detected!
Port 1: Gamepad (Start/Select)
Port 2: Touch game area to shoot
```

### 3. Préparer les logs
```powershell
adb logcat -c  # Clear logs
adb logcat | Select-String "ZAPPER"
```

---

## 🧪 TEST 1: Trigger immédiat (triggerOnTouch = true)

### Configuration
- ✅ "Trigger on Touch" = **ON** (défaut)
- ✅ "Trigger Delay" = **0ms**

### Procédure
1. Attendez qu'un canard apparaisse
2. **Touchez** le canard (un seul doigt)
3. **Relevez** le doigt immédiatement

### Résultats attendus
✅ Le canard tombe **IMMÉDIATEMENT** au moment du touch  
✅ Pas besoin de maintenir le doigt appuyé

### Logs attendus
```
[ZAPPER] Touch DOWN at (540, 1200) → POINTER([0-1]: 0.5, 0.6) on port 2
[ZAPPER] Trigger FIRED on port 2 (delay: 0ms)
```

**SI LE CANARD TOMBE:** ✅ LE ZAPPER FONCTIONNE!  
**SI RIEN:** ⚠️ Problème à investiguer (voir section Troubleshooting)

---

## 🧪 TEST 2: Trigger au release (triggerOnTouch = false)

### Configuration
1. Appuyez sur BACK → Quick Menu
2. Settings → Advanced Overlay Settings
3. **Désactiver** "Trigger on Touch"
4. Retour au jeu

### Procédure
1. Attendez qu'un canard apparaisse
2. **Touchez et MAINTENEZ** le doigt sur le canard (ne tirez pas encore)
3. **Relevez** le doigt

### Résultats attendus
✅ Le canard tombe au moment où vous **LEVEZ** le doigt (ACTION_UP)  
✅ Vous pouvez "viser" en maintenant le doigt avant de tirer

### Logs attendus
```
[ZAPPER] Touch DOWN at (540, 1200) → POINTER([0-1]: 0.5, 0.6) on port 2
[ZAPPER] Touch UP - POINTER released
[ZAPPER] Trigger FIRED on port 2 (delay: 0ms)
```

---

## 🧪 TEST 3: Trigger avec délai (triggerDelay)

### Configuration
1. Advanced Overlay Settings
2. **Activer** "Trigger on Touch"
3. Mettre "Trigger Delay" à **500ms** (0.5 seconde)

### Procédure
1. Touchez un canard
2. Observez le délai avant le tir

### Résultats attendus
✅ Délai de **0.5 seconde** entre le touch et le tir  
✅ Le canard peut bouger pendant le délai

### Logs attendus
```
[ZAPPER] Touch DOWN at (540, 1200) → POINTER([0-1]: 0.5, 0.6) on port 2
[ZAPPER] Trigger FIRED on port 2 (delay: 500ms)  ← 500ms plus tard
```

---

## 🧪 TEST 4: Multi-touch - 2 doigts (START)

### Configuration
1. Advanced Overlay Settings
2. "2 Fingers Action" → Choisir **START** (1)

### Procédure
1. Dans Duck Hunt (en jeu)
2. Touchez l'écran avec **2 doigts** simultanément
3. Maintenez 1 seconde

### Résultats attendus
✅ Le menu START s'ouvre (pause)  
✅ Ou l'action START est détectée dans le jeu

### Logs attendus
```
[ZAPPER] Multi-touch: 2 fingers → action 1
[ZAPPER] Multi-touch action 1 sent: keyCode=108 on port 2
```

---

## 🧪 TEST 5: Multi-touch - 3 doigts (SELECT)

### Configuration
1. Advanced Overlay Settings
2. "3 Fingers Action" → Choisir **SELECT** (2)

### Procédure
1. Dans Duck Hunt (en jeu)
2. Touchez l'écran avec **3 doigts** simultanément

### Résultats attendus
✅ L'action SELECT est envoyée (changement de mode de jeu)

### Logs attendus
```
[ZAPPER] Multi-touch: 3 fingers → action 2
[ZAPPER] Multi-touch action 2 sent: keyCode=109 on port 2
```

---

## 🧪 TEST 6: Lightgun Port (changement de port)

### Configuration
1. Advanced Overlay Settings
2. "Lightgun Port" → Changer de **1** à **0**

### Procédure
1. Relancez Duck Hunt
2. Touchez pour tirer

### Résultats attendus
✅ Les logs montrent **port 1** au lieu de **port 2**

### Logs attendus
```
[ZAPPER] Touch DOWN → POINTER on port 1  ← Port 1 au lieu de 2!
[ZAPPER] Trigger FIRED on port 1
```

**Note:** Duck Hunt nécessite le port 2, donc ça ne fonctionnera probablement pas, mais ça confirme que l'option est branchée!

---

## 🧪 TEST 7: Allow Offscreen (tir hors zone)

### Configuration
1. Advanced Overlay Settings
2. **Désactiver** "Allow Offscreen"

### Procédure
1. Dans Duck Hunt
2. **Touchez en dehors de la zone de jeu** (sur la QuickActionsBar, overlays, etc.)

### Résultats attendus
✅ Le tir est **ignoré** (aucun effet)

### Logs attendus
```
[ZAPPER] Touch OUTSIDE game area and allowOffscreen=false - ignored
```

### Configuration inverse
1. **Activer** "Allow Offscreen"
2. Touchez hors zone

### Résultats attendus
✅ Le tir est **clamped** aux bounds et envoyé quand même

### Logs attendus
```
[ZAPPER] Touch OUTSIDE game area, clamping to bounds
[ZAPPER] Trigger FIRED on port 2
```

---

## 📊 RÉCAPITULATIF DES OPTIONS

| Option | Valeur par défaut | Impact visible |
|--------|-------------------|----------------|
| **Trigger on Touch** | ON (true) | Tir instantané vs release |
| **Trigger Delay** | 0ms | Délai observable avant tir |
| **Lightgun Port** | 1 (Port 2) | Logs montrent le port |
| **Allow Offscreen** | ON (true) | Touch hors zone accepté/refusé |
| **2 Fingers** | None (0) | Menu START si configuré |
| **3 Fingers** | None (0) | SELECT si configuré |
| **4 Fingers** | None (0) | Action personnalisée |

---

## ⚠️ TROUBLESHOOTING

### Problème: "Le canard ne tombe toujours pas"

**Vérifications:**

1. **Les logs montrent-ils `Trigger FIRED`?**
   - ✅ OUI → Le trigger est envoyé, problème dans FCEUmm
   - ❌ NON → Le code ne s'exécute pas

2. **La zone de touch est-elle correcte?**
   - Logs: `Touch DOWN at (x, y) → POINTER([0-1]: relX, relY)`
   - Vérifier que relX et relY sont dans [0, 1]

3. **Le port est-il correct?**
   - Logs: `on port 2`
   - Duck Hunt nécessite port 2 (index 1)

4. **Les core options sont-elles correctes?**
   ```
   fceumm_zapper_mode = "touchscreen"
   fceumm_zapper_trigger = "enabled"
   fceumm_zapper_sensor = "enabled"
   ```

---

### Problème: "Rien ne se passe quand je touche"

**Vérifications:**

1. **isZapperGame est-il true?**
   - Log au démarrage: `[ZAPPER] Zapper game detected EARLY: Duck Hunt`

2. **gameViewBounds est-il disponible?**
   - Devrait apparaître après ~1 seconde
   - Log: `[BOUNDS] GLRetroView REAL bounds: ...`

3. **Le touch est-il intercepté par les overlays?**
   - Vérifier pointerInteropFilter dans ComposeEmulatorScreen

---

### Problème: "Multi-touch ne fonctionne pas"

**Vérifications:**

1. **L'option est-elle configurée?**
   - Vérifier dans Advanced Settings: "2 Fingers" ≠ None (0)

2. **Les doigts sont-ils simultanés?**
   - Il faut toucher avec les 2/3/4 doigts EN MÊME TEMPS

3. **Les logs montrent-ils la détection?**
   - `[ZAPPER] Multi-touch: 2 fingers → action 1`

---

## 🎯 CRITÈRES DE SUCCÈS

### SUCCESS MINIMAL:
✅ Les logs montrent `Trigger FIRED`  
✅ Les coordonnées sont correctes [0, 1]  
✅ Le port est correct (port 2)

### SUCCESS COMPLET:
✅ Les canards tombent quand on les touche  
✅ Le timing du tir est correct (immédiat au touch)  
✅ Les multi-touch actions fonctionnent  
✅ Les options sont toutes branchées et fonctionnelles

---

## 📝 RAPPORT DE TEST

Après vos tests, notez:

**Test 1 (Trigger immediat):** ☐ Réussi ☐ Échoué  
**Test 2 (Trigger au release):** ☐ Réussi ☐ Échoué  
**Test 3 (Trigger avec délai):** ☐ Réussi ☐ Échoué  
**Test 4 (Multi-touch 2 doigts):** ☐ Réussi ☐ Échoué  
**Test 5 (Allow offscreen):** ☐ Réussi ☐ Échoué  

**Logs critiques observés:**
```
[Collez les logs ici]
```

**Canard tombe:** ☐ OUI ☐ NON

---

**Prêt pour les tests!** Connectez votre device et testez Duck Hunt! 🎯🦆

