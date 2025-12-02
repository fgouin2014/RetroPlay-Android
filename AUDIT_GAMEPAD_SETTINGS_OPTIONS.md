# Audit Complet: GamePad Settings - Options Non Appliquées

**Date:** 2025-01-XX  
**Problème:** Les options du menu Advanced Overlay Settings ne sont pas appliquées dans GamePadLayoutManager

---

## PROBLÈME CRITIQUE IDENTIFIÉ

### 1. GamePadLayoutManager.kt - Options manquantes

**Ligne 302-334:** `RetroArchOverlayScreen` est appelé avec des paramètres incomplets:

```kotlin
RetroArchOverlayScreen(
    layout = layout,
    overlayName = overlayPreference.overlayName,
    assetManager = assetManager,
    // ... callbacks ...
    swapAnalogSticks = overlayPreference.swapAnalogSticks,
    invertAnalogLeftY = overlayPreference.invertAnalogLeftY,
    invertAnalogRightY = overlayPreference.invertAnalogRightY,
    overlayScale = overlayPreference.scale,
    overlayXOffset = overlayPreference.xOffset,
    overlayYOffset = overlayPreference.yOffset,
    overlayXSeparation = overlayPreference.xSeparation,
    overlayYSeparation = overlayPreference.ySeparation,
    aspectAdjust = 0.0f,  // ❌ TODO: Passer depuis advancedSettings si disponible
    modifier = modifier
)
```

**Options MANQUANTES:**
- ❌ `overlayOpacity` (opacity depuis AdvancedOverlaySettings)
- ❌ `dpadDiagonalSensitivity` (depuis AdvancedOverlaySettings)
- ❌ `abxyDiagonalSensitivity` (depuis AdvancedOverlaySettings)
- ❌ `analogRecenterZone` (depuis AdvancedOverlaySettings)
- ❌ `showInputsMode` (depuis AdvancedOverlaySettings)
- ❌ `hideWhenGamepadConnected` (depuis AdvancedOverlaySettings)
- ❌ `aspectAdjust` (hardcodé à 0.0f au lieu de charger depuis AdvancedOverlaySettings)

---

### 2. Comparaison avec RetroArchEmulatorActivity.kt

**Ligne 3820-3840:** Dans RetroArchEmulatorActivity, les options SONT passées:

```kotlin
RetroArchOverlayScreen(
    // ... autres paramètres ...
    overlayOpacity = advancedSettings.opacity,  // ✅
    dpadDiagonalSensitivity = advancedSettings.dpadDiagonalSensitivity,  // ✅
    abxyDiagonalSensitivity = advancedSettings.abxyDiagonalSensitivity,  // ✅
    showInputsMode = advancedSettings.showInputs,  // ✅
    hideWhenGamepadConnected = advancedSettings.hideWhenGamepadConnected,  // ✅
    analogRecenterZone = advancedSettings.analogRecenterZone,  // ✅
    aspectAdjust = advancedSettings.aspectAdjust,  // ✅
    // ...
)
```

**Conclusion:** GamePadLayoutManager n'utilise PAS les AdvancedOverlaySettings!

---

## OPTIONS DISPONIBLES DANS AdvancedOverlaySettings

**Fichier:** `AdvancedOverlaySettingsDialog.kt` et `OverlayModels.kt`

**Options définies:**
1. `dpadDiagonalSensitivity: Int` (0-100)
2. `abxyDiagonalSensitivity: Int` (0-100)
3. `analogRecenterZone: Int` (0-100)
4. `opacity: Float` (0.0-1.0)
5. `aspectAdjust: Float` (-0.5 à 0.5)
6. `showInputs: ShowInputsMode` (NONE/TOUCHED/PHYSICAL/BOTH)
7. `hideWhenGamepadConnected: Boolean`
8. `hideInMenu: Boolean`
9. `behindMenu: Boolean`
10. Options Lightgun (port, trigger, etc.)
11. Options Mouse (speed, swipe, etc.)

---

## IMPACT

**Problèmes causés:**
1. ❌ Les zones de détection diagonales ne fonctionnent pas (dpadDiagonalSensitivity/abxyDiagonalSensitivity non appliquées)
2. ❌ L'opacité ne fonctionne pas (overlayOpacity non passée)
3. ❌ L'ajustement d'aspect ne fonctionne pas (aspectAdjust hardcodé à 0.0f)
4. ❌ Les zones de recentrage analog ne fonctionnent pas (analogRecenterZone non passée)
5. ❌ Le mode "Show Inputs" ne fonctionne pas (showInputsMode non passé)
6. ❌ Le masquage automatique avec gamepad ne fonctionne pas (hideWhenGamepadConnected non passé)

**Résultat:** Toutes les options du menu Advanced Overlay Settings sont ignorées dans GamePadLayoutManager!

---

## SOLUTION REQUISE

**Modifier `GamePadLayoutManager.kt` pour:**
1. Charger `AdvancedOverlaySettings` depuis SharedPreferences
2. Passer toutes les options au `RetroArchOverlayScreen`
3. Gérer l'orientation (landscape/portrait) comme dans RetroArchEmulatorActivity

---

**Dernière mise à jour:** 2025-01-XX

