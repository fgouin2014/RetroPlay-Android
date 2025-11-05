package com.retroplay

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
import com.retroplay.config.RetroPlayConfigManager

@Composable
fun PerGameConfigDialog(
    gameCRC: String,
    gameName: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit = {}
) {
    val globalConfig = remember { RetroPlayConfigManager.loadConfig() }
    val hasOverride = remember { RetroPlayConfigManager.hasGameConfig(gameCRC) }
    val initialConfig = remember { 
        RetroPlayConfigManager.loadGameConfig(gameCRC) ?: globalConfig
    }
    
    var runAheadEnabled by remember { mutableStateOf(initialConfig.runAheadEnabled) }
    var runAheadFrames by remember { mutableStateOf(initialConfig.runAheadFrames) }
    var rewindEnabled by remember { mutableStateOf(initialConfig.rewindEnable) }
    var rewindBufferSize by remember { mutableStateOf(initialConfig.rewindBufferSize / (1024 * 1024)) } // MB
    var fastforwardRatio by remember { mutableStateOf(initialConfig.fastforwardRatio) }
    var videoVsync by remember { mutableStateOf(initialConfig.videoVsync) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
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
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "CRC: $gameCRC",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                if (hasOverride) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF263238)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✅",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "This game has custom settings (overrides global config)",
                                fontSize = 13.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF263238)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ℹ️",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Currently using global config. Changes will create game-specific overrides.",
                                fontSize = 13.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Run-Ahead Section
                ConfigSectionHeader("⚡ Run-Ahead (Input Lag Reduction)")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Run-Ahead", color = Color.White)
                    Switch(
                        checked = runAheadEnabled,
                        onCheckedChange = { runAheadEnabled = it }
                    )
                }
                
                if (runAheadEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Frames: $runAheadFrames (reduces lag by ~${runAheadFrames * 16}ms)",
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp
                    )
                    
                    Slider(
                        value = runAheadFrames.toFloat(),
                        onValueChange = { runAheadFrames = it.toInt() },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Rewind Section
                ConfigSectionHeader("⏪ Rewind")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Rewind", color = Color.White)
                    Switch(
                        checked = rewindEnabled,
                        onCheckedChange = { rewindEnabled = it }
                    )
                }
                
                if (rewindEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Buffer Size: ${rewindBufferSize}MB",
                        color = Color(0xFFB0BEC5),
                        fontSize = 14.sp
                    )
                    
                    Slider(
                        value = rewindBufferSize.toFloat(),
                        onValueChange = { rewindBufferSize = it.toInt() },
                        valueRange = 5f..100f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Fast Forward Section
                ConfigSectionHeader("⏩ Fast Forward")
                
                Text(
                    text = "Speed: ${fastforwardRatio}x",
                    color = Color(0xFFB0BEC5),
                    fontSize = 14.sp
                )
                
                Slider(
                    value = fastforwardRatio,
                    onValueChange = { fastforwardRatio = it },
                    valueRange = 1.5f..10f,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Video Section
                ConfigSectionHeader("🎥 Video")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("VSync (reduce tearing)", color = Color.White)
                    Switch(
                        checked = videoVsync,
                        onCheckedChange = { videoVsync = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Global config comparison
                if (!hasOverride) {
                    HorizontalDivider(color = Color(0xFF424242))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "💡 Current Global Defaults",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = """
                            Run-Ahead: ${if (globalConfig.runAheadEnabled) "${globalConfig.runAheadFrames} frames" else "Disabled"}
                            Rewind: ${if (globalConfig.rewindEnable) "${globalConfig.rewindBufferSize / (1024 * 1024)}MB" else "Disabled"}
                            Fast Forward: ${globalConfig.fastforwardRatio}x
                            VSync: ${if (globalConfig.videoVsync) "ON" else "OFF"}
                        """.trimIndent(),
                        fontSize = 13.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 20.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasOverride) {
                        // Delete override button
                        OutlinedButton(
                            onClick = {
                                RetroPlayConfigManager.deleteGameConfig(gameCRC)
                                onSave()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFF44336)
                            )
                        ) {
                            Text("RESET TO GLOBAL")
                        }
                    } else {
                        // Cancel button
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL")
                        }
                    }
                    
                    // Save button
                    Button(
                        onClick = {
                            val config = RetroPlayConfigManager.RetroPlayConfig(
                                runAheadEnabled = runAheadEnabled,
                                runAheadFrames = runAheadFrames,
                                rewindEnable = rewindEnabled,
                                rewindBufferSize = rewindBufferSize * 1024 * 1024,
                                fastforwardRatio = fastforwardRatio,
                                videoVsync = videoVsync,
                                // Copy other values from initial config
                                runAheadSecondaryInstance = initialConfig.runAheadSecondaryInstance,
                                runAheadHideWarnings = initialConfig.runAheadHideWarnings,
                                rewindGranularity = initialConfig.rewindGranularity,
                                fastforwardFrameskip = initialConfig.fastforwardFrameskip,
                                audioEnable = initialConfig.audioEnable,
                                audioMuted = initialConfig.audioMuted,
                                audioVolume = initialConfig.audioVolume,
                                videoHardSync = initialConfig.videoHardSync,
                                videoHardSyncFrames = initialConfig.videoHardSyncFrames,
                                videoFrameDelay = initialConfig.videoFrameDelay,
                                smartConfigEnabled = initialConfig.smartConfigEnabled,
                                smartConfigAutoRunAhead = initialConfig.smartConfigAutoRunAhead,
                                smartConfigAutoRewind = initialConfig.smartConfigAutoRewind,
                                smartConfigAutoOverlay = initialConfig.smartConfigAutoOverlay,
                                smartConfigShowOSD = initialConfig.smartConfigShowOSD,
                                inputAutodetectEnable = initialConfig.inputAutodetectEnable,
                                inputPollTypeInterval = initialConfig.inputPollTypeInterval,
                                savefileCompressionEnabled = initialConfig.savefileCompressionEnabled,
                                savestateCompressionEnabled = initialConfig.savestateCompressionEnabled,
                                screenshotDirectory = initialConfig.screenshotDirectory
                            )
                            RetroPlayConfigManager.saveGameConfig(gameCRC, config)
                            onSave()
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

@Composable
private fun ConfigSectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2196F3),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

