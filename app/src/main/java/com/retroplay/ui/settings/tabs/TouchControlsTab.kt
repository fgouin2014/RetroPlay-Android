package com.retroplay.ui.settings.tabs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import com.retroplay.GamePadLayoutManager
import com.retroplay.overlay.assets.OverlayAssetManager
import com.retroplay.overlay.models.OverlayPreference
import com.retroplay.overlay.models.OverlayPreferenceManager
import com.retroplay.overlay.models.AdvancedOverlaySettings
import com.retroplay.overlay.models.ShowInputsMode
import com.retroplay.input.PadKitHelper
import com.retroplay.ui.components.*
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TouchControlsTab(
    console: String,
    context: Context,
    prefs: SharedPreferences,
    currentVariant: GamePadLayoutManager.LayoutVariant,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onLoadCustomCfg: (() -> Unit)? = null,
    onPreviewSettings: ((TouchControllerSettingsManager.Settings) -> Unit)? = null,
    onSettingsChanged: () -> Unit = {}
) {
    // === Touch Controls ===
    
    // Choose Variant: RetroArch vs Radial
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val variants = GamePadLayoutManager.LayoutVariant.values()
        variants.forEach { variant ->
            val isSelected = currentVariant == variant
            Button(
                onClick = { onVariantChanged(variant) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF333333)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (variant == GamePadLayoutManager.LayoutVariant.RETROARCH) "RetroArch Overlay" else "Radial Menu",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }

    if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
        // === RETROARCH OVERLAY SETTINGS ===
        
        // Orientation & Advanced Settings State
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        val orientation = if (isLandscape) "landscape" else "portrait"

        LaunchedEffect(console) {
             OverlayPreferenceManager.migrateAdvancedSettingsToPerOrientation(prefs, console)
        }

        var useSameSettings by remember { 
             mutableStateOf(OverlayPreferenceManager.checkIfSettingsAreSame(prefs, console))
        }

        var advancedSettings by remember(orientation, useSameSettings) { 
             mutableStateOf(
                 if (useSameSettings) {
                     OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                         ?: OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
                 } else {
                     OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
                 }
             )
        }

        // Deconstruct Advanced Settings
        var dpadDiagonalSensitivity by remember { mutableIntStateOf(advancedSettings.dpadDiagonalSensitivity) }
        var abxyDiagonalSensitivity by remember { mutableIntStateOf(advancedSettings.abxyDiagonalSensitivity) }
        var analogRecenterZone by remember { mutableIntStateOf(advancedSettings.analogRecenterZone) }
        var opacity by remember { mutableFloatStateOf(advancedSettings.opacity) }
        var aspectAdjust by remember { mutableFloatStateOf(advancedSettings.aspectAdjust) }
        
        // Global/Legacy fields not in AdvancedOverlaySettings typically
        var scale by remember { mutableFloatStateOf(prefs.getFloat("overlay_scale", 1.0f)) } 
        if (scale == 0f) scale = 1.0f

        var xOffset by remember { mutableFloatStateOf(prefs.getFloat("overlay_x_offset", 0.0f)) }
        var yOffset by remember { mutableFloatStateOf(prefs.getFloat("overlay_y_offset", 0.0f)) }
        var xSeparation by remember { mutableFloatStateOf(prefs.getFloat("overlay_x_separation", 0.0f)) }
        var ySeparation by remember { mutableFloatStateOf(prefs.getFloat("overlay_y_separation", 0.0f)) }
        var autoRotate by remember { mutableStateOf(prefs.getBoolean("overlay_auto_rotate", true)) }
        
        // Other Advanced Fields
        var hideInMenu by remember { mutableStateOf(advancedSettings.hideInMenu) }
        var behindMenu by remember { mutableStateOf(advancedSettings.behindMenu) }
        var hideWhenGamepad by remember { mutableStateOf(advancedSettings.hideWhenGamepadConnected) }
        var hideWhenGamepadPort0Only by remember { mutableStateOf(advancedSettings.hideWhenGamepadConnectedPort0Only) }
        var showInputs by remember { mutableStateOf(advancedSettings.showInputs) }
        var showInputsPort by remember { mutableIntStateOf(advancedSettings.showInputsPort) }
        var lightgunPort by remember { mutableIntStateOf(advancedSettings.lightgunPort) }
        var lightgunTriggerOnTouch by remember { mutableStateOf(advancedSettings.lightgunTriggerOnTouch) }
        var lightgunTriggerDelay by remember { mutableIntStateOf(advancedSettings.lightgunTriggerDelay) }
        var lightgunAllowOffscreen by remember { mutableStateOf(advancedSettings.lightgunAllowOffscreen) }
        var lightgunTwoTouchInput by remember { mutableIntStateOf(advancedSettings.lightgunTwoTouchInput) }
        var lightgunThreeTouchInput by remember { mutableIntStateOf(advancedSettings.lightgunThreeTouchInput) }
        var lightgunFourTouchInput by remember { mutableIntStateOf(advancedSettings.lightgunFourTouchInput) }
        var mouseSpeed by remember { mutableFloatStateOf(advancedSettings.mouseSpeed) }
        var mouseSwipeThreshold by remember { mutableFloatStateOf(advancedSettings.mouseSwipeThreshold) }
        var mouseHoldToDrag by remember { mutableStateOf(advancedSettings.mouseHoldToDrag) }
        var mouseHoldMsec by remember { mutableIntStateOf(advancedSettings.mouseHoldMsec) }
        var mouseDoubleTapToDrag by remember { mutableStateOf(advancedSettings.mouseDoubleTapToDrag) }
        var mouseDtapMsec by remember { mutableIntStateOf(advancedSettings.mouseDtapMsec) }
        var showMouseCursor by remember { mutableStateOf(advancedSettings.showMouseCursor) }
        var hapticFeedback by remember { mutableStateOf(prefs.getBoolean("overlay_haptic_feedback", true)) }

        // Reload settings on orientation change
        LaunchedEffect(orientation, useSameSettings) {
             val newSettings = if (useSameSettings) {
                 OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                     ?: OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
             } else {
                 OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
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

        // Save Advanced Settings
        LaunchedEffect(dpadDiagonalSensitivity, abxyDiagonalSensitivity, analogRecenterZone, opacity, aspectAdjust, hideInMenu, behindMenu, hideWhenGamepad, hideWhenGamepadPort0Only, showInputs, showInputsPort, lightgunPort, lightgunTriggerOnTouch, lightgunTriggerDelay, lightgunAllowOffscreen, lightgunTwoTouchInput, lightgunThreeTouchInput, lightgunFourTouchInput, mouseSpeed, mouseSwipeThreshold, mouseHoldToDrag, mouseHoldMsec, mouseDoubleTapToDrag, mouseDtapMsec, showMouseCursor, useSameSettings) {
             val newSettings = AdvancedOverlaySettings(
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
                 OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", newSettings)
                 OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", newSettings)
             } else {
                 OverlayPreferenceManager.saveAdvancedSettings(prefs, console, orientation, newSettings)
             }
        }

        // Load Overlays List
        val assetManager = remember { OverlayAssetManager(context) }
        var overlayPackages by remember { mutableStateOf<List<String>>(emptyList()) }
        var customBrowsed by remember { mutableStateOf(prefs.getStringSet("overlay_custom_history", emptySet())?.toList() ?: emptyList()) }
        
        LaunchedEffect(Unit) {
            overlayPackages = assetManager.getCompatibleOverlays(console)
        }

        var selectedOverlay by remember { mutableStateOf(prefs.getString("selected_overlay_pkg_$console", "flat") ?: "flat") }
        var selectedCustomPath by remember { mutableStateOf(prefs.getString("selected_overlay_custom_path_$console", null)) }
        val isCustom = selectedOverlay == "custom"
        
        // Current Selected Layouts
        var selectedLandscapeLayout by remember { mutableStateOf(prefs.getString("selected_overlay_layout_landscape_$console", "landscape") ?: "landscape") }
        var selectedPortraitLayout by remember { mutableStateOf(prefs.getString("selected_overlay_layout_portrait_$console", "portrait") ?: "portrait") }
        
        // Layout Selection Logic
        var availableLayoutsCache by remember { mutableStateOf<List<String>>(emptyList()) }
        var availableLayoutsLoading by remember { mutableStateOf(false) }
        
        // OPTIMIZATION: Async load to avoid ANR on Main Thread (e.g. rgpad has 16+ layouts)
        LaunchedEffect(selectedOverlay, selectedCustomPath) {
            if (selectedOverlay.isNotEmpty()) {
                availableLayoutsLoading = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val customCfgName = if(selectedOverlay=="custom" && selectedCustomPath != null) selectedCustomPath!!.substringAfter("/") else null
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
        
        // Auto-Select Valid Layout if current becomes invalid
        LaunchedEffect(availableLayoutsCache, selectedLandscapeLayout) {
            if (availableLayoutsCache.isNotEmpty()) {
                val landscapeLayouts = availableLayoutsCache.filter { it.contains("landscape", ignoreCase = true) }
                val portraitLayouts = availableLayoutsCache.filter { it.contains("portrait", ignoreCase = true) }

                if (landscapeLayouts.isNotEmpty() && !landscapeLayouts.contains(selectedLandscapeLayout)) {
                    selectedLandscapeLayout = landscapeLayouts.first()
                } else if (landscapeLayouts.isEmpty() && !availableLayoutsCache.contains(selectedLandscapeLayout)) {
                    selectedLandscapeLayout = availableLayoutsCache.first()
                }
                
                // Also validate portrait
                 if (portraitLayouts.isNotEmpty() && !portraitLayouts.contains(selectedPortraitLayout)) {
                    selectedPortraitLayout = portraitLayouts.first()
                } else if (portraitLayouts.isEmpty() && !availableLayoutsCache.contains(selectedPortraitLayout)) {
                    selectedPortraitLayout = selectedLandscapeLayout
                }
            }
        }
        
        // Current Selected Layouts (Moved up)
        
        // Save Global/Overlay Preference
        LaunchedEffect(selectedOverlay, selectedLandscapeLayout, selectedPortraitLayout, opacity, scale, xOffset, yOffset, xSeparation, ySeparation, aspectAdjust, autoRotate, hapticFeedback, useSameSettings) {
            val pref = OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                scale = scale,
                autoRotate = autoRotate,
                customCfgName = selectedCustomPath,
                xOffset = xOffset, yOffset = yOffset,
                xSeparation = xSeparation, ySeparation = ySeparation
            )
            OverlayPreferenceManager.save(prefs, console, pref)
            
            prefs.edit().apply {
                putFloat("overlay_opacity", opacity)
                putFloat("overlay_scale", scale)
                putFloat("overlay_x_offset", xOffset)
                putFloat("overlay_y_offset", yOffset)
                putFloat("overlay_x_separation", xSeparation)
                putFloat("overlay_y_separation", ySeparation)
                putFloat("overlay_aspect_adjust", aspectAdjust)
                putBoolean("overlay_haptic_feedback", hapticFeedback)
                putBoolean("overlay_auto_rotate", autoRotate)
            }.apply()
            
            // Trigger Flash Effect
            onSettingsChanged()
        }

        // --- UI ---
        Text("Overlay Style", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            var expandedOverlayMenu by remember { mutableStateOf(false) }
            Button(
                onClick = { expandedOverlayMenu = true },
                colors = ButtonDefaults.buttonColors(containerColor = if (isCustom) Color(0xFF333333) else Color(0xFF2A2A2A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isCustom) "Custom .cfg" else selectedOverlay.uppercase(), color = Color.White)
                    Text("▼", color = Color.Gray)
                }
            }
            DropdownMenu(expanded = expandedOverlayMenu, onDismissRequest = { expandedOverlayMenu = false }) {
                overlayPackages.forEach { pkg ->
                    DropdownMenuItem(text = { Text(pkg.uppercase()) }, onClick = { selectedOverlay = pkg; selectedCustomPath = null; expandedOverlayMenu = false })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Browse Custom .cfg...", color = Color(0xFF64B5F6)) }, onClick = { 
                     expandedOverlayMenu = false
                     onLoadCustomCfg?.invoke()
                })
                
                if (customBrowsed.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Recent Custom:", modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = Color.Gray)
                    customBrowsed.forEach { path ->
                         DropdownMenuItem(
                             text = { Text(path.substringAfterLast("/"), fontSize=12.sp) }, 
                             onClick = { 
                                 selectedOverlay = "custom"
                                 selectedCustomPath = path
                                 expandedOverlayMenu = false
                             },
                             trailingIcon = {
                                 IconButton(onClick = { 
                                     val newHistory = customBrowsed - path
                                     prefs.edit().putStringSet("overlay_custom_history", newHistory.toSet()).apply()
                                     customBrowsed = newHistory
                                 }) { Icon(androidx.compose.material.icons.Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                             }
                         )
                    }
                }
            }
        }
        
        if (isCustom && selectedCustomPath != null) {
            Text(selectedCustomPath!!.substringAfterLast("/"), color = Color(0xFF4CAF50), fontSize = 12.sp, modifier = Modifier.padding(bottom=8.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Layout Variants
        val landscapeLayouts = availableLayoutsCache.filter { it.contains("landscape", ignoreCase=true) }.ifEmpty { if(availableLayoutsCache.isNotEmpty()) availableLayoutsCache else listOf("landscape") }
        val portraitLayouts = availableLayoutsCache.filter { it.contains("portrait", ignoreCase=true) }.ifEmpty { if(availableLayoutsCache.isNotEmpty()) availableLayoutsCache else listOf("portrait") }
        
        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF222222), RoundedCornerShape(8.dp)).padding(12.dp)) {
            Text("Layout Selection", color = Color(0xFFE0E0E0), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
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
            
            val showInputsModes = ShowInputsMode.values()
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
                DropdownMenu(expanded = expandedShowInputs, onDismissRequest = { expandedShowInputs = false }) {
                    showInputsModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) }, 
                            onClick = { showInputs = mode; expandedShowInputs = false }
                        )
                    }
                }
            }
            if (showInputs != ShowInputsMode.NONE) {
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
        
        // --- ADDED RESET BUTTON ---
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF444444))
        
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                     if (overlayPackages.isNotEmpty()) {
                         selectedOverlay = overlayPackages.first()
                         selectedCustomPath = null
                     }
                     scale = 1.0f
                     opacity = 0.7f
                     xOffset = 0.0f
                     yOffset = 0.0f
                     aspectAdjust = 0.0f
                     xSeparation = 0.0f
                     ySeparation = 0.0f
                     dpadDiagonalSensitivity = 80
                     abxyDiagonalSensitivity = 50
                     analogRecenterZone = 0
                     // ... reset others if needed
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
            ) {
                Text("Reset Tab Defaults", color = Color.White)
            }
        }
        
    } else {
        // === RADIAL MENU SETTINGS ===
        Text("Radial Menu Appearance", color = Color(0xFF2196F3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        
        val radialKey = "gamepad_${console}_settings"
        
        // Helper to construct and preview settings
        fun preview(
            scale: Float,
            rotation: Float,
            marginX: Float,
            marginY: Float,
            swap: Boolean,
            invertLeft: Boolean,
            invertRight: Boolean
        ) {
            val newSettings = TouchControllerSettingsManager.Settings(
                scale = scale,
                rotation = rotation,
                marginX = marginX,
                marginY = marginY,
                swapAnalogSticks = swap,
                invertAnalogLeftY = invertLeft,
                invertAnalogRightY = invertRight,
                lightgunTriggerOnTouch = prefs.getBoolean("${radialKey}_lightgun_trigger_on_touch", true),
                lightgunTriggerDelay = prefs.getInt("${radialKey}_lightgun_trigger_delay", 0),
                lightgunAllowOffscreen = prefs.getBoolean("${radialKey}_lightgun_allow_offscreen", false)
            )
            onPreviewSettings?.invoke(newSettings)
        }

        var radialScale by remember { mutableFloatStateOf(prefs.getFloat("${radialKey}_scale", 1.0f)) }
        var radialRotation by remember { mutableFloatStateOf(prefs.getFloat("${radialKey}_rotation", 0.0f)) }
        var radialMarginX by remember { mutableFloatStateOf(prefs.getFloat("${radialKey}_marginX", 0.0f)) }
        var radialMarginY by remember { mutableFloatStateOf(prefs.getFloat("${radialKey}_marginY", 0.0f)) }
        var radialSwap by remember { mutableStateOf(prefs.getBoolean("${radialKey}_swap", false)) }
        var radialInvertLeftY by remember { mutableStateOf(prefs.getBoolean("${radialKey}_invertLeftY", prefs.getBoolean("${radialKey}_invertY", false))) }
        var radialInvertRightY by remember { mutableStateOf(prefs.getBoolean("${radialKey}_invertRightY", prefs.getBoolean("${radialKey}_invertY", false))) }

        if (radialScale == 0f) { radialScale = 1.0f }

        // Slider for rotation, margins, etc. ...
        SliderWithLabel(label = "Rotation", value = radialRotation, onValueChange = { 
            radialRotation = it 
            prefs.edit().putFloat("${radialKey}_rotation", it).apply()
            preview(radialScale, it, radialMarginX, radialMarginY, radialSwap, radialInvertLeftY, radialInvertRightY)
        }, valueRange = -0.5f..0.5f, displayValue = String.format("%.0f°", radialRotation * 360f / 2f), activeColor = Color(0xFF4CAF50))
        
        SliderWithLabel(label = "Margin X", value = radialMarginX, onValueChange = { 
            radialMarginX = it 
            prefs.edit().putFloat("${radialKey}_marginX", it).apply()
            preview(radialScale, radialRotation, it, radialMarginY, radialSwap, radialInvertLeftY, radialInvertRightY)
        }, valueRange = 0f..1f, displayValue = String.format("%.2f", radialMarginX), activeColor = Color(0xFFFF9800))
        
        SliderWithLabel(label = "Margin Y", value = radialMarginY, onValueChange = { 
            radialMarginY = it 
            prefs.edit().putFloat("${radialKey}_marginY", it).apply()
            preview(radialScale, radialRotation, radialMarginX, it, radialSwap, radialInvertLeftY, radialInvertRightY)
        }, valueRange = 0f..1f, displayValue = String.format("%.2f", radialMarginY), activeColor = Color(0xFFFF9800))
        
        Spacer(Modifier.height(8.dp))
        SwitchRow(title = "Swap Analog Sticks", subtitle = "Swap left and right analog sticks", checked = radialSwap, onCheckedChange = { 
            radialSwap = it
            prefs.edit().putBoolean("${radialKey}_swap", it).apply()
             preview(radialScale, radialRotation, radialMarginX, radialMarginY, it, radialInvertLeftY, radialInvertRightY)
        })
        SwitchRow(title = "Invert Left Y Axis", subtitle = "Invert vertical axis for L-Stick", checked = radialInvertLeftY, onCheckedChange = { 
            radialInvertLeftY = it
            prefs.edit().putBoolean("${radialKey}_invertLeftY", it).apply()
            preview(radialScale, radialRotation, radialMarginX, radialMarginY, radialSwap, it, radialInvertRightY)
        })
        SwitchRow(title = "Invert Right Y Axis", subtitle = "Invert vertical axis for R-Stick", checked = radialInvertRightY, onCheckedChange = { 
            radialInvertRightY = it
            prefs.edit().putBoolean("${radialKey}_invertRightY", it).apply()
            preview(radialScale, radialRotation, radialMarginX, radialMarginY, radialSwap, radialInvertLeftY, it)
        })

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF444444))
        
        // Reset Button for Radial
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                     radialScale = 1.0f
                     radialRotation = 0f
                     radialMarginX = 0f
                     radialMarginY = 0f
                     preview(1.0f, 0f, 0f, 0f, radialSwap, radialInvertLeftY, radialInvertRightY)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
            ) {
                Text("Reset Tab Defaults", color = Color.White)
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
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
        }
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text(lightgunActionLabels[currentValue] ?: "Unknown ($currentValue)", color = Color.White, fontSize = 12.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                lightgunActionLabels.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onValueChange(id); expanded = false })
                }
            }
        }
    }
}
