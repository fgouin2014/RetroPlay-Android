package com.retroplay.cheat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog de sélection des codes de triche
 * Interface Material 3 avec liste scrollable
 */
@Composable
fun CheatSelectionDialog(
    gameName: String,
    console: String,
    cheats: List<CheatManager.Cheat>,
    onDismiss: () -> Unit,
    onCheatsChanged: (List<CheatManager.Cheat>) -> Unit,
    onAddCustomCheat: () -> Unit,
    onDeleteCheat: ((CheatManager.Cheat) -> Unit)? = null
) {
    // État mutable pour les cheats (recharge quand 'cheats' change)
    var cheatsList by remember(cheats) { mutableStateOf(cheats) }
    
    // Tab sélectionné (0 = RetroArch, 1 = User)
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Filtrer les cheats selon le tab
    val filteredCheats = when (selectedTab) {
        0 -> cheatsList.filter { it.type == CheatManager.CheatType.RETROARCH }
        1 -> cheatsList.filter { it.type == CheatManager.CheatType.CUSTOM }
        else -> cheatsList
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f),  // Plus haut pour plus d'espace
                colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)  // Padding réduit pour plus d'espace
                ) {
                    // Header
                    Text(
                        "CHEAT CODES - ${console.uppercase()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Text(
                        gameName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    HorizontalDivider(
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    // Tabs (RetroArch / User)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("RetroArch", color = if (selectedTab == 0) Color(0xFF4CAF50) else Color.Gray) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("User", color = if (selectedTab == 1) Color(0xFF4CAF50) else Color.Gray) }
                            )
                        }
                        
                        // Bouton [+ Add] à droite
                        TextButton(
                            onClick = onAddCustomCheat,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                        ) {
                            Text("[+ ADD]", fontFamily = FontFamily.Monospace)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Liste des codes (filtrés selon le tab)
                    if (filteredCheats.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (selectedTab == 0) "No RetroArch cheats" else "No User cheats",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    if (selectedTab == 0) "Download RetroArch cheats" else "Add custom cheats using [+ ADD]",
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)  // Espacement compact
                        ) {
                            items(
                                items = filteredCheats,  // Utiliser filteredCheats, pas cheatsList
                                key = { cheat -> "${cheat.description}_${cheat.code}" }  // Key unique pour performance
                            ) { cheat ->
                                CheatItem(
                                    cheat = cheat,
                                    onToggle = { enabled ->
                                        cheatsList = cheatsList.map {
                                            if (it == cheat) it.copy(enabled = enabled) else it
                                        }
                                        onCheatsChanged(cheatsList)
                                    },
                                    onDelete = if (onDeleteCheat != null && cheat.type == CheatManager.CheatType.CUSTOM) {
                                        {
                                            cheatsList = cheatsList.filter { it != cheat }
                                            onDeleteCheat(cheat)
                                            onCheatsChanged(cheatsList)
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    // Bouton Close
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun CheatItem(
    cheat: CheatManager.Cheat,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null  // Null = pas de bouton delete (codes RetroArch)
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (cheat.enabled) Color(0xFF1B5E20) else Color(0xFF212121)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    cheat.description,
                    color = if (cheat.enabled) Color(0xFF4CAF50) else Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                
                Text(
                    cheat.code,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                // Badge du type de code
                Text(
                    cheat.type.name,
                    color = Color(0xFF9E9E9E),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton Delete (seulement pour codes custom)
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFE53935)
                        )
                    ) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Switch(
                    checked = cheat.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF81C784),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }
    }
}

/**
 * Dialog pour ajouter un code personnalisé
 */
@Composable
fun AddCustomCheatDialog(
    console: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, CheatManager.CheatType) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CheatManager.CheatType.GAMESHARK) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD000000))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add Custom Cheat",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF2196F3)
                )
                
                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = Color.Gray) },
                    placeholder = { Text("e.g., Infinite Health") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color.Gray
                    )
                )
                
                // Code
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Code", color = Color.Gray) },
                    placeholder = { Text("8009C6E4 03E7") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color.Gray
                    )
                )
                
                // Type de code
                Text("Code Type:", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CheatManager.CheatType.GAMESHARK to "GameShark",
                        CheatManager.CheatType.GAME_GENIE to "Game Genie",
                        CheatManager.CheatType.ACTION_REPLAY to "Action Replay"
                    ).forEach { (type, label) ->
                        TextButton(
                            onClick = { selectedType = type },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (selectedType == type) Color(0xFF2196F3) else Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                label,
                                color = if (selectedType == type) Color.White else Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = Color.Gray)
                
                // Boutons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (description.isNotBlank() && code.isNotBlank()) {
                                onAdd(description, code, selectedType)
                                onDismiss()
                            }
                        },
                        enabled = description.isNotBlank() && code.isNotBlank()
                    ) {
                        Text("Add", color = if (description.isNotBlank() && code.isNotBlank()) Color(0xFF4CAF50) else Color.Gray)
                    }
                }
            }
        }
    }
}

