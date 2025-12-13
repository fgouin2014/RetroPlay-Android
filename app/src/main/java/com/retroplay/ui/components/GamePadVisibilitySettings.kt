package com.retroplay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.overlay.assets.OverlayAssetManager
import com.retroplay.overlay.models.OverlayPreferenceManager

/**
 * Composable pour l'onglet Visibility (Overlays) de GamePadSettingsDialog
 * Extrait pour modularité et réutilisabilité
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePadVisibilitySettings(
    console: String,
    prefs: android.content.SharedPreferences,
    assetManager: OverlayAssetManager,
    overlayPackages: List<String>,
    customBrowsed: List<String>,
    availableLayouts: List<String>,
    selectedOverlay: String,
    selectedCustomPath: String?,
    selectedLandscapeLayout: String,
    selectedPortraitLayout: String,
    autoRotate: Boolean,
    swapAnalogSticks: Boolean,
    invertAnalogLeftY: Boolean,
    invertAnalogRightY: Boolean,
    scale: Float,
    xOffset: Float,
    yOffset: Float,
    xSeparation: Float,
    ySeparation: Float,
    onSelectedOverlayChanged: (String) -> Unit,
    onSelectedCustomPathChanged: (String?) -> Unit,
    onSelectedLandscapeLayoutChanged: (String) -> Unit,
    onSelectedPortraitLayoutChanged: (String) -> Unit,
    onAutoRotateChanged: (Boolean) -> Unit,
    onSwapAnalogSticksChanged: (Boolean) -> Unit,
    onInvertAnalogLeftYChanged: (Boolean) -> Unit,
    onInvertAnalogRightYChanged: (Boolean) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onXOffsetChanged: (Float) -> Unit,
    onYOffsetChanged: (Float) -> Unit,
    onXSeparationChanged: (Float) -> Unit,
    onYSeparationChanged: (Float) -> Unit,
    onCustomBrowsedUpdated: () -> Unit,
    onLoadCustomCfg: (() -> Unit)? = null
) {
    // ========== SECTION: GAMEPAD OVERLAYS ==========
    Text(
        text = "GamePad Overlays",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF4CAF50),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    Text(
        text = "Note: For advanced overlay settings (sensitivity, opacity, lightgun, mouse), see 'Advanced Overlay Settings' in the main menu",
        fontSize = 10.sp,
        color = Color(0xFF666666),
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Overlay Package",
            color = Color(0xFFFF9800),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (overlayPackages.isEmpty() && customBrowsed.isEmpty()) {
            Text(
                text = "No overlays available for this console.",
                color = Color(0xFF888888),
                fontSize = 13.sp
            )
        } else {
            overlayPackages.forEach { overlayName ->
                val isSelected = selectedOverlay == overlayName && selectedCustomPath == null
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectedCustomPathChanged(null)
                            onSelectedOverlayChanged(overlayName)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1B5E20).copy(alpha = 0.6f) else Color(0x10FFFFFF)
                    ),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF4CAF50) else Color(0x30FFFFFF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectedCustomPathChanged(null)
                                onSelectedOverlayChanged(overlayName)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF4CAF50),
                                unselectedColor = Color(0xFF777777)
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = overlayName,
                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Official RetroArch overlay",
                                color = Color(0xFF777777),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (customBrowsed.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF333333))
            Text(
                text = "Custom Overlays (File Picker)",
                color = Color(0xFFFF9800),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            customBrowsed.forEach { customPath ->
                val overlayName = customPath.substringBefore("/")
                val isSelected = selectedCustomPath == customPath
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectedCustomPathChanged(customPath)
                            onSelectedOverlayChanged(overlayName)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF311B92).copy(alpha = 0.6f) else Color(0x10FFFFFF)
                    ),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF9575CD) else Color(0x30FFFFFF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectedCustomPathChanged(customPath)
                                onSelectedOverlayChanged(overlayName)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF9575CD),
                                unselectedColor = Color(0xFF777777)
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customPath,
                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Imported from storage",
                                color = Color(0xFF777777),
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = {
                            OverlayPreferenceManager.removeCustomBrowsed(prefs, console, customPath)
                            onCustomBrowsedUpdated()
                            if (selectedCustomPath == customPath) {
                                onSelectedCustomPathChanged(null)
                                if (overlayPackages.isNotEmpty()) {
                                    onSelectedOverlayChanged(overlayPackages.first())
                                } else {
                                    onSelectedOverlayChanged("")
                                }
                            }
                        }) {
                            Text(
                                text = "X",
                                color = Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (onLoadCustomCfg != null) {
            TextButton(
                onClick = onLoadCustomCfg,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color(0xFF2196F3).copy(alpha = 0.15f)
                )
            ) {
                Text("Load Custom .cfg", color = Color(0xFF90CAF9))
            }
        }
    }

    if (selectedOverlay.isNotEmpty() && availableLayouts.isNotEmpty()) {
        HorizontalDivider(color = Color(0xFF444444))
        Text(
            text = "Layout Selection",
            color = Color(0xFFFF9800),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        val landscapeLayouts = availableLayouts.filter { it.contains("landscape", ignoreCase = true) }
        val portraitLayouts = availableLayouts.filter { it.contains("portrait", ignoreCase = true) }

        if (landscapeLayouts.isNotEmpty()) {
            Text("Landscape (${landscapeLayouts.size} layouts)", color = Color(0xFFBBBBBB), fontSize = 13.sp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                landscapeLayouts.forEach { layoutName ->
                    val isSelected = selectedLandscapeLayout == layoutName
                    TextButton(
                        onClick = { onSelectedLandscapeLayoutChanged(layoutName) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0x20FFFFFF)
                        ),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFC107) else Color(0x30FFFFFF)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = layoutDisplayName(layoutName),
                            color = if (isSelected) Color.Black else Color(0xFFBDBDBD),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (portraitLayouts.isNotEmpty()) {
            Text("Portrait (${portraitLayouts.size} layouts)", color = Color(0xFFBBBBBB), fontSize = 13.sp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                portraitLayouts.forEach { layoutName ->
                    val isSelected = selectedPortraitLayout == layoutName
                    TextButton(
                        onClick = { onSelectedPortraitLayoutChanged(layoutName) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0x20FFFFFF)
                        ),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFC107) else Color(0x30FFFFFF)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = layoutDisplayName(layoutName),
                            color = if (isSelected) Color.Black else Color(0xFFBDBDBD),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = autoRotate,
                onCheckedChange = onAutoRotateChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF4CAF50),
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                    uncheckedThumbColor = Color(0xFF777777),
                    uncheckedTrackColor = Color(0x40FFFFFF)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Auto-switch on rotation", color = Color.White, fontSize = 13.sp)
                Text("Switch layouts with device orientation", color = Color(0xFF888888), fontSize = 11.sp)
            }
        }
    }

    HorizontalDivider(color = Color(0xFF444444))

    Text(
        text = "Analog Options",
        color = Color(0xFFFF9800),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    SwitchRow(
        title = "Swap Left/Right Sticks",
        subtitle = "Exchange left and right analog positions",
        checked = swapAnalogSticks,
        onCheckedChange = onSwapAnalogSticksChanged
    )

    SwitchRow(
        title = "Invert Left Stick Y",
        subtitle = "Reverse up/down movement for left analog",
        checked = invertAnalogLeftY,
        onCheckedChange = onInvertAnalogLeftYChanged
    )

    SwitchRow(
        title = "Invert Right Stick Y",
        subtitle = "Reverse up/down movement for right analog",
        checked = invertAnalogRightY,
        onCheckedChange = onInvertAnalogRightYChanged
    )

    HorizontalDivider(color = Color(0xFF444444))
    Text("Position & Scale", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SliderWithLabel(
            label = "Scale",
            value = scale,
            onValueChange = onScaleChanged,
            valueRange = 0.5f..1.5f,
            displayValue = "${String.format("%.2f", scale)}x",
            activeColor = Color(0xFF4CAF50)
        )
        SliderWithLabel(
            label = "X Offset",
            value = xOffset,
            onValueChange = onXOffsetChanged,
            valueRange = -0.2f..0.2f,
            displayValue = String.format("%.3f", xOffset),
            activeColor = Color(0xFF2196F3)
        )
        SliderWithLabel(
            label = "Y Offset",
            value = yOffset,
            onValueChange = onYOffsetChanged,
            valueRange = -0.2f..0.2f,
            displayValue = String.format("%.3f", yOffset),
            activeColor = Color(0xFFE91E63)
        )
        SliderWithLabel(
            label = "X Separation",
            value = xSeparation,
            onValueChange = onXSeparationChanged,
            valueRange = -0.2f..0.2f,
            displayValue = String.format("%.3f", xSeparation),
            activeColor = Color(0xFF00BCD4)
        )
        SliderWithLabel(
            label = "Y Separation",
            value = ySeparation,
            onValueChange = onYSeparationChanged,
            valueRange = -0.2f..0.2f,
            displayValue = String.format("%.3f", ySeparation),
            activeColor = Color(0xFF9C27B0)
        )
    }
}

