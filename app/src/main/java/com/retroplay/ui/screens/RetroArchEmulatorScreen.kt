package com.retroplay.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.retroplay.CoreSelector
import com.retroplay.CoreSelectorDialog
import com.retroplay.CoreVariable
import com.retroplay.CrosshairMode
import com.retroplay.GamePadLayoutManager
import com.retroplay.QuickTurboMenu
import com.retroplay.R
import com.retroplay.TurboSettingsDialog
import com.retroplay.ZapperCrosshair
import com.retroplay.input.PadKitHelper

import com.retroplay.overlay.models.OverlayPreferenceManager
import com.retroplay.overlay.models.RetroArchButtonMapping
import com.retroplay.overlay.renderer.RetroArchOverlayScreen
import com.retroplay.overlay.ui.OverlayCfgBrowserDialog
import com.retroplay.rewind.RewindManager
import com.retroplay.ui.QuickActionsBar
import com.retroplay.ui.dialogs.GameInfoDialog
import com.retroplay.ui.dialogs.PerGameConfigDialog
import com.retroplay.ui.dialogs.SmartConfigDialog
import com.retroplay.ui.dialogs.DiskSwapperDialog
import com.retroplay.database.GameInfo

import com.retroplay.ui.dialogs.DipSwitchDialog
import com.retroplay.ui.dialogs.EmulationSettingsDialog

import com.retroplay.ui.dialogs.CoreErrorDialog
import com.retroplay.ui.dialogs.GamePadSettingsDialog
import com.retroplay.ui.dialogs.QuickActionsMenu
import com.retroplay.ui.dialogs.QuickMenuDialog
import com.retroplay.ui.dialogs.SlotSelectionDialog
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKit
import gg.padkit.inputevents.InputEvent
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

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
    showGameInfoDialog: MutableState<Boolean>,
    showCheatsDialog: MutableState<Boolean>,
    showSmartConfigDialog: MutableState<Boolean>,
    showPerGameConfigDialog: MutableState<Boolean>,
    showCfgBrowser: MutableState<Boolean> = mutableStateOf(false),  // File browser custom pour .cfg
    showTurboSettings: MutableState<Boolean> = mutableStateOf(false),
    showQuickTurbo: MutableState<Boolean> = mutableStateOf(false),
    gameInfo: GameInfo? = null,
    cheatFile: File? = null,
    gameCRC: String?,
    configId: String?,
    loadedCheats: List<com.retroplay.cheat.CheatManager.Cheat>,
    overlaysVisible: MutableState<Boolean>,
    initialVariant: GamePadLayoutManager.LayoutVariant,
    cheatApplier: com.retroplay.cheat.CheatApplier,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onDeleteSlot: (Int) -> Unit,  // Delete save slot
    onFinishActivity: () -> Unit,
    onHotkey: (String) -> Unit,  // Callback pour hotkeys
    onHotkeyChange: (String, Boolean) -> Unit = { _, _ -> },
    onLightgunAction: (String) -> Unit = {},  // Callback pour lightgun actions

    showDipSwitchDialog: MutableState<Boolean>,
    showCoreOptionsDialog: MutableState<Boolean>,
    dipSwitches: androidx.compose.runtime.snapshots.SnapshotStateList<CoreVariable>,
    coreOptions: androidx.compose.runtime.snapshots.SnapshotStateList<CoreVariable>,
    isZapperGame: Boolean = false,
    gameViewBounds: MutableState<Rect?>,  // Bounds du GLRetroView
    onZapperTouch: (MotionEvent) -> Boolean = { false },
    onLoadCustomCfg: (() -> Unit)? = null,  // Callback pour file picker
    // Quick Wins
    onToggleFastForward: () -> Unit = {},
    onToggleAudioMute: () -> Unit = {},
    onCycleShader: () -> Unit = {},
    onToggleQuickActionsBar: () -> Unit = {},
    onConfigureZapper: () -> Unit = {},  // TEST: Configure Zapper manuellement
    onToggleCrosshairMode: () -> Unit = {},  // Toggle Crosshair mode (RetroPlay/FCEUmm/Both/None)
    isFastForwardActive: Boolean = false,
    audioMuted: Boolean = false,
    currentShaderName: String = "None",
    quickActionsBarVisible: Boolean = true,
    quickActionsBarAutoHideEnabled: MutableState<Boolean>? = null,
    quickActionsBarAutoHideTimer: MutableState<Long>? = null,
    crosshairMode: CrosshairMode = CrosshairMode.RETROPLAY_ONLY,
    // Disk Swapper & Screenshot
    showDiskSwapperDialog: MutableState<Boolean>,
    availableDisks: Int = 0,
    currentDisk: Int = 0,
    onTakeScreenshot: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    // N64 Extensions Dialog
    showN64ExtensionsDialog: MutableState<Boolean> = mutableStateOf(false),
    n64ExtensionsInfo: MutableState<List<Pair<Int, String>>> = mutableStateOf(emptyList()),
    onRewindPress: () -> Unit = {},
    onRewindRelease: () -> Unit = {},
    rewindManager: RewindManager? = null,
    // Callbacks pour RetroArchSettingsDialog
    onShaderChanged: (String) -> Unit = {},
    onFastForwardRatioChanged: (Float) -> Unit = {},
    onAudioVolumeChanged: (Float) -> Unit = {},
    onAudioMuteChanged: (Boolean) -> Unit = {},
    onVsyncChanged: (Boolean) -> Unit = {},
    onRewindEnabledChanged: (Boolean) -> Unit = {},
    onAspectRatioChanged: (String) -> Unit = {},
    onTurboSettings: () -> Unit = {},
    onSmartConfig: () -> Unit = {},
    onPerGameConfig: () -> Unit = {},
    onDipSwitches: () -> Unit = {},
    onCoreOptions: () -> Unit = {},
    onDiskSwapper: () -> Unit = {}
) {
    // NO Radial/Lemuroid settings needed - RetroArch overlays only!
    
    // État debug mode (accessible partout dans le Composable)
    val showDebug = remember { prefs.getBoolean("overlay_debug_mode", false) }
    val debugModeState = remember { mutableStateOf(showDebug) }
    
    // Dialog States (Managed locally by Screen)
    val showGameInfoDialog = remember { mutableStateOf(false) }
    val showSmartConfigDialog = remember { mutableStateOf(false) }
    val showPerGameConfigDialog = remember { mutableStateOf(false) }
    
    val rewindManagerState = rewindManager
    val rewindEnabled: Boolean
    val rewindSupported: Boolean
    val rewindActive: Boolean
    val rewindBufferCount: Int
    val rewindSeconds: Float
    if (rewindManagerState != null) {
        val enabled by rewindManagerState.enabled.collectAsState(initial = rewindManagerState.enabled.value)
        val supported by rewindManagerState.isSupported.collectAsState(initial = rewindManagerState.isSupported.value)
        val active by rewindManagerState.isRewinding.collectAsState(initial = rewindManagerState.isRewinding.value)
        val available by rewindManagerState.availableStates.collectAsState(initial = rewindManagerState.availableStates.value)
        rewindEnabled = enabled
        rewindSupported = supported
        rewindActive = active
        rewindBufferCount = available
        rewindSeconds = if (enabled && supported) rewindManagerState.availableDurationSeconds() else 0f
    } else {
        rewindEnabled = false
        rewindSupported = false
        rewindActive = false
        rewindBufferCount = 0
        rewindSeconds = 0f
    }
    val canUseRewind = rewindEnabled && rewindSupported && rewindBufferCount > 0
    // Variante de layout (state mutable)
    var layoutVariant by remember {
        mutableStateOf(initialVariant)
    }

    // Charger les settings du contrôleur Lemuroid (nécessaire pour le mode Radial)
    var settings by remember(layoutVariant, console) {
        mutableStateOf(PadKitHelper.loadSettings(prefs, console))
    }
    
    // LIVE UPDATE: Listen for changes to Radial Menu settings
    // Use remember to keep a strong reference to the listener (prevents GC)
    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key != null && key.startsWith("gamepad_$console")) {
                settings = PadKitHelper.loadSettings(sharedPrefs, console)
                // Visual confirmation for debugging
                // android.widget.Toast.makeText(retroView.context, "Radial Updated!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    
    LaunchedEffect(console) {
        ensureRetroArchOverlayPreference(prefs, console, retroView.context)
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
        // Re-apply shader to ensure it sticks
        onShaderChanged(currentShaderName)
        Log.d("ComposeEmulator", "QuickMenu closed with cooldown (Shader re-applied)")
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
                    retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0)
                }
            },
            onButtonRelease = { keyCodes ->
                keyCodes.forEach { keyCode ->
                    retroView.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, 0)
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
            },
            onHotkeyChange = onHotkeyChange,
            onLightgunAction = onLightgunAction
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
    
    // Unified UI Structure to prevent GLRetroView detachment
    MaterialTheme {
        CompositionLocalProvider(LocalLemuroidPadTheme provides LemuroidPadTheme()) {
            PadKit(
                onInputEvents = { event ->
                    // Only process PadKit inputs if NOT in RetroArch mode (RetroArch handles its own inputs)
                    if (layoutVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) {
                        PadKitHelper.handleInputEvents(event, retroView, showMainMenu, settings)
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // 1. The Game Layer (ConstraintLayout) - Persistent
                    // Calculate offsets for RetroArch mode (matches previous logic)
                    val quickBarOffsetDp = if (quickActionsBarVisible) -23.dp else 0.dp
                    val verticalOffsetDp = if (isLandscape) {
                        quickBarOffsetDp
                    } else {
                        quickBarOffsetDp + (-configuration.screenHeightDp * 0.20f).dp
                    }
                    
                    // Determine constraints
                    val currentConstraintSet = if (layoutVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                        buildRetroArchConstraints(verticalOffsetDp)
                    } else {
                        if (isLandscape) buildLandscapeConstraints() else buildPortraitConstraints()
                    }
                    
                    ConstraintLayout(
                        modifier = Modifier.fillMaxSize(),
                        constraintSet = currentConstraintSet
                    ) {
                        // 1.a The Game View (STABLE - Never removed/recreated)
                        AndroidView(
                            factory = { retroView },
                            modifier = Modifier
                                .layoutId("gameView")
                                .onGloballyPositioned { layoutCoordinates ->
                                    // Unified bounds tracking
                                    val position = layoutCoordinates.positionInWindow()
                                    val size = layoutCoordinates.size
                                    val realBounds = Rect(
                                        left = position.x,
                                        top = position.y,
                                        right = position.x + size.width,
                                        bottom = position.y + size.height
                                    )
                                    val oldBounds = gameViewBounds.value
                                    val hasChanged = oldBounds == null || 
                                        kotlin.math.abs(oldBounds.left - realBounds.left) > 3 ||
                                        kotlin.math.abs(oldBounds.top - realBounds.top) > 3 ||
                                        kotlin.math.abs(oldBounds.width - realBounds.width) > 3 ||
                                        kotlin.math.abs(oldBounds.height - realBounds.height) > 3
                                    
                                    if (hasChanged) {
                                        Log.d("ComposeEmulator", "[BOUNDS] GLRetroView REAL bounds: $realBounds")
                                    }
                                    gameViewBounds.value = realBounds
                                }
                                .pointerInteropFilter { event ->
                                    if (isZapperGame) {
                                        val handled = onZapperTouch(event)
                                        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                                             Log.v("RetroArchEmulator", "[ZAPPER] ACTION_MOVE: (${event.x}, ${event.y})")
                                        }
                                        handled
                                    } else {
                                        false
                                    }
                                }
                        )

                        // 1.b Lemuroid Pads (Only if NOT RetroArch and Valid Layout)
                        if (layoutVariant != GamePadLayoutManager.LayoutVariant.RETROARCH && overlaysVisible.value) {
                             // Force recomposition when settings change (Live Update)
                             key(settings) {
                                 layout.left(this@PadKit, Modifier.layoutId("leftPad"), settings)
                                 layout.right(this@PadKit, Modifier.layoutId("rightPad"), settings)
                             }
                        }
                    }

                    // 2. RetroArch Overlay Layer (Z-Index > 0)
                    if (layoutVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                         Box(modifier = Modifier.fillMaxSize()) {
                            // Zapper Crosshair (RetroPlay Style)
                            if (isZapperGame) {
                                ZapperCrosshair(
                                    onTouch = { event -> onZapperTouch(event) },
                                    visible = crosshairMode.showRetroPlayCrosshair()
                                )
                            }
                            
                            // Paste RetroArch Overlay Logic
                            val overlayPreferenceState = remember { mutableStateOf(OverlayPreferenceManager.load(prefs, console)) }
                            val currentOrientation = if (isLandscape) "landscape" else "portrait"
                            val advancedSettingsState = remember { mutableStateOf(OverlayPreferenceManager.loadAdvancedSettings(prefs, console, currentOrientation)) }
                            
                            val preferenceListener = remember {
                                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                    val matchesOverlay = key?.startsWith("overlay_$console") == true
                                    val matchesVariant = key == "gamepad_${console}_variant"
                                    val matchesDebug = key == "overlay_debug_mode"
                                    val matchesAdvanced = key?.startsWith("overlay_${console}_dpad_diagonal_sensitivity") == true ||
                                                        key?.startsWith("overlay_${console}_abxy_diagonal_sensitivity") == true ||
                                                        key?.startsWith("overlay_${console}_analog_recenter_zone") == true ||
                                                        key?.startsWith("overlay_${console}_opacity") == true ||
                                                        key?.startsWith("overlay_${console}_aspect_adjust") == true ||
                                                        key?.startsWith("overlay_${console}_hide_in_menu") == true ||
                                                        key?.startsWith("overlay_${console}_behind_menu") == true ||
                                                        key?.startsWith("overlay_${console}_hide_when_gamepad") == true ||
                                                        key?.startsWith("overlay_${console}_show_inputs") == true ||
                                                        key?.startsWith("overlay_${console}_show_inputs_port") == true ||
                                                        key?.startsWith("overlay_${console}_lightgun") == true ||
                                                        key?.startsWith("overlay_${console}_mouse") == true
                                    
                                    if (matchesOverlay || matchesVariant) {
                                        val newPref = OverlayPreferenceManager.load(prefs, console)
                                        overlayPreferenceState.value = newPref
                                        currentRetroArchLayout = null
                                    }
                                    if (matchesDebug) {
                                        debugModeState.value = prefs.getBoolean("overlay_debug_mode", false)
                                    }
                                    if (matchesAdvanced) {
                                        val currentOrientation = if (isLandscape) "landscape" else "portrait"
                                        val newAdvanced = OverlayPreferenceManager.loadAdvancedSettings(prefs, console, currentOrientation)
                                        advancedSettingsState.value = newAdvanced
                                    }
                                }
                            }
                            
                            DisposableEffect(console) {
                                prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
                                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
                            }
                            
                            val overlayPreference = overlayPreferenceState.value
                            if (overlayPreference != null) {
                                val assetManager = remember { com.retroplay.overlay.assets.OverlayAssetManager(retroView.context) }
                                val overlayConfig = remember(overlayPreference.overlayName, overlayPreference.customCfgName, console) {
                                    assetManager.loadOverlayConfig(overlayPreference.overlayName, console, overlayPreference.customCfgName)
                                }
                                
                                val requestedLayoutName = currentRetroArchLayout ?: run {
                                    if (overlayPreference.autoRotate) {
                                        if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
                                    } else {
                                        if (isLandscape) overlayPreference.landscapeLayout else overlayPreference.portraitLayout
                                    }
                                }
                                
                                val layoutName = overlayConfig?.layouts?.get(requestedLayoutName)?.let { requestedLayoutName }
                                    ?: run {
                                        val orientation = if (isLandscape) "landscape" else "portrait"
                                        overlayConfig?.layouts?.keys?.firstOrNull { it.contains(orientation) }
                                    }
                                
                                if (layoutName != null) {
                                    overlayConfig?.layouts?.get(layoutName)?.let { overlayLayout ->
                                        val advancedSettings = advancedSettingsState.value
                                        val isOverlayConfiguration = showGamePadSettings.value
                                        val isMenuOpen = showMainMenu.value || showQuickMenu.value || showCoreOptionsDialog.value || showPerGameConfigDialog.value || showDiskSwapperDialog.value
                                        
                                        val shouldShowOverlay = when {
                                            !overlaysVisible.value -> false
                                            isOverlayConfiguration -> true
                                            !isMenuOpen -> true
                                            advancedSettings.hideInMenu -> false
                                            else -> true
                                        }
                                        
                                        if (shouldShowOverlay) {
                                            key(layoutName, isLandscape) {
                                                RetroArchOverlayScreen(
                                                    layout = overlayLayout,
                                                    overlayName = overlayPreference.overlayName,
                                                    customCfgName = overlayPreference.customCfgName,
                                                    assetManager = assetManager,
                                                    showDebug = debugModeState.value,
                                                    swapAnalogSticks = overlayPreference.swapAnalogSticks,
                                                    invertAnalogLeftY = overlayPreference.invertAnalogLeftY,
                                                    invertAnalogRightY = overlayPreference.invertAnalogRightY,
                                                    overlayScale = overlayPreference.scale,
                                                    overlayXOffset = overlayPreference.xOffset,
                                                    overlayYOffset = overlayPreference.yOffset,
                                                    overlayXSeparation = overlayPreference.xSeparation,
                                                    overlayYSeparation = overlayPreference.ySeparation,
                                                    overlayOpacity = advancedSettings.opacity,
                                                    dpadDiagonalSensitivity = advancedSettings.dpadDiagonalSensitivity,
                                                    abxyDiagonalSensitivity = advancedSettings.abxyDiagonalSensitivity,
                                                    showInputsMode = advancedSettings.showInputs,
                                                    showInputsPort = advancedSettings.showInputsPort,
                                                    retroView = retroView,
                                                    hideWhenGamepadConnected = advancedSettings.hideWhenGamepadConnected,
                                                    hideWhenGamepadConnectedPort0Only = advancedSettings.hideWhenGamepadConnectedPort0Only,
                                                    analogRecenterZone = advancedSettings.analogRecenterZone,
                                                    aspectAdjust = advancedSettings.aspectAdjust,
                                                    isZapperGame = isZapperGame,
                                                    onButtonPress = { action ->
                                                        val keyCodes = RetroArchButtonMapping.parseAction(action)
                                                        if (keyCodes.isNotEmpty()) {
                                                            keyCodes.forEach { keyCode -> retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0) }
                                                        }
                                                    },
                                                    onButtonRelease = { action ->
                                                        val keyCodes = RetroArchButtonMapping.parseAction(action)
                                                        if (keyCodes.isNotEmpty()) {
                                                            keyCodes.forEach { keyCode -> retroView.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, 0) }
                                                        }
                                                    },
                                                    onLayoutSwitch = { requestedLayout ->
                                                        val mappedLayout = mapLayoutToDeviceOrientation(
                                                            requestedLayout = requestedLayout,
                                                            isLandscape = isLandscape,
                                                            availableLayouts = overlayConfig.layouts.keys
                                                        )
                                                        currentRetroArchLayout = mappedLayout
                                                    },
                                                    onMenuToggle = { showMainMenu.value = true },
                                                    onAnalogMove = { action, x, y ->
                                                        val source = when (action) {
                                                            "analog_left" -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                                                            "analog_right" -> GLRetroView.MOTION_SOURCE_ANALOG_RIGHT
                                                            else -> GLRetroView.MOTION_SOURCE_ANALOG_LEFT
                                                        }
                                                        retroView.sendMotionEvent(source, x, y)
                                                    },
                                                    onHotkey = onHotkey,
                                                    onLightgunAction = onLightgunAction,
                                                    availableLayouts = overlayConfig.layouts.keys.toList().sorted(),
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text("Layout '$requestedLayoutName' not found", color = Color(0xFFFF5722), style = MaterialTheme.typography.titleMedium)
                                            Text("Available: ${overlayConfig?.layouts?.keys?.joinToString()}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                            Button(onClick = { showMainMenu.value = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text("Open Settings", color = Color.Black) }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("No RetroArch overlay loaded", color = Color(0xFFFF5722), style = MaterialTheme.typography.titleLarge)
                                        Text("Select overlay in GamePad Settings", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                                        Button(onClick = { showMainMenu.value = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text("Open Settings", color = Color.Black) }
                                    }
                                }
                            }
                         }
                    }
                // Scope continues...
                
                // Toggle Logic for QuickActionsBar
                // Start collapsed by default
                var isQuickBarExpanded by remember { mutableStateOf(false) }

                // Intercept System Back Gesture (Hardware Back)
    // Behavior: Open Quick Actions Menu (Dashboard) - User Request "Quick Action"
    BackHandler(enabled = !showQuickMenu.value) {
        showQuickMenu.value = true
        retroView.onPause() // PAUSE Emulation when dashboard opens
    }

    // Gestion du FULLSCREEN et des insets pour éviter la barre blanche de navigation
    val view = LocalView.current
                val density = LocalDensity.current
                val statusBarHeight = remember {
                    derivedStateOf {
                        val insets = ViewCompat.getRootWindowInsets(view)
                        val topInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
                        val heightDp = with(density) { topInset.toDp() }
                        if (heightDp.value < 5f) 40.dp else heightDp
                    }
                }
                
                // 1. COLLAPSED STATE: Show Chevron Button
                if (!isQuickBarExpanded && quickActionsBarVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = statusBarHeight.value + 8.dp)
                            .clip(CircleShape)
                            .clickable { isQuickBarExpanded = true }
                            .background(Color(0xB3000000))
                            .padding(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right_24),
                            contentDescription = "Show Quick Actions",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // 2. EXPANDED STATE: Show Bar + Scrim
                if (isQuickBarExpanded && quickActionsBarVisible) {
                    // Scrim: Invisible full-screen layer to detect outside clicks
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { 
                                isQuickBarExpanded = false 
                            }
                    )
                    
                    // The Bar itself (blocks clicks from going to Scrim)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* Consume clicks on the bar itself so it doesn't close */ }
                    ) {
                        QuickActionsBar(
                            isFastForwardActive = isFastForwardActive,
                            audioMuted = audioMuted,
                            isRewindSupported = canUseRewind,
                            isRewinding = rewindActive,
                            rewindDurationSeconds = if (canUseRewind) rewindSeconds else 0f,
                            onRewindPress = { onRewindPress() },
                            onRewindRelease = { onRewindRelease() },
                            onToggleFastForward = { onToggleFastForward() },
                            onToggleAudioMute = { onToggleAudioMute() },
                            onQuickSave = { onSaveState(1) },
                            onQuickLoad = { onLoadState(1) },
                            onOpenSettings = { showMainMenu.value = true },
                            onShaderSelected = { shaderName -> onShaderChanged(shaderName) },
                            currentShaderName = currentShaderName
                        )
                    }
                }
                
                // États locaux pour les sous-menus
                var showSaveSlots by remember { mutableStateOf(false) }
                var showLoadSlots by remember { mutableStateOf(false) }
                var showCheatCodes by remember { mutableStateOf(false) }
                var showCoreSelector by remember { mutableStateOf(false) }
                var showRestartDialog by remember { mutableStateOf(false) }
                var selectedCoreForRestart by remember { mutableStateOf<String?>(null) }
                
                val context = LocalContext.current
                
                if (showQuickMenu.value) {
                    QuickActionsMenu(
                        onDismiss = { 
                            closeQuickMenuWithCooldown()
                        },
                        onHideOverlay = {
                            overlaysVisible.value = !overlaysVisible.value
                            closeQuickMenuWithCooldown()
                        },
                        onSettings = {
                            showQuickMenu.value = false
                            showMainMenu.value = true
                        },

                        onGameInfo = {
                            showQuickMenu.value = false
                            showGameInfoDialog.value = true
                        },
                        onOpenGallery = {
                            showQuickMenu.value = false
                            onOpenGallery()
                        },
                        onSmartConfig = {
                            closeQuickMenuWithCooldown()
                            showSmartConfigDialog.value = true
                        },
                        onCheats = {
                            showQuickMenu.value = false
                            showCheatsDialog.value = true
                        },
                        onSaveState = { slot ->
                            closeQuickMenuWithCooldown()
                            onSaveState(slot)
                        },
                        onLoadState = { slot ->
                            closeQuickMenuWithCooldown()
                            onLoadState(slot)
                        },
                        onSaveStateClick = {
                            closeQuickMenuWithCooldown()
                            showSaveSlots = true
                        },
                        onLoadStateClick = {
                            closeQuickMenuWithCooldown()
                            showLoadSlots = true
                        },
                        onQuit = {
                            showQuickMenu.value = false
                            retroView.onPause()
                            onFinishActivity()
                        },
                        onToggleFastForward = {
                            onToggleFastForward()
                            closeQuickMenuWithCooldown()
                        },
                        onToggleAudioMute = {
                            onToggleAudioMute()
                        },
                        onCycleShader = {
                            onCycleShader()
                        },
                        onToggleQuickActionsBar = {
                            onToggleQuickActionsBar()
                        },
                        onConfigureZapper = {
                            onConfigureZapper()
                            closeQuickMenuWithCooldown()
                        },
                        onToggleCrosshairMode = {
                            onToggleCrosshairMode()
                        },
                        overlaysVisible = overlaysVisible.value,
                        isFastForwardActive = isFastForwardActive,
                        audioMuted = audioMuted,
                        currentShaderName = currentShaderName,
                        quickActionsBarVisible = quickActionsBarVisible,
                        isZapperGame = isZapperGame,
                        crosshairMode = crosshairMode,
                        hasGameInfo = (gameCRC != null),
                        hasGallery = true,
                        hasCheats = loadedCheats.isNotEmpty(),
                        rewindBufferSeconds = rewindSeconds,
                        isRewindSupported = canUseRewind,
                        isRewinding = rewindActive
                    )
                }
                
                if (showMainMenu.value) {
                    QuickMenuDialog(
                        gameName = gameName,
                        gameCRC = gameCRC,
                        configId = configId,
                        console = console,
                        prefs = prefs,
                        onDismiss = { showMainMenu.value = false },
                        onResume = { closeQuickMenuWithCooldown() },
                        onReset = { 
                            closeQuickMenuWithCooldown()
                            try {
                                retroView.reset()
                                android.widget.Toast.makeText(context, "Console Reset", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.util.Log.e("ComposeEmulator", "Error resetting console", e)
                            }
                        },
                        onSaveGame = { 
                            showMainMenu.value = false
                            showSaveSlots = true
                        },
                        onLoadGame = { 
                            showMainMenu.value = false
                            showLoadSlots = true
                        },
                        onSaveStateClick = { 
                            showMainMenu.value = false
                            showSaveSlots = true
                        },
                        onLoadStateClick = { 
                            showMainMenu.value = false
                            showLoadSlots = true
                        },
                        onGamePadSettings = {
                            showMainMenu.value = false
                            showGamePadSettings.value = true
                        },

                        onGameInfo = {
                            showMainMenu.value = false
                            showGameInfoDialog.value = true
                        },
                        onCheatCodes = {
                            showMainMenu.value = false
                            showCheatsDialog.value = true
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
                        onSmartConfig = {
                            closeQuickMenuWithCooldown()
                            showSmartConfigDialog.value = true
                        },
                        onPerGameConfig = {
                            showPerGameConfigDialog.value = true
                        },
                        onTurboSettings = {
                            showMainMenu.value = false
                            onTurboSettings()
                        },
                        onDiskSwapper = {
                            showMainMenu.value = false
                            showDiskSwapperDialog.value = true
                        },
                        onScreenshot = {
                            showMainMenu.value = false
                            onTakeScreenshot()
                        },
                        onOpenGallery = {
                            showMainMenu.value = false
                            onOpenGallery()
                        },
                        hasGameInfo = (gameCRC?.isNotEmpty() == true),
                        hasDipSwitches = dipSwitches.isNotEmpty(),
                        hasCoreOptions = coreOptions.isNotEmpty(),
                        availableDisks = availableDisks
                    )
                }
                
                if (showSaveSlots) {
                    SlotSelectionDialog(
                        title = "Save Game",
                        console = console,
                        gameName = gameName,
                        onDismiss = { showSaveSlots = false },
                        onSlotSelected = { slot ->
                            showSaveSlots = false
                            onSaveState(slot)
                        },
                        onDeleteSlot = { slot ->
                            onDeleteSlot(slot)
                        }
                    )
                }
                
                if (showLoadSlots) {
                    SlotSelectionDialog(
                        title = "Load Game",
                        console = console,
                        gameName = gameName,
                        onDismiss = { showLoadSlots = false },
                        onSlotSelected = { slot ->
                            showLoadSlots = false
                            onLoadState(slot)
                        },
                        onDeleteSlot = { slot ->
                            onDeleteSlot(slot)
                        }
                    )
                }
                
                if (showTurboSettings.value) {
                    TurboSettingsDialog(
                        onDismiss = { showTurboSettings.value = false },
                        onSave = { settings ->
                            android.widget.Toast.makeText(retroView.context, "Turbo settings saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                
                if (showQuickTurbo.value) {
                    QuickTurboMenu(
                        onDismiss = { showQuickTurbo.value = false }
                    )
                }
                
                if (showQuickMenu.value) {
                    QuickActionsMenu(
                        onDismiss = { showQuickMenu.value = false },
                        onHideOverlay = { overlaysVisible.value = !overlaysVisible.value },
                        onSettings = {
                            showQuickMenu.value = false
                            showMainMenu.value = true
                        },

                        onGameInfo = {
                            showQuickMenu.value = false
                            showGameInfoDialog.value = true
                        },
                        onOpenGallery = {
                            showQuickMenu.value = false
                            onOpenGallery()
                        },
                        onSmartConfig = {
                            closeQuickMenuWithCooldown()
                            showSmartConfigDialog.value = true
                        },
                        onCheats = {
                            showQuickMenu.value = false
                            showCheatsDialog.value = true
                        },
                        onSaveState = { slot ->
                            closeQuickMenuWithCooldown()
                            onSaveState(slot)
                        },
                        onLoadState = { slot ->
                            closeQuickMenuWithCooldown()
                            onLoadState(slot)
                        },
                        onSaveStateClick = {
                            closeQuickMenuWithCooldown()
                            showSaveSlots = true
                        },
                        onLoadStateClick = {
                            closeQuickMenuWithCooldown()
                            showLoadSlots = true
                        },
                        onQuit = {
                            showQuickMenu.value = false
                            retroView.onPause()
                            onFinishActivity()
                        },
                        onToggleFastForward = {
                            onToggleFastForward()
                            closeQuickMenuWithCooldown()
                        },
                        onToggleAudioMute = {
                            onToggleAudioMute()
                        },
                        onCycleShader = {
                            onCycleShader()
                        },
                        onToggleQuickActionsBar = {
                            onToggleQuickActionsBar()
                        },
                        onConfigureZapper = {
                            onConfigureZapper()
                            closeQuickMenuWithCooldown()
                        },
                        onToggleCrosshairMode = {
                            onToggleCrosshairMode()
                        },
                        overlaysVisible = overlaysVisible.value,
                        isFastForwardActive = isFastForwardActive,
                        audioMuted = audioMuted,
                        currentShaderName = currentShaderName,
                        quickActionsBarVisible = quickActionsBarVisible,
                        isZapperGame = isZapperGame,
                        crosshairMode = crosshairMode,
                        hasGameInfo = (gameCRC?.isNotEmpty() == true),
                        hasGallery = true,
                        hasCheats = loadedCheats.isNotEmpty(),
                        rewindBufferSeconds = rewindSeconds,
                        isRewindSupported = canUseRewind,
                        isRewinding = rewindActive
                    )
                }
                
                if (showGamePadSettings.value) {
                    GamePadSettingsDialog(
                        console = console,
                        onDismiss = { 
                            showGamePadSettings.value = false
                            // Force reload settings to ensure persistence
                            settings = PadKitHelper.loadSettings(prefs, console)
                            // If returning to game (no other menus open), resume and re-apply shader
                            if (!showMainMenu.value && !showQuickMenu.value) {
                                retroView.onResume()
                                // Re-apply shader to ensure it takes effect after resume
                                // Using currentShaderName to trigger the update in Activity
                                onShaderChanged(currentShaderName)
                                Log.i("ComposeEmulator", "GamePadSettings closed: Resumed retroView and re-applied shader")
                            }
                        },
                        context = retroView.context,
                        prefs = prefs,
                        onLoadCustomCfg = onLoadCustomCfg,
                        debugModeState = debugModeState,
                        currentVariant = layoutVariant,
                        onVariantChanged = { newVariant -> 
                            layoutVariant = newVariant 
                            onVariantChanged(newVariant) // Propagate to Activity for persistence
                        },
                        onShaderChanged = onShaderChanged,
                        onFastForwardRatioChanged = onFastForwardRatioChanged,
                        onAudioVolumeChanged = onAudioVolumeChanged,
                        onAudioMuteChanged = onAudioMuteChanged,
                        onVsyncChanged = onVsyncChanged,
                        onRewindEnabledChanged = onRewindEnabledChanged,
                        onAspectRatioChanged = onAspectRatioChanged,
                        onPsxAnalogModeChanged = { enabled ->
                            if (console.equals("psx", ignoreCase = true)) {
                                try {
                                    val controllers = retroView.getControllers()
                                    if (enabled) {
                                        if (controllers.isNotEmpty() && controllers[0].isNotEmpty()) {
                                            val dualshock = controllers[0].firstOrNull { 
                                                it.description?.contains("dualshock", ignoreCase = true) == true
                                            } ?: controllers[0].firstOrNull {
                                                it.description?.contains("analog", ignoreCase = true) == true
                                            }
                                            if (dualshock != null) {
                                                retroView.setControllerType(0, dualshock.id)
                                                Log.i("ComposeEmulator", "[PSX] Analog mode ON - DualShock activated (id=${dualshock.id})")
                                            }
                                        }
                                    } else {
                                        if (controllers.isNotEmpty() && controllers[0].isNotEmpty()) {
                                            val standardPad = controllers[0].firstOrNull { 
                                                it.description?.contains("standard", ignoreCase = true) == true ||
                                                it.description?.contains("digital", ignoreCase = true) == true
                                            }
                                            if (standardPad != null) {
                                                retroView.setControllerType(0, standardPad.id)
                                                Log.i("ComposeEmulator", "[PSX] Analog mode OFF - Standard pad activated (id=${standardPad.id})")
                                            } else {
                                                retroView.setControllerType(0, controllers[0][0].id)
                                                Log.i("ComposeEmulator", "[PSX] Analog mode OFF - Default pad activated (id=${controllers[0][0].id})")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ComposeEmulator", "[PSX] Error changing analog mode: ${e.message}")
                                }
                            }
                        },
                        onPreviewSettings = { newSettings ->
                            // Direct State Update for smooth Live Preview
                            settings = newSettings
                        }
                    )
                }
                
                
                if (showCfgBrowser.value) {
                    OverlayCfgBrowserDialog(
                        onDismiss = { showCfgBrowser.value = false },
                        onCfgSelected = { overlayName, cfgName ->
                            val pref = com.retroplay.overlay.models.OverlayPreference(
                                enabled = true,
                                overlayName = overlayName,
                                customCfgName = cfgName,
                                landscapeLayout = "landscape-A",
                                portraitLayout = "portrait-A",
                                autoRotate = true
                            )
                            OverlayPreferenceManager.save(prefs, console, pref)
                            OverlayPreferenceManager.saveCustomBrowsed(prefs, console, "$overlayName/$cfgName")
                            
                            android.widget.Toast.makeText(
                                retroView.context,
                                "Custom overlay '$overlayName/$cfgName' loaded!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            
                            Log.i("ComposeEmulator", "Custom overlay selected: $overlayName/$cfgName")
                        }
                    )
                }
                
                if (showCheatCodes) {
                    val cheatManager = remember { com.retroplay.cheat.CheatManager(retroView.context) }
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
                            cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
                            cheatApplier.applyCheatsList(updatedCheats)
                            Log.i("NativeComposeEmulator", "[$console] Applied ${updatedCheats.count { it.enabled }} active cheat(s)")
                        },
                        onAddCustomCheat = {
                            showAddCheatDialog = true
                        },
                        onDeleteCheat = { deletedCheat ->
                            cheatManager.deleteCustomCheat(console, gameName, deletedCheat)
                            cheats = cheatManager.loadCheatsForGame(console, gameName)
                        }
                    )
                    
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
                                cheatManager.addCustomCheatToFile(console, gameName, newCheat)
                                cheats = cheatManager.loadCheatsForGame(console, gameName)
                            }
                        )
                    }
                }
                
                if (showCoreSelector) {
                    CoreSelectorDialog(
                        console = console,
                        currentGamePath = romPath,
                        onCoreSelected = { selectedCore ->
                            showCoreSelector = false
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
                            CoreSelector.removeCoreOverride(romPath)
                            selectedCoreForRestart = null
                            showRestartDialog = true
                        },
                        onDismiss = {
                            showCoreSelector = false
                        }
                    )
                }
                
                if (showRestartDialog) {
                    AlertDialog(
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
                
                if (showN64ExtensionsDialog.value) {
                    N64ExtensionsDialog(
                        extensions = n64ExtensionsInfo.value,
                        onDismiss = { showN64ExtensionsDialog.value = false }
                    )
                }

                if (showGameInfoDialog.value) {
                    GameInfoDialog(
                        gameInfo = gameInfo,
                        gameCRC = gameCRC,
                        console = console,
                        cheatFile = cheatFile,
                        onDismiss = { showGameInfoDialog.value = false },
                        onOpenPerGameConfig = { _, _ ->
                            showGameInfoDialog.value = false
                            showPerGameConfigDialog.value = true
                        },
                        onOpenSmartConfig = {
                            showGameInfoDialog.value = false
                            showSmartConfigDialog.value = true
                        },
                        onOpenGallery = onOpenGallery
                    )
                }

                if (showSmartConfigDialog.value) {
                    SmartConfigDialog(
                        onDismiss = { showSmartConfigDialog.value = false },
                        onSettingsChanged = { /* Settings saved */ },
                        gameName = gameName,
                        consoleName = console,
                        onViewMetadata = {
                            showSmartConfigDialog.value = false
                            showGameInfoDialog.value = true
                        }
                    )
                }

                if (showPerGameConfigDialog.value && gameCRC != null) {
                    PerGameConfigDialog(
                        gameCRC = gameCRC,
                        gameName = gameName ?: "Unknown Game",
                        onDismiss = { showPerGameConfigDialog.value = false },
                        onSave = { /* Configuration saved */ }
                    )
                }

                if (showDiskSwapperDialog.value) {
                    DiskSwapperDialog(
                        availableDisks = availableDisks,
                        currentDiskIndex = currentDisk,
                        onDiskSelected = { index -> onDiskSwapper() }, // This calls the activity callback? No, onDiskSwapper is ()->Unit. Need to fix callback signature in Screen?
                        onDismiss = { showDiskSwapperDialog.value = false }
                    )
                }

                if (showDipSwitchDialog.value) {
                    DipSwitchDialog(
                        gameName = gameName ?: "",
                        dipSwitches = dipSwitches,
                        onApply = { values -> 
                            // TODO: Apply DIP switches
                            // We need a callback that takes Map<String, String>
                            // But onDipSwitches is ()->Unit?
                            // For now, let's just close it. Activity probably handles state updates via CoreVariableManager.
                        },
                        onDismiss = { showDipSwitchDialog.value = false }
                    )
                }
                
                if (showCoreOptionsDialog.value) {
                    EmulationSettingsDialog(
                        gameName = gameName ?: "",
                        coreOptions = coreOptions,
                        onApply = { values ->
                            // 1. Separate Core Variables from Controller Ports
                            // The dialog returns generic values map, but we know keys match CoreVariable keys
                            val variableUpdates = mutableMapOf<String, String>()

                            values.forEach { (key, value) ->
                                // Core Variables
                                if (coreOptions.any { it.key == key }) {
                                    variableUpdates[key] = value
                                }
                            }

                            // 2. Apply Core Variables
                            if (variableUpdates.isNotEmpty()) {
                                // Update in memory state
                                val updatedList = coreOptions.map { variable ->
                                    if (variableUpdates.containsKey(variable.key)) {
                                        variable.copy(currentValue = variableUpdates[variable.key]!!)
                                    } else {
                                        variable
                                    }
                                }
                                coreOptions.clear()
                                coreOptions.addAll(updatedList)

                                // Save to persistence
                                // We need coreId. We can get it from currentCoreFilePath or passed props?
                                // RetroArchEmulatorScreen has 'selectedCoreForRestart' but maybe not the raw ID.
                                // However, CoreVariableManager needs context.
                                val currentCoreId = com.retroplay.CoreVariableManager.extractCoreId(
                                    (retroView.context as? com.retroplay.RetroArchEmulatorActivity)?.getCurrentCorePath() ?: "unknown"
                                )
                                val gameId = java.io.File(romPath).nameWithoutExtension
                                com.retroplay.CoreVariableManager.saveVariables(retroView.context, gameId, currentCoreId, variableUpdates)

                                // Apply to running core
                                retroView.updateVariables(*variableUpdates.map { com.swordfish.libretrodroid.Variable(it.key, it.value) }.toTypedArray())

                                android.widget.Toast.makeText(retroView.context, "Core options applied!", android.widget.Toast.LENGTH_SHORT).show()
                            }

                            // 3. Controller Ports
                            // These are saved to SharedPreferences inside the Dialog, but we must apply them live
                            // Use PROPER keys with console prefix
                            val port1Type = prefs.getInt("${console}_port1_device_type", 1)
                            val port2Type = prefs.getInt("${console}_port2_device_type", 1)

                            try {
                                retroView.setControllerType(0, port1Type)
                                retroView.setControllerType(1, port2Type)
                                android.util.Log.i("ComposeEmulator", "Applied controller types: Port1=$port1Type, Port2=$port2Type")
                            } catch (e: Exception) {
                                android.util.Log.e("ComposeEmulator", "Error applying controller types: ${e.message}")
                            }
                        },
                        onDismiss = { showCoreOptionsDialog.value = false },
                        context = retroView.context,
                        prefs = prefs,
                        console = console
                    )
                }
        }
    }
}
}
}

// Private Helpers

@Composable
private fun N64ExtensionsDialog(
    extensions: List<Pair<Int, String>>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.6f)
                    .verticalScroll(scrollState),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Extensions N64 Configurees",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Les extensions suivantes ont ete configurees pour les ports N64:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider()
                    if (extensions.isEmpty()) {
                        Text(
                            text = "Aucune extension configuree",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        extensions.forEach { (port, extensionName) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Port $port:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = extensionName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Fermer",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

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
            bottom.linkTo(leftPad.top)
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
            bottom.linkTo(parent.bottom)
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

private fun buildRetroArchConstraints(topMargin: androidx.compose.ui.unit.Dp): ConstraintSet {
    return ConstraintSet {
        val gameView = createRefFor("gameView")
        constrain(gameView) {
            width = Dimension.fillToConstraints
            height = Dimension.fillToConstraints
            top.linkTo(parent.top, margin = topMargin)
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }
    }
}

private fun mapLayoutToDeviceOrientation(
    requestedLayout: String,
    isLandscape: Boolean,
    availableLayouts: Set<String>? = null
): String {
    if (availableLayouts?.contains(requestedLayout) == true) {
        return requestedLayout
    }
    val deviceOrientation = if (isLandscape) "landscape" else "portrait"
    val isAnalog = requestedLayout.contains("analog", ignoreCase = true)
    val isHidden = requestedLayout.contains("hidden", ignoreCase = true)
    if (availableLayouts == null) {
        return requestedLayout
    }
    return when {
        isHidden -> {
            availableLayouts.find { it.contains("hidden", ignoreCase = true) }
                ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
        isAnalog -> {
            availableLayouts.find { 
                it.contains(deviceOrientation, ignoreCase = true) && it.contains("analog", ignoreCase = true)
            } ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
        else -> {
            availableLayouts.find { 
                it == deviceOrientation || it == "$deviceOrientation-A"
            } ?: findFallbackLayout(deviceOrientation, availableLayouts)
        }
    }
}

private fun findFallbackLayout(orientation: String, availableLayouts: Set<String>): String {
    return availableLayouts.firstOrNull { it.contains(orientation, ignoreCase = true) }
        ?: availableLayouts.firstOrNull()
        ?: "landscape"
}

private fun ensureRetroArchOverlayPreference(
    prefs: SharedPreferences,
    console: String,
    context: Context
) {
    val current = OverlayPreferenceManager.load(prefs, console)
    if (current != null) {
        return
    }
    val assetManager = com.retroplay.overlay.assets.OverlayAssetManager(context)
    val overlays = assetManager.getCompatibleOverlays(console)
    if (overlays.isEmpty()) {
        return
    }
    val defaultOverlay = overlays.first()
    val (landscape, portrait) = computeDefaultLayoutsForOverlay(assetManager, defaultOverlay, console, null)
    val preference = com.retroplay.overlay.models.OverlayPreference(
        enabled = true,
        overlayName = defaultOverlay,
        customCfgName = null,
        landscapeLayout = landscape,
        portraitLayout = portrait,
        autoRotate = true,
        swapAnalogSticks = false,
        invertAnalogLeftY = false,
        invertAnalogRightY = false,
        scale = 1.0f,
        xOffset = 0.0f,
        yOffset = 0.0f,
        xSeparation = 0.0f,
        ySeparation = 0.0f
    )
    OverlayPreferenceManager.save(prefs, console, preference)
}

private fun computeDefaultLayoutsForOverlay(
    assetManager: com.retroplay.overlay.assets.OverlayAssetManager,
    overlayName: String,
    console: String,
    customCfgName: String?
): Pair<String, String> {
    val layouts = assetManager.getAvailableLayouts(overlayName, console, customCfgName)
    val landscape = layouts.firstOrNull { it.contains("landscape", ignoreCase = true) }
        ?: layouts.firstOrNull()
        ?: "landscape-A"
    val portrait = layouts.firstOrNull { it.contains("portrait", ignoreCase = true) }
        ?: landscape
    return landscape to portrait
}
