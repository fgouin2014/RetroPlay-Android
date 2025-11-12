package com.retroplay.rewind

import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gestionnaire Rewind pour RetroPlay.
 *
 * - Capture périodiquement les savestates du core (serializeState)
 * - Stocke les états dans un buffer circulaire borné en mémoire
 * - Permet de rejouer les états en arrière (unserializeState)
 */
class RewindManager(
    private val retroView: GLRetroView,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "RewindManager"
        private const val DEFAULT_REWIND_STEP_DELAY_MS = 16L
        private const val MIN_BUFFER_BYTES = 1 * 1024 * 1024 // 1 MB
    }

    private val states = ArrayDeque<ByteArray>()
    private val mutex = Mutex()
    private val captureInFlight = AtomicBoolean(false)

    private var totalBytes: Int = 0
    private var frameCounter: Int = 0
    private var granularity: Int = 1
    private var maxBufferBytes: Int = 10 * 1024 * 1024
    private var rewindJob: Job? = null
    private var supportChecked = false

    private val _isSupported = MutableStateFlow(true)
    val isSupported: StateFlow<Boolean> = _isSupported.asStateFlow()

    private val _isRewinding = MutableStateFlow(false)
    val isRewinding: StateFlow<Boolean> = _isRewinding.asStateFlow()

    private val _availableStates = MutableStateFlow(0)
    val availableStates: StateFlow<Int> = _availableStates.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun configure(enabled: Boolean, bufferSizeBytes: Int, granularity: Int) {
        _enabled.value = enabled
        maxBufferBytes = bufferSizeBytes.coerceAtLeast(MIN_BUFFER_BYTES)
        this.granularity = granularity.coerceAtLeast(1)
        if (!enabled) {
            stopRewind()
            clearBuffer()
        }
    }

    suspend fun ensureSupport(): Boolean {
        if (supportChecked) {
            return _isSupported.value
        }
        supportChecked = true
        val state = withContext(Dispatchers.IO) {
            try {
                retroView.serializeState()
            } catch (e: Exception) {
                Log.e(TAG, "serializeState failed during support check", e)
                ByteArray(0)
            }
        }
        val supported = state.isNotEmpty()
        _isSupported.value = supported
        if (!supported) {
            Log.w(TAG, "Core does not support serializeState; disabling Rewind")
            clearBuffer()
        }
        return supported
    }

    fun reset() {
        stopRewind()
        clearBuffer()
        frameCounter = 0
        supportChecked = false
        _isSupported.value = true
    }

    fun onFrameRendered() {
        if (!_enabled.value || !_isSupported.value || _isRewinding.value) {
            return
        }
        frameCounter++
        if (frameCounter % granularity != 0) {
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                captureState()
            } finally {
                captureInFlight.set(false)
            }
        }
    }

    fun startRewind(stepDelayMs: Long = DEFAULT_REWIND_STEP_DELAY_MS): Boolean {
        if (!_enabled.value || !_isSupported.value) {
            return false
        }
        if (_availableStates.value == 0) {
            Log.d(TAG, "No states in rewind buffer")
            return false
        }
        if (_isRewinding.value) {
            return true
        }
        _isRewinding.value = true
        rewindJob?.cancel()
        rewindJob = scope.launch(Dispatchers.Default) {
            while (_isRewinding.value) {
                val state = mutex.withLock {
                    if (states.isEmpty()) {
                        null
                    } else {
                        val buffer = states.removeLast()
                        totalBytes -= buffer.size
                        _availableStates.value = states.size
                        buffer
                    }
                }

                if (state == null) {
                    _isRewinding.value = false
                    break
                }

                val success = withContext(Dispatchers.IO) {
                    try {
                        retroView.unserializeState(state)
                    } catch (e: Exception) {
                        Log.e(TAG, "unserializeState failed during rewind", e)
                        false
                    }
                }

                if (!success) {
                    _isRewinding.value = false
                    break
                }

                delay(stepDelayMs)
            }
        }
        return true
    }

    fun stopRewind() {
        if (!_isRewinding.value) {
            return
        }
        _isRewinding.value = false
        rewindJob?.cancel()
        rewindJob = null
    }

    fun availableDurationSeconds(fps: Int = 60): Float {
        if (fps <= 0) return 0f
        val frames = _availableStates.value * granularity
        return frames.toFloat() / fps.toFloat()
    }

    private suspend fun captureState() {
        val state = try {
            retroView.serializeState()
        } catch (e: Exception) {
            Log.e(TAG, "serializeState failed during capture", e)
            ByteArray(0)
        }

        if (state.isEmpty()) {
            // Sauvegarde invalide → désactiver Rewind pour ce core
            _isSupported.value = false
            clearBuffer()
            return
        }

        mutex.withLock {
            states.addLast(state)
            totalBytes += state.size
            while (totalBytes > maxBufferBytes && states.isNotEmpty()) {
                val removed = states.removeFirst()
                totalBytes -= removed.size
            }
            _availableStates.value = states.size
        }
    }

    private fun clearBuffer() {
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                states.clear()
                totalBytes = 0
                _availableStates.value = 0
            }
        }
    }
}

