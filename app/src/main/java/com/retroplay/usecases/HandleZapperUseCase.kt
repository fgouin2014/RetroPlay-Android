package com.retroplay.usecases

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.retroplay.CrosshairMode
import com.retroplay.CoreConfigManager
import com.retroplay.helpers.ZapperCoordinateHelper
import com.retroplay.config.RetroPlayConfigManager

/**
 * Use Case pour gérer les fonctionnalités zapper/lightgun.
 * Extracted from RetroArchEmulatorActivity to improve modularity and testability.
 * 
 * Gère:
 * - Actions lightgun depuis les overlays
 * - Envoi d'actions lightgun au core
 * - Toggle du mode crosshair (RetroPlay/FCEUmm/Both/None)
 */
class HandleZapperUseCase(
    private val context: Context,
    private val retroView: GLRetroView,
    private val prefs: SharedPreferences,
    private val console: String,
    private var customConfigId: String? = null,  // Config ID per-game (CRC ou PSX Serial)
    private var gameName: String = ""  // Game name for per-game config lookup
) {
    
    /**
     * Met à jour le customConfigId (appelé après chargement du jeu)
     */
    fun setCustomConfigId(configId: String?) {
        this.customConfigId = configId
        Log.d(TAG, "[ZAPPER] Custom config ID updated: $configId")
    }
    
    /**
     * Met à jour le gameName (appelé après chargement du jeu)
     */
    fun setGameName(name: String) {
        this.gameName = name
        Log.d(TAG, "[ZAPPER] Game name updated: $name")
    }
    
    companion object {
        private const val TAG = "HandleZapperUseCase"
    }
    
    /**
     * Interface pour les callbacks UI et états
     */
    interface ZapperCallbacks {
        fun runOnUiThread(action: Runnable)
        fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT)
        fun getCrosshairMode(): MutableState<CrosshairMode>
        fun getResources(): android.content.res.Resources
    }
    
    // P3: Quick tap detection - Compatible RetroArch android_check_quick_tap()
    // Stocke le timestamp du dernier tap pour détecter les taps rapides (< 200ms)
    private var lastZapperTapTime: Long = 0
    private var quickTapResetHandler: android.os.Handler? = null
    private val quickTapResetRunnable = Runnable {
        // Reset après 200ms si aucun nouveau tap (compatible RetroArch ligne 809-811)
        if (lastZapperTapTime > 0) {
            val timeSinceLastTap = android.os.SystemClock.elapsedRealtime() - lastZapperTapTime
            if (timeSinceLastTap >= 200) {
                lastZapperTapTime = 0
                Log.d(TAG, "[ZAPPER] Quick tap timer reset (>200ms)")
            }
        }
    }
    
    private var callbacks: ZapperCallbacks? = null
    
    fun setCallbacks(callbacks: ZapperCallbacks) {
        this.callbacks = callbacks
    }
    
    /**
     * Gère les actions lightgun depuis les overlays (gun_trigger, gun_reload, etc.)
     * Convertit le nom de l'action en ID numérique et envoie l'action au port lightgun configuré
     */
    fun handleLightgunAction(action: String) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ZapperCallbacks not set")
            return
        }
        
        // Charger les settings lightgun pour obtenir le port (selon orientation)
        val isLandscapeForSettings = callbacksRef.getResources().configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val orientationForSettings = if (isLandscapeForSettings) "landscape" else "portrait"
        val lightgunSettings = com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientationForSettings)
        val port = lightgunSettings.lightgunPort
        
        // Convertir le nom de l'action en ID numérique RetroArch
        val actionId = com.retroplay.overlay.models.RetroArchButtonMapping.lightgunActionToId(action)
        
        if (actionId == 0) {
            Log.w(TAG, "[LIGHTGUN] Unknown lightgun action: $action")
            return
        }
        
        // Envoyer l'action au port lightgun
        sendLightgunAction(actionId, port)
        Log.i(TAG, "[LIGHTGUN] Action '$action' (id=$actionId) sent to port ${port + 1}")
    }
    
    /**
     * Gère les actions multi-touch configurables (2/3/4 doigts)
     * Compatible RetroArch configuration.c lignes 2652-2654
     * 
     * @param event Touch event contenant le nombre de doigts
     */
    fun handleMultiTouchActions(event: android.view.MotionEvent) {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ZapperCallbacks not set")
            return
        }
        
        // Ne traiter que les événements DOWN (pas MOVE/UP) pour éviter déclenchements multiples
        if (event.actionMasked != android.view.MotionEvent.ACTION_DOWN && 
            event.actionMasked != android.view.MotionEvent.ACTION_POINTER_DOWN) {
            return
        }
        
        // Get orientation from context
        val isLandscapeForSettings = callbacksRef.getResources().configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val orientationForSettings = if (isLandscapeForSettings) "landscape" else "portrait"
        val lightgunSettings = com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientationForSettings)
        val fingerCount = event.pointerCount
        
        // Support multi-touch (2/3/4 doigts) - Compatible RetroArch overlay_lightgun_action enum
        if (fingerCount > 1 && fingerCount <= 4) {
            val actionId = when (fingerCount) {
                2 -> lightgunSettings.lightgunTwoTouchInput
                3 -> lightgunSettings.lightgunThreeTouchInput
                4 -> lightgunSettings.lightgunFourTouchInput
                else -> 0
            }
            
            if (actionId > 0) {
                sendLightgunAction(actionId, lightgunSettings.lightgunPort)
                // Convertir actionId en nom d'action pour le log
                val actionName = when (actionId) {
                    1 -> "gun_trigger"
                    2 -> "gun_reload"
                    3 -> "gun_aux_a"
                    4 -> "gun_aux_b"
                    5 -> "gun_aux_c"
                    6 -> "gun_start"
                    7 -> "gun_select"
                    8 -> "gun_dpad_up"
                    9 -> "gun_dpad_down"
                    10 -> "gun_dpad_left"
                    11 -> "gun_dpad_right"
                    else -> "unknown($actionId)"
                }
                Log.i(TAG, "[ZAPPER] Multi-touch: $fingerCount fingers → action $actionId ($actionName)")
            } else {
                Log.d(TAG, "[ZAPPER] Multi-touch: $fingerCount fingers detected but no action configured (actionId=0)")
            }
        }
    }
    
    /**
     * Envoie une action lightgun configurée (multi-touch)
     * Mapping selon RetroArch overlay system
     * 
     * @param actionId ID de l'action (1=TRIGGER, 2=RELOAD, 3=AUX_A, etc.)
     * @param port Port du lightgun
     */
    fun sendLightgunAction(actionId: Int, port: Int) {
        val keyCode = when (actionId) {
            1 -> android.view.KeyEvent.KEYCODE_BUTTON_A       // LIGHTGUN_TRIGGER (utilise BUTTON_A comme trigger)
            2 -> android.view.KeyEvent.KEYCODE_BUTTON_SELECT  // LIGHTGUN_RELOAD (offscreen shot)
            3 -> android.view.KeyEvent.KEYCODE_BUTTON_A       // LIGHTGUN_AUX_A
            4 -> android.view.KeyEvent.KEYCODE_BUTTON_B       // LIGHTGUN_AUX_B
            5 -> android.view.KeyEvent.KEYCODE_BUTTON_X       // LIGHTGUN_AUX_C
            6 -> android.view.KeyEvent.KEYCODE_BUTTON_START   // LIGHTGUN_START
            7 -> android.view.KeyEvent.KEYCODE_BUTTON_SELECT  // LIGHTGUN_SELECT
            8 -> android.view.KeyEvent.KEYCODE_DPAD_UP        // LIGHTGUN_DPAD_UP
            9 -> android.view.KeyEvent.KEYCODE_DPAD_DOWN      // LIGHTGUN_DPAD_DOWN
            10 -> android.view.KeyEvent.KEYCODE_DPAD_LEFT     // LIGHTGUN_DPAD_LEFT
            11 -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT    // LIGHTGUN_DPAD_RIGHT
            else -> return  // 0 = none
        }
        
        // Envoyer pulse rapide
        try {
            retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode, port)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, keyCode, port)
                } catch (e: Exception) {
                    Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendKeyEvent (UP) failed: ${e.message}", e)
                }
            }, 50)
        } catch (e: Exception) {
            Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendKeyEvent (DOWN) failed: ${e.message}", e)
        }
        
        Log.d(TAG, "[ZAPPER] Multi-touch action $actionId sent: keyCode=$keyCode on port ${port+1}")
    }
    
    /**
     * Envoie un signal de trigger lightgun au core
     * Simule un appui/relâchement rapide du bouton A sur le port lightgun
     * 
     * @param port Port du lightgun (index 0-3)
     * @param delayMs Délai avant déclenchement (0 = immédiat)
     */
    fun sendLightgunTrigger(port: Int, delayMs: Int) {
        val sendTrigger = Runnable {
            // Envoyer un pulse rapide de BUTTON_A (DOWN puis UP)
            try {
                retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A, port)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_A, port)
                    } catch (e: Exception) {
                        Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendKeyEvent (trigger UP) failed: ${e.message}", e)
                    }
                }, 50)  // 50ms pulse
                
                Log.d(TAG, "[ZAPPER] Trigger FIRED on port ${port+1} (delay: ${delayMs}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendKeyEvent (trigger DOWN) failed: ${e.message}", e)
            }
        }
        
        if (delayMs > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(sendTrigger, delayMs.toLong())
        } else {
            sendTrigger.run()
        }
    }
    
    /**
     * Toggle Crosshair Mode (Cycle entre RetroPlay / FCEUmm / Both / None)
     */
    fun toggleCrosshairMode() {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ZapperCallbacks not set")
            return
        }
        
        val crosshairMode = callbacksRef.getCrosshairMode()
        crosshairMode.value = crosshairMode.value.next()
        Log.i(TAG, "[CROSSHAIR] Mode: ${crosshairMode.value.displayName}")
        
        // Sauvegarder dans SharedPreferences
        prefs.edit().putString("emulation_crosshair_mode", crosshairMode.value.name).apply()
        
        // Si on est en jeu NES, mettre à jour la config du core dynamiquement
        if (console == "nes") {
            val config = CoreConfigManager.loadConfig(context, "FCEUmm").toMutableMap()
            config["fceumm_show_crosshair"] = if (crosshairMode.value.showFCEUmmCrosshair()) "enabled" else "disabled"
            CoreConfigManager.saveConfig(context, "FCEUmm", config)
            
            // Appliquer au core sans redémarrer
            val nesVariables = config.map { (key, value) -> com.swordfish.libretrodroid.Variable(key, value) }.toTypedArray()
            retroView.updateVariables(*nesVariables)
            Log.i(TAG, "[CROSSHAIR] Updated fceumm_show_crosshair = ${config["fceumm_show_crosshair"]}")
        }
        
        callbacksRef.runOnUiThread {
            callbacksRef.showToast(
                "Crosshair: ${crosshairMode.value.displayName}",
                Toast.LENGTH_SHORT
            )
        }
    }
    
    /**
     * Gestion des touches Zapper - Envoie position POINTER + trigger au port configuré
     * 
     * CRITIQUE: Cette méthode est essentielle pour le fonctionnement du zapper.
     * Toute modification doit être testée rigoureusement.
     * 
     * Utilise RETRO_DEVICE_POINTER (6) pour envoyer coordonnées exactes au core FCEUmm
     * 
     * P3: Quick tap detection - Détecte les taps rapides (< 200ms) pour améliorer réactivité
     * Compatible RetroArch android_check_quick_tap() (lignes 805-815)
     * 
     * @param event Touch event
     * @param gameViewBounds Bounds exacts du GLRetroView (zone de jeu)
     * @param triggerOnTouch Si true, tir instantané au DOWN, sinon au UP
     * @param allowOffscreen Si false, clamp position aux bounds
     * @param triggerDelay Délai en ms avant déclenchement du trigger
     * @param lightgunPort Port du lightgun (0-3, -1 = tous)
     * @return true si l'événement a été traité
     */
    fun handleZapperTouch(
        event: android.view.MotionEvent,
        gameViewBounds: Rect?,
        triggerOnTouch: Boolean = true,
        allowOffscreen: Boolean = true,
        triggerDelay: Int = 0,
        lightgunPort: Int = 1,  // Port 2 (index 1) = Zapper NES traditionnel
        pulseDuration: Int = 16  // Durée du pulse en ms (default: 16ms = 1 frame)
    ): Boolean {
        val callbacksRef = callbacks ?: run {
            Log.e(TAG, "ZapperCallbacks not set")
            return false
        }
        
        // CRITIQUE: Vérifier que retroView est valide avant d'utiliser
        try {
            // Test simple pour vérifier que retroView n'est pas null ou dans un état invalide
            retroView.hashCode()  // Si null, cela lancera une exception
        } catch (e: Exception) {
            Log.e(TAG, "[ZAPPER] CRASH PREVENTION: retroView is null or invalid: ${e.message}", e)
            return false
        }
        
        // CRITIQUE: Si lightgunPort = -1 (all ports), utiliser le port par défaut (1 = port 2 NES)
        val actualPort = if (lightgunPort < 0) 1 else lightgunPort.coerceIn(0, 3)
        
        Log.d(TAG, "[ZAPPER] handleZapperTouch: lightgunPort=$lightgunPort, actualPort=$actualPort, console=$console")
        
        // Vérifier si le zapper est configuré (manuellement OU via RetroPlayConfigManager)
        val isZapperConfigured = isZapperConfigured(console, prefs, customConfigId)
        
        Log.d(TAG, "[ZAPPER] Configuration check: isZapperConfigured=$isZapperConfigured, customConfigId=$customConfigId, gameName=$gameName")
        
        // Permettre le zapper uniquement si configuré
        if (!isZapperConfigured) {
            Log.w(TAG, "[ZAPPER] Zapper not configured - check logs above for details. Ignoring touch.")
            return false
        }
        
        Log.i(TAG, "[ZAPPER] Zapper is configured - processing touch on port $actualPort")
        
        // CRITIQUE: Pour Chiller (port 0), vérifier que le device type est correctement configuré
        if (actualPort == 0) {
            Log.i(TAG, "[ZAPPER] Chiller mode detected (port 0) - ensuring device type is configured correctly")
        }
        
        // Vérifier si bounds disponibles
        val bounds = gameViewBounds
        if (bounds == null) {
            Log.w(TAG, "[ZAPPER] GLRetroView bounds not available yet, ignoring touch")
            return false
        }
        
        // Touch coordinates (écran)
        val touchX = event.x
        val touchY = event.y
        
        // ⚠️ CRITIQUE: Utiliser le helper pour les calculs de coordonnées
        // La logique de trigger/button reste dans le UseCase pour éviter les régressions
        val coords = ZapperCoordinateHelper.calculateZapperCoordinates(
            touchX = touchX,
            touchY = touchY,
            gameViewBounds = bounds,
            retroView = retroView,
            console = console,
            context = context,
            allowOffscreen = allowOffscreen
        ) ?: return false
        
        // Extraire les valeurs du helper
        val relativeX = coords.relativeX
        val relativeY = coords.relativeY
        val gameWidth = coords.gameWidth
        val gameHeight = coords.gameHeight
        val coreAspectRatio = coords.coreAspectRatio
        val actualViewport = coords.actualViewport
        
        // Calculer les valeurs pour le debug (conversion inverse FCEUmm)
        val viewportTop = actualViewport.top * bounds.height
        val viewportBottom = actualViewport.bottom * bounds.height
        val viewportLeft = actualViewport.left * bounds.width
        val viewportRight = actualViewport.right * bounds.width
        val viewportWidth = viewportRight - viewportLeft
        val viewportHeight = viewportBottom - viewportTop
        
        // Conversion FCEUmm pour debug (déjà calculée dans le helper)
        val config = if (console == "nes") {
            CoreConfigManager.loadConfig(context, "FCEUmm")
        } else {
            emptyMap()
        }
        
        // CONVERSION INVERSE: Où FCEUmm pense qu'on vise en pixels écran (pour debug)
        val fceummCrosshairXInViewport = (coords.fceummX / gameWidth.toFloat()) * viewportWidth + viewportLeft
        val fceummCrosshairYInViewport = (coords.fceummY / gameHeight.toFloat()) * viewportHeight + viewportTop
        val fceummCrosshairXOnScreen = fceummCrosshairXInViewport + bounds.left
        val fceummCrosshairYOnScreen = fceummCrosshairYInViewport + bounds.top
        
        // Calculer l'écart entre notre touch et où FCEUmm pense qu'on vise
        val deltaX = touchX - fceummCrosshairXOnScreen
        val deltaY = touchY - fceummCrosshairYOnScreen
        
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                // Envoyer position POINTER au core (pour coordonnées X/Y)
                try {
                    retroView.sendMotionEvent(
                        LibretroDroid.MOTION_SOURCE_POINTER,
                        relativeX,  // 0.0 à 1.0 (LibretroDroid convertit)
                        relativeY,  // 0.0 à 1.0
                        actualPort  // Port configuré (index 0-3), corrigé si -1
                    )
                    Log.d(TAG, "[ZAPPER] sendMotionEvent: POINTER(x=$relativeX, y=$relativeY, port=$actualPort) sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendMotionEvent failed: ${e.message}", e)
                    return false
                }
                
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    // P3: Quick tap detection - Compatible RetroArch android_check_quick_tap() (lignes 805-815)
                    val currentTime = android.os.SystemClock.elapsedRealtime()
                    val timeSinceLastTap = if (lastZapperTapTime > 0) currentTime - lastZapperTapTime else Long.MAX_VALUE
                    val isQuickTap = timeSinceLastTap < 200
                    lastZapperTapTime = currentTime
                    
                    // Reset le handler précédent et programmer un nouveau reset après 200ms
                    quickTapResetHandler?.removeCallbacks(quickTapResetRunnable)
                    quickTapResetHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    quickTapResetHandler?.postDelayed(quickTapResetRunnable, 200)
                    
                    if (isQuickTap && timeSinceLastTap != Long.MAX_VALUE) {
                        Log.d(TAG, "[ZAPPER] Quick tap detected (${timeSinceLastTap}ms < 200ms)")
                    }
                    
                    // NOTE: En mode RetroPointer, le trigger est AUTOMATIQUE via POINTER_PRESSED
                    // FCEUmm lit: input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED)
                    // LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0) ? 1 : 0
                    // MAIS: sendMouseButton peut être nécessaire en complément pour certains cas
                    // Les coordonnées sont déjà envoyées via sendMotionEvent ci-dessus
                    
                    // Déclencher le trigger selon l'option triggerOnTouch
                    if (triggerOnTouch) {
                        // Envoyer sendMouseButton en complément de POINTER_PRESSED pour meilleure compatibilité
                                try {
                                    retroView.sendMouseButton(
                                        LibretroDroid.MOUSE_BUTTON_LEFT,
                                        true,  // Pressed
                                        actualPort
                                    )
                            Log.d(TAG, "[ZAPPER] MOUSE BUTTON LEFT pressed on port $actualPort (triggerOnTouch=true, complement to POINTER_PRESSED)")
                                    
                            // Release après pulseDuration (configurable depuis la config per-game)
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            retroView.sendMouseButton(
                                                LibretroDroid.MOUSE_BUTTON_LEFT,
                                                false,  // Released
                                                actualPort
                                            )
                                    Log.d(TAG, "[ZAPPER] MOUSE BUTTON LEFT released after ${pulseDuration}ms pulse (port=$actualPort)")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendMouseButton (release) failed: ${e.message}", e)
                                        }
                                    }, pulseDuration.toLong())
                                } catch (e: Exception) {
                                    Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendMouseButton (press) failed: ${e.message}", e)
                                }
                        } else {
                        Log.d(TAG, "[ZAPPER] Touch DOWN registered, waiting for UP to trigger (triggerOnTouch=false)")
                    }
                    
                    if (actualPort == 0) {
                        Log.i(TAG, "[ZAPPER] Chiller (port 0): POINTER_PRESSED + sendMouseButton sent")
                    }
                    
                    // Utiliser le helper pour le logging des conversions
                    ZapperCoordinateHelper.logCoordinateConversions(
                        touchX = touchX,
                        touchY = touchY,
                        bounds = bounds,
                        coords = coords,
                        config = config
                    )
                    Log.d(TAG, "  15. FCEUmm crosshair (screen): (${fceummCrosshairXOnScreen.toInt()}, ${fceummCrosshairYOnScreen.toInt()})")
                    Log.d(TAG, "  16. Delta (touch - FCEUmm): (${deltaX.toInt()}px, ${deltaY.toInt()}px) ${if (deltaY > 0) "FCEUmm trop BAS" else if (deltaY < 0) "FCEUmm trop HAUT" else "ALIGNÉ"}")
                    Log.d(TAG, "  17. Port: $actualPort | PRESSED: ${relativeX >= 0f && relativeY >= 0f}")
                    
                    // NOTE: En mode RetroPointer, le trigger est AUTOMATIQUE via POINTER_PRESSED
                    // FCEUmm lit: input_cb(port, RETRO_DEVICE_POINTER, 0, RETRO_DEVICE_ID_POINTER_PRESSED)
                    // LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0) ? 1 : 0
                    // Donc pas besoin d'envoyer BUTTON_A! Le trigger est automatique!
                    
                    // Gérer multi-touch (2/3/4 doigts) pour actions START/SELECT/etc.
                    handleMultiTouchActions(event)
                }
                return true
            }
            
            android.view.MotionEvent.ACTION_UP -> {
                try {
                    // Si triggerOnTouch = false, déclencher le trigger MAINTENANT (au release)
                    if (!triggerOnTouch) {
                        try {
                            retroView.sendMouseButton(
                                LibretroDroid.MOUSE_BUTTON_LEFT,
                                true,  // Pressed
                                actualPort
                            )
                            Log.i(TAG, "[ZAPPER] MOUSE_BUTTON_LEFT pressed on port $actualPort (triggerOnTouch=false, firing on UP)")
                            
                            // Attendre triggerDelay si configuré
                            if (triggerDelay > 0) {
                                Thread.sleep(triggerDelay.toLong())
                            }
                            
                            // Release immédiat
                            retroView.sendMouseButton(
                                LibretroDroid.MOUSE_BUTTON_LEFT,
                                false,  // Released
                                actualPort
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendMouseButton (UP) failed: ${e.message}", e)
                        }
                    }
                    
                    // CRITIQUE: Envoyer coordonnées NÉGATIVES pour forcer POINTER_PRESSED = 0
                    // LibretroDroid calcule: POINTER_PRESSED = (X >= 0 && Y >= 0) ? 1 : 0
                    // Si on ne fait pas ça, les coordonnées restent en mémoire et POINTER_PRESSED reste à 1!
                    try {
                        retroView.sendMotionEvent(
                            LibretroDroid.MOTION_SOURCE_POINTER,
                            -1f,  // X négatif → pointerScreenXAxis < 0
                            -1f,  // Y négatif → pointerScreenYAxis < 0
                            actualPort
                        )
                        Log.d(TAG, "[ZAPPER] Touch UP - POINTER reset to (-1, -1), POINTER_PRESSED now FALSE (port=$actualPort)")
                        
                        if (actualPort == 0) {
                            Log.i(TAG, "[ZAPPER] Chiller (port 0): POINTER reset to (-1, -1) - trigger désactivé")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ZAPPER] CRASH PREVENTION: sendMotionEvent (UP) failed: ${e.message}", e)
                    }
                    
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "[ZAPPER] CRASH PREVENTION: ACTION_UP handler failed: ${e.message}", e)
                    return false
                }
            }
            
            else -> return false
        }
    }
    
    /**
     * Vérifie si le zapper est configuré manuellement (via SharedPreferences ou RetroPlayConfigManager)
     * @param customConfigId Config ID per-game (CRC ou PSX Serial) pour vérifier la config per-game
     */
    private fun isZapperConfigured(console: String, prefs: SharedPreferences, customConfigId: String? = null): Boolean {
        Log.d(TAG, "[ZAPPER] Checking configuration: console=$console, gameName=$gameName, customConfigId=$customConfigId")
        
        // 1. Vérifier configuration manuelle dans SharedPreferences (controller ports)
        // Priority: console_config then compose_gamepad_settings (comme dans ControllerHelper)
        val consoleConfigPrefs = context.getSharedPreferences("console_config", android.content.Context.MODE_PRIVATE)
        val composePrefs = context.getSharedPreferences("compose_gamepad_settings", android.content.Context.MODE_PRIVATE)
        
        for (port in 0..3) {
            val portKey = "controller_port_${console}_port${port}"
            // Priority: console_config then compose_gamepad_settings
            var controllerType = consoleConfigPrefs.getInt(portKey, -1)
            if (controllerType == -1) {
                controllerType = composePrefs.getInt(portKey, -1)
            }
            // Vérifier tous les périphériques qui nécessitent le touchscreen
            if (controllerType == 258 || // RETRO_DEVICE_ZAPPER (NES)
                controllerType == 4 ||   // RETRO_DEVICE_LIGHTGUN (PSX Guncon, Genesis Menacer, etc.)
                controllerType == 260) { // RETRO_DEVICE_SUPERSCOPE (SNES)
                val deviceName = when (controllerType) {
                    258 -> "Zapper"
                    4 -> "Lightgun/Guncon"
                    260 -> "SuperScope"
                    else -> "Type $controllerType"
                }
                Log.i(TAG, "[PERIPHERAL] $deviceName configured manually on port $port (controller_port_${console}_port${port})")
                return true
            }
        }
        
        // 2. Vérifier configuration dans RetroPlayConfigManager (per-game PRIORITÉ sur global)
        try {
            // Vérifier config per-game si gameName est disponible
            if (gameName.isNotEmpty()) {
                val effectiveConfig = RetroPlayConfigManager.getEffectiveConfig(console, gameName)
                Log.d(TAG, "[ZAPPER] Effective config for $gameName: zapperEnabled=${effectiveConfig.zapperEnabled}, zapperPort=${effectiveConfig.zapperPort}")
                if (effectiveConfig.zapperEnabled) {
                    Log.i(TAG, "[ZAPPER] Zapper enabled in per-game config (game: $gameName, port: ${effectiveConfig.zapperPort})")
                    return true
                }
            }
            
            // Fallback: vérifier config globale
            val globalConfig = RetroPlayConfigManager.loadConfig(console)
            Log.d(TAG, "[ZAPPER] Global config: zapperEnabled=${globalConfig.zapperEnabled}, zapperPort=${globalConfig.zapperPort}")
            if (globalConfig.zapperEnabled) {
                Log.i(TAG, "[ZAPPER] Zapper enabled in global config (port: ${globalConfig.zapperPort})")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ZAPPER] Error checking RetroPlayConfigManager: ${e.message}", e)
        }
        
        Log.w(TAG, "[ZAPPER] Zapper NOT configured - no manual port config and no RetroPlayConfigManager config")
        return false
    }
}
















