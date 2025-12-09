package com.retroplay.viewmodels

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.retroplay.CoreVariable

/**
 * ViewModel for NativeComposeEmulatorActivity.
 * Holds UI state to survive configuration changes and separate concerns.
 */
class NativeEmulatorViewModel : ViewModel() {

    // UI Visibility States
    val showMainMenu = mutableStateOf(false)
    val showGamePadSettings = mutableStateOf(false)
    val showAdvancedRadialSettings = mutableStateOf(false)
    val showQuickMenu = mutableStateOf(false)
    val overlaysVisible = mutableStateOf(true)
    val showGameInfoDialog = mutableStateOf(false)
    val showDiskSwapperDialog = mutableStateOf(false)
    val showCoreErrorDialog = mutableStateOf(false)
    val showCoreSelectorFromError = mutableStateOf(false)
    val showCoreChangeConfirmDialog = mutableStateOf(false)
    
    // Core Error State
    var failedCoreName = mutableStateOf("")
    var coreChangeConfirmMessage = mutableStateOf("")

    // Quick Actions Bar
    val quickActionsBarVisible = mutableStateOf(true)
    val quickActionsBarAutoHideEnabled = mutableStateOf(true)
    val quickActionsBarAutoHideTimer = mutableStateOf(0L)

    // Emulation State
    val isFastForwardActive = mutableStateOf(false)
    val audioMuted = mutableStateOf(false)
    var fastForwardRatio = mutableStateOf(2f)

    // Core Variables (DIP Switches / Core Options)
    val showDipSwitchDialog = mutableStateOf(false)
    val showCoreOptionsDialog = mutableStateOf(false)
    
    val allCoreVariables = mutableStateListOf<CoreVariable>()
    val dipSwitches = mutableStateListOf<CoreVariable>()
    val coreOptions = mutableStateListOf<CoreVariable>()

    // Disk Swapper State
    val availableDisksState = mutableIntStateOf(0)
    val currentDiskState = mutableIntStateOf(0)

    // Turbo States
    val showTurboSettings = mutableStateOf(false)
    val showQuickTurbo = mutableStateOf(false)

    // Smart Config & Per-Game Config States
    val showSmartConfigDialog = mutableStateOf(false)
    val showPerGameConfigDialog = mutableStateOf(false)

    fun toggleQuickActionsBar() {
        quickActionsBarVisible.value = !quickActionsBarVisible.value
    }

    fun toggleFastForward() {
        isFastForwardActive.value = !isFastForwardActive.value
    }
    
    fun toggleAudioMute() {
        audioMuted.value = !audioMuted.value
    }

    fun resetState() {
        showMainMenu.value = false
        showGamePadSettings.value = false
        // ... reset logic if needed
    }

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
            val gameInfo = com.retroplay.database.DatabaseManager.lookupGameByName(initialGameName, console)
            if (gameInfo != null) {
                gameCRC = gameInfo.crc
                com.retroplay.config.RetroPlayConfigManager.updateConfigValue("last_resolved_crc", gameInfo.crc)
            }
        }
        
        // 3. Determine Custom Config ID (for Configuration Storage)
        customConfigId = gameCRC // Default to CRC (or null)
        
        // Priority 1: Use passed Overrides (from GameDetailsActivity)
        if (overridePsxSerial != null) {
             android.util.Log.i("NativeEmulatorViewModel", "[Identity] Using passed PSX Serial: $overridePsxSerial")
             customConfigId = overridePsxSerial
        } else if (overrideConfigId != null) {
             android.util.Log.i("NativeEmulatorViewModel", "[Identity] Using passed Config ID: $overrideConfigId")
             customConfigId = overrideConfigId
        } else {
             // Priority 2: Extract PSX Serial locally (if not passed)
            if (console == "psx" || console == "ps1" || console == "playstation") {
                 val serial = com.retroplay.util.PsxSerialExtractor.extractSerial(romPath)
                 if (serial != null) {
                     android.util.Log.i("NativeEmulatorViewModel", "[Identity] Extracted Serial locally: $serial")
                     customConfigId = serial
                 }
            }
        }
        
        // Priority 3: Fallback to Name if still null (ensures Config is always usable)
        if (customConfigId == null) {
            val safeName = initialGameName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            android.util.Log.w("NativeEmulatorViewModel", "[Identity] No ID found. Using Name Fallback: $safeName")
            customConfigId = safeName
        }
        
        return customConfigId
    }
}
