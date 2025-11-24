# Audit Nos Rules RetroPlay - Contrôleurs Physiques

**Date:** 2025-01-XX  
**Méthodologie:** Nos Rules - Analyse exhaustive des sources officielles RetroArch  
**Sources analysées:**
- `c:\repos\docs-master\docs\guides\controller-autoconfiguration.md` (912 lignes)
- `c:\repos\retroarch-joypad-autoconfig-master\android\` (209 fichiers .cfg)
- `c:\repos\RetroArch-master\input\drivers_joypad\android_joypad.c`
- `RetroPlay-Android/libretrodroid/src/main/cpp/input.cpp`
- `RetroPlay-Android/libretrodroid/src/main/cpp/libretrodroid.cpp`

---

## Partie 1 : Système d'autoconfiguration RetroArch

### 1.1 Architecture générale

Le système d'autoconfiguration de RetroArch permet de détecter automatiquement les contrôleurs physiques et d'appliquer une configuration de mapping RetroPad appropriée.

**Matching algorithm:** RetroArch compare les caractéristiques suivantes pour trouver le profil correspondant :
1. **Controller driver** (`input_driver`) : Interface logicielle (ex: `android`, `sdl2`, `udev`)
2. **Device Index** (`input_device`) : Nom du contrôleur reconnu par le système (ex: "Pro Controller", "Sony DualShock 4 Controller")
3. **Vendor ID** (`input_vendor_id`) : Identifiant unique du fabricant (ex: "1356" = Sony)
4. **Product ID** (`input_product_id`) : Identifiant spécifique du modèle (ex: "1476" = DualShock 4 v1)

**Score de matching:** RetroArch calcule un score de correspondance pour chaque profil et sélectionne celui avec le score le plus élevé.

### 1.2 Format autoconfig (.cfg) Android

#### 1.2.1 Métadonnées (identification)

```ini
input_driver = "android"
input_device = "Pro Controller"
input_device_display_name = "Nintendo Switch Pro Controller"
input_vendor_id = "1406"
input_product_id = "8201"
```

**Variables d'identification:**
- `input_driver` : Driver utilisé (OBLIGATOIRE) - `"android"` pour Android
- `input_device` : Device Index reconnu par le système (OBLIGATOIRE pour Android)
- `input_device_display_name` : Nom d'affichage lisible (OPTIONNEL)
- `input_vendor_id` : Vendor ID en décimal (OBLIGATOIRE pour Android)
- `input_product_id` : Product ID en décimal (OBLIGATOIRE pour Android)

**Variables alternatives (pour contrôleurs identiques avec différents VID/PID):**
- `input_vendor_id_alt1` à `input_vendor_id_alt9` : Autres Vendor IDs
- `input_product_id_alt1` à `input_product_id_alt9` : Autres Product IDs
- `input_device_alt1` à `input_device_alt9` : Autres Device Index

**Note importante:** Android utilise le Device Index comme nom Bluetooth. Les VID/PID sont utilisés comme identifiants primaires, avec Device Index en fallback.

#### 1.2.2 Mapping RetroPad (boutons)

```ini
# Boutons face (ABXY)
input_a_btn = "97"
input_b_btn = "96"
input_x_btn = "100"
input_y_btn = "99"

# Boutons système
input_select_btn = "109"
input_start_btn = "108"
input_menu_toggle_btn = "110"

# Boutons épaules
input_l_btn = "102"
input_r_btn = "103"
input_l2_btn = "104"
input_r2_btn = "105"
input_l3_btn = "106"
input_r3_btn = "107"

# D-Pad (Android utilise des boutons ou h0up/h0down/h0left/h0right)
input_up_btn = "19"      # Ou "h0up"
input_down_btn = "20"    # Ou "h0down"
input_left_btn = "21"    # Ou "h0left"
input_right_btn = "22"   # Ou "h0right"
```

**Valeurs possibles:**
- **Boutons** : "0" à "203" (IDs Android keycodes)
- **D-Pad spécial** : `"h0up"`, `"h0down"`, `"h0left"`, `"h0right"` (pour driver android/udev)

#### 1.2.3 Mapping axes analogiques

```ini
# Triggers analogiques (L2/R2)
input_l2_axis = "+8"     # Format: "+N" ou "-N" où N = numéro d'axe
input_r2_axis = "+9"

# Analog sticks
input_l_x_plus_axis = "+0"
input_l_x_minus_axis = "-0"
input_l_y_plus_axis = "+1"
input_l_y_minus_axis = "-1"
input_r_x_plus_axis = "+2"
input_r_x_minus_axis = "-2"
input_r_y_plus_axis = "+3"
input_r_y_minus_axis = "-3"
```

**Format axes:**
- **+N** : Axe N en direction positive
- **-N** : Axe N en direction négative
- **Valeurs** : "0" à "10" (IDs d'axes Android)

#### 1.2.4 Labels descriptifs (input descriptors)

```ini
input_a_btn_label = "A"
input_b_btn_label = "B"
input_x_btn_label = "X"
input_y_btn_label = "Y"
input_select_btn_label = "Minus"
input_start_btn_label = "Plus"
input_l_btn_label = "L"
input_r_btn_label = "R"
input_l2_axis_label = "ZL"
input_r2_axis_label = "ZR"
input_l3_btn_label = "Left Stick Press"
input_r3_btn_label = "Right Stick Press"
input_menu_toggle_btn_label = "Home"
input_up_btn_label = "D-Pad Up"
input_down_btn_label = "D-Pad Down"
input_left_btn_label = "D-Pad Left"
input_right_btn_label = "D-Pad Right"
input_l_x_plus_axis_label = "Left Analog X+ (Right)"
input_l_x_minus_axis_label = "Left Analog X- (Left)"
input_l_y_plus_axis_label = "Left Analog Y+ (Down)"
input_l_y_minus_axis_label = "Left Analog Y- (Up)"
input_r_x_plus_axis_label = "Right Analog X+ (Right)"
input_r_x_minus_axis_label = "Right Analog X- (Left)"
input_r_y_plus_axis_label = "Right Analog Y+ (Down)"
input_r_y_minus_axis_label = "Right Analog Y- (Up)"
```

**Convention de nommage:**
- Utiliser les noms officiels du fabricant
- Capitalisation standard (pas tout en majuscules sauf abréviations)
- Ne pas ajouter "button" si déjà présent dans le nom officiel

### 1.3 Exemples de fichiers autoconfig Android

#### 1.3.1 Nintendo Switch Pro Controller

```ini
input_driver = "android"
input_device = "Pro Controller"
input_device_display_name = "Nintendo Switch Pro Controller"
input_vendor_id = "1406"
input_product_id = "8201"

# Mapping
input_a_btn = "97"
input_b_btn = "96"
input_x_btn = "100"
input_y_btn = "99"
input_select_btn = "109"
input_start_btn = "108"
input_l_btn = "102"
input_r_btn = "103"
input_l2_btn = "104"
input_r2_btn = "105"
input_l3_btn = "106"
input_r3_btn = "107"
input_menu_toggle_btn = "110"
input_up_btn = "19"
input_down_btn = "20"
input_left_btn = "21"
input_right_btn = "22"
input_l_x_plus_axis = "+0"
input_l_x_minus_axis = "-0"
input_l_y_plus_axis = "+1"
input_l_y_minus_axis = "-1"
input_r_x_plus_axis = "+2"
input_r_x_minus_axis = "-2"
input_r_y_plus_axis = "+3"
input_r_y_minus_axis = "-3"

# Labels
input_a_btn_label = "A"
input_b_btn_label = "B"
input_x_btn_label = "X"
input_y_btn_label = "Y"
input_select_btn_label = "Minus"
input_start_btn_label = "Plus"
input_l_btn_label = "L"
input_r_btn_label = "R"
input_l2_btn_label = "ZL"
input_r2_btn_label = "ZR"
input_l3_btn_label = "Left Stick Press"
input_r3_btn_label = "Right Stick Press"
input_menu_toggle_btn_label = "Home"
input_up_btn_label = "D-Pad Up"
input_down_btn_label = "D-Pad Down"
input_left_btn_label = "D-Pad Left"
input_right_btn_label = "D-Pad Right"
input_l_x_plus_axis_label = "Left Analog X+ (Right)"
input_l_x_minus_axis_label = "Left Analog X- (Left)"
input_l_y_plus_axis_label = "Left Analog Y+ (Down)"
input_l_y_minus_axis_label = "Left Analog Y- (Up)"
input_r_x_plus_axis_label = "Right Analog X+ (Right)"
input_r_x_minus_axis_label = "Right Analog X- (Left)"
input_r_y_plus_axis_label = "Right Analog Y+ (Down)"
input_r_y_minus_axis_label = "Right Analog Y- (Up)"
```

#### 1.3.2 Sony DualShock 4 Controller

```ini
input_driver = "android"
input_device = "Sony Computer Entertainment Wireless Controller"
input_device_display_name = "Sony DualShock 4 Controller"
input_vendor_id = "1356"
input_product_id = "1476"

# Mapping
input_b_btn = "96"
input_y_btn = "99"
input_select_btn = "4"
input_start_btn = "108"
input_up_btn = "h0up"
input_down_btn = "h0down"
input_left_btn = "h0left"
input_right_btn = "h0right"
input_a_btn = "97"
input_x_btn = "100"
input_l_btn = "102"
input_r_btn = "103"
input_l2_btn = "104"
input_r2_btn = "105"
input_l3_btn = "106"
input_r3_btn = "107"
input_l_x_plus_axis = "+0"
input_l_x_minus_axis = "-0"
input_l_y_plus_axis = "+1"
input_l_y_minus_axis = "-1"
input_r_x_plus_axis = "+2"
input_r_x_minus_axis = "-2"
input_r_y_plus_axis = "+3"
input_r_y_minus_axis = "-3"
input_menu_toggle_btn = "109"

# Labels (PlayStation)
input_b_btn_label = "Cross"
input_y_btn_label = "Square"
input_select_btn_label = "Share"
input_start_btn_label = "Options"
input_up_btn_label = "D-Pad Up"
input_down_btn_label = "D-Pad Down"
input_left_btn_label = "D-Pad Left"
input_right_btn_label = "D-Pad Right"
input_a_btn_label = "Circle"
input_x_btn_label = "Triangle"
input_l_btn_label = "L1"
input_r_btn_label = "R1"
input_l2_btn_label = "L2"
input_r2_btn_label = "R2"
input_l3_btn_label = "L3"
input_r3_btn_label = "R3"
input_menu_toggle_btn_label = "Touchpad"
```

#### 1.3.3 Xbox One S Wireless Controller

```ini
input_driver = "android"
input_device = "Xbox Wireless Controller"
input_device_display_name = "Xbox One S Wireless Controller"
input_vendor_id = "1118"
input_product_id = "765"

# Mapping
input_b_btn = "96"
input_y_btn = "99"
input_select_btn = "109"
input_start_btn = "108"
input_up_btn = "h0up"
input_down_btn = "h0down"
input_left_btn = "h0left"
input_right_btn = "h0right"
input_a_btn = "97"
input_x_btn = "100"
input_l_btn = "102"
input_r_btn = "103"
input_l2_axis = "+8"     # Analog trigger
input_r2_axis = "+9"     # Analog trigger
input_l3_btn = "106"
input_r3_btn = "107"
input_l_x_plus_axis = "+0"
input_l_x_minus_axis = "-0"
input_l_y_plus_axis = "+1"
input_l_y_minus_axis = "-1"
input_r_x_plus_axis = "+2"
input_r_x_minus_axis = "-2"
input_r_y_plus_axis = "+3"
input_r_y_minus_axis = "-3"
input_menu_toggle_btn = "110"

# Labels (Xbox)
input_b_btn_label = "A"
input_y_btn_label = "X"
input_select_btn_label = "View"
input_start_btn_label = "Menu"
input_up_btn_label = "D-Pad Up"
input_down_btn_label = "D-Pad Down"
input_left_btn_label = "D-Pad Left"
input_right_btn_label = "D-Pad Right"
input_a_btn_label = "B"
input_x_btn_label = "Y"
input_l_btn_label = "Left Bumper"
input_r_btn_label = "Right Bumper"
input_l2_axis_label = "Left Trigger"
input_r2_axis_label = "Right Trigger"
input_l3_btn_label = "Left Thumb"
input_r3_btn_label = "Right Thumb"
input_menu_toggle_btn_label = "Guide"
```

### 1.4 Statistiques des fichiers autoconfig Android

**Total de fichiers analysés:** 209 fichiers .cfg dans `c:\repos\retroarch-joypad-autoconfig-master\android\`

**Fabricants principaux:**
- **8BitDo** : ~40+ variantes (SN30, Pro, Lite, Ultimate, etc.)
- **Sony** : DualShock 3, DualShock 4, DualSense (plusieurs variantes)
- **Microsoft** : Xbox 360, Xbox One, Xbox Series
- **Nintendo** : Switch Pro Controller
- **Autres** : Logitech, Razer, iPega, SteelSeries, etc.

**Fichiers spéciaux (default-off):**
- `DualSense Wireless Controller (Android 11) (default-off).cfg`
- `DualSense Wireless Controller (Google TV 12) (default-off).cfg`

Ces fichiers ont les variables `input_device`, `input_vendor_id`, `input_product_id` commentées pour désactiver l'auto-configuration par défaut.

---

## Partie 2 : API LibretroDroid pour contrôleurs

### 2.1 Structure Controller

```cpp
// RetroPlay-Android/libretrodroid/src/main/cpp/libretrodroid.h
struct Controller {
    unsigned int id;
    std::string description;
};
```

**Champs:**
- `id` : Type de device Libretro (ex: `RETRO_DEVICE_JOYPAD = 1`)
- `description` : Nom lisible du contrôleur (ex: "DualShock", "Analog Controller")

### 2.2 Fonction getControllers()

**Signature:**
```cpp
// C++ (libretrodroid.cpp)
std::vector<std::vector<struct Controller>> LibretroDroid::getControllers()

// JNI (libretrodroidjni.cpp)
JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getControllers(
    JNIEnv* env,
    jclass obj
)

// Java (LibretroDroid.java)
public static native Controller[][] getControllers()

// Kotlin (GLRetroView.kt)
fun getControllers(): Array<Array<Controller>>
```

**Retourne:** Tableau 2D de contrôleurs
- Dimension 1 : Ports (0-3, jusqu'à 4 joueurs)
- Dimension 2 : Types de contrôleurs disponibles pour ce port

**Implémentation:**
```cpp
// libretrodroid.cpp:139-141
std::vector<std::vector<struct Controller>> LibretroDroid::getControllers() {
    return Environment::getInstance().getControllers();
}
```

**Utilisation dans RetroPlay:**
```kotlin
// RetroArchEmulatorActivity.kt:1356-1357
val controllers = retroView.getControllers()
Log.i(TAG, "[PSX] Available controllers: ${controllers.getOrNull(0)?.map { "id=${it.id} desc='${it.description}'" }}")
```

**Source:** Les contrôleurs disponibles sont fournis par le core Libretro via `RETRO_ENVIRONMENT_SET_CONTROLLER_INFO` lors de `retro_load_game()`.

### 2.3 Fonction setControllerType()

**Signature:**
```cpp
// C++ (libretrodroid.cpp)
void LibretroDroid::setControllerType(unsigned int port, unsigned int type)

// JNI (libretrodroidjni.cpp)
JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setControllerType(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint type
)

// Java (LibretroDroid.java)
public static native void setControllerType(int port, int type)

// Kotlin (GLRetroView.kt)
fun setControllerType(port: Int, type: Int)
```

**Paramètres:**
- `port` : Index du port (0-3, 0 = Player 1)
- `type` : ID du type de device Libretro (voir section 2.4)

**Implémentation:**
```cpp
// libretrodroid.cpp:143-145
void LibretroDroid::setControllerType(unsigned int port, unsigned int type) {
    core->retro_set_controller_port_device(port, type);
}
```

**Appel Libretro:** Appelle directement `retro_set_controller_port_device()` du core chargé.

**Utilisation dans RetroPlay:**
```kotlin
// RetroArchEmulatorActivity.kt:1368
retroView.setControllerType(0, dualshock.id)
```

### 2.4 Types de devices Libretro

Définis dans `libretro.h`:

```cpp
#define RETRO_DEVICE_NONE         0
#define RETRO_DEVICE_JOYPAD       1   // Gamepad standard (16 boutons RetroPad)
#define RETRO_DEVICE_MOUSE        2   // Souris
#define RETRO_DEVICE_KEYBOARD     3   // Clavier
#define RETRO_DEVICE_LIGHTGUN     4   // Lightgun (Super Scope, Justifier, etc.)
#define RETRO_DEVICE_ANALOG       5   // Analog sticks (combiné avec JOYPAD)
#define RETRO_DEVICE_POINTER      6   // Pointer/Touchscreen (Zapper, etc.)

// Sous-classes de LIGHTGUN
#define RETRO_DEVICE_SUPER_SCOPE  RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_LIGHTGUN, 1)
#define RETRO_DEVICE_JUSTIFIER    RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_LIGHTGUN, 2)

// Sous-classes (cores spécifiques)
// Exemple PSX:
#define RETRO_DEVICE_PSX_JOYPAD   1
#define RETRO_DEVICE_PSX_DUALANALOG  2
#define RETRO_DEVICE_PSX_DUALSHOCK   3

// Exemple N64:
#define RETRO_DEVICE_N64_JOYPAD   1
// Extensions via retro_set_controller_port_device()

// Exemple Zapper NES:
#define RETRO_DEVICE_ZAPPER       258  // RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_POINTER, 2)
```

**Macro RETRO_DEVICE_SUBCLASS:**
```cpp
#define RETRO_DEVICE_TYPE_SHIFT   8
#define RETRO_DEVICE_MASK         ((1 << RETRO_DEVICE_TYPE_SHIFT) - 1)
#define RETRO_DEVICE_SUBCLASS(base, id) (((id + 1) << RETRO_DEVICE_TYPE_SHIFT) | base)
```

**Calcul des sous-classes:**
- `RETRO_DEVICE_ZAPPER = (2 + 1) << 8 | RETRO_DEVICE_POINTER = 768 | 6 = 774` (en décimal) ou `0x306` (hex)
- Mais FCEUmm utilise directement `258` (0x102) pour Zapper

### 2.5 Fonction sendKeyEvent()

**Signature:**
```kotlin
// GLRetroView.kt:129
fun sendKeyEvent(action: Int, keyCode: Int, port: Int = 0)
```

**Paramètres:**
- `action` : `KeyEvent.ACTION_DOWN` (0) ou `KeyEvent.ACTION_UP` (1)
- `keyCode` : Code Android (ex: `KeyEvent.KEYCODE_BUTTON_A = 96`)
- `port` : Port du contrôleur (0-3, défaut: 0)

**Implémentation native:**
```cpp
// input.cpp:222-233
void Input::onKeyEvent(unsigned int port, int action, int keyCode) {
    int retroKeyCode = convertAndroidToLibretroKey(keyCode);
    if (retroKeyCode == UNKNOWN_KEY) {
        return;
    }

    if (action == AKEY_EVENT_ACTION_DOWN) {
        pads[port].pressedKeys.insert(retroKeyCode);
    } else if (action == AKEY_EVENT_ACTION_UP) {
        pads[port].pressedKeys.erase(retroKeyCode);
    }
}
```

**Stockage:** Les touches pressées sont stockées dans `pads[port].pressedKeys` (std::set<int>).

### 2.6 Fonction sendMotionEvent()

**Signature:**
```kotlin
// GLRetroView.kt:134
fun sendMotionEvent(source: Int, xAxis: Float, yAxis: Float, port: Int = 0)
```

**Paramètres:**
- `source` : Source motion (voir section 2.7)
- `xAxis` : Valeur X normalisée [-1.0, 1.0] ou pixel pour POINTER
- `yAxis` : Valeur Y normalisée [-1.0, 1.0] ou pixel pour POINTER
- `port` : Port du contrôleur (0-3, défaut: 0)

**Implémentation native:**
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
            LOGD("[NATIVE MOTION] port=%d POINTER stored: X=%.3f Y=%.3f", port, xAxis, yAxis);
            pads[port].pointerScreenXAxis = xAxis;
            pads[port].pointerScreenYAxis = yAxis;
            break;
    }
}
```

**Stockage:** Les valeurs analogiques/pointer sont stockées dans `pads[port]` :
- `dpadXAxis`, `dpadYAxis` : -1, 0, ou 1
- `joypadLeftXAxis`, `joypadLeftYAxis` : [-1.0, 1.0]
- `joypadRightXAxis`, `joypadRightYAxis` : [-1.0, 1.0]
- `pointerScreenXAxis`, `pointerScreenYAxis` : [0.0, 1.0] (coordonnées normalisées)

### 2.7 Sources motion (MOTION_SOURCE_*)

Définies dans `GLRetroView.kt`:
```kotlin
companion object {
    const val MOTION_SOURCE_DPAD = 0
    const val MOTION_SOURCE_ANALOG_LEFT = 1
    const val MOTION_SOURCE_ANALOG_RIGHT = 2
    const val MOTION_SOURCE_POINTER = 3
}
```

**Utilisation:**
- `MOTION_SOURCE_DPAD` : D-Pad directionnel (valeurs -1, 0, 1)
- `MOTION_SOURCE_ANALOG_LEFT` : Analog stick gauche (valeurs [-1.0, 1.0])
- `MOTION_SOURCE_ANALOG_RIGHT` : Analog stick droit (valeurs [-1.0, 1.0])
- `MOTION_SOURCE_POINTER` : Pointer/Touchscreen (valeurs [0.0, 1.0] normalisées)

---

## Partie 3 : Mapping Android → RetroPad

### 3.1 Conversion keycodes Android → Libretro

Fonction `convertAndroidToLibretroKey()` dans `input.cpp:175-220`:

```cpp
int Input::convertAndroidToLibretroKey(int keyCode) const {
    switch (keyCode) {
        case AKEYCODE_BUTTON_START:   return RETRO_DEVICE_ID_JOYPAD_START;
        case AKEYCODE_BUTTON_SELECT:  return RETRO_DEVICE_ID_JOYPAD_SELECT;
        case AKEYCODE_BUTTON_A:       return RETRO_DEVICE_ID_JOYPAD_A;
        case AKEYCODE_BUTTON_X:       return RETRO_DEVICE_ID_JOYPAD_X;
        case AKEYCODE_BUTTON_Y:       return RETRO_DEVICE_ID_JOYPAD_Y;
        case AKEYCODE_BUTTON_B:       return RETRO_DEVICE_ID_JOYPAD_B;
        case AKEYCODE_BUTTON_L1:      return RETRO_DEVICE_ID_JOYPAD_L;
        case AKEYCODE_BUTTON_L2:      return RETRO_DEVICE_ID_JOYPAD_L2;
        case AKEYCODE_BUTTON_R1:      return RETRO_DEVICE_ID_JOYPAD_R;
        case AKEYCODE_BUTTON_R2:      return RETRO_DEVICE_ID_JOYPAD_R2;
        case AKEYCODE_BUTTON_THUMBL:  return RETRO_DEVICE_ID_JOYPAD_L3;
        case AKEYCODE_BUTTON_THUMBR:  return RETRO_DEVICE_ID_JOYPAD_R3;
        case AKEYCODE_DPAD_UP:        return RETRO_DEVICE_ID_JOYPAD_UP;
        case AKEYCODE_DPAD_DOWN:      return RETRO_DEVICE_ID_JOYPAD_DOWN;
        case AKEYCODE_DPAD_LEFT:      return RETRO_DEVICE_ID_JOYPAD_LEFT;
        case AKEYCODE_DPAD_RIGHT:     return RETRO_DEVICE_ID_JOYPAD_RIGHT;
        case AKEYCODE_DPAD_UP_RIGHT:  return Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT;
        case AKEYCODE_DPAD_UP_LEFT:   return Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT;
        case AKEYCODE_DPAD_DOWN_RIGHT:return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT;
        case AKEYCODE_DPAD_DOWN_LEFT: return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT;
        default:                       return UNKNOWN_KEY;
    }
}
```

### 3.2 Mapping complet keycodes Android

| Android KeyCode | Valeur | Libretro ID | Bouton RetroPad |
|----------------|--------|-------------|-----------------|
| `AKEYCODE_BUTTON_A` | 96 | `RETRO_DEVICE_ID_JOYPAD_A` (8) | A |
| `AKEYCODE_BUTTON_B` | 97 | `RETRO_DEVICE_ID_JOYPAD_B` (0) | B |
| `AKEYCODE_BUTTON_X` | 99 | `RETRO_DEVICE_ID_JOYPAD_X` (9) | X |
| `AKEYCODE_BUTTON_Y` | 100 | `RETRO_DEVICE_ID_JOYPAD_Y` (1) | Y |
| `AKEYCODE_BUTTON_SELECT` | 109 | `RETRO_DEVICE_ID_JOYPAD_SELECT` (2) | Select |
| `AKEYCODE_BUTTON_START` | 108 | `RETRO_DEVICE_ID_JOYPAD_START` (3) | Start |
| `AKEYCODE_BUTTON_L1` | 102 | `RETRO_DEVICE_ID_JOYPAD_L` (10) | L1 |
| `AKEYCODE_BUTTON_R1` | 103 | `RETRO_DEVICE_ID_JOYPAD_R` (11) | R1 |
| `AKEYCODE_BUTTON_L2` | 104 | `RETRO_DEVICE_ID_JOYPAD_L2` (12) | L2 |
| `AKEYCODE_BUTTON_R2` | 105 | `RETRO_DEVICE_ID_JOYPAD_R2` (13) | R2 |
| `AKEYCODE_BUTTON_THUMBL` | 106 | `RETRO_DEVICE_ID_JOYPAD_L3` (14) | L3 |
| `AKEYCODE_BUTTON_THUMBR` | 107 | `RETRO_DEVICE_ID_JOYPAD_R3` (15) | R3 |
| `AKEYCODE_DPAD_UP` | 19 | `RETRO_DEVICE_ID_JOYPAD_UP` (4) | D-Pad Up |
| `AKEYCODE_DPAD_DOWN` | 20 | `RETRO_DEVICE_ID_JOYPAD_DOWN` (5) | D-Pad Down |
| `AKEYCODE_DPAD_LEFT` | 21 | `RETRO_DEVICE_ID_JOYPAD_LEFT` (6) | D-Pad Left |
| `AKEYCODE_DPAD_RIGHT` | 22 | `RETRO_DEVICE_ID_JOYPAD_RIGHT` (7) | D-Pad Right |
| `AKEYCODE_DPAD_UP_LEFT` | - | `RETRO_DEVICE_ID_JOYPAD_UP_LEFT` | D-Pad Up+Left |
| `AKEYCODE_DPAD_UP_RIGHT` | - | `RETRO_DEVICE_ID_JOYPAD_UP_RIGHT` | D-Pad Up+Right |
| `AKEYCODE_DPAD_DOWN_LEFT` | - | `RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT` | D-Pad Down+Left |
| `AKEYCODE_DPAD_DOWN_RIGHT` | - | `RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT` | D-Pad Down+Right |

**Note:** Les diagonales D-Pad sont des valeurs spéciales non-standard Android, probablement gérées par LibretroDroid.

### 3.3 Stockage des états dans Input::PadState

Structure `PadState` dans `input.h` (implémentée dans `input.cpp`):

```cpp
struct PadState {
    std::set<int> pressedKeys;          // Boutons pressés (IDs Libretro)
    int dpadXAxis;                      // D-Pad X: -1 (left), 0 (none), 1 (right)
    int dpadYAxis;                      // D-Pad Y: -1 (up), 0 (none), 1 (down)
    float joypadLeftXAxis;              // Analog left X: [-1.0, 1.0]
    float joypadLeftYAxis;              // Analog left Y: [-1.0, 1.0]
    float joypadRightXAxis;             // Analog right X: [-1.0, 1.0]
    float joypadRightYAxis;             // Analog right Y: [-1.0, 1.0]
    float pointerScreenXAxis;           // Pointer X: [0.0, 1.0] normalisé
    float pointerScreenYAxis;           // Pointer Y: [0.0, 1.0] normalisé
    bool mouseButtonLeft;               // Mouse left button
    bool mouseButtonRight;              // Mouse right button
    bool mouseButtonMiddle;             // Mouse middle button
};
```

**Array de ports:** `PadState pads[4]` - Un état par port (0-3).

---

## Partie 4 : Support par console

### 4.1 PlayStation (PSX) - DualShock

**Code source:** `RetroArchEmulatorActivity.kt:1352-1378`

**Objectif:** Configurer automatiquement le DualShock pour activer les analog sticks.

**Implémentation:**
```kotlin
if (console.equals("psx", ignoreCase = true)) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            val controllers = retroView.getControllers()
            Log.i(TAG, "[PSX] Available controllers: ${controllers.getOrNull(0)?.map { "id=${it.id} desc='${it.description}'" }}")
            
            if (controllers.isNotEmpty() && controllers[0].isNotEmpty()) {
                // Chercher le contrôleur DualShock (essayer "dualshock" en priorité)
                val dualshock = controllers[0].firstOrNull { 
                    it.description?.contains("dualshock", ignoreCase = true) == true
                } ?: controllers[0].firstOrNull {
                    it.description?.contains("analog", ignoreCase = true) == true
                }
                
                if (dualshock != null) {
                    retroView.setControllerType(0, dualshock.id)
                    Log.i(TAG, "[PSX] Controller type set to DualShock (id=${dualshock.id}, desc='${dualshock.description}')")
                } else {
                    Log.w(TAG, "[PSX] DualShock controller not found. Using default.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PSX] Error configuring controller type", e)
        }
    }, 1000)  // Attendre 1 seconde pour que le core soit complètement initialisé
}
```

**Types de contrôleurs PSX disponibles:**
- `PS1` (id: 1) : Gamepad standard sans analog
- `Dual Analog` (id: 2) : Dual Analog Controller avec analog sticks
- `DualShock` (id: 3) : DualShock avec analog sticks + rumble

**Délai de configuration:** 1 seconde après chargement du core pour s'assurer que `getControllers()` retourne les valeurs correctes.

### 4.2 Nintendo 64 (N64) - Extensions

**Code source:** `RetroArchEmulatorActivity.kt:1406-1442`

**Objectif:** Configurer les extensions contrôleur (Controller Pak, Rumble Pak, Transfer Pak).

**Implémentation actuelle:**
```kotlin
if (console.equals("n64", ignoreCase = true)) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            Log.i(TAG, "[N64] Configuring controller extensions...")

            val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@RetroArchEmulatorActivity)
            val prefix = "n64_"

            // Mapping des positions spinner vers les valeurs Libretro :
            // 0 = Controller Pak (1), 1 = Rumble Pak (2), 2 = Transfer Pak (5)
            val pakValues = intArrayOf(1, 2, 5)

            // TODO: Configurer les extensions contrôleur N64
            // Les valeurs sont sauvegardées dans les préférences mais l'application dans LibretroDroid
            // nécessite une investigation supplémentaire pour la méthode correcte
            for (port in 0..3) {  // 4 ports maximum pour N64
                try {
                    val pakPosition = prefs.getInt(prefix + "pak_port" + (port + 1), 0) // Default: Controller Pak
                    val pakValue = pakValues.getOrElse(pakPosition) { 1 } // Fallback to Controller Pak

                    val pakName = when (pakPosition) {
                        0 -> "Controller Pak"
                        1 -> "Rumble Pak"
                        2 -> "Transfer Pak"
                        else -> "Unknown"
                    }
                    Log.i(TAG, "[N64] Extension configured for port ${port + 1}: $pakName (value=$pakValue)")
                } catch (e: Exception) {
                    Log.w(TAG, "[N64] Could not configure extension for port ${port + 1}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[N64] Error configuring controller extensions", e)
        }
    }, 1000)
}
```

**Gap identifié:** La configuration des extensions N64 n'est pas implémentée. Les valeurs sont sauvegardées dans les préférences mais aucune méthode n'est appelée pour configurer le core.

**Types d'extensions N64:**
- `Controller Pak` (value: 1) : Mémoire de sauvegarde
- `Rumble Pak` (value: 2) : Vibration
- `Transfer Pak` (value: 5) : Transfert Game Boy

**Méthode probable (à investiguer):** Utiliser `retro_set_controller_port_device()` avec les IDs d'extensions ou des core options spécifiques N64.

### 4.3 NES - Zapper/Lightgun

**Code source:** `RetroArchEmulatorActivity.kt:1380-1404` et multiples fichiers `ZAPPER_*.md`

**Objectif:** Configurer le Zapper pour les jeux comme Duck Hunt.

**Implémentation actuelle:**
```kotlin
if (isZapperGame) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            // Port 1 = Gamepad (Start/Select), Port 2 = Zapper (Touch to shoot)
            retroView.setControllerType(0, 1)  // RETRO_DEVICE_JOYPAD = 1
            retroView.setControllerType(1, 258)  // RETRO_DEVICE_ZAPPER = 258
            
            Log.i(TAG, "[ZAPPER] Port 1 = JOYPAD, Port 2 = ZAPPER (id=258)")
        } catch (e: Exception) {
            Log.e(TAG, "[ZAPPER] Error configuring controller type", e)
        }
    }, 1000)
}
```

**Types de devices:**
- Port 0: `RETRO_DEVICE_JOYPAD` (1) pour Start/Select
- Port 1: `RETRO_DEVICE_ZAPPER` (258) pour pointer/shoot

**Note:** `258` est l'ID spécifique FCEUmm pour Zapper. D'autres cores utilisent `RETRO_DEVICE_POINTER` (6) ou `RETRO_DEVICE_LIGHTGUN` (4).

**Configuration FCEUmm:**
- Option core: `fceumm_zapper_mode = "touchscreen"` (pour Android, pas "lightgun")
- Port 2 configuré comme Zapper via `setControllerType(1, 258)`

**Envoi position:**
```kotlin
// Via sendMotionEvent(MOTION_SOURCE_POINTER, x, y, port=1)
retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_POINTER, normalizedX, normalizedY, 1)
```

**Lecture dans le core:**
Le core FCEUmm appelle `retro_input_state(port, device=258, index, id)` avec:
- `id = RETRO_DEVICE_ID_POINTER_X` : Position X
- `id = RETRO_DEVICE_ID_POINTER_Y` : Position Y
- `id = RETRO_DEVICE_ID_POINTER_PRESSED` : Trigger (pressed)

**Conversion dans input.cpp:**
```cpp
case RETRO_DEVICE_POINTER: {
    switch (id) {
        case RETRO_DEVICE_ID_POINTER_PRESSED:
            return (int16_t) (isXActive && isYActive ? 1 : 0);
        case RETRO_DEVICE_ID_POINTER_X:
            return (int16_t) (2.0 * (pads[port].pointerScreenXAxis - 0.5f) * MAX_RANGE_MOTION);
        case RETRO_DEVICE_ID_POINTER_Y:
            return (int16_t) (2.0 * (pads[port].pointerScreenYAxis - 0.5f) * MAX_RANGE_MOTION);
    }
}
```

---

## Partie 5 : Gaps identifiés & Améliorations possibles

### 5.1 Gaps fonctionnels

1. **N64 Extensions non implémentées**
   - **Problème:** Les extensions (Controller Pak, Rumble Pak, Transfer Pak) sont loggées mais jamais configurées dans le core
   - **Impact:** Les jeux N64 nécessitant des extensions ne fonctionnent pas correctement
   - **Solution requise:** Investiguer l'API Libretro pour configurer les extensions N64 (core options ou `retro_set_controller_port_device`)

2. **Autoconfig Android non intégré**
   - **Problème:** RetroPlay n'utilise pas le système d'autoconfig RetroArch (209 fichiers .cfg disponibles dans repos)
   - **Impact:** Les utilisateurs doivent mapper manuellement leurs gamepads physiques
   - **Solution requise:** Intégrer le système d'autoconfig RetroArch dans RetroPlay

3. **Hotkeys gamepad non configurés**
   - **Problème:** Les hotkeys RetroArch (save state, load state, fast forward, etc.) ne sont pas mappables depuis un gamepad physique
   - **Impact:** Les utilisateurs doivent utiliser les overlays touch pour les hotkeys
   - **Solution requise:** Ajouter support hotkeys dans RetroPlay avec mapping gamepad

### 5.2 Améliorations potentielles

1. **Détection automatique gamepad**
   - Détecter automatiquement les gamepads connectés via `InputDevice.getDeviceIds()`
   - Appliquer l'autoconfig correspondant si disponible
   - Fallback vers mapping par défaut si autoconfig non trouvé

2. **UI configuration gamepad par console**
   - Interface pour sélectionner le type de contrôleur par console
   - Sauvegarde des préférences par console
   - Application automatique au chargement du jeu

3. **Support complet des types de devices**
   - Documentation complète de tous les types disponibles par core
   - Interface pour changer le type de device en temps réel
   - Support des sous-classes (ex: Super Scope, Justifier)

---

## Conclusion

Ce document d'audit "Nos Rules" fournit une documentation exhaustive du système de contrôleurs physiques dans RetroPlay, basée sur l'analyse approfondie des sources officielles RetroArch et de l'implémentation actuelle de RetroPlay.

**Principales découvertes:**
- ✅ API LibretroDroid complètement documentée
- ✅ Mapping Android → RetroPad exhaustif
- ✅ Support PSX DualShock fonctionnel
- ⚠️ N64 Extensions non implémentées (gap identifié)
- ⚠️ Autoconfig Android non intégré (amélioration possible)
- ✅ Zapper/Lightgun documenté avec tous les détails

**Prochaines étapes:**
1. Investiguer l'implémentation des extensions N64
2. Intégrer le système d'autoconfig RetroArch
3. Ajouter support hotkeys gamepad
4. Créer UI de configuration gamepad par console


