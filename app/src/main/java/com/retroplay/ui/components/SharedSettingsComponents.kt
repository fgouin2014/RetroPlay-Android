package com.retroplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.overlay.assets.OverlayAssetManager
import kotlin.math.abs

@Composable
fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    activeColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(displayValue, color = activeColor, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color(0xFF3A3A3A)
            )
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showModifiedIndicator: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFA726),
                checkedTrackColor = Color(0xFFFFA726).copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF777777),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (showModifiedIndicator) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("●", color = Color(0xFFFF9800), fontSize = 8.sp)
                }
            }
            Text(subtitle, color = Color(0xFF9E9E9E), fontSize = 12.sp)
        }
    }
}

@Composable
fun ExpandableSettingsCard(
    title: String,
    icon: ImageVector? = null,
    initialExpanded: Boolean = false,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onReset != null && expanded) {
                        TextButton(onClick = onReset, modifier = Modifier.height(30.dp)) {
                            Text("RESET", color = Color(0xFF666666), fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (expanded) "▼" else "▶", color = Color.Gray, fontSize = 12.sp)
                }
            }
            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

fun layoutDisplayName(layoutName: String): String {
    val lower = layoutName.lowercase()
    
    // Pour rgpad: utiliser noms réels au lieu de labels génériques
    // Ex: "portrait-basic-mini" → "Basic Mini"
    //     "landscape-options-hidden" → "Options Hidden"
    if (lower.contains("basic") || lower.contains("options")) {
        return layoutName
            .removePrefix("landscape-")
            .removePrefix("portrait-")
            .split("-")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
    
    // Fallback pour autres overlays (flat, dual-shock, etc.)
    return when {
        lower.contains("both-analog") -> "Both Analog"
        lower.contains("left-analog") && lower.contains("menu") -> "Left Analog + Menu"
        lower.contains("left-analog") -> "Left Analog"
        lower.contains("right-analog") -> "Right Analog"
        lower.contains("analog") && lower.contains("menu") -> "Analog + Menu"
        lower.contains("analog") -> "Analog"
        lower.contains("menu") -> "Menu"
        lower.endsWith("-b") -> "Variant B"
        lower.endsWith("-c") -> "Variant C"
        else -> "Digital"
    }
}

fun snapToZero(value: Float): Float = if (abs(value) < 0.01f) 0.0f else value

fun computeDefaultLayouts(
    assetManager: OverlayAssetManager,
    overlayName: String,
    console: String,
    customCfgName: String?
): Pair<String, String> {
    val layouts = assetManager.getAvailableLayouts(overlayName, console, customCfgName)
    val landscape = layouts.firstOrNull { it.contains("landscape", ignoreCase = true) }
        ?: layouts.firstOrNull()
        ?: "landscape-A"
    val portrait = layouts.firstOrNull { it.contains("portrait", ignoreCase = true) }
        ?: landscape
    return landscape to portrait
}
