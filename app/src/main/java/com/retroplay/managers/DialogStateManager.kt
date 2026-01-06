package com.retroplay.managers

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Manager centralisé pour gérer tous les états de dialogs dans RetroArchEmulatorActivity.
 * Extracted to improve modularity and prevent dialog conflicts.
 */
class DialogStateManager {
    
    // Menus principaux
    val showMainMenu: MutableState<Boolean> = mutableStateOf(false)
    val showQuickMenu: MutableState<Boolean> = mutableStateOf(false)
    val showGamePadSettings: MutableState<Boolean> = mutableStateOf(false)
    
    // Overlays
    val overlaysVisible: MutableState<Boolean> = mutableStateOf(true)
    
    // Core dialogs
    val showCoreErrorDialog: MutableState<Boolean> = mutableStateOf(false)
    val showCoreSelectorFromError: MutableState<Boolean> = mutableStateOf(false)
    val showCoreChangeConfirmDialog: MutableState<Boolean> = mutableStateOf(false)
    
    // Turbo menus
    val showTurboSettings: MutableState<Boolean> = mutableStateOf(false)
    val showQuickTurbo: MutableState<Boolean> = mutableStateOf(false)
    
    // Game configuration dialogs
    val showDipSwitchDialog: MutableState<Boolean> = mutableStateOf(false)
    val showCoreOptionsDialog: MutableState<Boolean> = mutableStateOf(false)
    val showGameInfoDialog: MutableState<Boolean> = mutableStateOf(false)
    val showN64ExtensionsDialog: MutableState<Boolean> = mutableStateOf(false)
    val showCheatsDialog: MutableState<Boolean> = mutableStateOf(false)
    val showSmartConfigDialog: MutableState<Boolean> = mutableStateOf(false)
    val showPerGameConfigDialog: MutableState<Boolean> = mutableStateOf(false)
    val showDiskSwapperDialog: MutableState<Boolean> = mutableStateOf(false)
    
    // File browser
    val showCfgBrowser: MutableState<Boolean> = mutableStateOf(false)
    
    /**
     * Ouvre le menu principal et ferme les autres dialogs si nécessaire
     */
    fun openMainMenu() {
        closeAllDialogs()
        showMainMenu.value = true
    }
    
    /**
     * Ferme le menu principal
     */
    fun closeMainMenu() {
        showMainMenu.value = false
    }
    
    /**
     * Ouvre le menu rapide
     */
    fun openQuickMenu() {
        closeAllDialogs()
        showQuickMenu.value = true
    }
    
    /**
     * Ferme le menu rapide
     */
    fun closeQuickMenu() {
        showQuickMenu.value = false
    }
    
    /**
     * Ouvre les paramètres du gamepad
     */
    fun openGamePadSettings() {
        closeAllDialogs()
        showGamePadSettings.value = true
    }
    
    /**
     * Ferme les paramètres du gamepad
     */
    fun closeGamePadSettings() {
        showGamePadSettings.value = false
    }
    
    /**
     * Ouvre le dialog d'erreur de core
     */
    fun openCoreErrorDialog() {
        showCoreErrorDialog.value = true
    }
    
    /**
     * Ferme le dialog d'erreur de core
     */
    fun closeCoreErrorDialog() {
        showCoreErrorDialog.value = false
    }
    
    /**
     * Ouvre le sélecteur de core depuis l'erreur
     */
    fun openCoreSelectorFromError() {
        showCoreSelectorFromError.value = true
    }
    
    /**
     * Ferme le sélecteur de core depuis l'erreur
     */
    fun closeCoreSelectorFromError() {
        showCoreSelectorFromError.value = false
    }
    
    /**
     * Ouvre le dialog de confirmation de changement de core
     */
    fun openCoreChangeConfirmDialog() {
        showCoreChangeConfirmDialog.value = true
    }
    
    /**
     * Ferme le dialog de confirmation de changement de core
     */
    fun closeCoreChangeConfirmDialog() {
        showCoreChangeConfirmDialog.value = false
    }
    
    /**
     * Ouvre les paramètres turbo
     */
    fun openTurboSettings() {
        closeAllDialogs()
        showTurboSettings.value = true
    }
    
    /**
     * Ferme les paramètres turbo
     */
    fun closeTurboSettings() {
        showTurboSettings.value = false
    }
    
    /**
     * Ouvre le menu turbo rapide
     */
    fun openQuickTurbo() {
        closeAllDialogs()
        showQuickTurbo.value = true
    }
    
    /**
     * Ferme le menu turbo rapide
     */
    fun closeQuickTurbo() {
        showQuickTurbo.value = false
    }
    
    /**
     * Ouvre le dialog DIP Switch
     */
    fun openDipSwitchDialog() {
        closeAllDialogs()
        showDipSwitchDialog.value = true
    }
    
    /**
     * Ferme le dialog DIP Switch
     */
    fun closeDipSwitchDialog() {
        showDipSwitchDialog.value = false
    }
    
    /**
     * Ouvre le dialog Core Options
     */
    fun openCoreOptionsDialog() {
        closeAllDialogs()
        showCoreOptionsDialog.value = true
    }
    
    /**
     * Ferme le dialog Core Options
     */
    fun closeCoreOptionsDialog() {
        showCoreOptionsDialog.value = false
    }
    
    /**
     * Ouvre le dialog Game Info
     */
    fun openGameInfoDialog() {
        closeAllDialogs()
        showGameInfoDialog.value = true
    }
    
    /**
     * Ferme le dialog Game Info
     */
    fun closeGameInfoDialog() {
        showGameInfoDialog.value = false
    }
    
    /**
     * Ouvre le dialog N64 Extensions
     */
    fun openN64ExtensionsDialog() {
        closeAllDialogs()
        showN64ExtensionsDialog.value = true
    }
    
    /**
     * Ferme le dialog N64 Extensions
     */
    fun closeN64ExtensionsDialog() {
        showN64ExtensionsDialog.value = false
    }
    
    /**
     * Ouvre le dialog Cheats
     */
    fun openCheatsDialog() {
        closeAllDialogs()
        showCheatsDialog.value = true
    }
    
    /**
     * Ferme le dialog Cheats
     */
    fun closeCheatsDialog() {
        showCheatsDialog.value = false
    }
    
    /**
     * Ouvre le dialog Smart Config
     */
    fun openSmartConfigDialog() {
        closeAllDialogs()
        showSmartConfigDialog.value = true
    }
    
    /**
     * Ferme le dialog Smart Config
     */
    fun closeSmartConfigDialog() {
        showSmartConfigDialog.value = false
    }
    
    /**
     * Ouvre le dialog Per Game Config
     */
    fun openPerGameConfigDialog() {
        closeAllDialogs()
        showPerGameConfigDialog.value = true
    }
    
    /**
     * Ferme le dialog Per Game Config
     */
    fun closePerGameConfigDialog() {
        showPerGameConfigDialog.value = false
    }
    
    /**
     * Ouvre le dialog Disk Swapper
     */
    fun openDiskSwapperDialog() {
        closeAllDialogs()
        showDiskSwapperDialog.value = true
    }
    
    /**
     * Ferme le dialog Disk Swapper
     */
    fun closeDiskSwapperDialog() {
        showDiskSwapperDialog.value = false
    }
    
    /**
     * Ouvre le browser de fichiers .cfg
     */
    fun openCfgBrowser() {
        closeAllDialogs()
        showCfgBrowser.value = true
    }
    
    /**
     * Ferme le browser de fichiers .cfg
     */
    fun closeCfgBrowser() {
        showCfgBrowser.value = false
    }
    
    /**
     * Bascule la visibilité des overlays
     */
    fun toggleOverlays() {
        overlaysVisible.value = !overlaysVisible.value
    }
    
    /**
     * Ferme tous les dialogs (sauf overlays)
     */
    fun closeAllDialogs() {
        showMainMenu.value = false
        showQuickMenu.value = false
        showGamePadSettings.value = false
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
        showCfgBrowser.value = false
        // Note: on ne ferme pas les dialogs d'erreur de core car ils sont critiques
    }
    
    /**
     * Réinitialise tous les états
     */
    fun reset() {
        closeAllDialogs()
        showCoreErrorDialog.value = false
        showCoreSelectorFromError.value = false
        showCoreChangeConfirmDialog.value = false
        overlaysVisible.value = true
    }
    
    /**
     * Vérifie si un dialog est ouvert
     */
    fun isAnyDialogOpen(): Boolean {
        return showMainMenu.value ||
               showQuickMenu.value ||
               showGamePadSettings.value ||
               showTurboSettings.value ||
               showQuickTurbo.value ||
               showDipSwitchDialog.value ||
               showCoreOptionsDialog.value ||
               showGameInfoDialog.value ||
               showN64ExtensionsDialog.value ||
               showCheatsDialog.value ||
               showSmartConfigDialog.value ||
               showPerGameConfigDialog.value ||
               showDiskSwapperDialog.value ||
               showCfgBrowser.value ||
               showCoreErrorDialog.value ||
               showCoreSelectorFromError.value ||
               showCoreChangeConfirmDialog.value
    }
}

