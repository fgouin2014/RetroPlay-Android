package com.retroplay.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * ViewModel pour l'interface KITT
 * Gère les états et les animations du système KITT
 */
class KittViewModel : ViewModel() {
    
    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage
    
    private val _isKittActive = MutableLiveData<Boolean>()
    val isKittActive: LiveData<Boolean> = _isKittActive
    
    private val _isScannerActive = MutableLiveData<Boolean>()
    val isScannerActive: LiveData<Boolean> = _isScannerActive
    
    private val _vumeterLevel = MutableLiveData<Float>()
    val vumeterLevel: LiveData<Float> = _vumeterLevel
    
    init {
        _isKittActive.value = false
        _isScannerActive.value = false
        _vumeterLevel.value = 0f
        _statusMessage.value = "KITT STANDBY"
    }
    
    fun startKitt() {
        _isKittActive.value = true
        _statusMessage.value = "KITT ACTIVATED"
    }
    
    fun stopKitt() {
        _isKittActive.value = false
        _statusMessage.value = "KITT DEACTIVATED"
    }
    
    fun startScanner() {
        _isScannerActive.value = true
        _statusMessage.value = "SCANNER ACTIVE"
    }
    
    fun stopScanner() {
        _isScannerActive.value = false
        _statusMessage.value = "SCANNER INACTIVE"
    }
    
    fun updateVumeterLevel(level: Float) {
        _vumeterLevel.value = level
    }
    
    fun checkStatus() {
        val status = when {
            _isKittActive.value == true && _isScannerActive.value == true -> "ALL SYSTEMS ACTIVE"
            _isKittActive.value == true -> "KITT ACTIVE - SCANNER STANDBY"
            else -> "ALL SYSTEMS STANDBY"
        }
        _statusMessage.value = status
    }
}
