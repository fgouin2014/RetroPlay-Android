# Plan - Configuration Per-Orientation Overlays

**Date:** 2025-01-XX  
**Priorité:** P3 (Court Terme)  
**Temps estimé:** 2-4 heures

---

## 🎯 OBJECTIF

Permettre des settings avancés différents pour landscape et portrait, au lieu d'avoir des settings globaux.

**Exemple d'usage:**
- Landscape: Opacity 0.7, D-Pad sensitivity 80%
- Portrait: Opacity 0.5, D-Pad sensitivity 60% (moins de place, besoin de moins de sensibilité)

---

## 📊 ÉTAT ACTUEL

### Ce qui fonctionne déjà
- ✅ Layouts séparés: `landscapeLayout` et `portraitLayout` dans `OverlayPreference`
- ✅ Détection orientation: `isLandscape` dans les activités
- ✅ Switch automatique de layout selon orientation

### Ce qui manque
- ❌ Settings avancés globaux: `dpadDiagonalSensitivity`, `opacity`, etc. sont les mêmes pour landscape/portrait
- ❌ Pas de séparation par orientation dans `AdvancedOverlaySettings`

---

## 🏗️ ARCHITECTURE PROPOSÉE

### 1. Modification des clés SharedPreferences

**Actuel:**
```
overlay_${console}_dpad_diagonal_sensitivity = 80
overlay_${console}_opacity = 0.7
```

**Nouveau:**
```
overlay_${console}_dpad_diagonal_sensitivity_landscape = 80
overlay_${console}_dpad_diagonal_sensitivity_portrait = 60
overlay_${console}_opacity_landscape = 0.7
overlay_${console}_opacity_portrait = 0.5
```

**Migration:**
- Si clé avec `_landscape` ou `_portrait` existe → utiliser
- Sinon, si ancienne clé globale existe → copier vers les deux orientations
- Sinon → utiliser valeurs par défaut

### 2. Modification `OverlayPreferenceManager`

**Fichier:** `app/src/main/java/com/retroplay/overlay/models/OverlayModels.kt`

**Fonctions à modifier:**
- `loadAdvancedSettings()` → Ajouter paramètre `orientation: String?` (null = global, "landscape", "portrait")
- `saveAdvancedSettings()` → Ajouter paramètre `orientation: String?`

**Nouvelle signature:**
```kotlin
fun loadAdvancedSettings(
    prefs: SharedPreferences,
    console: String,
    orientation: String? = null  // null = global (backward compat), "landscape", "portrait"
): AdvancedOverlaySettings {
    val suffix = if (orientation != null) "_$orientation" else ""
    // ...
    dpadDiagonalSensitivity = prefs.getInt("overlay_${console}_dpad_diagonal_sensitivity$suffix", 
        // Fallback: essayer global, puis default
        prefs.getInt("overlay_${console}_dpad_diagonal_sensitivity", 80))
    // ...
}
```

### 3. Modification `AdvancedOverlaySettingsDialog`

**Fichier:** `app/src/main/java/com/retroplay/AdvancedOverlaySettingsDialog.kt`

**Changements:**
1. Détecter orientation actuelle du device
2. Ajouter toggle "Use same settings for both orientations" (par défaut: false)
3. Si toggle activé → sauvegarder global (compatibilité)
4. Si toggle désactivé → sauvegarder selon orientation actuelle
5. Optionnel: Ajouter onglets Landscape/Portrait pour éditer les deux

**Signature modifiée:**
```kotlin
@Composable
fun AdvancedOverlaySettingsDialog(
    console: String,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    currentOrientation: String? = null  // "landscape" ou "portrait", null = auto-detect
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val orientation = currentOrientation ?: if (isLandscape) "landscape" else "portrait"
    
    // Toggle pour "Use same settings for both"
    var useSameSettings by remember { 
        mutableStateOf(
            // Vérifier si les deux orientations ont les mêmes valeurs
            checkIfSettingsAreSame(prefs, console)
        )
    }
    
    // Charger settings selon orientation
    var settings by remember(orientation) { 
        mutableStateOf(
            OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
        )
    }
    
    // ...
}
```

### 4. Utilisation dans les activités

**Fichiers:**
- `RetroArchEmulatorActivity.kt`
- `NativeComposeEmulatorActivity.kt`

**Changements:**
- Passer `orientation` à `loadAdvancedSettings()` selon `isLandscape`
- Passer `orientation` à `AdvancedOverlaySettingsDialog()`

**Exemple:**
```kotlin
val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
val orientation = if (isLandscape) "landscape" else "portrait"
val lightgunSettings = OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)

// ...

AdvancedOverlaySettingsDialog(
    console = console,
    onDismiss = { showAdvancedOverlaySettings.value = false },
    context = this,
    prefs = prefs,
    currentOrientation = orientation
)
```

---

## 📝 IMPLÉMENTATION DÉTAILLÉE

### Étape 1: Migration des clés SharedPreferences

**Fonction helper:**
```kotlin
fun migrateAdvancedSettingsToPerOrientation(
    prefs: SharedPreferences,
    console: String
) {
    // Vérifier si migration déjà faite
    if (prefs.contains("overlay_${console}_dpad_diagonal_sensitivity_landscape")) {
        return  // Déjà migré
    }
    
    // Lire valeurs globales actuelles
    val globalSettings = loadAdvancedSettings(prefs, console, null)
    
    // Copier vers landscape et portrait
    saveAdvancedSettings(prefs, console, "landscape", globalSettings)
    saveAdvancedSettings(prefs, console, "portrait", globalSettings)
    
    // Optionnel: Supprimer anciennes clés globales (ou les garder pour compatibilité)
}
```

### Étape 2: Modification `loadAdvancedSettings`

```kotlin
fun loadAdvancedSettings(
    prefs: SharedPreferences,
    console: String,
    orientation: String? = null
): AdvancedOverlaySettings {
    val suffix = if (orientation != null) "_$orientation" else ""
    
    // Helper pour lire avec fallback
    fun getInt(key: String, default: Int): Int {
        val perOrientationKey = "overlay_${console}_${key}$suffix"
        val globalKey = "overlay_${console}_${key}"
        
        return when {
            prefs.contains(perOrientationKey) -> prefs.getInt(perOrientationKey, default)
            prefs.contains(globalKey) -> prefs.getInt(globalKey, default)  // Fallback global
            else -> default
        }
    }
    
    fun getFloat(key: String, default: Float): Float {
        val perOrientationKey = "overlay_${console}_${key}$suffix"
        val globalKey = "overlay_${console}_${key}"
        
        return when {
            prefs.contains(perOrientationKey) -> prefs.getFloat(perOrientationKey, default)
            prefs.contains(globalKey) -> prefs.getFloat(globalKey, default)
            else -> default
        }
    }
    
    fun getBoolean(key: String, default: Boolean): Boolean {
        val perOrientationKey = "overlay_${console}_${key}$suffix"
        val globalKey = "overlay_${console}_${key}"
        
        return when {
            prefs.contains(perOrientationKey) -> prefs.getBoolean(perOrientationKey, default)
            prefs.contains(globalKey) -> prefs.getBoolean(globalKey, default)
            else -> default
        }
    }
    
    // ... utiliser les helpers pour tous les champs
}
```

### Étape 3: Modification `saveAdvancedSettings`

```kotlin
fun saveAdvancedSettings(
    prefs: SharedPreferences,
    console: String,
    orientation: String?,
    settings: AdvancedOverlaySettings
) {
    val suffix = if (orientation != null) "_$orientation" else ""
    val editor = prefs.edit()
    
    editor.putInt("overlay_${console}_dpad_diagonal_sensitivity$suffix", settings.dpadDiagonalSensitivity)
    editor.putInt("overlay_${console}_abxy_diagonal_sensitivity$suffix", settings.abxyDiagonalSensitivity)
    // ... tous les autres champs
    
    editor.commit()
}
```

### Étape 4: Helper pour vérifier si settings identiques

```kotlin
fun checkIfSettingsAreSame(
    prefs: SharedPreferences,
    console: String
): Boolean {
    val landscape = loadAdvancedSettings(prefs, console, "landscape")
    val portrait = loadAdvancedSettings(prefs, console, "portrait")
    
    return landscape == portrait  // Nécessite que AdvancedOverlaySettings soit data class avec equals()
}
```

### Étape 5: Modification Dialog

```kotlin
@Composable
fun AdvancedOverlaySettingsDialog(
    console: String,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    currentOrientation: String? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val orientation = currentOrientation ?: if (isLandscape) "landscape" else "portrait"
    
    // Toggle "Use same settings for both"
    var useSameSettings by remember { 
        mutableStateOf(checkIfSettingsAreSame(prefs, console))
    }
    
    // Charger settings selon orientation
    var settings by remember(orientation, useSameSettings) { 
        mutableStateOf(
            if (useSameSettings) {
                // Si "same settings", charger global (ou landscape par défaut)
                OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                    ?: OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
            } else {
                OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
            }
        )
    }
    
    // ... UI existante ...
    
    // Ajouter toggle en haut du dialog
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Use Same Settings for Both Orientations",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "When enabled, landscape and portrait share the same settings",
                color = Color(0xFF888888),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = useSameSettings,
            onCheckedChange = { 
                useSameSettings = it
                if (it) {
                    // Copier settings actuels vers l'autre orientation
                    val current = OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
                    OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", current)
                    OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", current)
                }
            }
        )
    }
    
    // Sauvegarder selon toggle
    LaunchedEffect(/* ... tous les settings ... */) {
        if (useSameSettings) {
            // Sauvegarder global (ou les deux orientations avec même valeur)
            OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", settings)
            OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", settings)
        } else {
            // Sauvegarder selon orientation actuelle
            OverlayPreferenceManager.saveAdvancedSettings(prefs, console, orientation, settings)
        }
    }
}
```

---

## ✅ TESTS

### Test 1: Migration
- Vérifier que anciennes clés globales sont migrées vers landscape/portrait
- Vérifier que valeurs par défaut sont appliquées si aucune clé n'existe

### Test 2: Settings séparés
- Configurer opacity 0.7 en landscape
- Configurer opacity 0.5 en portrait
- Vérifier que les deux sont sauvegardés séparément
- Vérifier que le bon setting est chargé selon orientation

### Test 3: Toggle "Use same settings"
- Activer toggle
- Modifier un setting
- Vérifier que les deux orientations sont mises à jour
- Désactiver toggle
- Vérifier que settings restent séparés

### Test 4: Backward compatibility
- Vérifier que anciennes installations (sans clés per-orientation) fonctionnent
- Vérifier que migration automatique fonctionne

---

## 📋 CHECKLIST

- [ ] Modifier `loadAdvancedSettings()` pour accepter `orientation`
- [ ] Modifier `saveAdvancedSettings()` pour accepter `orientation`
- [ ] Ajouter migration automatique des clés globales
- [ ] Modifier `AdvancedOverlaySettingsDialog` pour détecter orientation
- [ ] Ajouter toggle "Use same settings for both"
- [ ] Modifier `RetroArchEmulatorActivity` pour passer orientation
- [ ] Modifier `NativeComposeEmulatorActivity` pour passer orientation
- [ ] Tests migration
- [ ] Tests settings séparés
- [ ] Tests backward compatibility

---

## 🔄 PROCHAINES ÉTAPES

Après cette implémentation:
1. Preview Overlay (1-2 jours)
2. Support souris relative (si nécessaire)
3. Autres améliorations UX

---

**Note:** Cette implémentation est backward compatible. Les anciennes installations continueront de fonctionner avec des settings globaux jusqu'à ce qu'elles soient migrées automatiquement.

