package com.retroplay.ui.dialogs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.retroplay.ui.components.*
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
    onOpenAdvancedOverlaySettings: (() -> Unit)? = null,
    onPsxAnalogModeChanged: ((Boolean) -> Unit)? = null,
    onPreviewSettings: ((TouchControllerSettingsManager.Settings) -> Unit)? = null
) {
    // Migration logic
    LaunchedEffect(Unit) {
        val migrated = prefs.getBoolean("emulation_settings_migrated_from_cfg", false)
        if (!migrated) {
            try {
                val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                val editor = prefs.edit()
                if (!prefs.contains("emulation_video_vsync")) editor.putBoolean("emulation_video_vsync", config.videoVsync)
                if (!prefs.contains("emulation_rewind_enable")) editor.putBoolean("emulation_rewind_enable", config.rewindEnable)
                if (!prefs.contains("emulation_runahead_enabled")) editor.putBoolean("emulation_runahead_enabled", config.runAheadEnabled)
                if (!prefs.contains("emulation_runahead_frames")) editor.putInt("emulation_runahead_frames", config.runAheadFrames)
                editor.putBoolean("emulation_settings_migrated_from_cfg", true).apply()
            } catch (e: Exception) {
                android.util.Log.e("RetroArchSettingsDialog", "Migration failed: ${e.message}")
            }
        }
    }
    
    val assetManager = remember { OverlayAssetManager(context) }
    val overlayPackages = remember { assetManager.getCompatibleOverlays(console) }
    var customBrowsed by remember { mutableStateOf(OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()) }
    
    var availableLayoutsLoading by remember { mutableStateOf(false) }
    var availableLayoutsCache by remember { mutableStateOf<List<String>>(emptyList()) }

    val currentOverlayPref = remember { OverlayPreferenceManager.load(prefs, console) }
    var selectedOverlay by remember { mutableStateOf(currentOverlayPref?.overlayName ?: overlayPackages.firstOrNull().orEmpty()) }
    var selectedCustomPath by remember { mutableStateOf(currentOverlayPref?.customCfgName?.let { "${currentOverlayPref.overlayName}/$it" }) }
    var selectedLandscapeLayout by remember { mutableStateOf(currentOverlayPref?.landscapeLayout ?: "landscape-A") }
    var selectedPortraitLayout by remember { mutableStateOf(currentOverlayPref?.portraitLayout ?: "portrait-A") }
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
    var radialLightgunTriggerOnTouch by remember { mutableStateOf(radialSettings.lightgunTriggerOnTouch) }
    var radialLightgunTriggerDelay by remember { mutableIntStateOf(radialSettings.lightgunTriggerDelay) }
    var radialLightgunAllowOffscreen by remember { mutableStateOf(radialSettings.lightgunAllowOffscreen) }
    
    var isAdjusting by remember { mutableStateOf(false) }
    var isTransparent by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Haptic Feedback State (New)
    var hapticFeedback by remember { mutableStateOf(prefs.getBoolean("overlay_haptic_feedback", false)) }
    
    // ADVANCED OVERLAY SETTINGS STATE
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val orientation = if (isLandscape) "landscape" else "portrait"
    
    LaunchedEffect(console) {
        com.retroplay.overlay.models.OverlayPreferenceManager.migrateAdvancedSettingsToPerOrientation(prefs, console)
    }
    
    var useSameSettings by remember { 
        mutableStateOf(com.retroplay.overlay.models.OverlayPreferenceManager.checkIfSettingsAreSame(prefs, console))
    }
    
    var advancedSettings by remember(orientation, useSameSettings) { 
        mutableStateOf(
            if (useSameSettings) {
                com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                    ?: com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
            } else {
                com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
            }
        )
    }
    
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
    var lightgunTwoTouchInput by remember { mutableStateOf(advancedSettings.lightgunTwoTouchInput) }
    var lightgunThreeTouchInput by remember { mutableStateOf(advancedSettings.lightgunThreeTouchInput) }
    var lightgunFourTouchInput by remember { mutableStateOf(advancedSettings.lightgunFourTouchInput) }
    var mouseSpeed by remember { mutableStateOf(advancedSettings.mouseSpeed) }
    var mouseSwipeThreshold by remember { mutableStateOf(advancedSettings.mouseSwipeThreshold) }
    var mouseHoldToDrag by remember { mutableStateOf(advancedSettings.mouseHoldToDrag) }
    var mouseHoldMsec by remember { mutableStateOf(advancedSettings.mouseHoldMsec) }
    var mouseDoubleTapToDrag by remember { mutableStateOf(advancedSettings.mouseDoubleTapToDrag) }
    var mouseDtapMsec by remember { mutableStateOf(advancedSettings.mouseDtapMsec) }
    var showMouseCursor by remember { mutableStateOf(advancedSettings.showMouseCursor) }
    
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
        lightgunTwoTouchInput = newSettings.lightgunTwoTouchInput
        lightgunThreeTouchInput = newSettings.lightgunThreeTouchInput
        lightgunFourTouchInput = newSettings.lightgunFourTouchInput
        mouseSpeed = newSettings.mouseSpeed
        mouseSwipeThreshold = newSettings.mouseSwipeThreshold
        mouseHoldToDrag = newSettings.mouseHoldToDrag
        mouseHoldMsec = newSettings.mouseHoldMsec
        mouseDoubleTapToDrag = newSettings.mouseDoubleTapToDrag
        mouseDtapMsec = newSettings.mouseDtapMsec
        showMouseCursor = newSettings.showMouseCursor
    }
    
    LaunchedEffect(dpadDiagonalSensitivity, abxyDiagonalSensitivity, analogRecenterZone, opacity, aspectAdjust, hideInMenu, behindMenu, hideWhenGamepad, hideWhenGamepadPort0Only, showInputs, showInputsPort, lightgunPort, lightgunTriggerOnTouch, lightgunTriggerDelay, lightgunAllowOffscreen, lightgunTwoTouchInput, lightgunThreeTouchInput, lightgunFourTouchInput, mouseSpeed, mouseSwipeThreshold, mouseHoldToDrag, mouseHoldMsec, mouseDoubleTapToDrag, mouseDtapMsec, showMouseCursor, useSameSettings) {
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
            lightgunTwoTouchInput = lightgunTwoTouchInput,
            lightgunThreeTouchInput = lightgunThreeTouchInput,
            lightgunFourTouchInput = lightgunFourTouchInput,
            mouseSpeed = mouseSpeed,
            mouseSwipeThreshold = mouseSwipeThreshold,
            mouseHoldToDrag = mouseHoldToDrag,
            mouseHoldMsec = mouseHoldMsec,
            mouseDoubleTapToDrag = mouseDoubleTapToDrag,
            mouseDtapMsec = mouseDtapMsec,
            showMouseCursor = showMouseCursor
        )
        if (useSameSettings) {
            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", newSettings)
            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", newSettings)
        } else {
            com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, orientation, newSettings)
        }
        isTransparent = true
        kotlinx.coroutines.delay(2000)
        isTransparent = false
    }

    DisposableEffect(console) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "overlay_${console}_custom_browsed") customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
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

    LaunchedEffect(selectedOverlay, selectedCustomPath) {
        if (selectedOverlay.isNotEmpty()) {
            availableLayoutsLoading = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val customCfgName = selectedCustomPath?.substringAfter("/")
                val layouts = assetManager.getAvailableLayouts(selectedOverlay, console, customCfgName)
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
            if (landscapeLayouts.isNotEmpty() && !landscapeLayouts.contains(selectedLandscapeLayout)) selectedLandscapeLayout = landscapeLayouts.first()
            else if (landscapeLayouts.isEmpty() && !availableLayouts.contains(selectedLandscapeLayout)) selectedLandscapeLayout = availableLayouts.first()
            if (portraitLayouts.isNotEmpty() && !portraitLayouts.contains(selectedPortraitLayout)) selectedPortraitLayout = portraitLayouts.first()
            else if (portraitLayouts.isEmpty() && !availableLayouts.contains(selectedPortraitLayout)) selectedPortraitLayout = selectedLandscapeLayout
        }
    }

    LaunchedEffect(selectedOverlay, selectedCustomPath, selectedLandscapeLayout, selectedPortraitLayout, autoRotate, swapAnalogSticks, invertAnalogLeftY, invertAnalogRightY, scale, xOffset, yOffset, xSeparation, ySeparation) {
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

    LaunchedEffect(radialScale, radialRotation, radialMarginX, radialMarginY, radialLightgunTriggerOnTouch, radialLightgunTriggerDelay, radialLightgunAllowOffscreen) {
        PadKitHelper.saveSettings(prefs, console, TouchControllerSettingsManager.Settings(
            scale = radialScale,
            rotation = radialRotation,
            marginX = radialMarginX,
            marginY = radialMarginY,
            swapAnalogSticks = swapAnalogSticks,
            invertAnalogLeftY = invertAnalogLeftY,
            invertAnalogRightY = invertAnalogRightY,
            lightgunTriggerOnTouch = radialLightgunTriggerOnTouch,
            lightgunTriggerDelay = radialLightgunTriggerDelay,
            lightgunAllowOffscreen = radialLightgunAllowOffscreen
        ))
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
                    containerColor = if (isTransparent) Color(0xFF000000).copy(alpha = 0.3f) else Color(0xFF000000).copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "GamePad Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    
                    Text(
                        text = "Unified Settings",
                        fontSize = 12.sp,
                        color = Color(0xFFBBBBBB)
                    )

                    HorizontalDivider(color = Color(0xFF444444))
                    
                    // Unified Tabs (Always Visible)
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Touch Controls", color = if (selectedTab == 0) Color(0xFFFF9800) else Color(0xFF888888), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Emulation", color = if (selectedTab == 1) Color(0xFFFF9800) else Color(0xFF888888), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Graphics", color = if (selectedTab == 2) Color(0xFFFF9800) else Color(0xFF888888), fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                    
                    HorizontalDivider(color = Color(0xFF444444))
                    
                    when (selectedTab) {
                        0 -> {
                            // Mode Switcher
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
                                 ) { Text("RetroArch Overlay", color = Color.White) }
                                 
                                 Button(
                                     onClick = { onVariantChanged(GamePadLayoutManager.LayoutVariant.DEFAULT) },
                                     colors = ButtonDefaults.buttonColors(
                                         containerColor = if(currentVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) Color(0xFF2196F3) else Color.Transparent
                                     ),
                                     modifier = Modifier.weight(1f),
                                     shape = RoundedCornerShape(6.dp)
                                 ) { Text("Radial Menu", color = Color.White) }
                            }
                            
                            Spacer(Modifier.height(16.dp))

                            if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                                // === RETROARCH OVERLAY SETTINGS ===
                                Text("Overlay Selection", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                
                                    // Add "None/Disabled" option
                                    val overlayListPlusNone = listOf("None") + overlayPackages
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Standard Overlays
                                        overlayListPlusNone.forEach { overlayName ->
                                            val isSelected = selectedOverlay == overlayName && selectedCustomPath == null
                                            Card(
                                                modifier = Modifier.fillMaxWidth().clickable { 
                                                    selectedCustomPath = null; 
                                                    selectedOverlay = overlayName 
                                                },
                                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF2E7D32).copy(alpha = 0.5f) else Color(0xFF1C1C1C)),
                                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF4CAF50) else Color(0xFF2F2F2F))
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = isSelected, onClick = { selectedCustomPath = null; selectedOverlay = overlayName }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50), unselectedColor = Color(0xFF777777)))
                                                    Text(text = if(overlayName == "None") "Disabled (None)" else overlayName, color = if (isSelected) Color.White else Color(0xFFCCCCCC), fontSize = 14.sp)
                                                }
                                            }
                                        }

                                        // Custom Overlays (Browsed History)
                                        if (customBrowsed.isNotEmpty()) {
                                            HorizontalDivider(color = Color(0xFF333333))
                                            Text(
                                                text = "Custom Overlays",
                                                color = Color(0xFFFF9800),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(top=8.dp)
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
                                                        }
                                                        IconButton(onClick = {
                                                            OverlayPreferenceManager.removeCustomBrowsed(prefs, console, customPath)
                                                            customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
                                                            if (selectedCustomPath == customPath) {
                                                                selectedCustomPath = null
                                                                if (overlayPackages.isNotEmpty()) {
                                                                    selectedOverlay = overlayPackages.first()
                                                                } else {
                                                                    selectedOverlay = "None"
                                                                }
                                                            }
                                                        }) {
                                                            Text("X", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Browse Button
                                        Button(
                                            onClick = { onLoadCustomCfg?.invoke() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Browse Custom .cfg...", color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // Layout Variants (Restored FlowRow)
                                if (availableLayoutsCache.isNotEmpty()) {
                                    Text("Layout Selection", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    
                                    val landscapeLayouts = availableLayoutsCache.filter { it.contains("landscape", ignoreCase = true) }
                                    val portraitLayouts = availableLayoutsCache.filter { it.contains("portrait", ignoreCase = true) }

                                    if (landscapeLayouts.isNotEmpty()) {
                                        Text("Landscape Variants", color = Color(0xFFBBBBBB), fontSize = 13.sp)
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
                                    
                                    Spacer(Modifier.height(8.dp))
                                    
                                    if (portraitLayouts.isNotEmpty()) {
                                        Text("Portrait Variants", color = Color(0xFFBBBBBB), fontSize = 13.sp)
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
                                    
                                    Spacer(Modifier.height(8.dp))
                                    SwitchRow(title = "Auto-Rotate Overlay", subtitle = "Automatically switch layout based on screen orientation", checked = autoRotate, onCheckedChange = { autoRotate = it })
                                }

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFF444444))
                                Spacer(Modifier.height(16.dp))
                                
                                // Advanced Settings Integration (Collapsible)
                                ExpandableSettingsCard(
                                    title = "Advanced Overlay Settings",
                                    icon = Icons.Filled.Settings,
                                    onReset = {
                                        opacity = 0.7f; scale = 1.0f; aspectAdjust = 0.0f
                                        xOffset = 0.0f; yOffset = 0.0f; xSeparation = 0.0f; ySeparation = 0.0f
                                        dpadDiagonalSensitivity = 80; abxyDiagonalSensitivity = 50; analogRecenterZone = 0
                                        hideInMenu = true; behindMenu = false; hideWhenGamepad = false; hideWhenGamepadPort0Only = false
                                        showInputs = com.retroplay.overlay.models.ShowInputsMode.NONE; showInputsPort = 0
                                    }
                                ) {
                                    SwitchRow(
                                        title = "Use Same Settings for Both Orientations", 
                                        subtitle = "Apply changes to both Landscape and Portrait", 
                                        checked = useSameSettings, 
                                        onCheckedChange = { useSameSettings = it }
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    
                                    SliderWithLabel(label = "Opacity", value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f, displayValue = "${(opacity * 100).toInt()}%", activeColor = Color(0xFFE91E63))
                                    SliderWithLabel(label = "Scale", value = scale, onValueChange = { scale = it }, valueRange = 0.5f..1.5f, displayValue = String.format("%.2f", scale) + "x", activeColor = Color(0xFF4CAF50))
                                    SliderWithLabel(label = "X Offset (Horiz)", value = xOffset, onValueChange = { xOffset = it }, valueRange = -0.3f..0.3f, displayValue = String.format("%.3f", xOffset), activeColor = Color(0xFF2196F3))
                                    SliderWithLabel(label = "Y Offset (Vert)", value = yOffset, onValueChange = { yOffset = it }, valueRange = -0.3f..0.3f, displayValue = String.format("%.3f", yOffset), activeColor = Color(0xFF2196F3))
                                    
                                    SliderWithLabel(label = "X Separation", value = xSeparation, onValueChange = { xSeparation = it }, valueRange = -0.3f..0.3f, displayValue = String.format("%.3f", xSeparation), activeColor = Color(0xFFFF9800))
                                    SliderWithLabel(label = "Y Separation", value = ySeparation, onValueChange = { ySeparation = it }, valueRange = -0.3f..0.3f, displayValue = String.format("%.3f", ySeparation), activeColor = Color(0xFFFF9800))
                                    
                                    SliderWithLabel(label = "Aspect Adjust", value = aspectAdjust, onValueChange = { aspectAdjust = it }, valueRange = -0.5f..0.5f, displayValue = String.format("%.2f", aspectAdjust), activeColor = Color(0xFFE91E63))
                                    
                                    // Reset Geometry
                                    Row(modifier = Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { 
                                            scale = 1.0f; aspectAdjust = 0.0f
                                            xOffset = 0.0f; yOffset = 0.0f
                                            xSeparation = 0.0f; ySeparation = 0.0f
                                        }) {
                                            Text("Reset Geometry", color = Color(0xFFFF5252), fontSize = 12.sp)
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(4.dp))
                                    Text("Sensitivity", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                                    SliderWithLabel(label = "D-Pad Sensitivity", value = dpadDiagonalSensitivity.toFloat(), onValueChange = { dpadDiagonalSensitivity = it.toInt() }, valueRange = 0f..100f, displayValue = "$dpadDiagonalSensitivity%", activeColor = Color(0xFF4CAF50))
                                    SliderWithLabel(label = "ABXY Sensitivity", value = abxyDiagonalSensitivity.toFloat(), onValueChange = { abxyDiagonalSensitivity = it.toInt() }, valueRange = 0f..100f, displayValue = "$abxyDiagonalSensitivity%", activeColor = Color(0xFF4CAF50))
                                    SliderWithLabel(label = "Analog Recenter Zone", value = analogRecenterZone.toFloat(), onValueChange = { analogRecenterZone = it.toInt() }, valueRange = 0f..100f, displayValue = "$analogRecenterZone%", activeColor = Color(0xFF00BCD4))
                                    
                                    // Reset Sensitivity
                                    Row(modifier = Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { 
                                            dpadDiagonalSensitivity = 80
                                            abxyDiagonalSensitivity = 50
                                            analogRecenterZone = 0
                                        }) {
                                            Text("Reset Sensitivity", color = Color(0xFFFF5252), fontSize = 12.sp)
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(4.dp))
                                    Text("Behavior", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                                    SwitchRow(title = "Hide Overlay in Menu", subtitle = "Hide when RetroArch menu is open", checked = hideInMenu, onCheckedChange = { hideInMenu = it })
                                    SwitchRow(title = "Overlay Behind Menu", subtitle = "Render overlay behind menu", checked = behindMenu, onCheckedChange = { behindMenu = it })
                                    SwitchRow(title = "Hide When Gamepad Connected", subtitle = "Hide overlay when physical gamepad detected", checked = hideWhenGamepad, onCheckedChange = { hideWhenGamepad = it })
                                    if (hideWhenGamepad) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                                            SwitchRow(title = "Port 0 Only", subtitle = "Hide only if gamepad on Port 0", checked = hideWhenGamepadPort0Only, onCheckedChange = { hideWhenGamepadPort0Only = it })
                                        }
                                    }
                                    
                                    // Haptic Feedback
                                    SwitchRow(title = "Vibrate on Touch", subtitle = "Haptic feedback on button press", checked = hapticFeedback, onCheckedChange = { 
                                        hapticFeedback = it
                                        prefs.edit().putBoolean("overlay_haptic_feedback", it).apply()
                                    }, showModifiedIndicator = hapticFeedback)

                                    Spacer(Modifier.height(4.dp))
                                    Text("Display Inputs", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                                    
                                    val showInputsModes = com.retroplay.overlay.models.ShowInputsMode.values()
                                    var expandedShowInputs by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Button(
                                            onClick = { expandedShowInputs = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("Show Inputs: ${showInputs.name}", color = Color.White)
                                                Text("▼", color = Color.Gray)
                                            }
                                        }
                                        androidx.compose.material3.DropdownMenu(expanded = expandedShowInputs, onDismissRequest = { expandedShowInputs = false }) {
                                            showInputsModes.forEach { mode ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(mode.name) }, 
                                                    onClick = { showInputs = mode; expandedShowInputs = false }
                                                )
                                            }
                                        }
                                    }
                                    if (showInputs != com.retroplay.overlay.models.ShowInputsMode.NONE) {
                                         SliderWithLabel(label = "Show Inputs Port", value = showInputsPort.toFloat(), onValueChange = { showInputsPort = it.toInt() }, valueRange = 0f..3f, displayValue = "Port $showInputsPort", activeColor = Color(0xFFFF9800))
                                    }
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // LIGHTGUN (ZAPPER)
                                ExpandableSettingsCard(
                                    title = "Lightgun (Zapper)",
                                    icon = null,
                                    onReset = {
                                        lightgunTriggerOnTouch = true; lightgunAllowOffscreen = true
                                        lightgunPort = -1; lightgunTriggerDelay = 0
                                    }
                                ) {
                                    SwitchRow(title = "Trigger on Touch", subtitle = "Fire immediately on touch", checked = lightgunTriggerOnTouch, onCheckedChange = { lightgunTriggerOnTouch = it })
                                    SwitchRow(title = "Allow Offscreen", subtitle = "Allow shooting outside screen", checked = lightgunAllowOffscreen, onCheckedChange = { lightgunAllowOffscreen = it })
                                    SliderWithLabel(label = "Lightgun Port", value = lightgunPort.toFloat(), onValueChange = { lightgunPort = it.toInt() }, valueRange = -1f..3f, displayValue = if(lightgunPort == -1) "All Ports" else "Port $lightgunPort", activeColor = Color(0xFF00BCD4))
                                    SliderWithLabel(label = "Trigger Delay", value = lightgunTriggerDelay.toFloat(), onValueChange = { lightgunTriggerDelay = it.toInt() }, valueRange = 0f..500f, displayValue = "${lightgunTriggerDelay}ms", activeColor = Color(0xFFFFEB3B))
                                    
                                    // Multi-Touch Actions
                                    Text("Multi-Touch Actions", color = Color(0xFFBBBBBB), fontSize = 14.sp)
                                    LightgunInputSelector("2-Finger Action", lightgunTwoTouchInput) { lightgunTwoTouchInput = it }
                                    LightgunInputSelector("3-Finger Action", lightgunThreeTouchInput) { lightgunThreeTouchInput = it }
                                    LightgunInputSelector("4-Finger Action", lightgunFourTouchInput) { lightgunFourTouchInput = it }
                                }
                                
                                Spacer(Modifier.height(16.dp))

                                // MOUSE
                                ExpandableSettingsCard(
                                    title = "Mouse",
                                    icon = null,
                                    onReset = {
                                        showMouseCursor = false; mouseSpeed = 1.0f; mouseSwipeThreshold = 1.0f
                                        mouseHoldToDrag = true; mouseHoldMsec = 200
                                        mouseDoubleTapToDrag = false; mouseDtapMsec = 200
                                    }
                                ) {
                                    SwitchRow(title = "Show Mouse Cursor", subtitle = "Show cursor for mouse emulation", checked = showMouseCursor, onCheckedChange = { showMouseCursor = it })
                                    SliderWithLabel(label = "Mouse Speed", value = mouseSpeed, onValueChange = { mouseSpeed = it }, valueRange = 0.1f..5.0f, displayValue = String.format("%.1fx", mouseSpeed), activeColor = Color(0xFF00BCD4))
                                    SliderWithLabel(label = "Swipe Threshold", value = mouseSwipeThreshold, onValueChange = { mouseSwipeThreshold = it }, valueRange = 0.1f..10.0f, displayValue = String.format("%.1fpx", mouseSwipeThreshold), activeColor = Color(0xFFFFEB3B))
                                    
                                    SwitchRow(title = "Hold to Drag", subtitle = "Hold finger to activate drag mode", checked = mouseHoldToDrag, onCheckedChange = { mouseHoldToDrag = it })
                                    if (mouseHoldToDrag) {
                                        SliderWithLabel(label = "Hold Duration", value = mouseHoldMsec.toFloat(), onValueChange = { mouseHoldMsec = it.toInt() }, valueRange = 100f..2000f, displayValue = "${mouseHoldMsec}ms", activeColor = Color(0xFF4CAF50))
                                    }
                                    
                                    SwitchRow(title = "Double-Tap to Drag", subtitle = "Double-tap to toggle drag mode", checked = mouseDoubleTapToDrag, onCheckedChange = { mouseDoubleTapToDrag = it })
                                    if (mouseDoubleTapToDrag) {
                                        SliderWithLabel(label = "Double-Tap Timing", value = mouseDtapMsec.toFloat(), onValueChange = { mouseDtapMsec = it.toInt() }, valueRange = 50f..1000f, displayValue = "${mouseDtapMsec}ms", activeColor = Color(0xFF4CAF50))
                                    }
                                }
                                
                            } else {
                                // === RADIAL MENU SETTINGS ===
                                Text("Radial Menu Appearance", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                
                                val radialKey = "gamepad_${console}_settings"
                                
                                // Helper to construct and preview settings
                                fun preview() {
                                    val newSettings = TouchControllerSettingsManager.Settings(
                                        scale = radialScale,
                                        rotation = radialRotation,
                                        marginX = radialMarginX,
                                        marginY = radialMarginY,
                                        swapAnalogSticks = swapAnalogSticks,
                                        invertAnalogLeftY = invertAnalogLeftY,
                                        invertAnalogRightY = invertAnalogRightY,
                                        lightgunTriggerOnTouch = radialLightgunTriggerOnTouch,
                                        lightgunTriggerDelay = radialLightgunTriggerDelay,
                                        lightgunAllowOffscreen = radialLightgunAllowOffscreen
                                    )
                                    onPreviewSettings?.invoke(newSettings)
                                }

                                // Scale Slider removed by user request (11 Dec)
                                /*
                                SliderWithLabel(label = "Scale", value = radialScale, onValueChange = { 
                                    radialScale = it 
                                    prefs.edit().putFloat("${radialKey}_scale", it).apply()
                                    Log.d("GamePadSettings", "Writing scale $it to ${radialKey}_scale")
                                    preview()
                                }, valueRange = 0.5f..2.0f, displayValue = String.format("%.2f", radialScale) + "x", activeColor = Color(0xFF2196F3))
                                */
                                
                                SliderWithLabel(label = "Rotation", value = radialRotation, onValueChange = { 
                                    radialRotation = it 
                                    prefs.edit().putFloat("${radialKey}_rotation", it).apply()
                                    preview()
                                }, valueRange = 0f..1f, displayValue = "${(radialRotation * 360).toInt()}°", activeColor = Color(0xFF2196F3))
                                
                                SliderWithLabel(label = "Margin X", value = radialMarginX, onValueChange = { 
                                    radialMarginX = it 
                                    prefs.edit().putFloat("${radialKey}_marginX", it).apply()
                                    preview()
                                }, valueRange = 0f..0.5f, displayValue = "${(radialMarginX * 100).toInt()}%", activeColor = Color(0xFF2196F3))
                                
                                SliderWithLabel(label = "Margin Y", value = radialMarginY, onValueChange = { 
                                    radialMarginY = it 
                                    prefs.edit().putFloat("${radialKey}_marginY", it).apply()
                                    preview()
                                }, valueRange = 0f..0.5f, displayValue = "${(radialMarginY * 100).toInt()}%", activeColor = Color(0xFF2196F3))
                                
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFF444444))
                                Spacer(Modifier.height(16.dp))
                                
                                // ZAPPER RADIAL SECTION
                                Text("Lightgun (Zapper)", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                
                                SwitchRow(title = "Trigger on Touch", subtitle = "Fire immediately on touch", checked = radialLightgunTriggerOnTouch, onCheckedChange = { 
                                    radialLightgunTriggerOnTouch = it
                                    prefs.edit().putBoolean("${radialKey}_lightgun_trigger_on_touch", it).apply()
                                    preview()
                                })
                                SwitchRow(title = "Allow Offscreen", subtitle = "Allow shooting outside screen area", checked = radialLightgunAllowOffscreen, onCheckedChange = { 
                                    radialLightgunAllowOffscreen = it
                                    prefs.edit().putBoolean("${radialKey}_lightgun_allow_offscreen", it).apply()
                                    preview()
                                })
                                SliderWithLabel(label = "Trigger Delay", value = radialLightgunTriggerDelay.toFloat(), onValueChange = { 
                                    radialLightgunTriggerDelay = it.toInt()
                                    prefs.edit().putInt("${radialKey}_lightgun_trigger_delay", it.toInt()).apply()
                                    preview()
                                }, valueRange = 0f..500f, displayValue = "${radialLightgunTriggerDelay}ms", activeColor = Color(0xFFFFEB3B))
                            }
                            
                            // Shared Options (Analog Swap)
                            val radialKey = "gamepad_${console}_settings"
                            val overlayKey = "overlay_${console}"
                            
                            SwitchRow(title = "Swap Sticks", subtitle = "Swap Left/Right Analog", checked = swapAnalogSticks, onCheckedChange = { 
                                swapAnalogSticks = it 
                                prefs.edit().putBoolean("${radialKey}_swap", it).putBoolean("${overlayKey}_swap_analog_sticks", it).apply()
                            })
                            SwitchRow(title = "Invert Left Y", subtitle = "Invert Vertical Axis Left", checked = invertAnalogLeftY, onCheckedChange = { 
                                invertAnalogLeftY = it 
                                prefs.edit().putBoolean("${radialKey}_invertLeftY", it).putBoolean("${overlayKey}_invert_analog_left", it).apply()
                            })
                            SwitchRow(title = "Invert Right Y", subtitle = "Invert Vertical Axis Right", checked = invertAnalogRightY, onCheckedChange = { 
                                invertAnalogRightY = it
                                prefs.edit().putBoolean("${radialKey}_invertRightY", it).putBoolean("${overlayKey}_invert_analog_right", it).apply()
                            })
                        }
                        1 -> {
                            // Audio Settings
                            Text("Audio Settings", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            var audioVolume by remember { mutableStateOf(prefs.getFloat("emulation_audio_volume", 1.0f)) }
                            var audioMuted by remember { mutableStateOf(prefs.getBoolean("emulation_audio_muted", false)) }
                            var lowLatencyAudio by remember { mutableStateOf(prefs.getBoolean("emulation_audio_low_latency", false)) }
                            
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
                                    valueRange = 0f..1f,
                                    displayValue = "${(audioVolume*100).toInt()}%",
                                    activeColor = Color(0xFF4CAF50)
                                )
                                if (!volumeIsDefault) { Spacer(modifier = Modifier.width(8.dp)); Text("●", color = Color(0xFFFF9800), fontSize = 8.sp) }
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
                            
                            val lowLatencyIsDefault = !lowLatencyAudio
                            SwitchRow(
                                title = "Low Latency Audio",
                                subtitle = "Use low latency audio mode (reduces delay)",
                                checked = lowLatencyAudio,
                                onCheckedChange = { 
                                    lowLatencyAudio = it
                                    prefs.edit().putBoolean("emulation_audio_low_latency", it).apply()
                                },
                                showModifiedIndicator = !lowLatencyIsDefault
                            )

                            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))
                            
                            // Rewind & Run-Ahead
                            Text("Rewind & Run-Ahead", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            
                            var rewindEnabled by remember { mutableStateOf(prefs.getBoolean("emulation_rewind_enable", false)) }
                            SwitchRow(
                                title = "Rewind",
                                subtitle = "Enable rewind functionality",
                                checked = rewindEnabled,
                                onCheckedChange = { 
                                    rewindEnabled = it
                                    prefs.edit().putBoolean("emulation_rewind_enable", it).apply()
                                    // Sync Config
                                    try {
                                        val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                        com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(rewindEnable = it))
                                    } catch(e: Exception) {}
                                    onRewindEnabledChanged(it)
                                }
                            )
                            
                            var runAheadEnabled by remember { mutableStateOf(prefs.getBoolean("emulation_runahead_enabled", false)) }
                            var runAheadFrames by remember { mutableStateOf(prefs.getInt("emulation_runahead_frames", 1).toFloat()) }
                            
                            SwitchRow(
                                title = "Run-Ahead",
                                subtitle = "Reduce input lag by running N frames ahead (requires save state)",
                                checked = runAheadEnabled,
                                onCheckedChange = { 
                                    runAheadEnabled = it
                                    prefs.edit().putBoolean("emulation_runahead_enabled", it).apply()
                                    // Sync Config
                                    try {
                                        val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                        com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(runAheadEnabled = it))
                                    } catch(e: Exception) {}
                                }
                            )
                            
                            if (runAheadEnabled) {
                                SliderWithLabel(
                                    label = "Run-Ahead Frames",
                                    value = runAheadFrames,
                                    onValueChange = { 
                                        runAheadFrames = it
                                        prefs.edit().putInt("emulation_runahead_frames", it.toInt()).apply()
                                        // Sync Config
                                        try {
                                            val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                            com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(runAheadFrames = it.toInt()))
                                        } catch(e: Exception) {}
                                    },
                                    valueRange = 1f..10f,
                                    displayValue = "${runAheadFrames.toInt()} frames",
                                    activeColor = Color(0xFF4CAF50)
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))
                            
                            // Controller Ports
                            Text("Controller Ports", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Configure controller type for each port (Manual overrides auto)", color = Color(0xFF888888), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            
                            // Dynamic Controller Definitions based on Console
                            val controllerTypes = remember(console) {
                                when (console.lowercase()) {
                                    "nes" -> listOf(
                                        "Auto (Default)" to -1,
                                        "None" to 0,
                                        "Joypad" to 1,
                                        "Zapper (Lightgun)" to 258 // Keeping legacy ID 258 for FCEUmm
                                    )
                                    "snes" -> listOf(
                                        "Auto (Default)" to -1,
                                        "None" to 0,
                                        "Joypad" to 1,
                                        "Mouse" to 2,
                                        "SuperScope" to 260 // 260 for SuperScope often used
                                    )
                                    "psx" -> listOf(
                                        "Auto (Default)" to -1,
                                        "None" to 0,
                                        "Joypad (Digital)" to 1,
                                        "Analog (DualShock)" to 5,
                                        "Guncon" to 4,
                                        "Mouse" to 2
                                    )
                                    "genesis", "megadrive" -> listOf(
                                        "Auto (Default)" to -1,
                                        "None" to 0,
                                        "Joypad (3-Button)" to 1,
                                        "Menacer (Lightgun)" to 4
                                    )
                                    else -> listOf(
                                        "Auto (Default)" to -1,
                                        "None" to 0,
                                        "Joypad" to 1,
                                        "Lightgun" to 4,
                                        "Analog" to 5,
                                        "Mouse" to 2
                                    )
                                }
                            }
                            for (port in 0..3) {
                                // SYNCED KEY: Matches EmulationSettingsDialog and RetroArchEmulatorScreen
                                val portKey = "${console}_port${port+1}_device_type"
                                var selectedControllerType by remember { mutableStateOf(prefs.getInt(portKey, 1)) }
                                var expandedPortMenu by remember { mutableStateOf(false) }
                                
                                val selectedName = controllerTypes.find { it.second == selectedControllerType }?.first ?: "Joypad"
                                val isDefault = selectedControllerType == 1
                                
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Button(
                                        onClick = { expandedPortMenu = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isDefault) Color(0xFF2A2A2A) else Color(0xFF3A2A1A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Port ${port + 1}: $selectedName", color = Color.White)
                                            Text("▼", color = Color.Gray)
                                        }
                                    }
                                    androidx.compose.material3.DropdownMenu(expanded = expandedPortMenu, onDismissRequest = { expandedPortMenu = false }) {
                                        controllerTypes.forEach { (name, id) ->
                                            androidx.compose.material3.DropdownMenuItem(text = { Text(name) }, onClick = { selectedControllerType = id; expandedPortMenu = false; prefs.edit().putInt(portKey, id).apply() })
                                        }
                                    }
                                }
                            }
                            
                            
                            val psxVariants = listOf("psx", "playstation", "ps1", "pcsx_rearmed", "beetle_psx", "duckstation", "swanstation", "pcsx")
                            if (psxVariants.any { console.equals(it, ignoreCase = true) }) {
                                 Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))
                                 Text("PlayStation Settings", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                 var psxAnalogMode by remember { mutableStateOf(prefs.getBoolean("psx_analog_mode_enabled", true)) }
                                 SwitchRow(
                                     title = "Enable Analog Mode (DualShock)",
                                     subtitle = "Activate analog sticks on PSX controller",
                                     checked = psxAnalogMode,
                                     onCheckedChange = { 
                                         psxAnalogMode = it
                                         prefs.edit().putBoolean("psx_analog_mode_enabled", it).apply()
                                         onPsxAnalogModeChanged?.invoke(it)
                                     }
                                 )
                            }
                        }
                        2 -> {
                            // Video Settings
                            Text("Video Settings", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            var vsyncEnabled by remember { mutableStateOf(prefs.getBoolean("emulation_video_vsync", true)) }
                            val vsyncIsDefault = vsyncEnabled == true
                            SwitchRow(
                                title = "VSync",
                                subtitle = "Vertical Synchronization (reduces tearing)",
                                checked = vsyncEnabled,
                                onCheckedChange = { 
                                    vsyncEnabled = it
                                    prefs.edit().putBoolean("emulation_video_vsync", it).apply()
                                    // Sync with Config
                                    try {
                                        val config = com.retroplay.config.RetroPlayConfigManager.loadConfig()
                                        com.retroplay.config.RetroPlayConfigManager.saveConfig(config.copy(videoVsync = it))
                                    } catch(e: Exception) {}
                                    onVsyncChanged(it)
                                },
                                showModifiedIndicator = !vsyncIsDefault
                            )
                            
                            // Aspect Ratio
                            val aspectRatios = listOf(
                                "AUTO" to "Auto (Default)",
                                "CORE" to "Core Provided",
                                "4:3" to "4:3 (Traditional)",
                                "16:9" to "16:9 (Widescreen)",
                                "16:10" to "16:10 (Tablet)",
                                "18:9" to "18:9 (2:1)",
                                "19:9" to "19:9 (Mobile)",
                                "19.5:9" to "19.5:9 (Modern Mobile)",
                                "20:9" to "20:9 (Tall Mobile)",
                                "21:9" to "21:9 (Ultrawide)",
                                "1:1" to "1:1 (Square)"
                            )
                            var expandedAspectRatioMenu by remember { mutableStateOf(false) }
                            var selectedAspectRatio by remember { mutableStateOf(prefs.getString("emulation_video_aspect_ratio", "AUTO") ?: "AUTO") }
                            val aspectRatioIsDefault = selectedAspectRatio == "AUTO"
                            
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Button(
                                    onClick = { expandedAspectRatioMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (aspectRatioIsDefault) Color(0xFF2A2A2A) else Color(0xFF3A2A1A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Aspect Ratio: ${aspectRatios.find { it.first == selectedAspectRatio }?.second ?: selectedAspectRatio}", color = Color.White)
                                        Text("▼", color = Color.Gray)
                                    }
                                }
                                androidx.compose.material3.DropdownMenu(expanded = expandedAspectRatioMenu, onDismissRequest = { expandedAspectRatioMenu = false }) {
                                    aspectRatios.forEach { (ratio, name) ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text(name) }, onClick = { selectedAspectRatio = ratio; expandedAspectRatioMenu = false; prefs.edit().putString("emulation_video_aspect_ratio", ratio).apply(); onAspectRatioChanged(ratio) })
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFF444444))
                            Spacer(Modifier.height(16.dp))
                            
                            // Shaders
                            Text("Shaders", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            var expandedShaderMenu by remember { mutableStateOf(false) }
                            var selectedShaderName by remember { mutableStateOf(prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT") }
                            val availableShaders = com.retroplay.shader.ShaderManager.ShaderPreset.values()
                            val currentShader = com.retroplay.shader.ShaderManager.fromString(selectedShaderName)
                            
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Button(
                                    onClick = { expandedShaderMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Shader: ${currentShader.displayName}", color = Color.White)
                                        Text("▼", color = Color.Gray)
                                    }
                                }
                                androidx.compose.material3.DropdownMenu(expanded = expandedShaderMenu, onDismissRequest = { expandedShaderMenu = false }) {
                                    availableShaders.forEach { shader ->
                                        androidx.compose.material3.DropdownMenuItem(text = { Text("${shader.icon} ${shader.displayName}") }, onClick = { selectedShaderName = shader.name; expandedShaderMenu = false; prefs.edit().putString("emulation_shader_preset", shader.name).apply(); onShaderChanged(shader.name) })
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF444444))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
                                    if (overlayPackages.isNotEmpty()) {
                                        val defaultOverlay = overlayPackages.first()
                                        selectedOverlay = defaultOverlay
                                        selectedCustomPath = null
                                        scale = 1.0f
                                    }
                                } else {
                                    radialScale = 0.5f
                                    radialRotation = 0f
                                    radialMarginX = 0f
                                    radialMarginY = 0f
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Text("Reset Defaults", color = Color.White)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun layoutDisplayName(name: String): String {
    return name.replace("landscape-", "L-", ignoreCase = true)
               .replace("portrait-", "P-", ignoreCase = true)
}

private val lightgunActionLabels = mapOf(
    0 to "None",
    2 to "Start",
    1 to "Select",
    15 to "Button A",
    16 to "Button B",
    5 to "Menu Toggle",
    6 to "Gun Aux A",
    7 to "Gun Aux B",
    8 to "Gun Aux C"
)

@Composable
private fun LightgunInputSelector(label: String, currentValue: Int, onValueChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(lightgunActionLabels[currentValue] ?: "Unknown ($currentValue)", color = Color.White, fontSize = 12.sp)
            }
            androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                lightgunActionLabels.forEach { (key, name) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onValueChange(key); expanded = false }
                    )
                }
            }
        }
    }
}
