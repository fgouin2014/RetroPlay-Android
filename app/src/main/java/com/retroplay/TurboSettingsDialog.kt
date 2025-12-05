package com.retroplay

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.overlay.models.TurboPreferenceManager
import com.retroplay.overlay.models.TurboSettings

@Composable
fun TurboSettingsDialog(
    onDismiss: () -> Unit,
    onSave: (TurboSettings) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { 
        context.getSharedPreferences("retroplay_prefs", Context.MODE_PRIVATE) 
    }
    var settings by remember { mutableStateOf(TurboPreferenceManager.load(prefs)) }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f)
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title
                    Text(
                        "Turbo Configuration",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    
                    Divider(color = Color.Gray, thickness = 1.dp)
                    
                    // Enable/Disable
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Turbo Fire", color = Color.White, fontSize = 16.sp)
                            Text(
                                "Enable turbo functionality", 
                                color = Color.Gray, 
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { settings = settings.copy(enabled = it) }
                        )
                    }
                    
                    // Frequency Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Turbo Frequency", color = Color.White)
                            Text(
                                "${settings.frequency} Hz (${1000/settings.frequency}ms)",
                                color = Color(0xFF00BCD4)
                            )
                        }
                        Slider(
                            value = settings.frequency.toFloat(),
                            onValueChange = { settings = settings.copy(frequency = it.toInt()) },
                            valueRange = 5f..30f,
                            steps = 24,
                            enabled = settings.enabled
                        )
                        Text(
                            "RetroArch default: 10 Hz | Fast: 30 Hz",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    
                    // Duty Cycle Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duty Cycle", color = Color.White)
                            Text(
                                "${(settings.dutyCycle * 100).toInt()}%",
                                color = Color(0xFF00BCD4)
                            )
                        }
                        Slider(
                            value = settings.dutyCycle,
                            onValueChange = { settings = settings.copy(dutyCycle = it) },
                            valueRange = 0.1f..0.9f,
                            steps = 7,
                            enabled = settings.enabled
                        )
                        Text(
                            "Time button held during cycle (50% = half ON, half OFF)",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    
                    // Boutons turbo disponibles
                    Text("Turbo Buttons", color = Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Select which buttons can use turbo", color = Color.Gray, fontSize = 11.sp)
                    
                    // Grille de boutons sélectionnables
                    val availableButtons = listOf(
                        "a" to "A", "b" to "B", "x" to "X", "y" to "Y",
                        "l" to "L", "r" to "R", "l2" to "L2", "r2" to "R2",
                        "up" to "UP", "down" to "DOWN", "left" to "LEFT", "right" to "RIGHT"
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableButtons.chunked(4).forEach { rowButtons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowButtons.forEach { (action, label) ->
                                    val isSelected = action in settings.enabledButtons
                                    Button(
                                        onClick = {
                                            settings = if (isSelected) {
                                                settings.copy(enabledButtons = settings.enabledButtons - action)
                                            } else {
                                                settings.copy(enabledButtons = settings.enabledButtons + action)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(45.dp),
                                        enabled = settings.enabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) Color(0xFF00BCD4) else Color(0xFF424242),
                                            disabledContainerColor = Color(0xFF2A2A2A)
                                        )
                                    ) {
                                        Text(label, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Gray
                            )
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                TurboPreferenceManager.save(prefs, settings)
                                onSave(settings)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

