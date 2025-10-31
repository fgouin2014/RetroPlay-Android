# 🎯 Découverte Critique: Zone de Tir du Zapper

**Date:** 31 octobre 2025  
**Découverte par:** Utilisateur (debugging Duck Hunt)  
**Problème:** Zone de tir non-synchronisée avec l'écran de jeu

---

## 🐛 Le Problème

### Symptômes
- Duck Hunt ne détecte pas les tirs correctement
- Fonctionne en landscape mais PAS en portrait
- Zone de détection décalée par rapport au jeu

### Cause Racine

**Code actuel (ligne 188-191 NativeComposeEmulatorActivity.kt):**
```kotlin
// Note: Pour l'instant, on considère que la zone centrale EST la zone de jeu
val centerX = 0.35f..0.65f  // 30% width au centre
val centerY = 0.35f..0.65f  // 30% height au centre

val isInGameArea = normalizedX in centerX && normalizedY in centerY
```

**Problèmes:**
1. ❌ Pourcentage **hardcodé pour landscape** (0.35-0.65)
2. ❌ Ne s'adapte **pas à l'orientation** (portrait vs landscape)
3. ❌ Suppose que le GLRetroView occupe 30% au centre → **FAUX**
4. ❌ Pas de **bounds check exact** du GLRetroView

### Découverte Clé

> "Je testais en portrait mais ce calcul était fait pour le mode panoramique et qui devait couvrir **juste l'écran de jeu en tout temps** et être **synchronisé**"

**La zone de tir doit:**
- Couvrir **exactement les bounds du GLRetroView** (pas un % arbitraire)
- Être **synchronisée** avec la position/taille réelle du GLRetroView
- S'adapter **dynamiquement** à l'orientation

---

## ✅ La Solution

### Étape 1: Obtenir les Bounds Exacts du GLRetroView

```kotlin
// Dans ComposeEmulatorScreen
AndroidView(
    factory = { retroView },
    modifier = Modifier
        .layoutId("gameView")
        .onGloballyPositioned { layoutCoordinates ->
            // Capturer les bounds exacts du GLRetroView
            val bounds = layoutCoordinates.boundsInWindow()
            gameViewBounds.value = bounds
        }
)

// State pour stocker les bounds
val gameViewBounds = remember { mutableStateOf<Rect?>(null) }
```

### Étape 2: Convertir Touch Coordinates → GLRetroView Space

```kotlin
fun handleZapperTouch(
    touchX: Float,  // Coordonnées écran
    touchY: Float,
    screenWidth: Int,
    screenHeight: Int,
    gameViewBounds: Rect  // Bounds du GLRetroView
): Boolean {
    // 1. Vérifier si touch est DANS le GLRetroView
    if (touchX !in gameViewBounds.left..gameViewBounds.right ||
        touchY !in gameViewBounds.top..gameViewBounds.bottom) {
        return false  // Touch en dehors de la zone de jeu
    }
    
    // 2. Convertir en coordonnées relatives au GLRetroView
    val relativeX = (touchX - gameViewBounds.left) / gameViewBounds.width
    val relativeY = (touchY - gameViewBounds.top) / gameViewBounds.height
    
    // 3. Normaliser pour LibretroDroid [-1, +1]
    val normalizedX = (relativeX * 2f) - 1f  // 0..1 → -1..+1
    val normalizedY = (relativeY * 2f) - 1f  // 0..1 → -1..+1
    
    // 4. Envoyer au core
    retroView.sendMotionEvent(
        InputDevice.MOTION_POINTER,
        normalizedX,
        normalizedY
    )
    
    return true
}
```

### Étape 3: Support Multi-Orientation

```kotlin
// Détection automatique de l'orientation
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

// Les bounds du GLRetroView s'adaptent automatiquement via ConstraintLayout
// Pas besoin de calculs spéciaux!
```

---

## 🎮 Implémentation Complète

### Fichier: NativeComposeEmulatorActivity.kt

```kotlin
@Composable
fun ComposeEmulatorScreen(
    retroView: GLRetroView,
    console: String,
    gameName: String,
    // ...
) {
    val isZapperGame = remember { ZapperGameDetector.isZapperGame(gameName, console) }
    val gameViewBounds = remember { mutableStateOf<Rect?>(null) }
    val configuration = LocalConfiguration.current
    
    ConstraintLayout(
        constraintSet = constraintSet,
        modifier = Modifier.fillMaxSize()
    ) {
        // GameView avec bounds tracking
        AndroidView(
            factory = { retroView },
            modifier = Modifier
                .layoutId("gameView")
                .onGloballyPositioned { layoutCoordinates ->
                    val bounds = layoutCoordinates.boundsInWindow()
                    gameViewBounds.value = bounds
                    Log.d(TAG, "GLRetroView bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")
                }
        )
        
        // Zapper Overlay (si jeu Zapper)
        if (isZapperGame) {
            ZapperOverlay(
                retroView = retroView,
                gameViewBounds = gameViewBounds.value,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Gamepads normaux (Lemuroid/RetroArch)
            // ...
        }
    }
}

@Composable
fun ZapperOverlay(
    retroView: GLRetroView,
    gameViewBounds: Rect?,
    modifier: Modifier = Modifier
) {
    var crosshairPosition by remember { mutableStateOf<Offset?>(null) }
    
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val bounds = gameViewBounds ?: return@detectTapGestures
                        
                        // Vérifier si touch dans GLRetroView
                        if (offset.x in bounds.left..bounds.right &&
                            offset.y in bounds.top..bounds.bottom) {
                            
                            // Afficher crosshair
                            crosshairPosition = offset
                            
                            // Convertir en coordonnées GLRetroView
                            val relativeX = (offset.x - bounds.left) / bounds.width
                            val relativeY = (offset.y - bounds.top) / bounds.height
                            
                            // Normaliser pour LibretroDroid
                            val normalizedX = (relativeX * 2f) - 1f
                            val normalizedY = (relativeY * 2f) - 1f
                            
                            // Envoyer au core
                            retroView.sendMotionEvent(
                                InputDevice.MOTION_POINTER,
                                normalizedX,
                                normalizedY
                            )
                            
                            // Trigger (bouton A sur port 2)
                            retroView.sendKeyEvent(
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_BUTTON_A,
                                1  // Port 2 (Zapper)
                            )
                            
                            // Attendre release
                            tryAwaitRelease()
                            
                            // Release trigger
                            retroView.sendKeyEvent(
                                KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_BUTTON_A,
                                1
                            )
                            
                            crosshairPosition = null
                        }
                    }
                )
            }
    ) {
        // DEBUG: Afficher les bounds du GLRetroView
        val bounds = gameViewBounds
        if (bounds != null) {
            // Rectangle vert autour de la zone de jeu
            drawRect(
                color = Color.Green,
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                style = Stroke(width = 4f)
            )
        }
        
        // Crosshair au touch
        val crosshair = crosshairPosition
        if (crosshair != null) {
            val crosshairSize = 40f
            
            // Croix rouge
            drawLine(
                color = Color.Red,
                start = Offset(crosshair.x - crosshairSize, crosshair.y),
                end = Offset(crosshair.x + crosshairSize, crosshair.y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.Red,
                start = Offset(crosshair.x, crosshair.y - crosshairSize),
                end = Offset(crosshair.x, crosshair.y + crosshairSize),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            
            // Cercle central
            drawCircle(
                color = Color.Red,
                radius = 5f,
                center = crosshair
            )
        }
    }
}
```

---

## 🧪 Tests

### Test 1: Duck Hunt Landscape
```
1. Lancer Duck Hunt en landscape
2. Vérifier rectangle vert autour du GLRetroView
3. Taper dans la zone verte → crosshair rouge apparaît
4. Canard doit tomber si touché
```

### Test 2: Duck Hunt Portrait
```
1. Rotation en portrait (automatique)
2. Rectangle vert doit s'adapter à la nouvelle orientation
3. Taper dans la zone verte → crosshair rouge
4. Canard doit tomber si touché
```

### Test 3: Touch en dehors
```
1. Taper en dehors du rectangle vert (overlays, bars)
2. Aucun crosshair ne doit apparaître
3. Aucun événement envoyé au core
```

---

## 📊 Résultats Attendus

### ✅ Avant (Problème)
- ❌ Zone hardcodée 0.35-0.65 (30% au centre)
- ❌ Ne fonctionne QUE en landscape
- ❌ Décalage si GLRetroView pas exactement au centre
- ❌ Pas de feedback visuel des bounds

### ✅ Après (Solution)
- ✅ Zone calculée dynamiquement depuis GLRetroView.bounds
- ✅ Fonctionne en landscape ET portrait
- ✅ Toujours synchronisé avec l'écran de jeu
- ✅ Rectangle vert DEBUG pour visualiser la zone
- ✅ Crosshair précis au pixel

---

## 🚀 Avantages

1. **Précision Absolue**
   - Coordonnées exactes du GLRetroView
   - Conversion pixel-perfect

2. **Multi-Orientation**
   - S'adapte automatiquement à la rotation
   - Pas de calculs spéciaux requis

3. **Debug Visuel**
   - Rectangle vert montre les bounds exacts
   - Crosshair rouge confirme la position du tir

4. **Maintenance**
   - Code clair et documenté
   - Pas de magic numbers

---

## 📝 TODO

- [ ] Implémenter `onGloballyPositioned` pour capturer bounds
- [ ] Créer `ZapperOverlay` composable
- [ ] Ajouter rectangle DEBUG (vert) autour GLRetroView
- [ ] Tester Duck Hunt en landscape
- [ ] Tester Duck Hunt en portrait
- [ ] Tester Hogan's Alley (précision)
- [ ] Option pour désactiver rectangle DEBUG en production

---

## 🎯 Impact

Cette découverte résout **LE problème majeur du Zapper** :
- ❌ Avant: Zone arbitraire, ne fonctionne que en landscape
- ✅ Après: Zone exacte, fonctionne en toutes orientations

**Estimation:** Cette découverte économise ~4-6h de debugging !

---

**Découverte documentée le:** 31 octobre 2025  
**Priorité:** HAUTE (bloquant pour Duck Hunt)  
**Complexité:** Moyenne (2-3h d'implémentation)


