package com.retroplay.ui.settings.tabs

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import com.retroplay.ui.components.SwitchRow
import com.retroplay.ui.components.SliderWithLabel

@Composable
fun GraphicsTab(
    prefs: SharedPreferences,
    onVsyncChanged: (Boolean) -> Unit,
    onAspectRatioChanged: (String) -> Unit,
    onShaderChanged: (String) -> Unit,
    console: String, // Console name (required for config path)
    gameName: String = "", // For per-game config (like saves system)
    isPerGameMode: Boolean = false, // For per-game config
    coreFilePath: String? = null  // Optional core file path for config file name
) {
    // Video Settings
    Text("Video Settings", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    var vsyncEnabled by remember { mutableStateOf(prefs.getBoolean("emulation_video_vsync", true)) }
    val vsyncIsDefault = vsyncEnabled == true
    SwitchRow(
        title = "VSync",
        subtitle = "Vertical Synchronization (reduces tearing)",
        checked = vsyncEnabled,
        onCheckedChange = { 
            vsyncEnabled = it
            prefs.edit().putBoolean("emulation_video_vsync", it).apply()
            // Sync with Config (global or per-game)
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                val updatedConfig = config.copy(videoVsync = it)
                if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                }
            } catch(e: Exception) {}
            onVsyncChanged(it)
        },
        showModifiedIndicator = !vsyncIsDefault
    )
    
    // Video Frame Delay
    var videoFrameDelay by remember(isPerGameMode, gameName, console) { 
        mutableStateOf(
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                config.videoFrameDelay.toFloat()
            } catch (e: Exception) {
                0.0f // Default value
            }
        )
    }
    
    SliderWithLabel(
        label = "Video Frame Delay",
        value = videoFrameDelay,
        onValueChange = { 
            videoFrameDelay = it
            // Save to RetroPlayConfigManager (global or per-game)
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                val updatedConfig = config.copy(videoFrameDelay = it.toInt())
                if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                }
            } catch(e: Exception) {}
        },
        valueRange = 0f..7f,
        displayValue = "${videoFrameDelay.toInt()}",
        activeColor = Color(0xFF4CAF50)
    )
    
    Text(
        text = "Frame delay for video output. Higher values reduce input lag but may cause stuttering. Default: 0",
        color = Color(0xFF888888),
        fontSize = 11.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )
    
    // Video Hard Sync Frames
    var videoHardSyncEnabled by remember(isPerGameMode, gameName, console) { 
        mutableStateOf(
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                config.videoHardSync
            } catch (e: Exception) {
                false // Default value
            }
        )
    }
    
    var videoHardSyncFrames by remember(isPerGameMode, gameName, console) { 
        mutableStateOf(
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                config.videoHardSyncFrames.toFloat()
            } catch (e: Exception) {
                0.0f // Default value
            }
        )
    }
    
    SwitchRow(
        title = "Video Hard Sync",
        subtitle = "Hard synchronization for video output (advanced)",
        checked = videoHardSyncEnabled,
        onCheckedChange = { 
            videoHardSyncEnabled = it
            // Save to RetroPlayConfigManager (global or per-game)
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                val updatedConfig = config.copy(videoHardSync = it)
                if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                }
            } catch(e: Exception) {}
        }
    )
    
    if (videoHardSyncEnabled) {
        SliderWithLabel(
            label = "Hard Sync Frames",
            value = videoHardSyncFrames,
            onValueChange = { 
                videoHardSyncFrames = it
                // Save to RetroPlayConfigManager (global or per-game)
                try {
                    val config = if (isPerGameMode && gameName.isNotEmpty()) {
                        com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName)
                    } else {
                        com.retroplay.config.RetroPlayConfigManager.loadConfig(console)
                    }
                    val updatedConfig = config.copy(videoHardSyncFrames = it.toInt())
                    if (isPerGameMode && gameName.isNotEmpty()) {
                        com.retroplay.config.RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig)
                    } else {
                        com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig)
                    }
                } catch(e: Exception) {}
            },
            valueRange = 0f..7f,
            displayValue = "${videoHardSyncFrames.toInt()}",
            activeColor = Color(0xFFFF9800)
        )
        
        Text(
            text = "Hard sync frames. Adjusts frame timing for hard sync mode. Default: 0",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
    
    // Aspect Ratio
    val aspectRatios = listOf(
        "AUTO" to "Auto (Default)",
        "CORE" to "Core Provided",
        "4:3" to "4:3 (Traditional)",
        "16:9" to "16:9 (Widescreen)",
        "16:10" to "16:10 (Tablet)",
        "18:9" to "18:9 (2:1)",
        "19:9" to "19:9 (Mobile)",
        "19.5:9" to "19.5:9 (Modern Mobile)",
        "20:9" to "20:9 (Tall Mobile)",
        "21:9" to "21:9 (Ultrawide)",
        "1:1" to "1:1 (Square)"
    )
    var expandedAspectRatioMenu by remember { mutableStateOf(false) }
    var selectedAspectRatio by remember { mutableStateOf(prefs.getString("emulation_video_aspect_ratio", "AUTO") ?: "AUTO") }
    val aspectRatioIsDefault = selectedAspectRatio == "AUTO"
    
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Button(
            onClick = { expandedAspectRatioMenu = true },
            colors = ButtonDefaults.buttonColors(containerColor = if (aspectRatioIsDefault) Color(0xFF2A2A2A) else Color(0xFF3A2A1A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Aspect Ratio: ${aspectRatios.find { it.first == selectedAspectRatio }?.second ?: selectedAspectRatio}", color = Color.White)
                Text("▼", color = Color.Gray)
            }
        }
        DropdownMenu(expanded = expandedAspectRatioMenu, onDismissRequest = { expandedAspectRatioMenu = false }) {
            aspectRatios.forEach { (ratio, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { selectedAspectRatio = ratio; expandedAspectRatioMenu = false; prefs.edit().putString("emulation_video_aspect_ratio", ratio).apply(); onAspectRatioChanged(ratio) })
            }
        }
    }
    
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = Color(0xFF444444))
    Spacer(Modifier.height(16.dp))
    
    // Shaders
    Text("Shaders", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    var expandedShaderMenu by remember { mutableStateOf(false) }
    var selectedShaderName by remember { mutableStateOf(prefs.getString("emulation_shader_preset", "DEFAULT") ?: "DEFAULT") }
    val availableShaders = com.retroplay.shader.ShaderManager.ShaderPreset.values()
    val currentShader = com.retroplay.shader.ShaderManager.fromString(selectedShaderName)
    
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Button(
            onClick = { expandedShaderMenu = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Shader: ${currentShader.displayName}", color = Color.White)
                Text("▼", color = Color.Gray)
            }
        }
        DropdownMenu(expanded = expandedShaderMenu, onDismissRequest = { expandedShaderMenu = false }) {
            availableShaders.forEach { shader ->
                DropdownMenuItem(text = { Text("${shader.icon} ${shader.displayName}") }, onClick = { selectedShaderName = shader.name; expandedShaderMenu = false; prefs.edit().putString("emulation_shader_preset", shader.name).apply(); onShaderChanged(shader.name) })
            }
        }
    }
}
