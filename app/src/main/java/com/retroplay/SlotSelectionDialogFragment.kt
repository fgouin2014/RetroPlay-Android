package com.retroplay

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.DialogFragment
import java.io.File

class SlotSelectionDialogFragment : DialogFragment() {
    private var console: String? = null
    private var gameName: String? = null
    private var romPath: String? = null
    
    companion object {
        fun newInstance(console: String, gameName: String, romPath: String = ""): SlotSelectionDialogFragment {
            return SlotSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("console", console)
                    putString("gameName", gameName)
                    putString("romPath", romPath)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        console = arguments?.getString("console")
        gameName = arguments?.getString("gameName")
        romPath = arguments?.getString("romPath")
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get theme colors
        val themeManager = ThemeManager.getInstance(requireContext())
        val primaryColor = themeManager.getPrimaryColor(requireContext())
        val headerBgColor = themeManager.getHeaderBackgroundColor(requireContext())
        
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            SlotSelectionDialogForDetails(
                console = console ?: "",
                gameName = gameName ?: "",
                primaryColor = Color(primaryColor),
                headerBgColor = Color(headerBgColor),
                onDismiss = { dismiss() },
                onSlotSelected = { slot ->
                    dismiss()
                    val intent = Intent(requireContext(), RetroArchEmulatorActivity::class.java).apply {
                        putExtra("console", console)
                        putExtra("gameName", gameName)
                        putExtra("romPath", romPath ?: "")
                        putExtra("loadSlot", slot)
                    }
                    startActivity(intent)
                }
            )
        }
        
        dialog.setContentView(composeView)
        return dialog
    }
}

@Composable
private fun SlotSelectionDialogForDetails(
    console: String,
    gameName: String,
    primaryColor: Color,
    headerBgColor: Color,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit
) {
    // Create gradient from theme primary color
    val gradientColors = remember(primaryColor) {
        listOf(
            primaryColor.copy(alpha = 0.8f),
            primaryColor
        )
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.88f),
                colors = CardDefaults.cardColors(containerColor = headerBgColor),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header with theme gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = gradientColors
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                "CHARGER PARTIE",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${console.uppercase()} - $gameName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE0E0E0)
                            )
                        }
                    }
                    
                    // Slots grid
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (slot in 1..5) {
                            // Structure : saves/{console}/{gameName}/slot{slot}.state
                            val gameDir = File("/storage/emulated/0/GameLibrary-Data/saves/$console/$gameName")
                            val expectedSaveFile = File(gameDir, "slot$slot.state")
                            
                            // Recherche flexible : d'abord le fichier exact, puis n'importe quel slot{slot}.state
                            var saveFile: File? = if (expectedSaveFile.exists()) expectedSaveFile else null
                            if (saveFile == null && gameDir.exists()) {
                                val stateFiles = gameDir.listFiles { _, name -> name.startsWith("slot$slot") && name.endsWith(".state") }
                                if (stateFiles != null && stateFiles.isNotEmpty()) {
                                    saveFile = stateFiles[0]
                                }
                            }
                            
                            val thumbnailFile = File(gameDir, "slot${slot}_thumbnail.png")
                            val isOccupied = saveFile != null && saveFile.exists()
                            val hasThumbnail = thumbnailFile.exists()
                            val thumbnailTimestamp = if (hasThumbnail) thumbnailFile.lastModified() else 0L
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isOccupied) { 
                                        if (isOccupied) onSlotSelected(slot) 
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOccupied) 
                                        headerBgColor.copy(alpha = 0.7f) 
                                    else 
                                        headerBgColor.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail
                                    if (hasThumbnail) {
                                        val thumbnailBitmap = remember(thumbnailFile.absolutePath, thumbnailTimestamp) {
                                            android.graphics.BitmapFactory.decodeFile(thumbnailFile.absolutePath)
                                        }
                                        thumbnailBitmap?.let { bitmap ->
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Slot $slot",
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        2.dp,
                                                        primaryColor,
                                                        RoundedCornerShape(8.dp)
                                                    ),
                                                contentScale = ContentScale.Crop
                                            )
                                        } ?: EmptySlotPlaceholderDetails(slot, headerBgColor, primaryColor)
                                    } else {
                                        EmptySlotPlaceholderDetails(slot, headerBgColor, primaryColor)
                                    }
                                    
                                    // Info
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "SLOT $slot",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isOccupied) primaryColor else Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        if (isOccupied) {
                                            val lastModified = saveFile.lastModified()
                                            val sizeKB = saveFile.length() / 1024
                                            val dateFormat = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                                            val dateStr = dateFormat.format(java.util.Date(lastModified))
                                            
                                            Text(
                                                "📅 $dateStr",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFB0B0B0)
                                            )
                                            Text(
                                                "💾 ${sizeKB}KB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFB0B0B0)
                                            )
                                        } else {
                                            Text(
                                                "Slot vide",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray,
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Close button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBgColor)
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = headerBgColor.copy(alpha = 0.5f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("FERMER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySlotPlaceholderDetails(slot: Int, headerBgColor: Color, primaryColor: Color) {
    val placeholderGradient = remember(headerBgColor) {
        listOf(
            headerBgColor.copy(alpha = 0.6f),
            headerBgColor.copy(alpha = 0.3f)
        )
    }
    
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = placeholderGradient
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                primaryColor.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$slot",
            style = MaterialTheme.typography.displayMedium,
            color = primaryColor.copy(alpha = 0.3f),
            fontWeight = FontWeight.Bold
        )
    }
}
