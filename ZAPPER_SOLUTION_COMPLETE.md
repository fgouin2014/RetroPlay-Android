# Solution Complète - Zapper NES Fonctionnel

## Méthodologie: "Nos Rules"
- Source de vérité: `c:\repos\RetroArch-master` et `c:\repos\libretro-fceumm-master`
- Implémentation exacte basée sur le code officiel RetroArch
- Pas de simplifications arbitraires

---

## Problème Initial
- Le Zapper NES ne fonctionnait pas dans RetroPlay (Duck Hunt, Gotcha)
- Symptômes:
  * Pas de flash blanc (trigger ne fonctionne pas)
  * Pas de son "POW"
  * Les canards/cibles ne tombent jamais
  * Coordonnées figées

---

## Solution Finale (3 fixes critiques)

### 1. FIX CRITIQUE: Reset POINTER_PRESSED à 0 après ACTION_UP

**Problème:**
```kotlin
// AVANT: Les coordonnées restaient en mémoire après ACTION_UP
// LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0)
// Donc POINTER_PRESSED restait à 1 même après avoir relâché!
// FCEUmm ne voyait jamais la transition 0 → 1 du trigger
```

**Solution:**
```kotlin
// RetroArchEmulatorActivity.kt ligne ~630-650
android.view.MotionEvent.ACTION_UP -> {
    // CRITIQUE: Envoyer coordonnées NÉGATIVES pour forcer POINTER_PRESSED = 0
    // LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0) ? 1 : 0
    retroView.sendMotionEvent(
        com.swordfish.libretrodroid.LibretroDroid.MOTION_SOURCE_POINTER,
        -1f,  // X négatif → pointerScreenXAxis < 0
        -1f,  // Y négatif → pointerScreenYAxis < 0
        lightgunPort
    )
    Log.d(TAG, "[ZAPPER] Touch UP - POINTER reset to (-1, -1), POINTER_PRESSED now FALSE")
    
    return true
}
```

**Résultat:** Le trigger fonctionne maintenant! Flash blanc ✓, son "POW" ✓

---

### 2. FIX CRITIQUE: Configuration FCEUmm sensor = "disabled"

**Problème:**
```
fceumm_zapper_sensor = "enabled"  ← Inverse la logique de détection!
```

FCEUmm avec `sensor = "enabled"` inverse le signal de détection de lumière.
Résultat: Les pixels BLANCS sont interprétés comme NOIRS et vice-versa.

**Solution:**
```ini
# /storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg
fceumm_zapper_sensor = "disabled"
```

**Résultat:** Les cibles tombent maintenant! ✓

---

### 3. FIX CALIBRATION PORTRAIT: Calcul offsetY corrigé

**Problème:**
```kotlin
// AVANT: Ne prenait pas en compte l'offset vertical en portrait
val visualTop = 0f
val visualBottom = bounds.bottom  // 1803
val visualHeight = visualBottom - visualTop  // 1803 ❌
// Mais l'écran fait 2340px! Les coordonnées étaient comprimées!
```

**Solution:**
```kotlin
// RetroArchEmulatorActivity.kt ligne ~549-556
// CORRECTION PORTRAIT: bounds.top négatif signifie que le View déborde en haut
// Exemple: bounds.top=-537, bounds.bottom=1803 → Hauteur View = 2340px
// Touch Y=1170 (centre écran 2340/2) doit mapper à 0.5 (centre jeu)
val offsetY = if (bounds.top < 0) -bounds.top else 0f  // 537 en portrait
val visualTop = 0f  
val visualBottom = bounds.bottom + offsetY  // 1803 + 537 = 2340 (vraie hauteur)
val visualHeight = visualBottom - visualTop  // 2340 ✓
```

**Résultat:** Centre parfaitement calibré en portrait ET panoramique ✓

---

## Système .cfg Complet (Format RetroArch)

### Architecture
```
/storage/emulated/0/RetroPlay-Data/config/
├── FCEUmm/
│   └── FCEUmm.cfg
├── Snes9x/
│   └── Snes9x.cfg
└── ...
```

### CoreConfigManager.kt
**Fonctions principales:**
- `loadConfig(context, coreName)` → Map<String, String>
- `saveConfig(context, coreName, config)`
- `createDefaultConfigIfNeeded(context, coreName, defaultConfig)`
- `getOption(context, coreName, optionName, defaultValue)`

### Intégration
```kotlin
// Au démarrage du core (GLRetroViewData.apply {})
"nes" -> {
    CoreConfigManager.createDefaultConfigIfNeeded(
        this@RetroArchEmulatorActivity, 
        "FCEUmm", 
        CoreConfigManager.getDefaultConfig("fceumm")
    )
    
    val config = CoreConfigManager.loadConfig(this@RetroArchEmulatorActivity, "FCEUmm")
    val nesVariables = config.map { (key, value) -> Variable(key, value) }.toTypedArray()
    variables = nesVariables
}
```

### Configuration Zapper Fonctionnelle
```ini
# /storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg
fceumm_zapper_mode = "touchscreen"
fceumm_zapper_trigger = "enabled"
fceumm_zapper_sensor = "disabled"        # CRITIQUE!
fceumm_zapper_tolerance = "6"            # Radius détection (6-20)
fceumm_show_crosshair = "enabled"        # Afficher réticule
```

---

## Configuration du Port Zapper

**Automatique via variables core:**
```kotlin
// Le .cfg configure automatiquement le Zapper sur Port 2
// Plus besoin du bouton "CONFIGURE ZAPPER NOW"
```

**Logs de confirmation:**
```
I Libretro Core: Player 2: Zapper
I RetroArchEmulator: [NES] Zapper configured as RETRO_DEVICE_ZAPPER (258) on port 2 (index 1)
```

---

## Toast Recommandation Panoramique

**Pour meilleure précision en portrait:**
```kotlin
// RetroArchEmulatorActivity.kt ligne ~745-754
if (isZapperGame) {
    val configuration = resources.configuration
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    if (isPortrait) {
        android.widget.Toast.makeText(
            this,
            "For better Zapper accuracy, use landscape mode",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
```

---

## LibretroDroid - Logs Debug

**Activés pour diagnostic:**
```cpp
// libretrodroid/src/main/cpp/input.cpp ligne ~33-36
// DEBUG: Log POINTER/MOUSE requests only (device 6 or 2)
if (device == 6 || device == 2) {
    LOGI("[NATIVE INPUT] port=%u device=%u index=%u id=%u", port, device, index, id);
}

// ligne ~118-135
case RETRO_DEVICE_ID_POINTER_PRESSED: {
    bool isXActive = pads[port].pointerScreenXAxis >= 0;
    bool isYActive = pads[port].pointerScreenYAxis >= 0;
    int16_t result = (int16_t) (isXActive && isYActive ? 1 : 0);
    LOGI("[NATIVE POINTER] port=%d PRESSED=%d (X=%.3f Y=%.3f)", 
         port, result, pads[port].pointerScreenXAxis, pads[port].pointerScreenYAxis);
    return result;
}
```

---

## Core FCEUmm Source

**Emplacement:** `c:\repos\libretro-fceumm-master\`

**Fix critique appliqué:**
```c
// src/drivers/libretro/libretro.c ligne 2461-2465
/* Fix: Initialize sensor state (mousedata[3])
 * Used when switchZapper != 0 (alternative mode)
 * 0 = light detected when triggered
 */
mousedata[3] = 0;
```

**Core utilisé:**
- Source: `c:\repos\libretro-fceumm-master\jni\libs\arm64-v8a\libretro.so`
- Copié vers: `RetroPlay-Android\app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so`

---

## Comment Reproduire (Future Référence)

### 1. Préparer le Core FCEUmm
```powershell
# Le core est déjà compilé dans c:\repos
Copy-Item "c:\repos\libretro-fceumm-master\jni\libs\arm64-v8a\libretro.so" `
          "RetroPlay-Android\app\src\main\jniLibs\arm64-v8a\fceumm_libretro_android.so" -Force
```

### 2. Code Critique dans RetroArchEmulatorActivity.kt

**A. Calcul calibration portrait (ligne ~549-570):**
```kotlin
val offsetY = if (bounds.top < 0) -bounds.top else 0f  // 537 en portrait
val visualTop = 0f  
val visualBottom = bounds.bottom + offsetY  // 1803 + 537 = 2340
val visualHeight = visualBottom - visualTop  // 2340

val viewportTop = visualTop + (viewport.top * visualHeight)
val viewportBottom = visualTop + (viewport.bottom * visualHeight)
val viewportLeft = bounds.left + (viewport.left * bounds.width)
val viewportRight = bounds.left + (viewport.right * bounds.width)

val clampedX = touchX.coerceIn(viewportLeft, viewportRight)
val clampedY = touchY.coerceIn(viewportTop, viewportBottom)

val viewportWidth = viewportRight - viewportLeft
val viewportHeight = viewportBottom - viewportTop

val relativeX = (clampedX - viewportLeft) / viewportWidth
val relativeY = (clampedY - viewportTop) / viewportHeight
```

**B. Reset POINTER sur ACTION_UP (ligne ~630-650):**
```kotlin
android.view.MotionEvent.ACTION_UP -> {
    retroView.sendMotionEvent(
        LibretroDroid.MOTION_SOURCE_POINTER,
        -1f,  // Force POINTER_PRESSED = 0
        -1f,
        lightgunPort
    )
    return true
}
```

**C. Chargement .cfg au démarrage (ligne ~935-963):**
```kotlin
"nes" -> {
    CoreConfigManager.createDefaultConfigIfNeeded(
        this@RetroArchEmulatorActivity, 
        "FCEUmm", 
        CoreConfigManager.getDefaultConfig("fceumm")
    )
    
    val config = CoreConfigManager.loadConfig(this@RetroArchEmulatorActivity, "FCEUmm")
    val nesVariables = config.map { (key, value) -> Variable(key, value) }.toTypedArray()
    variables = nesVariables
}
```

### 3. Configuration FCEUmm.cfg sur le Device

**Créer:** `/storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg`

**Contenu minimal requis:**
```ini
# FCEUmm Configuration
fceumm_zapper_mode = "touchscreen"
fceumm_zapper_trigger = "enabled"
fceumm_zapper_sensor = "disabled"        # CRITIQUE: disabled = détection correcte
fceumm_zapper_tolerance = "6"
fceumm_show_crosshair = "enabled"
```

### 4. Push du fichier .cfg via ADB
```powershell
adb push "FCEUmm.cfg" "/storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg"
```

---

## Tests de Validation

### Jeux Testés
- ✅ Duck Hunt (World)
- ✅ Gotcha! - The Sport! (USA)

### Checklist Fonctionnement
- [x] Flash blanc quand on touche l'écran (trigger)
- [x] Son "POW" audible
- [x] Canards/cibles tombent quand on vise correctement
- [x] Réticule visible à l'écran
- [x] Calibration correcte au centre (panoramique ET portrait)
- [x] Configuration sauvegardée dans .cfg (pas hardcodée)
- [x] Toast recommandant panoramique en portrait

### Logs de Confirmation
```
I ZapperGameDetector: Zapper game detected: Duck Hunt (World)
I RetroArchEmulator: [ZAPPER] Zapper game detected EARLY: Duck Hunt (World)
I RetroArchEmulator: [NES] Loading FCEUmm configuration from .cfg file
I RetroArchEmulator: [NES] Loaded 5 variables from FCEUmm.cfg
I Libretro Core:  Player 2: Zapper
I RetroArchEmulator: [NES] Zapper configured as RETRO_DEVICE_ZAPPER (258) on port 2
I libretrodroid: [NATIVE POINTER] port=1 PRESSED=1 (X=0.504 Y=0.496)
```

---

## Distorsion Restante (Non-Critique)

### Symptôme
- En **portrait**: Réticule correct au centre, légèrement décalé aux bords
- En **panoramique**: Parfait partout ✓

### Cause
- Écran portrait (1080x2340) a un aspect ratio très différent du NES (256x240)
- Le jeu NES est étiré pour remplir tout l'écran (pas de pillarbox)
- Crée une distorsion non-linéaire aux extrémités

### Workaround Temporaire
```ini
fceumm_zapper_tolerance = "10"   # Augmenter si décalage important
```

### Solution Future (Non-Implémentée)
Implémenter `video_driver_translate_coord_viewport()` de RetroArch (gfx/video_driver.c ligne 693-768):
- Soustraire l'offset du viewport avant scaling
- Utiliser les vraies dimensions du viewport (avec pillarbox/letterbox)
- Formule exacte: `scaled_x = ((mouse_x * 0xffff) / (vp_width - 1)) - 0x8000`

---

## Commits Git

### Commit 39613a8: "Zapper NES fonctionnel + Systeme .cfg complet"
- Fix POINTER_PRESSED reset
- CoreConfigManager.kt
- Configuration automatique depuis .cfg
- Core FCEUmm avec fix mousedata[3]
- LibretroDroid logs debug

### Commit a0beb2c: "Add landscape mode recommendation toast"
- Toast en portrait recommandant panoramique
- Ajout de tout libretrodroid/ au tracking git

---

## Fichiers Modifiés

### Nouveaux Fichiers
- `app/src/main/java/com/retroplay/CoreConfigManager.kt`
- `libretrodroid/src/main/cpp/input.cpp`
- `libretrodroid/src/main/cpp/input.h`
- `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt`
- `libretrodroid/src/main/java/com/swordfish/libretrodroid/LibretroDroid.java`

### Fichiers Modifiés
- `app/src/main/java/com/retroplay/RetroArchEmulatorActivity.kt`
  * handleZapperTouch(): Reset POINTER sur ACTION_UP
  * onCreate(): Chargement .cfg au lieu de hardcode
  * onCreate(): Toast recommandation panoramique
  * Calcul visualBottom corrigé

- `app/src/main/java/com/retroplay/NativeComposeEmulatorActivity.kt`
  * Mêmes modifications que RetroArchEmulatorActivity

- `app/src/main/jniLibs/arm64-v8a/fceumm_libretro_android.so`
  * Core depuis c:\repos avec fix mousedata[3]

---

## Références Code Source Officiel

### RetroArch Android Input
**Fichier:** `c:\repos\RetroArch-master\input\drivers\android_input.c`

**Fonction mapping:** ligne 841-855
```c
video_driver_translate_coord_viewport_confined_wrap(
    &vp, x, y,
    &android->pointer[motion_ptr].confined_x,
    &android->pointer[motion_ptr].confined_y,
    &android->pointer[motion_ptr].full_x,
    &android->pointer[motion_ptr].full_y
);
```

### RetroArch Viewport Translation
**Fichier:** `c:\repos\RetroArch-master\gfx\video_driver.c`

**Fonction:** ligne 693-768
```c
bool video_driver_translate_coord_viewport(
    struct video_viewport *vp,
    int mouse_x, int mouse_y,
    int16_t *res_x, int16_t *res_y,
    int16_t *res_screen_x, int16_t *res_screen_y,
    bool report_oob
)
{
    // ÉTAPE CLÉ: Soustraire l'offset du viewport
    mouse_x -= vp->x;
    mouse_y -= vp->y;
    
    // Scaler avec les dimensions du viewport (pas full screen)
    scaled_x = ((mouse_x * 0xffff) / (norm_vp_width - 1)) - 0x8000;
    scaled_y = ((mouse_y * 0xffff) / (norm_vp_height - 1)) - 0x8000;
}
```

### FCEUmm Zapper Code
**Fichier:** `c:\repos\libretro-fceumm-master\src\input\zapper.c`

**Détection lumière:** ligne 52-113 (ZapperFrapper)
**Lecture trigger:** ligne 168-191 (ReadZapper)
**Update variables:** ligne 198-225 (UpdateZapper)

**Tolerance:** ligne 69-82
```c
if (scanline >= (zy - tolerance) && scanline <= (zy + tolerance)) {
    // Zone circulaire de rayon = tolerance pixels
    if (xs <= (zx + spread) && xs >= (zx - spread)) {
        // Détecte la lumière ici
    }
}
```

---

## Utilisation Finale

### Modifier les Options Zapper
**Via l'interface:**
1. Lancer Duck Hunt/Gotcha
2. Quick Menu → Core Options
3. Modifier les options Zapper
4. Apply → Sauvegarde dans FCEUmm.cfg
5. Redémarrer le jeu pour appliquer

**Via éditeur de texte:**
1. Éditer `/storage/emulated/0/RetroPlay-Data/config/FCEUmm/FCEUmm.cfg`
2. Sauvegarder
3. Relancer le jeu

### Jeux Zapper Détectés
**Fichier:** `app/src/main/java/com/retroplay/ZapperGameDetector.kt`

Liste actuelle:
- "duck hunt"
- "wild gunman"
- "hogan's alley"
- "gumshoe"
- "gotcha"
- "freedom force"
- "laser invasion"
- "mechanized attack"
- "operation wolf"
- "shoot"
- "target"
- "zapper"

---

## Problèmes Résolus

### 1. Trigger ne fonctionnait pas
**Cause:** POINTER_PRESSED restait à 1 après ACTION_UP
**Solution:** Reset à (-1, -1) sur ACTION_UP

### 2. Détection de lumière inversée
**Cause:** `fceumm_zapper_sensor = "enabled"` inverse la logique
**Solution:** `sensor = "disabled"`

### 3. Calibration portrait décalée vers le bas
**Cause:** Calcul visualHeight ne prenait pas en compte offsetY
**Solution:** `visualBottom = bounds.bottom + offsetY`

### 4. Options hardcodées dans le code
**Cause:** Valeurs fixes dans le code Kotlin
**Solution:** Système .cfg complet (CoreConfigManager)

---

## Performance

**Mode Panoramique:** Calibration pixel-perfect ✓
**Mode Portrait:** Calibration correcte au centre, légère distorsion aux bords (acceptable avec tolerance)

---

## Notes pour le Futur

1. **Toujours utiliser le core de `c:\repos`** (pas buildbot)
2. **Vérifier que `sensor = "disabled"`** dans le .cfg
3. **Tester en panoramique d'abord** (calibration optimale)
4. **Le .cfg est la source de vérité** (pas le code hardcodé)
5. **Pour corriger la distorsion portrait:** Implémenter `video_driver_translate_coord_viewport()` de RetroArch

---

## Jeux d'Arcade Light Gun (TODO Future)

Jeux comme Alien 3, T2 Judgment Day utilisent:
- `RETRO_DEVICE_LIGHTGUN` (pas POINTER)
- Coordonnées absolues (pas de détection de lumière)
- Nécessite adaptation du code (différent de NES)

---

**Date de Solution:** 2 Novembre 2025
**Commits:** 39613a8, a0beb2c
**Méthodologie:** "Nos Rules" - Source `c:\repos\RetroArch-master`

