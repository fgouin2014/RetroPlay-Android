# Test Pratique - EmulationSettingsDialog

**Date:** 2025-01-XX  
**Objectif:** Valider le nouveau dialog d'émulation en conditions réelles

---

## 🚀 Préparation

### 1. Installer l'APK

```powershell
# Compiler et installer
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Préparer les Logs

```powershell
# Filtrer les logs RetroPlay
adb logcat -c
adb logcat -s RetroArchEmulatorActivity:V CoreVariableManager:V EmulationSettingsDialog:V | Select-String -Pattern "Emulation|Core Options|Apply|Fast Forward|Audio Volume|Category"
```

---

## 📋 Tests à Effectuer

### Test 1: Ouverture du Dialog ⭐

**Jeu recommandé:** N64 (Super Mario 64) ou SNES (Super Mario World)

**Actions:**
1. Lancer un jeu
2. Ouvrir le menu principal (bouton menu ou swipe)
3. Sélectionner "Core Options" ou "Emulation Settings"

**Résultat attendu:**
- ✅ Dialog s'ouvre avec le nom du jeu en haut
- ✅ Sections catégorisées visibles (Video, Audio, Input, etc.)
- ✅ Section "Global Settings" visible en bas
- ✅ Pas de crash

**Logs attendus:**
```
[RetroArchEmulatorActivity] Opening EmulationSettingsDialog for game: [nom]
[CoreVariableManager] Parsed X core options
```

---

### Test 2: Catégorisation Core Options ⭐⭐

**Jeu recommandé:** N64 (ParaLLEl N64) - Beaucoup d'options

**Actions:**
1. Ouvrir le dialog
2. Observer les catégories affichées
3. Scroller pour voir toutes les catégories

**Résultat attendu:**
- ✅ **Video:** Resolution, Anti-aliasing, Bilinear
- ✅ **Audio:** (si présentes)
- ✅ **Input:** (si présentes)
- ✅ **Performance:** Frameskip, etc.
- ✅ **Emulation:** Region, Mode, etc.
- ✅ **Other:** Options non catégorisées

**Vérification:**
- Les options sont bien groupées par catégorie
- Les catégories sont dans un ordre logique
- Pas d'options dupliquées

---

### Test 3: Modification Core Options ⭐⭐⭐

**Jeu recommandé:** N64 (ParaLLEl N64)

**Actions:**
1. Ouvrir le dialog
2. Dans "Video", modifier "Resolution" (ex: 320x240 → 640x480)
3. Observer le changement visuel
4. Cliquer "Apply"
5. Observer le jeu après application

**Résultat attendu:**
- ✅ La valeur change dans le dropdown
- ✅ Après "Apply", le changement est appliqué au core
- ✅ Le jeu reflète le changement (résolution plus haute)
- ✅ Pas de crash ou freeze

**Logs attendus:**
```
[CoreVariableManager] Applied X core option changes and global settings to running core
[RetroArchEmulatorActivity] Applied X core option changes
```

**Vérification sauvegarde:**
```powershell
# Vérifier que les options sont sauvegardées
adb shell "run-as com.retroplay find /data/data/com.retroplay/files -name '*.cfg' -exec cat {} \;"
```

---

### Test 4: Fast Forward Ratio ⭐⭐

**Jeu recommandé:** NES (Super Mario Bros) - Rapide à tester

**Actions:**
1. Ouvrir le dialog
2. Dans "Global Settings", modifier "Fast Forward Ratio" (ex: 2x → 5x)
3. Cliquer "Apply"
4. Activer le fast forward (hotkey ou bouton)
5. Observer la vitesse du jeu

**Résultat attendu:**
- ✅ Le slider permet de sélectionner 1x à 10x
- ✅ La valeur affichée correspond à la sélection
- ✅ Après "Apply", le fast forward utilise le nouveau ratio
- ✅ La vitesse est visiblement différente

**Logs attendus:**
```
[RetroArchEmulatorActivity] Applied X core option changes and global settings to running core
[FAST_FORWARD] ENABLED (5x)
```

**Vérification sauvegarde:**
```powershell
# Vérifier SharedPreferences
adb shell "run-as com.retroplay cat /data/data/com.retroplay/shared_prefs/*.xml | Select-String 'fast_forward'"
```

---

### Test 5: Audio Volume ⭐

**Jeu recommandé:** NES (Super Mario Bros) - Audio clair

**Actions:**
1. Ouvrir le dialog
2. Dans "Global Settings", modifier "Audio Volume" (ex: 0 dB → -10 dB)
3. Cliquer "Apply"
4. Observer le volume audio

**Résultat attendu:**
- ✅ Le slider permet de sélectionner -80 dB à +12 dB
- ✅ La valeur affichée correspond à la sélection
- ✅ La valeur est sauvegardée
- ⚠️ **Note:** Le volume n'est pas encore appliqué au système (nécessite AudioManager)

**Logs attendus:**
```
[RetroArchEmulatorActivity] Applied X core option changes and global settings to running core
```

**Vérification sauvegarde:**
```powershell
# Vérifier SharedPreferences
adb shell "run-as com.retroplay cat /data/data/com.retroplay/shared_prefs/*.xml | Select-String 'audio_volume'"
```

---

### Test 6: Cancel vs Apply ⭐⭐

**Jeu recommandé:** N64 (Super Mario 64)

**Actions:**
1. Ouvrir le dialog
2. Modifier plusieurs options (core options + global settings)
3. Cliquer "Cancel"
4. Rouvrir le dialog

**Résultat attendu:**
- ✅ Les modifications ne sont PAS appliquées
- ✅ Les valeurs reviennent aux valeurs d'origine

**Actions (suite):**
5. Modifier à nouveau les options
6. Cliquer "Apply"
7. Rouvrir le dialog

**Résultat attendu:**
- ✅ Les modifications SONT appliquées
- ✅ Les nouvelles valeurs sont affichées

---

### Test 7: Scroll et UI ⭐

**Jeu recommandé:** MAME/FBNeo (beaucoup d'options)

**Actions:**
1. Ouvrir le dialog sur un jeu avec beaucoup d'options
2. Scroller dans le dialog
3. Vérifier la lisibilité

**Résultat attendu:**
- ✅ Le dialog scroll correctement
- ✅ Les catégories restent visibles et organisées
- ✅ Les couleurs sont cohérentes (vert pour valeurs, blanc pour textes)
- ✅ Les séparateurs (Divider) sont visibles entre sections
- ✅ Pas de lag ou freeze

---

### Test 8: Options Booléennes vs Enum ⭐

**Jeu recommandé:** N64 (ParaLLEl N64)

**Actions:**
1. Ouvrir le dialog
2. Tester le Switch pour les options booléennes (ex: "Bilinear")
3. Tester le Dropdown pour les options enum (ex: "Resolution")

**Résultat attendu:**
- ✅ Les options booléennes affichent un Switch
- ✅ Les options enum affichent un Dropdown
- ✅ Les deux fonctionnent correctement
- ✅ Les valeurs sont correctement appliquées

---

## 🐛 Problèmes à Reporter

### Crash
- Message d'erreur complet
- Stack trace
- Jeu et console utilisés
- Actions effectuées avant le crash

### Comportement Incorrect
- Option non catégorisée correctement
- Valeur non appliquée
- Valeur non sauvegardée
- UI non scrollable

### Performance
- Lag lors du scroll
- Freeze lors de l'application
- Dialog lent à s'ouvrir

---

## 📊 Checklist de Validation

- [ ] Dialog s'ouvre correctement
- [ ] Catégorisation fonctionne
- [ ] Core options modifiables
- [ ] Fast Forward Ratio fonctionne
- [ ] Audio Volume sauvegardé (application future)
- [ ] Cancel/Apply fonctionnent
- [ ] Scroll fonctionne
- [ ] Options booléennes fonctionnent
- [ ] Options enum fonctionnent
- [ ] Pas de crash
- [ ] Performance acceptable

---

## 🔍 Commandes Utiles

### Voir les logs en temps réel
```powershell
adb logcat -s RetroArchEmulatorActivity:V CoreVariableManager:V | Select-String -Pattern "Emulation|Core Options|Apply"
```

### Vérifier les sauvegardes
```powershell
# Core options (.cfg)
adb shell "run-as com.retroplay find /data/data/com.retroplay/files -name '*.cfg' -exec cat {} \;"

# Global settings (SharedPreferences)
adb shell "run-as com.retroplay cat /data/data/com.retroplay/shared_prefs/*.xml | Select-String 'emulation_'"
```

### Nettoyer les logs
```powershell
adb logcat -c
```

---

## ✅ Résultats Attendus

### Succès
- Dialog fonctionnel et intuitif
- Catégorisation claire et logique
- Modifications appliquées et sauvegardées
- UI responsive et scrollable

### Échecs à Reporter
- Crash lors de l'ouverture
- Catégorisation incorrecte
- Modifications non appliquées
- UI non scrollable ou illisible

---

**Bon test! 🎮**

