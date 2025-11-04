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
import com.retroplay.database.GameInfo
import java.io.File

@Composable
fun GameInfoDialog(
    gameInfo: GameInfo?,
    gameCRC: String?,
    cheatFile: File?,
    onDismiss: () -> Unit
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
                    text = "📊 GAME INFORMATION",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "libretro-database lookup",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (gameInfo != null) {
                    // Game found in database
                    GameInfoContent(gameInfo, gameCRC, cheatFile)
                } else {
                    // Game not found
                    GameNotFoundContent(gameCRC)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
private fun GameInfoContent(gameInfo: GameInfo, gameCRC: String?, cheatFile: File?) {
    Column {
        // Game Name
        InfoRow(
            icon = "🎮",
            label = "Game Name",
            value = gameInfo.name,
            highlight = true
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // CRC32
        if (gameCRC != null) {
            InfoRow(
                icon = "🔍",
                label = "CRC32",
                value = gameCRC,
                mono = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Genre
        gameInfo.genre?.let { genre ->
            InfoRow(
                icon = "🎭",
                label = "Genre",
                value = genre
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Developer
        gameInfo.developer?.let { dev ->
            InfoRow(
                icon = "🏢",
                label = "Developer",
                value = dev
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Publisher
        gameInfo.publisher?.let { pub ->
            InfoRow(
                icon = "📦",
                label = "Publisher",
                value = pub
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Release Year
        gameInfo.releaseYear?.let { year ->
            val fullDate = if (gameInfo.releaseMonth != null) {
                "${gameInfo.releaseMonth}/$year"
            } else {
                year.toString()
            }
            InfoRow(
                icon = "📅",
                label = "Released",
                value = fullDate
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
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
        
        if (features.isNotEmpty()) {
            InfoRow(
                icon = "⭐",
                label = "Features",
                value = features.joinToString(", ")
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
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
        
        Text(
            text = "💡 SMART CONFIG RECOMMENDATIONS",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val smartConfig = com.retroplay.database.SmartConfigManager.getSmartConfig(gameInfo, gameInfo.console)
        
        InfoRow(
            icon = "⚡",
            label = "Run-Ahead",
            value = if (smartConfig.runAheadEnabled) {
                "${smartConfig.runAheadFrames} frames (input lag -${smartConfig.runAheadFrames * 16}ms)"
            } else {
                "Not recommended for ${gameInfo.genre}"
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        InfoRow(
            icon = "⏪",
            label = "Rewind",
            value = if (smartConfig.rewindEnabled) {
                "${smartConfig.rewindBufferSize / 1024 / 1024}MB buffer"
            } else {
                "Not recommended"
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        InfoRow(
            icon = "🎨",
            label = "Optimal Overlay",
            value = smartConfig.overlayName
        )
    }
}

@Composable
private fun GameNotFoundContent(gameCRC: String?) {
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
        
        if (gameCRC != null) {
            Text(
                text = "CRC32: $gameCRC",
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

