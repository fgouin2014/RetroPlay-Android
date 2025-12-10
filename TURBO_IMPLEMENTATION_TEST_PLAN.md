# Plan de Tests: Turbo 10Hz Compatible RetroArch

## Tests Automatisés (Compilation)

### Test 1: Compilation sans erreurs
```bash
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
./gradlew clean assembleDebug
```

**Résultat attendu:** Build successful

### Test 2: Vérification des imports
- TurboSettings import dans TurboSettingsDialog.kt
- TurboSettings import dans QuickTurboMenu.kt
- TurboSettings import dans RetroArchOverlayRenderer.kt

**Status:** Passes (no lint errors)

---

## Tests Manuels Requis

### Test 3: Configuration par défaut

**Steps:**
1. Installer l'APK sur le device
2. Lancer RetroPlay
3. Ouvrir Settings ou lire SharedPreferences

**Vérifications:**
- `turbo_enabled` = true
- `turbo_frequency` = 10 (Hz)
- `turbo_duty_cycle` = 0.5 (50%)
- `turbo_allow_dpad` = false
- `turbo_mode` = "AUTO_TOGGLE"

### Test 4: Menu Settings accessible

**Steps:**
1. Lancer un jeu NES (ex: Contra)
2. Ouvrir Main Menu (button START ou menu)
3. Chercher "Turbo Settings" dans la liste

**Vérifications:**
- Bouton "Turbo Settings" visible (couleur cyan 0xFF00BCD4)
- Click ouvre TurboSettingsDialog
- Dialog affiche tous les sliders et switches

### Test 5: Configuration Turbo Frequency

**Steps:**
1. Ouvrir Turbo Settings
2. Ajuster slider Frequency: 5 Hz
3. Sauvegarder
4. Relancer le jeu avec un overlay ayant turbo=true
5. Tester le bouton turbo

**Vérifications:**
- Fréquence: 5 Hz = 200ms cycle (très lent, visible)
- Timer: (frameCounter % 12) < 6 = 5 Hz @ 60fps

**Répéter avec:**
- 10 Hz (défaut RetroArch): 100ms cycle
- 15 Hz (fast): 67ms cycle
- 30 Hz (ancien): 33ms cycle

### Test 6: Duty Cycle variations

**Steps:**
1. Frequency = 10 Hz
2. Duty Cycle = 10% (1 frame ON, 5 frames OFF)
3. Tester bouton turbo

**Vérifications:**
- Bouton pressé très brièvement (10% du cycle)
- Release plus long (90% du cycle)

**Répéter avec:**
- 50% (défaut): 3 frames ON, 3 OFF
- 90%: 5 frames ON, 1 frame OFF

### Test 7: Restriction D-Pad

**Steps:**
1. Turbo Settings: Allow D-Pad = OFF
2. Utiliser overlay avec direction turbo=true
3. Presser direction UP avec turbo

**Vérifications:**
- Direction fonctionne normalement (pas de turbo)
- Log: pas de turbo activé sur D-Pad

**Avec Allow D-Pad = ON:**
- Direction active le turbo
- Log: turbo activé

### Test 8: Quick Menu In-Game

**Steps:**
1. Pendant le jeu, presser L3 + R3 simultanément

**Vérifications:**
- Quick Turbo Menu s'ouvre
- 4 presets visibles: 5/10/15/30 Hz
- Preset actuel surligné en cyan
- Click preset change immédiatement la fréquence

### Test 9: Persistance des settings

**Steps:**
1. Changer Frequency = 15 Hz
2. Sauvegarder
3. Quitter le jeu
4. Relancer le même jeu
5. Ouvrir Turbo Settings

**Vérifications:**
- Frequency = 15 Hz (valeur sauvegardée)
- Tous les autres settings persistés

### Test 10: Frame-based vs Timer

**Mesures:**
1. Logger timestamps dans RetroArchOverlayRenderer
2. Fréquence = 10 Hz
3. Compter les press/release sur 10 secondes

**Vérifications:**
- Nombre de presses: ~100 (10/sec)
- Période moyenne: ~100ms ± 5ms
- Pas de drift avec le temps

---

## Tests avec Overlays Spécifiques

### Test 11: flat-nes overlay

**Overlay:** `/storage/emulated/0/GameLibrary-Data/data/overlays/flat/nes.cfg`

**Boutons turbo:**
- a avec turbo=true
- b avec turbo=true

**Steps:**
1. Lancer Contra (NES) avec flat-nes overlay
2. Maintenir bouton A (shoot)
3. Observer la fréquence de tir

**Vérifications:**
- @ 10 Hz: Tir réaliste, ~10 bullets/sec
- @ 30 Hz: Tir rapide, ~30 bullets/sec
- Pas de lag, pas de presses manquées

### Test 12: dual-shock overlay

**Overlay:** `/storage/emulated/0/GameLibrary-Data/data/overlays/dual-shock/psx.cfg`

**Boutons turbo:**
- cross avec turbo=true
- circle avec turbo=true

**Steps:**
1. Lancer jeu PSX avec dual-shock
2. Tester bouton Cross avec turbo
3. Tester simultané Cross + Circle turbo

**Vérifications:**
- Les deux boutons turbo fonctionnent indépendamment
- Synchronisés sur le même frameCounter
- Pas d'interférences

---

## Tests de Régression

### Test 13: Boutons normaux (non-turbo)

**Steps:**
1. Presser boutons sans turbo=true
2. Vérifier comportement normal

**Vérifications:**
- Pas de modulation, press/release direct
- Pas d'ajout au turboState

### Test 14: Turbo désactivé globalement

**Steps:**
1. Turbo Settings: Enable = OFF
2. Presser bouton avec turbo=true

**Vérifications:**
- Bouton fonctionne normalement (pas de turbo)
- turboState reste vide

### Test 15: Overlay sans boutons turbo

**Steps:**
1. Charger overlay sans turbo=true dans .cfg
2. Jouer normalement

**Vérifications:**
- Aucun impact, jeu normal
- Pas de timer turbo actif (turboState vide)

---

## Critères de Succès

### Must Have (Bloquants)
- [ ] Build successful sans erreurs
- [ ] Turbo Settings Dialog s'ouvre et sauvegarde
- [ ] Fréquence 10 Hz par défaut appliquée
- [ ] Frame-based timer fonctionne (16ms delay)
- [ ] Boutons turbo fonctionnent avec flat-nes

### Should Have (Importants)
- [ ] Quick Turbo Menu fonctionne
- [ ] Presets changent la fréquence immédiatement
- [ ] Duty Cycle ajustable fonctionne
- [ ] Restriction D-Pad respectée
- [ ] Persistance settings entre sessions

### Nice to Have (Bonus)
- [ ] Hotkey L3+R3 ouvre Quick Menu
- [ ] Performance stable (pas de lag)
- [ ] Compatible avec tous les overlays RetroArch

---

## Résultats Attendus

### Compilation
**Status:** PASS - No lint errors

### Configuration Default
**Expected:**
```
TurboSettings(
    enabled = true,
    frequency = 10,
    dutyCycle = 0.5f,
    allowDpad = false,
    mode = AUTO_TOGGLE
)
```

### Frame Timing @ 10 Hz
```
Frame 0-2:   PRESSED   (0,1,2 % 6 < 3)
Frame 3-5:   RELEASED  (3,4,5 % 6 >= 3)
Frame 6-8:   PRESSED   (6,7,8 % 6 < 3)
...
Period: 100ms (6 frames @ 60fps)
```

### UI Flow
```
Main Menu → Turbo Settings → [Adjust] → Save
                          ↓
                   Quick Turbo (L3+R3)
                          ↓
                   [5Hz|10Hz|15Hz|30Hz]
```

---

## Prochaines Étapes Après Validation

1. Tester sur device Android réel
2. Mesurer performance avec logcat
3. Tester avec 10+ overlays différents
4. Collecter feedback utilisateur sur 10 Hz vs 30 Hz
5. Documenter dans README.md

**Implémentation complète terminée!**


