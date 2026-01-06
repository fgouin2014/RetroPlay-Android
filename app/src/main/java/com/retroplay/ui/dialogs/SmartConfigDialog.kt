package com.retroplay.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.retroplay.database.SmartConfig

/**
 * Smart Config Dialog
 * Displays smart configuration info and allows navigation to metadata view.
 */
@Composable
fun SmartConfigDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit = {},
    gameName: String = "",
    consoleName: String = "",
    onViewMetadata: () -> Unit = {},
    gameCRC: String? = null,
    smartConfig: SmartConfig? = null,
    onSave: (SmartConfig) -> Unit = {},
    onApplySmartConfig: (SmartConfig) -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Text(
                    text = "⚡ SMART CONFIG",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$consoleName • $gameName",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (smartConfig != null) {
                    // Display Smart Config Info
                    Text(
                        text = "Recommended Settings:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Run-Ahead
                    ConfigRow(
                        icon = "🚀",
                        label = "Run-Ahead",
                        value = if (smartConfig.runAheadEnabled) "${smartConfig.runAheadFrames} frames" else "Disabled",
                        enabled = smartConfig.runAheadEnabled
                    )
                    
                    // Rewind
                    ConfigRow(
                        icon = "⏪",
                        label = "Rewind",
                        value = if (smartConfig.rewindEnabled) {
                            "${smartConfig.rewindBufferSize / 1024 / 1024}MB @ 1/${smartConfig.rewindGranularity}f"
                        } else "Disabled",
                        enabled = smartConfig.rewindEnabled
                    )
                    
                    // Overlay
                    ConfigRow(
                        icon = "🎮",
                        label = "Overlay",
                        value = smartConfig.overlayName,
                        enabled = true
                    )
                    
                    // Fast Forward
                    ConfigRow(
                        icon = "⏩",
                        label = "Fast Forward",
                        value = "${smartConfig.fastForwardRatio}x",
                        enabled = true
                    )
                } else {
                    // No Smart Config available
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ℹ️ No Smart Config available for this game.\n\nDefault settings will be used.",
                            fontSize = 14.sp,
                            color = Color(0xFF9E9E9E),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // View Metadata button (if handler provided)
                    if (smartConfig != null) {
                        OutlinedButton(
                            onClick = onViewMetadata,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("📋 VIEW INFO", fontSize = 14.sp)
                        }
                    }
                    
                    // Close button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("CLOSE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(
    icon: String,
    label: String,
    value: String,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabled) Color(0xFF2C2C2C) else Color(0xFF1A1A1A),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = if (enabled) Color.White else Color(0xFF666666)
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            color = if (enabled) Color(0xFF4CAF50) else Color(0xFF666666),
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}
