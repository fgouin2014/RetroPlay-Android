package com.retroplay

import android.util.Log
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog pour afficher et modifier les Core Options d'un émulateur.
 * Les Core Options sont des paramètres de l'émulateur (résolution, filtres, frameskip, etc.)
 */
@Composable
fun CoreOptionsDialog(
    gameName: String,
    coreOptions: List<CoreVariable>,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    // État local pour les modifications
    // CRITIQUE: Utiliser remember(coreOptions) pour réinitialiser si coreOptions change
    val modifiedValues = remember(coreOptions) {
        mutableStateMapOf<String, String>().apply {
            coreOptions.forEach { opt ->
                put(opt.key, opt.currentValue)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2C),
        tonalElevation = 8.dp,
        title = {
            Column {
                Text(
                    text = "Core Options",
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
                if (coreOptions.isEmpty()) {
                    Text(
                        text = "No core options available.",
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                } else {
                    coreOptions.forEach { option ->
                        val displayValue = modifiedValues[option.key] ?: option.currentValue
                        
                        CoreOptionItem(
                            option = option,
                            currentValue = displayValue,
                            onValueChange = { newValue ->
                                modifiedValues[option.key] = newValue
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
private fun CoreOptionItem(
    option: CoreVariable,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = option.displayName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        if (option.isBoolean()) {
            // Switch pour les valeurs booléennes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (option.getBooleanValue()) "Enabled" else "Disabled",
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp
                )
                Switch(
                    checked = currentValue.lowercase() in listOf("enabled", "on", "true"),
                    onCheckedChange = { checked ->
                        val newValue = if (checked) {
                            option.possibleValues.firstOrNull { 
                                it.lowercase() in listOf("enabled", "on", "true") 
                            } ?: "enabled"
                        } else {
                            option.possibleValues.firstOrNull { 
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
            if (option.possibleValues.isEmpty()) {
                // Option sans valeurs possibles - afficher en lecture seule
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentValue.ifEmpty { "N/A" },
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic
                            )
                        )
                        Text(
                            text = "(Read-only)",
                            color = Color(0xFF666666),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // Dropdown normal avec valeurs possibles
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
                                text = currentValue.ifEmpty { "N/A" },
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
                            .background(Color(0xFF1E1E1E))  // Fond plus foncé pour meilleur contraste
                    ) {
                        option.possibleValues.forEach { value ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = value,
                                        color = if (value == currentValue) Color(0xFF4CAF50) else Color.White,  // Texte blanc sur fond foncé
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    onValueChange(value)
                                    expanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = Color.White,
                                    leadingIconColor = Color.White,
                                    trailingIconColor = Color.White,
                                    disabledTextColor = Color(0xFF666666),
                                    disabledLeadingIconColor = Color(0xFF666666),
                                    disabledTrailingIconColor = Color(0xFF666666)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}


