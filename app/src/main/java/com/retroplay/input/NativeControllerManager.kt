package com.retroplay.input

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.swordfish.libretrodroid.GLRetroView

/**
 * Manages controller configuration, port mapping, and console-specific extensions (e.g. N64 Paks).
 * Extracts logic previously found in NativeComposeEmulatorActivity.
 */
class NativeControllerManager(
    private val context: Context,
    private val retroView: GLRetroView
) {

    companion object {
        private const val TAG = "NativeControllerManager"
    }

    /**
     * Configure controllers after game is loaded.
     * Handles manual port configuration and auto-detection (Zapper, etc.)
     */
    fun configureControllers(
        console: String,
        isZapperGame: Boolean,
        zapperPort: Int
    ) {
        try {
            // Check if retroView has controllers
            val testControllers = try {
                retroView.getControllers()
            } catch (e: Exception) {
                Log.w(TAG, "[CONTROLLER] Cannot access controllers: ${e.message}")
                null
            }

            if (testControllers == null || (testControllers is Array<*> && testControllers.isEmpty()) || (testControllers is Collection<*> && testControllers.isEmpty())) {
                Log.w(TAG, "[CONTROLLER] No controllers available, game may not be loaded yet")
                return
            }

            // PSX Controller Type Logic (Commented out in original, keeping it here for reference/future enable)
            /*
            if (console.equals("psx", ignoreCase = true)) {
                 // ... (DualShock logic logic)
                 Log.i(TAG, "[PSX] Controller configuration DISABLED for testing")
            }
            */

            var hasManualConfig = false
            
            // Read from console_config (ConsoleConfigActivity preference file)
            // AND compose_gamepad_settings (RetroArchSettingsDialog preference file)
            val consoleConfigPrefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE)
            val composePrefs = context.getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)

            // Check each port (0-3)
            for (port in 0..3) {
                // Priority: console_config then compose_gamepad_settings
                var manualControllerType = consoleConfigPrefs.getInt("controller_port_${console}_port${port}", -1)
                if (manualControllerType == -1) {
                    manualControllerType = composePrefs.getInt("controller_port_${console}_port${port}", -1)
                }

                if (manualControllerType != -1) {
                    hasManualConfig = true
                    try {
                        retroView.setControllerType(port, manualControllerType)
                        val controllerName = getControllerName(manualControllerType)
                        Log.i(TAG, "[CONTROLLER] Port ${port + 1} manually configured as: $controllerName (id=$manualControllerType)")
                    } catch (e: Exception) {
                        Log.w(TAG, "[CONTROLLER] Failed to set controller type for port ${port + 1}: ${e.message} - Continuing")
                    }
                }
            }

            // Auto-detection fallback (Zapper)
            if (!hasManualConfig && isZapperGame) {
                try {
                    // RETRO_DEVICE_ZAPPER = 258
                    retroView.setControllerType(zapperPort, 258)
                    Log.i(TAG, "[ZAPPER] Auto-detected: Zapper configured as RETRO_DEVICE_ZAPPER (258) on port ${zapperPort + 1}")
                    
                    showToast("Zapper detected! Port ${zapperPort + 1}\nTouch game area to shoot")
                } catch (e: Exception) {
                    Log.e(TAG, "[ZAPPER] Failed to set Zapper controller type: ${e.message}")
                }
            } else if (hasManualConfig) {
                Log.i(TAG, "[CONTROLLER] Manual port configuration applied (auto-detection overridden)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[CONTROLLER] Error in configureControllers: ${e.message}", e)
        }
    }

    /**
     * Configures N64 specific extensions (Controller Pak, Rumble Pak, Transfer Pak).
     */
    fun configureN64Extensions() {
        try {
            Log.i(TAG, "[N64] Configuring controller extensions...")

            // Shared Preferences
            val prefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE)
            val prefix = "n64_"

            // Value map: 0=Controller Pak(1), 1=Rumble Pak(2), 2=Transfer Pak(5)
            val pakValues = intArrayOf(1, 2, 5)
            val configuredExtensions = mutableListOf<Pair<Int, String>>()

            /*
            // Logic commented out in original file for testing stability
            for (port in 0..3) {
                try {
                    val pakPosition = prefs.getInt(prefix + "pak_port" + (port + 1), 0)
                    if (pakPosition >= 0 && pakPosition < pakValues.size) {
                        val pakValue = pakValues[pakPosition]
                         val pakName = when (pakPosition) {
                            0 -> "Controller Pak"
                            1 -> "Rumble Pak"
                            2 -> "Transfer Pak"
                            else -> "Unknown"
                        }
                        
                        try {
                            retroView.setControllerType(port, pakValue)
                             configuredExtensions.add(Pair(port + 1, pakName))
                        } catch (e: Exception) {
                             Log.w(TAG, "[N64] Failed to set extension port ${port + 1}: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[N64] Error configuring port ${port + 1}: ${e.message}")
                }
            }
            */
            Log.i(TAG, "[N64] Extension configuration DISABLED for testing (mirroring original Activity behavior)")

            if (configuredExtensions.isNotEmpty()) {
                Log.i(TAG, "[N64] Successfully configured: ${configuredExtensions.joinToString { "Port ${it.first}=${it.second}" }}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[N64] Error configuring controller extensions: ${e.message}", e)
        }
    }
    
    // Helper to decode controller type names for logging
    private fun getControllerName(type: Int): String {
        return when (type) {
            0 -> "None"
            1 -> "Joypad"
            4 -> "Lightgun"
            6 -> "Pointer"
            258 -> "Zapper"
            else -> "Type $type"
        }
    }

    private fun showToast(message: String) {
        // Need to run on UI thread if calling from background check? 
        // Generally this is called from FrameRendered which might be GL thread, 
        // but Toast can be tricky. Using MainLooper just in case.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
             Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
