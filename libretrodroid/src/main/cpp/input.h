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

#ifndef LIBRETRODROID_INPUT_H
#define LIBRETRODROID_INPUT_H

#include <cstdint>
#include <unordered_set>
#include <array>

namespace libretrodroid {

class Input {

private:
    // Viewport structure (similar to RetroArch's video_viewport)
    struct Viewport {
        int x = 0;                  // Viewport X offset
        int y = 0;                  // Viewport Y offset
        unsigned width = 0;         // Viewport width
        unsigned height = 0;        // Viewport height
        unsigned full_width = 0;    // Full screen width
        unsigned full_height = 0;   // Full screen height
    };
    
    // P1 #8: Sensors Support - compatible RetroArch sensor_t structure
    // Stores accelerometer/gyroscope values (x, y, z)
    struct SensorState {
        float x = 0.0f;
        float y = 0.0f;
        float z = 0.0f;
    };
    
    // Pointer state structure (similar to RetroArch's input_pointer)
    struct PointerState {
        float screenX = -1.0f;  // Normalized [0.0, 1.0] or -1 if inactive
        float screenY = -1.0f;  // Normalized [0.0, 1.0] or -1 if inactive
        bool active = false;    // True if pointer is currently active
        
        // Viewport-converted coordinates (RetroArch format)
        int16_t x = 0;              // Converted X [-0x7fff, +0x7fff]
        int16_t y = 0;              // Converted Y [-0x7fff, +0x7fff]
        int16_t confined_x = 0;     // Confined X (clamped to viewport)
        int16_t confined_y = 0;     // Confined Y (clamped to viewport)
        int16_t screen_x = 0;       // Screen-space X
        int16_t screen_y = 0;       // Screen-space Y
    };
    
    struct GamePadState {
        std::unordered_set<int> pressedKeys;  // RetroPad IDs (RETRO_DEVICE_ID_JOYPAD_*)

        int dpadXAxis = 0;
        int dpadYAxis = 0;
        float joypadLeftXAxis = 0;
        float joypadLeftYAxis = 0;
        float joypadRightXAxis = 0;
        float joypadRightYAxis = 0;
        
        // Multi-touch support (up to 16 pointers like RetroArch)
        static constexpr int MAX_POINTERS = 16;
        PointerState pointers[MAX_POINTERS];
        int pointerCount = 0;
        
        // Legacy single pointer (kept for backward compatibility, maps to pointer[0])
        float pointerScreenXAxis = -1;
        float pointerScreenYAxis = -1;
        
        // Mouse button states (for Zapper/Lightgun)
        bool mouseButtonLeft = false;
        bool mouseButtonRight = false;
        bool mouseButtonMiddle = false;
        
        // Keyboard support (P1 #9) - compatible RetroArch RETRO_DEVICE_KEYBOARD
        // Store pressed keyboard keys as RetroK IDs (RETROK_*)
        std::unordered_set<unsigned> pressedKeyboardKeys;
    };

public:
    static constexpr int MOTION_SOURCE_DPAD = 0;
    static constexpr int MOTION_SOURCE_ANALOG_LEFT = 1;
    static constexpr int MOTION_SOURCE_ANALOG_RIGHT = 2;
    static constexpr int MOTION_SOURCE_POINTER = 3;
    static constexpr int MAX_RANGE_MOTION = 0x7fff;

    static constexpr int RETRO_DEVICE_ID_JOYPAD_UP_LEFT = 50;
    static constexpr int RETRO_DEVICE_ID_JOYPAD_UP_RIGHT = 51;
    static constexpr int RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT = 52;
    static constexpr int RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT = 53;

    int16_t getInputState(unsigned port, unsigned device, unsigned index, unsigned id);

    void onKeyEvent(unsigned int port, int action, int keyCode);
    void onMotionEvent(int port, int motionSource, float xAxis, float yAxis);
    
    // Multi-touch support: update pointer at specified index (0-15)
    void onMotionEventMulti(int port, int motionSource, float xAxis, float yAxis, int pointerIndex);
    
    void onMouseButton(int port, int button, int pressed);
    
    // P1 #9: Keyboard Support - compatible RetroArch android_input_poll_event_type_keyboard()
    void onKeyboardEvent(unsigned int port, int action, int keyCode, int metaState);
    
    // P1 #8: Sensors Support - compatible RetroArch android_input_set_sensor_state()
    // Enable/disable sensors (accelerometer, gyroscope) for port 0
    bool setSensorState(unsigned port, unsigned action, unsigned eventRate);
    
    // P1 #8: Sensors Support - get sensor input (float return, compatible RetroArch android_input_get_sensor_input)
    // Returns sensor value (X, Y, or Z) for accelerometer or gyroscope
    float getSensorInput(unsigned port, unsigned id) const;
    
    // P1 #8: Sensors Support - update sensor values from Android
    // Called from JNI when sensor events are received
    void onSensorEvent(int sensorType, float x, float y, float z);

private:
    const int UNKNOWN_KEY = -1;

    template<typename ...T>
    bool anyPressed(unsigned int port, unsigned id, T&... args) const;
    bool anyPressed(unsigned int port, unsigned int id) const;
    int convertAndroidToLibretroKey(int keyCode) const;
    
    // P1 #9: Keyboard Support - convert Android keycode → RetroK (compatible RetroArch input_keymaps_translate_keysym_to_rk)
    // Maps Android AKEYCODE_* to RetroArch RETROK_* (for RETRO_DEVICE_KEYBOARD)
    // Compatible with RetroArch input_keymaps.c:rarch_key_map_android[] (lignes 1395-1502)
    unsigned convertAndroidToRetroK(int keyCode) const;
    
    // P1 #8: Sensors Support - sensor states (global, not per-port like RetroArch)
    SensorState accelerometerState;
    SensorState gyroscopeState;
    
    // P1 #8: Sensors Support - enabled flags (compatible RetroArch sensor_state_mask)
    bool accelerometerEnabled = false;
    bool gyroscopeEnabled = false;
    
    // Viewport conversion functions (RetroArch compatible)
    // Convert normalized coordinates [0.0, 1.0] to RetroArch format [-0x7fff, +0x7fff]
    int16_t convertNormalizedToRetroArch(float normalized, bool reportOob) const;
    
    // Full viewport conversion (for future use with actual viewport info)
    bool translateCoordViewport(
        const Viewport& vp,
        int mouseX, int mouseY,
        int16_t* resX, int16_t* resY,
        int16_t* resScreenX, int16_t* resScreenY,
        bool reportOob) const;
    
    // Current viewport (can be set from video driver if needed)
    Viewport currentViewport;

    GamePadState pads[4];
};

}

#endif //LIBRETRODROID_INPUT_H
