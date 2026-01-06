package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Use Case pour gérer les hotkeys et actions rapides de l'émulateur.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Save/Load states
 * - Fast forward toggle/hold
 * - Audio mute toggle
 * - Shader cycle (next/prev)
 * - Rewind
 * - Reset
 * - Pause toggle
 * - Frame advance
 */
class HandleHotkeyUseCase(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val retroView: GLRetroView,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "HandleHotkeyUseCase"
    }
    
    /**
     * Interface pour les callbacks et états
     */
    interface HotkeyCallbacks {
        fun runOnUiThread(action: Runnable)
        fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT)
        fun getCurrentSaveSlot(): Int
        fun setCurrentSaveSlot(slot: Int)
        fun saveGameState(slot: Int)
        fun loadGameState(slot: Int)
        fun getFastForwardRatio(): Int
        fun isFastForwardActive(): Boolean
        fun setFastForwardActive(active: Boolean)
        fun isAudioMuted(): Boolean
        fun setAudioMuted(muted: Boolean)
        fun getCurrentShader(): com.retroplay.shader.ShaderManager.ShaderPreset
        fun setCurrentShader(shader: com.retroplay.shader.ShaderManager.ShaderPreset)
        fun isPaused(): Boolean
        fun setPaused(paused: Boolean)
        fun beginRewind(): Boolean
        fun endRewind()
        fun notifyRewindUnavailable()
    }
    
    private var callbacks: HotkeyCallbacks? = null
    
    fun setCallbacks(callbacks: HotkeyCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Gère une action de hotkey
     */
    fun handleHotkey(action: String) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "HotkeyCallbacks not set")
            return
        }
        
        Log.i(TAG, "Hotkey triggered: $action")
        when (action) {
            // Save/Load states
            "save_state" -> {
                callbacksRef.saveGameState(callbacksRef.getCurrentSaveSlot())
            }
            "load_state" -> {
                callbacksRef.loadGameState(callbacksRef.getCurrentSaveSlot())
            }
            "state_slot_increase" -> {
                // Augmenter le slot (0-9, cycle à 0 après 9)
                val newSlot = (callbacksRef.getCurrentSaveSlot() + 1) % 10
                callbacksRef.setCurrentSaveSlot(newSlot)
                Log.i(TAG, "State slot increased to: $newSlot")
                callbacksRef.runOnUiThread {
                    callbacksRef.showToast("Slot $newSlot", Toast.LENGTH_SHORT)
                }
            }
            "state_slot_decrease" -> {
                // Diminuer le slot (9-0, cycle à 9 après 0)
                val currentSlot = callbacksRef.getCurrentSaveSlot()
                val newSlot = if (currentSlot == 0) 9 else currentSlot - 1
                callbacksRef.setCurrentSaveSlot(newSlot)
                Log.i(TAG, "State slot decreased to: $newSlot")
                callbacksRef.runOnUiThread {
                    callbacksRef.showToast("Slot $newSlot", Toast.LENGTH_SHORT)
                }
            }
            
            // Fast forward
            "toggle_fast_forward" -> {
                toggleFastForward(callbacksRef)
            }
            "hold_fast_forward" -> {
                // Hold fast forward (maintenir pour accélérer)
                val ratio = callbacksRef.getFastForwardRatio()
                retroView.frameSpeed = ratio
                callbacksRef.setFastForwardActive(true)
                Log.i(TAG, "[FAST_FORWARD] Hold: ${ratio}x")
            }
            
            // Audio mute
            "audio_mute_toggle" -> {
                toggleAudioMute(callbacksRef)
            }
            
            // Shader cycle
            "shader_next" -> {
                cycleShader(callbacksRef, forward = true)
            }
            "shader_prev" -> {
                cycleShader(callbacksRef, forward = false)
            }
            
            // Rewind (nécessite support du core)
            "rewind" -> {
                if (callbacksRef.beginRewind()) {
                    lifecycleScope.launch {
                        delay(250)
                        callbacksRef.endRewind()
                    }
                }
            }
            
            // Reset
            "reset" -> {
                retroView.reset()
                Log.i(TAG, "Game reset")
                callbacksRef.runOnUiThread {
                    callbacksRef.showToast("Game Reset", Toast.LENGTH_SHORT)
                }
            }
            
            // Pause toggle
            "pause_toggle" -> {
                val wasPaused = callbacksRef.isPaused()
                val newPaused = !wasPaused
                callbacksRef.setPaused(newPaused)
                
                if (newPaused) {
                    retroView.onPause()
                    Log.i(TAG, "Game paused")
                    callbacksRef.runOnUiThread {
                        callbacksRef.showToast("Game Paused", Toast.LENGTH_SHORT)
                    }
                } else {
                    retroView.onResume()
                    Log.i(TAG, "Game resumed")
                    callbacksRef.runOnUiThread {
                        callbacksRef.showToast("Game Resumed", Toast.LENGTH_SHORT)
                    }
                }
            }
            
            // Screenshot
            "screenshot" -> {
                Log.i(TAG, "Screenshot (not implemented yet)")
                callbacksRef.runOnUiThread {
                    callbacksRef.showToast("Screenshot not implemented", Toast.LENGTH_SHORT)
                }
            }
            
            // Slow motion
            "toggle_slowmotion" -> {
                // LibretroDroid frameSpeed est un Int (pas de valeurs < 1)
                Log.i(TAG, "Slow motion (not supported - frameSpeed must be >= 1)")
                callbacksRef.runOnUiThread {
                    callbacksRef.showToast("Slow motion not supported", Toast.LENGTH_SHORT)
                }
            }
            
            // Frame advance
            "frame_advance" -> {
                // Frame advance = pause + resume (1 frame) + pause
                // LibretroDroid va rendre 1 frame puis se re-pauser
                if (!callbacksRef.isPaused()) {
                    retroView.onPause()
                    callbacksRef.setPaused(true)
                }
                // Resume pour 1 frame, puis re-pause via handler
                retroView.onResume()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    retroView.onPause()
                }, 16)  // ~1 frame à 60fps
                Log.i(TAG, "Frame advance (1 frame)")
                callbacksRef.runOnUiThread {
                    callbacksRef.showToast("Frame +1", Toast.LENGTH_SHORT)
                }
            }
            
            else -> {
                Log.w(TAG, "Unknown hotkey: $action")
            }
        }
    }
    
    /**
     * Gère les changements d'état des hotkeys (pressed/released)
     */
    fun handleHotkeyChange(action: String, pressed: Boolean) {
        val callbacksRef = callbacks ?: return
        
        if (action != "rewind") {
            return
        }
        if (pressed) {
            callbacksRef.beginRewind()
        } else {
            callbacksRef.endRewind()
        }
    }
    
    /**
     * Toggle fast forward
     */
    private fun toggleFastForward(callbacks: HotkeyCallbacks) {
        val wasActive = callbacks.isFastForwardActive()
        val newActive = !wasActive
        callbacks.setFastForwardActive(newActive)
        
        val speed = if (newActive) callbacks.getFastForwardRatio() else 1
        retroView.frameSpeed = speed
        Log.i(TAG, "[FAST_FORWARD] ${if (newActive) "ENABLED (${callbacks.getFastForwardRatio()}x)" else "DISABLED (1x)"}")
        
        // Sauvegarder l'état dans SharedPreferences
        prefs.edit().putBoolean("emulation_fast_forward_active", newActive).apply()
        
        callbacks.runOnUiThread {
            callbacks.showToast(
                if (newActive) "Fast Forward: ${callbacks.getFastForwardRatio()}x" else "Normal Speed",
                Toast.LENGTH_SHORT
            )
        }
    }
    
    /**
     * Toggle audio mute
     */
    private fun toggleAudioMute(callbacks: HotkeyCallbacks) {
        val wasMuted = callbacks.isAudioMuted()
        val newMuted = !wasMuted
        callbacks.setAudioMuted(newMuted)
        
        retroView.audioEnabled = !newMuted
        Log.i(TAG, "[AUDIO] ${if (newMuted) "MUTED" else "UNMUTED"}")
        
        // Sauvegarder dans SharedPreferences
        prefs.edit().putBoolean("emulation_audio_muted", newMuted).apply()
        
        callbacks.runOnUiThread {
            callbacks.showToast(
                if (newMuted) "Audio Muted" else "Audio Unmuted",
                Toast.LENGTH_SHORT
            )
        }
    }
    
    /**
     * Cycle shader (forward or backward)
     */
    private fun cycleShader(callbacks: HotkeyCallbacks, forward: Boolean) {
        val currentShader = callbacks.getCurrentShader()
        val nextShader = if (forward) {
            com.retroplay.shader.ShaderManager.getNextShader(currentShader)
        } else {
            com.retroplay.shader.ShaderManager.getPreviousShader(currentShader)
        }
        
        // Update state immediately for UI response
        callbacks.setCurrentShader(nextShader)
        
        // Save preference
        prefs.edit().putString("emulation_shader_preset", nextShader.name).apply()
        
        // Apply to RetroView on GL Thread (CRITICAL: Context must be valid)
        try {
            // Vérifier que retroView est valide en accédant à une propriété
            val width = retroView.width
            if (width > 0) {
                retroView.queueEvent {
                    try {
                        val shaderConfig = com.retroplay.shader.ShaderManager.getShaderConfig(nextShader)
                        retroView.shader = shaderConfig
                        Log.i(TAG, "[SHADER] Cycle Applied (GL Thread): ${nextShader.displayName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "[SHADER] Error setting shader on GLThread", e)
                    }
                }
            }
            
            // Show Toast on UI Thread
            callbacks.runOnUiThread {
                callbacks.showToast(
                    "Shader: ${nextShader.displayName}",
                    Toast.LENGTH_SHORT
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "[SHADER] Error scheduling shader update", e)
            callbacks.showToast("Shader Error", Toast.LENGTH_SHORT)
        }
    }
}
