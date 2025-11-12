package com.retroplay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.runahead.RunAheadManager

/**
 * Snapshot de l'état Smart Config à afficher dans le HUD.
 *
 * Tous les champs sont "déjà synchronisés" au moment où le composant est appelé – aucune lecture
 * de Flow ne se fait dans le composant lui-même pour garder l'affichage indépendant.
 */
data class SmartConfigOsdData(
    val runAheadStatus: RunAheadManager.RunAheadStatus,
    val runAheadEnabled: Boolean,
    val rewindSupported: Boolean,
    val rewindActive: Boolean,
    val rewindSeconds: Float,
    val fastForwardActive: Boolean,
    val fastForwardRatio: Float,
    val audioMuted: Boolean,
    val autoSmartConfig: Boolean
)

@Composable
fun SmartConfigOsd(
    modifier: Modifier = Modifier,
    data: SmartConfigOsdData,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            color = Color(0xCC212121)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Smart Config",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                SmartConfigRow(
                    label = "Run-Ahead",
                    value = if (data.runAheadEnabled && data.runAheadStatus.supported) {
                        "${data.runAheadStatus.frames} f  (hit ${data.runAheadStatus.hitRatio}%)"
                    } else if (!data.runAheadStatus.supported) {
                        "Not supported"
                    } else {
                        "Disabled"
                    },
                    indicatorColor = when {
                        !data.runAheadStatus.supported -> Color(0xFFB00020)
                        data.runAheadEnabled -> Color(0xFF76FF03)
                        else -> Color(0xFF9E9E9E)
                    }
                )

                SmartConfigRow(
                    label = "Rewind",
                    value = when {
                        !data.rewindSupported -> "Not supported"
                        data.rewindActive -> "Rewinding (${data.rewindSeconds.formatSeconds()}s)"
                        data.rewindSeconds > 0f -> "${data.rewindSeconds.formatSeconds()}s buffer"
                        else -> "Inactive"
                    },
                    indicatorColor = when {
                        !data.rewindSupported -> Color(0xFFB00020)
                        data.rewindActive -> Color(0xFF03A9F4)
                        data.rewindSeconds > 0f -> Color(0xFF4CAF50)
                        else -> Color(0xFF9E9E9E)
                    }
                )

                SmartConfigRow(
                    label = "Fast Forward",
                    value = if (data.fastForwardActive) {
                        "${data.fastForwardRatio}x"
                    } else {
                        "Disabled"
                    },
                    indicatorColor = if (data.fastForwardActive) Color(0xFFFFC107) else Color(0xFF9E9E9E)
                )

                SmartConfigRow(
                    label = "Audio",
                    value = if (data.audioMuted) "Muted" else "Normal",
                    indicatorColor = if (data.audioMuted) Color(0xFFFF6F00) else Color(0xFF80CBC4)
                )

                SmartConfigRow(
                    label = "Auto Smart Config",
                    value = if (data.autoSmartConfig) "Auto" else "Manual",
                    indicatorColor = if (data.autoSmartConfig) Color(0xFF64B5F6) else Color(0xFF9E9E9E)
                )
            }
        }
    }
}

@Composable
private fun SmartConfigRow(
    label: String,
    value: String,
    indicatorColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(indicatorColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFBDBDBD))
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
            )
        }
    }
}

private fun Float.formatSeconds(): String {
    return when {
        this >= 10f -> String.format("%.0f", this)
        this >= 1f -> String.format("%.1f", this)
        else -> String.format("%.2f", this)
    }
}

