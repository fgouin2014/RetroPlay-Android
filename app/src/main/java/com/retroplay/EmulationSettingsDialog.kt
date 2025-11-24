package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
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
 * Emulation Settings Dialog
 * 
 * Dialog amélioré pour configurer les options d'émulation:
 * - Core Options (catégorisées par type)
 * - Settings globaux (Fast Forward, Audio, etc.)
 * 
 * Pattern: Box + Card + verticalScroll (validé dans AdvancedOverlaySettingsDialog)
 */
@Composable
fun EmulationSettingsDialog(
    gameName: String,
    coreOptions: List<CoreVariable>,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences
) {
    val TAG = "EmulationSettingsDialog"
    
    // Log quand le dialog s'ouvre
    LaunchedEffect(Unit) {
        Log.i(TAG, "Opening EmulationSettingsDialog for game: $gameName")
        Log.i(TAG, "Core options count: ${coreOptions.size}")
    }
    
    // État local pour les modifications des core options
    val modifiedValues = remember {
        mutableStateMapOf<String, String>().apply {
            coreOptions.forEach { putAll(mapOf(it.key to it.currentValue)) }
        }
    }
    
    // Settings globaux (Fast Forward, Audio, etc.)
    var fastForwardRatio by remember { 
        mutableStateOf(prefs.getFloat("emulation_fast_forward_ratio", 2.0f)) 
    }
    var audioVolume by remember { 
        mutableStateOf(prefs.getFloat("emulation_audio_volume", 0.0f)) 
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .verticalScroll(rememberScrollState()),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Text(
                        text = "Emulation Settings",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = gameName,
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                    
                    Divider(color = Color(0xFF404040))
                    
                    // Core Options (catégorisées)
                    if (coreOptions.isNotEmpty()) {
                        val categorized = categorizeCoreOptions(coreOptions)
                        
                        // Log catégorisation
                        LaunchedEffect(categorized) {
                            Log.i(TAG, "Categorized ${coreOptions.size} options into ${categorized.size} categories:")
                            categorized.forEach { (cat, opts) ->
                                Log.d(TAG, "  - $cat: ${opts.size} options")
                            }
                        }
                        
                        categorized.forEach { (category, options) ->
                            if (options.isNotEmpty()) {
                                CoreOptionsCategory(
                                    categoryName = category,
                                    options = options,
                                    modifiedValues = modifiedValues,
                                    onValueChange = { key, value ->
                                        modifiedValues[key] = value
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    } else {
                        Text(
                            text = "No core options available.",
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )
                    }
                    
                    Divider(color = Color(0xFF404040))
                    
                    // Global Settings
                    Text(
                        text = "Global Settings",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Fast Forward Ratio
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fast Forward Ratio",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${fastForwardRatio.toInt()}x",
                                color = Color(0xFF4CAF50),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = fastForwardRatio,
                            onValueChange = { fastForwardRatio = it },
                            valueRange = 1f..10f,
                            steps = 8, // 1, 2, 3, ..., 10
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50),
                                activeTrackColor = Color(0xFF81C784)
                            )
                        )
                        Text(
                            text = "Speed multiplier for fast forward (1x to 10x)",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp
                        )
                    }
                    
                    // Audio Volume
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio Volume",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${audioVolume.toInt()} dB",
                                color = Color(0xFF4CAF50),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = audioVolume,
                            onValueChange = { audioVolume = it },
                            valueRange = -80f..12f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50),
                                activeTrackColor = Color(0xFF81C784)
                            )
                        )
                        Text(
                            text = "Audio volume in decibels (-80 to +12 dB)",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF9E9E9E))
                        }
                        Button(
                            onClick = {
                                Log.i(TAG, "Apply clicked - ${modifiedValues.size} core options modified")
                                Log.i(TAG, "Global settings - Fast Forward: ${fastForwardRatio.toInt()}x, Audio Volume: ${audioVolume.toInt()} dB")
                                
                                // Save global settings
                                prefs.edit()
                                    .putFloat("emulation_fast_forward_ratio", fastForwardRatio)
                                    .putFloat("emulation_audio_volume", audioVolume)
                                    .apply()
                                
                                // Apply core options
                                onApply(modifiedValues.toMap())
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Apply", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Catégorise les core options par type (Video, Audio, Input, etc.)
 */
private fun categorizeCoreOptions(options: List<CoreVariable>): Map<String, List<CoreVariable>> {
    val categories = mutableMapOf<String, MutableList<CoreVariable>>()
    
    options.forEach { option ->
        val category = detectCategory(option)
        categories.getOrPut(category) { mutableListOf() }.add(option)
    }
    
    // Trier les catégories dans un ordre logique
    val orderedCategories = listOf(
        "Video", "Audio", "Input", "Performance", "Emulation", "Other"
    )
    
    val result = mutableMapOf<String, List<CoreVariable>>()
    orderedCategories.forEach { cat ->
        categories[cat]?.let { result[cat] = it }
    }
    // Ajouter les catégories non listées
    categories.forEach { (cat, opts) ->
        if (cat !in orderedCategories) {
            result[cat] = opts
        }
    }
    
    return result
}

/**
 * Détecte la catégorie d'une option basée sur sa clé
 */
private fun detectCategory(option: CoreVariable): String {
    val key = option.key.lowercase()
    
    return when {
        // Video
        key.contains("resolution") || key.contains("screensize") || 
        key.contains("antialiasing") || key.contains("bilinear") || 
        key.contains("filtering") || key.contains("texture") ||
        key.contains("enhanced") || key.contains("internal") -> "Video"
        
        // Audio
        key.contains("audio") || key.contains("sound") || 
        key.contains("spu") || key.contains("sample") ||
        key.contains("volume") -> "Audio"
        
        // Input
        key.contains("input") || key.contains("controller") ||
        key.contains("zapper") || key.contains("lightgun") ||
        key.contains("mouse") || key.contains("keyboard") -> "Input"
        
        // Performance
        key.contains("frameskip") || key.contains("frames") ||
        key.contains("thread") || key.contains("speed") ||
        key.contains("turbo") -> "Performance"
        
        // Emulation
        key.contains("region") || key.contains("ntsc") || key.contains("pal") ||
        key.contains("mode") || key.contains("emulation") ||
        key.contains("accuracy") || key.contains("compatibility") -> "Emulation"
        
        // Other (default)
        else -> "Other"
    }
}

/**
 * Composable pour afficher une catégorie d'options
 */
@Composable
private fun CoreOptionsCategory(
    categoryName: String,
    options: List<CoreVariable>,
    modifiedValues: MutableMap<String, String>,
    onValueChange: (String, String) -> Unit
) {
    Column {
        Text(
            text = categoryName,
            color = Color(0xFF4CAF50),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        options.forEach { option ->
            CoreOptionItem(
                option = option,
                currentValue = modifiedValues[option.key] ?: option.currentValue,
                onValueChange = { newValue ->
                    onValueChange(option.key, newValue)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Item d'option individuel (réutilisé depuis CoreOptionsDialog)
 */
@Composable
private fun CoreOptionItem(
    option: CoreVariable,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Fallback si displayName est vide: utiliser la clé formatée
        val displayText = if (option.displayName.isBlank()) {
            // Extraire le nom de l'option depuis la clé (ex: "parallel-n64-screensize" -> "Screensize")
            option.key.split("-").lastOrNull()?.replaceFirstChar { it.uppercase() } 
                ?: option.key
        } else {
            option.displayName
        }
        
        Text(
            text = displayText,
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
                    text = option.getBooleanDisplayValue(),
                    color = Color(0xFFB0B0B0),
                    fontSize = 14.sp
                )
                Switch(
                    checked = currentValue.lowercase().trim() in listOf("enabled", "on", "true", "1"),
                    onCheckedChange = { checked ->
                        val newValue = if (checked) {
                            // Chercher d'abord dans les valeurs possibles
                            option.possibleValues.firstOrNull { 
                                it.lowercase().trim() in listOf("enabled", "on", "true", "1") 
                            } ?: 
                            // Si pas trouvé, utiliser "1" si possibleValues contient "0" et "1"
                            (if (option.possibleValues.map { it.trim() }.toSet() == setOf("0", "1")) "1" else "enabled")
                        } else {
                            option.possibleValues.firstOrNull { 
                                it.lowercase().trim() in listOf("disabled", "off", "false", "0") 
                            } ?:
                            // Si pas trouvé, utiliser "0" si possibleValues contient "0" et "1"
                            (if (option.possibleValues.map { it.trim() }.toSet() == setOf("0", "1")) "0" else "disabled")
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
                    option.possibleValues.forEach { value ->
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

