package com.retroplay.ui.dialogs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.retroplay.GamePadLayoutManager
import com.retroplay.config.RetroPlayConfigManager
import com.retroplay.ui.components.*
import com.retroplay.ui.settings.tabs.EmulationTab
import com.retroplay.ui.settings.tabs.GraphicsTab
import com.retroplay.ui.components.SwitchRow
import com.retroplay.ui.components.SliderWithLabel
import com.retroplay.ui.components.RadialSlider
import com.retroplay.ui.components.AdvancedVisibilitySettingsContent
import com.retroplay.input.PadKitHelper
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import kotlin.math.roundToInt

/**
 * Unified Settings Dialog - Consolidates all settings in one place
 * 
 * Structure:
 * - Tab 0: Overlays (visibility, layouts, position)
 * - Tab 1: Input (controller ports, zapper, hotkeys)
 * - Tab 2: Emulation (rewind, run-ahead, vsync, audio) with Global/Per-Game toggle
 * - Tab 3: Video (vsync, aspect ratio, shaders)
 * - Tab 4: Core Options (core variables, DIP switches)
 * - Tab 5: Advanced (debug, overlay advanced)
 */
@Composable
fun UnifiedSettingsDialog(
    console: String,
    gameCRC: String? = null,
    gameName: String = "",
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    onLoadCustomCfg: (() -> Unit)? = null,
    debugModeState: MutableState<Boolean>,
    currentVariant: GamePadLayoutManager.LayoutVariant,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onShaderChanged: (String) -> Unit = {},
    onFastForwardRatioChanged: (Float) -> Unit = {},
    onAudioVolumeChanged: (Float) -> Unit = {},
    onAudioMuteChanged: (Boolean) -> Unit = {},
    onVsyncChanged: (Boolean) -> Unit = {},
    onRewindEnabledChanged: (Boolean) -> Unit = {},
    onAspectRatioChanged: (String) -> Unit = {},
    onPsxAnalogModeChanged: ((Boolean) -> Unit)? = null,
    coreOptions: List<com.retroplay.CoreVariable> = emptyList(),
    onCoreOptionsChanged: ((Map<String, String>) -> Unit)? = null,
    onZapperConfigChanged: (() -> Unit)? = null,  // Callback pour appliquer config zapper au runtime
    initialTab: Int = 0,  // 0=Overlays, 1=Input, 2=Emulation, 3=Video, 4=Advanced
    coreFilePath: String? = null,  // Optional core file path for config file name (e.g. "fceumm_libretro_android.so")
    romPath: String = ""  // CRITIQUE: Chemin complet du fichier ROM pour extraire le gameId correct
) {
    // Tab selection (0-4) - Use initialTab if provided
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    
    // Global/Per-Game toggle for Emulation tab
    var isPerGameMode by remember { mutableStateOf(gameCRC != null && RetroPlayConfigManager.hasGameConfig(console, gameName)) }
    
    // État partagé pour les core options modifiées (accessible depuis les boutons Apply/Save)
    var currentCoreOptionsValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // État converti pour les overlays (accessible depuis les boutons Apply/Save)
    var currentOverlayPreference by remember { mutableStateOf<com.retroplay.overlay.models.OverlayPreference?>(null) }
    
    // Load config (global or per-game)
    // Use coreFilePath to determine config file name (e.g. fceumm.cfg instead of retroplay.cfg)
    val effectiveConfig = remember(console, gameName, gameCRC, isPerGameMode, coreFilePath) {
        if (isPerGameMode && gameName.isNotEmpty()) {
            RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
        } else {
            RetroPlayConfigManager.loadConfig(console, coreFilePath)
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "⚙️ SETTINGS",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        if (gameName.isNotEmpty()) {
                            Text(
                                text = gameName,
                                fontSize = 12.sp,
                                color = Color(0xFF9E9E9E)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Color.White, fontSize = 20.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(8.dp))
                
                // Tab Rows (2 rows pour éviter manque d'espace) - Amélioration contraste
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Row 1: Overlays, Input, Emulation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTab == 0) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            text = { 
                                Text(
                                    "Overlays", 
                                    fontSize = 11.sp, 
                                    maxLines = 1,
                                    color = if (selectedTab == 0) Color.White else Color(0xFFCCCCCC),
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTab == 1) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            text = { 
                                Text(
                                    "Input", 
                                    fontSize = 11.sp, 
                                    maxLines = 1,
                                    color = if (selectedTab == 1) Color.White else Color(0xFFCCCCCC),
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTab == 2) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            text = { 
                                Text(
                                    "Emulation", 
                                    fontSize = 11.sp, 
                                    maxLines = 1,
                                    color = if (selectedTab == 2) Color.White else Color(0xFFCCCCCC),
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            }
                        )
                    }
                    // Row 2: Video, Core Options (si disponible), Advanced
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTab == 3) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            text = { 
                                Text(
                                    "Video", 
                                    fontSize = 11.sp, 
                                    maxLines = 1,
                                    color = if (selectedTab == 3) Color.White else Color(0xFFCCCCCC),
                                    fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            }
                        )
                        if (coreOptions.isNotEmpty() && onCoreOptionsChanged != null) {
                            Tab(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selectedTab == 4) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                text = { 
                                    Text(
                                        "Core Options", 
                                        fontSize = 11.sp, 
                                        maxLines = 1,
                                        color = if (selectedTab == 4) Color.White else Color(0xFFCCCCCC),
                                        fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    ) 
                                }
                            )
                        } else {
                            // Espace vide si Core Options n'est pas disponible
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Tab(
                            selected = selectedTab == 5,
                            onClick = { selectedTab = 5 },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selectedTab == 5) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            text = { 
                                Text(
                                    "Advanced", 
                                    fontSize = 11.sp, 
                                    maxLines = 1,
                                    color = if (selectedTab == 5) Color.White else Color(0xFFCCCCCC),
                                    fontWeight = if (selectedTab == 5) FontWeight.Bold else FontWeight.Normal,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                ) 
                            }
                        )
                    }
                }
                
                HorizontalDivider(color = Color(0xFF444444))
                
                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // Tab 0: Overlays
                                OverlaysTabContent(
                                    console = console,
                                    prefs = prefs,
                                    context = context,
                                    onLoadCustomCfg = onLoadCustomCfg,
                                    currentVariant = currentVariant,
                                    onVariantChanged = onVariantChanged,
                                    onOverlayChanged = { currentOverlayPreference = it }
                                )
                            }
                            1 -> {
                                // Tab 1: Input (Controller Ports + Zapper + Hotkeys)
                                InputTabContent(
                                    console = console,
                                    prefs = prefs,
                                    effectiveConfig = effectiveConfig,
                                    gameCRC = gameCRC,
                                    gameName = gameName,
                                    isPerGameMode = isPerGameMode,
                                    onIsPerGameModeChanged = { isPerGameMode = it },
                                    onZapperConfigChanged = onZapperConfigChanged,
                                    coreFilePath = coreFilePath
                                )
                            }
                            2 -> {
                                // Tab 2: Emulation (with Global/Per-Game toggle)
                                EmulationTabContent(
                                    console = console,
                                    prefs = prefs,
                                    gameCRC = gameCRC,
                                    gameName = gameName,
                                    isPerGameMode = isPerGameMode,
                                    onIsPerGameModeChanged = { isPerGameMode = it },
                                    effectiveConfig = effectiveConfig,
                                    onRewindEnabledChanged = onRewindEnabledChanged,
                                    onFastForwardRatioChanged = onFastForwardRatioChanged,
                                    onAudioVolumeChanged = onAudioVolumeChanged,
                                    onAudioMuteChanged = onAudioMuteChanged,
                                    onPsxAnalogModeChanged = onPsxAnalogModeChanged,
                                    coreFilePath = coreFilePath
                                )
                            }
                            3 -> {
                                // Tab 3: Video
                                GraphicsTab(
                                    prefs = prefs,
                                    onVsyncChanged = onVsyncChanged,
                                    onAspectRatioChanged = onAspectRatioChanged,
                                    onShaderChanged = onShaderChanged,
                                    console = console,
                                    gameName = gameName,
                                    isPerGameMode = isPerGameMode,
                                    coreFilePath = coreFilePath
                                )
                            }
                            4 -> {
                                // Tab 4: Core Options (with Global/Per-Game toggle)
                                CoreOptionsTabContent(
                                    coreOptions = coreOptions,
                                    context = context,
                                    gameName = gameName,
                                    coreFilePath = coreFilePath,
                                    romPath = romPath,
                                    isPerGameMode = isPerGameMode,
                                    onIsPerGameModeChanged = { isPerGameMode = it },
                                    onValuesChanged = { currentCoreOptionsValues = it }
                                )
                            }
                            5 -> {
                                // Tab 5: Advanced
                                AdvancedTabContent(
                                    console = console,
                                    prefs = prefs,
                                    debugModeState = debugModeState
                                )
                            }
                        }
                    }
                }
                
                // Bottom buttons (Save/Cancel)
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF444444))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton DEFAULT
                    OutlinedButton(
                        onClick = {
                            android.util.Log.i("UnifiedSettings", "🔄 DEFAULT button clicked for tab $selectedTab")
                            
                            when (selectedTab) {
                                0 -> {
                                    // Tab 0: Overlays - Réinitialiser les préférences d'overlay
                                    // Note: Les overlays n'ont pas vraiment de "default" simple
                                    // On pourrait réinitialiser à la variante par défaut ou les prefs
                                    android.widget.Toast.makeText(
                                        context,
                                        "Overlay defaults not implemented",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                1 -> {
                                    // Tab 1: Input - Réinitialiser les ports de contrôleurs et config zapper
                                    val consoleConfigPrefs = context.getSharedPreferences("console_config", Context.MODE_PRIVATE)
                                    val composePrefs = context.getSharedPreferences("compose_gamepad_settings", Context.MODE_PRIVATE)
                                    val editor1 = consoleConfigPrefs.edit()
                                    val editor2 = composePrefs.edit()
                                    
                                    // Réinitialiser tous les ports à -1 (Auto)
                                    for (port in 0..3) {
                                        val key = "controller_port_${console}_port${port}"
                                        editor1.remove(key)
                                        editor2.remove(key)
                                    }
                                    editor1.apply()
                                    editor2.apply()
                                    
                                    // Réinitialiser uniquement la partie Input de la config (zapper, etc.)
                                    val currentConfig = if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                    }
                                    val defaultInputConfig = currentConfig.copy(
                                        zapperEnabled = false,
                                        zapperPort = 1,
                                        zapperTriggerDelay = 0,
                                        zapperTriggerOnTouch = true,
                                        zapperPulseDuration = 50,
                                        zapperAllowOffscreen = false
                                    )
                                    if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.saveGameConfig(console, gameName, defaultInputConfig, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.saveConfig(console, defaultInputConfig, coreFilePath)
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        "Input settings reset to defaults",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                2 -> {
                                    // Tab 2: Emulation - Réinitialiser la config d'émulation
                                    val currentConfig = if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                    }
                                    val defaultEmulationConfig = RetroPlayConfigManager.RetroPlayConfig().copy(
                                        // Garder les autres valeurs mais réinitialiser l'émulation
                                        zapperEnabled = currentConfig.zapperEnabled,
                                        zapperPort = currentConfig.zapperPort,
                                        zapperTriggerDelay = currentConfig.zapperTriggerDelay,
                                        zapperTriggerOnTouch = currentConfig.zapperTriggerOnTouch,
                                        zapperPulseDuration = currentConfig.zapperPulseDuration,
                                        zapperAllowOffscreen = currentConfig.zapperAllowOffscreen,
                                        videoVsync = currentConfig.videoVsync,
                                        videoFrameDelay = currentConfig.videoFrameDelay,
                                        videoHardSyncFrames = currentConfig.videoHardSyncFrames,
                                        inputPollTypeInterval = currentConfig.inputPollTypeInterval
                                    )
                                    if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.saveGameConfig(console, gameName, defaultEmulationConfig, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.saveConfig(console, defaultEmulationConfig, coreFilePath)
                                    }
                                    
                                    // Réinitialiser aussi les prefs audio
                                    prefs.edit().apply {
                                        putFloat("emulation_audio_volume", 1.0f)
                                        putBoolean("emulation_audio_muted", false)
                                        putBoolean("emulation_audio_low_latency", false)
                                        putFloat("emulation_fast_forward_ratio", 2.0f)
                                        putBoolean("emulation_rewind_enabled", false)
                                        apply()
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        "Emulation settings reset to defaults",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                3 -> {
                                    // Tab 3: Video - Réinitialiser la config vidéo
                                    val currentConfig = if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                    }
                                    val defaultVideoConfig = currentConfig.copy(
                                        videoVsync = true,
                                        videoFrameDelay = 0,
                                        videoHardSyncFrames = 0
                                    )
                                    if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.saveGameConfig(console, gameName, defaultVideoConfig, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.saveConfig(console, defaultVideoConfig, coreFilePath)
                                    }
                                    
                                    // Réinitialiser le shader
                                    prefs.edit().putString("emulation_shader", "none").apply()
                                    onShaderChanged("none")
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        "Video settings reset to defaults",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                4 -> {
                                    // Tab 4: Core Options - Réinitialiser aux valeurs par défaut du core
                                    if (coreOptions.isNotEmpty() && coreFilePath != null) {
                                        val coreId = com.retroplay.CoreVariableManager.extractCoreId(coreFilePath)
                                        val gameId = if (isPerGameMode && romPath.isNotEmpty()) {
                                            java.io.File(romPath).nameWithoutExtension
                                        } else {
                                            "global"
                                        }
                                        
                                        // Supprimer les valeurs sauvegardées pour revenir aux defaults
                                        try {
                                            val prefs = context.getSharedPreferences("core_variables_${gameId}_${coreId}", Context.MODE_PRIVATE)
                                            prefs.edit().clear().apply()
                                            android.util.Log.i("UnifiedSettings", "🔄 Cleared saved core options for $gameId/$coreId")
                                            
                                            // Recharger les valeurs par défaut (currentValue du core)
                                            val defaultValues = coreOptions.associate { it.key to it.currentValue }
                                            
                                            // Mettre à jour l'UI
                                            // Note: On devrait recharger le tab, mais pour l'instant on notifie juste
                                            android.widget.Toast.makeText(
                                                context,
                                                "Core options reset to defaults (restart to apply)",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            android.util.Log.e("UnifiedSettings", "❌ Error resetting core options: ${e.message}", e)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Error resetting core options",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "No core options to reset",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                5 -> {
                                    // Tab 5: Advanced - Réinitialiser les prefs avancées
                                    prefs.edit().apply {
                                        putBoolean("debug_mode_enabled", false)
                                        putBoolean("overlay_hide_in_menu", false)
                                        putBoolean("overlay_hide_when_gamepad_connected", false)
                                        putBoolean("overlay_hide_when_gamepad_connected_port0_only", false)
                                        putFloat("overlay_opacity", 1.0f)
                                        putInt("overlay_dpad_diagonal_sensitivity", 50)
                                        putInt("overlay_abxy_diagonal_sensitivity", 50)
                                        putInt("overlay_analog_recenter_zone", 0)
                                        putFloat("overlay_aspect_adjust", 0.0f)
                                        apply()
                                    }
                                    debugModeState.value = false
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        "Advanced settings reset to defaults",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF9800)  // Orange pour "default"
                        )
                    ) {
                        Text("DEFAULT", fontSize = 13.sp)
                    }
                    
                    // Bouton APPLY
                    OutlinedButton(
                        onClick = {
                            android.util.Log.i("UnifiedSettings", "🔘 APPLY button clicked for tab $selectedTab")
                            
                            when (selectedTab) {
                                0 -> {
                                    // Tab 0: Overlays - Sauvegarder pour appliquer (listener Activity)
                                    if (currentOverlayPreference != null) {
                                        com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, currentOverlayPreference!!)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Overlay settings applied",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "No overlay changes to apply",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                1 -> {
                                    // Tab 1: Input - Appliquer config zapper
                                    if (console == "nes") {
                                        onZapperConfigChanged?.invoke()
                                        android.util.Log.i("UnifiedSettings", "✅ Zapper config applied")
                                    }
                                    android.widget.Toast.makeText(
                                        context,
                                        "Input settings applied (not saved)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                2 -> {
                                    // Tab 2: Emulation - Les callbacks sont déjà appelés en temps réel
                                    // Rien à appliquer ici
                                    android.widget.Toast.makeText(
                                        context,
                                        "Emulation settings applied (already active)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                3 -> {
                                    // Tab 3: Video - Les callbacks sont déjà appelés en temps réel
                                    // Rien à appliquer ici
                                    android.widget.Toast.makeText(
                                        context,
                                        "Video settings applied (already active)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                4 -> {
                                    // Tab 4: Core Options - Appliquer au runtime
                                    if (currentCoreOptionsValues.isNotEmpty() && onCoreOptionsChanged != null) {
                                        android.util.Log.i("UnifiedSettings", "🔄 APPLY: Applying ${currentCoreOptionsValues.size} core options to runtime (not saving)")
                                        currentCoreOptionsValues.forEach { (key, value) ->
                                            android.util.Log.d("UnifiedSettings", "  - $key = $value")
                                        }
                                        onCoreOptionsChanged.invoke(currentCoreOptionsValues)
                                        android.util.Log.i("UnifiedSettings", "✅ APPLY: onCoreOptionsChanged callback invoked")
                                        android.widget.Toast.makeText(
                                            context,
                                            "Core options applied (not saved)",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "No core options to apply",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                5 -> {
                                    // Tab 5: Advanced - Les prefs sont déjà sauvegardées en temps réel
                                    android.widget.Toast.makeText(
                                        context,
                                        "Advanced settings applied (already active)",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF4CAF50)  // Vert pour "apply"
                        )
                    ) {
                        Text("APPLY", fontSize = 13.sp)
                    }
                    
                    // Bouton SAVE
                    Button(
                        onClick = {
                            android.util.Log.i("UnifiedSettings", "💾 SAVE button clicked for tab $selectedTab")
                            
                            when (selectedTab) {
                                0 -> {
                                    // Tab 0: Overlays - Sauvegarder les préférences
                                    if (currentOverlayPreference != null) {
                                        com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, currentOverlayPreference!!)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Overlay settings saved",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        onDismiss()
                                    } else {
                                        // Si null, c'est qu'on n'a pas encore chargé/modifié, on ferme juste
                                        onDismiss()
                                    }
                                }
                                1 -> {
                                    // Tab 1: Input - Sauvegarder config RetroPlay + appliquer zapper
                                    val currentConfig = if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                    }
                                    
                                    if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.saveGameConfig(console, gameName, currentConfig, coreFilePath)
                                        android.util.Log.i("UnifiedSettings", "💾 Per-game input config saved: $console/$gameName")
                                    } else {
                                        RetroPlayConfigManager.saveConfig(console, currentConfig, coreFilePath)
                                        android.util.Log.i("UnifiedSettings", "💾 Global input config saved")
                                    }
                                    
                                    // Appliquer la config zapper au runtime
                                    if (console == "nes") {
                                        onZapperConfigChanged?.invoke()
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isPerGameMode) "Input config saved: $gameName.cfg" else "Input config saved",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                2 -> {
                                    // Tab 2: Emulation - Sauvegarder config RetroPlay
                                    val currentConfig = if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                    }
                                    
                                    if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.saveGameConfig(console, gameName, currentConfig, coreFilePath)
                                        android.util.Log.i("UnifiedSettings", "💾 Per-game emulation config saved: $console/$gameName")
                                    } else {
                                        RetroPlayConfigManager.saveConfig(console, currentConfig, coreFilePath)
                                        android.util.Log.i("UnifiedSettings", "💾 Global emulation config saved")
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isPerGameMode) "Emulation config saved: $gameName.cfg" else "Emulation config saved",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                3 -> {
                                    // Tab 3: Video - Sauvegarder config RetroPlay
                                    val currentConfig = if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                    } else {
                                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                    }
                                    
                                    if (isPerGameMode && gameName.isNotEmpty()) {
                                        RetroPlayConfigManager.saveGameConfig(console, gameName, currentConfig, coreFilePath)
                                        android.util.Log.i("UnifiedSettings", "💾 Per-game video config saved: $console/$gameName")
                                    } else {
                                        RetroPlayConfigManager.saveConfig(console, currentConfig, coreFilePath)
                                        android.util.Log.i("UnifiedSettings", "💾 Global video config saved")
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isPerGameMode) "Video config saved: $gameName.cfg" else "Video config saved",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                4 -> {
                                    // Tab 4: Core Options - Sauvegarder et appliquer
                                    if (currentCoreOptionsValues.isNotEmpty() && coreFilePath != null) {
                                        val coreId = com.retroplay.CoreVariableManager.extractCoreId(coreFilePath)
                                        val gameId = if (isPerGameMode && romPath.isNotEmpty()) {
                                            java.io.File(romPath).nameWithoutExtension
                                        } else {
                                            "global"
                                        }
                                        
                                        android.util.Log.i("UnifiedSettings", "💾 SAVE: Saving ${currentCoreOptionsValues.size} core options (gameId=$gameId, coreId=$coreId, mode=${if (isPerGameMode) "Per-Game" else "Global"})")
                                        try {
                                            com.retroplay.CoreVariableManager.saveVariables(
                                                context,
                                                gameId,
                                                coreId,
                                                currentCoreOptionsValues
                                            )
                                            android.util.Log.i("UnifiedSettings", "✅ Core options saved successfully")
                                            // CRITIQUE: Mettre à jour savedValues pour refléter les nouvelles valeurs sauvegardées
                                            // Note: savedValues est dans CoreOptionsTabContent, on ne peut pas le mettre à jour directement ici
                                            // Le LaunchedEffect dans CoreOptionsTabContent se déclenchera au prochain rendu
                                        } catch (e: Exception) {
                                            android.util.Log.e("UnifiedSettings", "❌ Error saving core options: ${e.message}", e)
                                        }
                                    }
                                    
                                    // Appliquer les core options au runtime
                                    if (currentCoreOptionsValues.isNotEmpty() && onCoreOptionsChanged != null) {
                                        android.util.Log.i("UnifiedSettings", "🔄 SAVE: Applying ${currentCoreOptionsValues.size} core options to runtime")
                                        onCoreOptionsChanged.invoke(currentCoreOptionsValues)
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isPerGameMode) "Core options saved: Per-Game" else "Core options saved: Global",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                5 -> {
                                    // Tab 5: Advanced - Les prefs sont déjà sauvegardées en temps réel
                                    android.widget.Toast.makeText(
                                        context,
                                        "Advanced settings saved",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = "💾 SAVE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Tab 0: Overlays Content
@Composable
private fun OverlaysTabContent(
    console: String,
    prefs: SharedPreferences,
    context: Context,
    onLoadCustomCfg: (() -> Unit)?,
    currentVariant: GamePadLayoutManager.LayoutVariant,
    onVariantChanged: (GamePadLayoutManager.LayoutVariant) -> Unit,
    onOverlayChanged: (com.retroplay.overlay.models.OverlayPreference) -> Unit
) {
    // Migrate from GamePadSettingsDialog - use GamePadVisibilitySettings component
    val assetManager = remember { 
        try {
            com.retroplay.overlay.assets.OverlayAssetManager(context)
        } catch (e: Exception) {
            android.util.Log.e("UnifiedSettings", "Error creating OverlayAssetManager: ${e.message}", e)
            null
        }
    }
    val overlayPackages = remember(assetManager, console) { 
        try {
            assetManager?.getCompatibleOverlays(console) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("UnifiedSettings", "Error getting compatible overlays: ${e.message}", e)
            emptyList()
        }
    }
    var customBrowsed by remember { 
        mutableStateOf(
            try {
                com.retroplay.overlay.models.OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
            } catch (e: Exception) {
                android.util.Log.e("UnifiedSettings", "Error getting custom browsed list: ${e.message}", e)
                emptyList()
            }
        )
    }
    var availableLayoutsLoading by remember { mutableStateOf(false) }
    var availableLayoutsCache by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val currentOverlayPref = remember(prefs, console) { 
        try {
            com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console)
        } catch (e: Exception) {
            android.util.Log.e("UnifiedSettings", "Error loading overlay preference: ${e.message}", e)
            null
        }
    }
    var selectedOverlay by remember(currentOverlayPref, overlayPackages) {
        mutableStateOf(
            currentOverlayPref?.overlayName ?: overlayPackages.firstOrNull().orEmpty()
        )
    }
    var selectedCustomPath by remember(currentOverlayPref) {
        mutableStateOf(
            currentOverlayPref?.customCfgName?.let { "${currentOverlayPref.overlayName}/$it" }
        )
    }
    var selectedLandscapeLayout by remember(currentOverlayPref) {
        mutableStateOf(currentOverlayPref?.landscapeLayout ?: "landscape-A")
    }
    var selectedPortraitLayout by remember(currentOverlayPref) {
        mutableStateOf(currentOverlayPref?.portraitLayout ?: "portrait-A")
    }
    var autoRotate by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.autoRotate ?: true) }
    var swapAnalogSticks by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.swapAnalogSticks ?: false) }
    var invertAnalogLeftY by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.invertAnalogLeftY ?: false) }
    var invertAnalogRightY by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.invertAnalogRightY ?: false) }
    var scale by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.scale ?: 1.0f) }
    var xOffset by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.xOffset ?: 0.0f) }
    var yOffset by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.yOffset ?: 0.0f) }
    var xSeparation by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.xSeparation ?: 0.0f) }
    var ySeparation by remember(currentOverlayPref) { mutableStateOf(currentOverlayPref?.ySeparation ?: 0.0f) }
    
    // Load available layouts asynchronously
    LaunchedEffect(selectedOverlay, assetManager) {
        if (assetManager != null && availableLayoutsCache.isEmpty() && !availableLayoutsLoading) {
            availableLayoutsLoading = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val layouts = assetManager.getAvailableLayouts(console, selectedOverlay)
                    availableLayoutsCache = layouts
                } catch (e: Exception) {
                    android.util.Log.e("UnifiedSettings", "Error loading layouts: ${e.message}", e)
                } finally {
                    availableLayoutsLoading = false
                }
            }
        }
    }
    
    // Track state changes and report to parent
    LaunchedEffect(
        selectedOverlay,
        selectedCustomPath,
        selectedLandscapeLayout,
        selectedPortraitLayout,
        autoRotate,
        swapAnalogSticks,
        invertAnalogLeftY,
        invertAnalogRightY,
        scale,
        xOffset,
        yOffset,
        xSeparation,
        ySeparation
    ) {
        if (selectedOverlay.isNotEmpty()) {
            val customCfgName = selectedCustomPath?.substringAfter("/")
            val pref = com.retroplay.overlay.models.OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                customCfgName = customCfgName,
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogLeftY = invertAnalogLeftY,
                invertAnalogRightY = invertAnalogRightY,
                scale = scale,
                xOffset = xOffset,
                yOffset = yOffset,
                xSeparation = xSeparation,
                ySeparation = ySeparation
            )
            // Restore Auto-Save for Live Preview
            // The user expects the overlay to update immediately when changing settings.
            // Since the renderer listens to SharedPreferences, we must save to trigger the update.
            com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, pref)
            
            // Still update parent state for the SAVE/APPLY buttons
            onOverlayChanged(pref)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GamePad Variant Selection
        Text(
            text = "Layout Selection",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )
        
        // Variant Tabs - Reload when console changes
        val availableVariants = remember(console) { 
            GamePadLayoutManager.getAvailableVariants(console)
        }
        
        // Debug: Log available variants
        LaunchedEffect(console, availableVariants) {
            android.util.Log.d("UnifiedSettings", "Available variants for $console: ${availableVariants.map { it.first.name }}")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableVariants.forEach { (variant, displayName) ->
                Button(
                    onClick = { onVariantChanged(variant) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentVariant == variant) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                        contentColor = if (currentVariant == variant) Color.White else Color(0xFFCCCCCC)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = displayName,
                        fontSize = 12.sp,
                        fontWeight = if (currentVariant == variant) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Show RetroArch overlay settings only if RetroArch variant is selected
        if (currentVariant == GamePadLayoutManager.LayoutVariant.RETROARCH) {
            if (assetManager != null) {
                GamePadVisibilitySettings(
                    console = console,
                    prefs = prefs,
                    assetManager = assetManager,
                    overlayPackages = overlayPackages,
                customBrowsed = customBrowsed,
                availableLayouts = availableLayoutsCache,
                selectedOverlay = selectedOverlay,
                selectedCustomPath = selectedCustomPath,
                selectedLandscapeLayout = selectedLandscapeLayout,
                selectedPortraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogLeftY = invertAnalogLeftY,
                invertAnalogRightY = invertAnalogRightY,
                scale = scale,
                xOffset = xOffset,
                yOffset = yOffset,
                xSeparation = xSeparation,
                ySeparation = ySeparation,
                onSelectedOverlayChanged = { selectedOverlay = it },
                onSelectedCustomPathChanged = { selectedCustomPath = it },
                onSelectedLandscapeLayoutChanged = { selectedLandscapeLayout = it },
                onSelectedPortraitLayoutChanged = { selectedPortraitLayout = it },
                onAutoRotateChanged = { autoRotate = it },
                onSwapAnalogSticksChanged = { swapAnalogSticks = it },
                onInvertAnalogLeftYChanged = { invertAnalogLeftY = it },
                onInvertAnalogRightYChanged = { invertAnalogRightY = it },
                onScaleChanged = { scale = it },
                onXOffsetChanged = { xOffset = it },
                onYOffsetChanged = { yOffset = it },
                onXSeparationChanged = { xSeparation = it },
                onYSeparationChanged = { ySeparation = it },
                onCustomBrowsedUpdated = {
                    customBrowsed = com.retroplay.overlay.models.OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
                },
                onLoadCustomCfg = onLoadCustomCfg
                )
            } else {
                Text(
                    text = "Error loading overlay assets. Please restart the app.",
                    color = Color(0xFFFF0000),
                    fontSize = 14.sp
                )
            }
        } else {
            // Radial Menu Settings - Only show if NOT RetroArch
            if (currentVariant != GamePadLayoutManager.LayoutVariant.RETROARCH) {
                RadialMenuSettingsContent(
                    console = console,
                    prefs = prefs
                )
            }
        }
    }
}

// Tab 1: Input Content (Controller Ports + Zapper + Hotkeys)
@Composable
private fun InputTabContent(
    console: String,
    prefs: SharedPreferences,
    effectiveConfig: RetroPlayConfigManager.RetroPlayConfig,
    gameCRC: String?,
    gameName: String,  // Added for config system (like saves)
    isPerGameMode: Boolean,
    onIsPerGameModeChanged: (Boolean) -> Unit,
    onZapperConfigChanged: (() -> Unit)? = null,  // Callback pour appliquer config zapper au runtime
    coreFilePath: String? = null  // Optional core file path for config file name
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Controller Ports Section
        Text(
            text = "Controller Ports",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )
        
        // Port 1
        ControllerPortRow(
            label = "Port 1 (Player 1)",
            console = console,
            port = 0,
            prefs = prefs
        )
        
        // Port 2
        ControllerPortRow(
            label = "Port 2 (Player 2)",
            console = console,
            port = 1,
            prefs = prefs
        )
        
        // Port 3-4 if supported
        if (console.lowercase() in listOf("psx", "n64", "snes")) {
            ControllerPortRow(
                label = "Port 3 (Player 3)",
                console = console,
                port = 2,
                prefs = prefs
            )
            ControllerPortRow(
                label = "Port 4 (Player 4)",
                console = console,
                port = 3,
                prefs = prefs
            )
        }
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Zapper Configuration Section (if NES or if zapper configured)
        if (console.lowercase().contains("nes") || isZapperConfigured(console, prefs)) {
            Text(
                text = "Zapper Configuration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800)
            )
            
            // Load zapper config from RetroPlayConfigManager
            var zapperEnabled by remember { 
                mutableStateOf(effectiveConfig.zapperEnabled)
            }
            var zapperPort by remember { 
                mutableStateOf(effectiveConfig.zapperPort)
            }
            var zapperTriggerDelay by remember { 
                mutableStateOf(effectiveConfig.zapperTriggerDelay)
            }
            var zapperTriggerOnTouch by remember { 
                mutableStateOf(effectiveConfig.zapperTriggerOnTouch)
            }
            var zapperPulseDuration by remember { 
                mutableStateOf(effectiveConfig.zapperPulseDuration)
            }
            var zapperAllowOffscreen by remember { 
                mutableStateOf(effectiveConfig.zapperAllowOffscreen)
            }
            
            // Enable Zapper Globally
            SwitchRow(
                title = "Enable Zapper Globally",
                subtitle = "Activate zapper for all games",
                checked = zapperEnabled,
                onCheckedChange = { 
                    zapperEnabled = it
                    // Save to config
                    val config = if (isPerGameMode && gameName.isNotEmpty()) {
                        RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                    } else {
                        RetroPlayConfigManager.loadConfig(console, coreFilePath)
                    }
                    val updatedConfig = config.copy(zapperEnabled = it)
                    if (isPerGameMode && gameName.isNotEmpty()) {
                        RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                    } else {
                        RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                    }
                    
                    // CRITIQUE: Appliquer la config zapper au runtime si changée
                    if (it && console == "nes") {
                        onZapperConfigChanged?.invoke()
                    }
                }
            )
            
            if (zapperEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Default Port
                var expandedPortMenu by remember { mutableStateOf(false) }
                val portOptions = listOf("Port 1" to 0, "Port 2" to 1)
                val selectedPortName = portOptions.find { it.second == zapperPort }?.first ?: "Port 2"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default Port", color = Color.White, fontSize = 14.sp)
                    Box {
                        Button(
                            onClick = { expandedPortMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                        ) {
                            Text(selectedPortName, color = Color.White, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = expandedPortMenu,
                            onDismissRequest = { expandedPortMenu = false }
                        ) {
                            portOptions.forEach { (name, port) ->
                                DropdownMenuItem(
                                    text = { Text(name, color = Color.White) },
                                    onClick = {
                                        zapperPort = port
                                        expandedPortMenu = false
                                        // Save
                                        val config = if (isPerGameMode && gameName.isNotEmpty()) {
                                            RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                                        } else {
                                            RetroPlayConfigManager.loadConfig(console, coreFilePath)
                                        }
                                        val updatedConfig = config.copy(zapperPort = port)
                                        if (isPerGameMode && gameName.isNotEmpty()) {
                                            RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                                        } else {
                                            RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                                        }
                                        
                                        // CRITIQUE: Appliquer la config zapper au runtime
                                        if (console == "nes") {
                                            onZapperConfigChanged?.invoke()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Trigger Delay
                SliderWithLabel(
                    label = "Trigger Delay",
                    value = zapperTriggerDelay.toFloat(),
                    onValueChange = { 
                        zapperTriggerDelay = it.toInt()
                        // Save
                        val config = if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                        } else {
                            RetroPlayConfigManager.loadConfig(console, coreFilePath)
                        }
                        val updatedConfig = config.copy(zapperTriggerDelay = it.toInt())
                        if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                        } else {
                            RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                        }
                        
                        // CRITIQUE: Appliquer la config zapper au runtime
                        if (console == "nes") {
                            onZapperConfigChanged?.invoke()
                        }
                    },
                    valueRange = 0f..200f,
                    displayValue = "${zapperTriggerDelay}ms",
                    activeColor = Color(0xFF4CAF50)
                )
                
                // Trigger on Touch
                SwitchRow(
                    title = "Trigger on Touch DOWN",
                    subtitle = "If OFF, triggers on touch UP",
                    checked = zapperTriggerOnTouch,
                    onCheckedChange = { 
                        zapperTriggerOnTouch = it
                        // Save
                        val config = if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                        } else {
                            RetroPlayConfigManager.loadConfig(console, coreFilePath)
                        }
                        val updatedConfig = config.copy(zapperTriggerOnTouch = it)
                        if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                        } else {
                            RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                        }
                        
                        // CRITIQUE: Appliquer la config zapper au runtime
                        if (console == "nes") {
                            onZapperConfigChanged?.invoke()
                        }
                    }
                )
                
                // Pulse Duration
                SliderWithLabel(
                    label = "Pulse Duration",
                    value = zapperPulseDuration.toFloat(),
                    onValueChange = { 
                        zapperPulseDuration = it.toInt()
                        // Save
                        val config = if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                        } else {
                            RetroPlayConfigManager.loadConfig(console, coreFilePath)
                        }
                        val updatedConfig = config.copy(zapperPulseDuration = it.toInt())
                        if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                        } else {
                            RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                        }
                        
                        // CRITIQUE: Appliquer la config zapper au runtime
                        if (console == "nes") {
                            onZapperConfigChanged?.invoke()
                        }
                    },
                    valueRange = 10f..100f,
                    displayValue = "${zapperPulseDuration}ms",
                    activeColor = Color(0xFF4CAF50)
                )
                
                // Allow Offscreen
                SwitchRow(
                    title = "Allow Offscreen Touches",
                    subtitle = "Allow touches outside game area",
                    checked = zapperAllowOffscreen,
                    onCheckedChange = { 
                        zapperAllowOffscreen = it
                        // Save
                        val config = if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.getEffectiveConfig(console, gameName, coreFilePath)
                        } else {
                            RetroPlayConfigManager.loadConfig(console, coreFilePath)
                        }
                        val updatedConfig = config.copy(zapperAllowOffscreen = it)
                        if (isPerGameMode && gameName.isNotEmpty()) {
                            RetroPlayConfigManager.saveGameConfig(console, gameName, updatedConfig, coreFilePath)
                        } else {
                            RetroPlayConfigManager.saveConfig(console, updatedConfig, coreFilePath)
                        }
                        
                        // CRITIQUE: Appliquer la config zapper au runtime
                        if (console == "nes") {
                            onZapperConfigChanged?.invoke()
                        }
                    }
                )
            }
        }
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Hotkeys Section
        Text(
            text = "Hotkeys",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )
        
        Text(
            text = "Hotkeys are configured in overlay .cfg files.",
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E)
        )
    }
}

// Tab 2: Emulation Content (with Global/Per-Game toggle)
@Composable
private fun EmulationTabContent(
    console: String,
    prefs: SharedPreferences,
    gameCRC: String?,
    gameName: String,
    isPerGameMode: Boolean,
    onIsPerGameModeChanged: (Boolean) -> Unit,
    effectiveConfig: RetroPlayConfigManager.RetroPlayConfig,
    onRewindEnabledChanged: (Boolean) -> Unit,
    onFastForwardRatioChanged: (Float) -> Unit,
    onAudioVolumeChanged: (Float) -> Unit,
    onAudioMuteChanged: (Boolean) -> Unit,
    onPsxAnalogModeChanged: ((Boolean) -> Unit)?,
    coreFilePath: String? = null  // Optional core file path for config file name
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Global/Per-Game Toggle
        if (gameCRC != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Configuration Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isPerGameMode) "Per-Game (${gameName})" else "Global (All Games)",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Switch(
                    checked = isPerGameMode,
                    onCheckedChange = { 
                        onIsPerGameModeChanged(it)
                        // Save immediately when toggled
                        if (it && gameName.isNotEmpty()) {
                            val currentConfig = RetroPlayConfigManager.loadConfig(console, coreFilePath)
                            RetroPlayConfigManager.saveGameConfig(console, gameName, currentConfig, coreFilePath)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF81C784)
                    )
                )
            }
            HorizontalDivider(color = Color(0xFF444444))
        }
        
        // Smart Config Section (if available)
        // TODO: Load SmartConfig from database if available
        // For now, just show a note that Smart Config can be applied
        if (effectiveConfig.smartConfigEnabled) {
            HorizontalDivider(color = Color(0xFF444444))
            Text(
                text = "Smart Config",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800)
            )
            Text(
                text = "Smart Config uses game metadata to auto-configure optimal settings.",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E)
            )
            Text(
                text = "Note: Smart Config recommendations are shown in Game Info dialog.",
                fontSize = 11.sp,
                color = Color(0xFF666666),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Use EmulationTab component (migrated from GamePadSettingsDialog)
        EmulationTab(
            console = console,
            prefs = prefs,
            onAudioMuteChanged = onAudioMuteChanged,
            onAudioVolumeChanged = onAudioVolumeChanged,
            onRewindEnabledChanged = onRewindEnabledChanged,
            onPsxAnalogModeChanged = onPsxAnalogModeChanged,
            debugModeState = remember { mutableStateOf(false) },
            onFastForwardRatioChanged = onFastForwardRatioChanged,
            gameName = gameName,
            isPerGameMode = isPerGameMode,
            coreFilePath = coreFilePath
        )
    }
}

// Tab 4: Core Options Content
@Composable
private fun CoreOptionsTabContent(
    coreOptions: List<com.retroplay.CoreVariable>,
    context: Context? = null,
    gameName: String = "",
    coreFilePath: String? = null,
    romPath: String = "",
    isPerGameMode: Boolean = false,
    onIsPerGameModeChanged: ((Boolean) -> Unit)? = null,
    onValuesChanged: (Map<String, String>) -> Unit
) {
    // État pour les valeurs sauvegardées
    var savedValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // Charger les valeurs sauvegardées au chargement de l'onglet (selon le mode Global/Per-Game)
    LaunchedEffect(coreOptions, romPath, coreFilePath, isPerGameMode) {
        if (context != null && coreFilePath != null) {
            val coreId = com.retroplay.CoreVariableManager.extractCoreId(coreFilePath)
            // CRITIQUE: Utiliser romPath pour extraire le gameId (comme dans LoadCoreVariablesUseCase)
            // pour garantir la cohérence entre sauvegarde et chargement
            val gameId = if (isPerGameMode && romPath.isNotEmpty()) {
                java.io.File(romPath).nameWithoutExtension
            } else {
                "global"
            }
            android.util.Log.i("UnifiedSettings", "🔍 Loading core options: romPath=$romPath, gameId=$gameId, coreId=$coreId, mode=${if (isPerGameMode) "Per-Game" else "Global"}")
            // Charger d'abord selon le mode, puis essayer l'autre mode si rien n'est trouvé
            var loaded = com.retroplay.CoreVariableManager.loadVariables(context, gameId, coreId)
            if (loaded.isEmpty() && isPerGameMode) {
                // Si mode Per-Game mais rien trouvé, essayer Global
                loaded = com.retroplay.CoreVariableManager.loadVariables(context, "global", coreId)
                android.util.Log.i("UnifiedSettings", "No per-game values found, trying global values")
            } else if (loaded.isEmpty() && !isPerGameMode) {
                // Si mode Global mais rien trouvé, essayer Per-Game
                if (romPath.isNotEmpty()) {
                    val perGameId = java.io.File(romPath).nameWithoutExtension
                    loaded = com.retroplay.CoreVariableManager.loadVariables(context, perGameId, coreId)
                    android.util.Log.i("UnifiedSettings", "No global values found, trying per-game values (gameId=$perGameId)")
                }
            }
            android.util.Log.i("UnifiedSettings", "📥 Loaded ${loaded.size} saved core option values for $gameId/$coreId (mode=${if (isPerGameMode) "Per-Game" else "Global"})")
            loaded.forEach { (key, value) ->
                android.util.Log.d("UnifiedSettings", "  - $key = $value")
            }
            savedValues = loaded
        } else {
            android.util.Log.w("UnifiedSettings", "⚠️ Cannot load saved values: context=${context != null}, gameName=$gameName, coreFilePath=$coreFilePath")
            savedValues = emptyMap()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Core Options",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )
        Text(
            text = "Configure core-specific emulator settings",
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E)
        )
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Global/Per-Game Toggle (comme dans EmulationTab)
        if (onIsPerGameModeChanged != null && gameName.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Configuration Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isPerGameMode) "Per-Game (${gameName})" else "Global (All Games)",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Switch(
                    checked = isPerGameMode,
                    onCheckedChange = { 
                        onIsPerGameModeChanged(it)
                        android.util.Log.i("UnifiedSettings", "🔄 Core Options mode changed to: ${if (it) "Per-Game" else "Global"}")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF81C784)
                    )
                )
            }
            HorizontalDivider(color = Color(0xFF444444))
        }
        
        if (coreOptions.isEmpty()) {
            Text(
                text = "No core options available for this core",
                fontSize = 14.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            // NOUVELLE APPROCHE: SharedPreferences est la source de vérité
            // Charger directement depuis SharedPreferences et utiliser savedValues comme source de vérité
            // coreOptions.currentValue est utilisé seulement comme valeur par défaut si rien n'est sauvegardé
            val modifiedValues = remember(coreOptions, savedValues) {
                val map = mutableStateMapOf<String, String>()
                coreOptions.forEach { opt ->
                    // Utiliser savedValues si disponible (source de vérité), sinon currentValue (valeur par défaut du core)
                    val valueToUse = savedValues[opt.key] ?: opt.currentValue
                    map[opt.key] = valueToUse
                    android.util.Log.d("UnifiedSettings", "Initialized ${opt.key} = $valueToUse (saved=${savedValues[opt.key]}, default=${opt.currentValue})")
                }
                // Notifier le parent avec les valeurs initiales
                onValuesChanged(map.toMap())
                map
            }
            
            Column {
                coreOptions.forEach { variable ->
                    val currentVal = modifiedValues[variable.key] ?: variable.currentValue
                    
                    if (variable.isBoolean()) {
                        SwitchRow(
                            title = variable.displayName,
                            subtitle = currentVal,
                            checked = currentVal.lowercase() in listOf("enabled", "true", "1"),
                            onCheckedChange = { isChecked ->
                                val newVal = when {
                                    variable.possibleValues.contains("true") -> if (isChecked) "true" else "false"
                                    variable.possibleValues.contains("1") -> if (isChecked) "1" else "0"
                                    else -> if (isChecked) "enabled" else "disabled"
                                }
                                modifiedValues[variable.key] = newVal
                                // Notifier le parent avec les valeurs mises à jour
                                onValuesChanged(modifiedValues.toMap())
                            }
                        )
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(variable.displayName, color = Color.White, fontSize = 14.sp)
                                Text(currentVal, color = Color(0xFF9E9E9E), fontSize = 12.sp)
                            }
                            Box {
                                Button(
                                    onClick = { expanded = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                                ) {
                                    Text(currentVal, color = Color.White, fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(Color(0xFF1E1E1E))  // Fond foncé pour meilleur contraste
                                ) {
                                    variable.possibleValues.forEach { value ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    value, 
                                                    color = Color.White,  // Texte blanc sur fond foncé
                                                    fontSize = 13.sp
                                                ) 
                                            },
                                            onClick = {
                                                modifiedValues[variable.key] = value
                                                // Notifier le parent avec les valeurs mises à jour
                                                onValuesChanged(modifiedValues.toMap())
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
                    HorizontalDivider(color = Color(0xFF333333))
                }
                
                
                // BOUTON SUPPRIMÉ - Utiliser les boutons [Apply] et [Save] au pied du dialog
                /*
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        try {
                            android.util.Log.i("UnifiedSettings", "🔘 Apply Core Options button clicked (mode=${if (isPerGameMode) "Per-Game" else "Global"})")
                            val valuesToApply = modifiedValues.toMap()
                            android.util.Log.i("UnifiedSettings", "🔄 Applying ${valuesToApply.size} core option values")
                            valuesToApply.forEach { (key, value) ->
                                android.util.Log.d("UnifiedSettings", "  - $key = $value")
                            }
                            
                            // Sauvegarder selon le mode Global/Per-Game
                            if (context != null && coreFilePath != null) {
                                val coreId = com.retroplay.CoreVariableManager.extractCoreId(coreFilePath)
                                // CRITIQUE: Utiliser romPath pour extraire le gameId (comme dans LoadCoreVariablesUseCase)
                                val gameId = if (isPerGameMode && romPath.isNotEmpty()) {
                                    java.io.File(romPath).nameWithoutExtension
                                } else {
                                    "global"
                                }
                                android.util.Log.i("UnifiedSettings", "🔍 Saving core options: romPath=$romPath, gameId=$gameId, coreId=$coreId, mode=${if (isPerGameMode) "Per-Game" else "Global"}")
                                
                                android.util.Log.i("UnifiedSettings", "💾 Saving core options: gameId=$gameId, coreId=$coreId (mode=${if (isPerGameMode) "Per-Game" else "Global"})")
                                try {
                                    com.retroplay.CoreVariableManager.saveVariables(
                                        context,
                                        gameId,
                                        coreId,
                                        valuesToApply
                                    )
                                    android.util.Log.i("UnifiedSettings", "✅ Successfully saved ${valuesToApply.size} core option values")
                                    
                                    // CRITIQUE: Mettre à jour savedValues pour refléter les nouvelles valeurs sauvegardées
                                    // Cela garantit que modifiedValues sera réinitialisé avec les nouvelles valeurs sauvegardées
                                    savedValues = valuesToApply
                                    android.util.Log.d("UnifiedSettings", "Updated savedValues with ${valuesToApply.size} values")
                                    
                                    // Afficher un toast de confirmation
                                    android.widget.Toast.makeText(
                                        context,
                                        "Core options saved (${if (isPerGameMode) "Per-Game" else "Global"})",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    android.util.Log.e("UnifiedSettings", "❌ Error saving core options: ${e.message}", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Error saving core options: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            
                            // Passer TOUTES les valeurs modifiées (comme dans CoreOptionsDialog)
                            if (onCoreOptionsChanged != null) {
                                android.util.Log.i("UnifiedSettings", "✅ onCoreOptionsChanged callback is NOT null, invoking...")
                                onCoreOptionsChanged.invoke(valuesToApply)
                                android.util.Log.i("UnifiedSettings", "✅ onCoreOptionsChanged callback invoked successfully")
                            } else {
                                android.util.Log.e("UnifiedSettings", "❌ onCoreOptionsChanged callback is NULL!")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("UnifiedSettings", "❌ Error in button onClick: ${e.message}", e)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Core Options", color = Color.White, fontWeight = FontWeight.Bold)
                }
                */
            }
        }
    }
}

// Tab 5: Advanced Content
@Composable
private fun AdvancedTabContent(
    console: String,
    prefs: SharedPreferences,
    debugModeState: MutableState<Boolean>
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val orientation = if (isLandscape) "landscape" else "portrait"
    
    // Advanced Overlay Settings State
    var useSameSettings by remember { 
        mutableStateOf(
            try {
                com.retroplay.overlay.models.OverlayPreferenceManager.checkIfSettingsAreSame(prefs, console)
            } catch (e: Exception) {
                android.util.Log.e("UnifiedSettings", "Error checking if settings are same: ${e.message}", e)
                false  // Default to false if error
            }
        )
    }
    
    var advancedSettings by remember(orientation, useSameSettings) { 
        mutableStateOf(
            if (useSameSettings) {
                com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                    ?: com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
            } else {
                com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
            }
        )
    }
    
    var dpadDiagonalSensitivity by remember { mutableStateOf(advancedSettings.dpadDiagonalSensitivity) }
    var abxyDiagonalSensitivity by remember { mutableStateOf(advancedSettings.abxyDiagonalSensitivity) }
    var analogRecenterZone by remember { mutableStateOf(advancedSettings.analogRecenterZone) }
    var opacity by remember { mutableStateOf(advancedSettings.opacity) }
    var aspectAdjust by remember { mutableStateOf(advancedSettings.aspectAdjust) }
    var hideInMenu by remember { mutableStateOf(advancedSettings.hideInMenu) }
    var behindMenu by remember { mutableStateOf(advancedSettings.behindMenu) }
    var hideWhenGamepad by remember { mutableStateOf(advancedSettings.hideWhenGamepadConnected) }
    var hideWhenGamepadPort0Only by remember { mutableStateOf(advancedSettings.hideWhenGamepadConnectedPort0Only) }
    var showInputs by remember { mutableStateOf(advancedSettings.showInputs) }
    var showInputsPort by remember { mutableStateOf(advancedSettings.showInputsPort) }
    var lightgunPort by remember { mutableStateOf(advancedSettings.lightgunPort) }
    var lightgunTriggerOnTouch by remember { mutableStateOf(advancedSettings.lightgunTriggerOnTouch) }
    var lightgunTriggerDelay by remember { mutableStateOf(advancedSettings.lightgunTriggerDelay) }
    var lightgunAllowOffscreen by remember { mutableStateOf(advancedSettings.lightgunAllowOffscreen) }
    var mouseSpeed by remember { mutableStateOf(advancedSettings.mouseSpeed) }
    var mouseSwipeThreshold by remember { mutableStateOf(advancedSettings.mouseSwipeThreshold) }
    var mouseHoldToDrag by remember { mutableStateOf(advancedSettings.mouseHoldToDrag) }
    var mouseHoldMsec by remember { mutableStateOf(advancedSettings.mouseHoldMsec) }
    var mouseDoubleTapToDrag by remember { mutableStateOf(advancedSettings.mouseDoubleTapToDrag) }
    var mouseDtapMsec by remember { mutableStateOf(advancedSettings.mouseDtapMsec) }
    var showMouseCursor by remember { mutableStateOf(advancedSettings.showMouseCursor) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Debug Mode
        SwitchRow(
            title = "Debug Mode",
            subtitle = "Show overlay debug information",
            checked = debugModeState.value,
            onCheckedChange = { debugModeState.value = it }
        )
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Advanced Overlay Settings
        Text(
            text = "Advanced Overlay Settings",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )
        
        // Use GamePadAdvancedSettings component
        GamePadAdvancedSettings(
            console = console,
            prefs = prefs,
            orientation = orientation,
            useSameSettings = useSameSettings,
            dpadDiagonalSensitivity = dpadDiagonalSensitivity,
            abxyDiagonalSensitivity = abxyDiagonalSensitivity,
            analogRecenterZone = analogRecenterZone,
            opacity = opacity,
            aspectAdjust = aspectAdjust,
            showInputs = showInputs,
            showInputsPort = showInputsPort,
            hideInMenu = hideInMenu,
            behindMenu = behindMenu,
            hideWhenGamepad = hideWhenGamepad,
            hideWhenGamepadPort0Only = hideWhenGamepadPort0Only,
            lightgunPort = lightgunPort,
            lightgunTriggerOnTouch = lightgunTriggerOnTouch,
            lightgunTriggerDelay = lightgunTriggerDelay,
            lightgunAllowOffscreen = lightgunAllowOffscreen,
            mouseSpeed = mouseSpeed,
            mouseSwipeThreshold = mouseSwipeThreshold,
            mouseHoldToDrag = mouseHoldToDrag,
            mouseHoldMsec = mouseHoldMsec,
            mouseDoubleTapToDrag = mouseDoubleTapToDrag,
            mouseDtapMsec = mouseDtapMsec,
            showMouseCursor = showMouseCursor,
            onUseSameSettingsChanged = { useSameSettings = it },
            onDpadDiagonalSensitivityChanged = { dpadDiagonalSensitivity = it },
            onAbxyDiagonalSensitivityChanged = { abxyDiagonalSensitivity = it },
            onAnalogRecenterZoneChanged = { analogRecenterZone = it },
            onOpacityChanged = { opacity = it },
            onAspectAdjustChanged = { aspectAdjust = it },
            onShowInputsChanged = { showInputs = it },
            onShowInputsPortChanged = { showInputsPort = it },
            onHideInMenuChanged = { hideInMenu = it },
            onBehindMenuChanged = { behindMenu = it },
            onHideWhenGamepadChanged = { hideWhenGamepad = it },
            onHideWhenGamepadPort0OnlyChanged = { hideWhenGamepadPort0Only = it },
            onLightgunPortChanged = { lightgunPort = it },
            onLightgunTriggerOnTouchChanged = { lightgunTriggerOnTouch = it },
            onLightgunTriggerDelayChanged = { lightgunTriggerDelay = it },
            onLightgunAllowOffscreenChanged = { lightgunAllowOffscreen = it },
            onMouseSpeedChanged = { mouseSpeed = it },
            onMouseSwipeThresholdChanged = { mouseSwipeThreshold = it },
            onMouseHoldToDragChanged = { mouseHoldToDrag = it },
            onMouseHoldMsecChanged = { mouseHoldMsec = it },
            onMouseDoubleTapToDragChanged = { mouseDoubleTapToDrag = it },
            onMouseDtapMsecChanged = { mouseDtapMsec = it },
            onShowMouseCursorChanged = { showMouseCursor = it },
            onUseSameSettingsToggled = { newValue ->
                if (newValue) {
                    val current = com.retroplay.overlay.models.AdvancedOverlaySettings(
                        dpadDiagonalSensitivity = dpadDiagonalSensitivity,
                        abxyDiagonalSensitivity = abxyDiagonalSensitivity,
                        analogRecenterZone = analogRecenterZone,
                        opacity = opacity,
                        aspectAdjust = aspectAdjust,
                        hideInMenu = hideInMenu,
                        behindMenu = behindMenu,
                        hideWhenGamepadConnected = hideWhenGamepad,
                        hideWhenGamepadConnectedPort0Only = hideWhenGamepadPort0Only,
                        showInputs = showInputs,
                        showInputsPort = showInputsPort,
                        lightgunPort = lightgunPort,
                        lightgunTriggerOnTouch = lightgunTriggerOnTouch,
                        lightgunTriggerDelay = lightgunTriggerDelay,
                        lightgunAllowOffscreen = lightgunAllowOffscreen,
                        lightgunTwoTouchInput = advancedSettings.lightgunTwoTouchInput,
                        lightgunThreeTouchInput = advancedSettings.lightgunThreeTouchInput,
                        lightgunFourTouchInput = advancedSettings.lightgunFourTouchInput,
                        mouseSpeed = mouseSpeed,
                        mouseSwipeThreshold = mouseSwipeThreshold,
                        mouseHoldToDrag = mouseHoldToDrag,
                        mouseHoldMsec = mouseHoldMsec,
                        mouseDoubleTapToDrag = mouseDoubleTapToDrag,
                        mouseDtapMsec = mouseDtapMsec,
                        showMouseCursor = showMouseCursor
                    )
                    com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", current)
                    com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", current)
                }
            }
        )
    }
}

// Helper: Check if zapper is configured
private fun isZapperConfigured(console: String, prefs: SharedPreferences): Boolean {
    for (port in 0..3) {
        val portKey = "controller_port_${console}_port${port}"
        val controllerType = prefs.getInt(portKey, -1)
        if (controllerType == 258) { // RETRO_DEVICE_ZAPPER
            return true
        }
    }
    return false
}

// Helper: Controller Port Row
@Composable
private fun ControllerPortRow(
    label: String,
    console: String,
    port: Int,
    prefs: SharedPreferences
) {
    val portKey = "controller_port_${console}_port${port}"
    var selectedControllerType by remember { mutableStateOf(prefs.getInt(portKey, 1)) }
    
    val controllerTypes = when (console.lowercase()) {
        "nes" -> listOf(
            "Auto (Default)" to -1,
            "None" to 0,
            "Joypad" to 1,
            "Zapper (Lightgun)" to 258
        )
        "snes" -> listOf(
            "Auto (Default)" to -1,
            "None" to 0,
            "Joypad" to 1,
            "Mouse" to 2,
            "SuperScope" to 260
        )
        "psx" -> listOf(
            "Auto (Default)" to -1,
            "None" to 0,
            "Joypad (Digital)" to 1,
            "Analog (DualShock)" to 5,
            "Guncon" to 4,
            "Mouse" to 2
        )
        "genesis", "megadrive" -> listOf(
            "Auto (Default)" to -1,
            "None" to 0,
            "Joypad (3-Button)" to 1,
            "Menacer (Lightgun)" to 4
        )
        else -> listOf(
            "Auto (Default)" to -1,
            "None" to 0,
            "Joypad" to 1,
            "Lightgun" to 4,
            "Analog" to 5,
            "Mouse" to 2
        )
    }
    
    var expanded by remember { mutableStateOf(false) }
    val selectedName = controllerTypes.find { it.second == selectedControllerType }?.first ?: "Joypad"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedControllerType == 1) Color(0xFF2A2A2A) else Color(0xFF3A2A1A)
                )
            ) {
                Text(selectedName, color = Color.White, fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1E1E1E))  // Fond foncé pour meilleur contraste
            ) {
                controllerTypes.forEach { (name, id) ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                name, 
                                color = Color.White,  // Texte blanc sur fond foncé
                                fontSize = 13.sp
                            ) 
                        },
                        onClick = {
                            selectedControllerType = id
                            prefs.edit().putInt(portKey, id).apply()
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

// Radial Menu Settings Content
@Composable
private fun RadialMenuSettingsContent(
    console: String,
    prefs: SharedPreferences
) {
    // Load Radial Settings
    val radialSettings = remember(prefs, console) { 
        try {
            PadKitHelper.loadSettings(prefs, console)
        } catch (e: Exception) {
            android.util.Log.e("UnifiedSettings", "Error loading radial settings: ${e.message}", e)
            TouchControllerSettingsManager.Settings()  // Default values
        }
    }
    var radialScale by remember(radialSettings) { mutableFloatStateOf(radialSettings.scale) }
    var radialRotation by remember(radialSettings) { mutableFloatStateOf(radialSettings.rotation) }
    var radialMarginX by remember(radialSettings) { mutableFloatStateOf(radialSettings.marginX) }
    var radialMarginY by remember(radialSettings) { mutableFloatStateOf(radialSettings.marginY) }
    var radialSwapAnalogSticks by remember(radialSettings) { mutableStateOf(radialSettings.swapAnalogSticks) }
    var radialInvertAnalogLeftY by remember(radialSettings) { mutableStateOf(radialSettings.invertAnalogLeftY) }
    var radialInvertAnalogRightY by remember(radialSettings) { mutableStateOf(radialSettings.invertAnalogRightY) }
    
    // Advanced Visibility Settings
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val orientation = if (isLandscape) "landscape" else "portrait"
    
    var useSameSettings by remember { 
        mutableStateOf(
            try {
                com.retroplay.overlay.models.OverlayPreferenceManager.checkIfSettingsAreSame(prefs, console)
            } catch (e: Exception) {
                android.util.Log.e("UnifiedSettings", "Error checking if settings are same: ${e.message}", e)
                false  // Default to false if error
            }
        )
    }
    
    var advancedSettings by remember(orientation, useSameSettings) { 
        mutableStateOf(
            try {
                val loaded = if (useSameSettings) {
                    com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, null)
                        ?: com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, "landscape")
                } else {
                    com.retroplay.overlay.models.OverlayPreferenceManager.loadAdvancedSettings(prefs, console, orientation)
                }
                loaded ?: com.retroplay.overlay.models.AdvancedOverlaySettings()  // Ensure never null
            } catch (e: Exception) {
                android.util.Log.e("UnifiedSettings", "Error loading advanced settings: ${e.message}", e)
                com.retroplay.overlay.models.AdvancedOverlaySettings()  // Default values
            }
        )
    }
    
    var hideInMenu by remember(advancedSettings) { mutableStateOf(advancedSettings.hideInMenu) }
    var behindMenu by remember(advancedSettings) { mutableStateOf(advancedSettings.behindMenu) }
    var hideWhenGamepad by remember(advancedSettings) { mutableStateOf(advancedSettings.hideWhenGamepadConnected) }
    var hideWhenGamepadPort0Only by remember(advancedSettings) { mutableStateOf(advancedSettings.hideWhenGamepadConnectedPort0Only) }
    
    // Lightgun settings (from radialSettings if available, otherwise from advancedSettings)
    var lightgunTriggerOnTouch by remember(radialSettings) { mutableStateOf(radialSettings.lightgunTriggerOnTouch) }
    var lightgunTriggerDelay by remember(radialSettings) { mutableIntStateOf(radialSettings.lightgunTriggerDelay) }
    var lightgunAllowOffscreen by remember(radialSettings) { mutableStateOf(radialSettings.lightgunAllowOffscreen) }
    var lightgunPort by remember(advancedSettings) { mutableIntStateOf(advancedSettings.lightgunPort) }
    
    // Save Radial Settings when they change
    // Utiliser une variable pour éviter de sauvegarder au premier rendu (quand les valeurs sont égales à radialSettings)
    var isInitialLoad by remember { mutableStateOf(true) }
    
    LaunchedEffect(
        radialScale,
        radialRotation,
        radialMarginX,
        radialMarginY,
        radialSwapAnalogSticks,
        radialInvertAnalogLeftY,
        radialInvertAnalogRightY,
        lightgunTriggerOnTouch,
        lightgunTriggerDelay,
        lightgunAllowOffscreen
    ) {
        // Ne pas sauvegarder au premier rendu (valeurs initiales chargées depuis SharedPreferences)
        if (isInitialLoad) {
            isInitialLoad = false
            return@LaunchedEffect
        }
        
        try {
            val newSettings = TouchControllerSettingsManager.Settings(
                scale = radialScale,
                rotation = radialRotation,
                marginX = radialMarginX,
                marginY = radialMarginY,
                swapAnalogSticks = radialSwapAnalogSticks,
                invertAnalogLeftY = radialInvertAnalogLeftY,
                invertAnalogRightY = radialInvertAnalogRightY,
                lightgunTriggerOnTouch = lightgunTriggerOnTouch,
                lightgunTriggerDelay = lightgunTriggerDelay,
                lightgunAllowOffscreen = lightgunAllowOffscreen
            )
            PadKitHelper.saveSettings(prefs, console, newSettings)
            android.util.Log.d("UnifiedSettings", "Radial settings saved for $console: scale=$radialScale, rotation=$radialRotation")
        } catch (e: Exception) {
            android.util.Log.e("UnifiedSettings", "Error saving radial settings: ${e.message}", e)
        }
    }
    
    // Save Advanced Settings when they change
    LaunchedEffect(hideInMenu, behindMenu, hideWhenGamepad, hideWhenGamepadPort0Only, lightgunPort) {
        try {
            val currentSettings = advancedSettings
            val updatedSettings = currentSettings.copy(
                hideInMenu = hideInMenu,
                behindMenu = behindMenu,
                hideWhenGamepadConnected = hideWhenGamepad,
                hideWhenGamepadConnectedPort0Only = hideWhenGamepadPort0Only,
                lightgunPort = lightgunPort
            )
            if (useSameSettings) {
                com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "landscape", updatedSettings)
                com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, "portrait", updatedSettings)
            } else {
                com.retroplay.overlay.models.OverlayPreferenceManager.saveAdvancedSettings(prefs, console, orientation, updatedSettings)
            }
        } catch (e: Exception) {
            android.util.Log.e("UnifiedSettings", "Error saving advanced settings: ${e.message}", e)
        }
    }
    
    // Tab selection for Radial Menu
    var selectedRadialTab by remember { mutableIntStateOf(0) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tabs
        TabRow(
            selectedTabIndex = selectedRadialTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFFFF9800),
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedRadialTab == 0,
                onClick = { selectedRadialTab = 0 },
                text = { 
                    Text(
                        "Settings", 
                        color = if (selectedRadialTab == 0) Color(0xFFFF9800) else Color(0xFF888888),
                        fontWeight = if (selectedRadialTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedRadialTab == 1,
                onClick = { selectedRadialTab = 1 },
                text = { 
                    Text(
                        "Advanced",
                        color = if (selectedRadialTab == 1) Color(0xFFFF9800) else Color(0xFF888888),
                        fontWeight = if (selectedRadialTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
        
        HorizontalDivider(color = Color(0xFF444444))
        
        // Tab Content
        when (selectedRadialTab) {
            0 -> {
                // Settings Tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Radial Menu",
                        color = Color(0xFF2196F3),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0x40FFFFFF))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Precision Adjustments",
                        color = Color(0xFF2196F3),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    RadialSlider(
                        label = "Scale",
                        value = radialScale,
                        displayValue = "${String.format("%.2f", radialScale * 0.75f + 0.75f)}x",
                        valueRange = 0f..1f,
                        onValueChange = { radialScale = it },
                        onNudge = { delta ->
                            radialScale = (radialScale + delta).coerceIn(0f, 1f)
                        }
                    )
                    
                    RadialSlider(
                        label = "Rotation",
                        value = radialRotation,
                        displayValue = "${String.format("%.0f", radialRotation * TouchControllerSettingsManager.MAX_ROTATION)}°",
                        valueRange = 0f..1f,
                        onValueChange = { radialRotation = it },
                        onNudge = { delta ->
                            radialRotation = (radialRotation + delta).coerceIn(0f, 1f)
                        }
                    )
                    
                    RadialSlider(
                        label = "Horizontal Margin",
                        value = radialMarginX,
                        displayValue = "${String.format("%.0f", radialMarginX * TouchControllerSettingsManager.MAX_MARGINS)}dp",
                        valueRange = 0f..1f,
                        onValueChange = { radialMarginX = it },
                        onNudge = { delta ->
                            radialMarginX = (radialMarginX + delta).coerceIn(0f, 1f)
                        }
                    )
                    
                    RadialSlider(
                        label = "Vertical Margin",
                        value = radialMarginY,
                        displayValue = "${String.format("%.0f", radialMarginY * TouchControllerSettingsManager.MAX_MARGINS)}dp",
                        valueRange = 0f..1f,
                        onValueChange = { radialMarginY = it },
                        onNudge = { delta ->
                            radialMarginY = (radialMarginY + delta).coerceIn(0f, 1f)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0x40FFFFFF))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Analog Options",
                        color = Color(0xFFFF9800),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    SwitchRow(
                        title = "Swap Left/Right Sticks",
                        subtitle = "Exchange left and right analog positions",
                        checked = radialSwapAnalogSticks,
                        onCheckedChange = { radialSwapAnalogSticks = it }
                    )
                    
                    SwitchRow(
                        title = "Invert Left Stick Y",
                        subtitle = "Reverse up/down movement for left analog",
                        checked = radialInvertAnalogLeftY,
                        onCheckedChange = { radialInvertAnalogLeftY = it }
                    )
                    
                    SwitchRow(
                        title = "Invert Right Stick Y",
                        subtitle = "Reverse up/down movement for right analog",
                        checked = radialInvertAnalogRightY,
                        onCheckedChange = { radialInvertAnalogRightY = it }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0x40FFFFFF))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Lightgun Options",
                        color = Color(0xFFFFC107),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    SwitchRow(
                        title = "Trigger on Touch",
                        subtitle = "Fire immediately when touching the screen",
                        checked = lightgunTriggerOnTouch,
                        onCheckedChange = { lightgunTriggerOnTouch = it }
                    )
                    
                    SwitchRow(
                        title = "Allow Off-Screen Shots",
                        subtitle = "Permit shooting outside the playfield",
                        checked = lightgunAllowOffscreen,
                        onCheckedChange = { lightgunAllowOffscreen = it }
                    )
                    
                    Column {
                        Text(
                            text = "Trigger Delay: ${lightgunTriggerDelay} frame(s)",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = lightgunTriggerDelay.toFloat(),
                            onValueChange = { lightgunTriggerDelay = it.roundToInt().coerceAtLeast(0) },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFC107),
                                activeTrackColor = Color(0xFFFFC107),
                                inactiveTrackColor = Color(0x40FFFFFF)
                            )
                        )
                        Text(
                            text = "Frames to wait before sending the lightgun trigger",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Lightgun Port: ${if (lightgunPort < 0) "All" else lightgunPort + 1}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Slider(
                            value = lightgunPort.toFloat(),
                            onValueChange = {
                                lightgunPort = it.roundToInt().coerceIn(-1, 3)
                            },
                            valueRange = -1f..3f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50),
                                activeTrackColor = Color(0xFF4CAF50),
                                inactiveTrackColor = Color(0x40FFFFFF)
                            )
                        )
                        Text(
                            text = "Choose which port controls the lightgun (-1 = all ports)",
                            color = Color(0xFF888888),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            1 -> {
                // Advanced Tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AdvancedVisibilitySettingsContent(
                        hideInMenu = hideInMenu,
                        onHideInMenuChanged = { hideInMenu = it },
                        behindMenu = behindMenu,
                        onBehindMenuChanged = { behindMenu = it },
                        hideWhenGamepad = hideWhenGamepad,
                        onHideWhenGamepadChanged = { hideWhenGamepad = it },
                        hideWhenGamepadPort0Only = hideWhenGamepadPort0Only,
                        onHideWhenGamepadPort0OnlyChanged = { hideWhenGamepadPort0Only = it }
                    )
                }
            }
        }
    }
}