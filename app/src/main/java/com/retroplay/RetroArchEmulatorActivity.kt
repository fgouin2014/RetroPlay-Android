package com.retroplay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.view.KeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.ChainStyle
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.Variable
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.layouts.*
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager

import com.retroplay.database.GameInfo
import com.retroplay.database.DatabaseManager
import com.retroplay.database.SmartConfigManager
import java.io.File
import android.os.Environment

import com.swordfish.libretrodroid.ShaderConfig
import gg.padkit.PadKit
import gg.padkit.inputevents.InputEvent
import gg.padkit.ids.Id

import com.retroplay.ui.screens.ComposeEmulatorScreen
import com.retroplay.GamePadLayoutManager

import androidx.compose.ui.util.lerp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.compose.runtime.LaunchedEffect
import com.retroplay.viewmodels.RetroArchEmulatorViewModel

import java.io.FileOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.retroplay.ScreenshotManager
import com.retroplay.gallery.ScreenshotRepository
import com.retroplay.rewind.RewindManager
import com.retroplay.config.RetroPlayConfigManager
import com.retroplay.helpers.EmulatorConfigHelper
import com.retroplay.helpers.ControllerHelper
import com.retroplay.helpers.CoreDisplayHelper
import com.retroplay.helpers.UriFileHelper
import com.retroplay.helpers.ZapperCoordinateHelper
import com.retroplay.managers.DialogStateManager
import com.retroplay.managers.OverlayManager
import com.retroplay.managers.CoreManager
import com.retroplay.managers.InputManager
import com.retroplay.usecases.ApplyCheatUseCase
import com.retroplay.usecases.LoadCoreUseCase
import com.retroplay.usecases.LoadOverlayUseCase
import com.retroplay.usecases.TakeScreenshotUseCase
import com.retroplay.usecases.SaveStateUseCase
import com.retroplay.usecases.EmulatorControlsUseCase
import com.retroplay.usecases.HandleHotkeyUseCase
import com.retroplay.usecases.HandleZapperUseCase
import com.retroplay.usecases.CorePathResolverUseCase
import com.retroplay.usecases.LoadCoreVariablesUseCase
import com.retroplay.usecases.CheatManagerUseCase
import com.retroplay.usecases.ConfigFileHandlerUseCase
import com.retroplay.usecases.ActivityLifecycleUseCase
import com.retroplay.usecases.FullscreenSetupUseCase
import com.retroplay.CrosshairMode
import com.retroplay.CoreVariable
import androidx.compose.runtime.MutableState

/**
 * RetroArch Emulator Activity
 * 
 * Features:
 * - Jetpack Compose UI for dialogs/menus
 * - Pure RetroArch overlays (Android layouts)
 * - LibretroDroid native cores (ARM64)
 * - NO Radial/Lemuroid gamepads
 */
class RetroArchEmulatorActivity : ComponentActivity() {
    
    // ViewModel for UI state management
    private val viewModel: RetroArchEmulatorViewModel by viewModels()
    
    companion object {
        private const val TAG = "RetroArchEmulator"
        
        /**
         * Convertit le nom technique du core en nom d'affichage lisible
         * Délégué à CoreDisplayHelper
         */
        private fun getCoreDisplayName(coreFileName: String): String {
            return CoreDisplayHelper.getCoreDisplayName(coreFileName)
        }
        
    }
    
    private lateinit var retroView: GLRetroView
    private lateinit var console: String
    private lateinit var romPath: String
    private lateinit var gameName: String
    private lateinit var screenshotGameId: String
    private lateinit var prefs: SharedPreferences
    private lateinit var cheatApplier: com.retroplay.cheat.CheatApplier
    private lateinit var applyCheatUseCase: ApplyCheatUseCase
    private lateinit var takeScreenshotUseCase: TakeScreenshotUseCase
    private lateinit var saveStateUseCase: SaveStateUseCase
    private lateinit var handleHotkeyUseCase: HandleHotkeyUseCase
    private lateinit var emulatorControlsUseCase: EmulatorControlsUseCase
    private lateinit var handleZapperUseCase: HandleZapperUseCase
    private lateinit var corePathResolverUseCase: CorePathResolverUseCase
    private lateinit var loadCoreVariablesUseCase: LoadCoreVariablesUseCase
    private lateinit var cheatManagerUseCase: CheatManagerUseCase
    private lateinit var configFileHandlerUseCase: ConfigFileHandlerUseCase
    private lateinit var activityLifecycleUseCase: ActivityLifecycleUseCase
    private lateinit var fullscreenSetupUseCase: FullscreenSetupUseCase
    private var currentCoreFilePath: String? = null
    
    /**
     * Get the current core file path (public accessor for UI)
     */
    fun getCurrentCorePath(): String? {
        return currentCoreFilePath
    }
    private var gameCRC: String? = null  // Database CRC (if available)
    private var loadedCheats = mutableListOf<com.retroplay.cheat.CheatManager.Cheat>()  // Cheats loaded for current game
    private var rewindManager: RewindManager? = null
    private var runAheadManager: com.retroplay.runahead.RunAheadManager? = null
    // Note: retroPlayConfig will be initialized in onCreate with console
    private lateinit var retroPlayConfig: RetroPlayConfigManager.RetroPlayConfig
    private var runAheadEnabledConfig: Boolean = false
    private var runAheadFramesConfig: Int = 0
    
    // Autoconfig system (RetroArch gamepad autoconfiguration)
    private lateinit var autoconfigManager: com.retroplay.input.AutoconfigManager
    private lateinit var nativeControllerManager: com.retroplay.input.NativeControllerManager
    // private lateinit var zapperManager: com.retroplay.input.ZapperManager
    
    // Zapper support (NES light gun)
    private var isZapperGame: Boolean = false
    private var zapperPort: Int = 1  // Port par défaut: 1 (index) = Port 2 NES. 0 = Port 1 pour Chiller
    
    // Managers centralisés
    private val dialogStateManager = DialogStateManager()
    private lateinit var overlayManager: OverlayManager
    private lateinit var coreManager: CoreManager
    private lateinit var inputManager: InputManager
    
    // Variables associées aux dialogs
    private var failedCoreName = ""
    private var controllerConfigurationDone = false  // Flag pour éviter de configurer plusieurs fois
    private var coreChangeConfirmMessage = ""
    
    // Résultat du chargement de core (utilisé dans callbacks asynchrones)
    private var loadCoreResult: com.retroplay.usecases.LoadCoreUseCase.LoadCoreResult? = null
    
    // État pour le dialog des extensions N64 (données spécifiques)
    private val n64ExtensionsInfo = mutableStateOf<List<Pair<Int, String>>>(emptyList())
    
    // Game Metadata for Info Dialog
    private val gameInfo = mutableStateOf<GameInfo?>(null)
    private val cheatFile = mutableStateOf<File?>(null)
    
    // Configuration per-game
    private var perGameConfigCRC: String? = null
    private var customConfigId: String? = null // Separate ID for Config (e.g. Serial) vs Content (CRC)
    private var perGameConfigGameName: String = ""
    private var allCoreVariables = mutableStateListOf<CoreVariable>()
    private val dipSwitches = mutableStateListOf<CoreVariable>()
    private val coreOptions = mutableStateListOf<CoreVariable>()
    private var availableDisks = mutableIntStateOf(0)
    private var currentDisk = mutableIntStateOf(0)
    
    // File picker pour custom .cfg (initialisé AVANT onCreate avec lateinit)
    private lateinit var pickCustomCfgLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    
    /**
     * Gérer le fichier .cfg sélectionné (LECTURE SEULE - aucune modification)
     * Utilise ContentResolver pour gérer les content:// URIs correctement
     * Délègue au LoadOverlayUseCase
     */
    private fun handleCustomCfgSelection(uri: android.net.Uri) {
        if (::configFileHandlerUseCase.isInitialized) {
            configFileHandlerUseCase.handleCustomCfgSelection(uri)
        } else {
            Log.e(TAG, "ConfigFileHandlerUseCase not initialized")
            Toast.makeText(this, "Config handler not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Fonctions getFileNameFromUri et readFileFromUri déplacées vers UriFileHelper
    
    /**
     * Apply per-game configuration overrides (if exists).
     * This function loads and applies game-specific settings like Run-Ahead, Rewind, etc.
     * 
     * NOTE: Currently only logs the config as Run-Ahead/Rewind APIs are not yet exposed
     * from LibretroDroid native layer. Full implementation will be added when APIs are ready.
     * 
     * @param gameCRC CRC32 of the game ROM (may be null if not calculated)
     */

    
    // Fonctions extractOverlayFolderFromUri et detectOverlayNameFromContent déplacées vers UriFileHelper
    
    /**
     * Configure tous les contrôleurs après que le jeu soit chargé (appelé après le premier FrameRendered)
     */

    
    /**
     * Configure le Zapper (Port 2) manuellement pendant le jeu (À CHAUD)
     * TEST: Reproduire ce que RetroArch fait quand on configure via Quick Menu
     * 
     * RetroArch fait ça à chaud sans redémarrer, donc on fait pareil!
     */

    
    /**
     * Termine l'activité de manière sécurisée.
     * MAME2010 a un bug dans son destructeur, on doit donc utiliser killProcess().
     * @param delayMs Délai en millisecondes avant de terminer (pour laisser les dialogs s'afficher)
     */
    private fun safeFinishActivity(currentCore: String?, delayMs: Long = 0) {
        if (::activityLifecycleUseCase.isInitialized) {
            activityLifecycleUseCase.safeFinishActivity(currentCore, delayMs)
        } else {
            Log.e(TAG, "ActivityLifecycleUseCase not initialized")
            finish()
        }
    }
    
    /**
     * Envoie un signal de trigger lightgun au core
     * Simule un appui/relâchement rapide du bouton A sur le port lightgun
     * 
     * @param port Port du lightgun (index 0-3)
     * @param delayMs Délai avant déclenchement (0 = immédiat)
     */
    private fun sendLightgunTrigger(port: Int, delayMs: Int) {
        if (::handleZapperUseCase.isInitialized) {
            handleZapperUseCase.sendLightgunTrigger(port, delayMs)
        } else {
            Log.e(TAG, "HandleZapperUseCase not initialized")
        }
    }
    
    /**
     * Gère les actions multi-touch configurables (2/3/4 doigts)
     * Compatible RetroArch configuration.c lignes 2652-2654
     * 
     * @param event Touch event contenant le nombre de doigts
     */
    private fun handleMultiTouchActions(event: android.view.MotionEvent) {
        if (::handleZapperUseCase.isInitialized) {
            handleZapperUseCase.handleMultiTouchActions(event)
        } else {
            Log.e(TAG, "HandleZapperUseCase not initialized")
        }
    }
    
    /**
     * Envoie une action lightgun configurée (multi-touch)
     * Mapping selon RetroArch overlay system
     * 
     * @param actionId ID de l'action (1=START, 2=SELECT, 3=AUX_A, etc.)
     * @param port Port du lightgun
     */
    private fun sendLightgunAction(actionId: Int, port: Int) {
        if (::handleZapperUseCase.isInitialized) {
            handleZapperUseCase.sendLightgunAction(actionId, port)
        } else {
            Log.e(TAG, "HandleZapperUseCase not initialized")
        }
    }
    
    /**
     * Gestion des touches Zapper - Délègue au HandleZapperUseCase
     * CRITIQUE: Cette méthode est essentielle pour le fonctionnement du zapper.
     */
    private fun handleZapperTouch(
        event: android.view.MotionEvent,
        gameViewBounds: androidx.compose.ui.geometry.Rect?,
        triggerOnTouch: Boolean = true,
        allowOffscreen: Boolean = true,
        triggerDelay: Int = 0,
        lightgunPort: Int = 1,
        pulseDuration: Int = 16
    ): Boolean {
        if (::handleZapperUseCase.isInitialized) {
            return handleZapperUseCase.handleZapperTouch(
                event,
                gameViewBounds,
                triggerOnTouch,
                allowOffscreen,
                triggerDelay,
                lightgunPort,
                pulseDuration
            )
        } else {
            Log.e(TAG, "HandleZapperUseCase not initialized")
            return false
        }
    }
    
    /**
     * Charge les variables de core (DIP switches et Core Options) et applique les valeurs sauvegardées
     * Délègue au LoadCoreVariablesUseCase
     */
    private fun loadCoreVariables() {
        if (::loadCoreVariablesUseCase.isInitialized) {
            loadCoreVariablesUseCase.loadCoreVariables(romPath)
        } else {
            Log.e(TAG, "LoadCoreVariablesUseCase not initialized")
        }
    }
    
    /**
     * Take a screenshot of the current game
     */
    // Fonction takeScreenshot déplacée vers TakeScreenshotUseCase
    private fun takeScreenshot() {
        takeScreenshotUseCase.takeScreenshot(retroView, console, screenshotGameId)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "🟢 RetroArchEmulatorActivity.onCreate() STARTED")
        
        val receivedRomPath = intent.getStringExtra("romPath")
        Log.i(TAG, "📥 Intent received - romPath: $receivedRomPath")
        Log.i(TAG, "📥 Intent received - gameName: ${intent.getStringExtra("gameName")}")
        Log.i(TAG, "📥 Intent received - console: ${intent.getStringExtra("console")}")
        Log.i(TAG, "📥 Intent received - loadSlot: ${intent.getIntExtra("loadSlot", -1)}")
        
        romPath = receivedRomPath ?: run {
            Log.e(TAG, "❌ No ROM path provided in Intent")
            Toast.makeText(this, "Error: No ROM path", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Log du chemin ROM pour déboguer les caractères spéciaux
        Log.i(TAG, "✅ ROM path validated (handles special chars): $romPath")
        
        console = intent.getStringExtra("console") ?: "psx"
        gameName = intent.getStringExtra("gameName") ?: "Game"
        gameCRC = intent.getStringExtra("gameCRC")  // Database CRC (may be null)
        val rawGameId = intent.getStringExtra("gameId") ?: gameName
        val gameId = rawGameId  // legacy usage for config loading
        // screenshotGameId sera initialisé après la création de takeScreenshotUseCase
        val loadSlot = intent.getIntExtra("loadSlot", 0)  // 0 = nouvelle partie, 1-5 = charger slot
        
        // Initialize ViewModel
        val viewModel = androidx.lifecycle.ViewModelProvider(this).get(com.retroplay.viewmodels.RetroArchEmulatorViewModel::class.java)
        
        // Read override Identity from Intent (passed by GameDetailsActivity)
        val psxSerial = intent.getStringExtra("psxSerial")
        val intentConfigId = intent.getStringExtra("configId")

        // MODULAR IDENTITY RESOLUTION:
        // Resolve Game Identity (CRC, Serial, or Name) via ViewModel
        // This handles DB lookup fallback (if CRC invalid) and Config ID priority (Serial > CRC > Name)
        val resolvedId = viewModel.resolveGameIdentity(
            gameName, 
            gameCRC, 
            console, 
            romPath,
            overrideConfigId = intentConfigId,
            overridePsxSerial = psxSerial
        )
        
        // Update local variables from ViewModel state
        gameCRC = viewModel.gameCRC
        customConfigId = viewModel.customConfigId
        
        Log.i(TAG, "[DEBUG] Config Initialization Check (Modular):")
        Log.i(TAG, "  - loadSlot: $loadSlot")
        Log.i(TAG, "  - gameName: $gameName")
        Log.i(TAG, "  - gameCRC (Resolved): $gameCRC")
        Log.i(TAG, "  - customConfigId (Resolved): $customConfigId")
        Log.i(TAG, "  - Config file path: /storage/emulated/0/RetroPlay-Data/config/games/${customConfigId ?: gameCRC ?: "UNKNOWN"}.cfg")
        
        // CRITIQUE: Initialiser prefs AVANT applyPerGameConfig() qui l'utilise
        prefs = getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)
        
        // Charger la config (sans appliquer Rewind/RunAhead car les managers ne sont pas encore initialisés)
        // Les settings seront appliqués APRÈS l'initialisation des managers (ligne 1094-1095)
        // Note: Use gameName for config (like saves system), not customConfigId
        // Note: coreFilePath will be set later after core is loaded, so we pass null for now
        // The config will use fallback "retroplay.cfg" until core is loaded
        retroPlayConfig = EmulatorConfigHelper.applyPerGameConfig(
            customConfigId,  // Still passed for compatibility, but gameName is used for config
            gameName,
            console,
            null,  // rewindManager pas encore initialisé
            null,  // runAheadManager pas encore initialisé
            prefs,
            null   // coreFilePath pas encore disponible (sera mis à jour après chargement du core)
        )

        // Load Game Metadata (Async) for Info Dialog
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var info: GameInfo? = null
            
            // 1. Try Lookup by CRC
            if (gameCRC != null) {
                info = DatabaseManager.lookupGameAsync(gameCRC!!, console)
            }
            
            // 2. Fallback: Lookup by Name
            if (info == null) {
                info = DatabaseManager.lookupGameByNameAsync(gameName, console)
            }
            
            gameInfo.value = info
            
            // 3. Find Cheat File
            if (info != null) {
                cheatFile.value = DatabaseManager.getCheatsPath(info, console)
            }
        }

        
        // Configuration Périphériques (Zapper/Lightgun/Guncon/etc): UNIQUEMENT basée sur la configuration manuelle des ports
        // Plus de détection automatique - l'utilisateur configure manuellement via le menu
        // Supporte: Zapper (258), Lightgun/Guncon (4), SuperScope (260), Menacer (4), Mouse (2), etc.
        
        // Vérifier configuration manuelle (SharedPreferences - controller ports)
        // Priority: console_config then compose_gamepad_settings (comme dans ControllerHelper)
        val consoleConfigPrefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
        for (port in 0..3) {
            val portKey = "controller_port_${console}_port${port}"
            // Priority: console_config then compose_gamepad_settings
            var controllerType = consoleConfigPrefs.getInt(portKey, -1)
            if (controllerType == -1) {
                controllerType = prefs.getInt(portKey, -1)
            }
            // Détecter tous les périphériques qui nécessitent le touchscreen (Zapper, Lightgun, Guncon, SuperScope, Menacer)
            if (controllerType == 258 || // RETRO_DEVICE_ZAPPER (NES)
                controllerType == 4 ||   // RETRO_DEVICE_LIGHTGUN (PSX Guncon, Genesis Menacer, etc.)
                controllerType == 260) { // RETRO_DEVICE_SUPERSCOPE (SNES)
                isZapperGame = true
                zapperPort = port
                val deviceName = when (controllerType) {
                    258 -> "Zapper"
                    4 -> "Lightgun/Guncon"
                    260 -> "SuperScope"
                    else -> "Type $controllerType"
                }
                Log.i(TAG, "[PERIPHERAL] $deviceName configured manually on port $port (from ${if (consoleConfigPrefs.getInt(portKey, -1) == controllerType) "console_config" else "compose_gamepad_settings"})")
                break
            }
        }
        
        // Vérifier configuration per-game (RetroPlayConfigManager) - optionnel
        if (!isZapperGame && gameName.isNotEmpty()) {
            val effectiveConfig = RetroPlayConfigManager.getEffectiveConfig(console, gameName)
            if (effectiveConfig.zapperEnabled) {
                isZapperGame = true
                zapperPort = effectiveConfig.zapperPort
                Log.i(TAG, "[ZAPPER] Zapper enabled in per-game config (port: $zapperPort)")
            }
        }
        
        // Vérifier configuration globale (RetroPlayConfigManager) - optionnel
        if (!isZapperGame) {
            val globalConfig = RetroPlayConfigManager.loadConfig(console)
            if (globalConfig.zapperEnabled) {
                isZapperGame = true
                zapperPort = globalConfig.zapperPort
                Log.i(TAG, "[ZAPPER] Zapper enabled in global config (port: $zapperPort)")
            }
        }
        
        // Recommandation mode panoramique pour meilleure précision (si zapper activé)
        if (isZapperGame) {
            val configuration = resources.configuration
            val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            if (isPortrait) {
                android.widget.Toast.makeText(
                    this,
                    "For better Zapper accuracy, use landscape mode",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        
        Log.i(TAG, "🟢 NativeComposeEmulator starting: $gameName ($console) from $romPath" + 
                if (loadSlot > 0) " [LOAD SLOT $loadSlot]" else " [NEW GAME]")
        
        // Note: prefs est maintenant initialisé plus tôt (avant applyPerGameConfig)
        
        // Initialize FullscreenSetupUseCase (après prefs)
        fullscreenSetupUseCase = FullscreenSetupUseCase(this, prefs)
        fullscreenSetupUseCase.setupFullscreenMode()
        
        // Initialize Managers
        overlayManager = OverlayManager(this, prefs)
        coreManager = CoreManager(this)
        
        // Use Cases
        takeScreenshotUseCase = TakeScreenshotUseCase(this, lifecycleScope)
        
        // Note: saveStateUseCase sera initialisé après la création de retroView
        
        // Sanitize gameId pour les screenshots (doit être après l'initialisation de takeScreenshotUseCase)
        val rawGameIdForScreenshot = intent.getStringExtra("gameId") ?: gameName
        screenshotGameId = takeScreenshotUseCase.sanitizeGameId(rawGameIdForScreenshot, gameName)
        
        // Initialiser le système d'autoconfig RetroArch (P0 - Priorité Critique)
        autoconfigManager = com.retroplay.input.AutoconfigManager(this)
        
        // Détecter automatiquement les gamepads connectés et appliquer leurs configurations
        lifecycleScope.launch {
            try {
                val gamepads = autoconfigManager.detectConnectedGamepads()
                for (gamepad in gamepads) {
                    val config = autoconfigManager.findConfigForDevice(gamepad)
                    if (config != null) {
                        Log.i(TAG, "✅ Autoconfig found for gamepad: ${gamepad.name} → ${config.deviceName}")
                        autoconfigManager.applyConfig(gamepad, config)
                    } else {
                        Log.w(TAG, "⚠️ No autoconfig found for gamepad: ${gamepad.name} (vid=${gamepad.vendorId}, pid=${gamepad.productId})")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting/autoconfiguring gamepads", e)
            }
        }
        
        // Quick Wins: Charger états Fast Forward et Audio Mute
        // Note: fastForwardRatio est sauvegardé comme Float dans EmulationSettingsDialog
        fastForwardRatio = try {
            prefs.getFloat("emulation_fast_forward_ratio", 2.0f).toInt().coerceIn(1, 10)
        } catch (e: ClassCastException) {
            // Migration: si c'était un Int avant, le lire comme Int puis migrer vers Float
            val oldValue = prefs.getInt("emulation_fast_forward_ratio", 2)
            prefs.edit().putFloat("emulation_fast_forward_ratio", oldValue.toFloat()).apply()
            oldValue.coerceIn(1, 10)
        }
        audioMuted.value = prefs.getBoolean("emulation_audio_muted", false)
        
        // Quick Win #4: Charger shader préféré
        val savedShaderName = prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT"
        currentShader.value = com.retroplay.shader.ShaderManager.fromString(savedShaderName)
        
        // QuickActionsBar visibility: Charger état
        quickActionsBarVisible.value = prefs.getBoolean("emulation_quick_actions_bar_visible", true)
        quickActionsBarAutoHideEnabled.value = prefs.getBoolean("emulation_quick_actions_bar_auto_hide", true)
        // Ne PAS initialiser le timer au démarrage si auto-hide est activé
        // Le timer sera mis à jour SEULEMENT quand on clique sur le chevron
        // Les touches sur l'écran ne réaffichent PAS la barre automatiquement
        if (!quickActionsBarAutoHideEnabled.value) {
            quickActionsBarAutoHideTimer.value = android.os.SystemClock.elapsedRealtime()  // Initialiser seulement si auto-hide désactivé
        }
        
        // Crosshair Mode: Charger mode d'affichage du crosshair Zapper
        val savedCrosshairMode = prefs.getString("emulation_crosshair_mode", "RETROPLAY_ONLY") ?: "RETROPLAY_ONLY"
        crosshairMode.value = try {
            CrosshairMode.valueOf(savedCrosshairMode)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid crosshair mode: $savedCrosshairMode, using default")
            CrosshairMode.RETROPLAY_ONLY
        }
        
        // Initialiser le file picker AVANT setContent (CRITIQUE pour lifecycle)
        pickCustomCfgLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                handleCustomCfgSelection(uri)
            } else {
                Log.w(TAG, "No file selected")
            }
        }
        
        // Fix: Load saved variant from preferences
        val savedVariant = GamePadLayoutManager.loadVariant(prefs, console)
        Log.i(TAG, "Loaded saved gamepad variant: $savedVariant for console: $console")
        
        // Charger le core via CoreManager
        try {
            if (::coreManager.isInitialized) {
                loadCoreResult = coreManager.loadCore(console, romPath, gameName)
            } else {
                Log.e(TAG, "CoreManager not initialized")
                // Fallback: utiliser LoadCoreUseCase directement
                val loadCoreUseCase = LoadCoreUseCase(this)
                loadCoreResult = loadCoreUseCase.loadCore(console, romPath, gameName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Failed to load core", e)
            Toast.makeText(this, "Error: Failed to load core for $console", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // CRITIQUE: Vérifier que loadCoreResult n'est pas null
        val coreResult = loadCoreResult ?: run {
            Log.e(TAG, "❌ CRITICAL: loadCoreResult is null")
            Toast.makeText(this, "Error: Core loading failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        Log.i(TAG, "✅ Core loaded result: $coreResult")
        Log.i(TAG, "   - Core Path: ${coreResult.coreFilePath}")
        Log.i(TAG, "   - Failed Core Name: ${coreResult.failedCoreName}")
        Log.i(TAG, "   - Should Show Error: ${coreResult.shouldShowErrorDialog}")
        
        // Gérer l'affichage du dialog d'erreur si crash détecté
        if (coreResult.shouldShowErrorDialog) {
            failedCoreName = coreResult.failedCoreName ?: ""
            dialogStateManager.openCoreErrorDialog()
        }
        
        // Créer GLRetroView avec GLRetroViewData
        val data = com.swordfish.libretrodroid.GLRetroViewData(this).apply {
            coreFilePath = coreResult.coreFilePath
            
            // Gérer l'extraction des ROMs depuis les archives (.zip, .7z)
            // FCEUmm (NES) et la plupart des cores ne peuvent pas charger directement les ZIP
            // Toujours extraire en mémoire (gameFileBytes) pour les archives non-arcade
            val isArchive = romPath.endsWith(".zip", ignoreCase = true) || romPath.endsWith(".7z", ignoreCase = true)
            val isArcadeZip = (console.equals("fbneo", ignoreCase = true) || 
                              console.equals("arcade", ignoreCase = true) || 
                              console.equals("mame", ignoreCase = true) || 
                              console.equals("neogeo", ignoreCase = true)) && 
                             romPath.endsWith(".zip", ignoreCase = true)
            
            if (isArchive && !isArcadeZip) {
                // Archive non-arcade: extraire la ROM
                try {
                    val romFile = java.io.File(romPath)
                    if (!romFile.exists()) {
                        Log.e(TAG, "❌ Archive file does not exist: $romPath")
                        gameFilePath = romPath  // Fallback
                    } else {
                        Log.i(TAG, "🔍 Extracting ROM from archive: $romPath")
                        
                        val validExtensions = when (console.lowercase()) {
                            "nes" -> listOf(".nes", ".fds")
                            "snes" -> listOf(".smc", ".sfc", ".fig")
                            "gb" -> listOf(".gb")
                            "gbc" -> listOf(".gbc")
                            "gba" -> listOf(".gba")
                            "genesis" -> listOf(".gen", ".md", ".smd")
                            "mastersystem" -> listOf(".sms")
                            "gamegear" -> listOf(".gg")
                            "32x" -> listOf(".32x")
                            "ngp" -> listOf(".ngp")
                            "wonderswancolor" -> listOf(".ws", ".wsc")
                            "pce" -> listOf(".pce")

                            else -> emptyList()
                        }
                        
                        if (romPath.endsWith(".zip", ignoreCase = true)) {
                            val zipFile = java.util.zip.ZipFile(romPath)
                            val entries = zipFile.entries()
                            var romBytes: ByteArray? = null
                            
                            // Trouver le premier fichier ROM valide dans le zip
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val entryName = entry.name.lowercase()
                                
                                if (validExtensions.any { entryName.endsWith(it) }) {
                                    Log.i(TAG, "✅ Found valid ROM in zip: ${entry.name}")
                                    val inputStream = zipFile.getInputStream(entry)
                                    romBytes = inputStream.readBytes()
                                    inputStream.close()
                                    Log.i(TAG, "📦 Extracted ROM from zip: ${entry.name} (${romBytes.size} bytes)")
                                    break
                                }
                            }
                            
                            zipFile.close()
                            
                            if (romBytes != null) {
                                // Passer le fichier via gameFileBytes au lieu de gameFilePath
                                gameFileBytes = romBytes
                                gameFilePath = null
                                Log.i(TAG, "✅ Using gameFileBytes for extracted ROM")
                            } else {
                                // Fallback: utiliser le chemin du zip directement
                                gameFilePath = romPath
                                Log.w(TAG, "⚠️ No valid ROM found in zip, trying zip directly: $romPath")
                            }
                        } else {
                            // .7z non supporté pour l'instant, utiliser le chemin directement
                            gameFilePath = romPath
                            Log.w(TAG, "⚠️ .7z extraction not implemented, using path directly: $romPath")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error extracting ROM from archive: ${e.message}", e)
                    // Fallback: utiliser le chemin directement
                    gameFilePath = romPath
                }
            } else {
                // Fichier normal ou arcade zip: utiliser le chemin directement
                gameFilePath = romPath
                Log.i(TAG, "📁 Using gameFilePath directly: $romPath")
            }
            
            // Sauvegarder le core actuel pour le cleanup
            this@RetroArchEmulatorActivity.currentCoreFilePath = coreResult.coreFilePath
            
            // System directory (BIOS)
            systemDirectory = com.retroplay.helpers.GameLibraryPaths.BIOS_DIR
            
            // Saves directory (SHARED)
            val sharedSavesDir = File(com.retroplay.helpers.GameLibraryPaths.getSavesDirForConsole(console))
            if (!sharedSavesDir.exists()) sharedSavesDir.mkdirs()
            savesDirectory = sharedSavesDir.absolutePath
            
            // Shader (appliquer le shader sauvegardé)
            shader = com.retroplay.shader.ShaderManager.getShaderConfig(currentShader.value)
            
            // Options
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true
            
            // Fix pour les cores avec rendu hardware (Dreamcast/Flycast, N64, PSX, NDS)
            // Lemuroid utilise skipDuplicateFrames = false pour ces cores car ils ont
            // des problèmes de rendu spécifiques avec le filtrage de frames dupliquées
            skipDuplicateFrames = when (console.lowercase()) {

                "n64" -> false  // Mupen64/Parallel64 (hardware rendering)  
                "psx", "ps1" -> false  // PCSX ReARMed (peut utiliser hardware rendering)
                else -> true  // Par défaut: activer le filtrage pour better performance
            }
            Log.i(TAG, "[INIT] skipDuplicateFrames = $skipDuplicateFrames for console: $console")

            // Configuration des variables de core via l'API officielle LibretroDroid
            val corePrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@RetroArchEmulatorActivity)
            val prefix = "${console}_"

            // Détecter le core N64 réellement utilisé
            val actualCoreFile = coreFilePath ?: ""
            val isParallelN64 = actualCoreFile.contains("parallel_n64")
            val isMupen64Plus = actualCoreFile.contains("mupen64plus")

            // Configuration des variables selon la console
            Log.i(TAG, "[INIT] Configuring core variables for console: '$console', isZapperGame=$isZapperGame, zapperPort=$zapperPort")
            when (console) {
                "n64" -> {
                    // Lire depuis console_config (comme ConsoleConfigActivity)
                    val consoleConfigPrefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
                    val consolePrefix = "n64_"
                    // Clés: n64_resolution, n64_antialiasing, n64_bilinear (sans redondance)
                    val resolution = consoleConfigPrefs.getInt("${consolePrefix}resolution", 0)
                    val antialiasing = consoleConfigPrefs.getInt("${consolePrefix}antialiasing", 0)
                    val bilinear = consoleConfigPrefs.getBoolean("${consolePrefix}bilinear", false)
                    Log.i(TAG, "[N64] Core options loaded - Resolution: $resolution, AA: $antialiasing, Bilinear: $bilinear")
                    Log.i(TAG, "[N64] Detected core file: $actualCoreFile (Parallel: $isParallelN64, Mupen64Plus: $isMupen64Plus)")

                    // Appliquer les variables selon le core détecté
                    val n64Variables = if (isParallelN64) {
                        // ParaLLEl N64 variables
                        arrayOf(
                            Variable("parallel-n64-screensize", when (resolution) {
                                0 -> "320x240"
                                1 -> "640x480"
                                2 -> "960x720"
                                3 -> "1280x960"
                                else -> "320x240"
                            }),
                            Variable("parallel-n64-antialiasmode", antialiasing.toString()),
                            Variable("parallel-n64-bilinear_mode", if (bilinear) "1" else "0")
                        )
                    } else if (isMupen64Plus) {
                        // Mupen64Plus Next variables
                        // Note: Mupen64Plus utilise des noms de variables différents de ParaLLEl N64
                        // Variables connues basées sur documentation RetroArch et tests
                        Log.i(TAG, "[N64] Mupen64Plus Next detected - applying core options")
                        
                        // Mupen64Plus utilise des variables avec préfixe "mupen64plus-"
                        // Mapping similaire à ParaLLEl N64 mais avec noms différents
                        arrayOf(
                            // Resolution: Mupen64Plus utilise "mupen64plus-rdp-resolution" ou "mupen64plus-gfx-resolution"
                            // Essayer les deux formats possibles
                            Variable("mupen64plus-rdp-resolution", when (resolution) {
                                0 -> "320x240"
                                1 -> "640x480"
                                2 -> "960x720"
                                3 -> "1280x960"
                                else -> "320x240"
                            }),
                            // Anti-aliasing: Mupen64Plus utilise MSAA (Multi-Sample Anti-Aliasing)
                            // Valeurs: 0 = disabled, 2 = 2x, 4 = 4x, 8 = 8x
                            Variable("mupen64plus-rdp-msaa", when (antialiasing) {
                                0 -> "0"  // Disabled
                                1 -> "2"  // 2x MSAA
                                2 -> "4"  // 4x MSAA
                                3 -> "8"  // 8x MSAA
                                else -> "0"
                            }),
                            // Bilinear filtering: 0 = disabled, 1 = enabled
                            Variable("mupen64plus-rdp-bilinear", if (bilinear) "1" else "0")
                        ).also {
                            Log.i(TAG, "[N64] Mupen64Plus variables configured: resolution=$resolution, AA=$antialiasing, bilinear=$bilinear")
                        }
                    } else {
                        // Core inconnu - utiliser les variables ParaLLEl N64 par défaut
                        Log.w(TAG, "[N64] Unknown N64 core, using ParaLLEl N64 variables as fallback")
                        arrayOf(
                            Variable("parallel-n64-screensize", when (resolution) {
                                0 -> "320x240"
                                1 -> "640x480"
                                2 -> "960x720"
                                3 -> "1280x960"
                                else -> "320x240"
                            }),
                            Variable("parallel-n64-antialiasmode", antialiasing.toString()),
                            Variable("parallel-n64-bilinear_mode", if (bilinear) "1" else "0")
                        )
                    }

                    try {
                        variables = n64Variables
                        val coreName = when {
                            isParallelN64 -> "ParaLLEl N64"
                            isMupen64Plus -> "Mupen64Plus Next"
                            else -> "Unknown N64 core"
                        }
                        Log.i(TAG, "[N64] Core variables set for $coreName via GLRetroViewData.variables API")
                        
                        // Pour Mupen64Plus: Log les variables configurées pour validation
                        if (isMupen64Plus && n64Variables.isNotEmpty()) {
                            Log.d(TAG, "[N64] Mupen64Plus variables applied:")
                            n64Variables.forEach { variable ->
                                Log.d(TAG, "[N64]   ${variable.key} = ${variable.value}")
                            }
                            Log.d(TAG, "[N64] Note: If variables don't work, check available variables via retroView.getVariables() after core loads")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[N64] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "psx", "ps1", "playstation" -> {
                    // Lire depuis console_config (comme ConsoleConfigActivity)
                    val consoleConfigPrefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
                    val consolePrefix = "psx_"
                    // Clés: psx_resolution, psx_texture_filtering, psx_dithering (sans redondance)
                    val resolution = consoleConfigPrefs.getInt("${consolePrefix}resolution", 0)
                    val textureFiltering = consoleConfigPrefs.getBoolean("${consolePrefix}texture_filtering", true)
                    val dithering = consoleConfigPrefs.getBoolean("${consolePrefix}dithering", true)
                    Log.i(TAG, "[PSX] Core options loaded - Resolution: $resolution, Filter: $textureFiltering, Dither: $dithering")

                    // Créer les variables PSX (Array<Variable>)
                    val psxVariables = arrayOf(
                        Variable("beetle_psx_hw_internal_resolution", when (resolution) {
                            0 -> "1"
                            1 -> "2"
                            2 -> "4"
                            3 -> "8"
                            else -> "1"
                        }),
                        Variable("beetle_psx_hw_filter", if (textureFiltering) "1" else "0"),
                        Variable("beetle_psx_hw_dithering", if (dithering) "enabled" else "disabled")
                    )

                    try {
                        variables = psxVariables
                        Log.i(TAG, "[PSX] Core variables set via GLRetroViewData.variables API")
                    } catch (e: Exception) {
                        Log.w(TAG, "[PSX] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "nes" -> {
                    // Configuration NES / FCEUmm depuis .cfg
                    Log.i(TAG, "[NES] Loading FCEUmm configuration from .cfg file")
                    
                    // Créer la config par défaut si inexistante
                    CoreConfigManager.createDefaultConfigIfNeeded(this@RetroArchEmulatorActivity, "FCEUmm", CoreConfigManager.getDefaultConfig("fceumm"))
                    
                    // Charger la config depuis le .cfg
                    val config = CoreConfigManager.loadConfig(this@RetroArchEmulatorActivity, "FCEUmm").toMutableMap()
                    
                    if (config.isNotEmpty()) {
                        // Convertir Map<String, String> en Array<Variable>
                        val nesVariables = config.map { (key, value) ->
                            Variable(key, value)
                        }.toTypedArray()
                        
                        try {
                            variables = nesVariables
                            Log.i(TAG, "[NES] Loaded ${nesVariables.size} variables from FCEUmm.cfg")
                            config.forEach { (key, value) ->
                                Log.d(TAG, "[NES]   $key = \"$value\"")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[NES] Failed to set variables from .cfg: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "[NES] No configuration found in FCEUmm.cfg, using defaults")
                    }
                }
                "snes" -> {
                    // Lire depuis console_config (comme ConsoleConfigActivity)
                    val consoleConfigPrefs = getSharedPreferences("console_config", Context.MODE_PRIVATE)
                    val consolePrefix = "snes_"
                    // Clés: snes_blend_mode, snes_hires (sans redondance)
                    val blendMode = consoleConfigPrefs.getInt("${consolePrefix}blend_mode", 0)
                    val hires = consoleConfigPrefs.getBoolean("${consolePrefix}hires", false)
                    Log.i(TAG, "[SNES] Core options loaded - Blend: $blendMode, HiRes: $hires")

                    // Créer les variables SNES (Array<Variable>)
                    val snesVariables = arrayOf(
                        Variable("snes9x_blend_layers", when (blendMode) {
                            0 -> "disabled"
                            1 -> "merge"
                            2 -> "additive"
                            3 -> "subtractive"
                            else -> "disabled"
                        }),
                        Variable("snes9x_hires_blend", if (hires) "enabled" else "disabled")
                    )

                    try {
                        variables = snesVariables
                        Log.i(TAG, "[SNES] Core variables set via GLRetroViewData.variables API")
                    } catch (e: Exception) {
                        Log.w(TAG, "[SNES] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "arcade", "mame" -> {
                    // MAME cores have issues with variable initialization in LibretroDroid
                    // Using empty array to avoid segfaults during core initialization/destruction
                    Log.i(TAG, "[ARCADE] Initializing MAME core with empty variables array (console=$console)")

                    try {
                        variables = emptyArray()
                        Log.i(TAG, "[ARCADE] MAME core variables initialized (empty array)")
                    } catch (e: Exception) {
                        Log.e(TAG, "[ARCADE] Failed to initialize MAME variables: ${e.message}")
                    }
                }
                "fbneo", "neogeo", "cps1", "cps2" -> {
                    // FBNeo core options
                    Log.i(TAG, "[ARCADE] Setting up FBNeo core options (console=$console)")

                    try {
                        variables = emptyArray()
                        Log.i(TAG, "[ARCADE] FBNeo core variables initialized (empty array)")
                    } catch (e: Exception) {
                        Log.w(TAG, "[ARCADE] Failed to set FBNeo variables: ${e.message}")
                    }
                }

                else -> {
                    // Pour toutes les autres consoles sans configuration spécifique,
                    // initialiser un tableau vide pour éviter les crashes de segfault
                    Log.i(TAG, "[$console] Initializing default core variables (empty array)")
                    
                    try {
                        variables = emptyArray()
                        Log.i(TAG, "[$console] Default core variables initialized")
                    } catch (e: Exception) {
                        Log.w(TAG, "[$console] Failed to set default variables: ${e.message}")
                    }
                }
            }
        }
        
        retroView = GLRetroView(this, data)
        
        // Initialize InputManager (après retroView)
        inputManager = InputManager(retroView)
        
        // Initialize SaveStateUseCase after retroView is created
        saveStateUseCase = SaveStateUseCase(this, lifecycleScope, retroView, console, gameName)
        saveStateUseCase.setCallbacks(object : SaveStateUseCase.SaveStateCallbacks {
            override suspend fun <T> runOnGLThread(block: () -> T): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                retroView.queueEvent {
                    try {
                        val result = block()
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }
            
            override fun runOnUiThread(action: Runnable) {
                this@RetroArchEmulatorActivity.runOnUiThread(action)
            }
            
            override fun showToast(message: String, duration: Int) {
                Toast.makeText(this@RetroArchEmulatorActivity, message, duration).show()
            }
        })
        
        // Initialize HandleHotkeyUseCase
        handleHotkeyUseCase = HandleHotkeyUseCase(this, lifecycleScope, retroView, prefs)
        handleHotkeyUseCase.setCallbacks(object : HandleHotkeyUseCase.HotkeyCallbacks {
            override fun runOnUiThread(action: Runnable) {
                this@RetroArchEmulatorActivity.runOnUiThread(action)
            }
            
            override fun showToast(message: String, duration: Int) {
                Toast.makeText(this@RetroArchEmulatorActivity, message, duration).show()
            }
            
            override fun getCurrentSaveSlot(): Int = currentSaveSlot.value
            override fun setCurrentSaveSlot(slot: Int) { currentSaveSlot.value = slot }
            override fun saveGameState(slot: Int) { this@RetroArchEmulatorActivity.saveGameState(slot) }
            override fun loadGameState(slot: Int) { this@RetroArchEmulatorActivity.loadGameState(slot) }
            override fun getFastForwardRatio(): Int = fastForwardRatio
            override fun isFastForwardActive(): Boolean = isFastForwardActive.value
            override fun setFastForwardActive(active: Boolean) { isFastForwardActive.value = active }
            override fun isAudioMuted(): Boolean = audioMuted.value
            override fun setAudioMuted(muted: Boolean) { audioMuted.value = muted }
            override fun getCurrentShader(): com.retroplay.shader.ShaderManager.ShaderPreset = currentShader.value
            override fun setCurrentShader(shader: com.retroplay.shader.ShaderManager.ShaderPreset) { currentShader.value = shader }
            override fun isPaused(): Boolean = isPaused
            override fun setPaused(paused: Boolean) { isPaused = paused }
            override fun beginRewind(): Boolean = this@RetroArchEmulatorActivity.beginRewind()
            override fun endRewind() { this@RetroArchEmulatorActivity.endRewind() }
            override fun notifyRewindUnavailable() { this@RetroArchEmulatorActivity.notifyRewindUnavailable() }
        })
        
        // Initialize EmulatorControlsUseCase
        emulatorControlsUseCase = EmulatorControlsUseCase(this, retroView, prefs)
        emulatorControlsUseCase.setCallbacks(object : EmulatorControlsUseCase.ControlsCallbacks {
            override fun runOnUiThread(action: Runnable) {
                this@RetroArchEmulatorActivity.runOnUiThread(action)
            }
            
            override fun showToast(message: String, duration: Int) {
                Toast.makeText(this@RetroArchEmulatorActivity, message, duration).show()
            }
            
            override fun getFastForwardRatio(): Int = fastForwardRatio
            override fun isFastForwardActive(): MutableState<Boolean> = isFastForwardActive
            override fun isAudioMuted(): MutableState<Boolean> = audioMuted
            override fun isQuickActionsBarVisible(): MutableState<Boolean> = quickActionsBarVisible
            override fun getGameViewBounds(): MutableState<androidx.compose.ui.geometry.Rect?> {
                // Créer un MutableState si gameViewBoundsForAspectRatio est null
                if (gameViewBoundsForAspectRatio == null) {
                    gameViewBoundsForAspectRatio = mutableStateOf(null)
                }
                return gameViewBoundsForAspectRatio!!
            }
        })
        
        // Initialize HandleZapperUseCase
        handleZapperUseCase = HandleZapperUseCase(this, retroView, prefs, console, customConfigId, gameName)
        handleZapperUseCase.setCallbacks(object : HandleZapperUseCase.ZapperCallbacks {
            override fun runOnUiThread(action: Runnable) {
                this@RetroArchEmulatorActivity.runOnUiThread(action)
            }
            override fun showToast(message: String, duration: Int) {
                Toast.makeText(this@RetroArchEmulatorActivity, message, duration).show()
            }
            override fun getCrosshairMode(): MutableState<CrosshairMode> = crosshairMode
            override fun getResources(): android.content.res.Resources = resources
        })
        
        // Initialize CorePathResolverUseCase
        corePathResolverUseCase = CorePathResolverUseCase(this, prefs)
        
        // Initialize LoadCoreVariablesUseCase
        loadCoreVariablesUseCase = LoadCoreVariablesUseCase(this, retroView)
        loadCoreVariablesUseCase.setCallbacks(object : LoadCoreVariablesUseCase.CoreVariablesCallbacks {
            override fun getCurrentCoreFilePath(): String? = currentCoreFilePath
            override fun getGameId(): String = gameName
            override fun getAllCoreVariables(): MutableList<CoreVariable> = allCoreVariables
            override fun getDipSwitches(): MutableList<CoreVariable> = dipSwitches
            override fun getCoreOptions(): MutableList<CoreVariable> = coreOptions
        })
        
        // Initialize CheatApplier (must be before CheatManagerUseCase)
        cheatApplier = com.retroplay.cheat.CheatApplier(retroView)
        
        // Initialize CheatManagerUseCase
        cheatManagerUseCase = CheatManagerUseCase(this, cheatApplier)
        cheatManagerUseCase.setCallbacks(object : CheatManagerUseCase.CheatManagerCallbacks {
            override fun getLoadedCheats(): MutableList<com.retroplay.cheat.CheatManager.Cheat> = loadedCheats
            override fun setCheatFile(file: File?) { cheatFile.value = file }
            override fun runOnUiThread(action: Runnable) {
                this@RetroArchEmulatorActivity.runOnUiThread(action)
            }
            override fun showToast(message: String, duration: Int) {
                Toast.makeText(this@RetroArchEmulatorActivity, message, duration).show()
            }
        })
        
        // Initialize ConfigFileHandlerUseCase (gère les overlays .cfg, pas les configs de core)
        configFileHandlerUseCase = ConfigFileHandlerUseCase(this, prefs, console)
        configFileHandlerUseCase.setCallbacks(object : ConfigFileHandlerUseCase.ConfigFileCallbacks {
            override fun showToast(message: String, duration: Int) {
                Toast.makeText(this@RetroArchEmulatorActivity, message, duration).show()
            }
        })
        
        // Initialize ActivityLifecycleUseCase
        activityLifecycleUseCase = ActivityLifecycleUseCase()
        activityLifecycleUseCase.setCallbacks(object : ActivityLifecycleUseCase.LifecycleCallbacks {
            override fun finishActivity() {
                finish()
            }
        })
        
        rewindManager = RewindManager(retroView, lifecycleScope)
        runAheadManager = com.retroplay.runahead.RunAheadManager(retroView)
        
        // Initialize Native Managers (Radial/Zapper support)
        nativeControllerManager = com.retroplay.input.NativeControllerManager(this, retroView)
        // zapperManager = com.retroplay.input.ZapperManager(this, retroView)
        
        // CRITIQUE: Appliquer les settings Rewind/RunAhead APRÈS l'initialisation des managers
        // (Les managers sont maintenant initialisés ligne 1121-1122)
        EmulatorConfigHelper.applyRewindSettings(retroPlayConfig, rewindManager, gameCRC, gameName, console, prefs)
        EmulatorConfigHelper.applyRunAheadSettings(retroPlayConfig, runAheadManager)
        
        lifecycle.addObserver(retroView)
        
        // Quick Wins: Appliquer l'état audio au démarrage (après création de retroView)
        retroView.audioEnabled = !audioMuted.value
        
        // Initialize disk info for multi-disc games (PSX, etc.) and configure controllers after first frame
        lifecycleScope.launch {
            try {
                retroView.getGLRetroEvents().collect { event ->
                    if (event is GLRetroView.GLRetroEvents.FrameRendered) {
                        rewindManager?.onFrameRendered()
                        
                        // Initialize Run-Ahead after first frame (game is loaded)
                        if (!controllerConfigurationDone) {
                            runAheadManager?.onSurfaceReady()
                        }
                        
                        // Configure controllers after first frame is rendered (game is loaded and running)
                        if (!controllerConfigurationDone && !dialogStateManager.showCoreErrorDialog.value) {
                            controllerConfigurationDone = true
                            ControllerHelper.configureControllersAfterGameLoaded(
                                this@RetroArchEmulatorActivity,
                                retroView,
                                console,
                                prefs,
                                isZapperGame,
                                zapperPort,
                                dialogStateManager.showCoreErrorDialog.value
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting GLRetroEvents for disk info: ${e.message}")
            }
        }
        
        // Poll for multi-disc changes safely (every 2 seconds)
        lifecycleScope.launch {
            while (isActive) {
                try {
                    if (::retroView.isInitialized && retroView.isGameLoaded()) {
                        val disks = retroView.getAvailableDisks()
                        val current = retroView.getCurrentDisk()
                        if (disks != availableDisks.intValue || current != currentDisk.intValue) {
                            availableDisks.intValue = disks
                            currentDisk.intValue = current
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error polling disk status: ${e.message}")
                }
                delay(2000)
            }
        }
        
        // === ÉCOUTE DES ERREURS LIBRETRODROID (CHARGEMENT ÉCHOUÉ) ===
        lifecycleScope.launch {
            try {
                retroView.getGLRetroErrors().collect { errorCode ->
                    Log.e(TAG, "❌ GLRetroView ERROR detected: $errorCode")
                    
                    // Erreurs qui nécessitent l'affichage du dialog
                    val needsUserAction = errorCode == GLRetroView.ERROR_LOAD_GAME || 
                                         errorCode == GLRetroView.ERROR_LOAD_LIBRARY
                    
                    if (needsUserAction && loadCoreResult != null) {
                        val selectedCore = loadCoreResult?.coreFilePath
                        if (selectedCore != null) {
                            Log.w(TAG, "⚠️ Core $selectedCore failed to load game")
                            
                            // Extraire le nom du core pour l'affichage (nom lisible)
                            failedCoreName = getCoreDisplayName(selectedCore)
                            
                            // Afficher le dialog d'erreur
                            runOnUiThread {
                                dialogStateManager.openCoreErrorDialog()
                            }
                        } else {
                            Log.e(TAG, "❌ loadCoreResult.coreFilePath is null")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting GLRetroErrors: ${e.message}")
            }
        }
        
        // === DÉTECTION DE SUCCÈS (NETTOYER LES PREFS DE CRASH) ===
        // Si le jeu tourne pendant 5 secondes sans crash, nettoyer les prefs
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                // Vérifier si le dialog d'erreur est affiché
                // Si oui, le jeu n'est PAS chargé correctement, ne pas déclarer le succès
                if (!dialogStateManager.showCoreErrorDialog.value && loadCoreResult != null) {
                    val corePath = loadCoreResult?.coreFilePath
                    if (corePath != null) {
                        // Succès ! Le jeu ne crashe pas avec ce core
                        Log.i(TAG, "✅ SUCCESS: Game running successfully with core: $corePath")
                        
                        // Nettoyer les infos de crash
                        if (::coreManager.isInitialized) {
                            coreManager.clearCrashPreferences()
                        }
                    } else {
                        Log.w(TAG, "⚠️ loadCoreResult.coreFilePath is null, cannot declare success")
                    }
                } else {
                    Log.w(TAG, "⚠️ Error dialog is showing, not declaring success")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in success detection: ${e.message}")
            }
        }, 2000L) // 2 secondes pour considérer un crash (CRASH_TIMEOUT_MS)
        
        // Configurer le type de contrôleur pour PSX (DualShock pour analog sticks)
        // TEMPORAIREMENT DÉSACTIVÉ pour tester si c'est la cause du crash
        /*
        if (console.equals("psx", ignoreCase = true)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val controllers = retroView.getControllers()
                    Log.i(TAG, "[PSX] Available controllers: ${controllers.getOrNull(0)?.map { "id=${it.id} desc='${it.description}'" }}")
                    
                    if (controllers.isNotEmpty() && controllers[0].isNotEmpty()) {
                        // Chercher le contrôleur DualShock (essayer "dualshock" en priorité)
                        val dualshock = controllers[0].firstOrNull { 
                            it.description?.contains("dualshock", ignoreCase = true) == true
                        } ?: controllers[0].firstOrNull {
                            it.description?.contains("analog", ignoreCase = true) == true
                        }
                        
                        if (dualshock != null) {
                            retroView.setControllerType(0, dualshock.id)
                            Log.i(TAG, "[PSX] Controller type set to DualShock (id=${dualshock.id}, desc='${dualshock.description}')")
                        } else {
                            Log.w(TAG, "[PSX] DualShock controller not found. Using default.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[PSX] Error configuring controller type", e)
                }
            }, 1000)  // Attendre 1 seconde pour que le core soit complètement initialisé
        }
        */
        Log.i(TAG, "[PSX] Delayed controller configuration DISABLED for testing")
        
        // Configuration des contrôleurs et extensions N64 se fait maintenant via FrameRendered event
        // Voir lifecycleScope.launch { retroView.getGLRetroEvents().collect { ... } } ci-dessus (ligne 1753-1763)
        // La fonction configureControllersAfterGameLoaded() gère à la fois les contrôleurs et les extensions N64

        // Initialiser ApplyCheatUseCase (cheatApplier already initialized above)
        applyCheatUseCase = ApplyCheatUseCase(this, cheatApplier)
        
        // === CHARGER LES DIP SWITCHES ET CORE OPTIONS ===
        // Attendre que le core expose ses variables (certains cores prennent du temps)
        // Flycast (Dreamcast) nécessite plus de temps pour charger le CHD et initialiser le hardware rendering
        val coreVariablesDelay = when (console.lowercase()) {

            else -> 2000L  // Standard delay for other cores
        }
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                try {
                    loadCoreVariables()
                    Log.i(TAG, "[CORE_VARS] Loaded after ${coreVariablesDelay}ms delay for console: $console")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load core variables: ${e.message}")
                }
            }
        }, coreVariablesDelay)
        
        // Charger la save depuis le slot demandé (si loadSlot > 0)
        // IMPORTANT : Différer le chargement pour laisser le core s'initialiser
        // NES nécessite plus de temps que les autres consoles pour s'initialiser complètement
        if (loadSlot > 0) {
            // Délai adaptatif selon la console
            val saveLoadDelay = when (console.lowercase()) {
                "nes" -> 3500L  // NES nécessite plus de temps pour éviter l'écran noir
                "psx", "psp" -> 3000L  // PSX/PSP sont plus lents
                else -> 2500L  // Autres consoles
            }
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                lifecycleScope.launch {
                    // Structure : saves/{console}/{gameName}/slot{slot}.state
                    val gameDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console/$gameName")
                    val expectedSaveFile = File(gameDir, "slot$loadSlot.state")
                    
                    Log.i(TAG, "[SAVE] Looking for save state:")
                    Log.i(TAG, "  - Console: $console")
                    Log.i(TAG, "  - Slot: $loadSlot")
                    Log.i(TAG, "  - Game name: $gameName")
                    Log.i(TAG, "  - Expected path: ${expectedSaveFile.absolutePath}")
                    Log.i(TAG, "  - Game dir exists: ${gameDir.exists()}")
                    
                    // Vérifier si le répertoire existe et lister son contenu pour déboguer
                    if (gameDir.exists()) {
                        val files = gameDir.listFiles()
                        Log.i(TAG, "  - Files in game dir: ${files?.size ?: 0}")
                        files?.forEach { file ->
                            Log.i(TAG, "    - ${file.name} (${file.length()} bytes)")
                        }
                    }
                    
                    // Essayer d'abord avec le nom exact
                    var saveFile: File? = if (expectedSaveFile.exists()) expectedSaveFile else null
                    
                    // Si pas trouvé, chercher le premier fichier slot{slot}.state dans le répertoire
                    if (saveFile == null && gameDir.exists()) {
                        val stateFiles = gameDir.listFiles { _, name -> name.startsWith("slot$loadSlot") && name.endsWith(".state") }
                        if (stateFiles != null && stateFiles.isNotEmpty()) {
                            saveFile = stateFiles[0]  // Prendre le premier fichier slot{slot}.state trouvé
                            Log.w(TAG, "[SAVE] Exact match not found, using first slot$loadSlot.state file: ${saveFile.name}")
                        }
                    }
                    
                    if (saveFile != null && saveFile.exists()) {
                        try {
                            // CRITIQUE: Appel JNI doit être sur GL Thread
                            val stateBytes = saveFile.readBytes()
                            runOnGLThread { 
                                retroView.unserializeState(stateBytes)
                                Log.i(TAG, "[$console] Save state unserialized successfully on GL thread")
                            }
                            Log.i(TAG, "[$console] Save state loaded from slot $loadSlot: ${saveFile.absolutePath}")
                            Toast.makeText(this@RetroArchEmulatorActivity, "[$console] Loaded from Slot $loadSlot", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "[$console] Error loading save from slot $loadSlot", e)
                            Toast.makeText(this@RetroArchEmulatorActivity, "[$console] Error loading save", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "[$console] No save found in slot $loadSlot (checked: ${expectedSaveFile.absolutePath})")
                        Toast.makeText(this@RetroArchEmulatorActivity, "[$console] No save in Slot $loadSlot", Toast.LENGTH_SHORT).show()
                    }
                }
            }, saveLoadDelay)  // Délai adaptatif selon la console
        }
        
        // Charger et appliquer les codes de triche activés APRÈS le chargement complet du jeu
        // Pour les jeux PSX (PBP), le chargement initial est lent, donc on attend plus longtemps
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            loadAndApplyCheats()
        }, if (loadSlot > 0) 3000 else 8000)  // 8s pour NEW GAME, 3s pour LOAD SAVE
        
        // State pour capturer les bounds exacts du GLRetroView (pour Zapper et Aspect Ratio)
        // Défini ICI (dans l'Activity) pour être accessible dans onZapperTouch et applyAspectRatio
        val gameViewBounds = mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
        
        // Stocker la référence pour applyAspectRatio
        gameViewBoundsForAspectRatio = gameViewBounds
        
        setContent {
            ComposeEmulatorScreen(
                retroView = retroView,
                console = console,
                gameName = gameName,
                romPath = romPath,
                prefs = prefs,
                showMainMenu = dialogStateManager.showMainMenu,
                showGamePadSettings = dialogStateManager.showGamePadSettings,
                showQuickMenu = dialogStateManager.showQuickMenu,
                showGameInfoDialog = dialogStateManager.showGameInfoDialog,
                showCheatsDialog = dialogStateManager.showCheatsDialog,
                showSmartConfigDialog = dialogStateManager.showSmartConfigDialog,
                showPerGameConfigDialog = dialogStateManager.showPerGameConfigDialog,
                showCfgBrowser = dialogStateManager.showCfgBrowser,
                showTurboSettings = dialogStateManager.showTurboSettings,
                showQuickTurbo = dialogStateManager.showQuickTurbo,
                gameInfo = gameInfo.value,
                cheatFile = cheatFile.value,
                gameCRC = gameCRC,
                coreFilePath = currentCoreFilePath,  // Pass core file path for config file name

                loadedCheats = loadedCheats,
                overlaysVisible = dialogStateManager.overlaysVisible,
                initialVariant = savedVariant,
                cheatApplier = cheatApplier,
                onVariantChanged = { newVariant ->
                    GamePadLayoutManager.saveVariant(prefs, console, newVariant)
                },
                onFinishActivity = {
                    // Fermer le jeu avec délai pour une transition fluide
                    safeFinishActivity(currentCoreFilePath, delayMs = 1200)
                },
                onSaveState = { slot ->
                    saveGameState(slot)
                },
                onDeleteSlot = { slot ->
                    deleteSaveSlot(console, gameName, slot)
                },
                isZapperGame = isZapperGame,
                gameViewBounds = gameViewBounds,  // Passer le state pour capture
                onZapperTouch = { event ->
                    // Get orientation from context
                    val isLandscapeForSettings = this@RetroArchEmulatorActivity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val orientationForSettings = if (isLandscapeForSettings) "landscape" else "portrait"
                    val lightgunSettings = com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientationForSettings)
                    
                    // CRITIQUE: Utiliser zapperPort de l'Activity (configuré depuis RetroPlayConfigManager ou détection)
                    // au lieu de lightgunSettings.lightgunPort qui vient des paramètres overlay
                    val effectiveZapperPort = if (isZapperGame) zapperPort else lightgunSettings.lightgunPort
                    
                    // Charger les paramètres zapper depuis RetroPlayConfigManager si disponible
                    val effectiveConfig = if (gameName.isNotEmpty() && isZapperGame) {
                        RetroPlayConfigManager.getEffectiveConfig(console, gameName)
                    } else if (isZapperGame) {
                        RetroPlayConfigManager.loadConfig(console)
                    } else {
                        null
                    }
                    
                    val triggerOnTouch = effectiveConfig?.zapperTriggerOnTouch ?: lightgunSettings.lightgunTriggerOnTouch
                    val allowOffscreen = effectiveConfig?.zapperAllowOffscreen ?: lightgunSettings.lightgunAllowOffscreen
                    val triggerDelay = effectiveConfig?.zapperTriggerDelay ?: lightgunSettings.lightgunTriggerDelay
                    val pulseDuration = effectiveConfig?.zapperPulseDuration ?: 16  // Default 16ms (1 frame) si pas configuré
                    
                    Log.d(TAG, "[ZAPPER] handleZapperTouch called: port=$effectiveZapperPort (from ${if (isZapperGame) "zapperPort" else "lightgunSettings"}), triggerOnTouch=$triggerOnTouch, triggerDelay=$triggerDelay, pulseDuration=$pulseDuration")
                    
                    // CRITIQUE: Log supplémentaire pour Chiller (port 0)
                    if (effectiveZapperPort == 0) {
                        Log.i(TAG, "[ZAPPER] Chiller mode: Using port 0 (Port 1 NES) for zapper touch")
                        Log.i(TAG, "[ZAPPER] Chiller config: triggerOnTouch=$triggerOnTouch, allowOffscreen=$allowOffscreen, triggerDelay=$triggerDelay, pulseDuration=$pulseDuration")
                    }
                    
                    handleZapperTouch(
                        event, 
                        gameViewBounds.value, 
                        triggerOnTouch, 
                        allowOffscreen,
                        triggerDelay,
                        effectiveZapperPort,
                        pulseDuration
                    )
                },
                onLoadState = { slot ->
                    loadGameState(slot)
                },
                onHotkey = { action ->
                    handleHotkey(action)
                },
                onHotkeyChange = { action, pressed ->
                    handleHotkeyChange(action, pressed)
                },
                onLightgunAction = { action ->
                    handleLightgunAction(action)
                },
                onTurboSettings = {
                    dialogStateManager.openTurboSettings()
                },
                onSmartConfig = {
                    dialogStateManager.openSmartConfigDialog()
                },
                onPerGameConfig = {
                    if (customConfigId != null) {
                        perGameConfigCRC = customConfigId
                        perGameConfigGameName = gameName
                        dialogStateManager.openPerGameConfigDialog()
                    }
                },
                onZapperConfigChanged = {
                    // Appliquer la config zapper au runtime après changement de config per-game
                    applyZapperConfig()
                },
                configId = customConfigId,
                // Quick Wins callbacks
                onRewindPress = {
                    beginRewind()
                },
                onRewindRelease = {
                    endRewind()
                },
                onToggleFastForward = {
                    handleHotkey("toggle_fast_forward")
                },
                onToggleAudioMute = {
                    handleHotkey("audio_mute_toggle")
                },
                onCycleShader = {
                    handleHotkey("shader_next")
                },
                onToggleQuickActionsBar = {
                    toggleQuickActionsBar()
                },
                onConfigureZapper = {
                    ControllerHelper.configureZapperManually(
                        this@RetroArchEmulatorActivity, 
                        retroView,
                        gameName,
                        if (isZapperGame) zapperPort else null
                    )
                },
                onToggleCrosshairMode = {
                    if (::handleZapperUseCase.isInitialized) {
                        handleZapperUseCase.toggleCrosshairMode()
                    } else {
                        toggleCrosshairMode()  // Fallback si handleZapperUseCase pas initialisé
                    }
                },
                isFastForwardActive = isFastForwardActive.value,
                audioMuted = audioMuted.value,
                currentShaderName = currentShader.value.displayName,
                quickActionsBarVisible = quickActionsBarVisible.value,
                quickActionsBarAutoHideEnabled = quickActionsBarAutoHideEnabled,
                quickActionsBarAutoHideTimer = quickActionsBarAutoHideTimer,
                crosshairMode = crosshairMode.value,
                showDipSwitchDialog = dialogStateManager.showDipSwitchDialog,
                showCoreOptionsDialog = dialogStateManager.showCoreOptionsDialog,
                dipSwitches = dipSwitches,
                coreOptions = coreOptions,
                showDiskSwapperDialog = dialogStateManager.showDiskSwapperDialog,
                showN64ExtensionsDialog = dialogStateManager.showN64ExtensionsDialog,
                n64ExtensionsInfo = n64ExtensionsInfo,
                availableDisks = availableDisks.intValue,
                currentDisk = currentDisk.intValue,
                onTakeScreenshot = {
                    takeScreenshot()
                },
                onOpenGallery = {
                    val intent = Intent(this@RetroArchEmulatorActivity, com.retroplay.gallery.ScreenshotGalleryActivity::class.java).apply {
                        putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_CONSOLE, console)
                        putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_GAME_ID, screenshotGameId)
                        putExtra(com.retroplay.gallery.ScreenshotGalleryActivity.EXTRA_GAME_NAME, gameName)
                    }
                    startActivity(intent)
                },
                onLoadCustomCfg = {
                    // Afficher le file browser custom au lieu du SAF Android
                    // Le SAF ne montre pas les .cfg car ils ne sont pas indexés dans MediaStore
                    // Le browser custom lit directement le FS avec MANAGE_EXTERNAL_STORAGE
                    dialogStateManager.openCfgBrowser()
                },
                // Callbacks pour RetroArchSettingsDialog
                onShaderChanged = { shaderName ->
                    currentShader.value = com.retroplay.shader.ShaderManager.fromString(shaderName)
                    val shaderConfig = com.retroplay.shader.ShaderManager.getShaderConfig(currentShader.value)
                    
                    // Appliquer le shader via GL Thread (Safe)
                    if (::retroView.isInitialized) {
                        retroView.queueEvent {
                            try {
                                retroView.shader = shaderConfig
                                Log.i(TAG, "[SHADER] Applied (GL Thread): ${currentShader.value.displayName}")
                            } catch (e: Exception) {
                                Log.e(TAG, "[SHADER] Error applying shader on GLThread", e)
                            }
                        }
                    } else {
                        Log.w(TAG, "[SHADER] Skipped application - retroView not initialized")
                    }
                },
                onFastForwardRatioChanged = { ratio: Float ->
                    fastForwardRatio = ratio.toInt()
                    // Note: Fast forward ratio est appliqué via toggleFastForward() quand actif
                    // La valeur est sauvegardée et sera utilisée au prochain toggle
                    Log.i(TAG, "[FAST_FORWARD] Ratio changed to: ${fastForwardRatio}x")
                },
                onAudioVolumeChanged = { volume: Float ->
                    // Audio volume est géré via retroView.audioEnabled et système Android
                    Log.i(TAG, "[AUDIO] Volume changed to: ${(volume * 100).toInt()}%")
                },
                onAudioMuteChanged = { muted: Boolean ->
                    audioMuted.value = muted
                    retroView.audioEnabled = !muted
                    Log.i(TAG, "[AUDIO] Mute changed to: $muted")
                },
                onVsyncChanged = { enabled: Boolean ->
                    val config = RetroPlayConfigManager.loadConfig(console)
                    RetroPlayConfigManager.saveConfig(console, config.copy(videoVsync = enabled))
                    // Note: VSync est appliqué au prochain chargement de ROM
                    Log.i(TAG, "[VIDEO] VSync changed to: $enabled")
                },
                onRewindEnabledChanged = { enabled: Boolean ->
                    val config = RetroPlayConfigManager.loadConfig(console)
                    val newConfig = config.copy(rewindEnable = enabled)
                    RetroPlayConfigManager.saveConfig(console, newConfig)
                    retroPlayConfig = newConfig
                    EmulatorConfigHelper.applyRewindSettings(newConfig, rewindManager, gameCRC, gameName, console, prefs)
                    Log.i(TAG, "[REWIND] Enabled changed to: $enabled")
                },
                onAspectRatioChanged = onAspectRatioChangedCallback,
                rewindManager = rewindManager
            )

            


        }
    }
    
    @Suppress("DEPRECATION")
    private fun setupFullscreenMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun getCorePath(console: String): String {
        val gameName = intent.getStringExtra("gameName") ?: ""
        val romPath = intent.getStringExtra("romPath") ?: ""
        return if (::corePathResolverUseCase.isInitialized) {
            corePathResolverUseCase.getCorePath(console, gameName, romPath)
        } else {
            Log.e(TAG, "CorePathResolverUseCase not initialized")
            "fceumm_libretro_android.so" // Fallback
        }
    }
    
    /**
     * Retourne la liste des cores à essayer dans l'ordre (fallback automatique)
     * Si le premier core crash, on essaie le suivant automatiquement
     */
    private fun getCoreFallbacks(console: String): List<String> {
        val gameName = intent.getStringExtra("gameName") ?: ""
        val romPath = intent.getStringExtra("romPath") ?: ""
        return if (::corePathResolverUseCase.isInitialized) {
            corePathResolverUseCase.getCoreFallbacks(console, gameName, romPath)
        } else {
            Log.e(TAG, "CorePathResolverUseCase not initialized")
            listOf(getCorePath(console))
        }
    }
    
    // Charger et appliquer les codes de triche au démarrage
    private fun loadAndApplyCheats() {
        // Utiliser ApplyCheatUseCase pour charger et appliquer les cheats
        val (result, cheats) = applyCheatUseCase.loadAndApplyCheats(console, gameName, romPath)
        
        // Synchroniser loadedCheats avec les cheats chargés (pour la sauvegarde et l'UI)
        loadedCheats.clear()
        loadedCheats.addAll(cheats)
        
        if (!result.success) {
            Log.e(TAG, "Failed to apply cheats: ${result.errorMessage}")
        } else if (result.enabledCount > 0) {
            Log.i(TAG, "[$console] Successfully applied ${result.enabledCount} cheat(s) for $gameName")
        } else if (result.loadedCount > 0) {
            Log.d(TAG, "[$console] Loaded ${result.loadedCount} cheat(s) for $gameName (none enabled)")
        }
    }
    
    // Sauvegarder l'état des cheats (enabled/disabled) dans le fichier .cht
    private fun saveCheatStates() {
        applyCheatUseCase.saveCheatStates(console, gameName, loadedCheats)
    }
    
    /**
     * Helper pour exécuter du code sur le GL Thread de manière sûre
     * CRITIQUE: Tous les appels JNI (serializeState, unserializeState, etc.) DOIVENT être exécutés sur le GL Thread
     */
    private suspend fun <T> runOnGLThread(block: () -> T): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        retroView.queueEvent {
            try {
                val result = block()
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    // Sauvegarder l'état du jeu dans un slot (organisé par console/slot)
    private fun saveGameState(slot: Int) {
        if (::saveStateUseCase.isInitialized) {
            saveStateUseCase.saveGameState(slot)
        } else {
            Log.e(TAG, "SaveStateUseCase not initialized")
            Toast.makeText(this, "Save system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Charger l'état du jeu depuis un slot (organisé par console/slot)
    private fun loadGameState(slot: Int) {
        if (::saveStateUseCase.isInitialized) {
            saveStateUseCase.loadGameState(slot)
        } else {
            Log.e(TAG, "SaveStateUseCase not initialized")
            Toast.makeText(this, "Load system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Supprimer une sauvegarde (slot)
    private fun deleteSaveSlot(console: String, gameName: String, slot: Int) {
        if (::saveStateUseCase.isInitialized) {
            saveStateUseCase.deleteSaveSlot(slot)
        } else {
            Log.e(TAG, "SaveStateUseCase not initialized")
            Toast.makeText(this, "Delete system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // État pour fast forward et pause (MutableState pour reactivity Compose)
    private val isFastForwardActive = mutableStateOf(false)
    private var fastForwardRatio = 2  // 2x par défaut (2x, 3x, 4x disponibles)
    private var isPaused = false
    private val currentSaveSlot = mutableStateOf(0)  // Slot par défaut (0-9), MutableState pour reactivity
    
    // État pour audio mute (MutableState pour reactivity Compose)
    private val audioMuted = mutableStateOf(false)
    
    // État pour shader selection (MutableState pour reactivity Compose)
    private val currentShader = mutableStateOf(com.retroplay.shader.ShaderManager.ShaderPreset.DEFAULT)
    
    // Référence aux bounds pour aspect ratio (initialisée dans onCreate)
    private var gameViewBoundsForAspectRatio: MutableState<androidx.compose.ui.geometry.Rect?>? = null
    
    // Callback pour aspect ratio (utilise une lambda qui appelle applyAspectRatio)
    private val onAspectRatioChangedCallback: (String) -> Unit = { aspectRatio ->
        applyAspectRatio(aspectRatio)
    }
    
    // État pour QuickActionsBar visibility (MutableState pour reactivity Compose)
    private val quickActionsBarVisible = mutableStateOf(true)
    private val quickActionsBarAutoHideEnabled = mutableStateOf(true)  // Auto-hide activé par défaut
    private val quickActionsBarAutoHideTimer = mutableStateOf(0L)  // Timer pour auto-hide
    
    // État pour mode d'affichage du crosshair Zapper (MutableState pour reactivity Compose)
    private val crosshairMode = mutableStateOf(CrosshairMode.RETROPLAY_ONLY)
    
    // Gérer les hotkeys RetroArch
    private fun handleHotkey(action: String) {
        if (::handleHotkeyUseCase.isInitialized) {
            handleHotkeyUseCase.handleHotkey(action)
        } else {
            Log.e(TAG, "HandleHotkeyUseCase not initialized")
            Toast.makeText(this, "Hotkey system not ready", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleHotkeyChange(action: String, pressed: Boolean) {
        if (::handleHotkeyUseCase.isInitialized) {
            handleHotkeyUseCase.handleHotkeyChange(action, pressed)
        } else {
            Log.e(TAG, "HandleHotkeyUseCase not initialized")
        }
    }

    private fun beginRewind(): Boolean {
        val manager = rewindManager ?: return false
        val started = manager.startRewind()
        if (!started) {
            notifyRewindUnavailable()
        }
        return started
    }

    private fun endRewind() {
        rewindManager?.stopRewind()
        // No screenshot needed at end of rewind
    }
    
    /**
     * Gère les actions lightgun depuis les overlays (gun_trigger, gun_reload, etc.)
     * Convertit le nom de l'action en ID numérique et envoie l'action au port lightgun configuré
     */
    fun handleLightgunAction(action: String) {
        if (::handleZapperUseCase.isInitialized) {
            handleZapperUseCase.handleLightgunAction(action)
        } else {
            Log.e(TAG, "HandleZapperUseCase not initialized")
            Toast.makeText(this, "Zapper system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Capture a screenshot and save it as a thumbnail for the save slot
     */
    private fun captureSlotThumbnail(slot: Int) {
        if (::saveStateUseCase.isInitialized) {
            saveStateUseCase.captureSlotThumbnail(slot)
        } else {
            Log.w(TAG, "SaveStateUseCase not initialized, cannot capture thumbnail")
        }
    }
    
    // Quick Win #1: Fast Forward Toggle
    private fun toggleFastForward() {
        if (::emulatorControlsUseCase.isInitialized) {
            emulatorControlsUseCase.toggleFastForward()
        } else {
            Log.e(TAG, "EmulatorControlsUseCase not initialized")
            Toast.makeText(this, "Controls system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Quick Win #2: Audio Mute Toggle
    private fun toggleAudioMute() {
        if (::emulatorControlsUseCase.isInitialized) {
            emulatorControlsUseCase.toggleAudioMute()
        } else {
            Log.e(TAG, "EmulatorControlsUseCase not initialized")
            Toast.makeText(this, "Controls system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // QuickActionsBar Visibility Toggle
    private fun toggleQuickActionsBar() {
        if (::emulatorControlsUseCase.isInitialized) {
            emulatorControlsUseCase.toggleQuickActionsBar()
        } else {
            Log.e(TAG, "EmulatorControlsUseCase not initialized")
            Toast.makeText(this, "Controls system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Applique un aspect ratio personnalisé en calculant et appliquant le viewport approprié
     * @param aspectRatioString Aspect ratio sélectionné ("AUTO", "4:3", "16:9", etc.)
     */
    fun applyAspectRatio(aspectRatioString: String) {
        if (::emulatorControlsUseCase.isInitialized) {
            emulatorControlsUseCase.applyAspectRatio(aspectRatioString, console) {
                // Retry callback
                applyAspectRatio(aspectRatioString)
            }
        } else {
            Log.e(TAG, "EmulatorControlsUseCase not initialized")
            Toast.makeText(this, "Controls system not ready", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Quick Win #4: Shader Cycle (Next/Prev shader)
    // Délégué à HandleHotkeyUseCase via handleHotkey("shader_next") ou handleHotkey("shader_prev")
    
    // Toggle Crosshair Mode (Cycle entre RetroPlay / FCEUmm / Both / None)
    private fun toggleCrosshairMode() {
        crosshairMode.value = crosshairMode.value.next()
        Log.i(TAG, "[CROSSHAIR] Mode: ${crosshairMode.value.displayName}")
        
        // Sauvegarder dans SharedPreferences
        prefs.edit().putString("emulation_crosshair_mode", crosshairMode.value.name).apply()
        
        // Si on est en jeu NES, mettre à jour la config du core dynamiquement
        if (console == "nes") {
            val config = CoreConfigManager.loadConfig(this, "FCEUmm").toMutableMap()
            config["fceumm_show_crosshair"] = if (crosshairMode.value.showFCEUmmCrosshair()) "enabled" else "disabled"
            CoreConfigManager.saveConfig(this, "FCEUmm", config)
            
            // Appliquer au core sans redémarrer
            val nesVariables = config.map { (key, value) -> com.swordfish.libretrodroid.Variable(key, value) }.toTypedArray()
            retroView.updateVariables(*nesVariables)
            Log.i(TAG, "[CROSSHAIR] Updated fceumm_show_crosshair = ${config["fceumm_show_crosshair"]}")
        }
        
        runOnUiThread {
            Toast.makeText(
                this,
                "Crosshair: ${crosshairMode.value.displayName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * Applique la config zapper au runtime (appelé après changement de config per-game)
     * Applique la configuration sauvegardée pour les jeux zapper NES
     */
    private fun applyZapperConfig() {
        if (console == "nes" && isZapperGame) {
            try {
                val config = CoreConfigManager.loadConfig(this, "FCEUmm").toMutableMap()
                
                // Appliquer au core sans redémarrer
                val nesVariables = config.map { (key, value) -> com.swordfish.libretrodroid.Variable(key, value) }.toTypedArray()
                retroView.updateVariables(*nesVariables)
                Log.i(TAG, "[ZAPPER] Applied zapper config from FCEUmm.cfg")
            } catch (e: Exception) {
                Log.e(TAG, "[ZAPPER] Failed to apply zapper config: ${e.message}", e)
            }
        }
    }
    
    // Lifecycle managed by lifecycle.addObserver(retroView)
    
    override fun onResume() {
        super.onResume()
        // Force re-apply shader on resume to ensure it's active after potential context loss
        // Delay slightly to ensure GL surface is ready
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                // Only if retroView is initialized (avoid crash on very early resume)
                if (::retroView.isInitialized) {
                    val shaderConfig = com.retroplay.shader.ShaderManager.getShaderConfig(currentShader.value)
                    retroView.shader = shaderConfig
                    Log.i(TAG, "[LIFECYCLE] onResume - Re-applied shader: ${currentShader.value.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[LIFECYCLE] Error re-applying shader in onResume", e)
            }
        }, 500)
    }

    override fun onDestroy() {
        try {
            Log.i(TAG, "[LIFECYCLE] onDestroy called - cleaning up core")
            rewindManager?.reset()
            // Ne pas appeler retroView.onDestroy() manuellement car lifecycle.addObserver le fait déjà
            // Juste logger pour le debug
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    private fun notifyRewindUnavailable() {
        val manager = rewindManager
        val message = when {
            manager == null -> "Rewind not available"
            !manager.enabled.value -> "Rewind is disabled for this game"
            !manager.isSupported.value -> "Rewind unsupported by current core"
            manager.availableStates.value == 0 -> "Rewind buffer is empty"
            else -> "Rewind not available"
        }
        runOnUiThread {
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}


