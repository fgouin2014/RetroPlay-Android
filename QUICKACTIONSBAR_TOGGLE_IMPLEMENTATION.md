# QuickActionsBar Toggle Implementation

**Date:** 2025-10-31  
**Feature:** Option pour activer/désactiver la QuickActionsBar depuis le menu rapide

---

## Vue d'ensemble

L'utilisateur a demandé une option pour activer/désactiver la QuickActionsBar (barre d'actions rapides en haut de l'écran) directement depuis le menu rapide pendant le jeu.

## Modifications implémentées

### 1. Variable d'état (MutableState)
**Fichier:** `RetroArchEmulatorActivity.kt`

```kotlin
// État pour QuickActionsBar visibility (MutableState pour reactivity Compose)
private val quickActionsBarVisible = mutableStateOf(true)
```

- Valeur par défaut: `true` (visible)
- Compose-reactive pour mise à jour automatique de l'UI

### 2. Chargement/Sauvegarde dans SharedPreferences
**Fichier:** `RetroArchEmulatorActivity.kt` - `onCreate()`

```kotlin
// QuickActionsBar visibility: Charger état
quickActionsBarVisible.value = prefs.getBoolean("emulation_quick_actions_bar_visible", true)
```

**Clé SharedPreferences:** `emulation_quick_actions_bar_visible`

### 3. Fonction Toggle
**Fichier:** `RetroArchEmulatorActivity.kt`

```kotlin
// QuickActionsBar Visibility Toggle
private fun toggleQuickActionsBar() {
    quickActionsBarVisible.value = !quickActionsBarVisible.value
    Log.i(TAG, "[QUICK_ACTIONS_BAR] ${if (quickActionsBarVisible.value) "VISIBLE" else "HIDDEN"}")
    
    // Sauvegarder l'état dans SharedPreferences
    prefs.edit().putBoolean("emulation_quick_actions_bar_visible", quickActionsBarVisible.value).apply()
    
    runOnUiThread {
        Toast.makeText(
            this,
            if (quickActionsBarVisible.value) "Quick Actions Bar Visible" else "Quick Actions Bar Hidden",
            Toast.LENGTH_SHORT
        ).show()
    }
}
```

### 4. Bouton dans QuickMenuDialog
**Fichier:** `RetroArchEmulatorActivity.kt` - `QuickMenuDialog()`

```kotlin
// Bouton Hide/Show QuickActionsBar
androidx.compose.material3.Button(
    onClick = onToggleQuickActionsBar,
    modifier = Modifier.fillMaxWidth(),
    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = if (quickActionsBarVisible) Color(0xFF00BCD4) else Color(0xFF607D8B)
    )
) {
    Text(
        if (quickActionsBarVisible) "QUICK BAR: VISIBLE" else "QUICK BAR: HIDDEN", 
        color = Color.White
    )
}
```

**Couleurs:**
- **Visible (cyan):** `#00BCD4`
- **Hidden (gris):** `#607D8B`

### 5. Affichage conditionnel de la QuickActionsBar
**Fichier:** `RetroArchEmulatorActivity.kt` - `ComposeEmulatorScreen()`

```kotlin
// Quick Actions Bar (Hybrid mode - variante F)
if (quickActionsBarVisible) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
    ) {
        com.retroplay.ui.QuickActionsBar(
            isFastForwardActive = isFastForwardActive,
            audioMuted = audioMuted,
            onToggleFastForward = onToggleFastForward,
            onToggleAudioMute = onToggleAudioMute,
            onQuickSave = { onSaveState(1) },
            onQuickLoad = { onLoadState(1) },
            onCycleShader = onCycleShader,
            currentShaderName = currentShaderName,
            onOpenSettings = { showMainMenu.value = true }
        )
    }
}
```

### 6. Ajustement dynamique de l'offset du GLRetroView
**Fichier:** `RetroArchEmulatorActivity.kt` - `ComposeEmulatorScreen()`

```kotlin
// Offset conditionnel basé sur la visibilité de la QuickActionsBar
val quickBarOffsetDp = if (quickActionsBarVisible) -23.dp else 0.dp

val verticalOffsetDp = if (isLandscape) {
    quickBarOffsetDp  // Landscape : remonter pour QuickBar (si visible)
} else {
    quickBarOffsetDp + (-configuration.screenHeightDp * 0.20f).dp  // Portrait : 20% + QuickBar (si visible)
}
```

**Logique:**
- **Si visible:** Offset de `-23.dp` pour compenser la hauteur de la barre
- **Si cachée:** Offset de `0.dp` (pas de compensation)

### 7. Propagation des paramètres

**Ajouts aux signatures de fonctions:**

- `ComposeEmulatorScreen()`:
  ```kotlin
  onToggleQuickActionsBar: () -> Unit = {},
  quickActionsBarVisible: Boolean = true
  ```

- `QuickMenuDialog()`:
  ```kotlin
  onToggleQuickActionsBar: () -> Unit = {},
  quickActionsBarVisible: Boolean = true
  ```

- Appel dans `setContent`:
  ```kotlin
  onToggleQuickActionsBar = { toggleQuickActionsBar() },
  quickActionsBarVisible = quickActionsBarVisible.value,
  ```

---

## Comportement

### Quand la barre est VISIBLE (défaut)
- La QuickActionsBar est affichée en haut de l'écran
- L'écran de jeu est décalé de `-23.dp` vers le haut
- Bouton dans Quick Menu: **cyan** avec texte "QUICK BAR: VISIBLE"

### Quand la barre est CACHÉE
- La QuickActionsBar n'est pas affichée
- L'écran de jeu n'a aucun offset (plein écran)
- Bouton dans Quick Menu: **gris** avec texte "QUICK BAR: HIDDEN"
- Toast: "Quick Actions Bar Hidden"

---

## Accès utilisateur

1. **Pendant le jeu:** Appuyer sur le bouton **BACK** pour ouvrir le Quick Menu
2. **Dans le Quick Menu:** Cliquer sur le bouton **"QUICK BAR: VISIBLE"** ou **"QUICK BAR: HIDDEN"**
3. **Résultat:** La barre apparaît/disparaît immédiatement et l'état est sauvegardé

---

## Persistance

L'état de la visibilité de la QuickActionsBar est **persistant** entre les sessions de jeu grâce à la sauvegarde dans `SharedPreferences`.

**Clé:** `emulation_quick_actions_bar_visible`  
**Type:** `Boolean`  
**Valeur par défaut:** `true`

---

## Tests à effectuer

1. **Test basique:**
   - Lancer un jeu
   - Appuyer sur BACK pour ouvrir le Quick Menu
   - Cliquer sur "QUICK BAR: VISIBLE"
   - Vérifier que la barre disparaît et l'écran de jeu s'agrandit
   - Recliquer sur "QUICK BAR: HIDDEN"
   - Vérifier que la barre réapparaît

2. **Test persistance:**
   - Cacher la barre
   - Quitter le jeu
   - Relancer le jeu
   - Vérifier que la barre reste cachée

3. **Test orientation:**
   - Tester en mode portrait et landscape
   - Vérifier que l'offset s'ajuste correctement dans les deux orientations

4. **Test fonctionnalité:**
   - Vérifier que les boutons de la QuickActionsBar fonctionnent quand elle est visible
   - Vérifier que les overlays ne sont pas affectés par le toggle

---

## Fichiers modifiés

- `RetroPlay-Android/app/src/main/java/com/retroplay/RetroArchEmulatorActivity.kt`

---

## Compilation et installation

```bash
cd C:\androidProject\ChatAI-Android-beta\RetroPlay-Android
.\gradlew assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Statut:** ✅ Compilé et installé avec succès le 2025-10-31

---

## Prochaines étapes

- Tester sur device réel
- Vérifier le comportement en portrait et landscape
- Confirmer la persistance entre sessions
- Valider l'ajustement de l'offset dans toutes les configurations

