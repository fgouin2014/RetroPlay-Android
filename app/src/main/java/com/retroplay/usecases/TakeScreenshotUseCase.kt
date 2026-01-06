package com.retroplay.usecases

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import com.retroplay.ScreenshotManager
import com.retroplay.gallery.ScreenshotRepository
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Use Case pour la capture d'écran.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
class TakeScreenshotUseCase(
    private val context: Context,
    private val lifecycleScope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "TakeScreenshotUseCase"
        private const val GL_THREAD_DELAY_MS = 100L
    }
    
    /**
     * Résultat de la capture d'écran
     */
    data class ScreenshotResult(
        val success: Boolean,
        val screenshotPath: String? = null,
        val thumbnailPath: String? = null,
        val errorMessage: String? = null
    )
    
    /**
     * Capture une screenshot depuis le GLRetroView
     * 
     * @param retroView La vue RetroArch depuis laquelle capturer
     * @param console L'ID de la console (pour le chemin de sauvegarde)
     * @param gameId L'ID du jeu (sanitized, pour le nom de fichier)
     * @param onResult Callback appelé avec le résultat (optionnel, pour tests)
     */
    fun takeScreenshot(
        retroView: GLRetroView,
        console: String,
        gameId: String,
        onResult: ((ScreenshotResult) -> Unit)? = null
    ) {
        lifecycleScope.launch {
            try {
                var screenshotBitmap: Bitmap? = null
                
                // Capture screenshot from GL thread
                retroView.queueEvent {
                    try {
                        val width = retroView.width
                        val height = retroView.height
                        screenshotBitmap = ScreenshotManager.captureScreenshotGL(width, height)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to capture screenshot from GL", e)
                    }
                }
                
                // Wait a bit for GL thread to finish
                delay(GL_THREAD_DELAY_MS)
                
                screenshotBitmap?.let { bitmap ->
                    val result = ScreenshotManager.saveScreenshot(bitmap, console, gameId)
                    
                    val screenshotResult = if (result != null) {
                        Log.i(TAG, "Screenshot saved: ${result.screenshotPath}")
                        result.thumbnailPath?.let { thumb ->
                            Log.i(TAG, "Thumbnail saved: $thumb")
                        }
                        ScreenshotResult(
                            success = true,
                            screenshotPath = result.screenshotPath,
                            thumbnailPath = result.thumbnailPath
                        )
                    } else {
                        ScreenshotResult(
                            success = false,
                            errorMessage = "Failed to save screenshot"
                        )
                    }
                    
                    // Show toast on UI thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (screenshotResult.success) {
                            Toast.makeText(
                                context,
                                "Screenshot saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                screenshotResult.errorMessage ?: "Failed to save screenshot",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    
                    onResult?.invoke(screenshotResult)
                } ?: run {
                    val errorResult = ScreenshotResult(
                        success = false,
                        errorMessage = "Failed to capture screenshot"
                    )
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            errorResult.errorMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    onResult?.invoke(errorResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot error", e)
                val errorResult = ScreenshotResult(
                    success = false,
                    errorMessage = "Screenshot error: ${e.message}"
                )
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        errorResult.errorMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                onResult?.invoke(errorResult)
            }
        }
    }
    
    /**
     * Sanitize un gameId pour l'utiliser comme nom de fichier
     * 
     * @param gameId L'ID du jeu brut
     * @param fallback Le fallback si gameId est vide
     * @return Le gameId sanitized
     */
    fun sanitizeGameId(gameId: String?, fallback: String): String {
        val rawGameId = gameId ?: fallback
        return ScreenshotRepository.sanitizeGameKey(rawGameId.ifBlank { fallback })
    }
}

