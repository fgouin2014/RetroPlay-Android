# Plan d'implémentation : Support Zapper/Light Gun pour FCEUmm

## 📋 Analyse de l'existant

### ✅ Ce qui fonctionne déjà
1. **LibretroDroid supporte les touch events nativement**
   - `GLRetroView.onTouchEvent()` (ligne 132-148) capture automatiquement les touches
   - `LibretroDroid.onTouchEvent(x, y)` transmet au core via JNI
   - Coordonnées normalisées : [-1, +1] pour X et Y

2. **FCEUmm supporte le Zapper**
   - Type de contrôleur : `RETRO_DEVICE_POINTER` (device ID 6)
   - Entrées requises :
     * `RETRO_DEVICE_ID_POINTER_X` : Position X
     * `RETRO_DEVICE_ID_POINTER_Y` : Position Y
     * `RETRO_DEVICE_ID_POINTER_PRESSED` : Trigger (bouton de tir)

3. **LibretroDroid gère RETRO_DEVICE_POINTER**
   - Fichier : `libretrodroid/src/main/cpp/input.cpp` (ligne 105-115)
   - Retourne les coordonnées pointer et l'état "pressed"

---

## ❌ Ce qui manque actuellement

### 1. **Configuration du type de contrôleur**
**Problème** : Par défaut, FCEUmm utilise un gamepad standard (RETRO_DEVICE_JOYPAD) sur le port 2.  
**Solution** : Appeler `retroView.setControllerType(port, type)` pour configurer le Zapper.

### 2. **Touch events bloqués par les overlays**
**Problème** : Les overlays Compose (RetroArch, Lemuroid) interceptent les touch events avant qu'ils n'atteignent `GLRetroView`.  
**Solution** : Désactiver les overlays pour les jeux Zapper OU rendre les overlays transparents aux touches hors des boutons.

### 3. **Détection automatique des jeux Zapper**
**Problème** : L'utilisateur ne sait pas quand activer le Zapper.  
**Solution** : Détecter automatiquement les jeux nécessitant le Zapper (Duck Hunt, Hogan's Alley, etc.).

### 4. **UI pour le mode Zapper**
**Problème** : L'utilisateur ne voit pas où il vise.  
**Solution** : Afficher un réticule de visée (crosshair) au centre de l'écran tactile.

### 5. **Bouton de tir**
**Problème** : Le touch event seul ne suffit pas, il faut un bouton pour "tirer".  
**Solution** : Utiliser le bouton A du gamepad virtuel OU le tap de l'écran comme trigger.

---

## 🎯 Plan d'implémentation

### **Phase 1 : Configuration du Zapper (automatique)**

#### 1.1. Créer une liste des jeux Zapper
**Fichier** : `ZapperGameDetector.kt` (nouveau)

```kotlin
object ZapperGameDetector {
    // Liste des jeux NES nécessitant le Zapper
    private val ZAPPER_GAMES = setOf(
        "duck hunt",
        "duckhunt",
        "hogan's alley",
        "hogans alley",
        "wild gunman",
        "gumshoe",
        "freedom force",
        "gotcha! the sport!",
        "laser invasion",
        "mechanized attack",
        "operation wolf",
        "shooting range",
        "the adventures of bayou billy",
        "to the earth",
        "town and country surf designs",
        "baby boomer"
    )
    
    fun isZapperGame(romName: String, console: String): Boolean {
        if (console.lowercase() != "nes") return false
        
        val normalizedName = romName
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
        
        return ZAPPER_GAMES.any { normalizedName.contains(it) }
    }
}
```

#### 1.2. Modifier `NativeComposeEmulatorActivity.onCreate()`
**Fichier** : `NativeComposeEmulatorActivity.kt`

```kotlin
// Après l'initialisation du retroView, détecter si Zapper nécessaire
val isZapperGame = ZapperGameDetector.isZapperGame(gameName, console)

if (isZapperGame && console.lowercase() == "nes") {
    Log.i(TAG, "Zapper game detected: $gameName - Configuring port 2 as RETRO_DEVICE_POINTER")
    
    // Configurer le port 2 (player 2) comme Zapper/Pointer
    // RETRO_DEVICE_POINTER = 6
    try {
        retroView.setControllerType(1, 6) // Port 2 (index 1) = RETRO_DEVICE_POINTER
        Log.i(TAG, "Successfully configured Zapper on port 2")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to configure Zapper: ${e.message}")
    }
    
    // Afficher un toast pour informer l'utilisateur
    Toast.makeText(this, "Zapper enabled! Tap screen to shoot", Toast.LENGTH_LONG).show()
}
```

---

### **Phase 2 : Gestion des touch events**

#### 2.1. Vérifier que GLRetroView reçoit les touches
**Fichier** : `NativeComposeEmulatorActivity.kt`

**Problème potentiel** : Les overlays Compose peuvent bloquer les touches.

**Test** : Ajouter des logs dans `GLRetroView.onTouchEvent()` pour vérifier :
```kotlin
// Dans LibretroDroid-master/libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt
override fun onTouchEvent(event: MotionEvent?): Boolean {
    Log.d("GLRetroView", "Touch event received: action=${event?.actionMasked} x=${event?.x} y=${event?.y}")
    // ... rest of code
}
```

#### 2.2. Rendre AndroidView cliquable
**Fichier** : `NativeComposeEmulatorActivity.kt` (dans `ComposeEmulatorScreen`)

```kotlin
AndroidView(
    factory = { retroView },
    modifier = Modifier
        .layoutId("gameView")
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null // Pas d'effet visuel
        ) {
            // Laisser passer au GLRetroView
        }
)
```

**Alternative si bloqué** : Utiliser `pointerInteropFilter` :
```kotlin
AndroidView(
    factory = { retroView },
    modifier = Modifier
        .layoutId("gameView")
        .pointerInteropFilter { motionEvent ->
            // Envoyer manuellement à GLRetroView
            retroView.dispatchTouchEvent(motionEvent)
            true
        }
)
```

---

### **Phase 3 : Bouton de tir (Trigger)**

#### 3.1. Option A : Utiliser le tap comme trigger
**Concept** : Quand l'utilisateur tape l'écran, envoyer un `POINTER_PRESSED`.

**Problème** : `GLRetroView.onTouchEvent()` normalise déjà les coordonnées mais ne gère pas explicitement le "pressed".

**Solution** : Modifier `onTouchEvent` pour envoyer un keycode de bouton A en parallèle :

```kotlin
// Dans NativeComposeEmulatorActivity
// Wrapper autour du touch event
fun handleZapperTouch(event: MotionEvent, retroView: GLRetroView) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            // Envoyer le touch au retroView (position)
            retroView.onTouchEvent(event)
            // Envoyer le trigger (bouton A)
            retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 1) // Port 2
        }
        MotionEvent.ACTION_MOVE -> {
            retroView.onTouchEvent(event)
        }
        MotionEvent.ACTION_UP -> {
            retroView.onTouchEvent(event)
            // Relâcher le trigger
            retroView.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 1)
        }
    }
}
```

#### 3.2. Option B : Utiliser le bouton A du gamepad virtuel
**Concept** : Le gamepad virtuel reste visible, l'utilisateur vise avec l'écran et tire avec le bouton A.

**Avantage** : Plus intuitif, séparation claire entre visée et tir.

**Implémentation** : 
- Garder le gamepad virtuel actif
- Les touch events sur le gamepad passent au retroView
- Le bouton A envoie le trigger sur le port 2 au lieu du port 1

```kotlin
// Dans GamePadLayoutManager ou RetroArchOverlayScreen
onButtonPress = { action ->
    if (action == "a" && isZapperGame) {
        // Envoyer sur port 2 (Zapper)
        retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, 1)
    } else {
        // Port 1 normal
        // ...
    }
}
```

---

### **Phase 4 : UI - Réticule de visée**

#### 4.1. Créer un composable pour le crosshair
**Fichier** : `ZapperCrosshair.kt` (nouveau)

```kotlin
package com.retroplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter

@Composable
fun ZapperCrosshair(
    onTouch: (MotionEvent) -> Boolean
) {
    var touchX by remember { mutableStateOf(0f) }
    var touchY by remember { mutableStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                touchX = event.x
                touchY = event.y
                isTouching = event.actionMasked == MotionEvent.ACTION_DOWN || 
                             event.actionMasked == MotionEvent.ACTION_MOVE
                onTouch(event)
            }
    ) {
        if (isTouching) {
            val crosshairSize = 40f
            val strokeWidth = 3f
            
            // Ligne horizontale
            drawLine(
                color = Color.Red,
                start = Offset(touchX - crosshairSize, touchY),
                end = Offset(touchX + crosshairSize, touchY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Ligne verticale
            drawLine(
                color = Color.Red,
                start = Offset(touchX, touchY - crosshairSize),
                end = Offset(touchX, touchY + crosshairSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // Cercle central
            drawCircle(
                color = Color.Red,
                radius = 5f,
                center = Offset(touchX, touchY)
            )
        }
    }
}
```

#### 4.2. Intégrer le crosshair dans ComposeEmulatorScreen
**Fichier** : `NativeComposeEmulatorActivity.kt`

```kotlin
@Composable
fun ComposeEmulatorScreen(
    retroView: GLRetroView,
    console: String,
    gameName: String,
    // ...
) {
    val isZapperGame = remember { ZapperGameDetector.isZapperGame(gameName, console) }
    
    ConstraintLayout(
        constraintSet = constraintSet,
        modifier = Modifier.fillMaxSize()
    ) {
        // GameView (AndroidView)
        AndroidView(...)
        
        // Overlays (si non-Zapper)
        if (!isZapperGame) {
            // Lemuroid/RetroArch gamepads
        }
        
        // Zapper crosshair (si Zapper)
        if (isZapperGame) {
            ZapperCrosshair(
                onTouch = { event ->
                    handleZapperTouch(event, retroView)
                }
            )
        }
    }
}
```

---

### **Phase 5 : Tests et ajustements**

#### 5.1. Tests à effectuer
1. **Duck Hunt** : Jeu de base
2. **Hogan's Alley** : Vérifier la précision
3. **Wild Gunman** : Vérifier les tirs rapides

#### 5.2. Ajustements potentiels
- **Calibration** : Si la visée est décalée, ajuster les offsets dans FCEUmm
- **Sensibilité** : Ajuster la tolérance du Zapper (`FCEU_ZapperSetTolerance()`)
- **Performance** : Vérifier qu'il n'y a pas de lag entre le tap et le tir

---

## 📊 Ordre d'implémentation recommandé

### Sprint 1 : Configuration de base (30 min)
1. Créer `ZapperGameDetector.kt`
2. Ajouter la détection dans `onCreate()`
3. Appeler `setControllerType(1, 6)`
4. **Test** : Logs pour vérifier que le Zapper est configuré

### Sprint 2 : Touch events (45 min)
1. Vérifier que `GLRetroView` reçoit les touches
2. Ajouter `pointerInteropFilter` si nécessaire
3. Implémenter `handleZapperTouch()` pour le trigger
4. **Test** : Duck Hunt devrait réagir aux taps (même sans visée précise)

### Sprint 3 : UI Crosshair (30 min)
1. Créer `ZapperCrosshair.kt`
2. Intégrer dans `ComposeEmulatorScreen`
3. Conditionner l'affichage avec `isZapperGame`
4. **Test** : Le réticule s'affiche et suit le doigt

### Sprint 4 : Ajustements et polish (45 min)
1. Tester plusieurs jeux Zapper
2. Ajuster la liste `ZAPPER_GAMES` si nécessaire
3. Améliorer le crosshair (couleur, taille, animation au tir)
4. Ajouter une option dans les settings pour activer/désactiver manuellement le Zapper

---

## 🚀 Résultat attendu

**Avant** :
- Duck Hunt ne fonctionne pas (aucune détection des touches)

**Après** :
- Duck Hunt détecté automatiquement
- Zapper configuré sur le port 2
- Réticule rouge s'affiche quand on touche l'écran
- Tap = tir (détection des canards touchés)
- Gamepad virtuel masqué ou adapté

---

## 📝 Notes supplémentaires

### Performance
- Les touch events sont déjà optimisés dans LibretroDroid (JNI natif)
- Le crosshair Canvas est très léger (2-3 lignes + 1 cercle)
- Aucun impact sur les FPS

### Compatibilité
- Fonctionne uniquement avec FCEUmm (NES)
- Les autres cores (SNES9x, etc.) ne supportent pas le Zapper
- Pas de conflit avec les jeux non-Zapper (détection automatique)

### Extensions futures
- Support du Super Scope (SNES) : `RETRO_DEVICE_LIGHTGUN` au lieu de `POINTER`
- Support des jeux multi-Zapper (2 joueurs)
- Calibration manuelle dans les settings
- Haptic feedback au tir (vibration)

---

## ✅ Validation finale

**Critères de succès** :
1. ✅ Duck Hunt se lance et détecte le Zapper
2. ✅ Les taps sont convertis en tirs
3. ✅ Les canards tombent quand touchés
4. ✅ Le réticule s'affiche correctement
5. ✅ Pas de régression sur les jeux non-Zapper

**Prêt à implémenter** : OUI

