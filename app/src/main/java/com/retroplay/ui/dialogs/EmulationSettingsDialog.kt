package com.retroplay.ui.dialogs

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.CoreVariable

@Composable
fun EmulationSettingsDialog(
    gameName: String,
    coreOptions: List<CoreVariable>,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    console: String
) {
    // Temporary state to hold changes before applying
    val changedValues = remember { mutableStateMapOf<String, String>() }
    
    // Controller Port States (Loaded from prefs if available, else default)
    // Scoped to console ID to prevent cross-contamination
    val port1Key = "${console}_port1_device_type"
    val port2Key = "${console}_port2_device_type"
    
    val port1Type = remember { mutableStateOf(prefs.getInt(port1Key, 1)) } // Default Joypad
    val port2Type = remember { mutableStateOf(prefs.getInt(port2Key, 1)) }
    
    // Expandable sections
    var showCoreOptions by remember { mutableStateOf(true) }
    var showControllerPorts by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Emulation Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }
                
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBBBBBB),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Divider(color = Color(0xFF333333))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // ================= SECTION: CONTROLLER PORTS =================
                    SectionHeader(
                        title = "Controller Ports",
                        isExpanded = showControllerPorts,
                        onToggle = { showControllerPorts = !showControllerPorts }
                    )
                    
                    AnimatedVisibility(
                        visible = showControllerPorts,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            ControllerPortRow("Port 1 (Player 1)", port1Type.value) { newValue ->
                                port1Type.value = newValue
                                prefs.edit().putInt(port1Key, newValue).apply()
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            ControllerPortRow("Port 2 (Player 2)", port2Type.value) { newValue ->
                                port2Type.value = newValue
                                prefs.edit().putInt(port2Key, newValue).apply()
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Note: Changes to ports may require a restart of the content.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Divider(color = Color(0xFF333333))

                    // ================= SECTION: CORE OPTIONS =================
                    SectionHeader(
                        title = "Core Options",
                        isExpanded = showCoreOptions,
                        onToggle = { showCoreOptions = !showCoreOptions }
                    )

                    AnimatedVisibility(
                        visible = showCoreOptions,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (coreOptions.isEmpty()) {
                            Text(
                                text = "No options available for this core.",
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Column(modifier = Modifier.padding(8.dp)) {
                                coreOptions.forEach { variable ->
                                    val currentVal = changedValues[variable.key] ?: variable.currentValue
                                    
                                    if (variable.isBoolean()) {
                                        // Render as Switch
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(variable.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                                                Text(currentVal, color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = currentVal.lowercase() == "enabled" || currentVal.lowercase() == "true" || currentVal == "1",
                                                onCheckedChange = { isChecked ->
                                                    val newVal = if (isChecked) "enabled" else "disabled" // Default to enabled/disabled text
                                                    // Try to match original format if possible
                                                    val matchedVal = if (variable.possibleValues.contains("true")) {
                                                        if (isChecked) "true" else "false"
                                                    } else if (variable.possibleValues.contains("1")) {
                                                        if (isChecked) "1" else "0"
                                                    } else {
                                                        if (isChecked) "enabled" else "disabled"
                                                    }
                                                    changedValues[variable.key] = matchedVal
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color(0xFF4CAF50),
                                                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                    } else {
                                        // Render as Dropdown
                                        var expanded by remember { mutableStateOf(false) }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(variable.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF2C2C2C), RoundedCornerShape(4.dp))
                                                    .clickable { expanded = true }
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(currentVal, color = Color.White)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                                                }
                                                DropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false },
                                                    modifier = Modifier.background(Color(0xFF333333))
                                                ) {
                                                    variable.possibleValues.forEach { value ->
                                                        DropdownMenuItem(
                                                            text = { Text(value, color = Color.White) },
                                                            onClick = {
                                                                changedValues[variable.key] = value
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Divider(color = Color(0xFF333333))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApply(changedValues)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Apply Changes", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = Color(0xFFFF9800)
        )
    }
}

@Composable
private fun ControllerPortRow(label: String, currentType: Int, onTypeChanged: (Int) -> Unit) {
    val deviceTypes = mapOf(
        1 to "Joypad",
        2 to "Mouse",
        258 to "Zapper (Lightgun)", // RETRO_DEVICE_ZAPPER
        514 to "Pointer"           // RETRO_DEVICE_POINTER
    )
    
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text(deviceTypes[currentType] ?: "Unknown ($currentType)", color = Color.White)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF333333))
            ) {
                deviceTypes.forEach { (typeId, name) ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White) },
                        onClick = {
                            onTypeChanged(typeId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
