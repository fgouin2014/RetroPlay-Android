package com.retroplay.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.retroplay.CrosshairMode

@Composable
fun QuickActionsMenu(
    onDismiss: () -> Unit,
    onHideOverlay: () -> Unit,
    onSettings: () -> Unit,
    onGameInfo: () -> Unit,
    onOpenGallery: () -> Unit,
    onSmartConfig: () -> Unit,
    onCheats: () -> Unit,
    onSaveState: (Int) -> Unit, // Slot 0
    onLoadState: (Int) -> Unit, // Slot 0
    onSaveStateClick: () -> Unit,
    onLoadStateClick: () -> Unit,
    onQuit: () -> Unit,
    onToggleFastForward: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onCycleShader: () -> Unit,
    onToggleQuickActionsBar: () -> Unit,
    onConfigureZapper: () -> Unit,
    onToggleCrosshairMode: () -> Unit,
    overlaysVisible: Boolean,
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
    currentShaderName: String,
    quickActionsBarVisible: Boolean,
    isZapperGame: Boolean,
    crosshairMode: CrosshairMode,
    hasGameInfo: Boolean,
    hasGallery: Boolean,
    hasCheats: Boolean,
    rewindBufferSeconds: Float,
    isRewindSupported: Boolean,
    isRewinding: Boolean
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Layout / Overlay Toggle
                Button(onClick = onHideOverlay, modifier = Modifier.fillMaxWidth()) {
                    Text(if (overlaysVisible) "Hide Overlay" else "Show Overlay")
                }

                // Fast Forward
                Button(
                    onClick = onToggleFastForward, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFastForwardActive) Color(0xFF4CAF50) else Color(0xFF2196F3)
                    )
                ) {
                    Text(if (isFastForwardActive) "Fast Forward: ON" else "Fast Forward: OFF")
                }

                // Audio Mute
                Button(
                    onClick = onToggleAudioMute, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (audioMuted) Color(0xFFF44336) else Color(0xFF2196F3)
                    )
                ) {
                    Text(if (audioMuted) "Unmute Audio" else "Mute Audio")
                }

                // Shader
                Button(onClick = onCycleShader, modifier = Modifier.fillMaxWidth()) {
                    Text("Shader: $currentShaderName")
                }
                
                // Quick Actions Bar
                Button(onClick = onToggleQuickActionsBar, modifier = Modifier.fillMaxWidth()) {
                    Text(if (quickActionsBarVisible) "Hide Quick Bar" else "Show Quick Bar")
                }

                Divider(color = Color.Gray)

                // Save/Load Quick (Slot 0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onSaveState(0) }, modifier = Modifier.weight(1f)) {
                        Text("Save (Quick)")
                    }
                    Button(onClick = { onLoadState(0) }, modifier = Modifier.weight(1f)) {
                        Text("Load (Quick)")
                    }
                }
                
                // Save/Load Slots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onSaveStateClick, modifier = Modifier.weight(1f)) {
                        Text("Save to Slot...")
                    }
                    Button(onClick = onLoadStateClick, modifier = Modifier.weight(1f)) {
                        Text("Load from Slot...")
                    }
                }

                Divider(color = Color.Gray)

                // Menus
                Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Main Menu")
                }
                


                 if (hasGameInfo) {
                    Button(onClick = onGameInfo, modifier = Modifier.fillMaxWidth()) {
                        Text("Game Info")
                    }
                }

                if (hasCheats) {
                    Button(onClick = onCheats, modifier = Modifier.fillMaxWidth()) {
                        Text("Cheats")
                    }
                }
                
                // Zapper
                if (isZapperGame) {
                    Divider(color = Color.Gray)
                    Button(onClick = onConfigureZapper, modifier = Modifier.fillMaxWidth()) {
                        Text("Configure Zapper")
                    }
                    Button(onClick = onToggleCrosshairMode, modifier = Modifier.fillMaxWidth()) {
                        Text("Crosshair: ${crosshairMode.name}")
                    }
                }

                Divider(color = Color.Gray)

                // Quit
                Button(
                    onClick = onQuit, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Text("Quit Game", color = Color.White)
                }
            }
        }
    }
}
