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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.retroplay.R
import kotlinx.coroutines.delay

/**
 * Quick Actions Bar - Variante F (Hybrid)
 * 
 * Barre overlay pour actions rapides en jeu :
 * - Mode compact : Mini-icons + menu [⋮]
 * - Mode expand : Barre complète avec toutes les actions
 * - Auto-collapse après 3s
 * - Quick toggles directs sur états visibles
 */
@Composable
fun QuickActionsBar(
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
    onToggleFastForward: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleShader: () -> Unit = {},  // Quick Win #4
    currentShaderName: String = "None",
    modifier: Modifier = Modifier
) {
    // État expand/collapse
    var isExpanded by remember { mutableStateOf(false) }
    
    // Auto-collapse après 3 secondes
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(3000)
            isExpanded = false
        }
    }
    
    // Animation pulse pour Fast Forward actif
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
    
    // Récupérer le padding de la status bar pour éviter le cutout
    // En mode fullscreen, utiliser un padding minimum de 40dp pour le cutout
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarHeight = remember {
        derivedStateOf {
            val insets = ViewCompat.getRootWindowInsets(view)
            val topInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
            val heightDp = with(density) { topInset.toDp() }
            // Si 0 (fullscreen), utiliser 40dp minimum pour le cutout
            if (heightDp.value < 5f) 40.dp else heightDp
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarHeight.value)  // Padding pour éviter le cutout (min 40dp)
            .background(Color(0x80000000))  // Noir 50% transparent
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        if (isExpanded) {
            // MODE EXPAND : Barre complète
            ExpandedBar(
                isFastForwardActive = isFastForwardActive,
                audioMuted = audioMuted,
                ffPulseAlpha = ffPulseAlpha,
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
            // MODE COMPACT : Mini-icons + menu
            CompactBar(
                isFastForwardActive = isFastForwardActive,
                audioMuted = audioMuted,
                ffPulseAlpha = ffPulseAlpha,
                onToggleFastForward = onToggleFastForward,
                onToggleAudioMute = onToggleAudioMute,
                onExpand = { isExpanded = true },
                currentShaderName = currentShaderName
            )
        }
    }
}

/**
 * Mode Compact : Seulement états actifs + icône menu
 */
@Composable
private fun CompactBar(
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
    ffPulseAlpha: Float,
    onToggleFastForward: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onExpand: () -> Unit,
    currentShaderName: String = "None"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // États actifs à gauche
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fast Forward (si actif)
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
            
            // Audio Muted (si muted)
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
            
            // Shader actif (si différent de None)
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
                        text = currentShaderName.take(6),  // 6 caractères max en mode compact
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9C27B0)
                    )
                }
            }
            
            // Si rien d'actif, afficher placeholder
            if (!isFastForwardActive && !audioMuted && currentShaderName == "None (Fast)") {
                Text(
                    text = "RetroPlay",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        
        // Icône menu à droite
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

/**
 * Mode Expand : Barre complète avec toutes les actions
 */
@Composable
private fun ExpandedBar(
    isFastForwardActive: Boolean,
    audioMuted: Boolean,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fast Forward
        ActionButton(
            icon = "⚡",
            label = if (isFastForwardActive) "2x" else "",
            isActive = isFastForwardActive,
            activeColor = Color(0xFFFF5722),
            alpha = if (isFastForwardActive) ffPulseAlpha else 1f,
            onClick = onToggleFastForward
        )
        
        // Audio Mute
        ActionButton(
            icon = if (audioMuted) "🔇" else "🔊",
            label = "",
            isActive = audioMuted,
            activeColor = Color(0xFFF44336),
            onClick = onToggleAudioMute
        )
        
        // Quick Save
        ActionButton(
            icon = "💾",
            label = "",
            isActive = false,
            onClick = onQuickSave
        )
        
        // Quick Load
        ActionButton(
            icon = "📂",
            label = "",
            isActive = false,
            onClick = onQuickLoad
        )
        
        // Shader Cycle (Quick Win #4) - Afficher le nom du shader
        ActionButton(
            icon = "🎨",
            label = currentShaderName.take(8),  // 8 premiers caractères max
            isActive = currentShaderName != "None (Fast)",
            activeColor = Color(0xFF9C27B0),  // Purple pour shader actif
            onClick = onCycleShader
        )
        
        // Settings
        ActionButton(
            icon = "⚙️",
            label = "",
            isActive = false,
            onClick = onOpenSettings
        )
        
        // Collapse button
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

/**
 * Bouton d'action avec icône et label optionnel
 */
@Composable
private fun ActionButton(
    icon: String,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color.White,
    alpha: Float = 1f,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.White,
        animationSpec = tween(300),
        label = "button_color"
    )
    
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            fontSize = 28.sp,
            color = color.copy(alpha = alpha)
        )
        
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = alpha)
            )
        }
    }
}

