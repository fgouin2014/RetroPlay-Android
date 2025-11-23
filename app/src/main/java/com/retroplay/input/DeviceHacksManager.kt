package com.retroplay.input

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.InputDevice

/**
 * Gestionnaire des hacks spéciaux pour devices Android
 * 
 * Implémente les hacks RetroArch pour devices spéciaux :
 * - NVIDIA Shield (TV, Portable, Gamepad) - Groupement Virtual + Controller
 * - Xperia Play - Groupement 2 HID devices
 * - GPD XD - Groupement Virtual + Controller
 * - Archos Gamepad - Groupement 2 HID devices
 * - Amazon Fire TV - Mapping remote spécial
 * 
 * Compatible avec RetroArch android_input.c:handle_hotplug() (lignes 1057-1363)
 * 
 * Références:
 * - android_input.c:1041-1363 (handle_hotplug avec tous les hacks)
 */
class DeviceHacksManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceHacksManager"
    }
    
    /**
     * Résultat d'un hack de device
     */
    data class DeviceHackResult(
        val modifiedDeviceName: String,      // Nom modifié pour autoconfig
        val shouldGroupWithDeviceId: Int? = null,  // ID du device à grouper avec (si applicable)
        val forcePort: Int? = null,          // Port forcé (0 pour remotes, etc.)
        val shouldSkip: Boolean = false      // true si ce device doit être ignoré (grouper avec autre)
    )
    
    /**
     * Appliquer les hacks spéciaux pour un device détecté
     * 
     * @param device InputDevice Android
     * @param deviceName Nom du device (depuis InputDevice.getName())
     * @param vendorId Vendor ID du device
     * @param productId Product ID du device
     * @param deviceId Device ID Android
     * @param padsConnected Nombre de pads déjà connectés
     * @param padId1 ID du premier pad groupé (pour multi-device hacks)
     * @param padId2 ID du deuxième pad groupé (pour multi-device hacks)
     * @return DeviceHackResult avec les modifications à appliquer
     */
    fun applyDeviceHacks(
        device: InputDevice?,
        deviceName: String,
        vendorId: Int,
        productId: Int,
        deviceId: Int,
        padsConnected: Int,
        padId1: Int? = null,
        padId2: Int? = null
    ): DeviceHackResult {
        val deviceModel = Build.MODEL
        
        // NVIDIA Shield Android TV (lignes 1082-1123)
        if (deviceModel.contains("SHIELD Android TV", ignoreCase = true) &&
            (deviceName.contains("Virtual", ignoreCase = true) ||
             deviceName.contains("NVIDIA Corporation NVIDIA Controller v01.0", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] Special device detected: $deviceModel")
            
            // Si remote ou virtual controller déjà mappé, le remplacer
            // (géré par AutoconfigManager, on retourne juste le nom modifié)
            if (deviceName.contains("Virtual", ignoreCase = true) && padsConnected == 0) {
                return DeviceHackResult(
                    modifiedDeviceName = "SHIELD Virtual Controller",
                    forcePort = 0
                )
            } else {
                return DeviceHackResult(
                    modifiedDeviceName = "NVIDIA SHIELD Controller"
                )
            }
        }
        
        // NVIDIA Shield Portable (lignes 1125-1142)
        if (deviceModel.contains("SHIELD", ignoreCase = true) &&
            (deviceName.contains("Virtual", ignoreCase = true) ||
             deviceName.contains("gpio", ignoreCase = true) ||
             deviceName.contains("NVIDIA Corporation NVIDIA Controller v01.01", ignoreCase = true) ||
             deviceName.contains("NVIDIA Corporation NVIDIA Controller v01.02", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] Special device detected: $deviceModel")
            
            // Premier device → padId1, deuxième → padId2
            // Si padId2 > 0, on skip (déjà groupé)
            if (padId2 != null && padId2 > 0) {
                return DeviceHackResult(
                    modifiedDeviceName = deviceName,
                    shouldSkip = true
                )
            }
            
            return DeviceHackResult(
                modifiedDeviceName = "NVIDIA SHIELD Portable",
                shouldGroupWithDeviceId = padId1
            )
        }
        
        // NVIDIA Shield Gamepad (lignes 1144-1161)
        if (deviceModel.contains("SHIELD", ignoreCase = true) &&
            (deviceName.contains("Virtual", ignoreCase = true) ||
             deviceName.contains("gpio", ignoreCase = true) ||
             deviceName.contains("NVIDIA Corporation NVIDIA Controller v01.03", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] Special device detected: $deviceModel")
            
            // Si controller v01.03 et premier pad → padId1
            // Si Virtual/gpio et padId1 existe → grouper avec padId1
            if (deviceName.contains("NVIDIA Corporation NVIDIA Controller v01.03", ignoreCase = true) &&
                padsConnected == 0) {
                return DeviceHackResult(
                    modifiedDeviceName = "NVIDIA SHIELD Gamepad",
                    shouldGroupWithDeviceId = deviceId  // Stocker comme padId1
                )
            } else if ((deviceName.contains("Virtual", ignoreCase = true) ||
                        deviceName.contains("gpio", ignoreCase = true)) &&
                       padId1 != null) {
                return DeviceHackResult(
                    modifiedDeviceName = "NVIDIA SHIELD Gamepad",
                    shouldGroupWithDeviceId = padId1,
                    shouldSkip = true  // Grouper avec padId1, ne pas créer de nouveau pad
                )
            }
            
            return DeviceHackResult(
                modifiedDeviceName = "NVIDIA SHIELD Gamepad"
            )
        }
        
        // GPD XD (lignes 1171-1193)
        if (deviceModel.contains("XD", ignoreCase = true) &&
            (deviceName.contains("Virtual", ignoreCase = true) ||
             deviceName.contains("rk29-keypad", ignoreCase = true) ||
             deviceName.contains("Playstation3", ignoreCase = true) ||
             deviceName.contains("XBOX", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] Special device detected: $deviceModel")
            
            // Premier device → padId1, deuxième → padId2
            if (padId2 != null && padId2 > 0) {
                return DeviceHackResult(
                    modifiedDeviceName = deviceName,
                    shouldSkip = true
                )
            }
            
            return DeviceHackResult(
                modifiedDeviceName = "GPD XD",
                forcePort = 0,
                shouldGroupWithDeviceId = padId1
            )
        }
        
        // Xperia Play (lignes 1195-1226)
        if ((deviceModel.startsWith("R800", ignoreCase = true) ||
             deviceModel.contains("Xperia Play", ignoreCase = true) ||
             deviceModel.contains("Play", ignoreCase = true) ||
             deviceModel.contains("SO-01D", ignoreCase = true)) ||
            (deviceName.contains("keypad-game-zeus", ignoreCase = true) ||
             deviceName.contains("keypad-zeus", ignoreCase = true) ||
             deviceName.contains("Android Gamepad", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] Special device detected: $deviceModel")
            
            // Premier device → padId1, deuxième → padId2
            if (padId2 != null && padId2 > 0) {
                return DeviceHackResult(
                    modifiedDeviceName = deviceName,
                    shouldSkip = true
                )
            }
            
            return DeviceHackResult(
                modifiedDeviceName = "XPERIA Play",
                forcePort = 0,
                shouldGroupWithDeviceId = padId1
            )
        }
        
        // Archos Gamepad (lignes 1228-1249)
        if (deviceModel.contains("ARCHOS GAMEPAD", ignoreCase = true) &&
            (deviceName.contains("joy_key", ignoreCase = true) ||
             deviceName.contains("joystick", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] ARCHOS GAMEPAD Detected: $deviceModel")
            
            // Premier device → padId1, deuxième → padId2
            if (padId2 != null && padId2 > 0) {
                return DeviceHackResult(
                    modifiedDeviceName = deviceName,
                    shouldSkip = true
                )
            }
            
            return DeviceHackResult(
                modifiedDeviceName = "ARCHOS GamePad",
                forcePort = 0,
                shouldGroupWithDeviceId = padId1
            )
        }
        
        // Amazon Fire TV & Fire stick (lignes 1251-1282)
        if (deviceModel.startsWith("AFT", ignoreCase = true) &&
            (deviceModel.contains("AFTB", ignoreCase = true) ||
             deviceModel.contains("AFTT", ignoreCase = true) ||
             deviceModel.contains("AFTS", ignoreCase = true) ||
             deviceModel.contains("AFTM", ignoreCase = true) ||
             deviceModel.contains("AFTRS", ignoreCase = true))) {
            
            Log.i(TAG, "[Android] Special device detected: $deviceModel")
            
            // Remote toujours mappé sur port 0
            if (deviceName.contains("Amazon Fire TV Remote", ignoreCase = true)) {
                return DeviceHackResult(
                    modifiedDeviceName = deviceName,
                    forcePort = 0
                )
            }
            
            // Si remote déjà mappé et gamepad arrive, remplacer
            // (géré par AutoconfigManager)
            return DeviceHackResult(
                modifiedDeviceName = deviceName
            )
        }
        
        // Autres remotes (lignes 1288-1295)
        if (deviceName.contains("Amazon Fire TV Remote", ignoreCase = true) ||
            deviceName.contains("Nexus Remote", ignoreCase = true) ||
            deviceName.contains("SHIELD Remote", ignoreCase = true)) {
            return DeviceHackResult(
                modifiedDeviceName = deviceName,
                forcePort = 0
            )
        }
        
        // iControlPad (ligne 1297-1298)
        if (deviceName.contains("iControlPad-", ignoreCase = true)) {
            return DeviceHackResult(
                modifiedDeviceName = "iControlPad HID Joystick profile"
            )
        }
        
        // TTT THT Arcade (lignes 1300-1306)
        if (deviceName.contains("TTT THT Arcade console 2P USB Play", ignoreCase = true)) {
            val port = padsConnected
            val name = if (port == 0) {
                "TTT THT Arcade (User 1)"
            } else if (port == 1) {
                "TTT THT Arcade (User 2)"
            } else {
                deviceName
            }
            return DeviceHackResult(
                modifiedDeviceName = name
            )
        }
        
        // MOGA (ligne 1307-1308)
        if (deviceName.contains("MOGA", ignoreCase = true)) {
            return DeviceHackResult(
                modifiedDeviceName = "Moga IME"
            )
        }
        
        // Pas de hack spécifique, retourner le nom original
        return DeviceHackResult(
            modifiedDeviceName = deviceName
        )
    }
    
    /**
     * Obtenir le device ID à utiliser pour un device groupé
     * Compatible avec RetroArch android_input_get_id() (lignes 1365-1371)
     * 
     * @param deviceId Device ID original
     * @param padId1 Premier pad groupé
     * @param padId2 Deuxième pad groupé
     * @return Device ID à utiliser (padId1 si deviceId == padId2)
     */
    fun getGroupedDeviceId(deviceId: Int, padId1: Int?, padId2: Int?): Int {
        if (padId2 != null && deviceId == padId2) {
            return padId1 ?: deviceId
        }
        return deviceId
    }
}

