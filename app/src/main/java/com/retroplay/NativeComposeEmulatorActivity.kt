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
    private val showQuickMenu = mutableStateOf(false)
    private val overlaysVisible = mutableStateOf(true)
    
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
                    finish()
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
    showQuickMenu: MutableState<Boolean>,
    overlaysVisible: MutableState<Boolean>,
    initialSettings: TouchControllerSettingsManager.Settings,
    initialVariant: GamePadLayoutManager.LayoutVariant,
    cheatApplier: com.retroplay.cheat.CheatApplier,
    onSettingsChanged: (TouchControllerSettingsManager.Settings) -> Unit,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onFinishActivity: () -> Unit
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
                            val overlayConfig = remember(overlayPreference.overlayName) {
                                assetManager.loadOverlayConfig(overlayPreference.overlayName)
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
                                
                                // key() force le recompose quand layoutName OU orientation change
                                // Afficher seulement si overlaysVisible est true
                                if (overlaysVisible.value) {
                                    androidx.compose.runtime.key(layoutName, isLandscape) {
                                        com.retroplay.overlay.renderer.RetroArchOverlayScreen(
                                        layout = overlayLayout,
                                        overlayName = overlayPreference.overlayName,
                                        assetManager = assetManager,
                                        showDebug = debugModeState.value,
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
            val layouts = assetManager.getAvailableLayouts(selectedOverlay)
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
fun QuickMenuDialog(
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
