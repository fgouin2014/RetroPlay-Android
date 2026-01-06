package com.retroplay.overlay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroplay.overlay.assets.OverlayAssetManager
import java.io.File

/**
 * File browser custom pour sélectionner les fichiers .cfg d'overlay
 * Compatible avec Android 11+ sans utiliser le Storage Access Framework (SAF)
 * 
 * Utilise directement l'API File avec la permission MANAGE_EXTERNAL_STORAGE
 * pour afficher TOUS les fichiers .cfg, même ceux non indexés par MediaStore
 */
@Composable
fun OverlayCfgBrowserDialog(
    onDismiss: () -> Unit,
    onCfgSelected: (overlayName: String, cfgName: String) -> Unit
) {
    // État de navigation
    var selectedPackage by remember { mutableStateOf<File?>(null) }
    var cfgFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var overlayPackages by remember { mutableStateOf<List<File>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Charger la liste des packages au premier affichage
    LaunchedEffect(Unit) {
        try {
            val overlayDir = File(OverlayAssetManager.OVERLAY_DIR)
            if (!overlayDir.exists()) {
                errorMessage = "Overlay directory not found: ${overlayDir.absolutePath}"
            } else {
                overlayPackages = overlayDir.listFiles { file -> 
                    file.isDirectory 
                }?.sortedBy { it.name } ?: emptyList()
                
                if (overlayPackages.isEmpty()) {
                    errorMessage = "No overlay packages found in ${overlayDir.absolutePath}"
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error reading overlay directory: ${e.message}"
        }
    }
    
    // Charger les .cfg quand un package est sélectionné
    LaunchedEffect(selectedPackage) {
        selectedPackage?.let { pkg ->
            try {
                cfgFiles = pkg.listFiles { file -> 
                    file.extension.equals("cfg", ignoreCase = true)
                }?.sortedBy { it.name } ?: emptyList()
                
                if (cfgFiles.isEmpty()) {
                    errorMessage = "No .cfg files found in ${pkg.name}"
                }
            } catch (e: Exception) {
                errorMessage = "Error reading .cfg files: ${e.message}"
            }
        }
    }
    
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
                    containerColor = Color(0xFF1C1C1C)
                ),
                border = BorderStroke(2.dp, Color(0xFF9575CD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedPackage != null) {
                                IconButton(onClick = { 
                                    selectedPackage = null
                                    errorMessage = null
                                }) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color(0xFF9575CD)
                                    )
                                }
                            }
                            Text(
                                text = if (selectedPackage == null) "Select Overlay Package" else "Select .cfg File",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9575CD)
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFFB0B0B0))
                        }
                    }
                    
                    HorizontalDivider(color = Color(0xFF333333))
                    
                    // Afficher le chemin actuel
                    Text(
                        text = if (selectedPackage == null) {
                            "Location: ${OverlayAssetManager.OVERLAY_DIR}"
                        } else {
                            "Package: ${selectedPackage!!.name}"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                    
                    // Message d'erreur si présent
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF8B0000).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFFF6B6B),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    // Contenu scrollable
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F))
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (selectedPackage == null) {
                                // Liste des packages d'overlay
                                if (overlayPackages.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No overlay packages found",
                                            color = Color(0xFF888888)
                                        )
                                    }
                                } else {
                                    overlayPackages.forEach { pkg ->
                                        OverlayPackageItem(
                                            packageName = pkg.name,
                                            onClick = { 
                                                selectedPackage = pkg
                                                errorMessage = null
                                            }
                                        )
                                    }
                                }
                            } else {
                                // Liste des fichiers .cfg dans le package sélectionné
                                if (cfgFiles.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No .cfg files found in ${selectedPackage!!.name}",
                                            color = Color(0xFF888888)
                                        )
                                    }
                                } else {
                                    cfgFiles.forEach { cfg ->
                                        CfgFileItem(
                                            fileName = cfg.name,
                                            fileSize = cfg.length(),
                                            onClick = {
                                                onCfgSelected(selectedPackage!!.name, cfg.name)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Footer avec info
                    HorizontalDivider(color = Color(0xFF333333))
                    Text(
                        text = if (selectedPackage == null) {
                            "${overlayPackages.size} package(s) found"
                        } else {
                            "${cfgFiles.size} .cfg file(s) found in ${selectedPackage!!.name}"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    }
}

/**
 * Item pour afficher un package d'overlay
 */
@Composable
private fun OverlayPackageItem(
    packageName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1C)
        ),
        border = BorderStroke(1.dp, Color(0xFF2F2F2F))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône dossier (texte)
            Text(
                text = "📂",
                fontSize = 28.sp,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = packageName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Tap to view .cfg files",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

/**
 * Item pour afficher un fichier .cfg
 */
@Composable
private fun CfgFileItem(
    fileName: String,
    fileSize: Long,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C1C1C)
        ),
        border = BorderStroke(1.dp, Color(0xFF2F2F2F))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône fichier .cfg (texte)
            Text(
                text = "📄",
                fontSize = 28.sp,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = formatFileSize(fileSize),
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

/**
 * Formater la taille d'un fichier
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

