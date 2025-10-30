package com.retroplay

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * RetroArch Settings Dialog
 * 
 * Simplified dialog for configuring ONLY RetroArch overlays:
 * - Overlay Package selection (nes-overlay, snes-overlay, etc.)
 * - Landscape layout selection (landscape-A, landscape-B, etc.)
 * - Portrait layout selection (portrait-A, portrait-B, etc.)
 * - Auto-Rotate toggle
 * 
 * NO Radial/Lemuroid gamepad settings (scale, rotation, margin)
 */
@Composable
fun RetroArchSettingsDialog(
    console: String,
    onDismiss: () -> Unit,
    context: Context,
    prefs: SharedPreferences,
    onLoadCustomCfg: (() -> Unit)? = null  // Callback pour ouvrir file picker
) {
    // Overlay Asset Manager
    val assetManager = remember { com.retroplay.overlay.assets.OverlayAssetManager(context) }
    val availableOverlays = remember { assetManager.getCompatibleOverlays(console) }
    
    // Load custom browsed overlays
    val customBrowsed = remember { com.retroplay.overlay.models.OverlayPreferenceManager.getCustomBrowsedList(prefs, console).toList() }
    
    // Load current preferences
    val currentOverlayPref = remember { com.retroplay.overlay.models.OverlayPreferenceManager.load(prefs, console) }
    var selectedOverlay by remember { 
        mutableStateOf(
            currentOverlayPref?.overlayName ?: 
            if (availableOverlays.isNotEmpty()) availableOverlays[0] else ""
        ) 
    }
    // Separate state for custom overlay path (ex: "flat/nes.cfg")
    var selectedCustomPath by remember { mutableStateOf<String?>(null) }
    
    var selectedLandscapeLayout by remember { mutableStateOf(currentOverlayPref?.landscapeLayout ?: "landscape-A") }
    var selectedPortraitLayout by remember { mutableStateOf(currentOverlayPref?.portraitLayout ?: "portrait-A") }
    var autoRotate by remember { mutableStateOf(currentOverlayPref?.autoRotate ?: true) }
    var swapAnalogSticks by remember { mutableStateOf(currentOverlayPref?.swapAnalogSticks ?: false) }
    var invertAnalogY by remember { mutableStateOf(currentOverlayPref?.invertAnalogY ?: false) }
    
    // Semi-transparent state for preview (30% transparent for 2 seconds)
    var isTransparent by remember { mutableStateOf(false) }
    
    // Available layouts for selected overlay
    val availableLayouts = remember(selectedOverlay, console) {
        if (selectedOverlay.isNotEmpty()) {
            assetManager.getAvailableLayouts(selectedOverlay, console)
        } else {
            emptyList()
        }
    }
    
    // Save preferences when changed + trigger 30% transparency for 2 seconds
    LaunchedEffect(selectedOverlay, selectedLandscapeLayout, selectedPortraitLayout, autoRotate, swapAnalogSticks, invertAnalogY) {
        if (selectedOverlay.isNotEmpty()) {
            val pref = com.retroplay.overlay.models.OverlayPreference(
                enabled = true,
                overlayName = selectedOverlay,
                landscapeLayout = selectedLandscapeLayout,
                portraitLayout = selectedPortraitLayout,
                autoRotate = autoRotate,
                swapAnalogSticks = swapAnalogSticks,
                invertAnalogY = invertAnalogY
            )
            com.retroplay.overlay.models.OverlayPreferenceManager.save(prefs, console, pref)
            android.util.Log.i("RetroArchSettings", "Saved overlay pref for $console: overlay='$selectedOverlay' landscape='$selectedLandscapeLayout' portrait='$selectedPortraitLayout' autoRotate=$autoRotate swap=$swapAnalogSticks invertY=$invertAnalogY")
            
            // Trigger 30% transparency for 2 seconds to preview overlay
            isTransparent = true
            kotlinx.coroutines.delay(2000)
            isTransparent = false
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = if (isTransparent) 
                    Color(0xDD000000).copy(alpha = 0.3f)  // Preview: 30% transparent
                else 
                    Color(0xDD000000).copy(alpha = 0.4f)  // Normal: 40% transparent
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = "RetroArch Overlay Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Console: ${console.uppercase()}",
                    fontSize = 14.sp,
                    color = Color(0xFFBBBBBB),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Overlay Package Selection
                    Text(
                        "Overlay Package",
                        color = Color(0xFFFF9800),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (availableOverlays.isEmpty()) {
                        Text(
                            "No overlays available for this console",
                            color = Color(0xFF888888),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    } else {
                        availableOverlays.forEach { overlayName ->
                            // Standard overlay is selected only if no custom path is active
                            val isSelected = selectedOverlay == overlayName && selectedCustomPath == null
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (isSelected) Color(0xFF2196F3).copy(alpha = 0.3f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF2196F3) else Color(0xFF444444)
                                    )
                                    .clickable { 
                                        selectedCustomPath = null  // Clear custom selection
                                        selectedOverlay = overlayName 
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { 
                                        selectedCustomPath = null  // Clear custom selection
                                        selectedOverlay = overlayName 
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF2196F3),
                                        unselectedColor = Color(0xFF888888)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    overlayName,
                                    color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    
                    // Custom Overlays Section (browsed via file picker)
                    if (customBrowsed.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFF444444),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Text(
                            "Custom Overlays (File Picker)",
                            color = Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        customBrowsed.forEach { customPath ->
                            val customOverlayName = customPath.substringBefore("/")
                            // Compare the FULL path, not just the overlay name
                            val isSelected = selectedCustomPath == customPath
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (isSelected) Color(0xFFFF9800).copy(alpha = 0.3f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFFF9800) else Color(0xFF444444)
                                    )
                                    .clickable { 
                                        selectedCustomPath = customPath
                                        selectedOverlay = customOverlayName
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { 
                                        selectedCustomPath = customPath
                                        selectedOverlay = customOverlayName
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFFF9800),
                                        unselectedColor = Color(0xFF888888)
                                    )
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        customPath,
                                        color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "via File Picker",
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Layout Selection (only if overlay is selected and layouts are available)
                    if (selectedOverlay.isNotEmpty() && availableLayouts.isNotEmpty()) {
                        // Landscape Layout
                        Text(
                            "Landscape Layout",
                            color = Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val landscapeLayouts = availableLayouts.filter { it.contains("landscape", ignoreCase = true) }
                        if (landscapeLayouts.isEmpty()) {
                            Text(
                                "No landscape layouts available",
                                color = Color(0xFF888888),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        } else {
                            landscapeLayouts.forEach { layoutName ->
                                val isSelected = selectedLandscapeLayout == layoutName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.3f) 
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF444444)
                                        )
                                        .clickable { selectedLandscapeLayout = layoutName }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedLandscapeLayout = layoutName },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF4CAF50),
                                            unselectedColor = Color(0xFF888888)
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        layoutName,
                                        color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Portrait Layout
                        Text(
                            "Portrait Layout",
                            color = Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        val portraitLayouts = availableLayouts.filter { it.contains("portrait", ignoreCase = true) }
                        if (portraitLayouts.isEmpty()) {
                            Text(
                                "No portrait layouts available",
                                color = Color(0xFF888888),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        } else {
                            portraitLayouts.forEach { layoutName ->
                                val isSelected = selectedPortraitLayout == layoutName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            if (isSelected) Color(0xFFE91E63).copy(alpha = 0.3f) 
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFFE91E63) else Color(0xFF444444)
                                        )
                                        .clickable { selectedPortraitLayout = layoutName }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedPortraitLayout = layoutName },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFFE91E63),
                                            unselectedColor = Color(0xFF888888)
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        layoutName,
                                        color = if (isSelected) Color.White else Color(0xFFBBBBBB),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Auto-Rotate Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Auto-Rotate Overlay",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Automatically switch layout based on device orientation",
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = autoRotate,
                                onCheckedChange = { autoRotate = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF4CAF50),
                                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color(0xFF888888),
                                    uncheckedTrackColor = Color(0xFF444444)
                                )
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color(0xFF444444),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Text(
                            "Analog Stick Options",
                            color = Color(0xFFFF9800),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Swap Analog Sticks Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Swap Left/Right Sticks",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Swap analog stick L and R positions",
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = swapAnalogSticks,
                                onCheckedChange = { swapAnalogSticks = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF2196F3),
                                    checkedTrackColor = Color(0xFF2196F3).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color(0xFF888888),
                                    uncheckedTrackColor = Color(0xFF444444)
                                )
                            )
                        }
                        
                        // Invert Y Axis Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Invert Y Axis",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Invert up/down for analog sticks",
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = invertAnalogY,
                                onCheckedChange = { invertAnalogY = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFE91E63),
                                    checkedTrackColor = Color(0xFFE91E63).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color(0xFF888888),
                                    uncheckedTrackColor = Color(0xFF444444)
                                )
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Load Custom .cfg Button
                    if (onLoadCustomCfg != null) {
                        Button(
                            onClick = onLoadCustomCfg,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Text("Load Custom .cfg", color = Color.White, fontSize = 14.sp)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    
                    // Done Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("DONE", color = Color.White)
                    }
                }
            }
        }
    }
}

