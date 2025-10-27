package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.ChainStyle
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.layouts.*
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKit
import gg.padkit.inputevents.InputEvent
import gg.padkit.ids.Id
import androidx.compose.ui.util.lerp
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
    }
    
    private lateinit var retroView: GLRetroView
    private lateinit var console: String
    private lateinit var romPath: String
    private lateinit var gameName: String
    private lateinit var prefs: SharedPreferences
    private lateinit var cheatApplier: com.retroplay.cheat.CheatApplier
    
    // États des menus
    private val showMainMenu = mutableStateOf(false)
    private val showGamePadSettings = mutableStateOf(false)
    
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
        val loadSlot = intent.getIntExtra("loadSlot", 0)  // 0 = nouvelle partie, 1-5 = charger slot
        
        Log.i(TAG, "🟢 NativeComposeEmulator starting: $gameName ($console) from $romPath" + 
                if (loadSlot > 0) " [LOAD SLOT $loadSlot]" else " [NEW GAME]")
        
        // Charger les settings depuis SharedPreferences
        prefs = getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)
        val savedSettings = loadSettings(prefs, console)
        val savedVariant = GamePadLayoutManager.loadVariant(prefs, console)
        
        // Créer GLRetroView avec GLRetroViewData
        val data = com.swordfish.libretrodroid.GLRetroViewData(this).apply {
            coreFilePath = getCorePath(console)
            gameFilePath = romPath
            
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
        }
        
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)
        
        // Initialiser le CheatApplier
        cheatApplier = com.retroplay.cheat.CheatApplier(retroView)
        
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
                initialSettings = savedSettings,
                initialVariant = savedVariant,
                cheatApplier = cheatApplier,
                onSettingsChanged = { newSettings ->
                    saveSettings(prefs, console, newSettings)
                },
                onVariantChanged = { newVariant ->
                    GamePadLayoutManager.saveVariant(prefs, console, newVariant)
                },
                onSaveState = { slot ->
                    saveGameState(slot)
                },
                onLoadState = { slot ->
                    loadGameState(slot)
                }
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
        // Core names (in APK native libs, not on storage)
        // 18+ consoles natives supportees
        
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
        if (relativePath.isNotEmpty()) {
            val overrideManager = CoreOverrideManager.getInstance()
            val overrideCoreId = overrideManager.getCoreOverride(relativePath)
            
            if (overrideCoreId != null) {
                Log.i(TAG, "Using core override for $gameName: $overrideCoreId")
                // Mapper le coreId vers le fichier .so
                val overrideCorePath = when (overrideCoreId.lowercase()) {
                    "mame2010" -> "mame2010_libretro_android.so"
                    "mame2003_plus" -> "mame2003_plus_libretro_android.so"
                    "fbneo" -> "fbneo_libretro_android.so"
                    "fceumm" -> "fceumm_libretro_android.so"
                    "snes9x" -> "snes9x_libretro_android.so"
                    "parallel_n64" -> "parallel_n64_libretro_android.so"
                    "gambatte" -> "gambatte_libretro_android.so"
                    "mgba" -> "libmgba_libretro_android.so"
                    "pcsx_rearmed" -> "pcsx_rearmed_libretro_android.so"
                    "ppsspp" -> "ppsspp_libretro_android.so"
                    "genesis_plus_gx" -> "genesis_plus_gx_libretro_android.so"
                    "picodrive" -> "picodrive_libretro_android.so"
                    else -> null
                }
                
                if (overrideCorePath != null) {
                    Log.i(TAG, "Core override resolved to: $overrideCorePath")
                    return overrideCorePath
                }
            }
        }
        
        // Pas d'override, utiliser la logique par défaut basée sur la console
        // Pour les sous-consoles (ex: fbneo/sega), utiliser le parent (fbneo)
        val consoleKey = if (console.contains("/")) {
            console.substringBefore("/").lowercase()
        } else {
            console.lowercase()
        }
        
        return when (consoleKey) {
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
            "lynx", "atarilynx" -> "mednafen_lynx_libretro_android.so"  // Beetle Lynx (plus stable que Handy)
            
            // Other
            "ngp", "ngc", "neogeopocket" -> "mednafen_ngp_libretro_android.so"
            "ws", "wsc", "wonderswan" -> "mednafen_wswan_libretro_android.so"
            "pce", "turbografx", "pcengine" -> "mednafen_pce_libretro_android.so"
            "arcade" -> "mame2003_plus_libretro_android.so"
            "mame" -> "mame2010_libretro_android.so"
            "fbneo", "neogeo", "cps1", "cps2" -> "fbneo_libretro_android.so"
            
            else -> {
                // Fallback: essayer quand meme avec fceumm, mais afficher un warning
                android.util.Log.w("NativeComposeEmulator", "No native core for console: $console, using fceumm fallback (may not work)")
                "fceumm_libretro_android.so"
            }
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
fun ComposeEmulatorScreen(
    retroView: GLRetroView,
    console: String,
    gameName: String,
    romPath: String,
    prefs: SharedPreferences,
    showMainMenu: MutableState<Boolean>,
    showGamePadSettings: MutableState<Boolean>,
    initialSettings: TouchControllerSettingsManager.Settings,
    initialVariant: GamePadLayoutManager.LayoutVariant,
    cheatApplier: com.retroplay.cheat.CheatApplier,
    onSettingsChanged: (TouchControllerSettingsManager.Settings) -> Unit,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit
) {
    // Settings manager pour les gamepads (state mutable)
    var settings by remember {
        mutableStateOf(initialSettings)
    }
    
    // Variante de layout (state mutable)
    var layoutVariant by remember {
        mutableStateOf(initialVariant)
    }
    
    // État pour le switch de layout RetroArch
    var currentRetroArchLayout by remember { mutableStateOf<String?>(null) }
    
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
            onLayoutSwitch = { newLayoutName ->
                currentRetroArchLayout = newLayoutName
                Log.i("ComposeEmulator", "RetroArch layout switched to: $newLayoutName")
            }
        )
    } else {
        // Utiliser getLayout normal pour Lemuroid
        GamePadLayoutManager.getLayout(console, layoutVariant)
    }
    
    // Détection de l'orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
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
                        
                        // Left GamePad (dynamique selon console et variante)
                        layout.left(this@PadKit, Modifier.layoutId("leftPad"), settings)
                        
                        // Right GamePad (dynamique selon console et variante)
                        layout.right(this@PadKit, Modifier.layoutId("rightPad"), settings)
                    }
                }
                
                // États locaux pour les sous-menus
                var showSaveSlots by remember { mutableStateOf(false) }
                var showLoadSlots by remember { mutableStateOf(false) }
                var showCheatCodes by remember { mutableStateOf(false) }
                
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
                        }
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
fun MainMenuDialog(
    gameName: String,
    console: String,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onGamePadSettings: () -> Unit,
    onCheatCodes: () -> Unit
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
fun SlotSelectionDialog(
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
fun GamePadSettingsDialog(
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
    val availableLayouts = remember(selectedOverlay) {
        if (selectedVariant == GamePadLayoutManager.LayoutVariant.RETROARCH && selectedOverlay.isNotEmpty()) {
            assetManager.getAvailableLayouts(selectedOverlay)
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
                    Text("Layout Variant", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    landscapeLayouts.take(3).forEach { layoutName ->
                                        val isSelected = selectedLandscapeLayout == layoutName
                                        TextButton(
                                            onClick = { selectedLandscapeLayout = layoutName },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                layoutName.substringAfter("-").uppercase(),
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    portraitLayouts.take(3).forEach { layoutName ->
                                        val isSelected = selectedPortraitLayout == layoutName
                                        TextButton(
                                            onClick = { selectedPortraitLayout = layoutName },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = if (isSelected) Color(0xFFFF9800) else Color.Transparent
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                layoutName.substringAfter("-").uppercase(),
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
                
                // Scale (0.75x - 1.5x)
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
                
                // Boutons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        scale = currentSettings.scale
                        rotation = currentSettings.rotation
                        marginX = currentSettings.marginX
                        marginY = currentSettings.marginY
                        onDismiss()
                    }) {
                        Text("Reset")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
            }
        }
    }
}
