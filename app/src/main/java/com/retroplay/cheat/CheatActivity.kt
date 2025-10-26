package com.retroplay.cheat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.retroplay.cheat.CheatManager.CheatType

/**
 * Activity pour gérer les codes de triche
 * Interface Compose complète
 */
class CheatActivity : ComponentActivity() {
    
    private lateinit var cheatManager: CheatManager
    private lateinit var console: String
    private lateinit var gameName: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Récupérer les paramètres
        console = intent.getStringExtra("console") ?: run {
            Toast.makeText(this, "Error: No console provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        gameName = intent.getStringExtra("gameName") ?: run {
            Toast.makeText(this, "Error: No game name provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        cheatManager = CheatManager(this)
        
        setContent {
            MaterialTheme {
                CheatScreen(
                    console = console,
                    gameName = gameName,
                    cheatManager = cheatManager,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun CheatScreen(
    console: String,
    gameName: String,
    cheatManager: CheatManager,
    onFinish: () -> Unit
) {
    // Charger les codes
    var cheats by remember { mutableStateOf(cheatManager.loadCheatsForGame(console, gameName)) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    CheatSelectionDialog(
        gameName = gameName,
        console = console,
        cheats = cheats,
        onDismiss = onFinish,
        onCheatsChanged = { updatedCheats ->
            cheats = updatedCheats
            // Sauvegarder les modifications
            cheatManager.saveEnabledCheats(console, gameName, updatedCheats)
        },
        onAddCustomCheat = {
            showAddDialog = true
        },
        onDeleteCheat = { deletedCheat ->
            // Supprimer du fichier .cht custom
            cheatManager.deleteCustomCheat(console, gameName, deletedCheat)
            // Recharger tous les codes
            cheats = cheatManager.loadCheatsForGame(console, gameName)
        }
    )
    
    // Dialog pour ajouter un code personnalisé
    if (showAddDialog) {
        AddCustomCheatDialog(
            console = console,
            onDismiss = { showAddDialog = false },
            onAdd = { description, code, type ->
                val newCheat = cheatManager.createCustomCheat(
                    console = console,
                    gameName = gameName,
                    description = description,
                    code = code,
                    type = type
                )
                
                // Ajouter au fichier .cht custom
                cheatManager.addCustomCheatToFile(console, gameName, newCheat)
                // Recharger tous les codes pour inclure le nouveau
                cheats = cheatManager.loadCheatsForGame(console, gameName)
            }
        )
    }
}

