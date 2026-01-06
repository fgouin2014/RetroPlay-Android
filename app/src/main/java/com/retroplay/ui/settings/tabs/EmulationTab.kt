package com.retroplay.ui.settings.tabs

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import com.retroplay.ui.components.SwitchRow
import com.retroplay.ui.components.SliderWithLabel

@Composable
fun EmulationTab(
    console: String,
    prefs: SharedPreferences,
    onAudioMuteChanged: (Boolean) -> Unit,
    onAudioVolumeChanged: (Float) -> Unit,
    onRewindEnabledChanged: (Boolean) -> Unit,
    onPsxAnalogModeChanged: ((Boolean) -> Unit)?,
    debugModeState: MutableState<Boolean>,
    onFastForwardRatioChanged: (Float) -> Unit,
    gameName: String = "", // For per-game config (like saves system)
    isPerGameMode: Boolean = false, // For per-game config
    coreFilePath: String? = null  // Optional core file path for config file name
) {
    // Audio Settings
    Text("Audio Settings", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    
    // Volume Control
    var audioVolume by remember { mutableStateOf(prefs.getFloat("emulation_audio_volume", 1.0f)) }
    val volumeIsDefault = audioVolume == 1.0f
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        SliderWithLabel(
            label = "Volume",
            value = audioVolume,
            onValueChange = { 
                audioVolume = it
                prefs.edit().putFloat("emulation_audio_volume", it).apply()
                onAudioVolumeChanged(it)
            },
            valueRange = 0f..1f,
            displayValue = "${(audioVolume * 100).toInt()}%",
            activeColor = Color(0xFF4CAF50)
        )
        if (!volumeIsDefault) { 
             Spacer(modifier = Modifier.width(8.dp))
             Text("●", color = Color(0xFFFF9800), fontSize = 8.sp) 
        }
    }

    var audioMuted by remember { mutableStateOf(prefs.getBoolean("emulation_audio_muted", false)) }
    val muteIsDefault = !audioMuted
    SwitchRow(
        title = "Mute Audio",
        subtitle = "Disable all audio output",
        checked = audioMuted,
        onCheckedChange = { 
            audioMuted = it
            prefs.edit().putBoolean("emulation_audio_muted", it).apply()
            onAudioMuteChanged(it)
        },
        showModifiedIndicator = !muteIsDefault
    )
    
    var lowLatencyAudio by remember { mutableStateOf(prefs.getBoolean("emulation_audio_low_latency", false)) }
    val lowLatencyIsDefault = !lowLatencyAudio
    SwitchRow(
        title = "Low Latency Audio",
        subtitle = "Use low latency audio mode (reduces delay)",
        checked = lowLatencyAudio,
        onCheckedChange = { 
            lowLatencyAudio = it
            prefs.edit().putBoolean("emulation_audio_low_latency", it).apply()
        },
        showModifiedIndicator = !lowLatencyIsDefault
    )

    Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))
    
    // Rewind & Run-Ahead
    Text("Rewind & Run-Ahead", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    
    var rewindEnabled by remember { mutableStateOf(prefs.getBoolean("emulation_rewind_enable", false)) }
    SwitchRow(
        title = "Rewind",
        subtitle = "Enable rewind functionality",
        checked = rewindEnabled,
        onCheckedChange = { 
            rewindEnabled = it
            prefs.edit().putBoolean("emulation_rewind_enable", it).apply()
            // Sync Config (global or per-game)
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                val updatedConfig = config.copy(rewindEnable = it)
                if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                }
            } catch(e: Exception) {}
            onRewindEnabledChanged(it)
        }
    )
    
    var runAheadEnabled by remember { mutableStateOf(prefs.getBoolean("emulation_runahead_enabled", false)) }
    var runAheadFrames by remember { mutableStateOf(prefs.getInt("emulation_runahead_frames", 1).toFloat()) }
    
    SwitchRow(
        title = "Run-Ahead",
        subtitle = "Reduce input lag by running N frames ahead (requires save state)",
        checked = runAheadEnabled,
        onCheckedChange = { 
            runAheadEnabled = it
            prefs.edit().putBoolean("emulation_runahead_enabled", it).apply()
            // Sync Config (global or per-game)
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                val updatedConfig = config.copy(runAheadEnabled = it)
                // Note: EmulationTab doesn't have gameName, so we can only save global config
                com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig)
            } catch(e: Exception) {}
        }
    )
    
    if (runAheadEnabled) {
        SliderWithLabel(
            label = "Run-Ahead Frames",
            value = runAheadFrames,
            onValueChange = { 
                runAheadFrames = it
                prefs.edit().putInt("emulation_runahead_frames", it.toInt()).apply()
                // Sync Config (global or per-game)
                try {
                    val config = if (isPerGameMode && gameName.isNotEmpty()) {
                        com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName)
                    } else {
                        com.retroplay.config.RetroPlayConfigManager.loadConfig(console)
                    }
                    val updatedConfig = config.copy(runAheadFrames = it.toInt())
                    if (isPerGameMode && gameName.isNotEmpty()) {
                        com.retroplay.config.RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig)
                    } else {
                        com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig)
                    }
                } catch(e: Exception) {}
            },
            valueRange = 1f..10f,
            displayValue = "${runAheadFrames.toInt()} frames",
            activeColor = Color(0xFF4CAF50)
        )
    }
    
    Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))

    // ========== ADVANCED SECTION ==========
    Text(
        text = "Advanced Settings",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    var fastForwardRatio by remember {
        mutableStateOf(prefs.getFloat("emulation_fast_forward_ratio", 2.0f))
    }

    SliderWithLabel(
        label = "Fast Forward Ratio",
        value = fastForwardRatio,
        onValueChange = {
            fastForwardRatio = it
            prefs.edit().putFloat("emulation_fast_forward_ratio", it).apply()
            onFastForwardRatioChanged(it)
        },
        valueRange = 1.0f..10.0f,
        displayValue = "${fastForwardRatio.toInt()}x",
        activeColor = Color(0xFFFF9800)
    )

    SwitchRow(
        title = "Debug Mode",
        subtitle = "Show overlay debug information",
        checked = debugModeState.value,
        onCheckedChange = { debugModeState.value = it }
    )
    
    Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))

    // ========== INPUT TIMING SECTION ==========
    Text(
        text = "Input Timing",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
    
    // Load inputPollTypeInterval from RetroPlayConfigManager
    var inputPollTypeInterval by remember(isPerGameMode, gameName, console) { 
        mutableStateOf(
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                config.inputPollTypeInterval.toFloat()
            } catch (e: Exception) {
                2.0f // Default value
            }
        )
    }
    
    SliderWithLabel(
        label = "Input Poll Type Interval",
        value = inputPollTypeInterval,
        onValueChange = { 
            inputPollTypeInterval = it
            // Save to RetroPlayConfigManager (global or per-game)
            try {
                val config = if (isPerGameMode && gameName.isNotEmpty()) {
                    com.retroplay.config.RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                } else {
                    com.retroplay.config.RetroPlayConfigManager.loadConfig(console, coreFilePath)
                }
                val updatedConfig = config.copy(inputPollTypeInterval = it.toInt())
                // Note: EmulationTab doesn't have gameName, so we can only save global config
                com.retroplay.config.RetroPlayConfigManager.saveConfig(console, updatedConfig)
            } catch(e: Exception) {}
        },
        valueRange = 0f..10f,
        displayValue = "${inputPollTypeInterval.toInt()}",
        activeColor = Color(0xFF2196F3)
    )
    
    Text(
        text = "Input polling interval. Lower values reduce input lag but may impact performance. Default: 2",
        color = Color(0xFF888888),
        fontSize = 11.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )
    
    Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))

    // ========== INPUT SETTINGS (HOTKEYS) SECTION ==========
    Text(
        text = "Input Settings (Hotkeys)",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = "Hotkeys are configured in overlay .cfg files. Available hotkeys:",
        color = Color(0xFF888888),
        fontSize = 12.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Liste des hotkeys disponibles
    val hotkeys = listOf(
        "Save State" to "save_state",
        "Load State" to "load_state",
        "State Slot +" to "state_slot_increase",
        "State Slot -" to "state_slot_decrease",
        "Fast Forward" to "toggle_fast_forward",
        "Rewind" to "rewind",
        "Reset" to "reset",
        "Pause Toggle" to "pause_toggle",
        "Audio Mute" to "audio_mute_toggle",
        "Shader Next" to "shader_next",
        "Shader Prev" to "shader_prev",
        "Screenshot" to "screenshot",
        "Frame Advance" to "frame_advance"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        hotkeys.forEach { (displayName, action) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = action,
                        color = Color(0xFF888888),
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "✓",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Note: Hotkeys are triggered from overlay buttons. To configure hotkeys, edit the overlay .cfg file or use overlay buttons.",
        color = Color(0xFF666666),
        fontSize = 11.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )

    Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))

    // Note: Controller Ports are configured in the "Input" tab to avoid duplication
    
    val psxVariants = listOf("psx", "playstation", "ps1", "pcsx_rearmed", "beetle_psx", "duckstation", "swanstation", "pcsx")
    if (psxVariants.any { console.equals(it, ignoreCase = true) }) {
         Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color(0xFF444444)); Spacer(Modifier.height(16.dp))
         Text("PlayStation Settings", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
         var psxAnalogMode by remember { mutableStateOf(prefs.getBoolean("psx_analog_mode_enabled", true)) }
         SwitchRow(
             title = "Enable Analog Mode (DualShock)",
             subtitle = "Activate analog sticks on PSX controller",
             checked = psxAnalogMode,
             onCheckedChange = { 
                 psxAnalogMode = it
                 prefs.edit().putBoolean("psx_analog_mode_enabled", it).apply()
                 onPsxAnalogModeChanged?.invoke(it)
             }
         )
    }
}
