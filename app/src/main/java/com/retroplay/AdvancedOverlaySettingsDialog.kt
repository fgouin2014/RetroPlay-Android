package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Advanced Overlay Settings Dialog
 * 
 * Options avancées pour les overlays RetroArch:
 * - Sensitivity (diagonal sensitivity, recenter zone)
 * - Visual (opacity, show inputs, aspect adjust)
 * - Behavior (hide in menu, behind menu, etc.)
 * - Lightgun options
 * - Mouse options
 */
@Composable
fun AdvancedOverlaySettingsDialog(
    console: String,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences
) {
    // Load current advanced settings
    var dpadDiagonalSensitivity by remember { mutableStateOf(prefs.getInt("overlay_${console}_dpad_diagonal_sensitivity", 50)) }
    var abxyDiagonalSensitivity by remember { mutableStateOf(prefs.getInt("overlay_${console}_abxy_diagonal_sensitivity", 50)) }
    var analogRecenterZone by remember { mutableStateOf(prefs.getInt("overlay_${console}_analog_recenter_zone", 0)) }
    
    var opacity by remember { mutableStateOf(prefs.getFloat("overlay_${console}_opacity", 1.0f)) }
    var aspectAdjust by remember { mutableStateOf(prefs.getFloat("overlay_${console}_aspect_adjust", 0.0f)) }
    
    var hideInMenu by remember { mutableStateOf(prefs.getBoolean("overlay_${console}_hide_in_menu", false)) }
    var behindMenu by remember { mutableStateOf(prefs.getBoolean("overlay_${console}_behind_menu", false)) }
    var hideWhenGamepad by remember { mutableStateOf(prefs.getBoolean("overlay_${console}_hide_when_gamepad", false)) }
    
    val showInputsString = remember { prefs.getString("overlay_${console}_show_inputs", "NONE") ?: "NONE" }
    var showInputs by remember { mutableStateOf(com.retroplay.overlay.models.ShowInputsMode.valueOf(showInputsString)) }
    var showInputsPort by remember { mutableStateOf(prefs.getInt("overlay_${console}_show_inputs_port", 0)) }
    
    // Lightgun options
    var lightgunTriggerOnTouch by remember { mutableStateOf(prefs.getBoolean("overlay_${console}_lightgun_trigger_on_touch", false)) }
    var lightgunAllowOffscreen by remember { mutableStateOf(prefs.getBoolean("overlay_${console}_lightgun_allow_offscreen", true)) }
    
    // Save when changed
    LaunchedEffect(dpadDiagonalSensitivity, abxyDiagonalSensitivity, analogRecenterZone, opacity, aspectAdjust, hideInMenu, behindMenu, hideWhenGamepad, showInputs, showInputsPort, lightgunTriggerOnTouch, lightgunAllowOffscreen) {
        prefs.edit()
            .putInt("overlay_${console}_dpad_diagonal_sensitivity", dpadDiagonalSensitivity)
            .putInt("overlay_${console}_abxy_diagonal_sensitivity", abxyDiagonalSensitivity)
            .putInt("overlay_${console}_analog_recenter_zone", analogRecenterZone)
            .putFloat("overlay_${console}_opacity", opacity)
            .putFloat("overlay_${console}_aspect_adjust", aspectAdjust)
            .putBoolean("overlay_${console}_hide_in_menu", hideInMenu)
            .putBoolean("overlay_${console}_behind_menu", behindMenu)
            .putBoolean("overlay_${console}_hide_when_gamepad", hideWhenGamepad)
            .putString("overlay_${console}_show_inputs", showInputs.name)
            .putInt("overlay_${console}_show_inputs_port", showInputsPort)
            .putBoolean("overlay_${console}_lightgun_trigger_on_touch", lightgunTriggerOnTouch)
            .putBoolean("overlay_${console}_lightgun_allow_offscreen", lightgunAllowOffscreen)
            .commit()
        android.util.Log.i("AdvancedOverlaySettings", "Saved for $console: dpadSens=$dpadDiagonalSensitivity abxySens=$abxyDiagonalSensitivity recenter=$analogRecenterZone opacity=$opacity")
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xDD000000)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    "Advanced Overlay Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
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
                            "Recenter analog sticks on first touch (0=no recenter, 100=always)",
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
                
                Spacer(Modifier.height(16.dp))
                
                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Close", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

