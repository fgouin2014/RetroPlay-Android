# Test EmulationSettingsDialog

**Date:** 2025-01-XX  
**Status:** À tester

---

## Objectif

Valider que le nouveau `EmulationSettingsDialog` fonctionne correctement avec:
- Catégorisation automatique des core options
- Settings globaux (Fast Forward Ratio, Audio Volume)
- Sauvegarde et application des modifications

---

## Prérequis

1. APK compilé avec les modifications
2. Device Android connecté via ADB
3. Au moins un jeu chargé avec des core options disponibles

---

## Tests à Effectuer

### Test 1: Ouverture du Dialog

**Actions:**
1. Lancer un jeu (ex: NES, SNES, N64)
2. Ouvrir le menu principal
3. Sélectionner "Core Options" ou "Emulation Settings"

**Résultat attendu:**
- ✅ Dialog s'ouvre avec le nom du jeu affiché
- ✅ Sections catégorisées visibles (Video, Audio, Input, etc.)
- ✅ Section "Global Settings" visible en bas

---

### Test 2: Catégorisation des Core Options

**Actions:**
1. Ouvrir le dialog sur un jeu N64 (ex: Super Mario 64)
2. Observer les catégories affichées

**Résultat attendu:**
- ✅ Options vidéo (Resolution, Anti-aliasing, Bilinear) dans "Video"
- ✅ Options audio dans "Audio" (si présentes)
- ✅ Options input dans "Input" (si présentes)
- ✅ Autres options dans "Other" ou catégories appropriées

**Jeux recommandés pour test:**
- **N64 (ParaLLEl N64):** Résolution, Anti-aliasing, Bilinear
- **MAME/FBNeo:** DIP Switches (ne doivent PAS apparaître ici, seulement dans DIP Switches dialog)
- **PSX (PCSX ReARMed):** Enhanced resolution, Frame skipping, Dithering

---

### Test 3: Modification Core Options

**Actions:**
1. Ouvrir le dialog
2. Modifier une option (ex: Resolution N64: 320x240 → 640x480)
3. Cliquer "Apply"
4. Observer le comportement du jeu

**Résultat attendu:**
- ✅ La valeur change dans le dropdown/switch
- ✅ Après "Apply", le changement est appliqué au core
- ✅ Le jeu reflète le changement (ex: résolution plus haute)
- ✅ La valeur est sauvegardée (redémarrer le jeu pour vérifier)

**Vérification sauvegarde:**
```bash
# Vérifier que les options sont sauvegardées dans .cfg
adb shell "find /storage/emulated/0/RetroPlay-Data -name '*.cfg' -exec cat {} \;"
```

---

### Test 4: Fast Forward Ratio

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
- ✅ La valeur est sauvegardée dans SharedPreferences

**Vérification sauvegarde:**
```bash
# Vérifier SharedPreferences
adb shell "run-as com.retroplay cat /data/data/com.retroplay/shared_prefs/*.xml | grep fast_forward"
```

---

### Test 5: Audio Volume

**Actions:**
1. Ouvrir le dialog
2. Dans "Global Settings", modifier "Audio Volume" (ex: 0 dB → -10 dB)
3. Cliquer "Apply"
4. Observer le volume audio du jeu

**Résultat attendu:**
- ✅ Le slider permet de sélectionner -80 dB à +12 dB
- ✅ La valeur affichée correspond à la sélection
- ✅ La valeur est sauvegardée dans SharedPreferences
- ⚠️ **Note:** Le volume audio nécessite une implémentation AudioManager (à faire)

**Vérification sauvegarde:**
```bash
# Vérifier SharedPreferences
adb shell "run-as com.retroplay cat /data/data/com.retroplay/shared_prefs/*.xml | grep audio_volume"
```

---

### Test 6: Cancel vs Apply

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

### Test 7: Scroll et UI

**Actions:**
1. Ouvrir le dialog sur un jeu avec beaucoup d'options (ex: MAME avec 10+ options)
2. Scroller dans le dialog
3. Vérifier la lisibilité

**Résultat attendu:**
- ✅ Le dialog scroll correctement
- ✅ Les catégories restent visibles et organisées
- ✅ Les couleurs sont cohérentes (vert pour valeurs, blanc pour textes)
- ✅ Les séparateurs (Divider) sont visibles entre sections

---

### Test 8: Options Booléennes vs Enum

**Actions:**
1. Ouvrir le dialog sur un jeu avec options booléennes (ex: "Enabled/Disabled")
2. Tester le Switch pour les booléennes
3. Tester le Dropdown pour les enums (ex: Resolution)

**Résultat attendu:**
- ✅ Les options booléennes affichent un Switch
- ✅ Les options enum affichent un Dropdown
- ✅ Les deux fonctionnent correctement

---

## Logs à Surveiller

```bash
# Filtrer les logs RetroPlay
adb logcat -s RetroArchEmulatorActivity:V CoreVariableManager:V | Select-String -Pattern "Emulation|Core Options|Apply|Fast Forward|Audio Volume"
```

**Logs attendus:**
- `[CoreVariableManager] Parsed X core options`
- `Applied X core option changes and global settings to running core`
- `[FAST_FORWARD] ENABLED (Xx)`

---

## Problèmes Connus / Limitations

1. **Audio Volume:** Le volume n'est pas encore appliqué au système (nécessite AudioManager)
2. **Fast Forward Ratio:** Nécessite que le fast forward soit activé pour voir l'effet
3. **Catégorisation:** Basée sur des mots-clés, peut ne pas être parfaite pour tous les cores

---

## Résultats Attendus

### ✅ Succès
- Dialog s'ouvre correctement
- Catégorisation fonctionne
- Modifications sont appliquées et sauvegardées
- UI est lisible et scrollable

### ❌ Échecs à Reporter
- Dialog ne s'ouvre pas
- Catégorisation incorrecte
- Modifications non appliquées
- Crash lors de l'application
- UI non scrollable ou illisible

---

## Prochaines Étapes

Après validation:
1. Ajouter d'autres settings globaux (Run-Ahead, Rewind) quand APIs disponibles
2. Implémenter AudioManager pour le volume audio
3. Améliorer la catégorisation si nécessaire
4. Ajouter des descriptions d'aide pour chaque option

