package com.retroplay.input

import android.view.KeyEvent
import android.util.Log
import androidx.compose.runtime.MutableState
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent

/**
 * Helper object to handle PadKit events and bridge them to GLRetroView.
 * Extracts logic previously embedded in NativeComposeEmulatorActivity.
 */
object PadKitHelper {

    private const val TAG = "PadKitHelper"

    /**
     * Handles a list of input events from PadKit and dispatches them to the emulator view.
     * Also intercepts specific global actions like the Menu button.
     *
     * @param events List of input events from PadKit.
     * @param retroView The emulator view to send events to.
     * @param showMainMenu State to toggle the main menu when the menu button is pressed.
     * @param settings Current controller settings (for swap/invert logic).
     */
    fun handleInputEvents(
        events: List<InputEvent>,
        retroView: GLRetroView,
        showMainMenu: MutableState<Boolean>,
        settings: TouchControllerSettingsManager.Settings
    ) {
        // Intercept menu button (like Lemuroid)
        val menuEvent = events.firstOrNull { 
            it is InputEvent.Button && it.id == KeyEvent.KEYCODE_BUTTON_MODE
        }
        
        if (menuEvent != null && (menuEvent as InputEvent.Button).pressed) {
            Log.i(TAG, "Menu button pressed, opening main menu")
            showMainMenu.value = true
            return  // Do not send menu event to emulator
        }
        
        // Process all other events
        events.forEach { event ->
            when (event) {
                is InputEvent.Button -> {
                    val keyCode = event.id
                    if (keyCode != KeyEvent.KEYCODE_BUTTON_MODE) {
                        val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                        try {
                            retroView.sendKeyEvent(action, keyCode, 0)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending key event: ${e.message}")
                        }
                    }
                }
                
                is InputEvent.DiscreteDirection -> {
                    // D-Pad and discrete directions
                    val source = when (event.id) {
                        0 -> GLRetroView.MOTION_SOURCE_DPAD
                        1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                        2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                        else -> GLRetroView.MOTION_SOURCE_DPAD
                    }
                    try {
                        retroView.sendMotionEvent(source, event.direction.x, -event.direction.y)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending motion event (Discrete): ${e.message}")
                    }
                }
                
                is InputEvent.ContinuousDirection -> {
                    // Analog sticks
                    // Note: In ComposeTouchLayouts: MOTION_SOURCE_LEFT_STICK = 1, MOTION_SOURCE_RIGHT_STICK = 2
                    var stickId = event.id
                    
                    // Apply swap if requested (1 and 2 only, not DPAD which is 0)
                    if (settings.swapAnalogSticks && (stickId == 1 || stickId == 2)) {
                        stickId = if (stickId == 1) 2 else 1
                    }
                    
                    val source = when (stickId) {
                        1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                        2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                        else -> GLRetroView.MOTION_SOURCE_DPAD // ID 0 = DPAD
                    }
                    
                    // Apply Y inversion if requested
                    val invertY = when (stickId) {
                        1 -> settings.invertAnalogLeftY
                        2 -> settings.invertAnalogRightY
                        else -> false
                    }
                    val yAxis = if (invertY) event.direction.y else -event.direction.y
                    
                    try {
                        retroView.sendMotionEvent(source, event.direction.x, yAxis)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending motion event (Continuous): ${e.message}")
                    }
                }
            }
        }
    }
    
    fun loadSettings(prefs: android.content.SharedPreferences, console: String): TouchControllerSettingsManager.Settings {
        val key = "gamepad_${console}_settings"
        return TouchControllerSettingsManager.Settings(
            scale = prefs.getFloat("${key}_scale", 0.5f),
            rotation = prefs.getFloat("${key}_rotation", 0.0f),
            marginX = prefs.getFloat("${key}_marginX", 0.0f),
            marginY = prefs.getFloat("${key}_marginY", 0.0f),
            swapAnalogSticks = prefs.getBoolean("${key}_swap", false),
            invertAnalogLeftY = prefs.getBoolean("${key}_invertLeftY", prefs.getBoolean("${key}_invertY", false)),
            invertAnalogRightY = prefs.getBoolean("${key}_invertRightY", prefs.getBoolean("${key}_invertY", false))
        )
    }
}
