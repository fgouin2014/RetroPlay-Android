package com.retroplay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay

/**
 * Quick Actions Bar - Variante F (Hybrid)
 *
 * - Mode compact : Mini-icons + menu [⋮]
 * - Mode expand : Barre complète avec toutes les actions
 * - Auto-collapse après 6s
 * - Quick toggles directs sur états visibles
 */
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
    onCycleShader: () -> Unit = {},  // Quick Win #4
    currentShaderName: String = "None",
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(6000)
            isExpanded = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ffPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ff_pulse"
    )

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarHeight.value)
            .background(Color(0x80000000))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        if (isExpanded) {
            ExpandedBar(
                isFastForwardActive = isFastForwardActive,
                audioMuted = audioMuted,
                isRewindSupported = isRewindSupported,
                isRewinding = isRewinding,
                rewindDurationSeconds = rewindDurationSeconds,
                ffPulseAlpha = ffPulseAlpha,
                onRewindPress = onRewindPress,
                onRewindRelease = onRewindRelease,
                onToggleFastForward = onToggleFastForward,
                onToggleAudioMute = onToggleAudioMute,
                onQuickSave = onQuickSave,
                onQuickLoad = onQuickLoad,
                onOpenSettings = onOpenSettings,
                onCycleShader = onCycleShader,
                currentShaderName = currentShaderName,
                onCollapse = { isExpanded = false }
            )
        } else {
            CompactBar(
                isFastForwardActive = isFastForwardActive,
                audioMuted = audioMuted,
                isRewindSupported = isRewindSupported,
                isRewinding = isRewinding,
                rewindDurationSeconds = rewindDurationSeconds,
                ffPulseAlpha = ffPulseAlpha,
                onRewindPress = onRewindPress,
                onRewindRelease = onRewindRelease,
                onToggleFastForward = onToggleFastForward,
                onToggleAudioMute = onToggleAudioMute,
                onExpand = { isExpanded = true },
                currentShaderName = currentShaderName
            )
        }
    }
}

@Composable
private fun CompactBar(
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
    isRewindSupported: Boolean,
    isRewinding: Boolean,
    rewindDurationSeconds: Float,
    ffPulseAlpha: Float,
    onRewindPress: () -> Unit,
    onRewindRelease: () -> Unit,
    onToggleFastForward: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onExpand: () -> Unit,
    currentShaderName: String = "None"
) {
    val rewindLabel = remember(rewindDurationSeconds) {
        String.format(Locale.US, "%.1fs", rewindDurationSeconds.coerceAtLeast(0f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .pressAndHold(onRewindPress, onRewindRelease)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rewindColor = when {
                    isRewinding -> Color(0xFF03A9F4)
                    isRewindSupported -> Color(0xFF90CAF9)
                    else -> Color.White.copy(alpha = 0.5f)
                }
                Text(
                    text = "RW",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = rewindColor
                )
                Text(
                    text = rewindLabel,
                    fontSize = 12.sp,
                    color = rewindColor.copy(alpha = 0.9f)
                )
            }

            AnimatedVisibility(
                visible = isFastForwardActive,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onToggleFastForward() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 20.sp,
                        color = Color(0xFFFF5722).copy(alpha = ffPulseAlpha)
                    )
                    Text(
                        text = "2x",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722).copy(alpha = ffPulseAlpha)
                    )
                }
            }

            AnimatedVisibility(
                visible = audioMuted,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = "🔇",
                    fontSize = 20.sp,
                    color = Color(0xFFF44336),
                    modifier = Modifier
                        .clickable { onToggleAudioMute() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (currentShaderName != "None (Fast)") {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎨",
                        fontSize = 16.sp,
                        color = Color(0xFF9C27B0)
                    )
                    Text(
                        text = currentShaderName.take(6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9C27B0)
                    )
                }
            }

            if (!isFastForwardActive && !audioMuted && currentShaderName == "None (Fast)" && (!isRewindSupported || rewindDurationSeconds <= 0f)) {
                Text(
                    text = "RetroPlay",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        IconButton(
            onClick = onExpand,
            modifier = Modifier.size(36.dp)
        ) {
            Text(
                text = "⋮",
                fontSize = 24.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpandedBar(
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
    isRewindSupported: Boolean,
    isRewinding: Boolean,
    rewindDurationSeconds: Float,
    onRewindPress: () -> Unit,
    onRewindRelease: () -> Unit,
    ffPulseAlpha: Float,
    onToggleFastForward: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleShader: () -> Unit,
    currentShaderName: String,
    onCollapse: () -> Unit
) {
    val rewindLabel = remember(rewindDurationSeconds) {
        if (rewindDurationSeconds <= 0f) "" else String.format(Locale.US, "%.1fs", rewindDurationSeconds)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = "RW",
            label = rewindLabel,
            isActive = isRewinding,
            activeColor = Color(0xFF03A9F4),
            onPress = onRewindPress,
            onRelease = onRewindRelease
        )

        ActionButton(
            icon = "⚡",
            label = if (isFastForwardActive) "2x" else "",
            isActive = isFastForwardActive,
            activeColor = Color(0xFFFF5722),
            alpha = if (isFastForwardActive) ffPulseAlpha else 1f,
            onClick = onToggleFastForward
        )

        ActionButton(
            icon = if (audioMuted) "🔇" else "🔊",
            label = "",
            isActive = audioMuted,
            activeColor = Color(0xFFF44336),
            onClick = onToggleAudioMute
        )

        ActionButton(
            icon = "💾",
            label = "",
            isActive = false,
            onClick = onQuickSave
        )

        ActionButton(
            icon = "📂",
            label = "",
            isActive = false,
            onClick = onQuickLoad
        )

        ActionButton(
            icon = "🎨",
            label = currentShaderName.take(8),
            isActive = currentShaderName != "None (Fast)",
            activeColor = Color(0xFF9C27B0),
            onClick = onCycleShader
        )

        ActionButton(
            icon = "⚙️",
            label = "",
            isActive = false,
            onClick = onOpenSettings
        )

        IconButton(
            onClick = onCollapse,
            modifier = Modifier.size(40.dp)
        ) {
            Text(
                text = "✕",
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: String,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color.White,
    alpha: Float = 1f,
    onClick: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val color by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.White,
        animationSpec = tween(300),
        label = "button_color"
    )

    val contentColor = if (enabled) color else Color.White.copy(alpha = 0.4f)
    val contentAlpha = if (enabled) alpha else 0.4f

    var modifier = Modifier.padding(8.dp)
    modifier = when {
        onPress != null && onRelease != null -> modifier.pressAndHold(onPress, onRelease)
        onClick != null -> modifier.clickable(enabled = enabled) { onClick() }
        else -> modifier
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            fontSize = 28.sp,
            color = contentColor.copy(alpha = contentAlpha)
        )

        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = contentAlpha)
            )
        }
    }
}

private fun Modifier.pressAndHold(
    onPress: () -> Unit,
    onRelease: () -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPress()
        val up = waitForUpOrCancellation()
        onRelease()
    }
}

