package com.retroplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composant réutilisable pour un slider avec boutons de nudge (-/+)
 * Utilisé pour ajuster finement les valeurs numériques dans les paramètres
 * 
 * @param label Le label affiché au-dessus du slider
 * @param value La valeur actuelle (normalisée entre 0f et 1f)
 * @param displayValue La valeur affichée à l'utilisateur (formatée)
 * @param valueRange La plage de valeurs acceptées
 * @param onValueChange Callback appelé quand la valeur change via le slider
 * @param onNudge Callback appelé quand l'utilisateur clique sur -/+ (delta en Float)
 */
@Composable
fun RadialSlider(
    label: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onNudge: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.LightGray,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Text(
                text = displayValue,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onNudge(-0.01f) },
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "-",
                    color = Color(0xFFB0BEC5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { onNudge(0.01f) },
                modifier = Modifier.size(32.dp)
            ) {
                Text(
                    text = "+",
                    color = Color(0xFFB0BEC5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

