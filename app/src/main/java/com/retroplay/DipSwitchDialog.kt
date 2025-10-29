package com.retroplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

/**
 * Dialog pour afficher et modifier les DIP switches d'un jeu arcade.
 * Les DIP switches sont des paramètres hardware de la machine d'arcade (difficulté, vies, coins, etc.)
 */
@Composable
fun DipSwitchDialog(
    gameName: String,
    dipSwitches: List<CoreVariable>,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    // État local pour les modifications
    val modifiedValues = remember {
        mutableStateMapOf<String, String>().apply {
            dipSwitches.forEach { putAll(mapOf(it.key to it.currentValue)) }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2C),
        tonalElevation = 8.dp,
        title = {
            Column {
                Text(
                    text = "DIP Switches",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = gameName,
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (dipSwitches.isEmpty()) {
                    Text(
                        text = "No DIP switches available for this game.",
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                } else {
                    dipSwitches.forEach { dipSwitch ->
                        DipSwitchItem(
                            dipSwitch = dipSwitch,
                            currentValue = modifiedValues[dipSwitch.key] ?: dipSwitch.currentValue,
                            onValueChange = { newValue ->
                                modifiedValues[dipSwitch.key] = newValue
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF9E9E9E), fontSize = 16.sp)
                }
                TextButton(onClick = {
                    onApply(modifiedValues.toMap())
                    onDismiss()
                }) {
                    Text("Apply", color = Color(0xFF4CAF50), fontSize = 16.sp)
                }
            }
        }
    )
}

@Composable
private fun DipSwitchItem(
    dipSwitch: CoreVariable,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = dipSwitch.displayName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (dipSwitch.isBoolean()) {
            // Switch pour les valeurs booléennes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (dipSwitch.getBooleanValue()) "Enabled" else "Disabled",
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp
                )
                Switch(
                    checked = currentValue.lowercase() in listOf("enabled", "on", "true"),
                    onCheckedChange = { checked ->
                        val newValue = if (checked) {
                            dipSwitch.possibleValues.firstOrNull { 
                                it.lowercase() in listOf("enabled", "on", "true") 
                            } ?: "enabled"
                        } else {
                            dipSwitch.possibleValues.firstOrNull { 
                                it.lowercase() in listOf("disabled", "off", "false") 
                            } ?: "disabled"
                        }
                        onValueChange(newValue)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF81C784)
                    )
                )
            }
        } else {
            // Dropdown pour les valeurs multiples
            var expanded by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    shape = MaterialTheme.shapes.small,
                    color = Color(0xFF3C3C3C)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentValue,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "▼",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3C3C3C))
                ) {
                    dipSwitch.possibleValues.forEach { value ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = value,
                                    color = if (value == currentValue) Color(0xFF4CAF50) else Color.White
                                )
                            },
                            onClick = {
                                onValueChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}


