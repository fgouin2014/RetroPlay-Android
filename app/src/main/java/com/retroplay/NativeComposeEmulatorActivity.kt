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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalConfiguration
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.layouts.*
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKit
import gg.padkit.inputevents.InputEvent
import gg.padkit.ids.Id
import androidx.compose.ui.util.lerp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.File

/**
 * Native Compose Emulator Activity
 * 
 * Features:
 * - Jetpack Compose UI
 * - Lemuroid-TouchInput native gamepads
 * - Vector-based PlayStation symbols (uniform)
 * - LibretroDroid native cores (ARM64)
 */
class NativeComposeEmulatorActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "NativeComposeEmulator"
        private const val CRASH_PREFS = "core_crash_detection"
        private const val KEY_LAST_GAME = "last_game_path"
        private const val KEY_LAST_CORE = "last_core_attempted"
        private const val KEY_TIMESTAMP = "crash_timestamp"
        private const val CRASH_TIMEOUT_MS = 2000L // 2 secondes pour considérer un crash
        
        /**
         * Convertit le nom technique du core en nom d'affichage lisible
         */
        private fun getCoreDisplayName(coreFileName: String): String {
            val coreName = coreFileName.replace("_libretro_android.so", "")
            return when (coreName.lowercase()) {
                "fbneo" -> "FBNeo"
                "mame2003_plus" -> "MAME 2003 Plus"
                "mame2003" -> "MAME 2003"
                "mame2010" -> "MAME 2010"
                "fceumm" -> "FCEUmm"
                "mesen" -> "Mesen"
                "snes9x" -> "Snes9x"
                "parallel_n64" -> "ParaLLEl N64"
                "mupen64plus_next" -> "Mupen64Plus Next"
                "gambatte" -> "Gambatte"
                "libmgba", "mgba" -> "mGBA"
                "pcsx_rearmed" -> "PCSX ReARMed"
                "ppsspp" -> "PPSSPP"
                "genesis_plus_gx" -> "Genesis Plus GX"
                "picodrive" -> "PicoDrive"
                else -> coreName.uppercase().replace("_", " ")
            }
        }
        
    }
    
    private lateinit var retroView: GLRetroView
    private lateinit var console: String
    private lateinit var romPath: String
    private lateinit var gameName: String
    private lateinit var prefs: SharedPreferences
    private lateinit var cheatApplier: com.retroplay.cheat.CheatApplier
    private var currentCoreFilePath: String? = null
    
    // Zapper support (NES light gun)
    private var isZapperGame: Boolean = false
    
    // États des menus
    private val showMainMenu = mutableStateOf(false)
    private val showGamePadSettings = mutableStateOf(false)
    private val showQuickMenu = mutableStateOf(false)
    private val overlaysVisible = mutableStateOf(true)
    private val showCoreErrorDialog = mutableStateOf(false)
    private val showCoreSelectorFromError = mutableStateOf(false)
    private var failedCoreName = ""
    private val showCoreChangeConfirmDialog = mutableStateOf(false)
    private var coreChangeConfirmMessage = ""
    
    // États pour DIP Switches et Core Options
    private val showDipSwitchDialog = mutableStateOf(false)
    private val showCoreOptionsDialog = mutableStateOf(false)
    private var allCoreVariables = mutableStateListOf<CoreVariable>()
    private val dipSwitches = mutableStateListOf<CoreVariable>()
    private val coreOptions = mutableStateListOf<CoreVariable>()
    
    /**
     * Termine l'activité de manière sécurisée.
     * MAME2010 a un bug dans son destructeur, on doit donc utiliser killProcess().
     * @param delayMs Délai en millisecondes avant de terminer (pour laisser les dialogs s'afficher)
     */
    private fun safeFinishActivity(currentCore: String?, delayMs: Long = 0) {
        val isMame2010 = currentCore?.contains("mame2010", ignoreCase = true) == true
        
        if (delayMs > 0) {
            // Retarder la fermeture pour laisser le temps aux dialogs de s'afficher
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isMame2010) {
                    Log.w(TAG, "⚠️ MAME2010 detected - using process kill to avoid destructor crash")
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else {
                    Log.i(TAG, "✓ Normal activity finish (returning to GameDetails)")
                    finish() // Retourne à GameDetailsActivity
                }
            }, delayMs)
        } else {
            if (isMame2010) {
                Log.w(TAG, "⚠️ MAME2010 detected - using process kill to avoid destructor crash")
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                Log.i(TAG, "✓ Normal activity finish (returning to GameDetails)")
                finish() // Retourne à GameDetailsActivity
            }
        }
    }
    
    /**
     * Gestion des touches Zapper - Émule le bouton A au port 2 (Zapper port)
     * Port 1 (index 0) = Manette standard (Start/Select pour menus)
     * Port 2 (index 1) = Zapper (Button A pour tirer)
     * 
     * Filtre les touches pour ne capturer QUE la zone centrale (35%-65% de largeur)
     */
    private fun handleZapperTouch(event: android.view.MotionEvent): Boolean {
        if (!isZapperGame) {
            return false
        }
        
        // Vérifier que la touche est dans la zone centrale (35% à 65% de largeur)
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val touchX = event.x
        val relativeX = touchX / screenWidth
        
        // Zone libre pour Duck Hunt : 35% à 65% (30% de largeur au centre)
        val isInZapperZone = relativeX >= 0.35f && relativeX <= 0.65f
        
        if (!isInZapperZone) {
            Log.d(TAG, "[ZAPPER] Touch OUTSIDE zone centrale: x=$touchX (${(relativeX * 100).toInt()}%) - ignored")
            return false  // Laisser passer au gamepad
        }
        
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "[ZAPPER] Touch DOWN in zone centrale: x=$touchX (${(relativeX * 100).toInt()}%) - Button A pressed (port 2)")
                // Envoyer au port 1 (Player 2 / Zapper port)
                retroView.sendKeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_BUTTON_A,
                    1  // Port 2 (index 1)
                )
                return true
            }
            
            android.view.MotionEvent.ACTION_UP -> {
                Log.d(TAG, "[ZAPPER] Touch UP in zone centrale: x=$touchX (${(relativeX * 100).toInt()}%) - Button A released (port 2)")
                // Envoyer au port 1 (Player 2 / Zapper port)
                retroView.sendKeyEvent(
                    android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_BUTTON_A,
                    1  // Port 2 (index 1)
                )
                return true
            }
            
            else -> return false
        }
    }
    
    /**
     * Charge les variables de core (DIP switches et Core Options) et applique les valeurs sauvegardées
     */
    private fun loadCoreVariables() {
        try {
            // Extraire le coreId AVANT de récupérer les variables pour vérifier la cohérence
            val expectedCoreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
            Log.i(TAG, "Loading variables for core: $expectedCoreId")
            
            // Récupérer les variables depuis le core LibretroDroid
            val variables = retroView.getVariables()
            Log.i(TAG, "Retrieved ${variables.size} variables from core")
            
            // Vérifier que les variables correspondent au core attendu
            if (variables.isNotEmpty()) {
                val firstKey = variables.firstOrNull()?.key ?: ""
                Log.i(TAG, "First variable key: $firstKey (expected prefix: $expectedCoreId)")
            }
            
            // Parser les variables
            val parsed = CoreVariableManager.parseVariables(variables)
            
            // Filtrer pour ne garder que les variables du core actuel
            val filtered = CoreVariableManager.filterVariablesForCore(parsed, expectedCoreId)
            
            allCoreVariables.clear()
            allCoreVariables.addAll(filtered)
            
            // Séparer DIP switches et Core Options
            dipSwitches.clear()
            dipSwitches.addAll(CoreVariableManager.getDipSwitches(filtered))
            coreOptions.clear()
            coreOptions.addAll(CoreVariableManager.getCoreOptions(filtered))
            
            Log.i(TAG, "Parsed: ${dipSwitches.size} DIP switches, ${coreOptions.size} core options")
            
            // Extraire le coreId depuis le chemin du fichier
            val coreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
            val gameId = File(romPath).nameWithoutExtension
            
            // Charger les valeurs sauvegardées
            val savedValues = CoreVariableManager.loadVariables(this, gameId, coreId)
            
            if (savedValues.isNotEmpty()) {
                // Appliquer les valeurs sauvegardées
                val updatedVariables = CoreVariableManager.applyLoadedValues(filtered, savedValues)
                allCoreVariables.clear()
                allCoreVariables.addAll(updatedVariables)
                dipSwitches.clear()
                dipSwitches.addAll(CoreVariableManager.getDipSwitches(updatedVariables))
                coreOptions.clear()
                coreOptions.addAll(CoreVariableManager.getCoreOptions(updatedVariables))
                
                // Appliquer au core LibretroDroid
                val libretroVars = CoreVariableManager.toLibretroVariables(updatedVariables)
                retroView.updateVariables(*libretroVars)
                Log.i(TAG, "Applied ${savedValues.size} saved variables to core")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading core variables", e)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupFullscreenMode()
        
        romPath = intent.getStringExtra("romPath") ?: run {
            Log.e(TAG, "No ROM path provided")
            Toast.makeText(this, "Error: No ROM path", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        console = intent.getStringExtra("console") ?: "psx"
        gameName = intent.getStringExtra("gameName") ?: "Game"
        val gameId = intent.getStringExtra("gameId") ?: gameName  // Use gameName as fallback
        val loadSlot = intent.getIntExtra("loadSlot", 0)  // 0 = nouvelle partie, 1-5 = charger slot
        
        // Détecter les jeux Zapper AVANT la création de GLRetroViewData
        // pour pouvoir passer les variables initiales au core
        isZapperGame = ZapperGameDetector.isZapperGame(gameName, console)
        if (isZapperGame) {
            Log.i(TAG, "[ZAPPER] Zapper game detected EARLY: $gameName")
        }
        
        Log.i(TAG, "🟢 NativeComposeEmulator starting: $gameName ($console) from $romPath" + 
                if (loadSlot > 0) " [LOAD SLOT $loadSlot]" else " [NEW GAME]")
        
        // === DÉTECTION DE CRASH (SANS FALLBACK AUTOMATIQUE) ===
        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        val lastGamePath = crashPrefs.getString(KEY_LAST_GAME, null)
        val lastCoreAttempted = crashPrefs.getString(KEY_LAST_CORE, null)
        val lastTimestamp = crashPrefs.getLong(KEY_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        
        // Charger les settings depuis SharedPreferences
        prefs = getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)
        val savedSettings = loadSettings(prefs, console)
        
        // ALWAYS use NATIVE (Radial/Lemuroid) in this activity
        val savedVariant = GamePadLayoutManager.LayoutVariant.DEFAULT
        Log.i(TAG, "Emulator mode forced: NATIVE (Radial) in NativeComposeEmulatorActivity")
        
        // Créer GLRetroView avec GLRetroViewData
        val data = com.swordfish.libretrodroid.GLRetroViewData(this).apply {
            // Utiliser le core normal (avec override si défini)
            val selectedCore = getCorePath(console)
            
            // Vérifier si le dernier lancement a crashé (même jeu, moins de 5s)
            val wasCrash = lastGamePath == romPath && 
                           lastCoreAttempted != null && 
                           (currentTime - lastTimestamp) < CRASH_TIMEOUT_MS
            
            // N'afficher le dialog QUE si le core est IDENTIQUE au core qui a crashé
            // Si l'utilisateur a changé le core manuellement, ne pas afficher le dialog
            if (wasCrash && lastCoreAttempted == selectedCore) {
                Log.w(TAG, "⚠️ CRASH DETECTED on previous attempt with core: $lastCoreAttempted")
                // Extraire le nom du core pour l'affichage (nom lisible)
                failedCoreName = getCoreDisplayName(lastCoreAttempted)
                // Nettoyer les prefs pour ne pas re-afficher le dialog
                crashPrefs.edit().clear().apply()
                // Afficher le dialog d'erreur
                showCoreErrorDialog.value = true
            } else if (wasCrash && lastCoreAttempted != selectedCore) {
                // L'utilisateur a déjà changé le core manuellement, juste nettoyer les prefs
                Log.i(TAG, "✓ User manually changed core from $lastCoreAttempted to $selectedCore")
                crashPrefs.edit().clear().apply()
            }
            
            coreFilePath = selectedCore
            gameFilePath = romPath
            
            // Sauvegarder le core actuel pour le cleanup
            this@NativeComposeEmulatorActivity.currentCoreFilePath = selectedCore
            
            // Sauvegarder la tentative actuelle pour détecter un crash futur
            crashPrefs.edit().apply {
                putString(KEY_LAST_GAME, romPath)
                putString(KEY_LAST_CORE, selectedCore)
                putLong(KEY_TIMESTAMP, currentTime)
                apply()
            }
            Log.i(TAG, "📝 Core logged: $selectedCore for $gameName")
            
            // System directory (BIOS)
            systemDirectory = "/storage/emulated/0/GameLibrary-Data/data/bios"
            
            // Saves directory (SHARED)
            val sharedSavesDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console")
            if (!sharedSavesDir.exists()) sharedSavesDir.mkdirs()
            savesDirectory = sharedSavesDir.absolutePath
            
            // Shader
            shader = ShaderConfig.Default
            
            // Options
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true

            // Configuration des variables de core via l'API officielle LibretroDroid
            val corePrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@NativeComposeEmulatorActivity)
            val prefix = "${console}_"

            // Détecter le core N64 réellement utilisé
            val actualCoreFile = coreFilePath ?: ""
            val isParallelN64 = actualCoreFile.contains("parallel_n64")
            val isMupen64Plus = actualCoreFile.contains("mupen64plus")

            // Configuration des variables selon la console
            Log.i(TAG, "[INIT] Configuring core variables for console: '$console', isZapperGame=$isZapperGame")
            when (console) {
                "n64" -> {
                    val resolution = corePrefs.getInt("${prefix}n64_resolution", 0)
                    val antialiasing = corePrefs.getInt("${prefix}n64_antialiasing", 0)
                    val bilinear = corePrefs.getBoolean("${prefix}n64_bilinear", false)
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
                        // Mupen64Plus Next variables (plus limitées que ParaLLEl N64)
                        // Mupen64Plus a moins d'options configurables via variables
                        Log.i(TAG, "[N64] Mupen64Plus Next detected - limited core options available")
                        // Pour l'instant, pas de variables spécifiques connues pour Mupen64Plus
                        // On pourrait ajouter des variables si elles sont découvertes
                        emptyArray<Variable>()
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
                    } catch (e: Exception) {
                        Log.w(TAG, "[N64] Failed to set variables via GLRetroViewData API: ${e.message}")
                    }
                }
                "psx", "ps1", "playstation" -> {
                    val resolution = corePrefs.getInt("${prefix}psx_resolution", 0)
                    val textureFiltering = corePrefs.getBoolean("${prefix}psx_texture_filtering", true)
                    val dithering = corePrefs.getBoolean("${prefix}psx_dithering", true)
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
                    // Note: Zapper configuration désactivée car LibretroDroid ne supporte pas RETRO_DEVICE_LIGHTGUN
                    // L'utilisateur doit utiliser le bouton A du gamepad pour tirer dans Duck Hunt
                    if (isZapperGame) {
                        Log.i(TAG, "[NES] Zapper game detected: $gameName - use gamepad button A to shoot")
                    }
                }
                "snes" -> {
                    val blendMode = corePrefs.getInt("${prefix}snes_blend_mode", 0)
                    val hires = corePrefs.getBoolean("${prefix}snes_hires", false)
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
        lifecycle.addObserver(retroView)
        
        // === ÉCOUTE DES ERREURS LIBRETRODROID (CHARGEMENT ÉCHOUÉ) ===
        lifecycleScope.launch {
            try {
                retroView.getGLRetroErrors().collect { errorCode ->
                    Log.e(TAG, "❌ GLRetroView ERROR detected: $errorCode")
                    
                    // Erreurs qui nécessitent l'affichage du dialog
                    val needsUserAction = errorCode == GLRetroView.ERROR_LOAD_GAME || 
                                         errorCode == GLRetroView.ERROR_LOAD_LIBRARY
                    
                    if (needsUserAction) {
                        val selectedCore = data.coreFilePath ?: "unknown"
                        Log.w(TAG, "⚠️ Core $selectedCore failed to load game")
                        
                        // Extraire le nom du core pour l'affichage (nom lisible)
                        failedCoreName = getCoreDisplayName(selectedCore)
                        
                        // Afficher le dialog d'erreur
                        runOnUiThread {
                            showCoreErrorDialog.value = true
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
                if (!showCoreErrorDialog.value) {
                    // Succès ! Le jeu ne crashe pas avec ce core
                    Log.i(TAG, "✅ SUCCESS: Game running successfully with core: ${data.coreFilePath}")
                    
                    // Nettoyer les infos de crash
                    crashPrefs.edit().clear().apply()
                } else {
                    Log.w(TAG, "⚠️ Error dialog is showing, not declaring success")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in success detection: ${e.message}")
            }
        }, CRASH_TIMEOUT_MS)
        
        // Configurer le type de contrôleur pour PSX (DualShock pour analog sticks)
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
        
        // Configuration pour les jeux Zapper (Duck Hunt, etc.)
        // Port 1 = Gamepad (Start/Select), Port 2 = Zapper (Touch to shoot)
        if (isZapperGame) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runOnUiThread {
                    Toast.makeText(
                        this@NativeComposeEmulatorActivity,
                        "Zapper detected!\nPort 1: Gamepad (Start/Select)\nPort 2: Touch game area to shoot",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 1000)
        }

        // Configurer les extensions contrôleur pour N64
        if (console.equals("n64", ignoreCase = true)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.i(TAG, "[N64] Configuring controller extensions...")

                    // Charger les paramètres depuis SharedPreferences
                    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@NativeComposeEmulatorActivity)
                    val prefix = "n64_"

                    // Mapping des positions spinner vers les valeurs Libretro :
                    // 0 = Controller Pak (1), 1 = Rumble Pak (2), 2 = Transfer Pak (5)
                    val pakValues = intArrayOf(1, 2, 5)

                    // TODO: Configurer les extensions contrôleur N64
                    // Les valeurs sont sauvegardées dans les préférences mais l'application dans LibretroDroid
                    // nécessite une investigation supplémentaire pour la méthode correcte
                    for (port in 0..3) {  // 4 ports maximum pour N64
                        try {
                            val pakPosition = prefs.getInt(prefix + "pak_port" + (port + 1), 0) // Default: Controller Pak
                            val pakValue = pakValues.getOrElse(pakPosition) { 1 } // Fallback to Controller Pak

                            val pakName = when (pakPosition) {
                                0 -> "Controller Pak"
                                1 -> "Rumble Pak"
                                2 -> "Transfer Pak"
                                else -> "Unknown"
                            }
                            Log.i(TAG, "[N64] Extension configured for port ${port + 1}: $pakName (value=$pakValue)")
                        } catch (e: Exception) {
                            Log.w(TAG, "[N64] Could not configure extension for port ${port + 1}: ${e.message}")
                        }
                    }


                } catch (e: Exception) {
                    Log.e(TAG, "[N64] Error configuring controller extensions", e)
                }
            }, 1000)  // Attendre 1 seconde pour que le core soit complètement initialisé
        }

        // Initialiser le CheatApplier
        cheatApplier = com.retroplay.cheat.CheatApplier(retroView)
        
        // === CHARGER LES DIP SWITCHES ET CORE OPTIONS ===
        // Attendre que le core expose ses variables (certains cores comme MAME prennent du temps)
        // Délai réduit à 2 secondes pour s'assurer que le core est complètement initialisé
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                try {
                    loadCoreVariables()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load core variables: ${e.message}")
                }
            }
        }, 2000)
        
        // Charger la save depuis le slot demandé (si loadSlot > 0)
        // IMPORTANT : Différer le chargement pour laisser le core s'initialiser
        if (loadSlot > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val saveFile = File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$loadSlot/$gameName.state")
                if (saveFile.exists()) {
                    try {
                        retroView.unserializeState(saveFile.readBytes())
                        Log.i(TAG, "[$console] Save state loaded from slot $loadSlot: ${saveFile.absolutePath}")
                        Toast.makeText(this, "[$console] Loaded from Slot $loadSlot", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "[$console] Error loading save from slot $loadSlot", e)
                        Toast.makeText(this, "[$console] Error loading save", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "[$console] No save found in slot $loadSlot")
                    Toast.makeText(this, "[$console] No save in Slot $loadSlot", Toast.LENGTH_SHORT).show()
                }
            }, 2000)  // Attendre 2 secondes pour que le core s'initialise complètement
        }
        
        // Charger et appliquer les codes de triche activés APRÈS le chargement complet du jeu
        // Pour les jeux PSX (PBP), le chargement initial est lent, donc on attend plus longtemps
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            loadAndApplyCheats()
        }, if (loadSlot > 0) 3000 else 8000)  // 8s pour NEW GAME, 3s pour LOAD SAVE
        
        setContent {
            ComposeEmulatorScreen(
                retroView = retroView,
                console = console,
                gameName = gameName,
                romPath = romPath,
                prefs = prefs,
                showMainMenu = showMainMenu,
                showGamePadSettings = showGamePadSettings,
                showQuickMenu = showQuickMenu,
                overlaysVisible = overlaysVisible,
                initialSettings = savedSettings,
                initialVariant = savedVariant,
                cheatApplier = cheatApplier,
                onSettingsChanged = { newSettings ->
                    saveSettings(prefs, console, newSettings)
                },
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
                isZapperGame = isZapperGame,
                onZapperTouch = { event ->
                    handleZapperTouch(event)
                },
                onLoadState = { slot ->
                    loadGameState(slot)
                },
                showDipSwitchDialog = showDipSwitchDialog,
                showCoreOptionsDialog = showCoreOptionsDialog,
                dipSwitches = dipSwitches,
                coreOptions = coreOptions
            )
            
            // Dialog d'erreur de chargement du core
            if (showCoreErrorDialog.value) {
                CoreErrorDialog(
                    coreName = failedCoreName,
                    gameName = gameName,
                    onChangeCore = {
                        showCoreErrorDialog.value = false
                        // Attendre un peu que le dialog se ferme avant d'ouvrir le suivant
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            showCoreSelectorFromError.value = true
                        }, 100)
                    },
                    onRetry = {
                        showCoreErrorDialog.value = false
                        // Nettoyer les prefs de crash avant de réessayer
                        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        crashPrefs.edit().clear().apply()
                        // Fermer le jeu - l'utilisateur relancera depuis GameDetailsActivity
                        safeFinishActivity(currentCoreFilePath, delayMs = 0)
                    },
                    onCancel = {
                        showCoreErrorDialog.value = false
                        // Retourner à GameDetailsActivity
                        safeFinishActivity(currentCoreFilePath, delayMs = 0)
                    }
                )
            }
            
            // Dialog de sélection de core (après erreur)
            if (showCoreSelectorFromError.value) {
                CoreSelectorDialog(
                    console = console,
                    currentGamePath = romPath,
                    onCoreSelected = { coreInfo ->
                        showCoreSelectorFromError.value = false
                        
                        // Nettoyer les prefs de crash
                        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        crashPrefs.edit().clear().apply()
                        
                        // Sauvegarder le nouveau core comme override
                        val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
                            romPath.substringAfter("/GameLibrary-Data/")
                        } else {
                            ""
                        }
                        if (relativePath.isNotEmpty()) {
                            val overrideManager = CoreOverrideManager.getInstance()
                            overrideManager.setOverride(relativePath, coreInfo.coreId, "User selected after core error")
                            Log.i(TAG, "Core override saved: $relativePath → ${coreInfo.coreId}")
                        }
                        // Afficher un message de confirmation avant de fermer
                        coreChangeConfirmMessage = "Core changed to ${coreInfo.displayName}.\n\nRelaunch the game from the menu to apply."
                        showCoreChangeConfirmDialog.value = true
                    },
                    onResetToDefault = {
                        showCoreSelectorFromError.value = false
                        
                        // Nettoyer les prefs de crash
                        val crashPrefs = getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        crashPrefs.edit().clear().apply()
                        
                        // Supprimer l'override
                        val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
                            romPath.substringAfter("/GameLibrary-Data/")
                        } else {
                            ""
                        }
                        if (relativePath.isNotEmpty()) {
                            val overrideManager = CoreOverrideManager.getInstance()
                            overrideManager.removeOverride(relativePath)
                            Log.i(TAG, "Core override removed: $relativePath")
                        }
                        // Afficher un message de confirmation avant de fermer
                        coreChangeConfirmMessage = "Core reset to default.\n\nRelaunch the game from the menu to apply."
                        showCoreChangeConfirmDialog.value = true
                    },
                    onDismiss = {
                        showCoreSelectorFromError.value = false
                    }
                )
            }
            
            // Dialog de confirmation du changement de core
            if (showCoreChangeConfirmDialog.value) {
                AlertDialog(
                    onDismissRequest = { showCoreChangeConfirmDialog.value = false },
                    title = {
                        Text(
                            text = "Core Changed",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = coreChangeConfirmMessage,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCoreChangeConfirmDialog.value = false
                                // Fermer le jeu avec délai pour une transition fluide
                                safeFinishActivity(currentCoreFilePath, delayMs = 1200)
                            }
                        ) {
                            Text("OK", color = Color(0xFF4CAF50), fontSize = 16.sp)
                        }
                    },
                    containerColor = Color(0xFF2C2C2C),
                    tonalElevation = 8.dp
                )
            }
            
            // === DIP SWITCHES DIALOG ===
            if (showDipSwitchDialog.value) {
                DipSwitchDialog(
                    gameName = gameName,
                    dipSwitches = dipSwitches,
                    onApply = { modifiedValues ->
                        // Extraire le coreId
                        val coreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
                        val gameId = File(romPath).nameWithoutExtension
                        
                        // Sauvegarder les modifications
                        CoreVariableManager.saveVariables(this@NativeComposeEmulatorActivity, gameId, coreId, modifiedValues)
                        
                        // Appliquer au core
                        val updatedVars = dipSwitches.map { dip ->
                            if (modifiedValues.containsKey(dip.key)) {
                                dip.copy(currentValue = modifiedValues[dip.key]!!)
                            } else {
                                dip
                            }
                        }
                        val libretroVars = CoreVariableManager.toLibretroVariables(updatedVars)
                        retroView.updateVariables(*libretroVars)
                        
                        // Mettre à jour la liste locale
                        dipSwitches.clear()
                        dipSwitches.addAll(updatedVars)
                        
                        Log.i(TAG, "Applied ${modifiedValues.size} DIP switch changes")
                    },
                    onDismiss = { showDipSwitchDialog.value = false }
                )
            }
            
            // === CORE OPTIONS DIALOG ===
            if (showCoreOptionsDialog.value) {
                CoreOptionsDialog(
                    gameName = gameName,
                    coreOptions = coreOptions,
                    onApply = { modifiedValues ->
                        // Extraire le coreId
                        val coreId = currentCoreFilePath?.let { CoreVariableManager.extractCoreId(it) } ?: "unknown"
                        val gameId = File(romPath).nameWithoutExtension
                        
                        // Sauvegarder les modifications
                        CoreVariableManager.saveVariables(this@NativeComposeEmulatorActivity, gameId, coreId, modifiedValues)
                        
                        // Appliquer au core
                        val updatedVars = coreOptions.map { opt ->
                            if (modifiedValues.containsKey(opt.key)) {
                                opt.copy(currentValue = modifiedValues[opt.key]!!)
                            } else {
                                opt
                            }
                        }
                        val libretroVars = CoreVariableManager.toLibretroVariables(updatedVars)
                        retroView.updateVariables(*libretroVars)
                        
                        // Mettre à jour la liste locale
                        coreOptions.clear()
                        coreOptions.addAll(updatedVars)
                        
                        Log.i(TAG, "Applied ${modifiedValues.size} core option changes")
                    },
                    onDismiss = { showCoreOptionsDialog.value = false }
                )
            }
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
        // Vérifier s'il y a un override de core pour ce jeu spécifique
        val gameName = intent.getStringExtra("gameName") ?: ""
        val romPath = intent.getStringExtra("romPath") ?: ""
        
        // Construire le chemin relatif du jeu (ex: "fbneo/sega/afighter.zip")
        val relativePath = if (romPath.contains("/GameLibrary-Data/")) {
            romPath.substringAfter("/GameLibrary-Data/")
        } else {
            ""
        }
        
        // Vérifier l'override
        var coreFileName: String? = null
        if (relativePath.isNotEmpty()) {
            val overrideManager = CoreOverrideManager.getInstance()
            val overrideCoreId = overrideManager.getCoreOverride(relativePath)
            
            if (overrideCoreId != null) {
                Log.i(TAG, "Using core override for $gameName: $overrideCoreId")
                // Mapper le coreId vers le fichier .so
                coreFileName = when (overrideCoreId.lowercase()) {
                    "mame2010" -> "mame2010_libretro_android.so"
                    "mame2003_plus" -> "mame2003_plus_libretro_android.so"
                    "mame2003" -> "mame2003_libretro_android.so"
                    "fbneo" -> "fbneo_libretro_android.so"
                    "fceumm" -> "fceumm_libretro_android.so"
                    "snes9x" -> "snes9x_libretro_android.so"
                    "parallel_n64" -> "parallel_n64_libretro_android.so"
                    "mupen64plus_next" -> "mupen64plus_next_libretro_android.so"
                    "mupen64plus_next_gles3" -> "mupen64plus_next_libretro_android.so"
                    "mupen64plus_next_gles2" -> "mupen64plus_next_gles2_libretro_android.so"
                    "gambatte" -> "gambatte_libretro_android.so"
                    "mgba" -> "libmgba_libretro_android.so"
                    "pcsx_rearmed" -> "pcsx_rearmed_libretro_android.so"
                    "ppsspp" -> "ppsspp_libretro_android.so"
                    "genesis_plus_gx" -> "genesis_plus_gx_libretro_android.so"
                    "picodrive" -> "picodrive_libretro_android.so"
                    else -> null
                }
            }
        }
        
        // Si pas d'override, utiliser la logique par défaut basée sur la console
        if (coreFileName == null) {
        // Pour les sous-consoles (ex: fbneo/sega), utiliser le parent (fbneo)
        val consoleKey = if (console.contains("/")) {
            console.substringBefore("/").lowercase()
        } else {
            console.lowercase()
        }
        
            coreFileName = when (consoleKey) {
            // Nintendo
            "nes" -> "fceumm_libretro_android.so"
            "snes" -> "snes9x_libretro_android.so"
            "n64" -> "parallel_n64_libretro_android.so"
            "gb", "gbc" -> "gambatte_libretro_android.so"
            "gba" -> "libmgba_libretro_android.so"
            
            // Sony
            "psx", "ps1", "playstation" -> "pcsx_rearmed_libretro_android.so"
            "psp" -> "ppsspp_libretro_android.so"
            
            // Sega
            "genesis", "megadrive", "md" -> "genesis_plus_gx_libretro_android.so"
            "scd", "segacd" -> "genesis_plus_gx_libretro_android.so"
            "mastersystem", "sms", "segasms" -> "genesis_plus_gx_libretro_android.so"
            "gamegear", "gg", "segagg" -> "genesis_plus_gx_libretro_android.so"
            "32x", "sega32x" -> "picodrive_libretro_android.so"
            
            // Atari
            "atari2600", "atari", "a2600" -> "stella2014_libretro_android.so"
            "atari5200", "a5200" -> "a5200_libretro_android.so"
            "atari7800", "a7800" -> "prosystem_libretro_android.so"
                "lynx", "atarilynx" -> "mednafen_lynx_libretro_android.so"
            
            // Other
            "ngp", "ngc", "neogeopocket" -> "mednafen_ngp_libretro_android.so"
            "ws", "wsc", "wonderswan" -> "mednafen_wswan_libretro_android.so"
            "pce", "turbografx", "pcengine" -> "mednafen_pce_libretro_android.so"
            "arcade" -> "mame2003_plus_libretro_android.so"
            "mame" -> "mame2010_libretro_android.so"
            "fbneo", "neogeo", "cps1", "cps2" -> "fbneo_libretro_android.so"
            
            else -> {
                    Log.w(TAG, "No native core for console: $console, using fceumm fallback")
                "fceumm_libretro_android.so"
            }
            }
        }
        
        // Vérifier si le core est téléchargé dans RetroPlay-Data/cores/
        val downloadedCorePath = CoreDownloader.getCorePathByFileName(this, coreFileName)
        if (downloadedCorePath != null) {
            Log.i(TAG, "Using downloaded core: $downloadedCorePath")
            return downloadedCorePath
        }
        
        // Sinon, utiliser le core embarqué (juste le nom du fichier)
        Log.i(TAG, "Using embedded core: $coreFileName")
        return coreFileName
    }
    
    /**
     * Retourne la liste des cores à essayer dans l'ordre (fallback automatique)
     * Si le premier core crash, on essaie le suivant automatiquement
     */
    private fun getCoreFallbacks(console: String): List<String> {
        val consoleKey = if (console.contains("/")) {
            console.substringBefore("/").lowercase()
        } else {
            console.lowercase()
        }
        
        return when (consoleKey) {
            // Arcade: FBNeo (plus compatible) → MAME2003+ → MAME2003 → MAME2010
            "arcade" -> listOf(
                "fbneo_libretro_android.so",
                "mame2003_plus_libretro_android.so",
                "mame2003_libretro_android.so",
                "mame2010_libretro_android.so"
            )
            "mame" -> listOf(
                "mame2003_plus_libretro_android.so",
                "mame2003_libretro_android.so",
                "mame2010_libretro_android.so",
                "fbneo_libretro_android.so"
            )
            "fbneo", "neogeo", "cps1", "cps2" -> listOf(
                "fbneo_libretro_android.so",
                "mame2003_plus_libretro_android.so"
            )
            
            // N64: ParaLLEl (performant) → Mupen64Plus (compatible)
            "n64" -> listOf(
                "parallel_n64_libretro_android.so",
                "mupen64plus_next_libretro_android.so"
            )
            
            // Pour les autres consoles, un seul core disponible
            else -> listOf(getCorePath(console))
        }
    }
    
    // Charger les settings depuis SharedPreferences
    private fun loadSettings(prefs: android.content.SharedPreferences, console: String): TouchControllerSettingsManager.Settings {
        val key = "gamepad_${console}_settings"
        return TouchControllerSettingsManager.Settings(
            scale = prefs.getFloat("${key}_scale", 0.5f),
            rotation = prefs.getFloat("${key}_rotation", 0.0f),
            marginX = prefs.getFloat("${key}_marginX", 0.0f),
            marginY = prefs.getFloat("${key}_marginY", 0.0f)
        )
    }
    
    // Sauvegarder les settings dans SharedPreferences
    private fun saveSettings(prefs: android.content.SharedPreferences, console: String, settings: TouchControllerSettingsManager.Settings) {
        val key = "gamepad_${console}_settings"
        prefs.edit().apply {
            putFloat("${key}_scale", settings.scale)
            putFloat("${key}_rotation", settings.rotation)
            putFloat("${key}_marginX", settings.marginX)
            putFloat("${key}_marginY", settings.marginY)
            apply()
        }
        Log.i(TAG, "Settings saved for $console: scale=${settings.scale}, rotation=${settings.rotation}")
    }
    
    // Charger et appliquer les codes de triche au démarrage
    private fun loadAndApplyCheats() {
        try {
            val cheatManager = com.retroplay.cheat.CheatManager(this)
            val cheats = cheatManager.loadCheatsForGame(console, gameName, romPath)
            
            if (cheats.isNotEmpty()) {
                val enabledCount = cheats.count { it.enabled }
                if (enabledCount > 0) {
                    Log.i(TAG, "[$console] Loading $enabledCount active cheat(s) for $gameName")
                    cheatApplier.applyCheatsList(cheats)
                    
                    runOnUiThread {
                        Toast.makeText(this, "[$console] $enabledCount cheat(s) active", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d(TAG, "[$console] No active cheats for $gameName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cheats", e)
        }
    }
    
    // Sauvegarder l'état du jeu dans un slot (organisé par console/slot)
    private fun saveGameState(slot: Int) {
        try {
            // Structure : saves/{console}/slot{slot}/{gameName}.state
            val slotDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$slot")
            if (!slotDir.exists()) {
                slotDir.mkdirs()
            }
            
            val saveFile = File(slotDir, "${gameName}.state")
            val stateData = retroView.serializeState()
            saveFile.writeBytes(stateData)
            
            Log.i(TAG, "[$console] Game state saved to slot $slot: ${saveFile.absolutePath}")
            runOnUiThread {
                Toast.makeText(this, "[$console] Saved to Slot $slot", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving game state to slot $slot", e)
            runOnUiThread {
                Toast.makeText(this, "Error saving game", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Charger l'état du jeu depuis un slot (organisé par console/slot)
    private fun loadGameState(slot: Int) {
        try {
            // Structure : saves/{console}/slot{slot}/{gameName}.state
            val saveFile = File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$slot/${gameName}.state")
            if (saveFile.exists()) {
                retroView.unserializeState(saveFile.readBytes())
                Log.i(TAG, "[$console] Game state loaded from slot $slot: ${saveFile.absolutePath}")
                runOnUiThread {
                    Toast.makeText(this, "[$console] Loaded from Slot $slot", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "No save state found for slot $slot in $console")
                runOnUiThread {
                    Toast.makeText(this, "[$console] No save in Slot $slot", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading game state from slot $slot", e)
            runOnUiThread {
                Toast.makeText(this, "Error loading game", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Lifecycle managed by lifecycle.addObserver(retroView)
    
    override fun onDestroy() {
        try {
            Log.i(TAG, "🔴 onDestroy called - cleaning up core")
            // Ne pas appeler retroView.onDestroy() manuellement car lifecycle.addObserver le fait déjà
            // Juste logger pour le debug
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }
}

// Handle PadKit events (List of events from gamepads)
private fun handlePadKitEvent(
    events: List<InputEvent>,
    retroView: GLRetroView,
    showMainMenu: MutableState<Boolean>
) {
    // Intercepter le bouton menu (comme Lemuroid le fait)
    val menuEvent = events.firstOrNull { 
        it is InputEvent.Button && it.id == KeyEvent.KEYCODE_BUTTON_MODE
    }
    
    if (menuEvent != null && (menuEvent as InputEvent.Button).pressed) {
        Log.i("NativeComposeEmulator", "Menu button pressed, opening main menu")
        showMainMenu.value = true
        return  // Ne pas envoyer l'événement menu à l'émulateur
    }
    
    // Traiter tous les autres événements
    events.forEach { event ->
        when (event) {
            is InputEvent.Button -> {
                val keyCode = event.id
                if (keyCode != KeyEvent.KEYCODE_BUTTON_MODE) {
                    val action = if (event.pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                    retroView.sendKeyEvent(action, keyCode)
                }
            }
            
            is InputEvent.DiscreteDirection -> {
                // D-Pad et directions discrètes
                val source = when (event.id) {
                    0 -> GLRetroView.MOTION_SOURCE_DPAD
                    1 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                    2 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                    else -> GLRetroView.MOTION_SOURCE_DPAD
                }
                retroView.sendMotionEvent(source, event.direction.x, -event.direction.y)
            }
            
            is InputEvent.ContinuousDirection -> {
                // Analog sticks (mouvements continus)
                val source = when (event.id) {
                    0 -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                    1 -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                    else -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                }
                retroView.sendMotionEvent(source, event.direction.x, -event.direction.y)
            }
        }
    }
}

@Composable
private fun ComposeEmulatorScreen(
    retroView: GLRetroView,
    console: String,
    gameName: String,
    romPath: String,
    prefs: SharedPreferences,
    showMainMenu: MutableState<Boolean>,
    showGamePadSettings: MutableState<Boolean>,
    showQuickMenu: MutableState<Boolean>,
    overlaysVisible: MutableState<Boolean>,
    initialSettings: TouchControllerSettingsManager.Settings,
    initialVariant: GamePadLayoutManager.LayoutVariant,
    cheatApplier: com.retroplay.cheat.CheatApplier,
    onSettingsChanged: (TouchControllerSettingsManager.Settings) -> Unit,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onFinishActivity: () -> Unit,
    showDipSwitchDialog: MutableState<Boolean>,
    showCoreOptionsDialog: MutableState<Boolean>,
    dipSwitches: androidx.compose.runtime.snapshots.SnapshotStateList<CoreVariable>,
    coreOptions: androidx.compose.runtime.snapshots.SnapshotStateList<CoreVariable>,
    isZapperGame: Boolean = false,
    onZapperTouch: (android.view.MotionEvent) -> Boolean = { false }
) {
    // Settings manager pour les gamepads (state mutable)
    var settings by remember {
        mutableStateOf(initialSettings)
    }
    
    // Variante de layout (state mutable)
    var layoutVariant by remember {
        mutableStateOf(initialVariant)
    }
    
    // État pour le switch de layout RetroArch (overrides la préférence)
    var currentRetroArchLayout by remember { mutableStateOf<String?>(null) }
    
    // Cooldown pour éviter la réouverture immédiate du QuickMenu après fermeture
    var lastMenuCloseTime by remember { mutableStateOf(0L) }
    val cooldownMs = 800L  // 800ms de cooldown après fermeture
    
    // Fonction helper pour fermer le QuickMenu avec enregistrement du cooldown
    fun closeQuickMenuWithCooldown() {
        lastMenuCloseTime = System.currentTimeMillis()
        showQuickMenu.value = false
        retroView.onResume()
        Log.d("ComposeEmulator", "QuickMenu closed with cooldown")
    }
    
    // Détection de l'orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    // Gestion du bouton back avec un seul handler pour éviter les conflits
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        
        when {
            // 1. QuickMenu ouvert -> Fermer avec cooldown
            showQuickMenu.value -> {
                closeQuickMenuWithCooldown()
            }
            
            // 2. MainMenu ou GamePadSettings ouvert -> Fermer
            showMainMenu.value || showGamePadSettings.value -> {
                showMainMenu.value = false
                showGamePadSettings.value = false
                // Si QuickMenu n'est pas ouvert, reprendre le jeu
                if (!showQuickMenu.value) {
                    retroView.onResume()
                }
                Log.d("ComposeEmulator", "Back: MainMenu/Settings closed")
            }
            
            // 3. Aucun menu ouvert -> Ouvrir QuickMenu (avec cooldown)
            else -> {
                val timeSinceClose = currentTime - lastMenuCloseTime
                if (timeSinceClose > cooldownMs) {
                    showQuickMenu.value = true
                    retroView.onPause()
                    Log.d("ComposeEmulator", "Back: QuickMenu opened")
                } else {
                    Log.d("ComposeEmulator", "Back: Cooldown active (${cooldownMs - timeSinceClose}ms remaining)")
                }
            }
        }
    }
    
    // Récupérer le layout approprié
    val layout = if (layoutVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
        // Utiliser getLayoutWithContext pour RetroArch
        GamePadLayoutManager.getLayoutWithContext(
            console = console,
            variant = layoutVariant,
            context = retroView.context,
            prefs = prefs,
            onButtonPress = { keyCodes ->
                keyCodes.forEach { keyCode ->
                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
                }
            },
            onButtonRelease = { keyCodes ->
                keyCodes.forEach { keyCode ->
                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, keyCode, 0)
                }
            },
            onLayoutSwitch = { requestedLayout ->
                // Mapper le layout demandé à l'orientation physique du device
                val mappedLayout = mapLayoutToDeviceOrientation(
                    requestedLayout = requestedLayout,
                    isLandscape = isLandscape
                )
                currentRetroArchLayout = mappedLayout
                Log.i("ComposeEmulator", "RetroArch layout switch: requested='$requestedLayout' mapped='$mappedLayout' (device=${if (isLandscape) "landscape" else "portrait"})")
            },
            onMenuToggle = {
                // Ouvrir le menu principal
                Log.i("ComposeEmulator", "Menu toggle from RetroArch overlay")
                showMainMenu.value = true
            }
        )
    } else {
        // Utiliser getLayout normal pour Lemuroid
        GamePadLayoutManager.getLayout(console, layoutVariant)
    }
    
    // Contraintes pour le layout (style Lemuroid)
    val constraintSet = if (isLandscape) {
        // Paysage : gameView fullscreen, gamepads par-dessus (overlay)
        buildLandscapeConstraints()
    } else {
        // Portrait : gameView en haut, gamepads en bas
        buildPortraitConstraints()
    }
    
    // Fournir le thème Lemuroid pour les gamepads
    CompositionLocalProvider(LocalLemuroidPadTheme provides LemuroidPadTheme()) {
        MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (layoutVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    // Mode RetroArch : Overlay fullscreen par-dessus le gameView
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Emulator View avec offset vertical pour éviter que les doigts cachent l'écran
                        // Portrait : offset de 20% vers le haut (laisse espace pour les doigts en bas)
                        // Landscape : centré (pas d'offset, les boutons sont sur les côtés)
                        val verticalOffsetDp = if (isLandscape) {
                            0.dp  // Landscape : pas d'offset
                        } else {
                            (-configuration.screenHeightDp * 0.20f).dp  // Portrait : 20% vers le haut
                        }
                        
                        AndroidView(
                            factory = { retroView },
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .offset(y = verticalOffsetDp)
                        )
                        
                        // Overlay RetroArch fullscreen par-dessus (appelé directement, pas via LayoutPair)
                        // Utiliser State pour recharger dynamiquement quand les prefs changent
                        val overlayPreferenceState = remember { mutableStateOf(com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console)) }
                        
                        // CRITIQUE: Garder une référence forte au listener pour éviter le garbage collection
                        val preferenceListener = remember {
                            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                android.util.Log.d("ComposeEmulator", "⚙️ Preference changed: key='$key' | console='$console'")
                                val matchesOverlay = key?.startsWith("overlay_$console") == true
                                val matchesVariant = key == "gamepad_${console}_variant"
                                android.util.Log.d("ComposeEmulator", "  matchesOverlay=$matchesOverlay, matchesVariant=$matchesVariant")
                                
                                if (matchesOverlay || matchesVariant) {
                                    val newPref = com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console)
                                    overlayPreferenceState.value = newPref
                                    android.util.Log.i("ComposeEmulator", "🔄 Overlay preference reloaded for $console: overlay='${newPref?.overlayName}' landscape='${newPref?.landscapeLayout}' portrait='${newPref?.portraitLayout}'")
                                    // Reset currentRetroArchLayout pour forcer l'utilisation de la nouvelle préférence
                                    currentRetroArchLayout = null
                                }
                            }
                        }
                        
                        // Recharger la préférence quand elle change dans GamePad Settings
                        DisposableEffect(console) {
                            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
                            android.util.Log.d("ComposeEmulator", "Listener registered for $console")
                            onDispose {
                                prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                                android.util.Log.d("ComposeEmulator", "Listener unregistered for $console")
                            }
                        }
                        
                        val overlayPreference = overlayPreferenceState.value
                        if (overlayPreference != null) {
                            val assetManager = remember { com.retroplay.overlay.assets.OverlayAssetManager(retroView.context) }
                            // Recharger la config si le nom de l'overlay change
                            val overlayConfig = remember(overlayPreference.overlayName, console) {
                                assetManager.loadOverlayConfig(overlayPreference.overlayName, console)
                            }
                            
                            // Utiliser currentRetroArchLayout si défini (boutons overlay_next), 
                            // sinon utiliser la préférence (GamePad Settings)
                            // IMPORTANT: Dépendre aussi de overlayPreference.landscapeLayout et portraitLayout pour recomposer !
                            val requestedLayoutName = currentRetroArchLayout ?: run {
                                if (overlayPreference.autoRotate) {
                                    val selected = if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
                                    android.util.Log.d("ComposeEmulator", "Auto-rotate: isLandscape=$isLandscape, selected layout='$selected'")
                                    selected
                                } else {
                                    // Si pas auto-rotate, utiliser le layout selon l'orientation actuelle
                                    val selected = if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
                                    android.util.Log.d("ComposeEmulator", "Manual mode: isLandscape=$isLandscape, selected layout='$selected'")
                                    selected
                                }
                            }
                            
                            // Trouver le layout (avec fallback si le nom exact n'existe pas)
                            val layoutName = overlayConfig?.layouts?.get(requestedLayoutName)?.let { 
                                android.util.Log.d("ComposeEmulator", "Using requested layout: '$requestedLayoutName'")
                                requestedLayoutName 
                            }
                                ?: run {
                                    // Fallback : chercher le premier layout correspondant à l'orientation
                                    val orientation = if (isLandscape) "landscape" else "portrait"
                                    val fallback = overlayConfig?.layouts?.keys?.firstOrNull { it.contains(orientation) }
                                    android.util.Log.w("ComposeEmulator", "Layout '$requestedLayoutName' not found, using fallback: '$fallback'")
                                    fallback
                                }
                            
                            if (layoutName != null) {
                                overlayConfig?.layouts?.get(layoutName)?.let { overlayLayout ->
                                // Lire le mode debug depuis les préférences
                                val showDebug = remember { prefs.getBoolean("overlay_debug_mode", false) }
                                val debugModeState = remember { mutableStateOf(showDebug) }
                                
                                // CRITIQUE: Garder une référence forte au listener pour éviter le garbage collection
                                val debugListener = remember {
                                    android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                        if (key == "overlay_debug_mode") {
                                            debugModeState.value = prefs.getBoolean("overlay_debug_mode", false)
                                            android.util.Log.d("ComposeEmulator", "Debug mode changed: ${debugModeState.value}")
                                        }
                                    }
                                }
                                
                                // Observer les changements de préférence
                                DisposableEffect(Unit) {
                                    prefs.registerOnSharedPreferenceChangeListener(debugListener)
                                    onDispose {
                                        prefs.unregisterOnSharedPreferenceChangeListener(debugListener)
                                    }
                                }
                                
                                // Charger advanced settings
                                val advancedSettings = remember(console) {
                                    com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console)
                                }
                                
                                // key() force le recompose quand layoutName OU orientation change
                                // Afficher seulement si overlaysVisible est true
                                if (overlaysVisible.value) {
                                    androidx.compose.runtime.key(layoutName, isLandscape) {
                                        com.retroplay.overlay.renderer.RetroArchOverlayScreen(
                                        layout = overlayLayout,
                                        overlayName = overlayPreference.overlayName,
                                        assetManager = assetManager,
                                        showDebug = debugModeState.value,
                                        swapAnalogSticks = overlayPreference.swapAnalogSticks,
                                        invertAnalogY = overlayPreference.invertAnalogY,
                                        overlayScale = overlayPreference.scale,
                                        overlayXOffset = overlayPreference.xOffset,
                                        overlayYOffset = overlayPreference.yOffset,
                                        overlayXSeparation = overlayPreference.xSeparation,
                                        overlayYSeparation = overlayPreference.ySeparation,
                                        overlayOpacity = advancedSettings.opacity,
                                        dpadDiagonalSensitivity = advancedSettings.dpadDiagonalSensitivity,
                                        abxyDiagonalSensitivity = advancedSettings.abxyDiagonalSensitivity,
                                        onButtonPress = { action ->
                                            val keyCodes = com.retroplay.overlay.models.RetroArchButtonMapping.parseAction(action)
                                            if (keyCodes.isNotEmpty()) {
                                                Log.d("ComposeEmulator", "Buttons pressed: $action -> $keyCodes")
                                                keyCodes.forEach { keyCode ->
                                                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
                                                }
                                            }
                                        },
                                        onButtonRelease = { action ->
                                            val keyCodes = com.retroplay.overlay.models.RetroArchButtonMapping.parseAction(action)
                                            if (keyCodes.isNotEmpty()) {
                                                Log.d("ComposeEmulator", "Buttons released: $action -> $keyCodes")
                                                keyCodes.forEach { keyCode ->
                                                    retroView.sendKeyEvent(android.view.KeyEvent.ACTION_UP, keyCode, 0)
                                                }
                                            }
                                        },
                                        onLayoutSwitch = { requestedLayout ->
                                            // Mapper le layout demandé à l'orientation physique du device
                                            val mappedLayout = mapLayoutToDeviceOrientation(
                                                requestedLayout = requestedLayout,
                                                isLandscape = isLandscape,
                                                availableLayouts = overlayConfig.layouts.keys
                                            )
                                            currentRetroArchLayout = mappedLayout
                                            Log.i("ComposeEmulator", "RetroArch layout switch: $layoutName -> requested='$requestedLayout' mapped='$mappedLayout' (device=${if (isLandscape) "landscape" else "portrait"})")
                                        },
                                        onMenuToggle = {
                                            Log.i("ComposeEmulator", "Menu toggle from RetroArch overlay")
                                            showMainMenu.value = true
                                        },
                                        onAnalogMove = { action, x, y ->
                                            // Envoyer les valeurs analog à l'émulateur
                                            val source = when (action) {
                                                "analog_left" -> com.swordfish.libretrodroid.GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                                                "analog_right" -> com.swordfish.libretrodroid.GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                                                else -> com.swordfish.libretrodroid.GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                                            }
                                            // Envoyer directement sans inversion (comme Lemuroid)
                                            Log.d("AnalogInput", "sendMotionEvent: source=$source, x=$x, y=$y")
                                            retroView.sendMotionEvent(source, x, y)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                                }
                            } else {
                                // Layout non trouvé - Afficher message d'erreur
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            "Layout '$requestedLayoutName' not found in overlay",
                                            color = Color(0xFFFF5722),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Available layouts: ${overlayConfig?.layouts?.keys?.joinToString()}",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        androidx.compose.material3.Button(
                                            onClick = { showMainMenu.value = true },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF9800)
                                            )
                                        ) {
                                            Text("Open Settings", color = Color.Black)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Aucun overlay chargé - Afficher message d'erreur
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        "No RetroArch overlay loaded",
                                        color = Color(0xFFFF5722),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        "Please select an overlay in GamePad Settings\nor switch back to Default mode",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    androidx.compose.material3.Button(
                                        onClick = { showMainMenu.value = true },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF9800)
                                        )
                                    ) {
                                        Text("Open Settings", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Mode Lemuroid : Layout gauche/droite standard
                    PadKit(
                        onInputEvents = { event ->
                            handlePadKitEvent(event, retroView, showMainMenu)
                        }
                    ) {
                        ConstraintLayout(
                            modifier = Modifier.fillMaxSize(),
                            constraintSet = constraintSet
                        ) {
                            // Emulator View
                            AndroidView(
                                factory = { retroView },
                                modifier = Modifier.layoutId("gameView")
                            )
                            
                            // GamePads (affichés seulement si overlaysVisible est true)
                            if (overlaysVisible.value) {
                                // Left GamePad (dynamique selon console et variante)
                                layout.left(this@PadKit, Modifier.layoutId("leftPad"), settings)
                                
                                // Right GamePad (dynamique selon console et variante)
                                layout.right(this@PadKit, Modifier.layoutId("rightPad"), settings)
                            }
                        }
                    }
                }
                
                // États locaux pour les sous-menus
                var showSaveSlots by remember { mutableStateOf(false) }
                var showLoadSlots by remember { mutableStateOf(false) }
                var showCheatCodes by remember { mutableStateOf(false) }
                var showCoreSelector by remember { mutableStateOf(false) }
                var showRestartDialog by remember { mutableStateOf(false) }
                var selectedCoreForRestart by remember { mutableStateOf<String?>(null) }
                
                // Capturer le context Activity pour l'utiliser dans les lambdas
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? android.app.Activity
                
                // Quick Menu (Menu Rapide - Back button)
                if (showQuickMenu.value) {
                    QuickMenuDialog(
                        onDismiss = { 
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                        },
                        onHideOverlay = {
                            overlaysVisible.value = !overlaysVisible.value
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                        },
                        onSettings = {
                            showQuickMenu.value = false
                            showMainMenu.value = true
                        },
                        onSaveState = { slot ->
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                            onSaveState(slot)
                        },
                        onLoadState = { slot ->
                            closeQuickMenuWithCooldown()  // Fermer avec cooldown
                            onLoadState(slot)
                        },
                        onQuit = {
                            showQuickMenu.value = false
                            retroView.onPause()
                            onFinishActivity()
                        },
                        overlaysVisible = overlaysVisible.value
                    )
                }
                
                // Main Menu (Save/Load/Settings/Cheats)
                if (showMainMenu.value) {
                    MainMenuDialog(
                        gameName = gameName,
                        console = console,
                        prefs = prefs,
                        onDismiss = { showMainMenu.value = false },
                        onSaveGame = { 
                            showMainMenu.value = false
                            showSaveSlots = true
                        },
                        onLoadGame = { 
                            showMainMenu.value = false
                            showLoadSlots = true
                        },
                        onGamePadSettings = {
                            showMainMenu.value = false
                            showGamePadSettings.value = true
                        },
                        onCheatCodes = {
                            showMainMenu.value = false
                            showCheatCodes = true
                        },
                        onChangeCore = {
                            showMainMenu.value = false
                            showCoreSelector = true
                        },
                        onDipSwitches = {
                            showMainMenu.value = false
                            showDipSwitchDialog.value = true
                        },
                        onCoreOptions = {
                            showMainMenu.value = false
                            showCoreOptionsDialog.value = true
                        },
                        hasDipSwitches = dipSwitches.isNotEmpty(),
                        hasCoreOptions = coreOptions.isNotEmpty()
                    )
                }
                
                // Save Slots Dialog
                if (showSaveSlots) {
                    SlotSelectionDialog(
                        title = "Save Game",
                        console = console,
                        gameName = gameName,
                        onDismiss = { showSaveSlots = false },
                        onSlotSelected = { slot ->
                            showSaveSlots = false
                            onSaveState(slot)
                        }
                    )
                }
                
                // Load Slots Dialog
                if (showLoadSlots) {
                    SlotSelectionDialog(
                        title = "Load Game",
                        console = console,
                        gameName = gameName,
                        onDismiss = { showLoadSlots = false },
                        onSlotSelected = { slot ->
                            showLoadSlots = false
                            onLoadState(slot)
                        }
                    )
                }
                
                // GamePad Settings Dialog avec LIVE PREVIEW et PERSISTANCE
                if (showGamePadSettings.value) {
                    GamePadSettingsDialog(
                        console = console,
                        currentSettings = settings,
                        currentVariant = layoutVariant,
                        onDismiss = { showGamePadSettings.value = false },
                        onApply = { newSettings ->
                            // Appliquer instantanément (live preview)
                            settings = newSettings
                            // Sauvegarder dans SharedPreferences
                            onSettingsChanged(newSettings)
                        },
                        onVariantChange = { newVariant ->
                            // Changer de variante et sauvegarder
                            layoutVariant = newVariant
                            onVariantChanged(newVariant)
                        },
                        context = retroView.context,
                        prefs = prefs
                    )
                }
                
                // Cheat Codes Dialog
                if (showCheatCodes) {
                    val cheatManager = remember { com.retroplay.cheat.CheatManager(retroView.context) }
                    
                    // RECHARGER les codes à chaque ouverture pour avoir l'état actuel
                    // IMPORTANT: Passer romPath pour la recherche dans sous-répertoires
                    var cheats by remember(showCheatCodes) { 
                        mutableStateOf(cheatManager.loadCheatsForGame(console, gameName, romPath)) 
                    }
                    
                    var showAddCheatDialog by remember { mutableStateOf(false) }
                    
                    com.retroplay.cheat.CheatSelectionDialog(
                        gameName = gameName,
                        console = console,
                        cheats = cheats,
                        onDismiss = { showCheatCodes = false },
                        onCheatsChanged = { updatedCheats ->
                            cheats = updatedCheats
                            // Sauvegarder les modifications
                            cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
                            // Appliquer immédiatement au core
                            cheatApplier.applyCheatsList(updatedCheats)
                            Log.i("NativeComposeEmulator", "[$console] Applied ${updatedCheats.count { it.enabled }} active cheat(s)")
                        },
                        onAddCustomCheat = {
                            showAddCheatDialog = true
                        },
                        onDeleteCheat = { deletedCheat ->
                            // Supprimer du fichier .cht custom
                            cheatManager.deleteCustomCheat(console, gameName, deletedCheat)
                            // Recharger tous les codes
                            cheats = cheatManager.loadCheatsForGame(console, gameName)
                        }
                    )
                    
                    // Dialog pour ajouter un code personnalisé
                    if (showAddCheatDialog) {
                        com.retroplay.cheat.AddCustomCheatDialog(
                            console = console,
                            onDismiss = { showAddCheatDialog = false },
                            onAdd = { description, code, type ->
                                val newCheat = cheatManager.createCustomCheat(
                                    console = console,
                                    gameName = gameName,
                                    description = description,
                                    code = code,
                                    type = type
                                )
                                // Ajouter au fichier .cht custom
                                cheatManager.addCustomCheatToFile(console, gameName, newCheat)
                                // Recharger tous les codes pour inclure le nouveau
                                cheats = cheatManager.loadCheatsForGame(console, gameName)
                            }
                        )
                    }
                }
                
                // Core Selector Dialog
                if (showCoreSelector) {
                    CoreSelectorDialog(
                        console = console,
                        currentGamePath = romPath,
                        onCoreSelected = { selectedCore ->
                            showCoreSelector = false
                            // Save override and show restart dialog
                            CoreSelector.setCoreOverride(
                                romPath,
                                selectedCore.coreId,
                                "User selected from Main Menu: ${selectedCore.displayName}"
                            )
                            selectedCoreForRestart = selectedCore.displayName
                            showRestartDialog = true
                        },
                        onResetToDefault = {
                            showCoreSelector = false
                            // Remove override and show restart dialog
                            CoreSelector.removeCoreOverride(romPath)
                            selectedCoreForRestart = null
                            showRestartDialog = true
                        },
                        onDismiss = {
                            showCoreSelector = false
                        }
                    )
                }
                
                // Restart Confirmation Dialog
                if (showRestartDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showRestartDialog = false },
                        title = {
                            Text(
                                text = "Core Changed",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = if (selectedCoreForRestart != null) {
                                    "Core changed to $selectedCoreForRestart.\n\nRelaunch the game from the menu to apply."
                                } else {
                                    "Core reset to default.\n\nRelaunch the game from the menu to apply."
                                },
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showRestartDialog = false
                                    // Fermer le jeu - l'utilisateur relancera depuis GameDetailsActivity
                                    onFinishActivity()
                                }
                            ) {
                                Text("OK", color = Color(0xFF4CAF50))
                            }
                        },
                        containerColor = Color(0xFF2C2C2C),
                        tonalElevation = 8.dp
                    )
                }
                
                // Box Zapper transparent par-dessus l'overlay RetroArch (zone de jeu uniquement)
                // DOIT avoir exactement la même taille et le même offset que l'AndroidView
                if (isZapperGame && !showMainMenu.value && !showGamePadSettings.value && !showQuickMenu.value) {
                    // Calculer le même offset vertical que l'AndroidView
                    val zapperVerticalOffsetDp = if (isLandscape) {
                        0.dp  // Landscape : pas d'offset
                    } else {
                        (-configuration.screenHeightDp * 0.20f).dp  // Portrait : 20% vers le haut
                    }
                    
                    // MODE DEBUG: Afficher la zone Zapper en rouge semi-transparent
                    val showDebugZapperZone = true  // Mettre à false pour masquer
                    
                    // Adapter la largeur de la Box selon l'orientation
                    // Landscape : 30% (contrôles sur les côtés) | Portrait : 100% (contrôles en bas)
                    val boxWidthFraction = if (isLandscape) {
                        0.30f  // Landscape : zone centrale seulement
                    } else {
                        1.0f   // Portrait : toute la largeur (contrôles sous l'écran)
                    }
                    
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth(boxWidthFraction)  // 30% de la largeur au centre
                            .fillMaxHeight()  // Même hauteur que l'AndroidView
                            .offset(y = zapperVerticalOffsetDp)  // MÊME offset que l'AndroidView
                            .align(Alignment.Center)  // Centrer horizontalement
                            .background(
                                if (showDebugZapperZone) 
                                    Color.Red.copy(alpha = 0.3f)  // Rouge semi-transparent pour debug
                                else 
                                    Color.Transparent
                            )
                            .pointerInteropFilter { event ->
                                // Laisser passer les touches vers le gamepad si hors zone centrale
                                val handled = onZapperTouch(event)
                                android.util.Log.d("ZapperBox", "Touch at (${event.x}, ${event.y}) handled=$handled")
                                handled
                            }
                    )
                }
            }
        }
    }
}

// Build ConstraintSet pour mode Portrait
private fun buildPortraitConstraints(): ConstraintSet {
    return ConstraintSet {
        val gameView = createRefFor("gameView")
        val leftPad = createRefFor("leftPad")
        val rightPad = createRefFor("rightPad")
        
        val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)
        
        constrain(gameView) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(leftPad.top)  // gameView s'arrête au-dessus des gamepads
        }
        
        constrain(gamePadChain) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
        
        constrain(leftPad) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom)
        }
        
        constrain(rightPad) {
            width = Dimension.fillToConstraints
            bottom.linkTo(parent.bottom)
        }
    }
}

// Build ConstraintSet pour mode Paysage
private fun buildLandscapeConstraints(): ConstraintSet {
    return ConstraintSet {
        val gameView = createRefFor("gameView")
        val leftPad = createRefFor("leftPad")
        val rightPad = createRefFor("rightPad")
        
        val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)
        
        constrain(gameView) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
            bottom.linkTo(parent.bottom)  // Fullscreen
        }
        
        constrain(gamePadChain) {
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
        
        constrain(leftPad) {
            width = Dimension.wrapContent
            height = Dimension.matchParent
            bottom.linkTo(parent.bottom)
        }
        
        constrain(rightPad) {
            width = Dimension.wrapContent
            height = Dimension.matchParent
            bottom.linkTo(parent.bottom)
        }
    }
}

// Main Menu Dialog (Save/Load/Settings/Cheats/Cache)
@Composable
private fun MainMenuDialog(
    gameName: String,
    console: String,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onGamePadSettings: () -> Unit,
    onCheatCodes: () -> Unit,
    onChangeCore: () -> Unit,
    onDipSwitches: () -> Unit,
    onCoreOptions: () -> Unit,
    hasDipSwitches: Boolean,
    hasCoreOptions: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cacheState by remember { mutableStateOf(prefs.getBoolean("cache_enabled_$console", false)) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Titre
                    Text(
                        gameName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Save Game
                    TextButton(
                        onClick = onSaveGame,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Game", color = Color.White)
                    }
                    
                    // Load Game
                    TextButton(
                        onClick = onLoadGame,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Game", color = Color.White)
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Cheat Codes
                    TextButton(
                        onClick = onCheatCodes,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cheat Codes", color = Color(0xFF4CAF50))
                    }
                    
                    // GamePad Settings
                    TextButton(
                        onClick = onGamePadSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GamePad Settings", color = Color.White)
                    }
                    
                    // DIP Switches (arcade only, if available)
                    if (hasDipSwitches) {
                        TextButton(
                            onClick = onDipSwitches,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DIP Switches", color = Color(0xFFFFA726))
                        }
                    }
                    
                    // Core Options (if available)
                    if (hasCoreOptions) {
                        TextButton(
                            onClick = onCoreOptions,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Core Options", color = Color(0xFF64B5F6))
                        }
                    }
                    
                    // Change Core
                    TextButton(
                        onClick = onChangeCore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Core & Restart", color = Color(0xFFE91E63))
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // ZIP Cache Toggle (pour toutes les consoles)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ZIP Cache Extraction", color = Color.White)
                            Text(
                                "Enable if ROM doesn't load",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = cacheState,
                            onCheckedChange = { enabled ->
                                cacheState = enabled
                                prefs.edit().putBoolean("cache_enabled_$console", enabled).apply()
                                android.util.Log.i("MainMenu", "[$console] Cache enabled: $enabled")
                                android.widget.Toast.makeText(context,
                                    "[$console] Cache " + (if (enabled) "enabled" else "disabled"), 
                                    android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Close
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.Gray)
                    }
                }
            }
        }
    }
}

// Slot Selection Dialog (Save/Load avec 5 slots par console)
@Composable
private fun SlotSelectionDialog(
    title: String,
    console: String,
    gameName: String,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)  // Pleine largeur (95% pour petites marges)
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Titre avec console
                    Text(
                        "$title - ${console.uppercase()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    
                    Text(
                        gameName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // 5 slots avec infos détaillées
                    for (slot in 1..5) {
                        val saveFile = java.io.File("/storage/emulated/0/GameLibrary-Data/saves/$console/slot$slot/${gameName}.state")
                        val isOccupied = saveFile.exists()
                        
                        TextButton(
                            onClick = { onSlotSelected(slot) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Ligne 1 : Slot + Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Slot $slot",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    
                                    if (isOccupied) {
                                        Text(
                                            "[Occupied]",
                                            color = Color(0xFF4CAF50),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        Text(
                                            "[Empty]",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                
                                // Ligne 2 : Infos détaillées (si occupé)
                                if (isOccupied) {
                                    val lastModified = saveFile.lastModified()
                                    val sizeKB = saveFile.length() / 1024
                                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                    val dateStr = dateFormat.format(java.util.Date(lastModified))
                                    
                                    Text(
                                        "$dateStr - ${sizeKB}KB",
                                        color = Color(0xFFAAAAAA),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    
                    androidx.compose.material3.HorizontalDivider(color = Color.Gray)
                    
                    // Cancel
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun GamePadSettingsDialog(
    console: String,
    currentSettings: TouchControllerSettingsManager.Settings,
    currentVariant: GamePadLayoutManager.LayoutVariant,
    onDismiss: () -> Unit,
    onApply: (TouchControllerSettingsManager.Settings) -> Unit,
    onVariantChange: (GamePadLayoutManager.LayoutVariant) -> Unit,
    context: Context,
    prefs: SharedPreferences
) {
    var scale by remember { mutableFloatStateOf(currentSettings.scale) }
    var rotation by remember { mutableFloatStateOf(currentSettings.rotation) }
    var marginX by remember { mutableFloatStateOf(currentSettings.marginX) }
    var marginY by remember { mutableFloatStateOf(currentSettings.marginY) }
    var selectedVariant by remember { mutableStateOf(currentVariant) }
    
    // État pour l'overlay RetroArch
    val assetManager = remember { com.retroplay.overlay.assets.OverlayAssetManager(context) }
    val availableOverlays = remember { assetManager.getCompatibleOverlays(console) }
    
    // Charger préférence actuelle
    val currentOverlayPref = remember { com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console) }
    var selectedOverlay by remember { mutableStateOf(currentOverlayPref?.overlayName ?: if (availableOverlays.isNotEmpty()) availableOverlays[0] else "") }
    var selectedLandscapeLayout by remember { mutableStateOf(currentOverlayPref?.landscapeLayout ?: "landscape-A") }
    var selectedPortraitLayout by remember { mutableStateOf(currentOverlayPref?.portraitLayout ?: "portrait-A") }
    var autoRotate by remember { mutableStateOf(currentOverlayPref?.autoRotate ?: true) }
    
    // État pour détecter si un slider est en train d'être bougé
    var isAdjusting by remember { mutableStateOf(false) }
    
    // Liste des variantes disponibles pour cette console
    val availableVariants = GamePadLayoutManager.getAvailableVariants(console)
    
    // Charger layouts disponibles pour l'overlay sélectionné (si RetroArch)
    val availableLayouts = remember(selectedOverlay, console) {
        if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH && selectedOverlay.isNotEmpty()) {
            val layouts = assetManager.getAvailableLayouts(selectedOverlay, console)
            android.util.Log.d("GamePadSettings", "Loaded ${layouts.size} layouts for '$selectedOverlay': ${layouts.joinToString()}")
            layouts
        } else {
            emptyList()
        }
    }
    
    // Sauvegarder la préférence overlay quand modifiée
    LaunchedEffect(selectedVariant, selectedOverlay, selectedLandscapeLayout, selectedPortraitLayout, autoRotate) {
        if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH && selectedOverlay.isNotEmpty()) {
            val pref = com.retroplay.overlay.models.OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate
            )
            com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, pref)
            android.util.Log.i("GamePadSettings", "✅ SAVED overlay pref for $console: overlay='$selectedOverlay' landscape='$selectedLandscapeLayout' portrait='$selectedPortraitLayout' autoRotate=$autoRotate")
        } else if (selectedVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) {
            // Désactiver RetroArch si autre variante choisie
            com.retroplay.overlay.models.OverlayPreferenceManager.disable(prefs, console)
        }
    }
    
    // Alpha du fond : plus transparent quand on ajuste
    val dialogAlpha = if (isAdjusting) 0x33000000 else 0x99000000
    
    // LIVE PREVIEW : Appliquer les changements instantanément
    LaunchedEffect(scale, rotation, marginX, marginY) {
        onApply(
            TouchControllerSettingsManager.Settings(
                scale = scale,
                rotation = rotation,
                marginX = marginX,
                marginY = marginY
            )
        )
    }
    
    // Appliquer changement de variante
    LaunchedEffect(selectedVariant) {
        if (selectedVariant != currentVariant) {
            onVariantChange(selectedVariant)
        }
    }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter  // Positionner en haut
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.8f)  // Limiter hauteur à 80% de l'écran
                    .padding(top = 40.dp),
                colors = CardDefaults.cardColors(containerColor = Color(dialogAlpha))
            ) {
            // Ajouter ScrollView pour le contenu qui peut être long
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Titre
                Text(
                    "GamePad Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                
                // Sélecteur de variante (si plusieurs disponibles)
                if (availableVariants.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("Gamepad Mode", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableVariants.forEach { (variant, label) ->
                            val isSelected = selectedVariant == variant
                            TextButton(
                                onClick = { selectedVariant = variant },
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) Color(0xFF4CAF50) else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // Configuration RetroArch Overlay (affiché seulement si variante RETROARCH sélectionnée)
                if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("RetroArch Overlay", color = Color(0xFFFF9800), style = MaterialTheme.typography.titleMedium)
                    
                    // Sélection de l'overlay package
                    if (availableOverlays.isNotEmpty()) {
                        Text("Overlay Package", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Column {
                            availableOverlays.forEach { overlayName ->
                                val isSelected = selectedOverlay == overlayName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOverlay = overlayName }
                                        .background(if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color.Transparent)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedOverlay = overlayName }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        overlayName,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        // Sélection des layouts (si overlay chargé)
                        if (selectedOverlay.isNotEmpty() && availableLayouts.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            
                            // Filtrer par orientation
                            val landscapeLayouts = availableLayouts.filter { it.contains("landscape") }
                            val portraitLayouts = availableLayouts.filter { it.contains("portrait") }
                            
                            if (landscapeLayouts.isNotEmpty()) {
                                Text("Landscape Layout", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                // Afficher tous les layouts en plusieurs lignes si nécessaire
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    landscapeLayouts.forEach { layoutName ->
                                        val isSelected = selectedLandscapeLayout == layoutName
                                        // Extraire label lisible (ex: "landscape-left-analog" -> "Left Analog")
                                        val displayName = when {
                                            layoutName.contains("both-analog") -> "Both Analog"
                                            layoutName.contains("left-analog") && layoutName.contains("menu") -> "L.Analog+Menu"
                                            layoutName.contains("left-analog") -> "Left Analog"
                                            layoutName.contains("right-analog") -> "Right Analog"
                                            layoutName.contains("analog") && layoutName.contains("menu") -> "Analog+Menu"
                                            layoutName.contains("analog") -> "Analog"
                                            layoutName.contains("menu") -> "Menu"
                                            layoutName.endsWith("-B") -> "B"
                                            else -> "Digital"
                                        }
                                        TextButton(
                                            onClick = { 
                                                android.util.Log.i("GamePadSettings", "Landscape layout changed: $selectedLandscapeLayout -> $layoutName")
                                                selectedLandscapeLayout = layoutName
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                                            ),
                                            modifier = Modifier.padding(0.dp)
                                        ) {
                                            Text(
                                                displayName,
                                                color = if (isSelected) Color.Black else Color.Gray,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            
                            if (portraitLayouts.isNotEmpty()) {
                                Text("Portrait Layout", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                // Afficher tous les layouts en plusieurs lignes si nécessaire
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    portraitLayouts.forEach { layoutName ->
                                        val isSelected = selectedPortraitLayout == layoutName
                                        // Extraire label lisible
                                        val displayName = when {
                                            layoutName.contains("analog") && layoutName.contains("menu") -> "Analog+Menu"
                                            layoutName.contains("analog") -> "Analog"
                                            layoutName.contains("menu") -> "Menu"
                                            else -> "Digital"
                                        }
                                        TextButton(
                                            onClick = { 
                                                android.util.Log.i("GamePadSettings", "Portrait layout changed: $selectedPortraitLayout -> $layoutName")
                                                selectedPortraitLayout = layoutName
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                                            ),
                                            modifier = Modifier.padding(0.dp)
                                        ) {
                                            Text(
                                                displayName,
                                                color = if (isSelected) Color.Black else Color.Gray,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Auto-rotate option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = autoRotate,
                                    onCheckedChange = { autoRotate = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFFF9800),
                                        uncheckedColor = Color.Gray
                                    )
                                )
                                Text(
                                    "Auto-switch on rotation",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            // DEBUG MODE - Afficher les hitboxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var debugMode by remember { 
                                    mutableStateOf(prefs.getBoolean("overlay_debug_mode", false)) 
                                }
                                
                                androidx.compose.material3.Switch(
                                    checked = debugMode,
                                    onCheckedChange = { 
                                        debugMode = it
                                        prefs.edit().putBoolean("overlay_debug_mode", it).apply()
                                    },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFF5722),
                                        checkedTrackColor = Color(0xFFFF5722).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.5f)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "DEBUG: Show hitboxes",
                                    color = if (debugMode) Color(0xFFFF5722) else Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Text(
                            "No compatible overlays found for $console",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                }
                
                // Sliders Lemuroid (affichés SEULEMENT si mode Default ou Compact)
                if (selectedVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    Text("Lemuroid Gamepad Adjustments", color = Color(0xFF2196F3), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    
                    // Scale (0.75x - 1.5x)
                    Text("Scale: ${String.format("%.2f", scale * 0.75f + 0.75f)}x", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = scale,
                        onValueChange = { 
                            scale = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Rotation (0° - 45°)
                    Text("Rotation: ${String.format("%.0f", rotation * 45f)}°", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = rotation,
                        onValueChange = { 
                            rotation = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Margin X (0dp - 96dp)
                    Text("Margin X: ${String.format("%.0f", marginX * 96f)}dp", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = marginX,
                        onValueChange = { 
                            marginX = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Margin Y (0dp - 96dp)
                    Text("Margin Y: ${String.format("%.0f", marginY * 96f)}dp", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = marginY,
                        onValueChange = { 
                            marginY = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(8.dp))
                }
                
                // Boutons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Bouton RESET TO DEFAULT (à gauche)
                    androidx.compose.material3.Button(
                        onClick = {
                            // Forcer le retour au mode DEFAULT
                            selectedVariant = GamePadLayoutManager.LayoutVariant.DEFAULT
                            // Désactiver RetroArch overlay
                            com.retroplay.overlay.models.OverlayPreferenceManager.disable(prefs, console)
                            // Appliquer le changement
                            onVariantChange(GamePadLayoutManager.LayoutVariant.DEFAULT)
                            android.util.Log.i("GamePadSettings", "Force reset to DEFAULT mode for $console")
                            onDismiss()
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Text("RESET TO DEFAULT", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    
                    // Bouton Done (à droite)
                    androidx.compose.material3.Button(
                        onClick = onDismiss,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("DONE", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            }
        }
    }
}

// Quick Menu Dialog (Menu Rapide - Bouton Back)
@Composable
private fun QuickMenuDialog(
    onDismiss: () -> Unit,
    onHideOverlay: () -> Unit,
    onSettings: () -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onQuit: () -> Unit,
    overlaysVisible: Boolean
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Titre
                Text(
                    "MENU RAPIDE",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                
                Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                
                // Bouton Resume (si en pause)
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("RESUME", color = Color.White)
                }
                
                // Bouton Hide/Show Overlay
                androidx.compose.material3.Button(
                    onClick = onHideOverlay,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text(
                        if (overlaysVisible) "HIDE OVERLAY" else "SHOW OVERLAY", 
                        color = Color.White
                    )
                }
                
                // Bouton Save State (Quick Save Slot 1)
                androidx.compose.material3.Button(
                    onClick = { onSaveState(1) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("SAVE STATE (Slot 1)", color = Color.White)
                }
                
                // Bouton Load State (Quick Load Slot 1)
                androidx.compose.material3.Button(
                    onClick = { onLoadState(1) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Text("LOAD STATE (Slot 1)", color = Color.White)
                }
                
                // Bouton Settings (ouvrir le menu complet)
                androidx.compose.material3.Button(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF607D8B)
                    )
                ) {
                    Text("SETTINGS", color = Color.White)
                }
                
                Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                
                // Bouton Quit
                androidx.compose.material3.Button(
                    onClick = onQuit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Text("QUIT GAME", color = Color.White)
                }
            }
        }
    }
}

/**
 * Mappe un layout demandé vers un layout compatible avec l'orientation physique du device
 * 
 * RÈGLE SIMPLE : 
 * - Si le layout existe → L'utiliser tel quel (pas de mapping)
 * - Si le layout n'existe PAS → Chercher un équivalent dans l'orientation du device
 * 
 * @param requestedLayout Layout demandé par le bouton système
 * @param isLandscape True si le device est en landscape, false si portrait
 * @param availableLayouts Ensemble des layouts disponibles dans l'overlay actuel (optionnel)
 * @return Layout mappé compatible avec l'orientation physique
 */
private fun mapLayoutToDeviceOrientation(
    requestedLayout: String,
    isLandscape: Boolean,
    availableLayouts: Set<String>? = null
): String {
    // ÉTAPE 1 : Si le layout demandé existe, l'utiliser tel quel
    if (availableLayouts?.contains(requestedLayout) == true) {
        return requestedLayout
    }
    
    // ÉTAPE 2 : Le layout n'existe pas → Trouver un équivalent dans l'orientation du device
    val deviceOrientation = if (isLandscape) "landscape" else "portrait"
    
    // Déterminer le type de layout demandé
    val isAnalog = requestedLayout.contains("analog", ignoreCase = true)
    val isHidden = requestedLayout.contains("hidden", ignoreCase = true)
    
    // Chercher un équivalent compatible
    if (availableLayouts == null) {
        return requestedLayout  // Pas de layouts disponibles : retourner tel quel
    }
    
    return when {
        isHidden -> {
            // Layout hidden : chercher n'importe quel hidden
            availableLayouts.find { it.contains("hidden", ignoreCase = true) }
                ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
        isAnalog -> {
            // Layout analog : chercher un layout analog dans l'orientation du device
            availableLayouts.find { 
                it.contains(deviceOrientation, ignoreCase = true) && it.contains("analog", ignoreCase = true)
            } ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
        else -> {
            // Layout de base : chercher le layout de base dans l'orientation du device
            availableLayouts.find { 
                it == deviceOrientation || it == "$deviceOrientation-A"
            } ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
    }
}

/**
 * Trouve un layout de fallback pour une orientation donnée
 */
private fun findFallbackLayout(orientation: String, availableLayouts: Set<String>): String {
    // Chercher le premier layout correspondant à l'orientation
    return availableLayouts.firstOrNull { it.contains(orientation, ignoreCase = true) }
        ?: availableLayouts.firstOrNull() // Dernier recours : premier layout disponible
        ?: "landscape" // Ultra-fallback
}

@Composable
private fun CoreErrorDialog(
    coreName: String,
    gameName: String,
    onChangeCore: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Pas de dismiss en cliquant à l'extérieur */ },
        title = {
            Text(
                text = "Core Loading Failed",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "$coreName could not load:",
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = gameName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bouton "Change Core"
                TextButton(
                    onClick = onChangeCore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Core", color = Color(0xFFE91E63), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                // Bouton "Retry"
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry", color = Color(0xFF4CAF50), fontSize = 16.sp)
                }
                
                // Bouton "Cancel"
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color(0xFF9E9E9E), fontSize = 16.sp)
                }
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
