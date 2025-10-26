# RetroArch Overlay System - Implementation Plan

**Date:** 2025-10-26  
**Project:** RetroPlay Android  
**Branch:** `feature/retroarch-overlays`  
**Target App:** RetroPlay ONLY (ChatAI sync later)

---

## 🎯 OBJECTIF

Implémenter un système d'overlays RetroArch authentiques pour remplacer/compléter le système Lemuroid touchinput actuel, en utilisant les overlays officiels de `c:\repos\common-overlays-master\gamepads\`.

---

## 📊 ANALYSE COMPARATIVE

### Système Actuel (Lemuroid)

**Architecture:**
```
GamePadLayoutManager.kt
├── getLayout(console, variant) → LayoutPair
├── NESLeft/NESRight (Composables hardcodés)
├── PSXLeft/PSXRight
├── Genesis3Left/Genesis6Left
└── ...
```

**Avantages:**
- ✅ Intégré avec Compose
- ✅ Performance optimale
- ✅ Fonctionne bien

**Limitations:**
- ❌ Layouts hardcodés dans le code
- ❌ Difficile d'ajouter de nouvelles variantes
- ❌ Pas de fichiers de config externes
- ❌ Limité aux layouts programmés

### Système RetroArch (à implémenter)

**Architecture:**
```
common-overlays-master/gamepads/
├── flat/
│   ├── arcade.cfg (9 layouts: landscape-4/6, portrait-4/6, hidden, style2)
│   ├── nes.cfg (12 layouts: landscape/portrait/hidden × A/B/gb)
│   ├── psx.cfg
│   └── img/ (PNG pour chaque bouton)
├── dual-shock/
│   ├── dual-shock.cfg (layouts avec analog sticks)
│   └── img/
└── arcade-anim/
    ├── 6-button-fighter.cfg
    ├── neogeo.cfg
    └── img/
```

**Format .cfg:**
```ini
overlays = 12
overlay0_normalized = true
overlay0_name = "landscape-A"
overlay0_range_mod = 1.5
overlay0_alpha_mod = 2.0

overlay0_desc8 = "a,0.91667,0.85185,radial,0.05000,0.08889"
overlay0_desc8_overlay = img/A.png
overlay0_desc9 = "b,0.80208,0.85185,radial,0.05000,0.08889"
overlay0_desc9_overlay = img/B.png
overlay0_desc10 = "a|b,0.85938,0.83333,radial,0.01667,0.02963"  # Combo button
overlay0_desc15 = "overlay_next,0.35000,0.08889,radial,0.02604,0.046296"
overlay0_desc15_next_target = "portrait-A"  # Switch to portrait
```

**Avantages:**
- ✅ Configuration externe (fichiers .cfg)
- ✅ Overlays multiples par console
- ✅ Support landscape/portrait/hidden
- ✅ Boutons combo (a|b, x|y)
- ✅ Switch dynamique entre layouts
- ✅ Collection complète (arcade, neogeo, saturn, dreamcast)

**Challenges:**
- ⚠️ Parser les fichiers .cfg
- ⚠️ Charger et gérer les images PNG
- ⚠️ Convertir coordonnées normalisées → pixels
- ⚠️ Intégrer avec Compose
- ⚠️ Gérer les touch events

---

## 🏗️ ARCHITECTURE PROPOSÉE

### 1. Modèles de Données

**Fichier:** `app/src/main/java/com/retroplay/overlay/models/OverlayModels.kt`

```kotlin
package com.retroplay.overlay.models

data class RetroArchOverlayConfig(
    val totalOverlays: Int,
    val layouts: Map<String, OverlayLayout>  // "landscape-A" -> OverlayLayout
)

data class OverlayLayout(
    val name: String,                     // "landscape-A", "portrait-6", etc.
    val fullScreen: Boolean,
    val normalized: Boolean,
    val rangeModifier: Float,             // Multiplier for hit zones
    val alphaModifier: Float,             // Transparency
    val buttons: List<OverlayButton>
)

data class OverlayButton(
    val action: String,                   // "a", "b", "left", "a|b" (combo), "overlay_next"
    val x: Float,                         // Position X (0.0-1.0 if normalized)
    val y: Float,                         // Position Y (0.0-1.0 if normalized)
    val shape: ButtonShape,               // RADIAL or RECT
    val width: Float,                     // Width (0.0-1.0 if normalized)
    val height: Float,                    // Height (0.0-1.0 if normalized)
    val imagePath: String?,               // "img/A.png"
    val nextTarget: String?               // For overlay_next buttons
)

enum class ButtonShape {
    RADIAL,   // Circular/elliptical hitbox
    RECT      // Rectangular hitbox
}

// Mapping RetroArch actions -> Android KeyEvents
object RetroArchButtonMapping {
    val ACTION_TO_KEYCODE = mapOf(
        "a" to KeyEvent.KEYCODE_BUTTON_A,
        "b" to KeyEvent.KEYCODE_BUTTON_B,
        "x" to KeyEvent.KEYCODE_BUTTON_X,
        "y" to KeyEvent.KEYCODE_BUTTON_Y,
        "l" to KeyEvent.KEYCODE_BUTTON_L1,
        "r" to KeyEvent.KEYCODE_BUTTON_R1,
        "l2" to KeyEvent.KEYCODE_BUTTON_L2,
        "r2" to KeyEvent.KEYCODE_BUTTON_R2,
        "start" to KeyEvent.KEYCODE_BUTTON_START,
        "select" to KeyEvent.KEYCODE_BUTTON_SELECT,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "up" to KeyEvent.KEYCODE_DPAD_UP,
        "down" to KeyEvent.KEYCODE_DPAD_DOWN
        // Combos handled separately: "a|b", "x|y", etc.
    )
}
```

### 2. Parser RetroArch .cfg

**Fichier:** `app/src/main/java/com/retroplay/overlay/parser/RetroArchOverlayParser.kt`

```kotlin
package com.retroplay.overlay.parser

import com.retroplay.overlay.models.*
import java.io.File

class RetroArchOverlayParser {
    
    /**
     * Parse a RetroArch .cfg file
     */
    fun parseConfig(cfgFile: File): RetroArchOverlayConfig {
        val lines = cfgFile.readLines()
        val totalOverlays = lines.find { it.startsWith("overlays = ") }
            ?.substringAfter("= ")?.toIntOrNull() ?: 0
        
        val layouts = mutableMapOf<String, OverlayLayout>()
        
        for (i in 0 until totalOverlays) {
            val layout = parseOverlay(lines, i)
            if (layout != null) {
                layouts[layout.name] = layout
            }
        }
        
        return RetroArchOverlayConfig(totalOverlays, layouts)
    }
    
    /**
     * Parse a single overlay (e.g. overlay0, overlay1)
     */
    private fun parseOverlay(lines: List<String>, index: Int): OverlayLayout? {
        val prefix = "overlay${index}_"
        
        val name = lines.find { it.startsWith("${prefix}name = ") }
            ?.substringAfter("\"")?.substringBefore("\"") ?: return null
        
        val fullScreen = lines.find { it.startsWith("${prefix}full_screen = ") }
            ?.substringAfter("= ")?.toBoolean() ?: true
        
        val normalized = lines.find { it.startsWith("${prefix}normalized = ") }
            ?.substringAfter("= ")?.toBoolean() ?: true
        
        val rangeMod = lines.find { it.startsWith("${prefix}range_mod = ") }
            ?.substringAfter("= ")?.toFloatOrNull() ?: 1.0f
        
        val alphaMod = lines.find { it.startsWith("${prefix}alpha_mod = ") }
            ?.substringAfter("= ")?.toFloatOrNull() ?: 1.0f
        
        val descCount = lines.find { it.startsWith("${prefix}descs = ") }
            ?.substringAfter("= ")?.toIntOrNull() ?: 0
        
        val buttons = mutableListOf<OverlayButton>()
        for (descIndex in 0 until descCount) {
            val button = parseButton(lines, prefix, descIndex)
            if (button != null) {
                buttons.add(button)
            }
        }
        
        return OverlayLayout(name, fullScreen, normalized, rangeMod, alphaMod, buttons)
    }
    
    /**
     * Parse a single button descriptor
     * Format: overlay0_desc8 = "a,0.91667,0.85185,radial,0.05000,0.08889"
     */
    private fun parseButton(lines: List<String>, prefix: String, descIndex: Int): OverlayButton? {
        val descKey = "${prefix}desc${descIndex}"
        
        // Get button definition line
        val descLine = lines.find { it.trim().startsWith("$descKey = ") } ?: return null
        val descValue = descLine.substringAfter("\"").substringBefore("\"")
        val parts = descValue.split(",")
        
        if (parts.size < 4) return null
        
        val action = parts[0].trim()
        val x = parts[1].trim().toFloatOrNull() ?: return null
        val y = parts[2].trim().toFloatOrNull() ?: return null
        val shape = when (parts[3].trim().lowercase()) {
            "radial" -> ButtonShape.RADIAL
            "rect" -> ButtonShape.RECT
            else -> ButtonShape.RADIAL
        }
        
        val width = if (parts.size > 4) parts[4].trim().toFloatOrNull() ?: 0.05f else 0.05f
        val height = if (parts.size > 5) parts[5].trim().toFloatOrNull() ?: 0.05f else 0.05f
        
        // Get image overlay (if exists)
        val overlayLine = lines.find { it.trim().startsWith("${descKey}_overlay = ") }
        val imagePath = overlayLine?.substringAfter("= ")?.trim()
        
        // Get next target (for overlay_next buttons)
        val nextTargetLine = lines.find { it.trim().startsWith("${descKey}_next_target = ") }
        val nextTarget = nextTargetLine?.substringAfter("\"")?.substringBefore("\"")
        
        return OverlayButton(action, x, y, shape, width, height, imagePath, nextTarget)
    }
}
```

### 3. Asset Manager

**Fichier:** `app/src/main/java/com/retroplay/overlay/assets/OverlayAssetManager.kt`

```kotlin
package com.retroplay.overlay.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

class OverlayAssetManager(private val context: Context) {
    
    companion object {
        const val OVERLAY_DIR = "/storage/emulated/0/RetroPlay-Data/overlays"
    }
    
    /**
     * Install overlays from assets to external storage
     * Called at first launch
     */
    fun installOverlaysIfNeeded() {
        val overlayDir = File(OVERLAY_DIR)
        if (overlayDir.exists()) return  // Already installed
        
        overlayDir.mkdirs()
        
        // Copy from c:\repos\common-overlays-master\gamepads\flat\
        // to /storage/emulated/0/RetroPlay-Data/overlays/flat/
        
        copyOverlayPackage("flat-nes", "flat/nes.cfg", "flat/img/")
        copyOverlayPackage("flat-arcade", "flat/arcade.cfg", "flat/img/")
        copyOverlayPackage("flat-neogeo", "flat/neogeo.cfg", "flat/img/")
        // ... autres overlays
    }
    
    /**
     * Copy an overlay package (cfg + images)
     */
    private fun copyOverlayPackage(packageName: String, cfgPath: String, imgDir: String) {
        val targetDir = File(OVERLAY_DIR, packageName)
        targetDir.mkdirs()
        
        // Copy .cfg file from assets
        context.assets.open("overlays/$cfgPath").use { input ->
            File(targetDir, "${packageName}.cfg").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Copy images
        val imgTargetDir = File(targetDir, "img")
        imgTargetDir.mkdirs()
        
        val imageFiles = context.assets.list("overlays/$imgDir") ?: emptyArray()
        for (imageFile in imageFiles) {
            context.assets.open("overlays/$imgDir/$imageFile").use { input ->
                File(imgTargetDir, imageFile).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    
    /**
     * Load overlay configuration
     */
    fun loadOverlayConfig(overlayName: String): RetroArchOverlayConfig? {
        val cfgFile = File(OVERLAY_DIR, "$overlayName/$overlayName.cfg")
        if (!cfgFile.exists()) return null
        
        return RetroArchOverlayParser().parseConfig(cfgFile)
    }
    
    /**
     * Load button image
     */
    fun loadButtonImage(overlayName: String, imagePath: String): Bitmap? {
        val imageFile = File(OVERLAY_DIR, "$overlayName/$imagePath")
        if (!imageFile.exists()) return null
        
        return BitmapFactory.decodeFile(imageFile.absolutePath)
    }
    
    /**
     * Get list of available overlays
     */
    fun getAvailableOverlays(): List<String> {
        val overlayDir = File(OVERLAY_DIR)
        if (!overlayDir.exists()) return emptyList()
        
        return overlayDir.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }
    
    /**
     * Get available layouts for an overlay
     * e.g. ["landscape-A", "landscape-B", "portrait-A", "hidden-A"]
     */
    fun getAvailableLayouts(overlayName: String): List<String> {
        val config = loadOverlayConfig(overlayName) ?: return emptyList()
        return config.layouts.keys.toList().sorted()
    }
}
```

### 4. Renderer Compose

**Fichier:** `app/src/main/java/com/retroplay/overlay/renderer/RetroArchOverlayRenderer.kt`

```kotlin
package com.retroplay.overlay.renderer

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.retroplay.overlay.models.*
import kotlin.math.sqrt

@Composable
fun RetroArchOverlayScreen(
    layout: OverlayLayout,
    overlayName: String,
    assetManager: OverlayAssetManager,
    onButtonPress: (String) -> Unit,
    onButtonRelease: (String) -> Unit,
    onLayoutSwitch: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Render each button
        layout.buttons.forEach { button ->
            RetroArchButton(
                button = button,
                overlayName = overlayName,
                assetManager = assetManager,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                rangeModifier = layout.rangeModifier,
                alphaModifier = layout.alphaModifier,
                onPress = {
                    if (button.action == "overlay_next" && button.nextTarget != null) {
                        onLayoutSwitch(button.nextTarget)
                    } else {
                        onButtonPress(button.action)
                    }
                },
                onRelease = {
                    if (button.action != "overlay_next") {
                        onButtonRelease(button.action)
                    }
                }
            )
        }
    }
}

@Composable
fun RetroArchButton(
    button: OverlayButton,
    overlayName: String,
    assetManager: OverlayAssetManager,
    screenWidth: Dp,
    screenHeight: Dp,
    rangeModifier: Float,
    alphaModifier: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val density = LocalDensity.current
    
    // Convert normalized coordinates to pixels
    val xPx = with(density) { (button.x * screenWidth.value).dp.toPx() }
    val yPx = with(density) { (button.y * screenHeight.value).dp.toPx() }
    val widthPx = with(density) { (button.width * screenWidth.value * rangeModifier).dp.toPx() }
    val heightPx = with(density) { (button.height * screenHeight.value * rangeModifier).dp.toPx() }
    
    // Load button image (if exists)
    val bitmap = remember(overlayName, button.imagePath) {
        button.imagePath?.let { assetManager.loadButtonImage(overlayName, it) }
    }
    
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .offset(x = with(density) { xPx.toDp() }, y = with(density) { yPx.toDp() })
            .size(width = with(density) { widthPx.toDp() }, height = with(density) { heightPx.toDp() })
            .pointerInput(button.action) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPress()
                        tryAwaitRelease()
                        isPressed = false
                        onRelease()
                    }
                )
            }
    ) {
        // Render button image
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = button.action,
                alpha = if (isPressed) 1.0f else (0.7f * alphaModifier)
            )
        }
    }
}
```

### 5. Intégration GamePadLayoutManager

**Fichier (modifié):** `app/src/main/java/com/retroplay/GamePadLayoutManager.kt`

```kotlin
// Add new variants
enum class LayoutVariant {
    // Existing
    DEFAULT,
    DUALSHOCK,
    BASIC,
    THREE_BUTTON,
    SIX_BUTTON,
    
    // NEW - RetroArch Overlays
    RETROARCH      // Use RetroArch overlay system
}

// Add overlay preferences
fun getOverlayPreference(prefs: SharedPreferences, console: String): Pair<String, String>? {
    val overlayName = prefs.getString("overlay_${console}_name", null)
    val layoutName = prefs.getString("overlay_${console}_layout", null)
    
    return if (overlayName != null && layoutName != null) {
        overlayName to layoutName
    } else {
        null
    }
}

fun setOverlayPreference(
    prefs: SharedPreferences, 
    console: String, 
    overlayName: String, 
    layoutName: String
) {
    prefs.edit()
        .putString("overlay_${console}_name", overlayName)
        .putString("overlay_${console}_layout", layoutName)
        .apply()
}
```

### 6. UI de Sélection

**Fichier (modifié):** `app/src/main/java/com/retroplay/GameDetailsActivity.java`

**Ajouter bouton:** "OVERLAY CONFIG"

```java
private void showOverlayConfigDialog() {
    OverlayAssetManager assetManager = new OverlayAssetManager(this);
    List<String> availableOverlays = assetManager.getAvailableOverlays();
    
    // Filter overlays compatible with current console
    List<String> compatibleOverlays = filterOverlaysForConsole(availableOverlays, currentConsole);
    
    String[] overlayNames = compatibleOverlays.toArray(new String[0]);
    
    new AlertDialog.Builder(this)
        .setTitle("Select Overlay for " + currentConsole.toUpperCase())
        .setItems(overlayNames, (dialog, which) -> {
            String selected = overlayNames[which];
            showLayoutSelectionDialog(selected);
        })
        .setNegativeButton("Cancel", null)
        .show();
}

private void showLayoutSelectionDialog(String overlayName) {
    OverlayAssetManager assetManager = new OverlayAssetManager(this);
    List<String> layouts = assetManager.getAvailableLayouts(overlayName);
    
    String[] layoutNames = layouts.toArray(new String[0]);
    
    new AlertDialog.Builder(this)
        .setTitle("Select Layout")
        .setItems(layoutNames, (dialog, which) -> {
            String selectedLayout = layoutNames[which];
            saveOverlayPreference(overlayName, selectedLayout);
            Toast.makeText(this, "Overlay saved: " + overlayName + " - " + selectedLayout, Toast.LENGTH_LONG).show();
        })
        .setNeutralButton("Preview", (d, w) -> {
            // TODO: Open preview activity
        })
        .setNegativeButton("Cancel", null)
        .show();
}
```

---

## 📂 STRUCTURE DES FICHIERS

### Dans le projet RetroPlay:

```
RetroPlay-Android/
├── app/src/main/
│   ├── assets/
│   │   └── overlays/              ← NOUVEAU
│   │       ├── flat-nes/
│   │       │   ├── flat-nes.cfg
│   │       │   └── img/
│   │       │       ├── A.png
│   │       │       ├── B.png
│   │       │       ├── dpad-left.png
│   │       │       ├── dpad-right.png
│   │       │       ├── dpad-up.png
│   │       │       ├── dpad-down.png
│   │       │       ├── start_genesis.png
│   │       │       ├── rgui.png
│   │       │       ├── hide.png
│   │       │       ├── show.png
│   │       │       ├── rotate.png
│   │       │       ├── overlay-A.png
│   │       │       └── 2-button_gba.png
│   │       ├── flat-arcade/
│   │       │   ├── flat-arcade.cfg
│   │       │   └── img/ (plus d'images: X, Y, L, R, 6-button, etc.)
│   │       ├── dual-shock/
│   │       ├── flat-neogeo/
│   │       └── ...
│   │
│   └── java/com/retroplay/
│       └── overlay/              ← NOUVEAU PACKAGE
│           ├── models/
│           │   └── OverlayModels.kt
│           ├── parser/
│           │   └── RetroArchOverlayParser.kt
│           ├── renderer/
│           │   └── RetroArchOverlayRenderer.kt
│           └── assets/
│               └── OverlayAssetManager.kt
│
└── /storage/emulated/0/RetroPlay-Data/
    └── overlays/                  ← COPIÉ AU RUNTIME
        ├── flat-nes/
        ├── flat-arcade/
        └── ...
```

---

## 🔄 WORKFLOW D'UTILISATION

### 1. Installation (Premier lancement)
```
App Start → OverlayAssetManager.installOverlaysIfNeeded()
         → Copy overlays from assets to /RetroPlay-Data/overlays/
```

### 2. Sélection d'overlay (Utilisateur)
```
GameDetailsActivity → "OVERLAY CONFIG" button
                   → Select overlay (flat-nes, dual-shock, etc.)
                   → Select layout (landscape-A, portrait-B, etc.)
                   → Save to SharedPreferences
```

### 3. Lancement du jeu
```
NativeComposeEmulatorActivity.onCreate()
├── Load console preference (e.g. "nes")
├── Check GamePadLayoutManager.getLayout(variant)
├── If variant == RETROARCH:
│   ├── Load overlay from SharedPrefs (e.g. "flat-nes", "landscape-A")
│   ├── OverlayAssetManager.loadOverlayConfig("flat-nes")
│   ├── Get layout "landscape-A" from config
│   └── Render RetroArchOverlayScreen()
└── Else: Use Lemuroid layouts (NESLeft/Right)
```

### 4. Gameplay
```
User touches button
→ RetroArchButton detects touch
→ Check if inside hitbox (radial/rect + rangeModifier)
→ Send KeyEvent to RetroArch core
→ Visual feedback (alpha change)
```

### 5. Layout switching (In-game)
```
User taps overlay_next button (e.g. "rotate" icon)
→ onLayoutSwitch("portrait-A")
→ Reload layout from config
→ Re-render overlay
```

---

## 🎯 ORDRE D'IMPLÉMENTATION

### PHASE 1: Infrastructure (Jour 1-2)
1. ✅ Créer branche Git `feature/retroarch-overlays`
2. ✅ Créer ce document de design
3. ⏳ Créer `OverlayModels.kt`
4. ⏳ Créer `RetroArchOverlayParser.kt`
5. ⏳ Test unitaire du parser avec `flat/nes.cfg`

### PHASE 2: Assets (Jour 2-3)
6. ⏳ Copier overlays de `c:\repos\common-overlays-master\gamepads\flat\` vers `app/src/main/assets/overlays/`
   - Commencer avec NES uniquement (flat/nes.cfg + img/)
7. ⏳ Créer `OverlayAssetManager.kt`
8. ⏳ Tester installation au premier lancement

### PHASE 3: Renderer (Jour 3-5)
9. ⏳ Créer `RetroArchOverlayRenderer.kt`
10. ⏳ Implémenter `RetroArchOverlayScreen` (Composable)
11. ⏳ Implémenter `RetroArchButton` (Composable)
12. ⏳ Gérer touch detection (radial vs rect)
13. ⏳ Gérer multi-touch (plusieurs boutons simultanés)

### PHASE 4: Intégration NES (Jour 5-7) ⭐ VALIDATION
14. ⏳ Modifier `GamePadLayoutManager` pour supporter `RETROARCH` variant
15. ⏳ Intégrer dans `NativeComposeEmulatorActivity`
16. ⏳ Test complet avec un jeu NES
17. ⏳ Validation : landscape/portrait/hidden switching
18. ⏳ Validation : combo buttons (a|b)

### PHASE 5: UI Sélection (Jour 7-8)
19. ⏳ Ajouter bouton "OVERLAY CONFIG" dans `GameDetailsActivity`
20. ⏳ Dialog de sélection overlay
21. ⏳ Dialog de sélection layout
22. ⏳ Preview système (optionnel)

### PHASE 6: Expansion consoles (Jour 8+)
23. ⏳ SNES (flat/snes.cfg)
24. ⏳ Genesis (flat/genesis.cfg)
25. ⏳ Arcade (flat/arcade.cfg + flat/neogeo.cfg + flat/cps.cfg)
26. ⏳ PSX (dual-shock/dual-shock.cfg)
27. ⏳ Autres consoles selon besoin

### PHASE 7: Validation RetroPlay (Jour finale)
28. ⏳ Tests complets toutes consoles
29. ⏳ Export APK de test
30. ⏳ Merge vers master

### PHASE 8: Sync ChatAI (Après validation)
31. ⏳ Port vers ChatAI
32. ⏳ Tests ChatAI
33. ⏳ Validation finale

---

## ⚠️ RISQUES & MITIGATION

### Risque 1: Performance (Rendering de nombreux PNG)
**Mitigation:**
- Cache les Bitmaps en mémoire
- Utiliser `remember {}` dans Compose
- Optimiser la taille des images (compress si nécessaire)

### Risque 2: Régression (Casser le système actuel)
**Mitigation:**
- Mode LEGACY (Lemuroid) par défaut
- Mode RETROARCH opt-in (sélection manuelle)
- Ne PAS supprimer le code Lemuroid existant

### Risque 3: Touch detection imprécise
**Mitigation:**
- Tester avec `rangeModifier` (1.5 par défaut dans RetroArch)
- Permettre ajustement par l'utilisateur
- Visualiser les hitboxes en mode debug

### Risque 4: Coordonnées normalisées incorrectes
**Mitigation:**
- Tester landscape ET portrait
- Vérifier rotation d'écran
- Comparer avec RetroArch Android officiel

---

## 🧪 TESTS CRITIQUES

### Test 1: Parser .cfg
- ✅ Charger `flat/nes.cfg`
- ✅ Vérifier 12 layouts détectés
- ✅ Vérifier coordonnées correctes pour "landscape-A"
- ✅ Vérifier images chargées

### Test 2: Touch Detection
- ✅ Toucher bouton A → KeyEvent.KEYCODE_BUTTON_A
- ✅ Toucher bouton B → KeyEvent.KEYCODE_BUTTON_B
- ✅ Toucher a|b (combo) → Les 2 keycodes simultanés
- ✅ Toucher D-Pad directions

### Test 3: Layout Switching
- ✅ Toucher "rotate" → Switch landscape → portrait
- ✅ Toucher "hide" → Switch visible → hidden
- ✅ Toucher "show" → Switch hidden → visible
- ✅ Toucher "overlay-A/B" → Switch entre variantes

### Test 4: NES Gameplay
- ✅ Jouer Super Mario Bros
- ✅ Tester tous les boutons (A, B, D-Pad, Start, Select)
- ✅ Tester rotation portrait/landscape
- ✅ Vérifier performance (pas de lag)

---

## 📋 CHECKLIST VALIDATION

### Avant merge vers master:
- [ ] Parser fonctionne pour tous les .cfg
- [ ] Renderer affiche correctement tous les layouts
- [ ] Touch detection 100% précise
- [ ] NES complètement fonctionnel
- [ ] Au moins 3 consoles testées (NES, SNES, Arcade)
- [ ] Pas de régression sur layouts Lemuroid
- [ ] Performance acceptable (60fps)
- [ ] Documentation complète
- [ ] APK de test exporté

---

## 🔮 FUTURES AMÉLIORATIONS

### Post-implémentation:
- Preview overlay avant sélection
- Éditeur de layout (ajuster positions)
- Support des overlays animés (arcade-anim/)
- Import d'overlays custom depuis URL/fichier
- Synchronisation cloud des préférences d'overlay
- Support des boutons L2/R2 analogiques (PSX)
- Support des sticks analogiques (PSX, N64, PSP)

---

## 📞 RÉFÉRENCES

### Sources:
- `c:\repos\common-overlays-master\gamepads\`
- `c:\repos\Lemuroid\lemuroid-touchinput\`
- `c:\repos\RetroArch-master\` (référence API)

### Documentation RetroArch:
- https://docs.libretro.com/guides/libretro-overlays/
- Format .cfg specification
- Normalized coordinates (0.0-1.0)
- Button mapping standard

---

**FIN DU DOCUMENT DE DESIGN**

*Ce document sera mis à jour au fur et à mesure de l'implémentation.*

