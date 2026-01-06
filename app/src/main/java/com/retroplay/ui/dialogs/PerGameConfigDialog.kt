package com.retroplay.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.config.RetroPlayConfigManager

/**
 * Per-Game Configuration Dialog
 * Allows configuring run-ahead and rewind settings for a specific game.
 */
@Composable
fun PerGameConfigDialog(
    gameCRC: String,
    gameName: String,
    console: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit = {}
) {
    // Load game-specific or global config (use gameName like saves system)
    val config = remember(console, gameName) { 
        RetroPlayConfigManager.loadGameConfig(console, gameName) ?: RetroPlayConfigManager.loadConfig(console)
    }
    
    var runAheadEnabled by remember { mutableStateOf(config.runAheadEnabled) }
    var runAheadFrames by remember { mutableStateOf(config.runAheadFrames) }
    var rewindEnabled by remember { mutableStateOf(config.rewindEnable) }
    var rewindBufferMB by remember { mutableStateOf(config.rewindBufferSize / 1024 / 1024) }
    
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
                    text = "⚙️ PER-GAME CONFIG",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = gameName,
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                
                Text(
                    text = "CRC: $gameCRC",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Run-Ahead Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🚀 Run-Ahead", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Reduce input lag", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                    }
                    Switch(
                        checked = runAheadEnabled,
                        onCheckedChange = { runAheadEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF81C784)
                        )
                    )
                }
                
                if (runAheadEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Frames: $runAheadFrames", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = runAheadFrames.toFloat(),
                        onValueChange = { runAheadFrames = it.toInt() },
                        valueRange = 1f..4f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4CAF50),
                            activeTrackColor = Color(0xFF4CAF50)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Rewind Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⏪ Rewind", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Time travel feature", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                    }
                    Switch(
                        checked = rewindEnabled,
                        onCheckedChange = { rewindEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF2196F3),
                            checkedTrackColor = Color(0xFF64B5F6)
                        )
                    )
                }
                
                if (rewindEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Buffer: ${rewindBufferMB}MB", color = Color.White, fontSize = 14.sp)
                    Slider(
                        value = rewindBufferMB.toFloat(),
                        onValueChange = { rewindBufferMB = it.toInt() },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2196F3),
                            activeTrackColor = Color(0xFF2196F3)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Info Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2C2C2C)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ℹ️ Note",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Changes require restarting the game to take effect.",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Save and Close buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL")
                    }
                    
                    Button(
                        onClick = {
                            val updatedConfig = config.copy(
                                runAheadEnabled = runAheadEnabled,
                                runAheadFrames = runAheadFrames,
                                rewindEnable = rewindEnabled,
                                rewindBufferSize = rewindBufferMB * 1024 * 1024
                            )
                            RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig)
                            onSave()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("💾 SAVE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
