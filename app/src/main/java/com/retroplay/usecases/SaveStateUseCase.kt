package com.retroplay.usecases

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.launch
import java.io.File

/**
 * Use Case pour gérer les sauvegardes d'état du jeu (save states).
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Sauvegarde d'état dans un slot
 * - Chargement d'état depuis un slot
 * - Suppression d'un slot
 * - Capture de thumbnail pour un slot
 */
class SaveStateUseCase(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val retroView: GLRetroView,
    private val console: String,
    private val gameName: String
) {
    
    companion object {
        private const val TAG = "SaveStateUseCase"
        private const val SAVES_BASE_PATH = "/storage/emulated/0/GameLibrary-Data/saves"
    }
    
    /**
     * Interface pour les callbacks UI et GL Thread
     */
    interface SaveStateCallbacks {
        suspend fun <T> runOnGLThread(block: () -> T): T
        fun runOnUiThread(action: Runnable)
        fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT)
    }
    
    private var callbacks: SaveStateCallbacks? = null
    
    fun setCallbacks(callbacks: SaveStateCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Sauvegarde l'état du jeu dans un slot
     */
    fun saveGameState(slot: Int) {
        lifecycleScope.launch {
            try {
                // Vérifier que le core est chargé avant de sauvegarder
                if (!retroView.isGameLoaded()) {
                    Log.w(TAG, "[$console] Cannot save state: game not loaded yet")
                    callbacks?.runOnUiThread {
                        callbacks?.showToast("Game not ready for saving", Toast.LENGTH_SHORT)
                    }
                    return@launch
                }
                
                // Structure : saves/{console}/{gameName}/slot{slot}.state
                val gameDir = File("$SAVES_BASE_PATH/$console/$gameName")
                if (!gameDir.exists()) {
                    gameDir.mkdirs()
                }
                
                val saveFile = File(gameDir, "slot$slot.state")
                // CRITIQUE: Appel JNI doit être sur GL Thread
                val stateData = callbacks?.runOnGLThread { 
                    retroView.serializeState()
                } ?: run {
                    Log.e(TAG, "runOnGLThread callback not set")
                    return@launch
                }
                
                // Vérifier que les données sont valides
                if (stateData.isEmpty()) {
                    Log.w(TAG, "[$console] serializeState returned empty data")
                    callbacks?.runOnUiThread {
                        callbacks?.showToast("Save failed: empty state", Toast.LENGTH_SHORT)
                    }
                    return@launch
                }
                
                saveFile.writeBytes(stateData)
                
                Log.i(TAG, "[$console] Game state saved to slot $slot: ${saveFile.absolutePath}")
                callbacks?.runOnUiThread {
                    callbacks?.showToast("[$console] Saved to Slot $slot", Toast.LENGTH_SHORT)
                    // Capture screenshot for slot thumbnail
                    captureSlotThumbnail(slot)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving game state to slot $slot", e)
                callbacks?.runOnUiThread {
                    callbacks?.showToast("Error saving game", Toast.LENGTH_SHORT)
                }
            }
        }
    }
    
    /**
     * Charge l'état du jeu depuis un slot
     */
    fun loadGameState(slot: Int) {
        lifecycleScope.launch {
            try {
                // Structure : saves/{console}/{gameName}/slot{slot}.state
                val saveFile = File("$SAVES_BASE_PATH/$console/$gameName/slot$slot.state")
                if (saveFile.exists()) {
                    // CRITIQUE: Appel JNI doit être sur GL Thread
                    val stateBytes = saveFile.readBytes()
                    val callbacksRef = callbacks ?: run {
                        Log.e(TAG, "SaveStateCallbacks not set")
                        return@launch
                    }
                    callbacksRef.runOnGLThread { 
                        retroView.unserializeState(stateBytes) 
                    }
                    Log.i(TAG, "[$console] Game state loaded from slot $slot: ${saveFile.absolutePath}")
                    callbacks?.runOnUiThread {
                        callbacks?.showToast("[$console] Loaded from Slot $slot", Toast.LENGTH_SHORT)
                    }
                } else {
                    Log.w(TAG, "No save state found for slot $slot in $console")
                    callbacks?.runOnUiThread {
                        callbacks?.showToast("[$console] No save in Slot $slot", Toast.LENGTH_SHORT)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading game state from slot $slot", e)
                callbacks?.runOnUiThread {
                    callbacks?.showToast("Error loading game", Toast.LENGTH_SHORT)
                }
            }
        }
    }
    
    /**
     * Supprime une sauvegarde (slot)
     */
    fun deleteSaveSlot(slot: Int) {
        try {
            val gameDir = File("$SAVES_BASE_PATH/$console/$gameName")
            val saveFile = File(gameDir, "slot$slot.state")
            val thumbnailFile = File(gameDir, "slot${slot}_thumbnail.png")
            
            var deleted = false
            if (saveFile.exists()) {
                saveFile.delete()
                deleted = true
                Log.i(TAG, "[$console] Deleted save file: ${saveFile.absolutePath}")
            }
            if (thumbnailFile.exists()) {
                thumbnailFile.delete()
                Log.i(TAG, "[$console] Deleted thumbnail: ${thumbnailFile.absolutePath}")
            }
            
            if (deleted) {
                callbacks?.runOnUiThread {
                    callbacks?.showToast("[$console] Slot $slot supprimé", Toast.LENGTH_SHORT)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting save slot $slot", e)
            callbacks?.runOnUiThread {
                callbacks?.showToast("Error deleting slot", Toast.LENGTH_SHORT)
            }
        }
    }
    
    /**
     * Capture une screenshot et la sauvegarde comme thumbnail pour le slot
     * Utilise ScreenshotManager pour la capture
     */
    fun captureSlotThumbnail(slot: Int) {
        try {
            // Vérifier que retroView est valide en accédant à une propriété
            val width = retroView.width
            if (width <= 0) return
            
            retroView.queueEvent {
                try {
                    val viewWidth = retroView.width
                    val viewHeight = retroView.height
                    if (viewWidth <= 0 || viewHeight <= 0) return@queueEvent
                    
                    val screenshotBitmap = com.retroplay.ScreenshotManager.captureScreenshotGL(viewWidth, viewHeight)
                    if (screenshotBitmap != null) {
                        // Save thumbnail in the game directory
                        val gameDir = File("$SAVES_BASE_PATH/$console/$gameName")
                        if (!gameDir.exists()) {
                            gameDir.mkdirs()
                        }
                        
                        val thumbnailFile = File(gameDir, "slot${slot}_thumbnail.png")
                        java.io.FileOutputStream(thumbnailFile).use { out ->
                            screenshotBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
                        }
                        
                        Log.i(TAG, "[$console] Slot $slot thumbnail saved: ${thumbnailFile.absolutePath}")
                        screenshotBitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to capture slot thumbnail", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "retroView not initialized or invalid", e)
        }
    }
    
    /**
     * Vérifie si un slot existe
     */
    fun hasSaveState(slot: Int): Boolean {
        val saveFile = File("$SAVES_BASE_PATH/$console/$gameName/slot$slot.state")
        return saveFile.exists()
    }
    
    /**
     * Obtient le chemin du fichier de sauvegarde pour un slot
     */
    fun getSaveStatePath(slot: Int): String? {
        val saveFile = File("$SAVES_BASE_PATH/$console/$gameName/slot$slot.state")
        return if (saveFile.exists()) saveFile.absolutePath else null
    }
}
















