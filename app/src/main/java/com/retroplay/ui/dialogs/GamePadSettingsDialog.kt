package com.retroplay.ui.dialogs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.overlay.assets.OverlayAssetManager
import com.retroplay.overlay.models.OverlayPreference
import com.retroplay.overlay.models.OverlayPreferenceManager
import com.retroplay.GamePadLayoutManager
import com.retroplay.input.PadKitHelper
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.retroplay.ui.components.*
import com.retroplay.ui.components.RadialSlider
import com.retroplay.ui.components.AdvancedVisibilitySettingsContent
import com.retroplay.ui.components.GamePadVisibilitySettings
import com.retroplay.ui.components.GamePadAdvancedSettings
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size



@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePadSettingsDialog(
    console: String,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    onLoadCustomCfg: (() -> Unit)? = null,
    debugModeState: MutableState<Boolean>,
    currentVariant: GamePadLayoutManager.LayoutVariant,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onShaderChanged: (String) -> Unit = {},
    onFastForwardRatioChanged: (Float) -> Unit = {},
    onAudioVolumeChanged: (Float) -> Unit = {},
    onAudioMuteChanged: (Boolean) -> Unit = {},
    onVsyncChanged: (Boolean) -> Unit = {},
    onRewindEnabledChanged: (Boolean) -> Unit = {},
    onAspectRatioChanged: (String) -> Unit = {},
    onOpenAdvancedOverlaySettings: (() -> Unit)? = null,  // Callback pour ouvrir Advanced Overlay Settings
    onPsxAnalogModeChanged: ((Boolean) -> Unit)? = null  // Callback pour changer mode analog PSX (immediate, no reboot)
) {
    // Migration automatique depuis .cfg vers SharedPreferences (une seule fois)
    LaunchedEffect(Unit) {
        val migrated = prefs.getBoolean("emulation_settings_migrated_from_cfg", false)
        if (!migrated) {
            try {
                // Note: GamePadSettingsDialog doesn't have console context, use default
                // This is legacy code, should use UnifiedSettingsDialog instead
                val config = com.retroplay.config.RetroPlayConfigManager.loadConfig("nes")  // Default fallback
                val editor = prefs.edit()
                
                // Migrer VSync
                if (!prefs.contains("emulation_video_vsync")) {
                    editor.putBoolean("emulation_video_vsync", config.videoVsync)
                }
                
                // Migrer Rewind
                if (!prefs.contains("emulation_rewind_enable")) {
                    editor.putBoolean("emulation_rewind_enable", config.rewindEnable)
                }
                
                // Migrer Run-Ahead
                if (!prefs.contains("emulation_runahead_enabled")) {
                    editor.putBoolean("emulation_runahead_enabled", config.runAheadEnabled)
                }
                if (!prefs.contains("emulation_runahead_frames")) {
                    editor.putInt("emulation_runahead_frames", config.runAheadFrames)
                }
                
                editor.putBoolean("emulation_settings_migrated_from_cfg", true).apply()
                android.util.Log.i("RetroArchSettingsDialog", "Migrated settings from .cfg to SharedPreferences")
            } catch (e: Exception) {
                android.util.Log.e("RetroArchSettingsDialog", "Migration failed: ${e.message}")
            }
        }
    }
    val assetManager = remember { OverlayAssetManager(context) }
    val overlayPackages = remember { assetManager.getCompatibleOverlays(console) }

    var customBrowsed by remember { mutableStateOf(OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()) }
    
    // OPTIMISATION: Chargement asynchrone des layouts disponibles (évite ANR pour packages avec 16+ layouts)
    var availableLayoutsLoading by remember { mutableStateOf(false) }
    var availableLayoutsCache by remember { mutableStateOf<List<String>>(emptyList()) }

    val currentOverlayPref = remember { OverlayPreferenceManager.load(prefs, console) }
    var selectedOverlay by remember {
        mutableStateOf(
            currentOverlayPref?.overlayName
                ?: overlayPackages.firstOrNull().orEmpty()
        )
    }
    var selectedCustomPath by remember {
        mutableStateOf(
            currentOverlayPref?.customCfgName?.let {
                "${currentOverlayPref.overlayName}/$it"
            }
        )
    }
    var selectedLandscapeLayout by remember {
        mutableStateOf(currentOverlayPref?.landscapeLayout ?: "landscape-A")
    }
    var selectedPortraitLayout by remember {
        mutableStateOf(currentOverlayPref?.portraitLayout ?: "portrait-A")
    }
    var autoRotate by remember { mutableStateOf(currentOverlayPref?.autoRotate ?: true) }
    var swapAnalogSticks by remember { mutableStateOf(currentOverlayPref?.swapAnalogSticks ?: false) }
    var invertAnalogLeftY by remember { mutableStateOf(currentOverlayPref?.invertAnalogLeftY ?: false) }
    var invertAnalogRightY by remember { mutableStateOf(currentOverlayPref?.invertAnalogRightY ?: false) }
    var scale by remember { mutableStateOf(currentOverlayPref?.scale ?: 1.0f) }
    var xOffset by remember { mutableStateOf(currentOverlayPref?.xOffset ?: 0.0f) }
    var yOffset by remember { mutableStateOf(currentOverlayPref?.yOffset ?: 0.0f) }
    var xSeparation by remember { mutableStateOf(currentOverlayPref?.xSeparation ?: 0.0f) }
    var ySeparation by remember { mutableStateOf(currentOverlayPref?.ySeparation ?: 0.0f) }

    // Radial Settings State
    val radialSettings = remember { PadKitHelper.loadSettings(prefs, console) }
    var radialScale by remember { mutableFloatStateOf(radialSettings.scale) }
    var radialRotation by remember { mutableFloatStateOf(radialSettings.rotation) }
    var radialMarginX by remember { mutableFloatStateOf(radialSettings.marginX) }
    var radialMarginY by remember { mutableFloatStateOf(radialSettings.marginY) }
    var radialSwapAnalogSticks by remember { mutableStateOf(radialSettings.swapAnalogSticks) }
    var radialInvertAnalogLeftY by remember { mutableStateOf(radialSettings.invertAnalogLeftY) }
    var radialInvertAnalogRightY by remember { mutableStateOf(radialSettings.invertAnalogRightY) }

    
    // Detect optimization
    var isAdjusting by remember { mutableStateOf(false) }

    var isTransparent by remember { mutableStateOf(false) }

    val contentScrollState = rememberScrollState()
    
    // Tab sélectionné (0 = Overlays, 1 = Advanced, 2 = General)
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // ========== ADVANCED OVERLAY SETTINGS STATE (intégré depuis AdvancedOverlaySettingsDialog) ==========
    // Detect current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val orientation = if (isLandscape) "landscape" else "portrait"
    
    // Migrate global settings to per-orientation (one-time, on first load)
    LaunchedEffect(console) {
        com.retroplay.overlay.models.OverlayPreferenceManager.migrateAdvancedSettingsToPerOrientation(prefs, console)
    }
    
    // Check if settings are the same for both orientations
    var useSameSettings by remember { 
        mutableStateOf(
            com.retroplay.overlay.models.OverlayPreferenceManager.checkIfSettingsAreSame(prefs, console)
        )
    }
    
    // Load settings according to orientation (or global if useSameSettings)
    var advancedSettings by remember(orientation, useSameSettings) { 
        mutableStateOf(
            if (useSameSettings) {
                // If "same settings", try to load global first, then fallback to landscape
                com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                    ?: com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
            } else {
                com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
            }
        )
    }
    
    // State variables from loaded settings
    var dpadDiagonalSensitivity by remember { mutableStateOf(advancedSettings.dpadDiagonalSensitivity) }
    var abxyDiagonalSensitivity by remember { mutableStateOf(advancedSettings.abxyDiagonalSensitivity) }
    var analogRecenterZone by remember { mutableStateOf(advancedSettings.analogRecenterZone) }
    var opacity by remember { mutableStateOf(advancedSettings.opacity) }
    var aspectAdjust by remember { mutableStateOf(advancedSettings.aspectAdjust) }
    var hideInMenu by remember { mutableStateOf(advancedSettings.hideInMenu) }
    var behindMenu by remember { mutableStateOf(advancedSettings.behindMenu) }
    var hideWhenGamepad by remember { mutableStateOf(advancedSettings.hideWhenGamepadConnected) }
    var hideWhenGamepadPort0Only by remember { mutableStateOf(advancedSettings.hideWhenGamepadConnectedPort0Only) }
    var showInputs by remember { mutableStateOf(advancedSettings.showInputs) }
    var showInputsPort by remember { mutableStateOf(advancedSettings.showInputsPort) }
    var lightgunPort by remember { mutableStateOf(advancedSettings.lightgunPort) }
    var lightgunTriggerOnTouch by remember { mutableStateOf(advancedSettings.lightgunTriggerOnTouch) }
    var lightgunTriggerDelay by remember { mutableStateOf(advancedSettings.lightgunTriggerDelay) }
    var lightgunAllowOffscreen by remember { mutableStateOf(advancedSettings.lightgunAllowOffscreen) }
    var mouseSpeed by remember { mutableStateOf(advancedSettings.mouseSpeed) }
    var mouseSwipeThreshold by remember { mutableStateOf(advancedSettings.mouseSwipeThreshold) }
    var mouseHoldToDrag by remember { mutableStateOf(advancedSettings.mouseHoldToDrag) }
    var mouseHoldMsec by remember { mutableStateOf(advancedSettings.mouseHoldMsec) }
    var mouseDoubleTapToDrag by remember { mutableStateOf(advancedSettings.mouseDoubleTapToDrag) }
    var mouseDtapMsec by remember { mutableStateOf(advancedSettings.mouseDtapMsec) }
    var showMouseCursor by remember { mutableStateOf(advancedSettings.showMouseCursor) }
    
    // Reload settings when orientation or toggle changes
    LaunchedEffect(orientation, useSameSettings) {
        val newSettings = if (useSameSettings) {
            com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                ?: com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
        } else {
            com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
        }
        advancedSettings = newSettings
        dpadDiagonalSensitivity = newSettings.dpadDiagonalSensitivity
        abxyDiagonalSensitivity = newSettings.abxyDiagonalSensitivity
        analogRecenterZone = newSettings.analogRecenterZone
        opacity = newSettings.opacity
        aspectAdjust = newSettings.aspectAdjust
        hideInMenu = newSettings.hideInMenu
        behindMenu = newSettings.behindMenu
        hideWhenGamepad = newSettings.hideWhenGamepadConnected
        hideWhenGamepadPort0Only = newSettings.hideWhenGamepadConnectedPort0Only
        showInputs = newSettings.showInputs
        showInputsPort = newSettings.showInputsPort
        lightgunPort = newSettings.lightgunPort
        lightgunTriggerOnTouch = newSettings.lightgunTriggerOnTouch
        lightgunTriggerDelay = newSettings.lightgunTriggerDelay
        lightgunAllowOffscreen = newSettings.lightgunAllowOffscreen
        mouseSpeed = newSettings.mouseSpeed
        mouseSwipeThreshold = newSettings.mouseSwipeThreshold
        mouseHoldToDrag = newSettings.mouseHoldToDrag
        mouseHoldMsec = newSettings.mouseHoldMsec
        mouseDoubleTapToDrag = newSettings.mouseDoubleTapToDrag
        mouseDtapMsec = newSettings.mouseDtapMsec
        showMouseCursor = newSettings.showMouseCursor
    }
    
    // Save when changed + trigger preview transparency (Advanced Overlay Settings)
    LaunchedEffect(dpadDiagonalSensitivity, abxyDiagonalSensitivity, analogRecenterZone, opacity, aspectAdjust, hideInMenu, behindMenu, hideWhenGamepad, hideWhenGamepadPort0Only, showInputs, showInputsPort, lightgunPort, lightgunTriggerOnTouch, lightgunTriggerDelay, lightgunAllowOffscreen, mouseSpeed, mouseSwipeThreshold, mouseHoldToDrag, mouseHoldMsec, mouseDoubleTapToDrag, mouseDtapMsec, showMouseCursor, useSameSettings) {
        val newSettings = com.retroplay.overlay.models.AdvancedOverlaySettings(
            dpadDiagonalSensitivity = dpadDiagonalSensitivity,
            abxyDiagonalSensitivity = abxyDiagonalSensitivity,
            analogRecenterZone = analogRecenterZone,
            opacity = opacity,
            aspectAdjust = aspectAdjust,
            hideInMenu = hideInMenu,
            behindMenu = behindMenu,
            hideWhenGamepadConnected = hideWhenGamepad,
            hideWhenGamepadConnectedPort0Only = hideWhenGamepadPort0Only,
            showInputs = showInputs,
            showInputsPort = showInputsPort,
            lightgunPort = lightgunPort,
            lightgunTriggerOnTouch = lightgunTriggerOnTouch,
            lightgunTriggerDelay = lightgunTriggerDelay,
            lightgunAllowOffscreen = lightgunAllowOffscreen,
            lightgunTwoTouchInput = advancedSettings.lightgunTwoTouchInput,
            lightgunThreeTouchInput = advancedSettings.lightgunThreeTouchInput,
            lightgunFourTouchInput = advancedSettings.lightgunFourTouchInput,
            mouseSpeed = mouseSpeed,
            mouseSwipeThreshold = mouseSwipeThreshold,
            mouseHoldToDrag = mouseHoldToDrag,
            mouseHoldMsec = mouseHoldMsec,
            mouseDoubleTapToDrag = mouseDoubleTapToDrag,
            mouseDtapMsec = mouseDtapMsec,
            showMouseCursor = showMouseCursor
        )
        
        if (useSameSettings) {
            // Save to both orientations
            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", newSettings)
            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", newSettings)
        } else {
            // Save to current orientation
            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, orientation, newSettings)
        }
        
        android.util.Log.i("RetroArchSettings", "Saved advanced settings for $console ($orientation, useSame=$useSameSettings): dpadSens=$dpadDiagonalSensitivity abxySens=$abxyDiagonalSensitivity recenter=$analogRecenterZone opacity=$opacity")
        
        // Trigger preview transparency for 2 seconds
        isTransparent = true
        kotlinx.coroutines.delay(2000)
        isTransparent = false
    }

    DisposableEffect(console) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "overlay_${console}_custom_browsed") {
                customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
            }
            if (key == "overlay_${console}_name" || key == "overlay_${console}_custom_cfg") {
                val updatedPref = OverlayPreferenceManager.load(prefs, console)
                if (updatedPref != null) {
                    selectedOverlay = updatedPref.overlayName
                    selectedCustomPath = updatedPref.customCfgName?.let { "${updatedPref.overlayName}/$it" }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // OPTIMISATION: Chargement asynchrone avec LaunchedEffect pour éviter ANR
    // rgpad a 16 layouts, le parsing peut prendre 5-10 secondes sur le main thread
    LaunchedEffect(selectedOverlay, selectedCustomPath) {
        if (selectedOverlay.isNotEmpty()) {
            availableLayoutsLoading = true
            // Lancer dans un coroutine (ne bloque pas le UI thread)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val customCfgName = selectedCustomPath?.substringAfter("/")
                val layouts = assetManager.getAvailableLayouts(selectedOverlay, console, customCfgName)
                // Mettre à jour sur le main thread
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    availableLayoutsCache = layouts
                    availableLayoutsLoading = false
                }
            }
        } else {
            availableLayoutsCache = emptyList()
            availableLayoutsLoading = false
        }
    }
    
    val availableLayouts = availableLayoutsCache

    LaunchedEffect(availableLayouts, selectedLandscapeLayout) {
        if (availableLayouts.isNotEmpty()) {
            val landscapeLayouts = availableLayouts.filter { it.contains("landscape", ignoreCase = true) }
            val portraitLayouts = availableLayouts.filter { it.contains("portrait", ignoreCase = true) }

            if (landscapeLayouts.isNotEmpty() && !landscapeLayouts.contains(selectedLandscapeLayout)) {
                selectedLandscapeLayout = landscapeLayouts.first()
            } else if (landscapeLayouts.isEmpty() && !availableLayouts.contains(selectedLandscapeLayout)) {
                selectedLandscapeLayout = availableLayouts.first()
            }

            if (portraitLayouts.isNotEmpty() && !portraitLayouts.contains(selectedPortraitLayout)) {
                selectedPortraitLayout = portraitLayouts.first()
            } else if (portraitLayouts.isEmpty() && !availableLayouts.contains(selectedPortraitLayout)) {
                selectedPortraitLayout = selectedLandscapeLayout
            }
        }
    }

    LaunchedEffect(
        selectedOverlay,
        selectedCustomPath,
        selectedLandscapeLayout,
        selectedPortraitLayout,
        autoRotate,
        swapAnalogSticks,
        invertAnalogLeftY,
        invertAnalogRightY,
        scale,
        xOffset,
        yOffset,
        xSeparation,
        ySeparation
    ) {
        if (selectedOverlay.isNotEmpty()) {
            val customCfgName = selectedCustomPath?.substringAfter("/")
            val pref = OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                customCfgName = customCfgName,
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogLeftY = invertAnalogLeftY,
                invertAnalogRightY = invertAnalogRightY,
                scale = scale,
                xOffset = xOffset,
                yOffset = yOffset,
                xSeparation = xSeparation,
                ySeparation = ySeparation
            )
            OverlayPreferenceManager.save(prefs, console, pref)
            isTransparent = true
            kotlinx.coroutines.delay(1200)
            isTransparent = false
        }
    }

    // Save Radial Settings
    LaunchedEffect(
        radialScale,
        radialRotation,
        radialMarginX,
        radialMarginY,
        radialSwapAnalogSticks,
        radialInvertAnalogLeftY,
        radialInvertAnalogRightY
    ) {
        PadKitHelper.saveSettings(prefs, console, TouchControllerSettingsManager.Settings(
            scale = radialScale,
            rotation = radialRotation,
            marginX = radialMarginX,
            marginY = radialMarginY,
            swapAnalogSticks = radialSwapAnalogSticks,
            invertAnalogLeftY = radialInvertAnalogLeftY,
            invertAnalogRightY = radialInvertAnalogRightY
        ))
    }




    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .verticalScroll(contentScrollState),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTransparent)
                        Color(0xFF000000).copy(alpha = 0.3f)
                    else
                        Color(0xB3111111) // 70% opacity for better see-through
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "GamePad Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    
                    // Layout Switch
                    // Layout Switch (Refactored to Tabs)
                    TabRow(
                        selectedTabIndex = if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) 0 else 1,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Tab(
                            selected = currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH,
                            onClick = { onVariantChanged(GamePadLayoutManager.LayoutVariant.RETROARCH) },
                            text = { 
                                Text(
                                    "RetroArch", 
                                    color = if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                        Tab(
                            selected = currentVariant == GamePadLayoutManager.LayoutVariant.DEFAULT,
                            onClick = { onVariantChanged(GamePadLayoutManager.LayoutVariant.DEFAULT) },
                            text = { 
                                Text(
                                    "Radial Menu", 
                                    color = if (currentVariant == GamePadLayoutManager.LayoutVariant.DEFAULT) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (currentVariant == GamePadLayoutManager.LayoutVariant.DEFAULT) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }

                    if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                    Text(
                        text = "Console: ${console.uppercase()}",
                        fontSize = 14.sp,
                        color = Color(0xFFBBBBBB)
                    )

                    HorizontalDivider(color = Color(0xFF444444))
                    
                    // Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { 
                                Text(
                                    "Overlays", 
                                    color = if (selectedTab == 0) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { 
                                Text(
                                    "Advanced", 
                                    color = if (selectedTab == 1) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { 
                                Text(
                                    "General", 
                                    color = if (selectedTab == 2) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                    
                    HorizontalDivider(color = Color(0xFF444444))
                    
                    // Contenu selon l'onglet sélectionné
                    when (selectedTab) {
                        0 -> {
                            // ========== ONGLET 1: OVERLAYS (Visibility) ==========
                            GamePadVisibilitySettings(
                                console = console,
                                prefs = prefs,
                                assetManager = assetManager,
                                overlayPackages = overlayPackages,
                                customBrowsed = customBrowsed,
                                availableLayouts = availableLayouts,
                                selectedOverlay = selectedOverlay,
                                selectedCustomPath = selectedCustomPath,
                                selectedLandscapeLayout = selectedLandscapeLayout,
                                selectedPortraitLayout = selectedPortraitLayout,
                                autoRotate = autoRotate,
                                swapAnalogSticks = swapAnalogSticks,
                                invertAnalogLeftY = invertAnalogLeftY,
                                invertAnalogRightY = invertAnalogRightY,
                                scale = scale,
                                xOffset = xOffset,
                                yOffset = yOffset,
                                xSeparation = xSeparation,
                                ySeparation = ySeparation,
                                onSelectedOverlayChanged = { selectedOverlay = it },
                                onSelectedCustomPathChanged = { selectedCustomPath = it },
                                onSelectedLandscapeLayoutChanged = { selectedLandscapeLayout = it },
                                onSelectedPortraitLayoutChanged = { selectedPortraitLayout = it },
                                onAutoRotateChanged = { autoRotate = it },
                                onSwapAnalogSticksChanged = { swapAnalogSticks = it },
                                onInvertAnalogLeftYChanged = { invertAnalogLeftY = it },
                                onInvertAnalogRightYChanged = { invertAnalogRightY = it },
                                onScaleChanged = { scale = it },
                                onXOffsetChanged = { xOffset = snapToZero(it) },
                                onYOffsetChanged = { yOffset = snapToZero(it) },
                                onXSeparationChanged = { xSeparation = snapToZero(it) },
                                onYSeparationChanged = { ySeparation = snapToZero(it) },
                                onCustomBrowsedUpdated = {
                                    customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
                                },
                                onLoadCustomCfg = onLoadCustomCfg
                            )
                    }
                        1 -> {
                            // ========== ONGLET 2: ADVANCED OVERLAY SETTINGS ==========
                            GamePadAdvancedSettings(
                                console = console,
                                prefs = prefs,
                                orientation = orientation,
                                useSameSettings = useSameSettings,
                                dpadDiagonalSensitivity = dpadDiagonalSensitivity,
                                abxyDiagonalSensitivity = abxyDiagonalSensitivity,
                                analogRecenterZone = analogRecenterZone,
                                opacity = opacity,
                                aspectAdjust = aspectAdjust,
                                showInputs = showInputs,
                                showInputsPort = showInputsPort,
                                hideInMenu = hideInMenu,
                                behindMenu = behindMenu,
                                hideWhenGamepad = hideWhenGamepad,
                                hideWhenGamepadPort0Only = hideWhenGamepadPort0Only,
                                lightgunPort = lightgunPort,
                                lightgunTriggerOnTouch = lightgunTriggerOnTouch,
                                lightgunTriggerDelay = lightgunTriggerDelay,
                                lightgunAllowOffscreen = lightgunAllowOffscreen,
                                mouseSpeed = mouseSpeed,
                                mouseSwipeThreshold = mouseSwipeThreshold,
                                mouseHoldToDrag = mouseHoldToDrag,
                                mouseHoldMsec = mouseHoldMsec,
                                mouseDoubleTapToDrag = mouseDoubleTapToDrag,
                                mouseDtapMsec = mouseDtapMsec,
                                showMouseCursor = showMouseCursor,
                                onUseSameSettingsChanged = { useSameSettings = it },
                                onDpadDiagonalSensitivityChanged = { dpadDiagonalSensitivity = it },
                                onAbxyDiagonalSensitivityChanged = { abxyDiagonalSensitivity = it },
                                onAnalogRecenterZoneChanged = { analogRecenterZone = it },
                                onOpacityChanged = { opacity = it },
                                onAspectAdjustChanged = { aspectAdjust = it },
                                onShowInputsChanged = { showInputs = it },
                                onShowInputsPortChanged = { showInputsPort = it },
                                onHideInMenuChanged = { hideInMenu = it },
                                onBehindMenuChanged = { behindMenu = it },
                                onHideWhenGamepadChanged = { hideWhenGamepad = it },
                                onHideWhenGamepadPort0OnlyChanged = { hideWhenGamepadPort0Only = it },
                                onLightgunPortChanged = { lightgunPort = it },
                                onLightgunTriggerOnTouchChanged = { lightgunTriggerOnTouch = it },
                                onLightgunTriggerDelayChanged = { lightgunTriggerDelay = it },
                                onLightgunAllowOffscreenChanged = { lightgunAllowOffscreen = it },
                                onMouseSpeedChanged = { mouseSpeed = it },
                                onMouseSwipeThresholdChanged = { mouseSwipeThreshold = it },
                                onMouseHoldToDragChanged = { mouseHoldToDrag = it },
                                onMouseHoldMsecChanged = { mouseHoldMsec = it },
                                onMouseDoubleTapToDragChanged = { mouseDoubleTapToDrag = it },
                                onMouseDtapMsecChanged = { mouseDtapMsec = it },
                                onShowMouseCursorChanged = { showMouseCursor = it },
                                onUseSameSettingsToggled = { newValue ->
                                    if (newValue) {
                                            // Copy current settings to both orientations
                                            val current = com.retroplay.overlay.models.AdvancedOverlaySettings(
                                                dpadDiagonalSensitivity = dpadDiagonalSensitivity,
                                                abxyDiagonalSensitivity = abxyDiagonalSensitivity,
                                                analogRecenterZone = analogRecenterZone,
                                                opacity = opacity,
                                                aspectAdjust = aspectAdjust,
                                                hideInMenu = hideInMenu,
                                                behindMenu = behindMenu,
                                                hideWhenGamepadConnected = hideWhenGamepad,
                                                hideWhenGamepadConnectedPort0Only = hideWhenGamepadPort0Only,
                                                showInputs = showInputs,
                                                showInputsPort = showInputsPort,
                                                lightgunPort = lightgunPort,
                                                lightgunTriggerOnTouch = lightgunTriggerOnTouch,
                                                lightgunTriggerDelay = lightgunTriggerDelay,
                                                lightgunAllowOffscreen = lightgunAllowOffscreen,
                                                lightgunTwoTouchInput = advancedSettings.lightgunTwoTouchInput,
                                                lightgunThreeTouchInput = advancedSettings.lightgunThreeTouchInput,
                                                lightgunFourTouchInput = advancedSettings.lightgunFourTouchInput,
                                                mouseSpeed = mouseSpeed,
                                                mouseSwipeThreshold = mouseSwipeThreshold,
                                                mouseHoldToDrag = mouseHoldToDrag,
                                                mouseHoldMsec = mouseHoldMsec,
                                                mouseDoubleTapToDrag = mouseDoubleTapToDrag,
                                                mouseDtapMsec = mouseDtapMsec,
                                                showMouseCursor = showMouseCursor
                                            )
                                            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", current)
                                            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", current)
                                        }
                                }
                            )
                    }
                        2 -> {
                            GeneralSettingsTabContent(
                                prefs = prefs,
                                console = console,
                                debugModeState = debugModeState,
                                onShaderChanged = onShaderChanged,
                                onVsyncChanged = onVsyncChanged,
                                onAspectRatioChanged = onAspectRatioChanged,
                                onAudioVolumeChanged = onAudioVolumeChanged,
                                onAudioMuteChanged = onAudioMuteChanged,
                                onFastForwardRatioChanged = onFastForwardRatioChanged,
                                onRewindEnabledChanged = onRewindEnabledChanged,
                                onPsxAnalogModeChanged = onPsxAnalogModeChanged
                            )
                        }    

                    }

                    HorizontalDivider(color = Color(0xFF444444))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (overlayPackages.isNotEmpty()) {
                                    val defaultOverlay = overlayPackages.first()
                                    val (land, port) = computeDefaultLayouts(assetManager, defaultOverlay, console, null)
                                    selectedOverlay = defaultOverlay
                                    selectedCustomPath = null
                                    selectedLandscapeLayout = land
                                    selectedPortraitLayout = port
                                    autoRotate = true
                                    swapAnalogSticks = false
                                    invertAnalogLeftY = false
                                    invertAnalogRightY = false
                                    scale = 1.0f
                                    xOffset = 0.0f
                                    yOffset = 0.0f
                                    xSeparation = 0.0f
                                    ySeparation = 0.0f
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Text("Reset Overlay", color = Color.White)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                } else {
                    // Radial Settings Mode with Tabs
                    var selectedRadialTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    
                    androidx.compose.material3.TabRow(
                        selectedTabIndex = selectedRadialTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        androidx.compose.material3.Tab(
                            selected = selectedRadialTab == 0,
                            onClick = { selectedRadialTab = 0 },
                            text = { 
                                Text(
                                    "Settings", 
                                    color = if (selectedRadialTab == 0) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (selectedRadialTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        androidx.compose.material3.Tab(
                            selected = selectedRadialTab == 1,
                            onClick = { selectedRadialTab = 1 },
                            text = { 
                                Text(
                                    "Advanced",
                                    color = if (selectedRadialTab == 1) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (selectedRadialTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        androidx.compose.material3.Tab(
                            selected = selectedRadialTab == 2,
                            onClick = { selectedRadialTab = 2 },
                            text = { 
                                Text(
                                    "General",
                                    color = if (selectedRadialTab == 2) Color(0xFFFF9800) else Color(0xFF888888),
                                    fontWeight = if (selectedRadialTab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }

                    when (selectedRadialTab) {
                        0 -> {
                            // Radial Settings UI - Advanced Integrated
                            // ... (Keep existing Radial implementation)
                            Text("Radial Menu", color = Color(0xFF2196F3), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            // ... (Keep existing Radial implementation lines 1772-1964)
                             Spacer(Modifier.height(8.dp))
                            
                            HorizontalDivider(color = Color(0x40FFFFFF))
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                text = "Precision Adjustments",
                                color = Color(0xFF2196F3),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            RadialSlider(
                                label = "Scale",
                                value = radialScale,
                                displayValue = "${String.format("%.2f", radialScale * 0.75f + 0.75f)}x",
                                valueRange = 0f..1f,
                                onValueChange = { radialScale = it },
                                onNudge = { delta ->
                                    radialScale = (radialScale + delta).coerceIn(0f, 1f)
                                }
                            )

                            RadialSlider(
                                label = "Rotation",
                                value = radialRotation,
                                displayValue = "${String.format("%.0f", radialRotation * TouchControllerSettingsManager.MAX_ROTATION)}°",
                                valueRange = 0f..1f,
                                onValueChange = { radialRotation = it },
                                onNudge = { delta ->
                                    radialRotation = (radialRotation + delta).coerceIn(0f, 1f)
                                }
                            )

                            RadialSlider(
                                label = "Horizontal Margin",
                                value = radialMarginX,
                                displayValue = "${String.format("%.0f", radialMarginX * TouchControllerSettingsManager.MAX_MARGINS)}dp",
                                valueRange = 0f..1f,
                                onValueChange = { radialMarginX = it },
                                onNudge = { delta ->
                                    radialMarginX = (radialMarginX + delta).coerceIn(0f, 1f)
                                }
                            )

                            RadialSlider(
                                label = "Vertical Margin",
                                value = radialMarginY,
                                displayValue = "${String.format("%.0f", radialMarginY * TouchControllerSettingsManager.MAX_MARGINS)}dp",
                                valueRange = 0f..1f,
                                onValueChange = { radialMarginY = it },
                                onNudge = { delta ->
                                    radialMarginY = (radialMarginY + delta).coerceIn(0f, 1f)
                                }
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0x40FFFFFF))
                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "Analog Options",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            SwitchRow(
                                title = "Swap Left/Right Sticks",
                                subtitle = "Exchange left and right analog positions",
                                checked = radialSwapAnalogSticks,
                                onCheckedChange = { radialSwapAnalogSticks = it }
                            )

                            SwitchRow(
                                title = "Invert Left Stick Y",
                                subtitle = "Reverse up/down movement for left analog",
                                checked = radialInvertAnalogLeftY,
                                onCheckedChange = { radialInvertAnalogLeftY = it }
                            )

                            SwitchRow(
                                title = "Invert Right Stick Y",
                                subtitle = "Reverse up/down movement for right analog",
                                checked = radialInvertAnalogRightY,
                                onCheckedChange = { radialInvertAnalogRightY = it }
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0x40FFFFFF))
                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = "Lightgun Options",
                                color = Color(0xFFFFC107),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            SwitchRow(
                                title = "Trigger on Touch",
                                subtitle = "Fire immediately when touching the screen",
                                checked = lightgunTriggerOnTouch,
                                onCheckedChange = { lightgunTriggerOnTouch = it }
                            )

                            SwitchRow(
                                title = "Allow Off-Screen Shots",
                                subtitle = "Permit shooting outside the playfield",
                                checked = lightgunAllowOffscreen,
                                onCheckedChange = { lightgunAllowOffscreen = it }
                            )

                            Column {
                                Text(
                                    text = "Trigger Delay: ${lightgunTriggerDelay} frame(s)",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Slider(
                                    value = lightgunTriggerDelay.toFloat(),
                                    onValueChange = { lightgunTriggerDelay = it.roundToInt().coerceAtLeast(0) },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFC107),
                                        activeTrackColor = Color(0xFFFFC107),
                                        inactiveTrackColor = Color(0x40FFFFFF)
                                    )
                                )
                                Text(
                                    text = "Frames to wait before sending the lightgun trigger",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }

                            Column {
                                Text(
                                    text = "Lightgun Port: ${if (lightgunPort < 0) "All" else lightgunPort + 1}",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Slider(
                                    value = lightgunPort.toFloat(),
                                    onValueChange = {
                                        lightgunPort = it.roundToInt().coerceIn(-1, 3)
                                    },
                                    valueRange = -1f..3f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF4CAF50),
                                        activeTrackColor = Color(0xFF4CAF50),
                                        inactiveTrackColor = Color(0x40FFFFFF)
                                    )
                                )
                                Text(
                                    text = "Choose which port controls the lightgun (-1 = all ports)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Buttons for Radial - Keep Reset and Done
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = {
                                        radialScale = 0.5f 
                                        radialRotation = 0f
                                        radialMarginX = 0f
                                        radialMarginY = 0f
                                        radialSwapAnalogSticks = false
                                        radialInvertAnalogLeftY = false
                                        radialInvertAnalogRightY = false
                                        // Lightgun defaults (managed by AdvancedOverlaySettings logic)
                                        lightgunTriggerOnTouch = true
                                        lightgunTriggerDelay = 1
                                        lightgunAllowOffscreen = true
                                        lightgunPort = -1
                                    },
                                     colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                                ) { Text("Reset", color = Color.White) }
                                
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) { Text("Done", color = Color.White) }
                            }
                        }
                        1 -> {
                             // NEW Advanced Tab content for Radial
                            AdvancedVisibilitySettingsContent(
                                hideInMenu = hideInMenu,
                                onHideInMenuChanged = { hideInMenu = it },
                                behindMenu = behindMenu,
                                onBehindMenuChanged = { behindMenu = it },
                                hideWhenGamepad = hideWhenGamepad,
                                onHideWhenGamepadChanged = { hideWhenGamepad = it },
                                hideWhenGamepadPort0Only = hideWhenGamepadPort0Only,
                                onHideWhenGamepadPort0OnlyChanged = { hideWhenGamepadPort0Only = it }
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Done", color = Color.White) }
                            }
                        }
                        2 -> {
                            GeneralSettingsTabContent(
                                // ... (Keep existing General Settings logic)
                                prefs = prefs,
                                console = console,
                                debugModeState = debugModeState,
                                onShaderChanged = onShaderChanged,
                                onVsyncChanged = onVsyncChanged,
                                onAspectRatioChanged = onAspectRatioChanged,
                                onAudioVolumeChanged = onAudioVolumeChanged,
                                onAudioMuteChanged = onAudioMuteChanged,
                                onFastForwardRatioChanged = onFastForwardRatioChanged,
                                onRewindEnabledChanged = onRewindEnabledChanged,
                                onPsxAnalogModeChanged = onPsxAnalogModeChanged
                            )
                            Spacer(Modifier.height(16.dp))
                             Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) { Text("Done", color = Color.White) }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}


// RadialSlider extrait vers com.retroplay.ui.components.RadialSlider.kt

@Composable
private fun GeneralSettingsTabContent(
    prefs: android.content.SharedPreferences,
    console: String,
    debugModeState: androidx.compose.runtime.MutableState<Boolean>,
    onShaderChanged: (String) -> Unit,
    onVsyncChanged: (Boolean) -> Unit,
    onAspectRatioChanged: (String) -> Unit,
    onAudioVolumeChanged: (Float) -> Unit,
    onAudioMuteChanged: (Boolean) -> Unit,
    onFastForwardRatioChanged: (Float) -> Unit,
    onRewindEnabledChanged: (Boolean) -> Unit,
    onPsxAnalogModeChanged: ((Boolean) -> Unit)?
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // ========== SHADERS SECTION ==========
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Shaders",
            color = Color(0xFFFF9800),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        androidx.compose.material3.TextButton(
            onClick = {
                prefs.edit().putString("emulation_shader_preset", "DEFAULT").apply()
                onShaderChanged("DEFAULT")
            }
        ) {
            Text("Reset", color = Color(0xFF888888), fontSize = 12.sp)
        }
    }

    var selectedShaderName by remember { 
        mutableStateOf(
            prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT"
        )
    }
    var expandedShaderMenu by remember { mutableStateOf(false) }

    val availableShaders = com.retroplay.shader.ShaderManager.ShaderPreset.values()
    val currentShader = com.retroplay.shader.ShaderManager.fromString(selectedShaderName)

    Box {
        Button(
            onClick = { expandedShaderMenu = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A2A2A)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shader Preset: ${currentShader.displayName}",
                    color = Color.White
                )
                Text("▼", color = Color(0xFF888888))
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expandedShaderMenu,
            onDismissRequest = { expandedShaderMenu = false },
            modifier = Modifier.background(Color(0xFF1C1C1C))
        ) {
            availableShaders.forEach { shader ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { 
                        Text(
                            text = "${shader.icon} ${shader.displayName}",
                            color = if (shader == currentShader) Color(0xFFFF9800) else Color.White
                        )
                    },
                    onClick = {
                        selectedShaderName = shader.name
                        expandedShaderMenu = false
                        prefs.edit().putString("emulation_shader_preset", shader.name).apply()
                        onShaderChanged(shader.name)
                    }
                )
            }
        }
    }

    HorizontalDivider(color = Color(0x40FFFFFF))

    // ========== VIDEO SECTION ==========
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Video Settings",
            color = Color(0xFFFF9800),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        androidx.compose.material3.TextButton(
            onClick = {
                prefs.edit()
                    .putBoolean("emulation_video_vsync", true)
                    .putString("emulation_video_aspect_ratio", "AUTO")
                    .apply()
                // Synchroniser avec RetroPlayConfigManager
                // Note: GamePadSettingsDialog doesn't have console context, use default
                // This is legacy code, should use UnifiedSettingsDialog instead
                val config = com.retroplay.config.RetroPlayConfigManager.loadConfig("nes")  // Default fallback
                // Note: GamePadSettingsDialog doesn't have console context, use default
                com.retroplay.config.RetroPlayConfigManager.saveConfig("nes", config.copy(videoVsync = true))  // Default fallback
                onVsyncChanged(true)
            }
        ) {
            Text("Reset", color = Color(0xFF888888), fontSize = 12.sp)
        }
    }

    var vsyncEnabled by remember { 
        mutableStateOf(
            prefs.getBoolean("emulation_video_vsync", true)
        )
    }

    val vsyncIsDefault = vsyncEnabled == true

    SwitchRow(
        title = "VSync",
        subtitle = "Vertical synchronization (reduces screen tearing)",
        checked = vsyncEnabled,
        onCheckedChange = { 
            vsyncEnabled = it
            prefs.edit().putBoolean("emulation_video_vsync", it).apply()
            // Note: GamePadSettingsDialog doesn't have console context, use default
            val config = com.retroplay.config.RetroPlayConfigManager.loadConfig("nes")  // Default fallback
            com.retroplay.config.RetroPlayConfigManager.saveConfig("nes", config.copy(videoVsync = it))  // Default fallback
            onVsyncChanged(it)
        },
        showModifiedIndicator = !vsyncIsDefault
    )

    // Aspect Ratio
    var selectedAspectRatio by remember { 
        mutableStateOf(
            prefs.getString("emulation_video_aspect_ratio", "AUTO") ?: "AUTO"
        )
    }
    var expandedAspectRatioMenu by remember { mutableStateOf(false) }

    val aspectRatios = listOf(
        "AUTO" to "Auto (Core Default)",
        "4:3" to "4:3 (1.33:1)",
        "16:9" to "16:9 (1.78:1)",
        "16:10" to "16:10 (1.6:1)",
        "1:1" to "1:1 (Square)",
        "21:9" to "21:9 (2.33:1)"
    )

    val aspectRatioIsDefault = selectedAspectRatio == "AUTO"

    Box {
        Button(
            onClick = { expandedAspectRatioMenu = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (aspectRatioIsDefault) Color(0xFF2A2A2A) else Color(0xFF3A2A1A)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Aspect Ratio: ${aspectRatios.find { it.first == selectedAspectRatio }?.second ?: selectedAspectRatio}",
                        color = Color.White
                    )
                    if (!aspectRatioIsDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("●", color = Color(0xFFFF9800), fontSize = 8.sp)
                    }
                }
                Text("▼", color = Color(0xFF888888))
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expandedAspectRatioMenu,
            onDismissRequest = { expandedAspectRatioMenu = false },
            modifier = Modifier.background(Color(0xFF222222))
        ) {
            aspectRatios.forEach { (ratio, displayName) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { 
                        Text(
                            text = displayName,
                            color = if (ratio == selectedAspectRatio) Color(0xFFFF9800) else Color.White
                        )
                    },
                    onClick = {
                        selectedAspectRatio = ratio
                        expandedAspectRatioMenu = false
                        prefs.edit().putString("emulation_video_aspect_ratio", ratio).apply()
                        onAspectRatioChanged(ratio)
                    }
                )
            }
        }
    }

    HorizontalDivider(color = Color(0x40FFFFFF))

    // ========== AUDIO SECTION ==========
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Audio Settings",
            color = Color(0xFFFF9800),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        androidx.compose.material3.TextButton(
            onClick = {
                prefs.edit()
                    .putFloat("emulation_audio_volume", 1.0f)
                    .putBoolean("emulation_audio_muted", false)
                    .putBoolean("emulation_audio_low_latency", false)
                    .apply()
                onAudioVolumeChanged(1.0f)
                onAudioMuteChanged(false)
            }
        ) {
            Text("Reset", color = Color(0xFF888888), fontSize = 12.sp)
        }
    }

    var audioVolume by remember { 
        mutableStateOf(prefs.getFloat("emulation_audio_volume", 1.0f)) 
    }
    var audioMuted by remember { 
        mutableStateOf(prefs.getBoolean("emulation_audio_muted", false)) 
    }

    val volumeIsDefault = audioVolume == 1.0f

    Row(verticalAlignment = Alignment.CenterVertically) {
        SliderWithLabel(
            label = "Volume",
            value = audioVolume,
            onValueChange = { 
                audioVolume = it
                prefs.edit().putFloat("emulation_audio_volume", it).apply()
                onAudioVolumeChanged(it)
            },
            valueRange = 0.0f..1.0f,
            displayValue = "${(audioVolume * 100).toInt()}%",
            activeColor = Color(0xFF4CAF50)
        )
        if (!volumeIsDefault) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("●", color = Color(0xFFFF9800), fontSize = 8.sp)
        }
    }

    val muteIsDefault = !audioMuted

    SwitchRow(
        title = "Mute Audio",
        subtitle = "Disable all audio output",
        checked = audioMuted,
        onCheckedChange = { 
            audioMuted = it
            prefs.edit().putBoolean("emulation_audio_muted", it).apply()
            onAudioMuteChanged(it)
        },
        showModifiedIndicator = !muteIsDefault
    )

    var lowLatencyAudio by remember { 
        mutableStateOf(
            prefs.getBoolean("emulation_audio_low_latency", false)
        )
    }

    val lowLatencyIsDefault = !lowLatencyAudio

    SwitchRow(
        title = "Low Latency Audio",
        subtitle = "Use low latency audio mode (reduces audio delay, may increase battery usage)",
        checked = lowLatencyAudio,
        onCheckedChange = { 
            lowLatencyAudio = it
            prefs.edit().putBoolean("emulation_audio_low_latency", it).apply()
        },
        showModifiedIndicator = !lowLatencyIsDefault
    )

    HorizontalDivider(color = Color(0x40FFFFFF))

    // ========== ADVANCED SECTION ==========
    Text(
        text = "Advanced Settings",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    var fastForwardRatio by remember { 
        mutableStateOf(prefs.getFloat("emulation_fast_forward_ratio", 2.0f)) 
    }

    SliderWithLabel(
        label = "Fast Forward Ratio",
        value = fastForwardRatio,
        onValueChange = { 
            fastForwardRatio = it
            prefs.edit().putFloat("emulation_fast_forward_ratio", it).apply()
            onFastForwardRatioChanged(it)
        },
        valueRange = 1.0f..10.0f,
        displayValue = "${fastForwardRatio.toInt()}x",
        activeColor = Color(0xFFFF9800)
    )

    var rewindEnabled by remember { 
        mutableStateOf(
            prefs.getBoolean("emulation_rewind_enable", false)
        )
    }

    SwitchRow(
        title = "Rewind",
        subtitle = "Enable rewind functionality (requires buffer)",
        checked = rewindEnabled,
        onCheckedChange = { 
            rewindEnabled = it
            prefs.edit().putBoolean("emulation_rewind_enable", it).apply()
            // Note: GamePadSettingsDialog doesn't have console context, use default
            val config = com.retroplay.config.RetroPlayConfigManager.loadConfig("nes")  // Default fallback
            com.retroplay.config.RetroPlayConfigManager.saveConfig("nes", config.copy(rewindEnable = it))  // Default fallback
            onRewindEnabledChanged(it)
        }
    )

    SwitchRow(
        title = "Debug Mode",
        subtitle = "Show overlay debug information",
        checked = debugModeState.value,
        onCheckedChange = { debugModeState.value = it }
    )

    HorizontalDivider(color = Color(0x40FFFFFF))

    // Run-Ahead Settings
    var runAheadEnabled by remember { 
        mutableStateOf(
            prefs.getBoolean("emulation_runahead_enabled", false)
        )
    }
    var runAheadFrames by remember { 
        mutableStateOf(
            prefs.getInt("emulation_runahead_frames", 1).toFloat()
        )
    }

    SwitchRow(
        title = "Run-Ahead",
        subtitle = "Reduce input lag by running N frames ahead (requires save state support)",
        checked = runAheadEnabled,
        onCheckedChange = { 
            runAheadEnabled = it
            prefs.edit().putBoolean("emulation_runahead_enabled", it).apply()
            // Note: GamePadSettingsDialog doesn't have console context, use default
            val config = com.retroplay.config.RetroPlayConfigManager.loadConfig("nes")  // Default fallback
            com.retroplay.config.RetroPlayConfigManager.saveConfig("nes", config.copy(runAheadEnabled = it))  // Default fallback
        }
    )

    if (runAheadEnabled) {
        SliderWithLabel(
            label = "Run-Ahead Frames",
            value = runAheadFrames,
            onValueChange = { 
                runAheadFrames = it
                prefs.edit().putInt("emulation_runahead_frames", it.toInt()).apply()
                // Note: GamePadSettingsDialog doesn't have console context, use default
                // This is legacy code, should use UnifiedSettingsDialog instead
                val config = com.retroplay.config.RetroPlayConfigManager.loadConfig("nes")  // Default fallback
                // Note: GamePadSettingsDialog doesn't have console context, use default
                com.retroplay.config.RetroPlayConfigManager.saveConfig("nes", config.copy(runAheadFrames = it.toInt()))  // Default fallback
            },
            valueRange = 1.0f..4.0f,
            displayValue = "${runAheadFrames.toInt()} frames",
            activeColor = Color(0xFF4CAF50)
        )

        Text(
            text = "Recommended: 1-2 frames for most games, 3-4 for fighting games",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic
        )
    }

    HorizontalDivider(color = Color(0x40FFFFFF))

    // ========== CONSOLE-SPECIFIC SETTINGS ==========
    if (console.equals("psx", ignoreCase = true)) {
        Text(
            text = "PlayStation Settings",
            color = Color(0xFFFF9800),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        var psxAnalogMode by remember { 
            mutableStateOf(
                prefs.getBoolean("psx_analog_mode_enabled", true)
            )
        }

        SwitchRow(
            title = "Enable Analog Mode (DualShock)",
            subtitle = "Activate analog sticks on PSX controller (like pressing ANALOG button on DualShock)",
            checked = psxAnalogMode,
            onCheckedChange = { 
                psxAnalogMode = it
                prefs.edit().putBoolean("psx_analog_mode_enabled", it).apply()
                onPsxAnalogModeChanged?.invoke(it)
                android.widget.Toast.makeText(
                    context,
                    if (it) "Analog mode ON - Applied immediately!" else "Analog mode OFF - Applied immediately!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )

        Text(
            text = "When enabled, analog sticks will work in games that support them (Ape Escape, Medal of Honor, Crash Team Racing, etc.). Changes apply immediately!",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )

        HorizontalDivider(color = Color(0x40FFFFFF))
    }

    // ========== INPUT SETTINGS (HOTKEYS) SECTION ==========
    Text(
        text = "Input Settings (Hotkeys)",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Hotkeys are configured in overlay .cfg files. Available hotkeys:",
        color = Color(0xFF888888),
        fontSize = 12.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    val hotkeys = listOf(
        "Save State" to "save_state",
        "Load State" to "load_state",
        "State Slot +" to "state_slot_increase",
        "State Slot -" to "state_slot_decrease",
        "Fast Forward" to "toggle_fast_forward",
        "Rewind" to "rewind",
        "Reset" to "reset",
        "Pause Toggle" to "pause_toggle",
        "Audio Mute" to "audio_mute_toggle",
        "Shader Next" to "shader_next",
        "Shader Prev" to "shader_prev",
        "Screenshot" to "screenshot",
        "Frame Advance" to "frame_advance"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        hotkeys.forEach { (displayName, action) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = action,
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "✓",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Note: Hotkeys are triggered from overlay buttons. To configure hotkeys, edit the overlay .cfg file or use overlay buttons.",
        color = Color(0xFF666666),
        fontSize = 11.sp,
        fontStyle = FontStyle.Italic
    )

    HorizontalDivider(color = Color(0x40FFFFFF))

    Spacer(modifier = Modifier.height(8.dp))

    // ========== CONTROLLER PORTS CONFIGURATION ==========
    Text(
        text = "Controller Ports",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Configure controller type for each port. Manual configuration overrides auto-detection.",
        color = Color(0xFF888888),
        fontSize = 12.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    val controllerTypes = listOf(
        "Auto (Default)" to -1,
        "None" to 0,
        "Joypad" to 1,
        "Lightgun" to 4,
        "Pointer" to 6,
        "Zapper (NES)" to 258
    )

    for (port in 0..3) {
        var selectedControllerType by remember { 
            mutableStateOf(
                prefs.getInt("controller_port_${console}_port${port}", -1) // -1 = Auto
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Port ${port + 1}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            var expandedPortMenu by remember { mutableStateOf(false) }

            Box {
                androidx.compose.material3.TextButton(
                    onClick = { expandedPortMenu = true }
                ) {
                    Text(
                        text = controllerTypes.find { it.second == selectedControllerType }?.first ?: "Auto",
                        color = if (selectedControllerType == -1) Color(0xFF888888) else Color(0xFF00BCD4),
                        fontSize = 12.sp
                    )
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = expandedPortMenu,
                    onDismissRequest = { expandedPortMenu = false },
                    modifier = Modifier.background(Color(0xFF222222))
                ) {
                    controllerTypes.forEach { (name, id) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(name, color = Color.White) },
                            onClick = {
                                selectedControllerType = id
                                expandedPortMenu = false
                                prefs.edit().putInt("controller_port_${console}_port${port}", id).apply()
                            }
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Note: 'Auto' uses automatic detection. Manual settings override auto-detection for this console.",
        color = Color(0xFF666666),
        fontSize = 11.sp,
        fontStyle = FontStyle.Italic
    )
}
// ... (End of file)

