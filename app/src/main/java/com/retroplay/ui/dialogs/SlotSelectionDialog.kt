package com.retroplay.ui.dialogs

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.config.RetroPlayConfigManager
import com.retroplay.ThemeManager
import java.io.File
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory

// Modern Slot Selection Dialog with Theme Colors
@Composable
fun SlotSelectionDialog(
    title: String,
    console: String,
    gameName: String,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit,
    onDeleteSlot: ((Int) -> Unit)? = null
) {
    var slotToDelete by remember { mutableStateOf<Int?>(null) }
    
    // Get theme colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeManager = remember { ThemeManager.getInstance(context) }
    val primaryColor = remember { Color(themeManager.getPrimaryColor(context)) }
    val headerBgColor = remember { Color(themeManager.getHeaderBackgroundColor(context)) }
    
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
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
                                title.uppercase(),
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
                            val saveFile = File(gameDir, "slot$slot.state")
                            val thumbnailFile = File(gameDir, "slot${slot}_thumbnail.png")
                            val isOccupied = saveFile.exists()
                            val hasThumbnail = thumbnailFile.exists()
                            val thumbnailTimestamp = if (hasThumbnail) thumbnailFile.lastModified() else 0L
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSlotSelected(slot) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOccupied) 
                                        headerBgColor.copy(alpha = 0.7f) 
                                    else 
                                        headerBgColor.copy(alpha = 0.4f)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
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
                                            BitmapFactory.decodeFile(thumbnailFile.absolutePath)
                                        }
                                        thumbnailBitmap?.let { bitmap ->
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Slot $slot",
                                                modifier = Modifier
                                                    .size(100.dp)
                                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                                    .border(
                                                        2.dp,
                                                        primaryColor,
                                                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                                    ),
                                                contentScale = ContentScale.Crop
                                            )
                                        } ?: EmptySlotPlaceholder(slot, headerBgColor, primaryColor)
                                    } else {
                                        EmptySlotPlaceholder(slot, headerBgColor, primaryColor)
                                    }
                                    
                                    // Info
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "SLOT $slot",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isOccupied) primaryColor else Color.Gray,
                                                fontWeight = FontWeight.Bold
                                            )
                                            
                                            if (isOccupied && onDeleteSlot != null) {
                                                IconButton(
                                                    onClick = { slotToDelete = slot },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Text("🗑️", fontSize = 18.sp)
                                                }
                                            }
                                        }
                                        
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
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text("FERMER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        // Confirmation dialog for deletion
        slotToDelete?.let { slot ->
            AlertDialog(
                onDismissRequest = { slotToDelete = null },
                containerColor = headerBgColor,
                title = { 
                    Text(
                        "Supprimer Slot $slot ?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = { 
                    Text(
                        "Cette action est irréversible. Le fichier de sauvegarde et sa capture d'écran seront supprimés.",
                        color = Color(0xFFE0E0E0)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteSlot?.invoke(slot)
                            slotToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        )
                    ) {
                        Text("SUPPRIMER", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { slotToDelete = null }
                    ) {
                        Text("ANNULER", color = Color(0xFFB0B0B0))
                    }
                }
            )
        }
    }
}

// Empty slot placeholder composable with theme colors
@Composable
private fun EmptySlotPlaceholder(slot: Int, headerBgColor: Color, primaryColor: Color) {
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                primaryColor.copy(alpha = 0.3f),
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
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