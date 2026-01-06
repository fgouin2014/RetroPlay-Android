package com.retroplay.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CoreErrorDialog(
    coreName: String,
    gameName: String,
    onChangeCore: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Pas de dismiss en cliquant à l'extérieur */ },
        title = {
            Text(
                text = "Core Loading Failed",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "$coreName could not load:",
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = gameName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bouton "Change Core"
                TextButton(
                    onClick = onChangeCore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Core", color = Color(0xFFE91E63), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                // Bouton "Retry"
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry", color = Color(0xFF4CAF50), fontSize = 16.sp)
                }
                
                // Bouton "Cancel"
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Color(0xFF9E9E9E), fontSize = 16.sp)
                }
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
