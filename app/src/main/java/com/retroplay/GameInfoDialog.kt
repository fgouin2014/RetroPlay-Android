package com.retroplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.config.RetroPlayConfigManager
import com.retroplay.database.SmartConfig
import com.retroplay.database.SmartConfigManager
import com.retroplay.database.GameInfo
import java.io.File
import java.util.Locale
import kotlin.math.abs

@Composable
fun GameInfoDialog(
    gameInfo: GameInfo?,
    gameCRC: String?,
    console: String?,
    cheatFile: File?,
    onDismiss: () -> Unit,
    onOpenPerGameConfig: ((String, String) -> Unit)? = null,
    onOpenSmartConfig: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
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
                    text = "📘 GAME INTEL",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Source: libretro-database metadata",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (gameInfo != null) {
                    // Game found in database
                    GameInfoContent(
                        gameInfo = gameInfo,
                        gameCRC = gameCRC,
                        console = console,
                        cheatFile = cheatFile,
                        onOpenSmartConfig = onOpenSmartConfig
                    )
                } else {
                    // Game not found
                    GameNotFoundContent(gameCRC, console)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Per-Game Config button (if CRC is available)
                if (gameCRC != null && onOpenPerGameConfig != null) {
                    val hasOverride = com.retroplay.config.RetroPlayConfigManager.hasGameConfig(gameCRC)
                    
                    OutlinedButton(
                        onClick = { 
                            onOpenPerGameConfig(gameCRC, gameInfo?.name ?: "Unknown Game")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (hasOverride) Color(0xFF4CAF50) else Color(0xFF2196F3)
                        )
                    ) {
                        Text(
                            text = if (hasOverride) "⚙️ EDIT GAME CONFIG (CUSTOM)" else "⚙️ CONFIGURE THIS GAME",
                            fontSize = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Open Gallery button (if handler provided)
                if (onOpenGallery != null) {
                    OutlinedButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF9C27B0)
                        )
                    ) {
                        Text("OPEN GALLERY", fontSize = 16.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("CLOSE", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun GameInfoContent(
    gameInfo: GameInfo,
    gameCRC: String?,
    console: String?,
    cheatFile: File?,
    onOpenSmartConfig: (() -> Unit)?
) {
    Column {
        // Game Name
        InfoRow(
            icon = "🎮",
            label = "Game Name",
            value = gameInfo.name,
            highlight = true
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Console
        val consoleDisplay = console ?: gameInfo.console
        InfoRow(
            icon = "🕹️",
            label = "Console",
            value = consoleDisplay
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // CRC32
        if (gameCRC != null) {
            InfoRow(
                icon = "🔍",
                label = "Loaded CRC32",
                value = gameCRC,
                mono = true
            )
            
            if (!gameInfo.crc.equals(gameCRC, ignoreCase = true) && gameInfo.crc.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(
                    icon = "📚",
                    label = "Database CRC32",
                    value = gameInfo.crc,
                    mono = true,
                    highlight = true
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Genre
        val genre = gameInfo.genre?.takeIf { it.isNotBlank() } ?: "Not catalogued"
        InfoRow(
            icon = "🎭",
            label = "Genre",
            value = genre
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Developer
        val developer = gameInfo.developer?.takeIf { it.isNotBlank() } ?: "Unknown"
        InfoRow(
            icon = "🏢",
            label = "Developer",
            value = developer
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Publisher
        val publisher = gameInfo.publisher?.takeIf { it.isNotBlank() } ?: "Unknown"
        InfoRow(
            icon = "📦",
            label = "Publisher",
            value = publisher
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Release Year
        val releaseInfo = when {
            gameInfo.releaseYear != null && gameInfo.releaseMonth != null -> "${gameInfo.releaseMonth}/${gameInfo.releaseYear}"
            gameInfo.releaseYear != null -> gameInfo.releaseYear.toString()
            else -> "Unknown"
        }
        InfoRow(
            icon = "📅",
            label = "Released",
            value = releaseInfo
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Max Players
        if (gameInfo.maxPlayers > 1) {
            InfoRow(
                icon = "👥",
                label = "Players",
                value = "1-${gameInfo.maxPlayers}"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Features
        val features = mutableListOf<String>()
        if (gameInfo.hasRumble) features.add("Rumble")
        if (gameInfo.hasAnalog) features.add("Analog")
        if (gameInfo.isHack) features.add("Fan Mod")
        if (gameInfo.isHomebrew) features.add("Homebrew")
        
        val featuresSummary = if (features.isNotEmpty()) {
            features.joinToString(", ")
        } else {
            "None detected"
        }
        InfoRow(
            icon = "⭐",
            label = "Features",
            value = featuresSummary
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Divider
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = Color(0xFF424242)
        )
        
        // Cheats Available
        val cheatCount = cheatFile?.let { countCheats(it) } ?: 0
        InfoRow(
            icon = "🎮",
            label = "Cheats",
            value = if (cheatCount > 0) {
                "$cheatCount available"
            } else {
                "None found"
            },
            highlight = cheatCount > 0
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Smart Config Recommendations
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = Color(0xFF424242)
        )
        
        val recommendedConfig = SmartConfigManager.getSmartConfig(gameInfo, gameInfo.console)
        val appliedConfig = RetroPlayConfigManager.getEffectiveConfig(gameCRC)
        val hasPerGameOverride = gameCRC?.let { RetroPlayConfigManager.hasGameConfig(it) } == true

        SmartConfigSection(
            gameInfo = gameInfo,
            gameCRC = gameCRC,
            recommended = recommendedConfig,
            appliedConfig = appliedConfig,
            hasPerGameOverride = hasPerGameOverride,
            onOpenSmartConfig = onOpenSmartConfig
        )
    }
}

@Composable
private fun SmartConfigSection(
    gameInfo: GameInfo,
    gameCRC: String?,
    recommended: SmartConfig,
    appliedConfig: RetroPlayConfigManager.RetroPlayConfig,
    hasPerGameOverride: Boolean,
    onOpenSmartConfig: (() -> Unit)?
) {
    Column {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "⚙ SMART CONFIG CENTER",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Disclaimer
        Text(
            text = "Compare libretro recommendations with RetroPlay applied settings",
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        val smartConfigStatus = buildString {
            append(if (appliedConfig.smartConfigEnabled) "Enabled" else "Disabled")
            append(" • Auto Run-Ahead: ")
            append(if (appliedConfig.smartConfigAutoRunAhead) "On" else "Off")
            append(" • Auto Rewind: ")
            append(if (appliedConfig.smartConfigAutoRewind) "On" else "Off")
            append(" • Auto Overlay: ")
            append(if (appliedConfig.smartConfigAutoOverlay) "On" else "Off")
            append(" • OSD: ")
            append(if (appliedConfig.smartConfigShowOSD) "On" else "Off")
        }

        InfoRow(
            icon = "⚙️",
            label = "Smart Config",
            value = smartConfigStatus,
            highlight = appliedConfig.smartConfigEnabled
        )

        if (hasPerGameOverride && gameCRC != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Per-game override active for CRC $gameCRC",
                fontSize = 12.sp,
                color = Color(0xFFFFC107),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val recommendedRunAheadText = if (recommended.runAheadEnabled) {
            formatFrames(recommended.runAheadFrames)
        } else {
            "Disabled"
        }
        val appliedRunAheadText = when {
            !appliedConfig.smartConfigEnabled -> "Smart Config disabled"
            appliedConfig.smartConfigAutoRunAhead -> "Auto (${formatFrames(recommended.runAheadFrames)})"
            appliedConfig.runAheadEnabled -> "Manual ${formatFrames(appliedConfig.runAheadFrames)}"
            else -> "Disabled"
        }
        val runAheadMatches = when {
            !appliedConfig.smartConfigEnabled -> !recommended.runAheadEnabled
            appliedConfig.smartConfigAutoRunAhead -> recommended.runAheadEnabled
            else -> recommended.runAheadEnabled == appliedConfig.runAheadEnabled &&
                    (!recommended.runAheadEnabled || recommended.runAheadFrames == appliedConfig.runAheadFrames)
        }

        SmartConfigCard(
            icon = "⚡",
            title = "Run-Ahead",
            recommendedValue = recommendedRunAheadText,
            recommendedColor = if (recommended.runAheadEnabled) Color(0xFF4CAF50) else Color(0xFF757575),
            appliedValue = appliedRunAheadText,
            appliedColor = if (runAheadMatches) Color(0xFF64B5F6) else Color(0xFFFFC107),
            mismatch = !runAheadMatches,
            description = if (recommended.runAheadEnabled) {
                "Reduces input lag by ~${recommended.runAheadFrames * 16}ms. Recommended for ${gameInfo.genre} games."
            } else {
                "Not recommended for ${gameInfo.genre ?: "this genre"}. May cause instability."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val recommendedRewindText = if (recommended.rewindEnabled) {
            formatMegabytes(recommended.rewindBufferSize)
        } else {
            "Disabled"
        }
        val appliedRewindText = when {
            !appliedConfig.smartConfigEnabled -> "Smart Config disabled"
            appliedConfig.smartConfigAutoRewind -> "Auto (${formatMegabytes(recommended.rewindBufferSize)})"
            appliedConfig.rewindEnable -> "Manual ${formatMegabytes(appliedConfig.rewindBufferSize)}"
            else -> "Disabled"
        }
        val rewindBufferMatches = abs(appliedConfig.rewindBufferSize - recommended.rewindBufferSize) <= 1 * 1024 * 1024
        val rewindMatches = when {
            !appliedConfig.smartConfigEnabled -> !recommended.rewindEnabled
            appliedConfig.smartConfigAutoRewind -> recommended.rewindEnabled
            else -> recommended.rewindEnabled == appliedConfig.rewindEnable &&
                    (!recommended.rewindEnabled || rewindBufferMatches)
        }

        SmartConfigCard(
            icon = "⏪",
            title = "Rewind",
            recommendedValue = recommendedRewindText,
            recommendedColor = if (recommended.rewindEnabled) Color(0xFFFF9800) else Color(0xFF757575),
            appliedValue = appliedRewindText,
            appliedColor = if (rewindMatches) Color(0xFF4CAF50) else Color(0xFFFFC107),
            mismatch = !rewindMatches,
            description = if (recommended.rewindEnabled) {
                "Helpful for puzzle and adventure games. Uses RAM for replay buffer."
            } else {
                "Not needed for ${gameInfo.genre ?: "this genre"}. Saves memory and CPU."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val recommendedOverlayText = recommended.overlayName
        val appliedOverlayText = when {
            !appliedConfig.smartConfigEnabled -> "Smart Config disabled"
            appliedConfig.smartConfigAutoOverlay -> "Auto ($recommendedOverlayText)"
            else -> "Manual"
        }
        val overlayMatches = appliedConfig.smartConfigEnabled && appliedConfig.smartConfigAutoOverlay

        SmartConfigCard(
            icon = "🎨",
            title = "Optimal Overlay",
            recommendedValue = recommendedOverlayText,
            recommendedColor = Color(0xFF9C27B0),
            appliedValue = appliedOverlayText,
            appliedColor = if (overlayMatches) Color(0xFF64B5F6) else Color(0xFFFFC107),
            mismatch = !overlayMatches,
            description = "Recommended touch controls layout for ${gameInfo.console} games."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF263238)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ℹ️",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Smart Config writes to retroplay.cfg and optional per-game overrides. Restart the game to apply new values.",
                        fontSize = 13.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 18.sp
                    )
                }
                if (onOpenSmartConfig != null) {
                    Text(
                        text = "Need adjustments? Open the Smart Config Center to switch between global and per-game automation.",
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
            }
        }

        if (onOpenSmartConfig != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenSmartConfig,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Smart Config Center")
            }
        }
    }
}

@Composable
private fun SmartConfigCard(
    icon: String,
    title: String,
    recommendedValue: String,
    recommendedColor: Color,
    appliedValue: String,
    appliedColor: Color,
    mismatch: Boolean,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF263238)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(recommendedColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                SmartConfigComparisonRow(
                    label = "Recommended",
                    value = recommendedValue,
                    color = recommendedColor
                )

                SmartConfigComparisonRow(
                    label = "Applied",
                    value = appliedValue,
                    color = appliedColor
                )

                if (mismatch) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠ Differs from recommendation",
                        fontSize = 11.sp,
                        color = Color(0xFFFFC107),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFFB0BEC5),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SmartConfigComparisonRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF9E9E9E)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun GameNotFoundContent(gameCRC: String?, console: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "⚠️",
            fontSize = 48.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Game Not Found in Database",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        console?.let {
            Text(
                text = "Console: $it",
                fontSize = 14.sp,
                color = Color(0xFF9E9E9E),
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (gameCRC != null) {
            Text(
                text = "Loaded CRC32: $gameCRC",
                fontSize = 14.sp,
                color = Color(0xFF9E9E9E),
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Text(
            text = "This ROM may be:\n• A modified/hacked version\n• A homebrew game\n• Not yet in database\n\nSome features may be limited.",
            fontSize = 14.sp,
            color = Color(0xFF9E9E9E),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun InfoRow(
    icon: String,
    label: String,
    value: String,
    highlight: Boolean = false,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 18.sp,
            modifier = Modifier.width(32.dp)
        )
        
        // Label
        Text(
            text = "$label:",
            fontSize = 15.sp,
            color = Color(0xFF9E9E9E),
            modifier = Modifier.width(120.dp)
        )
        
        // Value
        Text(
            text = value,
            fontSize = 15.sp,
            color = when {
                highlight -> Color(0xFF4CAF50)
                else -> Color.White
            },
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatFrames(frames: Int): String {
    return if (frames == 1) "1 frame" else "$frames frames"
}

private fun formatMegabytes(bytes: Int): String {
    val megabytes = bytes / (1024f * 1024f)
    return if (megabytes >= 10f) {
        String.format(Locale.US, "%.0f MB", megabytes)
    } else {
        String.format(Locale.US, "%.1f MB", megabytes)
    }
}

private fun countCheats(cheatFile: File): Int {
    return try {
        cheatFile.readLines().firstOrNull { it.trim().startsWith("cheats =") }
            ?.substringAfter("=")
            ?.trim()
            ?.toIntOrNull() ?: 0
    } catch (e: Exception) {
        0
    }
}

