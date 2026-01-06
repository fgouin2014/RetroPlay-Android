package com.retroplay.managers

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests unitaires pour DialogStateManager
 */
class DialogStateManagerTest {
    
    @Test
    fun testOpenCloseMainMenu() {
        val manager = DialogStateManager()
        assertFalse(manager.showMainMenu.value)
        
        manager.openMainMenu()
        assertTrue(manager.showMainMenu.value)
        
        manager.closeMainMenu()
        assertFalse(manager.showMainMenu.value)
    }
    
    @Test
    fun testOpenCloseQuickMenu() {
        val manager = DialogStateManager()
        assertFalse(manager.showQuickMenu.value)
        
        manager.openQuickMenu()
        assertTrue(manager.showQuickMenu.value)
        
        manager.closeQuickMenu()
        assertFalse(manager.showQuickMenu.value)
    }
    
    @Test
    fun testOpenCloseGamePadSettings() {
        val manager = DialogStateManager()
        assertFalse(manager.showGamePadSettings.value)
        
        manager.openGamePadSettings()
        assertTrue(manager.showGamePadSettings.value)
        
        manager.closeGamePadSettings()
        assertFalse(manager.showGamePadSettings.value)
    }
    
    @Test
    fun testOpenMainMenuClosesOthers() {
        val manager = DialogStateManager()
        manager.openQuickMenu()
        manager.openGamePadSettings()
        
        assertTrue(manager.showQuickMenu.value)
        assertTrue(manager.showGamePadSettings.value)
        
        manager.openMainMenu()
        
        assertTrue(manager.showMainMenu.value)
        assertFalse(manager.showQuickMenu.value)
        assertFalse(manager.showGamePadSettings.value)
    }
    
    @Test
    fun testOpenQuickMenuClosesOthers() {
        val manager = DialogStateManager()
        manager.openMainMenu()
        manager.openGamePadSettings()
        
        manager.openQuickMenu()
        
        assertTrue(manager.showQuickMenu.value)
        assertFalse(manager.showMainMenu.value)
        assertFalse(manager.showGamePadSettings.value)
    }
    
    @Test
    fun testCloseAllDialogs() {
        val manager = DialogStateManager()
        manager.openMainMenu()
        manager.openQuickMenu()
        manager.openGamePadSettings()
        manager.openTurboSettings()
        
        assertTrue(manager.isAnyDialogOpen())
        
        manager.closeAllDialogs()
        
        assertFalse(manager.showMainMenu.value)
        assertFalse(manager.showQuickMenu.value)
        assertFalse(manager.showGamePadSettings.value)
        assertFalse(manager.showTurboSettings.value)
        assertFalse(manager.isAnyDialogOpen())
    }
    
    @Test
    fun testToggleOverlays() {
        val manager = DialogStateManager()
        assertTrue(manager.overlaysVisible.value)
        
        manager.toggleOverlays()
        assertFalse(manager.overlaysVisible.value)
        
        manager.toggleOverlays()
        assertTrue(manager.overlaysVisible.value)
    }
    
    @Test
    fun testOpenCoreErrorDialog() {
        val manager = DialogStateManager()
        assertFalse(manager.showCoreErrorDialog.value)
        
        manager.openCoreErrorDialog()
        assertTrue(manager.showCoreErrorDialog.value)
        
        manager.closeCoreErrorDialog()
        assertFalse(manager.showCoreErrorDialog.value)
    }
    
    @Test
    fun testCoreErrorDialogNotClosedByCloseAllDialogs() {
        val manager = DialogStateManager()
        manager.openCoreErrorDialog()
        manager.openMainMenu()
        
        manager.closeAllDialogs()
        
        // Core error dialog should remain open (critical dialog)
        assertTrue(manager.showCoreErrorDialog.value)
        assertFalse(manager.showMainMenu.value)
    }
    
    @Test
    fun testIsAnyDialogOpen_NoDialogs() {
        val manager = DialogStateManager()
        assertFalse(manager.isAnyDialogOpen())
    }
    
    @Test
    fun testIsAnyDialogOpen_WithDialogs() {
        val manager = DialogStateManager()
        manager.openMainMenu()
        assertTrue(manager.isAnyDialogOpen())
        
        manager.closeMainMenu()
        assertFalse(manager.isAnyDialogOpen())
    }
    
    @Test
    fun testReset() {
        val manager = DialogStateManager()
        manager.openMainMenu()
        manager.openCoreErrorDialog()
        manager.toggleOverlays()
        
        manager.reset()
        
        assertFalse(manager.showMainMenu.value)
        assertFalse(manager.showCoreErrorDialog.value)
        assertTrue(manager.overlaysVisible.value) // Reset to default (true)
    }
    
    @Test
    fun testMultipleDialogs() {
        val manager = DialogStateManager()
        
        manager.openDipSwitchDialog()
        assertTrue(manager.showDipSwitchDialog.value)
        
        manager.openCoreOptionsDialog()
        assertTrue(manager.showCoreOptionsDialog.value)
        assertFalse(manager.showDipSwitchDialog.value) // Should be closed
        
        manager.openGameInfoDialog()
        assertTrue(manager.showGameInfoDialog.value)
        assertFalse(manager.showCoreOptionsDialog.value) // Should be closed
    }
    
    @Test
    fun testCheatsDialog() {
        val manager = DialogStateManager()
        manager.openCheatsDialog()
        assertTrue(manager.showCheatsDialog.value)
        
        manager.closeCheatsDialog()
        assertFalse(manager.showCheatsDialog.value)
    }
    
    @Test
    fun testSmartConfigDialog() {
        val manager = DialogStateManager()
        manager.openSmartConfigDialog()
        assertTrue(manager.showSmartConfigDialog.value)
        
        manager.closeSmartConfigDialog()
        assertFalse(manager.showSmartConfigDialog.value)
    }
    
    @Test
    fun testPerGameConfigDialog() {
        val manager = DialogStateManager()
        manager.openPerGameConfigDialog()
        assertTrue(manager.showPerGameConfigDialog.value)
        
        manager.closePerGameConfigDialog()
        assertFalse(manager.showPerGameConfigDialog.value)
    }
    
    @Test
    fun testN64ExtensionsDialog() {
        val manager = DialogStateManager()
        manager.openN64ExtensionsDialog()
        assertTrue(manager.showN64ExtensionsDialog.value)
        
        manager.closeN64ExtensionsDialog()
        assertFalse(manager.showN64ExtensionsDialog.value)
    }
    
    @Test
    fun testDiskSwapperDialog() {
        val manager = DialogStateManager()
        manager.openDiskSwapperDialog()
        assertTrue(manager.showDiskSwapperDialog.value)
        
        manager.closeDiskSwapperDialog()
        assertFalse(manager.showDiskSwapperDialog.value)
    }
    
    @Test
    fun testCfgBrowser() {
        val manager = DialogStateManager()
        manager.openCfgBrowser()
        assertTrue(manager.showCfgBrowser.value)
        
        manager.closeCfgBrowser()
        assertFalse(manager.showCfgBrowser.value)
    }
    
    @Test
    fun testTurboDialogs() {
        val manager = DialogStateManager()
        manager.openTurboSettings()
        assertTrue(manager.showTurboSettings.value)
        
        manager.openQuickTurbo()
        assertTrue(manager.showQuickTurbo.value)
        assertFalse(manager.showTurboSettings.value) // Should be closed
    }
}

