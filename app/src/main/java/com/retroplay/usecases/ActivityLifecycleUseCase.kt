package com.retroplay.usecases

import android.app.Activity
import android.util.Log

/**
 * Use Case pour gérer le cycle de vie de l'activité (fin sécurisée).
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 */
class ActivityLifecycleUseCase {
    
    companion object {
        private const val TAG = "ActivityLifecycleUseCase"
    }
    
    /**
     * Interface pour les callbacks
     */
    interface LifecycleCallbacks {
        fun finishActivity()
    }
    
    private var callbacks: LifecycleCallbacks? = null
    
    fun setCallbacks(callbacks: LifecycleCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Termine l'activité de manière sécurisée.
     * MAME2010 a un bug dans son destructeur, on doit donc utiliser killProcess().
     * 
     * @param currentCore Nom du core actuel (pour détecter MAME2010)
     * @param delayMs Délai en millisecondes avant de terminer (pour laisser les dialogs s'afficher)
     */
    fun safeFinishActivity(currentCore: String?, delayMs: Long = 0) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "LifecycleCallbacks not set")
            return
        }
        
        val isMame2010 = currentCore?.contains("mame2010", ignoreCase = true) == true
        
        if (delayMs > 0) {
            // Retarder la fermeture pour laisser le temps aux dialogs de s'afficher
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isMame2010) {
                    Log.w(TAG, "⚠️ MAME2010 detected - using process kill to avoid destructor crash")
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else {
                    Log.i(TAG, "✓ Normal activity finish (returning to GameDetails)")
                    callbacksRef.finishActivity()
                }
            }, delayMs)
        } else {
            if (isMame2010) {
                Log.w(TAG, "⚠️ MAME2010 detected - using process kill to avoid destructor crash")
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                Log.i(TAG, "✓ Normal activity finish (returning to GameDetails)")
                callbacksRef.finishActivity()
            }
        }
    }
}
















