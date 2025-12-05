package com.retroplay.input

import android.content.Context
import android.util.Log
import android.view.InputDevice
import com.swordfish.libretrodroid.LibretroDroid
import com.retroplay.overlay.models.RetroPadIds
import java.io.File
import java.io.InputStream

/**
 * Gestionnaire d'autoconfiguration RetroArch pour gamepads Android
 * 
 * Implémente le système d'autoconfig RetroArch selon les spécifications officielles :
 * - Détection automatique des devices connectés (VID/PID/Name)
 * - Chargement des fichiers .cfg depuis assets ou storage
 * - Matching par VID/PID (prioritaire) puis par name
 * - Support alternatives (input_device_alt1, input_vendor_id_alt1, etc.)
 * 
 * Références:
 * - controller-autoconfiguration.md (912 lignes)
 * - android_input.c:1041-1363 (handle_hotplug)
 * - 209 fichiers .cfg Android disponibles
 */
class AutoconfigManager(private val context: Context) {
    
    private val deviceHacksManager = DeviceHacksManager(context)
    
    companion object {
        private const val TAG = "AutoconfigManager"
        
        // Répertoire autoconfig dans assets (à créer si nécessaire)
        private const val ASSETS_AUTOCONFIG_ROOT = "autoconfig/android"
        
        // Répertoire autoconfig dans storage (prioritaire pour custom configs)
        private const val STORAGE_AUTOCONFIG_ROOT = "/storage/emulated/0/RetroArch/autoconfig/android"
        
        // Répertoire autoconfig alternatif (pour retroplay)
        private const val RETROPLAY_AUTOCONFIG_ROOT = "/storage/emulated/0/RetroPlay-Data/autoconfig/android"
    }
    
    /**
     * Configuration d'un device parsée depuis un fichier .cfg
     */
    data class DeviceConfig(
        val deviceName: String,
        val vendorId: Int? = null,
        val productId: Int? = null,
        val displayName: String? = null,
        val buttonMappings: Map<String, String> = emptyMap(),
        val axisMappings: Map<String, String> = emptyMap(),
        val labels: Map<String, String> = emptyMap(),
        val alternatives: List<DeviceConfig> = emptyList()
    )
    
    /**
     * Informations d'un device connecté
     */
    data class ConnectedDevice(
        val deviceId: Int,
        val name: String,
        val vendorId: Int,
        val productId: Int,
        val sources: Int
    )
    
    /**
     * Détecter tous les gamepads connectés
     * 
     * @return Liste des devices avec SOURCE_GAMEPAD
     */
    fun detectConnectedGamepads(): List<ConnectedDevice> {
        val gamepads = mutableListOf<ConnectedDevice>()
        
        try {
            val deviceIds = InputDevice.getDeviceIds()
            
            for (deviceId in deviceIds) {
                val device = InputDevice.getDevice(deviceId) ?: continue
                
                // Vérifier si c'est un gamepad
                if ((device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
                    val vendorId = device.vendorId
                    val productId = device.productId
                    val name = device.name ?: "Unknown Device"
                    
                    Log.d(TAG, "Gamepad detected: name='$name' vid=$vendorId pid=$productId deviceId=$deviceId")
                    
                    gamepads.add(
                        ConnectedDevice(
                            deviceId = deviceId,
                            name = name,
                            vendorId = vendorId,
                            productId = productId,
                            sources = device.sources
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting gamepads", e)
        }
        
        Log.i(TAG, "Detected ${gamepads.size} gamepad(s)")
        return gamepads
    }
    
    /**
     * Trouver le fichier .cfg correspondant à un device
     * 
     * Stratégie de matching (selon controller-autoconfiguration.md):
     * 1. Appliquer hacks spéciaux devices (Shield, Xperia Play, etc.)
     * 2. VID/PID exact match (prioritaire)
     * 3. VID/PID avec alternatives
     * 4. Name match (fallback)
     * 
     * @param device Device connecté
     * @param padsConnected Nombre de pads déjà connectés (pour hacks multi-device)
     * @param padId1 ID du premier pad groupé (pour hacks multi-device)
     * @param padId2 ID du deuxième pad groupé (pour hacks multi-device)
     * @return Configuration trouvée ou null
     */
    fun findConfigForDevice(
        device: ConnectedDevice,
        padsConnected: Int = 0,
        padId1: Int? = null,
        padId2: Int? = null
    ): DeviceConfig? {
        Log.d(TAG, "Searching config for device: name='${device.name}' vid=${device.vendorId} pid=${device.productId}")
        
        // 0. Appliquer hacks spéciaux devices (compatible RetroArch handle_hotplug)
        val hackResult = deviceHacksManager.applyDeviceHacks(
            device = null,  // InputDevice non disponible ici, utiliser device.name
            deviceName = device.name,
            vendorId = device.vendorId,
            productId = device.productId,
            deviceId = device.deviceId,
            padsConnected = padsConnected,
            padId1 = padId1,
            padId2 = padId2
        )
        
        // Si le device doit être ignoré (grouper avec autre), retourner null
        if (hackResult.shouldSkip) {
            Log.d(TAG, "Device skipped (grouped with another): ${device.name}")
            return null
        }
        
        // Utiliser le nom modifié par le hack pour la recherche
        val searchName = hackResult.modifiedDeviceName
        
        Log.d(TAG, "Device hack applied: '${device.name}' -> '$searchName'")
        
        // 1. Chercher par VID/PID (prioritaire pour Android)
        val configByVidPid = findConfigByVidPid(device.vendorId, device.productId)
        if (configByVidPid != null) {
            Log.i(TAG, "Found config by VID/PID: ${configByVidPid.deviceName}")
            return configByVidPid
        }
        
        // 2. Chercher par name modifié (après hack)
        val configByName = findConfigByName(searchName)
        if (configByName != null) {
            Log.i(TAG, "Found config by name (hacked): ${configByName.deviceName}")
            return configByName
        }
        
        // 3. Chercher par name original (fallback)
        val configByOriginalName = findConfigByName(device.name)
        if (configByOriginalName != null) {
            Log.i(TAG, "Found config by original name: ${configByOriginalName.deviceName}")
            return configByOriginalName
        }
        
        Log.w(TAG, "No config found for device: ${device.name} (searched as: $searchName)")
        return null
    }
    
    /**
     * Chercher configuration par VID/PID
     * 
     * CORRECTION: Retourner le principal avec les mappings partagés si une alternative match
     * (compatible RetroArch task_autodetect.c:input_autoconfigure_get_config_file_affinity)
     */
    private fun findConfigByVidPid(vendorId: Int, productId: Int): DeviceConfig? {
        // Lister tous les fichiers .cfg disponibles
        val configFiles = listAllConfigFiles()
        
        for (configFileRef in configFiles) {
            val config = parseConfigFile(configFileRef)
            if (config != null) {
                // Vérifier VID/PID principal
                if (config.vendorId == vendorId && config.productId == productId) {
                    return config
                }
                
                // Vérifier alternatives - retourner le principal avec mappings partagés
                for (alt in config.alternatives) {
                    if (alt.vendorId == vendorId && alt.productId == productId) {
                        // Retourner le principal (avec mappings) au lieu de l'alternative seule
                        // Les mappings sont déjà hérités dans les alternatives lors du parsing
                        return config
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Chercher configuration par name
     * 
     * CORRECTION: Retourner le principal avec les mappings partagés si une alternative match
     * (compatible RetroArch task_autodetect.c:input_autoconfigure_get_config_file_affinity)
     */
    private fun findConfigByName(deviceName: String): DeviceConfig? {
        val configFiles = listAllConfigFiles()
        
        for (configFileRef in configFiles) {
            val config = parseConfigFile(configFileRef)
            if (config != null) {
                // Match exact du name
                if (config.deviceName.equals(deviceName, ignoreCase = true)) {
                    return config
                }
                
                // Match avec alternatives - retourner le principal avec mappings partagés
                for (alt in config.alternatives) {
                    if (alt.deviceName.equals(deviceName, ignoreCase = true)) {
                        // Retourner le principal (avec mappings) au lieu de l'alternative seule
                        // Les mappings sont déjà hérités dans les alternatives lors du parsing
                        return config
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Représentation d'un fichier config (storage ou assets)
     */
    private data class ConfigFileRef(
        val name: String,
        val isAsset: Boolean,
        val file: File? = null,
        val assetPath: String? = null
    )
    
    /**
     * Lister tous les fichiers .cfg disponibles (assets + storage)
     */
    private fun listAllConfigFiles(): List<ConfigFileRef> {
        val configFiles = mutableListOf<ConfigFileRef>()
        val seenNames = mutableSetOf<String>()
        
        // 1. Chercher dans storage (prioritaire pour custom configs)
        val storageDir = File(STORAGE_AUTOCONFIG_ROOT)
        if (storageDir.exists() && storageDir.isDirectory) {
            storageDir.listFiles()?.filter { it.extension == "cfg" }?.forEach { file ->
                if (!seenNames.contains(file.name)) {
                    configFiles.add(ConfigFileRef(file.name, isAsset = false, file = file))
                    seenNames.add(file.name)
                }
            }
        }
        
        // 2. Chercher dans RetroPlay storage (alternatif)
        val retroplayDir = File(RETROPLAY_AUTOCONFIG_ROOT)
        if (retroplayDir.exists() && retroplayDir.isDirectory) {
            retroplayDir.listFiles()?.filter { it.extension == "cfg" }?.forEach { file ->
                if (!seenNames.contains(file.name)) {
                    configFiles.add(ConfigFileRef(file.name, isAsset = false, file = file))
                    seenNames.add(file.name)
                }
            }
        }
        
        // 3. Chercher dans assets (fallback, seulement si pas déjà dans storage)
        try {
            val assetsFiles = context.assets.list(ASSETS_AUTOCONFIG_ROOT) ?: emptyArray()
            for (fileName in assetsFiles) {
                if (fileName.endsWith(".cfg") && !seenNames.contains(fileName)) {
                    val assetPath = "$ASSETS_AUTOCONFIG_ROOT/$fileName"
                    configFiles.add(ConfigFileRef(fileName, isAsset = true, assetPath = assetPath))
                    seenNames.add(fileName)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Assets autoconfig directory not found or empty", e)
        }
        
        Log.d(TAG, "Found ${configFiles.size} config files total (${configFiles.count { !it.isAsset }} from storage, ${configFiles.count { it.isAsset }} from assets)")
        return configFiles
    }
    
    /**
     * Parser un fichier .cfg RetroArch
     * 
     * Format selon controller-autoconfiguration.md:
     * - input_driver = "android"
     * - input_device = "Device Name"
     * - input_vendor_id = "1118"
     * - input_product_id = "654"
     * - input_*_btn = "96" (boutons)
     * - input_*_axis = "+0" (axes)
     * - input_*_label = "Label" (labels)
     * - Support alternatives (input_device_alt1, input_vendor_id_alt1, etc.)
     */
    private fun parseConfigFile(configFileRef: ConfigFileRef): DeviceConfig? {
        try {
            val lines = if (!configFileRef.isAsset && configFileRef.file != null) {
                // Fichier dans storage
                configFileRef.file.readLines()
            } else if (configFileRef.isAsset && configFileRef.assetPath != null) {
                // Fichier dans assets
                context.assets.open(configFileRef.assetPath).bufferedReader().use { it.readLines() }
            } else {
                Log.e(TAG, "Invalid config file reference: $configFileRef")
                return null
            }
            
            val config = parseConfigLines(lines)
            // Vérifier que la config est valide (au moins deviceName doit être non vide)
            if (config.deviceName.isEmpty()) {
                Log.w(TAG, "Config file ${configFileRef.name} has empty deviceName, skipping")
                return null
            }
            
            return config
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config file: ${configFileRef.name}", e)
            return null
        }
    }
    
    /**
     * Parser les lignes d'un fichier .cfg
     */
    private fun parseConfigLines(lines: List<String>): DeviceConfig {
        var deviceName = ""
        var vendorId: Int? = null
        var productId: Int? = null
        var displayName: String? = null
        val buttonMappings = mutableMapOf<String, String>()
        val axisMappings = mutableMapOf<String, String>()
        val labels = mutableMapOf<String, String>()
        val alternatives = mutableListOf<DeviceConfig>()
        
        var currentAltIndex = 0
        var altDeviceName = ""
        var altVendorId: Int? = null
        var altProductId: Int? = null
        var altDisplayName: String? = null
        // CORRECTION: Les mappings ne sont pas parsés pour les alternatives (partagés avec principal)
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }
            
            val parts = trimmed.split("=", limit = 2)
            if (parts.size != 2) {
                continue
            }
            
            val key = parts[0].trim()
            val value = parts[1].trim().trim('"')
            
            // Détecter si c'est une alternative (_alt1, _alt2, etc.)
            val isAlternative = key.contains("_alt")
            val altMatch = Regex("_alt(\\d+)").find(key)
            val altNum = altMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            if (isAlternative && altNum > 0) {
                // Alternative détectée
                if (currentAltIndex != altNum) {
                    // Nouvelle alternative, sauvegarder la précédente
                    // CORRECTION: Les mappings sont hérités du principal (partagés)
                    if (currentAltIndex > 0 && altDeviceName.isNotEmpty()) {
                        alternatives.add(
                            DeviceConfig(
                                deviceName = altDeviceName,
                                vendorId = altVendorId,
                                productId = altProductId,
                                displayName = altDisplayName,
                                // Mappings hérités du principal (seront copiés après parsing complet)
                                buttonMappings = emptyMap(),
                                axisMappings = emptyMap(),
                                labels = emptyMap()
                            )
                        )
                    }
                    currentAltIndex = altNum
                    altDeviceName = ""
                    altVendorId = null
                    altProductId = null
                    altDisplayName = null
                }
                
                // Parser champ alternative
                // CORRECTION: Les mappings (input_*_btn, input_*_axis, input_*_label) n'ont PAS de _alt suffix
                // Ils sont partagés entre principal et alternatives (compatible RetroArch task_autodetect.c)
                val cleanKey = key.replace("_alt$altNum", "")
                when {
                    cleanKey == "input_device" -> altDeviceName = value
                    cleanKey == "input_device_display_name" -> altDisplayName = value
                    cleanKey == "input_vendor_id" -> altVendorId = value.toIntOrNull()
                    cleanKey == "input_product_id" -> altProductId = value.toIntOrNull()
                    // Les mappings sont partagés, pas de parsing avec _alt suffix
                }
            } else {
                // Champ principal
                when (key) {
                    "input_device" -> deviceName = value
                    "input_device_display_name" -> displayName = value
                    "input_vendor_id" -> vendorId = value.toIntOrNull()
                    "input_product_id" -> productId = value.toIntOrNull()
                }
                
                // Mappings boutons/axes/labels
                when {
                    key.endsWith("_btn") -> buttonMappings[key] = value
                    key.endsWith("_axis") -> axisMappings[key] = value
                    key.endsWith("_label") -> labels[key] = value
                }
            }
        }
        
        // Sauvegarder la dernière alternative si présente
        // CORRECTION: Les mappings sont hérités du principal (partagés)
        if (currentAltIndex > 0 && altDeviceName.isNotEmpty()) {
            alternatives.add(
                DeviceConfig(
                    deviceName = altDeviceName,
                    vendorId = altVendorId,
                    productId = altProductId,
                    displayName = altDisplayName,
                    // Mappings hérités du principal (seront copiés après)
                    buttonMappings = emptyMap(),
                    axisMappings = emptyMap(),
                    labels = emptyMap()
                )
            )
        }
        
        // CORRECTION: Hériter les mappings du principal vers les alternatives (compatible RetroArch)
        // Les mappings sont partagés entre principal et alternatives
        val alternativesWithMappings = alternatives.map { alt ->
            alt.copy(
                buttonMappings = buttonMappings,
                axisMappings = axisMappings,
                labels = labels
            )
        }
        
        return DeviceConfig(
            deviceName = deviceName,
            vendorId = vendorId,
            productId = productId,
            displayName = displayName,
            buttonMappings = buttonMappings,
            axisMappings = axisMappings,
            labels = labels,
            alternatives = alternativesWithMappings
        )
    }
    
    /**
     * Parser une valeur de mapping depuis .cfg (compatible RetroArch input_config_parse_joy_button)
     * Formats supportés:
     * - "96" → AKEYCODE numérique (strtoull)
     * - "h0up" → Hat direction (HAT_MAP)
     * - "nul" → NO_BTN (pas de mapping)
     * 
     * @param value Valeur depuis .cfg (ex: "96", "h0up", "nul")
     * @return AKEYCODE (Int) ou null si "nul" ou invalide
     */
    private fun parseButtonValue(value: String): Int? {
        val trimmed = value.trim()
        
        // "nul" → NO_BTN (pas de mapping)
        if (trimmed.equals("nul", ignoreCase = true) || trimmed.isEmpty()) {
            return null
        }
        
        // "h0up" → Hat direction (HAT_MAP) - TODO: Support hats si nécessaire
        if (trimmed.startsWith("h", ignoreCase = true)) {
            // Pour l'instant, on ignore les hats (peu utilisés sur Android)
            Log.w(TAG, "Hat mapping not supported yet: $trimmed")
            return null
        }
        
        // "96" → AKEYCODE numérique (strtoull)
        return trimmed.toIntOrNull()
    }
    
    /**
     * Convertir nom de bouton .cfg → RetroPad ID
     * Ex: "input_a_btn" → RetroPadIds.JOYPAD_A
     * Compatible RetroArch input_config_translate_str_to_bind_id()
     */
    private fun getRetroPadIdFromButtonName(buttonName: String): Int? {
        // Extraire le nom du bouton (ex: "input_a_btn" → "a")
        val name = buttonName
            .removePrefix("input_")
            .removeSuffix("_btn")
            .lowercase()
        
        // Mapping nom → RetroPad ID (compatible RetroArch)
        return when (name) {
            "a" -> RetroPadIds.JOYPAD_A
            "b" -> RetroPadIds.JOYPAD_B
            "x" -> RetroPadIds.JOYPAD_X
            "y" -> RetroPadIds.JOYPAD_Y
            "l" -> RetroPadIds.JOYPAD_L
            "r" -> RetroPadIds.JOYPAD_R
            "l2" -> RetroPadIds.JOYPAD_L2
            "r2" -> RetroPadIds.JOYPAD_R2
            "l3" -> RetroPadIds.JOYPAD_L3
            "r3" -> RetroPadIds.JOYPAD_R3
            "start" -> RetroPadIds.JOYPAD_START
            "select" -> RetroPadIds.JOYPAD_SELECT
            "up" -> RetroPadIds.JOYPAD_UP
            "down" -> RetroPadIds.JOYPAD_DOWN
            "left" -> RetroPadIds.JOYPAD_LEFT
            "right" -> RetroPadIds.JOYPAD_RIGHT
            else -> {
                Log.w(TAG, "Unknown button name: $name (from $buttonName)")
                null
            }
        }
    }
    
    /**
     * Appliquer une configuration à un device
     * Compatible RetroArch input_config_set_autoconfig_binds()
     * 
     * @param device Device connecté
     * @param config Configuration parsée depuis .cfg
     * @param port Port du controller (0-3)
     */
    fun applyConfig(device: ConnectedDevice, config: DeviceConfig, port: Int = 0) {
        Log.i(TAG, "Applying config '${config.deviceName}' to device '${device.name}' on port $port")
        
        // Clear existing mappings for this port
        LibretroDroid.clearAutoconfigMappings(port)
        
        // Apply button mappings
        var appliedCount = 0
        for ((buttonName, value) in config.buttonMappings) {
            val retroPadId = getRetroPadIdFromButtonName(buttonName)
            val keyCode = parseButtonValue(value)
            
            if (retroPadId != null && keyCode != null) {
                LibretroDroid.setAutoconfigMapping(port, retroPadId, keyCode)
                appliedCount++
                Log.d(TAG, "Mapped: $buttonName = $value → RetroPad ID=$retroPadId, AKEYCODE=$keyCode")
            } else {
                if (retroPadId == null) {
                    Log.w(TAG, "Unknown button name: $buttonName")
                }
                if (keyCode == null) {
                    Log.d(TAG, "Skipping button mapping: $buttonName = $value (nul or invalid)")
                }
            }
        }
        
        Log.i(TAG, "Applied $appliedCount button mappings for port $port")
        
        // TODO: Apply axis mappings (input_*_axis) - nécessite parsing "+0", "-0", etc.
        // Pour l'instant, on se concentre sur les boutons
    }
}

