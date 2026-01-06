package com.retroplay.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.overlay.models.ShowInputsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Composable pour l'onglet Advanced de GamePadSettingsDialog
 * Extrait pour modularité et réutilisabilité
 */
@Composable
fun GamePadAdvancedSettings(
    console: String,
    prefs: android.content.SharedPreferences,
    orientation: String,
    useSameSettings: Boolean,
    dpadDiagonalSensitivity: Int,
    abxyDiagonalSensitivity: Int,
    analogRecenterZone: Int,
    opacity: Float,
    aspectAdjust: Float,
    showInputs: ShowInputsMode,
    showInputsPort: Int,
    hideInMenu: Boolean,
    behindMenu: Boolean,
    hideWhenGamepad: Boolean,
    hideWhenGamepadPort0Only: Boolean,
    lightgunPort: Int,
    lightgunTriggerOnTouch: Boolean,
    lightgunTriggerDelay: Int,
    lightgunAllowOffscreen: Boolean,
    mouseSpeed: Float,
    mouseSwipeThreshold: Float,
    mouseHoldToDrag: Boolean,
    mouseHoldMsec: Int,
    mouseDoubleTapToDrag: Boolean,
    mouseDtapMsec: Int,
    showMouseCursor: Boolean,
    onUseSameSettingsChanged: (Boolean) -> Unit,
    onDpadDiagonalSensitivityChanged: (Int) -> Unit,
    onAbxyDiagonalSensitivityChanged: (Int) -> Unit,
    onAnalogRecenterZoneChanged: (Int) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onAspectAdjustChanged: (Float) -> Unit,
    onShowInputsChanged: (ShowInputsMode) -> Unit,
    onShowInputsPortChanged: (Int) -> Unit,
    onHideInMenuChanged: (Boolean) -> Unit,
    onBehindMenuChanged: (Boolean) -> Unit,
    onHideWhenGamepadChanged: (Boolean) -> Unit,
    onHideWhenGamepadPort0OnlyChanged: (Boolean) -> Unit,
    onLightgunPortChanged: (Int) -> Unit,
    onLightgunTriggerOnTouchChanged: (Boolean) -> Unit,
    onLightgunTriggerDelayChanged: (Int) -> Unit,
    onLightgunAllowOffscreenChanged: (Boolean) -> Unit,
    onMouseSpeedChanged: (Float) -> Unit,
    onMouseSwipeThresholdChanged: (Float) -> Unit,
    onMouseHoldToDragChanged: (Boolean) -> Unit,
    onMouseHoldMsecChanged: (Int) -> Unit,
    onMouseDoubleTapToDragChanged: (Boolean) -> Unit,
    onMouseDtapMsecChanged: (Int) -> Unit,
    onShowMouseCursorChanged: (Boolean) -> Unit,
    onUseSameSettingsToggled: ((Boolean) -> Unit)? = null
) {
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
            onCheckedChange = { newValue ->
                onUseSameSettingsChanged(newValue)
                onUseSameSettingsToggled?.invoke(newValue)
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
            onValueChange = { onDpadDiagonalSensitivityChanged(it.toInt()) },
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
            onValueChange = { onAbxyDiagonalSensitivityChanged(it.toInt()) },
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
            onValueChange = { onAnalogRecenterZoneChanged(it.toInt()) },
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
            onValueChange = onOpacityChanged,
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
                onAspectAdjustChanged(if (abs(newValue) < 0.01f) 0.0f else newValue)
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
        ShowInputsMode.values().forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = showInputs == mode,
                    onClick = { onShowInputsChanged(mode) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFF9C27B0),
                        unselectedColor = Color(0xFF888888)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (mode) {
                        ShowInputsMode.NONE -> "None (default)"
                        ShowInputsMode.TOUCHED -> "Touched (touch only)"
                        ShowInputsMode.PHYSICAL -> "Physical (gamepad only)"
                        ShowInputsMode.BOTH -> "Both (touch + gamepad)"
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
    AdvancedVisibilitySettingsContent(
        hideInMenu = hideInMenu,
        onHideInMenuChanged = onHideInMenuChanged,
        behindMenu = behindMenu,
        onBehindMenuChanged = onBehindMenuChanged,
        hideWhenGamepad = hideWhenGamepad,
        onHideWhenGamepadChanged = onHideWhenGamepadChanged,
        hideWhenGamepadPort0Only = hideWhenGamepadPort0Only,
        onHideWhenGamepadPort0OnlyChanged = onHideWhenGamepadPort0OnlyChanged
    )
    
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
            onCheckedChange = onLightgunTriggerOnTouchChanged,
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
            onCheckedChange = onLightgunAllowOffscreenChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF9C27B0),
                checkedTrackColor = Color(0xFF9C27B0).copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
    }
    
    // Lightgun Port - REMOVED: Port is configured in Input tab (Controller Ports section)
    // The port is already set via controller_port_{console}_port{N} in SharedPreferences
    // This was redundant with the Input tab configuration
    
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
            onValueChange = { onLightgunTriggerDelayChanged(it.toInt()) },
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
            onValueChange = onMouseSpeedChanged,
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
            onValueChange = onMouseSwipeThresholdChanged,
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
            onCheckedChange = onMouseHoldToDragChanged,
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
                onValueChange = { onMouseHoldMsecChanged(it.toInt()) },
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
            onCheckedChange = onMouseDoubleTapToDragChanged,
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
                onValueChange = { onMouseDtapMsecChanged(it.toInt()) },
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
            onCheckedChange = onShowMouseCursorChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF9800),
                checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
    }
}

