package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.overlay.assets.OverlayAssetManager
import com.retroplay.overlay.models.OverlayPreference
import com.retroplay.overlay.models.OverlayPreferenceManager
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetroArchSettingsDialog(
    console: String,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    onLoadCustomCfg: (() -> Unit)? = null,
    debugModeState: MutableState<Boolean>
) {
    val assetManager = remember { OverlayAssetManager(context) }

    var overlayPackages by remember { mutableStateOf(emptyList<String>()) }
    var customBrowsed by remember { mutableStateOf(OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()) }

    var selectedOverlay by remember { mutableStateOf("") }
    var selectedCustomPath by remember { mutableStateOf<String?>(null) }
    var selectedLandscapeLayout by remember { mutableStateOf("landscape-A") }
    var selectedPortraitLayout by remember { mutableStateOf("portrait-A") }
    var autoRotate by remember { mutableStateOf(true) }
    var swapAnalogSticks by remember { mutableStateOf(false) }
    var invertAnalogY by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1.0f) }
    var xOffset by remember { mutableStateOf(0.0f) }
    var yOffset by remember { mutableStateOf(0.0f) }
    var xSeparation by remember { mutableStateOf(0.0f) }
    var ySeparation by remember { mutableStateOf(0.0f) }

    var isPreviewTransparent by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()

    LaunchedEffect(console) {
        overlayPackages = assetManager.getCompatibleOverlays(console)
        val pref = OverlayPreferenceManager.load(prefs, console)
        if (pref != null) {
            selectedOverlay = pref.overlayName
            selectedCustomPath = pref.customCfgName?.let { "${pref.overlayName}/$it" }
            selectedLandscapeLayout = pref.landscapeLayout
            selectedPortraitLayout = pref.portraitLayout
            autoRotate = pref.autoRotate
            swapAnalogSticks = pref.swapAnalogSticks
            invertAnalogY = pref.invertAnalogY
            scale = pref.scale
            xOffset = pref.xOffset
            yOffset = pref.yOffset
            xSeparation = pref.xSeparation
            ySeparation = pref.ySeparation
        } else if (overlayPackages.isNotEmpty()) {
            val defaultOverlay = overlayPackages.first()
            val (land, port) = computeDefaultLayouts(assetManager, defaultOverlay, console, null)
            selectedOverlay = defaultOverlay
            selectedCustomPath = null
            selectedLandscapeLayout = land
            selectedPortraitLayout = port
            autoRotate = true
            swapAnalogSticks = false
            invertAnalogY = false
            scale = 1.0f
            xOffset = 0.0f
            yOffset = 0.0f
            xSeparation = 0.0f
            ySeparation = 0.0f

            val defaultPref = OverlayPreference(
                enabled = true,
                overlayName = defaultOverlay,
                customCfgName = null,
                landscapeLayout = land,
                portraitLayout = port,
                autoRotate = true,
                swapAnalogSticks = false,
                invertAnalogY = false,
                scale = 1.0f,
                xOffset = 0.0f,
                yOffset = 0.0f,
                xSeparation = 0.0f,
                ySeparation = 0.0f
            )
            OverlayPreferenceManager.save(prefs, console, defaultPref)
        }
    }

    DisposableEffect(console) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "overlay_${console}_custom_browsed") {
                customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
            }
            if (key == "overlay_${console}_name" || key == "overlay_${console}_custom_cfg") {
                val updatedPref = OverlayPreferenceManager.load(prefs, console)
                if (updatedPref != null) {
                    selectedOverlay = updatedPref.overlayName
                    selectedCustomPath = updatedPref.customCfgName?.let { "${updatedPref.overlayName}/$it" }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(debugModeState.value) {
        prefs.edit()
            .putBoolean("overlay_debug_mode", debugModeState.value)
            .commit()
    }

    val customCfgName = selectedCustomPath?.substringAfter("/")
    val availableLayouts = remember(selectedOverlay, customCfgName) {
        if (selectedOverlay.isNotEmpty()) {
            assetManager.getAvailableLayouts(selectedOverlay, console, customCfgName)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(availableLayouts, selectedLandscapeLayout) {
        if (availableLayouts.isNotEmpty()) {
            val landscapeLayouts = availableLayouts.filter { it.contains("landscape", ignoreCase = true) }
            val portraitLayouts = availableLayouts.filter { it.contains("portrait", ignoreCase = true) }

            if (landscapeLayouts.isNotEmpty() && !landscapeLayouts.contains(selectedLandscapeLayout)) {
                selectedLandscapeLayout = landscapeLayouts.first()
            } else if (landscapeLayouts.isEmpty() && !availableLayouts.contains(selectedLandscapeLayout)) {
                selectedLandscapeLayout = availableLayouts.first()
            }

            if (portraitLayouts.isNotEmpty() && !portraitLayouts.contains(selectedPortraitLayout)) {
                selectedPortraitLayout = portraitLayouts.first()
            } else if (portraitLayouts.isEmpty() && !availableLayouts.contains(selectedPortraitLayout)) {
                selectedPortraitLayout = selectedLandscapeLayout
            }
        }
    }

    LaunchedEffect(
        selectedOverlay,
        selectedCustomPath,
        selectedLandscapeLayout,
        selectedPortraitLayout,
        autoRotate,
        swapAnalogSticks,
        invertAnalogY,
        scale,
        xOffset,
        yOffset,
        xSeparation,
        ySeparation
    ) {
        if (selectedOverlay.isNotEmpty()) {
            val pref = OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                customCfgName = selectedCustomPath?.substringAfter("/"),
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogY = invertAnalogY,
                scale = scale,
                xOffset = xOffset,
                yOffset = yOffset,
                xSeparation = xSeparation,
                ySeparation = ySeparation
            )
            OverlayPreferenceManager.save(prefs, console, pref)
            isPreviewTransparent = true
            delay(1200)
            isPreviewTransparent = false
        }
    }

    val containerAlpha = if (isPreviewTransparent) 0.32f else 0.42f

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF000000).copy(alpha = containerAlpha)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "RetroArch Overlay Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        text = "Console: ${console.uppercase()}",
                        fontSize = 14.sp,
                        color = Color(0xFFBBBBBB)
                    )

                    HorizontalDivider(color = Color(0xFF444444))

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
                                            selectedCustomPath = null
                                            selectedOverlay = overlayName
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFF1C1C1C)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF4CAF50) else Color(0xFF2F2F2F))
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
                                                selectedCustomPath = null
                                                selectedOverlay = overlayName
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
                                            selectedCustomPath = customPath
                                            selectedOverlay = overlayName
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFF311B92).copy(alpha = 0.45f) else Color(0xFF1C1C1C)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF9575CD) else Color(0xFF2F2F2F))
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
                                                selectedCustomPath = customPath
                                                selectedOverlay = overlayName
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
                                            customBrowsed = OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList()
                                            if (selectedCustomPath == customPath) {
                                                selectedCustomPath = null
                                                if (overlayPackages.isNotEmpty()) {
                                                    selectedOverlay = overlayPackages.first()
                                                } else {
                                                    selectedOverlay = ""
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
                            Text("Landscape", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                landscapeLayouts.forEach { layoutName ->
                                    val isSelected = selectedLandscapeLayout == layoutName
                                    TextButton(
                                        onClick = { selectedLandscapeLayout = layoutName },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF2A2A2A)
                                        ),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFC107) else Color(0xFF3A3A3A)),
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
                            Text("Portrait", color = Color(0xFFBBBBBB), fontSize = 13.sp)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                portraitLayouts.forEach { layoutName ->
                                    val isSelected = selectedPortraitLayout == layoutName
                                    TextButton(
                                        onClick = { selectedPortraitLayout = layoutName },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF2A2A2A)
                                        ),
                                        shape = RoundedCornerShape(50),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFFC107) else Color(0xFF3A3A3A)),
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
                                onCheckedChange = { autoRotate = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF4CAF50),
                                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color(0xFF777777),
                                    uncheckedTrackColor = Color(0xFF444444)
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
                    Text("Analog Options", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = swapAnalogSticks,
                            onCheckedChange = { swapAnalogSticks = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF2196F3),
                                checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color(0xFF777777),
                                uncheckedTrackColor = Color(0xFF444444)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Swap Left/Right sticks", color = Color.White, fontSize = 13.sp)
                            Text("Exchange L and R analog sticks", color = Color(0xFF888888), fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = invertAnalogY,
                            onCheckedChange = { invertAnalogY = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFE91E63),
                                checkedTrackColor = Color(0xFFE91E63).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color(0xFF777777),
                                uncheckedTrackColor = Color(0xFF444444)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Invert Y axis", color = Color.White, fontSize = 13.sp)
                            Text("Reverse up/down analog input", color = Color(0xFF888888), fontSize = 11.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = debugModeState.value,
                            onCheckedChange = { debugModeState.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFEB3B),
                                checkedTrackColor = Color(0xFFFFEB3B).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color(0xFF777777),
                                uncheckedTrackColor = Color(0xFF444444)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Debug hitboxes", color = Color(0xFFFFEB3B), fontSize = 13.sp)
                            Text("Display overlay hitboxes for tuning", color = Color(0xFF888888), fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF444444))
                    Text("Position & Scale", color = Color(0xFFFF9800), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SliderWithLabel(
                            label = "Scale",
                            value = scale,
                            onValueChange = { scale = it },
                            valueRange = 0.5f..1.5f,
                            displayValue = "${String.format("%.2f", scale)}x",
                            activeColor = Color(0xFF4CAF50)
                        )
                        SliderWithLabel(
                            label = "X Offset",
                            value = xOffset,
                            onValueChange = { xOffset = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", xOffset),
                            activeColor = Color(0xFF2196F3)
                        )
                        SliderWithLabel(
                            label = "Y Offset",
                            value = yOffset,
                            onValueChange = { yOffset = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", yOffset),
                            activeColor = Color(0xFFE91E63)
                        )
                        SliderWithLabel(
                            label = "X Separation",
                            value = xSeparation,
                            onValueChange = { xSeparation = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", xSeparation),
                            activeColor = Color(0xFF00BCD4)
                        )
                        SliderWithLabel(
                            label = "Y Separation",
                            value = ySeparation,
                            onValueChange = { ySeparation = snapToZero(it) },
                            valueRange = -0.2f..0.2f,
                            displayValue = String.format("%.3f", ySeparation),
                            activeColor = Color(0xFF9C27B0)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF444444))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (overlayPackages.isNotEmpty()) {
                                    val defaultOverlay = overlayPackages.first()
                                    val (land, port) = computeDefaultLayouts(assetManager, defaultOverlay, console, null)
                                    selectedOverlay = defaultOverlay
                                    selectedCustomPath = null
                                    selectedLandscapeLayout = land
                                    selectedPortraitLayout = port
                                    autoRotate = true
                                    swapAnalogSticks = false
                                    invertAnalogY = false
                                    scale = 1.0f
                                    xOffset = 0.0f
                                    yOffset = 0.0f
                                    xSeparation = 0.0f
                                    ySeparation = 0.0f
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                        ) {
                            Text("Reset Overlay", color = Color.White)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun layoutDisplayName(layoutName: String): String {
    val lower = layoutName.lowercase()
    return when {
        lower.contains("both-analog") -> "Both Analog"
        lower.contains("left-analog") && lower.contains("menu") -> "Left Analog + Menu"
        lower.contains("left-analog") -> "Left Analog"
        lower.contains("right-analog") -> "Right Analog"
        lower.contains("analog") && lower.contains("menu") -> "Analog + Menu"
        lower.contains("analog") -> "Analog"
        lower.contains("menu") -> "Menu"
        lower.endsWith("-b") -> "Variant B"
        lower.endsWith("-c") -> "Variant C"
        else -> "Digital"
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    activeColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(displayValue, color = activeColor, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = Color(0xFF3A3A3A)
            )
        )
    }
}

private fun snapToZero(value: Float): Float = if (abs(value) < 0.01f) 0.0f else value

private fun computeDefaultLayouts(
    assetManager: OverlayAssetManager,
    overlayName: String,
    console: String,
    customCfgName: String?
): Pair<String, String> {
    val layouts = assetManager.getAvailableLayouts(overlayName, console, customCfgName)
    val landscape = layouts.firstOrNull { it.contains("landscape", ignoreCase = true) }
        ?: layouts.firstOrNull()
        ?: "landscape-A"
    val portrait = layouts.firstOrNull { it.contains("portrait", ignoreCase = true) }
        ?: landscape
    return landscape to portrait
}
