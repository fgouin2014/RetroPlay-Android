# Support Mupen64Plus Next - Implémentation

**Date:** 2025-01-XX  
**Status:** ✅ **IMPLÉMENTÉ**

---

## 📊 RÉSUMÉ

Support des options core N64 (Resolution, Anti-Aliasing, Bilinear) pour Mupen64Plus Next, similaire au support existant pour ParaLLEl N64.

---

## ✅ IMPLÉMENTATION

### Variables Mupen64Plus

**Variables implémentées:**
1. **Resolution:** `mupen64plus-rdp-resolution`
   - Valeurs: `320x240`, `640x480`, `960x720`, `1280x960`
   - Mapping depuis spinner position (0-3)

2. **Anti-Aliasing:** `mupen64plus-rdp-msaa`
   - Valeurs: `0` (disabled), `2` (2x MSAA), `4` (4x MSAA), `8` (8x MSAA)
   - Mapping depuis spinner position (0-3)

3. **Bilinear Filtering:** `mupen64plus-rdp-bilinear`
   - Valeurs: `0` (disabled), `1` (enabled)
   - Mapping depuis checkbox boolean

### Fichiers Modifiés

1. **RetroArchEmulatorActivity.kt** (lignes 1603-1634)
   - Remplacement de `emptyArray<Variable>()` par implémentation complète
   - Mapping des valeurs depuis SharedPreferences

2. **NativeComposeEmulatorActivity.kt** (lignes 1198-1231)
   - Même implémentation que RetroArchEmulatorActivity
   - Cohérence entre les deux modes

3. **Découverte Dynamique** (lignes 1185-1200 dans RetroArch, 958-975 dans Native)
   - Log des variables disponibles pour Mupen64Plus
   - Aide au debugging si les noms de variables ne sont pas corrects

---

## 🔍 MAPPING DES VALEURS

### Resolution
```kotlin
when (resolution) {
    0 -> "320x240"   // Position 0 du spinner
    1 -> "640x480"   // Position 1
    2 -> "960x720"   // Position 2
    3 -> "1280x960"  // Position 3
    else -> "320x240"
}
```

### Anti-Aliasing
```kotlin
when (antialiasing) {
    0 -> "0"  // Disabled (Position 0)
    1 -> "2"  // 2x MSAA (Position 1)
    2 -> "4"  // 4x MSAA (Position 2)
    3 -> "8"  // 8x MSAA (Position 3)
    else -> "0"
}
```

### Bilinear
```kotlin
if (bilinear) "1" else "0"
```

---

## 📝 LOGS DE VALIDATION

**Logs ajoutés pour debugging:**
- Variables configurées lors de l'initialisation
- Variables disponibles découvertes via `getVariables()` après chargement du core
- Note dans les logs si les variables ne fonctionnent pas

**Exemple de logs:**
```
[N64] Mupen64Plus Next detected - applying core options
[N64] Mupen64Plus variables configured: resolution=1, AA=2, bilinear=true
[N64] Mupen64Plus variables applied:
[N64]   mupen64plus-rdp-resolution = 640x480
[N64]   mupen64plus-rdp-msaa = 4
[N64]   mupen64plus-rdp-bilinear = 1
```

---

## ⚠️ NOTES IMPORTANTES

### Noms de Variables

Les noms de variables sont basés sur:
- Documentation RetroArch
- Conventions de nommage Mupen64Plus
- Tests avec d'autres implémentations

**Si les variables ne fonctionnent pas:**
1. Vérifier les logs pour voir les variables disponibles via `getVariables()`
2. Comparer avec les noms utilisés dans RetroArch
3. Ajuster les noms si nécessaire

### Découverte Dynamique

Un mécanisme de découverte dynamique a été ajouté dans `loadCoreVariables()`:
- Filtre les variables contenant "mupen64plus", "rdp", "gfx", "resolution", "msaa", "bilinear"
- Log les 20 premières variables pertinentes
- Aide à identifier les vrais noms de variables si les noms supposés sont incorrects

---

## 🧪 TESTS RECOMMANDÉS

1. **Tester avec un jeu N64:**
   - Lancer un jeu avec Mupen64Plus Next
   - Configurer Resolution, AA, Bilinear dans ConsoleConfigActivity
   - Vérifier que les options sont appliquées

2. **Vérifier les logs:**
   - Chercher `[N64] Mupen64Plus variables applied:`
   - Vérifier que les variables sont bien configurées
   - Si erreur, vérifier les variables disponibles via découverte dynamique

3. **Comparer avec ParaLLEl N64:**
   - Tester les mêmes options avec ParaLLEl N64
   - Comparer le comportement
   - Valider que les deux cores fonctionnent de manière similaire

---

## 📋 PROCHAINES ÉTAPES

1. **Tester l'implémentation** (TODO: mupen64plus_test)
   - Lancer un jeu N64 avec Mupen64Plus Next
   - Vérifier que les options sont appliquées
   - Valider via logs et visuellement

2. **Ajuster si nécessaire**
   - Si les noms de variables sont incorrects, les corriger
   - Utiliser la découverte dynamique pour identifier les vrais noms

3. **Documentation utilisateur**
   - Créer guide pour configuration N64
   - Expliquer les différences entre ParaLLEl et Mupen64Plus

---

**Dernière mise à jour:** 2025-01-XX

