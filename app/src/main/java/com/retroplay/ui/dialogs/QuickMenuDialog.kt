package com.retroplay.ui.dialogs

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import com.retroplay.config.RetroPlayConfigManager

/**
 * Quick Menu for RetroArch Emulator.
 * Extracted from RetroArchEmulatorActivity for better modularity.
 */
@Composable
fun QuickMenuDialog(
    gameName: String,
    gameCRC: String?,
    configId: String?, // ID for configuration (Serial or Name fallback)
    console: String,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onSaveStateClick: () -> Unit,
    onLoadStateClick: () -> Unit,
    onGamePadSettings: () -> Unit,
    onGameInfo: () -> Unit = {},
    onCheatCodes: () -> Unit,
    onChangeCore: () -> Unit,
    onDipSwitches: () -> Unit,
    onCoreOptions: () -> Unit,
    onSmartConfig: () -> Unit = {},
    onPerGameConfig: () -> Unit = {},
    onDiskSwapper: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onTurboSettings: () -> Unit = {},
    hasGameInfo: Boolean = false,
    hasDipSwitches: Boolean,
    hasCoreOptions: Boolean,
    availableDisks: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var cacheState by remember { mutableStateOf(prefs.getBoolean("cache_enabled_$console", false)) }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Titre
                    Text(
                        gameName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    
                    HorizontalDivider(color = Color.Gray)
                    
                    // Save Game
                    TextButton(
                        onClick = onSaveGame,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Game", color = Color.White)
                    }
                    
                    // Load Game
                    TextButton(
                        onClick = onLoadGame,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Game", color = Color.White)
                    }
                    
                    HorizontalDivider(color = Color.Gray)
                    
                    if (hasGameInfo) {
                        TextButton(
                            onClick = onGameInfo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Game Info", color = Color(0xFF64B5F6))
                        }
                    }

                    // Screenshot
                    TextButton(
                        onClick = onScreenshot,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Take Screenshot", color = Color(0xFF2196F3))
                    }
                    
                    TextButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Gallery", color = Color(0xFFBB86FC))
                    }
                    
                    HorizontalDivider(color = Color.Gray)
                    
                    // Cheat Codes
                    TextButton(
                        onClick = onCheatCodes,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cheat Codes", color = Color(0xFF4CAF50))
                    }
                    
                    // GamePad Settings
                    TextButton(
                        onClick = onGamePadSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GamePad Settings", color = Color.White)
                    }
                    
                    // Turbo Settings
                    TextButton(
                        onClick = onTurboSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Turbo Settings", color = Color(0xFF00BCD4))
                    }
                    
                    // DIP Switches (if available)
                    if (hasDipSwitches) {
                        TextButton(
                            onClick = onDipSwitches,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DIP Switches", color = Color(0xFFFF9800))
                        }
                    }
                    
                    // Core Options (if available)
                    if (hasCoreOptions) {
                        TextButton(
                            onClick = onCoreOptions,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Core Options", color = Color(0xFF64B5F6))
                        }
                    }
                    

                    
                    // Smart Config Settings
                    TextButton(
                        onClick = onSmartConfig,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Smart Config", color = Color(0xFF4CAF50))
                    }
                    
                    // Per-Game Config
                    // Uses configId (Serial/Fallback) if available, otherwise gameCRC
                    val effectiveConfigId = configId ?: gameCRC
                    
                    TextButton(
                        onClick = onPerGameConfig,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = effectiveConfigId != null
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Per-Game Config", 
                                color = if (effectiveConfigId != null) Color(0xFF2196F3) else Color(0xFF666666)
                            )
                            // Note: QuickMenuDialog doesn't have gameName, so we can't check per-game config
                            // This check is disabled for now (would need console and gameName)
                            // effectiveConfigId?.let { id ->
                            //     if (RetroPlayConfigManager.hasGameConfig(console, gameName)) {
                            //         Text("●", color = Color(0xFFFF9800), fontSize = 8.sp)
                            //     }
                            // }
                        }
                    }
                    
                    // Disk Swapper (PSX multi-disc games)
                    if (availableDisks > 1) {
                        TextButton(
                            onClick = onDiskSwapper,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Swap Disk ($availableDisks discs)", color = Color(0xFFFF9800))
                        }
                    }
                    
                    // Change Core
                    // Change Core
                    TextButton(
                        onClick = onChangeCore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Core & Restart", color = Color(0xFFE91E63))
                    }

                    HorizontalDivider(color = Color.Gray)

                    // Session Controls (Grouped)
                    TextButton(
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RESUME", color = Color(0xFF4CAF50))
                    }

                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Console (Soft Reset)", color = Color(0xFFFF5722))
                    }
                    
                    HorizontalDivider(color = Color.Gray)
                    
                    // ZIP Cache Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ZIP Cache Extraction", color = Color.White)
                            Text(
                                "Enable if ROM doesn't load",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = cacheState,
                            onCheckedChange = { enabled ->
                                cacheState = enabled
                                prefs.edit().putBoolean("cache_enabled_$console", enabled).apply()
                                android.util.Log.i("MainMenu", "[$console] Cache enabled: $enabled")
                                android.widget.Toast.makeText(context,
                                    "[$console] Cache " + (if (enabled) "enabled" else "disabled"), 
                                    android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF4CAF50),
                                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    HorizontalDivider(color = Color.Gray)
                    
                    // Close button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                    ) {
                        Text("CLOSE", color = Color.White)
                    }
                }
            }
        }
    }
}
