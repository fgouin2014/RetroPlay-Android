package com.retroplay

import androidx.compose.foundation.background
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
import androidx.compose.ui.window.Dialog
import com.retroplay.smartconfig.SmartConfigPreferences
import com.retroplay.smartconfig.SmartConfigSettings

@Composable
fun SmartConfigDialog(
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Load from retroplay.cfg file instead of SharedPreferences
    var config by remember { 
        mutableStateOf(com.retroplay.config.RetroPlayConfigManager.loadConfig())
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Text(
                        text = "💡 SMART CONFIG",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    
                    Text(
                        text = "Automatic optimal settings based on game metadata",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9E9E)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    HorizontalDivider(color = Color(0xFF424242))
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Global Enable/Disable
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Smart Config",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Master switch for all auto-configuration",
                                fontSize = 12.sp,
                                color = Color(0xFF9E9E9E)
                            )
                        }
                        
                        Switch(
                            checked = config.smartConfigEnabled,
                            onCheckedChange = { 
                                config = config.copy(smartConfigEnabled = it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Feature toggles (only shown if globally enabled)
                    if (config.smartConfigEnabled) {
                        Text(
                            text = "AUTO-APPLY FEATURES",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        
                        // Run-Ahead
                        FeatureToggle(
                            title = "⚡ Run-Ahead (Input Lag Reduction)",
                            description = "Automatically set Run-Ahead frames based on genre\n" +
                                    "Fighting: 4 frames, Platformer: 2 frames, RPG: 0 frames",
                            enabled = config.smartConfigAutoRunAhead,
                            onToggle = { config = config.copy(smartConfigAutoRunAhead = it) },
                            badge = "EXPERIMENTAL",
                            badgeColor = Color(0xFFFF9800)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Rewind
                        FeatureToggle(
                            title = "⏪ Rewind Buffer",
                            description = "Automatically set Rewind buffer size based on console and genre\n" +
                                    "Platformer: Large buffer, Fighting: Small buffer",
                            enabled = config.smartConfigAutoRewind,
                            onToggle = { config = config.copy(smartConfigAutoRewind = it) },
                            badge = "EXPERIMENTAL",
                            badgeColor = Color(0xFFFF9800)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Overlay
                        FeatureToggle(
                            title = "🎮 Optimal Overlay",
                            description = "Automatically select best overlay based on game requirements\n" +
                                    "PSX Racing: Analog sticks, Fighting: 6-button layout",
                            enabled = config.smartConfigAutoOverlay,
                            onToggle = { config = config.copy(smartConfigAutoOverlay = it) },
                            badge = "USER CHOICE",
                            badgeColor = Color(0xFF2196F3)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        HorizontalDivider(color = Color(0xFF424242))
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // OSD Notifications
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show OSD Notifications",
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Display on-screen message when Smart Config is applied",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9E9E9E)
                                )
                            }
                            
                            Switch(
                                checked = config.smartConfigShowOSD,
                                onCheckedChange = { 
                                    config = config.copy(smartConfigShowOSD = it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF2196F3),
                                    checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Info box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "ℹ️ How Smart Config Works",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Smart Config uses libretro-database metadata (genre, console, features) " +
                                        "to automatically configure optimal emulation settings.\n\n" +
                                        "You can override settings per-game from the Quick Menu.\n\n" +
                                        "Example: Fighting games get 4 frames Run-Ahead for tournament-level " +
                                        "responsiveness, while RPGs get 0 frames (no benefit).",
                                fontSize = 12.sp,
                                color = Color(0xFFB0BEC5),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF424242)
                            )
                        ) {
                            Text("CANCEL")
                        }
                        
                        Button(
                            onClick = {
                                com.retroplay.config.RetroPlayConfigManager.saveConfig(config)
                                onSettingsChanged()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("SAVE")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    badge: String? = null,
    badgeColor: Color = Color(0xFFFF9800)
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFF263238) else Color(0xFF1A1A1A)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        badge?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .background(badgeColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E),
                        lineHeight = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

