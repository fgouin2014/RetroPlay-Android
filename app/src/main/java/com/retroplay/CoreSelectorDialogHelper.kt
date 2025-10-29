package com.retroplay

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.window.Dialog
import androidx.appcompat.app.AlertDialog

/**
 * Helper pour afficher le CoreSelectorDialog depuis Java
 */
object CoreSelectorDialogHelper {
    
    /**
     * Affiche le dialog de sélection de core depuis une Activity Java
     */
    @JvmStatic
    fun show(
        activity: Activity,
        console: String,
        gamePath: String,
        onCoreSelected: (CoreInfo) -> Unit,
        onResetToDefault: () -> Unit
    ) {
        // Créer un AlertDialog avec un ComposeView
        val dialog = AlertDialog.Builder(activity)
            .create()
        
        val composeView = ComposeView(activity).apply {
            setContent {
                var showDialog by remember { mutableStateOf(true) }
                
                if (showDialog) {
                    CoreSelectorDialog(
                        console = console,
                        currentGamePath = gamePath,
                        onCoreSelected = { coreInfo ->
                            showDialog = false
                            dialog.dismiss()
                            onCoreSelected(coreInfo)
                        },
                        onResetToDefault = {
                            showDialog = false
                            dialog.dismiss()
                            onResetToDefault()
                        },
                        onDismiss = {
                            showDialog = false
                            dialog.dismiss()
                        }
                    )
                }
            }
        }
        
        dialog.setView(composeView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}

