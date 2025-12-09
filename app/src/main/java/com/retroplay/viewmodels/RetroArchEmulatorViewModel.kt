package com.retroplay.viewmodels

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
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
