package com.retroplay.helpers

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import com.swordfish.libretrodroid.GLRetroView

/**
 * Helper class to handle Controller Configuration (Input Ports, Zapper, Manual Overrides).
 * Extracted from RetroArchEmulatorActivity.
 */
object ControllerHelper {

    private const val TAG = "ControllerHelper"

    /**
     * Configure all controllers after the game is loaded (called after first FrameRendered).
     */
    fun configureControllersAfterGameLoaded(
        activity: Activity,
        retroView: GLRetroView,
        console: String,
        prefs: SharedPreferences,
        isZapperGame: Boolean,
        zapperPort: Int,
        isCoreErrorDialogVisible: Boolean
    ) {
        try {
            // CRITIQUE: Log pour Chiller
            if (isZapperGame && zapperPort == 0) {
                Log.i(TAG, "[ZAPPER] configureControllersAfterGameLoaded called for Chiller: isZapperGame=$isZapperGame, zapperPort=$zapperPort")
            }
            
            // Check if game failed to load
            if (isCoreErrorDialogVisible) {
                Log.w(TAG, "[CONTROLLER] Game failed to load, skipping controller configuration")
                return
            }
            
            // Verify retroView is valid
            try {
                val testControllers = retroView.getControllers()
                if (testControllers.isEmpty()) {
                    Log.w(TAG, "[CONTROLLER] No controllers available, game may not be loaded yet")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "[CONTROLLER] Cannot access controllers, game not loaded: ${e.message}")
                return
            }
            
            // PSX: Configure DualShock if analog mode enabled
            if (console.equals("psx", ignoreCase = true)) {
                val psxAnalogEnabled = prefs.getBoolean("psx_analog_mode_enabled", true)
                
                if (psxAnalogEnabled) {
                    try {
                        val controllers = retroView.getControllers()
                        Log.i(TAG, "[PSX] Available controllers: ${controllers.getOrNull(0)?.map { "id=${it.id} desc='${it.description}'" }}")
                        
                        if (controllers.isNotEmpty() && controllers[0].isNotEmpty()) {
                            // Search for DualShock controller
                            val dualshock = controllers[0].firstOrNull { 
                                it.description?.contains("dualshock", ignoreCase = true) == true
                            } ?: controllers[0].firstOrNull {
                                it.description?.contains("analog", ignoreCase = true) == true
                            }
                            
                            if (dualshock != null) {
                                try {
                                    retroView.setControllerType(0, dualshock.id)
                                    Log.i(TAG, "[PSX] Controller type set to DualShock (id=${dualshock.id}, desc='${dualshock.description}') - Analog sticks ENABLED")
                                } catch (e: Exception) {
                                    Log.e(TAG, "[PSX] Failed to set DualShock controller type: ${e.message}")
                                }
                            } else {
                                Log.w(TAG, "[PSX] DualShock controller not found. Using default.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[PSX] Error configuring controller type", e)
                    }
                } else {
                    Log.i(TAG, "[PSX] Analog mode DISABLED - Using standard pad (D-Pad only)")
                }
            }
            
            // Port Configuration
            var hasManualConfig = false
            var zapperPortConfiguredManually = false
            
            val consoleConfigPrefs = activity.getSharedPreferences("console_config", Context.MODE_PRIVATE)
            val composePrefs = activity.getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)
            
            // Check each port (0-3) for manual configuration
            for (port in 0..3) {
                // Priority: console_config then compose_gamepad_settings
                var manualControllerType = consoleConfigPrefs.getInt("controller_port_${console}_port${port}", -1)
                if (manualControllerType == -1) {
                    manualControllerType = composePrefs.getInt("controller_port_${console}_port${port}", -1)
                }
                
                if (manualControllerType != -1) {
                    hasManualConfig = true
                    // Vérifier si c'est un périphérique touchscreen qui est configuré (Zapper, Lightgun, Guncon, SuperScope)
                    if (manualControllerType == 258 || // RETRO_DEVICE_ZAPPER
                        manualControllerType == 4 ||   // RETRO_DEVICE_LIGHTGUN (PSX Guncon, Genesis Menacer, etc.)
                        manualControllerType == 260) { // RETRO_DEVICE_SUPERSCOPE
                        zapperPortConfiguredManually = true
                    }
                    try {
                        retroView.setControllerType(port, manualControllerType)
                        val controllerName = when (manualControllerType) {
                            0 -> "None"
                            1 -> "Joypad"
                            2 -> "Mouse"
                            4 -> "Lightgun"
                            5 -> "Analog"
                            6 -> "Pointer"
                            258 -> "Zapper"
                            260 -> "SuperScope"
                            else -> "Type $manualControllerType"
                        }
                        Log.i(TAG, "[CONTROLLER] Port ${port + 1} manually configured as: $controllerName (id=$manualControllerType)")
                    } catch (e: Exception) {
                        Log.w(TAG, "[CONTROLLER] Failed to set controller type for port ${port + 1}: ${e.message} - Continuing with other ports")
                    }
                }
            }
            
            // Si isZapperGame est vrai (configuré via RetroPlayConfigManager) mais que le port périphérique
            // n'est pas configuré manuellement, appliquer le device type approprié selon la console
            if (isZapperGame && !zapperPortConfiguredManually) {
                try {
                    // Déterminer le device type selon la console
                    val deviceType = when (console.lowercase()) {
                        "nes" -> 258  // RETRO_DEVICE_ZAPPER
                        "snes" -> 260 // RETRO_DEVICE_SUPERSCOPE
                        "psx", "ps1", "playstation" -> 4  // RETRO_DEVICE_LIGHTGUN (Guncon)
                        "genesis", "megadrive" -> 4  // RETRO_DEVICE_LIGHTGUN (Menacer)
                        else -> 258  // Par défaut: Zapper
                    }
                    retroView.setControllerType(zapperPort, deviceType)
                    val deviceName = when (deviceType) {
                        258 -> "Zapper"
                        4 -> "Lightgun/Guncon"
                        260 -> "SuperScope"
                        else -> "Type $deviceType"
                    }
                    Log.i(TAG, "[PERIPHERAL] $deviceName configured from RetroPlayConfigManager on port ${zapperPort + 1} (index $zapperPort)")
                } catch (e: Exception) {
                    Log.e(TAG, "[PERIPHERAL] Failed to set peripheral controller type: ${e.message}", e)
                }
            }
            
            // Configuration manuelle appliquée - plus de détection automatique
            if (hasManualConfig) {
                Log.i(TAG, "[CONTROLLER] Manual port configuration applied for all ports")
            } else if (isZapperGame) {
                Log.i(TAG, "[CONTROLLER] Zapper configuration applied from RetroPlayConfigManager")
            } else {
                Log.d(TAG, "[CONTROLLER] No manual port configuration - using auto-detection from core")
            }
            
            // N64 Extensions (Placeholder - same as original)
             Log.i(TAG, "[N64] Extension configuration DISABLED for testing")
            
        } catch (e: Exception) {
            Log.e(TAG, "[CONTROLLER] Error in configureControllersAfterGameLoaded: ${e.message}", e)
        }
    }

    /**
     * Configure Zapper manually during gameplay (Hot Config).
     * Détecte automatiquement si c'est Chiller (port 0) ou un autre jeu zapper (port 1).
     */
    fun configureZapperManually(
        context: Context,
        retroView: GLRetroView,
        gameName: String? = null,
        zapperPort: Int? = null
    ) {
        try {
            Log.i(TAG, "[ZAPPER] Manual configuration triggered (hot config)!")
            
            // Verify game is loaded
            try {
                val testControllers = retroView.getControllers()
                if (testControllers.isEmpty()) {
                    Log.w(TAG, "[ZAPPER] No controllers available, game may not be loaded")
                    Toast.makeText(context, "Game not loaded. Cannot configure Zapper.", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "[ZAPPER] Cannot access controllers, game not loaded: ${e.message}")
                Toast.makeText(context, "Game not loaded. Cannot configure Zapper.", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Déterminer le port zapper: Chiller utilise port 0, les autres jeux utilisent port 1
            val targetZapperPort = if (zapperPort != null) {
                zapperPort
            } else if (gameName != null) {
                val normalizedName = gameName.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (normalizedName.contains("chiller")) {
                    0  // Port 1 NES pour Chiller
                } else {
                    1  // Port 2 NES pour les autres jeux zapper
                }
            } else {
                1  // Par défaut: port 1 (port 2 NES)
            }
            
            Log.i(TAG, "[ZAPPER] Detected zapper port: $targetZapperPort (${if (targetZapperPort == 0) "Port 1 NES - Chiller" else "Port 2 NES - Standard"})")
            
            // Configurer les ports selon le jeu
            if (targetZapperPort == 0) {
                // Chiller: Port 0 = Zapper, Port 1 = Gamepad (optionnel)
                try {
                    retroView.setControllerType(0, 258)  // RETRO_DEVICE_ZAPPER = 258 sur port 0
                    Log.i(TAG, "[ZAPPER] Port 1 (index 0) configured as ZAPPER (258) for Chiller")
                } catch (e: Exception) {
                    Log.e(TAG, "[ZAPPER] Failed to set Port 1 as ZAPPER: ${e.message}")
                    throw e
                }
                
                // Optionnel: Configurer port 1 comme gamepad si nécessaire
                try {
                    retroView.setControllerType(1, 1)  // RETRO_DEVICE_JOYPAD = 1
                    Log.i(TAG, "[ZAPPER] Port 2 (index 1) configured as GAMEPAD (1)")
                } catch (e: Exception) {
                    Log.w(TAG, "[ZAPPER] Failed to set Port 2 as GAMEPAD (non-critical): ${e.message}")
                }
                
                Toast.makeText(
                    context,
                    "Zapper configured! Port 1=Zapper (Chiller mode)",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Jeux zapper standards: Port 0 = Gamepad, Port 1 = Zapper
            try {
                retroView.setControllerType(0, 1)  // RETRO_DEVICE_JOYPAD = 1
                    Log.i(TAG, "[ZAPPER] Port 1 (index 0) configured as GAMEPAD (1)")
            } catch (e: Exception) {
                Log.e(TAG, "[ZAPPER] Failed to set Port 1 as GAMEPAD: ${e.message}")
                throw e
            }
            
            try {
                retroView.setControllerType(1, 258)  // RETRO_DEVICE_ZAPPER = 258
                    Log.i(TAG, "[ZAPPER] Port 2 (index 1) configured as ZAPPER (258)")
            } catch (e: Exception) {
                Log.e(TAG, "[ZAPPER] Failed to set Port 2 as ZAPPER: ${e.message}")
                throw e
            }
            
            Toast.makeText(
                context,
                    "Zapper configured! Port 1=Gamepad, Port 2=Zapper",
                Toast.LENGTH_LONG
            ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ZAPPER] Error during manual configuration", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}