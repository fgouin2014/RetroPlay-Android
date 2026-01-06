package com.retroplay.managers

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid

/**
 * Manager centralisé pour gérer tous les inputs de manière unifiée.
 * Extracted from RetroArchEmulatorActivity to improve modularity.
 * 
 * Gère:
 * - Envoi d'événements clavier
 * - Envoi d'événements de mouvement (pointer)
 * - Envoi d'événements souris
 * - Gestion des ports de contrôleurs
 */
class InputManager(
    private val retroView: GLRetroView
) {
    
    companion object {
        private const val TAG = "InputManager"
    }
    
    /**
     * Envoie un événement clavier au core
     * 
     * @param action Action (ACTION_DOWN ou ACTION_UP)
     * @param keyCode Code de la touche
     * @param port Port du contrôleur (0-3)
     */
    fun sendKeyEvent(action: Int, keyCode: Int, port: Int = 0) {
        try {
            retroView.sendKeyEvent(action, keyCode, port)
            Log.d(TAG, "Key event sent: action=$action, keyCode=$keyCode, port=$port")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending key event", e)
        }
    }
    
    /**
     * Envoie un événement de mouvement (pointer) au core
     * 
     * @param source Source du mouvement (MOTION_SOURCE_POINTER, etc.)
     * @param x Coordonnée X normalisée (0.0-1.0)
     * @param y Coordonnée Y normalisée (0.0-1.0)
     * @param port Port du contrôleur (0-3)
     */
    fun sendMotionEvent(source: Int, x: Float, y: Float, port: Int = 0) {
        try {
            retroView.sendMotionEvent(source, x, y, port)
            Log.d(TAG, "Motion event sent: source=$source, x=$x, y=$y, port=$port")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending motion event", e)
        }
    }
    
    /**
     * Envoie un événement de bouton souris au core
     * 
     * @param button Bouton (MOUSE_BUTTON_LEFT, MOUSE_BUTTON_RIGHT, etc.)
     * @param pressed true si pressé, false si relâché
     * @param port Port du contrôleur (0-3)
     */
    fun sendMouseButton(button: Int, pressed: Boolean, port: Int = 0) {
        try {
            retroView.sendMouseButton(button, pressed, port)
            Log.d(TAG, "Mouse button sent: button=$button, pressed=$pressed, port=$port")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mouse button", e)
        }
    }
    
    /**
     * Envoie un pulse rapide d'un bouton (appui puis relâchement)
     * 
     * @param keyCode Code de la touche
     * @param port Port du contrôleur (0-3)
     * @param delayMs Délai avant déclenchement (0 = immédiat)
     * @param pulseDurationMs Durée du pulse en ms
     */
    fun sendKeyPulse(
        keyCode: Int,
        port: Int = 0,
        delayMs: Int = 0,
        pulseDurationMs: Int = 50
    ) {
        val sendPulse = Runnable {
            sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, port)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendKeyEvent(KeyEvent.ACTION_UP, keyCode, port)
            }, pulseDurationMs.toLong())
        }
        
        if (delayMs > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(sendPulse, delayMs.toLong())
        } else {
            sendPulse.run()
        }
    }
    
    /**
     * Envoie un pulse rapide d'un bouton souris
     * 
     * @param button Bouton souris
     * @param port Port du contrôleur (0-3)
     * @param delayMs Délai avant déclenchement (0 = immédiat)
     * @param pulseDurationMs Durée du pulse en ms
     */
    fun sendMouseButtonPulse(
        button: Int,
        port: Int = 0,
        delayMs: Int = 0,
        pulseDurationMs: Int = 50
    ) {
        val sendPulse = Runnable {
            sendMouseButton(button, true, port)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendMouseButton(button, false, port)
            }, pulseDurationMs.toLong())
        }
        
        if (delayMs > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(sendPulse, delayMs.toLong())
        } else {
            sendPulse.run()
        }
    }
}
















