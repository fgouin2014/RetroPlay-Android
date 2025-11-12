package com.retroplay.runahead

import android.os.SystemClock
import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RunAheadManager(
    private val retroView: GLRetroView
) : GLRetroView.FrameInterceptor, GLRetroView.InputListener {

    companion object {
        private const val TAG = "RunAheadManager"
        const val MAX_FRAMES = 12
        private const val PORT_COUNT = 4
        private const val ANALOG_THRESHOLD = 0.01f
        private const val POINTER_THRESHOLD = 0.005f
        private const val INIT_RETRY_INTERVAL_MS = 500L
    }

    private val inputDirty = AtomicBoolean(false)
    private val statusFlow = MutableStateFlow(
        RunAheadStatus(
            enabled = false,
            supported = true,
            frames = 0,
            framesExecuted = 0,
            totalFrames = 0,
            skippedFrames = 0
        )
    )

    private val keyStates = Array(PORT_COUNT) { mutableSetOf<Int>() }
    private val dpadState = Array(PORT_COUNT) { IntArray(2) }
    private val analogState = Array(PORT_COUNT) { FloatArray(4) } // Lx, Ly, Rx, Ry
    private val pointerState = Array(PORT_COUNT) { PointerState() }
    private val mouseButtons = Array(PORT_COUNT) { BooleanArray(3) }

    private var runAheadEnabled = false
    private var runAheadFrames = 0

    private var stateBuffer = ByteArray(0)
    private var stateSize = 0
    private var initialized = false
    private var supported = true
    private var lastInitializationAttempt = 0L

    private var totalFrames = 0L
    private var runAheadFramesExecuted = 0L
    private var skippedFrames = 0L

    private data class PointerState(
        var x: Float = Float.NaN,
        var y: Float = Float.NaN,
        var pressed: Boolean = false
    )

    init {
        retroView.setFrameInterceptor(this)
        retroView.setInputListener(this)
    }

    fun configure(enabled: Boolean, frames: Int) {
        val clamped = frames.coerceIn(0, MAX_FRAMES)
        runAheadEnabled = enabled && clamped > 0
        runAheadFrames = if (runAheadEnabled) clamped else 0
        if (runAheadEnabled) {
            markInputDirty()
            ensureInitialized(force = true)
            Log.i(TAG, "Configured run-ahead: enabled=true frames=$runAheadFrames")
        } else {
            Log.i(TAG, "Configured run-ahead: enabled=false")
        }
        publishStatus()
    }

    fun release() {
        retroView.setFrameInterceptor(null)
        retroView.setInputListener(null)
        initialized = false
        supported = true
        inputDirty.set(false)
        publishStatus(resetCounters = true)
    }

    fun onSurfaceReady() {
        if (runAheadEnabled) {
            ensureInitialized(force = true)
        }
    }

    private fun ensureInitialized(force: Boolean = false): Boolean {
        if (!runAheadEnabled) {
            return false
        }
        if (initialized && !force) {
            return true
        }
        if (!supported && !force) {
            return false
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastInitializationAttempt < INIT_RETRY_INTERVAL_MS) {
            return initialized && supported
        }
        lastInitializationAttempt = now
        return try {
            val state = retroView.serializeState()
            if (state.isEmpty()) {
                Log.w(TAG, "serializeState returned an empty buffer, disabling run-ahead")
                initialized = false
                supported = false
                false
            } else {
                stateSize = state.size
                if (stateBuffer.size != stateSize) {
                    stateBuffer = ByteArray(stateSize)
                }
                initialized = true
                supported = true
                Log.i(TAG, "Run-ahead initialized: stateSize=$stateSize bytes")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Run-ahead initialization failed: ${e.message}", e)
            initialized = false
            supported = false
            false
        }
    }

    override fun onBeforeFrame(): Boolean {
        if (!runAheadEnabled) {
            publishStatus()
            return false
        }
        if (!supported) {
            if (totalFrames % 600 == 0L) {
                Log.w(TAG, "Run-ahead skipped: current core does not support savestates")
            }
            publishStatus()
            return false
        }
        totalFrames++
        if (!inputDirty.get()) {
            skippedFrames++
            publishStatus()
            return false
        }
        if (!ensureInitialized()) {
            skippedFrames++
            publishStatus()
            return false
        }
        val handled = performRunAhead()
        publishStatus()
        return handled
    }

    override fun onAfterFrame(frameHandled: Boolean) {
        if (frameHandled) {
            runAheadFramesExecuted++
            inputDirty.set(false)
            publishStatus()
        }
    }

    private fun performRunAhead(): Boolean {
        val framesToRun = runAheadFrames
        if (framesToRun <= 0) {
            return false
        }
        val audioBefore = retroView.audioEnabled
        var stateCaptured = false
        var executedAnyFrame = false
        return try {
            for (index in 0..framesToRun) {
                val lastFrame = index == framesToRun
                if (!lastFrame) {
                    retroView.audioEnabled = false
                }
                LibretroDroid.step(retroView)
                executedAnyFrame = true
                if (!lastFrame) {
                    retroView.audioEnabled = audioBefore
                }
                if (index == 0) {
                    val state = retroView.serializeState()
                    if (state.isEmpty()) {
                        Log.w(TAG, "Run-ahead state capture returned empty buffer, disabling feature")
                        supported = false
                        return true // avoid stepping twice
                    }
                    if (state.size != stateSize) {
                        stateSize = state.size
                        stateBuffer = ByteArray(stateSize)
                    }
                    System.arraycopy(state, 0, stateBuffer, 0, stateSize)
                    stateCaptured = true
                }
            }
            if (stateCaptured) {
                val restored = retroView.unserializeState(stateBuffer)
                if (!restored) {
                    Log.w(TAG, "Run-ahead restore failed, disabling feature")
                    supported = false
                }
            } else {
                supported = false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Run-ahead execution failed: ${e.message}", e)
            supported = false
            executedAnyFrame
        } finally {
            retroView.audioEnabled = audioBefore
        }
    }

    private fun markInputDirty() {
        inputDirty.set(true)
        publishStatus()
    }

    override fun onKeyEvent(port: Int, action: Int, keyCode: Int) {
        if (port !in 0 until PORT_COUNT) return
        val pressedKeys = keyStates[port]
        val changed = when (action) {
            android.view.KeyEvent.ACTION_DOWN -> pressedKeys.add(keyCode)
            android.view.KeyEvent.ACTION_UP -> pressedKeys.remove(keyCode)
            else -> false
        }
        if (changed) {
            markInputDirty()
        }
    }

    override fun onMotionEvent(port: Int, source: Int, xAxis: Float, yAxis: Float) {
        if (port !in 0 until PORT_COUNT) return
        val changed = when (source) {
            GLRetroView.MOTION_SOURCE_DPAD -> updateDpad(port, xAxis, yAxis)
            GLRetroView.MOTION_SOURCE_ANALOG_LEFT -> updateAnalog(analogState[port], 0, xAxis, yAxis)
            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT -> updateAnalog(analogState[port], 2, xAxis, yAxis)
            GLRetroView.MOTION_SOURCE_POINTER -> updatePointer(port, xAxis, yAxis, pointerState[port].pressed)
            else -> false
        }
        if (changed) {
            markInputDirty()
        }
    }

    override fun onTouchEvent(action: Int, normalizedX: Float, normalizedY: Float) {
        val pointer = pointerState[0]
        val pressed = action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE
        val changed = updatePointer(0, normalizedX, normalizedY, pressed)
        if (changed) {
            pointer.pressed = pressed
            markInputDirty()
        }
        if (!pressed && pointer.pressed) {
            pointer.pressed = false
            pointer.x = Float.NaN
            pointer.y = Float.NaN
        }
    }

    override fun onMouseButton(port: Int, button: Int, pressed: Boolean) {
        if (port !in 0 until PORT_COUNT) return
        val buttons = mouseButtons[port]
        val index = when (button) {
            1 -> 0
            2 -> 1
            3 -> 2
            else -> return
        }
        if (buttons[index] != pressed) {
            buttons[index] = pressed
            markInputDirty()
        }
    }

    private fun updateDpad(port: Int, xAxis: Float, yAxis: Float): Boolean {
        val state = dpadState[port]
        val newX = xAxis.toInt()
        val newY = yAxis.toInt()
        return if (state[0] != newX || state[1] != newY) {
            state[0] = newX
            state[1] = newY
            true
        } else {
            false
        }
    }

    private fun updateAnalog(values: FloatArray, offset: Int, xAxis: Float, yAxis: Float): Boolean {
        var changed = false
        if (abs(values[offset] - xAxis) > ANALOG_THRESHOLD) {
            values[offset] = xAxis
            changed = true
        }
        if (abs(values[offset + 1] - yAxis) > ANALOG_THRESHOLD) {
            values[offset + 1] = yAxis
            changed = true
        }
        return changed
    }

    private fun updatePointer(port: Int, xAxis: Float, yAxis: Float, pressed: Boolean): Boolean {
        val state = pointerState[port]
        var changed = false
        if (pressed) {
            if (state.x.isNaN() || abs(state.x - xAxis) > POINTER_THRESHOLD) {
                state.x = xAxis
                changed = true
            }
            if (state.y.isNaN() || abs(state.y - yAxis) > POINTER_THRESHOLD) {
                state.y = yAxis
                changed = true
            }
            if (!state.pressed) {
                state.pressed = true
                changed = true
            }
        } else if (state.pressed) {
            state.pressed = false
            state.x = Float.NaN
            state.y = Float.NaN
            changed = true
        }
        return changed
    }

    fun getStats(): String {
        val total = max(1L, totalFrames)
        val runAheadRatio = (runAheadFramesExecuted * 100) / total
        val skippedRatio = (skippedFrames * 100) / total
        return "enabled=$runAheadEnabled frames=$runAheadFrames supported=$supported total=$total runAhead=$runAheadFramesExecuted (${runAheadRatio}%) skipped=$skippedFrames (${skippedRatio}%)"
    }

    fun status(): StateFlow<RunAheadStatus> = statusFlow.asStateFlow()

    private fun publishStatus(resetCounters: Boolean = false) {
        if (resetCounters) {
            totalFrames = 0
            runAheadFramesExecuted = 0
            skippedFrames = 0
        }
        statusFlow.value = RunAheadStatus(
            enabled = runAheadEnabled,
            supported = supported,
            frames = runAheadFrames,
            framesExecuted = runAheadFramesExecuted,
            totalFrames = totalFrames,
            skippedFrames = skippedFrames
        )
    }

    data class RunAheadStatus(
        val enabled: Boolean,
        val supported: Boolean,
        val frames: Int,
        val framesExecuted: Long,
        val totalFrames: Long,
        val skippedFrames: Long
    ) {
        val effectiveFrames: Int get() = if (supported && enabled) frames else 0
        val hitRatio: Int get() = if (totalFrames == 0L) 0 else ((framesExecuted * 100) / totalFrames).toInt()
        val skipRatio: Int get() = if (totalFrames == 0L) 0 else ((skippedFrames * 100) / totalFrames).toInt()
    }
}
