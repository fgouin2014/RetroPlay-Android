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
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape

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
                val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
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
    
    // Detect optimization
    var isAdjusting by remember { mutableStateOf(false) }

    var isTransparent by remember { mutableStateOf(false) }
    var showAdvancedRadialSettings by remember { mutableStateOf(false) }
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
    LaunchedEffect(radialScale, radialRotation, radialMarginX, radialMarginY) {
        PadKitHelper.saveSettings(prefs, console, TouchControllerSettingsManager.Settings(
            scale = radialScale,
            rotation = radialRotation,
            marginX = radialMarginX,
            marginY = radialMarginY,
            swapAnalogSticks = swapAnalogSticks,
            invertAnalogLeftY = invertAnalogLeftY,
            invertAnalogRightY = invertAnalogRightY
        ))
    }


    // Advanced Radial Settings Dialog
    if (showAdvancedRadialSettings) {
        AdvancedRadialSettingsDialog(
            console = console,
            initialSettings = TouchControllerSettingsManager.Settings(
                scale = radialScale,
                rotation = radialRotation,
                marginX = radialMarginX,
                marginY = radialMarginY,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogLeftY = invertAnalogLeftY,
                invertAnalogRightY = invertAnalogRightY
            ),
            prefs = prefs,
            onPreview = { previewSettings ->
                radialScale = previewSettings.scale
                radialRotation = previewSettings.rotation
                radialMarginX = previewSettings.marginX
                radialMarginY = previewSettings.marginY
                swapAnalogSticks = previewSettings.swapAnalogSticks
                invertAnalogLeftY = previewSettings.invertAnalogLeftY
                invertAnalogRightY = previewSettings.invertAnalogRightY
            },
            onSave = { newSettings ->
                radialScale = newSettings.scale
                radialRotation = newSettings.rotation
                radialMarginX = newSettings.marginX
                radialMarginY = newSettings.marginY
                swapAnalogSticks = newSettings.swapAnalogSticks
                invertAnalogLeftY = newSettings.invertAnalogLeftY
                invertAnalogRightY = newSettings.invertAnalogRightY
                showAdvancedRadialSettings = false
            },
            onCancel = { showAdvancedRadialSettings = false }
        )
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
                        Color(0xFF000000).copy(alpha = 0.4f)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF333333), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                         Button(
                             onClick = { onVariantChanged(GamePadLayoutManager.LayoutVariant.RETROARCH) },
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = if(currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) Color(0xFF4CAF50) else Color.Transparent
                             ),
                             modifier = Modifier.weight(1f),
                             shape = RoundedCornerShape(6.dp)
                         ) { Text("RetroArch", color = Color.White) }
                         
                         Button(
                             onClick = { onVariantChanged(GamePadLayoutManager.LayoutVariant.DEFAULT) },
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = if(currentVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) Color(0xFF2196F3) else Color.Transparent
                             ),
                             modifier = Modifier.weight(1f),
                             shape = RoundedCornerShape(6.dp)
                         ) { Text("Radial Menu", color = Color.White) }
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
                            // ========== ONGLET 1: OVERLAYS ==========
                    
                    // ========== SECTION: GAMEPAD OVERLAYS ==========
                    Text(
                        text = "GamePad Overlays",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    Text(
                        text = "Note: For advanced overlay settings (sensitivity, opacity, lightgun, mouse), see 'Advanced Overlay Settings' in the main menu",
                        fontSize = 10.sp,
                        color = Color(0xFF666666),
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Overlay Package",
                            color = Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (overlayPackages.isEmpty() && customBrowsed.isEmpty()) {
                            Text(
                                text = "No overlays available for this console.",
                                color = Color(0xFF888888),
                                fontSize = 13.sp
                            )
                        } else {
                            overlayPackages.forEach { overlayName ->
                                val isSelected = selectedOverlay == overlayName && selectedCustomPath == null
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCustomPath = null
                                            selectedOverlay = overlayName
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFF1C1C1C)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF4CAF50) else Color(0xFF2F2F2F))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedCustomPath = null
                                                selectedOverlay = overlayName
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF4CAF50),
                                                unselectedColor = Color(0xFF777777)
                                            )
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = overlayName,
                                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "Official RetroArch overlay",
                                                color = Color(0xFF777777),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (customBrowsed.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFF333333))
                            Text(
                                text = "Custom Overlays (File Picker)",
                                color = Color(0xFFFF9800),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            customBrowsed.forEach { customPath ->
                                val overlayName = customPath.substringBefore("/")
                                val isSelected = selectedCustomPath == customPath
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCustomPath = customPath
                                            selectedOverlay = overlayName
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF311B92).copy(alpha = 0.45f) else Color(0xFF1C1C1C)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF9575CD) else Color(0xFF2F2F2F))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedCustomPath = customPath
                                                selectedOverlay = overlayName
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF9575CD),
                                                unselectedColor = Color(0xFF777777)
                                            )
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = customPath,
                                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Imported from storage",
                                                color = Color(0xFF777777),
                                                fontSize = 11.sp
                                            )
                                        }
                                        IconButton(onClick = {
                                            OverlayPreferenceManager.removeCustomBrowsed(prefs, console, customPath)
                                            customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
                                            if (selectedCustomPath == customPath) {
                                                selectedCustomPath = null
                                                if (overlayPackages.isNotEmpty()) {
                                                    selectedOverlay = overlayPackages.first()
                                                } else {
                                                    selectedOverlay = ""
                                                }
                                            }
                                        }) {
                                            Text(
                                                text = "X",
                                                color = Color(0xFFFF5252),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (onLoadCustomCfg != null) {
                            TextButton(
                                onClick = onLoadCustomCfg,
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = Color(0xFF2196F3).copy(alpha = 0.15f)
                                )
                            ) {
                                Text("Load Custom .cfg", color = Color(0xFF90CAF9))
                            }
                        }
                    }

                    if (selectedOverlay.isNotEmpty() && availableLayouts.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF444444))
                        Text(
                            text = "Layout Selection",
                            color = Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        val landscapeLayouts = availableLayouts.filter { it.contains("landscape", ignoreCase = true) }
                        val portraitLayouts = availableLayouts.filter { it.contains("portrait", ignoreCase = true) }

                        if (landscapeLayouts.isNotEmpty()) {
                            Text("Landscape (${landscapeLayouts.size} layouts)", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                landscapeLayouts.forEach { layoutName ->
                                    val isSelected = selectedLandscapeLayout == layoutName
                                    TextButton(
                                        onClick = { selectedLandscapeLayout = layoutName },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF2A2A2A)
                                        ),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFC107) else Color(0xFF3A3A3A)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = layoutDisplayName(layoutName),
                                            color = if (isSelected) Color.Black else Color(0xFFBDBDBD),
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        if (portraitLayouts.isNotEmpty()) {
                            Text("Portrait (${portraitLayouts.size} layouts)", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                portraitLayouts.forEach { layoutName ->
                                    val isSelected = selectedPortraitLayout == layoutName
                                    TextButton(
                                        onClick = { selectedPortraitLayout = layoutName },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF2A2A2A)
                                        ),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFC107) else Color(0xFF3A3A3A)),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = layoutDisplayName(layoutName),
                                            color = if (isSelected) Color.Black else Color(0xFFBDBDBD),
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = autoRotate,
                                onCheckedChange = { autoRotate = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF4CAF50),
                                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color(0xFF777777),
                                    uncheckedTrackColor = Color(0xFF444444)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Auto-switch on rotation", color = Color.White, fontSize = 13.sp)
                                Text("Switch layouts with device orientation", color = Color(0xFF888888), fontSize = 11.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF444444))

                    Text(
                        text = "Analog Options",
                        color = Color(0xFFFF9800),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    SwitchRow(
                        title = "Swap Left/Right Sticks",
                        subtitle = "Exchange left and right analog positions",
                        checked = swapAnalogSticks,
                        onCheckedChange = { swapAnalogSticks = it }
                    )

                    SwitchRow(
                        title = "Invert Left Stick Y",
                        subtitle = "Reverse up/down movement for left analog",
                        checked = invertAnalogLeftY,
                        onCheckedChange = { invertAnalogLeftY = it }
                    )

                    SwitchRow(
                        title = "Invert Right Stick Y",
                        subtitle = "Reverse up/down movement for right analog",
                        checked = invertAnalogRightY,
                        onCheckedChange = { invertAnalogRightY = it }
                    )

                    HorizontalDivider(color = Color(0xFF444444))
                    Text("Position & Scale", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SliderWithLabel(
                            label = "Scale",
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.5f..1.5f,
                            displayValue = "${String.format("%.2f", scale)}x",
                            activeColor = Color(0xFF4CAF50)
                        )
                        SliderWithLabel(
                            label = "X Offset",
                            value = xOffset,
                            onValueChange = { xOffset = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", xOffset),
                            activeColor = Color(0xFF2196F3)
                        )
                        SliderWithLabel(
                            label = "Y Offset",
                            value = yOffset,
                            onValueChange = { yOffset = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", yOffset),
                            activeColor = Color(0xFFE91E63)
                        )
                        SliderWithLabel(
                            label = "X Separation",
                            value = xSeparation,
                            onValueChange = { xSeparation = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", xSeparation),
                            activeColor = Color(0xFF00BCD4)
                        )
                        SliderWithLabel(
                            label = "Y Separation",
                            value = ySeparation,
                            onValueChange = { ySeparation = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", ySeparation),
                            activeColor = Color(0xFF9C27B0)
                        )
                    }
                    }
                        1 -> {
                            // ========== ONGLET 2: ADVANCED OVERLAY SETTINGS ==========
                            // (Contenu complet intégré depuis AdvancedOverlaySettingsDialog)
                            
                            // Header
                            Text(
                                "Advanced Overlay Settings",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Current orientation indicator
                            Text(
                                "Current: ${orientation.replaceFirstChar { it.uppercaseChar() }}",
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Toggle: Use same settings for both orientations
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Use Same Settings for Both Orientations",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "When enabled, landscape and portrait share the same settings",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = useSameSettings,
                                    onCheckedChange = { 
                                        useSameSettings = it
                                        if (it) {
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
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF4CAF50),
                                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF444444))
                            Spacer(Modifier.height(8.dp))
                            
                            // === SENSITIVITY ===
                            Text(
                                "Sensitivity",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // D-Pad Diagonal Sensitivity
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("D-Pad Diagonal Sensitivity", color = Color.White, fontSize = 14.sp)
                                    Text("$dpadDiagonalSensitivity%", color = Color(0xFF4CAF50), fontSize = 14.sp)
                                }
                                Slider(
                                    value = dpadDiagonalSensitivity.toFloat(),
                                    onValueChange = { dpadDiagonalSensitivity = it.toInt() },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF4CAF50),
                                        activeTrackColor = Color(0xFF4CAF50),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Size of diagonal zones (0=cardinal only, 100=diagonal only)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // ABXY Diagonal Sensitivity
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ABXY Diagonal Sensitivity", color = Color.White, fontSize = 14.sp)
                                    Text("$abxyDiagonalSensitivity%", color = Color(0xFF2196F3), fontSize = 14.sp)
                                }
                                Slider(
                                    value = abxyDiagonalSensitivity.toFloat(),
                                    onValueChange = { abxyDiagonalSensitivity = it.toInt() },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF2196F3),
                                        activeTrackColor = Color(0xFF2196F3),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Size of diagonal zones for ABXY area",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // Analog Recenter Zone
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Analog Recenter Zone", color = Color.White, fontSize = 14.sp)
                                    Text("$analogRecenterZone%", color = Color(0xFFE91E63), fontSize = 14.sp)
                                }
                                Slider(
                                    value = analogRecenterZone.toFloat(),
                                    onValueChange = { analogRecenterZone = it.toInt() },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFE91E63),
                                        activeTrackColor = Color(0xFFE91E63),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Zone autour du centre où le stick se recentre sur le premier touch (0=centre fixe, 100=recentre partout)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF444444))
                            Spacer(Modifier.height(16.dp))
                            
                            // === VISUAL ===
                            Text(
                                "Visual",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Opacity
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Overlay Opacity", color = Color.White, fontSize = 14.sp)
                                    Text("${(opacity * 100).toInt()}%", color = Color(0xFFFFEB3B), fontSize = 14.sp)
                                }
                                Slider(
                                    value = opacity,
                                    onValueChange = { opacity = it },
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFEB3B),
                                        activeTrackColor = Color(0xFFFFEB3B),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Global transparency of overlay (0=invisible, 100=opaque)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // Aspect Adjust
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Aspect Adjust", color = Color.White, fontSize = 14.sp)
                                    Text(String.format("%.2f", aspectAdjust), color = Color(0xFF00BCD4), fontSize = 14.sp)
                                }
                                Slider(
                                    value = aspectAdjust,
                                    onValueChange = { newValue ->
                                        aspectAdjust = if (kotlin.math.abs(newValue) < 0.01f) 0.0f else newValue
                                    },
                                    valueRange = -0.5f..0.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00BCD4),
                                        activeTrackColor = Color(0xFF00BCD4),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Adjust aspect ratio compensation",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // Show Inputs (Radio buttons)
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Show Inputs", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Visual highlight of pressed buttons",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                
                                // Radio buttons pour les modes
                                com.retroplay.overlay.models.ShowInputsMode.values().forEach { mode ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = showInputs == mode,
                                            onClick = { showInputs = mode },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = Color(0xFF9C27B0),
                                                unselectedColor = Color(0xFF888888)
                                            )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = when (mode) {
                                                com.retroplay.overlay.models.ShowInputsMode.NONE -> "None (default)"
                                                com.retroplay.overlay.models.ShowInputsMode.TOUCHED -> "Touched (touch only)"
                                                com.retroplay.overlay.models.ShowInputsMode.PHYSICAL -> "Physical (gamepad only)"
                                                com.retroplay.overlay.models.ShowInputsMode.BOTH -> "Both (touch + gamepad)"
                                            },
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF444444))
                            Spacer(Modifier.height(16.dp))
                            
                            // === BEHAVIOR ===
                            Text(
                                "Behavior",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Hide in Menu
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Hide Overlay in Menu",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Hide overlay when in-game menu is open",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = hideInMenu,
                                    onCheckedChange = { hideInMenu = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF4CAF50),
                                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Behind Menu
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Overlay Behind Menu",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Render overlay behind menu (instead of hiding)",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = behindMenu,
                                    onCheckedChange = { behindMenu = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF2196F3),
                                        checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Hide When Gamepad Connected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Hide When Gamepad Connected",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Hide touch overlay when physical gamepad is detected",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = hideWhenGamepad,
                                    onCheckedChange = { hideWhenGamepad = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFF9800),
                                        checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Hide When Gamepad Connected - Port 0 Only (multijoueur)
                            if (hideWhenGamepad) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Port 0 Only (Multijoueur)",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Hide overlay only if gamepad on port 0. Allows player 2 to use overlay touch",
                                            color = Color(0xFF888888),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Switch(
                                        checked = hideWhenGamepadPort0Only,
                                        onCheckedChange = { hideWhenGamepadPort0Only = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFFFF9800),
                                            checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f),
                                            uncheckedThumbColor = Color(0xFF888888),
                                            uncheckedTrackColor = Color(0xFF444444)
                                        )
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF444444))
                            Spacer(Modifier.height(16.dp))
                            
                            // === LIGHTGUN ===
                            Text(
                                "Lightgun (Zapper)",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Trigger on Touch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Trigger on Touch",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Fire immediately on touch (vs on release)",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = lightgunTriggerOnTouch,
                                    onCheckedChange = { lightgunTriggerOnTouch = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFE91E63),
                                        checkedTrackColor = Color(0xFFE91E63).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Allow Offscreen
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Allow Offscreen Shots",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Allow shooting outside the game screen area",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = lightgunAllowOffscreen,
                                    onCheckedChange = { lightgunAllowOffscreen = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF9C27B0),
                                        checkedTrackColor = Color(0xFF9C27B0).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Lightgun Port
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Lightgun Port", color = Color.White, fontSize = 14.sp)
                                    Text("Port $lightgunPort", color = Color(0xFF00BCD4), fontSize = 14.sp)
                                }
                                Slider(
                                    value = lightgunPort.toFloat(),
                                    onValueChange = { lightgunPort = it.toInt() },
                                    valueRange = 0f..3f,
                                    steps = 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00BCD4),
                                        activeTrackColor = Color(0xFF00BCD4),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Controller port for lightgun (0-3)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // Trigger Delay
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Trigger Delay", color = Color.White, fontSize = 14.sp)
                                    Text("${lightgunTriggerDelay}ms", color = Color(0xFFFFEB3B), fontSize = 14.sp)
                                }
                                Slider(
                                    value = lightgunTriggerDelay.toFloat(),
                                    onValueChange = { lightgunTriggerDelay = it.toInt() },
                                    valueRange = 0f..500f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFEB3B),
                                        activeTrackColor = Color(0xFFFFEB3B),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Delay before trigger fires (milliseconds)",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF444444))
                            Spacer(Modifier.height(16.dp))
                            
                            // === MOUSE ===
                            Text(
                                "Mouse",
                                color = Color(0xFFFF9800),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Mouse Speed
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Mouse Speed", color = Color.White, fontSize = 14.sp)
                                    Text(String.format("%.1fx", mouseSpeed), color = Color(0xFF00BCD4), fontSize = 14.sp)
                                }
                                Slider(
                                    value = mouseSpeed,
                                    onValueChange = { mouseSpeed = it },
                                    valueRange = 0.1f..5.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00BCD4),
                                        activeTrackColor = Color(0xFF00BCD4),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Mouse movement speed multiplier",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // Swipe Threshold
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Swipe Threshold", color = Color.White, fontSize = 14.sp)
                                    Text(String.format("%.1fpx", mouseSwipeThreshold), color = Color(0xFFFFEB3B), fontSize = 14.sp)
                                }
                                Slider(
                                    value = mouseSwipeThreshold,
                                    onValueChange = { mouseSwipeThreshold = it },
                                    valueRange = 0.1f..10.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFEB3B),
                                        activeTrackColor = Color(0xFFFFEB3B),
                                        inactiveTrackColor = Color(0xFF444444)
                                    )
                                )
                                Text(
                                    "Minimum distance to register as swipe",
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp
                                )
                            }
                            
                            // Hold to Drag
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Hold to Drag",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Hold finger to activate drag mode",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = mouseHoldToDrag,
                                    onCheckedChange = { mouseHoldToDrag = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF4CAF50),
                                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Hold Duration
                            if (mouseHoldToDrag) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Hold Duration", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                                        Text("${mouseHoldMsec}ms", color = Color(0xFF4CAF50), fontSize = 13.sp)
                                    }
                                    Slider(
                                        value = mouseHoldMsec.toFloat(),
                                        onValueChange = { mouseHoldMsec = it.toInt() },
                                        valueRange = 100f..2000f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF4CAF50),
                                            activeTrackColor = Color(0xFF4CAF50),
                                            inactiveTrackColor = Color(0xFF444444)
                                        )
                                    )
                                    Text(
                                        "Time to hold before activating drag (ms)",
                                        color = Color(0xFF888888),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            
                            // Double-Tap to Drag
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Double-Tap to Drag",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Double-tap to toggle drag mode",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = mouseDoubleTapToDrag,
                                    onCheckedChange = { mouseDoubleTapToDrag = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF2196F3),
                                        checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                            
                            // Double-Tap Timing
                            if (mouseDoubleTapToDrag) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Double-Tap Timing", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                                        Text("${mouseDtapMsec}ms", color = Color(0xFF2196F3), fontSize = 13.sp)
                                    }
                                    Slider(
                                        value = mouseDtapMsec.toFloat(),
                                        onValueChange = { mouseDtapMsec = it.toInt() },
                                        valueRange = 100f..1000f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF2196F3),
                                            activeTrackColor = Color(0xFF2196F3),
                                            inactiveTrackColor = Color(0xFF444444)
                                        )
                                    )
                                    Text(
                                        "Max time between taps to register as double-tap (ms)",
                                        color = Color(0xFF888888),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            
                            // Show Mouse Cursor
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Show Mouse Cursor",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Display cursor for mouse emulation",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = showMouseCursor,
                                    onCheckedChange = { showMouseCursor = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFFFF9800),
                                        checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0xFF444444)
                                    )
                                )
                            }
                    }
                        2 -> {
                        // ========== ONGLET 3: RETROARCH GENERAL SETTINGS ==========
                            
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
                                TextButton(
                                    onClick = {
                                        // Reset Shader to default
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

                            HorizontalDivider(color = Color(0xFF444444))

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
                                TextButton(
                                    onClick = {
                                        // Reset Video Settings to defaults
                                        prefs.edit()
                                            .putBoolean("emulation_video_vsync", true)
                                            .putString("emulation_video_aspect_ratio", "AUTO")
                                            .apply()
                                        // Synchroniser avec RetroPlayConfigManager pour compatibilité
                                        val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                        com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(videoVsync = true))
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
                                    // Synchroniser avec RetroPlayConfigManager pour compatibilité
                                    val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                    com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(videoVsync = it))
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
                                    modifier = Modifier.background(Color(0xFF1C1C1C))
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

                            HorizontalDivider(color = Color(0xFF444444))

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
                                TextButton(
                                    onClick = {
                                        // Reset Audio Settings to defaults
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

                            HorizontalDivider(color = Color(0xFF444444))

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
                                    // Synchroniser avec RetroPlayConfigManager pour compatibilité
                                    val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                    com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(rewindEnable = it))
                                    onRewindEnabledChanged(it)
                                }
                            )
                            
                            SwitchRow(
                                title = "Debug Mode",
                                subtitle = "Show overlay debug information",
                                checked = debugModeState.value,
                                onCheckedChange = { debugModeState.value = it }
                            )
                            
                            HorizontalDivider(color = Color(0xFF444444))
                            
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
                                    // Synchroniser avec RetroPlayConfigManager pour compatibilité
                                    val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                    com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(runAheadEnabled = it))
                                }
                            )
                            
                            if (runAheadEnabled) {
                                SliderWithLabel(
                                    label = "Run-Ahead Frames",
                                    value = runAheadFrames,
                                    onValueChange = { 
                                        runAheadFrames = it
                                        prefs.edit().putInt("emulation_runahead_frames", it.toInt()).apply()
                                        // Synchroniser avec RetroPlayConfigManager pour compatibilité
                                        val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                        com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(runAheadFrames = it.toInt()))
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

                            HorizontalDivider(color = Color(0xFF444444))
                            
                            // ========== CONSOLE-SPECIFIC SETTINGS ==========
                            // PSX: Enable Analog Mode (DualShock)
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
                                        // Appliquer immédiatement si callback fourni
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
                                
                                HorizontalDivider(color = Color(0xFF444444))
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
                            
                            // Liste des hotkeys disponibles
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
                            
                            HorizontalDivider(color = Color(0xFF444444))
                            
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
                            
                            // Ports configuration (0-3)
                            // Note: getControllers() nécessite retroView, donc on utilise une liste statique pour l'instant
                            // Les IDs standards Libretro:
                            // 0 = None, 1 = Joypad, 4 = Lightgun, 6 = Pointer, 258 = Zapper (FCEUmm)
                            val controllerTypes = listOf(
                                "Auto (Default)" to -1,
                                "None" to 0,
                                "Joypad" to 1,
                                "Lightgun" to 4,
                                "Pointer" to 6,
                                "Zapper (NES)" to 258
                            )
                            
                            // Configuration pour chaque port (0-3)
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
                                        TextButton(
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
                                            onDismissRequest = { expandedPortMenu = false }
                                        ) {
                                            controllerTypes.forEach { (name, id) ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(name, color = Color.White) },
                                                    onClick = {
                                                        selectedControllerType = id
                                                        expandedPortMenu = false
                                                        // Sauvegarder la configuration
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
                    // Radial Settings UI
                    Text("Lemuroid Gamepad Adjustments", color = Color(0xFF2196F3), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    
                    Button(
                        onClick = { showAdvancedRadialSettings = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text("Advanced Settings", color = Color.White)
                    }

                    HorizontalDivider(color = Color(0xFF444444))
                    Spacer(Modifier.height(8.dp))
                    
                    // Scale (0.75x - 1.5x)
                    Text("Scale: ${String.format("%.2f", radialScale * 0.75f + 0.75f)}x", color = Color.LightGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Slider(
                        value = radialScale,
                        onValueChange = { 
                            radialScale = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Rotation (0° - 45°)
                    Text("Rotation: ${String.format("%.0f", radialRotation * 45f)}°", color = Color.LightGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Slider(
                        value = radialRotation,
                        onValueChange = { 
                            radialRotation = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Margin X (0dp - 96dp)
                    Text("Margin X: ${String.format("%.0f", radialMarginX * 96f)}dp", color = Color.LightGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Slider(
                        value = radialMarginX,
                        onValueChange = { 
                            radialMarginX = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Margin Y (0dp - 96dp)
                    Text("Margin Y: ${String.format("%.0f", radialMarginY * 96f)}dp", color = Color.LightGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Slider(
                        value = radialMarginY,
                        onValueChange = { 
                            radialMarginY = it
                            isAdjusting = true
                        },
                        onValueChangeFinished = { isAdjusting = false },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Buttons for Radial
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                radialScale = 0.5f // Default?
                                radialRotation = 0f
                                radialMarginX = 0f
                                radialMarginY = 0f
                                // And force saved? (handled by LaunchedEffect)
                            },
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) { Text("Reset", color = Color.White) }
                        
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


