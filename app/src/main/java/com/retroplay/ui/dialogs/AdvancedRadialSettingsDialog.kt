package com.retroplay.ui.dialogs

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.overlay.models.OverlayPreferenceManager
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.retroplay.ui.components.SwitchRow
import kotlin.math.roundToInt

@Composable
fun AdvancedRadialSettingsDialog(
    console: String,
    initialSettings: TouchControllerSettingsManager.Settings,
    prefs: SharedPreferences,
    onPreview: (TouchControllerSettingsManager.Settings) -> Unit,
    onSave: (TouchControllerSettingsManager.Settings) -> Unit,
    onCancel: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(initialSettings.scale) }
    var rotation by remember { mutableFloatStateOf(initialSettings.rotation) }
    var marginX by remember { mutableFloatStateOf(initialSettings.marginX) }
    var marginY by remember { mutableFloatStateOf(initialSettings.marginY) }
    var swapAnalogSticks by remember { mutableStateOf(initialSettings.swapAnalogSticks) }
    var invertAnalogLeftY by remember { mutableStateOf(initialSettings.invertAnalogLeftY) }
    var invertAnalogRightY by remember { mutableStateOf(initialSettings.invertAnalogRightY) }

    val advancedOverlay = remember(console) { OverlayPreferenceManager.loadAdvancedSettings(prefs, console) }
    var lightgunTriggerOnTouch by remember { mutableStateOf(advancedOverlay.lightgunTriggerOnTouch) }
    var lightgunTriggerDelay by remember { mutableIntStateOf(advancedOverlay.lightgunTriggerDelay) }
    var lightgunAllowOffscreen by remember { mutableStateOf(advancedOverlay.lightgunAllowOffscreen) }
    var lightgunPort by remember { mutableIntStateOf(advancedOverlay.lightgunPort) }

    LaunchedEffect(
        scale,
        rotation,
        marginX,
        marginY,
        swapAnalogSticks,
        invertAnalogLeftY,
        invertAnalogRightY
    ) {
        onPreview(
            TouchControllerSettingsManager.Settings(
                scale = scale,
                rotation = rotation,
                marginX = marginX,
                marginY = marginY,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogLeftY = invertAnalogLeftY,
                invertAnalogRightY = invertAnalogRightY
            )
        )
    }

    LaunchedEffect(lightgunTriggerOnTouch, lightgunTriggerDelay, lightgunAllowOffscreen, lightgunPort) {
        prefs.edit()
            .putBoolean("overlay_${console}_lightgun_trigger_on_touch", lightgunTriggerOnTouch)
            .putInt("overlay_${console}_lightgun_trigger_delay", lightgunTriggerDelay)
            .putBoolean("overlay_${console}_lightgun_allow_offscreen", lightgunAllowOffscreen)
            .putInt("overlay_${console}_lightgun_port", lightgunPort)
            .commit()
    }

    Dialog(onDismissRequest = onCancel) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.88f)
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Advanced Radial Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    HorizontalDivider(color = Color(0xFF444444))

                    Text(
                        text = "Precision Adjustments",
                        color = Color(0xFF2196F3),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    RadialSlider(
                        label = "Scale",
                        value = scale,
                        displayValue = "${String.format("%.2f", scale * 0.75f + 0.75f)}x",
                        valueRange = 0f..1f,
                        onValueChange = { scale = it },
                        onNudge = { delta ->
                            scale = (scale + delta).coerceIn(0f, 1f)
                        }
                    )

                    RadialSlider(
                        label = "Rotation",
                        value = rotation,
                        displayValue = "${String.format("%.0f", rotation * TouchControllerSettingsManager.MAX_ROTATION)}°",
                        valueRange = 0f..1f,
                        onValueChange = { rotation = it },
                        onNudge = { delta ->
                            rotation = (rotation + delta).coerceIn(0f, 1f)
                        }
                    )

                    RadialSlider(
                        label = "Horizontal Margin",
                        value = marginX,
                        displayValue = "${String.format("%.0f", marginX * TouchControllerSettingsManager.MAX_MARGINS)}dp",
                        valueRange = 0f..1f,
                        onValueChange = { marginX = it },
                        onNudge = { delta ->
                            marginX = (marginX + delta).coerceIn(0f, 1f)
                        }
                    )

                    RadialSlider(
                        label = "Vertical Margin",
                        value = marginY,
                        displayValue = "${String.format("%.0f", marginY * TouchControllerSettingsManager.MAX_MARGINS)}dp",
                        valueRange = 0f..1f,
                        onValueChange = { marginY = it },
                        onNudge = { delta ->
                            marginY = (marginY + delta).coerceIn(0f, 1f)
                        }
                    )

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
                                inactiveTrackColor = Color(0xFF444444)
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
                                inactiveTrackColor = Color(0xFF444444)
                            )
                        )
                        Text(
                            text = "Choose which port controls the lightgun (-1 = all ports)",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }

                    HorizontalDivider(color = Color(0xFF444444))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                val defaults = TouchControllerSettingsManager.Settings()
                                scale = defaults.scale
                                rotation = defaults.rotation
                                marginX = defaults.marginX
                                marginY = defaults.marginY
                                swapAnalogSticks = defaults.swapAnalogSticks
                                invertAnalogLeftY = defaults.invertAnalogLeftY
                                invertAnalogRightY = defaults.invertAnalogRightY
                                lightgunTriggerOnTouch = true
                                lightgunTriggerDelay = 1
                                lightgunAllowOffscreen = true
                                lightgunPort = -1
                            }
                        ) {
                            Text("Reset", color = Color(0xFFFF5722))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                onClick = {
                                    onPreview(initialSettings)
                                    onCancel()
                                }
                            ) {
                                Text("Cancel", color = Color(0xFFBDBDBD))
                            }

                            Button(
                                onClick = {
                                    val updated = TouchControllerSettingsManager.Settings(
                                        scale = scale,
                                        rotation = rotation,
                                        marginX = marginX,
                                        marginY = marginY,
                                        swapAnalogSticks = swapAnalogSticks,
                                        invertAnalogLeftY = invertAnalogLeftY,
                                        invertAnalogRightY = invertAnalogRightY
                                    )
                                    onSave(updated)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadialSlider(
    label: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onNudge: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Text(displayValue, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onNudge(-0.01f) },
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "-",
                    color = Color(0xFFB0BEC5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { onNudge(0.01f) },
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "+",
                    color = Color(0xFFB0BEC5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
