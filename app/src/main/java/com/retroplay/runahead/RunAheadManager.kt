package com.retroplay.runahead

import android.util.Log
import com.swordfish.libretrodroid.GLRetroView

/**
 * Run-Ahead Manager - Reduces input lag by running the core ahead of real-time.
 * 
 * Based on RetroArch's implementation (c:\repos\RetroArch-master\runahead.c)
 * 
 * How it works:
 * 1. Save current core state (savestate)
 * 2. Run core N frames ahead with current input
 * 3. Render the "future" frame (reduced lag)
 * 4. Restore the saved state
 * 5. Execute one normal frame
 * 
 * This effectively removes N frames of internal input lag from the emulated game.
 * 
 * Typical usage:
 * - Fighting games: 2-4 frames (-32ms to -66ms lag)
 * - Platformers: 1-2 frames (-16ms to -32ms lag)
 * - Shoot'em ups: 1-2 frames
 * - RPGs/Puzzle: 0 frames (not needed)
 */
class RunAheadManager(
    private val retroView: GLRetroView,
    private var frames: Int = 0,
    private var enabled: Boolean = false
) {
    companion object {
        private const val TAG = "RunAheadManager"
        private const val MAX_FRAMES = 12  // RetroArch limit
    }
    
    private var stateBuffer: ByteArray? = null
    private var stateSize: Int = 0
    private var frameCount: Long = 0
    private var supported: Boolean = true
    
    /**
     * Enable/disable Run-Ahead.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.i(TAG, "Run-Ahead ${if (enabled) "ENABLED" else "DISABLED"} ($frames frames)")
    }
    
    /**
     * Set number of frames to run ahead (0-12).
     */
    fun setFrames(frames: Int) {
        this.frames = frames.coerceIn(0, MAX_FRAMES)
        Log.i(TAG, "Run-Ahead frames set to: $frames")
    }
    
    /**
     * Check if the current core supports Run-Ahead (savestate API).
     * 
     * This should be called after the game is loaded.
     */
    fun checkCoreSupport(): Boolean {
        return try {
            // Try to serialize state
            val testState = retroView.serializeState()
            stateSize = testState.size
            supported = testState.isNotEmpty()
            
            if (supported) {
                Log.i(TAG, "✅ Core supports Run-Ahead (savestate size: ${stateSize / 1024} KB)")
            } else {
                Log.w(TAG, "⚠️ Core does not support Run-Ahead (empty savestate)")
            }
            
            supported
        } catch (e: Exception) {
            Log.e(TAG, "❌ Core does not support Run-Ahead: ${e.message}")
            supported = false
            false
        }
    }
    
    /**
     * Process one frame with Run-Ahead.
     * 
     * This should be called INSTEAD of retroView.step() in the game loop.
     */
    fun processFrame() {
        frameCount++
        
        // If disabled or frames=0, just run normally
        if (!enabled || frames == 0 || !supported) {
            return  // GLRetroView handles frame stepping internally
        }
        
        try {
            // 1. Save current state
            stateBuffer = retroView.serializeState()
            
            if (stateBuffer == null || stateBuffer!!.isEmpty()) {
                Log.w(TAG, "Failed to serialize state, disabling Run-Ahead")
                enabled = false
                supported = false
                return
            }
            
            // 2. Run N frames ahead (retroView.step() is called internally by GLRetroView)
            // NOTE: GLRetroView runs its own game loop, so we can't manually step.
            // Instead, we'll need to hook into the frame callback.
            // For now, this is a placeholder for the concept.
            
            // 3. Rendering happens automatically
            
            // 4. Restore state (will happen on next frame)
            // NOTE: This needs to be integrated into GLRetroView's rendering loop
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Run-Ahead: ${e.message}", e)
            enabled = false
        }
    }
    
    /**
     * Get current Run-Ahead stats for debugging.
     */
    fun getStats(): String {
        return "Enabled: $enabled, Frames: $frames, State Size: ${stateSize / 1024} KB, " +
               "Supported: $supported, Frame Count: $frameCount"
    }
    
    /**
     * Reset Run-Ahead state (call when game is reset or reloaded).
     */
    fun reset() {
        stateBuffer = null
        frameCount = 0
        Log.d(TAG, "Run-Ahead state reset")
    }
}

