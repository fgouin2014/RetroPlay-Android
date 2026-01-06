package com.retroplay.viewmodels

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.retroplay.config.RetroPlayConfigManager
import com.retroplay.database.DatabaseManager

// ... (existing imports)

    // ...

import com.retroplay.CoreVariable

/**
 * ViewModel for RetroArchEmulatorActivity.
 * Holds UI state to survive configuration changes and separate concerns.
 */
class RetroArchEmulatorViewModel : ViewModel() {

    // UI Visibility States
    val showMainMenu = mutableStateOf(false)
    val showGamePadSettings = mutableStateOf(false)
    val showAdvancedOverlaySettings = mutableStateOf(false)
    val showQuickMenu = mutableStateOf(false)
    val overlaysVisible = mutableStateOf(true)
    
    // Dialog States
    val showCoreErrorDialog = mutableStateOf(false)
    val showCoreSelectorFromError = mutableStateOf(false)
    val showCoreChangeConfirmDialog = mutableStateOf(false)
    
    // Turbo menus
    val showTurboSettings = mutableStateOf(false)
    val showQuickTurbo = mutableStateOf(false)
    
    // États pour DIP Switches, Core Options, Game Info et Cheats
    val showDipSwitchDialog = mutableStateOf(false)
    val showCoreOptionsDialog = mutableStateOf(false)
    val showGameInfoDialog = mutableStateOf(false)
    
    // État pour le dialog des extensions N64
    val showN64ExtensionsDialog = mutableStateOf(false)
    val n64ExtensionsInfo = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    
    val showCheatsDialog = mutableStateOf(false)
    val showSmartConfigDialog = mutableStateOf(false)
    val showPerGameConfigDialog = mutableStateOf(false)
    val showDiskSwapperDialog = mutableStateOf(false)
    
    // Core Error State
    var failedCoreName = mutableStateOf("")
    var coreChangeConfirmMessage = mutableStateOf("")
    
    // Per-Game Config State
    var perGameConfigCRC: String? = null
    var perGameConfigGameName = mutableStateOf("")
    
    // Core Variables (DIP Switches / Core Options)
    val allCoreVariables = mutableStateListOf<CoreVariable>()
    val dipSwitches = mutableStateListOf<CoreVariable>()
    val coreOptions = mutableStateListOf<CoreVariable>()
    
    // Disk Swapper State
    val availableDisksState = mutableIntStateOf(0)
    val currentDiskState = mutableIntStateOf(0)
    
    // Controller Configuration Flag
    var controllerConfigurationDone = false

    // Config ID State (Resolved Identity)
    var gameCRC: String? = null
    var customConfigId: String? = null

    /**
     * Resolves the game identity using all available sources (Intent, Serial, Database Name).
     * This centralizes the logic to ensure consistent behavior across the app.
     */
    fun resolveGameIdentity(
        initialGameName: String,
        initialGameCRC: String?,
        console: String,
        romPath: String,
        overrideConfigId: String? = null,
        overridePsxSerial: String? = null
    ): String? {
        // 1. Start with what we have
        gameCRC = initialGameCRC
        
        // 2. If CRC is missing (e.g. Save State), try Name Fallback in DB
        if (gameCRC == null) {
            val gameInfo = DatabaseManager.lookupGameByName(initialGameName, console)
            if (gameInfo != null) {
                gameCRC = gameInfo.crc
                // Note: updateConfigValue now requires console parameter
                // This is a debug value, skip for now
                // RetroPlayConfigManager.updateConfigValue(console, "last_resolved_crc", gameInfo.crc) // Optional debug
            }
        }
        
        // 3. Determine Custom Config ID (for Configuration Storage)
        customConfigId = gameCRC // Default to CRC (or null)
        
        // Priority 1: Use passed Overrides (from GameDetailsActivity)
        if (overridePsxSerial != null) {
             android.util.Log.i("RetroArchViewModel", "[Identity] Using passed PSX Serial: $overridePsxSerial")
             customConfigId = overridePsxSerial
             // Also store it as if extracted locally so logic works elsewhere
             // (No specific field for serial here aside from customConfigId)
        } else if (overrideConfigId != null) {
             android.util.Log.i("RetroArchViewModel", "[Identity] Using passed Config ID: $overrideConfigId")
             customConfigId = overrideConfigId
        } else {
             // Priority 2: Extract PSX Serial locally (if not passed)
            if (console == "psx" || console == "ps1" || console == "playstation") {
                 val serial = com.retroplay.util.PsxSerialExtractor.extractSerial(romPath)
                 if (serial != null) {
                     android.util.Log.i("RetroArchViewModel", "[Identity] Extracted Serial locally: $serial")
                     customConfigId = serial
                 }
            }
        }
        
        // Priority 3: Fallback to Name if still null (ensures Config is always usable)
        if (customConfigId == null) {
            val safeName = initialGameName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            android.util.Log.w("RetroArchViewModel", "[Identity] No ID found. Using Name Fallback: $safeName")
            customConfigId = safeName
        }
        
        return customConfigId
    }

    fun toggleQuickMenu() {
        showQuickMenu.value = !showQuickMenu.value
    }

    fun toggleOverlaysVisibility() {
        overlaysVisible.value = !overlaysVisible.value
    }

    fun resetState() {
        showMainMenu.value = false
        showGamePadSettings.value = false
        showAdvancedOverlaySettings.value = false
        showQuickMenu.value = false
        showCoreErrorDialog.value = false
        showTurboSettings.value = false
        showQuickTurbo.value = false
        showDipSwitchDialog.value = false
        showCoreOptionsDialog.value = false
        showGameInfoDialog.value = false
        showN64ExtensionsDialog.value = false
        showCheatsDialog.value = false
        showSmartConfigDialog.value = false
        showPerGameConfigDialog.value = false
        showDiskSwapperDialog.value = false
    }
}
