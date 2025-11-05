package com.retroplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog Compose pour afficher et activer/désactiver les cheats
 */
@Composable
fun CheatsDialog(
    cheats: List<com.retroplay.cheat.CheatManager.Cheat>,
    gameName: String,
    enabledCheats: Set<Int>, // Set of enabled cheat indices
    onCheatToggle: (Int, Boolean, com.retroplay.cheat.CheatManager.Cheat) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = "🎮 CHEATS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Game name
                Text(
                    text = gameName,
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Cheat count
                Text(
                    text = "${cheats.size} cheats available",
                    fontSize = 12.sp,
                    color = Color(0xFFBBBBBB)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Cheats list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(cheats) { index, cheat ->
                        CheatItem(
                            cheat = cheat,
                            isEnabled = enabledCheats.contains(index),
                            onToggle = { enabled ->
                                onCheatToggle(index, enabled, cheat)
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
private fun CheatItem(
    cheat: com.retroplay.cheat.CheatManager.Cheat,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isEnabled) Color(0xFF2E7D32) else Color(0xFF2C2C2C)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Cheat description
                Text(
                    text = cheat.description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) Color.White else Color(0xFFCCCCCC)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Cheat code
                Text(
                    text = cheat.code,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isEnabled) Color(0xFFBBBBBB) else Color(0xFF888888)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Toggle switch
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF81C784),
                    uncheckedThumbColor = Color(0xFF666666),
                    uncheckedTrackColor = Color(0xFF444444)
                )
            )
        }
    }
}

