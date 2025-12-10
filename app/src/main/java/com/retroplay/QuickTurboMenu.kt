package com.retroplay

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.overlay.models.TurboPreferenceManager
import com.retroplay.overlay.models.TurboSettings

@Composable
fun QuickTurboMenu(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { 
        context.getSharedPreferences("retroplay_prefs", Context.MODE_PRIVATE) 
    }
    var settings by remember { mutableStateOf(TurboPreferenceManager.load(prefs)) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(280.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Quick Turbo",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                
                // Quick Presets
                Text("Quick Presets", color = Color.Gray, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickPresetButton("Slow\n5 Hz", 5, settings.frequency) {
                        settings = settings.copy(frequency = it)
                        TurboPreferenceManager.save(prefs, settings)
                    }
                    QuickPresetButton("Normal\n10 Hz", 10, settings.frequency) {
                        settings = settings.copy(frequency = it)
                        TurboPreferenceManager.save(prefs, settings)
                    }
                    QuickPresetButton("Fast\n15 Hz", 15, settings.frequency) {
                        settings = settings.copy(frequency = it)
                        TurboPreferenceManager.save(prefs, settings)
                    }
                    QuickPresetButton("Rapid\n30 Hz", 30, settings.frequency) {
                        settings = settings.copy(frequency = it)
                        TurboPreferenceManager.save(prefs, settings)
                    }
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickPresetButton(
    label: String,
    frequency: Int,
    currentFrequency: Int,
    onSelect: (Int) -> Unit
) {
    val isSelected = frequency == currentFrequency
    Button(
        onClick = { onSelect(frequency) },
        modifier = Modifier.weight(1f).height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF00BCD4) else Color(0xFF424242)
        )
    ) {
        Text(
            label,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}


