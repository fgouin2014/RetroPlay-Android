package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.swordfish.libretrodroid.GLRetroView

/**
 * Use Case pour gérer les contrôles de l'émulateur (fast forward, audio mute, etc.).
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Fast Forward toggle
 * - Audio Mute toggle
 * - Quick Actions Bar visibility toggle
 * - Aspect Ratio application
 */
class EmulatorControlsUseCase(
    private val context: Context,
    private val retroView: GLRetroView,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "EmulatorControlsUseCase"
    }
    
    /**
     * Interface pour les callbacks UI et états
     */
    interface ControlsCallbacks {
        fun runOnUiThread(action: Runnable)
        fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT)
        fun getFastForwardRatio(): Int
        fun isFastForwardActive(): MutableState<Boolean>
        fun isAudioMuted(): MutableState<Boolean>
        fun isQuickActionsBarVisible(): MutableState<Boolean>
        fun getGameViewBounds(): MutableState<androidx.compose.ui.geometry.Rect?>
    }
    
    private var callbacks: ControlsCallbacks? = null
    
    fun setCallbacks(callbacks: ControlsCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Toggle Fast Forward
     */
    fun toggleFastForward() {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ControlsCallbacks not set")
            return
        }
        
        val isActiveState = callbacksRef.isFastForwardActive()
        isActiveState.value = !isActiveState.value
        val speed = if (isActiveState.value) callbacksRef.getFastForwardRatio() else 1
        retroView.frameSpeed = speed
        Log.i(TAG, "[FAST_FORWARD] ${if (isActiveState.value) "ENABLED (${callbacksRef.getFastForwardRatio()}x)" else "DISABLED (1x)"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_fast_forward_active", isActiveState.value).apply()
        
        callbacksRef.runOnUiThread {
            callbacksRef.showToast(
                if (isActiveState.value) "Fast Forward: ${callbacksRef.getFastForwardRatio()}x" else "Normal Speed",
                Toast.LENGTH_SHORT
            )
        }
    }
    
    /**
     * Toggle Audio Mute
     */
    fun toggleAudioMute() {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ControlsCallbacks not set")
            return
        }
        
        val isMutedState = callbacksRef.isAudioMuted()
        isMutedState.value = !isMutedState.value
        retroView.audioEnabled = !isMutedState.value
        Log.i(TAG, "[AUDIO] ${if (isMutedState.value) "MUTED" else "UNMUTED"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_audio_muted", isMutedState.value).apply()
        
        callbacksRef.runOnUiThread {
            callbacksRef.showToast(
                if (isMutedState.value) "Audio Muted" else "Audio Unmuted",
                Toast.LENGTH_SHORT
            )
        }
    }
    
    /**
     * Toggle Quick Actions Bar visibility
     */
    fun toggleQuickActionsBar() {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ControlsCallbacks not set")
            return
        }
        
        val isVisibleState = callbacksRef.isQuickActionsBarVisible()
        isVisibleState.value = !isVisibleState.value
        Log.i(TAG, "[QUICK_ACTIONS_BAR] ${if (isVisibleState.value) "VISIBLE" else "HIDDEN"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_quick_actions_bar_visible", isVisibleState.value).apply()
        
        callbacksRef.runOnUiThread {
            callbacksRef.showToast(
                if (isVisibleState.value) "Quick Actions Bar Visible" else "Quick Actions Bar Hidden",
                Toast.LENGTH_SHORT
            )
        }
    }
    
    /**
     * Applique un aspect ratio personnalisé en calculant et appliquant le viewport approprié
     * @param aspectRatioString Aspect ratio sélectionné ("AUTO", "4:3", "16:9", etc.)
     * @param console Console name for preferences
     * @param retryCallback Callback pour retry si bounds pas disponibles
     */
    fun applyAspectRatio(aspectRatioString: String, console: String, retryCallback: (() -> Unit)? = null) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ControlsCallbacks not set")
            return
        }
        
        if (aspectRatioString == "AUTO") {
            // Mode AUTO: utiliser l'aspect ratio du core (viewport plein écran)
            callbacksRef.runOnUiThread {
                retroView.viewport = android.graphics.RectF(0f, 0f, 1f, 1f)
                Log.i(TAG, "[ASPECT_RATIO] Reset to AUTO (core default)")
            }
            return
        }
        
        // Convertir le string en float (ex: "4:3" -> 1.333f)
        val targetAspectRatio = when (aspectRatioString) {
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            "16:10" -> 16f / 10f
            "1:1" -> 1f / 1f
            "21:9" -> 21f / 9f
            else -> {
                Log.w(TAG, "[ASPECT_RATIO] Unknown ratio: $aspectRatioString, using AUTO")
                callbacksRef.runOnUiThread {
                    retroView.viewport = android.graphics.RectF(0f, 0f, 1f, 1f)
                }
                return
            }
        }
        
        // Récupérer les bounds du GLRetroView
        val bounds = callbacksRef.getGameViewBounds().value
        if (bounds == null) {
            // Retry après un court délai si bounds pas disponible
            Log.w(TAG, "[ASPECT_RATIO] GLRetroView bounds not available yet, will retry")
            if (retryCallback != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    retryCallback()
                }, 100)
            }
            return
        }
        
        val screenWidth = bounds.width
        val screenHeight = bounds.height
        val screenAspectRatio = screenWidth / screenHeight
        
        // Calculer le viewport avec letterboxing
        val viewport = if (screenAspectRatio > targetAspectRatio) {
            // Écran plus large que le ratio cible → Bandes noires à gauche/droite
            val gameWidth = screenHeight * targetAspectRatio
            val letterboxWidth = (screenWidth - gameWidth) / 2f
            val left = letterboxWidth / screenWidth
            val right = 1f - left
            android.graphics.RectF(left, 0f, right, 1f)
        } else {
            // Écran plus haut que le ratio cible → Bandes noires en haut/bas
            val gameHeight = screenWidth / targetAspectRatio
            val letterboxHeight = (screenHeight - gameHeight) / 2f
            val top = letterboxHeight / screenHeight
            val bottom = 1f - top
            android.graphics.RectF(0f, top, 1f, bottom)
        }
        
        // Appliquer le viewport
        callbacksRef.runOnUiThread {
            retroView.viewport = viewport
            Log.i(TAG, "[ASPECT_RATIO] Applied $aspectRatioString (${targetAspectRatio}): viewport=(${viewport.left}, ${viewport.top}, ${viewport.right}, ${viewport.bottom})")
        }
    }
}
















