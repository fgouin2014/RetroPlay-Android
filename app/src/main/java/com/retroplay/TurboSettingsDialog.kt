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
                    
                    // Allow D-Pad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Allow D-Pad Turbo", color = Color.White)
                            Text(
                                "Enable turbo on directional buttons",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.allowDpad,
                            enabled = settings.enabled,
                            onCheckedChange = { settings = settings.copy(allowDpad = it) }
                        )
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

