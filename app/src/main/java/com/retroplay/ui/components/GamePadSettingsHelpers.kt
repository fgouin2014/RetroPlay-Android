package com.retroplay.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Helper functions pour GamePadSettingsDialog
 * Extrait pour réutilisabilité et modularité
 * 
 * Note: snapToZero et layoutDisplayName sont déjà définis dans SharedSettingsComponents.kt
 */

/**
 * Composable pour les paramètres de visibilité avancés
 * Extrait de GamePadSettingsDialog pour modularité
 */
@Composable
fun AdvancedVisibilitySettingsContent(
    hideInMenu: Boolean,
    onHideInMenuChanged: (Boolean) -> Unit,
    behindMenu: Boolean,
    onBehindMenuChanged: (Boolean) -> Unit,
    hideWhenGamepad: Boolean,
    onHideWhenGamepadChanged: (Boolean) -> Unit,
    hideWhenGamepadPort0Only: Boolean,
    onHideWhenGamepadPort0OnlyChanged: (Boolean) -> Unit
) {
    Text(
        "Behavior",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    
    // Hide in Menu
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Hide in Menu",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "Hide overlay when RetroArch menu is open",
                color = Color(0xFF888888),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = hideInMenu,
            onCheckedChange = onHideInMenuChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4CAF50),
                checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
    }
    
    // Behind Menu
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Behind Menu",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "Show overlay behind RetroArch menu (transparent)",
                color = Color(0xFF888888),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = behindMenu,
            onCheckedChange = onBehindMenuChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF2196F3),
                checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
    }
    
    // Hide When Gamepad Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Hide When Gamepad Connected",
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                "Hide touch overlay when physical gamepad is detected",
                color = Color(0xFF888888),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = hideWhenGamepad,
            onCheckedChange = onHideWhenGamepadChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF9800),
                checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF444444)
            )
        )
    }
    
    // Hide When Gamepad Connected - Port 0 Only (multijoueur)
    if (hideWhenGamepad) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Port 0 Only (Multijoueur)",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    "Hide overlay only if gamepad on port 0. Allows player 2 to use overlay touch",
                    color = Color(0xFF888888),
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = hideWhenGamepadPort0Only,
                onCheckedChange = onHideWhenGamepadPort0OnlyChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF9800),
                    checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF444444)
                )
            )
        }
    }
}

