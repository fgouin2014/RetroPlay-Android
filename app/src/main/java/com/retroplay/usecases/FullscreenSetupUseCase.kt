package com.retroplay.usecases

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * Use Case pour configurer le mode plein écran.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
class FullscreenSetupUseCase(
    private val activity: Activity,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "FullscreenSetupUseCase"
    }
    
    /**
     * Configure le mode plein écran (toujours activé pour l'émulateur)
     */
    fun setupFullscreenMode() {
        try {
            // Mode plein écran avec flags complets
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            
            // Garder l'écran allumé pendant l'émulation
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            Log.i(TAG, "Fullscreen mode configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up fullscreen mode", e)
        }
    }
}
















