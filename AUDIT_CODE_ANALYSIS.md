# Audit Nos Rules RetroPlay - Analyse du Code

**Date:** 2025-01-XX  
**Méthodologie:** Nos Rules - Comparaison exhaustive code source RetroArch vs RetroPlay  
**Sources analysées:**
- `c:\repos\RetroArch-master\input\drivers\android_input.c` (2000+ lignes)
- `c:\repos\RetroArch-master\input\drivers_joypad\android_joypad.c` (200+ lignes)
- `RetroPlay-Android/libretrodroid/src/main/cpp/input.cpp` (400+ lignes)
- `RetroPlay-Android/libretrodroid/src/main/cpp/environment.cpp` (500+ lignes)
- `RetroPlay-Android/app/src/main/java/com/retroplay/RetroArchEmulatorActivity.kt` (1500+ lignes)
- `RetroPlay-Android/app/src/main/java/com/retroplay/overlay/renderer/RetroArchOverlayRenderer.kt` (1200+ lignes)

---

## Partie 1 : Architecture générale

### 1.1 RetroArch Android (officiel)

**Structure:**
```
android_input.c (Input Driver)
    ├── android_input_init()
    ├── android_input_poll()
    ├── android_input_poll_event_type_keyboard()
    ├── android_input_poll_event_type_motion()
    └── android_input_state()

android_joypad.c (Controller Driver)
    ├── android_joypad_init()
    ├── android_joypad_query_pad()
    ├── android_joypad_state()
    ├── android_joypad_button()
    ├── android_joypad_axis()
    ├── android_joypad_poll()
    └── android_joypad_rumble()

handle_hotplug() (Autoconfig)
    ├── Détection device (VID/PID/Name)
    ├── Hacks spéciaux (Shield, Xperia Play, etc.)
    └── input_autoconfigure_connect()
```

**Caractéristiques:**
- Input driver gère clavier, souris, touchscreen, pointeur
- Joypad driver gère gamepads via autoconfig
- Hotplug automatique des devices
- Hacks spéciaux pour devices connus (Shield, Xperia Play, etc.)

### 1.2 RetroPlay (LibretroDroid)

**Structure:**
```
input.cpp (Native Input Handler)
    ├── Input::onKeyEvent()
    ├── Input::onMotionEvent()
    ├── Input::getInputState()
    └── Input::convertAndroidToLibretroKey()

environment.cpp (Environment Handler)
    ├── Environment::handle_callback_environment()
    ├── Environment::environment_handle_set_controller_info()
    └── Environment::getControllers()

RetroArchEmulatorActivity.kt (Android Layer)
    ├── Configuration PSX DualShock
    ├── Configuration N64 Extensions (TODO)
    └── Configuration Zapper/Lightgun

RetroArchOverlayRenderer.kt (Compose Overlay)
    ├── RetroArchOverlayScreen()
    ├── handleOverlayTouch()
    └── Support analog sticks, dpad_area, abxy_area
```

**Caractéristiques:**
- Input handler natif (C++) gère key/motion events
- Environment handler gère callbacks Libretro
- Android layer (Kotlin) gère configuration consoles
- Compose overlay (Kotlin) gère rendu touchscreen

---

## Partie 2 : Comparaison Input Handling

### 2.1 Gestion des événements clavier

**RetroArch Android:**
```c
// android_input.c:879-950
static INLINE void android_input_poll_event_type_keyboard(
      AInputEvent *event, int keycode, int *handled)
{
    int keydown = (AKeyEvent_getAction(event) == AKEY_EVENT_ACTION_DOWN);
    unsigned keyboardcode = input_keymaps_translate_keysym_to_rk(keycode);
    uint16_t mod = 0;
    int meta = AKeyEvent_getMetaState(event);
    
    // Mapping modifiers (Alt, Ctrl, Shift, etc.)
    if (meta & AMETA_ALT_ON) mod |= RETROKMOD_ALT;
    if (meta & AMETA_CTRL_ON) mod |= RETROKMOD_CTRL;
    if (meta & AMETA_SHIFT_ON) mod |= RETROKMOD_SHIFT;
    
    // Envoyer au core via input_driver_state_t
    input_driver_keyboard_event(keydown, keyboardcode, keycode, mod, RETRO_DEVICE_KEYBOARD);
}
```

**RetroPlay:**
```cpp
// input.cpp:175-220
int Input::convertAndroidToLibretroKey(int keyCode) const {
    switch (keyCode) {
        case AKEYCODE_BUTTON_START:   return RETRO_DEVICE_ID_JOYPAD_START;
        case AKEYCODE_BUTTON_SELECT:  return RETRO_DEVICE_ID_JOYPAD_SELECT;
        case AKEYCODE_BUTTON_A:       return RETRO_DEVICE_ID_JOYPAD_A;
        // ... (mapping complet RetroPad)
    }
}

void Input::onKeyEvent(unsigned int port, int action, int keyCode) {
    int retroKeyCode = convertAndroidToLibretroKey(keyCode);
    if (retroKeyCode == UNKNOWN_KEY) return;
    
    if (action == AKEY_EVENT_ACTION_DOWN) {
        pads[port].pressedKeys.insert(retroKeyCode);
    } else if (action == AKEY_EVENT_ACTION_UP) {
        pads[port].pressedKeys.erase(retroKeyCode);
    }
}
```

**Comparaison:**
- ✅ RetroPlay mappe directement keycodes Android → RetroPad (simplifié)
- ⚠️ RetroArch supporte clavier complet (modifiers, keymaps) via `input_keymaps_translate_keysym_to_rk()`
- ✅ RetroPlay gère seulement gamepad buttons (suffisant pour la plupart des cas)

### 2.2 Gestion des événements motion (analog/pointer)

**RetroArch Android:**
```c
// android_input.c:727-867
static INLINE void android_input_poll_event_type_motion(
      android_input_t *android, AInputEvent *event,
      int port, int source)
{
    // Détection souris vs touchscreen
    if ((source & AINPUT_SOURCE_MOUSE) == AINPUT_SOURCE_MOUSE) {
        android_mouse_calculate_deltas(android, event, motion_ptr, source);
        // Envoyer via input_driver_state_t
        return;
    }
    
    // Gestion multi-touch (touchscreen)
    int pointer_max = MIN(AMotionEvent_getPointerCount(event), MAX_TOUCH);
    for (motion_ptr = 0; motion_ptr < pointer_max; motion_ptr++) {
        float x = AMotionEvent_getX(event, motion_ptr);
        float y = AMotionEvent_getY(event, motion_ptr);
        
        // Conversion viewport (confined_wrap + wrap)
        video_driver_translate_coord_viewport_confined_wrap(&vp, x, y, ...);
        video_driver_translate_coord_viewport_wrap(&vp, x, y, ...);
        
        android->pointer[motion_ptr].x = ...
        android->pointer[motion_ptr].y = ...
    }
}
```

**RetroPlay:**
```cpp
// input.cpp:235-258
void Input::onMotionEvent(int port, int motionSource, float xAxis, float yAxis) {
    switch (motionSource) {
        case Input::MOTION_SOURCE_DPAD:
            pads[port].dpadXAxis = (int) round(xAxis);
            pads[port].dpadYAxis = (int) round(yAxis);
            break;
            
        case Input::MOTION_SOURCE_ANALOG_LEFT:
            pads[port].joypadLeftXAxis = xAxis;
            pads[port].joypadLeftYAxis = yAxis;
            break;
            
        case Input::MOTION_SOURCE_ANALOG_RIGHT:
            pads[port].joypadRightXAxis = xAxis;
            pads[port].joypadRightYAxis = yAxis;
            break;
            
        case Input::MOTION_SOURCE_POINTER:
            pads[port].pointerScreenXAxis = xAxis;
            pads[port].pointerScreenYAxis = yAxis;
            break;
    }
}
```

**Comparaison:**
- ✅ RetroPlay gère 4 sources motion (DPAD, ANALOG_LEFT, ANALOG_RIGHT, POINTER)
- ⚠️ RetroArch gère multi-touch natif (MAX_TOUCH = 64) via array de pointeurs
- ✅ RetroPlay simplifie (1 pointer max par port) - suffisant pour la plupart des cas

### 2.3 Gestion pointer/lightgun

**RetroArch Android:**
```c
// android_input.c:642-725
static INLINE void android_mouse_calculate_deltas(android_input_t *android,
      AInputEvent *event, size_t motion_ptr, int source)
{
    // Support souris relative (Oreo+)
    if ((source & AINPUT_SOURCE_MOUSE_RELATIVE) == AINPUT_SOURCE_MOUSE_RELATIVE) {
        x_delta = AMotionEvent_getX(event, motion_ptr);
        y_delta = AMotionEvent_getY(event, motion_ptr);
    } else {
        // Support AXIS_RELATIVE (Nougat+) ou fallback AXIS_X/Y
        if (p_AMotionEvent_getAxisValue) {
            x_delta = AMotionEvent_getAxisValue(event, AMOTION_EVENT_AXIS_RELATIVE_X, motion_ptr);
            y_delta = AMotionEvent_getAxisValue(event, AMOTION_EVENT_AXIS_RELATIVE_Y, motion_ptr);
        }
    }
    
    // Conversion viewport
    video_driver_translate_coord_viewport_confined_wrap(&vp, x, y, ...);
}
```

**RetroPlay:**
```kotlin
// RetroArchEmulatorActivity.kt:735-758
when (event.actionMasked) {
    ACTION_DOWN, ACTION_MOVE -> {
        // Conversion écran → viewport → normalisé
        val touchXInView = event.x - bounds.left
        val touchYInView = event.y - bounds.top
        val relativeX = touchXInView / bounds.width
        val relativeY = touchYInView / bounds.height
        
        // Envoyer position POINTER
        retroView.sendMotionEvent(MOTION_SOURCE_POINTER, relativeX, relativeY, lightgunPort)
        
        if (event.actionMasked == ACTION_DOWN && triggerOnTouch) {
            retroView.sendMouseButton(MOUSE_BUTTON_LEFT, true, lightgunPort)
        }
    }
}
```

**Comparaison:**
- ✅ RetroPlay gère pointer via `sendMotionEvent(MOTION_SOURCE_POINTER)`
- ⚠️ RetroArch gère souris/pointer via conversion viewport complexe (confined_wrap, wrap)
- ✅ RetroPlay simplifie (coordonnées normalisées directement) - fonctionnel pour Zapper

---

## Partie 3 : Comparaison Controller Info

### 3.1 RETRO_ENVIRONMENT_SET_CONTROLLER_INFO

**RetroArch Android:**
- Non géré explicitement (géré par le système d'autoconfig)
- Les contrôleurs sont détectés via `handle_hotplug()` et mappés via autoconfig

**RetroPlay:**
```cpp
// environment.cpp:123-150
bool Environment::environment_handle_set_controller_info(const struct retro_controller_info* received) {
    unsigned port = 0;
    while (received[port].types != nullptr) {
        controllers[port].clear();
        
        for (unsigned i = 0; received[port].types[i].desc != nullptr; i++) {
            struct Controller controller;
            controller.id = received[port].types[i].id;
            controller.description = std::string(received[port].types[i].desc);
            controllers[port].push_back(controller);
        }
        port++;
    }
    return true;
}
```

**Comparaison:**
- ✅ RetroPlay stocke les contrôleurs disponibles via `RETRO_ENVIRONMENT_SET_CONTROLLER_INFO`
- ⚠️ RetroArch ne gère pas cette callback (utilise autoconfig uniquement)
- ✅ RetroPlay permet de lister les contrôleurs disponibles (nécessaire pour `getControllers()`)

### 3.2 getControllers()

**RetroPlay:**
```cpp
// environment.cpp:448-450
const std::vector<std::vector<struct Controller>> &Environment::getControllers() const {
    return controllers;
}
```

**Utilisation dans RetroPlay:**
```kotlin
// RetroArchEmulatorActivity.kt:1356-1368
val controllers = retroView.getControllers()
val dualshock = controllers[0].firstOrNull {
    it.description?.contains("dualshock", ignoreCase = true) == true
}
if (dualshock != null) {
    retroView.setControllerType(0, dualshock.id)
}
```

**Comparaison:**
- ✅ RetroPlay permet de lister les contrôleurs disponibles par port
- ⚠️ RetroArch ne fournit pas cette API (configuration manuelle uniquement)
- ✅ RetroPlay améliore l'UX en permettant sélection automatique (ex: DualShock pour PSX)

---

## Partie 4 : Comparaison Autoconfig

### 4.1 Système d'autoconfig

**RetroArch Android:**
```c
// android_input.c:1041-1363
static void handle_hotplug(android_input_t *android,
      struct android_app *android_app, int *port, int id, int source)
{
    // Détection device
    engine_lookup_name(device_name, &vendorId, &productId, sizeof(device_name), id);
    
    // Hacks spéciaux (Shield, Xperia Play, etc.)
    if (strstr(device_model, "SHIELD Android TV") && ...) {
        // Hack NVIDIA Shield
    }
    
    // Autoconfig via input_autoconfigure_connect()
    input_autoconfigure_connect(name_buf, NULL, android_joypad.ident, *port, vendorId, productId);
}
```

**RetroPlay:**
- ❌ **Non implémenté** - Pas de système d'autoconfig
- ⚠️ Configuration manuelle uniquement via `setControllerType()`
- ⚠️ Pas de détection automatique des gamepads connectés

**Gap identifié:** RetroPlay n'a pas de système d'autoconfig RetroArch, nécessitant configuration manuelle pour chaque gamepad.

### 4.2 Hacks spéciaux devices

**RetroArch Android:**
- Support NVIDIA Shield (TV, Portable, Gamepad)
- Support Xperia Play
- Support GPD XD
- Support Archos Gamepad
- Support Amazon Fire TV
- Support iControlPad

**RetroPlay:**
- ❌ **Non implémenté** - Pas de hacks spéciaux
- ⚠️ Tous les devices traités de manière générique

**Gap identifié:** RetroPlay ne gère pas les devices spéciaux (Shield, Xperia Play, etc.) qui nécessitent des hacks.

---

## Partie 5 : Comparaison Overlays

### 5.1 Parser .cfg

**RetroArch:**
- Parser complet dans `task_overlay.c` (1000+ lignes)
- Support récursif #include (16 niveaux)
- Support tous les champs overlay (reach_*, movable, exclusive, etc.)

**RetroPlay:**
- Parser Kotlin dans `RetroArchOverlayParser.kt` (400+ lignes)
- Support récursif #include (16 niveaux) ✅
- Support tous les champs overlay ✅
- **Verdict:** ✅ **100% compatible** (voir AUDIT_OVERLAYS_COMPLET.md)

### 5.2 Rendu overlay

**RetroArch:**
- Rendu OpenGL via video driver
- Support textures RGBA
- Support transformations (scale, rotate, etc.)

**RetroPlay:**
- Rendu Compose (Canvas/Image)
- Support Bitmap RGBA
- Support transformations (scale, offset, separation)
- **Verdict:** ✅ **Fonctionnel** - Implémentation moderne avec Compose

---

## Partie 6 : Gaps identifiés

### 6.1 Gaps majeurs

1. **Système d'autoconfig non implémenté**
   - **Impact:** Configuration manuelle requise pour chaque gamepad
   - **Solution:** Intégrer le système d'autoconfig RetroArch (209 fichiers .cfg Android disponibles)

2. **Hacks spéciaux devices non implémentés**
   - **Impact:** Devices spéciaux (Shield, Xperia Play) ne fonctionnent pas correctement
   - **Solution:** Implémenter les hacks spéciaux RetroArch pour devices connus

3. **Support clavier complet non implémenté**
   - **Impact:** Claviers physiques non supportés (modifiers, keymaps)
   - **Solution:** Ajouter support clavier complet via `input_keymaps_translate_keysym_to_rk()`

### 6.2 Gaps mineurs

1. **Multi-touch natif limité**
   - **Impact:** Support 1 pointer max par port (suffisant pour la plupart des cas)
   - **Solution:** Améliorer support multi-touch si nécessaire (MAX_TOUCH = 64)

2. **Conversion viewport simplifiée**
   - **Impact:** Pas de support souris relative (Oreo+) ni AXIS_RELATIVE (Nougat+)
   - **Solution:** Ajouter support souris relative si nécessaire

---

## Partie 7 : Points forts RetroPlay

### 7.1 Architecture moderne

- ✅ API LibretroDroid propre (C++/JNI/Kotlin)
- ✅ Séparation claire des responsabilités (input/environment/overlay)
- ✅ Compose moderne pour overlays (vs OpenGL RetroArch)

### 7.2 Features uniques

- ✅ `getControllers()` - Liste contrôleurs disponibles par port
- ✅ Configuration automatique console (PSX DualShock, Zapper)
- ✅ Support Zapper/Lightgun complet avec options RetroArch

### 7.3 Compatibilité

- ✅ Parser overlay 100% compatible RetroArch
- ✅ Support tous les champs overlay (reach_*, movable, exclusive, etc.)
- ✅ Support #include récursif (16 niveaux)

---

## Conclusion

Cette analyse "Nos Rules" compare l'implémentation officielle RetroArch Android avec RetroPlay, identifiant les gaps majeurs et mineurs, ainsi que les points forts de RetroPlay.

**Principales découvertes:**
- ✅ Architecture RetroPlay moderne et propre
- ✅ Parser overlay 100% compatible RetroArch
- ✅ Support Zapper/Lightgun complet
- ⚠️ Système d'autoconfig non implémenté (gap majeur)
- ⚠️ Hacks spéciaux devices non implémentés (gap majeur)
- ⚠️ Support clavier complet non implémenté (gap mineur)

**Recommandations:**
1. Intégrer le système d'autoconfig RetroArch (priorité haute)
2. Implémenter les hacks spéciaux devices (priorité moyenne)
3. Ajouter support clavier complet si nécessaire (priorité basse)


