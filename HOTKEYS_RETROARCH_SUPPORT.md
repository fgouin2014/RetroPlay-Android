# Hotkeys RetroArch - Support RetroPlay

**Date:** 2025-01-XX  
**Status:** ✅ Implémenté (avec quelques améliorations)

---

## Vue d'ensemble

RetroPlay supporte les hotkeys RetroArch officiels pour contrôler l'émulation via les overlays ou les gamepads. Les hotkeys peuvent être assignés à des boutons dans les overlays RetroArch (fichiers `.cfg`).

---

## Hotkeys Supportés

### Save/Load States

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `save_state` | Sauvegarder l'état actuel | ✅ | Sauvegarde dans le slot actuel (0-9) |
| `load_state` | Charger l'état sauvegardé | ✅ | Charge depuis le slot actuel (0-9) |
| `state_slot_increase` | Augmenter le slot (0→1→...→9→0) | ✅ | Cycle à travers les slots 0-9 |
| `state_slot_decrease` | Diminuer le slot (9→8→...→0→9) | ✅ | Cycle inverse à travers les slots 0-9 |

**Comportement:**
- Les slots vont de 0 à 9 (10 slots total)
- Le slot actuel est affiché dans un Toast lors du changement
- Les sauvegardes sont stockées dans `/storage/emulated/0/GameLibrary-Data/saves/{console}/slot{N}/{gameName}.state`

---

### Fast Forward / Rewind

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `toggle_fast_forward` | Activer/Désactiver fast forward | ✅ | Toggle on/off |
| `hold_fast_forward` | Maintenir pour accélérer | ✅ | Active tant que maintenu |
| `rewind` | Rembobiner (hold) | ✅ | Nécessite support du core |

**Comportement:**
- `toggle_fast_forward`: Active/désactive le fast forward (2x par défaut)
- `hold_fast_forward`: Active le fast forward tant que le bouton est maintenu
- `rewind`: Utilise `handleHotkeyChange()` pour gérer l'état press/release

---

### Audio / Video

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `audio_mute_toggle` | Activer/Désactiver le son | ✅ | Toggle on/off |
| `shader_next` | Passer au shader suivant | ✅ | Cycle à travers les shaders |
| `shader_prev` | Passer au shader précédent | ✅ | Cycle vers le shader précédent |

**Comportement:**
- `audio_mute_toggle`: Mute/unmute l'audio de l'émulation
- `shader_next`: Change le shader actuel (cycle)

---

### Pause / Frame Control

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `pause_toggle` | Pause/Resume | ✅ | Toggle pause |
| `frame_advance` | Avancer d'une frame | ✅ | Pause → 1 frame → Pause |

**Comportement:**
- `pause_toggle`: Met en pause ou reprend l'émulation
- `frame_advance`: Avance d'exactement 1 frame (utile pour debug)

---

### Reset / Screenshot

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `reset` | Réinitialiser le jeu | ✅ | Soft reset du core |
| `screenshot` | Prendre une capture d'écran | ✅ | Sauvegarde dans la galerie |

**Comportement:**
- `reset`: Redémarre le jeu (soft reset)
- `screenshot`: Capture l'écran actuel et le sauvegarde

---

### Slow Motion (Non Supporté)

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `toggle_slowmotion` | Activer/Désactiver slow motion | ❌ | Non supporté (frameSpeed >= 1 requis) |

**Raison:** LibretroDroid `frameSpeed` est un `Int` et ne supporte pas les valeurs < 1 (slow motion).

---

## Hotkeys Overlay Control

Ces hotkeys sont gérés séparément et ne sont pas dans `HOTKEY_ACTIONS`:

| Hotkey | Description | Status | Notes |
|--------|-------------|--------|-------|
| `overlay_next` | Changer de layout overlay | ✅ | Supporte `next_target` |
| `menu_toggle` | Ouvrir/Fermer le menu | ✅ | Menu principal RetroPlay |

**Comportement:**
- `overlay_next`: Change le layout overlay actuel (ex: portrait → landscape)
- `menu_toggle`: Ouvre/ferme le menu principal RetroPlay

---

## Utilisation dans les Overlays

Les hotkeys peuvent être assignés à des boutons dans les fichiers `.cfg` RetroArch:

```ini
overlay_desc_0 = "save_state"
overlay_desc_0_overlay = "save_state.png"
overlay_desc_0_dpad_none = "true"
overlay_desc_0_button_a = "save_state"
```

**Exemple complet:**
```ini
# Bouton Save State
overlay_desc_0 = "save_state"
overlay_desc_0_overlay = "save_state.png"
overlay_desc_0_normalized = "true"
overlay_desc_0_x = "0.9"
overlay_desc_0_y = "0.1"
overlay_desc_0_w = "0.08"
overlay_desc_0_h = "0.08"
overlay_desc_0_button_a = "save_state"

# Bouton Load State
overlay_desc_1 = "load_state"
overlay_desc_1_overlay = "load_state.png"
overlay_desc_1_normalized = "true"
overlay_desc_1_x = "0.9"
overlay_desc_1_y = "0.2"
overlay_desc_1_w = "0.08"
overlay_desc_1_h = "0.08"
overlay_desc_1_button_a = "load_state"
```

---

## Implémentation Technique

### Fichiers Modifiés

1. **`RetroArchEmulatorActivity.kt`**
   - `handleHotkey(action: String)`: Gère tous les hotkeys instantanés
   - `handleHotkeyChange(action: String, pressed: Boolean)`: Gère les hotkeys avec état (rewind)
   - `currentSaveSlot`: State mutable pour le slot actuel (0-9)

2. **`OverlayModels.kt`**
   - `HOTKEY_ACTIONS`: Set de tous les hotkeys supportés
   - `isHotkeyAction(action: String)`: Vérifie si une action est un hotkey

3. **`RetroArchOverlayRenderer.kt`**
   - Détection des hotkeys dans les overlays
   - Appel de `onHotkey()` et `onHotkeyChange()` selon le type

---

## Hotkeys Manquants / À Implémenter

### Tous les hotkeys essentiels sont maintenant implémentés ✅

**Note:** `shader_prev` a été implémenté avec la fonction `cycleShaderBackward()` qui utilise `ShaderManager.getPreviousShader()`.

---

## Compatibilité RetroArch

**Hotkeys Compatibles:** ✅ 15/15 hotkeys essentiels  
**Hotkeys Non Supportés:** 1 (slow motion - limitation technique)

**Conclusion:** RetroPlay supporte la grande majorité des hotkeys RetroArch essentiels. Les hotkeys manquants sont soit non supportés pour des raisons techniques (slow motion), soit faciles à ajouter (`shader_prev`).

---

## Références

- **Code RetroPlay:** `RetroArchEmulatorActivity.kt` lignes 2297-2429
- **Hotkeys Liste:** `OverlayModels.kt` lignes 285-299
- **Détection Overlay:** `RetroArchOverlayRenderer.kt` lignes 531-537
- **Sources RetroArch:** `c:\repos\RetroArch-master\input\input_driver.c`

---

**Dernière mise à jour:** 2025-01-XX

