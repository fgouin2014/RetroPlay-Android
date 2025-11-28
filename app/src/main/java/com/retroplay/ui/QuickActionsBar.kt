package com.retroplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.retroplay.R
import java.util.Locale

@Composable
fun QuickActionsBar(
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
    isRewindSupported: Boolean,
    isRewinding: Boolean,
    rewindDurationSeconds: Float,
    onRewindPress: () -> Unit,
    onRewindRelease: () -> Unit,
    onToggleFastForward: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleShader: () -> Unit = {},
    currentShaderName: String = "None",
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarHeight = remember {
        derivedStateOf {
            val insets = ViewCompat.getRootWindowInsets(view)
            val topInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
            val heightDp = with(density) { topInset.toDp() }
            if (heightDp.value < 5f) 40.dp else heightDp
        }
    }

    val rewindBufferText = if (isRewindSupported) {
        String.format(Locale.US, "%.1fs", rewindDurationSeconds.coerceAtLeast(0f))
    } else {
        "--"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()  // Ne prendre que la hauteur nécessaire, pas toute la hauteur
            .padding(top = statusBarHeight.value)
            .background(BarBackground)
            .pointerInteropFilter { event ->
                // Détecter les touches sur la barre pour réafficher (auto-hide)
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    // Le timer sera réinitialisé par l'activité parente
                    // On retourne false pour laisser passer les touches aux boutons
                }
                false  // Ne pas consommer l'événement
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RewindAction(
                label = rewindBufferText,
                isSupported = isRewindSupported,
                isRewinding = isRewinding,
                onPress = onRewindPress,
                onRelease = onRewindRelease
            )

            IconAction(
                iconRes = R.drawable.ic_fast_forward_24,
                tint = if (isFastForwardActive) FastForwardColor else DefaultIconColor,
                onClick = onToggleFastForward
            )

            IconAction(
                iconRes = if (audioMuted) R.drawable.ic_volume_off_24 else R.drawable.ic_volume_up_24,
                tint = if (audioMuted) AudioMuteColor else DefaultIconColor,
                onClick = onToggleAudioMute
            )

            IconAction(
                iconRes = R.drawable.ic_save_24,
                tint = AccentUtilityColor,
                onClick = onQuickSave
            )

            IconAction(
                iconRes = R.drawable.ic_restore_24,
                tint = AccentUtilityColor,
                onClick = onQuickLoad
            )

            IconAction(
                iconRes = R.drawable.ic_palette_24,
                tint = if (currentShaderName != "None (Fast)") ShaderActiveColor else DefaultIconColor,
                onClick = onCycleShader
            )

            IconAction(
                iconRes = R.drawable.ic_settings_24,
                tint = AccentSettingsColor,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun RewindAction(
    label: String,
    isSupported: Boolean,
    isRewinding: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val tint = when {
        isRewinding -> RewindActiveColor
        isSupported -> RewindReadyColor
        else -> DisabledIconColor
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
        modifier = if (isSupported) {
            modifier.pressAndHold(onPress, onRelease)
        } else {
            modifier
        }

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rewind_24),
                contentDescription = "Rewind",
                tint = tint
            )
        }

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = tint.copy(alpha = if (isSupported) 0.9f else 0.6f)
        )
    }
}

@Composable
private fun IconAction(
    iconRes: Int,
    tint: Color,
    onClick: (() -> Unit)? = null
) {
    var modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
    modifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint
        )
    }
}

private fun Modifier.pressAndHold(
    onPress: () -> Unit,
    onRelease: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown()
        onPress()
        waitForUpOrCancellation()
        onRelease()
    }
}

private val BarBackground = Color(0xB3000000)
private val DefaultIconColor = Color.White
private val DisabledIconColor = Color.White.copy(alpha = 0.35f)
private val RewindReadyColor = Color(0xFF4DD0E1)
private val RewindActiveColor = Color(0xFF03A9F4)
private val FastForwardColor = Color(0xFFFF7043)
private val AudioMuteColor = Color(0xFFF44336)
private val ShaderActiveColor = Color(0xFFB388FF)
private val AccentUtilityColor = Color(0xFF81D4FA)
private val AccentSettingsColor = Color(0xFFFFB74D)

