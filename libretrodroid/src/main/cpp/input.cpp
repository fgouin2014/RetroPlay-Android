/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "input.h"
#include "log.h"

#include <cmath>

#include <android/input.h>
#include <android/keycodes.h>

#include "../../libretro-common/include/libretro.h"

namespace libretrodroid {

int16_t Input::getInputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4 || port < 0) return 0;
    
    // DEBUG: Log POINTER/MOUSE requests only (device 6 or 2)
    if (device == 6 || device == 2) {
        LOGI("[NATIVE INPUT] port=%u device=%u index=%u id=%u", port, device, index, id);
    }

    switch (device) {
        case RETRO_DEVICE_JOYPAD: {
            switch (id) {
                case RETRO_DEVICE_ID_JOYPAD_LEFT: {
                    bool axis = pads[port].dpadXAxis == -1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT
                    );
                    return axis || buttons;
                }
                case RETRO_DEVICE_ID_JOYPAD_RIGHT: {
                    bool axis = pads[port].dpadXAxis == 1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_RIGHT,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT
                    );
                    return axis || buttons;
                }
                case RETRO_DEVICE_ID_JOYPAD_UP: {
                    bool axis = pads[port].dpadYAxis == -1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_UP,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT
                    );
                    return axis || buttons;
                }
                case RETRO_DEVICE_ID_JOYPAD_DOWN: {
                    bool axis = pads[port].dpadYAxis == 1;
                    bool buttons = anyPressed(
                        port,
                        RETRO_DEVICE_ID_JOYPAD_DOWN,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT,
                        Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT
                    );
                    return axis || buttons;
                }
                default:
                    return anyPressed(port, id);
            }
        }

        case RETRO_DEVICE_ANALOG: {
            switch (index) {
                case RETRO_DEVICE_INDEX_ANALOG_LEFT:
                    switch (id) {
                        case RETRO_DEVICE_ID_ANALOG_X:
                            return (int16_t) (pads[port].joypadLeftXAxis * MAX_RANGE_MOTION);
                        case RETRO_DEVICE_ID_ANALOG_Y:
                            return (int16_t) (pads[port].joypadLeftYAxis * MAX_RANGE_MOTION);
                        default:
                            return 0;
                    }
                case RETRO_DEVICE_INDEX_ANALOG_RIGHT:
                    switch (id) {
                        case RETRO_DEVICE_ID_ANALOG_X:
                            return (int16_t) (pads[port].joypadRightXAxis * MAX_RANGE_MOTION);
                        case RETRO_DEVICE_ID_ANALOG_Y:
                            return (int16_t) (pads[port].joypadRightYAxis * MAX_RANGE_MOTION);
                        default:
                            return 0;
                    }
                case RETRO_DEVICE_INDEX_ANALOG_BUTTON:
                    // P2: Triggers séparés (L2/R2 axes) - Compatible RetroArch
                    // Analog buttons are reported in range [0, 0x7fff] where 0 = unpressed, 0x7fff = fully pressed
                    // id is the RetroPad button ID (RETRO_DEVICE_ID_JOYPAD_L2 or RETRO_DEVICE_ID_JOYPAD_R2)
                    switch (id) {
                        case RETRO_DEVICE_ID_JOYPAD_L2:
                            // Return L2 trigger value in range [0, 0x7fff]
                            return (int16_t) (pads[port].triggerL2 * 0x7fff);
                        case RETRO_DEVICE_ID_JOYPAD_R2:
                            // Return R2 trigger value in range [0, 0x7fff]
                            return (int16_t) (pads[port].triggerR2 * 0x7fff);
                        default:
                            return 0;
                    }
                default:
                    return 0;
            }
        }

        case RETRO_DEVICE_POINTER: {
            // Multi-touch support: index (0-15) corresponds to pointer number
            if (index >= GamePadState::MAX_POINTERS) {
                return 0;
            }
            
            const PointerState& pointer = pads[port].pointers[index];

            switch (id) {
                case RETRO_DEVICE_ID_POINTER_PRESSED: {
                    bool isPressed = pointer.active && pointer.screenX >= 0.0f && pointer.screenY >= 0.0f;
                    int16_t result = (int16_t) (isPressed ? 1 : 0);
                    LOGI("[NATIVE POINTER] port=%d index=%d PRESSED=%d (X=%.3f Y=%.3f)", port, index, result, pointer.screenX, pointer.screenY);
                    return result;
                }

                case RETRO_DEVICE_ID_POINTER_X: {
                    if (!pointer.active || pointer.screenX < 0.0f) {
                        return 0;
                    }
                    // Use viewport-converted coordinates (always calculated in onMotionEventMulti)
                    int16_t result = pointer.x;
                    LOGI("[NATIVE POINTER] port=%d index=%d X=%d (raw=%.3f, viewport=%d)", port, index, result, pointer.screenX, pointer.x);
                    return result;
                }

                case RETRO_DEVICE_ID_POINTER_Y: {
                    if (!pointer.active || pointer.screenY < 0.0f) {
                        return 0;
                    }
                    // Use viewport-converted coordinates (always calculated in onMotionEventMulti)
                    int16_t result = pointer.y;
                    LOGI("[NATIVE POINTER] port=%d index=%d Y=%d (raw=%.3f, viewport=%d)", port, index, result, pointer.screenY, pointer.y);
                    return result;
                }
                
                case RETRO_DEVICE_ID_POINTER_COUNT: {
                    // Return number of active pointers (RetroArch compatible)
                    int16_t result = (int16_t) pads[port].pointerCount;
                    LOGI("[NATIVE POINTER] port=%d COUNT=%d", port, result);
                    return result;
                }
                
                case RETRO_DEVICE_ID_POINTER_IS_OFFSCREEN: {
                    // Return 1 if pointer is off-screen or near edge (RetroArch compatible)
                    // RetroArch: Returns 1 if pointer is outside screen or near edge
                    // We check if coordinates are outside viewport bounds or negative
                    bool isOffscreen = !pointer.active || 
                                       pointer.screenX < 0.0f || pointer.screenY < 0.0f ||
                                       pointer.screenX > 1.0f || pointer.screenY > 1.0f ||
                                       pointer.x < -0x7fff || pointer.y < -0x7fff ||
                                       pointer.x > 0x7fff || pointer.y > 0x7fff;
                    int16_t result = (int16_t) (isOffscreen ? 1 : 0);
                    LOGI("[NATIVE POINTER] port=%d index=%d IS_OFFSCREEN=%d (screenX=%.3f screenY=%.3f)", 
                         port, index, result, pointer.screenX, pointer.screenY);
                    return result;
                }

                default:
                    return 0;
            }
        }
        
        case RETRO_DEVICE_MOUSE: {
            switch (id) {
                case RETRO_DEVICE_ID_MOUSE_LEFT:
                    LOGD("[NATIVE MOUSE] port=%d GET LEFT=%d", port, pads[port].mouseButtonLeft ? 1 : 0);
                    return pads[port].mouseButtonLeft ? 1 : 0;
                
                case RETRO_DEVICE_ID_MOUSE_RIGHT:
                    LOGD("[NATIVE MOUSE] port=%d GET RIGHT=%d", port, pads[port].mouseButtonRight ? 1 : 0);
                    return pads[port].mouseButtonRight ? 1 : 0;
                
                case RETRO_DEVICE_ID_MOUSE_MIDDLE:
                    LOGD("[NATIVE MOUSE] port=%d GET MIDDLE=%d", port, pads[port].mouseButtonMiddle ? 1 : 0);
                    return pads[port].mouseButtonMiddle ? 1 : 0;
                
                case RETRO_DEVICE_ID_MOUSE_BUTTON_4:
                    // P3: Gestion boutons souris multiples - Compatible RetroArch
                    LOGD("[NATIVE MOUSE] port=%d GET BUTTON_4=%d", port, pads[port].mouseButton4 ? 1 : 0);
                    return pads[port].mouseButton4 ? 1 : 0;
                
                case RETRO_DEVICE_ID_MOUSE_BUTTON_5:
                    // P3: Gestion boutons souris multiples - Compatible RetroArch
                    LOGD("[NATIVE MOUSE] port=%d GET BUTTON_5=%d", port, pads[port].mouseButton5 ? 1 : 0);
                    return pads[port].mouseButton5 ? 1 : 0;
                
                case RETRO_DEVICE_ID_MOUSE_X:
                    // Use POINTER coordinates for mouse X
                    return (int16_t) (2.0 * (pads[port].pointerScreenXAxis - 0.5f) * MAX_RANGE_MOTION);
                
                case RETRO_DEVICE_ID_MOUSE_Y:
                    // Use POINTER coordinates for mouse Y
                    return (int16_t) (2.0 * (pads[port].pointerScreenYAxis - 0.5f) * MAX_RANGE_MOTION);
                
                default:
                    return 0;
            }
        }

        // P1 #9: Keyboard Support - compatible RetroArch RETRO_DEVICE_KEYBOARD
        case RETRO_DEVICE_KEYBOARD: {
            // id is the RetroK keycode (RETROK_*)
            // Return 1 if key is pressed, 0 otherwise
            // Compatible with RetroArch input_driver.c:input_state_keyboard()
            unsigned retroK = id;
            bool isPressed = pads[port].pressedKeyboardKeys.find(retroK) != pads[port].pressedKeyboardKeys.end();
            return isPressed ? 1 : 0;
        }
        
        // P1 #8: Sensors Support - compatible RetroArch android_input_get_sensor_input()
        // Note: In RetroArch, sensors are exposed via get_sensor_input() callback (float return)
        // In LibretroDroid, we use RETRO_DEVICE_ANALOG with special IDs for sensors
        // Sensors are global (port 0 only) in RetroArch
        // We use device value 0x100 (256) to indicate sensor device (not standard RETRO_DEVICE_*)
        // This is a workaround since LibretroDroid doesn't expose get_sensor_input() callback
        default: {
            // Check if this is a sensor request (device >= 0x100 indicates sensor)
            // This is a custom extension for LibretroDroid compatibility
            if (device >= 0x100 && port == 0) {
                unsigned sensorDevice = device - 0x100;
                const SensorState* sensorState = nullptr;
                bool sensorEnabled = false;
                
                // sensorDevice: 0 = accelerometer, 1 = gyroscope
                if (sensorDevice == 0) {
                    sensorState = &accelerometerState;
                    sensorEnabled = accelerometerEnabled;
                } else if (sensorDevice == 1) {
                    sensorState = &gyroscopeState;
                    sensorEnabled = gyroscopeEnabled;
                }
                
                if (!sensorEnabled || !sensorState) {
                    return 0;
                }
                
                // Return sensor value based on id (X, Y, or Z)
                // Compatible with RetroArch android_input_get_sensor_input() (lignes 2026-2051)
                // id: 0=X, 1=Y, 2=Z (matching RETRO_SENSOR_ACCELEROMETER_X/Y/Z)
                switch (id) {
                    case 0: // X
                        return (int16_t)(sensorState->x * MAX_RANGE_MOTION);
                    case 1: // Y
                        return (int16_t)(sensorState->y * MAX_RANGE_MOTION);
                    case 2: // Z
                        return (int16_t)(sensorState->z * MAX_RANGE_MOTION);
        default:
            return 0;
                }
            }
            
            return 0;
        }
    }
}

int Input::convertAndroidToLibretroKey(int keyCode) const {
    // Convert Android gamepad buttons → RetroPad IDs (RETRO_DEVICE_ID_JOYPAD_*)
    switch (keyCode) {
        case AKEYCODE_BUTTON_START:
            return RETRO_DEVICE_ID_JOYPAD_START;
        case AKEYCODE_BUTTON_SELECT:
            return RETRO_DEVICE_ID_JOYPAD_SELECT;
        case AKEYCODE_BUTTON_A:
            return RETRO_DEVICE_ID_JOYPAD_A;
        case AKEYCODE_BUTTON_X:
            return RETRO_DEVICE_ID_JOYPAD_X;
        case AKEYCODE_BUTTON_Y:
            return RETRO_DEVICE_ID_JOYPAD_Y;
        case AKEYCODE_BUTTON_B:
            return RETRO_DEVICE_ID_JOYPAD_B;
        case AKEYCODE_BUTTON_L1:
            return RETRO_DEVICE_ID_JOYPAD_L;
        case AKEYCODE_BUTTON_L2:
            return RETRO_DEVICE_ID_JOYPAD_L2;
        case AKEYCODE_BUTTON_R1:
            return RETRO_DEVICE_ID_JOYPAD_R;
        case AKEYCODE_BUTTON_R2:
            return RETRO_DEVICE_ID_JOYPAD_R2;
        case AKEYCODE_BUTTON_THUMBL:
            return RETRO_DEVICE_ID_JOYPAD_L3;
        case AKEYCODE_BUTTON_THUMBR:
            return RETRO_DEVICE_ID_JOYPAD_R3;
        case AKEYCODE_DPAD_UP:
            return RETRO_DEVICE_ID_JOYPAD_UP;
        case AKEYCODE_DPAD_DOWN:
            return RETRO_DEVICE_ID_JOYPAD_DOWN;
        case AKEYCODE_DPAD_LEFT:
            return RETRO_DEVICE_ID_JOYPAD_LEFT;
        case AKEYCODE_DPAD_RIGHT:
            return RETRO_DEVICE_ID_JOYPAD_RIGHT;
        case AKEYCODE_DPAD_UP_RIGHT:
            return Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT;
        case AKEYCODE_DPAD_UP_LEFT:
            return Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT;
        case AKEYCODE_DPAD_DOWN_RIGHT:
            return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT;
        case AKEYCODE_DPAD_DOWN_LEFT:
            return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT;
        default:
            return UNKNOWN_KEY;
    }
}

// P1 #9: Keyboard Support - convert Android keycode → RetroK
// Compatible with RetroArch input_keymaps.c:rarch_key_map_android[] (lignes 1395-1502)
// Maps Android AKEYCODE_* to RetroArch RETROK_* (for RETRO_DEVICE_KEYBOARD)
unsigned Input::convertAndroidToRetroK(int keyCode) const {
    // Mapping complet Android → RetroK (compatible RetroArch)
    // Source: RetroArch input_keymaps.c:rarch_key_map_android[] lignes 1395-1502
    switch (keyCode) {
        // Special keys
        case AKEYCODE_BACK: return RETROK_BACKSPACE;
        case AKEYCODE_TAB: return RETROK_TAB;
        case AKEYCODE_CLEAR: return RETROK_CLEAR;
        case AKEYCODE_ENTER: return RETROK_RETURN;
        case AKEYCODE_DPAD_CENTER: return RETROK_RETURN;
        case AKEYCODE_BREAK: return RETROK_PAUSE;
        case AKEYCODE_ESCAPE: return RETROK_ESCAPE;
        case AKEYCODE_SPACE: return RETROK_SPACE;
        case AKEYCODE_DEL: return RETROK_DELETE;
        case AKEYCODE_FORWARD_DEL: return RETROK_DELETE;
        
        // Symbols
        case AKEYCODE_APOSTROPHE: return RETROK_QUOTE;
        case AKEYCODE_COMMA: return RETROK_COMMA;
        case AKEYCODE_MINUS: return RETROK_MINUS;
        case AKEYCODE_PERIOD: return RETROK_PERIOD;
        case AKEYCODE_SLASH: return RETROK_SLASH;
        case AKEYCODE_SEMICOLON: return RETROK_SEMICOLON;
        case AKEYCODE_EQUALS: return RETROK_EQUALS;
        case AKEYCODE_LEFT_BRACKET: return RETROK_LEFTBRACKET;
        case AKEYCODE_BACKSLASH: return RETROK_BACKSLASH;
        case AKEYCODE_RIGHT_BRACKET: return RETROK_RIGHTBRACKET;
        case AKEYCODE_GRAVE: return RETROK_BACKQUOTE;
        
        // Numbers
        case AKEYCODE_0: return RETROK_0;
        case AKEYCODE_1: return RETROK_1;
        case AKEYCODE_2: return RETROK_2;
        case AKEYCODE_3: return RETROK_3;
        case AKEYCODE_4: return RETROK_4;
        case AKEYCODE_5: return RETROK_5;
        case AKEYCODE_6: return RETROK_6;
        case AKEYCODE_7: return RETROK_7;
        case AKEYCODE_8: return RETROK_8;
        case AKEYCODE_9: return RETROK_9;
        
        // Letters
        case AKEYCODE_A: return RETROK_a;
        case AKEYCODE_B: return RETROK_b;
        case AKEYCODE_C: return RETROK_c;
        case AKEYCODE_D: return RETROK_d;
        case AKEYCODE_E: return RETROK_e;
        case AKEYCODE_F: return RETROK_f;
        case AKEYCODE_G: return RETROK_g;
        case AKEYCODE_H: return RETROK_h;
        case AKEYCODE_I: return RETROK_i;
        case AKEYCODE_J: return RETROK_j;
        case AKEYCODE_K: return RETROK_k;
        case AKEYCODE_L: return RETROK_l;
        case AKEYCODE_M: return RETROK_m;
        case AKEYCODE_N: return RETROK_n;
        case AKEYCODE_O: return RETROK_o;
        case AKEYCODE_P: return RETROK_p;
        case AKEYCODE_Q: return RETROK_q;
        case AKEYCODE_R: return RETROK_r;
        case AKEYCODE_S: return RETROK_s;
        case AKEYCODE_T: return RETROK_t;
        case AKEYCODE_U: return RETROK_u;
        case AKEYCODE_V: return RETROK_v;
        case AKEYCODE_W: return RETROK_w;
        case AKEYCODE_X: return RETROK_x;
        case AKEYCODE_Y: return RETROK_y;
        case AKEYCODE_Z: return RETROK_z;
        
        // Keypad
        case AKEYCODE_NUMPAD_0: return RETROK_KP0;
        case AKEYCODE_NUMPAD_1: return RETROK_KP1;
        case AKEYCODE_NUMPAD_2: return RETROK_KP2;
        case AKEYCODE_NUMPAD_3: return RETROK_KP3;
        case AKEYCODE_NUMPAD_4: return RETROK_KP4;
        case AKEYCODE_NUMPAD_5: return RETROK_KP5;
        case AKEYCODE_NUMPAD_6: return RETROK_KP6;
        case AKEYCODE_NUMPAD_7: return RETROK_KP7;
        case AKEYCODE_NUMPAD_8: return RETROK_KP8;
        case AKEYCODE_NUMPAD_9: return RETROK_KP9;
        case AKEYCODE_NUMPAD_DOT: return RETROK_KP_PERIOD;
        case AKEYCODE_NUMPAD_DIVIDE: return RETROK_KP_DIVIDE;
        case AKEYCODE_NUMPAD_MULTIPLY: return RETROK_KP_MULTIPLY;
        case AKEYCODE_NUMPAD_SUBTRACT: return RETROK_KP_MINUS;
        case AKEYCODE_NUMPAD_ADD: return RETROK_KP_PLUS;
        case AKEYCODE_NUMPAD_ENTER: return RETROK_KP_ENTER;
        case AKEYCODE_NUMPAD_EQUALS: return RETROK_KP_EQUALS;
        
        // Arrow keys (mapped from D-pad for keyboard)
        case AKEYCODE_DPAD_UP: return RETROK_UP;
        case AKEYCODE_DPAD_DOWN: return RETROK_DOWN;
        case AKEYCODE_DPAD_RIGHT: return RETROK_RIGHT;
        case AKEYCODE_DPAD_LEFT: return RETROK_LEFT;
        
        // Navigation keys
        case AKEYCODE_INSERT: return RETROK_INSERT;
        case AKEYCODE_MOVE_HOME: return RETROK_HOME;
        case AKEYCODE_MOVE_END: return RETROK_END;
        case AKEYCODE_PAGE_UP: return RETROK_PAGEUP;
        case AKEYCODE_PAGE_DOWN: return RETROK_PAGEDOWN;
        
        // Function keys
        case AKEYCODE_F1: return RETROK_F1;
        case AKEYCODE_F2: return RETROK_F2;
        case AKEYCODE_F3: return RETROK_F3;
        case AKEYCODE_F4: return RETROK_F4;
        case AKEYCODE_F5: return RETROK_F5;
        case AKEYCODE_F6: return RETROK_F6;
        case AKEYCODE_F7: return RETROK_F7;
        case AKEYCODE_F8: return RETROK_F8;
        case AKEYCODE_F9: return RETROK_F9;
        case AKEYCODE_F10: return RETROK_F10;
        case AKEYCODE_F11: return RETROK_F11;
        case AKEYCODE_F12: return RETROK_F12;
        
        // Lock keys
        case AKEYCODE_NUM_LOCK: return RETROK_NUMLOCK;
        case AKEYCODE_CAPS_LOCK: return RETROK_CAPSLOCK;
        case AKEYCODE_SCROLL_LOCK: return RETROK_SCROLLOCK;
        
        // Modifier keys
        case AKEYCODE_SHIFT_LEFT: return RETROK_LSHIFT;
        case AKEYCODE_SHIFT_RIGHT: return RETROK_RSHIFT;
        case AKEYCODE_CTRL_LEFT: return RETROK_LCTRL;
        case AKEYCODE_CTRL_RIGHT: return RETROK_RCTRL;
        case AKEYCODE_ALT_LEFT: return RETROK_LALT;
        case AKEYCODE_ALT_RIGHT: return RETROK_RALT;
        
        default:
            return RETROK_UNKNOWN;
    }
}

// P1 #8: Sensors Support - compatible RetroArch android_input_set_sensor_state()
// Enable/disable sensors (accelerometer, gyroscope) for port 0
// Compatible with RetroArch android_input.c lignes 1933-2024
bool Input::setSensorState(unsigned port, unsigned action, unsigned eventRate) {
    // RetroArch only supports sensors on port 0
    if (port > 0) {
        return false;
    }
    
    // Default event rate: 60 Hz (compatible RetroArch DEFAULT_ASENSOR_EVENT_RATE = 60)
    if (eventRate == 0) {
        eventRate = 60;
    }
    
    switch (action) {
        case RETRO_SENSOR_ACCELEROMETER_ENABLE:
            accelerometerEnabled = true;
            // Reset values when enabling (compatible RetroArch behavior)
            accelerometerState.x = 0.0f;
            accelerometerState.y = 0.0f;
            accelerometerState.z = 0.0f;
            LOGD("[NATIVE SENSOR] Accelerometer ENABLED (event_rate=%u)", eventRate);
            return true;
            
        case RETRO_SENSOR_ACCELEROMETER_DISABLE:
            accelerometerEnabled = false;
            // Reset values when disabling (compatible RetroArch behavior)
            accelerometerState.x = 0.0f;
            accelerometerState.y = 0.0f;
            accelerometerState.z = 0.0f;
            LOGD("[NATIVE SENSOR] Accelerometer DISABLED");
            return true;
            
        case RETRO_SENSOR_GYROSCOPE_ENABLE:
            gyroscopeEnabled = true;
            // Reset values when enabling (compatible RetroArch behavior)
            gyroscopeState.x = 0.0f;
            gyroscopeState.y = 0.0f;
            gyroscopeState.z = 0.0f;
            LOGD("[NATIVE SENSOR] Gyroscope ENABLED (event_rate=%u)", eventRate);
            return true;
            
        case RETRO_SENSOR_GYROSCOPE_DISABLE:
            gyroscopeEnabled = false;
            // Reset values when disabling (compatible RetroArch behavior)
            gyroscopeState.x = 0.0f;
            gyroscopeState.y = 0.0f;
            gyroscopeState.z = 0.0f;
            LOGD("[NATIVE SENSOR] Gyroscope DISABLED");
            return true;
            
        default:
            return false;
    }
}

// P1 #8: Sensors Support - get sensor input (float return, compatible RetroArch android_input_get_sensor_input)
// Returns sensor value (X, Y, or Z) for accelerometer or gyroscope
// Compatible with RetroArch android_input.c lignes 2026-2051
float Input::getSensorInput(unsigned port, unsigned id) const {
    // RetroArch only supports sensors on port 0
    if (port > 0) {
        return 0.0f;
    }
    
    switch (id) {
        case RETRO_SENSOR_ACCELEROMETER_X:
            return accelerometerState.x;
        case RETRO_SENSOR_ACCELEROMETER_Y:
            return accelerometerState.y;
        case RETRO_SENSOR_ACCELEROMETER_Z:
            return accelerometerState.z;
        case RETRO_SENSOR_GYROSCOPE_X:
            return gyroscopeState.x;
        case RETRO_SENSOR_GYROSCOPE_Y:
            return gyroscopeState.y;
        case RETRO_SENSOR_GYROSCOPE_Z:
            return gyroscopeState.z;
        default:
            return 0.0f;
    }
}

// P1 #8: Sensors Support - update sensor values from Android
// Called from JNI when sensor events are received (ASensorEvent)
// Compatible with RetroArch android_input_poll_user() lignes 1580-1623
// P2: Triggers séparés (L2/R2 axes) - Update trigger analog values
void Input::setTriggerValue(unsigned port, int trigger, float value) {
    if (port >= 4) {  // MAX_PORTS = 4 (ports 0-3)
        return;
    }
    
    // Clamp value to [0.0, 1.0]
    float clampedValue = std::max(0.0f, std::min(1.0f, value));
    
    // trigger: 0 = L2, 1 = R2 (matching RetroArch analog_state[port][6/7])
    if (trigger == 0) {
        pads[port].triggerL2 = clampedValue;
        LOGD("[NATIVE TRIGGER] port=%d L2=%.3f", port, clampedValue);
    } else if (trigger == 1) {
        pads[port].triggerR2 = clampedValue;
        LOGD("[NATIVE TRIGGER] port=%d R2=%.3f", port, clampedValue);
    }
}

void Input::onSensorEvent(int sensorType, float x, float y, float z) {
    // sensorType: ASENSOR_TYPE_ACCELEROMETER (1) or ASENSOR_TYPE_GYROSCOPE (4)
    // Compatible with RetroArch ASensorEvent.type values
    
    switch (sensorType) {
        case 1: // ASENSOR_TYPE_ACCELEROMETER
            if (accelerometerEnabled) {
                accelerometerState.x = x;
                accelerometerState.y = y;
                accelerometerState.z = z;
                LOGD("[NATIVE SENSOR] Accelerometer: X=%.3f Y=%.3f Z=%.3f", x, y, z);
            }
            break;
            
        case 4: // ASENSOR_TYPE_GYROSCOPE
            if (gyroscopeEnabled) {
                // Note: RetroArch reads gyroscope from event.data[0/1/2] instead of event.acceleration
                // This is because ASensorEvent struct is "mysterious" according to RetroArch comments
                gyroscopeState.x = x;
                gyroscopeState.y = y;
                gyroscopeState.z = z;
                LOGD("[NATIVE SENSOR] Gyroscope: X=%.3f Y=%.3f Z=%.3f", x, y, z);
            }
            break;
            
        default:
            // Unknown sensor type, ignore
            break;
    }
}

// Show Inputs PHYSICAL: Vérifier si un bouton physique est pressé
bool Input::isPhysicalButtonPressed(unsigned port, int retroPadId) const {
    if (port >= 4) return false;
    return pads[port].pressedKeys.find(retroPadId) != pads[port].pressedKeys.end();
}

void Input::onKeyEvent(unsigned int port, int action, int keyCode) {
    if (port >= 4) return;
    
    // CORRECTION: Utiliser mappings autoconfig dynamiques (compatible RetroArch)
    // 1. Vérifier si un mapping autoconfig existe pour ce keyCode
    int retroPadId = getRetroPadIdFromKeyCode(port, keyCode);
    
    if (retroPadId != UNKNOWN_KEY) {
        // Mapping autoconfig trouvé - utiliser le mapping dynamique
        if (action == AKEY_EVENT_ACTION_DOWN) {
            pads[port].pressedKeys.insert(retroPadId);
            pads[port].pressedKeyCodes.insert(keyCode);
        } else if (action == AKEY_EVENT_ACTION_UP) {
            pads[port].pressedKeys.erase(retroPadId);
            pads[port].pressedKeyCodes.erase(keyCode);
        }
        return;
    }
    
    // 2. Fallback: mapping hardcodé (si pas d'autoconfig)
    int retroKeyCode = convertAndroidToLibretroKey(keyCode);
    if (retroKeyCode != UNKNOWN_KEY) {
        // Gamepad button (RetroPad) - hardcoded mapping
        if (action == AKEY_EVENT_ACTION_DOWN) {
            pads[port].pressedKeys.insert(retroKeyCode);
            pads[port].pressedKeyCodes.insert(keyCode);
        } else if (action == AKEY_EVENT_ACTION_UP) {
            pads[port].pressedKeys.erase(retroKeyCode);
            pads[port].pressedKeyCodes.erase(keyCode);
        }
        return;
    }
    
    // 3. Pas un gamepad button, essayer comme clavier (RetroK)
    // Note: We can't get metaState here, so modifiers won't be available
    // For full keyboard support with modifiers, use onKeyboardEvent() instead
    unsigned retroK = convertAndroidToRetroK(keyCode);
    if (retroK != RETROK_UNKNOWN) {
        if (action == AKEY_EVENT_ACTION_DOWN) {
            pads[port].pressedKeyboardKeys.insert(retroK);
        } else if (action == AKEY_EVENT_ACTION_UP) {
            pads[port].pressedKeyboardKeys.erase(retroK);
        }
    }
}

// P1 #9: Keyboard Support - compatible RetroArch android_input_poll_event_type_keyboard()
// Full keyboard support with modifiers (ALT, CTRL, SHIFT, etc.)
// Compatible with RetroArch android_input.c lignes 879-909
void Input::onKeyboardEvent(unsigned int port, int action, int keyCode, int metaState) {
    int keydown = (action == AKEY_EVENT_ACTION_DOWN);
    unsigned keyboardcode = convertAndroidToRetroK(keyCode);
    
    if (keyboardcode == RETROK_UNKNOWN) {
        // Not a keyboard key, try as gamepad button (fallback)
        int retroKeyCode = convertAndroidToLibretroKey(keyCode);
        if (retroKeyCode != UNKNOWN_KEY) {
            if (keydown) {
                pads[port].pressedKeys.insert(retroKeyCode);
            } else {
                pads[port].pressedKeys.erase(retroKeyCode);
            }
        }
        return;
    }
    
    // Update keyboard state
    if (keydown) {
        pads[port].pressedKeyboardKeys.insert(keyboardcode);
    } else {
        pads[port].pressedKeyboardKeys.erase(keyboardcode);
    }
    
    // Note: Modifiers (ALT, CTRL, SHIFT) are extracted from metaState
    // but not stored separately in LibretroDroid architecture
    // If core needs modifiers, it should use RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK
    // which is handled by environment.cpp (outside Input class)
}

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
            // Legacy: update pointer[0] for backward compatibility
            LOGD("[NATIVE MOTION] port=%d POINTER[0] stored: X=%.3f Y=%.3f", port, xAxis, yAxis);
            onMotionEventMulti(port, motionSource, xAxis, yAxis, 0);
            break;
    }
}

void Input::onMotionEventMulti(int port, int motionSource, float xAxis, float yAxis, int pointerIndex) {
    if (port < 0 || port >= 4) return;
    if (pointerIndex < 0 || pointerIndex >= GamePadState::MAX_POINTERS) return;
    
    if (motionSource == Input::MOTION_SOURCE_POINTER) {
        PointerState& pointer = pads[port].pointers[pointerIndex];
        
        // Update pointer state
        pointer.screenX = xAxis;
        pointer.screenY = yAxis;
        pointer.active = (xAxis >= 0.0f && yAxis >= 0.0f);
        
        // Viewport conversion: Convert normalized [0.0, 1.0] to RetroArch format [-0x7fff, +0x7fff]
        // This follows the same logic as RetroArch's video_driver_translate_coord_viewport()
        if (pointer.active) {
            // Simple conversion for now (normalized coordinates already account for viewport)
            // Future enhancement: Use actual viewport info if available
            pointer.x = convertNormalizedToRetroArch(xAxis, true);   // reportOob = true
            pointer.y = convertNormalizedToRetroArch(yAxis, true);   // reportOob = true
            pointer.confined_x = convertNormalizedToRetroArch(xAxis, false); // reportOob = false (clamped)
            pointer.confined_y = convertNormalizedToRetroArch(yAxis, false); // reportOob = false (clamped)
            pointer.screen_x = pointer.x;  // For now, same as pointer.x
            pointer.screen_y = pointer.y;  // For now, same as pointer.y
        } else {
            // Reset coordinates when inactive
            pointer.x = 0;
            pointer.y = 0;
            pointer.confined_x = 0;
            pointer.confined_y = 0;
            pointer.screen_x = 0;
            pointer.screen_y = 0;
        }
        
        // Update legacy fields for backward compatibility (pointer[0] only)
        if (pointerIndex == 0) {
            pads[port].pointerScreenXAxis = xAxis;
            pads[port].pointerScreenYAxis = yAxis;
        }
        
        // Update pointer count
        int maxActiveIndex = -1;
        for (int i = 0; i < GamePadState::MAX_POINTERS; i++) {
            if (pads[port].pointers[i].active) {
                maxActiveIndex = i;
            }
        }
        pads[port].pointerCount = (maxActiveIndex >= 0) ? (maxActiveIndex + 1) : 0;
        
        LOGD("[NATIVE MOTION] port=%d POINTER[%d] stored: X=%.3f Y=%.3f (active=%d, count=%d, converted=%d,%d)", 
             port, pointerIndex, xAxis, yAxis, pointer.active ? 1 : 0, pads[port].pointerCount, pointer.x, pointer.y);
    }
}

// Viewport conversion: Convert normalized [0.0, 1.0] to RetroArch format [-0x7fff, +0x7fff]
// This follows the same edge case logic as RetroArch's video_driver_translate_coord_viewport()
int16_t Input::convertNormalizedToRetroArch(float normalized, bool reportOob) const {
    if (normalized < 0.0f || normalized > 1.0f) {
        // Out of bounds
        if (!reportOob) {
            // Clamp to bounds
            if (normalized < 0.0f) {
                return -0x7fff;
            } else {
                return 0x7fff;
            }
        }
        // Report out of bounds
        return -0x8000;
    }
    
    // Convert [0.0, 1.0] to [-0x7fff, +0x7fff]
    // RetroArch logic: 0 maps to -0x7fff, 1 maps to +0x7fff
    // Edge cases: 0 → -0x7fff, 1 → +0x7fff
    if (normalized == 0.0f) {
        return -0x7fff;
    } else if (normalized == 1.0f) {
        return 0x7fff;
    } else {
        // Linear conversion: (normalized - 0.5) * 2.0 * 0x7fff
        // This is equivalent to: normalized * 0xffff - 0x8000 (RetroArch formula)
        int16_t result = (int16_t) ((normalized * 0xffff) - 0x8000);
        return result;
    }
}

// Full viewport conversion (for future use with actual viewport info)
bool Input::translateCoordViewport(
    const Viewport& vp,
    int mouseX, int mouseY,
    int16_t* resX, int16_t* resY,
    int16_t* resScreenX, int16_t* resScreenY,
    bool reportOob) const {
    
    if (!resX || !resY || !resScreenX || !resScreenY) {
        return false;
    }
    
    // Validate viewport
    if (vp.width <= 0 || vp.height <= 0 || vp.full_width <= 0 || vp.full_height <= 0) {
        return false;
    }
    
    // RetroArch-compatible conversion
    int scaled_screen_x = -0x8000; /* OOB */
    int scaled_screen_y = -0x8000; /* OOB */
    int scaled_x = -0x8000; /* OOB */
    int scaled_y = -0x8000; /* OOB */
    
    // Convert screen coordinates
    if (mouseX > 0 && mouseX < (int)vp.full_width) {
        scaled_screen_x = ((mouseX * 0xffff) / ((int)vp.full_width - 1)) - 0x8000;
    } else if (mouseX == 0) {
        scaled_screen_x = -0x7fff;
    }
    
    if (mouseY > 0 && mouseY < (int)vp.full_height) {
        scaled_screen_y = ((mouseY * 0xffff) / ((int)vp.full_height - 1)) - 0x8000;
    } else if (mouseY == 0) {
        scaled_screen_y = -0x7fff;
    }
    
    // Convert viewport coordinates (subtract viewport offset)
    mouseX -= vp.x;
    mouseY -= vp.y;
    
    if (mouseX > 0 && mouseX < (int)vp.width) {
        scaled_x = ((mouseX * 0xffff) / ((int)vp.width - 1)) - 0x8000;
    } else if (mouseX == 0) {
        scaled_x = -0x7fff;
    } else if (!reportOob) {
        // Clamp to bounds if not reporting out-of-bounds
        if (mouseX < 0) {
            scaled_x = -0x7fff;
        } else {
            scaled_x = 0x7fff;
        }
    }
    
    if (mouseY > 0 && mouseY < (int)vp.height) {
        scaled_y = ((mouseY * 0xffff) / ((int)vp.height - 1)) - 0x8000;
    } else if (mouseY == 0) {
        scaled_y = -0x7fff;
    } else if (!reportOob) {
        // Clamp to bounds if not reporting out-of-bounds
        if (mouseY < 0) {
            scaled_y = -0x7fff;
        } else {
            scaled_y = 0x7fff;
        }
    }
    
    *resX = (int16_t)scaled_x;
    *resY = (int16_t)scaled_y;
    *resScreenX = (int16_t)scaled_screen_x;
    *resScreenY = (int16_t)scaled_screen_y;
    
    return true;
}

template<typename... T>
bool Input::anyPressed(unsigned int port, unsigned int id, T &... args) const {
    return anyPressed(port, id) || anyPressed(port, args...);
}

bool Input::anyPressed(unsigned int port, unsigned int id) const {
    return pads[port].pressedKeys.count(id) > 0;
}

void Input::onMouseButton(int port, int button, int pressed) {
    if (port < 0 || port >= 4) return;
    
    bool isPressed = (pressed != 0);
    
    switch (button) {
        case 1: // Left button
            pads[port].mouseButtonLeft = isPressed;
            LOGD("[NATIVE MOUSE] port=%d BUTTON_LEFT=%d", port, isPressed ? 1 : 0);
            break;
        case 2: // Right button
            pads[port].mouseButtonRight = isPressed;
            LOGD("[NATIVE MOUSE] port=%d BUTTON_RIGHT=%d", port, isPressed ? 1 : 0);
            break;
        case 3: // Middle button
            pads[port].mouseButtonMiddle = isPressed;
            LOGD("[NATIVE MOUSE] port=%d BUTTON_MIDDLE=%d", port, isPressed ? 1 : 0);
            break;
        case 4: // Button 4 (P3: Gestion boutons souris multiples)
            pads[port].mouseButton4 = isPressed;
            LOGD("[NATIVE MOUSE] port=%d BUTTON_4=%d", port, isPressed ? 1 : 0);
            break;
        case 5: // Button 5 (P3: Gestion boutons souris multiples)
            pads[port].mouseButton5 = isPressed;
            LOGD("[NATIVE MOUSE] port=%d BUTTON_5=%d", port, isPressed ? 1 : 0);
            break;
        default:
            LOGD("[NATIVE MOUSE] Unknown mouse button: %d", button);
            break;
    }
}

// Autoconfig mappings - compatible RetroArch input_config_set_autoconfig_binds()
void Input::setAutoconfigMapping(unsigned port, int retroPadId, int keyCode) {
    if (port >= 4) return;
    
    // Mapping: RETRO_DEVICE_ID_JOYPAD_* → AKEYCODE (joykey from .cfg)
    // Ex: input_a_btn = "96" → setAutoconfigMapping(port, RETRO_DEVICE_ID_JOYPAD_A, 96)
    // Compatible RetroArch: android_joypad_button_state() uses joykey from autoconfig
    pads[port].autoconfigMappings[keyCode] = retroPadId;
    
    LOGD("[NATIVE AUTOCONFIG] port=%d mapping: AKEYCODE=%d → RetroPad ID=%d", port, keyCode, retroPadId);
}

void Input::clearAutoconfigMappings(unsigned port) {
    if (port >= 4) return;
    pads[port].autoconfigMappings.clear();
    LOGD("[NATIVE AUTOCONFIG] port=%d mappings cleared", port);
}

int Input::getRetroPadIdFromKeyCode(unsigned port, int keyCode) const {
    if (port >= 4) return UNKNOWN_KEY;
    
    // Chercher dans les mappings autoconfig
    auto it = pads[port].autoconfigMappings.find(keyCode);
    if (it != pads[port].autoconfigMappings.end()) {
        return it->second;  // Retourner le RetroPad ID mappé
    }
    
    return UNKNOWN_KEY;  // Pas de mapping trouvé
}

} //namespace libretrodroid
