package com.retroplay

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Core information pour chaque émulateur
 */
data class CoreInfo(
    val coreId: String,
    val displayName: String,
    val fileName: String,
    val description: String = ""
)

/**
 * Gestionnaire de sélection de cores
 */
object CoreSelector {
    private const val TAG = "CoreSelector"
    
    /**
     * Retourne la liste des cores disponibles pour une console
     */
    fun getAvailableCores(console: String): List<CoreInfo> {
        val consoleKey = if (console.contains("/")) {
            console.substringBefore("/").lowercase()
        } else {
            console.lowercase()
        }
        
        return when (consoleKey) {
            "arcade" -> listOf(
                CoreInfo("fbneo", "FBNeo", "fbneo_libretro_android.so", "Most compatible for arcade"),
                CoreInfo("mame2003_plus", "MAME 2003 Plus", "mame2003_plus_libretro_android.so", "Good compatibility"),
                CoreInfo("mame2003", "MAME 2003", "mame2003_libretro_android.so", "Classic version"),
                CoreInfo("mame2010", "MAME 2010", "mame2010_libretro_android.so", "More recent, may crash")
            )
            "mame" -> listOf(
                CoreInfo("mame2003_plus", "MAME 2003 Plus", "mame2003_plus_libretro_android.so", "Recommended"),
                CoreInfo("mame2003", "MAME 2003", "mame2003_libretro_android.so", "Classic version"),
                CoreInfo("mame2010", "MAME 2010", "mame2010_libretro_android.so", "More recent"),
                CoreInfo("fbneo", "FBNeo", "fbneo_libretro_android.so", "Alternative")
            )
            "fbneo", "neogeo", "cps1", "cps2" -> listOf(
                CoreInfo("fbneo", "FBNeo", "fbneo_libretro_android.so", "Recommended"),
                CoreInfo("mame2003_plus", "MAME 2003 Plus", "mame2003_plus_libretro_android.so", "Alternative")
            )
            "n64" -> listOf(
                CoreInfo("parallel_n64", "ParaLLEl N64", "parallel_n64_libretro_android.so", "Fast and accurate"),
                CoreInfo("mupen64plus_next", "Mupen64Plus Next", "mupen64plus_next_libretro_android.so", "High compatibility")
            )
            "nes" -> listOf(
                CoreInfo("fceumm", "FCEUmm", "fceumm_libretro_android.so", "Best NES emulator")
            )
            "snes" -> listOf(
                CoreInfo("snes9x", "Snes9x", "snes9x_libretro_android.so", "Best SNES emulator")
            )
            "genesis", "megadrive", "md", "scd", "segacd", "mastersystem", "sms", "gamegear", "gg" -> listOf(
                CoreInfo("genesis_plus_gx", "Genesis Plus GX", "genesis_plus_gx_libretro_android.so", "Best for Sega consoles")
            )
            "psx", "ps1", "playstation" -> listOf(
                CoreInfo("pcsx_rearmed", "PCSX ReARMed", "pcsx_rearmed_libretro_android.so", "Best PSX emulator")
            )
            "psp" -> listOf(
                CoreInfo("ppsspp", "PPSSPP", "ppsspp_libretro_android.so", "Best PSP emulator")
            )
            "gb", "gbc" -> listOf(
                CoreInfo("gambatte", "Gambatte", "gambatte_libretro_android.so", "Best GB/GBC emulator")
            )
            "gba" -> listOf(
                CoreInfo("mgba", "mGBA", "libmgba_libretro_android.so", "Best GBA emulator")
            )
            else -> emptyList()
        }
    }
    
    /**
     * Obtient le core actuellement utilisé pour un jeu
     */
    fun getCurrentCore(context: Context, gamePath: String, console: String): CoreInfo? {
        val relativePath = if (gamePath.contains("/GameLibrary-Data/")) {
            gamePath.substringAfter("/GameLibrary-Data/")
        } else {
            return null
        }
        
        val overrideManager = CoreOverrideManager.getInstance()
        val overrideCoreId = overrideManager.getCoreOverride(relativePath)
        
        val availableCores = getAvailableCores(console)
        
        return if (overrideCoreId != null) {
            // Il y a un override
            availableCores.firstOrNull { it.coreId == overrideCoreId }
        } else {
            // Utilise le core par défaut (premier de la liste)
            availableCores.firstOrNull()
        }
    }
    
    /**
     * Définit un override de core pour un jeu
     */
    fun setCoreOverride(gamePath: String, coreId: String, reason: String = "User selected") {
        val relativePath = if (gamePath.contains("/GameLibrary-Data/")) {
            gamePath.substringAfter("/GameLibrary-Data/")
        } else {
            Log.e(TAG, "Invalid game path: $gamePath")
            return
        }
        
        val overrideManager = CoreOverrideManager.getInstance()
        overrideManager.setOverride(relativePath, coreId, reason)
        Log.i(TAG, "Core override set: $relativePath -> $coreId")
    }
    
    /**
     * Supprime l'override de core pour un jeu (utilise le core par défaut)
     */
    fun removeCoreOverride(gamePath: String) {
        val relativePath = if (gamePath.contains("/GameLibrary-Data/")) {
            gamePath.substringAfter("/GameLibrary-Data/")
        } else {
            Log.e(TAG, "Invalid game path: $gamePath")
            return
        }
        
        val overrideManager = CoreOverrideManager.getInstance()
        overrideManager.removeOverride(relativePath)
        Log.i(TAG, "Core override removed: $relativePath")
    }
    
    /**
     * Vérifie si un jeu a un override de core
     */
    fun hasOverride(gamePath: String): Boolean {
        val relativePath = if (gamePath.contains("/GameLibrary-Data/")) {
            gamePath.substringAfter("/GameLibrary-Data/")
        } else {
            return false
        }
        
        val overrideManager = CoreOverrideManager.getInstance()
        return overrideManager.hasOverride(relativePath)
    }
}

/**
 * Dialog Compose pour sélectionner un core
 */
@Composable
fun CoreSelectorDialog(
    console: String,
    currentGamePath: String,
    onCoreSelected: (CoreInfo) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
 ) {
     val availableCores = remember { CoreSelector.getAvailableCores(console) }
     val hasOverride = remember { CoreSelector.hasOverride(currentGamePath) }
     val currentCoreId = remember { 
         val relativePath = if (currentGamePath.contains("/GameLibrary-Data/")) {
             currentGamePath.substringAfter("/GameLibrary-Data/")
         } else ""
         if (relativePath.isNotEmpty()) {
             CoreOverrideManager.getInstance().getCoreOverride(relativePath)
         } else null
     }
     val currentCore = remember { 
         availableCores.firstOrNull { it.coreId == currentCoreId } ?: availableCores.firstOrNull()
     }
     
     Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Select Emulator Core",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Console: ${console.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (currentCore != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (hasOverride) {
                            "[⚡] ${currentCore.displayName}"
                        } else {
                            "[⚙] ${currentCore.displayName} (Default)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasOverride) Color(0xFFFFA726) else Color(0xFF4CAF50)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Liste des cores disponibles
                availableCores.forEach { core ->
                    val isActive = currentCore?.coreId == core.coreId
                    CoreOptionItem(
                        coreInfo = core,
                        isActive = isActive,
                        onClick = { onCoreSelected(core) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Bouton Reset to Default
                if (hasOverride) {
                    Button(
                        onClick = onResetToDefault,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350)
                        )
                    ) {
                        Text("Reset to Default")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Bouton Cancel
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CoreOptionItem(
    coreInfo: CoreInfo,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = coreInfo.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (coreInfo.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = coreInfo.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isActive) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

